package org.jeecg.modules.packagemanagement;

import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.packagemanagement.application.identity.PackageBusinessIdentityGuard;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackageBusinessIdentityGuardTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    Subject subject = mock(Subject.class);
    PackageBusinessIdentityGuard guard = new PackageBusinessIdentityGuard(jdbc);

    @BeforeEach void bind() { ThreadContext.bind(subject); }
    @AfterEach void unbind() { ThreadContext.remove(); }
    LoginUser login() {
        LoginUser user = new LoginUser(); user.setId("platform-admin"); user.setUsername("admin");
        when(subject.getPrincipal()).thenReturn(user); return user;
    }
    @Test void loginRoleAndExactPermissionAreIndependentRequirements() {
        assertEquals(401, assertThrows(PackageFailure.class, () -> guard.require("query")).getCode());
        login();
        when(subject.isPermitted("package:identity:query")).thenReturn(true);
        assertEquals(403, assertThrows(PackageFailure.class, () -> guard.require("query")).getCode());
        when(subject.hasRole("admin")).thenReturn(true);
        when(subject.isPermitted("package:identity:query")).thenReturn(false);
        assertEquals(403, assertThrows(PackageFailure.class, () -> guard.require("query")).getCode());
        verifyNoInteractions(jdbc);
    }
    @Test void staleDisabledDeletedOrRevokedOperatorIsRejected() {
        login(); when(subject.hasRole("admin")).thenReturn(true);
        when(subject.isPermitted("package:identity:open")).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("platform-admin"))).thenReturn(0L);
        assertEquals(403, assertThrows(PackageFailure.class, () -> guard.require("open")).getCode());
    }
    @Test void platformAdminNeedsNoOwnTenantMembershipOrBusinessMapping() {
        login(); when(subject.hasRole("admin")).thenReturn(true);
        when(subject.isPermitted("package:identity:bind")).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("platform-admin"))).thenReturn(1L);
        assertEquals("platform-admin", guard.require("bind").userId());
        verify(subject).isPermitted("package:identity:bind");
        verify(jdbc).queryForObject(argThat(sql -> sql.contains("sys_user") && sql.contains("status=1")
                && sql.contains("del_flag=0") && !sql.contains("pkg_user_identity") && !sql.contains("sys_user_tenant")), eq(Long.class), eq("platform-admin"));
    }
}
