package org.jeecg.modules.packagemanagement.infrastructure.integration;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jeecg.modules.packagemanagement.application.port.AppLogPort;
import org.jeecg.modules.packagemanagement.config.RequestLogClickHouseProperties;
import org.jeecg.modules.packagemanagement.domain.applog.AppLog;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;

/** Request logs only. The analytics api_call table is a different store. */
public final class ClickHouseAppLogAdapter implements AppLogPort {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(SHANGHAI);
    private static final String INSERT = "INSERT INTO jeecg_analytics.app_request_log SETTINGS async_insert=1, wait_for_async_insert=1 FORMAT JSONEachRow";
    private static final String COLUMNS = "id, package_name, req_path, req_host, req_ip, req_params, req_headers, req_method, result_msg, error_msg, toString(req_time, 'Asia/Shanghai') AS req_time, is_mobile";
    /** Qualify req_time. The SELECT alias has the same name and would otherwise replace the column in comparisons. */
    private static final String FILTER = """
            WHERE package_name IN {packages:Array(String)}
              AND ({name:String} = '' OR positionCaseInsensitiveUTF8(package_name, {name:String}) > 0)
              AND ({bounded:UInt8} = 0 OR (
                    jeecg_analytics.app_request_log.req_time >= parseDateTime64BestEffort({start:String}, 3, 'Asia/Shanghai')
                AND jeecg_analytics.app_request_log.req_time < parseDateTime64BestEffort({end:String}, 3, 'Asia/Shanghai')))
            """;
    private static final int MAX_RESPONSE = 16 * 1024 * 1024;

    private final boolean enabled;
    private final URI endpoint;
    private final HttpClient client;
    private final String writerAuth;
    private final String readerAuth;
    private final Duration timeout;

