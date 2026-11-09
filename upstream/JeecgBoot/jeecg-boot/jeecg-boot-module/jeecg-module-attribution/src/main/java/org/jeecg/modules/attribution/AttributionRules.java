package org.jeecg.modules.attribution;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.jeecg.modules.attribution.AttributionDomain.*;

/** Pure rules. Unknown attributes never satisfy either positive or negative predicates. */
public final class AttributionRules {
  @FunctionalInterface
  interface HostResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  private final HostResolver resolver;

  public AttributionRules() {
    this(InetAddress::getAllByName);
  }

  AttributionRules(HostResolver resolver) {
    this.resolver = Objects.requireNonNull(resolver);
  }

  public boolean matches(Binding b, Map<String, String> context) {
    if (b.conditions().isEmpty()) return true;
    var results =
        b.conditions().stream()
            .map(
                c -> {
                  String actual = context.get(c.field());
                  if (actual == null || actual.equals("UNKNOWN") || actual.isBlank()) return false;
                  boolean known =
                      c.field().equals("OS")
                          ? Set.of("ANDROID", "IOS", "WEB").contains(actual)
                          : c.field().equals("COUNTRY")
                              && Set.of(Locale.getISOCountries()).contains(actual);
                  if (!known) return false;
                  boolean contains = c.values().contains(actual);
                  return c.operator().equals("IS")
                      ? contains
                      : c.operator().equals("IS_NOT") && !contains;
                });
    return b.matchMode().equals("AND")
        ? results.allMatch(Boolean::booleanValue)
        : b.matchMode().equals("OR") && results.anyMatch(Boolean::booleanValue);
  }

  public Binding select(
      List<Binding> bindings,
      Map<String, String> context,
      String seed,
      java.util.function.Predicate<Binding> available) {
    List<Binding> candidates =
        bindings.stream()
            .filter(b -> b.weight() > 0 && matches(b, context) && available.test(b))
            .sorted(Comparator.comparingInt(Binding::sortOrder).thenComparing(Binding::bindingId))
            .toList();
    long total = candidates.stream().mapToLong(Binding::weight).sum();
    if (total == 0) return null;
    long slot = new BigInteger(digest(seed), 16).mod(BigInteger.valueOf(total)).longValue();
    for (var b : candidates) {
      if (slot < b.weight()) return b;
      slot -= b.weight();
    }
    throw new IllegalStateException("weighted interval");
  }

  public static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public void validate(JsonNode n, boolean countryEnabled) {
    fields(
        n,
        Set.of(
            "applicationName",
            "channelName",
            "fallbackUrl",
            "offers",
            "expectedConfigVersion",
            "managedBindingIds",
            "removedBindingIds",
            "applicationId",
            "applicationCode",
            "channelId",
            "channelCode",
            "configVersion",
            "promotionCode"),
        "");
    text(n, "applicationName", 100);
    text(n, "channelName", 100);
    text(n, "fallbackUrl", 2048);
    for (String field :
        List.of(
            "expectedConfigVersion",
            "applicationId",
            "applicationCode",
            "channelId",
            "channelCode",
            "configVersion")) if (n.hasNonNull(field)) text(n, field, 100);
    JsonNode offers = n.get("offers");
    if (offers == null || !offers.isArray() || offers.size() > 100)
      throw AttributionFailure.field("offers", "Expected at most 100 offers");
    Set<String> keys = new HashSet<>(), ids = new HashSet<>();
    boolean positive = offers.isEmpty();
    int i = 0;
    for (var o : offers) {
      String path = "offers[" + i++ + "]";
      fields(
          o,
          Set.of(
              "clientKey",
              "bindingId",
              "offerId",
              "targetUrl",
              "weight",
              "matchMode",
              "conditions",
              "sortOrder"),
          path);
      String key = text(o, "clientKey", 100, path);
      if (!keys.add(key))
        throw AttributionFailure.field(path + ".clientKey", "Duplicate client key");
      if (o.hasNonNull("bindingId") && !ids.add(text(o, "bindingId", 100, path)))
        throw AttributionFailure.field(path + ".bindingId", "Duplicate binding");
      if (o.hasNonNull("offerId")) text(o, "offerId", 100, path);
      text(o, "targetUrl", 2048, path);
      var weight = o.get("weight");
      if (weight == null
          || !weight.isIntegralNumber()
          || !weight.canConvertToInt()
          || weight.intValue() < 0
          || weight.intValue() > 1000000)
        throw AttributionFailure.field(path + ".weight", "Use an integer between 0 and 1000000");
      positive |= weight.intValue() > 0;
      String mode = text(o, "matchMode", 3, path);
      if (!Set.of("AND", "OR").contains(mode))
        throw AttributionFailure.field(path + ".matchMode", "Expected AND or OR");
      var conditions = o.get("conditions");
      if (conditions == null || !conditions.isArray() || conditions.size() > 20)
        throw AttributionFailure.field(
            path + ".conditions", "Expected a flat condition array (maximum 20)");
      int c = 0;
      for (var rule : conditions) {
        String rp = path + ".conditions[" + c++ + "]";
        fields(rule, Set.of("field", "operator", "values"), rp);
        String field = text(rule, "field", 7, rp), op = text(rule, "operator", 6, rp);
        if (!Set.of("OS", "COUNTRY").contains(field) || field.equals("COUNTRY") && !countryEnabled)
          throw AttributionFailure.field(rp + ".field", "Trusted condition source unavailable");
        if (!Set.of("IS", "IS_NOT").contains(op))
          throw AttributionFailure.field(rp + ".operator", "Unknown operator");
        var values = rule.get("values");
        if (values == null || !values.isArray() || values.isEmpty() || values.size() > 250)
          throw AttributionFailure.field(rp + ".values", "Nonempty values required");
        Set<String> seen = new HashSet<>();
        for (var value : values) {
          if (!value.isTextual()
              || !(field.equals("OS")
                      ? Set.of("ANDROID", "IOS", "WEB")
                      : Set.of(Locale.getISOCountries()))
                  .contains(value.textValue())
              || !seen.add(value.textValue()))
            throw AttributionFailure.field(rp + ".values", "Invalid or duplicate condition value");
        }
      }
    }
    if (!positive)
      throw AttributionFailure.field("offers", "At least one offer must have positive weight");
    for (String field : List.of("managedBindingIds", "removedBindingIds"))
      if (n.has(field)) {
        var values = n.get(field);
        if (!values.isArray() || values.size() > 100)
          throw AttributionFailure.field(field, "Expected string ID array");
        Set<String> seen = new HashSet<>();
        for (var v : values)
          if (!v.isTextual() || v.textValue().isBlank() || !seen.add(v.textValue()))
            throw AttributionFailure.field(field, "Invalid or duplicate ID");
      }
  }

