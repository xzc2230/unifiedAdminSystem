# Task 3 controller delivery

2026-11-09. Uncommitted implementation for root review. Task 1/2 foundation remains in place. No production routing or source repository modifications were made.

## Routes and frontend contract

All management endpoints are below `/package-admin` (host context `/jeecg-boot`). They use JEECG `Result`: successful payload is `result`, errors have `success=false`, `code`, `message`. Every operation first obtains the authenticated tenant Actor and checks `package:{domain}:{action}`. A mapped resource owner is additionally required, including package administrator actions within the selected tenant.

| Domain | List/detail/add/edit/delete/export |
|---|---|
| apps | `GET /apps/list`, `GET /apps/{id}`, `POST /apps`, `PUT /apps/{id}`, `DELETE /apps/{ids}`, `POST /apps/export` |
| store | Same pattern with `/store` except export is unavailable (404), matching source |
| statistics | Same pattern with `/statistics` |
| event-configs | Same pattern with `/event-configs` |
| event-history | Same pattern with `/event-history` |
| event-errors | Same pattern with `/event-errors` |
| pwa | Same pattern with `/pwa`, `appType=0` PWA / `1` W2A |
| landing-counts | Same pattern with `/landing-counts` |
| app-events | Same pattern with `/app-events`, entity primary key `eventId` |
| reviews | Same pattern with `/reviews`, entity primary key `reviewsId` |
| notifications | Same pattern with `/notifications` |
| request-logs | Same pattern with `/request-logs`, opaque string IDs |

Lists take `pageNo` / `pageSize` (default 1 / 10, maximum 500) and source entity filter properties; `Result.result={records,total}`. Scoping precedes pagination and totals. Detail takes numeric ID except ES logs. Add/edit use JSON source fields below; the URL determines update ID. Delete takes comma separated IDs and validates the entire batch before writes. Export takes form/query filters, returns XLSX bytes with real business columns and Chinese headings (not Result). SQL exports use source annotated columns where available; event history template placeholders were replaced with meaningful Chinese names. PWA private keys are removed from detail/list/mutation responses and excluded from export. Source-generated keys, appCode/linkId and identity fields cannot be overwritten by forms.

Permissions use `query` for list/detail/all/summary, `add`, `edit`, `remove`, `export`, plus special `transfer` and `clean`. IDs and owner fields are not editable. Editable field allowlists are the explicit `List.of` values in `PackageAdminRegistry`:

- apps: appName, areas, author, mark, packageCode (immutable on update), status, url, whetherRestrict, whetherUs, whetherUse.
- store: packageCode, storeUrl, status, successEmail, failEmail.
- statistics: packageCode (immutable on update), status, url, subAreas, subUrl, shunt. SDK counters/money cannot be edited.
- event-configs: packageId, packageToken, packagePixel, appName, url.
- event-history: full source event form including attribution, geographic, device, app, URL and payload fields; excludes id/owner/audit fields.
- event-errors: errorPackage, errorMessage, errorContent.
- pwa: appName, appIcon, appAdvertising, appDescription, appLanguage, appHost, appSubHost, appSub, appUrl, appEvent, appEventConf, downloadUrl, appType, score, tags, reviews, downloadCount, reviewsRecords.
- landing-counts: hostName, appName, time, access, download (source active manual counter form).
- app-events: appId, hostName, eventType, eventTypeName, deviceNo, ip.
- reviews: appId, headPortraitUrl, name, content, score.
- notifications: appId, notifyTitle, notifyMessage, notifyIcon.
- request-logs: packageName, reqPath, reqIp, reqParams, reqHeards, reqMethod, resultMsg, errorMsg, reqTime, isMobile. ID is generated on add and URL-controlled on edit.

`POST /apps/transfer` accepts `{ids:[1,2],targetUser:"JEECG user id"}`. The entire batch is locked and checked; the target must be an active current-tenant member with exactly one legacy mapping. Registry owner, numeric user_id and verified sys_user display name user_name update in the same SQL transaction. `GET /apps/transfer-targets` (same transfer permission) returns `Result.result=[{id,displayName}]`, restricted to valid mapped current-tenant users; cross-table identity join specifies compatible collation. No client userName or old numeric target ID is trusted. Transfer does not cascade to separately owned store/statistics rows.

`GET /pwa/all` accepts source filters and returns a scoped PWA array with privateKey removed. `GET /statistics/getTotal` accepts the same source filters independently of page parameters and returns `{totalPackage,totalPayment,totalMoney,totalRegister}` in Result; money remains BigDecimal rounded to scale 2. `DELETE /request-logs/clean` accepts log filters and requires `package:request-logs:clean`. ES list/export/clean always receive explicit allowed package lists (including empty); detail/edit/delete validate each record's package, and edit validates both old and new package.

## Compatibility and ownership

