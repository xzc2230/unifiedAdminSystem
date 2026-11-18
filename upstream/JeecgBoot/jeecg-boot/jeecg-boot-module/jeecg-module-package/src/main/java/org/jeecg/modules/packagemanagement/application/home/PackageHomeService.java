package org.jeecg.modules.packagemanagement.application.home;

import java.util.List;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStat;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageActor;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.PackStatMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.StoreStatusMapper;
import org.springframework.stereotype.Service;

/** Loads the current tenant's store and packstat rows without an owner filter. */
@Service
public class PackageHomeService {
    private final StoreStatusMapper stores;
    private final PackStatMapper stats;

    public PackageHomeService(StoreStatusMapper stores, PackStatMapper stats) {
        this.stores = stores;
        this.stats = stats;
    }

    public PackageHomeCounts.Report summary(PackageActor actor) {
        PackStat query = new PackStat();
        query.setTenantId(actor.tenantId());
        var storeRows = stores.selectStoreStatusByTenant(actor.tenantId());
        var statRows = stats.selectPackStatList(query);
        return PackageHomeCounts.count(storeRows == null ? List.<StoreStatus>of() : storeRows,
                statRows == null ? List.of() : statRows);
    }
}
