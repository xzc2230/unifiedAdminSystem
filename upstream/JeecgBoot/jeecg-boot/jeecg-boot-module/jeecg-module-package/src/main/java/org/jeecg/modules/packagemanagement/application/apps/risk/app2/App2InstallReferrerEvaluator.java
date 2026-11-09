package org.jeecg.modules.packagemanagement.application.apps.risk.app2;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Service
public class App2InstallReferrerEvaluator {

    private static final String UTM_MEDIUM_ORGANIC = "organic";
    private static final String UTM_SOURCE_GOOGLE_PLAY = "google-play";
    private static final String UTM_MEDIUM_NOT_SET = "(not set)";
    private static final String UTM_SOURCE_NOT_SET = "(not set)";

    private static final String TIP_INVALID = "InstallReferrer 无效";
    private static final String TIP_NO_VERSION = "InstallReferrer 缺少 installVersion";
    private static final String TIP_INSTALL_VERSION_UNKNOWN = "InstallReferrer unknown 拒绝";

    private static final String TIP_INSTANT = "googlePlayInstantParam 为 true";
    private static final String TIP_FULL_UTM = "全开模式 utm 组合不在白名单";
    private static final String TIP_ATTR_UTM_MEDIUM = "归因模式存在 utm_medium";
    private static final String TIP_BLACKLIST = "InstallReferrer 命中包级黑名单";

    public App2RiskResult evaluate(int status, AppManager app, App2DevicePayload device) {
        JSONObject ref = device.getInstallReferrer();
        if (ref == null || ref.isEmpty()) { return App2RiskResult.reject(TIP_INVALID); }
        if (StringUtils.isBlank(ref.getString("installVersion"))) { return App2RiskResult.reject(TIP_NO_VERSION); }
        if ("unknown".equalsIgnoreCase(ref.getString("installVersion"))) { return App2RiskResult.reject(TIP_INSTALL_VERSION_UNKNOWN); }
        if (isGooglePlayInstant(ref)) { return App2RiskResult.reject(TIP_INSTANT); }
        JSONObject utm = extractUtmObject(ref);
        ref.put("installReferrer", utm);

        if (matchesPkgBlacklist(app.getReferrerBlacklistJson(), ref)) { return App2RiskResult.reject(TIP_BLACKLIST); }
        if (status == App2RiskConstants.STATUS_FULL && violatesFullModeUtm(utm)) {
            return App2RiskResult.reject(TIP_FULL_UTM);
        }
        if (status == App2RiskConstants.STATUS_ATTR && hasUtmMediumValue(utm)) {
            return App2RiskResult.reject(TIP_ATTR_UTM_MEDIUM);
        }
        return App2RiskResult.ok();
    }

    /** googlePlayInstantParam 存在且为 true 时拒绝 */
    private static boolean isGooglePlayInstant(JSONObject ref) {
        if (!ref.containsKey("googlePlayInstantParam")) { return false; }
        Object v = ref.get("googlePlayInstantParam");
        if (v instanceof Boolean) { return Boolean.TRUE.equals(v); }
        if (v instanceof String) { return "true".equalsIgnoreCase(((String) v).trim()); }
        return ref.getBooleanValue("googlePlayInstantParam");
    }

    /**
     * 全开：无 utm_medium 放行； 有则仅允许 organic+google-play 或 (not set)+(not set)。
     */
    private static boolean violatesFullModeUtm(JSONObject utm) {
        if (utm == null || !utm.containsKey("utm_medium")) { return false; }
        String medium = StringUtils.trimToEmpty(utm.getString("utm_medium"));
        if (StringUtils.isBlank(medium)) { return false; }
        String source = StringUtils.trimToEmpty(utm.getString("utm_source"));
        return !isFullModeAllowedUtmPair(medium, source);
    }

    private static boolean isFullModeAllowedUtmPair(String medium, String source) {
        if (UTM_MEDIUM_ORGANIC.equals(medium) && UTM_SOURCE_GOOGLE_PLAY.equals(source)) { return true; }
        return UTM_MEDIUM_NOT_SET.equals(medium) && UTM_SOURCE_NOT_SET.equals(source);
    }

    /** 归因：installReferrer 内 utm_medium 有值则拒绝 */
    private static boolean hasUtmMediumValue(JSONObject utm) {
        if (utm == null || !utm.containsKey("utm_medium")) { return false; }
        return StringUtils.isNotBlank(utm.getString("utm_medium"));
    }

    private static JSONObject extractUtmObject(JSONObject ref) {
        Object inner = ref.get("installReferrer");
        if (inner instanceof JSONObject) { return (JSONObject) inner; }
        if (inner instanceof String) {
            String s = ((String) inner).trim();
            if (s.startsWith("{")) {
                try { return JSON.parseObject(s); } catch (Exception ignored) { return new JSONObject(); }
            }
            try {
                parseQueryStringToJson(s);
            }catch (Exception ignored) {
                ignored.printStackTrace();
                return new JSONObject();
            }
        }
        return new JSONObject();
    }

    private static JSONObject parseQueryStringToJson(String qs) {
        JSONObject jo = new JSONObject();
        if (StringUtils.isBlank(qs)) { return jo; }
        String query = qs.startsWith("?") ? qs.substring(1) : qs;
        for (String part : query.split("&")) {
            if (part.isEmpty()) { continue; }
            int i = part.indexOf('=');
            String k;
            String v;
            if (i < 0) {
                continue;
            } else {
                k = part.substring(0, i);
                v = i < part.length() - 1 ? part.substring(i + 1) : "";
            }
            try { v = URLDecoder.decode(v, StandardCharsets.UTF_8.name()); } catch (Exception ignored) { }
            if (StringUtils.isNotBlank(k)) { jo.put(k, v); }
        }
        return jo;
    }

    /**
     * 包级 Referrer 黑名单：模板为 InstallReferrer 子集，仅匹配 key 存在，不校验 value。
     */
    private static boolean matchesPkgBlacklist(String templateJson, JSONObject actual) {
        if (StringUtils.isBlank(templateJson)) { return false; }
        try {
            JSONObject template = JSON.parseObject(templateJson);
            if (template == null || template.isEmpty()) { return false; }
            return subtreeKeyMatches(template, actual);
        } catch (Exception e) { return false; }
    }

    private static boolean subtreeKeyMatches(JSONObject rule, JSONObject actual) {
        if (actual == null) { return false; }
        for (String key : rule.keySet()) {
            Object ruleVal = rule.get(key);
            Object actualVal = actual.get(key);
            if (actualVal == null) { return false; }
            if (ruleVal instanceof JSONObject) {
                if (!(actualVal instanceof JSONObject)) { return false; }
                if (!subtreeKeyMatches((JSONObject) ruleVal, (JSONObject) actualVal)) { return false; }
            }
        }
        return true;
    }
}
