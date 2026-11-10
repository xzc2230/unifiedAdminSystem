package org.jeecg.modules.packagemanagement.application.store;

import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;

public final class StoreListing {
    private StoreListing() {}

    public static void normalize(AppManager app) {
        String platform = app.getPlatform() == null || app.getPlatform().isBlank() ? "android" : app.getPlatform().trim().toLowerCase();
        if (!platform.equals("android") && !platform.equals("ios")) {
            throw new PackageFailure(400, "平台只允许 android 或 ios");
        }
        app.setPlatform(platform);
        if (platform.equals("android")) {
            app.setAppleAppId(null);
            app.setStoreRegion(null);
            return;
        }
        String appleId = app.getAppleAppId() == null ? "" : app.getAppleAppId().trim();
        if (!appleId.isEmpty() && !appleId.matches("[0-9]+")) throw new PackageFailure(400, "Apple ID 必须是数字");
        String region = app.getStoreRegion() == null || app.getStoreRegion().isBlank() ? "us" : app.getStoreRegion().trim().toLowerCase();
        if (!region.matches("[a-z]{2}")) throw new PackageFailure(400, "商店地区必须是两位字母");
        app.setAppleAppId(appleId.isEmpty() ? null : appleId);
        app.setStoreRegion(region);
    }

    public static String url(AppManager app) {
        if ("ios".equals(app.getPlatform())) {
            if (app.getAppleAppId() == null || app.getAppleAppId().isBlank()) return null;
            return "https://apps.apple.com/" + app.getStoreRegion() + "/app/id" + app.getAppleAppId();
        }
        return "https://play.google.com/store/apps/details?id=" + app.getPackageCode();
    }

    public static boolean identityChanged(AppManager previous, AppManager next) {
        return !previous.getPlatform().equals(next.getPlatform())
                || !previous.getPackageCode().equals(next.getPackageCode())
                || !java.util.Objects.equals(previous.getAppleAppId(), next.getAppleAppId());
    }
}
