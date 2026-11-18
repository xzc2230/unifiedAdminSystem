package org.jeecg.modules.packagemanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.jeecg.modules.packagemanagement.application.home.PackageHomeService;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStat;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageActor;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.PackStatMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.StoreStatusMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PackageHomeServiceTest {
    @Test void summaryCountsTheWholeTenantWithoutAnOwnerFilter() {
        StoreStatusMapper stores = mock(StoreStatusMapper.class);
        PackStatMapper stats = mock(PackStatMapper.class);
        StoreStatus listed = new StoreStatus();
        listed.setPlatform("android");
        listed.setPackageCode("com.live.app");
        listed.setStatus(1);
        when(stores.selectStoreStatusByTenant(8)).thenReturn(List.of(listed));
        when(stats.selectPackStatList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(stat()));
        var report = new PackageHomeService(stores, stats).summary(new PackageActor(8, "alice", 11, false));
        ArgumentCaptor<PackStat> query = ArgumentCaptor.forClass(PackStat.class);
        verify(stats).selectPackStatList(query.capture());
        assertEquals(8, query.getValue().getTenantId());
        assertNull(query.getValue().getOwnerUserId());
        verify(stores).selectStoreStatusByTenant(8);
        assertEquals(1, report.android().online());
    }

    private static PackStat stat() {
        PackStat row = new PackStat();
        row.setStoreUrl("https://play.google.com/store/apps/details?id=com.live.app");
        return row;
    }
}