The 20 public business paths and four common paths retain methods and source response shapes under `/package-legacy`; `target-endpoints.json` contains all 99 distinct source-to-target mappings with concrete files, handler names and dynamic test names. `/profile/**` is additionally registered as a static resource contract. ANY routes accept GET/POST/PUT/DELETE/PATCH/HEAD. Explicit profile one/two-segment routes outrank generic protocol routes, and deep profile paths use the catch-all. String protocol responses remain raw; `/system/{a}` retains Base64 and JSON Content-Type.

`PackageSerializationAdvice` is scoped only to module controllers. A private Jackson2 codec turns module objects into neutral JSON values using Shanghai legacy dates before the host Jackson3 writer; raw Strings bypass it. Local form Date editors use `yyyy-MM-dd HH:mm:ss`. There is no module ObjectMapper bean, global converter replacement or global timezone mutation. Public PWA projection contains the source 17 keys, fixed updateTime and nested legacy review dates; missing/failed lookup returns empty text. The PWA detail application service now returns null for an absent row rather than dereferencing null.

`PackageProxyFilter` strips forwarding identity headers from untrusted immediate peers; explicitly configured peers can provide original URI/proto/IP. `LegacyRequest` strips context/internal prefix and preserves query parameters, exposing trusted original URI only after the filter sets an internal attribute. Nginx overwrites incoming forwarded headers and passes original Host/query/path. Keep global forwarded-header strategy disabled at this boundary as documented in deployment README.

Repeat submission matches source URI + trimmed Authorization and compares raw body when present, otherwise serialized parameter map, with strictly less-than-1000ms repetition and source error `请求过于频繁`. Redis namespace is isolated from host keys.

All SQL management creates/updates/deletes and ownership changes use outer transactions. Parent package/PWA relations are checked before mutation, including both old and new parent references. Nested PWA reviews must already belong to that PWA and be individually authorized; new nested and standalone reviews inherit the verified parent PWA registry owner, including when a tenant administrator creates them for another owner. Nested review publication createTime remains editable as in source (missing new dates default to now; missing existing dates retain the stored date). Other audit fields are server-controlled. Public counters inherit parent PWA owners in their outer transaction. Subscription ownership was moved into the actual INSERT branch of the source service (not the upsert controller), so existing unowned historical rows are never silently claimed. FB event/error inserts inherit the path package's owner in an outer transaction. NPM, Facebook, push and ES side effects cannot participate in a SQL rollback; failures surface for operational reconciliation.

## Test evidence and scope

Command (JDK 21, offline cached dependencies):

```powershell
& 'D:/file/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn.cmd' -o '-Dmaven.repo.local=D:/workspace/JEECG/.runtime/m2' -f upstream/JeecgBoot/jeecg-boot/pom.xml -pl jeecg-boot-module/jeecg-module-package -am '-DskipTests=false' '-Dtest=Package*Test' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Final log: `.runtime/package-task3-final.log` (result appended after completion). Earlier failure logs `.runtime/package-task3-progress.log` and `.runtime/package-task3-tests.log` preserve actual compilation/test failures corrected during implementation. This extends existing code rather than claiming every line was written test-first.

- `PackageEndpointCoverageTest#all99SourceActionsResolveToExpectedHandler`: 99 dynamic cases resolve exact concrete controller methods; ANY cases check six methods. This is route/method coverage, explicitly not 99 full business scenarios.
- `PackageLegacyRoutesTest`: raw login and Base64 response, public ANY SDK, internal prefix/query handling.
- `PackageHttpSecurityTest`: management 401/403 before business access, whole ES batch validation before delete, source PWA projection/private-key exclusion/date serialization/empty result.
- `PackageLegacyBoundaryTest`: repeat window/key/body/parameter comparison and trusted/untrusted forwarding behavior.
- `PackageFileRoutesTest`: upload, resource/profile bytes, delete-after-download, parent traversal/backslash rejection, combined client/profile priority; symlink test conditionally skipped when OS refuses symlink creation.
- `PackagePublicOwnershipTest`: existing subscription never inherits, only actual inserted subscription inherits; missing PWA detail regression.
- `PackagePolicyRegressionTest`: submitted IDs/owners ignored, creation and claim inside transaction, cross-parent nested review rejected before business update.
- Existing protocol/business/identity/persistence/integration tests remain in the gate, using fake external ports. Two opt-in MySQL tests are skipped without their environment. Prior Task 2 real database verification remains recorded separately.

Root owns actual candidate-host startup/local HTTP smoke and live transfer-target collation verification; these are not claimed by the standalone MockMvc gate. No production external endpoints were contacted. OS junction boundary verification can use the root's ordinary-workspace fixture when the candidate host starts.

Deployment files: `deploy/package-api/nginx-legacy.conf.template` and `README.md`, environment bindings `PACKAGE_PROFILE_ROOT`, `PACKAGE_TRUSTED_PROXIES`; existing Task 2 integration variables remain. Swagger group `package-management`. Shiro permits only the narrow legacy namespace while management remains authenticated. Root should review the uncommitted diff, finish live-host validation and frontend wiring before release. Git mutation/commit is outside this implementer's assignment.

