package org.jeecg.modules.siteplatform;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoutingNodeTest {
    final RoutingRepository repository = mock(RoutingRepository.class);
    final String token = "0123456789abcdef0123456789abcdef";
    RoutingNodeController node(String id, String secret, String scope) { return new RoutingNodeController(repository, id, secret, scope); }
    @Test void missingOrInvalidConfigurationDisablesBothEndpoints() {
        for (String[] config : List.of(new String[]{"", token, "100/site"}, new String[]{"node", "short", "100/site"}, new String[]{"node", token, ""}, new String[]{"node", token, "100/site,broken"}, new String[]{"node", token, "100/site,200/site"})) {
            var node = node(config[0], config[1], config[2]);
            assertEquals(404, assertThrows(ResourceFailure.class, () -> node.snapshots(null)).status());
            assertEquals(404, assertThrows(ResourceFailure.class, () -> node.ack(null, new RoutingRequests.AckInput("site", "release", "a".repeat(64)))).status());
        }
        verifyNoInteractions(repository);
    }
    @Test void wrongOrMissingBearerNeverReadsDatabase() {
        var node = node("node", token, "100/site");
        for (String auth : new String[]{null, token, "Bearer wrong", "Bearer " + token + " "}) {
            assertEquals(401, assertThrows(ResourceFailure.class, () -> node.snapshots(auth)).status());
        }
        verifyNoInteractions(repository);
    }
    @Test void snapshotsUseOnlyExplicitServerAssignmentsAndReturnDirectJson() {
        when(repository.desired(100, "site")).thenReturn(Map.of("payload", "{}", "hash", "hash"));
        var response = node("node", token, "100/site").snapshots("Bearer " + token);
        assertEquals(Set.of("nodeId", "snapshots"), response.keySet());
        assertEquals("node", response.get("nodeId"));
        assertEquals(1, ((List<?>) response.get("snapshots")).size());
        verify(repository).desired(100, "site");
        verifyNoMoreInteractions(repository);
    }
    @Test void foreignSiteAndOldAckCannotChangeCurrentAck() {
        var node = node("node", token, "100/site");
        assertEquals(403, assertThrows(ResourceFailure.class, () -> node.ack("Bearer " + token, new RoutingRequests.AckInput("foreign", "r", "a".repeat(64)))).status());
        when(repository.lockSite(100, "site")).thenReturn(true);
        when(repository.desiredCurrent(100, "site")).thenReturn(Map.of("id", "new", "hash", "a".repeat(64)));
        assertEquals(409, assertThrows(ResourceFailure.class, () -> node.ack("Bearer " + token, new RoutingRequests.AckInput("site", "old", "a".repeat(64)))).status());
        assertEquals(409, assertThrows(ResourceFailure.class, () -> node.ack("Bearer " + token, new RoutingRequests.AckInput("site", "new", "b".repeat(64)))).status());
        verify(repository, never()).ack(anyInt(), anyString(), anyString(), anyString(), anyString());
        assertEquals(Map.of("accepted", true), node.ack("Bearer " + token, new RoutingRequests.AckInput("site", "new", "a".repeat(64))));
        verify(repository).ack(100, "site", "node", "new", "a".repeat(64));
    }
}
