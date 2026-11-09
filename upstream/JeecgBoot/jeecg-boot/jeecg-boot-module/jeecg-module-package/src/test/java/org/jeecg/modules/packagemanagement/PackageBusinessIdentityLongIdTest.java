package org.jeecg.modules.packagemanagement;

import java.sql.Connection;
import java.sql.Types;
import java.util.Set;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.jeecg.modules.packagemanagement.application.apps.IAppManagerService;
import org.jeecg.modules.packagemanagement.application.event.*;
import org.jeecg.modules.packagemanagement.application.notification.IAppNotifyService;
import org.jeecg.modules.packagemanagement.application.pwa.*;
import org.jeecg.modules.packagemanagement.application.statistics.IAppStatisticsService;
import org.jeecg.modules.packagemanagement.application.store.IStoreStatusService;
import org.jeecg.modules.packagemanagement.controller.admin.*;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.domain.statistics.AppStatistics;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppStatisticsMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackageBusinessIdentityLongIdTest {
    private static final long BUSINESS_ID = 1_000_000_000_000L;

    @Test
    @SuppressWarnings("unchecked")
    void registryCreatesStatisticsWithAllocatedBusinessId() {
        var repository = mock(PackageIdentityRepository.class);
        when(repository.activeMember(1000, "owner")).thenReturn(true);
        var access = new PackageAccess(repository);
        var actor = new PackageActor(1000, "owner", BUSINESS_ID, false);
        when(repository.lockOwnedIds(actor, "pkg_app_manager")).thenReturn(Set.of(7L));
        var apps = mock(IAppManagerService.class);
        var parent = new AppManager();
        parent.setId(7L);
        when(apps.selectAppManagerByPackageName("com.long.identity")).thenReturn(parent);
        var statistics = mock(IAppStatisticsService.class);
        when(statistics.insertAppStatistics(any())).thenAnswer(call -> {
            AppStatistics value = call.getArgument(0);
            assertEquals(BUSINESS_ID, value.getUserId().longValue());
            value.setId(8L);
            return 1;
        });
        var transaction = new TransactionTemplate(new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction() { return new Object(); }
            protected void doBegin(Object tx, org.springframework.transaction.TransactionDefinition definition) {}
            protected void doCommit(DefaultTransactionStatus status) {}
            protected void doRollback(DefaultTransactionStatus status) {}
        });
        var policy = new PackageResourcePolicy(access, apps, null, null, null, null);
        var registry = new PackageAdminRegistry(apps, mock(IStoreStatusService.class), statistics,
                mock(IEventConfigService.class), mock(IEvnetHistoryService.class), mock(IEventErrorService.class),
                mock(IAppPwaService.class), mock(ILoadCountService.class), mock(IAppEventRecordService.class),
                mock(IReviewsRecordService.class), mock(IAppNotifyService.class), access, transaction, policy);
        var resource = (ManagedResource<AppStatistics>) registry.get("statistics");
        var submitted = new AppStatistics();
        submitted.setPackageCode("com.long.identity");

        AppStatistics created = resource.add(actor, submitted);

        assertEquals(BUSINESS_ID, created.getUserId().longValue());
        verify(statistics).insertAppStatistics(created);
        verify(repository).lockOwnedIds(actor, "pkg_app_manager");
        verify(repository).claimCreated(actor, "pkg_app_statistics", 8L);
    }

    @Test
    void myBatisReadsAndWritesRealMySqlBigintUserIdWithoutOverflow() throws Exception {
        try (Connection connection = PackageBusinessIdentityMigrationTest.localConnection()) {
            // Connection-local shadow table leaves the shared schema and business rows untouched.
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TEMPORARY TABLE pkg_app_statistics (id BIGINT PRIMARY KEY,"
                        + "package_code VARCHAR(255),reigister_num BIGINT,payment_num BIGINT,payment_total DECIMAL(18,2),"
                        + "url TEXT,sub_areas TEXT,sub_url TEXT,create_time DATETIME,update_time DATETIME,"
                        + "user_id BIGINT,status INT,shunt INT)");
                statement.executeUpdate("INSERT INTO pkg_app_statistics(id,package_code,user_id)"
                        + " VALUES(1,'com.long.identity'," + BUSINESS_ID + ")");
                try (var rows = statement.executeQuery("SELECT user_id FROM pkg_app_statistics WHERE id=1")) {
                    assertEquals(Types.BIGINT, rows.getMetaData().getColumnType(1));
                    assertTrue(rows.next());
                    assertEquals(BUSINESS_ID, rows.getLong(1));
                }
            }
            var configuration = new Configuration();
            configuration.setEnvironment(new Environment("long-business-id", new JdbcTransactionFactory(),
                    new SingleConnectionDataSource(connection, true)));
            String xml = "org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/xml/AppStatisticsMapper.xml";
            try (var input = getClass().getClassLoader().getResourceAsStream(xml)) {
                assertNotNull(input, xml);
                new XMLMapperBuilder(input, configuration, xml, configuration.getSqlFragments()).parse();
            }
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(false)) {
                var mapper = session.getMapper(AppStatisticsMapper.class);
                AppStatistics stored = mapper.selectAppStatisticsById(1L);
                assertEquals(BUSINESS_ID, stored.getUserId().longValue());
                stored.setId(2L);
                stored.setPackageCode("com.long.identity.copy");
                assertEquals(1, mapper.insertAppStatistics(stored));
                session.clearCache();
                assertEquals(BUSINESS_ID, mapper.selectAppStatisticsById(2L).getUserId().longValue());
                session.rollback(true);
            }
        }
    }
}
