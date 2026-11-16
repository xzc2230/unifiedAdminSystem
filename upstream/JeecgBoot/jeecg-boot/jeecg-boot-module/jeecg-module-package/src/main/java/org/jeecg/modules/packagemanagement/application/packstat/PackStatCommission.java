package org.jeecg.modules.packagemanagement.application.packstat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Monthly pack-stat commission sheet. One record belongs to exactly one survival-day column. */
public final class PackStatCommission {
    public static final int[] DAYS = {7, 8, 9, 10, 11, 12, 13, 14, 15};
    public static final int[] ANDROID = {300, 400, 500, 600, 700, 800, 900, 1000, 1200};
    public static final int[] IOS = {400, 600, 800, 1000, 1200, 1400, 1600, 1800, 2000};
    public static final int[] PICKUP = {50, 50, 50, 50, 50, 50, 50, 50, 100};
    public static final int[] DESIGN = {25, 25, 25, 25, 25, 25, 25, 25, 50};

    private PackStatCommission() {}

    /** Days from the customer-use date to the drop date, or to today when the package is still up. Same day is 0. */
    public static int survivalDays(LocalDate useDate, LocalDate dropDate, LocalDate today) {
        if (useDate == null) return -1;
        LocalDate end = dropDate != null ? dropDate : today;
        if (end == null) return -1;
        long days = ChronoUnit.DAYS.between(useDate, end);
        if (days < 0L) return 0;
        if (days > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) days;
    }

    /** Exact day 7 through 14, and day 15 for every span of 15 days or more. Shorter spans are not paid. */
    public static Integer bucket(int days) {
        if (days < 7) return null;
        return days >= 15 ? 15 : days;
    }

    public static int amount(int[] counts, int[] rates) {
        int sum = 0;
        for (int i = 0; i < DAYS.length; i++) sum += counts[i] * rates[i];
        return sum;
    }

    public static Report build(String category, String packer, YearMonth month, LocalDate today, List<Source> rows) {
        Map<String, int[]> android = new LinkedHashMap<>();
        Map<String, int[]> ios = new LinkedHashMap<>();
        List<Detail> details = new ArrayList<>();
        for (Source row : rows == null ? List.<Source>of() : rows) {
            int days = survivalDays(row.useDate(), row.dropDate(), today);
            Integer day = bucket(days);
            String name = row.packer() == null ? "" : row.packer().trim();
            String platform = row.platform() == null ? "" : row.platform().trim().toLowerCase(Locale.ROOT);
            if (day != null && !name.isEmpty()) {
                Map<String, int[]> target = "ios".equals(platform) ? ios : "android".equals(platform) ? android : null;
                if (target != null) target.computeIfAbsent(name, key -> new int[DAYS.length])[day - 7]++;
            }
            details.add(new Detail(row.storeUrl(), row.customer(), row.replenish(), row.replenishUrl(), name,
                    row.useDate(), row.dropDate(), days < 0 ? null : days, category, platform));
        }
        details.sort(Comparator.comparing(Detail::useDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Detail::packer, Comparator.nullsLast(String::compareTo)));
        String person = packer == null ? "" : packer.trim();
        return new Report(title(category, person, month), fileName(category, person, month), category, person, month,
                people(android, ANDROID), people(ios, IOS), details);
    }

    private static String title(String category, String packer, YearMonth month) {
        String base = category + month.getYear() + "年" + month.getMonthValue() + "月绩效提成表";
        return packer.isEmpty() ? base : base + "-" + packer;
    }

    public static String fileName(String category, String packer, YearMonth month) {
        String monthText = month.toString();
        return packer == null || packer.isBlank()
                ? "包统计绩效-" + category + "-" + monthText + ".xlsx"
                : "包统计绩效-" + category + "-" + packer.trim() + "-" + monthText + ".xlsx";
    }

    private static List<Person> people(Map<String, int[]> counts, int[] rates) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new Person(entry.getKey(), entry.getValue(), amount(entry.getValue(), rates)))
                .toList();
    }

    public record Source(String platform, String packer, String storeUrl, String customer, boolean replenish,
            String replenishUrl, LocalDate useDate, LocalDate dropDate) {}

    public record Person(String packer, int[] counts, int amount) {}

    public record Detail(String storeUrl, String customer, boolean replenish, String replenishUrl, String packer,
            LocalDate useDate, LocalDate dropDate, Integer days, String category, String platform) {}

    public record Report(String title, String fileName, String category, String packer, YearMonth month,
            List<Person> android, List<Person> ios, List<Detail> details) {}
}
