package org.jeecg.modules.packagemanagement.application.apps.support;

import com.alibaba.fastjson2.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Country identifiers shared by legacy protocols, risk checks and administrative writes. */
public final class PackageCountryRegions {
    private static final Map<String, String> ALIASES;
    static {
        try (InputStream input = PackageCountryRegions.class.getResourceAsStream("/package-country-regions.json")) {
            if (input == null) throw new IllegalStateException("Missing country region mapping");
            JSONObject data = JSONObject.parseObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            Map<String, String> aliases = new HashMap<>();
            data.getJSONObject("aliases").forEach((key, value) -> aliases.put(key, value.toString()));
            ALIASES = Collections.unmodifiableMap(aliases);
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
    private PackageCountryRegions() {}
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
