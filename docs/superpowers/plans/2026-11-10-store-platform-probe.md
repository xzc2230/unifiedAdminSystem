# 应用平台与商店分探测 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 一个应用只属于 android 或 ios；保存时写入对应商店链接；现有的一条 `PackageStoreJob` 按商店记录的平台分别探测 Google Play 和 iTunes lookup。

**Architecture:** 新增纯函数 `StoreListing` 生成并校验链接。应用保存和统计补行都通过它写入 `pkg_store_status`。探测循环仍在 `StoreStatusServiceImpl.getStatus()`，安卓沿用 `StoreProbePort` 的 HTTP 成功判断，iOS 用 lookup JSON 的 `resultCount`。空平台按安卓处理，未知平台跳过。

**Tech Stack:** Java 21、Spring、MyBatis、JUnit 5、Mockito、Vue 3、现有 `package.data.ts` / `AppEditor.vue`。

## Global Constraints

- `platform` 只允许 `android` 或 `ios`。空平台在探测和保存时视为 `android`。
- iOS 的 `apple_app_id` 只能是数字。`store_region` 缺省 `us`，保存为两位小写字母。表单下拉与白名单地区使用同一个 `app_area` 字典。
- 安卓链接固定为 `https://play.google.com/store/apps/details?id=` 加包名。
- iOS 链接固定为 `https://apps.apple.com/{store_region}/app/id{apple_app_id}`。
- iOS 在架判断是 GET `https://itunes.apple.com/lookup?id={apple_app_id}&country={store_region}` 且 `resultCount >= 1`。不用网页 HTTP 200。
- 安卓在架判断仍是 `StoreProbePort.getStatus`，其内部 `HttpPort.Reply.success()` 为状态码 200–299。
- 商店状态保持 `0` 待探测、`1` 在架、`2` 已下架。状态 `2` 不探测。
- 修改平台、包名或 Apple ID 时重写链接，状态重置为 `0`，清空上架和下架时间，保留两个邮箱。
- 网络失败、超时、iOS 响应无法解析、未知平台、iOS 缺 Apple ID：不改状态，继续下一条。邮件失败只记日志。
- 邮件标题：安卓「GP上架」「GP下架」；iOS「App Store上架」「App Store下架」。
- 已有行回填 `android`，不改已有 Google Play 链接。迁移可重复执行。
- 不改 `PackagePackStatJob` 和包统计探测。
- 包名唯一改为平台加包名。商店记录按平台加包名查找。

---

### Task 1: 平台字段与商店链接

**Files:**
- Create: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/application/store/StoreListing.java`
- Create: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/test/java/org/jeecg/modules/packagemanagement/StoreListingTest.java`
- Create: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/db/package/015-store-platform.sql`
- Modify: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/db/package/001-schema.sql`
- Modify: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/domain/app/AppManager.java`
- Modify: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/domain/store/StoreStatus.java`
- Modify: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/xml/AppManagerMapper.xml`
- Modify: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/xml/StoreStatusMapper.xml`
- Modify: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/AppManagerMapper.java`
- Modify: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/StoreStatusMapper.java`

**Interfaces:**
- Consumes: 无。
- Produces: `StoreListing.normalize(AppManager)` 把空平台写成 `android`，iOS 地区空值写成 `cn`，并在非法时抛 `PackageFailure(400, message)`。`StoreListing.url(AppManager)` 返回商店链接。`StoreListing.identityChanged(AppManager previous, AppManager next)` 在平台、包名或 Apple ID 变化时返回 true。Mapper 新增 `selectAppManagerByPlatformAndPackage(String platform, String packageCode)` 与 `selectStoreStatusByPlatformAndPackage(String platform, String packageCode)`。

- [ ] **Step 1: Write the failing test**

