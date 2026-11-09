# Package API migration — Task 2 application/integration/identity handoff

Date: 2026-11-09. Base: Task 1 commit `603c8f6`. Changes are uncommitted. This task adds application services, external integration adapters, management identity primitives, and a store job. Controllers and frontend remain Task 3/4.

## Final review corrections

- Geo landing lookup now returns the configured landing fallback on malformed/missing `as` or external lookup failure. A Facebook match uses `facebookBlockedUrl` when provided and otherwise the landing fallback; it cannot default to an empty redirect.
- ES date storage/readback uses **UTC**, restoring the actual source Spring Data Elasticsearch 4.2.12 converter. Evidence is `.runtime/package-task2/spring-data-elasticsearch-4.2.12-sources.jar`: `SimpleElasticsearchPersistentProperty.java:228` calls the Date converter; `ElasticsearchDateConverter.java:110`, `:265`, `:291` convert Date/Instant and format/parse the custom no-zone pattern as UTC. Jackson's GMT+8 display annotation never controlled source ES storage.
- ES list queries keep Shanghai-formatted range values with `time_zone=Asia/Shanghai`. Source builder/export deliberately have **no time_zone**, so `exportQueryBody` and the facade's builder preserve that separate contract. Tests cover UTC save/readback, list narrow-range comparison across Shanghai midnight, and export/list range differences.
- Push global VAPID keys are validated outside the per-subscription try/catch through `PushPort.validateKeys(String,String)`. Invalid global keys produce legacy failure; per-subscription errors still preserve the source overall-success behavior.
- `job.PackageStoreJob` implements `org.quartz.Job`, provides a public no-arg constructor for the host's reflective creation, retains `ryGetStatus()`, and resolves the service through host SpringContextUtils when constructed by Quartz. `execute(JobExecutionContext)` delegates and wraps failure as JobExecutionException without automatic immediate retry. A test checks Job execution and reflective no-argument construction. Actual schedule creation remains Task 3/deployment.
- New identity/integration methods were expanded from compressed one-line bodies for review. The configuration test's duplicate mapper warnings are expected from pre-registering mock mappers before the real mapper scan; startup still verifies one ClientService bean and no module ObjectMapper bean. Existing compiler/Mockito agent warnings and deliberate failure-path logs are retained; output is not claimed warning-free.

## Implemented behavior

- Source `app`, `statistics`, `store`, `event`, `pwa`, `notify` services and system `AppEventRecord` / `ReviewsRecord` services retain interface names, method names, domain types, SQL mapper calls, branch order, and response contents. The thirteen SQL/protocol service implementations are source transplants, not generic CRUD stand-ins.
- `ClientServiceImpl` retains all eleven protocols: `api`, `login`, `redirected`, `getBanner`, `checkUrl`, `dynamicAdj`, `getUserInfo`, `base64Data`, `base64Data2`, `base64Data3`, `app1`. Its dependency changes are Jakarta servlet types, module domain/service imports, `GeoPort`, `AppLogPort`, a qualified `packageClock`, and configured default AES key. Original request URI is read unchanged; Task 3 must supply the externally visible original URI when an internal proxy prefix is added.
- Correction to earlier brief: actual source `checkUrl` reads `tokenID` / `baseTime` request headers and uses their values as parameter names. Only `app1` uses `ParamsUtils` to select the first parameter value containing a dot. The test's default checkUrl case supplies selector headers but omits their selected parameters, matching this source contract.
- `AppManagerServiceImpl` rejects a duplicate package with -1 and creates missing store status rows. Statistics increments the source misspelled `reigisterNum`, `paymentNum`, adds amount and rounds HALF_UP to scale 2. Store probes exclude status 2; only 0→1 and 1→2 change dates/send email. Email errors are reported and do not stop subsequent packages, matching the source helper's behavior.
- PWA creation invokes external VAPID generation, NPM host creation, preserves NPM `linkId`, assigns `Snowflake(1,1,true)`, and inserts/updates nested reviews after successful PWA insertion. Update preserves existing keys (does not invoke key generation), updates NPM linkId, and saves nested reviews. Delete invokes NPM removal per selected linkId. Source duplicate-host and nested-review conditions are retained.
- Push keeps the source partial-failure behavior: individual subscription exceptions are caught and processing continues, so the overall result can still be success. Missing subscriptions produces a legacy error. A service-private Jackson2 mapper encodes only legacy push messages; **no ObjectMapper bean is added to the host**.
- FB preserves event persistence → URL/query parsing → conditional remote send → success/failure EventError feedback. The HTTP payload matches source fields: client IP/UA, content id product123/quantity 1/home_delivery, currency/value, event name/time, website action source. It intentionally does not introduce fbc/fbp fields absent from the source SDK payload.
- Landing behavior remains `IEventConfigService.page`: the unusual `!ua.contains("Android") || !ua.contains("android")` condition is retained and tested.
- Active logs use index `applog` through `ElasticsearchAppLogAdapter`, retaining UUIDv7 IDs, source keyword fields and custom reqTime format. First save lazily creates the legacy index mapping when missing. Save/get, wildcard package filtering, Shanghai time range, descending reqTime, exact total hits, deletion, filtered clean, and scroll-based full export are implemented. `AppLog.setId(String)` was added to preserve remote IDs on deserialization. Missing ES configuration raises a concrete 503 rather than claiming persistence. Protocol request logging remains best-effort as in source and reports adapter failures.

