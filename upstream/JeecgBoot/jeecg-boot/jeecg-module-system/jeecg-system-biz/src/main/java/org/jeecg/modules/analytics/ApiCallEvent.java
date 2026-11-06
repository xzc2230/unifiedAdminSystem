package org.jeecg.modules.analytics;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/** Fixed metadata only. Never add headers, credentials, body, or client-supplied labels. */
public record ApiCallEvent(String event_id, String event_time, String api_id, String method, int status, long duration_ms) {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE");

    public static ApiCallEvent create(String apiId, String method, int status, long elapsed) {
        String id = apiId == null ? "" : apiId.substring(0, Math.min(64, apiId.length()));
        return new ApiCallEvent(UUID.randomUUID().toString(), TIME.format(Instant.now()), id,
                method != null && METHODS.contains(method) ? method : "OTHER",
                status >= 100 && status <= 599 ? status : 500, Math.max(0, Math.min(elapsed, 4_294_967_295L)));
    }
}