```java
package org.jeecg.modules.packagemanagement;

import org.jeecg.modules.packagemanagement.application.store.StoreListing;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StoreListingTest {
    @Test
    void blankPlatformBecomesAndroidPlayUrl() {
        AppManager app = new AppManager();
        app.setPackageCode("com.test");
        StoreListing.normalize(app);
        assertEquals("android", app.getPlatform());
        assertEquals("https://play.google.com/store/apps/details?id=com.test", StoreListing.url(app));
    }

    @Test
    void iosRequiresNumericAppleIdAndDefaultsRegion() {
        AppManager app = new AppManager();
        app.setPlatform("ios");
        app.setPackageCode("com.test");
        app.setAppleAppId("123456");
        StoreListing.normalize(app);
        assertEquals("cn", app.getStoreRegion());
        assertEquals("https://apps.apple.com/cn/app/id123456", StoreListing.url(app));
        app.setAppleAppId("12a");
        assertEquals(400, assertThrows(PackageFailure.class, () -> StoreListing.normalize(app)).getCode());
    }

    @Test
    void identityChangeIgnoresUnrelatedFields() {
        AppManager before = new AppManager();
        before.setPlatform("ios");
        before.setPackageCode("com.test");
        before.setAppleAppId("123456");
        AppManager after = new AppManager();
        after.setPlatform("ios");
        after.setPackageCode("com.test");
        after.setAppleAppId("123456");
        after.setAppName("renamed");
        assertFalse(StoreListing.identityChanged(before, after));
        after.setAppleAppId("9");
        assertTrue(StoreListing.identityChanged(before, after));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run from `upstream/JeecgBoot/jeecg-boot`:

```powershell
mvn -B "-Dmaven.repo.local=D:/workspace/JEECG/.runtime/m2" -pl jeecg-boot-module/jeecg-module-package -am test "-DskipTests=false" "-Dtest=StoreListingTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: FAIL，编译错误 `StoreListing` 不存在。

- [ ] **Step 3: Write minimal implementation**

`StoreListing` 只做字符串规则，不访问数据库：

```java
package org.jeecg.modules.packagemanagement.application.store;

import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;

public final class StoreListing {
    private StoreListing() {}

    public static void normalize(AppManager app) {
        String platform = app.getPlatform() == null || app.getPlatform().isBlank() ? "android" : app.getPlatform().trim().toLowerCase();
        if (!platform.equals("android") && !platform.equals("ios")) {
            throw new PackageFailure(400, "平台只允许 android 或 ios");
        }
        app.setPlatform(platform);
        if (platform.equals("android")) {
            app.setAppleAppId(null);
            app.setStoreRegion(null);
            return;
        }
        String appleId = app.getAppleAppId() == null ? "" : app.getAppleAppId().trim();
        if (!appleId.matches("[0-9]+")) throw new PackageFailure(400, "Apple ID 必须是数字");
        String region = app.getStoreRegion() == null || app.getStoreRegion().isBlank() ? "cn" : app.getStoreRegion().trim().toLowerCase();
        if (!region.matches("[a-z]{2}")) throw new PackageFailure(400, "商店地区必须是两位字母");
        app.setAppleAppId(appleId);
        app.setStoreRegion(region);
    }

    public static String url(AppManager app) {
        if ("ios".equals(app.getPlatform())) {
            return "https://apps.apple.com/" + app.getStoreRegion() + "/app/id" + app.getAppleAppId();
        }
        return "https://play.google.com/store/apps/details?id=" + app.getPackageCode();
    }

    public static boolean identityChanged(AppManager previous, AppManager next) {
        return !previous.getPlatform().equals(next.getPlatform())
                || !previous.getPackageCode().equals(next.getPackageCode())
                || !java.util.Objects.equals(previous.getAppleAppId(), next.getAppleAppId());
    }
}
```

给 `AppManager` 与 `StoreStatus` 增加 `platform`、`appleAppId`、`storeRegion` 及 getter/setter。`001-schema.sql` 的两张表加上这三列，并把应用唯一键改为 `UNIQUE KEY uk_pkg_app_manager_platform_package (platform, package_code)`。`015-store-platform.sql` 用可重复的 `ADD COLUMN` 判断：列不存在才添加；把空 `platform` 更新为 `android`；删除旧唯一键 `uk_pkg_app_manager_package_code` 后添加新唯一键。商店表增加 `UNIQUE KEY uk_pkg_store_platform_package (platform, package_code)`。执行前先查询重复包名，有重复就停止，不自动合并。

两个 Mapper XML 的 result、select 列、insert、update 都带上 `platform`、`apple_app_id`、`store_region`。新增按平台和包名精确查询的方法。保留旧的 `select*ByPackageName`，避免未改调用点编译失败。

- [ ] **Step 4: Run test to verify it passes**

