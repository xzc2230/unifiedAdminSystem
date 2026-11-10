package org.jeecg.modules.packagemanagement;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.jeecg.modules.packagemanagement.application.packstat.detect.PackStatDetectOutcomeApply;
import org.jeecg.modules.packagemanagement.application.packstat.detect.PackStatDetectResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PackagePackStatDetectOutcomeApplyTest
{
    @Test
    public void invalidDoesNotUpdateStats()
    {
        assertNull(PackStatDetectOutcomeApply.apply(
                PackStatDetectResult.INVALID,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 14)));
    }

    @Test
    public void normalUpdatesDurationsWithoutDropDate()
    {
        PackStatDetectOutcomeApply.StatsUpdate update = PackStatDetectOutcomeApply.apply(
                PackStatDetectResult.NORMAL,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 14));
        assertNotNull(update);
        assertNull(update.getDropDate());
        assertEquals(Integer.valueOf(13), update.getSurviveDays());
        assertEquals(Integer.valueOf(4), update.getUseDays());
    }

    @Test
    public void delistedSetsDropDateAndDurations()
    {
        PackStatDetectOutcomeApply.StatsUpdate update = PackStatDetectOutcomeApply.apply(
                PackStatDetectResult.DELISTED,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2026, 9, 14));
        assertNotNull(update);
        assertEquals(java.sql.Date.valueOf(LocalDate.of(2026, 9, 14)), update.getDropDate());
        assertEquals(Integer.valueOf(13), update.getSurviveDays());
        assertEquals(Integer.valueOf(6), update.getUseDays());
    }
}
