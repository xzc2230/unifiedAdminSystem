package org.jeecg.modules.packagemanagement.application.packstat.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calendar-day calculations matching MySQL DATEDIFF (same day = 0).
 */
public final class PackStatDateCalc
{
    private PackStatDateCalc()
    {
    }

    public static Integer surviveDays(LocalDate passDate, LocalDate dropDate)
    {
        if (dropDate == null)
        {
            return null;
        }
        if (passDate == null)
        {
            return Integer.valueOf(0);
        }
        return Integer.valueOf(nonNegativeDays(passDate, dropDate));
    }

    public static Integer useDays(LocalDate customerUseDate, LocalDate dropDate)
    {
        if (dropDate == null)
        {
            return null;
        }
        if (customerUseDate == null)
        {
            return Integer.valueOf(0);
        }
        return Integer.valueOf(nonNegativeDays(customerUseDate, dropDate));
    }

    public static int intersectionDays(LocalDate rangeStart, LocalDate rangeEnd,
            LocalDate windowStart, LocalDate windowEnd)
    {
        if (rangeStart == null || rangeEnd == null || windowStart == null || windowEnd == null)
        {
            return 0;
        }
        LocalDate left = rangeStart.isAfter(windowStart) ? rangeStart : windowStart;
        LocalDate right = rangeEnd.isBefore(windowEnd) ? rangeEnd : windowEnd;
        return nonNegativeDays(left, right);
    }

    /**
     * Resolve summary window anchor.
     * <ul>
     *   <li>passMonth empty → today</li>
     *   <li>passMonth is current month → today</li>
     *   <li>passMonth is past month → last day of that month</li>
     *   <li>passMonth is future → null (caller returns all zeros)</li>
     * </ul>
     */
    public static LocalDate resolveSummaryAnchor(String passMonth, LocalDate today)
    {
        if (today == null)
        {
            today = LocalDate.now();
        }
        if (passMonth == null || passMonth.trim().isEmpty())
        {
            return today;
        }
        YearMonth selected;
        try
        {
            selected = YearMonth.parse(passMonth.trim());
        }
        catch (Exception ex)
        {
            return today;
        }
        YearMonth current = YearMonth.from(today);
        if (selected.isAfter(current))
        {
            return null;
        }
        if (selected.equals(current))
        {
            return today;
        }
        return selected.atEndOfMonth();
    }

    /**
     * For each N in 7..15, sum survive/use intersections with window
     * [anchor-(N-1), anchor]. Keys: s7,u7,...,s15,u15.
     */
    public static Map<String, Integer> windowSummary(List<SurviveSpan> spans, LocalDate anchor)
    {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<String, Integer>();
        if (anchor == null)
        {
            anchor = LocalDate.now();
        }
        List<SurviveSpan> safeSpans = spans == null ? Collections.<SurviveSpan>emptyList() : spans;
        for (int n = 7; n <= 15; n++)
        {
            LocalDate windowStart = anchor.minusDays(n - 1);
            int surviveSum = 0;
            int useSum = 0;
            for (SurviveSpan span : safeSpans)
            {
                if (span == null)
                {
                    continue;
                }
                LocalDate rangeEnd = span.dropDate == null ? anchor : span.dropDate;
                if (rangeEnd.isAfter(anchor))
                {
                    rangeEnd = anchor;
                }
                if (span.passDate != null)
                {
                    surviveSum += intersectionDays(span.passDate, rangeEnd, windowStart, anchor);
                }
                if (span.customerUseDate != null)
                {
                    useSum += intersectionDays(span.customerUseDate, rangeEnd, windowStart, anchor);
                }
            }
            result.put("s" + n, Integer.valueOf(surviveSum));
            result.put("u" + n, Integer.valueOf(useSum));
        }
        return result;
    }

    /** @deprecated use {@link #windowSummary(List, LocalDate)} */
    public static Map<String, Integer> surviveSummary(List<SurviveSpan> spans, LocalDate today)
    {
        Map<String, Integer> full = windowSummary(spans, today);
        LinkedHashMap<String, Integer> legacy = new LinkedHashMap<String, Integer>();
        for (int n = 7; n <= 15; n++)
        {
            legacy.put(String.valueOf(n), full.get("s" + n));
        }
        return legacy;
    }

    private static int nonNegativeDays(LocalDate start, LocalDate end)
    {
        long days = ChronoUnit.DAYS.between(start, end);
        if (days < 0L)
        {
            return 0;
        }
        if (days > Integer.MAX_VALUE)
        {
            return Integer.MAX_VALUE;
        }
        return (int) days;
    }

    public static final class SurviveSpan
    {
        private final LocalDate passDate;
        private final LocalDate customerUseDate;
        private final LocalDate dropDate;

        public SurviveSpan(LocalDate passDate, LocalDate dropDate)
        {
            this(passDate, null, dropDate);
        }

        public SurviveSpan(LocalDate passDate, LocalDate customerUseDate, LocalDate dropDate)
        {
            this.passDate = passDate;
            this.customerUseDate = customerUseDate;
            this.dropDate = dropDate;
        }
    }
}
