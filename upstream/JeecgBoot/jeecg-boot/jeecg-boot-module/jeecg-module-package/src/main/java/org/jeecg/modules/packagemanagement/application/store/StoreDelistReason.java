package org.jeecg.modules.packagemanagement.application.store;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Short, durable explanation stored when a store probe marks a package offline. */
public final class StoreDelistReason {
    static final int MAX = 480;
    private static final String PLAY_NOT_FOUND = "We're sorry, the requested URL was not found on this server.";
    private static final Pattern TITLE = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private StoreDelistReason() {}

    public static String http(int status, String body) {
        String detail = detail(body);
        return limit(detail.isEmpty() ? "HTTP " + status : "HTTP " + status + " " + detail);
    }

    public static String itunesMiss(String region) {
        String country = region == null || region.isBlank() ? "us" : region.trim();
        return "iTunes lookup resultCount=0, country=" + country;
    }

    private static String detail(String body) {
        if (body == null || body.isBlank()) return "";
        if (body.contains(PLAY_NOT_FOUND)) return PLAY_NOT_FOUND;
        Matcher title = TITLE.matcher(body);
        if (!title.find()) return "";
        return clean(title.group(1));
    }

    private static String clean(String value) {
        return value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private static String limit(String value) {
        return value.length() <= MAX ? value : value.substring(0, MAX);
    }
}
