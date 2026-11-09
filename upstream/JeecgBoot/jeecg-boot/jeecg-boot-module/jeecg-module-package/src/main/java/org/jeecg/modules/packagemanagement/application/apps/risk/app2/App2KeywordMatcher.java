package org.jeecg.modules.packagemanagement.application.apps.risk.app2;

import org.apache.commons.lang3.StringUtils;

/**
 * 黑名单关键字匹配（contains/equals/prefix/suffix）及 ASN 数字归一化。
 */
public final class App2KeywordMatcher {

    private App2KeywordMatcher() {}

    /** action=observe 时仅观测不拦截 */
    public static boolean isObserveAction(String action) {
        return "observe".equalsIgnoreCase(StringUtils.trimToEmpty(action));
    }

    /** 按 matchMode 在 haystack 中匹配 keyword */
    public static boolean matches(String haystack, String keyword, String matchMode, Integer caseSensitive) {
        if (StringUtils.isBlank(haystack) || StringUtils.isBlank(keyword)) { return false; }
        boolean cs = caseSensitive != null && caseSensitive == 1;
        String h = cs ? haystack : haystack.toLowerCase();
        String k = cs ? keyword : keyword.toLowerCase();
        String mode = StringUtils.isBlank(matchMode) ? "contains" : matchMode.trim().toLowerCase();
        switch (mode) {
            case "equals": case "eq": return h.equals(k);
            case "startswith": case "prefix": case "starts_with": return h.startsWith(k);
            case "endswith": case "suffix": case "ends_with": return h.endsWith(k);
            default: return h.contains(k);
        }
    }

    /** 去掉 AS 前缀，仅保留 ASN 数字 */
    public static String normalizeAsnDigits(String s) {
        if (s == null) { return ""; }
        String t = s.trim();
        if (t.isEmpty()) { return ""; }
        t = t.toUpperCase();
        if (t.startsWith("AS")) { t = t.substring(2); }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') { digits.append(c); }
        }
        return digits.toString();
    }
}
