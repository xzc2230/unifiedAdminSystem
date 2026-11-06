package org.jeecg.modules.siteplatform;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Every DDL/DML operation targets a newly generated disposable schema. */
@EnabledIfEnvironmentVariable(named="JEECG_RESOURCE_DB_TEST", matches="true")
class RoutingRepositoryTest {
    static DriverManagerDataSource dataSource;
    static JdbcTemplate server, jdbc;
    static String schema;
    static ResourceRepository resources;
    static RoutingRepository repository;
    static DataSourceTransactionManager manager;
    static TransactionTemplate transactions;
    String site, target, domain;
    RoutingService alice, bob;
    ResourceService resourceService;
    static final String TOKEN = "0123456789abcdef0123456789abcdef";

    @BeforeAll static void database() throws Exception {
        schema = "sp_route_test_" + UUID.randomUUID().toString().replace("-", "");
        String base = "jdbc:mysql://127.0.0.1:13316/";
        String options = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&tinyInt1isBit=false";
        String password = System.getenv("JEECG_LOCAL_DB_PASSWORD");
        server = new JdbcTemplate(new DriverManagerDataSource(base + options, "root", password));
        server.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
        dataSource = new DriverManagerDataSource(base + schema + options, "root", password);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE sys_tenant(id int PRIMARY KEY,status int,del_flag int,begin_date datetime,end_date datetime)");
        jdbc.execute("CREATE TABLE sys_user(id varchar(32) PRIMARY KEY,username varchar(100),realname varchar(100),status int,del_flag int)");
        jdbc.execute("CREATE TABLE sys_user_tenant(tenant_id int,user_id varchar(32),status varchar(1), PRIMARY KEY(tenant_id,user_id))");
        for (String file : List.of("V20261106_1__site_resources.sql", "V20261106_2__routing_publication.sql")) {
            String sql = Files.readString(Path.of("../../db/site-platform/" + file));
            for (String statement : sql.substring(0, sql.indexOf("START TRANSACTION;")).split(";")) {
                if (!statement.isBlank()) jdbc.execute(statement);
            }
        }
        jdbc.update("INSERT INTO sys_tenant VALUES (100,1,0,NULL,NULL),(200,1,0,NULL,NULL)");
        jdbc.update("INSERT INTO sys_user VALUES ('alice','a','A',1,0),('bob','b','B',1,0),('foreign','f','F',1,0)");
        jdbc.update("INSERT INTO sys_user_tenant VALUES (100,'alice','1'),(100,'bob','1'),(200,'foreign','1')");
        resources = new ResourceRepository(dataSource); repository = new RoutingRepository(dataSource);
        manager = new DataSourceTransactionManager(dataSource); transactions = new TransactionTemplate(manager);
    }
    @AfterAll static void cleanup() {
        if (server != null && schema != null && schema.matches("sp_route_test_[a-f0-9]{32}")) server.execute("DROP DATABASE IF EXISTS `" + schema + "`");
    }
    @SuppressWarnings("unchecked") static <T> T transactional(T target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }
    ResourceAccess access(int tenant, String user) {
        ResourceAccess access = mock(ResourceAccess.class);
        when(access.current()).thenReturn(new ResourceAccess.Actor(tenant, user)); return access;
    }
    RoutingService service(int tenant, String user) { return transactional(new RoutingService(repository, resources, access(tenant, user))); }
    @BeforeEach void seed() {
        alice = service(100, "alice"); bob = service(100, "bob");
        resourceService = transactional(new ResourceService(resources, access(100, "alice"), mock(DnsTxtResolver.class)));
        site = resourceService.createSite(new ResourceRequests.SiteInput("Routing", "prod", true));
        resourceService.putMember(site, new ResourceRequests.MemberInput("bob", "ADMIN"));
        target = resourceService.addTarget(site, new ResourceRequests.TargetInput("Origin", "https://origin.example.com", true));
        domain = resourceService.bindDomain(site, new ResourceRequests.DomainInput("r" + site + ".example.com"));
        jdbc.update("UPDATE sp_domain_binding SET verified=1,verified_at=NOW(),challenge=NULL WHERE id=?", domain);
    }
    RoutingRequests.DraftInput input(long rev) { return new RoutingRequests.DraftInput(rev, target, List.of(domain), List.of(new RoutingRequests.RuleInput("api", null, "/api", "PREFIX", 10, target))); }
    String publish() {
        assertEquals(1, alice.save(site, input(0)));
        assertEquals(2, alice.submit(site, 1));
        assertEquals(3, bob.approve(site, 2));
        assertEquals(4, alice.publish(site, 3));
        return repository.desired(100, site).get("id").toString();
    }
    @Test void fullLifecycleKeepsCandidateBytesAndHistoryImmutableAndRollbackIsOnlyADraft() throws Exception {
        assertEquals(1, alice.save(site, input(0)));
        assertEquals(2, alice.submit(site, 1));
        var candidate = repository.draft(100, site);
        assertEquals(RoutingRules.hash(candidate.payload()), candidate.hash());
        String fixture = System.getenv("JEECG_ROUTING_COMPAT_FIXTURE");
        if (fixture != null && !fixture.isBlank()) Files.writeString(Path.of(fixture), RoutingRules.json(Map.of("nodeId", "compat-node", "snapshots",
            List.of(Map.of("payload", candidate.payload(), "hash", candidate.hash())))));
        assertEquals(403, assertThrows(ResourceFailure.class, () -> alice.approve(site, 2)).status());
        bob.approve(site, 2); alice.publish(site, 3);
        var live = repository.desired(100, site);
        assertEquals(candidate.payload(), live.get("payload")); assertEquals(candidate.hash(), live.get("hash"));
        String id = live.get("id").toString();
        assertEquals(409, assertThrows(ResourceFailure.class, () -> alice.publish(site, 3)).status());
        assertEquals(5, alice.rollback(site, id, 4));
        assertEquals(id, repository.desired(100, site).get("id"));
        assertEquals("DRAFT", repository.draft(100, site).status());
        alice.submit(site, 5); bob.approve(site, 6); alice.publish(site, 7);
        assertNotEquals(id, repository.desired(100, site).get("id"));
        assertEquals(id, repository.desired(100, site).get("sourceReleaseId"));
        assertEquals(candidate.payload(), repository.release(100, site, id).get("payload"));
        var view = alice.view(site).toString();
        assertFalse(view.contains("https://origin")); assertFalse(view.contains("candidate"));
        assertFalse(view.contains(TOKEN));
    }
    @Test void changedResourcesInvalidateApprovedCandidateAndRollback() {
        String old = publish();
        alice.save(site, input(4)); alice.submit(site, 5); bob.approve(site, 6);
        resourceService.updateTarget(site, target, new ResourceRequests.TargetInput("changed", "https://changed.example.com", true));
        assertEquals(409, assertThrows(ResourceFailure.class, () -> alice.publish(site, 7)).status());
        assertEquals(old, repository.desired(100, site).get("id"));
        assertEquals(409, assertThrows(ResourceFailure.class, () -> alice.rollback(site, old, 7)).status());
        assertEquals("APPROVED", repository.draft(100, site).status());
        resourceService.updateTarget(site, target, new ResourceRequests.TargetInput("disabled", "https://changed.example.com", false));
        assertEquals(409, assertThrows(ResourceFailure.class, () -> alice.submit(site, 7)).status());
    }
    @Test void desiredTargetsCannotBeDeletedAndHistoricalDomainsStayReservedForOfflineNodes() {
        publish();
        assertEquals(409, assertThrows(ResourceFailure.class, () -> resourceService.deleteTarget(site, target)).status());
        assertEquals(409, assertThrows(ResourceFailure.class, () -> resourceService.releaseDomain(site, domain)).status());
        String nextTarget = resourceService.addTarget(site, new ResourceRequests.TargetInput("Next", "https://next.example.com", true));
        String nextDomain = resourceService.bindDomain(site, new ResourceRequests.DomainInput("next" + site + ".example.com"));
        jdbc.update("UPDATE sp_domain_binding SET verified=1 WHERE id=?", nextDomain);
        alice.save(site, new RoutingRequests.DraftInput(4, nextTarget, List.of(nextDomain), List.of()));
        alice.submit(site, 5); bob.approve(site, 6); alice.publish(site, 7);
        resourceService.updateSite(site, new ResourceRequests.SiteInput("Routing", "prod", false));
        resourceService.deleteTarget(site, target);
        var failure = assertThrows(ResourceFailure.class, () -> resourceService.releaseDomain(site, domain));
        assertEquals(409, failure.status());
        assertEquals("域名已有发布历史，需运维完成旧节点撤除后迁移", failure.getMessage());
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM sp_route_release WHERE site_id=?", Long.class, site));
    }
    @Test void targetReferencedOnlyByRuleIsAlsoProtected() {
        String ruleTarget = resourceService.addTarget(site, new ResourceRequests.TargetInput("Rule", "https://rule.example.com", true));
        alice.save(site, new RoutingRequests.DraftInput(0, target, List.of(domain), List.of(new RoutingRequests.RuleInput("api", null, "/api", "PREFIX", 10, ruleTarget))));
        alice.submit(site, 1); bob.approve(site, 2); alice.publish(site, 3);
        assertEquals(409, assertThrows(ResourceFailure.class, () -> resourceService.deleteTarget(site, ruleTarget)).status());
    }
    @Test void foreignTenantAndForeignResourceIdsNeverExposeChildren() {
        publish();
        assertEquals(403, assertThrows(ResourceFailure.class, () -> service(200, "foreign").view(site)).status());
        assertEquals(403, assertThrows(ResourceFailure.class, () -> service(200, "foreign").rollback(site, repository.desired(100, site).get("id").toString(), 4)).status());
        assertEquals(409, assertThrows(ResourceFailure.class, () -> alice.save(site, new RoutingRequests.DraftInput(4, "missing", List.of(domain), List.of()))).status());
        assertEquals(4, repository.draft(100, site).revision());
    }
    @Test void nodeAckPersistsAndStaleAckDoesNotReplaceNewDesired() {
        String first = publish();
        var node = transactional(new RoutingNodeController(repository, "node", TOKEN, "100/" + site));
        var live = repository.desired(100, site);
        assertEquals(Map.of("accepted", true), node.ack("Bearer " + TOKEN, new RoutingRequests.AckInput(site, first, live.get("hash").toString())));
        assertTrue(alice.view(site).toString().contains("appliedAt"));
        alice.save(site, input(4)); alice.submit(site, 5); bob.approve(site, 6); alice.publish(site, 7);
        assertEquals(409, assertThrows(ResourceFailure.class, () -> node.ack("Bearer " + TOKEN, new RoutingRequests.AckInput(site, first, live.get("hash").toString()))).status());
        assertEquals(first, jdbc.queryForObject("SELECT release_id FROM sp_route_ack WHERE site_id=?", String.class, site));
        assertEquals(1, ((List<?>) node.snapshots("Bearer " + TOKEN).get("snapshots")).size());
        assertTrue(((List<?>) transactional(new RoutingNodeController(repository, "other", TOKEN, "200/" + site)).snapshots("Bearer " + TOKEN).get("snapshots")).isEmpty());
    }
    @Test void inventoryDisableDoesNotChangePublishedBytesButPreventsNextPublication() {
        publish();
        var published = repository.desired(100, site);
        alice.save(site, input(4)); alice.submit(site, 5); bob.approve(site, 6);
        resourceService.updateSite(site, new ResourceRequests.SiteInput("Routing", "prod", false));
        assertEquals(published, repository.desired(100, site));
        assertEquals(409, assertThrows(ResourceFailure.class, () -> alice.publish(site, 7)).status());
        alice.save(site, input(7));
        assertEquals(409, assertThrows(ResourceFailure.class, () -> alice.submit(site, 8)).status());
        assertEquals(published, repository.desired(100, site));
    }
    @Test void changedDomainAndTamperedCandidateCannotBePublished() {
        alice.save(site, input(0)); alice.submit(site, 1); bob.approve(site, 2);
        jdbc.update("UPDATE sp_domain_binding SET verified=0 WHERE id=?", domain);
        assertEquals(409, assertThrows(ResourceFailure.class, () -> alice.publish(site, 3)).status());
        jdbc.update("UPDATE sp_domain_binding SET verified=1 WHERE id=?", domain);
        jdbc.update("UPDATE sp_route_draft SET candidate_hash=? WHERE site_id=?", "0".repeat(64), site);
        assertEquals(409, assertThrows(ResourceFailure.class, () -> alice.publish(site, 3)).status());
        assertNull(repository.desired(100, site));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sp_route_release WHERE site_id=?", Long.class, site));
    }
    @Test void neverPublishedDomainCanStillBeReleasedAndRebound() {
        resourceService.releaseDomain(site, domain);
        String rebound = resourceService.bindDomain(site, new ResourceRequests.DomainInput("r" + site + ".example.com"));
        assertNotEquals(domain, rebound);
    }
    @Test void queuedOldAckRejectsTheNewDesiredEvenAfterEarlierConsistentRead() throws Exception {
        String first = publish();
        String hash = repository.desired(100, site).get("hash").toString();
        alice.save(site, input(4)); alice.submit(site, 5); bob.approve(site, 6);
        var node = transactional(new RoutingNodeController(repository, "node", TOKEN, "100/" + site));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch snapshot = new CountDownLatch(1); Future<?>[] queued = new Future<?>[1];
        try {
            transactions.executeWithoutResult(tx -> {
                resources.site(100, site, true);
                queued[0] = pool.submit(() -> transactions.executeWithoutResult(other -> {
                    assertTrue(resources.activeMember(100, "alice")); snapshot.countDown();
                    node.ack("Bearer " + TOKEN, new RoutingRequests.AckInput(site, first, hash));
                }));
                try { assertTrue(snapshot.await(10, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
                alice.publish(site, 7);
            });
            var failure = assertThrows(ExecutionException.class, () -> queued[0].get(15, TimeUnit.SECONDS));
            assertInstanceOf(ResourceFailure.class, failure.getCause());
            assertEquals(409, ((ResourceFailure) failure.getCause()).status());
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sp_route_ack WHERE site_id=?", Long.class, site));
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS)); }
    }
    @Test void expiredOrDisabledTenantWithdrawsNodeSnapshotAsSecurityRevocation() {
        publish();
        try {
            jdbc.update("UPDATE sys_tenant SET end_date=DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE id=100");
            assertNull(repository.desired(100, site));
            assertEquals(403, assertThrows(ResourceFailure.class, () -> alice.view(site)).status());
        } finally { jdbc.update("UPDATE sys_tenant SET end_date=NULL WHERE id=100"); }
    }
    @Test void simulationUsesHighestPriorityDomainScopeAndSegmentBoundaryWithoutNetwork() {
        String other = resourceService.addTarget(site, new ResourceRequests.TargetInput("API", "https://api.example.com/base", true));
        String hostname = resources.domains(100, site).get(0).get("hostname").toString();
        var rules = List.of(new RoutingRequests.RuleInput("generic", null, "/api", "PREFIX", 10, target),
            new RoutingRequests.RuleInput("exact", domain, "/api/items", "EXACT", 20, other));
        var input = new RoutingRequests.DraftInput(0, target, List.of(domain), rules);
        assertEquals(Map.of("ruleId", "exact", "targetId", other, "url", "https://api.example.com/base/api/items"), alice.simulate(site, new RoutingRequests.SimulateInput(hostname, "/api/items", input)));
        assertEquals("generic", alice.simulate(site, new RoutingRequests.SimulateInput(hostname, "/api/v1", input)).get("ruleId"));
        assertNull(alice.simulate(site, new RoutingRequests.SimulateInput(hostname, "/apian", input)).get("ruleId"));
        assertEquals(400, assertThrows(ResourceFailure.class, () -> alice.simulate(site, new RoutingRequests.SimulateInput("unknown.example.com", "/api", input))).status());
    }
    @Test void auditFailureRollsBackBusinessWrite() {
        ResourceRepository failing = spy(resources);
        doThrow(new IllegalStateException("audit failed")).when(failing).audit(any(), eq(site), eq("ROUTING_SAVE"), anyString(), anyString());
        var service = transactional(new RoutingService(repository, failing, access(100, "alice")));
        assertThrows(IllegalStateException.class, () -> service.save(site, input(0)));
        assertNull(repository.draft(100, site));
    }
    @Test void queuedRevokedSiteMemberCannotSaveUsingOldRepeatableReadSnapshot() throws Exception { queuedRevocation(false); }
    @Test void queuedRevokedTenantMemberCannotSaveUsingOldRepeatableReadSnapshot() throws Exception { queuedRevocation(true); }
    void queuedRevocation(boolean tenantMembership) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch snapshot = new CountDownLatch(1);
        Future<?>[] queued = new Future<?>[1];
        try {
            transactions.executeWithoutResult(tx -> {
                resources.site(100, site, true);
                queued[0] = pool.submit(() -> transactions.executeWithoutResult(other -> {
                    assertTrue(resources.activeMember(100, "bob")); snapshot.countDown();
                    bob.save(site, input(0));
                }));
                try { assertTrue(snapshot.await(10, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
                if (tenantMembership) jdbc.update("UPDATE sys_user_tenant SET status='0' WHERE tenant_id=100 AND user_id='bob'");
                else resourceService.removeMember(site, "bob");
            });
            var failure = assertThrows(ExecutionException.class, () -> queued[0].get(15, TimeUnit.SECONDS));
            assertInstanceOf(ResourceFailure.class, failure.getCause());
            assertEquals(403, ((ResourceFailure) failure.getCause()).status());
            assertNull(repository.draft(100, site));
        } finally {
            pool.shutdownNow(); assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));
            jdbc.update("UPDATE sys_user_tenant SET status='1' WHERE tenant_id=100 AND user_id='bob'");
        }
    }
    @Test void twoConcurrentSavesHaveExactlyOneWinner() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> save = () -> { start.await(); try { alice.save(site, input(0)); return 200; } catch (ResourceFailure e) { return e.status(); } };
        try {
            var a = pool.submit(save); var b = pool.submit(save); start.countDown();
            assertEquals(List.of(200, 409), java.util.stream.Stream.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)).sorted().toList());
            assertEquals(1, repository.draft(100, site).revision());
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS)); }
    }
    @Test void platformHostedUpstreamCannotPoisonTheCompleteNodeBatch() {
        String original = publish();
        ResourceService otherResources = transactional(new ResourceService(resources, access(200, "foreign"), mock(DnsTxtResolver.class)));
        String otherSite = otherResources.createSite(new ResourceRequests.SiteInput("Other", "test", true));
        String otherTarget = otherResources.addTarget(otherSite, new ResourceRequests.TargetInput("Origin", "https://other-origin.example.com", true));
        String otherHost = "b" + otherSite + ".example.com";
        String otherDomain = otherResources.bindDomain(otherSite, new ResourceRequests.DomainInput(otherHost));
        jdbc.update("UPDATE sp_domain_binding SET verified=1 WHERE id=?", otherDomain);
        RoutingService other = service(200, "foreign");
        other.save(otherSite, new RoutingRequests.DraftInput(0, otherTarget, List.of(otherDomain), List.of()));
        other.submit(otherSite, 1); other.approve(otherSite, 2); other.publish(otherSite, 3);
        resourceService.updateTarget(site, target, new ResourceRequests.TargetInput("chaining", "https://" + otherHost, true));
        alice.save(site, input(4)); alice.submit(site, 5); bob.approve(site, 6);
        assertEquals(409, assertThrows(ResourceFailure.class, () -> alice.publish(site, 7)).status());
        assertEquals(original, repository.desired(100, site).get("id"));
        assertEquals("APPROVED", repository.draft(100, site).status());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sp_route_release WHERE site_id=?", Long.class, site));
    }
    @Test void concurrentCrossTenantPublicationCannotInstallMutualHostedUpstreams() throws Exception {
        ResourceService otherResources = transactional(new ResourceService(resources, access(200, "foreign"), mock(DnsTxtResolver.class)));
        String otherSite = otherResources.createSite(new ResourceRequests.SiteInput("Other", "test", true));
        String hostA = "r" + site + ".example.com", hostB = "b" + otherSite + ".example.com";
        String otherTarget = otherResources.addTarget(otherSite, new ResourceRequests.TargetInput("A", "https://" + hostA, true));
        String otherDomain = otherResources.bindDomain(otherSite, new ResourceRequests.DomainInput(hostB));
        jdbc.update("UPDATE sp_domain_binding SET verified=1 WHERE id=?", otherDomain);
        resourceService.updateTarget(site, target, new ResourceRequests.TargetInput("B", "https://" + hostB, true));
        RoutingService other = service(200, "foreign");
        alice.save(site, input(0)); alice.submit(site, 1); bob.approve(site, 2);
        other.save(otherSite, new RoutingRequests.DraftInput(0, otherTarget, List.of(otherDomain), List.of()));
        other.submit(otherSite, 1); other.approve(otherSite, 2);
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { start.await(); try { alice.publish(site, 3); return 200; } catch (ResourceFailure e) { return e.status(); } });
            var b = pool.submit(() -> { start.await(); try { other.publish(otherSite, 3); return 200; } catch (ResourceFailure e) { return e.status(); } });
            start.countDown();
            assertEquals(List.of(200,409), java.util.stream.Stream.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)).sorted().toList());
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sp_route_live WHERE site_id IN (?,?)", Long.class, site, otherSite));
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS)); }
    }
}
