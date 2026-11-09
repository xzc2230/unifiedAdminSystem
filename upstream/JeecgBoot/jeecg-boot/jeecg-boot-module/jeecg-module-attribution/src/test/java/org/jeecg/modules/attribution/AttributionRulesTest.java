package org.jeecg.modules.attribution;

import static org.jeecg.modules.attribution.AttributionDomain.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import org.junit.jupiter.api.Test;

class AttributionRulesTest {
  final AttributionRules rules =
      new AttributionRules(host -> new InetAddress[] {InetAddress.getByName("93.184.216.34")});

  Binding b(String id, int weight, String field, String op, String... values) {
    return new Binding(
        id,
        id,
        "ch",
        "SIMPLE_UI",
        weight,
        "AND",
        field == null ? List.of() : List.of(new Condition(field, op, List.of(values))),
        new Offer(id, "https://example.com/" + id, Set.of(), null, null),
        0);
  }

  @Test
  void unknownIsNotNeverMatches() {
    assertFalse(rules.matches(b("a", 7, "OS", "IS_NOT", "ANDROID"), Map.of("OS", "UNKNOWN")));
    assertFalse(rules.matches(b("b", 3, "COUNTRY", "IS_NOT", "US"), Map.of()));
  }

  @Test
  void eachBindingFiltersBeforeAllCandidateWeightedSelection() {
    var a = b("a", 7, "OS", "IS", "ANDROID");
    var b = b("b", 3, "COUNTRY", "IS", "US");
    var zero = b("z", 0, null, null);
    int ac = 0, bc = 0;
    for (int i = 0; i < 1000; i++) {
      var selected =
          rules.select(
              List.of(a, b, zero),
              Map.of("OS", "ANDROID", "COUNTRY", "US"),
              "scope|app|ch|v1|" + i,
              x -> true);
      assertNotNull(selected);
      if (selected.bindingId().equals("a")) ac++;
      else if (selected.bindingId().equals("b")) bc++;
      else fail("zero weight selected");
    }
    assertTrue(ac > 600 && ac < 800);
    assertEquals(1000, ac + bc);
    assertEquals(
        a,
        rules.select(List.of(a, b), Map.of("OS", "ANDROID", "COUNTRY", "CN"), "same", x -> true));
    assertEquals(
        b, rules.select(List.of(a, b), Map.of("OS", "IOS", "COUNTRY", "US"), "same", x -> true));
    assertNull(rules.select(List.of(a, b), Map.of("OS", "UNKNOWN"), "same", x -> true));
    assertNull(
        rules.select(List.of(a, b), Map.of("OS", "ANDROID", "COUNTRY", "US"), "same", x -> false));
  }

  @Test
  void orAndMultiValueAndStableDecision() {
    var a = b("a", 1, "OS", "IS_NOT", "ANDROID", "IOS");
    assertTrue(rules.matches(a, Map.of("OS", "WEB")));
    assertFalse(rules.matches(a, Map.of("OS", "IOS")));
    var or =
        new Binding(
            "o",
            "o",
            "ch",
            "SIMPLE_UI",
            1,
            "OR",
            List.of(
                new Condition("OS", "IS_NOT", List.of("ANDROID")),
                new Condition("COUNTRY", "IS", List.of("US"))),
            a.offer(),
            0);
    assertTrue(rules.matches(or, Map.of("OS", "UNKNOWN", "COUNTRY", "US")));
    assertFalse(rules.matches(or, Map.of("OS", "UNKNOWN")));
    assertEquals(
        rules.select(List.of(a, or), Map.of("OS", "WEB"), "fixed", x -> true),
        rules.select(List.of(a, or), Map.of("OS", "WEB"), "fixed", x -> true));
  }

  @Test
  void urlParsesExactHostAndRejectsUnsafeForms() {
    assertTrue(rules.safeUrl("https://example.com/path?q=a%20b", Set.of("example.com")));
    for (String u :
        List.of(
            "http://example.com/a",
            "https://example.com.evil.org/",
            "https://user@example.com/",
            "https://127.0.0.1/",
            "https://10.0.0.1/",
            "https://example.com:444/",
            "https://example.com/%0afoo",
            "https://example.com/?redirect=https://evil.org",
            "https://localhost/",
            "https://router.home.arpa/",
            "https://[::1]/"))
      assertFalse(
          rules.safeUrl(
              u,
              Set.of(
                  "example.com", "127.0.0.1", "10.0.0.1", "localhost", "::1", "router.home.arpa")),
          u);
  }

