package org.jeecg.modules.packagemanagement.application.apps.support;

import com.alibaba.fastjson2.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Country identifiers shared by legacy protocols, risk checks and administrative writes. */
public final class PackageCountryRegions {
    private static final Map<String, String> ALIASES;
    private static final Map<String, String> LABELS;
    static {
        try (InputStream input = PackageCountryRegions.class.getResourceAsStream("/package-country-regions.json")) {
            if (input == null) throw new IllegalStateException("Missing country region mapping");
            JSONObject data = JSONObject.parseObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            Map<String, String> aliases = new HashMap<>();
            data.getJSONObject("aliases").forEach((key, value) -> aliases.put(key, value.toString()));
            Map<String, String> labels = new HashMap<>();
            data.getJSONArray("regions").forEach(item -> {
                JSONObject row = (JSONObject) item;
                labels.put(row.getString("value"), row.getString("label"));
            });
            ALIASES = Collections.unmodifiableMap(aliases);
            LABELS = Collections.unmodifiableMap(labels);
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
    private PackageCountryRegions() {}
    /** Show a stored `(countryCode)ip` value with the shared Chinese country name. */
    public static String displayReqIp(String reqIp) {
        if (reqIp == null || reqIp.length() < 4 || reqIp.charAt(0) != '(') return reqIp;
        int end = reqIp.indexOf(')');
        if (end != 3) return reqIp;
        String code = reqIp.substring(1, end).toLowerCase(Locale.ROOT);
        String label = LABELS.get(code);
        if (label == null) return reqIp;
        return "(" + label + ")" + reqIp.substring(end + 1);
    }
    public static String code(String value) {
        if (value == null) return "";
        String raw = value.trim();
        if (ALIASES.containsKey(raw)) return ALIASES.get(raw);
        return raw.matches("(?i)[a-z]{2}") ? raw.toLowerCase(Locale.ROOT) : "";
    }
    public static String normalizeAreas(String areas) {
        if (areas == null) return null;
        Set<String> values = new LinkedHashSet<>();
        for (String raw : areas.split(",")) {
            String code = code(raw);
            // Preserve unknown historical entries for review; never let them match a visitor.
            if (!raw.isBlank()) values.add(code.isEmpty() ? raw.trim() : code);
        }
        return String.join(",", values);
    }
    public static boolean matches(String areas, String country) {
        String code = code(country);
        return !code.isEmpty() && areas != null && Arrays.stream(areas.split(",")).anyMatch(area -> code.equals(code(area)));
    }
    public static String geoCode(Map<String, String> geo) {
        if (geo == null) return "";
        String code = code(geo.get("countryCode"));
        return code.isEmpty() ? code(geo.get("country")) : code;
    }
    /** Old scalar Geo lookup treats blocked providers as US; preserve that policy. */
    public static String accessCode(Map<String, String> geo) {
        return geo != null && "true".equals(geo.get("status")) ? "us" : geoCode(geo);
    }
    public static String regionalLink(JSONObject links, String country, String fallback) {
        String code = code(country);
        if (code.isEmpty()) return fallback;
        if (links.containsKey(code)) return links.getString(code);
        for (String key : links.keySet()) if (code.equals(code(key))) return links.getString(key);
        return fallback;
    }
}