Run the same Maven command. Expected: `StoreListingTest` 三项通过。

- [ ] **Step 5: Commit**

```powershell
git add upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/application/store/StoreListing.java upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/test/java/org/jeecg/modules/packagemanagement/StoreListingTest.java upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/db/package/015-store-platform.sql upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/db/package/001-schema.sql upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/domain/app/AppManager.java upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/domain/store/StoreStatus.java upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/xml/AppManagerMapper.xml upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/xml/StoreStatusMapper.xml upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/AppManagerMapper.java upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/StoreStatusMapper.java
git commit -m "feat(package): add store platform fields and listing rules"
```

### Task 2: 保存应用时同步商店记录

**Files:**
- Modify: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/application/apps/impl/AppManagerServiceImpl.java`
- Modify: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/application/statistics/impl/AppStatisticsServiceImpl.java`
- Modify: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/test/java/org/jeecg/modules/packagemanagement/PackageBusinessTest.java`
- Test: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/test/java/org/jeecg/modules/packagemanagement/StoreListingSyncTest.java`

**Interfaces:**
- Consumes: Task 1 的 `StoreListing` 与 `selectStoreStatusByPlatformAndPackage`。
- Produces: `AppManagerServiceImpl.insertAppManager` / `updateAppManager` 在写应用前调用 `StoreListing.normalize`。重复判断改为同一平台加包名。商店行不存在则插入状态 `0` 和对应链接；已存在且标识变化则重写链接、状态改 `0`、两个日期设 null。统计新增只在缺少 `android` 商店行时补一行安卓记录，不修改 iOS 行。

- [ ] **Step 1: Write the failing test**

```java
@Test
void iosInsertWritesAppStoreUrlAndAndroidDuplicateStaysIndependent() {
    var mapper = mock(AppManagerMapper.class);
    var stores = mock(StoreStatusMapper.class);
    var service = new AppManagerServiceImpl();
    PackageBusinessTest.inject(service, "appManagerMapper", mapper);
    PackageBusinessTest.inject(service, "storeStatusMapper", stores);
    AppManager ios = new AppManager();
    ios.setPlatform("ios");
    ios.setPackageCode("com.test");
    ios.setAppleAppId("123456");
    when(mapper.insertAppManager(ios)).thenReturn(1);
    assertEquals(1, service.insertAppManager(ios));
    verify(stores).insertStoreStatus(argThat(row ->
            "ios".equals(row.getPlatform())
                    && row.getStatus() == 0
                    && "https://apps.apple.com/cn/app/id123456".equals(row.getStoreUrl())));
}
```

把这段放进新建的 `StoreListingSyncTest`。同时改 `PackageBusinessTest.newPackageEnsuresStoreAndPreservesNumericOwner` 的查找桩为 `selectAppManagerByPlatformAndPackage("android", "com.test")`，并断言插入行的 `platform` 是 `android`。

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -B "-Dmaven.repo.local=D:/workspace/JEECG/.runtime/m2" -pl jeecg-boot-module/jeecg-module-package -am test "-DskipTests=false" "-Dtest=StoreListingSyncTest,PackageBusinessTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `iosInsertWritesAppStoreUrlAndAndroidDuplicateStaysIndependent` FAIL，因为插入的仍是 Google Play 链接。

- [ ] **Step 3: Write minimal implementation**

在 `insertAppManager` 和 `updateAppManager` 开头调用 `StoreListing.normalize`。查重和商店查找都使用平台加包名。抽出私有方法 `syncStore(AppManager app, boolean identityChanged)`：无行则插入；有行且 `identityChanged` 时设置新链接、`status=0`、`successDate=null`、`failDate=null`，再调用已有的日期更新语句。更新语句要允许把两个日期写成 SQL NULL，不能因为 MyBatis `<if test="successDate != null">` 跳过。给 `updateStoreStatusAndDate` 增加明确的清空分支，或新增 `resetStoreListing`，用 `success_date = NULL, fail_date = NULL`。

`updateAppManager` 从数据库读出旧行后比较 `StoreListing.identityChanged`。只改名称时不调用重置。

`AppStatisticsServiceImpl.insertAppStatistics` 改为 `selectStoreStatusByPlatformAndPackage("android", packageCode)`。缺失时插入 `platform=android` 和 Play 链接，不重置已有行。

