package org.jeecg.modules.packagemanagement;

import org.junit.jupiter.api.Test;
import org.jeecg.modules.packagemanagement.application.packstat.detect.PackStatNotifyEmailSupport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PackagePackStatNotifyEmailSupportTest
{
    @Test
    public void prefersRowEmailOverGlobal()
    {
        assertEquals("a@x.com,b@y.com",
                PackStatNotifyEmailSupport.resolve(" a@x.com,b@y.com ", "global@z.com"));
    }

    @Test
    public void fallsBackToGlobalWhenRowEmpty()
    {
        assertEquals("global@z.com", PackStatNotifyEmailSupport.resolve("  ", " global@z.com "));
        assertEquals("global@z.com", PackStatNotifyEmailSupport.resolve(null, "global@z.com"));
    }

    @Test
    public void returnsNullWhenBothEmpty()
    {
        assertNull(PackStatNotifyEmailSupport.resolve(null, null));
        assertNull(PackStatNotifyEmailSupport.resolve("", "  "));
    }

    @Test
    public void splitsCommaSeparatedRecipients()
    {
        assertArrayEquals(new String[] {"a@x.com", "b@y.com"},
                PackStatNotifyEmailSupport.splitRecipients("a@x.com, b@y.com,,"));
        assertArrayEquals(new String[0], PackStatNotifyEmailSupport.splitRecipients(null));
    }
}
