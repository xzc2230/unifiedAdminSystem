package org.jeecg.modules.packagemanagement.application.identity;

import java.sql.Statement;
import java.util.*;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.jeecg.modules.packagemanagement.application.identity.PackageBusinessIdentityGuard.Operator;

/** Writes lock host user, tenant/member, identity, then reservation; mapping and audit commit together. */
@Service
public class PackageBusinessIdentityService {
    public record Page(List<Map<String,Object>> records,long total) {}
    public record ResourceCount(String resourceType,long count) {}
    public record OwnershipConflict(String resourceType,String resourceId,int tenantId,String ownerUserId) {}
    public record Preview(String legacyUserId,boolean available,String conflict,
            List<ResourceCount> resourceCounts,List<OwnershipConflict> ownershipConflicts) {}
    private record Resource(String table,String key) {}
    private record Identity(long id,String state,String source) {}
    private static final List<Resource> RESOURCES=List.of(new Resource("pkg_app_manager","id"),
            new Resource("pkg_app_statistics","id"),new Resource("pkg_load_count","id"),
            new Resource("pkg_pwa","id"),new Resource("pkg_notification","id"),new Resource("pkg_app_event","event_id"));
    private static final Set<String> STATES=Set.of("UNOPENED","ACTIVE","DISABLED","ANOMALOUS");
    private static final Set<String> SOURCES=Set.of("MIGRATED","NEW","LEGACY");
    private static final String ACTIVE_TENANT="t.status=1 AND t.del_flag=0 AND (t.begin_date IS NULL OR t.begin_date<=NOW()) AND (t.end_date IS NULL OR t.end_date>NOW())";
    private static String textEqual(String left,String right) {
        return "CONVERT("+left+" USING utf8mb4) COLLATE utf8mb4_general_ci=CONVERT("+right+" USING utf8mb4) COLLATE utf8mb4_general_ci";
    }
    private static final String MEMBER_FROM=" FROM (SELECT tenant_id,user_id,MAX(status) AS status,COUNT(*) AS member_count FROM sys_user_tenant GROUP BY tenant_id,user_id) m"
            +" LEFT JOIN sys_user u ON u.id=m.user_id LEFT JOIN sys_tenant t ON t.id=m.tenant_id"
            +" LEFT JOIN (SELECT tenant_id,jeecg_user_id,COUNT(*) AS identity_count,MIN(legacy_user_id) AS legacy_user_id,MIN(status) AS status,MIN(source) AS source"
            +" FROM pkg_user_identity GROUP BY tenant_id,jeecg_user_id) i ON i.tenant_id=m.tenant_id AND "+textEqual("i.jeecg_user_id","m.user_id");
    private static final String STATE_SQL="CASE WHEN i.identity_count IS NULL THEN 'UNOPENED'"
            +" WHEN i.identity_count<>1 OR i.legacy_user_id<=0 OR i.status IS NULL OR BINARY i.status NOT IN ('ACTIVE','DISABLED')"
            +" OR i.source IS NULL OR BINARY i.source NOT IN ('MIGRATED','NEW','LEGACY') THEN 'ANOMALOUS'"
            +" WHEN i.status='DISABLED' THEN 'DISABLED'"
            +" WHEN u.id IS NULL OR COALESCE(u.status,0)<>1 OR COALESCE(u.del_flag,1)<>0 OR COALESCE(m.status,'')<>'1' OR m.member_count<>1"
            +" OR t.id IS NULL OR NOT COALESCE(("+ACTIVE_TENANT+"),0) THEN 'ANOMALOUS' ELSE 'ACTIVE' END";
    private static final String MEMBER_SELECT="SELECT m.user_id AS userId,u.username,u.realname,m.tenant_id AS tenantId,t.name AS tenantName,"
            +"CASE WHEN u.status=1 AND u.del_flag=0 THEN 1 ELSE 0 END AS userStatus,"
            +"CASE WHEN m.status='1' AND m.member_count=1 THEN '1' ELSE '0' END AS memberStatus,"
            +"CASE WHEN "+ACTIVE_TENANT+" THEN 1 ELSE 0 END AS tenantActive,"
            +"CAST(i.legacy_user_id AS CHAR) AS legacyUserId,"+STATE_SQL+" AS identityState,i.source";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public PackageBusinessIdentityService(JdbcTemplate jdbc,TransactionTemplate tx) { this.jdbc=jdbc; this.tx=tx; }

