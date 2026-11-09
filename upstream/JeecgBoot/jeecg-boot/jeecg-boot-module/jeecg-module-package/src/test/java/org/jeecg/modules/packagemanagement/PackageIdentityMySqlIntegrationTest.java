package org.jeecg.modules.packagemanagement;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import java.sql.DriverManager;
import java.net.URI;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PackageIdentityMySqlIntegrationTest {
 @Test void membershipOwnershipCrossTenantAdminTransferAndRevocationUseRealSql()throws Exception{
  String url=System.getenv("PACKAGE_TEST_MYSQL_URL"),user=System.getenv("PACKAGE_TEST_MYSQL_USER"),password=System.getenv("PACKAGE_TEST_MYSQL_PASSWORD");
  assumeTrue(url!=null&&user!=null&&password!=null,"local package test database is not configured");
  URI target=URI.create(url.substring("jdbc:".length()));assertEquals("127.0.0.1",target.getHost());assertEquals(13317,target.getPort());assertEquals("/package_migration_test",target.getPath());assertEquals("package_test",user);
  try(var connection=DriverManager.getConnection(url,user,password)){
   connection.setAutoCommit(false);
   try{
    JdbcTemplate jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));String suffix=UUID.randomUUID().toString().substring(0,8),u="pkg-u-"+suffix,v="pkg-v-"+suffix;
    int tenant=700000000+new Random().nextInt(10000000);long legacy=800000000000L+tenant;
    jdbc.update("INSERT INTO sys_user(id,username,status,del_flag) VALUES (?,?,1,0),(?,?,1,0)",u,u,v,v);
    jdbc.update("INSERT INTO sys_tenant(id,name,status,del_flag) VALUES (?, 'package test',1,0),(?, 'package other',1,0)",tenant,tenant+1);
    jdbc.update("INSERT INTO sys_user_tenant(id,user_id,tenant_id,status) VALUES (?,?,?,'1'),(?,?,?,'1'),(?,?,?,'1')","m1"+suffix,u,tenant,"m2"+suffix,v,tenant,"m3"+suffix,u,tenant+1);
    jdbc.update("INSERT INTO pkg_user_identity(legacy_user_id,tenant_id,jeecg_user_id) VALUES (?,?,?),(?,?,?),(?,?,?)",legacy,tenant,u,legacy+1,tenant,v,legacy+2,tenant+1,u);
    for(int i=0;i<4;i++)jdbc.update("INSERT INTO pkg_app_manager(id,package_code,user_id) VALUES (?,?,?)",legacy+i,"test."+suffix+"."+i,legacy);
    jdbc.update("INSERT INTO pkg_resource_owner(resource_type,resource_id,tenant_id,owner_user_id) VALUES ('pkg_app_manager',?,?,?),('pkg_app_manager',?,?,?),('pkg_app_manager',?,?,?)",legacy,tenant,u,legacy+1,tenant,v,legacy+2,tenant+1,u);
    var repository=new PackageIdentityRepository(jdbc);var access=new PackageAccess(repository);var actor=access.resolve(u,String.valueOf(tenant),false);var admin=access.resolve(u,String.valueOf(tenant),true);
    assertEquals(Set.of(legacy),access.scope(actor,"pkg_app_manager"));assertEquals(Set.of(legacy,legacy+1),access.scope(admin,"pkg_app_manager"));
    assertThrows(PackageFailure.class,()->access.requireRow(admin,"pkg_app_manager",legacy+2));assertThrows(PackageFailure.class,()->access.requireRow(admin,"pkg_app_manager",legacy+3));
    assertThrows(PackageFailure.class,()->access.transfer(actor,"pkg_app_manager",List.of(legacy,legacy+1),v));assertEquals(u,jdbc.queryForObject("SELECT owner_user_id FROM pkg_resource_owner WHERE resource_type='pkg_app_manager' AND resource_id=?",String.class,legacy));
    access.transfer(actor,"pkg_app_manager",List.of(legacy),v);assertEquals(v,jdbc.queryForObject("SELECT owner_user_id FROM pkg_resource_owner WHERE resource_type='pkg_app_manager' AND resource_id=?",String.class,legacy));assertEquals(legacy+1,jdbc.queryForObject("SELECT user_id FROM pkg_app_manager WHERE id=?",Long.class,legacy));
    jdbc.update("UPDATE sys_tenant SET end_date=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE id=?",tenant);assertThrows(PackageFailure.class,()->access.resolve(u,String.valueOf(tenant),false));
   }finally{connection.rollback();}
  }
 }
}
