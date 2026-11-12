package org.jeecg.modules.packagemanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;
import org.jeecg.modules.packagemanagement.application.apps.support.AppAttributionResolve;
import org.jeecg.modules.packagemanagement.application.port.AppLogPort;
import org.jeecg.modules.packagemanagement.application.port.GeoPort;
import org.jeecg.modules.packagemanagement.controller.publicapi.AppAttributionResolveFilter;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.domain.applog.AppLog;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppManagerMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AppAttributionResolveLogTest {
    @Test
    void matchedCallIsStoredLikeTheOtherClientLogs() throws Exception {
        AppLogPort logs = mock(AppLogPort.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = post(filter(app("com.example.app"), logs), chain, "{\"applicationCode\":\"com.example.app\"}");
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"resolutionState\":\"MATCHED\""));
        AppLog row = saved(logs);
        assertEquals("com.example.app", row.getPackageName());
        assertEquals("POST", row.getReqMethod());
        assertTrue(row.getReqPath().endsWith("/api/v1/attribution/resolve"));
        assertEquals("client.example", row.getReqHost());
        assertEquals("(sg)1.2.3.4", row.getReqIp());
        assertTrue(row.getReqParams().contains("com.example.app"));
        assertTrue(row.getResultMsg().contains("MATCHED"));
        assertTrue(row.getErrorMsg() == null || row.getErrorMsg().isBlank());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void closedRiskModeIsLoggedAndDoesNotReturnTheLink() throws Exception {
        AppLogPort logs = mock(AppLogPort.class);
        FilterChain chain = mock(FilterChain.class);
        AppManager app = app("com.example.app");
        app.setStatus(0);
        MockHttpServletResponse response = post(filter(app, logs), chain, "{\"applicationCode\":\"com.example.app\"}");
        assertFalse(response.getContentAsString().contains("MATCHED"));
        assertEquals("风控全关", saved(logs).getErrorMsg());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void businessErrorIsStoredWithThePackageName() throws Exception {
        AppLogPort logs = mock(AppLogPort.class);
        MockHttpServletResponse response = post(filter(app("com.example.app"), logs), mock(FilterChain.class),
                "{\"applicationCode\":\"com.example.app\",\"applicationId\":\"8\"}");
        assertEquals(400, response.getStatus());
        AppLog row = saved(logs);
        assertEquals("com.example.app", row.getPackageName());
        assertTrue(row.getErrorMsg().contains("不是同一个应用"));
        assertTrue(row.getResultMsg().contains("不是同一个应用"));
    }

    @Test
    void missingPackageIsStoredBeforeTheRequestContinues() throws Exception {
        AppLogPort logs = mock(AppLogPort.class);
        FilterChain chain = mock(FilterChain.class);
        post(filter(null, logs), chain, "{\"applicationCode\":\"com.missing.app\"}");
        AppLog row = saved(logs);
        assertEquals("com.missing.app", row.getPackageName());
        assertEquals("包不存在！！", row.getErrorMsg());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void blankPackageNameIsNotStored() throws Exception {
        AppLogPort logs = mock(AppLogPort.class);
        FilterChain chain = mock(FilterChain.class);
        post(filter(null, logs), chain, "{}");
        verify(logs, never()).save(any());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void fiveSegmentAliasUsesTheSameResolveAndStoresItsPath() throws Exception {
        AppLogPort logs = mock(AppLogPort.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = post(filter(app("com.example.app"), logs), chain, "/package-legacy/a/b/c/d/e", "{\"applicationCode\":\"com.example.app\"}");
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"resolutionState\":\"MATCHED\""));
        assertTrue(saved(logs).getReqPath().endsWith("/package-legacy/a/b/c/d/e"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void fourSegmentAndBannerListStayOutsideResolve() throws Exception {
        AppLogPort logs = mock(AppLogPort.class);
        FilterChain chain = mock(FilterChain.class);
        post(filter(app("com.example.app"), logs), chain, "/package-legacy/a/b/c/d", "{\"applicationCode\":\"com.example.app\"}");
        post(filter(app("com.example.app"), logs), chain, "/package-legacy/v2/api/banner/list/abc", "{\"applicationCode\":\"com.example.app\"}");
        verify(logs, never()).save(any());
        verify(chain, org.mockito.Mockito.times(2)).doFilter(any(), any());
    }

    @Test
    void storageFailureDoesNotChangeTheClientResponse() throws Exception {
        AppLogPort logs = mock(AppLogPort.class);
        doThrow(new IllegalStateException("down")).when(logs).save(any());
        MockHttpServletResponse response = post(filter(app("com.example.app"), logs), mock(FilterChain.class), "{\"applicationCode\":\"com.example.app\"}");
        assertEquals(200, response.getStatus());
        assertFalse(response.getContentAsString().isBlank());
    }

    private static AppAttributionResolveFilter filter(AppManager app, AppLogPort logs) {
        AppManagerMapper apps = mock(AppManagerMapper.class);
        if (app != null) when(apps.selectAppManagerByPackageName(app.getPackageCode())).thenReturn(app);
        GeoPort geo = mock(GeoPort.class);
        when(geo.getIpAddress(any())).thenReturn("1.2.3.4");
        when(geo.getCountryAndCountryCodeByIP("1.2.3.4")).thenReturn(Map.of("countryCode", "sg"));
        return new AppAttributionResolveFilter(new AppAttributionResolve(apps), logs, geo, Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    private static AppManager app(String packageCode) {
        AppManager app = new AppManager();
        app.setId(9L);
        app.setStatus(1);
        app.setPackageCode(packageCode);
        app.setUrl("https://example.invalid/land");
        return app;
    }

    private static MockHttpServletResponse post(AppAttributionResolveFilter filter, FilterChain chain, String body) throws Exception {
        return post(filter, chain, "/api/v1/attribution/resolve", body);
    }

    private static MockHttpServletResponse post(AppAttributionResolveFilter filter, FilterChain chain, String path, String body) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.addHeader("Host", "client.example:443");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private static AppLog saved(AppLogPort logs) {
        ArgumentCaptor<AppLog> captor = ArgumentCaptor.forClass(AppLog.class);
        verify(logs).save(captor.capture());
        return captor.getValue();
    }
}
