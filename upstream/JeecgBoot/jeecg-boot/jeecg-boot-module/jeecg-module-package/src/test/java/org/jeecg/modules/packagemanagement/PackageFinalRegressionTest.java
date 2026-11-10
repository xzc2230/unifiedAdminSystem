package org.jeecg.modules.packagemanagement;

import java.util.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.jeecg.modules.packagemanagement.application.apps.IAppManagerService;
import org.jeecg.modules.packagemanagement.application.statistics.impl.AppStatisticsServiceImpl;
import org.jeecg.modules.packagemanagement.controller.admin.*;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.domain.event.LoadCount;
import org.jeecg.modules.packagemanagement.domain.statistics.*;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.domain.system.ReviewsRecord;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.*;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackageFinalRegressionTest {
    private final PackageActor actor=new PackageActor(1,"owner",9,false);
    private TransactionTemplate transaction() {
        return new TransactionTemplate(new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction(){return new Object();}
            protected void doBegin(Object tx,org.springframework.transaction.TransactionDefinition definition){}
            protected void doCommit(DefaultTransactionStatus status){}
            protected void doRollback(DefaultTransactionStatus status){}
        });
    }
    @Test void adminCreationInitializesCountersBeforeFirstSdkUploadAndEditPreservesThem() {
        var mapper=mock(AppStatisticsMapper.class);var stores=mock(StoreStatusMapper.class);
        var service=new AppStatisticsServiceImpl();
        ReflectionTestUtils.setField(service,"appStatisticsMapper",mapper);
        ReflectionTestUtils.setField(service,"storeStatusMapper",stores);
        when(stores.selectStoreStatusByPackageName("com.test")).thenReturn(new StoreStatus());
        when(mapper.insertAppStatistics(any())).thenAnswer(call->{((AppStatistics)call.getArgument(0)).setId(1L);return 1;});
        when(mapper.updateAppStatistics(any())).thenReturn(1);
        var access=mock(PackageAccess.class);
        var resource=new ManagedResource<>("pkg_app_statistics",AppStatistics.class,AppStatistics::getId,f->List.of(),id->null,
            service::insertAppStatistics,service::updateAppStatistics,ids->1,(r,a)->r.setUserId(9L),
            List.of("packageCode","status"),Map.of("packageCode","包名"),access,transaction());
        var forged=new AppStatistics();forged.setPackageCode("com.test");forged.setReigisterNum(999L);forged.setPaymentNum(999L);forged.setPaymentTotal(new BigDecimal("999"));
        var created=resource.add(actor,forged);
        assertEquals(0L,created.getReigisterNum());assertEquals(0L,created.getPaymentNum());assertEquals(0,BigDecimal.ZERO.compareTo(created.getPaymentTotal()));
        when(mapper.selectAppStatisticsByPackageName("com.test")).thenReturn(created);
        var request=new MockHttpServletRequest();request.setParameter("packageCode","com.test");request.setParameter("register","1");request.setParameter("payment","1");request.setParameter("amount","2.335");
        assertEquals(0,((StatisticsVO<?>)service.uploadData(request)).getCode());
        assertEquals(1L,created.getReigisterNum());assertEquals(1L,created.getPaymentNum());assertEquals(new BigDecimal("2.34"),created.getPaymentTotal());
        service.updateAppStatistics(created);assertEquals(1L,created.getReigisterNum());assertEquals(new BigDecimal("2.34"),created.getPaymentTotal());
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    @Test void landingDateOnlyWorksForQueryAndJsonEditWithoutChangingDaySemantics() {
        var registry=mock(PackageAdminRegistry.class);var guard=mock(PackageAdminSupport.class);
        var resource=mock(ManagedResource.class);when(resource.type()).thenReturn(LoadCount.class);when(resource.table()).thenReturn("pkg_load_count");
        when(registry.get("landing-counts")).thenReturn(resource);when(guard.actor(anyString(),anyString())).thenReturn(actor);
        var captured=new ArrayList<LoadCount>();
        when(resource.scoped(eq(actor),any())).thenAnswer(call->{captured.add((LoadCount)call.getArgument(1));return List.of();});
        when(resource.edit(eq(actor),eq(1L),any())).thenAnswer(call->{captured.add((LoadCount)call.getArgument(2));return call.getArgument(2);});
        var controller=new PackageAdminController(registry,guard);var request=new MockHttpServletRequest();request.setParameter("time","2026-11-09");
        controller.list("landing-counts",1,10,request);
        controller.edit("landing-counts",1L,Map.of("time","2026-11-09"));
        controller.edit("landing-counts",1L,Map.of("time","2026-11-09 18:23:45"));
        var format=org.jeecg.modules.packagemanagement.controller.PackageJson.dateFormat();
        assertEquals("2026-11-09 00:00:00",format.format(captured.get(0).getTime()));
        assertEquals(captured.get(0).getTime(),captured.get(1).getTime());
        assertEquals("2026-11-09 18:23:45",format.format(captured.get(2).getTime()));
        assertThrows(RuntimeException.class,()->controller.edit("landing-counts",1L,Map.of("time","2026-02-30")));
    }
    @Test void appTransferDoesNotLockOutOwnerOfUnchangedIndependentStoreButReparentStillNeedsParentOwnership() {
        var access=mock(PackageAccess.class);var apps=mock(IAppManagerService.class);
        var parent=new AppManager();parent.setId(3L);when(apps.selectAppManagerByPackageName("com.transferred")).thenReturn(parent);
        doThrow(new PackageFailure(403,"now belongs to recipient")).when(access).lockRows(actor,"pkg_app_manager",List.of(3L));
        var saved=new StoreStatus();saved.setId(2L);saved.setPackageCode("com.transferred");
        var resource=new ManagedResource<>("pkg_store_status",StoreStatus.class,StoreStatus::getId,f->List.of(),id->saved,r->1,r->1,ids->1,(r,a)->{},List.of("packageCode","status"),Map.of("status","状态"),access,transaction());
        new PackageResourcePolicy(access,apps,null,null,null,null).attach(resource);
        var edit=new StoreStatus();edit.setStatus(1);
        assertDoesNotThrow(()->resource.edit(actor,2L,edit));assertEquals(1,saved.getStatus());
        var create=new StoreStatus();create.setPackageCode("com.transferred");
        assertThrows(PackageFailure.class,()->resource.add(actor,create));
        var other=new AppManager();other.setId(4L);when(apps.selectAppManagerByPackageName("com.other")).thenReturn(other);
        var move=new StoreStatus();move.setPackageCode("com.other");assertThrows(PackageFailure.class,()->resource.edit(actor,2L,move));
        verify(access,times(2)).lockEditableRows(actor,"pkg_store_status",List.of(2L));
    }
    @Test void standaloneReviewReparentSynchronizesOwnerOnlyAfterSuccessfulUpdateInTransaction() {
        var access=mock(PackageAccess.class);var admin=new PackageActor(1,"admin",1,true);
        var saved=new ReviewsRecord();saved.setReviewsId(4L);saved.setAppId(1L);
        var updated=new boolean[]{false};
        doAnswer(call->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());assertTrue(updated[0]);return null;})
            .when(access).reassignReviewToParent(admin,4L,2L);
        var resource=new ManagedResource<>("pkg_review",ReviewsRecord.class,ReviewsRecord::getReviewsId,f->List.of(),id->saved,r->1,r->{updated[0]=true;return 1;},ids->1,(r,a)->{},List.of("appId"),Map.of("appId","应用"),access,transaction());
        new PackageResourcePolicy(access,null,null,null,null,null).attach(resource);
        var submitted=new ReviewsRecord();submitted.setAppId(2L);resource.edit(admin,4L,submitted);
        // Registry must follow the destination parent, just as newly created nested reviews do.
        verify(access).reassignReviewToParent(admin,4L,2L);
        verify(access).lockRows(admin,"pkg_pwa",List.of(1L));verify(access).lockRows(admin,"pkg_pwa",List.of(2L));
    }
    @Test void unsuccessfulReviewUpdateDoesNotMoveRegistryOwnership() {
        var access=mock(PackageAccess.class);var saved=new ReviewsRecord();saved.setReviewsId(4L);saved.setAppId(1L);
        var resource=new ManagedResource<>("pkg_review",ReviewsRecord.class,ReviewsRecord::getReviewsId,f->List.of(),id->saved,r->1,r->0,ids->1,(r,a)->{},List.of("appId"),Map.of("appId","应用"),access,transaction());
        new PackageResourcePolicy(access,null,null,null,null,null).attach(resource);
        var move=new ReviewsRecord();move.setAppId(2L);assertThrows(PackageFailure.class,()->resource.edit(actor,4L,move));
        verify(access,never()).reassignReviewToParent(any(),anyLong(),anyLong());
    }
}
