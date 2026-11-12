package org.jeecg.modules.packagemanagement.application.store;

import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Turns the store-status multi-select into generic and iTunes lookup product-status filters. */
public final class StoreStatusQuery {
    /** Labels written from https://itunes.apple.com/lookup resultCount and isPreorder. */
    public static final List<String> IOS_LABELS = List.of("待上架", "已上架", "已下架", "预购");

    private StoreStatusQuery() {}

    public static void apply(StoreStatus row) {
        if (row == null || row.getStatusQuery() == null || row.getStatusQuery().isBlank()) return;
        List<Integer> any = new ArrayList<>();
        List<Integer> ios = new ArrayList<>();
        Set<String> products = new LinkedHashSet<>();
        Set<Integer> legacy = new LinkedHashSet<>();
        for (String part : row.getStatusQuery().split(",")) {
            String token = part.trim();
            if (token.isEmpty()) continue;
            if (token.regionMatches(true, 0, "ios:", 0, 4)) {
                String value = token.substring(4);
                if (value.matches("\\d+")) {
                    ios.add(Integer.valueOf(value));
                    continue;
                }
                if (!IOS_LABELS.contains(value)) throw new PackageFailure(400, "上架状态无效");
                products.add(value);
                if ("已上架".equals(value)) products.add("可供销售");
                Integer listed = legacyStatus(value);
                if (listed != null) legacy.add(listed);
                continue;
            }
            if (!token.matches("[012]")) throw new PackageFailure(400, "上架状态无效");
            any.add(Integer.valueOf(token));
        }
        ios.removeAll(any);
        row.setStatus(null);
        row.setStatusIn(any);
        row.setIosStatusIn(ios);
        row.setIosProductIn(new ArrayList<>(products));
        row.setIosLegacyIn(new ArrayList<>(legacy));
    }

    /** Rows synced before a product label existed still use 0, 1, or 2. */
    private static Integer legacyStatus(String label) {
        return switch (label) {
            case "待上架" -> 0;
            case "已上架" -> 1;
            case "已下架" -> 2;
            default -> null;
        };
    }
}
