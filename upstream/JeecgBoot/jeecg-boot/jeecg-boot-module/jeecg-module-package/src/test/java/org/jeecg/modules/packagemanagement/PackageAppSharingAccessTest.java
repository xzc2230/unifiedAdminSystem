package org.jeecg.modules.packagemanagement;

import java.sql.Connection;
import java.util.*;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.packagemanagement.controller.admin.ManagedResource;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackageAppSharingAccessTest {
    @Test void sharedApplicationIsVisibleWithoutSharingOtherResourcesOrTenants() throws Exception {
        try (Fixture f=new Fixture()) {
            assertEquals(Set.of(f.app),f.access.scope(f.owner,"pkg_app_manager"));
            assertEquals(Set.of(f.app),f.access.scope(f.recipient,"pkg_app_manager"),"recipient must see explicit shared app");
            assertTrue(f.access.scope(f.stranger,"pkg_app_manager").isEmpty());
            assertTrue(f.access.scope(f.recipient,"pkg_app_statistics").isEmpty());
            assertTrue(f.access.logPackages(f.recipient).isEmpty(),"sharing must not grant log access");
            assertThrows(PackageFailure.class,()->f.access.requireRow(f.recipient,"pkg_app_manager",f.app+1));
            assertEquals(Set.of(f.app),f.access.scope(f.admin,"pkg_app_manager"));
        }
    }

    @Test void recipientCanEditButCannotDeleteOrForwardAndOwnershipNeverChanges() throws Exception {
        try (Fixture f=new Fixture()) {
            var resource=f.resource();var change=new AppManager();change.setAppName("shared edit");
            change.setCanManage(true);
            assertDoesNotThrow(()->resource.edit(f.recipient,f.app,change),"shared recipient should edit");
            assertFalse(resource.detail(f.recipient,f.app).getCanManage());
            assertTrue(resource.detail(f.owner,f.app).getCanManage());
            assertTrue(resource.detail(f.admin,f.app).getCanManage());
            assertEquals("shared edit",f.jdbc.queryForObject("SELECT app_name FROM pkg_app_manager WHERE id=?",String.class,f.app));
            assertEquals(f.owner.userId(),f.jdbc.queryForObject("SELECT owner_user_id FROM pkg_resource_owner WHERE resource_type='pkg_app_manager' AND resource_id=?",String.class,f.app));
            assertEquals(f.owner.legacyUserId(),f.jdbc.queryForObject("SELECT user_id FROM pkg_app_manager WHERE id=?",Long.class,f.app));
            assertThrows(PackageFailure.class,()->resource.delete(f.recipient,List.of(f.app)));
            assertThrows(PackageFailure.class,()->f.access.transfer(f.recipient,"pkg_app_manager",List.of(f.app),f.stranger.userId()));
            assertDoesNotThrow(()->f.access.requireRow(f.owner,"pkg_app_manager",f.app));
        }
    }

    @Test void storeRecreatedDuringSharedEditBelongsToTheOriginalAppOwner() throws Exception {
        try(Fixture f=new Fixture()) {
            var stores=mock(org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.StoreStatusMapper.class);
            when(stores.selectStoreStatusByPackageName(anyString())).thenAnswer(call->{
                var rows=f.jdbc.query("SELECT id,package_code FROM pkg_store_status WHERE id=?",(rs,n)->{
                    var store=new org.jeecg.modules.packagemanagement.domain.store.StoreStatus();store.setId(rs.getLong(1));store.setPackageCode(rs.getString(2));return store;
                },f.app+10);return rows.isEmpty()?null:rows.get(0);
            });
            var resource=f.resource(true);
            new org.jeecg.modules.packagemanagement.controller.admin.PackageResourcePolicy(f.access,null,null,stores,null,f.jdbc).attach(resource);
            var change=new AppManager();change.setAppName("shared edit with missing store");
            resource.edit(f.recipient,f.app,change);
            assertEquals(f.owner.userId(),f.jdbc.queryForObject("SELECT owner_user_id FROM pkg_resource_owner WHERE resource_type='pkg_store_status' AND resource_id=?",String.class,f.app+10));
            assertTrue(f.access.scope(f.recipient,"pkg_store_status").isEmpty(),"app share cannot claim independent children");
        }
    }

    @Test void revocationRejectsSaveFromPreviouslyLoadedPageAndOwnerDeletionCleansShares() throws Exception {
        try (Fixture f=new Fixture()) {
            var resource=f.resource();
            assertDoesNotThrow(()->resource.detail(f.recipient,f.app));
            f.jdbc.update("DELETE FROM pkg_app_share WHERE app_id=? AND grantee_user_id=?",f.app,f.recipient.userId());
            var change=new AppManager();change.setAppName("late edit");
            assertThrows(PackageFailure.class,()->resource.edit(f.recipient,f.app,change));
            f.share(f.tenant,f.app,f.recipient.userId());
            assertEquals(1,resource.delete(f.owner,List.of(f.app)));
            assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM pkg_app_share WHERE app_id=?",Integer.class,f.app));
        }
    }

    @Test void malformedCrossTenantAndOrphanSharesCannotGrantAccess() throws Exception {
        try (Fixture f=new Fixture()) {
            f.jdbc.update("DELETE FROM pkg_app_share");
            f.share(f.tenant+1,f.app,f.recipient.userId());
            f.share(f.tenant,f.app+99,f.recipient.userId());
            assertTrue(f.access.scope(f.recipient,"pkg_app_manager").isEmpty());
        }
    }

    @Test void wildcardOperationsPermissionDoesNotMakeOrdinaryRoleAnAdministrator() {
        var repository=mock(PackageIdentityRepository.class);var access=new PackageAccess(repository);
        when(repository.activeMember(1002,"ordinary")).thenReturn(true);
        when(repository.legacyIds(1002,"ordinary")).thenReturn(List.of(19L));
        var subject=mock(Subject.class);var user=new LoginUser();user.setId("ordinary");
        when(subject.getPrincipal()).thenReturn(user);when(subject.isPermitted("package:*")).thenReturn(true);
        when(subject.hasRole("admin")).thenReturn(false);
        ThreadContext.bind(subject);TenantContext.setTenant("1002");
        try {assertFalse(access.current().administrator(),"ordinary wildcard grants must not widen data scope");}
        finally {ThreadContext.unbindSubject();TenantContext.clear();}
    }

    @Test void cachedAdministratorRoleCannotOutliveTheLiveRoleAssignment() {
        var repository=mock(PackageIdentityRepository.class);var access=new PackageAccess(repository);
        when(repository.activeMember(1002,"former-admin")).thenReturn(true);
        when(repository.legacyIds(1002,"former-admin")).thenReturn(List.of(20L));
        when(repository.activeAdministrator("former-admin")).thenReturn(true,false);
        var subject=mock(Subject.class);var user=new LoginUser();user.setId("former-admin");
        when(subject.getPrincipal()).thenReturn(user);when(subject.hasRole("admin")).thenReturn(true);
        ThreadContext.bind(subject);TenantContext.setTenant("1002");
        try {
            assertTrue(access.current().administrator());
            assertFalse(access.current().administrator(),"cached Shiro role cannot retain revoked data scope");
        } finally {ThreadContext.unbindSubject();TenantContext.clear();}
    }

    private static class Fixture implements AutoCloseable {
        final Connection connection=PackageBusinessIdentityMigrationTest.localConnection();
        final JdbcTemplate jdbc;
        final PackageAccess access;
        final int tenant=630000000+new Random().nextInt(1000000);
        final long app=850000000000L+tenant;
        final PackageActor owner,recipient,stranger,admin;
        Fixture() throws Exception {
            connection.setAutoCommit(false);jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            // Temporary fixture shadows the new table before the upgrade is applied locally.
            jdbc.execute("CREATE TEMPORARY TABLE pkg_app_share(tenant_id INT,app_id BIGINT,grantee_user_id VARCHAR(64),granted_by VARCHAR(64),reason VARCHAR(500),created_at DATETIME,PRIMARY KEY(tenant_id,app_id,grantee_user_id))");
            String suffix=UUID.randomUUID().toString().substring(0,8);
            owner=new PackageActor(tenant,"share-owner-"+suffix,app,false);
            recipient=new PackageActor(tenant,"share-recipient-"+suffix,app+1,false);
            stranger=new PackageActor(tenant,"share-other-"+suffix,app+2,false);
            admin=new PackageActor(tenant,owner.userId(),app,true);
            jdbc.update("INSERT INTO sys_tenant(id,name,status,del_flag) VALUES (?,'sharing test',1,0)",tenant);
            for(var actor:List.of(owner,recipient,stranger)) {
                jdbc.update("INSERT INTO sys_user(id,username,status,del_flag) VALUES (?,?,1,0)",actor.userId(),actor.userId());
                jdbc.update("INSERT INTO sys_user_tenant(id,user_id,tenant_id,status) VALUES (?,?,?,'1')",UUID.randomUUID().toString().replace("-",""),actor.userId(),tenant);
                jdbc.update("INSERT INTO pkg_user_identity(legacy_user_id,jeecg_user_id,tenant_id) VALUES (?,?,?)",actor.legacyUserId(),actor.userId(),tenant);
            }
            jdbc.update("INSERT INTO pkg_app_manager(id,app_name,package_code,url,user_id) VALUES (?,'original',?,'https://example.com',?)",app,"sharing.test."+suffix,app);
            jdbc.update("INSERT INTO pkg_resource_owner(resource_type,resource_id,tenant_id,owner_user_id) VALUES ('pkg_app_manager',?,?,?)",app,tenant,owner.userId());
            share(tenant,app,recipient.userId());access=new PackageAccess(new PackageIdentityRepository(jdbc));
        }
        void share(int tenant,long id,String user) {
            jdbc.update("INSERT INTO pkg_app_share(tenant_id,app_id,grantee_user_id,granted_by,reason,created_at) VALUES (?,?,?,?,?,NOW())",tenant,id,user,owner.userId(),"test");
        }
        ManagedResource<AppManager> resource() {return resource(false);}
        ManagedResource<AppManager> resource(boolean createStore) {
            var tx=new TransactionTemplate(new AbstractPlatformTransactionManager() {
                protected Object doGetTransaction(){return new Object();}
                protected void doBegin(Object value,org.springframework.transaction.TransactionDefinition definition){}
                protected void doCommit(DefaultTransactionStatus status){}
                protected void doRollback(DefaultTransactionStatus status){}
            });
            return new ManagedResource<>("pkg_app_manager",AppManager.class,AppManager::getId,filter->List.of(),id->{
                var rows=jdbc.query("SELECT id,app_name,user_id,package_code FROM pkg_app_manager WHERE id=?",(rs,n)->{var a=new AppManager();a.setId(rs.getLong(1));a.setAppName(rs.getString(2));a.setUserId(rs.getLong(3));a.setPackageCode(rs.getString(4));return a;},id);
                return rows.isEmpty()?null:rows.get(0);
            },row->1,row->{
                if(createStore)jdbc.update("INSERT INTO pkg_store_status(id,package_code,status) VALUES (?,?,0)",app+10,row.getPackageCode());
                return jdbc.update("UPDATE pkg_app_manager SET app_name=? WHERE id=?",row.getAppName(),row.getId());
            },ids->{int count=0;for(long id:ids)count+=jdbc.update("DELETE FROM pkg_app_manager WHERE id=?",id);return count;},(row,actor)->{},List.of("appName"),Map.of("appName","应用名称"),access,tx);
        }
        public void close() throws Exception {try {connection.rollback();}finally{connection.close();}}
    }
}
