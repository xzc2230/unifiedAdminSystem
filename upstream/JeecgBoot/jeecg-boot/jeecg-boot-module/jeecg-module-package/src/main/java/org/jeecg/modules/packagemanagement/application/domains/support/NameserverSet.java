package org.jeecg.modules.packagemanagement.application.domains.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Compares assigned Cloudflare nameservers with the public NS set. */
public final class NameserverSet {
    private NameserverSet() {}

    public static String join(Iterable<String> names) {
        Set<String> unique = new TreeSet<>();
        for (String name : names) {
            String normalized = normalize(name);
            if (!normalized.isEmpty()) unique.add(normalized);
        }
        return String.join(",", unique);
    }

    public static Set<String> parse(String csv) {
        Set<String> names = new TreeSet<>();
        if (csv == null || csv.isBlank()) return names;
        for (String part : csv.split(",")) {
            String normalized = normalize(part);
            if (!normalized.isEmpty()) names.add(normalized);
        }
        return names;
    }

    public static Set<String> fromDnsJson(String body) {
        Set<String> names = new TreeSet<>();
        if (body == null || body.isBlank()) return names;
        JSONObject json = JSON.parseObject(body);
        collect(json.getJSONArray("Answer"), names);
        collect(json.getJSONArray("Authority"), names);
        return names;
    }

    public static boolean same(String assigned, Set<String> observed) {
        Set<String> expected = parse(assigned);
        return !expected.isEmpty() && expected.equals(observed);
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String name = value.trim().toLowerCase(Locale.ROOT);
        while (name.endsWith(".")) name = name.substring(0, name.length() - 1);
        return name;
    }

    private static void collect(JSONArray rows, Set<String> names) {
        if (rows == null) return;
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (row == null || row.getIntValue("type") != 2) continue;
            String name = normalize(row.getString("data"));
            if (!name.isEmpty()) names.add(name);
        }
    }
}
