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
        Set<String> openPack = new HashSet<>();
        Set<String> droppedPack = new HashSet<>();
        if (stats != null) {
            for (PackStat row : stats) {
                String key = statKey(row);
                if (key == null) continue;
                if (row.getDropDate() == null) openPack.add(key);
                else droppedPack.add(key);
            }
        }
        return new Report(bucket("android", listed, pending, delisted, openPack, droppedPack),
                bucket("ios", listed, pending, delisted, openPack, droppedPack));
    }

    /** Same package name or Apple ID counts once. A name that appears on only one side still counts. */
    private static Counts bucket(String platform, Set<String> listed, Set<String> pending, Set<String> delisted,
            Set<String> openPack, Set<String> droppedPack) {
        String prefix = platform + ":";
        return new Counts(union(listed, openPack, prefix), size(pending, prefix), dropped(delisted, droppedPack, listed, prefix));
    }

    private static int union(Set<String> left, Set<String> right, String prefix) {
        Set<String> keys = new HashSet<>();
        for (String key : left) if (key.startsWith(prefix)) keys.add(key);
        for (String key : right) if (key.startsWith(prefix)) keys.add(key);
        return keys.size();
    }

    /** 已上架 stays online even when packstat already has a drop date. */
    private static int dropped(Set<String> delisted, Set<String> droppedPack, Set<String> listed, String prefix) {
        Set<String> keys = new HashSet<>();
        for (String key : delisted) if (key.startsWith(prefix)) keys.add(key);
        for (String key : droppedPack) if (key.startsWith(prefix)) keys.add(key);
        keys.removeAll(listed);
        return keys.size();
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