## Exact application packages and methods

Root package for all entries: `org.jeecg.modules.packagemanagement`.
Domain and mapper types remain the Task 1 mapping. Implementations reside under the listed application's `.impl` package (except the ES facade shown separately).

### `org.jeecg.modules.packagemanagement.application.apps.IAppManagerService`

```java
public AppManager selectAppManagerById(Long id);
public AppManager selectAppManagerByPackageName(String packageName);
public List<AppManager> selectAppManagerList(AppManager appManager);
public int insertAppManager(AppManager appManager);
public int updateAppManager(AppManager appManager);
public int deleteAppManagerByIds(Long[] ids);
public int deleteAppManagerById(Long id);
public int transferAppManagerById(String id, Integer userId,String userName);
```

### `org.jeecg.modules.packagemanagement.application.event.IAppEventRecordService`

```java
public AppEventRecord selectAppEventRecordByEventId(Long eventId);
public List<AppEventRecord> selectAppEventRecordList(AppEventRecord appEventRecord);
public int insertAppEventRecord(AppEventRecord appEventRecord);
public int updateAppEventRecord(AppEventRecord appEventRecord);
public int deleteAppEventRecordByEventIds(Long[] eventIds);
public int deleteAppEventRecordByEventId(Long eventId);
```

### `org.jeecg.modules.packagemanagement.application.event.IEventConfigService`

```java
public EventConfig selectEventConfigById(Long id);
public EventConfig selectEventConfigByPackageId(String packageId);
public List<EventConfig> selectEventConfigList(EventConfig eventConfig);
public int insertEventConfig(EventConfig eventConfig);
public int updateEventConfig(EventConfig eventConfig);
public int deleteEventConfigByIds(Long[] ids);
public int deleteEventConfigById(Long id);
public void page(@PathVariable String page, HttpServletRequest request, HttpServletResponse response) throws IOException;
```

### `org.jeecg.modules.packagemanagement.application.event.IEventErrorService`

```java
public EventError selectEventErrorById(Long id);
public List<EventError> selectEventErrorList(EventError eventError);
public int insertEventError(EventError eventError);
public int updateEventError(EventError eventError);
public int deleteEventErrorByIds(Long[] ids);
public int deleteEventErrorById(Long id);
```

### `org.jeecg.modules.packagemanagement.application.event.IEvnetHistoryService`

```java
public EvnetHistory selectEvnetHistoryById(Long id);
public List<EvnetHistory> selectEvnetHistoryList(EvnetHistory evnetHistory);
public int insertEvnetHistory(EvnetHistory evnetHistory);
public int updateEvnetHistory(EvnetHistory evnetHistory);
public int deleteEvnetHistoryByIds(Long[] ids);
public int deleteEvnetHistoryById(Long id);
public void fbEvent(@PathVariable String packageId, EvnetHistory fbEvent);
```

### `org.jeecg.modules.packagemanagement.application.event.ILoadCountService`

```java
public LoadCount selectLoadCountById(Long id);
public LoadCount selectLoadCountByHostName(String hostName);
public List<LoadCount> selectLoadCountList(LoadCount loadCount);
public int insertLoadCount(LoadCount loadCount);
public int updateLoadCount(LoadCount loadCount);
public int deleteLoadCountByIds(Long[] ids);
public int deleteLoadCountById(Long id);
```

