package org.jeecg.modules.packagemanagement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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
        app.setApplicationCode("Ab12Cd34Ef56Gh78");
        app.setUrl("https://example.invalid/land");
        app.setMark("备注内容");
        when(apps.selectAppManagerByApplicationCode("Ab12Cd34Ef56Gh78")).thenReturn(app);
        var service = new AppAttributionResolve(apps);
        Map<String, Object> body = service.tryResolve(json.readTree("""
                {"applicationCode":"Ab12Cd34Ef56Gh78","applicationId":"9","clickRef":null,"resolveToken":"short","resolveTokenType":"ATTRIBUTION_TOKEN"}
                """));
        assertEquals("Ab12Cd34Ef56Gh78", body.get("applicationCode"));
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
        verify(apps).selectAppManagerByApplicationCode("Ab12Cd34Ef56Gh78");
    }

    @Test
    void differentApplicationIdIsRejected() throws Exception {
        AppManagerMapper apps = mock(AppManagerMapper.class);
        AppManager app = new AppManager();
        app.setId(9L);
        app.setApplicationCode("Ab12Cd34Ef56Gh78");
        when(apps.selectAppManagerByApplicationCode("Ab12Cd34Ef56Gh78")).thenReturn(app);
        var service = new AppAttributionResolve(apps);
        assertEquals(400, assertThrows(PackageFailure.class, () -> service.tryResolve(json.readTree(
                "{\"applicationCode\":\"Ab12Cd34Ef56Gh78\",\"applicationId\":\"8\"}"))).getCode());
    }
}
