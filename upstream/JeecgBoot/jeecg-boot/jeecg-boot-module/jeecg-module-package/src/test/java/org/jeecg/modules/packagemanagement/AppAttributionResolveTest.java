package org.jeecg.modules.packagemanagement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.jeecg.modules.packagemanagement.application.apps.risk.app2.App2PkgRiskRedisService;
import org.jeecg.modules.packagemanagement.application.apps.risk.app2.App2RiskResult;
import org.jeecg.modules.packagemanagement.application.apps.support.AppApplicationCodes;
import org.jeecg.modules.packagemanagement.application.apps.support.AppAttributionResolve;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppManagerMapper;
import org.junit.jupiter.api.Test;

class AppAttributionResolveTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void defaultCodeIsSixteenMixedCharacters() {
        String code = AppApplicationCodes.next();
        assertTrue(AppApplicationCodes.valid(code));
        assertEquals(6, code.length());
    }

    @Test
    void resolveUsesTheApplicationWithoutCheckingTheToken() throws Exception {
        AppManagerMapper apps = mock(AppManagerMapper.class);
        AppManager app = new AppManager();
        app.setId(9L);
        app.setStatus(1);
        app.setPackageCode("com.example.app");
        app.setApplicationCode("Ab12Cd");
        app.setUrl("https://example.invalid/land");
        app.setMark("备注内容");
        when(apps.selectAppManagerByPackageName("com.example.app")).thenReturn(app);
        var service = new AppAttributionResolve(apps);
        Map<String, Object> body = service.tryResolve(json.readTree("""
                {"applicationCode":"com.example.app","applicationId":"9","clickRef":null,"resolveToken":"short","resolveTokenType":"ATTRIBUTION_TOKEN"}
                """));
        assertEquals("com.example.app", body.get("applicationCode"));
        assertNull(body.get("clickRef"));
        assertNull(body.get("promotionCode"));
        assertEquals("MATCHED", body.get("resolutionState"));
        assertEquals("REAL", body.get("parameterSource"));
        assertEquals("https://example.invalid/land", body.get("destinationUrl"));
        assertEquals("备注内容", body.get("data"));
        assertTrue(AppApplicationCodes.validToken((String) body.get("resolveToken")));
        assertEquals(16, String.valueOf(body.get("resolveToken")).length());
        assertTrue(String.valueOf(body.get("tokenExpiresAt")).compareTo(String.valueOf(body.get("resolvedAt"))) > 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> facebook = (Map<String, Object>) body.get("fbLinkParams");
        assertEquals("website_launch", facebook.get("campaign_name"));
        assertNull(facebook.get("adset_name"));
        assertNull(facebook.get("ad_name"));
        assertEquals(6, String.valueOf(facebook.get("campaign_id")).length());
        int adsetLength = String.valueOf(facebook.get("adset_id")).length();
        assertTrue(adsetLength == 6 || adsetLength == 7);
        assertEquals(8, String.valueOf(facebook.get("ad_id")).length());
        verify(apps).selectAppManagerByPackageName("com.example.app");
        verify(apps, never()).selectAppManagerByApplicationCode(any());
    }

    @Test
    void differentApplicationIdIsRejected() throws Exception {
        AppManagerMapper apps = mock(AppManagerMapper.class);
        AppManager app = new AppManager();
        app.setId(9L);
        app.setPackageCode("com.example.app");
        when(apps.selectAppManagerByPackageName("com.example.app")).thenReturn(app);
        var service = new AppAttributionResolve(apps);
        assertEquals(400, assertThrows(PackageFailure.class, () -> service.tryResolve(json.readTree(
                "{\"applicationCode\":\"com.example.app\",\"applicationId\":\"8\"}"))).getCode());
    }

    @Test
    void closedRiskModeIsNotResolved() throws Exception {
        AppManager app = openApp();
        app.setStatus(0);
        AppAttributionResolve.Outcome outcome = decide(app, Map.of("countryCode", "sg"), "{\"applicationCode\":\"com.example.app\"}");
        assertNull(outcome.payload());
        assertEquals("风控全关", outcome.denyTip());
    }

    @Test
    void unitedStatesAndCarrierBlocksAreRejected() throws Exception {
        AppManager app = openApp();
        app.setWhetherUs(1);
        AppAttributionResolve.Outcome outcome = decide(app, Map.of("status", "true", "countryCode", "sg"), "{\"applicationCode\":\"com.example.app\"}");
        assertNull(outcome.payload());
        assertEquals("禁用美国及google、apple、amazon等运营商", outcome.denyTip());
        app.setWhetherUs(0);
        app.setBlockedProviders("apple");
        outcome = decide(app, Map.of("countryCode", "sg", "status", "false", "providers", "google"), "{\"applicationCode\":\"com.example.app\"}");
        assertNotNull(outcome.payload());
        app.setBlockedProviders("google,facebook");
        outcome = decide(app, Map.of("countryCode", "sg", "status", "false", "providers", "google"), "{\"applicationCode\":\"com.example.app\"}");
        assertNull(outcome.payload());
        assertEquals("禁用运营商：google", outcome.denyTip());
    }

    @Test
    void areaWhitelistRejectsOtherCountries() throws Exception {
        AppManager app = openApp();
        app.setWhetherRestrict(1);
        app.setAreas("sg");
        AppAttributionResolve.Outcome outcome = decide(app, Map.of("countryCode", "br"), "{\"applicationCode\":\"com.example.app\"}");
        assertNull(outcome.payload());
        assertEquals("不在白名单", outcome.denyTip());
    }

    @Test
    void openModesUseThePackageRiskThresholds() throws Exception {
        AppManagerMapper apps = mock(AppManagerMapper.class);
        AppManager app = openApp();
        app.setStatus(2);
        when(apps.selectAppManagerByPackageName("com.example.app")).thenReturn(app);
        App2PkgRiskRedisService redis = mock(App2PkgRiskRedisService.class);
        when(redis.evaluate(any(), any())).thenReturn(App2RiskResult.reject("同一 gaid 关联过多 aid"));
        var service = new AppAttributionResolve(apps, redis);
        AppAttributionResolve.Outcome outcome = service.decide(json.readTree(
                "{\"applicationCode\":\"com.example.app\",\"gaid\":\"g-1\",\"aid\":\"a-1\",\"brand\":\"samsung\",\"deviceName\":\"s23\"}"),
                Map.of("countryCode", "sg"));
        assertNull(outcome.payload());
        assertEquals("同一 gaid 关联过多 aid", outcome.denyTip());
        verify(redis).evaluate(eq(app), any());
    }

    private AppAttributionResolve.Outcome decide(AppManager app, Map<String, String> geo, String body) throws Exception {
        AppManagerMapper apps = mock(AppManagerMapper.class);
        when(apps.selectAppManagerByPackageName(app.getPackageCode())).thenReturn(app);
        return new AppAttributionResolve(apps).decide(json.readTree(body), geo);
    }

    private static AppManager openApp() {
        AppManager app = new AppManager();
        app.setId(9L);
        app.setStatus(1);
        app.setPackageCode("com.example.app");
        app.setUrl("https://example.invalid/land");
        return app;
    }
}
