package org.jeecg.modules.attribution;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.function.Supplier;
import org.jeecg.modules.attribution.AttributionDomain.*;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * All writes share the host datasource; every transaction is explicitly scoped. No process-local
 * cache.
 */
public class AttributionRepository {
  final JdbcTemplate jdbc;
  final TransactionTemplate transaction;
  final ObjectMapper json = new ObjectMapper();

  public AttributionRepository(JdbcTemplate jdbc, PlatformTransactionManager manager) {
    this.jdbc = jdbc;
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    transaction.setTimeout(15);
  }

  public <T> T tx(Supplier<T> work) {
    return transaction.execute(status -> work.get());
  }

  public String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new AttributionFailure(503, "Stored configuration unavailable");
    }
  }

  public <T> T decode(String value, Class<T> type) {
    try {
      return json.readValue(value, type);
    } catch (Exception e) {
      throw new AttributionFailure(503, "Stored configuration unavailable");
    }
  }

  public JsonNode tree(String value) {
    return decode(value, JsonNode.class);
  }

  public Map<String, Object> one(String sql, Object... args) {
    var rows = jdbc.queryForList(sql, args);
    return rows.isEmpty() ? null : rows.get(0);
  }

  public boolean member(Actor a) {
    if (a == null
        || a.scopeId() == null
        || !a.scopeId().matches("[1-9][0-9]{0,8}")
        || a.userId() == null
        || a.userId().isBlank()) return false;
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_user_tenant m JOIN sys_tenant t ON t.id=m.tenant_id JOIN"
                + " sys_user u ON u.id=m.user_id WHERE m.tenant_id=? AND m.user_id=? AND"
                + " m.status='1' AND t.status=1 AND t.del_flag=0 AND u.status=1 AND u.del_flag=0"
                + " AND (t.begin_date IS NULL OR t.begin_date<=CURRENT_TIMESTAMP) AND (t.end_date"
                + " IS NULL OR t.end_date>CURRENT_TIMESTAMP)",
            Long.class,
            Integer.parseInt(a.scopeId()),
            a.userId())
        > 0;
  }

  public void requireMember(Actor a) {
    if (!member(a)) throw new AttributionFailure(403, "Active tenant membership required");
  }

  public Map<String, Object> authorize(Actor a, String app, boolean write) {
    requireMember(a);
    var row =
        one(
            "SELECT * FROM attr_application WHERE scope_id=? AND application_id=?",
            a.scopeId(),
            app);
    if (row == null) throw new AttributionFailure(403, "Application access denied");
    if (!a.userId().equals(row.get("owner_user_id"))) {
      var grant =
          one(
              "SELECT can_write FROM attr_app_grant WHERE scope_id=? AND application_id=? AND"
                  + " user_id=?",
              a.scopeId(),
              app,
              a.userId());
      if (grant == null || write && ((Number) grant.get("can_write")).intValue() != 1)
        throw new AttributionFailure(403, "Application access denied");
    }
    return row;
  }

  public Config version(String scope, String app, String version) {
    var row =
        one(
            "SELECT * FROM attr_config_version WHERE scope_id=? AND application_id=? AND"
                + " config_version=?",
            scope,
            app,
            version);
    if (row == null) throw new AttributionFailure(503, "Configuration version unavailable");
    if (!"1".equals(row.get("schema_version")))
      throw new AttributionFailure(409, "Unsupported historical configuration schema");
    String payload = (String) row.get("payload");
    if (!AttributionRules.digest(payload).equals(row.get("checksum")))
      throw new AttributionFailure(503, "Configuration checksum invalid");
    Config c = decode(payload, Config.class);
    if (!app.equals(c.applicationId()) || !version.equals(c.configVersion()))
      throw new AttributionFailure(503, "Configuration references invalid");
    return c;
  }

  public Config active(String scope, String app, boolean lock) {
    var row =
        one(
            "SELECT active_version FROM attr_application WHERE scope_id=? AND application_id=?"
                + (lock ? " FOR UPDATE" : ""),
            scope,
            app);
    if (row == null) throw new AttributionFailure(404, "Application not found");
    return version(scope, app, (String) row.get("active_version"));
  }

  public void putVersion(String scope, Config config) {
    String payload = encode(config);
    jdbc.update(
        "INSERT INTO"
            + " attr_config_version(scope_id,application_id,config_version,schema_version,payload,checksum,created_at)"
            + " VALUES(?,?,?,'1',?,?,?)",
        scope,
        config.applicationId(),
        config.configVersion(),
        payload,
        AttributionRules.digest(payload),
        System.currentTimeMillis());
  }

  public void audit(Actor actor, String app, String action, Object detail) {
    jdbc.update(
        "INSERT INTO"
            + " attr_audit(audit_id,scope_id,application_id,actor_id,action,detail_json,created_at)"
            + " VALUES(?,?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        actor.scopeId(),
        app,
        actor.userId(),
        action,
        encode(detail),
        System.currentTimeMillis());
  }

  public boolean blocked(String scope, String app, String kind, String value) {
    var row =
        one(
            "SELECT blocked FROM attr_safety WHERE scope_id=? AND application_id=? AND kind=? AND"
                + " value_digest=?",
            scope,
            app,
            kind,
            AttributionRules.digest(value));
    return row != null && ((Number) row.get("blocked")).intValue() == 1;
  }

  public JsonNode metadata(String scope, String app, String kind, String value) {
    var row =
        one(
            "SELECT metadata_json FROM attr_safety WHERE scope_id=? AND application_id=? AND kind=?"
                + " AND value_digest=?",
            scope,
            app,
            kind,
            AttributionRules.digest(value));
    return row == null || row.get("metadata_json") == null
        ? json.createObjectNode()
        : tree((String) row.get("metadata_json"));
  }

  public void rate(String bucket, int limit) {
    if (limit <= 0) throw new AttributionFailure(503, "Rate limit configuration unavailable");
    long window = System.currentTimeMillis() / 60000;
    jdbc.update("DELETE FROM attr_rate_limit WHERE window_start<?", window - 2);
    String digest = AttributionRules.digest(bucket);
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        boolean allowed =
            tx(
                () -> {
                  int changed =
                      jdbc.update(
                          "UPDATE attr_rate_limit SET request_count=request_count+1 WHERE"
                              + " bucket_digest=? AND window_start=? AND request_count<?",
                          digest,
                          window,
                          limit);
                  if (changed == 1) return true;
                  var row =
                      one(
                          "SELECT request_count FROM attr_rate_limit WHERE bucket_digest=? AND"
                              + " window_start=?",
                          digest,
                          window);
                  if (row != null)
                    return jdbc.update(
                            "UPDATE attr_rate_limit SET request_count=request_count+1 WHERE"
                                + " bucket_digest=? AND window_start=? AND request_count<?",
                            digest,
                            window,
                            limit)
                        == 1;
                  jdbc.update(
                      "INSERT INTO attr_rate_limit(bucket_digest,window_start,request_count)"
                          + " VALUES(?,?,1)",
                      digest,
                      window);
                  return true;
                });
        if (!allowed) throw new AttributionFailure(429, "Request limit exceeded");
        return;
      } catch (DuplicateKeyException collision) {
        /* failed transaction ended; retry in a fresh transaction */
      }
    }
    throw new AttributionFailure(503, "Rate limiter temporarily unavailable");
  }

  public Click click(String scope, String app, String click) {
    var row =
        one(
            "SELECT payload FROM attr_click_snapshot WHERE scope_id=? AND application_id=? AND"
                + " click_ref=?",
            scope,
            app,
            click);
    return row == null ? null : decode((String) row.get("payload"), Click.class);
  }

  public Map<String, Object> state(String scope, String app, String click) {
    var rows =
        jdbc.queryForList(
            "SELECT * FROM attr_attribution_state WHERE scope_id=? AND application_id=? AND"
                + " click_ref=?",
            scope,
            app,
            click);
    return rows.stream()
        .max(Comparator.comparingLong(r -> Long.parseLong((String) r.get("snapshot_version"))))
        .orElseThrow(() -> new AttributionFailure(503, "Attribution state unavailable"));
  }
}
