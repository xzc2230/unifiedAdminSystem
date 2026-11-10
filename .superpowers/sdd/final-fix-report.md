# Store-platform final fix report

## Fix

1. **Android switch clears app row**: Added `clearAppManagerAppleStoreIdentity` mapper update (`apple_app_id` / `store_region` = SQL NULL). `AppManagerServiceImpl.updateAppManager` calls it when saved platform is `android` and store identity changed (after `updateAppManager`).
2. **Deterministic package lookup**: `selectAppManagerByPackageName` and `selectStoreStatusByPackageName` now `ORDER BY CASE platform WHEN 'android' THEN 0 ELSE 1 END, id LIMIT 1`, with SQL comment that mixed-platform packages return the android row until client lookup is platform-aware.
3. **Tests**: `StoreListingSyncTest.updateIosToAndroidClearsAppRowAppleFields` verifies `clearAppManagerAppleStoreIdentity`; `StoreProbeTest.iosDelistingSendsAppStoreOfflineEmail` covers iOS status 1 → 2 when `resultCount` is 0 and mail subject `App Store下架`.

## Verify command

```text
mvn -B "-Dmaven.repo.local=D:/workspace/JEECG/.runtime/m2" -pl jeecg-boot-module/jeecg-module-package -am test "-DskipTests=false" "-Dtest=StoreListingSyncTest,StoreProbeTest,PackageBusinessTest,StoreListingTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Run from: `upstream/JeecgBoot/jeecg-boot`

## Test summary

- **Result**: BUILD SUCCESS
- **Tests run**: 16 (StoreListingSyncTest, StoreProbeTest, PackageBusinessTest, StoreListingTest)
- **Failures / errors / skipped**: 0 / 0 / 0
