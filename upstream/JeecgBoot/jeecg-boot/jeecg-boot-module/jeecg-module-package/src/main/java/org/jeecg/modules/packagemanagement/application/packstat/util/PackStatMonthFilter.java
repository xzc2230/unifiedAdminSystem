package org.jeecg.modules.packagemanagement.application.packstat.util;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Year-month list filter: selected month overlaps [passDate, dropDate],
 * where null dropDate means +infinity.
 */
public final class PackStatMonthFilter
{
    private PackStatMonthFilter()
    {
    }

    public static boolean overlapsMonth(LocalDate passDate, LocalDate dropDate, YearMonth month)
    {
        if (passDate == null || month == null)
        {
            return false;
        }
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        LocalDate rangeEnd = dropDate == null ? LocalDate.MAX : dropDate;
        return !passDate.isAfter(monthEnd) && !rangeEnd.isBefore(monthStart);
    }
}
