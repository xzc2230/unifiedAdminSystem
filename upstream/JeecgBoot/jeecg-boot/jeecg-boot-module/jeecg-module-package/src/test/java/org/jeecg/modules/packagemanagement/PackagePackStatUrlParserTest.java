package org.jeecg.modules.packagemanagement;

import org.jeecg.modules.packagemanagement.application.packstat.util.PackStatUrlParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PackagePackStatUrlParserTest
{
    @Test
    public void parseGooglePlayPackage()
    {
        PackStatUrlParser.Parsed parsed = PackStatUrlParser.tryParse(
                "https://play.google.com/store/apps/details?id=com.example.app");
        assertNotNull(parsed);
        assertEquals("android", parsed.getPlatform());
        assertEquals("com.example.app", parsed.getAppId());
        assertEquals("", parsed.getRegion());
    }

    @Test
    public void parseIosStoreRegion()
    {
        PackStatUrlParser.Parsed parsed = PackStatUrlParser.tryParse(
                "https://apps.apple.com/cn/app/demo/id123456");
        assertNotNull(parsed);
        assertEquals("ios", parsed.getPlatform());
        assertEquals("cn", parsed.getRegion());
        assertEquals("123456", parsed.getAppId());
    }

    @Test
    public void invalidUrlReturnsNull()
    {
        assertNull(PackStatUrlParser.tryParse("not-a-store-url"));
        assertNull(PackStatUrlParser.tryParse(null));
        assertNull(PackStatUrlParser.tryParse("  "));
    }
}
