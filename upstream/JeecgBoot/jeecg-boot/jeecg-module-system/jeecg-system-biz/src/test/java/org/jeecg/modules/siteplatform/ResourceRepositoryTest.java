package org.jeecg.modules.siteplatform;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses a new empty schema, never the running JEECG schema. Requires CREATE DATABASE. */
@EnabledIfEnvironmentVariable(named="JEECG_RESOURCE_DB_TEST", matches="true")
class ResourceRepositoryTest {
    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate server;
    private static JdbcTemplate jdbc;
    private static String schema;
    private static ResourceRepository repository;
    private static TransactionTemplate transactions;
    private static final ResourceAccess.Actor A = new ResourceAccess.Actor(100, "user-a");
    private static final ResourceAccess.Actor B = new ResourceAccess.Actor(200, "user-b");

    @BeforeAll static void database() throws Exception {
        schema = "sp_test_" + UUID.randomUUID().toString().replace("-", "");
        String base = "jdbc:mysql://127.0.0.1:13316/";
        String options = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&tinyInt1isBit=false";
        String password = System.getenv("JEECG_LOCAL_DB_PASSWORD");
        server = new JdbcTemplate(new DriverManagerDataSource(base + options, "root", password));
        server.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
        dataSource = new DriverManagerDataSource(base + schema + options, "root", password);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE sys_tenant(id int PRIMARY KEY,status int,del_flag int,begin_date datetime,end_date datetime)");
        jdbc.execute("CREATE TABLE sys_user(id varchar(32) PRIMARY KEY,username varchar(100),realname varchar(100),status int,del_flag int)");
        jdbc.execute("CREATE TABLE sys_user_tenant(tenant_id int,user_id varchar(32),status varchar(1))");
        String sql = Files.readString(Path.of("../../db/site-platform/V20261106_1__site_resources.sql"));
        for (String statement : sql.substring(0, sql.indexOf("START TRANSACTION;")).split(";")) {
            if (!statement.isBlank()) jdbc.execute(statement);
        }
        String routingSql = Files.readString(Path.of("../../db/site-platform/V20261106_2__routing_publication.sql"));
        for (String statement : routingSql.substring(0, routingSql.indexOf("START TRANSACTION;")).split(";")) {
            if (!statement.isBlank()) jdbc.execute(statement);
        }
        jdbc.update("INSERT INTO sys_tenant VALUES (100,1,0,NULL,NULL),(200,1,0,NULL,NULL),(300,1,0,NULL,DATE_SUB(NOW(),INTERVAL 1 DAY))");
        jdbc.update("INSERT INTO sys_user VALUES ('user-a','a','A',1,0),('user-b','b','B',1,0),('viewer','v','V',1,0),('editor','e','E',1,0)");
        jdbc.update("INSERT INTO sys_user_tenant VALUES (100,'user-a','1'),(200,'user-b','1'),(100,'viewer','1'),(100,'editor','1'),(300,'user-a','1')");
        repository = new ResourceRepository(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterAll static void cleanup() {
        if (server != null && schema != null && schema.matches("sp_test_[a-f0-9]{32}")) server.execute("DROP DATABASE IF EXISTS `" + schema + "`");
    }

    private ResourceService service(ResourceAccess.Actor actor) {
        ResourceAccess access = mock(ResourceAccess.class);
        when(access.current()).thenReturn(actor);
        return new ResourceService(repository, access, mock(DnsTxtResolver.class));
    }

    @Test void activeMembershipCannotBeSpoofedAndExpiredTenantsFail() {
        assertTrue(repository.activeMember(100, "user-a"));
        assertFalse(repository.activeMember(200, "user-a"));
        assertFalse(repository.activeMember(300, "user-a"));
    }

    @Test void listDetailsNestedResourcesAndWritesRemainTenantAndSiteScoped() {
        transactions.executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            ResourceService a = service(A);
            ResourceService b = service(B);
            String siteA = a.createSite(new ResourceRequests.SiteInput("A", "test", true));
            String siteB = b.createSite(new ResourceRequests.SiteInput("B", "test", true));
            String siteOther = a.createSite(new ResourceRequests.SiteInput("A2", "test", true));
            String target = a.addTarget(siteA, new ResourceRequests.TargetInput("Main", "https://example.com", true));
            assertEquals(2L, a.sites(1, 20).get("total"));
            assertEquals(1L, b.sites(1, 20).get("total"));
            assertThrows(ResourceFailure.class, () -> b.targets(siteA));
            assertThrows(ResourceFailure.class, () -> a.targets(siteB));
            assertThrows(ResourceFailure.class, () -> a.deleteTarget(siteOther, target));
            assertEquals(1, a.targets(siteA).size());
            a.putMember(siteA, new ResourceRequests.MemberInput("viewer", "VIEWER"));
            ResourceService viewer = service(new ResourceAccess.Actor(100, "viewer"));
            assertEquals(1L, viewer.sites(1, 20).get("total"));
            assertEquals(1, viewer.targets(siteA).size());
            assertThrows(ResourceFailure.class, () -> viewer.deleteTarget(siteA, target));
            assertThrows(ResourceFailure.class, () -> viewer.members(siteA));
            assertThrows(ResourceFailure.class, () -> viewer.targets(siteOther));
        });
    }

