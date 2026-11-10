package org.jeecg.modules.packagemanagement.application.apps.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppManagerMapper;
import org.springframework.stereotype.Component;

@Component
public class AppAttributionResolve {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final AppManagerMapper apps;

    public AppAttributionResolve(AppManagerMapper apps) {
        this.apps = apps;
    }

    public Map<String, Object> tryResolve(JsonNode body) {
        if (body == null || !body.hasNonNull("applicationCode")) return null;
        String code = body.get("applicationCode").asText("").trim();
        if (code.isEmpty()) return null;
        AppManager app = apps.selectAppManagerByPackageName(code);
        if (app == null || app.getPackageCode() == null || app.getPackageCode().isBlank()) return null;
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

    private static Map<String, Object> nullParams(String... keys) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : keys) values.put(key, null);
        return values;
    }
}