### `org.jeecg.modules.packagemanagement.application.notification.IAppNotifyService`

```java
public AppNotify selectAppNotifyById(Long id);
public List<AppNotify> selectAppNotifyList(AppNotify appNotify);
public LegacyAjaxResult insertAppNotify(AppNotify appNotify);
public int updateAppNotify(AppNotify appNotify);
public int deleteAppNotifyByIds(Long[] ids);
public int deleteAppNotifyById(Long id);
```

### `org.jeecg.modules.packagemanagement.application.pwa.IAppPwaService`

```java
public AppPwa selectAppPwaById(Long id);
public List<AppPwa> selectAppPwaList(AppPwa appPwa);
public List<AppPwa> selectAppPwaAppList(AppPwa appPwa);
public LegacyAjaxResult insertAppPwa(AppPwa appPwa);
public LegacyAjaxResult updateAppPwa(AppPwa appPwa);
public int deleteAppPwaByIds(Long[] ids);
public int deleteAppPwaById(Long id);
```

### `org.jeecg.modules.packagemanagement.application.pwa.IAppPwaSubService`

```java
public AppPwaSub selectAppPwaSubById(Long id);
public List<AppPwaSub> selectAppPwaSubList(AppPwaSub appPwaSub);
public int insertAppPwaSub(AppPwaSub appPwaSub);
public int updateAppPwaSub(AppPwaSub appPwaSub);
public int deleteAppPwaSubByIds(Long[] ids);
public int deleteAppPwaSubById(Long id);
```

### `org.jeecg.modules.packagemanagement.application.pwa.IReviewsRecordService`

```java
public ReviewsRecord selectReviewsRecordByReviewsId(Long reviewsId);
public List<ReviewsRecord> selectReviewsRecordList(ReviewsRecord reviewsRecord);
public int insertReviewsRecord(ReviewsRecord reviewsRecord);
public int updateReviewsRecord(ReviewsRecord reviewsRecord);
public int deleteReviewsRecordByReviewsIds(Long[] reviewsIds);
public int deleteReviewsRecordByReviewsId(Long reviewsId);
```

### `org.jeecg.modules.packagemanagement.application.statistics.IAppStatisticsService`

```java
public AppStatistics selectAppStatisticsById(Long id);
public List<AppStatistics> selectAppStatisticsList(AppStatistics appStatistics);
public AppStatistics selectAppStatisticsByPackageName(String packageName);
public int insertAppStatistics(AppStatistics appStatistics);
public int updateAppStatistics(AppStatistics appStatistics);
public Object uploadData(HttpServletRequest request);
public Object getDetails(HttpServletRequest request);
public int deleteAppStatisticsByIds(Long[] ids);
public int deleteAppStatisticsById(Long id);
```

### `org.jeecg.modules.packagemanagement.application.store.IStoreStatusService`

```java
public StoreStatus selectStoreStatusById(Long id);
public List<StoreStatus> selectStoreStatusList(StoreStatus storeStatus);
public int insertStoreStatus(StoreStatus storeStatus);
public int updateStoreStatusAndDate(StoreStatus storeStatus);
public int deleteStoreStatusByIds(Long[] ids);
public int deleteStoreStatusById(Long id);
public int updateStoreStatus(StoreStatus storeStatus);
public void getStatus();
```

### `org.jeecg.modules.packagemanagement.application.apps.ClientService`

```java
Object api(HttpServletRequest request);
Object login(HttpServletRequest request);
void redirected(HttpServletRequest request, HttpServletResponse response);
Object getBanner(HttpServletRequest request);
Object checkUrl(HttpServletRequest request);
Object dynamicAdj(HttpServletRequest request);
Object getUserInfo(HttpServletRequest request);
Object base64Data(HttpServletRequest request);
Object base64Data2(HttpServletRequest request);
Object base64Data3(HttpServletRequest request);
Object app1(HttpServletRequest request);
```

### ES facade and port

`application.applog.EsAppLogServiceImpl` exposes:

```java
Map<String,Object> builder(AppLog filter);
AppLogPort.Page list(AppLog filter, int page, int size, Collection<String> packages);
List<AppLog> export(AppLog filter, Collection<String> packages);
Optional<AppLog> findById(String id);
AppLog save(AppLog log);
void deleteAllById(Iterable<String> ids);
long clean(AppLog filter, Collection<String> packages);
```

