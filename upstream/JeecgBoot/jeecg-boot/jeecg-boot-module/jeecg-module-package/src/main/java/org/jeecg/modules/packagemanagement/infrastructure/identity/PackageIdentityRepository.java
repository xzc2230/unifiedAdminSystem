package org.jeecg.modules.packagemanagement.infrastructure.identity;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
public class PackageIdentityRepository  {
    private final JdbcTemplate jdbc;
    public PackageIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc=jdbc;
    }
    private static final Map<String,String> TABLES=Map.ofEntries(Map.entry("pkg_app_manager","id"),Map.entry("pkg_app_statistics","id"),Map.entry("pkg_store_status","id"),Map.entry("pkg_event_config","id"),Map.entry("pkg_event_history","id"),Map.entry("pkg_event_error","id"),Map.entry("pkg_load_count","id"),Map.entry("pkg_pwa","id"),Map.entry("pkg_pwa_subscription","id"),Map.entry("pkg_notification","id"),Map.entry("pkg_app_event","event_id"),Map.entry("pkg_review","reviews_id"));
    private static final Set<String> USER_SCOPED=Set.of("pkg_app_manager","pkg_app_statistics","pkg_load_count","pkg_pwa","pkg_notification","pkg_app_event");
    public static void validateType(String type) {
        if(!TABLES.containsKey(type))throw new PackageFailure(400,"Unknown package resource type");
    }
    public boolean activeMember(int tenant,String user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_tenant m JOIN sys_tenant t ON t.id=m.tenant_id JOIN sys_user u ON u.id=m.user_id WHERE m.tenant_id=? AND m.user_id=? AND m.status='1' AND t.status=1 AND t.del_flag=0 AND u.status=1 AND u.del_flag=0 AND (t.begin_date IS NULL OR t.begin_date<=NOW()) AND (t.end_date IS NULL OR t.end_date>NOW())",Long.class,tenant,user)>0;
    }
    public boolean activeAdministrator(String user) {
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM sys_user u WHERE u.id=? AND u.status=1 AND u.del_flag=0"
                +" AND EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.role_code='admin')",Long.class,user);
        return count!=null&&count==1;
    }
    public List<Long> legacyIds(int tenant,String user) {
        record Identity(Long id,String status) {}
        var rows=jdbc.query("SELECT legacy_user_id,status FROM pkg_user_identity WHERE tenant_id=? AND jeecg_user_id=?",
                (rs,index)->new Identity(rs.getObject("legacy_user_id",Long.class),rs.getString("status")),tenant,user);
        // Check cardinality before status: a disabled duplicate must still deny resolution.
        if(rows.size()==1) {
            var identity=rows.get(0);
            if(!"ACTIVE".equals(identity.status())||identity.id()==null||identity.id()<=0)return List.of();
        }
        return rows.stream().map(Identity::id).toList();
    }
    public Set<Long> ownedIds(PackageActor actor,String type) {
        validateType(type);
        String sql="SELECT resource_id FROM pkg_resource_owner WHERE tenant_id=? AND resource_type=?";
        List<Long> ids=actor.administrator()?jdbc.queryForList(sql,Long.class,actor.tenantId(),type):jdbc.queryForList(sql+" AND owner_user_id=?",Long.class,actor.tenantId(),type,actor.userId());
        Set<Long> allowed=new HashSet<>(ids);
        if(actor.administrator()) allowed.addAll(tenantBusinessIds(actor.tenantId(),type));
        return allowed;
    }
    /** Administrator list scope includes tenant business rows that were never written to the owner registry. */
    private List<Long> tenantBusinessIds(int tenant,String type) {
        if(!USER_SCOPED.contains(type)) return List.of();
        return jdbc.queryForList("SELECT b."+TABLES.get(type)+" FROM "+type+" b JOIN pkg_user_identity i ON i.legacy_user_id=b.user_id AND i.tenant_id=? AND i.status='ACTIVE'",Long.class,tenant);
    }
    public Set<Long> lockOwnedIds(PackageActor actor,String type) {
        validateType(type);
        String sql="SELECT resource_id FROM pkg_resource_owner WHERE tenant_id=? AND resource_type=?";
        return new HashSet<>(actor.administrator()?jdbc.queryForList(sql+" FOR UPDATE",Long.class,actor.tenantId(),type):jdbc.queryForList(sql+" AND owner_user_id=? FOR UPDATE",Long.class,actor.tenantId(),type,actor.userId()));
    }
    public Set<Long> sharedAppIds(PackageActor actor) {
        return new HashSet<>(jdbc.queryForList("SELECT s.app_id FROM pkg_app_share s JOIN pkg_resource_owner o"
                +" ON o.resource_type='pkg_app_manager' AND o.resource_id=s.app_id AND o.tenant_id=s.tenant_id"
                +" WHERE s.tenant_id=? AND s.grantee_user_id=?",Long.class,actor.tenantId(),actor.userId()));
    }
    public Set<Long> lockEditableAppIds(PackageActor actor,Collection<Long> ids) {
        if(ids==null||ids.isEmpty()||ids.stream().anyMatch(Objects::isNull))return Set.of();
        List<Object> args=new ArrayList<>();args.add(actor.tenantId());args.addAll(ids);
        // Grant/revoke operations lock the same ownership rows before touching shares.
        var rows=jdbc.queryForList("SELECT resource_id,owner_user_id FROM pkg_resource_owner WHERE tenant_id=?"
                +" AND resource_type='pkg_app_manager' AND resource_id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") ORDER BY resource_id FOR UPDATE",args.toArray());
        Set<Long> allowed=new HashSet<>();
        for(var row:rows) {
            long id=((Number)row.get("resource_id")).longValue();
            if(actor.administrator()||actor.userId().equals(row.get("owner_user_id")))allowed.add(id);
            else if(jdbc.queryForList("SELECT app_id FROM pkg_app_share WHERE tenant_id=? AND app_id=? AND grantee_user_id=? FOR UPDATE",Long.class,actor.tenantId(),id,actor.userId()).size()==1)allowed.add(id);
        }
        return allowed;
    }
    public void claimCreated(PackageActor actor,String type,long id) {
        validateType(type);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM "+type+" WHERE "+TABLES.get(type)+"=?",Long.class,id)!=1)throw new PackageFailure(404,"Created resource not found");
        jdbc.update("INSERT INTO pkg_resource_owner(resource_type,resource_id,tenant_id,owner_user_id) VALUES (?,?,?,?)",type,id,actor.tenantId(),actor.userId());
    }
    public void lockTenantParent(PackageActor actor,String type,long id) {
        validateType(type);
        var ids=jdbc.queryForList("SELECT resource_id FROM pkg_resource_owner WHERE resource_type=? AND resource_id=? AND tenant_id=? FOR UPDATE",Long.class,type,id,actor.tenantId());
        if(ids.size()!=1)throw new PackageFailure(403,"关联资源未映射或不属于当前租户");
    }
    public void reassignReviewToParent(PackageActor actor,long reviewId,long parentId) {
        int changed=jdbc.update("UPDATE pkg_resource_owner child JOIN pkg_resource_owner parent ON parent.resource_type='pkg_pwa' AND parent.resource_id=? AND parent.tenant_id=child.tenant_id SET child.owner_user_id=parent.owner_user_id WHERE child.resource_type='pkg_review' AND child.resource_id=? AND child.tenant_id=?",parentId,reviewId,actor.tenantId());
        if(changed!=1)throw new PackageFailure(409,"评论或PWA归属已变化");
    }
    public void claimCreatedFromParent(PackageActor actor,String child,long id,String parent,long parentId) {
        validateType(child);validateType(parent);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM "+child+" WHERE "+TABLES.get(child)+"=?",Long.class,id)!=1)
            throw new PackageFailure(404,"Created resource not found");
        int inserted=jdbc.update("INSERT INTO pkg_resource_owner(resource_type,resource_id,tenant_id,owner_user_id) SELECT ?,?,tenant_id,owner_user_id FROM pkg_resource_owner WHERE resource_type=? AND resource_id=? AND tenant_id=?",child,id,parent,parentId,actor.tenantId());
        if(inserted!=1)throw new PackageFailure(409,"Parent ownership changed");
    }
    public void transfer(PackageActor actor,String type,Collection<Long> ids,String targetUser) {
        validateType(type);
        List<Long> legacy=legacyIds(actor.tenantId(),targetUser);
        if(legacy.size()!=1)throw new PackageFailure(403,"Target legacy identity is ambiguous or missing");
        for(Long id:ids) {
            int n=jdbc.update("UPDATE pkg_resource_owner SET owner_user_id=? WHERE resource_type=? AND resource_id=? AND tenant_id=?",targetUser,type,id,actor.tenantId());
            if(n!=1)throw new PackageFailure(409,"Ownership changed");
            if("pkg_app_manager".equals(type)) {
                String username=jdbc.queryForObject("SELECT username FROM sys_user WHERE id=? AND status=1 AND del_flag=0",String.class,targetUser);
                if(username==null||username.isBlank())throw new PackageFailure(403,"目标用户名不存在");
                jdbc.update("UPDATE pkg_app_manager SET user_id=?, user_name=? WHERE id=?",legacy.get(0),username,id);
            } else if(Set.of("pkg_app_statistics","pkg_load_count","pkg_pwa","pkg_notification","pkg_app_event").contains(type))jdbc.update("UPDATE "+type+" SET user_id=? WHERE "+TABLES.get(type)+"=?",legacy.get(0),id);
        }
    }
    public List<String> ownedPackages(PackageActor actor) {
        String owner=actor.administrator()?"":" AND o.owner_user_id=?";
        String sql="SELECT a.package_code FROM pkg_app_manager a JOIN pkg_resource_owner o ON o.resource_type='pkg_app_manager' AND o.resource_id=a.id WHERE o.tenant_id=?"+owner;
        return actor.administrator()?jdbc.queryForList(sql,String.class,actor.tenantId()):jdbc.queryForList(sql,String.class,actor.tenantId(),actor.userId());
    }
    public void removeOwners(String type,Collection<Long> ids) {
        validateType(type);
        for(Long id:ids) {
            if("pkg_app_manager".equals(type))jdbc.update("DELETE FROM pkg_app_share WHERE app_id=?",id);
            jdbc.update("DELETE FROM pkg_resource_owner WHERE resource_type=? AND resource_id=?",type,id);
        }
    }
}
