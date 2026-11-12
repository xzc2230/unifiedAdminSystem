package org.jeecg.modules.packagemanagement;

import org.jeecg.modules.packagemanagement.application.apps.support.BlockedProviders;
import org.jeecg.modules.packagemanagement.application.apps.support.PackageCountryRegions;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PackageCountryRegionsTest {
    @Test void normalizesOldChineseAliasesAndUppercaseCodes() {
        assertEquals("br,ph,sg", PackageCountryRegions.normalizeAreas(" 巴西,BR,菲律賓, SG "));
        assertEquals("ae", PackageCountryRegions.code("阿联酌"));
        assertEquals("", PackageCountryRegions.normalizeAreas(""));
        assertNull(PackageCountryRegions.normalizeAreas(null));
        assertEquals("未识别地区", PackageCountryRegions.normalizeAreas("未识别地区"));
    }
    @Test void membershipIsExactAndUnknownCountriesFailClosed() {
        assertTrue(PackageCountryRegions.matches("BR,菲律宾", "ph"));
        assertTrue(PackageCountryRegions.matches("br,ph", "巴西"));
        assertFalse(PackageCountryRegions.matches("br,ph", "r"));
        assertFalse(PackageCountryRegions.matches("本地", "本地"));
        assertFalse(PackageCountryRegions.matches(null, "br"));
        assertFalse(PackageCountryRegions.matches("", "br"));
    }
    @Test void providerCodeTakesPriorityAndProviderBlockStillActsAsUs() {
        var geo = Map.of("country", "Brazil", "countryCode", "BR", "status", "false");
        assertEquals("br", PackageCountryRegions.accessCode(geo));
        assertEquals("br", PackageCountryRegions.geoCode(geo));
        assertEquals("us", PackageCountryRegions.accessCode(Map.of("countryCode", "br", "status", "true")));
        assertEquals("", PackageCountryRegions.geoCode(Map.of()));
    }
    @Test void requestIpShowsKnownCountryCodesInChinese() {
        assertEquals("(巴西)203.0.113.8", PackageCountryRegions.displayReqIp("(br)203.0.113.8"));
        assertEquals("(美国)1.1.1.1", PackageCountryRegions.displayReqIp("(US)1.1.1.1"));
        assertEquals("(zz)1.1.1.1", PackageCountryRegions.displayReqIp("(zz)1.1.1.1"));
        assertEquals("(中国)1.1.1.1", PackageCountryRegions.displayReqIp("(中国)1.1.1.1"));
        assertEquals("203.0.113.8", PackageCountryRegions.displayReqIp("203.0.113.8"));
        assertNull(PackageCountryRegions.displayReqIp(null));
    }
    @Test void countryCodeLinkWinsOverLegacyAliasesAndUnmatchedLinkUsesDefault() {
        JSONObject links = JSONObject.of("巴西", "old", "br", "new", "PH", "philippines");
        assertEquals("new", PackageCountryRegions.regionalLink(links,"BR","default"));
        assertEquals("philippines", PackageCountryRegions.regionalLink(links,"ph","default"));
        assertEquals("default", PackageCountryRegions.regionalLink(links,"sg","default"));
    }
    @Test void providerSelectionMatchesOnlyChosenCarriers() {
        assertEquals("google,facebook", BlockedProviders.detect("AS32934 Facebook, Inc.", "Google LLC", "local"));
        assertEquals("apple", BlockedProviders.detect("Apple Inc", "local", "local"));
        assertEquals("meta", BlockedProviders.detect("AS32934", "Meta Platforms, Inc.", "local"));
        assertEquals("tiktok", BlockedProviders.detect("AS138699 ByteDance", "TikTok Pte. Ltd.", "local"));
        assertEquals("", BlockedProviders.detect("AS1 Amazon", "Amazon.com", "Amazon"));
        assertEquals("google,apple,meta,facebook,tiktok", BlockedProviders.normalize("tiktok,amazon,google,unknown,apple,meta,facebook"));
        assertNull(BlockedProviders.normalize(null));
        assertEquals("", BlockedProviders.hit("apple", "google"));
        assertEquals("google", BlockedProviders.hit("google,apple", "google"));
        assertEquals("", BlockedProviders.hit("amazon", "amazon"));
    }
}
