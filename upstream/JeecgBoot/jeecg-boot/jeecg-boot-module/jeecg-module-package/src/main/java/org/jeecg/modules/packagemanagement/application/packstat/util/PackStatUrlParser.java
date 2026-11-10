package org.jeecg.modules.packagemanagement.application.packstat.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java 8 parser for App Store / Google Play URLs. Hint only; detection still GETs the saved store_url.
 */
public final class PackStatUrlParser
{
    private static final String DEFAULT_REGION = "us";

    private static final Pattern APP_STORE_URL_PATTERN = Pattern.compile(
            "^(https?://apps\\.apple\\.com/)([a-z]{2})(/app/)([^/]+)(/id(\\d+))(\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern APP_STORE_NO_REGION_PATTERN = Pattern.compile(
            "^(https?://apps\\.apple\\.com/)(/app/)([^/]+)(/id(\\d+))(\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GOOGLE_PLAY_HOST_PATTERN = Pattern.compile(
            "^https?://play\\.google\\.com/store/apps/details(\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile(
            "^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$");

    private PackStatUrlParser()
    {
    }

    public static Parsed tryParse(String input)
    {
        if (input == null)
        {
            return null;
        }
        String storeUrl = input.trim();
        if (storeUrl.isEmpty())
        {
            return null;
        }
        try
        {
            Parsed googlePlay = tryParseGooglePlay(storeUrl);
            if (googlePlay != null)
            {
                return googlePlay;
            }
            return parseAppStore(storeUrl);
        }
        catch (IllegalArgumentException ex)
        {
            return null;
        }
    }

    private static Parsed tryParseGooglePlay(String storeUrl)
    {
        if (!GOOGLE_PLAY_HOST_PATTERN.matcher(storeUrl).matches())
        {
            return null;
        }
        String appId = extractQueryParam(storeUrl, "id");
        if (appId == null || appId.trim().isEmpty())
        {
            throw new IllegalArgumentException("Google Play 链接缺少 id 参数");
        }
        appId = appId.trim();
        if (!PACKAGE_NAME_PATTERN.matcher(appId).matches())
        {
            throw new IllegalArgumentException("Google Play 包名格式不正确: " + appId);
        }
        String canonicalUrl = "https://play.google.com/store/apps/details?id=" + appId;
        return new Parsed(canonicalUrl, "", appId, "android");
    }

    private static Parsed parseAppStore(String storeUrl)
    {
        Matcher matcher = APP_STORE_URL_PATTERN.matcher(storeUrl);
        if (matcher.matches())
        {
            return new Parsed(storeUrl, matcher.group(2).toLowerCase(), matcher.group(6), "ios");
        }
        Matcher noRegionMatcher = APP_STORE_NO_REGION_PATTERN.matcher(storeUrl);
        if (noRegionMatcher.matches())
        {
            String rewritten = noRegionMatcher.group(1) + DEFAULT_REGION + noRegionMatcher.group(2)
                    + noRegionMatcher.group(3) + noRegionMatcher.group(4)
                    + (noRegionMatcher.group(6) != null ? noRegionMatcher.group(6) : "");
            return new Parsed(rewritten, DEFAULT_REGION, noRegionMatcher.group(5), "ios");
        }
        throw new IllegalArgumentException("应用商店链接格式不正确");
    }

    private static String extractQueryParam(String url, String name)
    {
        try
        {
            URI uri = new URI(url);
            String query = uri.getRawQuery();
            if (query == null || query.trim().isEmpty())
            {
                return null;
            }
            String[] pairs = query.split("&");
            for (int i = 0; i < pairs.length; i++)
            {
                String pair = pairs[i];
                int eq = pair.indexOf('=');
                if (eq <= 0)
                {
                    continue;
                }
                if (name.equals(pair.substring(0, eq)))
                {
                    return pair.substring(eq + 1);
                }
            }
            return null;
        }
        catch (URISyntaxException ex)
        {
            throw new IllegalArgumentException("Google Play 链接格式不正确");
        }
    }

    public static final class Parsed
    {
        private final String storeUrl;
        private final String region;
        private final String appId;
        private final String platform;

        public Parsed(String storeUrl, String region, String appId, String platform)
        {
            this.storeUrl = storeUrl;
            this.region = region;
            this.appId = appId;
            this.platform = platform;
        }

        public String getStoreUrl()
        {
            return storeUrl;
        }

        public String getRegion()
        {
            return region;
        }

        public String getAppId()
        {
            return appId;
        }

        public String getPlatform()
        {
            return platform;
        }
    }
}
