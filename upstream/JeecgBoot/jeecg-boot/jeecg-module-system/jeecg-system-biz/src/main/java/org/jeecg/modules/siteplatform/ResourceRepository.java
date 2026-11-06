package org.jeecg.modules.siteplatform;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** All identifiers are values, never SQL fragments supplied by clients. */
@Repository
public class ResourceRepository {
    private final JdbcTemplate jdbc;
    public ResourceRepository(DataSource dataSource) { this.jdbc = new JdbcTemplate(dataSource); }
    static String id() { return UUID.randomUUID().toString().replace("-", ""); }

    public boolean activeMember(int tenant, String user) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) FROM sys_user_tenant m
            JOIN sys_tenant t ON t.id=m.tenant_id
            JOIN sys_user u ON u.id=m.user_id
            WHERE m.tenant_id=? AND m.user_id=? AND m.status='1'
            AND t.status=1 AND t.del_flag=0 AND u.status=1 AND u.del_flag=0
            AND (t.begin_date IS NULL OR t.begin_date<=NOW())
            AND (t.end_date IS NULL OR t.end_date>NOW())
            """, Long.class, tenant, user) > 0;
    }

    /** Current read after taking the parent site lock, even if access.current established an older snapshot. */
    public boolean activeMemberCurrent(int tenant, String user) {
        return one("""
            SELECT m.user_id FROM sys_user_tenant m
            JOIN sys_tenant t ON t.id=m.tenant_id JOIN sys_user u ON u.id=m.user_id
            WHERE m.tenant_id=? AND m.user_id=? AND m.status='1'
            AND t.status=1 AND t.del_flag=0 AND u.status=1 AND u.del_flag=0
            AND (t.begin_date IS NULL OR t.begin_date<=NOW())
            AND (t.end_date IS NULL OR t.end_date>NOW()) FOR UPDATE
            """, tenant, user) != null;
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> site(int tenant, String site, boolean lock) {
        return one("SELECT id,name,environment,enabled,created_at AS createdAt FROM sp_site WHERE tenant_id=? AND id=?" + (lock ? " FOR UPDATE" : ""), tenant, site);
    }

    public String memberRole(int tenant, String site, String user) {
        // Current read: a transaction may already have a REPEATABLE READ snapshot
        // from tenant validation before it waited for the parent site lock.
        Map<String, Object> row = one("SELECT role FROM sp_site_member WHERE tenant_id=? AND site_id=? AND user_id=? FOR UPDATE", tenant, site, user);
        return row == null ? null : row.get("role").toString();
    }

    public List<Map<String, Object>> sites(ResourceAccess.Actor actor, int offset, int size) {
        return jdbc.queryForList("""
            SELECT s.id,s.name,s.environment,s.enabled,s.created_at AS createdAt,m.role AS myRole
            FROM sp_site s JOIN sp_site_member m ON m.tenant_id=s.tenant_id AND m.site_id=s.id
            WHERE s.tenant_id=? AND m.user_id=? ORDER BY s.created_at DESC,s.id LIMIT ? OFFSET ?
            """, actor.tenantId(), actor.userId(), size, offset);
    }

    public long countSites(ResourceAccess.Actor actor) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) FROM sp_site s JOIN sp_site_member m ON m.tenant_id=s.tenant_id AND m.site_id=s.id
            WHERE s.tenant_id=? AND m.user_id=?
            """, Long.class, actor.tenantId(), actor.userId());
    }

    public String insertSite(ResourceAccess.Actor actor, ResourceRequests.SiteInput input) {
        String id = id();
        jdbc.update("INSERT INTO sp_site(id,tenant_id,name,environment,enabled,created_by) VALUES (?,?,?,?,?,?)", id, actor.tenantId(), input.name().trim(), input.environment(), input.enabled(), actor.userId());
        upsertMember(actor.tenantId(), id, actor.userId(), "ADMIN");
        return id;
    }

    public void updateSite(int tenant, String site, ResourceRequests.SiteInput input) {
        jdbc.update("UPDATE sp_site SET name=?,enabled=?,updated_at=NOW() WHERE tenant_id=? AND id=?", input.name().trim(), input.enabled(), tenant, site);
    }

    public List<Map<String, Object>> targets(int tenant, String site) {
        return jdbc.queryForList("SELECT id,name,url,enabled,created_at AS createdAt FROM sp_site_target WHERE tenant_id=? AND site_id=? ORDER BY created_at,id", tenant, site);
    }

    public String insertTarget(int tenant, String site, ResourceRequests.TargetInput input, String normalizedUrl) {
        String id = id();
        jdbc.update("INSERT INTO sp_site_target(id,tenant_id,site_id,name,url,enabled) VALUES (?,?,?,?,?,?)", id, tenant, site, input.name().trim(), normalizedUrl, input.enabled());
        return id;
    }

    public int updateTarget(int tenant, String site, String id, ResourceRequests.TargetInput input, String url) {
        return jdbc.update("UPDATE sp_site_target SET name=?,url=?,enabled=?,updated_at=NOW() WHERE tenant_id=? AND site_id=? AND id=?", input.name().trim(), url, input.enabled(), tenant, site, id);
    }

    public int deleteTarget(int tenant, String site, String id) {
        return jdbc.update("DELETE FROM sp_site_target WHERE tenant_id=? AND site_id=? AND id=?", tenant, site, id);
    }

    public boolean liveTargetReferenced(int tenant, String site, String id) {
        return routingReference("""
            SELECT r.id FROM sp_route_live l JOIN sp_route_release r
            ON r.id=l.release_id AND r.tenant_id=l.tenant_id AND r.site_id=l.site_id
            WHERE l.tenant_id=? AND l.site_id=? AND
            (JSON_UNQUOTE(JSON_EXTRACT(r.input_json,'$.defaultTargetId'))=? OR
             JSON_CONTAINS(JSON_EXTRACT(r.input_json,'$.rules[*].targetId'),JSON_QUOTE(?))=1)
            LIMIT 1 FOR UPDATE
            """, tenant, site, id, id);
    }

    /** Offline nodes can retain any old release. Do not transfer a historically published hostname. */
    public boolean publishedDomainReferenced(int tenant, String site, String id) {
        return routingReference("""
            SELECT id FROM sp_route_release WHERE tenant_id=? AND site_id=?
            AND JSON_CONTAINS(input_json,JSON_QUOTE(?),'$.domainIds')=1 LIMIT 1 FOR UPDATE
            """, tenant, site, id);
    }

    private boolean routingReference(String sql, Object... args) {
        try { return one(sql, args) != null; }
        catch (org.springframework.dao.DataAccessException e) {
            // Missing/unavailable routing schema must fail closed, without exposing SQL via global handlers.
            throw new ResourceFailure(503, "路由发布数据暂不可用，请稍后重试");
        }
    }

    public List<Map<String, Object>> domains(int tenant, String site) {
        return jdbc.queryForList("SELECT id,hostname,verified,verified_at AS verifiedAt,challenge_expires AS challengeExpires FROM sp_domain_binding WHERE tenant_id=? AND site_id=? ORDER BY hostname", tenant, site);
    }

    public Map<String, Object> domain(int tenant, String site, String id) {
        return one("SELECT id,hostname,verified,challenge,challenge_expires AS challengeExpires,(challenge_expires>NOW()) AS challengeValid FROM sp_domain_binding WHERE tenant_id=? AND site_id=? AND id=?", tenant, site, id);
    }

    public String insertDomain(int tenant, String site, String hostname, String challenge) {
        String id = id();
        jdbc.update("INSERT INTO sp_domain_binding(id,tenant_id,site_id,hostname,challenge,challenge_expires) VALUES (?,?,?,?,?,?)", id, tenant, site, hostname, challenge, Timestamp.from(Instant.now().plusSeconds(1800)));
        return id;
    }

    public int verifyDomain(int tenant, String site, String id, String challenge) {
        return jdbc.update("UPDATE sp_domain_binding SET verified=1,verified_at=NOW(),challenge=NULL WHERE tenant_id=? AND site_id=? AND id=? AND verified=0 AND challenge=? AND challenge_expires>NOW()", tenant, site, id, challenge);
    }

    public int renewChallenge(int tenant, String site, String id, String challenge) {
        return jdbc.update("UPDATE sp_domain_binding SET challenge=?,challenge_expires=? WHERE tenant_id=? AND site_id=? AND id=? AND verified=0", challenge, Timestamp.from(Instant.now().plusSeconds(1800)), tenant, site, id);
    }

    public int releaseDomain(int tenant, String site, String id) {
        return jdbc.update("DELETE FROM sp_domain_binding WHERE tenant_id=? AND site_id=? AND id=?", tenant, site, id);
    }

    public List<Map<String, Object>> members(int tenant, String site) {
        return jdbc.queryForList("SELECT m.user_id AS userId,u.username,u.realname,m.role FROM sp_site_member m LEFT JOIN sys_user u ON u.id=m.user_id WHERE m.tenant_id=? AND m.site_id=? ORDER BY m.created_at,m.user_id", tenant, site);
    }

    public void upsertMember(int tenant, String site, String user, String role) {
        jdbc.update("INSERT INTO sp_site_member(tenant_id,site_id,user_id,role) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE role=?", tenant, site, user, role, role);
    }

    public int removeMember(int tenant, String site, String user) {
        return jdbc.update("DELETE FROM sp_site_member WHERE tenant_id=? AND site_id=? AND user_id=?", tenant, site, user);
    }

    public void audit(ResourceAccess.Actor actor, String site, String action, String resource, String summary) {
        jdbc.update("INSERT INTO sp_resource_audit(id,tenant_id,site_id,actor_id,action,resource_id,summary) VALUES (?,?,?,?,?,?,?)", id(), actor.tenantId(), site, actor.userId(), action, resource, summary);
    }

    public List<Map<String, Object>> auditEntries(int tenant, String site) {
        return jdbc.queryForList("SELECT id,actor_id AS actorId,action,resource_id AS resourceId,summary,created_at AS createdAt FROM sp_resource_audit WHERE tenant_id=? AND site_id=? ORDER BY created_at DESC,id DESC LIMIT 100", tenant, site);
    }
}
