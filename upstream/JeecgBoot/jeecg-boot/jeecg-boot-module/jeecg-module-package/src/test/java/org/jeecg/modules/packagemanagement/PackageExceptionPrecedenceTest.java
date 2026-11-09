package org.jeecg.modules.packagemanagement;

import org.junit.jupiter.api.*;
import org.jeecg.common.exception.JeecgBootExceptionHandler;
import org.jeecg.modules.base.service.BaseCommonService;
import org.jeecg.modules.packagemanagement.controller.admin.*;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class PackageExceptionPrecedenceTest {
    @AfterEach void unbind(){org.apache.shiro.util.ThreadContext.unbindSubject();}
    private JeecgBootExceptionHandler host() {
        org.apache.shiro.util.ThreadContext.bind(mock(org.apache.shiro.subject.Subject.class));
        var host=new JeecgBootExceptionHandler();ReflectionTestUtils.setField(host,"baseCommonService",mock(BaseCommonService.class));return host;
    }
    @Test void moduleStatusSurvivesHostCatchAllRegardlessOfRegistrationOrder() throws Exception {
        for(boolean hostFirst:new boolean[]{true,false}) {
            var guard=mock(PackageAdminSupport.class);var registry=mock(PackageAdminRegistry.class);
            var host=host();var local=new PackageExceptionHandler();
            var mvc=MockMvcBuilders.standaloneSetup(new PackageAdminController(registry,guard))
                .defaultRequest(get("/").header("User-Agent","Mozilla/5.0 test")).setControllerAdvice(hostFirst?new Object[]{host,local}:new Object[]{local,host}).build();
            assertEquals(404,mvc.perform(post("/package-admin/store/export")).andReturn().getResponse().getStatus());
            for(int code:new int[]{400,401,403,404,409,502,503}) {
                doThrow(new PackageFailure(code,"failure-"+code)).when(guard).actor("apps","query");
                var response=mvc.perform(get("/package-admin/apps/list")).andReturn().getResponse();
                assertEquals(code,response.getStatus());
                assertEquals(code,new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString()).path("code").asInt());
            }
            doThrow(new IllegalArgumentException("invalid")).when(guard).actor("apps","query");
            assertEquals(400,mvc.perform(get("/package-admin/apps/list")).andReturn().getResponse().getStatus());
        }
    }
    @RestController static class UnrelatedHostController {
        @GetMapping("/unrelated-host") Object fail(){throw new PackageFailure(403,"host behavior");}
    }
    @Test void unrelatedHostControllerStillUsesExistingHostHandler() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new UnrelatedHostController()).defaultRequest(get("/").header("User-Agent","Mozilla/5.0 test")).setControllerAdvice(host(),new PackageExceptionHandler()).build();
        var response=mvc.perform(get("/unrelated-host")).andReturn().getResponse();
        assertEquals(200,response.getStatus());assertEquals(500,new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString()).path("code").asInt());
    }
}