Final focused gate: **BUILD SUCCESS, 158 discovered, 155 passed, 3 skipped, 0 failures/errors**, 2026-11-09 13:18:01 Asia/Shanghai. Skips are the two opt-in MySQL tests and OS-restricted symlink creation. Root will verify a Windows junction through real HTTP. Effective source multipart limits are 10MB/file and 20MB/request; deploy README records the required opt-in host profile change (current host 10MB aggregate) and Nginx21m envelope headroom. No global host multipart setting was silently changed.





## Task 3 review fixes (2026-11-09)

Addressed all three Important findings:

1. `/common/download` now accepts only a plain download filename, rejects traversal/drive/separator inputs, and validates the actual download directory and final file against the canonical download subtree. A regression attempts `../upload/keep.txt&delete=true`, expects 400 and verifies the sibling upload remains untouched. Windows temporary directory short-path aliases required canonicalizing the profile root before comparing; the first regression run exposed this and is saved in `.runtime/package-task3-review-fixes-red.log`.
2. `PackageAccess.claimCreatedFromParent` locks/checks the authorized parent and the repository inserts the new child's registry ownership by selecting that parent's actual tenant/owner. It validates the newly inserted child exists and requires exactly one inherited owner. Both nested and standalone newly created reviews use this method, so an administrator cannot unintentionally make another user's PWA review inaccessible. The policy regression exercises administrator creation followed by parent-owner editing and checks that actor-owned claim is never called.
3. Nested review createTime retains submitted values for both new and existing reviews, matching the source nested raw-mapper workflow. Missing new dates default to now; missing existing dates use their stored value. A regression checks both supplied dates and subsequent owner edits. Identity and other audit fields remain sanitized.

The same offline `Package*Test` Maven gate was rerun serially after these changes; final result follows below. No Git mutation or production action was performed.

Review-fix final gate: **BUILD SUCCESS, 159 discovered / 156 passed / 3 skipped, zero failures/errors**, completed 2026-11-09 13:23:08 Asia/Shanghai; .runtime/package-task3-review-fixes.log. Skips remain two opt-in database cases and OS-restricted symlink creation. All three review regressions pass. Root can resume Maven packaging and candidate HTTP checks.


### Actual-host transfer picker character-set correction

The live host uses utf8mb3 `sys_user.id`, while migrated identity IDs use utf8mb4. Applying an utf8mb4 collation directly to the host ID failed. The picker now explicitly `CONVERT(... USING utf8mb4)` on both sides before the common collation. The rollback-only `PackageIdentityMySqlIntegrationTest` invokes the real controller picker with a real JdbcTemplate, verifies precisely the two eligible current-tenant users, and retains all existing membership/transfer/revocation assertions.

Targeted ordinary-sandbox live test against `127.0.0.1:13317/package_migration_test`: **BUILD SUCCESS, 1 passed, 0 skipped/failures/errors**, 2026-11-09 13:24:14 Asia/Shanghai. Log `.runtime/package-mysql/task3-transfer-targets.log`; Maven command as above with `-Dtest=PackageIdentityMySqlIntegrationTest` and local test environment credentials loaded without output. All fixture mutations roll back. Root can rebuild the candidate and verify the actual authenticated HTTP picker.

### Scoped management exception precedence correction

Actual candidate-host `POST /package-admin/store/export` returned HTTP 200 with a host 500 Result despite throwing PackageFailure(404). Investigation confirmed both module and host advice had default ordering. Spring chooses a matching handler from the first applicable advice; the host's `Exception` catch-all could therefore consume every management PackageFailure before the module's specific handler. This affects permission, missing-resource, validation and integration failure codes, not just store export.

Added highest precedence only to the existing `PackageExceptionHandler`, whose basePackages selector still restricts it to `controller.admin`. No host handler, global serializer or legacy response contract was changed. `PackageExceptionPrecedenceTest` registers the actual host and module advice in both registration orders, checks the real disabled-store-export route, all supported PackageFailure HTTP/body codes (400/401/403/404/409/502/503), and the existing invalid-argument 400 translation. A separate controller outside the scoped package retains the host's original HTTP 200/body500 behavior.

RED evidence `.runtime/package-task3-exception-red.log`: expected 404 but was 200, 2 tests with 1 failure and no errors. Initial fixture also needed the ordinary User-Agent header required by the host logging helper; after supplying it the regression reproduced the production status mismatch precisely. GREEN covering command uses the documented Maven options with `-Dtest=PackageExceptionPrecedenceTest,PackageHttpSecurityTest,PackageAdminRoutesTest`; result recorded below after completion.
Scoped exception GREEN: BUILD SUCCESS, 6 tests passed, zero failures/errors/skips, completed 2026-11-09 13:33:42 Asia/Shanghai; `.runtime/package-task3-exception-green.log`. Maven finished; root may repackage and repeat actual HTTP acceptance.
