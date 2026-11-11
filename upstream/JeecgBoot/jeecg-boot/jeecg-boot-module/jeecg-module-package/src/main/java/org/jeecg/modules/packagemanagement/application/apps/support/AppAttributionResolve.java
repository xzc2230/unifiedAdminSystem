package org.jeecg.modules.packagemanagement.application.apps.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jeecg.modules.packagemanagement.application.apps.risk.app2.App2DevicePayload;
import org.jeecg.modules.packagemanagement.application.apps.risk.app2.App2PkgRiskRedisService;
import org.jeecg.modules.packagemanagement.application.apps.risk.app2.App2RiskConstants;
import org.jeecg.modules.packagemanagement.application.apps.risk.app2.App2RiskResult;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppManagerMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AppAttributionResolve {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final String TIP_OFF = "风控全关";
    private static final String TIP_US = "禁用美国及google、apple、amazon等运营商";
    private static final String TIP_AREA = "不在白名单";
    private static final String TIP_MISSING = "包不存在！！";
    private final AppManagerMapper apps;
    private final App2PkgRiskRedisService thresholds;

    public AppAttributionResolve(AppManagerMapper apps) {
        this(apps, null);
    }

    @Autowired
    public AppAttributionResolve(AppManagerMapper apps, App2PkgRiskRedisService thresholds) {
        this.apps = apps;
        this.thresholds = thresholds;
    }

    public Map<String, Object> tryResolve(JsonNode body) {
        return decide(body, Map.of()).payload();
    }

    public Outcome decide(JsonNode body, Map<String, String> geo) {
        if (body == null || !body.hasNonNull("applicationCode")) return Outcome.skip();
        String code = body.get("applicationCode").asText("").trim();
        if (code.isEmpty()) return Outcome.skip();
        AppManager app = apps.selectAppManagerByPackageName(code);
        if (app == null || app.getPackageCode() == null || app.getPackageCode().isBlank()) return Outcome.deny(code, TIP_MISSING);
        if (body.hasNonNull("applicationId")) {
            String applicationId = body.get("applicationId").asText("").trim();
            if (!applicationId.isEmpty() && !applicationId.equals(String.valueOf(app.getId()))) {
                throw new PackageFailure(400, "applicationId 与 applicationCode 不是同一个应用");
            }
        }
        if (body.hasNonNull("resolveTokenType")) {
            String type = body.get("resolveTokenType").asText("");
            if (!type.equals("ATTRIBUTION_TOKEN") && !type.equals("DEVICE_ID") && !type.equals("INSTALL_ID")) {
                throw new PackageFailure(400, "resolveTokenType 不支持");
            }
        }
        int status = app.getStatus() == null ? App2RiskConstants.STATUS_OFF : app.getStatus();
        if (status == App2RiskConstants.STATUS_OFF) return Outcome.deny(app.getPackageCode(), TIP_OFF);
        String geoTip = geoTip(app, geo);
        if (geoTip != null) return Outcome.deny(app.getPackageCode(), geoTip);
        String thresholdTip = thresholdTip(app, body);
        if (thresholdTip != null) return Outcome.deny(app.getPackageCode(), thresholdTip);
        return Outcome.matched(payload(body, app));
    }

    private Map<String, Object> payload(JsonNode body, AppManager app) {
        OffsetDateTime resolvedAt = OffsetDateTime.now(SHANGHAI).truncatedTo(ChronoUnit.SECONDS);
        String token = body.hasNonNull("resolveToken") ? body.get("resolveToken").asText() : "";
        if (!AppApplicationCodes.validToken(token)) token = AppApplicationCodes.nextToken();
        Map<String, Object> bodyOut = new LinkedHashMap<>();
        bodyOut.put("clickRef", body.hasNonNull("clickRef") ? body.get("clickRef").asText() : null);
        bodyOut.put("resolveToken", token);
        bodyOut.put("applicationCode", app.getPackageCode());
        bodyOut.put("promotionCode", null);
        bodyOut.put("resolutionState", "MATCHED");
        bodyOut.put("parameterSource", "REAL");
        bodyOut.put("destinationUrl", app.getUrl());
        bodyOut.put("resolvedAt", resolvedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        bodyOut.put("tokenExpiresAt", resolvedAt.plusSeconds(600).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        bodyOut.put("data", app.getMark());
        bodyOut.put("afLinkParams", nullParams("pid", "c", "af_c_id", "af_adset", "af_adset_id", "af_ad", "af_ad_id"));
        Map<String, Object> facebook = new LinkedHashMap<>();
        facebook.put("campaign_id", Integer.valueOf(AppApplicationCodes.digits(6)));
        facebook.put("campaign_name", "website_launch");
        facebook.put("adset_id", Integer.valueOf(AppApplicationCodes.digits(AppApplicationCodes.either(6, 7))));
        facebook.put("adset_name", null);
        facebook.put("ad_id", Integer.valueOf(AppApplicationCodes.digits(8)));
        facebook.put("ad_name", null);
        bodyOut.put("fbLinkParams", facebook);
        return bodyOut;
    }

    /** Same IP gate as the other client interfaces: US block, then area whitelist. */
    private static String geoTip(AppManager app, Map<String, String> geo) {
        Map<String, String> city = geo == null ? Map.of() : geo;
        String country = PackageCountryRegions.geoCode(city);
        if (app.getWhetherUs() != null && app.getWhetherUs() == 1 && ("true".equals(city.get("status")) || "us".equals(country))) {
            return TIP_US;
        }
        if (app.getWhetherRestrict() != null && app.getWhetherRestrict() == 1) {
            String areas = app.getAreas();
            if (areas == null || areas.isBlank() || !PackageCountryRegions.matches(areas, country)) return TIP_AREA;
        }
        return null;
    }

    /** Modes 1, 2 and 3 count gaid/aid against the package thresholds. Missing identifiers are not counted. */
    private String thresholdTip(AppManager app, JsonNode body) {
        if (thresholds == null || body == null) return null;
        String gaid = text(body, "gaid");
        String aid = text(body, "aid");
        if (gaid.isEmpty() && aid.isEmpty()) return null;
        App2DevicePayload device = new App2DevicePayload(text(body, "brand"), text(body, "cpuAbi"), text(body, "deviceName"), aid, gaid, "", "", null);
        App2RiskResult result = thresholds.evaluate(app, device);
        if (result == null || result.isPass()) return null;
        return result.getRejectTip() == null || result.getRejectTip().isBlank() ? "风控未通过" : result.getRejectTip();
    }

    private static String text(JsonNode body, String field) {
        if (!body.hasNonNull(field)) return "";
        String value = body.get(field).asText("");
        return value == null ? "" : value.trim();
    }

    public static final class Outcome {
        private final Map<String, Object> payload;
        private final String packageName;
        private final String denyTip;

        private Outcome(Map<String, Object> payload, String packageName, String denyTip) {
            this.payload = payload;
            this.packageName = packageName == null ? "" : packageName;
            this.denyTip = denyTip == null ? "" : denyTip;
        }

        static Outcome skip() { return new Outcome(null, "", ""); }
        static Outcome deny(String packageName, String tip) { return new Outcome(null, packageName, tip); }
        static Outcome matched(Map<String, Object> payload) { return new Outcome(payload, String.valueOf(payload.get("applicationCode")), ""); }

        public Map<String, Object> payload() { return payload; }
        public String packageName() { return packageName; }
        public String denyTip() { return denyTip; }
    }

    private static Map<String, Object> nullParams(String... keys) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : keys) values.put(key, null);
        return values;
    }
}
