package org.jeecg.modules.packagemanagement;

import org.jeecg.modules.packagemanagement.application.packstat.util.PackStatDateCalc;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PackagePackStatDateCalcTest
{
    @Test
    public void surviveAndUseDaysEmptyWhenDropDateMissing()
    {
        LocalDate pass = LocalDate.of(2026, 9, 1);
        LocalDate use = LocalDate.of(2026, 9, 2);
        assertNull(PackStatDateCalc.surviveDays(pass, null));
        assertNull(PackStatDateCalc.useDays(use, null));
    }

    @Test
    public void aliveDurationsUseTodayAsEndDate()
    {
        LocalDate pass = LocalDate.of(2026, 9, 1);
        LocalDate use = LocalDate.of(2026, 9, 10);
        LocalDate today = LocalDate.of(2026, 9, 14);
        assertEquals(Integer.valueOf(13), PackStatDateCalc.surviveDays(pass, today));
        assertEquals(Integer.valueOf(4), PackStatDateCalc.useDays(use, today));
    }

    @Test
    public void surviveAndUseDaysAreNonNegativeDatediff()
    {
        LocalDate pass = LocalDate.of(2026, 9, 1);
        LocalDate use = LocalDate.of(2026, 9, 8);
        LocalDate drop = LocalDate.of(2026, 9, 10);
        assertEquals(Integer.valueOf(9), PackStatDateCalc.surviveDays(pass, drop));
        assertEquals(Integer.valueOf(2), PackStatDateCalc.useDays(use, drop));
        assertEquals(Integer.valueOf(0), PackStatDateCalc.surviveDays(drop, pass));
    }

    @Test
    public void sameDayIntersectionIsZero()
    {
        LocalDate day = LocalDate.of(2026, 9, 10);
        assertEquals(0, PackStatDateCalc.intersectionDays(day, day, day, day));
    }

    @Test
    public void eachWindowHasSurviveAndUseWithTodayAnchor()
    {
        LocalDate today = LocalDate.of(2026, 9, 14);
        LocalDate pass = LocalDate.of(2026, 9, 1);
        LocalDate use = LocalDate.of(2026, 9, 8);
        LocalDate drop = LocalDate.of(2026, 9, 10);
        Map<String, Integer> summary = PackStatDateCalc.windowSummary(
                Collections.singletonList(new PackStatDateCalc.SurviveSpan(pass, use, drop)), today);
        assertEquals(Integer.valueOf(2), summary.get("s7"));
        assertEquals(Integer.valueOf(2), summary.get("u7"));
        assertEquals(Integer.valueOf(9), summary.get("s15"));
        assertEquals(Integer.valueOf(2), summary.get("u15"));
    }

    @Test
    public void monthAnchorUsesMonthLastDay()
    {
        LocalDate monthEnd = LocalDate.of(2026, 9, 30);
        LocalDate pass = LocalDate.of(2026, 9, 20);
        LocalDate use = LocalDate.of(2026, 9, 22);
        LocalDate drop = null;
        Map<String, Integer> summary = PackStatDateCalc.windowSummary(
                Collections.singletonList(new PackStatDateCalc.SurviveSpan(pass, use, drop)), monthEnd);
        // 7-day window [9/24, 9/30]; survive [9/20, 9/30] overlap = 6
        assertEquals(Integer.valueOf(6), summary.get("s7"));
        // use [9/22, 9/30] ∩ [9/24, 9/30] = 6
        assertEquals(Integer.valueOf(6), summary.get("u7"));
    }

    @Test
    public void resolveAnchorPastMonthUsesLastDayCurrentUsesTodayFutureNull()
    {
        LocalDate today = LocalDate.of(2026, 9, 14);
        assertEquals(today, PackStatDateCalc.resolveSummaryAnchor(null, today));
        assertEquals(today, PackStatDateCalc.resolveSummaryAnchor("", today));
        assertEquals(today, PackStatDateCalc.resolveSummaryAnchor("2026-09", today));
        assertEquals(LocalDate.of(2026, 8, 31), PackStatDateCalc.resolveSummaryAnchor("2026-08", today));
        assertNull(PackStatDateCalc.resolveSummaryAnchor("2026-10", today));
    }
}