    public List<Map<String,Object>> tenants() {
        return jdbc.queryForList("SELECT t.id AS tenantId,t.name AS tenantName,t.status FROM sys_tenant t WHERE t.del_flag=0 ORDER BY t.name,t.id");
    }
    public Page list(Integer tenantId,String keyword,String state,int pageNo,int pageSize) {
        tenant(tenantId); page(pageNo,pageSize);
        if(keyword!=null && keyword.length()>100)throw bad("搜索词最多100字符");
        if(state!=null && !state.isEmpty() && !STATES.contains(state))throw bad("身份状态无效");
        List<Object> args=new ArrayList<>(); args.add(tenantId);
        String where=" WHERE m.tenant_id=?";
        if(keyword!=null && !keyword.isBlank()) {
            String term="%"+keyword.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            where+=" AND (u.username LIKE ? ESCAPE '!' OR u.realname LIKE ? ESCAPE '!')"; args.add(term); args.add(term);
        }
        if(state!=null && !state.isEmpty()) { where+=" AND "+STATE_SQL+"=?"; args.add(state); }
        Long total=jdbc.queryForObject("SELECT COUNT(*)"+MEMBER_FROM+where,Long.class,args.toArray());
        args.add(pageSize); args.add((long)(pageNo-1)*pageSize);
        var rows=jdbc.queryForList(MEMBER_SELECT+MEMBER_FROM+where+" ORDER BY u.username,m.user_id LIMIT ? OFFSET ?",args.toArray());
        return new Page(rows.stream().map(PackageBusinessIdentityService::normalizeRow).toList(),total==null?0:total);
    }
    public Map<String,Object> open(Operator operator,Integer tenantId,String userId,String reason) {
        target(tenantId,userId); reason(reason);
        return transaction(()->{
            lockTarget(tenantId,userId);
            Identity existing=identity(tenantId,userId,true);
            if(existing!=null)return row(tenantId,userId); // Includes disabled: open never restores.
            long id=allocate();
            insert(tenantId,userId,id,"NEW");
            audit(operator,tenantId,userId,id,"OPEN","UNOPENED","ACTIVE",reason);
            return row(tenantId,userId);
        });
    }
    public Preview preview(Integer tenantId,String userId,Object legacyUserId) {
        long id=legacy(legacyUserId); target(tenantId,userId);
        return transaction(()->{lockTarget(tenantId,userId);return inspect(tenantId,userId,id);});
    }
    public Map<String,Object> bind(Operator operator,Integer tenantId,String userId,Object legacyUserId,String reason) {
        long id=legacy(legacyUserId); target(tenantId,userId); reason(reason);
        return transaction(()->{
            lockTarget(tenantId,userId);
            Identity existing=identity(tenantId,userId,true);
            if(existing!=null) {
                if(existing.id()!=id)throw conflict("已有业务身份，不允许更换编号");
                return row(tenantId,userId);
            }
            // Serializes different target accounts trying to bind the same historical number.
            jdbc.update("INSERT INTO pkg_business_id_reservation(legacy_user_id,source) VALUES (?,'LEGACY') ON DUPLICATE KEY UPDATE legacy_user_id=legacy_user_id",id);
            jdbc.queryForList("SELECT legacy_user_id FROM pkg_business_id_reservation WHERE legacy_user_id=? FOR UPDATE",id);
            Preview preview=inspect(tenantId,userId,id);
            if(!preview.available())throw conflict(preview.conflict());
            insert(tenantId,userId,id,"LEGACY");
            audit(operator,tenantId,userId,id,"BIND","UNOPENED","ACTIVE",reason);
            return row(tenantId,userId);
        });
    }
    public Map<String,Object> status(Operator operator,Integer tenantId,String userId,Object enabled,String reason) {
        target(tenantId,userId); reason(reason);
        if(!(enabled instanceof Boolean))throw bad("必须明确提供布尔类型enabled");
        String next=Boolean.TRUE.equals(enabled)?"ACTIVE":"DISABLED";
        return transaction(()->{
            lockTarget(tenantId,userId); Identity existing=identity(tenantId,userId,true);
            if(existing==null)throw conflict("业务账号尚未开通");
            if(!existing.state().equals(next)) {
                jdbc.update("UPDATE pkg_user_identity SET status=?,updated_at=NOW() WHERE legacy_user_id=?",next,existing.id());
                audit(operator,tenantId,userId,existing.id(),"STATUS",existing.state(),next,reason);
            }
            return row(tenantId,userId);
        });
    }
    public Page audit(Integer tenantId,String userId,int pageNo,int pageSize) {
        tenant(tenantId); page(pageNo,pageSize);
        if(userId!=null)user(userId);
        List<Object> args=new ArrayList<>();args.add(tenantId);
        String where=" WHERE tenant_id=?";
        if(userId!=null) { where+=" AND target_user_id=?";args.add(userId); }
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM pkg_business_identity_audit"+where,Long.class,args.toArray());
        args.add(pageSize);args.add((long)(pageNo-1)*pageSize);
        var records=jdbc.queryForList("SELECT CAST(id AS CHAR) AS id,operator_id AS operatorId,operator_name AS operatorName,action,"
                +"CAST(legacy_user_id AS CHAR) AS legacyUserId,before_state AS beforeState,after_state AS afterState,reason,DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s') AS createdAt"
                +" FROM pkg_business_identity_audit"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",args.toArray());
        return new Page(records,total==null?0:total);
    }
    private <T> T transaction(java.util.function.Supplier<T> work) {
        try { return tx.execute(status->work.get()); }
        catch(DuplicateKeyException duplicate) { throw conflict("编号或账号已被占用，请刷新后重试"); }
        catch(PessimisticLockingFailureException contention) { throw conflict("业务身份正在由其他请求配置，请刷新后重试"); }
    }
    private void lockTarget(int tenantId,String userId) {
        // Always lock the target host user before touching allocation/identity state.
        var users=jdbc.queryForList("SELECT id,status,del_flag FROM sys_user WHERE id=? FOR UPDATE",userId);
        if(users.size()!=1 || !one(users.get(0).get("status")) || !zero(users.get(0).get("del_flag")))throw conflict("目标用户无效");
        var tenants=jdbc.queryForList("SELECT t.id FROM sys_tenant t WHERE t.id=? AND "+ACTIVE_TENANT+" FOR UPDATE",tenantId);
        if(tenants.size()!=1)throw conflict("目标租户无效或已过期");
        var members=jdbc.queryForList("SELECT status FROM sys_user_tenant WHERE tenant_id=? AND user_id=? FOR UPDATE",tenantId,userId);
        if(members.size()!=1 || !"1".equals(String.valueOf(members.get(0).get("status"))))throw conflict("目标租户成员关系无效");
    }
    private Identity identity(int tenantId,String userId,boolean lock) {
        var rows=jdbc.queryForList("SELECT legacy_user_id,status,source FROM pkg_user_identity WHERE tenant_id=? AND jeecg_user_id=?"+(lock?" FOR UPDATE":""),tenantId,userId);
        if(rows.isEmpty())return null;
        if(rows.size()!=1)throw conflict("业务身份映射不唯一");
        var row=rows.get(0); long id=((Number)row.get("legacy_user_id")).longValue();
        String state=(String)row.get("status"),source=(String)row.get("source");
        if(id<=0 || !("ACTIVE".equals(state)||"DISABLED".equals(state)) || !SOURCES.contains(source==null?"":source))throw conflict("业务身份映射异常");
        return new Identity(id,state,source);
    }
    private long allocate() {
        for(int attempt=0;attempt<1000;attempt++) {
            var key=new GeneratedKeyHolder();
            // Historical explicit IDs must never advance the new-account sequence, even after rollback.
            jdbc.update(connection->connection.prepareStatement("INSERT INTO pkg_business_id_allocator() VALUES ()",Statement.RETURN_GENERATED_KEYS),key);
            Number generated=key.getKey();
            if(generated==null || generated.longValue()<1000000000000L)throw conflict("业务编号分配失败");
            long id=generated.longValue();
            try {
                jdbc.update("INSERT INTO pkg_business_id_reservation(legacy_user_id,source) VALUES (?,'NEW')",id);
            } catch(DuplicateKeyException occupiedReservation) {
                continue; // Keep the existing reservation's source and timestamp unchanged.
            }
            boolean occupied=jdbc.queryForObject("SELECT COUNT(*) FROM pkg_user_identity WHERE legacy_user_id=?",Long.class,id)>0;
            for(Resource resource:RESOURCES) {
                if(jdbc.queryForObject("SELECT COUNT(*) FROM "+resource.table()+" WHERE user_id=?",Long.class,id)>0)occupied=true;
            }
            if(!occupied)return id;
            jdbc.update("UPDATE pkg_business_id_reservation SET source='HISTORICAL' WHERE legacy_user_id=?",id);
        }
        throw conflict("业务编号连续占用，请检查迁移预留数据");
    }
    private Preview inspect(int tenantId,String userId,long id) {
        String conflict=null;
        Identity selected;
        try { selected=identity(tenantId,userId,false); }
        catch(PackageFailure invalid) { selected=null; conflict=invalid.getMessage(); }
        if(selected!=null && selected.id()!=id)conflict="目标账号已经绑定其他编号";
        var mappings=jdbc.queryForList("SELECT tenant_id,jeecg_user_id,status,source FROM pkg_user_identity WHERE legacy_user_id=?",id);
        boolean same=mappings.size()==1 && ((Number)mappings.get(0).get("tenant_id")).intValue()==tenantId
                && userId.equals(mappings.get(0).get("jeecg_user_id")) && selected!=null && selected.id()==id;
        if(!mappings.isEmpty() && !same)conflict="历史编号已被其他身份占用或映射异常";
        var reservations=jdbc.queryForList("SELECT source FROM pkg_business_id_reservation WHERE legacy_user_id=?",id);
        if(!reservations.isEmpty()) {
            Object source=reservations.get(0).get("source");
            if(("NEW".equals(source) && !same) || !("NEW".equals(source)||"LEGACY".equals(source)||"HISTORICAL".equals(source)))
                conflict="编号已被预留，不可绑定";
        }
        List<ResourceCount> counts=new ArrayList<>();List<OwnershipConflict> owners=new ArrayList<>();
        for(Resource resource:RESOURCES) {
            long count=jdbc.queryForObject("SELECT COUNT(*) FROM "+resource.table()+" WHERE user_id=?",Long.class,id);
            counts.add(new ResourceCount(resource.table(),count));
            owners.addAll(jdbc.query("SELECT o.resource_id,o.tenant_id,o.owner_user_id FROM "+resource.table()+" b"
                    +" JOIN pkg_resource_owner o ON o.resource_type=? AND o.resource_id=b."+resource.key()
                    +" WHERE b.user_id=? AND (o.tenant_id<>? OR NOT ("+textEqual("o.owner_user_id","?")+"))",
                    (rs,n)->new OwnershipConflict(resource.table(),rs.getString("resource_id"),rs.getInt("tenant_id"),rs.getString("owner_user_id")),resource.table(),id,tenantId,userId));
        }
        if(!owners.isEmpty())conflict="历史资源存在其他租户或用户的已登记归属";
        return new Preview(Long.toString(id),conflict==null,conflict,List.copyOf(counts),List.copyOf(owners));
    }
    private void insert(int tenantId,String userId,long id,String source) {
        jdbc.update("INSERT INTO pkg_user_identity(legacy_user_id,tenant_id,jeecg_user_id,status,source,created_at,updated_at) VALUES (?,?,?,'ACTIVE',?,NOW(),NOW())",id,tenantId,userId,source);
    }
    private void audit(Operator operator,int tenantId,String userId,long id,String action,String before,String after,String reason) {
        if(operator==null || operator.userId()==null)throw new PackageFailure(401,"请先登录");
        jdbc.update("INSERT INTO pkg_business_identity_audit(operator_id,operator_name,target_user_id,tenant_id,legacy_user_id,action,before_state,after_state,reason) VALUES (?,?,?,?,?,?,?,?,?)",
                operator.userId(),operator.username(),userId,tenantId,id,action,before,after,reason);
    }
    private Map<String,Object> row(int tenantId,String userId) {
        var rows=jdbc.queryForList(MEMBER_SELECT+MEMBER_FROM+" WHERE m.tenant_id=? AND m.user_id=?",tenantId,userId);
        if(rows.size()!=1)throw conflict("目标成员信息不唯一");
        return normalizeRow(rows.get(0));
    }
    private static Map<String,Object> normalizeRow(Map<String,Object> row) {
        Map<String,Object> normalized=new LinkedHashMap<>(row);
        normalized.put("tenantActive",one(row.get("tenantActive")));
        return normalized;
    }
    private static boolean one(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue()==1; }
    private static boolean zero(Object value) { return Boolean.FALSE.equals(value) || value instanceof Number n && n.intValue()==0; }
    private static void tenant(Integer tenantId) { if(tenantId==null || tenantId<=0)throw bad("请选择目标租户"); }
    private static void user(String userId) { if(userId==null || userId.isBlank() || userId.length()>64)throw bad("目标用户无效"); }
    private static void target(Integer tenantId,String userId) { tenant(tenantId);user(userId); }
    private static void reason(String reason) { if(reason==null || reason.isBlank() || reason.length()>500)throw bad("原因必填且最多500字符"); }
    private static void page(int pageNo,int pageSize) { if(pageNo<1 || pageSize<1 || pageSize>100)throw bad("分页参数无效，每页最多100条"); }
    private static long legacy(Object value) {
        if(!(value instanceof String text) || !text.matches("[1-9][0-9]{0,18}"))throw bad("legacyUserId必须为正整数十进制字符串");
        try { return Long.parseLong((String)value); }
        catch(NumberFormatException invalid) { throw bad("legacyUserId超出Long范围"); }
    }
    private static PackageFailure bad(String message) { return new PackageFailure(400,message); }
    private static PackageFailure conflict(String message) { return new PackageFailure(409,message); }
}
