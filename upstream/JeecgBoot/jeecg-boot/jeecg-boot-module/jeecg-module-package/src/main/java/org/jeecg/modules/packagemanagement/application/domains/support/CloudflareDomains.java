package org.jeecg.modules.packagemanagement.application.domains.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jeecg.modules.packagemanagement.application.port.HttpPort;
import org.jeecg.modules.packagemanagement.config.PackageManagementProperties;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;

/** Cloudflare zone, Flexible SSL, and the apex plus wildcard proxied A records. */
public final class CloudflareDomains {
    private static final String API = "https://api.cloudflare.com/client/v4";
    private final HttpPort http;
    private final PackageManagementProperties settings;

    public CloudflareDomains(HttpPort http, PackageManagementProperties settings) {
        this.http = http;
        this.settings = settings;
    }

    public Zone open(String apex) {
        String token = required(settings.getCloudflareApiToken(), "CLOUDFLARE_API_TOKEN");
        String account = required(settings.getCloudflareAccountId(), "CLOUDFLARE_ACCOUNT_ID");
        JSONObject body = new JSONObject();
        body.put("name", apex);
        body.put("type", "full");
        JSONObject owner = new JSONObject();
        owner.put("id", account);
        body.put("account", owner);
        HttpPort.Reply created = call("POST", API + "/zones", body.toJSONString(), token);
        JSONObject json = json(created.body());
        if (created.success() && json.getBooleanValue("success")) return zone(json.getJSONObject("result"));
        if (!alreadyExists(json)) throw new PackageFailure(502, message(json));
        HttpPort.Reply listed = call("GET", API + "/zones?name=" + URLEncoder.encode(apex, StandardCharsets.UTF_8), null, token);
        JSONObject existing = json(listed.body());
        if (!listed.success() || !existing.getBooleanValue("success")) throw new PackageFailure(502, message(existing));
        JSONArray result = existing.getJSONArray("result");
        if (result == null || result.isEmpty()) throw new PackageFailure(502, "Cloudflare 未找到已有域名");
        return zone(result.getJSONObject(0));
    }

    public void activate(String zoneId, String apex, String origin) {
        String token = required(settings.getCloudflareApiToken(), "CLOUDFLARE_API_TOKEN");
        JSONObject ssl = new JSONObject();
        ssl.put("value", "flexible");
        HttpPort.Reply setting = call("PATCH", API + "/zones/" + zoneId + "/settings/ssl", ssl.toJSONString(), token);
        JSONObject settingJson = json(setting.body());
        if (!setting.success() || !settingJson.getBooleanValue("success")) throw new PackageFailure(502, message(settingJson));
        ensureAddress(token, zoneId, apex, origin);
        ensureAddress(token, zoneId, "*." + apex, origin);
    }

    public void deleteQuietly(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) return;
        try {
            String token = required(settings.getCloudflareApiToken(), "CLOUDFLARE_API_TOKEN");
            call("DELETE", API + "/zones/" + zoneId, null, token);
        } catch (RuntimeException ignored) {
            // Removing the zone is optional once nginx no longer serves the name.
        }
    }

    private void ensureAddress(String token, String zoneId, String name, String origin) {
        String listUrl = API + "/zones/" + zoneId + "/dns_records?type=A&name=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
        HttpPort.Reply listed = call("GET", listUrl, null, token);
        JSONObject json = json(listed.body());
        if (!listed.success() || !json.getBooleanValue("success")) throw new PackageFailure(502, message(json));
        JSONObject current = first(json.getJSONArray("result"));
        JSONObject body = new JSONObject();
        body.put("type", "A");
        body.put("name", name);
        body.put("content", origin);
        body.put("proxied", true);
        body.put("ttl", 1);
        if (current == null) {
            HttpPort.Reply created = call("POST", API + "/zones/" + zoneId + "/dns_records", body.toJSONString(), token);
            JSONObject createdJson = json(created.body());
            if (!created.success() || !createdJson.getBooleanValue("success")) throw new PackageFailure(502, message(createdJson));
            return;
        }
        if (origin.equals(current.getString("content")) && current.getBooleanValue("proxied")) return;
        HttpPort.Reply updated = call("PUT", API + "/zones/" + zoneId + "/dns_records/" + current.getString("id"), body.toJSONString(), token);
        JSONObject updatedJson = json(updated.body());
        if (!updated.success() || !updatedJson.getBooleanValue("success")) throw new PackageFailure(502, message(updatedJson));
    }

    private HttpPort.Reply call(String method, String url, String body, String token) {
        return http.exchange(method, url, body, Map.of(
                "Authorization", "Bearer " + token,
                "Content-Type", "application/json"));
    }

    private static Zone zone(JSONObject result) {
        if (result == null) throw new PackageFailure(502, "Cloudflare 未返回域名");
        List<String> servers = new ArrayList<>();
        JSONArray names = result.getJSONArray("name_servers");
        if (names != null) for (int i = 0; i < names.size(); i++) servers.add(names.getString(i));
        String joined = NameserverSet.join(servers);
        if (joined.isBlank() || result.getString("id") == null || result.getString("id").isBlank()) {
            throw new PackageFailure(502, "Cloudflare 未返回 NS");
        }
        return new Zone(result.getString("id"), joined);
    }

    private static JSONObject first(JSONArray rows) {
        return rows == null || rows.isEmpty() ? null : rows.getJSONObject(0);
    }

    private static JSONObject json(String body) {
        try { return body == null || body.isBlank() ? new JSONObject() : JSON.parseObject(body); }
        catch (RuntimeException failure) { return new JSONObject(); }
    }

    private static boolean alreadyExists(JSONObject json) {
        JSONArray errors = json.getJSONArray("errors");
        if (errors == null) return false;
        for (int i = 0; i < errors.size(); i++) {
            JSONObject error = errors.getJSONObject(i);
            if (error == null) continue;
            if (error.getIntValue("code") == 1061) return true;
            String text = error.getString("message");
            if (text != null && text.toLowerCase(java.util.Locale.ROOT).contains("already exists")) return true;
        }
        return false;
    }

    private static String message(JSONObject json) {
        JSONArray errors = json.getJSONArray("errors");
        if (errors != null && !errors.isEmpty() && errors.getJSONObject(0) != null) {
            String text = errors.getJSONObject(0).getString("message");
            if (text != null && !text.isBlank()) return text;
        }
        return "Cloudflare 请求失败";
    }

    static String required(String property, String envName) {
        if (property != null && !property.isBlank()) return property.trim();
        String env = System.getenv(envName);
        if (env == null || env.isBlank()) throw new PackageFailure(400, "未配置 Cloudflare");
        return env.trim();
    }

    public record Zone(String id, String nameservers) {}
}