`AppLogPort.Page` has `rows()` and `total()`. `null` package scope means trusted internal global access; management must always pass `PackageAccess.logPackages(actor)`, including the empty list (which becomes match_none). Detail/delete must load every log and validate all package owners before mutating any. ES side effects cannot participate in a SQL rollback; Task 3 must validate the entire batch first and surface partial external failures. Page numbers are 1-based. ES ordinary pagination remains subject to the server result-window limit; export uses scroll and cleans the scroll cursor.

The inactive source SQL AppLogMapper/IAppLogService path was not revived; all active request logging uses ES. No obsolete Boot2 ES client or RuoYi security dependency is introduced.

## Management identity contract for Task 3

Types: `infrastructure.identity.PackageActor`, `PackageAccess`, `PackageIdentityRepository`.

```java
record PackageActor(int tenantId, String userId, long legacyUserId, boolean administrator) {}
PackageActor PackageAccess.current();
PackageActor PackageAccess.resolve(String user, String tenant, boolean administrator);
Set<Long> scope(PackageActor actor, String resourceType);
List<Long> filterIds(PackageActor actor, String type, Collection<Long> ids);
<T> List<T> filter(PackageActor actor, String type, Collection<T> rows, Function<T,Long> id);
void requireRow(PackageActor actor, String type, Long id);
void requireRows(PackageActor actor, String type, Collection<Long> ids);
void lockRows(PackageActor actor, String type, Collection<Long> ids);
void claimCreated(PackageActor actor, String type, long id);
long effectiveLegacyUserId(PackageActor actor);
int effectiveLegacyUserIdInt(PackageActor actor);
List<String> logPackages(PackageActor actor);
void requireLogPackage(PackageActor actor, String packageName);
void transfer(PackageActor actor, String type, Collection<Long> ids, String targetUser);
void removeOwners(PackageActor actor, String type, Collection<Long> ids);
```

- **Only `current()` is the HTTP management entry point.** It takes `LoginUser.id` from Shiro and tenant from TenantContext, then checks active sys_user_tenant/sys_tenant/sys_user states, deletion flags, begin/end dates. `resolve` and PackageActor constructors are trusted internal/testing APIs; never bind them or the admin boolean from a request.
- Admin scope is derived from host `isPermitted("package:*")`, after active tenant membership verification. It includes other owners only inside the selected valid tenant. Numeric legacy userId 1 and source role 101 confer no rights. The legacy mapping must be unique for that host user/tenant; absent or ambiguous mappings are denied.
- Resource type is the exact `pkg_*` table name from 001-schema.sql. All 12 business SQL tables are whitelisted. Ownership requires explicit `pkg_resource_owner`. A legacy row's user_id alone does not authorize access. Missing mappings deny management even for package administrators. Public global package/appCode queries remain independent of Shiro and tenant headers.
- Business services are deliberately internal/raw. They **do not** enforce management guards themselves. Controllers must check permission and current actor, clear/overwrite client-supplied owner fields, and apply resource scope before pagination/count/export/aggregate. Do not filter an already paged result and reuse its global count.
- New records: set numeric userId from `effectiveLegacyUserId` (AppManager/AppPwa/LoadCount/AppNotify/AppEventRecord use Long) or `effectiveLegacyUserIdInt` (AppStatistics uses Integer), execute insertion, then `claimCreated` in the **same outer transaction**. Claim auxiliary newly created store/review rows too. `claimCreated` is only for rows created by trusted code in that transaction; it is not an endpoint for claiming historical rows.
- Existing edits/deletes/batch operations: validate all rows with `requireRows`, and call `lockRows` inside the same outer transaction as subsequent SQL writes. Only after validation may effects occur. `removeOwners` is intended after successful business deletion in that transaction. Nested PWA review IDs must also be checked against their parent PWA and authorized registry entries before calling `updateAppPwa`, because the preserved source method updates the supplied review IDs. Do not let source payloads reparent another user's reviews/subscriptions/notifications.
- `transfer` is transactional: validates the entire batch, locks ownership rows, verifies target membership, requires a unique target legacy mapping, changes registry owner and numeric business user_id for tables that carry it. It is tenant-local only. AppManager's source `transferAppManagerById(String,Integer,String)` remains an internal compatibility method; Task 3 should use the ownership-aware transfer and populate display `userName` from verified host identity if required. Transfer does not implicitly cascade to other resource types: controllers must explicitly determine and authorize aggregate-related rows if exposing PWA aggregate transfer.
- ES ownership derives from the globally unique AppManager package code. `logPackages` joins pkg_app_manager to its explicit owner entries. Null/unknown/unmapped package logs are not manageable. Public protocols may still append logs for configured packages without a management login.