  public static void fields(JsonNode n, Set<String> fields, String path) {
    if (n == null || !n.isObject()) throw AttributionFailure.field(path, "Expected object");
    n.fieldNames()
        .forEachRemaining(
            k -> {
              if (!fields.contains(k))
                throw AttributionFailure.field(
                    path.isEmpty() ? k : path + "." + k, "Unsupported field");
            });
  }

  public static String text(JsonNode n, String key, int max) {
    return text(n, key, max, "");
  }

  public static String text(JsonNode n, String key, int max, String path) {
    var v = n.get(key);
    if (v == null
        || !v.isTextual()
        || v.textValue().isBlank()
        || v.textValue().length() > max
        || v.textValue().chars().anyMatch(c -> Character.isISOControl(c)))
      throw AttributionFailure.field(
          path.isEmpty() ? key : path + "." + key,
          "Nonempty string required (maximum " + max + ")");
    return v.textValue();
  }

  public boolean safeUrl(String url, Set<String> hosts) {
    try {
      if (url == null
          || url.length() > 2048
          || url.chars().anyMatch(c -> c <= 32 || c == 127)
          || url.contains("\\")) return false;
      String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
      if (decoded.chars().anyMatch(c -> Character.isISOControl(c))
          || decoded.matches("(?is).*%(?:0[0-9a-f]|1[0-9a-f]|7f).*")) return false;
      URI uri = new URI(url);
      String host = uri.getHost();
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || host == null
          || uri.getRawUserInfo() != null
          || uri.getFragment() != null
          || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
      host = host.toLowerCase(Locale.ROOT);
      if (!hosts.contains(host)
          || host.endsWith(".")
          || !host.matches("(?=.{1,253}$)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")
          || !host.contains(".")
          || host.matches("[0-9.]+")) return false;
      for (String namespace :
          Set.of(
              "localhost",
              "local",
              "localdomain",
              "internal",
              "test",
              "invalid",
              "example",
              "arpa",
              "onion",
              "alt",
              "lan",
              "home",
              "corp",
              "intranet",
              "private"))
        if (host.equals(namespace) || host.endsWith("." + namespace)) return false;
      if (uri.getRawQuery() != null)
        for (String part : uri.getRawQuery().split("&")) {
          String key =
              URLDecoder.decode(part.split("=", 2)[0], StandardCharsets.UTF_8)
                  .toLowerCase(Locale.ROOT);
          if (Set.of(
                  "redirect",
                  "redirect_uri",
                  "redirect_url",
                  "return",
                  "return_url",
                  "returnto",
                  "url",
                  "next",
                  "continue",
                  "destination")
              .contains(key)) return false;
        }
      InetAddress[] addresses = resolver.resolve(host);
      if (addresses == null || addresses.length == 0) return false;
      for (InetAddress address : addresses) if (!publicAddress(address)) return false;
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean publicAddress(InetAddress address) {
    if (address == null
        || address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) return false;
    byte[] bytes = address.getAddress();
    int first = bytes[0] & 255, second = bytes[1] & 255;
    if (bytes.length == 4)
      return first != 0
          && first != 10
          && first != 127
          && first < 224
          && !(first == 100 && second >= 64 && second <= 127)
          && !(first == 169 && second == 254)
          && !(first == 172 && second >= 16 && second <= 31)
          && !(first == 192
              && (second == 168 || second == 0 && ((bytes[2] & 255) == 0 || (bytes[2] & 255) == 2)))
          && !(first == 198
              && (second == 18 || second == 19 || second == 51 && (bytes[2] & 255) == 100))
          && !(first == 203 && second == 0 && (bytes[2] & 255) == 113);
    // Only global unicast IPv6; exclude its documentation range as well.
    return bytes.length == 16
        && (first & 224) == 32
        && !(first == 32 && second == 1 && (bytes[2] & 255) == 13 && (bytes[3] & 255) == 184);
  }

  public String os(String ua) {
    if (ua == null || ua.length() > 2048) return "UNKNOWN";
    if (ua.contains("Android")) return "ANDROID";
    if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) return "IOS";
    if (ua.contains("Macintosh")
        && ua.contains("Safari")
        && !ua.contains("Chrome/")
        && !ua.contains("Chromium/")) return "UNKNOWN";
    if (ua.startsWith("Mozilla/")
        && (ua.contains("Windows NT") || ua.contains("Macintosh") || ua.contains("X11")))
      return "WEB";
    return "UNKNOWN";
  }
}
