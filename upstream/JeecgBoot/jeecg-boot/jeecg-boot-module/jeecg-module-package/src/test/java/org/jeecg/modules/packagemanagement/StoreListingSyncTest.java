package org.jeecg.modules.packagemanagement;

import org.jeecg.modules.packagemanagement.application.apps.impl.AppManagerServiceImpl;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppManagerMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.StoreStatusMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
