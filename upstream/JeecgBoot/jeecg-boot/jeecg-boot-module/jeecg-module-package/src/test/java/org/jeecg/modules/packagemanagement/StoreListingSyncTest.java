package org.jeecg.modules.packagemanagement;

import org.jeecg.modules.packagemanagement.application.apps.impl.AppManagerServiceImpl;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppManagerMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.StoreStatusMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class StoreListingSyncTest {

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
        when(mapper.selectAppManagerByPlatformAndPackage("ios", "com.test")).thenReturn(null);
        when(stores.selectStoreStatusByPlatformAndPackage("ios", "com.test")).thenReturn(null);
        when(mapper.insertAppManager(ios)).thenReturn(1);
        assertEquals(1, service.insertAppManager(ios));
        verify(stores).insertStoreStatus(argThat(row ->
                "ios".equals(row.getPlatform())
                        && row.getStatus() == 0
                        && "https://apps.apple.com/cn/app/id123456".equals(row.getStoreUrl())));
    }

    @Test
    void updateAppleIdChangeResetsStoreListingAndUrl() {
        var mapper = mock(AppManagerMapper.class);
        var stores = mock(StoreStatusMapper.class);
        var service = new AppManagerServiceImpl();
        PackageBusinessTest.inject(service, "appManagerMapper", mapper);
        PackageBusinessTest.inject(service, "storeStatusMapper", stores);

        AppManager previous = new AppManager();
        previous.setId(1L);
        previous.setPlatform("ios");
        previous.setPackageCode("com.test");
        previous.setAppleAppId("111111");
        previous.setStoreRegion("cn");

        AppManager updated = new AppManager();
        updated.setId(1L);
        updated.setPlatform("ios");
        updated.setPackageCode("com.test");
        updated.setAppleAppId("222222");
        updated.setStoreRegion("cn");

        StoreStatus existing = new StoreStatus();
        existing.setId(9L);
        existing.setPlatform("ios");
        existing.setPackageCode("com.test");
        existing.setStatus(1);
        existing.setStoreUrl("https://apps.apple.com/cn/app/id111111");

        when(mapper.selectAppManagerById(1L)).thenReturn(previous);
        when(stores.selectStoreStatusByPlatformAndPackage("ios", "com.test")).thenReturn(existing);
        when(mapper.updateAppManager(updated)).thenReturn(1);

        assertEquals(1, service.updateAppManager(updated));

        verify(stores).resetStoreListing(argThat(row ->
                row.getStatus() == 0
                        && "https://apps.apple.com/cn/app/id222222".equals(row.getStoreUrl())
                        && "222222".equals(row.getAppleAppId())));
        verify(stores, never()).insertStoreStatus(any());
    }

    @Test
    void updateNameOnlySkipsStoreResetAndInsert() {
        var mapper = mock(AppManagerMapper.class);
        var stores = mock(StoreStatusMapper.class);
        var service = new AppManagerServiceImpl();
        PackageBusinessTest.inject(service, "appManagerMapper", mapper);
        PackageBusinessTest.inject(service, "storeStatusMapper", stores);

        AppManager previous = new AppManager();
        previous.setId(1L);
        previous.setPlatform("android");
        previous.setPackageCode("com.test");
        previous.setAppName("Old Name");

        AppManager updated = new AppManager();
        updated.setId(1L);
        updated.setPlatform("android");
        updated.setPackageCode("com.test");
        updated.setAppName("New Name");

        StoreStatus existing = new StoreStatus();
        existing.setPlatform("android");
        existing.setPackageCode("com.test");
        existing.setStatus(1);

        when(mapper.selectAppManagerById(1L)).thenReturn(previous);
        when(stores.selectStoreStatusByPlatformAndPackage("android", "com.test")).thenReturn(existing);
        when(mapper.updateAppManager(updated)).thenReturn(1);

        assertEquals(1, service.updateAppManager(updated));

        verify(stores, never()).resetStoreListing(any());
        verify(stores, never()).insertStoreStatus(any());
    }
}