    public ClickHouseAppLogAdapter(RequestLogClickHouseProperties properties) {
        enabled = properties.isEnabled();
        if (!enabled) {
            endpoint = null;
            client = null;
            writerAuth = null;
            readerAuth = null;
            timeout = Duration.ofSeconds(5);
            return;
        }
        endpoint = URI.create(properties.getEndpoint());
        boolean loopback = endpoint.getHost() != null && Set.of("127.0.0.1", "localhost", "[::1]").contains(endpoint.getHost());
        if (endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !(endpoint.getPath().isEmpty() || endpoint.getPath().equals("/"))
                || !("https".equals(endpoint.getScheme()) || ("http".equals(endpoint.getScheme()) && loopback))
                || properties.getTimeoutSeconds() < 1 || properties.getTimeoutSeconds() > 30) {
            throw new IllegalArgumentException("Invalid ClickHouse endpoint or timeout; use HTTPS or loopback HTTP");
        }
        writerAuth = auth(properties.getWriterUser(), properties.getWriterPassword());
        readerAuth = auth(properties.getReaderUser(), properties.getReaderPassword());
        timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public AppLog save(AppLog log) {
        requireEnabled();
        if (log == null || log.getReqTime() == null) throw new PackageFailure(400, "请求时间不能为空");
        checkId(log.getId());
        request("query=" + encode(INSERT), json(log), writerAuth);
        return log;
    }

    public Optional<AppLog> findById(String id) {
        requireEnabled();
        checkId(id);
        String sql = "SELECT " + COLUMNS + " FROM jeecg_analytics.app_request_log WHERE id = {id:String} ORDER BY jeecg_analytics.app_request_log.req_time DESC LIMIT 1 FORMAT JSONEachRow";
        List<AppLog> rows = rows(request("param_id=" + encode(id), sql, readerAuth));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Page query(AppLog filter, int page, int size, Collection<String> allowedPackages) {
        requireEnabled();
        if (page < 1 || size < 1 || size > 1000) throw new IllegalArgumentException("Invalid page");
        if (allowedPackages == null || allowedPackages.isEmpty()) return new Page(List.of(), 0);
        long total = count(filter, allowedPackages);
        if (total == 0) return new Page(List.of(), 0);
        int offset = Math.multiplyExact(page - 1, size);
        String sql = "SELECT " + COLUMNS + " FROM jeecg_analytics.app_request_log " + FILTER
                + "ORDER BY jeecg_analytics.app_request_log.req_time DESC, id DESC LIMIT {limit:UInt32} OFFSET {offset:UInt32} FORMAT JSONEachRow";
        return new Page(rows(request(parameters(filter, allowedPackages, size, offset), sql, readerAuth)), total);
    }

    public List<AppLog> export(AppLog filter, Collection<String> allowedPackages) {
        List<AppLog> rows = new ArrayList<>();
        int page = 1;
        while (true) {
            Page batch = query(filter, page, 1000, allowedPackages);
            if (batch.rows().isEmpty()) return rows;
            rows.addAll(batch.rows());
            if (batch.rows().size() < 1000) return rows;
            page++;
        }
    }

    public void deleteAllById(Iterable<String> ids) {
        requireEnabled();
        List<String> keys = new ArrayList<>();
        for (String id : ids) { checkId(id); keys.add(id); }
        if (keys.isEmpty()) return;
        String sql = "DELETE FROM jeecg_analytics.app_request_log WHERE id IN {ids:Array(String)}";
        request("param_ids=" + encode(array(keys)), sql, writerAuth);
    }

    /** Delete by id inside the caller's packages. Avoids a reader scan that the 3-second profile rejects. */
    public void deleteOwned(Collection<String> ids, Collection<String> packages) {
        requireEnabled();
        if (ids == null || packages == null || packages.isEmpty()) return;
        List<String> keys = new ArrayList<>();
        for (String id : ids) { checkId(id); keys.add(id); }
        if (keys.isEmpty()) return;
        String sql = "DELETE FROM jeecg_analytics.app_request_log WHERE id IN {ids:Array(String)} AND package_name IN {packages:Array(String)}";
        request("param_ids=" + encode(array(keys)) + "&param_packages=" + encode(array(packages)), sql, writerAuth);
    }

    public long clean(AppLog filter, Collection<String> allowedPackages) {
        requireEnabled();
        if (allowedPackages == null || allowedPackages.isEmpty()) return 0;
        long matched = count(filter, allowedPackages);
        if (matched == 0) return 0;
        String sql = "DELETE FROM jeecg_analytics.app_request_log " + FILTER;
        request(parameters(filter, allowedPackages, null, null), sql, writerAuth);
        return matched;
    }

    private long count(AppLog filter, Collection<String> allowedPackages) {
        String sql = "SELECT count() AS total FROM jeecg_analytics.app_request_log " + FILTER + "FORMAT JSONEachRow";
        String body = request(parameters(filter, allowedPackages, null, null), sql, readerAuth);
        for (String line : body.split("\n")) {
            if (line.isBlank()) continue;
            Object value = JSONObject.parseObject(line).get("total");
            if (value instanceof Number number) return number.longValue();
            if (value != null) return Long.parseLong(value.toString());
        }
        throw new PackageFailure(502, "ClickHouse request failed");
    }

    private static List<AppLog> rows(String body) {
        List<AppLog> rows = new ArrayList<>();
        for (String line : body.split("\n")) if (!line.isBlank()) rows.add(row(JSONObject.parseObject(line)));
        return rows;
    }

    private static AppLog row(JSONObject json) {
        AppLog log = new AppLog();
        log.setId(json.getString("id"));
        log.setPackageName(json.getString("package_name"));
        log.setReqPath(json.getString("req_path"));
        log.setReqHost(json.getString("req_host"));
        log.setReqIp(json.getString("req_ip"));
        log.setReqParams(json.getString("req_params"));
        log.setReqHeards(json.getString("req_headers"));
        log.setReqMethod(json.getString("req_method"));
        log.setResultMsg(json.getString("result_msg"));
        log.setErrorMsg(json.getString("error_msg"));
        log.setReqTime(parseTime(json.getString("req_time")));
        log.setIsMobile(json.getInteger("is_mobile"));
        return log;
    }

    private static Date parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        LocalDateTime local;
        int dot = text.indexOf('.');
        if (dot == 19) {
            int end = dot + 1;
            while (end < text.length() && Character.isDigit(text.charAt(end))) end++;
            String fraction = text.substring(dot + 1, end);
            if (fraction.length() > 3) fraction = fraction.substring(0, 3);
            while (fraction.length() < 3) fraction = fraction + "0";
            local = LocalDateTime.parse(text.substring(0, 19) + "." + fraction, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        } else {
            local = LocalDateTime.parse(text.substring(0, 19), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return Date.from(local.atZone(SHANGHAI).toInstant());
    }

    private String parameters(AppLog filter, Collection<String> packages, Integer limit, Integer offset) {
        boolean bounded = filter != null && filter.getStartReqTime() != null && filter.getEndReqTime() != null;
        String name = filter == null || filter.getPackageName() == null ? "" : filter.getPackageName();
        if (name.length() > 256) throw new PackageFailure(400, "无效包名");
        StringBuilder query = new StringBuilder();
        append(query, "param_packages", array(packages));
        append(query, "param_name", name);
        append(query, "param_bounded", bounded ? "1" : "0");
        append(query, "param_start", bounded ? dayStart(filter.getStartReqTime()) : "1970-01-01 00:00:00");
        append(query, "param_end", bounded ? nextDay(filter.getEndReqTime()) : "1970-01-01 00:00:00");
        if (limit != null) append(query, "param_limit", Integer.toString(limit));
        if (offset != null) append(query, "param_offset", Integer.toString(offset));
        return query.toString();
    }

    private static String array(Collection<String> values) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (String value : values) {
            if (value == null || value.length() > 256) throw new PackageFailure(400, "无效包名");
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c < 32 || c == 127) throw new PackageFailure(400, "无效包名");
            }
            if (!first) builder.append(',');
            first = false;
            builder.append('\'');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '\\' || c == '\'') builder.append('\\');
                builder.append(c);
            }
            builder.append('\'');
        }
        builder.append(']');
        return builder.toString();
    }

    private static String json(AppLog log) {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", log.getId());
        row.put("package_name", text(log.getPackageName()));
        row.put("req_path", text(log.getReqPath()));
        row.put("req_host", text(log.getReqHost()));
        row.put("req_ip", text(log.getReqIp()));
        row.put("req_params", text(log.getReqParams()));
        row.put("req_headers", text(log.getReqHeards()));
        row.put("req_method", text(log.getReqMethod()));
        row.put("result_msg", text(log.getResultMsg()));
        row.put("error_msg", text(log.getErrorMsg()));
        row.put("req_time", MILLIS.format(log.getReqTime().toInstant()));
        int mobile = log.getIsMobile() == null ? 0 : log.getIsMobile();
        row.put("is_mobile", Math.max(0, Math.min(255, mobile)));
        return JSON.toJSONString(row);
    }

    private static String dayStart(Date value) {
        return value.toInstant().atZone(SHANGHAI).toLocalDate().atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String nextDay(Date value) {
        LocalDate day = value.toInstant().atZone(SHANGHAI).toLocalDate().plusDays(1);
        return day.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String text(String value) { return value == null ? "" : value; }

    private static void checkId(String id) {
        if (id == null || id.length() < 8 || id.length() > 64) throw new PackageFailure(400, "无效日志编号");
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '-';
            if (!ok) throw new PackageFailure(400, "无效日志编号");
        }
    }

    private void requireEnabled() {
        if (!enabled) throw new PackageFailure(503, "ClickHouse request logs are not configured");
    }

    private String request(String query, String body, String auth) {
        URI uri = URI.create(endpoint.toString().replaceAll("/$", "") + "/?" + query);
        var httpRequest = HttpRequest.newBuilder(uri).timeout(timeout).header("Authorization", auth)
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8)).build();
        try {
            var response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200 || response.body().length() > MAX_RESPONSE) throw new PackageFailure(502, "ClickHouse request failed");
            return response.body();
        } catch (PackageFailure failure) {
            throw failure;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new PackageFailure(502, "ClickHouse request failed");
        } catch (Exception failure) {
            throw new PackageFailure(502, "ClickHouse request failed");
        }
    }

    private static String auth(String user, String password) {
        if (user == null || user.isBlank() || user.contains(":") || password == null || password.isBlank()) {
            throw new IllegalArgumentException("ClickHouse credentials required");
        }
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static void append(StringBuilder query, String name, String value) {
        if (!query.isEmpty()) query.append('&');
        query.append(name).append('=').append(encode(value));
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
