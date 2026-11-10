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
        assertEquals("us", app.getStoreRegion());
        assertEquals("https://apps.apple.com/us/app/id123456", StoreListing.url(app));
        app.setAppleAppId("12a");
        assertEquals(400, assertThrows(PackageFailure.class, () -> StoreListing.normalize(app)).getCode());
        app.setAppleAppId("  ");
        StoreListing.normalize(app);
        assertNull(app.getAppleAppId());
        assertEquals("us", app.getStoreRegion());
        assertNull(StoreListing.url(app));
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
