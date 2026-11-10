package org.jeecg.modules.packagemanagement;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import org.jeecg.modules.packagemanagement.application.openapi.*;
import org.jeecg.modules.packagemanagement.application.apps.IAppManagerService;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.domain.openapi.*;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.*;
import org.jeecg.modules.packagemanagement.controller.openapi.*;
import org.jeecg.modules.packagemanagement.config.OpenApiWebConfiguration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.servlet.AbstractShiroFilter;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.filter.mgt.PathMatchingFilterChainResolver;

class PackageOpenApiTest {
 private final Clock clock=Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC);
 private final OpenApiTokenMapper tokenMapper=mock(OpenApiTokenMapper.class);
 private final PackageIdentityRepository identities=mock(PackageIdentityRepository.class);
 private final OpenApiTokenService tokens=new OpenApiTokenService(tokenMapper,identities,clock);
 private final PackageActor actor=new PackageActor(1,"u1",12,false);
 private OpenApiToken token(){var t=new OpenApiToken();t.setId(10L);t.setToken("test-token");t.setStatus(1);t.setTenantId(1);t.setOwnerUserId("u1");return t;}
 @Test void requiresEnabledNonExpiredTokenAndActiveTenantMember(){
  var t=token();when(tokenMapper.byToken("test-token")).thenReturn(t);when(identities.activeMember(1,"u1")).thenReturn(true);
  assertSame(t,tokens.valid(" test-token "));assertNull(tokens.valid(" "));
  t.setStatus(0);assertNull(tokens.valid("test-token"));t.setStatus(1);
  t.setExpireTime(Date.from(clock.instant().minusSeconds(1)));assertNull(tokens.valid("test-token"));
  t.setExpireTime(null);when(identities.activeMember(1,"u1")).thenReturn(false);assertNull(tokens.valid("test-token"));
 }
 @Test void tokenCreateIgnoresCallerOwnershipAndGeneratesUuid(){
  var row=token();row.setId(99L);row.setToken(" ");row.setTenantId(3);row.setOwnerUserId("attacker");
  tokens.create(actor,row);assertNull(row.getId());assertEquals(1,row.getTenantId());assertEquals("u1",row.getOwnerUserId());assertTrue(row.getToken().matches("[a-f0-9]{32}"));assertEquals(Date.from(clock.instant()),row.getCreateTime());
 }
 @Test void foreignTenantOrOwnerCannotReadUpdateDeleteAndBatchIsAtomic(){
  var own=token();var foreign=token();foreign.setId(11L);foreign.setTenantId(2);
  when(tokenMapper.byId(10L)).thenReturn(own);when(tokenMapper.byId(11L)).thenReturn(foreign);
  assertThrows(PackageFailure.class,()->tokens.detail(actor,11));
  assertThrows(PackageFailure.class,()->tokens.update(actor,foreign));
  assertThrows(PackageFailure.class,()->tokens.delete(actor,List.of(10L,11L)));verify(tokenMapper,never()).delete(anyLong(),anyInt());
  own.setOwnerUserId("u2");assertThrows(PackageFailure.class,()->tokens.detail(actor,10));
 }
 @Test void listForcesTenantAndOwnerScope(){
  var row=token();row.setOwnerUserId("u2");row.setTenantId(2);tokens.list(actor,row);
  assertEquals(1,row.getTenantId());assertEquals("u1",row.getOwnerUserId());
 }
 @Test void apiRejectsLegacyOrDifferentTokenRowsEvenWhenRegistryAllowsThem(){
  var apps=mock(IAppManagerService.class);var access=mock(PackageAccess.class);var service=new OpenApiManagerService(apps,mock(StoreStatusMapper.class),access,mock(OpenApiManagerMapper.class));
  var row=new AppManager();row.setId(1L);row.setUserId(10L);row.setUserName("legacy");when(apps.selectAppManagerById(1L)).thenReturn(row);
  assertThrows(PackageFailure.class,()->service.detail(token(),1));row.setUserName("api");row.setUserId(11L);assertThrows(PackageFailure.class,()->service.detail(token(),1));
  row.setUserId(10L);assertSame(row,service.detail(token(),1));verify(access).requireRow(any(),eq("pkg_app_manager"),eq(1L));
 }
 @Test void publicListNeverTrustsUserIdAndUserNameFilters(){
  var apps=mock(IAppManagerService.class);var access=mock(PackageAccess.class);var service=new OpenApiManagerService(apps,mock(StoreStatusMapper.class),access,mock(OpenApiManagerMapper.class));
  var query=new AppManager();query.setUserId(99L);query.setUserName("legacy");when(apps.selectAppManagerList(query)).thenReturn(List.of());when(access.filter(any(),anyString(),anyList(),any())).thenReturn(List.of());
  service.list(token(),query);assertEquals(10L,query.getUserId());assertEquals("api",query.getUserName());
 }
 @Test void immutablePackageNameAndBatchOwnershipAreCheckedBeforeMutation(){
  var apps=mock(IAppManagerService.class);var access=mock(PackageAccess.class);var service=new OpenApiManagerService(apps,mock(StoreStatusMapper.class),access,mock(OpenApiManagerMapper.class));
  var own=new AppManager();own.setId(1L);own.setPackageCode("com.test");own.setUserId(10L);own.setUserName("api");when(apps.selectAppManagerById(1L)).thenReturn(own);
  var locked=mock(OpenApiManagerMapper.class);when(locked.lockById(1L)).thenReturn(own);var lockedService=new OpenApiManagerService(apps,mock(StoreStatusMapper.class),access,locked);
  var edit=new AppManager();edit.setId(1L);edit.setPackageCode("com.changed");assertThrows(PackageFailure.class,()->lockedService.update(token(),edit));verify(apps,never()).updateAppManager(any());
  assertThrows(PackageFailure.class,()->service.delete(token(),List.of(1L,2L)));verify(apps,never()).deleteAppManagerByIds(any());
 }
 @Test void realHttpTokenBoundaryRejectsMissingDisabledAndExpiredHeaders()throws Exception {
  var logs=mock(OpenApiCallLogService.class);var mvc=MockMvcBuilders.standaloneSetup(new OpenApiManagerController(mock(OpenApiManagerService.class),logs)).addFilters(new OpenApiAuditFilter(logs)).addInterceptors(new OpenApiTokenInterceptor(tokens,logs)).build();
  var missing=mvc.perform(get("/openapi/manager/list")).andReturn().getResponse();assertEquals(401,missing.getStatus());assertTrue(missing.getContentAsString().contains("X-Api-Token"));
  var t=token();t.setStatus(0);when(tokenMapper.byToken("test-token")).thenReturn(t);
  assertEquals(401,mvc.perform(get("/openapi/manager/list").header("X-Api-Token","test-token")).andReturn().getResponse().getStatus());
  t.setStatus(1);t.setExpireTime(Date.from(clock.instant().minusSeconds(1)));
  assertEquals(401,mvc.perform(get("/openapi/manager/list").header("X-Api-Token","test-token")).andReturn().getResponse().getStatus());
  verify(logs,times(3)).record(anyString(),anyString(),anyString(),nullable(OpenApiToken.class));
 }
 @Test void publicResponseKeepsSourcePaginationContractAndRecordsExactlyOnce()throws Exception {
  var t=token();when(tokenMapper.byToken("test-token")).thenReturn(t);when(identities.activeMember(1,"u1")).thenReturn(true);
  var service=mock(OpenApiManagerService.class);var logs=mock(OpenApiCallLogService.class);var row=new AppManager();row.setId(2L);row.setPackageCode("com.test");when(service.list(eq(t),any())).thenReturn(List.of(row));
  var mvc=MockMvcBuilders.standaloneSetup(new OpenApiManagerController(service,logs)).addFilters(new OpenApiAuditFilter(logs)).addInterceptors(new OpenApiTokenInterceptor(tokens,logs)).build();
  var response=mvc.perform(get("/openapi/manager/list").param("pageNum","1").header("X-Api-Token","test-token")).andReturn().getResponse();
  assertEquals(200,response.getStatus());var body=org.jeecg.modules.packagemanagement.controller.PackageJson.mapper().readTree(response.getContentAsString());assertEquals(200,body.path("code").asInt());assertEquals(1,body.path("total").asInt());assertEquals("com.test",body.path("rows").get(0).path("packageCode").asText());assertFalse(body.has("success"));verify(logs,times(1)).record(anyString(),anyString(),anyString(),eq(t));
 }
 @Test void callLogsScopeAndTruncateAndIgnoreStorageFailures(){
  var mapper=mock(OpenApiCallLogMapper.class);var service=new OpenApiCallLogService(mapper,clock);var query=new OpenApiCallLog();query.setTenantId(2);query.setOwnerUserId("foreign");service.list(actor,query);assertEquals(1,query.getTenantId());assertEquals("u1",query.getOwnerUserId());
  var captor=org.mockito.ArgumentCaptor.forClass(OpenApiCallLog.class);service.record("/openapi/manager", "x".repeat(20000),"{}",token());verify(mapper).insert(captor.capture());assertEquals(10L,captor.getValue().getTokenId());assertTrue(captor.getValue().getRequestBody().endsWith("...(truncated)"));assertEquals(1,captor.getValue().getTenantId());
  doThrow(new IllegalStateException()).when(mapper).insert(any());assertDoesNotThrow(()->service.record("/x",null,null,token()));
 }
 @Test void factoryBeanBuildsExactOpenApiAnonymousChainAndRetainsManagementAuthentication()throws Exception {
  var factory=new ShiroFilterFactoryBean();factory.setSecurityManager(new DefaultWebSecurityManager());var definitions=new LinkedHashMap<String,String>();definitions.put("/package-admin/**","authc");definitions.put("/**","authc");factory.setFilterChainDefinitionMap(definitions);
  OpenApiWebConfiguration.packageOpenApiShiroBoundary().postProcessBeforeInitialization(factory,"shiroFilter");
  var filter=(AbstractShiroFilter)factory.getObject();var resolver=(PathMatchingFilterChainResolver)filter.getFilterChainResolver();var manager=resolver.getFilterChainManager();
  assertTrue(manager.getChainNames().contains("/openapi/manager/**"));assertEquals("anon",factory.getFilterChainDefinitionMap().get("/openapi/manager/**"));assertEquals("authc",factory.getFilterChainDefinitionMap().get("/package-admin/**"));assertEquals("authc",factory.getFilterChainDefinitionMap().get("/**"));assertEquals("/openapi/manager",factory.getFilterChainDefinitionMap().keySet().iterator().next());
 }
 @Test void createUsesTokenOwnerAndClaimsAppAndNewStoreInTheSameTenant(){
  var apps=mock(IAppManagerService.class);var stores=mock(StoreStatusMapper.class);var access=mock(PackageAccess.class);var locks=mock(OpenApiManagerMapper.class);var service=new OpenApiManagerService(apps,stores,access,locks);
  var createdStore=new org.jeecg.modules.packagemanagement.domain.store.StoreStatus();createdStore.setId(20L);
  when(stores.selectStoreStatusByPackageName("com.test")).thenReturn(null,createdStore);
  when(apps.insertAppManager(any())).thenAnswer(inv->{((AppManager)inv.getArgument(0)).setId(30L);return 1;});
  var row=new AppManager();row.setPackageCode(" com.test ");row.setUserId(999L);row.setUserName("forged");assertEquals(1,service.create(token(),row));
  assertEquals(10L,row.getUserId());assertEquals("api",row.getUserName());assertEquals("com.test",row.getPackageCode());
  verify(access).claimCreated(eq(new PackageActor(1,"u1",0,false)),eq("pkg_app_manager"),eq(30L));verify(access).claimCreated(any(),eq("pkg_store_status"),eq(20L));
 }
 @Test void publicCreateCannotLinkToAStoreFromAnotherTenant(){
  var apps=mock(IAppManagerService.class);var stores=mock(StoreStatusMapper.class);var access=mock(PackageAccess.class);var locks=mock(OpenApiManagerMapper.class);var service=new OpenApiManagerService(apps,stores,access,locks);
  var existing=new org.jeecg.modules.packagemanagement.domain.store.StoreStatus();existing.setId(7L);when(stores.selectStoreStatusByPackageName("com.test")).thenReturn(existing);
  var row=new AppManager();row.setPackageCode("com.test");assertThrows(PackageFailure.class,()->service.create(token(),row));verify(apps,never()).insertAppManager(any());
 }
 @Test void lockedCurrentOwnerIsRecheckedBeforeUpdate(){
  var apps=mock(IAppManagerService.class);var stores=mock(StoreStatusMapper.class);var access=mock(PackageAccess.class);var locks=mock(OpenApiManagerMapper.class);var service=new OpenApiManagerService(apps,stores,access,locks);
  var old=new AppManager();old.setId(1L);old.setPackageCode("com.test");old.setUserId(10L);old.setUserName("api");when(apps.selectAppManagerById(1L)).thenReturn(old);
  var changed=new AppManager();changed.setId(1L);changed.setUserId(11L);changed.setUserName("api");when(locks.lockById(1L)).thenReturn(changed);
  var input=new AppManager();input.setId(1L);assertThrows(PackageFailure.class,()->service.update(token(),input));verify(apps,never()).updateAppManager(any());
 }
 @Test void malformedPublicBodyRetainsSourceErrorEnvelopeAndGetsLogged()throws Exception {
  var t=token();when(tokenMapper.byToken("test-token")).thenReturn(t);when(identities.activeMember(1,"u1")).thenReturn(true);var logs=mock(OpenApiCallLogService.class);
  var mvc=MockMvcBuilders.standaloneSetup(new OpenApiManagerController(mock(OpenApiManagerService.class),logs)).setControllerAdvice(new OpenApiErrorAdvice()).addFilters(new OpenApiAuditFilter(logs)).addInterceptors(new OpenApiTokenInterceptor(tokens,logs)).build();
  var response=mvc.perform(post("/openapi/manager").header("X-Api-Token","test-token").contentType("application/json").content("not-json")).andReturn().getResponse();
  assertEquals(200,response.getStatus());assertTrue(response.getContentAsString().contains("\"code\":500"));assertFalse(response.getContentAsString().contains("\"success\""));verify(logs).record(anyString(),anyString(),anyString(),eq(t));
 }

 @Test void errorAuditKeepsOriginalMalformedBodyAndExactWireResponse()throws Exception {
  var t=token();when(tokenMapper.byToken("test-token")).thenReturn(t);when(identities.activeMember(1,"u1")).thenReturn(true);var logs=mock(OpenApiCallLogService.class);
  var mvc=MockMvcBuilders.standaloneSetup(new OpenApiManagerController(mock(OpenApiManagerService.class),logs)).setControllerAdvice(new OpenApiErrorAdvice()).addFilters(new OpenApiAuditFilter(logs)).addInterceptors(new OpenApiTokenInterceptor(tokens,logs)).build();
  var response=mvc.perform(post("/openapi/manager").header("X-Api-Token","test-token").contentType("application/json").content("not-json")).andReturn().getResponse();
  var req=org.mockito.ArgumentCaptor.forClass(String.class);var res=org.mockito.ArgumentCaptor.forClass(String.class);verify(logs).record(anyString(),req.capture(),res.capture(),eq(t));
  assertTrue(req.getValue().contains("not-json"),"audit lost original malformed JSON body");assertEquals(response.getContentAsString(),res.getValue(),"audit must contain the actual source error envelope");
 }

 @Test void rejectedKnownAndUnknownTokenAuditHasSafeOriginalRequestAndExactResponse()throws Exception {
  var logs=mock(OpenApiCallLogService.class);var t=token();t.setStatus(0);when(tokenMapper.byToken("test-token")).thenReturn(t);
  var mvc=MockMvcBuilders.standaloneSetup(new OpenApiManagerController(mock(OpenApiManagerService.class),logs)).setControllerAdvice(new OpenApiErrorAdvice()).addFilters(new OpenApiAuditFilter(logs)).addInterceptors(new OpenApiTokenInterceptor(tokens,logs)).build();
  var response=mvc.perform(post("/openapi/manager").queryParam("trace","abc").header("X-Api-Token","test-token").contentType("application/json").content("{\"packageCode\":\"com.test\"}")).andReturn().getResponse();
  var request=org.mockito.ArgumentCaptor.forClass(String.class);var result=org.mockito.ArgumentCaptor.forClass(String.class);verify(logs).record(eq("/openapi/manager?trace=abc"),request.capture(),result.capture(),eq(t));
  var tree=org.jeecg.modules.packagemanagement.controller.PackageJson.mapper().readTree(request.getValue());assertEquals("{\"packageCode\":\"com.test\"}",tree.path("body").asText());assertEquals("trace=abc",tree.path("query").asText());assertEquals("disabled_expired_or_inactive_token",tree.path("authentication").asText());assertFalse(request.getValue().contains("test-token"));assertEquals(response.getContentAsString(),result.getValue());
  reset(logs);var unknown=mvc.perform(post("/openapi/manager").header("X-Api-Token","unknown-secret").contentType("application/json").content("raw-body")).andReturn().getResponse();
  verify(logs).record(anyString(),request.capture(),result.capture(),isNull());assertEquals(401,unknown.getStatus());assertTrue(request.getValue().contains("unknown_token"));assertTrue(request.getValue().contains("raw-body"));assertFalse(request.getValue().contains("unknown-secret"));assertEquals(unknown.getContentAsString(),result.getValue());
 }
 @Test void typedBindingFailureIsAuditedAsExactCode500RatherThanHttp200()throws Exception {
  var t=token();when(tokenMapper.byToken("test-token")).thenReturn(t);when(identities.activeMember(1,"u1")).thenReturn(true);var logs=mock(OpenApiCallLogService.class);
  var mvc=MockMvcBuilders.standaloneSetup(new OpenApiManagerController(mock(OpenApiManagerService.class),logs)).setControllerAdvice(new OpenApiErrorAdvice()).addFilters(new OpenApiAuditFilter(logs)).addInterceptors(new OpenApiTokenInterceptor(tokens,logs)).build();
  var response=mvc.perform(get("/openapi/manager/list").queryParam("pageNum","invalid").header("X-Api-Token","test-token")).andReturn().getResponse();
  var request=org.mockito.ArgumentCaptor.forClass(String.class);var result=org.mockito.ArgumentCaptor.forClass(String.class);verify(logs).record(eq("/openapi/manager/list?pageNum=invalid"),request.capture(),result.capture(),eq(t));
  assertEquals(response.getContentAsString(),result.getValue());assertTrue(result.getValue().contains("\"code\":500"));assertTrue(request.getValue().contains("pageNum=invalid"));assertFalse(result.getValue().contains("请求未完成"));
 }
 @Test void publicDatesUseSourceIsoCodecAndDateFiltersAcceptSourcePatterns()throws Exception {
  var t=token();when(tokenMapper.byToken("test-token")).thenReturn(t);when(identities.activeMember(1,"u1")).thenReturn(true);var logs=mock(OpenApiCallLogService.class);var service=mock(OpenApiManagerService.class);
  var app=new AppManager();app.setId(2L);app.setCreateDate(new Date(0));app.setUpdateDate(Date.from(Instant.parse("2026-11-10T01:02:03Z")));when(service.list(eq(t),any())).thenReturn(List.of(app));when(service.detail(t,2L)).thenReturn(app);
  var mvc=MockMvcBuilders.standaloneSetup(new OpenApiManagerController(service,logs)).setControllerAdvice(new OpenApiErrorAdvice()).addFilters(new OpenApiAuditFilter(logs)).addInterceptors(new OpenApiTokenInterceptor(tokens,logs)).build();
  String expected="\""+java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx").withZone(ZoneId.systemDefault()).format(Instant.EPOCH)+"\"";
  var response=mvc.perform(get("/openapi/manager/2").header("X-Api-Token","test-token")).andReturn().getResponse();assertTrue(response.getContentAsString().contains("\"createDate\":"+expected));assertTrue(response.getContentAsString().matches("(?s).*updateDate\":\"[^\"]+T[^\"]+.*"));
  for(String pattern:List.of("2026-11-10","2026-11-10 12:13:14","2026/11/10","2026.11.10","2026-11")){
   var result=mvc.perform(get("/openapi/manager/list").param("createDate",pattern).header("X-Api-Token","test-token")).andReturn().getResponse();assertTrue(result.getContentAsString().contains("\"code\":200"),result.getContentAsString());
  }
  var query=org.mockito.ArgumentCaptor.forClass(AppManager.class);verify(service,times(5)).list(eq(t),query.capture());assertTrue(query.getAllValues().stream().allMatch(q->q.getCreateDate()!=null));
 }

 @Test void auditWrappingPreservesSuccessfulJsonBodyForControllerBinding()throws Exception {
  var t=token();when(tokenMapper.byToken("test-token")).thenReturn(t);when(identities.activeMember(1,"u1")).thenReturn(true);var logs=mock(OpenApiCallLogService.class);var service=mock(OpenApiManagerService.class);when(service.create(eq(t),any())).thenReturn(1);
  var mvc=MockMvcBuilders.standaloneSetup(new OpenApiManagerController(service,logs)).setControllerAdvice(new OpenApiErrorAdvice()).addFilters(new OpenApiAuditFilter(logs)).addInterceptors(new OpenApiTokenInterceptor(tokens,logs)).build();
  String original="{ \"packageCode\" : \"com.test\", \"status\": 2 }";var response=mvc.perform(post("/openapi/manager").header("X-Api-Token","test-token").contentType("application/json").content(original)).andReturn().getResponse();
  var row=org.mockito.ArgumentCaptor.forClass(AppManager.class);verify(service).create(eq(t),row.capture());assertEquals("com.test",row.getValue().getPackageCode());assertEquals(2,row.getValue().getStatus());
  var request=org.mockito.ArgumentCaptor.forClass(String.class);var result=org.mockito.ArgumentCaptor.forClass(String.class);verify(logs).record(anyString(),request.capture(),result.capture(),eq(t));assertEquals(original,org.jeecg.modules.packagemanagement.controller.PackageJson.mapper().readTree(request.getValue()).path("body").asText());assertEquals(response.getContentAsString(),result.getValue());assertTrue(result.getValue().contains("\"code\":200"));
 }

}
