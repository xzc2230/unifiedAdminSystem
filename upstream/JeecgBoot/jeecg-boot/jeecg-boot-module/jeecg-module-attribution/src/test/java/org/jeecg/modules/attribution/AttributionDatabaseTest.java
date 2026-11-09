package org.jeecg.modules.attribution;

import static org.jeecg.modules.attribution.AttributionDomain.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class AttributionDatabaseTest {
  JdbcTemplate jdbc;
  AttributionRepository repo;
  AttributionService service;
  AttributionProperties props;
  final ObjectMapper json = new ObjectMapper();
  final Actor actor = new Actor("1", "u1");

  JsonNode node(String s) throws Exception {
    return json.readTree(s);
  }

  JsonNode createBody() throws Exception {
    return node(
        """
{"applicationName":"App A","channelName":"Owned","fallbackUrl":"https://example.com/fallback","offers":[{"clientKey":"a","targetUrl":"https://example.com/offer","weight":1,"matchMode":"AND","conditions":[]}]}
""");
  }

  @BeforeEach
  void setup() {
    var ds =
        new DriverManagerDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
            "sa",
            "");
    jdbc = new JdbcTemplate(ds);
    new ResourceDatabasePopulator(new ClassPathResource("db/attribution/001-schema.sql"))
        .execute(ds);
    jdbc.execute("CREATE TABLE sys_user(id VARCHAR(64),status INT,del_flag INT)");
    jdbc.execute(
        "CREATE TABLE sys_tenant(id INT,status INT,del_flag INT,begin_date TIMESTAMP,end_date"
            + " TIMESTAMP)");
    jdbc.execute(
        "CREATE TABLE sys_user_tenant(user_id VARCHAR(64),tenant_id INT,status VARCHAR(8))");
    jdbc.update("INSERT INTO sys_user VALUES('u1',1,0),('u2',1,0)");
    jdbc.update("INSERT INTO sys_tenant VALUES(1,1,0,NULL,NULL),(2,1,0,NULL,NULL)");
    jdbc.update("INSERT INTO sys_user_tenant VALUES('u1',1,'1'),('u2',1,'1'),('u2',2,'1')");
    props = new AttributionProperties();
    props.setEnabled(true);
    props.setAllowedHosts(Set.of("example.com"));
    props.setHmacKey("test-only-key-with-at-least-32-characters");
    props.setSubjectRequestsPerMinute(10000);
    props.setApplicationRequestsPerMinute(10000);
    repo = new AttributionRepository(jdbc, new DataSourceTransactionManager(ds));
    service = new AttributionService(repo, props);
  }

  Map<String, Object> create() throws Exception {
    var result = service.save(actor, null, null, "create-key", createBody());
    assertFalse(result.isEmpty(), "Configuration save must return a committed application");
    props.setEnabledApplications(Set.of((String) result.get("applicationCode")));
    return result;
  }

  String app(Map<String, Object> c) {
    return (String) c.get("applicationId");
  }

  String ch(Map<String, Object> c) {
    return (String) c.get("channelId");
  }

  String credential(Map<String, Object> c, String subject, String identifier) throws Exception {
    return (String)
        service
            .issueSubject(
                actor,
                app(c),
                node(
                    "{\"subjectId\":\""
                        + subject
                        + "\",\"identifiers\":[{\"type\":\"INSTALL_ID\",\"value\":\""
                        + identifier
                        + "\"}]}"))
            .get("credential");
  }

  JsonNode simulated(Map<String, Object> c, String id) throws Exception {
    return node(
        "{\"applicationCode\":\""
            + c.get("applicationCode")
            + "\",\"resolveTokenType\":\"INSTALL_ID\",\"resolveToken\":\""
            + id
            + "\"}");
  }

  @Test
  void configAtomicIdempotentCasAndExplicitRemoval() throws Exception {
    var c = create();
    assertEquals(c, service.save(actor, null, null, "create-key", createBody()));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM attr_application", Integer.class));
    var changed = (com.fasterxml.jackson.databind.node.ObjectNode) createBody();
    changed.put("applicationName", "different");
    assertThrows(
        AttributionFailure.class, () -> service.save(actor, null, null, "create-key", changed));
    var edit = service.configuration(actor, app(c), ch(c));
    assertEquals("1", edit.path("expectedConfigVersion").asText());
    var omitted = edit.deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) omitted).putArray("offers");
    assertThrows(
        AttributionFailure.class, () -> service.save(actor, app(c), ch(c), "omit", omitted));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM attr_config_version", Integer.class));
    var good = edit.deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) good).put("applicationName", "New name");
    var v2 = service.save(actor, app(c), ch(c), "edit", good);
    assertEquals("2", v2.get("configVersion"));
    assertThrows(AttributionFailure.class, () -> service.save(actor, app(c), ch(c), "stale", edit));
    assertEquals("1", repo.version("1", app(c), "1").configVersion());
  }

  @Test
  void transactionRollbackAndTenantOwnership() throws Exception {
    var c = create();
    assertThrows(
        AttributionFailure.class, () -> service.configuration(new Actor("2", "u2"), app(c), ch(c)));
    assertThrows(
        AttributionFailure.class, () -> service.configuration(new Actor("1", "u2"), app(c), ch(c)));
    jdbc.update("UPDATE sys_tenant SET status=0 WHERE id=1");
    assertThrows(AttributionFailure.class, () -> service.configuration(actor, app(c), ch(c)));
    jdbc.update("UPDATE sys_tenant SET status=1 WHERE id=1");
    assertThrows(
        RuntimeException.class,
        () ->
            repo.tx(
                () -> {
                  jdbc.update("UPDATE attr_application SET active_version='broken'");
                  throw new IllegalStateException("abort");
                }));
    assertEquals("1", repo.active("1", app(c), false).configVersion());
  }

  @Test
  void stableSimulatedSnapshotsSubjectBindingAndDigestOnlyPersistence() throws Exception {
    var c = create();
    String raw = "1930000000000000001", bearer = credential(c, "subject-a", raw);
    var request = simulated(c, raw);
    var first = service.resolve("Bearer " + bearer, request, "Mozilla/5.0 (Linux; Android 14)");
    var second = service.resolve("Bearer " + bearer, request, "Mozilla/5.0 (Linux; Android 14)");
    assertEquals(first, second);
    assertEquals("SIMULATED", first.parameterSource());
    assertNull(first.clickRef());
    assertEquals(7, first.afLinkParams().size());
    assertEquals(6, first.fbLinkParams().size());
    assertTrue(first.afLinkParams().values().stream().allMatch(v -> v.startsWith("mock_")));
    assertEquals(9, json.valueToTree(first).size());
    assertEquals(
        1, jdbc.queryForObject("SELECT COUNT(*) FROM attr_fallback_snapshot", Integer.class));
    assertFalse(
        jdbc.queryForObject("SELECT identifier_hmac FROM attr_fallback_snapshot", String.class)
            .contains(raw));
    assertNotEquals(
        bearer,
        jdbc.queryForObject("SELECT credential_digest FROM attr_subject_credential", String.class));
    String other = credential(c, "subject-b", "other");
    assertThrows(AttributionFailure.class, () -> service.resolve("Bearer " + other, request, ""));
    assertThrows(AttributionFailure.class, () -> service.resolve(null, request, ""));
    props.setHmacKey(null);
    assertThrows(AttributionFailure.class, () -> service.resolve("Bearer " + bearer, request, ""));
  }

  @Test
  void concurrentHundredClicksFirstWinsHistoricalRealAndSafetyFallback() throws Exception {
    var c = create();
    String bearer = credential(c, "subject-a", "install");
    JsonNode click =
        node(
            "{\"subjectId\":\"subject-a\",\"clickRef\":\"click-1\",\"entryKey\":\"entry-1\",\"afLinkParams\":{},\"fbLinkParams\":{}}");
    ExecutorService pool = Executors.newFixedThreadPool(10);
    try {
      List<Callable<Map<String, Object>>> calls = new ArrayList<>();
      for (int i = 0; i < 100; i++)
        calls.add(
            () -> service.click(actor, app(c), ch(c), click, "Mozilla/5.0 (Linux; Android 14)"));
      for (var f : pool.invokeAll(calls)) assertEquals("click-1", f.get().get("clickRef"));
    } finally {
      pool.shutdownNow();
    }
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM attr_click_snapshot", Integer.class));
    var delivered = service.click(actor, app(c), ch(c), click, "");
    String token = (String) delivered.get("resolveToken");
    JsonNode req =
        node(
            "{\"applicationCode\":\""
                + c.get("applicationCode")
                + "\",\"resolveTokenType\":\"ATTRIBUTION_TOKEN\",\"resolveToken\":\""
                + token
                + "\"}");
    var original = service.resolve("Bearer " + bearer, req, "Mozilla/5.0 (Linux; Android 14)");
    assertEquals("REAL", original.parameterSource());
    assertEquals("PENDING", original.resolutionState());
    assertEquals("https://example.com/offer", original.destinationUrl());
    assertTrue(original.afLinkParams().isEmpty());
    service.safety(
        actor,
        app(c),
        node(
            "{\"kind\":\"URL\",\"value\":\"https://example.com/offer\",\"blocked\":true,\"reason\":\"test"
                + " revoke\"}"));
    var fallback = service.resolve("Bearer " + bearer, req, "");
    assertEquals("https://example.com/fallback", fallback.destinationUrl());
    assertEquals("REAL", fallback.parameterSource());
    service.evidence(
        actor,
        app(c),
        "click-1",
        node(
            "{\"expectedSnapshotVersion\":\"1\",\"evidenceId\":\"ev1\",\"state\":\"MATCHED\",\"reason\":\"verified"
                + " owned event\"}"));
    assertEquals("MATCHED", service.resolve("Bearer " + bearer, req, "").resolutionState());
    assertThrows(
        AttributionFailure.class,
        () -> service.resolve("Bearer " + credential(c, "subject-b", "b"), req, ""));
    service.safety(
        actor,
        app(c),
        node(
            "{\"kind\":\"URL\",\"value\":\"https://example.com/fallback\",\"blocked\":true,\"reason\":\"test"
                + " revoke\"}"));
    assertEquals(
        503,
        assertThrows(AttributionFailure.class, () -> service.resolve("Bearer " + bearer, req, ""))
            .status);
  }

  @Test
  void distributedDatabaseRateLimitAndDefaultOff() throws Exception {
    var c = create();
    String bearer = credential(c, "s", "id");
    props.setSubjectRequestsPerMinute(1);
    service.resolve("Bearer " + bearer, simulated(c, "id"), "");
    assertEquals(
        429,
        assertThrows(
                AttributionFailure.class,
                () -> service.resolve("Bearer " + bearer, simulated(c, "id"), ""))
            .status);
    props.setEnabled(false);
    assertEquals(
        503,
        assertThrows(
                AttributionFailure.class,
                () -> service.resolve("Bearer " + bearer, simulated(c, "id"), ""))
            .status);
  }

  @Test
  void concurrentSaveAndSimulationHaveOneCommittedFirstResult() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(10);
    try {
      var body = createBody();
      List<Callable<Map<String, Object>>> creates = new ArrayList<>();
      for (int i = 0; i < 20; i++)
        creates.add(() -> service.save(actor, null, null, "parallel-create", body));
      var results = pool.invokeAll(creates);
      var c = results.get(0).get();
      for (var result : results) assertEquals(c, result.get());
      assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM attr_application", Integer.class));
      props.setEnabledApplications(Set.of((String) c.get("applicationCode")));
      String bearer = credential(c, "subject", "install-concurrent");
      var request = simulated(c, "install-concurrent");
      List<Callable<ResolveResponse>> resolves = new ArrayList<>();
      for (int i = 0; i < 50; i++)
        resolves.add(() -> service.resolve("Bearer " + bearer, request, ""));
      var snapshots = pool.invokeAll(resolves);
      var first = snapshots.get(0).get();
      for (var result : snapshots) assertEquals(first, result.get());
      assertEquals(
          1, jdbc.queryForObject("SELECT COUNT(*) FROM attr_fallback_snapshot", Integer.class));
      var edit = service.configuration(actor, app(c), ch(c));
      List<Callable<Map<String, Object>>> edits = new ArrayList<>();
      for (int i = 0; i < 20; i++)
        edits.add(() -> service.save(actor, app(c), ch(c), "parallel-edit", edit));
      var saved = pool.invokeAll(edits);
      var edited = saved.get(0).get();
      for (var result : saved) assertEquals(edited, result.get());
      assertEquals("2", edited.get("configVersion"));
      assertEquals(
          2, jdbc.queryForObject("SELECT COUNT(*) FROM attr_config_version", Integer.class));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void otherChannelSharedOfferAndHistoricalPredicatesSurvive() throws Exception {
    var c = create();
    var original = repo.active("1", app(c), false);
    var first = original.bindings().get(0);
    var country = new Condition("COUNTRY", "IS", List.of("US"));
    var owned =
        new Binding(
            first.clientKey(),
            first.bindingId(),
            first.channelId(),
            first.bindingScope(),
            first.weight(),
            first.matchMode(),
            List.of(country),
            first.offer(),
            first.sortOrder());
    var otherChannel = new Channel("other-channel", "other-code", "Other channel", "OWNED");
    var shared =
        new Binding(
            "other-client",
            "other-binding",
            otherChannel.channelId(),
            "COMPLEX",
            5,
            "AND",
            List.of(),
            first.offer(),
            0);
    var version =
        new Config(
            original.applicationId(),
            original.applicationCode(),
            original.applicationName(),
            original.currentChannelId(),
            original.fallbackUrl(),
            "2",
            List.of(original.channels().get(0), otherChannel),
            List.of(owned, shared));
    repo.tx(
        () -> {
          repo.putVersion("1", version);
          jdbc.update(
              "UPDATE attr_application SET active_version='2' WHERE application_id=?", app(c));
          return true;
        });
    var edit =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            service.configuration(actor, app(c), ch(c));
    ((com.fasterxml.jackson.databind.node.ObjectNode) edit.path("offers").get(0))
        .put("targetUrl", "https://example.com/changed");
    service.save(actor, app(c), ch(c), "preserve-other", edit);
    var after = repo.active("1", app(c), false);
    assertEquals(
        shared,
        after.bindings().stream()
            .filter(b -> b.bindingId().equals("other-binding"))
            .findFirst()
            .orElseThrow());
    assertEquals(first.offer().targetUrl(), shared.offer().targetUrl());
    assertNotEquals(
        first.offer().offerId(),
        after.bindings().stream()
            .filter(b -> b.bindingId().equals(first.bindingId()))
            .findFirst()
            .orElseThrow()
            .offer()
            .offerId());
    assertThrows(
        AttributionFailure.class, () -> service.configuration(actor, app(c), "other-channel"));
    var changed =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            service.configuration(actor, app(c), ch(c));
    ((com.fasterxml.jackson.databind.node.ObjectNode)
            changed.path("offers").get(0).path("conditions").get(0))
        .put("operator", "IS_NOT");
    assertEquals(
        400,
        assertThrows(
                AttributionFailure.class,
                () -> service.save(actor, app(c), ch(c), "new-country", changed))
            .status);
  }

  @Test
  void fallbackClickIsHistoricalAndRealStateNeverBecomesSimulation() throws Exception {
    var empty = (com.fasterxml.jackson.databind.node.ObjectNode) createBody();
    empty.putArray("offers");
    var c = service.save(actor, null, null, "empty", empty);
    props.setEnabledApplications(Set.of((String) c.get("applicationCode")));
    String bearer = credential(c, "s", "id");
    var click =
        node(
            "{\"subjectId\":\"s\",\"clickRef\":\"empty-click\",\"entryKey\":\"empty-entry\",\"afLinkParams\":{},\"fbLinkParams\":{}}");
    var delivered = service.click(actor, app(c), ch(c), click, "");
    var req =
        node(
            "{\"applicationCode\":\""
                + c.get("applicationCode")
                + "\",\"resolveToken\":\""
                + delivered.get("resolveToken")
                + "\"}");
    var form =
        (com.fasterxml.jackson.databind.node.ObjectNode)
            service.configuration(actor, app(c), ch(c));
    form.put("fallbackUrl", "https://example.com/new-default");
    service.save(actor, app(c), ch(c), "default-edit", form);
    assertEquals(
        "https://example.com/fallback",
        service.resolve("Bearer " + bearer, req, "").destinationUrl());
    service.evidence(
        actor,
        app(c),
        "empty-click",
        node(
            "{\"expectedSnapshotVersion\":\"1\",\"evidenceId\":\"unmatched\",\"state\":\"UNMATCHED\",\"reason\":\"owned"
                + " event\"}"));
    var unmatched = service.resolve("Bearer " + bearer, req, "");
    assertEquals("REAL", unmatched.parameterSource());
    assertEquals("UNMATCHED", unmatched.resolutionState());
    assertTrue(unmatched.afLinkParams().isEmpty());
    jdbc.update("UPDATE attr_resolve_token SET expires_at=0");
    assertEquals(
        401,
        assertThrows(AttributionFailure.class, () -> service.resolve("Bearer " + bearer, req, ""))
            .status);
  }

  @Test
  void maximumClickReferenceAndCurrentPlatformAreValidated() throws Exception {
    var c = create();
    var original = repo.active("1", app(c), false);
    var b = original.bindings().get(0);
    var restricted =
        new Offer(b.offer().offerId(), b.offer().targetUrl(), Set.of("ANDROID"), null, null);
    var binding =
        new Binding(
            b.clientKey(),
            b.bindingId(),
            b.channelId(),
            b.bindingScope(),
            b.weight(),
            b.matchMode(),
            b.conditions(),
            restricted,
            b.sortOrder());
    var config =
        new Config(
            original.applicationId(),
            original.applicationCode(),
            original.applicationName(),
            original.currentChannelId(),
            original.fallbackUrl(),
            "2",
            original.channels(),
            List.of(binding));
    repo.tx(
        () -> {
          repo.putVersion("1", config);
          jdbc.update("UPDATE attr_application SET active_version='2'");
          return true;
        });
    String bearer = credential(c, "platform-subject", "platform-install");
    String ref = "x".repeat(100);
    var body = json.createObjectNode();
    body.put("subjectId", "platform-subject");
    body.put("clickRef", ref);
    body.put("entryKey", "platform-entry");
    body.putObject("afLinkParams");
    body.putObject("fbLinkParams");
    var delivered = service.click(actor, app(c), ch(c), body, "Mozilla/5.0 (Linux; Android 14)");
    var request = json.createObjectNode();
    request.put("applicationCode", (String) c.get("applicationCode"));
    request.put("resolveToken", (String) delivered.get("resolveToken"));
    assertEquals(
        "https://example.com/offer",
        service
            .resolve("Bearer " + bearer, request, "Mozilla/5.0 (Linux; Android 14)")
            .destinationUrl());
    assertEquals(
        "https://example.com/fallback",
        service
            .resolve("Bearer " + bearer, request, "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)")
            .destinationUrl());
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM attr_click_snapshot", Integer.class));
  }
}
