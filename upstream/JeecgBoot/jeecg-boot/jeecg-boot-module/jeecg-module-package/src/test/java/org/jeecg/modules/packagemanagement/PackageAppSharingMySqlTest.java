package org.jeecg.modules.packagemanagement;

import java.lang.reflect.*;
import java.net.URI;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.controller.admin.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.Mockito.*;

/** Every write uses connection-local temporary tables, including migration/menu checks. */
class PackageAppSharingMySqlTest {
    Connection connection;
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    PackageAccess access;
    PackageActor owner = new PackageActor(71,"sharing-owner",7101L,false);
    PackageActor recipient = new PackageActor(71,"sharing-recipient",7102L,false);
    Object service;

    @BeforeEach void setup() throws Exception {
        String url=System.getenv("PACKAGE_TEST_MYSQL_URL");
        assertNotNull(url,"Real isolated MySQL must be configured");
        URI uri=URI.create(url.substring(5));
        assertEquals("127.0.0.1",uri.getHost()); assertEquals(13317,uri.getPort());
        assertEquals("/package_migration_test",uri.getPath());
        connection=DriverManager.getConnection(url,System.getenv("PACKAGE_TEST_MYSQL_USER"),System.getenv("PACKAGE_TEST_MYSQL_PASSWORD"));
        var ds=new SingleConnectionDataSource(connection,true);
        jdbc=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        for(String table:List.of("sys_user","sys_user_tenant","sys_tenant","pkg_user_identity","pkg_resource_owner","pkg_app_manager","sys_permission"))
            jdbc.execute(jdbc.queryForMap("SHOW CREATE TABLE "+table).get("Create Table").toString().replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE"));
        jdbc.update("INSERT INTO sys_tenant(id,name,status,del_flag) VALUES (71,'sharing',1,0),(72,'other',1,0)");
        for(String user:List.of(owner.userId(),recipient.userId(),"sharing-third")) {
            jdbc.update("INSERT INTO sys_user(id,username,realname,status,del_flag) VALUES (?,?,?,1,0)",user,user,"Display "+user);
            jdbc.update("INSERT INTO sys_user_tenant(id,user_id,tenant_id,status) VALUES (?,?,71,'1')",user,user);
        }
        jdbc.update("INSERT INTO pkg_user_identity(legacy_user_id,tenant_id,jeecg_user_id) VALUES (7101,71,?),(7102,71,?),(7103,71,'sharing-third')",owner.userId(),recipient.userId());
        for(long id:List.of(9007199254740993L,9007199254740994L)) {
            jdbc.update("INSERT INTO pkg_app_manager(id,package_code,user_id,user_name) VALUES (?, ?,7101,'original')",id,"sharing."+id);
            jdbc.update("INSERT INTO pkg_resource_owner(resource_type,resource_id,tenant_id,owner_user_id) VALUES ('pkg_app_manager',?,71,?)",id,owner.userId());
        }
        access=new PackageAccess(new PackageIdentityRepository(jdbc)) {
            @Override public void lockRows(PackageActor actor,String table,Collection<Long> ids) {
                assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),"Ownership lock must be inside a transaction");
                super.lockRows(actor,table,ids);
            }
        };
    }
    @AfterEach void close() throws Exception { if(connection!=null)connection.close(); }
    void migrate() throws Exception {
        var resource=getClass().getClassLoader().getResourceAsStream("db/package/014-app-sharing.sql");
        assertNotNull(resource,"014 sharing migration must exist");
        String sql;
        try(resource){sql=new String(resource.readAllBytes(),StandardCharsets.UTF_8);}
        sql=sql.replaceAll("(?m)^\\s*--[^\\r\\n]*","").replace("CREATE TABLE IF NOT EXISTS","CREATE TEMPORARY TABLE IF NOT EXISTS");
        for(String statement:sql.split(";"))if(!statement.isBlank())jdbc.execute(statement);
    }
    void service() throws Exception {
        migrate();
        service=Class.forName("org.jeecg.modules.packagemanagement.application.sharing.PackageAppSharingService")
                .getConstructor(PackageAccess.class,JdbcTemplate.class,TransactionTemplate.class).newInstance(access,jdbc,tx);
    }
    Object call(String method,PackageActor actor,List<Long> ids,String target,String reason) {
        try{return service.getClass().getMethod(method,PackageActor.class,Collection.class,String.class,String.class).invoke(service,actor,ids,target,reason);}
        catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}
        catch(Exception e){throw new AssertionError(e);}
    }
    @SuppressWarnings("unchecked") Map<String,Object> page(String method) throws Exception {
        return (Map<String,Object>)service.getClass().getMethod(method,PackageActor.class,long.class).invoke(service,owner,9007199254740993L);
    }
    long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    @Test void migrationIsRepeatableAndOnlyRenamesExistingTransferMenu() throws Exception {
        jdbc.update("INSERT INTO sys_permission(id,name,perms) VALUES ('pkg_1_transfer','转移','package:apps:transfer'),('sharing-other','Other','other')");
        migrate(); jdbc.update("INSERT INTO pkg_app_share(tenant_id,app_id,grantee_user_id,granted_by,reason) VALUES (71,1,'x','y','keep')"); migrate();
        assertEquals(1,count("pkg_app_share"));
        assertEquals("共享",jdbc.queryForObject("SELECT name FROM sys_permission WHERE id='pkg_1_transfer'",String.class));
        assertEquals("package:apps:transfer",jdbc.queryForObject("SELECT perms FROM sys_permission WHERE id='pkg_1_transfer'",String.class));
        assertEquals(2,count("sys_permission"));
        assertThrows(Exception.class,()->jdbc.update("INSERT INTO pkg_app_share(tenant_id,app_id,grantee_user_id,granted_by,reason) VALUES (71,1,'x','y','duplicate')"));
    }
    @Test void grantRevokeAreIdempotentKeepOwnershipAndExposeStringDates() throws Exception {
        service(); var ids=List.of(9007199254740993L,9007199254740993L);
        jdbc.execute("ALTER TABLE pkg_app_share_audit AUTO_INCREMENT=9007199254740993");
        assertEquals(1,call("grant",owner,ids,recipient.userId(),"collaborate"));
        assertEquals(0,call("grant",owner,ids,recipient.userId(),"again"));
        assertEquals(1,count("pkg_app_share_audit"));
        assertEquals(owner.userId(),jdbc.queryForObject("SELECT owner_user_id FROM pkg_resource_owner WHERE resource_id=?",String.class,ids.get(0)));
        assertEquals(7101L,jdbc.queryForObject("SELECT user_id FROM pkg_app_manager WHERE id=?",Long.class,ids.get(0)));
        var shares=page("shares"); assertEquals(1L,shares.get("total"));
        var row=(Map<?,?>)((List<?>)shares.get("records")).get(0);
        assertEquals(recipient.userId(),row.get("userId")); assertEquals("Display "+recipient.userId(),row.get("displayName"));
        assertEquals(owner.userId(),row.get("grantedBy")); assertEquals("collaborate",row.get("reason")); assertInstanceOf(String.class,row.get("createdAt"));
        assertEquals(1,call("revoke",owner,ids,recipient.userId(),"finished"));
        assertEquals(0,call("revoke",owner,ids,recipient.userId(),"again"));
        assertEquals(0,count("pkg_app_share")); assertEquals(2,count("pkg_app_share_audit"));
        var audit=(Map<?,?>)((List<?>)page("audit").get("records")).get(0);
        assertEquals("9007199254740993",audit.get("appId")); assertInstanceOf(String.class,audit.get("id")); assertInstanceOf(String.class,audit.get("createdAt"));
        assertEquals("9007199254740994",audit.get("id"));
        assertEquals(recipient.userId(),audit.get("granteeUserId"));
        assertEquals("Display "+recipient.userId(),audit.get("displayName"));
        assertEquals("Display "+owner.userId(),audit.get("operatorName")); assertEquals("REVOKE",audit.get("action"));
    }
    @Test void rejectsInvalidInputSelfOwnerRecipientsAndMixedOrCrossTenantBatch() throws Exception {
        service(); var ids=List.of(9007199254740993L);
        for(List<Long> invalid:List.of(List.<Long>of(),List.of(0L),List.of(-1L),Collections.nCopies(101,ids.get(0))))
            assertEquals(400,assertThrows(PackageFailure.class,()->call("grant",owner,invalid,recipient.userId(),"reason")).getCode());
        for(String reason:List.of(" ","x".repeat(501)))assertThrows(PackageFailure.class,()->call("grant",owner,ids,recipient.userId(),reason));
        assertThrows(PackageFailure.class,()->call("grant",owner,ids,owner.userId(),"self"));
        var admin=new PackageActor(71,"sharing-third",7103L,true);
        assertThrows(PackageFailure.class,()->call("grant",admin,ids,owner.userId(),"owner"));
        assertThrows(PackageFailure.class,()->call("grant",owner,List.of(ids.get(0),999L),recipient.userId(),"mixed"));
        assertThrows(PackageFailure.class,()->call("grant",new PackageActor(72,owner.userId(),7101L,true),ids,recipient.userId(),"cross tenant"));
        call("grant",owner,ids,recipient.userId(),"grant");
        assertEquals(403,assertThrows(PackageFailure.class,()->call("grant",recipient,ids,"sharing-third","forward")).getCode());
        assertEquals(403,assertThrows(PackageFailure.class,()->call("revoke",recipient,ids,recipient.userId(),"revoke")).getCode());
        assertEquals(1,count("pkg_app_share_audit"));
    }
    @Test void rejectsInactiveMissingOrAmbiguousTargetAndRollsBackAuditFailure() throws Exception {
        service(); var ids=List.of(9007199254740993L);
        jdbc.update("UPDATE sys_user_tenant SET status='0' WHERE user_id=?",recipient.userId());
        assertThrows(PackageFailure.class,()->call("grant",owner,ids,recipient.userId(),"inactive"));
        jdbc.update("UPDATE sys_user_tenant SET status='1' WHERE user_id=?",recipient.userId());
        jdbc.update("UPDATE pkg_user_identity SET status='DISABLED' WHERE jeecg_user_id=?",recipient.userId());
        assertThrows(PackageFailure.class,()->call("grant",owner,ids,recipient.userId(),"disabled"));
        jdbc.update("UPDATE pkg_user_identity SET status='ACTIVE' WHERE jeecg_user_id=?",recipient.userId());
        // Simulate historical corrupt cardinality in this connection-local table only.
        jdbc.execute("ALTER TABLE pkg_user_identity DROP INDEX uk_pkg_identity_tenant_user");
        jdbc.update("INSERT INTO pkg_user_identity(legacy_user_id,tenant_id,jeecg_user_id,status) VALUES (7199,71,?,'DISABLED')",recipient.userId());
        assertThrows(PackageFailure.class,()->call("grant",owner,ids,recipient.userId(),"ambiguous"));
        jdbc.update("DELETE FROM pkg_user_identity WHERE legacy_user_id=7199");
        jdbc.execute("ALTER TABLE pkg_app_share_audit ADD CONSTRAINT sharing_test_failure CHECK (app_id <> 9007199254740993)");
        assertThrows(RuntimeException.class,()->call("grant",owner,ids,recipient.userId(),"audit failure"));
        assertEquals(0,count("pkg_app_share")); assertEquals(0,count("pkg_app_share_audit"));
    }
    @Test void legacyFiveArgumentControllerSharesWithoutReplacingOwner() throws Exception {
        migrate(); var guard=mock(PackageAdminSupport.class); when(guard.actor("apps","transfer")).thenReturn(owner);
        var controller=new PackageAdminActionsController(null,guard,access,tx,jdbc);
        assertEquals(1,controller.transfer(new PackageAdminActionsController.Transfer(List.of(9007199254740993L),recipient.userId())).getResult());
        assertEquals(owner.userId(),jdbc.queryForObject("SELECT owner_user_id FROM pkg_resource_owner WHERE resource_id=9007199254740993",String.class));
        assertEquals("original",jdbc.queryForObject("SELECT user_name FROM pkg_app_manager WHERE id=9007199254740993",String.class));
        assertEquals(1,count("pkg_app_share_audit"));
        assertFalse(jdbc.queryForObject("SELECT reason FROM pkg_app_share_audit",String.class).isBlank());
    }
    @Test void managementListsRequireOwnerAndAdministratorCanGrantForOwner() throws Exception {
        service(); var ids=List.of(9007199254740993L);
        var admin=new PackageActor(71,"sharing-third",7103L,true);
        assertEquals(1,call("grant",admin,ids,recipient.userId(),"admin grant"));
        for(String method:List.of("shares","audit")) {
            var failure=assertThrows(InvocationTargetException.class,()->service.getClass().getMethod(method,PackageActor.class,long.class).invoke(service,recipient,ids.get(0)));
            assertEquals(403,((PackageFailure)failure.getCause()).getCode());
        }
        assertEquals(owner.userId(),jdbc.queryForObject("SELECT owner_user_id FROM pkg_resource_owner WHERE resource_id=?",String.class,ids.get(0)));
    }
    @Test void revokeAuditFailureRestoresWholeBatchAndNullInputsAreBadRequests() throws Exception {
        service(); var ids=List.of(9007199254740993L,9007199254740994L);
        assertEquals(400,assertThrows(PackageFailure.class,()->call("grant",owner,null,recipient.userId(),"reason")).getCode());
        assertEquals(400,assertThrows(PackageFailure.class,()->call("grant",owner,ids,recipient.userId(),"reason")).getCode());
        assertEquals(400,assertThrows(PackageFailure.class,()->call("grant",owner,List.of(ids.get(0)),null,"reason")).getCode());
        assertEquals(400,assertThrows(PackageFailure.class,()->call("grant",owner,List.of(ids.get(0)),recipient.userId(),null)).getCode());
        assertEquals(400,assertThrows(PackageFailure.class,()->call("grant",owner,Arrays.asList(ids.get(0),null),recipient.userId(),"reason")).getCode());
        call("grant",owner,List.of(ids.get(0)),recipient.userId(),"grant");
        call("grant",owner,List.of(ids.get(1)),recipient.userId(),"grant");
        jdbc.execute("ALTER TABLE pkg_app_share_audit ADD CONSTRAINT sharing_test_revoke_failure CHECK (action <> 'REVOKE' OR app_id <> 9007199254740994)");
        assertThrows(RuntimeException.class,()->call("revoke",owner,ids,recipient.userId(),"audit failure"));
        assertEquals(2,count("pkg_app_share")); assertEquals(2,count("pkg_app_share_audit"));
    }
    @Test void revokeStillWorksAfterTargetAccountIsDisabled() throws Exception {
        service(); var ids=List.of(9007199254740993L);
        call("grant",owner,ids,recipient.userId(),"grant");
        jdbc.update("UPDATE sys_user SET status=2 WHERE id=?",recipient.userId());
        assertThrows(PackageFailure.class,()->call("grant",owner,List.of(9007199254740994L),recipient.userId(),"disabled"));
        assertEquals(1,call("revoke",owner,ids,recipient.userId(),"disabled account cleanup"));
        assertEquals(0,count("pkg_app_share")); assertEquals(2,count("pkg_app_share_audit"));
    }
    @Test void revokeStillWorksAfterMembershipIdentityAndAccountAreRemoved() throws Exception {
        service(); var ids=List.of(9007199254740993L);
        call("grant",owner,ids,recipient.userId(),"grant");
        jdbc.update("DELETE FROM sys_user_tenant WHERE user_id=?",recipient.userId());
        jdbc.update("DELETE FROM pkg_user_identity WHERE jeecg_user_id=?",recipient.userId());
        jdbc.update("DELETE FROM sys_user WHERE id=?",recipient.userId());
        assertEquals(1,call("revoke",owner,ids,recipient.userId(),"former member cleanup"));
        assertEquals(0,call("revoke",owner,ids,recipient.userId(),"retry"));
        assertEquals(0,count("pkg_app_share")); assertEquals(2,count("pkg_app_share_audit"));
        var audit=(Map<?,?>)((List<?>)page("audit").get("records")).get(0);
        assertEquals(recipient.userId(),audit.get("displayName"));
    }
    @Test void grantRejectsDuplicateValidMemberships() throws Exception {
        service();
        jdbc.update("INSERT INTO sys_user_tenant(id,user_id,tenant_id,status) VALUES ('sharing-duplicate',?,71,'1')",recipient.userId());
        assertEquals(403,assertThrows(PackageFailure.class,()->call("grant",owner,List.of(9007199254740993L),recipient.userId(),"ambiguous membership")).getCode());
        assertEquals(0,count("pkg_app_share")); assertEquals(0,count("pkg_app_share_audit"));
    }
}
