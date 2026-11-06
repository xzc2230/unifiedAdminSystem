package org.jeecg.modules.siteplatform;

import java.net.IDN;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

public final class ResourceRules {
    private ResourceRules() {}

    public static String hostname(String input) {
        if (input == null || input.isBlank() || !input.equals(input.trim())) throw invalid("请输入有效主机名");
        String name = input.endsWith(".") ? input.substring(0, input.length() - 1) : input;
        // Require DNS names, not URLs, IPs, wildcard records or private local suffixes.
        try { name = IDN.toASCII(name, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT); }
        catch (IllegalArgumentException e) { throw invalid("域名格式无效"); }
        if (name.length() > 253 || name.indexOf('.') < 1 || name.matches("[0-9.]+") ||
                name.endsWith(".local") || name.endsWith(".localhost") || name.endsWith(".internal")) throw invalid("仅支持公网 DNS 主机名");
        for (String label : name.split("\\.", -1)) {
            if (!label.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) throw invalid("域名标签无效");
        }
        return name;
    }

    public static String targetUrl(String input) {
        try {
            URI uri = new URI(input);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw invalid("目标须为无凭证、无片段的 HTTP/HTTPS 域名地址");
            }
            String host = hostname(uri.getHost());
            String value = scheme + "://" + host + (uri.getPort() == -1 ? "" : ":" + uri.getPort()) +
                    (uri.getRawPath() == null ? "" : uri.getRawPath()) + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            if (value.length() > 2000) throw invalid("目标地址过长");
            return value;
        } catch (ResourceFailure e) { throw e; }
        catch (Exception e) { throw invalid("目标地址格式无效"); }
    }

    public static String environment(String value) {
        if (value == null || !Set.of("dev", "test", "staging", "prod").contains(value)) throw invalid("环境无效");
        return value;
    }

    public static String role(String value) {
        if (value == null || !Set.of("ADMIN", "EDITOR", "VIEWER").contains(value)) throw invalid("站点角色无效");
        return value;
    }

    public static ResourceFailure invalid(String message) { return new ResourceFailure(400, message); }
}
