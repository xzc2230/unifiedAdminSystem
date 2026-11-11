package org.jeecg.modules.packagemanagement;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.jeecg.modules.packagemanagement.application.apps.support.*;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppManagerMapper;
import org.jeecg.modules.packagemanagement.application.apps.impl.AppManagerServiceImpl;
import org.springframework.test.util.ReflectionTestUtils;
class PackageDevFieldAndRiskTest {
 @Test void moduleEditFallsBackOnlyWithoutAnyGranularGrant(){
  var old=new AppManager();old.setUrl("before");var request=new AppManager();request.setUrl("after");
  var general=new AppManagerFieldPermissionPolicy(Set.of("package:apps:edit")::contains);assertDoesNotThrow(()->general.check(request,old));
  var limited=new AppManagerFieldPermissionPolicy(Set.of("package:apps:edit","package:apps:editField:appName")::contains);
  assertThrows(org.jeecg.modules.packagemanagement.exception.PackageFailure.class,()->limited.check(request,old));
  request.setUrl("before");request.setAppName("name");assertDoesNotThrow(()->limited.check(request,old));
  request.setPlatform("ios");assertDoesNotThrow(()->limited.check(request,old));
 }
 @Test void queryMaskPreservesIdentityAndOnlyKeepsGrantedFields(){
  var row=new AppManager();row.setId(42L);row.setPackageCode("com.test");row.setUrl("secret");row.setUserName("owner");row.setPlatform("ios");row.setAppleAppId("123456");
  var p=new AppManagerFieldPermissionPolicy(Set.of("package:apps:query","package:apps:queryField:packageCode")::contains);p.mask(row);
  assertEquals(42L,row.getId());assertEquals("com.test",row.getPackageCode());assertEquals("ios",row.getPlatform());assertEquals("123456",row.getAppleAppId());assertNull(row.getUrl());assertNull(row.getUserName());
 }
 @Test void asnNormalizationHasSource32BitBounds(){
  assertEquals("123",AsnLookup.normalize(" AS00123 "));assertEquals("4294967295",AsnLookup.normalize("4294967295"));
  for(String s:List.of("0","4294967296","-1","ASxx","1;drop"))assertNull(AsnLookup.normalize(s));
 }
 @Test void clientLookupTrimsAndRejectsBlankBeforeDatabase(){
  var mapper=mock(AppManagerMapper.class);var service=new AppManagerServiceImpl();ReflectionTestUtils.setField(service,"appManagerMapper",mapper);
  var row=new AppManager();when(mapper.selectAppManagerByPackageName("com.test")).thenReturn(row);
  assertSame(row,service.selectAppManagerByPackageName(" com.test "));assertNull(service.selectAppManagerByPackageName(" "));verify(mapper,never()).selectAppManagerByPackageName(" ");
 }

 @Test void riskCrudDefaultsAndAutoBlockOwnershipAreEnforced()throws Exception{
  var guard=mock(org.jeecg.modules.packagemanagement.controller.admin.PackageAdminSupport.class);
  var access=mock(org.jeecg.modules.packagemanagement.infrastructure.identity.PackageAccess.class);
  var actor=new org.jeecg.modules.packagemanagement.infrastructure.identity.PackageActor(1,"owner",1L,false);
  when(guard.actor(eq("risk"),anyString())).thenReturn(actor);
  var redis=mock(org.jeecg.modules.packagemanagement.application.apps.risk.app2.App2PkgRiskRedisService.class);
  var asn=mock(AsnLookup.class);
  var a=mock(org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppAsnBlacklistMapper.class);
  var b=mock(org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppDeviceBrandBlacklistMapper.class);
  var c=mock(org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppCpuAbiBlacklistMapper.class);
  var d=mock(org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppDeviceNameBlacklistMapper.class);
  when(b.insertAppDeviceBrandBlacklist(any())).thenReturn(1);
  var controller=new org.jeecg.modules.packagemanagement.controller.admin.PackageRiskController(guard,access,redis,asn,a,b,c,d);
  controller.add("brand",Map.of("brandKeyword","cloud","createBy","forged"));
  var captured=org.mockito.ArgumentCaptor.forClass(org.jeecg.modules.packagemanagement.domain.app.AppDeviceBrandBlacklist.class);
  verify(b).insertAppDeviceBrandBlacklist(captured.capture());var row=captured.getValue();
  assertEquals("contains",row.getMatchMode());assertEquals("block",row.getAction());assertEquals(1,row.getEnabled());assertEquals("owner",row.getCreateBy());
  doThrow(new org.jeecg.modules.packagemanagement.exception.PackageFailure(403,"other owner")).when(access).requireLogPackage(actor,"foreign");
  assertThrows(org.jeecg.modules.packagemanagement.exception.PackageFailure.class,()->controller.automatic("foreign",null,null));verifyNoInteractions(redis);
  when(redis.removeAutoBlock("com.test","gaid","v")).thenReturn(true);assertTrue(controller.unblock("com.test",null,"gaid","v").getResult());
  assertThrows(org.jeecg.modules.packagemanagement.exception.PackageFailure.class,()->controller.automatic("com.*",null,null));
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
  when(b.selectAppDeviceBrandBlacklistList(any())).thenReturn(List.of(row));
  var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/package-admin/risk/brand/list")).andReturn().getResponse();
  assertEquals(200,response.getStatus());assertTrue(response.getContentAsString().contains("cloud"));
 }