- [ ] **Step 4: Run test to verify it passes**

Run the same Maven command. Expected: 两个测试类通过。

- [ ] **Step 5: Commit**

```powershell
git add upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/application/apps/impl/AppManagerServiceImpl.java upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/application/statistics/impl/AppStatisticsServiceImpl.java upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/test/java/org/jeecg/modules/packagemanagement/PackageBusinessTest.java upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/test/java/org/jeecg/modules/packagemanagement/StoreListingSyncTest.java upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/xml/StoreStatusMapper.xml
git commit -m "feat(package): sync store listing from application platform"
```

### Task 3: 定时任务按平台探测

**Files:**
- Modify: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/application/store/impl/StoreStatusServiceImpl.java`
- Modify: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/config/PackageManagementConfiguration.java`
- Modify: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/test/java/org/jeecg/modules/packagemanagement/PackageBusinessTest.java`
- Test: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/test/java/org/jeecg/modules/packagemanagement/StoreProbeTest.java`

**Interfaces:**
- Consumes: `HttpPort.exchange` 与现有 `StoreProbePort`。
- Produces: `StoreStatusServiceImpl.getStatus()` 对每条状态不是 `2` 的记录调用平台判定。安卓调用 `probe.getStatus(storeUrl)`。iOS 调用 `http.exchange("GET", lookupUrl, null, Map.of())`，解析 `resultCount`。空平台走安卓。

- [ ] **Step 1: Write the failing test**

```java
@Test
void iosLookupResultCountDecidesListingAndNetworkFailureKeepsStatus() {
    var mapper = mock(StoreStatusMapper.class);
    var probe = mock(StoreProbePort.class);
    var http = mock(HttpPort.class);
    var mail = mock(MailPort.class);
    var service = new StoreStatusServiceImpl();
    PackageBusinessTest.inject(service, "storeStatusMapper", mapper);
    PackageBusinessTest.inject(service, "probe", probe);
    PackageBusinessTest.inject(service, "http", http);
    PackageBusinessTest.inject(service, "mail", mail);
    PackageBusinessTest.inject(service, "settings", new PackageManagementProperties());
    StoreStatus ios = new StoreStatus();
    ios.setStatus(0);
    ios.setPlatform("ios");
    ios.setAppleAppId("123456");
    ios.setStoreRegion("cn");
    ios.setStoreUrl("https://apps.apple.com/cn/app/id123456");
    when(mapper.selectStoreStatusList(any())).thenReturn(List.of(ios));
    when(http.exchange(eq("GET"), eq("https://itunes.apple.com/lookup?id=123456&country=cn"), isNull(), anyMap()))
            .thenReturn(new HttpPort.Reply(200, "{\"resultCount\":1}"));
    service.getStatus();
    assertEquals(1, ios.getStatus());
    verify(mail).sendEmail(anyString(), eq("App Store上架"), eq(ios.getStoreUrl()));
    verifyNoInteractions(probe);
}
```

再写一个测试：`http.exchange` 抛 `RuntimeException` 时状态保持 `0`，且后续安卓记录仍被 `probe.getStatus` 处理。现有 `storeOnlyTransitionsZeroToOneAndOneToTwoWithEmails` 里的记录不设平台，断言邮件标题仍是「GP上架」「GP下架」。

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -B "-Dmaven.repo.local=D:/workspace/JEECG/.runtime/m2" -pl jeecg-boot-module/jeecg-module-package -am test "-DskipTests=false" "-Dtest=StoreProbeTest,PackageBusinessTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `iosLookupResultCountDecidesListingAndNetworkFailureKeepsStatus` FAIL，因为没有 lookup 调用，iOS 行会走 Play 探测。

- [ ] **Step 3: Write minimal implementation**

`StoreStatusServiceImpl` 增加 `HttpPort http`。`getStatus` 维持 `status != 2` 过滤。每条记录单独 try/catch：

- `ios` 且 Apple ID 不是纯数字：记 warn，跳过。
- `ios`：GET lookup。状态码不是 200–299，或正文没有整数 `resultCount`：记 warn，不改状态。`resultCount >= 1` 为在架，否则不在架。
- `android` 或空平台：`probe.getStatus(storeUrl)`。探测抛异常则记 warn，不改状态。
- 其他平台：记 warn，跳过。
- 状态变化仍是 `0` 加在架到 `1`，`1` 加不在架到 `2`。邮件标题按平台选择四个固定字符串。

