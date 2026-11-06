package org.jeecg.modules.siteplatform;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.util.*;

/** Site lock is the serialization point for draft, publication, resources and node ACK. */
@Repository
public class RoutingRepository {
    private final JdbcTemplate jdbc;
    public RoutingRepository(DataSource source) { jdbc = new JdbcTemplate(source); }
    public record Draft(long revision, String status, String inputJson, String payload, String hash, String resourceState,
                        String submittedBy, String approvedBy, String sourceReleaseId) {}
    private Map<String,Object> one(String sql, Object... args) {
        var rows = jdbc.queryForList(sql, args); return rows.isEmpty() ? null : rows.get(0);
    }
    public boolean activeMemberCurrent(int tenant, String user) {
        return one("""
            SELECT m.user_id FROM sys_user_tenant m
            JOIN sys_tenant t ON t.id=m.tenant_id JOIN sys_user u ON u.id=m.user_id
            WHERE m.tenant_id=? AND m.user_id=? AND m.status='1'
            AND t.status=1 AND t.del_flag=0 AND u.status=1 AND u.del_flag=0
            AND (t.begin_date IS NULL OR t.begin_date<=NOW()) AND (t.end_date IS NULL OR t.end_date>NOW()) FOR UPDATE
            """, tenant, user) != null;
    }
    public Draft draft(int tenant, String site) {
        var rows = jdbc.query("""
            SELECT revision,status,input_json,candidate_payload,candidate_hash,resource_state,submitted_by,approved_by,source_release_id
            FROM sp_route_draft WHERE tenant_id=? AND site_id=? FOR UPDATE
            """, (rs, n) -> new Draft(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9)), tenant, site);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public void save(int tenant, String site, Draft draft) {
        int rows = jdbc.update("""
            UPDATE sp_route_draft SET revision=?,status=?,input_json=?,candidate_payload=?,candidate_hash=?,resource_state=?,
            submitted_by=?,approved_by=?,source_release_id=?,updated_at=NOW(3) WHERE tenant_id=? AND site_id=? AND revision=?
            """, draft.revision(), draft.status(), draft.inputJson(), draft.payload(), draft.hash(), draft.resourceState(),
            draft.submittedBy(), draft.approvedBy(), draft.sourceReleaseId(), tenant, site, draft.revision() - 1);
        if (rows == 1) return;
        if (draft.revision() == 1) {
            try {
                jdbc.update("""
                    INSERT INTO sp_route_draft(tenant_id,site_id,revision,status,input_json,candidate_payload,candidate_hash,resource_state,submitted_by,approved_by,source_release_id)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, tenant, site, draft.revision(), draft.status(), draft.inputJson(), draft.payload(), draft.hash(),
                    draft.resourceState(), draft.submittedBy(), draft.approvedBy(), draft.sourceReleaseId());
                return;
            } catch (DuplicateKeyException ignored) { }
        }
        throw new ResourceFailure(409, "草稿版本已变更，请刷新后重试");
    }
    public List<Map<String,Object>> domains(int tenant, String site) {
        return jdbc.queryForList("SELECT id,hostname,verified,verified_at AS verifiedAt FROM sp_domain_binding WHERE tenant_id=? AND site_id=? ORDER BY id FOR UPDATE", tenant, site);
    }
    public List<Map<String,Object>> targets(int tenant, String site) {
        return jdbc.queryForList("SELECT id,url,enabled,updated_at AS updatedAt FROM sp_site_target WHERE tenant_id=? AND site_id=? ORDER BY id FOR UPDATE", tenant, site);
    }
    public void lockPublication() {
        if (one("SELECT id FROM sp_route_publish_lock WHERE id=1 FOR UPDATE") == null)
            throw new ResourceFailure(503, "发布暂不可用，请联系运维");
    }
    public List<String> livePayloadsExcept(int tenant, String site) {
        // Do not join/lock another site: it may hold its site lock while waiting for our publication mutex.
        return jdbc.queryForList("""
            SELECT r.payload FROM sp_route_live l JOIN sp_route_release r
            ON r.id=l.release_id AND r.tenant_id=l.tenant_id AND r.site_id=l.site_id
            WHERE NOT (l.tenant_id=? AND l.site_id=?) ORDER BY l.tenant_id,l.site_id FOR UPDATE
            """, String.class, tenant, site);
    }
    public void publish(int tenant, String site, String id, Draft draft, String actor) {
        jdbc.update("""
            INSERT INTO sp_route_release(id,tenant_id,site_id,hash,payload,input_json,resource_state,created_by,source_release_id)
            VALUES (?,?,?,?,?,?,?,?,?)
            """, id, tenant, site, draft.hash(), draft.payload(), draft.inputJson(), draft.resourceState(), actor, draft.sourceReleaseId());
        jdbc.update("""
            INSERT INTO sp_route_live(tenant_id,site_id,release_id) VALUES (?,?,?)
            ON DUPLICATE KEY UPDATE release_id=VALUES(release_id)
            """, tenant, site, id);
    }
    public Map<String,Object> release(int tenant, String site, String id) {
        return one("""
            SELECT id,hash,payload,input_json AS inputJson,resource_state AS resourceState,source_release_id AS sourceReleaseId
            FROM sp_route_release WHERE tenant_id=? AND site_id=? AND id=? FOR UPDATE
            """, tenant, site, id);
    }
    public List<Map<String,Object>> releases(int tenant, String site) {
        var rows = jdbc.queryForList("""
            SELECT r.id,r.hash,r.created_at AS createdAt,r.created_by AS createdBy,r.source_release_id AS sourceReleaseId,
            CASE WHEN l.release_id=r.id THEN 1 ELSE 0 END AS desired
            FROM sp_route_release r LEFT JOIN sp_route_live l ON l.tenant_id=r.tenant_id AND l.site_id=r.site_id
            WHERE r.tenant_id=? AND r.site_id=? ORDER BY r.created_at DESC,r.id DESC LIMIT 50 FOR UPDATE
            """, tenant, site);
        List<Map<String,Object>> result = new ArrayList<>();
        for (var row : rows) {
            // Do not reuse an old consistent snapshot established before waiting for the site lock.
            var ack = one("SELECT release_id AS releaseId,hash,applied_at AS appliedAt FROM sp_route_ack WHERE tenant_id=? AND site_id=? AND release_id=? ORDER BY applied_at DESC,node_id LIMIT 1 FOR UPDATE",
                    tenant, site, row.get("id"));
            result.add(RoutingRules.object("id", row.get("id"), "hash", row.get("hash"), "createdAt", row.get("createdAt"),
                "createdBy", row.get("createdBy"), "sourceReleaseId", row.get("sourceReleaseId"), "desired", RoutingRules.flag(row.get("desired")),
                "ack", ack));
        }
        return result;
    }
    public boolean lockSite(int tenant, String site) {
        return one("SELECT id FROM sp_site WHERE tenant_id=? AND id=? FOR UPDATE", tenant, site) != null;
    }
    /** Normal consistent read keeps a complete node batch on one database snapshot. */
    public Map<String,Object> desired(int tenant, String site) {
        return desired(tenant, site, false);
    }
    /** ACK runs after the site lock, so must avoid a caller's older REPEATABLE READ snapshot. */
    public Map<String,Object> desiredCurrent(int tenant, String site) {
        return desired(tenant, site, true);
    }
    private Map<String,Object> desired(int tenant, String site, boolean current) {
        return one("""
            SELECT r.id,r.hash,r.payload,r.source_release_id AS sourceReleaseId
            FROM sp_route_live l JOIN sp_route_release r ON r.id=l.release_id AND r.tenant_id=l.tenant_id AND r.site_id=l.site_id
            JOIN sp_site s ON s.tenant_id=l.tenant_id AND s.id=l.site_id JOIN sys_tenant t ON t.id=s.tenant_id
            WHERE l.tenant_id=? AND l.site_id=? AND t.status=1 AND t.del_flag=0
            AND (t.begin_date IS NULL OR t.begin_date<=NOW()) AND (t.end_date IS NULL OR t.end_date>NOW())
            """ + (current ? " FOR UPDATE" : ""), tenant, site);
    }
    public void ack(int tenant, String site, String node, String release, String hash) {
        jdbc.update("""
            INSERT INTO sp_route_ack(tenant_id,site_id,node_id,release_id,hash) VALUES (?,?,?,?,?)
            ON DUPLICATE KEY UPDATE hash=VALUES(hash)
            """, tenant, site, node, release, hash);
    }
}
