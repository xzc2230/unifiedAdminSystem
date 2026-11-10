package org.jeecg.modules.packagemanagement.application.sharing;

import java.util.*;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageAccess;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageActor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Sharing management always uses ownership locks; shared read/edit scope is insufficient. */
@Service
public class PackageAppSharingService {
    private static final String APPS = "pkg_app_manager";
    private final PackageAccess access;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public PackageAppSharingService(PackageAccess access, JdbcTemplate jdbc, TransactionTemplate tx) {
        this.access = access;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    public int grant(PackageActor actor, Collection<Long> ids, String targetUser, String reason) {
        return change(actor, ids, targetUser, reason, true);
    }

    public int revoke(PackageActor actor, Collection<Long> ids, String targetUser, String reason) {
        return change(actor, ids, targetUser, reason, false);
    }

    private int change(PackageActor actor, Collection<Long> requested, String target, String reason, boolean grant) {
        List<Long> ids = validateIds(requested);
        if (target == null || target.isBlank() || target.length() > 64 || !target.equals(target.trim()))
            throw new PackageFailure(400, "请选择有效目标用户");
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw new PackageFailure(400, "共享原因必填且最多500字符");
        return tx.execute(status -> {
            // Same order as application editing: owner, target identity (grant), then share rows.
            access.lockRows(actor, APPS, ids);
            if (grant) {
                lockTarget(actor.tenantId(), target);
                if (actor.userId().equalsIgnoreCase(target))
                    throw new PackageFailure(400, "不能共享给自己");
                for (Long id : ids) {
                    // Let MySQL compare IDs using the registry's collation, including case variants.
                    if (!jdbc.queryForList("SELECT resource_id FROM pkg_resource_owner WHERE tenant_id=? AND resource_type=? AND resource_id=? AND owner_user_id=? FOR UPDATE",
                            Long.class, actor.tenantId(), APPS, id, target).isEmpty())
                        throw new PackageFailure(409, "不能共享给应用所有者");
                }
            }
            int changed = 0;
            for (Long id : ids) {
                boolean exists = !jdbc.queryForList("SELECT app_id FROM pkg_app_share WHERE tenant_id=? AND app_id=? AND grantee_user_id=? FOR UPDATE",
                        Long.class, actor.tenantId(), id, target).isEmpty();
                if (grant == exists) continue;
                int affected = grant
                        ? jdbc.update("INSERT INTO pkg_app_share(tenant_id,app_id,grantee_user_id,granted_by,reason) VALUES (?,?,?,?,?)",
                                actor.tenantId(), id, target, actor.userId(), reason)
                        : jdbc.update("DELETE FROM pkg_app_share WHERE tenant_id=? AND app_id=? AND grantee_user_id=?",
                                actor.tenantId(), id, target);
                if (affected != 1) throw new PackageFailure(409, "共享关系已变化，请重试");
                jdbc.update("INSERT INTO pkg_app_share_audit(tenant_id,app_id,grantee_user_id,operator_user_id,action,reason) VALUES (?,?,?,?,?,?)",
                        actor.tenantId(), id, target, actor.userId(), grant ? "GRANT" : "REVOKE", reason);
                changed++;
            }
            return changed;
        });
    }

    private static List<Long> validateIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new PackageFailure(400, "请选择1至100个正整数应用ID");
        return ids.stream().distinct().sorted().toList();
    }

    private void lockTarget(int tenant, String target) {
        var members = jdbc.queryForList("SELECT u.id FROM sys_user u JOIN sys_user_tenant m ON m.user_id=u.id JOIN sys_tenant t ON t.id=m.tenant_id WHERE m.tenant_id=? AND u.id=? AND m.status='1' AND u.status=1 AND u.del_flag=0 AND t.status=1 AND t.del_flag=0 AND (t.begin_date IS NULL OR t.begin_date<=NOW()) AND (t.end_date IS NULL OR t.end_date>NOW()) FOR UPDATE",
                String.class, tenant, target);
        if (members.size() != 1) throw new PackageFailure(403, "目标有效租户成员关系缺失或不唯一");
        var identities = jdbc.query("SELECT legacy_user_id,status FROM pkg_user_identity WHERE tenant_id=? AND jeecg_user_id=? FOR UPDATE",
                (rs, index) -> new TargetIdentity(rs.getObject("legacy_user_id", Long.class), rs.getString("status")), tenant, target);
        if (identities.size() != 1 || !"ACTIVE".equals(identities.get(0).status())
                || identities.get(0).legacyId() == null || identities.get(0).legacyId() <= 0)
            throw new PackageFailure(403, "目标业务身份缺失、停用或不唯一");
    }

    private record TargetIdentity(Long legacyId, String status) {}

    public Map<String, Object> shares(PackageActor actor, long appId) {
        List<Long> ids = validateIds(List.of(appId));
        return tx.execute(status -> {
            access.lockRows(actor, APPS, ids);
            List<Map<String, Object>> rows = jdbc.query("SELECT grantee_user_id,granted_by,reason,created_at FROM pkg_app_share WHERE tenant_id=? AND app_id=? ORDER BY created_at,grantee_user_id",
                    (rs, index) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("userId", rs.getString("grantee_user_id"));
                        row.put("grantedBy", rs.getString("granted_by"));
                        row.put("reason", rs.getString("reason"));
                        row.put("createdAt", rs.getString("created_at"));
                        return row;
                    }, actor.tenantId(), appId);
            Map<String,String> names = new HashMap<>();
            for (var row : rows) row.put("displayName", displayName((String)row.get("userId"), names));
            return page(rows);
        });
    }

    public Map<String, Object> audit(PackageActor actor, long appId) {
        List<Long> ids = validateIds(List.of(appId));
        return tx.execute(status -> {
            access.lockRows(actor, APPS, ids);
            List<Map<String, Object>> rows = jdbc.query("SELECT id,app_id,grantee_user_id,operator_user_id,action,reason,created_at FROM pkg_app_share_audit WHERE tenant_id=? AND app_id=? ORDER BY id DESC",
                    (rs, index) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getString("id"));
                        row.put("appId", rs.getString("app_id"));
                        row.put("granteeUserId", rs.getString("grantee_user_id"));
                        row.put("operatorUserId", rs.getString("operator_user_id"));
                        row.put("action", rs.getString("action"));
                        row.put("reason", rs.getString("reason"));
                        row.put("createdAt", rs.getString("created_at"));
                        return row;
                    }, actor.tenantId(), appId);
            Map<String,String> names = new HashMap<>();
            for (var row : rows) {
                row.put("displayName", displayName((String)row.get("granteeUserId"), names));
                row.put("operatorName", displayName((String)row.get("operatorUserId"), names));
            }
            return page(rows);
        });
    }

    private String displayName(String user, Map<String,String> cache) {
        return cache.computeIfAbsent(user, key -> {
            var names = jdbc.queryForList("SELECT COALESCE(NULLIF(realname,''),username) FROM sys_user WHERE id=?", String.class, key);
            return names.isEmpty() || names.get(0) == null ? key : names.get(0);
        });
    }

    private static Map<String, Object> page(List<Map<String, Object>> rows) {
        return Map.of("records", rows, "total", (long)rows.size());
    }
}