 @Test void malformedPackstatUrlDoesNotAbortOtherPackages(){
  var client=new org.jeecg.modules.packagemanagement.application.packstat.detect.PackStatHttpClient();
  assertEquals(org.jeecg.modules.packagemanagement.application.packstat.detect.PackStatDetectResult.INVALID,assertDoesNotThrow(()->client.probe("bad")).getResult());
 }
 @Test void roleSessionBoundaryMustBePresent(){assertDoesNotThrow(()->Class.forName("org.jeecg.modules.packagemanagement.infrastructure.identity.PackageRoleSessionInvalidator"));}

 @Test void roleMutationInvalidatesOnlyAffectedOtherSessions(){
  var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);var redis=mock(org.springframework.data.redis.core.StringRedisTemplate.class);
  when(jdbc.queryForList(contains("username"),eq(String.class),eq("role1"))).thenReturn(List.of("alice"));
  when(jdbc.queryForList(contains("SELECT DISTINCT user_id"),eq(String.class),eq("role1"))).thenReturn(List.of("alice-id"));
  var tokens=mock(org.springframework.data.redis.core.Cursor.class);var auth=mock(org.springframework.data.redis.core.Cursor.class);
  when(redis.scan(any(org.springframework.data.redis.core.ScanOptions.class))).thenReturn(tokens,auth);
  String prefix=org.jeecg.common.constant.CommonConstant.PREFIX_USER_TOKEN;
  when(tokens.hasNext()).thenReturn(true,true,true,false);when(tokens.next()).thenReturn(prefix+"keep",prefix+"other",prefix+"bob");when(auth.hasNext()).thenReturn(false);
  var service=new org.jeecg.modules.packagemanagement.infrastructure.identity.PackageRoleSessionInvalidator(jdbc,redis,token->token.equals("bob")?"bob":"alice");
  service.invalidateRole("role1","keep");verify(redis).delete(prefix+"other");verify(redis,never()).delete(prefix+"keep");verify(redis,never()).delete(prefix+"bob");verify(redis).delete(org.jeecg.common.constant.CommonConstant.PREFIX_USER_SHIRO_CACHE+"alice-id");
 }

 @Test void administrativeRequestLogFormattingPreservesInvalidText(){
  var adapter=mock(org.jeecg.modules.packagemanagement.infrastructure.integration.ElasticsearchAppLogAdapter.class);
  var log=new org.jeecg.modules.packagemanagement.domain.applog.AppLog();log.setReqParams("{\"a\":1}");log.setReqIp("(us)203.0.113.8");
  when(adapter.query(any(),anyInt(),anyInt(),any())).thenReturn(new org.jeecg.modules.packagemanagement.application.port.AppLogPort.Page(List.of(log),1));
  var service=new org.jeecg.modules.packagemanagement.application.applog.EsAppLogServiceImpl(adapter);
  var listed=service.list(new org.jeecg.modules.packagemanagement.domain.applog.AppLog(),1,10,List.of("com.test")).rows().get(0);
  assertTrue(listed.getReqParams().contains("\n"));
  assertEquals("(美国)203.0.113.8",listed.getReqIp());
  log.setReqParams("plain");assertEquals("plain",service.list(new org.jeecg.modules.packagemanagement.domain.applog.AppLog(),1,10,List.of("com.test")).rows().get(0).getReqParams());
  log.setReqIp("(br)203.0.113.8");when(adapter.findById("1")).thenReturn(java.util.Optional.of(log));
  assertEquals("(巴西)203.0.113.8",service.findById("1").orElseThrow().getReqIp());
  log.setReqIp("(sg)203.0.113.8");when(adapter.export(any(),any())).thenReturn(List.of(log));
  assertEquals("(新加坡)203.0.113.8",service.export(new org.jeecg.modules.packagemanagement.domain.applog.AppLog(),List.of("com.test")).get(0).getReqIp());
 }
}
