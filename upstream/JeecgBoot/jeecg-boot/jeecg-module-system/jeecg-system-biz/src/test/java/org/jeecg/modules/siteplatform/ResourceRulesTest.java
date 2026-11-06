package org.jeecg.modules.siteplatform;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceRulesTest {
    @Test void normalizesHostnamesBeforeUniquenessChecks() {
        assertEquals("example.com", ResourceRules.hostname("EXAMPLE.com."));
        assertEquals("xn--fsqu00a.xn--0zwm56d", ResourceRules.hostname("例子.测试"));
    }

    @Test void rejectsNonHostnamesAndLocalAddresses() {
        for (String value : new String[]{"https://example.com", "a.com/path", "a.com:443", "*.a.com", "a..com", "-a.com", "a.com..", "127.0.0.1", "localhost", "foo.local", "a_b.com"}) {
            assertThrows(ResourceFailure.class, () -> ResourceRules.hostname(value), value);
        }
    }

    @Test void targetRegistrationRejectsCredentialsScriptsAndPrivateIpLiterals() {
        for (String value : new String[]{"file:///etc/passwd", "javascript:alert(1)", "https://u:p@example.com", "http://127.0.0.1", "http://[::1]", "https://a.com/#secret", "http://localhost"}) {
            assertThrows(ResourceFailure.class, () -> ResourceRules.targetUrl(value), value);
        }
        assertEquals("https://example.com/path?q=1", ResourceRules.targetUrl("https://EXAMPLE.com/path?q=1"));
    }

    @Test void onlyKnownEnvironmentsAndRolesAreAllowed() {
        assertThrows(ResourceFailure.class, () -> ResourceRules.environment("all"));
        assertThrows(ResourceFailure.class, () -> ResourceRules.role("SUPER_ADMIN"));
        assertEquals("prod", ResourceRules.environment("prod"));
    }
}
