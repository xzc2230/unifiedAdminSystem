package org.jeecg.modules.packagemanagement;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.jeecg.modules.packagemanagement.application.packstat.util.PackStatMonthFilter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PackagePackStatMonthFilterTest
{
    @Test
    public void includesWhenMonthInsidePassToDrop()
    {
        assertTrue(PackStatMonthFilter.overlapsMonth(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 10, 15),
                YearMonth.of(2026, 9)));
    }

    @Test
    public void includesWhenDropNullTreatAsInfinity()
    {
        assertTrue(PackStatMonthFilter.overlapsMonth(
                LocalDate.of(2026, 8, 20),
                null,
                YearMonth.of(2026, 9)));
        assertTrue(PackStatMonthFilter.overlapsMonth(
                LocalDate.of(2026, 9, 1),
                null,
                YearMonth.of(2026, 12)));
        assertFalse(PackStatMonthFilter.overlapsMonth(
                LocalDate.of(2026, 10, 1),
                null,
                YearMonth.of(2026, 9)));
    }

    @Test
    public void excludesWhenMonthOutsideInterval()
    {
        assertFalse(PackStatMonthFilter.overlapsMonth(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                YearMonth.of(2026, 9)));
        assertFalse(PackStatMonthFilter.overlapsMonth(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 1),
                YearMonth.of(2026, 9)));
    }

    @Test
    public void includesBoundaryMonths()
    {
        assertTrue(PackStatMonthFilter.overlapsMonth(
                LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 9, 20),
                YearMonth.of(2026, 9)));
        assertTrue(PackStatMonthFilter.overlapsMonth(
                LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 10, 1),
                YearMonth.of(2026, 8)));
        assertTrue(PackStatMonthFilter.overlapsMonth(
                LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 10, 1),
                YearMonth.of(2026, 10)));
    }
}
