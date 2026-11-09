package org.jeecg.modules.packagemanagement;

import java.net.URI;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.jeecg.modules.packagemanagement.application.identity.*;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PackageBusinessIdentityMySqlIntegrationTest {
    DriverManagerDataSource ds;
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    PackageBusinessIdentityService service;
    final Set<Long> reservations = new HashSet<>();
    final String suffix=UUID.randomUUID().toString().substring(0,8), u="bi-u-"+suffix, v="bi-v-"+suffix;
    final int tenant=710000000+new Random().nextInt(10000000);
    final long old=1100000000L+new Random().nextInt(100000000);
    final PackageBusinessIdentityGuard.Operator operator=new PackageBusinessIdentityGuard.Operator("test-admin","identity-test");

    @BeforeEach void setup() {
        String url=System.getenv("PACKAGE_TEST_MYSQL_URL"),user=System.getenv("PACKAGE_TEST_MYSQL_USER"),password=System.getenv("PACKAGE_TEST_MYSQL_PASSWORD");
        assumeTrue(url!=null && user!=null && password!=null,"local package MySQL is not configured");
        URI uri=URI.create(url.substring(5)); assertEquals("127.0.0.1",uri.getHost()); assertEquals(13317,uri.getPort());
        assertEquals("/package_migration_test",uri.getPath()); assertEquals("package_test",user);
        ds=new DriverManagerDataSource(url,user,password); jdbc=new JdbcTemplate(ds);
        tx=new TransactionTemplate(new DataSourceTransactionManager(ds)); service=new PackageBusinessIdentityService(jdbc,tx);
        jdbc.update("INSERT INTO sys_user(id,username,status,del_flag) VALUES (?,?,1,0),(?,?,1,0)",u,u,v,v);
        jdbc.update("INSERT INTO sys_tenant(id,name,status,del_flag) VALUES (?,'identity test',1,0),(?,'other test',1,0)",tenant,tenant+1);
        jdbc.update("INSERT INTO sys_user_tenant(id,user_id,tenant_id,status) VALUES (?,?,?,'1'),(?,?,?,'2'),(?,?,?,'1')","bi1"+suffix,u,tenant,"bi2"+suffix,v,tenant,"bi3"+suffix,u,tenant+1);
    }
    @AfterEach void cleanup() {
        if(jdbc==null)return;
        reservations.addAll(jdbc.queryForList("SELECT legacy_user_id FROM pkg_user_identity WHERE tenant_id IN (?,?)",Long.class,tenant,tenant+1));
        jdbc.update("DELETE FROM pkg_business_identity_audit WHERE tenant_id IN (?,?)",tenant,tenant+1);
        jdbc.update("DELETE FROM pkg_user_identity WHERE tenant_id IN (?,?)",tenant,tenant+1);
        jdbc.update("DELETE FROM pkg_resource_owner WHERE owner_user_id IN (?,?)",u,v);
        for(String table: List.of("pkg_app_manager","pkg_app_statistics","pkg_load_count","pkg_pwa","pkg_notification","pkg_app_event"))
            jdbc.update("DELETE FROM "+table+" WHERE user_id=?",old);
        for(Long id:reservations) {
            jdbc.update("DELETE FROM pkg_business_id_reservation WHERE legacy_user_id=?",id);
            jdbc.update("DELETE FROM pkg_business_id_allocator WHERE id=?",id);
        }
        jdbc.update("DELETE FROM sys_user_tenant WHERE user_id IN (?,?)",u,v);
        jdbc.update("DELETE FROM sys_tenant WHERE id IN (?,?)",tenant,tenant+1);
        jdbc.update("DELETE FROM sys_user WHERE id IN (?,?)",u,v);
    }
    @Test void listsAllMembersAndSupportsStatesKeywordAndTenantIsolation() {
        var rows=service.list(tenant,null,null,1,100);
        assertEquals(2,rows.total()); assertTrue(rows.records().stream().allMatch(row->"UNOPENED".equals(row.get("identityState"))));
        assertEquals(1,service.list(tenant,u,null,1,10).total());
        assertEquals(Boolean.TRUE,rows.records().get(0).get("tenantActive"));
        var opened=service.open(operator,tenant,u,"new business account");
        assertEquals("NEW",opened.get("source")); assertInstanceOf(String.class,opened.get("legacyUserId"));
        assertEquals(1,service.list(tenant,null,"ACTIVE",1,10).total());
        assertEquals("UNOPENED",service.list(tenant+1,null,null,1,10).records().get(0).get("identityState"));
        assertThrows(PackageFailure.class,()->service.open(operator,tenant,v,"inactive member"));
        jdbc.update("UPDATE sys_tenant SET begin_date=DATE_ADD(NOW(),INTERVAL 1 DAY) WHERE id=?",tenant);
        assertThrows(PackageFailure.class,()->service.open(operator,tenant,u,"future tenant"));
        jdbc.update("UPDATE sys_tenant SET begin_date=NULL,end_date=NOW() WHERE id=?",tenant);
        assertThrows(PackageFailure.class,()->service.preview(tenant,u,String.valueOf(old)));
        assertTrue(service.tenants().stream().anyMatch(t->((Number)t.get("tenantId")).intValue()==tenant));
        jdbc.update("UPDATE sys_tenant SET end_date=NULL WHERE id=?",tenant);
        jdbc.update("UPDATE sys_user SET del_flag=1 WHERE id=?",u);
        assertEquals(0,((Number)service.list(tenant,u,null,1,10).records().get(0).get("userStatus")).intValue(),
                "Deleted accounts must be shown as unavailable so the UI cannot offer writes");
        assertThrows(PackageFailure.class,()->service.open(operator,tenant,u,"deleted user"));
        jdbc.update("UPDATE sys_user SET del_flag=0 WHERE id=?",u);
        jdbc.update("INSERT INTO sys_user_tenant(id,user_id,tenant_id,status) VALUES (?,?,?,'1')","bi-duplicate-"+suffix,u,tenant);
        assertEquals("0",String.valueOf(service.list(tenant,u,null,1,10).records().get(0).get("memberStatus")),
                "Ambiguous memberships must be unavailable in the configuration UI");
        assertThrows(PackageFailure.class,()->service.open(operator,tenant,u,"ambiguous membership"));
    }
    @Test void concurrentOpenIsIdempotentDisabledNeverRestoresAndAuditIsScoped() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(5);
        try {
            var start=new CountDownLatch(1); List<Future<Map<String,Object>>> results=new ArrayList<>();
            for(int i=0;i<5;i++)results.add(pool.submit(()->{start.await();return service.open(operator,tenant,u,"concurrent opening");}));
            start.countDown(); Set<Object> ids=new HashSet<>();
            for(var result:results)ids.add(result.get(30,TimeUnit.SECONDS).get("legacyUserId"));
            assertEquals(1,ids.size());
            assertEquals(1,service.audit(tenant,u,1,10).total());
            assertEquals("DISABLED",service.status(operator,tenant,u,false,"disable").get("identityState"));
            assertEquals("DISABLED",service.open(operator,tenant,u,"retry open").get("identityState"));
            assertEquals("DISABLED",service.bind(operator,tenant,u,ids.iterator().next(),"same binding").get("identityState"));
            assertThrows(PackageFailure.class,()->service.bind(operator,tenant,u,String.valueOf(old),"change id forbidden"));
            assertEquals("ACTIVE",service.status(operator,tenant,u,true,"restore").get("identityState"));
            assertEquals(3,service.audit(tenant,u,1,100).total());
            assertEquals(0,service.audit(tenant+1,u,1,10).total());
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pkg_resource_owner WHERE owner_user_id=?",Integer.class,u));
        } finally { pool.shutdownNow(); }
    }
    @Test void previewCountsSixTablesAndBindRechecksOwnershipWithoutClaiming() {
        for(String table:List.of("pkg_app_manager","pkg_app_statistics","pkg_load_count","pkg_pwa","pkg_notification","pkg_app_event"))
            jdbc.update("INSERT INTO "+table+"(user_id) VALUES (?)",old);
        reservations.add(old);
        jdbc.update("INSERT INTO pkg_business_id_reservation(legacy_user_id,source) VALUES (?,'HISTORICAL')",old);
        var preview=service.preview(tenant,u,String.valueOf(old));
        assertTrue(preview.available()); assertEquals(6,preview.resourceCounts().size());
        assertTrue(preview.resourceCounts().stream().allMatch(count->count.count()==1));
        long row=jdbc.queryForObject("SELECT id FROM pkg_app_manager WHERE user_id=?",Long.class,old);
        jdbc.update("INSERT INTO pkg_resource_owner(resource_type,resource_id,tenant_id,owner_user_id) VALUES ('pkg_app_manager',?,?,?)",row,tenant+1,v);
        assertFalse(service.preview(tenant,u,String.valueOf(old)).available());
        assertEquals(1,service.preview(tenant,u,String.valueOf(old)).ownershipConflicts().size());
        assertThrows(PackageFailure.class,()->service.bind(operator,tenant,u,String.valueOf(old),"must recheck"));
        jdbc.update("UPDATE pkg_resource_owner SET tenant_id=?,owner_user_id=? WHERE resource_type='pkg_app_manager' AND resource_id=?",tenant,u,row);
        assertEquals("LEGACY",service.bind(operator,tenant,u,String.valueOf(old),"verified history").get("source"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM pkg_resource_owner WHERE owner_user_id=?",Integer.class,u));
        assertFalse(service.preview(tenant+1,u,String.valueOf(old)).available());
    }
    @Test void newReservationCannotBeHijackedAndMalformedIdentityCannotBeUsed() {
        reservations.add(old); jdbc.update("INSERT INTO pkg_business_id_reservation(legacy_user_id,source) VALUES (?,'NEW')",old);
        assertFalse(service.preview(tenant,u,String.valueOf(old)).available());
        assertThrows(PackageFailure.class,()->service.bind(operator,tenant,u,String.valueOf(old),"reserved new"));
        jdbc.update("INSERT INTO pkg_user_identity(legacy_user_id,tenant_id,jeecg_user_id,status,source) VALUES (?,?,?,'BROKEN','MIGRATED')",old,tenant,u);
        assertEquals("ANOMALOUS",service.list(tenant,u,null,1,10).records().get(0).get("identityState"));
        assertThrows(PackageFailure.class,()->service.open(operator,tenant,u,"bad identity"));
        assertThrows(PackageFailure.class,()->service.status(operator,tenant,u,true,"no repair"));
    }
    @Test void nullHostStatusMakesMappedIdentityAnomalousAndCannotBeEnabled() {
        service.open(operator,tenant,u,"valid account");
        jdbc.update("UPDATE sys_user SET status=NULL WHERE id=?",u);
        assertEquals("ANOMALOUS",service.list(tenant,u,null,1,10).records().get(0).get("identityState"));
        assertThrows(PackageFailure.class,()->service.status(operator,tenant,u,true,"null host status"));
    }
    @Test void auditFailureRollsBackMappingAndReservation() {
        JdbcTemplate failing=new JdbcTemplate(ds) {
            @Override public int update(String sql,Object... args) {
                if(sql.startsWith("INSERT INTO pkg_business_identity_audit"))throw new IllegalStateException("audit unavailable");
                return super.update(sql,args);
            }
        };
        var broken=new PackageBusinessIdentityService(failing,tx);
        assertThrows(IllegalStateException.class,()->broken.bind(operator,tenant,u,String.valueOf(old),"rollback"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pkg_user_identity WHERE tenant_id=?",Integer.class,tenant));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pkg_business_id_reservation WHERE legacy_user_id=?",Integer.class,old));
        int allocations=jdbc.queryForObject("SELECT COUNT(*) FROM pkg_business_id_allocator",Integer.class);
        assertThrows(IllegalStateException.class,()->broken.open(operator,tenant,u,"new account audit rollback"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pkg_user_identity WHERE tenant_id=?",Integer.class,tenant));
        assertEquals(allocations,jdbc.queryForObject("SELECT COUNT(*) FROM pkg_business_id_allocator",Integer.class));
    }
    @Test void concurrentBindAndOpenCannotChangeTheWinningAccountId() throws Exception {
        reservations.add(old);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Future<Map<String,Object>> opening=pool.submit(()->{start.await();return service.open(operator,tenant,u,"racing open");});
            Future<Map<String,Object>> binding=pool.submit(()->{start.await();return service.bind(operator,tenant,u,String.valueOf(old),"racing bind");});
            start.countDown();var opened=opening.get(30,TimeUnit.SECONDS);
            try { assertEquals(opened.get("legacyUserId"),binding.get(30,TimeUnit.SECONDS).get("legacyUserId")); }
            catch(ExecutionException rejected) {
                assertInstanceOf(PackageFailure.class,rejected.getCause());
                assertNotEquals(String.valueOf(old),opened.get("legacyUserId"));
            }
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM pkg_user_identity WHERE tenant_id=? AND jeecg_user_id=?",Integer.class,tenant,u));
            assertEquals(1,service.audit(tenant,u,1,100).total());
        } finally { pool.shutdownNow(); }
    }
    @Test void differentAccountsCannotBindTheSameHistoricalIdConcurrently() throws Exception {
        reservations.add(old); var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        jdbc.update("INSERT INTO sys_user_tenant(id,user_id,tenant_id,status) VALUES (?,?,?,'1')","bi4"+suffix,v,tenant+1);
        try {
            List<Future<Map<String,Object>>> results=List.of(
                    pool.submit(()->{start.await();return service.bind(operator,tenant,u,String.valueOf(old),"first account");}),
                    pool.submit(()->{start.await();return service.bind(operator,tenant+1,v,String.valueOf(old),"other tenant");}));
            start.countDown();int success=0,rejected=0;
            for(var result:results)try { result.get(30,TimeUnit.SECONDS);success++; }
            catch(ExecutionException failure) { assertInstanceOf(PackageFailure.class,failure.getCause());rejected++; }
            assertEquals(1,success); assertEquals(1,rejected);
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM pkg_user_identity WHERE legacy_user_id=?",Integer.class,old));
        } finally { pool.shutdownNow(); }
    }
    @Test void generatedIdWithHistoricalReferencesIsSkippedAndMarkedHistorical() {
        JdbcTemplate occupied=new JdbcTemplate(ds) {
            boolean first=true;
            @Override public int update(PreparedStatementCreator creator,KeyHolder holder) {
                int result=super.update(creator,holder);
                if(first) {
                    first=false; long id=holder.getKey().longValue(); reservations.add(id);
                    super.update("INSERT INTO pkg_app_manager(user_id,package_code) VALUES (?,?)",id,"bi-occupied-"+suffix);
                }
                return result;
            }
        };
        try {
            var row=new PackageBusinessIdentityService(occupied,tx).open(operator,tenant,u,"skip unreserved history");
            long used=Long.parseLong((String)row.get("legacyUserId"));
            assertFalse(reservations.contains(used));
            for(long id:reservations) assertEquals("HISTORICAL",jdbc.queryForObject("SELECT source FROM pkg_business_id_reservation WHERE legacy_user_id=?",String.class,id));
        } finally { jdbc.update("DELETE FROM pkg_app_manager WHERE package_code=?","bi-occupied-"+suffix); }
    }
    @Test void bindingMaxLongNeverExhaustsFutureNewAccountAllocation() {
        long maximum=Long.MAX_VALUE; reservations.add(maximum);
        assertEquals(Long.toString(maximum),service.bind(operator,tenant,u,Long.toString(maximum),"verified maximum historical id").get("legacyUserId"));
        jdbc.update("UPDATE sys_user_tenant SET status='1' WHERE tenant_id=? AND user_id=?",tenant,v);
        String generated=(String)service.open(operator,tenant,v,"new account after maximum binding").get("legacyUserId");
        assertTrue(Long.parseLong(generated)>=1000000000000L); assertTrue(Long.parseLong(generated)<maximum);
    }
    @Test void rejectedMaximumBindingNeverExhaustsFutureNewAccountAllocation() {
        long maximum=Long.MAX_VALUE;
        jdbc.update("INSERT INTO pkg_app_manager(user_id,package_code) VALUES (?,?)",maximum,"bi-max-"+suffix);
        long app=jdbc.queryForObject("SELECT id FROM pkg_app_manager WHERE package_code=?",Long.class,"bi-max-"+suffix);
        jdbc.update("INSERT INTO pkg_resource_owner(resource_type,resource_id,tenant_id,owner_user_id) VALUES ('pkg_app_manager',?,?,?)",app,tenant+1,v);
        try {
            assertThrows(PackageFailure.class,()->service.bind(operator,tenant,u,Long.toString(maximum),"conflicting maximum id"));
            long generated=Long.parseLong((String)service.open(operator,tenant,u,"open after rejected maximum").get("legacyUserId"));
            assertTrue(generated>=1000000000000L && generated<maximum);
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pkg_business_id_reservation WHERE legacy_user_id=?",Integer.class,maximum));
        } finally { jdbc.update("DELETE FROM pkg_app_manager WHERE package_code=?","bi-max-"+suffix); }
    }
    @Test void preReservedGeneratedCandidateIsSkippedWithoutChangingItsSource() {
        JdbcTemplate collision=new JdbcTemplate(ds) {
            boolean first=true;
            @Override public int update(PreparedStatementCreator creator,KeyHolder holder) {
                int result=super.update(creator,holder);
                if(first) {
                    first=false;long id=holder.getKey().longValue();reservations.add(id);
                    super.update("INSERT INTO pkg_business_id_reservation(legacy_user_id,source) VALUES (?,'LEGACY')",id);
                }
                return result;
            }
        };
        var row=new PackageBusinessIdentityService(collision,tx).open(operator,tenant,u,"skip reserved number");
        assertFalse(reservations.contains(Long.parseLong((String)row.get("legacyUserId"))));
        for(long id:reservations)assertEquals("LEGACY",jdbc.queryForObject("SELECT source FROM pkg_business_id_reservation WHERE legacy_user_id=?",String.class,id));
    }
}
