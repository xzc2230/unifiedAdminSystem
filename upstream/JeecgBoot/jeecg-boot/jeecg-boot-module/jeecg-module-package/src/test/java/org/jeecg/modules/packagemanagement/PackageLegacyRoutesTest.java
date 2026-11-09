package org.jeecg.modules.packagemanagement;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.jeecg.modules.packagemanagement.controller.legacy.LegacyRequest;
import org.jeecg.modules.packagemanagement.controller.legacy.LegacyClientController;
import org.jeecg.modules.packagemanagement.controller.legacy.LegacyPublicController;
import org.jeecg.modules.packagemanagement.application.apps.ClientService;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackageLegacyRoutesTest {
    @Test void clientRoutesPreserveAnyMethodAndRawStringResponse() throws Exception {
        ClientService service = mock(ClientService.class);
        when(service.login(any())).thenReturn("plain");
        when(service.api(any())).thenReturn(java.util.Map.of("status", 0));
        var mvc = MockMvcBuilders.standaloneSetup(new LegacyClientController(service)).build();
        assertEquals("plain", mvc.perform(post("/package-legacy/v2/api/user/login")).andReturn().getResponse().getContentAsString());
        String base64 = mvc.perform(get("/package-legacy/system/check")).andReturn().getResponse().getContentAsString();
        assertEquals("{\"status\":0}", new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8));
    }
    @Test void fourSegmentPathAcceptsOnlyJsonPostAndKeepsSpecificLogin() throws Exception {
        ClientService service = mock(ClientService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new LegacyClientController(service)).build();
        assertEquals(200, mvc.perform(post("/package-legacy/a/b/c/d")
            .contentType("application/json").content("{\"t\":1,\"p\":\"x\"}"))
            .andReturn().getResponse().getStatus());
        assertEquals(405, mvc.perform(get("/package-legacy/a/b/c/d")).andReturn().getResponse().getStatus());
        assertEquals(415, mvc.perform(post("/package-legacy/a/b/c/d").contentType("text/plain").content("x"))
            .andReturn().getResponse().getStatus());
        mvc.perform(post("/package-legacy/v2/api/user/login").contentType("application/json").content("{}"));
        verify(service).login(any());
    }
    @Test void publicRoutesPreserveAnyAndSpecificRouting() throws Exception {
        var statistics = mock(org.jeecg.modules.packagemanagement.application.statistics.IAppStatisticsService.class);
        when(statistics.getDetails(any())).thenReturn("switch");
        var mvc = MockMvcBuilders.standaloneSetup(new LegacyPublicController(null, null, null, null, statistics, null, null, null)).build();
        assertEquals("switch", mvc.perform(post("/package-legacy/systemSDK/demo")).andReturn().getResponse().getContentAsString());
        verify(statistics).getDetails(any());
    }
    @Test void stripsInternalPrefixAndKeepsQuery() {
        var request = new MockHttpServletRequest("GET", "/jeecg-boot/package-legacy/system/demo");
        request.setContextPath("/jeecg-boot");
        request.setServletPath("/package-legacy/system/demo");
        request.setQueryString("id=test");
        var legacy = LegacyRequest.wrap(request);
        assertEquals("/system/demo", legacy.getRequestURI());
        assertEquals("", legacy.getContextPath());
        assertEquals("/system/demo", legacy.getServletPath());
        assertEquals("id=test", legacy.getQueryString());
    }
}
