package org.jeecg.modules.packagemanagement;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.jeecg.modules.packagemanagement.controller.*;
import org.jeecg.modules.packagemanagement.controller.admin.*;
import org.jeecg.modules.packagemanagement.controller.legacy.*;
import org.jeecg.modules.packagemanagement.application.applog.EsAppLogServiceImpl;
import org.jeecg.modules.packagemanagement.domain.applog.AppLog;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class PackageHttpSecurityTest {
    @Test void managementReturns401And403AndNeverCallsBusinessService() throws Exception {
        var registry=mock(PackageAdminRegistry.class);var guard=mock(PackageAdminSupport.class);
        var mvc=MockMvcBuilders.standaloneSetup(new PackageAdminController(registry,guard)).setControllerAdvice(new PackageExceptionHandler()).build();
        when(guard.actor("apps","query")).thenThrow(new PackageFailure(401,"login"));
        assertEquals(401,mvc.perform(get("/package-admin/apps/list")).andReturn().getResponse().getStatus());
        doThrow(new PackageFailure(403,"denied")).when(guard).actor("apps","query");
        assertEquals(403,mvc.perform(get("/package-admin/apps/1")).andReturn().getResponse().getStatus());verifyNoInteractions(registry);
    }
    @Test void logBatchChecksEveryPackageBeforeDelete() throws Exception {
        var logs=mock(EsAppLogServiceImpl.class);var guard=mock(PackageAdminSupport.class);var access=mock(PackageAccess.class);
        var actor=new PackageActor(1,"u",1,false);when(guard.actor(anyString(),anyString())).thenReturn(actor);
        AppLog first=new AppLog();first.setPackageName("mine");AppLog second=new AppLog();second.setPackageName("other");
        when(logs.findById("a")).thenReturn(Optional.of(first));when(logs.findById("b")).thenReturn(Optional.of(second));
        doThrow(new PackageFailure(403,"owner")).when(access).requireLogPackage(actor,"other");
        var mvc=MockMvcBuilders.standaloneSetup(new PackageRequestLogController(logs,guard,access)).setControllerAdvice(new PackageExceptionHandler()).build();
        assertEquals(403,mvc.perform(delete("/package-admin/request-logs/a,b")).andReturn().getResponse().getStatus());verify(logs,never()).deleteAllById(any());
    }
    @Test void legacyProjectionFormatsDatesLocallyAndKeepsEmptyStrings() throws Exception {
        var pwa=mock(org.jeecg.modules.packagemanagement.application.pwa.IAppPwaService.class);
        var reviews=mock(org.jeecg.modules.packagemanagement.application.pwa.IReviewsRecordService.class);
        var row=new org.jeecg.modules.packagemanagement.domain.pwa.AppPwa();row.setId(1L);row.setAppAdvertising("a,b");row.setTags("[\"game\"]");row.setPrivateKey("never-public");
        when(pwa.selectAppPwaAppList(any())).thenReturn(List.of(row));
        var review=new org.jeecg.modules.packagemanagement.domain.system.ReviewsRecord();review.setCreateTime(new Date(0));when(reviews.selectReviewsRecordList(any())).thenReturn(List.of(review));
        var controller=new LegacyPublicController(null,null,null,pwa,null,null,null,reviews);
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new PackageSerializationAdvice()).build();
        var response=mvc.perform(get("/package-legacy/pwa/code")).andReturn().getResponse();
        var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
        assertEquals("1970-01-01 08:00:00",json.path("reviewsRecordList").get(0).path("createTime").asText());assertFalse(response.getContentAsString().contains("never-public"));assertEquals(17,json.size());
        when(pwa.selectAppPwaAppList(any())).thenReturn(List.of());assertEquals("",mvc.perform(get("/package-legacy/pwa/missing")).andReturn().getResponse().getContentAsString());
    }
}
