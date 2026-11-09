package org.jeecg.modules.packagemanagement;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.jeecg.modules.packagemanagement.controller.admin.*;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.domain.pwa.AppPwa;
import org.jeecg.modules.packagemanagement.domain.system.ReviewsRecord;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.*;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackagePolicyRegressionTest {
    private TransactionTemplate transaction() {
        return new TransactionTemplate(new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction(){return new Object();}
            protected void doBegin(Object tx,org.springframework.transaction.TransactionDefinition definition){}
            protected void doCommit(DefaultTransactionStatus status){}
            protected void doRollback(DefaultTransactionStatus status){}
        });
    }
    @Test void addIgnoresClientIdAndOwnerAndClaimsInsideTransaction() {
        var access=mock(PackageAccess.class);var actor=new PackageActor(1,"user",9,false);
        var resource=new ManagedResource<>("pkg_app_manager",AppManager.class,AppManager::getId,f->List.of(),id->null,
            row->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());assertNull(row.getId());assertEquals(9L,row.getUserId());row.setId(10L);return 1;},r->1,ids->1,
            (row,a)->row.setUserId(a.legacyUserId()),List.of("packageCode"),Map.of("packageCode","包名"),access,transaction());
        var row=new AppManager();row.setId(999L);row.setUserId(777L);row.setPackageCode("mine");
        assertEquals(10L,resource.add(actor,row).getId());verify(access).claimCreated(actor,"pkg_app_manager",10L);
    }
    @Test void nestedReviewCannotBeReparentedEvenWhenBothIdsIndividuallyAuthorized() {
        var access=mock(PackageAccess.class);var reviews=mock(ReviewsRecordMapper.class);var actor=new PackageActor(1,"user",9,false);
        var saved=new AppPwa();saved.setId(1L);saved.setAppType("0");saved.setReviewsRecords(new ArrayList<>());
        var existingReview=new ReviewsRecord();existingReview.setReviewsId(4L);existingReview.setAppId(2L);when(reviews.selectReviewsRecordByReviewsId(4L)).thenReturn(existingReview);
        var invoked=new boolean[]{false};
        var resource=new ManagedResource<>("pkg_pwa",AppPwa.class,AppPwa::getId,f->List.of(),id->saved,r->1,r->{invoked[0]=true;return 1;},ids->1,(r,a)->{},List.of("reviewsRecords"),Map.of("appName","名称"),access,transaction());
        new PackageResourcePolicy(access,null,null,null,reviews,null).attach(resource);
        var submitted=new AppPwa();submitted.setReviewsRecords(List.of(existingReview));
        assertThrows(PackageFailure.class,()->resource.edit(actor,1L,submitted));assertFalse(invoked[0]);
    }
    @Test void administratorNewReviewUsesParentOwnerAndBothPublicationDatesRemainEditable() {
        var access=mock(PackageAccess.class);var reviews=mock(ReviewsRecordMapper.class);
        var admin=new PackageActor(1,"admin",1,true);var owner=new PackageActor(1,"owner",9,false);
        var parent=new AppPwa();parent.setId(1L);parent.setAppType("0");
        var savedReview=new ReviewsRecord();savedReview.setReviewsId(4L);savedReview.setAppId(1L);savedReview.setCreateTime(new Date(1000));
        when(reviews.selectReviewsRecordByReviewsId(4L)).thenReturn(savedReview);
        Set<Long> inherited=new HashSet<>();
        doAnswer(a->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());inherited.add(a.getArgument(2));return null;})
            .when(access).claimCreatedFromParent(eq(admin),eq("pkg_review"),anyLong(),eq("pkg_pwa"),eq(1L));
        doAnswer(a->{if(!inherited.contains(8L))throw new PackageFailure(403,"not parent owned");return null;})
            .when(access).lockRows(owner,"pkg_review",List.of(8L));
        var resource=new ManagedResource<>("pkg_pwa",AppPwa.class,AppPwa::getId,f->List.of(),id->parent,r->1,r->{
            for(var review:r.getReviewsRecords())if(review.getReviewsId()==null)review.setReviewsId(8L);return 1;
        },ids->1,(r,a)->{},List.of("reviewsRecords"),Map.of("appName","名称"),access,transaction());
        new PackageResourcePolicy(access,null,null,null,reviews,null).attach(resource);
        var fresh=new ReviewsRecord();fresh.setCreateTime(new Date(2000));
        var edited=new ReviewsRecord();edited.setReviewsId(4L);edited.setCreateTime(new Date(3000));
        var submitted=new AppPwa();submitted.setReviewsRecords(List.of(fresh,edited));resource.edit(admin,1L,submitted);
        assertEquals(new Date(2000),fresh.getCreateTime());assertEquals(new Date(3000),edited.getCreateTime());
        verify(access).claimCreatedFromParent(admin,"pkg_review",8L,"pkg_pwa",1L);verify(access,never()).claimCreated(any(),eq("pkg_review"),anyLong());
        when(reviews.selectReviewsRecordByReviewsId(8L)).thenReturn(fresh);
        var ownerEdit=new ReviewsRecord();ownerEdit.setReviewsId(8L);ownerEdit.setCreateTime(new Date(4000));
        var ownerSubmission=new AppPwa();ownerSubmission.setReviewsRecords(List.of(ownerEdit));
        assertDoesNotThrow(()->resource.edit(owner,1L,ownerSubmission));assertEquals(new Date(4000),ownerEdit.getCreateTime());
    }}