不要改 `PackageStoreJob` 和 `PackagePackStatJob`。

- [ ] **Step 4: Run test to verify it passes**

Run the same Maven command. Expected: 两个测试类通过。

- [ ] **Step 5: Commit**

```powershell
git add upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/application/store/impl/StoreStatusServiceImpl.java upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/test/java/org/jeecg/modules/packagemanagement/StoreProbeTest.java upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/test/java/org/jeecg/modules/packagemanagement/PackageBusinessTest.java
git commit -m "feat(package): probe store listings by platform"
```

### Task 4: 管理字段与应用表单

**Files:**
- Modify: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/application/apps/support/AppManagerFieldPermissionPolicy.java`
- Modify: `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/controller/admin/PackageAdminRegistry.java`
- Modify: `upstream/JeecgBoot/jeecgboot-vue3/src/views/packagemanagement/package.data.ts`
- Modify: `upstream/JeecgBoot/jeecgboot-vue3/src/views/packagemanagement/package-dev.mjs`
- Modify: `upstream/JeecgBoot/jeecgboot-vue3/src/views/packagemanagement/components/AppEditor.vue`
- Modify: `upstream/JeecgBoot/jeecgboot-vue3/tests/package-management.test.mjs`

**Interfaces:**
- Consumes: 保存接口已经接受 `platform`、`appleAppId`、`storeRegion`。
- Produces: 应用编辑和查询字段名单包含这三个字段。商店列表展示 `platform` 与 `storeUrl`。表单默认安卓；选 iOS 才显示 Apple ID 与地区。

- [ ] **Step 1: Write the failing test**

在 `tests/package-management.test.mjs` 增加：

```javascript
test('app editor offers android and ios store fields', () => {
  const keys = definitions.apps.fields.map((field) => field.key);
  assert.ok(keys.includes('platform'));
  assert.ok(keys.includes('appleAppId'));
  assert.ok(keys.includes('storeRegion'));
  assert.equal(definitions.apps.fields.find((field) => field.key === 'platform').type, 'radio');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run from `upstream/JeecgBoot/jeecgboot-vue3`:

```powershell
node --test --test-isolation=none tests/package-management.test.mjs
```

Expected: FAIL，`platform` 不在应用字段里。

- [ ] **Step 3: Write minimal implementation**

`package.data.ts` 的 apps 字段在包名之前增加：

```typescript
field('platform','平台',{type:'radio',options:[{value:'android',label:'安卓'},{value:'ios',label:'iOS'}],required:true}),
field('appleAppId','Apple ID'),
field('storeRegion','商店地区'),
```

store 字段增加 `field('platform','平台',{readonly:true})`，并放进列表列。`package-dev.mjs` 的 `EDIT_FIELDS` 与 `QUERY_FIELDS` 加入这三个键，顺序与 `AppManagerFieldPermissionPolicy.EDIT`、`QUERY` 一致。`PackageAdminRegistry` 的 apps 可写列和 store 展示列加入同名字段。

`AppEditor.vue` 在基础信息顶部渲染平台单选。模型不是 `ios` 时不渲染 Apple ID 和地区。切回安卓时把这两个字段写成空字符串。地区用 `app_area` 下拉，不选时保存为 `us`。Apple ID 使用数字输入。

- [ ] **Step 4: Run test to verify it passes**

Run the same node test. Expected: 新增断言通过，原有测试仍通过。

- [ ] **Step 5: Commit**

```powershell
git add upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/application/apps/support/AppManagerFieldPermissionPolicy.java upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement/controller/admin/PackageAdminRegistry.java upstream/JeecgBoot/jeecgboot-vue3/src/views/packagemanagement/package.data.ts upstream/JeecgBoot/jeecgboot-vue3/src/views/packagemanagement/package-dev.mjs upstream/JeecgBoot/jeecgboot-vue3/src/views/packagemanagement/components/AppEditor.vue upstream/JeecgBoot/jeecgboot-vue3/tests/package-management.test.mjs
git commit -m "feat(package): choose android or ios store details on the app form"
```