  @Test
  void strictSaveRejectsAllZeroFractionalWeightsAndUnavailableCountry() throws Exception {
    var mapper = new ObjectMapper();
    for (String weight : List.of("0", "-1", "1.2", "\"1\"")) {
      var n =
          mapper.readTree(
              "{\"applicationName\":\"a\",\"channelName\":\"c\",\"fallbackUrl\":\"https://example.com\",\"offers\":[{\"clientKey\":\"a\",\"targetUrl\":\"https://example.com\",\"weight\":"
                  + weight
                  + ",\"matchMode\":\"AND\",\"conditions\":[]}]}");
      assertThrows(RuntimeException.class, () -> rules.validate(n, false));
    }
    rules.validate(
        mapper.readTree(
            "{\"applicationName\":\"a\",\"channelName\":\"c\",\"fallbackUrl\":\"https://example.com\",\"offers\":[]}"),
        false);
  }

  @Test
  void reservedNamespacesAreRejectedBeforeDnsEvenWhenAllowlisted() {
    var noDns =
        new AttributionRules(
            host -> {
              throw new AssertionError("Reserved host reached DNS: " + host);
            });
    for (String host :
        List.of(
            "home.arpa",
            "router.home.arpa",
            "host.localhost",
            "host.local",
            "host.internal",
            "host.test",
            "host.invalid",
            "host.localdomain",
            "host.lan",
            "host.onion",
            "host.alt",
            "host.example")) assertFalse(noDns.safeUrl("https://" + host, Set.of(host)), host);
  }

  @Test
  void allowlistedHostsMustResolveExclusivelyToPublicAddresses() {
    for (String ip :
        List.of(
            "0.0.0.0",
            "127.0.0.1",
            "10.1.2.3",
            "172.16.0.1",
            "192.168.1.1",
            "169.254.169.254",
            "100.64.0.1",
            "198.18.0.1",
            "192.0.2.1",
            "224.0.0.1",
            "240.0.0.1",
            "::1",
            "fe80::1",
            "fc00::1",
            "fd00::1",
            "ff02::1",
            "2001:db8::1",
            "::ffff:10.0.0.1")) {
      var resolved =
          new AttributionRules(
              host ->
                  new InetAddress[] {
                    InetAddress.getByName("93.184.216.34"), InetAddress.getByName(ip)
                  });
      assertFalse(resolved.safeUrl("https://example.com/path", Set.of("example.com")), ip);
    }
    var ipv6 =
        new AttributionRules(
            host -> new InetAddress[] {InetAddress.getByName("2606:4700:4700::1111")});
    assertTrue(ipv6.safeUrl("https://example.com", Set.of("example.com")));
  }

  @Test
  void dnsFailureAndEmptyAnswersFailClosed() {
    var failure =
        new AttributionRules(
            host -> {
              throw new UnknownHostException(host);
            });
    var empty = new AttributionRules(host -> new InetAddress[0]);
    assertFalse(failure.safeUrl("https://example.com", Set.of("example.com")));
    assertFalse(empty.safeUrl("https://example.com", Set.of("example.com")));
  }

  @Test
  void ambiguousDesktopIpadCannotMatchNegativeOsRule() {
    String os =
        rules.os(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15 Version/17.0"
                + " Mobile/15E148 Safari/604.1");
    assertEquals("UNKNOWN", os);
    assertFalse(rules.matches(b("a", 1, "OS", "IS_NOT", "IOS"), Map.of("OS", os)));
    assertEquals(
        "WEB",
        rules.os(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/537.36 Chrome/130.0"
                + " Safari/537.36"));
  }

  @Test
  void osComesFromUserAgentOnly() {
    assertEquals("ANDROID", rules.os("Mozilla/5.0 (Linux; Android 14)"));
    assertEquals("IOS", rules.os("Mozilla/5.0 (iPhone; CPU iPhone OS 17)"));
    assertEquals("WEB", rules.os("Mozilla/5.0 (Windows NT 10.0)"));
    assertEquals("UNKNOWN", rules.os("curl/8"));
    assertEquals(
        "UNKNOWN",
        rules.os("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1 Safari/605.1"));
  }
}
