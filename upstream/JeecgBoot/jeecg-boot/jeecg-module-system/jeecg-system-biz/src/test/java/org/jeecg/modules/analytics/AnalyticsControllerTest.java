package org.jeecg.modules.analytics;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsControllerTest {
    @Test void globalAnalyticsNeedsBothPlatformAdminAndLogReadPermission() {
        assertArrayEquals(new String[]{"admin"}, AnalyticsController.class.getAnnotation(RequiresRoles.class).value());
        assertArrayEquals(new String[]{"openapi:open_api_log:list"}, AnalyticsController.class.getAnnotation(RequiresPermissions.class).value());
    }

    @Test void defaultOffDoesNotRequireDatabaseAndCountsNothing() {
        var service = new AnalyticsService(new ClickHouseProperties());
        service.record("api", "GET", 200, 1);
        assertEquals(false, service.status().get("enabled"));
        assertThrows(IllegalStateException.class, () -> service.summary(1, 2, ""));
        service.close();
    }

    @Test void realShiroAdvisorRejectsRoleAndPermissionFailuresBeforeCallingService() {
        var service = mock(AnalyticsService.class);
        var controller = new AnalyticsController(service);
        var factory = new org.springframework.aop.framework.ProxyFactory(controller);
        factory.addAdvisor(new org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor());
        var proxy = (AnalyticsController) factory.getProxy();
        var subject = mock(org.apache.shiro.subject.Subject.class);
        org.apache.shiro.util.ThreadContext.bind(subject);
        try {
            doThrow(new org.apache.shiro.authz.UnauthorizedException()).when(subject).checkRole("admin");
            assertThrows(org.apache.shiro.authz.UnauthorizedException.class, proxy::status);
            verifyNoInteractions(service);
            reset(subject);
            doThrow(new org.apache.shiro.authz.UnauthorizedException()).when(subject).checkPermission("openapi:open_api_log:list");
            assertThrows(org.apache.shiro.authz.UnauthorizedException.class, proxy::status);
            verifyNoInteractions(service);
        } finally { org.apache.shiro.util.ThreadContext.unbindSubject(); }
    }
}
