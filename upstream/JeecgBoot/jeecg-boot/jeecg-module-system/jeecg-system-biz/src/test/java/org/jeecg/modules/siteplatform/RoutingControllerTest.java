package org.jeecg.modules.siteplatform;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoutingControllerTest {
    @Test void shiroExemptsOnlyTheTwoExactNodeEndpoints() throws Exception {
        String config = java.nio.file.Files.readString(java.nio.file.Path.of("../../jeecg-boot-base-core/src/main/java/org/jeecg/config/shiro/ShiroConfig.java"));
        assertTrue(config.contains("filterChainDefinitionMap.put(\"/site-platform/node/snapshots\", \"anon\")"));
        assertTrue(config.contains("filterChainDefinitionMap.put(\"/site-platform/node/ack\", \"anon\")"));
        assertFalse(config.contains("\"/site-platform/node/**\""));
    }
    @Test void managementActionsDeclarePlatformPermissionsInAdditionToServiceRoleChecks() throws Exception {
        assertEquals("siteplatform:approve", RoutingController.class.getMethod("approve", String.class, RoutingRequests.RevisionInput.class).getAnnotation(RequiresPermissions.class).value()[0]);
        assertEquals("siteplatform:publish", RoutingController.class.getMethod("publish", String.class, RoutingRequests.RevisionInput.class).getAnnotation(RequiresPermissions.class).value()[0]);
        assertEquals("siteplatform:publish", RoutingController.class.getMethod("rollback", String.class, String.class, RoutingRequests.RevisionInput.class).getAnnotation(RequiresPermissions.class).value()[0]);
        assertEquals("/site-platform/sites/{site}/routing", RoutingController.class.getAnnotation(RequestMapping.class).value()[0]);
    }
    @Test void nodeControllerIsDirectJsonAndUsesOnlyTwoExactMappings() throws Exception {
        assertEquals("/site-platform/node", RoutingNodeController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertArrayEquals(new String[]{"/snapshots"}, RoutingNodeController.class.getMethod("snapshots", String.class).getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{"/ack"}, RoutingNodeController.class.getMethod("ack", String.class, RoutingRequests.AckInput.class).getAnnotation(PostMapping.class).value());
        var controller = new RoutingNodeController(mock(RoutingRepository.class), "", "", "");
        var failure = controller.failure(new ResourceFailure(401, "节点凭证无效"));
        assertEquals(401, failure.getStatusCode().value());
        assertEquals(java.util.Map.of("message", "节点凭证无效"), failure.getBody());
        assertFalse(failure.getBody().containsKey("result"));
    }
    @Test void managementControllerReturnsResultWithCurrentRevisionAndHttpConflict() {
        var service = mock(RoutingService.class);
        when(service.submit("site", 1)).thenReturn(2L);
        var controller = new RoutingController(service);
        assertEquals(java.util.Map.of("revision", 2L), controller.submit("site", new RoutingRequests.RevisionInput(1)).getResult());
        assertEquals(409, controller.failure(new ResourceFailure(409, "草稿已变更")).getStatusCode().value());
    }
    @Test void unexpectedFailuresNeverExposeSqlTargetsOrNodeSecrets() {
        String sensitive = "SELECT tenant=200,target=https://private-origin.example.com,node-secret";
        var node = new RoutingNodeController(mock(RoutingRepository.class), "", "", "");
        assertFalse(node.unexpected(new IllegalStateException(sensitive)).getBody().toString().contains("private-origin"));
        assertFalse(node.unexpected(new IllegalStateException(sensitive)).getBody().toString().contains("node-secret"));
        var controller = new RoutingController(mock(RoutingService.class));
        assertFalse(controller.unexpected(new IllegalStateException(sensitive)).getBody().toString().contains("tenant=200"));
    }
}
