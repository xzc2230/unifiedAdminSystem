package org.jeecg.modules.packagemanagement.infrastructure.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Anonymous inputs never determine an owner: only a previously mapped parent can do so. */
@Component
public class PackagePublicOwnership {
    private final JdbcTemplate jdbc;
    public PackagePublicOwnership(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void inherit(String child,long id,String parent,long parentId) {
        PackageIdentityRepository.validateType(child);PackageIdentityRepository.validateType(parent);
        jdbc.update("INSERT IGNORE INTO pkg_resource_owner(resource_type,resource_id,tenant_id,owner_user_id) "
            +"SELECT ?,?,tenant_id,owner_user_id FROM pkg_resource_owner WHERE resource_type=? AND resource_id=?",
            child,id,parent,parentId);
    }
    public void fromPackage(String child,Long id,String packageName) {
        if(id==null||packageName==null)return;
        var matches=jdbc.queryForList("SELECT id FROM pkg_app_manager WHERE package_code=?",Long.class,packageName);
        if(matches.size()==1)inherit(child,id,"pkg_app_manager",matches.get(0));
    }
}
