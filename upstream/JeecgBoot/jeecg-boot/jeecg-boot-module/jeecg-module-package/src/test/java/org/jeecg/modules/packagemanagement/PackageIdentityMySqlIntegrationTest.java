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
    var guard=org.mockito.Mockito.mock(org.jeecg.modules.packagemanagement.controller.admin.PackageAdminSupport.class);
    org.mockito.Mockito.when(guard.actor("apps","transfer")).thenReturn(actor);
    var targets=new org.jeecg.modules.packagemanagement.controller.admin.PackageAdminActionsController(null,guard,access,null,jdbc).targets().getResult();
    assertEquals(Set.of(u,v),targets.stream().map(row->String.valueOf(row.get("id"))).collect(java.util.stream.Collectors.toSet()),"Transfer picker must join host utf8mb3 IDs with migrated utf8mb4 identity IDs");
    assertEquals(Set.of(legacy),access.scope(actor,"pkg_app_manager"));assertEquals(Set.of(legacy,legacy+1),access.scope(admin,"pkg_app_manager"));
    assertThrows(PackageFailure.class,()->access.requireRow(admin,"pkg_app_manager",legacy+2));assertThrows(PackageFailure.class,()->access.requireRow(admin,"pkg_app_manager",legacy+3));
    assertThrows(PackageFailure.class,()->access.transfer(actor,"pkg_app_manager",List.of(legacy,legacy+1),v));assertEquals(u,jdbc.queryForObject("SELECT owner_user_id FROM pkg_resource_owner WHERE resource_type='pkg_app_manager' AND resource_id=?",String.class,legacy));
    access.transfer(actor,"pkg_app_manager",List.of(legacy),v);assertEquals(v,jdbc.queryForObject("SELECT owner_user_id FROM pkg_resource_owner WHERE resource_type='pkg_app_manager' AND resource_id=?",String.class,legacy));assertEquals(legacy+1,jdbc.queryForObject("SELECT user_id FROM pkg_app_manager WHERE id=?",Long.class,legacy));
    verifyIndependentChildrenAndMovedReview(jdbc,access,actor,admin,access.resolve(v,String.valueOf(tenant),false),legacy,suffix);
    jdbc.update("UPDATE sys_tenant SET end_date=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE id=?",tenant);assertThrows(PackageFailure.class,()->access.resolve(u,String.valueOf(tenant),false));
   }finally{connection.rollback();}
  }
 }
 private void verifyIndependentChildrenAndMovedReview(JdbcTemplate jdbc,PackageAccess access,PackageActor owner,PackageActor admin,PackageActor recipient,long base,String suffix) {
  var apps=org.mockito.Mockito.mock(org.jeecg.modules.packagemanagement.application.apps.IAppManagerService.class);
  var app=new org.jeecg.modules.packagemanagement.domain.app.AppManager();app.setId(base);app.setPackageCode("test."+suffix+".0");
  org.mockito.Mockito.when(apps.selectAppManagerByPackageName(app.getPackageCode())).thenReturn(app);
  var reviews=org.mockito.Mockito.mock(org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.ReviewsRecordMapper.class);
  var policy=new org.jeecg.modules.packagemanagement.controller.admin.PackageResourcePolicy(access,apps,null,null,reviews,jdbc);
  // The enclosing test owns the real connection transaction and always rolls it back.
  var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
   protected Object doGetTransaction(){return new Object();}
   protected void doBegin(Object value,org.springframework.transaction.TransactionDefinition definition){}
   protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status){}
   protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status){}
  });
  for(String type:List.of("pkg_store_status","pkg_app_statistics")) {
   long id=base+(type.equals("pkg_store_status")?10:11);
   jdbc.update("INSERT INTO "+type+"(id,package_code,status) VALUES (?,?,0)",id,app.getPackageCode());
   jdbc.update("INSERT INTO pkg_resource_owner(resource_type,resource_id,tenant_id,owner_user_id) VALUES (?,?,?,?)",type,id,owner.tenantId(),owner.userId());
   // Ownership remains independent after app-only transfer; same-tenant parent membership is still mandatory.
   assertDoesNotThrow(()->access.lockIndependentPackageParent(owner,base));
   assertThrows(PackageFailure.class,()->access.lockIndependentPackageParent(owner,base+2));
   assertThrows(PackageFailure.class,()->access.requireRow(recipient,type,id));
  }
  var saved=new org.jeecg.modules.packagemanagement.domain.store.StoreStatus();saved.setId(base+10);saved.setPackageCode(app.getPackageCode());
  var store=new org.jeecg.modules.packagemanagement.controller.admin.ManagedResource<>("pkg_store_status",org.jeecg.modules.packagemanagement.domain.store.StoreStatus.class,org.jeecg.modules.packagemanagement.domain.store.StoreStatus::getId,f->List.of(saved),id->saved,r->1,r->jdbc.update("UPDATE pkg_store_status SET status=? WHERE id=?",r.getStatus(),r.getId()),ids->1,(r,a)->{},List.of("status","packageCode"),Map.of("status","状态"),access,tx);
  policy.attach(store);var submitted=new org.jeecg.modules.packagemanagement.domain.store.StoreStatus();submitted.setStatus(1);store.edit(owner,base+10,submitted);
  assertEquals(1,jdbc.queryForObject("SELECT status FROM pkg_store_status WHERE id=?",Integer.class,base+10));
  var unauthorized=new org.jeecg.modules.packagemanagement.domain.store.StoreStatus();unauthorized.setPackageCode(app.getPackageCode());assertThrows(PackageFailure.class,()->store.add(owner,unauthorized));
  for(int n=20;n<=21;n++) {
   jdbc.update("INSERT INTO pkg_pwa(id,app_type) VALUES (?,'0')",base+n);
   jdbc.update("INSERT INTO pkg_resource_owner(resource_type,resource_id,tenant_id,owner_user_id) VALUES ('pkg_pwa',?,?,?)",base+n,owner.tenantId(),n==20?owner.userId():recipient.userId());
  }
  jdbc.update("INSERT INTO pkg_review(reviews_id,app_id,name) VALUES (?,?,'review')",base+22,base+20);
  jdbc.update("INSERT INTO pkg_resource_owner(resource_type,resource_id,tenant_id,owner_user_id) VALUES ('pkg_review',?,?,?)",base+22,owner.tenantId(),owner.userId());
  var review=new org.jeecg.modules.packagemanagement.domain.system.ReviewsRecord();review.setReviewsId(base+22);review.setAppId(base+20);
  var resource=new org.jeecg.modules.packagemanagement.controller.admin.ManagedResource<>("pkg_review",org.jeecg.modules.packagemanagement.domain.system.ReviewsRecord.class,org.jeecg.modules.packagemanagement.domain.system.ReviewsRecord::getReviewsId,f->List.of(review),id->review,r->1,r->jdbc.update("UPDATE pkg_review SET app_id=? WHERE reviews_id=?",r.getAppId(),r.getReviewsId()),ids->1,(r,a)->{},List.of("appId"),Map.of("appId","应用"),access,tx);
  policy.attach(resource);var move=new org.jeecg.modules.packagemanagement.domain.system.ReviewsRecord();move.setAppId(base+21);resource.edit(admin,base+22,move);
  assertEquals(base+21,jdbc.queryForObject("SELECT app_id FROM pkg_review WHERE reviews_id=?",Long.class,base+22));
  assertEquals(recipient.userId(),jdbc.queryForObject("SELECT owner_user_id FROM pkg_resource_owner WHERE resource_type='pkg_review' AND resource_id=?",String.class,base+22));
  assertDoesNotThrow(()->access.requireRow(recipient,"pkg_review",base+22));assertThrows(PackageFailure.class,()->access.requireRow(owner,"pkg_review",base+22));
  var parent=new org.jeecg.modules.packagemanagement.domain.pwa.AppPwa();parent.setId(base+21);parent.setAppType("0");
  org.mockito.Mockito.when(reviews.selectReviewsRecordByReviewsId(base+22)).thenReturn(review);
  var pwa=new org.jeecg.modules.packagemanagement.controller.admin.ManagedResource<>("pkg_pwa",org.jeecg.modules.packagemanagement.domain.pwa.AppPwa.class,org.jeecg.modules.packagemanagement.domain.pwa.AppPwa::getId,f->List.of(parent),id->parent,r->1,r->1,ids->1,(r,a)->{},List.of("reviewsRecords"),Map.of("appType","类型"),access,tx);
  policy.attach(pwa);var nested=new org.jeecg.modules.packagemanagement.domain.pwa.AppPwa();nested.setReviewsRecords(List.of(review));assertDoesNotThrow(()->pwa.edit(recipient,base+21,nested));
 }
}
