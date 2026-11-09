package org.jeecg.modules.attribution;

import static org.jeecg.modules.attribution.AttributionDomain.*;
import static org.jeecg.modules.attribution.AttributionRules.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.dao.DuplicateKeyException;

/** Each aggregate and first decision commits in one relational transaction. */
public class AttributionService {
  final AttributionRepository repo;
  final AttributionProperties props;
  final AttributionRules rules = new AttributionRules();
  final SecureRandom random = new SecureRandom();

  public AttributionService(AttributionRepository r, AttributionProperties p) {
    repo = r;
    props = p;
  }

  String id() {
    return UUID.randomUUID().toString();
  }

  String secret() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  String hmac(String... parts) {
    if (props.getHmacKey() == null
        || props.getHmacKey().getBytes(StandardCharsets.UTF_8).length < 32)
      throw new AttributionFailure(503, "External HMAC key unavailable");
    try {
      Mac m = Mac.getInstance("HmacSHA256");
      m.init(new SecretKeySpec(props.getHmacKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of()
          .formatHex(m.doFinal(repo.encode(parts).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException e) {
      throw new AttributionFailure(503, "Signing unavailable");
    }
  }

  <T> T retry(Supplier<T> work) {
    for (int i = 0; i < 5; i++)
      try {
        return repo.tx(work);
      } catch (DuplicateKeyException e) {
        if (i == 4) throw new AttributionFailure(409, "Concurrent write; retry the same request");
      }
    throw new IllegalStateException();
  }

  Channel channel(Config c, String channel) {
    return c.channels().stream()
        .filter(v -> v.channelId().equals(channel))
        .findFirst()
        .orElseThrow(() -> new AttributionFailure(403, "Channel access denied"));
  }

  List<Binding> managed(Config c, String channel) {
    return c.bindings().stream()
        .filter(b -> b.channelId().equals(channel) && b.bindingScope().equals("SIMPLE_UI"))
        .sorted(Comparator.comparingInt(Binding::sortOrder))
        .toList();
  }

  void editable(Config c, String ch) {
    channel(c, ch);
    if (c.bindings().stream()
        .anyMatch(b -> b.channelId().equals(ch) && !b.bindingScope().equals("SIMPLE_UI")))
      throw new AttributionFailure(409, "Historical rules require a compatible editor");
  }

  Map<String, Object> binding(Binding b) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("clientKey", b.clientKey());
    m.put("bindingId", b.bindingId());
    m.put("offerId", b.offer().offerId());
    m.put("targetUrl", b.offer().targetUrl());
    m.put("weight", b.weight());
    m.put("matchMode", b.matchMode());
    m.put("conditions", b.conditions());
    m.put("sortOrder", b.sortOrder());
    return m;
  }

  Map<String, Object> saved(Config c, String ch) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("applicationId", c.applicationId());
    m.put("applicationCode", c.applicationCode());
    m.put("channelId", ch);
    m.put("channelCode", channel(c, ch).channelCode());
    m.put("configVersion", c.configVersion());
    m.put("promotionCode", null);
    m.put("bindings", managed(c, ch).stream().map(this::binding).toList());
    return m;
  }

  void url(String u, String path) {
    if (!rules.safeUrl(u, props.getAllowedHosts()))
      throw AttributionFailure.field(
          path, "Use an approved public HTTPS host; contact an administrator for domain approval");
  }

  public Map<String, Object> save(Actor a, String app, String ch, String key, JsonNode body) {
    repo.requireMember(a);
    if (key == null
        || key.isBlank()
        || key.length() > 100
        || key.chars().anyMatch(Character::isISOControl))
      throw AttributionFailure.field("Idempotency-Key", "A bounded idempotency key is required");
    String route = digest(app == null ? "create" : app + ":" + ch),
        kd = digest(key),
        rd = digest(repo.encode(body));
    return retry(
        () -> {
          var prev =
              repo.one(
                  "SELECT * FROM attr_admin_idempotency WHERE scope_id=? AND actor_id=? AND"
                      + " route_digest=? AND key_digest=?",
                  a.scopeId(),
                  a.userId(),
                  route,
                  kd);
          if (prev != null) {
            if (!rd.equals(prev.get("request_digest")))
              throw new AttributionFailure(409, "Idempotency key used with another payload");
            if (app != null) repo.authorize(a, app, true);
            return repo.decode((String) prev.get("response_json"), Map.class);
          }
          repo.jdbc.update(
              "INSERT INTO"
                  + " attr_admin_idempotency(scope_id,actor_id,route_digest,key_digest,request_digest,created_at)"
                  + " VALUES(?,?,?,?,?,?)",
              a.scopeId(),
              a.userId(),
              route,
              kd,
              rd,
              System.currentTimeMillis());
          Config old = null;
          if (app != null) {
            repo.authorize(a, app, true);
            old = repo.active(a.scopeId(), app, true);
            editable(old, ch);
          }
          rules.validate(body, old != null);
          if (old == null
              && (body.hasNonNull("expectedConfigVersion") || body.hasNonNull("applicationId")))
            throw AttributionFailure.field(
                "expectedConfigVersion", "Create cannot use existing identifiers");
          if (old != null && !old.configVersion().equals(text(body, "expectedConfigVersion", 64)))
            throw new AttributionFailure(409, "Configuration changed; reload before editing");
          String name = text(body, "applicationName", 100).trim(),
              cn = text(body, "channelName", 100).trim(),
              fallback = text(body, "fallbackUrl", 2048).trim();
          url(fallback, "fallbackUrl");
          String aid = old == null ? id() : app,
              cid = old == null ? id() : ch,
              code = old == null ? "app_" + id().replace("-", "") : old.applicationCode();
          Map<String, Binding> before = new HashMap<>();
          if (old != null) managed(old, ch).forEach(b -> before.put(b.bindingId(), b));
          Set<String> removed = new HashSet<>(), retained = new HashSet<>();
          body.path("removedBindingIds").forEach(v -> removed.add(v.textValue()));
          if (!before.keySet().containsAll(removed))
            throw AttributionFailure.field("removedBindingIds", "Binding outside managed channel");
          List<Binding> changed = new ArrayList<>();
          int pos = 0;
          for (JsonNode n : body.path("offers")) {
            String path = "offers[" + pos + "]";
            Binding b =
                n.hasNonNull("bindingId") ? before.get(n.path("bindingId").textValue()) : null;
            if (n.hasNonNull("bindingId") && b == null)
              throw AttributionFailure.field(
                  path + ".bindingId", "Binding outside managed channel");
            if (b != null && removed.contains(b.bindingId()))
              throw AttributionFailure.field(
                  path + ".bindingId", "Cannot retain and remove the same binding");
            if (b != null) retained.add(b.bindingId());
            List<Condition> conditions = new ArrayList<>();
            for (JsonNode rule : n.path("conditions")) {
              List<String> values = new ArrayList<>();
              rule.path("values").forEach(v -> values.add(v.textValue()));
              var condition =
                  new Condition(
                      rule.path("field").textValue(), rule.path("operator").textValue(), values);
              if (condition.field().equals("COUNTRY")
                  && (b == null || !b.conditions().contains(condition)))
                throw AttributionFailure.field(
                    path + ".conditions",
                    "Trusted country source unavailable; retain or remove existing predicates");
              conditions.add(condition);
            }
            String target = text(n, "targetUrl", 2048, path).trim();
            url(target, path + ".targetUrl");
            Offer offer =
                b == null
                    ? new Offer(id(), target, Set.of(), null, null)
                    : new Offer(
                        b.offer().targetUrl().equals(target) ? b.offer().offerId() : id(),
                        target,
                        b.offer().platforms(),
                        b.offer().validFrom(),
                        b.offer().validTo());
            changed.add(
                new Binding(
                    text(n, "clientKey", 100),
                    b == null ? id() : b.bindingId(),
                    cid,
                    "SIMPLE_UI",
                    n.path("weight").intValue(),
                    n.path("matchMode").textValue(),
                    conditions,
                    offer,
                    pos++));
          }
          Set<String> accounted = new HashSet<>(removed);
          accounted.addAll(retained);
          if (!accounted.equals(before.keySet()))
            throw AttributionFailure.field(
                "removedBindingIds", "Every omitted binding must be explicitly removed");
          List<Channel> channels = new ArrayList<>();
          List<Binding> all = new ArrayList<>();
          if (old == null)
            channels.add(new Channel(cid, "ch_" + id().replace("-", ""), cn, "OWNED"));
          else {
            for (Channel c : old.channels())
              channels.add(
                  c.channelId().equals(cid)
                      ? new Channel(c.channelId(), c.channelCode(), cn, c.mediaType())
                      : c);
            for (Binding b : old.bindings())
              if (!b.channelId().equals(cid) || !b.bindingScope().equals("SIMPLE_UI")) all.add(b);
          }
          all.addAll(changed);
          String version =
              old == null
                  ? "1"
                  : new java.math.BigInteger(old.configVersion())
                      .add(java.math.BigInteger.ONE)
                      .toString();
          Config c =
              new Config(
                  aid,
                  code,
                  name,
                  old == null ? cid : old.currentChannelId(),
                  fallback,
                  version,
                  channels,
                  all);
          if (old == null)
            repo.jdbc.update(
                "INSERT INTO"
                    + " attr_application(application_id,scope_id,application_code,owner_user_id,active_version,enabled)"
                    + " VALUES(?,?,?,?,?,1)",
                aid,
                a.scopeId(),
                code,
                a.userId(),
                version);
          repo.putVersion(a.scopeId(), c);
          if (old != null
              && repo.jdbc.update(
                      "UPDATE attr_application SET active_version=? WHERE scope_id=? AND"
                          + " application_id=? AND active_version=?",
                      version,
                      a.scopeId(),
                      aid,
                      old.configVersion())
                  != 1) throw new AttributionFailure(409, "Configuration changed");
          var response = saved(c, cid);
          repo.jdbc.update(
              "UPDATE attr_admin_idempotency SET response_json=? WHERE scope_id=? AND actor_id=?"
                  + " AND route_digest=? AND key_digest=?",
              repo.encode(response),
              a.scopeId(),
              a.userId(),
              route,
              kd);
          repo.audit(a, aid, "CONFIG_SAVE", Map.of("configVersion", version, "channelId", cid));
          return response;
        });
  }

  public JsonNode configuration(Actor a, String app, String ch) {
    repo.authorize(a, app, false);
    Config c = repo.active(a.scopeId(), app, false);
    editable(c, ch);
    ObjectNode n = repo.json.createObjectNode();
    n.put("applicationName", c.applicationName());
    n.put("channelName", channel(c, ch).channelName());
    n.put("fallbackUrl", c.fallbackUrl());
    n.put("expectedConfigVersion", c.configVersion());
    n.set("offers", repo.json.valueToTree(managed(c, ch).stream().map(this::binding).toList()));
    n.set(
        "managedBindingIds",
        repo.json.valueToTree(managed(c, ch).stream().map(Binding::bindingId).toList()));
    n.putArray("removedBindingIds");
    return n;
  }

  public Map<String, Object> offers(Actor a, String app, String ch, String version) {
    repo.authorize(a, app, false);
    Config c = repo.active(a.scopeId(), app, false);
    channel(c, ch);
    if (!c.configVersion().equals(version))
      throw new AttributionFailure(409, "Configuration changed; refresh the list");
    return Map.of(
        "configVersion",
        c.configVersion(),
        "offers",
        managed(c, ch).stream().map(this::binding).toList());
  }

  public Map<String, Object> list(Actor a, String q, int page, int size) {
    repo.requireMember(a);
    if (q == null || q.length() > 100 || page < 1 || page > 1000000 || size < 1 || size > 100)
      throw new AttributionFailure(400, "Invalid search or pagination");
    List<Map<String, Object>> out = new ArrayList<>();
    for (var row :
        repo.jdbc.queryForList(
            "SELECT a.* FROM attr_application a WHERE a.scope_id=? AND (a.owner_user_id=? OR"
                + " EXISTS(SELECT 1 FROM attr_app_grant g WHERE g.scope_id=a.scope_id AND"
                + " g.application_id=a.application_id AND g.user_id=?)) ORDER BY a.application_id",
            a.scopeId(),
            a.userId(),
            a.userId())) {
      String app = (String) row.get("application_id");
      Config c = repo.active(a.scopeId(), app, false);
      Channel ch = channel(c, c.currentChannelId());
      String needle = q.toLowerCase(Locale.ROOT);
      if (!c.applicationName().toLowerCase(Locale.ROOT).contains(needle)
          && !ch.channelName().toLowerCase(Locale.ROOT).contains(needle)) continue;
      List<Binding> bs = managed(c, ch.channelId());
      boolean write = a.userId().equals(row.get("owner_user_id"));
      if (!write) {
        var grant =
            repo.one(
                "SELECT can_write FROM attr_app_grant WHERE scope_id=? AND application_id=? AND"
                    + " user_id=?",
                a.scopeId(),
                app,
                a.userId());
        write = grant != null && ((Number) grant.get("can_write")).intValue() == 1;
      }
      Map<String, Object> r = new LinkedHashMap<>();
      r.put("applicationId", app);
      r.put("applicationCode", c.applicationCode());
      r.put("applicationName", c.applicationName());
      r.put("channelId", ch.channelId());
      r.put("channelCode", ch.channelCode());
      r.put("channelName", ch.channelName());
      r.put("fallbackUrl", c.fallbackUrl());
      r.put("firstOfferUrl", bs.isEmpty() ? null : bs.get(0).offer().targetUrl());
      r.put("offerCount", bs.size());
      r.put("configVersion", c.configVersion());
      r.put(
          "editable",
          write
              && c.bindings().stream()
                  .noneMatch(
                      b ->
                          b.channelId().equals(ch.channelId())
                              && !b.bindingScope().equals("SIMPLE_UI")));
      out.add(r);
    }
    int from = (int) Math.min((long) (page - 1) * size, out.size());
    return Map.of(
        "records", out.subList(from, Math.min(from + size, out.size())), "total", out.size());
  }

  public Map<String, Object> issueSubject(Actor a, String app, JsonNode body) {
    repo.authorize(a, app, true);
    fields(body, Set.of("subjectId", "identifiers"), "");
    String subject = text(body, "subjectId", 100);
    hmac("check");
    var identifiers = body.path("identifiers");
    if (!identifiers.isArray() || identifiers.size() > 10)
      throw AttributionFailure.field("identifiers", "Expected at most 10 identifiers");
    String credential = secret();
    long expires = expiry(props.getCredentialTtlSeconds());
    return retry(
        () -> {
          repo.authorize(a, app, true);
          for (JsonNode n : identifiers) {
            fields(n, Set.of("type", "value"), "identifiers");
            String type = text(n, "type", 16), value = text(n, "value", 200);
            if (!Set.of("DEVICE_ID", "INSTALL_ID").contains(type))
              throw AttributionFailure.field("identifiers", "Unsupported type");
            String hash = hmac("identifier", a.scopeId(), app, type, value);
            var old =
                repo.one(
                    "SELECT subject_id FROM attr_subject_identifier WHERE scope_id=? AND"
                        + " application_id=? AND identifier_type=? AND identifier_hmac=?",
                    a.scopeId(),
                    app,
                    type,
                    hash);
            if (old != null && !subject.equals(old.get("subject_id")))
              throw new AttributionFailure(409, "Identifier belongs to another subject");
            if (old == null)
              repo.jdbc.update(
                  "INSERT INTO"
                      + " attr_subject_identifier(scope_id,application_id,identifier_type,identifier_hmac,subject_id,key_version)"
                      + " VALUES(?,?,?,?,?,'1')",
                  a.scopeId(),
                  app,
                  type,
                  hash,
                  subject);
          }
          repo.jdbc.update(
              "INSERT INTO"
                  + " attr_subject_credential(credential_digest,scope_id,application_id,subject_id,purpose,expires_at,revoked)"
                  + " VALUES(?,?,?,?,'RESOLVE',?,0)",
              digest(credential),
              a.scopeId(),
              app,
              subject,
              expires);
          repo.audit(a, app, "SUBJECT_ISSUED", Map.of("expiresAt", expires));
          return Map.of("credential", credential, "expiresAt", Long.toString(expires));
        });
  }

  long expiry(int seconds) {
    if (seconds < 1 || seconds > 86400)
      throw new AttributionFailure(503, "Credential lifetime unavailable");
    return System.currentTimeMillis() + seconds * 1000L;
  }

  Subject subject(String bearer) {
    hmac("check");
    if (bearer == null || !bearer.startsWith("Bearer ") || bearer.length() > 256)
      throw new AttributionFailure(401, "Independent subject credential required");
    var row =
        repo.one(
            "SELECT * FROM attr_subject_credential WHERE credential_digest=?",
            digest(bearer.substring(7)));
    if (row == null
        || ((Number) row.get("revoked")).intValue() != 0
        || ((Number) row.get("expires_at")).longValue() <= System.currentTimeMillis()
        || !"RESOLVE".equals(row.get("purpose")))
      throw new AttributionFailure(401, "Subject credential invalid or expired");
    return new Subject(
        (String) row.get("scope_id"),
        (String) row.get("application_id"),
        (String) row.get("subject_id"));
  }

  Config enabled(String scope, String app) {
    if (!props.isEnabled())
      throw new AttributionFailure(503, "Public attribution processing is disabled");
    var row =
        repo.one(
            "SELECT * FROM attr_application WHERE scope_id=? AND application_id=?", scope, app);
    if (row == null) throw new AttributionFailure(404, "Application not found");
    if (((Number) row.get("enabled")).intValue() != 1
        || !props.getEnabledApplications().contains((String) row.get("application_code")))
      throw new AttributionFailure(403, "Application is not enabled for public attribution");
    return repo.version(scope, app, (String) row.get("active_version"));
  }

  boolean available(String scope, String app, Offer offer, String os) {
    long now = System.currentTimeMillis();
    if (offer == null) return false;
    if (offer.validFrom() != null && now < offer.validFrom()
        || offer.validTo() != null && now >= offer.validTo()
        || !offer.platforms().isEmpty() && !offer.platforms().contains(os)
        || repo.blocked(scope, app, "OFFER", offer.offerId())) return false;
    return rules.safeUrl(offer.targetUrl(), props.getAllowedHosts())
        && !repo.blocked(scope, app, "URL", offer.targetUrl());
  }

  String destination(String scope, String app, String original, Offer offer, String os) {
    if (available(scope, app, offer, os)
        || offer == null
            && original != null
            && rules.safeUrl(original, props.getAllowedHosts())
            && !repo.blocked(scope, app, "URL", original)) return original;
    Config c = repo.active(scope, app, false);
    if (!rules.safeUrl(c.fallbackUrl(), props.getAllowedHosts())
        || repo.blocked(scope, app, "URL", c.fallbackUrl()))
      throw new AttributionFailure(503, "No safe destination available");
    return c.fallbackUrl();
  }

  static final Set<String>
      AF = Set.of("pid", "c", "af_c_id", "af_adset", "af_adset_id", "af_ad", "af_ad_id"),
      FB = Set.of("campaign_id", "campaign_name", "adset_id", "adset_name", "ad_id", "ad_name");

  Map<String, String> parameters(JsonNode body, String field, Set<String> allowed) {
    var n = body.path(field);
    if (!n.isObject() || n.size() > allowed.size())
      throw AttributionFailure.field(field, "Expected supported string parameters");
    Map<String, String> result = new LinkedHashMap<>();
    n.fieldNames()
        .forEachRemaining(
            k -> {
              if (!allowed.contains(k))
                throw AttributionFailure.field(field, "Unsupported media parameter");
              result.put(k, text(n, k, 500));
            });
    return result;
  }

  public Map<String, Object> click(Actor a, String app, String ch, JsonNode body, String ua) {
    repo.authorize(a, app, true);
    channel(enabled(a.scopeId(), app), ch);
    hmac("check");
    fields(
        body,
        Set.of(
            "subjectId", "clickRef", "entryKey", "afLinkParams", "fbLinkParams", "evidenceSource"),
        "");
    String subject = text(body, "subjectId", 100),
        click = text(body, "clickRef", 100),
        entry = text(body, "entryKey", 100);
    Map<String, String> af = parameters(body, "afLinkParams", AF),
        fb = parameters(body, "fbLinkParams", FB);
    if ((!af.isEmpty() || !fb.isEmpty())
        && (!body.hasNonNull("evidenceSource")
            || !props.getTrustedEvidenceSources().contains(text(body, "evidenceSource", 100))))
      throw new AttributionFailure(403, "Trusted media source is not configured");
    repo.rate("click-app:" + a.scopeId() + ":" + app, props.getApplicationRequestsPerMinute());
    return retry(
        () -> {
          repo.authorize(a, app, true);
          Config c = enabled(a.scopeId(), app);
          channel(c, ch);
          var rows =
              repo.jdbc.queryForList(
                  "SELECT payload FROM attr_click_snapshot WHERE scope_id=? AND application_id=?"
                      + " AND (click_ref=? OR entry_key=?)",
                  a.scopeId(),
                  app,
                  click,
                  entry);
          Click snap;
          if (!rows.isEmpty()) {
            snap = repo.decode((String) rows.get(0).get("payload"), Click.class);
            if (rows.size() > 1
                || !snap.subjectId().equals(subject)
                || !snap.channelId().equals(ch))
              throw new AttributionFailure(403, "Click belongs to another subject or entry");
          } else {
            var owner =
                repo.one(
                    "SELECT subject_id FROM attr_subject_credential WHERE scope_id=? AND"
                        + " application_id=? AND subject_id=? AND revoked=0 AND expires_at>?",
                    a.scopeId(),
                    app,
                    subject,
                    System.currentTimeMillis());
            if (owner == null)
              throw new AttributionFailure(403, "Verified subject binding required");
            String os = rules.os(ua);
            Map<String, String> ctx = Map.of("OS", os, "COUNTRY", "UNKNOWN");
            List<Binding> bs = c.bindings().stream().filter(b -> b.channelId().equals(ch)).toList();
            var candidates =
                bs.stream()
                    .filter(
                        b ->
                            b.weight() > 0
                                && rules.matches(b, ctx)
                                && available(a.scopeId(), app, b.offer(), os))
                    .map(Binding::bindingId)
                    .toList();
            Binding selected =
                rules.select(
                    bs,
                    ctx,
                    hmac("selection", a.scopeId(), app, ch, c.configVersion(), click),
                    b -> available(a.scopeId(), app, b.offer(), os));
            String target = selected == null ? c.fallbackUrl() : selected.offer().targetUrl();
            if (selected == null
                && (!rules.safeUrl(target, props.getAllowedHosts())
                    || repo.blocked(a.scopeId(), app, "URL", target)))
              throw new AttributionFailure(503, "No safe destination available");
            snap =
                new Click(
                    a.scopeId(),
                    app,
                    ch,
                    click,
                    entry,
                    subject,
                    c.configVersion(),
                    selected == null ? null : selected.bindingId(),
                    selected == null ? null : selected.offer(),
                    target,
                    candidates,
                    af,
                    fb,
                    os);
            repo.jdbc.update(
                "INSERT INTO"
                    + " attr_click_snapshot(scope_id,application_id,click_ref,entry_key,subject_id,channel_id,payload,created_at)"
                    + " VALUES(?,?,?,?,?,?,?,?)",
                a.scopeId(),
                app,
                click,
                entry,
                subject,
                ch,
                repo.encode(snap),
                System.currentTimeMillis());
            repo.jdbc.update(
                "INSERT INTO"
                    + " attr_attribution_state(scope_id,application_id,click_ref,snapshot_version,evidence_id,state,created_at)"
                    + " VALUES(?,?,?,'1',?,'PENDING',?)",
                a.scopeId(),
                app,
                click,
                "initial:" + id(),
                System.currentTimeMillis());
          }
          String token =
              hmac(
                  "resolve-token",
                  snap.scopeId(),
                  app,
                  snap.channelId(),
                  snap.clickRef(),
                  snap.subjectId());
          if (repo.one(
                  "SELECT token_digest FROM attr_resolve_token WHERE token_digest=?", digest(token))
              == null)
            repo.jdbc.update(
                "INSERT INTO"
                    + " attr_resolve_token(token_digest,scope_id,application_id,subject_id,channel_id,click_ref,purpose,expires_at,revoked)"
                    + " VALUES(?,?,?,?,?,?,'ATTRIBUTION_TOKEN',?,0)",
                digest(token),
                snap.scopeId(),
                app,
                snap.subjectId(),
                snap.channelId(),
                snap.clickRef(),
                expiry(props.getTokenTtlSeconds()));
          return Map.of(
              "clickRef",
              snap.clickRef(),
              "resolveToken",
              token,
              "applicationCode",
              c.applicationCode(),
              "configVersion",
              snap.configVersion());
        });
  }

  public ResolveResponse resolve(String bearer, JsonNode body, String ua) {
    fields(
        body,
        Set.of("applicationCode", "applicationId", "clickRef", "resolveToken", "resolveTokenType"),
        "");
    String code = text(body, "applicationCode", 64),
        token = text(body, "resolveToken", 200),
        type =
            body.hasNonNull("resolveTokenType")
                ? text(body, "resolveTokenType", 32)
                : "ATTRIBUTION_TOKEN";
    if (!Set.of("ATTRIBUTION_TOKEN", "DEVICE_ID", "INSTALL_ID").contains(type))
      throw AttributionFailure.field("resolveTokenType", "Unsupported token type");
    Subject subject = subject(bearer);
    var row =
        repo.one(
            "SELECT application_id,scope_id FROM attr_application WHERE application_code=?", code);
    if (row == null) throw new AttributionFailure(404, "Application not found");
    String app = (String) row.get("application_id"), scope = (String) row.get("scope_id");
    if (!subject.applicationId().equals(app) || !subject.scopeId().equals(scope))
      throw new AttributionFailure(403, "Subject not authorized for this application");
    if (body.hasNonNull("applicationId") && !app.equals(text(body, "applicationId", 64)))
      throw new AttributionFailure(403, "Application ID mismatch");
    Config active = enabled(scope, app);
    repo.rate("resolve-app:" + scope + ":" + app, props.getApplicationRequestsPerMinute());
    repo.rate(
        "resolve-subject:" + scope + ":" + app + ":" + subject.subjectId(),
        props.getSubjectRequestsPerMinute());
    if (type.equals("ATTRIBUTION_TOKEN")) {
      var bound = repo.one("SELECT * FROM attr_resolve_token WHERE token_digest=?", digest(token));
      if (bound == null
          || ((Number) bound.get("revoked")).intValue() != 0
          || ((Number) bound.get("expires_at")).longValue() <= System.currentTimeMillis()
          || !"ATTRIBUTION_TOKEN".equals(bound.get("purpose")))
        throw new AttributionFailure(401, "Attribution token invalid or expired");
      if (!scope.equals(bound.get("scope_id"))
          || !app.equals(bound.get("application_id"))
          || !subject.subjectId().equals(bound.get("subject_id")))
        throw new AttributionFailure(403, "Attribution token binding mismatch");
      String ref = (String) bound.get("click_ref");
      if (body.hasNonNull("clickRef") && !ref.equals(text(body, "clickRef", 100)))
        throw new AttributionFailure(403, "Click binding mismatch");
      Click snap = repo.click(scope, app, ref);
      if (snap == null) throw new AttributionFailure(404, "Real attribution record not found");
      if (!snap.subjectId().equals(subject.subjectId())
          || !snap.channelId().equals(bound.get("channel_id")))
        throw new AttributionFailure(403, "Real snapshot binding mismatch");
      return new ResolveResponse(
          ref,
          token,
          code,
          null,
          (String) repo.state(scope, app, ref).get("state"),
          "REAL",
          destination(scope, app, snap.originalUrl(), snap.offer(), rules.os(ua)),
          snap.afLinkParams(),
          snap.fbLinkParams());
    }
    String hash = hmac("identifier", scope, app, type, token);
    var binding =
        repo.one(
            "SELECT subject_id FROM attr_subject_identifier WHERE scope_id=? AND application_id=?"
                + " AND identifier_type=? AND identifier_hmac=?",
            scope,
            app,
            type,
            hash);
    if (binding == null || !subject.subjectId().equals(binding.get("subject_id")))
      throw new AttributionFailure(403, "Identifier not bound to authenticated subject");
    if (body.hasNonNull("clickRef")) {
      Click click = repo.click(scope, app, text(body, "clickRef", 100));
      if (click == null || !click.subjectId().equals(subject.subjectId()))
        throw new AttributionFailure(403, "Additional click association proof required");
    }
    return retry(
        () -> {
          String fallback = destination(scope, app, null, null, "UNKNOWN");
          var existing =
              repo.one(
                  "SELECT payload FROM attr_fallback_snapshot WHERE scope_id=? AND application_id=?"
                      + " AND identifier_type=? AND identifier_hmac=?",
                  scope,
                  app,
                  type,
                  hash);
          Map<String, Map<String, String>> template;
          if (existing != null) {
            template = repo.decode((String) existing.get("payload"), Map.class);
          } else {
            String stem = "mock_" + hmac("template", scope, app, type, hash).substring(0, 16);
            Map<String, String> af =
                Map.of(
                    "pid",
                    "mock_internal_fallback",
                    "c",
                    stem + "_campaign",
                    "af_c_id",
                    stem + "_campaign_id",
                    "af_adset",
                    stem + "_set",
                    "af_adset_id",
                    stem + "_set_id",
                    "af_ad",
                    stem + "_ad",
                    "af_ad_id",
                    stem + "_ad_id");
            Map<String, String> fb =
                Map.of(
                    "campaign_name",
                    af.get("c"),
                    "campaign_id",
                    af.get("af_c_id"),
                    "adset_name",
                    af.get("af_adset"),
                    "adset_id",
                    af.get("af_adset_id"),
                    "ad_name",
                    af.get("af_ad"),
                    "ad_id",
                    af.get("af_ad_id"));
            template = Map.of("af", af, "fb", fb);
            repo.jdbc.update(
                "INSERT INTO"
                    + " attr_fallback_snapshot(scope_id,application_id,identifier_type,identifier_hmac,key_version,template_version,internal_channel_id,payload,created_at)"
                    + " VALUES(?,?,?,?,'1',?,?,?,?)",
                scope,
                app,
                type,
                hash,
                active.configVersion(),
                "internal_fallback",
                repo.encode(template),
                System.currentTimeMillis());
          }
          return new ResolveResponse(
              null,
              token,
              code,
              null,
              "FALLBACK",
              "SIMULATED",
              fallback,
              template.get("af"),
              template.get("fb"));
        });
  }

  public void safety(Actor a, String app, JsonNode body) {
    repo.authorize(a, app, true);
    fields(body, Set.of("kind", "value", "blocked", "reason"), "");
    String kind = text(body, "kind", 16),
        value = text(body, "value", 2048),
        reason = text(body, "reason", 500);
    if (!Set.of("URL", "OFFER").contains(kind) || !body.path("blocked").isBoolean())
      throw new AttributionFailure(400, "Invalid safety operation");
    retry(
        () -> {
          repo.authorize(a, app, true);
          repo.active(a.scopeId(), app, true);
          String hash = digest(value);
          var exists =
              repo.one(
                  "SELECT blocked FROM attr_safety WHERE scope_id=? AND application_id=? AND kind=?"
                      + " AND value_digest=?",
                  a.scopeId(),
                  app,
                  kind,
                  hash);
          if (exists == null)
            repo.jdbc.update(
                "INSERT INTO"
                    + " attr_safety(scope_id,application_id,kind,value_digest,blocked,metadata_json)"
                    + " VALUES(?,?,?,?,?,?)",
                a.scopeId(),
                app,
                kind,
                hash,
                body.path("blocked").booleanValue() ? 1 : 0,
                "{}");
          else
            repo.jdbc.update(
                "UPDATE attr_safety SET blocked=? WHERE scope_id=? AND application_id=? AND kind=?"
                    + " AND value_digest=?",
                body.path("blocked").booleanValue() ? 1 : 0,
                a.scopeId(),
                app,
                kind,
                hash);
          repo.audit(
              a,
              app,
              "SAFETY_CHANGE",
              Map.of(
                  "kind",
                  kind,
                  "valueDigest",
                  hash,
                  "blocked",
                  body.path("blocked").booleanValue(),
                  "reason",
                  reason));
          return true;
        });
  }

  public void evidence(Actor a, String app, String click, JsonNode body) {
    repo.authorize(a, app, true);
    fields(body, Set.of("expectedSnapshotVersion", "evidenceId", "state", "reason"), "");
    String version = text(body, "expectedSnapshotVersion", 64),
        evidence = text(body, "evidenceId", 100),
        state = text(body, "state", 16),
        reason = text(body, "reason", 500);
    if (!Set.of("PENDING", "MATCHED", "UNMATCHED").contains(state))
      throw AttributionFailure.field("state", "Expected real attribution state");
    retry(
        () -> {
          repo.authorize(a, app, true);
          repo.active(a.scopeId(), app, true);
          if (repo.click(a.scopeId(), app, click) == null)
            throw new AttributionFailure(404, "Real click not found");
          var same =
              repo.one(
                  "SELECT * FROM attr_attribution_state WHERE scope_id=? AND application_id=? AND"
                      + " evidence_id=?",
                  a.scopeId(),
                  app,
                  evidence);
          if (same != null) {
            if (!click.equals(same.get("click_ref")) || !state.equals(same.get("state")))
              throw new AttributionFailure(409, "Evidence ID reused with different state");
            return true;
          }
          var current = repo.state(a.scopeId(), app, click);
          if (!version.equals(current.get("snapshot_version")))
            throw new AttributionFailure(409, "Attribution state changed");
          String next = new java.math.BigInteger(version).add(java.math.BigInteger.ONE).toString();
          repo.jdbc.update(
              "INSERT INTO"
                  + " attr_attribution_state(scope_id,application_id,click_ref,snapshot_version,evidence_id,state,created_at)"
                  + " VALUES(?,?,?,?,?,?,?)",
              a.scopeId(),
              app,
              click,
              next,
              evidence,
              state,
              System.currentTimeMillis());
          repo.audit(
              a,
              app,
              "EVIDENCE_APPEND",
              Map.of(
                  "clickRef",
                  click,
                  "snapshotVersion",
                  next,
                  "evidenceId",
                  evidence,
                  "state",
                  state,
                  "reason",
                  reason));
          return true;
        });
  }

  public Map<String, Object> diagnostics(Actor a, String app, String click) {
    repo.authorize(a, app, false);
    Click snap = repo.click(a.scopeId(), app, click);
    if (snap == null) throw new AttributionFailure(404, "Click not found");
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("clickRef", snap.clickRef());
    out.put("channelId", snap.channelId());
    out.put("configVersion", snap.configVersion());
    out.put("bindingId", snap.bindingId());
    out.put("offerId", snap.offer() == null ? null : snap.offer().offerId());
    out.put("candidateBindingIds", snap.candidates());
    out.put("snapshotVersion", repo.state(a.scopeId(), app, click).get("snapshot_version"));
    try {
      String target = destination(a.scopeId(), app, snap.originalUrl(), snap.offer(), snap.os());
      out.put(
          "destinationSource",
          target.equals(snap.originalUrl()) && snap.offer() != null ? "OFFER" : "APP_DEFAULT");
      out.put(
          "reason",
          target.equals(snap.originalUrl()) ? "ORIGINAL_AVAILABLE" : "ORIGINAL_UNAVAILABLE");
    } catch (AttributionFailure e) {
      out.put("destinationSource", "UNAVAILABLE");
      out.put("reason", "NO_SAFE_DESTINATION");
    }
    return out;
  }

  public void grant(Actor a, String app, JsonNode body) {
    var application = repo.authorize(a, app, true);
    if (!a.userId().equals(application.get("owner_user_id")))
      throw new AttributionFailure(403, "Only application owner may manage grants");
    fields(body, Set.of("userId", "canWrite", "revoked"), "");
    String user = text(body, "userId", 64);
    if (!body.path("canWrite").isBoolean() || !body.path("revoked").isBoolean())
      throw new AttributionFailure(400, "Grant flags must be booleans");
    repo.requireMember(new Actor(a.scopeId(), user));
    retry(
        () -> {
          repo.active(a.scopeId(), app, true);
          repo.jdbc.update(
              "DELETE FROM attr_app_grant WHERE scope_id=? AND application_id=? AND user_id=?",
              a.scopeId(),
              app,
              user);
          if (!body.path("revoked").booleanValue())
            repo.jdbc.update(
                "INSERT INTO attr_app_grant(scope_id,application_id,user_id,can_write)"
                    + " VALUES(?,?,?,?)",
                a.scopeId(),
                app,
                user,
                body.path("canWrite").booleanValue() ? 1 : 0);
          repo.audit(
              a,
              app,
              "APP_GRANT",
              Map.of(
                  "userId",
                  user,
                  "canWrite",
                  body.path("canWrite").booleanValue(),
                  "revoked",
                  body.path("revoked").booleanValue()));
          return true;
        });
  }
}
