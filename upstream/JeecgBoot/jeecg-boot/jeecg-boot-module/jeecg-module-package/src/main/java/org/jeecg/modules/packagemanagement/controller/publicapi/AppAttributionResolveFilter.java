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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.jeecg.modules.packagemanagement.application.apps.support.AppAttributionResolve;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AppAttributionResolveFilter extends OncePerRequestFilter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AppAttributionResolve resolve;

    public AppAttributionResolveFilter(AppAttributionResolve resolve) {
        this.resolve = resolve;
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
        try {
            Map<String, Object> payload = resolve.tryResolve(node);
            if (payload == null) {
                chain.doFilter(replay(request, body), response);
                return;
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneId.of("Asia/Shanghai"));
            response.setStatus(200);
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Trace-Id", "trace_" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
            response.setDateHeader("Date", now.toInstant().toEpochMilli());
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            JSON.writeValue(response.getOutputStream(), payload);
        } catch (PackageFailure failure) {
            response.setStatus(failure.getCode());
            response.setHeader("Cache-Control", "no-store");
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            JSON.writeValue(response.getOutputStream(), Map.of("message", failure.getMessage()));
        }
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
