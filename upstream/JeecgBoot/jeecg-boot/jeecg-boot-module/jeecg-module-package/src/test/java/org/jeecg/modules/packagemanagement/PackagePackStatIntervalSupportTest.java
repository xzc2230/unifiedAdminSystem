package org.jeecg.modules.packagemanagement;

import org.jeecg.modules.packagemanagement.application.packstat.detect.PackStatIntervalSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PackagePackStatIntervalSupportTest
{
    @Test
    public void invalidIncreasesIntervalCappedAt60()
    {
        assertEquals(3, PackStatIntervalSupport.nextIntervalSeconds(2, true));
        assertEquals(60, PackStatIntervalSupport.nextIntervalSeconds(60, true));
        assertEquals(60, PackStatIntervalSupport.nextIntervalSeconds(59, true));
    }

    @Test
    public void successDecreasesIntervalFlooredAt0()
    {
        assertEquals(1, PackStatIntervalSupport.nextIntervalSeconds(2, false));
        assertEquals(0, PackStatIntervalSupport.nextIntervalSeconds(0, false));
        assertEquals(0, PackStatIntervalSupport.nextIntervalSeconds(1, false));
    }
}
