package org.jeecg.modules.packagemanagement;

import java.util.*;
import org.jeecg.modules.packagemanagement.application.identity.*;
import org.jeecg.modules.packagemanagement.controller.admin.identity.PackageBusinessIdentityController;
import org.jeecg.modules.packagemanagement.controller.admin.PackageExceptionHandler;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class PackageBusinessIdentityHttpTest {
    @Test void malformedHttpInputKeepsResultEnvelopeAndNumericLegacyIdIsRejected() throws Exception {
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var guard=mock(PackageBusinessIdentityGuard.class);
        when(guard.require(anyString())).thenReturn(new PackageBusinessIdentityGuard.Operator("admin","admin"));
        var service=new PackageBusinessIdentityService(jdbc,mock(org.springframework.transaction.support.TransactionTemplate.class));
        var mvc=MockMvcBuilders.standaloneSetup(new PackageBusinessIdentityController(guard,service))
                .setControllerAdvice(new PackageExceptionHandler()).build();
        var missing=mvc.perform(get("/package-admin/identities/list")).andReturn().getResponse();
        assertEquals(400,missing.getStatus()); assertTrue(missing.getContentAsString().contains("\"code\":400"));
        var malformed=mvc.perform(post("/package-admin/identities/bind").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andReturn().getResponse();
        assertEquals(400,malformed.getStatus());assertTrue(malformed.getContentAsString().contains("\"code\":400"));
        for(String extra:List.of("\"legacyUserId\":12","\"legacyUserId\":true","\"legacyUserId\":\"9223372036854775808\"")) {
            var response=mvc.perform(post("/package-admin/identities/bind").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tenantId\":1,\"userId\":\"u\",\"reason\":\"test\","+extra+"}"))
                    .andReturn().getResponse();
            assertEquals(400,response.getStatus());assertTrue(response.getContentAsString().contains("\"code\":400"));
        }
        verifyNoInteractions(jdbc);
    }
    @Test void listUsesBooleanTenantActiveEvenWhenJdbcReturnsAnInteger() {
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(new HashMap<>(Map.of("tenantActive",1,"identityState","ACTIVE"))));
        var service=new PackageBusinessIdentityService(jdbc,mock(org.springframework.transaction.support.TransactionTemplate.class));
        assertEquals(Boolean.TRUE,service.list(1,null,null,1,10).records().get(0).get("tenantActive"));
    }
    @Test void everyRouteUsesItsOwnPermissionAndResultEnvelope() throws Exception {
        var guard = mock(PackageBusinessIdentityGuard.class);
        var service = mock(PackageBusinessIdentityService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PackageBusinessIdentityController(guard, service))
                .setControllerAdvice(new PackageExceptionHandler()).build();
        for (String route : List.of("tenants", "list", "audit", "legacy-preview")) {
            String permission = route.equals("legacy-preview") ? "bind" : "query";
            reset(guard); when(guard.require(permission)).thenThrow(new PackageFailure(403,"denied"));
            var response = mvc.perform(get("/package-admin/identities/" + route)
                    .param("tenantId","1").param("userId","u").param("legacyUserId","12"))
                    .andReturn().getResponse();
            assertEquals(403,response.getStatus(),route); assertTrue(response.getContentAsString().contains("\"code\":403"));
            verify(guard).require(permission);
        }
        for (String route : List.of("open", "bind", "status")) {
            reset(guard); when(guard.require(route)).thenThrow(new PackageFailure(401,"login"));
            String extra=route.equals("bind")?",\"legacyUserId\":\"12\"":route.equals("status")?",\"enabled\":true":"";
            assertEquals(401,mvc.perform(post("/package-admin/identities/"+route).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tenantId\":1,\"userId\":\"u\",\"reason\":\"test\""+extra+"}"))
                    .andReturn().getResponse().getStatus());
            verify(guard).require(route);
        }
        verifyNoInteractions(service);
        reset(guard); when(service.tenants()).thenReturn(List.of());
        var response=mvc.perform(get("/package-admin/identities/tenants")).andReturn().getResponse();
        assertEquals(200,response.getStatus()); assertTrue(response.getContentAsString().contains("\"result\":[]"));
    }
    @Test void strictDecimalLongAndInputBoundsRejectBeforeDatabaseAccess() {
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var service=new PackageBusinessIdentityService(jdbc, mock(org.springframework.transaction.support.TransactionTemplate.class));
        for(Object bad: Arrays.asList(null, 12L, "0", "-1", "+1", "01", " 1", "1.0", "1e3", "9223372036854775808"))
            assertThrows(PackageFailure.class,()->service.preview(1,"u",bad),String.valueOf(bad));
        assertThrows(PackageFailure.class,()->service.list(1,"x".repeat(101),null,1,10));
        assertThrows(PackageFailure.class,()->service.list(1,null,null,1,101));
        assertThrows(PackageFailure.class,()->service.list(1,null,"invalid",1,10));
        assertThrows(PackageFailure.class,()->service.open(null,1,"u"," "));
        assertThrows(PackageFailure.class,()->service.open(null,1,"u","x".repeat(501)));
        assertThrows(PackageFailure.class,()->service.status(null,1,"u",null,"test"));
        verifyNoInteractions(jdbc);
    }
}
