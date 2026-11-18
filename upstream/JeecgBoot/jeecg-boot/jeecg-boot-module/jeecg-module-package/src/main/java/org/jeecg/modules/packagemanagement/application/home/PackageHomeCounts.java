package org.jeecg.modules.packagemanagement.application.home;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jeecg.modules.packagemanagement.application.packstat.util.PackStatUrlParser;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStat;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;

/** Homepage Android and iOS counts for the current tenant. */
public final class PackageHomeCounts {
    public record Counts(int online, int review, int dropped) {}
    public record Report(Counts android, Counts ios) {}

    private PackageHomeCounts() {}

    public static Report count(List<StoreStatus> stores, List<PackStat> stats) {
        Set<String> listed = new HashSet<>();
        Set<String> pending = new HashSet<>();
        Set<String> delisted = new HashSet<>();
        if (stores != null) {
            for (StoreStatus row : stores) {
                String key = storeKey(row);
                if (key == null || row.getStatus() == null) continue;
                if (row.getStatus() == 1) listed.add(key);
                else if (row.getStatus() == 0 && !preorder(row)) pending.add(key);
                else if (row.getStatus() == 2) delisted.add(key);
            }
        }
        Set<String> packed = new HashSet<>();
        Set<String> dropped = new HashSet<>();
        if (stats != null) {
            for (PackStat row : stats) {
                String key = statKey(row);
                if (key == null) continue;
                packed.add(key);
                if (row.getDropDate() != null) dropped.add(key);
            }
        }
        return new Report(bucket("android", listed, pending, delisted, packed, dropped),
                bucket("ios", listed, pending, delisted, packed, dropped));
    }

    private static Counts bucket(String platform, Set<String> listed, Set<String> pending, Set<String> delisted,
            Set<String> packed, Set<String> dropped) {
        String prefix = platform + ":";
        return new Counts(overlap(listed, packed, prefix), size(pending, prefix), overlap(delisted, dropped, prefix));
    }

    private static int overlap(Set<String> left, Set<String> right, String prefix) {
        int count = 0;
        for (String key : left) if (key.startsWith(prefix) && right.contains(key)) count++;
        return count;
    }

    private static int size(Set<String> keys, String prefix) {
        int count = 0;
        for (String key : keys) if (key.startsWith(prefix)) count++;
        return count;
    }

    /** Android is the package name. iOS is the Apple ID digits. 预购 is not 待上架. */
    static String storeKey(StoreStatus row) {
        if (row == null || row.getPlatform() == null) return null;
        String platform = row.getPlatform().trim().toLowerCase(Locale.ROOT);
        if ("android".equals(platform)) {
            String code = row.getPackageCode() == null ? "" : row.getPackageCode().trim();
            return code.isEmpty() ? null : "android:" + code;
        }
        if ("ios".equals(platform)) {
            String id = digits(row.getAppleAppId());
            return id == null ? null : "ios:" + id;
        }
        return null;
    }

    static String statKey(PackStat row) {
        if (row == null) return null;
        var parsed = PackStatUrlParser.tryParse(row.getStoreUrl());
        if (parsed == null || parsed.getPlatform() == null || parsed.getAppId() == null) return null;
        if ("android".equals(parsed.getPlatform())) return "android:" + parsed.getAppId();
        if ("ios".equals(parsed.getPlatform())) {
            String id = digits(parsed.getAppId());
            return id == null ? null : "ios:" + id;
        }
        return null;
    }

    private static boolean preorder(StoreStatus row) {
        return row.getProductStatus() != null && "预购".equals(row.getProductStatus().trim());
    }

    private static String digits(String value) {
        if (value == null) return null;
        String id = value.trim();
        return id.matches("[0-9]+") ? id : null;
    }
}
