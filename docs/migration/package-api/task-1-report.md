# Package API migration — Task 1 handoff

Status: **DONE_WITH_CONCERNS** (2026-11-09)

## Delivered paths

- Maven module: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/pom.xml`; added to `jeecg-boot-module/pom.xml` and as a dependency of `jeecg-module-system/jeecg-system-start/pom.xml`. Its only direct production dependency is `jeecg-boot-base-core`. Parent JEECG artifact version is 3.9.5; root parent is Spring Boot 4.1.0.
- Domain: `.../jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/domain/`.
- Mapper interfaces: `.../jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/`.
- Mapper XML: `.../jeecg-module-package/src/main/resources/org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/xml/`.
- Inferred MySQL development DDL: `.../jeecg-module-package/src/main/resources/db/package/001-schema.sql`.
- Mapper contract tests: `.../jeecg-module-package/src/test/java/org/jeecg/modules/packagemanagement/PackagePersistenceMappingTest.java`.

## Exact domain type mapping for service migration

| Source type prefix `com.ruoyi.project` | Target type prefix `org.jeecg.modules.packagemanagement.domain` | Types |
| --- | --- | --- |
| `app.domain` | `app` | `AppManager` |
| `applog.domain` | `applog` | `AppLog` |
| `event.domain` | `event` | `EventConfig`, `EventError`, `EvnetHistory`, `FBEvent`, `LoadCount` |
| `notify.domain` | `notify` | `AppNotify` |
| `pwa.domain` | `pwa` | `AppPwa`, `AppPwaSub`, `PwaBody`, `PwaKeys`, `WebPushMessage` |
| `statistics.domain` | `statistics` | `AppStatistics`, `StatisticsVO` |
| `store.domain` | `store` | `StoreStatus` |
| `system.domain` | `system` | `AppEventRecord`, `ReviewsRecord` |

`com.ruoyi.framework.web.domain.BaseEntity` becomes `org.jeecg.modules.packagemanagement.domain.PackageEntity`, preserving search, audit, remark, and params accessors. Source `AppLog.initId()` still uses the source UUIDv7 helper, copied as `domain.applog.UUIDv7Generator`; the active ES index name is `applog`. The ES annotations and dependency are deferred to the ES integration layer. RuoYi Excel annotations and the unrelated Apache POI import in `StatisticsVO` are removed. Business field names and accessors remain source compatible, including the source spellings `EvnetHistory`, `reigisterNum`, and `PwaBody.CreateBoody`.

All twelve mapper interfaces have their source class names and method names under `org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper`. Their XML namespaces use that exact package. Their source condition, sort, and aggregate SQL is retained with table substitutions below. `AppManagerMapper.transferAppManagerById` has `@Param` names for its three existing parameters. Three inserts that previously lacked key retrieval now set `useGeneratedKeys`, and the source AppManager insert's literal `id` value is corrected to `#{id}`.

| Source table | Target table |
| --- | --- |
| `app_manager` | `pkg_app_manager` |
| `app_statistics` | `pkg_app_statistics` |
| `app_store_status` | `pkg_store_status` |
| `event_config` | `pkg_event_config` |
| `evnet_history` | `pkg_event_history` |
| `event_error` | `pkg_event_error` |
| `load_count` | `pkg_load_count` |
| `app_pwa` | `pkg_pwa` |
| `app_pwa_sub` | `pkg_pwa_subscription` |
| `app_notify` | `pkg_notification` |
| `app_event_record` | `pkg_app_event` |
| `reviews_record` | `pkg_review` |

The DDL adds `pkg_user_identity(legacy_user_id, tenant_id, jeecg_user_id)` with unique legacy ID ownership, and `pkg_resource_owner(resource_type, resource_id, tenant_id, owner_user_id)` with one owner per numeric business row. Unmapped historical resources must be denied in management operations by the future identity service; these tables do not change public package queries. ES `AppLog` uses a string UUID and is not stored in `pkg_resource_owner`; future log ownership can be resolved through its package name. The script supplies auto-increment numeric primary keys, with `useGeneratedKeys` on mapper inserts, and unique indexes for `pkg_app_manager.package_code` and `pkg_pwa.app_code`.

## Verification

From `upstream/JeecgBoot/jeecg-boot`, using `JAVA_HOME=C:\Program Files\Java\jdk-21` and Maven `D:\file\apache-maven-3.8.8-bin\apache-maven-3.8.8\bin\mvn.cmd`:

```powershell
& 'D:\file\apache-maven-3.8.8-bin\apache-maven-3.8.8\bin\mvn.cmd' -o '-Dmaven.repo.local=D:\workspace\JEECG\.runtime\m2' -pl jeecg-boot-module/jeecg-module-package -am '-DskipTests=false' '-Dtest=PackagePersistenceMappingTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: reactor **BUILD SUCCESS**, two package tests run, zero failures/errors. The tests make MyBatis parse all twelve XMLs and verify each interface method has a statement, all twelve renamed tables appear, and the `EvnetHistory` `appsflyer_id` and `json_str` mappings survive. `git diff --check` passed after the final POM edits. Static DDL check found the widest business table (`pkg_event_history`) has about 34,680 bytes of maximum `VARCHAR(255)` payload, below the 65,535 byte SQL row limit; large free text is `TEXT`/`LONGTEXT`.

An initial full reactor test run with `-DskipTests=false` was stopped in existing `jeecg-boot-base-core` tests before it reached this module: 59 tests, 2 failures and 2 errors caused by unavailable DNS/loopback connections in this sandbox. Reports: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-base-core/target/surefire-reports/`. The focused run compiles base-core and runs only the package tests.

## Remaining concerns

- Source production business DDL and historical data were unavailable. SQL types, lengths, defaults, unique constraints, and indexes in `001-schema.sql` are explicitly inferred. Compare with production before importing data or applying the script there. In particular, verify duplicate package/app codes before enabling the unique indexes.
- No live MySQL schema execution or historical data migration was performed. Numeric legacy user IDs need verified JEECG identity and tenant mappings before management operations are enabled.
- ES annotations/repository and SQL `AppLogMapper` are deliberately outside this SQL persistence layer. The source uses the `applog` ES index for active request logs; services must implement that adapter separately.
