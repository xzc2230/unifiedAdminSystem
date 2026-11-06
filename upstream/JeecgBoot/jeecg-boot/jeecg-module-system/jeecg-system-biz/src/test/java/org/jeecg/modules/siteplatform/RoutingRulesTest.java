package org.jeecg.modules.siteplatform;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import static org.junit.jupiter.api.Assertions.*;
class RoutingRulesTest {
    private boolean matches(String rule, String type, String path) throws Exception {
        try { return (boolean) Class.forName("org.jeecg.modules.siteplatform.RoutingRules").getMethod("matches", String.class, String.class, String.class).invoke(null, rule, type, path); }
        catch (ClassNotFoundException e) { fail("RoutingRules implementation is missing"); return false; }
        catch (InvocationTargetException e) { throw (RuntimeException) e.getCause(); }
    }
    @Test void prefixMatchesSegmentsOnly() throws Exception {
        assertTrue(matches("/api", "PREFIX", "/api"));
        assertTrue(matches("/api", "PREFIX", "/api/v1"));
        assertFalse(matches("/api", "PREFIX", "/apiculture"));
        assertTrue(matches("/", "PREFIX", "/any"));
    }
    @Test void exactDoesNotMatchDescendants() throws Exception {
        assertTrue(matches("/api", "EXACT", "/api"));
        assertFalse(matches("/api", "EXACT", "/api/v1"));
    }
    @Test void canonicalPathsRejectAmbiguityAndUppercaseEscapes() {
        for (String value : new String[]{"/a//b", "/a/../b", "/./a", "/a/%2ehtml", "/a/%2F/b", "/a/%5c", "/a/%", "/a/%xz", "/a?x=1", "/a#x", "/a\\b", "/a b", "/中文"})
            assertThrows(ResourceFailure.class, () -> RoutingRules.path(value), value);
        assertEquals("/x/%AB", RoutingRules.path("/x/%ab"));
    }
    @Test void ruleBoundsAndUniqueIdentityPriorityAreValidated() {
        var a = new RoutingRequests.RuleInput("a", null, "/api", "PREFIX", 1, "t");
        var b = new RoutingRequests.RuleInput("b", null, "/b", "EXACT", 1, "t");
        assertThrows(ResourceFailure.class, () -> RoutingRules.normalize(new RoutingRequests.DraftInput(0, "t", java.util.List.of("d"), java.util.List.of(a,b))));
        for (int priority : new int[]{-1,1000001}) {
            var rule = new RoutingRequests.RuleInput("a", null, "/", "EXACT", priority, "t");
            assertThrows(ResourceFailure.class, () -> RoutingRules.normalize(new RoutingRequests.DraftInput(0, "t", java.util.List.of("d"), java.util.List.of(rule))));
        }
        assertThrows(ResourceFailure.class, () -> RoutingRules.normalize(new RoutingRequests.DraftInput(0, "t", java.util.List.of("d"), java.util.Collections.nCopies(101, a))));
    }
    @Test void candidateBytesAreDeterministicAndDoNotContainExtraResourceFields() {
        var input = new RoutingRequests.DraftInput(0, "t", java.util.List.of("z", "a"), java.util.List.of());
        var site = java.util.Map.<String,Object>of("enabled", 1, "environment", "test");
        var a = java.util.Map.<String,Object>of("id", "a", "hostname", "a.example.com", "verified", 1, "challenge", "never-output");
        var z = java.util.Map.<String,Object>of("id", "z", "hostname", "z.example.com", "verified", 1);
        var target = java.util.Map.<String,Object>of("id", "t", "url", "https://origin.example.com/base/%ab", "enabled", 1);
        var first = RoutingRules.candidate(100, "site", site, input, java.util.List.of(z,a), java.util.List.of(target), "release");
        var second = RoutingRules.candidate(100, "site", site, input, java.util.List.of(a,z), java.util.List.of(target), "release");
        assertEquals(first, second); assertEquals(first.hash(), RoutingRules.hash(first.payload()));
        assertTrue(first.payload().contains("/base/%AB")); assertFalse(first.payload().contains("never-output"));
    }
    @Test void routedTargetsCannotHaveQueryOrAmbiguousBasePathOrProxyBackToPublishedHost() {
        for (String url : new String[]{"https://origin.example.com/?x=1", "https://origin.example.com/a/../b", "https://origin.example.com/%2e", "https://origin.example.com//b"})
            assertThrows(ResourceFailure.class, () -> RoutingRules.targetUrl(url), url);
        assertThrows(ResourceFailure.class, () -> RoutingRules.candidate(100, "site", java.util.Map.of("enabled", 1, "environment", "test"),
            new RoutingRequests.DraftInput(0, "t", java.util.List.of("d"), java.util.List.of()),
            java.util.List.of(java.util.Map.of("id", "d", "hostname", "www.example.com", "verified", 1)),
            java.util.List.of(java.util.Map.of("id", "t", "url", "https://www.example.com", "enabled", 1)), "release"));
    }
}