## External ports, configuration, and startup

### `application.port.AppLogPort`

```java
public interface AppLogPort {
record Page(java.util.List<org.jeecg.modules.packagemanagement.domain.applog.AppLog> rows,long total){}
 org.jeecg.modules.packagemanagement.domain.applog.AppLog save(org.jeecg.modules.packagemanagement.domain.applog.AppLog log);
 java.util.Optional<org.jeecg.modules.packagemanagement.domain.applog.AppLog> findById(String id);
 Page query(org.jeecg.modules.packagemanagement.domain.applog.AppLog filter,int page,int size,java.util.Collection<String> allowedPackages);
 void deleteAllById(java.lang.Iterable<String> ids);
 long clean(org.jeecg.modules.packagemanagement.domain.applog.AppLog filter,java.util.Collection<String> allowedPackages);
}
```

### `application.port.FacebookPort`

```java
public interface FacebookPort {
String send(org.jeecg.modules.packagemanagement.domain.event.EventConfig config,org.jeecg.modules.packagemanagement.domain.event.EvnetHistory event);
}
```

### `application.port.GeoPort`

```java
public interface GeoPort {
String getCityByIp(String ip); java.util.Map<String,String> getCountryAndCountryCodeByIP(String ip); String passFb(String ip,String uri);
 default String getIpAddress(jakarta.servlet.http.HttpServletRequest request){String ip=request.getHeader("X-Forwarded-For"); if(ip==null||ip.isEmpty()||"unknown".equalsIgnoreCase(ip)){for(String h:new String[]{"Proxy-Client-IP","WL-Proxy-Client-IP","HTTP_CLIENT_IP","HTTP_X_FORWARDED_FOR"}){ip=request.getHeader(h);if(ip!=null&&!ip.isEmpty()&&!"unknown".equalsIgnoreCase(ip))return ip;}return request.getRemoteAddr();}if(ip.length()>15){for(String part:ip.split(","))if(!"unknown".equalsIgnoreCase(part))return part;}return ip;}
}
```

### `application.port.HttpPort`

```java
public interface HttpPort {
record Reply(int status,String body) {public boolean success(){return status>=200&&status<300;}}
 Reply exchange(String method,String url,String body,java.util.Map<String,String> headers);
 default String json(String method,String url,String body,java.util.Map<String,String> headers){Reply r=exchange(method,url,body,headers);if(!r.success())throw new org.jeecg.modules.packagemanagement.exception.PackageFailure(502,"External HTTP status "+r.status());return r.body();}
}
```

### `application.port.MailPort`

```java
public interface MailPort {
void sendEmail(String recipients,String subject,String body);
}
```

### `application.port.PushPort`

```java
public interface PushPort {
void validateKeys(String publicKey,String privateKey) throws Exception;
void send(String publicKey,String privateKey,org.jeecg.modules.packagemanagement.domain.pwa.AppPwaSub subscription,byte[] payload) throws Exception;
}
```

### `application.port.PwaGateway`

```java
public interface PwaGateway {
String generateVapidKeys(); String createHost(String body); void updateHost(String linkId,String body); void deleteHost(String linkId);
}
```

### `application.port.StoreProbePort`

```java
public interface StoreProbePort {
boolean getStatus(String url);
}
```

Adapters: `JdkHttpAdapter`, `GeoHttpAdapter`, `NpmPwaAdapter`, `SmtpMailAdapter`, `FacebookHttpAdapter`, `WebPushAdapter`, `ElasticsearchAppLogAdapter`, all under `infrastructure.integration`.

