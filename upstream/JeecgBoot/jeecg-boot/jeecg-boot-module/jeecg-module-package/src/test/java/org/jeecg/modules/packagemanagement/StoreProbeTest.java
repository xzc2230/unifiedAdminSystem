package org.jeecg.modules.packagemanagement;

import org.junit.jupiter.api.Test;
import org.jeecg.modules.packagemanagement.application.port.HttpPort;
import org.jeecg.modules.packagemanagement.application.port.MailPort;
import org.jeecg.modules.packagemanagement.application.port.StoreProbePort;
import org.jeecg.modules.packagemanagement.application.store.impl.StoreStatusServiceImpl;
import org.jeecg.modules.packagemanagement.config.PackageManagementProperties;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppManagerMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.StoreStatusMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StoreProbeTest {

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
        assertEquals("已上架", ios.getProductStatus());
        verify(mail).sendEmail(anyString(), eq("App Store上架"), eq(ios.getStoreUrl()));
        verifyNoInteractions(probe);
    }

    @Test
    void iosNetworkFailureKeepsStatusAndAndroidRowStillProbed() {
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
        StoreStatus android = new StoreStatus();
        android.setStatus(0);
        android.setPlatform("android");
        android.setPackageCode("com.follow");
        android.setStoreUrl("https://play.google.com/store/apps/details?id=com.follow");
        when(mapper.selectStoreStatusList(any())).thenReturn(List.of(ios, android));
        when(http.exchange(eq("GET"), eq("https://itunes.apple.com/lookup?id=123456&country=cn"), isNull(), anyMap()))
                .thenThrow(new RuntimeException("network down"));
        when(probe.getStatus(android.getStoreUrl())).thenReturn(true);
        service.getStatus();
        assertEquals(0, ios.getStatus());
        assertEquals(1, android.getStatus());
        verify(probe).getStatus(android.getStoreUrl());
        verify(mail).sendEmail(anyString(), eq("GP上架"), eq(android.getStoreUrl()));
        verify(probe, never()).getStatus(ios.getStoreUrl());
    }

    @Test
    void iosDelistingSendsAppStoreOfflineEmail() {
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
        ios.setStatus(1);
        ios.setPlatform("ios");
        ios.setAppleAppId("123456");
        ios.setStoreRegion("cn");
        ios.setStoreUrl("https://apps.apple.com/cn/app/id123456");
        when(mapper.selectStoreStatusList(any())).thenReturn(List.of(ios));
        when(http.exchange(eq("GET"), eq("https://itunes.apple.com/lookup?id=123456&country=cn"), isNull(), anyMap()))
                .thenReturn(new HttpPort.Reply(200, "{\"resultCount\":0}"));
        service.getStatus();
        assertEquals(2, ios.getStatus());
        verify(mail).sendEmail(anyString(), eq("App Store下架"), eq(ios.getStoreUrl()));
        verifyNoInteractions(probe);
    }

    @Test
    void blankIosAppleIdSkipsLookupAndKeepsStatus() {
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
        ios.setPackageCode("com.blank");
        when(mapper.selectStoreStatusList(any())).thenReturn(List.of(ios));
        service.getStatus();
        assertEquals(0, ios.getStatus());
        verifyNoInteractions(http, probe, mail);
    }

    @Test
    void platformJobsFollowTheApplicationPlatform() {
        var mapper = mock(StoreStatusMapper.class);
        var apps = mock(AppManagerMapper.class);
        var probe = mock(StoreProbePort.class);
        var http = mock(HttpPort.class);
        var mail = mock(MailPort.class);
        var service = new StoreStatusServiceImpl();
        PackageBusinessTest.inject(service, "storeStatusMapper", mapper);
        PackageBusinessTest.inject(service, "appManagerMapper", apps);
        PackageBusinessTest.inject(service, "probe", probe);
        PackageBusinessTest.inject(service, "http", http);
        PackageBusinessTest.inject(service, "mail", mail);
        PackageBusinessTest.inject(service, "settings", new PackageManagementProperties());
        StoreStatus ios = new StoreStatus();
        ios.setStatus(0);
        ios.setPackageCode("com.ios");
        ios.setAppleAppId("123456");
        ios.setStoreRegion("us");
        ios.setStoreUrl("https://apps.apple.com/us/app/id123456");
        StoreStatus android = new StoreStatus();
        android.setStatus(0);
        android.setPlatform("android");
        android.setPackageCode("com.android");
        android.setStoreUrl("https://play.google.com/store/apps/details?id=com.android");
        AppManager iosApp = new AppManager();
        iosApp.setPlatform("ios");
        iosApp.setPackageCode("com.ios");
        AppManager androidApp = new AppManager();
        androidApp.setPlatform("android");
        androidApp.setPackageCode("com.android");
        when(mapper.selectStoreStatusList(any())).thenReturn(List.of(ios, android));
        when(apps.selectAppManagerByPlatformAndPackage("android", "com.ios")).thenReturn(null);
        when(apps.selectAppManagerByPlatformAndPackage("ios", "com.ios")).thenReturn(iosApp);
        when(apps.selectAppManagerByPlatformAndPackage("android", "com.android")).thenReturn(androidApp);
        when(http.exchange(eq("GET"), eq("https://itunes.apple.com/lookup?id=123456&country=us"), isNull(), anyMap()))
                .thenReturn(new HttpPort.Reply(200, "{\"resultCount\":1}"));
        when(probe.getStatus(android.getStoreUrl())).thenReturn(true);
        service.getStatus("ios");
        assertEquals(1, ios.getStatus());
        assertEquals(0, android.getStatus());
        verify(probe, never()).getStatus(anyString());
        service.getStatus("android");
        assertEquals(1, android.getStatus());
        verify(http, times(1)).exchange(anyString(), anyString(), any(), anyMap());
    }
}
