package org.jeecg.modules.packagemanagement.application.domains.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import org.jeecg.modules.packagemanagement.application.port.HttpPort;

/** Registration expiry from RDAP. Missing dates stay empty. */
public final class RdapExpiration {
    private final HttpPort http;

    public RdapExpiration(HttpPort http) { this.http = http; }

    public Date expiration(String apex) {
        try {
            HttpPort.Reply reply = http.exchange("GET", "https://rdap.org/domain/" + apex, null,
                    java.util.Map.of("Accept", "application/rdap+json"));
            if (!reply.success()) return null;
            return parse(reply.body());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static Date parse(String body) {
        if (body == null || body.isBlank()) return null;
        JSONArray events = JSON.parseObject(body).getJSONArray("events");
        if (events == null) return null;
        for (int i = 0; i < events.size(); i++) {
            JSONObject event = events.getJSONObject(i);
            if (event == null) continue;
            String action = String.valueOf(event.getString("eventAction")).toLowerCase(Locale.ROOT);
            if (!action.contains("expiration")) continue;
            String date = event.getString("eventDate");
            if (date == null || date.isBlank()) continue;
            return Date.from(Instant.parse(date));
        }
        return null;
    }
}