Legacy helper replacements: AESUtils/ParamsUtils stay under `application.legacy`; IpAddrUtil→GeoPort/GeoHttpAdapter; HttpClientUtil→HttpPort/JdkHttpAdapter plus StoreProbePort; PwaUtils/NginxTools→PwaGateway/NpmPwaAdapter; MailUtils→MailPort/SmtpMailAdapter; AppLogUtils/ES repository→AppLogPort. These business helpers were handled rather than discarded with generator code. LegacyAjaxResult is a small LinkedHashMap with code/msg/data and isError, preserving public compatibility; management Result translation belongs to controllers.

Explicit entry configuration: `config.PackageManagementConfiguration`. Import it from the host/module configuration if not already within host scanning. It scans only this module's @Service classes, registers mapper scanning and integration/identity beans, and loads `classpath:package-management.properties`. It does not import/scan the old app, alter host authentication, register controller routes, or publish a generic ObjectMapper bean. The configuration test also performs an outer application component scan and verifies a single ClientService bean, covering duplicate scan registration. JDK HttpClient initialization is deferred until an actual outbound request. No startup token login, VAPID request, ES request, mail, or push is performed.

All configuration keys use `jeecg.package-management.*`; the resource maps them to the following environment variables. No production credentials or the source's default AES secret were copied.

```properties
# Import this file explicitly from the host profile when enabling package integrations.
# All optional integrations stay unconfigured until their PACKAGE_* variables are supplied.
jeecg.package-management.default-aes-key=${PACKAGE_DEFAULT_AES_KEY:}
jeecg.package-management.geo-url=${PACKAGE_GEO_URL:}
jeecg.package-management.geo-api-key=${PACKAGE_GEO_API_KEY:}
jeecg.package-management.facebook-blocked-url=${PACKAGE_FACEBOOK_BLOCKED_URL:}
jeecg.package-management.landing-fallback-url=${PACKAGE_LANDING_FALLBACK_URL:https://www.bababagame.com/}
jeecg.package-management.npm-base-url=${PACKAGE_NPM_BASE_URL:}
jeecg.package-management.npm-identity=${PACKAGE_NPM_IDENTITY:}
jeecg.package-management.npm-secret=${PACKAGE_NPM_SECRET:}
jeecg.package-management.vapid-url=${PACKAGE_VAPID_URL:}
jeecg.package-management.pwa-root=${PACKAGE_PWA_ROOT:/data/pwa/}
jeecg.package-management.smtp-host=${PACKAGE_SMTP_HOST:}
jeecg.package-management.smtp-user=${PACKAGE_SMTP_USER:}
jeecg.package-management.smtp-password=${PACKAGE_SMTP_PASSWORD:}
jeecg.package-management.smtp-from=${PACKAGE_SMTP_FROM:}
jeecg.package-management.store-recipients=${PACKAGE_STORE_RECIPIENTS:}
jeecg.package-management.es-url=${PACKAGE_ES_URL:}
jeecg.package-management.es-username=${PACKAGE_ES_USERNAME:}
jeecg.package-management.es-password=${PACKAGE_ES_PASSWORD:}
jeecg.package-management.facebook-base-url=${PACKAGE_FACEBOOK_BASE_URL:}
jeecg.package-management.push-subject=${PACKAGE_PUSH_SUBJECT:}
jeecg.package-management.smtp-port=${PACKAGE_SMTP_PORT:587}
jeecg.package-management.smtp-start-tls=${PACKAGE_SMTP_START_TLS:true}
```

`PACKAGE_GEO_URL` supports `{ip}` and `{key}` placeholders. NPM base URL excludes `/api`; token and proxy-host paths are appended by the adapter. Unauthorized NPM operations refresh the token once, then fail explicitly (no unbounded recursion or token logging). Set the Facebook base URL to a versioned Graph endpoint; the adapter appends /{pixel}/events and uses the package's stored access token. Mail default recipients are supplied only by configuration and per-row success/fail emails. Configure the original AES default through PACKAGE_DEFAULT_AES_KEY for clients relying on it; appName remains the first-priority AES key. The PwaBody domain helper retains the source proxy forwarding fields, while static root directories are configurable.

One essential dependency added: `nl.martijndwars:web-push:5.1.1`, pinned to source. It and missing transitive native runtime jars were fetched from the configured Maven mirror. Jakarta mail/Jackson2/Hutool/Quartz/JDBC were already provided by the host dependency graph. No source Spring Boot2 dependencies were copied.

## Verification evidence

