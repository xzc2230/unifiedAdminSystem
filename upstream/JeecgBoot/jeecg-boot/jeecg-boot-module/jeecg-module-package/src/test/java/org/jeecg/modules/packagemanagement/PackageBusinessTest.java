package org.jeecg.modules.packagemanagement;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.jeecg.modules.packagemanagement.application.apps.impl.AppManagerServiceImpl;
import org.jeecg.modules.packagemanagement.application.statistics.impl.AppStatisticsServiceImpl;
import org.jeecg.modules.packagemanagement.application.store.impl.StoreStatusServiceImpl;
import org.jeecg.modules.packagemanagement.application.pwa.impl.AppPwaServiceImpl;
import org.jeecg.modules.packagemanagement.application.port.*;
import org.jeecg.modules.packagemanagement.config.*;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.domain.statistics.*;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.domain.pwa.AppPwa;
import org.jeecg.modules.packagemanagement.domain.system.ReviewsRecord;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.*;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PackageBusinessTest {
 @Test void mailFailureDoesNotStopLaterStoreTransitions(){
  var mapper=mock(StoreStatusMapper.class);var probe=mock(StoreProbePort.class);var mail=mock(MailPort.class);var service=new StoreStatusServiceImpl();inject(service,"storeStatusMapper",mapper);inject(service,"probe",probe);inject(service,"mail",mail);inject(service,"settings",new PackageManagementProperties());
  StoreStatus one=new StoreStatus();one.setStatus(0);one.setStoreUrl("one");StoreStatus two=new StoreStatus();two.setStatus(0);two.setStoreUrl("two");when(mapper.selectStoreStatusList(any())).thenReturn(List.of(one,two));when(probe.getStatus(anyString())).thenReturn(true);doThrow(new IllegalStateException("fake mail failure")).when(mail).sendEmail(anyString(),anyString(),eq("one"));
  assertDoesNotThrow(service::getStatus);assertEquals(1,two.getStatus());verify(mapper,times(2)).updateStoreStatusAndDate(any());
 }
 static void inject(Object bean,String field,Object value){ReflectionTestUtils.setField(bean,field,value);}
 @Test void duplicatePackageHasNoStoreSideEffectAndPublicLookupNeedsNoLogin(){
  var mapper=mock(AppManagerMapper.class);var stores=mock(StoreStatusMapper.class);var service=new AppManagerServiceImpl();inject(service,"appManagerMapper",mapper);inject(service,"storeStatusMapper",stores);
  AppManager app=new AppManager();app.setPackageCode("com.test");app.setUserId(1L);when(mapper.selectAppManagerByPackageName("com.test")).thenReturn(app);when(mapper.selectAppManagerByPlatformAndPackage("android","com.test")).thenReturn(app);
  assertSame(app,service.selectAppManagerByPackageName("com.test"));assertEquals(-1,service.insertAppManager(app));verifyNoInteractions(stores);verify(mapper,never()).insertAppManager(any());
 }
 @Test void newPackageEnsuresStoreAndPreservesNumericOwner(){
  var mapper=mock(AppManagerMapper.class);var stores=mock(StoreStatusMapper.class);var service=new AppManagerServiceImpl();inject(service,"appManagerMapper",mapper);inject(service,"storeStatusMapper",stores);
  AppManager app=new AppManager();app.setPackageCode("com.test");app.setUserId(17L);when(mapper.selectAppManagerByPlatformAndPackage("android","com.test")).thenReturn(null);when(stores.selectStoreStatusByPlatformAndPackage("android","com.test")).thenReturn(null);when(mapper.insertAppManager(app)).thenReturn(1);
  assertEquals(1,service.insertAppManager(app));verify(stores).insertStoreStatus(argThat(s->"android".equals(s.getPlatform())&&s.getStatus()==0&&s.getPackageCode().equals("com.test")&&s.getStoreUrl().endsWith("id=com.test")));assertEquals(17L,app.getUserId());assertNotNull(app.getCreateDate());
 }
 @Test void statisticsCountersAndAmountRoundHalfUpToTwoDecimals(){
  var mapper=mock(AppStatisticsMapper.class);var service=new AppStatisticsServiceImpl();inject(service,"appStatisticsMapper",mapper);
  AppStatistics value=new AppStatistics();value.setReigisterNum(4L);value.setPaymentNum(2L);value.setPaymentTotal(new BigDecimal("1.00"));when(mapper.selectAppStatisticsByPackageName("com.test")).thenReturn(value);when(mapper.updateAppStatistics(value)).thenReturn(1);
  MockHttpServletRequest request=new MockHttpServletRequest();request.setParameter("packageCode","com.test");request.setParameter("register","1");request.setParameter("payment","1");request.setParameter("amount","1.005");
  assertEquals(0,((StatisticsVO<?>)service.uploadData(request)).getCode());assertEquals(5L,value.getReigisterNum());assertEquals(3L,value.getPaymentNum());assertEquals(new BigDecimal("2.01"),value.getPaymentTotal());
 }
 @Test void storeOnlyTransitionsZeroToOneAndOneToTwoWithEmails(){
  var mapper=mock(StoreStatusMapper.class);var probe=mock(StoreProbePort.class);var mail=mock(MailPort.class);var service=new StoreStatusServiceImpl();inject(service,"storeStatusMapper",mapper);inject(service,"probe",probe);inject(service,"mail",mail);PackageManagementProperties config=new PackageManagementProperties();config.setStoreRecipients("ops@example.invalid");inject(service,"settings",config);
  StoreStatus up=new StoreStatus();up.setStatus(0);up.setStoreUrl("up");up.setSuccessEmail("up@example.invalid");StoreStatus down=new StoreStatus();down.setStatus(1);down.setStoreUrl("down");StoreStatus gone=new StoreStatus();gone.setStatus(2);gone.setStoreUrl("gone");when(mapper.selectStoreStatusList(any())).thenReturn(List.of(up,down,gone));when(probe.getStatus("up")).thenReturn(true);
  service.getStatus();assertEquals(1,up.getStatus());assertEquals(2,down.getStatus());assertNotNull(up.getSuccessDate());assertNotNull(down.getFailDate());verify(probe,never()).getStatus("gone");verify(mail).sendEmail("ops@example.invalid,up@example.invalid","GP上架","up");verify(mail).sendEmail("ops@example.invalid","GP下架","down");verify(mapper,times(2)).updateStoreStatusAndDate(any());
 }
 @Test void pwaCreatesKeysLinkAndNestedReviewsThenUpdateRetainsKeys(){
  var mapper=mock(AppPwaMapper.class);var reviews=mock(ReviewsRecordMapper.class);var gateway=mock(PwaGateway.class);var snowflake=mock(PackageSnowflake.class);var service=new AppPwaServiceImpl();inject(service,"appPwaMapper",mapper);inject(service,"reviewsRecordMapper",reviews);inject(service,"gateway",gateway);inject(service,"snowflake",snowflake);inject(service,"settings",new PackageManagementProperties());
  AppPwa app=new AppPwa();app.setAppType("1");app.setAppHost("test");app.setAppSub(".example.invalid");app.setAppSubHost("3");ReviewsRecord newReview=new ReviewsRecord();ReviewsRecord oldReview=new ReviewsRecord();oldReview.setReviewsId(9L);app.setReviewsRecords(List.of(newReview,oldReview));when(mapper.selectAppPwaList(any())).thenReturn(List.of());when(gateway.generateVapidKeys()).thenReturn("{\"publicKey\":\"pub-test\",\"privateKey\":\"priv-test\"}");when(gateway.createHost(anyString())).thenReturn("{\"id\":42}");when(snowflake.nextId()).thenReturn(123456L);when(mapper.insertAppPwa(app)).thenAnswer(i->{app.setId(7L);return 1;});when(mapper.updateAppPwa(app)).thenReturn(1);
  assertFalse(service.insertAppPwa(app).isError());assertEquals("42",app.getLinkId());assertEquals("123456",app.getAppCode());assertEquals("pub-test",app.getPublicKey());assertEquals("priv-test",app.getPrivateKey());assertEquals(7L,newReview.getAppId());verify(reviews).insertReviewsRecord(newReview);verify(reviews).updateReviewsRecord(oldReview);assertFalse(service.updateAppPwa(app).isError());verify(gateway,times(1)).generateVapidKeys();verify(gateway).updateHost(eq("42"),contains("autow2a"));assertEquals("priv-test",app.getPrivateKey());
 }
}
