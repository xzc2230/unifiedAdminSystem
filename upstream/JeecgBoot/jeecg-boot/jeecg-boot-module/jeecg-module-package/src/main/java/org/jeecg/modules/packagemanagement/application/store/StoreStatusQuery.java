package org.jeecg.modules.packagemanagement.application.store;

import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;

import java.util.ArrayList;
import java.util.List;

/** Turns the store-status multi-select into generic and iOS-only status lists. */
public final class StoreStatusQuery {
    private StoreStatusQuery() {}

    public static void apply(StoreStatus row) {
        if (row == null || row.getStatusQuery() == null || row.getStatusQuery().isBlank()) return;
        List<Integer> any = new ArrayList<>();
        List<Integer> ios = new ArrayList<>();
        for (String part : row.getStatusQuery().split(",")) {
            String token = part.trim();
            if (token.isEmpty()) continue;
            boolean iosOnly = token.regionMatches(true, 0, "ios:", 0, 4);
            String number = iosOnly ? token.substring(4) : token;
            if (!number.matches("[012]")) throw new PackageFailure(400, "上架状态无效");
            (iosOnly ? ios : any).add(Integer.valueOf(number));
        }
        ios.removeAll(any);
        row.setStatus(null);
        row.setStatusIn(any);
        row.setIosStatusIn(ios);
    }
}