    @Test void globalDomainUniquenessChallengeAndAuditDoNotLeakTokens() {
        transactions.executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            ResourceService a = service(A);
            ResourceService b = service(B);
            String siteA = a.createSite(new ResourceRequests.SiteInput("DNS-A", "test", true));
            String siteB = b.createSite(new ResourceRequests.SiteInput("DNS-B", "test", true));
            String domain = a.bindDomain(siteA, new ResourceRequests.DomainInput("EXAMPLE.com."));
            assertThrows(ResourceFailure.class, () -> b.bindDomain(siteB, new ResourceRequests.DomainInput("example.com")));
            assertFalse(a.domains(siteA).get(0).containsKey("challenge"));
            Map<String,Object> challenge = a.challenge(siteA, domain);
            assertEquals("_jeecg-verify.example.com", challenge.get("name"));
            assertEquals(75, challenge.get("value").toString().length());
            a.renewChallenge(siteA, domain);
            assertNotEquals(challenge.get("value"), a.challenge(siteA, domain).get("value"));
            assertFalse(a.audit(siteA).toString().contains(challenge.get("value").toString()));
            assertEquals(0, repository.verifyDomain(100, siteA, domain, "stale-token"));
            a.releaseDomain(siteA, domain);
            assertTrue(a.domains(siteA).isEmpty());
            assertNotNull(b.bindDomain(siteB, new ResourceRequests.DomainInput("example.com")));
        });
    }

    @Test void databaseBackedDnsVerificationAcceptsCorrectRecordAndRejectsExpiredChallenge() {
        transactions.executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            ResourceAccess access = mock(ResourceAccess.class);
            when(access.current()).thenReturn(A);
            DnsTxtResolver dns = mock(DnsTxtResolver.class);
            ResourceService a = new ResourceService(repository, access, dns);
            String site = a.createSite(new ResourceRequests.SiteInput("DNS-real", "test", true));
            String id = a.bindDomain(site, new ResourceRequests.DomainInput("verify.example.com"));
            String token = repository.domain(100, site, id).get("challenge").toString();
            when(dns.records("_jeecg-verify.verify.example.com")).thenReturn(Set.of("jeecg-site=" + token));
            a.verifyDomain(site, id);
            assertEquals(1, ((Number) a.domains(site).get(0).get("verified")).intValue());
            String expired = a.bindDomain(site, new ResourceRequests.DomainInput("expired.example.com"));
            jdbc.update("UPDATE sp_domain_binding SET challenge_expires=DATE_SUB(NOW(),INTERVAL 1 MINUTE) WHERE id=?", expired);
            assertEquals(409, assertThrows(ResourceFailure.class, () -> a.verifyDomain(site, expired)).status());
            verify(dns, never()).records("_jeecg-verify.expired.example.com");
        });
    }

    @Test void revokedAdminCannotRestoreSelfFromAQueuedTransaction() throws Exception { queuedRevocation(true); }
    @Test void revokedEditorCannotWriteFromAQueuedTransaction() throws Exception { queuedRevocation(false); }

    @Test void revokedTenantMemberCannotDeleteTargetFromQueuedTransaction() throws Exception {
        ResourceService owner = service(A);
        ResourceAccess.Actor victim = new ResourceAccess.Actor(100, "editor");
        String[] ids = transactions.execute(tx -> {
            String site = owner.createSite(new ResourceRequests.SiteInput("Tenant revocation", "test", true));
            owner.putMember(site, new ResourceRequests.MemberInput("editor", "EDITOR"));
            String target = owner.addTarget(site, new ResourceRequests.TargetInput("Keep", "https://example.com", true));
            return new String[]{site, target};
        });
        String site = ids[0], target = ids[1];
        CountDownLatch snapshot = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?>[] queued = new Future<?>[1];
        try {
            transactions.executeWithoutResult(tx -> {
                repository.site(100, site, true);
                queued[0] = executor.submit(() -> transactions.executeWithoutResult(other -> {
                    // Establish the same old snapshot as ResourceAccess.current before waiting for the site lock.
                    assertTrue(repository.activeMember(100, "editor"));
                    snapshot.countDown();
                    service(victim).deleteTarget(site, target);
                }));
                try { assertTrue(snapshot.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                jdbc.update("UPDATE sys_user_tenant SET status='0' WHERE tenant_id=100 AND user_id='editor'");
            });
            ExecutionException error = assertThrows(ExecutionException.class, () -> queued[0].get(10, TimeUnit.SECONDS));
            assertInstanceOf(ResourceFailure.class, error.getCause());
            assertEquals(403, ((ResourceFailure) error.getCause()).status());
            assertEquals(1, repository.targets(100, site).size());
            assertEquals("EDITOR", repository.memberRole(100, site, "editor"));
        } finally {
            executor.shutdownNow(); assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
            jdbc.update("UPDATE sys_user_tenant SET status='1' WHERE tenant_id=100 AND user_id='editor'");
            jdbc.update("DELETE FROM sp_resource_audit WHERE site_id=?", site);
            jdbc.update("DELETE FROM sp_site_member WHERE site_id=?", site);
            jdbc.update("DELETE FROM sp_site_target WHERE site_id=?", site);
            jdbc.update("DELETE FROM sp_site WHERE id=?", site);
        }
    }

    private void queuedRevocation(boolean selfGrant) throws Exception {
        ResourceService a = service(A);
        ResourceAccess.Actor victim = new ResourceAccess.Actor(100, "editor");
        String site = transactions.execute(tx -> {
            String id = a.createSite(new ResourceRequests.SiteInput("Concurrency", "test", true));
            a.putMember(id, new ResourceRequests.MemberInput("editor", selfGrant ? "ADMIN" : "EDITOR"));
            return id;
        });
        CountDownLatch snapshot = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?>[] queued = new Future<?>[1];
        try {
            transactions.executeWithoutResult(tx -> {
                repository.site(100, site, true);
                queued[0] = executor.submit(() -> transactions.executeWithoutResult(other -> {
                    // A consistent read BEFORE waiting for the site lock reproduces REPEATABLE READ.
                    assertTrue(repository.activeMember(100, "editor"));
                    snapshot.countDown();
                    if (selfGrant) service(victim).putMember(site, new ResourceRequests.MemberInput("editor", "ADMIN"));
                    else service(victim).addTarget(site, new ResourceRequests.TargetInput("Forbidden", "https://example.com", true));
                }));
                try { assertTrue(snapshot.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                a.removeMember(site, "editor");
            });
            ExecutionException error = assertThrows(ExecutionException.class, () -> queued[0].get(10, TimeUnit.SECONDS));
            assertInstanceOf(ResourceFailure.class, error.getCause());
            assertNull(repository.memberRole(100, site, "editor"));
            assertTrue(repository.targets(100, site).isEmpty());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
            jdbc.update("DELETE FROM sp_resource_audit WHERE site_id=?", site);
            jdbc.update("DELETE FROM sp_site_member WHERE site_id=?", site);
            jdbc.update("DELETE FROM sp_site_target WHERE site_id=?", site);
            jdbc.update("DELETE FROM sp_site WHERE id=?", site);
        }
    }
}
