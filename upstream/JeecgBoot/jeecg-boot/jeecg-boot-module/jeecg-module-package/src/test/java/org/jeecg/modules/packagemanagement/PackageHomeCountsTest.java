package org.jeecg.modules.packagemanagement;

import java.util.Date;
import java.util.List;
import org.jeecg.modules.packagemanagement.application.home.PackageHomeCounts;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStat;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackageHomeCountsTest {
    @Test void onlineCountsEveryListedPackageAndEveryOpenPackstatLink() {
        var report = PackageHomeCounts.count(List.of(
                store("android", "com.live.app", null, 1, null),
                store("android", "com.listed.only", null, 1, null)),
                List.of(stat("https://play.google.com/store/apps/details?id=com.live.app", null),
                        stat("https://play.google.com/store/apps/details?id=com.live.app", null),
                        stat("https://play.google.com/store/apps/details?id=com.pack.only", null)));
        assertEquals(3, report.android().online());
        assertEquals(0, report.android().dropped());
    }

    @Test void listedPackageWithADropDateStaysOnline() {
        var report = PackageHomeCounts.count(List.of(store("android", "com.live.app", null, 1, null)),
                List.of(stat("https://play.google.com/store/apps/details?id=com.live.app", new Date())));
        assertEquals(1, report.android().online());
        assertEquals(0, report.android().dropped());
    }

    @Test void reviewIsPendingStoreIdentityAndIgnoresPackstat() {
        var report = PackageHomeCounts.count(List.of(
                store("android", "com.review.app", null, 0, null),
                store("android", "com.review.app", null, 0, null),
                store("ios", "ios.review", "222", 0, null)),
                List.of(stat("https://play.google.com/store/apps/details?id=com.review.app", null)));
        assertEquals(1, report.android().review());
        assertEquals(1, report.ios().review());
        assertEquals(1, report.android().online());
    }

    @Test void preorderIsNotPendingReview() {
        var report = PackageHomeCounts.count(List.of(
                store("ios", "ios.preorder", "333", 0, "预购"),
                store("ios", "ios.live", "444", 1, "预购")),
                List.of(stat("https://apps.apple.com/us/app/live/id444", null)));
        assertEquals(0, report.ios().review());
        assertEquals(1, report.ios().online());
    }

    @Test void droppedCountsDelistedPackagesAndPackstatDropDates() {
        var report = PackageHomeCounts.count(List.of(
                store("android", "com.gone.app", null, 2, null),
                store("android", "com.quiet.app", null, 2, null),
                store("ios", "ios.gone", "555", 2, null)),
                List.of(stat("https://play.google.com/store/apps/details?id=com.gone.app", new Date()),
                        stat("https://play.google.com/store/apps/details?id=com.quiet.app", null),
                        stat("https://play.google.com/store/apps/details?id=com.drop.only", new Date()),
                        stat("https://apps.apple.com/us/app/gone/id555", new Date())));
        assertEquals(3, report.android().dropped());
        assertEquals(1, report.android().online());
        assertEquals(1, report.ios().dropped());
    }

    @Test void iosMatchesAppleIdDigitsAndSkipsUnusableLinks() {
        var report = PackageHomeCounts.count(List.of(
                store("ios", "ios.live", "1234567890", 1, null),
                store("ios", "ios.blank", " ", 1, null),
                store("android", " ", null, 1, null)),
                List.of(stat("https://apps.apple.com/us/app/name/id1234567890", null),
                        stat("https://example.com/not-a-store", null),
                        stat("https://play.google.com/store/apps/details?id=not a package", null)));
        assertEquals(1, report.ios().online());
        assertEquals(0, report.android().online());
    }
    private static StoreStatus store(String platform, String code, String appleId, int status, String product) {
        StoreStatus row = new StoreStatus();
        row.setPlatform(platform);
        row.setPackageCode(code);
        row.setAppleAppId(appleId);
        row.setStatus(status);
        row.setProductStatus(product);
        return row;
    }

    private static PackStat stat(String url, Date dropDate) {
        PackStat row = new PackStat();
        row.setStoreUrl(url);
        row.setDropDate(dropDate);
        return row;
    }
}
