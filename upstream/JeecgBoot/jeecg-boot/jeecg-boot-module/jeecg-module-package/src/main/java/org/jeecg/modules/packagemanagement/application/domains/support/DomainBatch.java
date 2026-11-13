package org.jeecg.modules.packagemanagement.application.domains.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;

/** Splits a pasted domain list on spaces, tabs, and line breaks. */
public final class DomainBatch {
    public static final int LIMIT = 50;

    private DomainBatch() {}

    public static List<String> tokens(String text) {
        if (text == null || text.isBlank()) throw new PackageFailure(400, "请填写域名");
        Map<String, String> unique = new LinkedHashMap<>();
        for (String token : text.trim().split("[\\s\\u00A0]+")) {
            if (token.isBlank()) continue;
            unique.putIfAbsent(token.toLowerCase(Locale.ROOT), token);
        }
        if (unique.isEmpty()) throw new PackageFailure(400, "请填写域名");
        if (unique.size() > LIMIT) throw new PackageFailure(400, "一次最多接入 50 个域名");
        return List.copyOf(unique.values());
    }
}