**Final reviewed-code run:** `.runtime/package-task2/review-fixes-green.log`, **BUILD SUCCESS, 40 discovered / 38 passed / 2 opt-in database tests skipped, 0 failures or errors**. The separately credentialed real MySQL identity test passed as recorded below. New regression RED logs are `review-geo-es-red.log` (three actual failures: Geo fallback, UTC save, UTC read), `es-export-red.log` (builder incorrectly included time_zone), `push-key-red.log` (invalid global keys incorrectly returned success), and `job-red.log` (job did not implement Quartz Job). All are covered by this final GREEN run. The run includes 12 protocol, 6 business, 3 identity, 11 integration adapter, 1 configuration, 1 Quartz job, 4 persistence mapping, and 2 opt-in database tests. No concurrent Maven build was running for this final verification.

Focused command from repository root (JAVA_HOME C:/Program Files/Java/jdk-21):

```powershell
& 'D:/file/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn.cmd' -o '-Dmaven.repo.local=D:/workspace/JEECG/.runtime/m2' -f upstream/JeecgBoot/jeecg-boot/pom.xml -pl jeecg-boot-module/jeecg-module-package -am '-DskipTests=false' '-Dtest=Package*Test' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

- Mechanical source transplantation was not presented as test-first implementation. Identity tests were written before the new identity package; initial RED was a compilation failure for the absent package (`identity-red.log`), followed by 3 passing policy tests (`identity-green.log`).
- Configuration RED: eager JDK HttpClient failed while trying to create a loopback selector in the sandbox (`config-red.log`); lazy initialization allowed startup. Next RED asserted the supplied PACKAGE_DEFAULT_AES_KEY was empty (`config-red-env.log`); explicit PropertySource made it pass. Host isolation RED (`config-isolation-red.log`) found the module's generic ObjectMapper bean; removing that bean and keeping notification serialization private made it pass.
- ES mapping RED (`es-mapping-red.log`): expected first-save index creation was absent. Added lazy source-equivalent mapping creation; the adapter test passed in `mail-red.log` and the full GREEN run.
- Mail continuation RED (`mail-red.log`): the adapter exception aborted subsequent store checks. Added service-level reporting/continuation, matching the source MailUtils behavior. Full GREEN verifies both transitions still execute.
- Initial protocol test found Spring MockHttpServletRequest rejects getParameter(null). The default checkUrl fixture was corrected to provide the header-selected parameter names; business source code was not changed to invent a different protocol.
- `final-green.log`: 35 tests discovered, 33 passed, 2 skipped (the two opt-in database tests without credentials), zero failures/errors. Test categories: 12 protocol tests; 6 business tests; 3 identity policy tests; 7 adapter/order/UA/notification tests; 1 startup/config isolation test; 4 existing mapper tests.
- `PackageIdentityMySqlIntegrationTest` was separately executed successfully by the root task against the disposable local database: **1 passed, 0 failures/errors/skipped** (`.runtime/package-mysql/identity-live.log`). It creates randomized users/tenants/members/crosswalk/resources, checks tenant-local admin and ordinary scopes, cross-tenant/unmapped denial, batch-before-write validation, valid numeric-owner transfer, and expired tenant rejection; all writes rollback in finally. The subagent's elevated invocation had been rejected by automatic approval; it was not retried or bypassed by the subagent. The root task independently reviewed target boundaries and ran under its ordinary authorized workspace context.
- Task 1's real MyBatis insert/readback/rollback evidence remains valid; no mapper SQL or schema was changed in Task 2.

No test contacts production/source IP lookup, NPM, VAPID, SMTP, Facebook, Elasticsearch, or push endpoints. Adapter behavior is tested through fake ports. Live external service compatibility, production credential configuration, and actual ES cluster mapping verification are deployment checks, not claimed here.

## Remaining controller-layer work

Wire all management routes through the identity primitives and outer transactions; claim only newly created resources; validate nested parent links; restore original public URI at the trusted proxy boundary; translate LegacyAjaxResult to host Result only for management; supply authorized log package lists for ES list/export/clean and validate entire delete batches. Preserve public global package lookups and the exact protocol response encodings. PWA/NPM/ES side effects are not transactional with MySQL, matching the source synchronous design; failures must be surfaced and reconciliation handled operationally. Preserve source behavior rather than introducing queues/retries in controllers.
