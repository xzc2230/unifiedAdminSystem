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
    public static void validateType(String type) {
        if(!TABLES.containsKey(type))throw new PackageFailure(400,"Unknown package resource type");
    }
    public boolean activeMember(int tenant,String user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_tenant m JOIN sys_tenant t ON t.id=m.tenant_id JOIN sys_user u ON u.id=m.user_id WHERE m.tenant_id=? AND m.user_id=? AND m.status='1' AND t.status=1 AND t.del_flag=0 AND u.status=1 AND u.del_flag=0 AND (t.begin_date IS NULL OR t.begin_date<=NOW()) AND (t.end_date IS NULL OR t.end_date>NOW())",Long.class,tenant,user)>0;
    }
    public List<Long> legacyIds(int tenant,String user) {
        return jdbc.queryForList("SELECT legacy_user_id FROM pkg_user_identity WHERE tenant_id=? AND jeecg_user_id=?",Long.class,tenant,user);
    }
    public Set<Long> ownedIds(PackageActor actor,String type) {
        validateType(type);
        String sql="SELECT resource_id FROM pkg_resource_owner WHERE tenant_id=? AND resource_type=?";
        List<Long> ids=actor.administrator()?jdbc.queryForList(sql,Long.class,actor.tenantId(),type):jdbc.queryForList(sql+" AND owner_user_id=?",Long.class,actor.tenantId(),type,actor.userId());
        return new HashSet<>(ids);
    }
    public Set<Long> lockOwnedIds(PackageActor actor,String type) {
        validateType(type);
        String sql="SELECT resource_id FROM pkg_resource_owner WHERE tenant_id=? AND resource_type=?";
        return new HashSet<>(actor.administrator()?jdbc.queryForList(sql+" FOR UPDATE",Long.class,actor.tenantId(),type):jdbc.queryForList(sql+" AND owner_user_id=? FOR UPDATE",Long.class,actor.tenantId(),type,actor.userId()));
    }
    public void claimCreated(PackageActor actor,String type,long id) {
        validateType(type);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM "+type+" WHERE "+TABLES.get(type)+"=?",Long.class,id)!=1)throw new PackageFailure(404,"Created resource not found");
        jdbc.update("INSERT INTO pkg_resource_owner(resource_type,resource_id,tenant_id,owner_user_id) VALUES (?,?,?,?)",type,id,actor.tenantId(),actor.userId());
    }
    public void transfer(PackageActor actor,String type,Collection<Long> ids,String targetUser) {
        validateType(type);
        List<Long> legacy=legacyIds(actor.tenantId(),targetUser);
        if(legacy.size()!=1)throw new PackageFailure(403,"Target legacy identity is ambiguous or missing");
        for(Long id:ids) {
            int n=jdbc.update("UPDATE pkg_resource_owner SET owner_user_id=? WHERE resource_type=? AND resource_id=? AND tenant_id=?",targetUser,type,id,actor.tenantId());
            if(n!=1)throw new PackageFailure(409,"Ownership changed");
            if(Set.of("pkg_app_manager","pkg_app_statistics","pkg_load_count","pkg_pwa","pkg_notification","pkg_app_event").contains(type))jdbc.update("UPDATE "+type+" SET user_id=? WHERE "+TABLES.get(type)+"=?",legacy.get(0),id);
        }
    }
    public List<String> ownedPackages(PackageActor actor) {
        String owner=actor.administrator()?"":" AND o.owner_user_id=?";
        String sql="SELECT a.package_code FROM pkg_app_manager a JOIN pkg_resource_owner o ON o.resource_type='pkg_app_manager' AND o.resource_id=a.id WHERE o.tenant_id=?"+owner;
        return actor.administrator()?jdbc.queryForList(sql,String.class,actor.tenantId()):jdbc.queryForList(sql,String.class,actor.tenantId(),actor.userId());
    }
    public void removeOwners(String type,Collection<Long> ids) {
        validateType(type);
        for(Long id:ids)jdbc.update("DELETE FROM pkg_resource_owner WHERE resource_type=? AND resource_id=?",type,id);
    }
}
