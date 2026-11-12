package org.jeecg.modules.packagemanagement.application.domains.support;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Registrable apex only. A typed subdomain such as www.example.com is rejected. */
public final class RegistrableApex {
    private static final Pattern LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private static final Set<String> COMPOUND_SUFFIXES = Set.of(
            "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "net.uk",
            "com.cn", "net.cn", "org.cn", "gov.cn", "edu.cn",
            "com.au", "net.au", "org.au", "edu.au", "gov.au",
            "co.jp", "or.jp", "ne.jp", "ac.jp", "com.hk", "com.tw", "com.sg",
            "com.br", "com.mx", "co.kr", "co.nz", "co.za", "com.tr", "com.ar",
            "com.my", "co.in", "com.co", "com.vn", "com.ph", "com.id", "co.id",
            "co.il", "com.pk", "com.ng", "com.eg", "com.sa", "com.ae", "com.ua",
            "co.th", "com.pe", "com.bd");

    private RegistrableApex() {}

    public static String normalize(String raw) {
        if (raw == null) throw new IllegalArgumentException("请填写一级域名");
        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(".")) name = name.substring(0, name.length() - 1);
        if (name.isBlank() || name.length() > 253 || name.indexOf(' ') >= 0 || name.indexOf('/') >= 0
                || name.indexOf(':') >= 0 || name.indexOf('@') >= 0 || name.indexOf('*') >= 0) {
            throw new IllegalArgumentException("请填写一级域名");
        }
        String[] labels = name.split("\\.", -1);
        int expected = COMPOUND_SUFFIXES.contains(suffix(labels)) ? 3 : 2;
        if (labels.length != expected) throw new IllegalArgumentException("请填写注册一级域名，不要填写子域名");
        for (String label : labels) if (!LABEL.matcher(label).matches()) throw new IllegalArgumentException("请填写一级域名");
        return name;
    }

    private static String suffix(String[] labels) {
        if (labels.length < 2) return "";
        return labels[labels.length - 2] + "." + labels[labels.length - 1];
    }
}
