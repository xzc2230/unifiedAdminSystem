package org.jeecg.modules.packagemanagement;

import org.jeecg.modules.packagemanagement.application.packstat.detect.PackStatDetectResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PackagePackStatDetectResultTest
{
    @Test
    public void http404IsDelisted()
    {
        assertEquals(PackStatDetectResult.DELISTED, PackStatDetectResult.fromHttpStatus(404));
    }

    @Test
    public void http200AndRedirectsAreNormal()
    {
        assertEquals(PackStatDetectResult.NORMAL, PackStatDetectResult.fromHttpStatus(200));
        assertEquals(PackStatDetectResult.NORMAL, PackStatDetectResult.fromHttpStatus(301));
        assertEquals(PackStatDetectResult.NORMAL, PackStatDetectResult.fromHttpStatus(302));
    }

    @Test
    public void otherCodesAndExceptionsAreInvalid()
    {
        assertEquals(PackStatDetectResult.INVALID, PackStatDetectResult.fromHttpStatus(500));
        assertEquals(PackStatDetectResult.INVALID, PackStatDetectResult.fromHttpStatus(403));
        assertEquals(PackStatDetectResult.INVALID, PackStatDetectResult.fromHttpStatus(-1));
        assertEquals(PackStatDetectResult.INVALID, PackStatDetectResult.fromHttpStatus(303));
    }
}
