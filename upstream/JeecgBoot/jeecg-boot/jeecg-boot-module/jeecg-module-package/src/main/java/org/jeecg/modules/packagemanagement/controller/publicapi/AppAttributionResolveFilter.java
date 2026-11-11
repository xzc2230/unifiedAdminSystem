package org.jeecg.modules.packagemanagement.controller.publicapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jeecg.modules.packagemanagement.application.apps.support.AppAttributionResolve;
import org.jeecg.modules.packagemanagement.application.apps.support.PackageCountryRegions;
import org.jeecg.modules.packagemanagement.application.port.AppLogPort;
import org.jeecg.modules.packagemanagement.application.port.GeoPort;
import org.jeecg.modules.packagemanagement.domain.applog.AppLog;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AppAttributionResolveFilter extends OncePerRequestFilter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AppAttributionResolve resolve;
    private final AppLogPort logs;
    private final GeoPort geo;
    private final Clock clock;

    public AppAttributionResolveFilter(AppAttributionResolve resolve, AppLogPort logs, GeoPort geo, @Qualifier("packageClock") Clock clock) {
        this.resolve = resolve;
        this.logs = logs;
        this.geo = geo;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (!isResolve(request)) {
            chain.doFilter(request, response);
            return;
        }
        byte[] body = request.getInputStream().readAllBytes();
        JsonNode node;
        try {
            node = body.length == 0 ? null : JSON.readTree(body);
        } catch (IOException ex) {
            chain.doFilter(replay(request, body), response);
            return;
        }
        String params = new String(body, StandardCharsets.UTF_8);
        Map<String, String> city = locate(request);
        try {
            AppAttributionResolve.Outcome outcome = resolve.decide(node, city);
            if (outcome.payload() == null) {
                if (!outcome.packageName().isBlank()) record(request, city, outcome.packageName(), params, "", outcome.denyTip());
                chain.doFilter(replay(request, body), response);
                return;
            }
            Map<String, Object> payload = outcome.payload();
            OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Shanghai"));
            response.setStatus(200);
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Trace-Id", "trace_" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            response.setDateHeader("Date", now.toInstant().toEpochMilli());
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String result = JSON.writeValueAsString(payload);
            response.getOutputStream().write(result.getBytes(StandardCharsets.UTF_8));
            record(request, city, String.valueOf(payload.get("applicationCode")), params, result, "");
        } catch (PackageFailure failure) {
            response.setStatus(failure.getCode());
            response.setHeader("Cache-Control", "no-store");
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String result = JSON.writeValueAsString(Map.of("message", failure.getMessage()));
            response.getOutputStream().write(result.getBytes(StandardCharsets.UTF_8));
            record(request, city, applicationCode(node), params, result, failure.getMessage());
        }
    }

    private Map<String, String> locate(HttpServletRequest request) {
        String ip = "";
        try {
            ip = geo.getIpAddress(request);
            Map<String, String> city = geo.getCountryAndCountryCodeByIP(ip);
            Map<String, String> located = city == null ? new LinkedHashMap<>() : new LinkedHashMap<>(city);
            located.put("ip", ip == null ? "" : ip);
            return located;
        } catch (RuntimeException ignored) {
            Map<String, String> located = new LinkedHashMap<>();
            located.put("status", "true");
            located.put("countryCode", "us");
            located.put("ip", ip == null ? "" : ip);
            return located;
        }
    }

    private void record(HttpServletRequest request, Map<String, String> city, String packageName, String params, String result, String error) {
        try {
            if (packageName == null || packageName.isBlank()) return;
            String ip = city == null ? "" : String.valueOf(city.getOrDefault("ip", ""));
            String country = "";
            try {
                country = PackageCountryRegions.accessCode(city);
            } catch (RuntimeException ignored) {
                country = "";
            }
            AppLog row = new AppLog();
            row.setPackageName(packageName);
            row.setReqPath(request.getRequestURI());
            row.setReqIp("(" + country + ")" + (ip == null ? "" : ip));
            row.setReqTime(java.util.Date.from(clock.instant()));
            row.setReqMethod(request.getMethod());
            row.setReqParams(params);
            row.setResultMsg(result);
            if (error != null && !error.isBlank()) row.setErrorMsg(error);
            logs.save(row);
        } catch (Exception failure) {
            LoggerFactory.getLogger(AppAttributionResolveFilter.class).warn("request log was not stored: {}", failure.toString());
        }
    }

    private static String applicationCode(JsonNode node) {
        if (node == null || !node.hasNonNull("applicationCode")) return "";
        String code = node.get("applicationCode").asText("");
        return code == null ? "" : code.trim();
    }

    private static boolean isResolve(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isBlank()) path = request.getRequestURI();
        return "POST".equalsIgnoreCase(request.getMethod()) && path != null && path.endsWith("/api/v1/attribution/resolve");
    }

    private static HttpServletRequest replay(HttpServletRequest request, byte[] body) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public jakarta.servlet.ServletInputStream getInputStream() {
                ByteArrayInputStream input = new ByteArrayInputStream(body);
                return new jakarta.servlet.ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(jakarta.servlet.ReadListener listener) {}
                };
            }

            @Override
            public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        };
    }
}
