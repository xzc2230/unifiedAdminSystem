package org.jeecg.modules.packagemanagement;

import org.jeecg.modules.packagemanagement.application.port.HttpPort;
import org.jeecg.modules.packagemanagement.application.store.StoreDelistReason;
import org.jeecg.modules.packagemanagement.config.PackageManagementConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoreDelistReasonTest {

    @Test
    void playNotFoundPageBecomesTheStoredReason() {
        String body = "<title>Not Found</title><div id=\"error-section\">We're sorry, the requested URL was not found on this server.</div>";
        assertEquals("HTTP 404 We're sorry, the requested URL was not found on this server.", StoreDelistReason.http(404, body));
        assertEquals("HTTP 503", StoreDelistReason.http(503, "   "));
        assertEquals("iTunes lookup resultCount=0, country=us", StoreDelistReason.itunesMiss(" "));
    }

    @Test
    void liveProbeKeepsSuccessOnlineAndStoresTheHttpReason() {
        HttpPort http = mock(HttpPort.class);
        when(http.exchange(anyString(), anyString(), isNull(), anyMap()))
                .thenReturn(new HttpPort.Reply(200, "<title>Store listing copy - Apps on Google Play</title>"))
                .thenReturn(new HttpPort.Reply(404, "<title>Not Found</title>We're sorry, the requested URL was not found on this server."));
        var probe = new PackageManagementConfiguration().packageStoreProbe(http);
        var listed = probe.getStatus("https://play.google.com/store/apps/details?id=com.live");
        var missing = probe.getStatus("https://play.google.com/store/apps/details?id=com.missing");
        assertTrue(listed.online());
        assertNull(listed.reason());
        assertFalse(missing.online());
        assertEquals("HTTP 404 We're sorry, the requested URL was not found on this server.", missing.reason());
    }
}
