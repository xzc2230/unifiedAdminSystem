package org.jeecg.modules.packagemanagement.application.apps.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Google, Apple, Meta, Facebook and TikTok provider gates chosen per application. */
public final class BlockedProviders {
    public static final List<String> CODES = List.of("google", "apple", "meta", "facebook", "tiktok");

    private BlockedProviders() {}

    /** Providers visible on an IP lookup. Apple matches AS. The others match AS, org or ISP. TikTok also matches ByteDance. */
    public static String detect(String as, String org, String isp) {
        String network = lower(as);
        String organization = lower(org);
        String carrier = lower(isp);
        String text = network + "\n" + organization + "\n" + carrier;
        List<String> found = new ArrayList<>();
        if (network.contains("google") || organization.contains("google") || carrier.contains("google")) found.add("google");
        if (network.contains("apple")) found.add("apple");
        if (text.contains("meta platforms") || text.matches("(?s).*\\bmeta\\b.*")) found.add("meta");
        if (text.contains("facebook")) found.add("facebook");
        if (text.contains("tiktok") || text.contains("bytedance")) found.add("tiktok");
        return String.join(",", found);
    }

    /** Keep only known providers, in selection order. Null stays null so an absent edit is not stored. */
    public static String normalize(String selected) {
        if (selected == null) return null;
        Set<String> kept = new LinkedHashSet<>();
        for (String code : CODES) if (contains(selected, code)) kept.add(code);
        return String.join(",", kept);
    }

    /** Selected providers that this visit matches, in the same order. Empty when the application selected none. */
    public static String hit(String selected, String detected) {
        String chosen = normalize(selected);
        String seen = normalize(detected);
        if (chosen == null || chosen.isEmpty() || seen == null || seen.isEmpty()) return "";
        List<String> matched = new ArrayList<>();
        for (String code : chosen.split(",")) if (contains(seen, code)) matched.add(code);
        return String.join(",", matched);
    }

    private static boolean contains(String csv, String code) {
        for (String part : csv.split(",")) if (code.equals(part.trim().toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
