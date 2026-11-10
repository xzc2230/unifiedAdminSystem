package org.jeecg.modules.packagemanagement;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppManagerMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.StoreStatusMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PackagePersistenceMySqlIntegrationTest {
    private static final String XML_ROOT =
            "org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/xml/";

    @Test
    void actualApplicationUpdateSqlPassesTheHostSafetyInterceptor() throws Exception {
        Configuration configuration = new Configuration();
        loadMapper(configuration, "AppManagerMapper.xml");
        AppManager app = new AppManager();
        app.setId(123L);app.setAppName("shared edit");app.setUrl("https://example.com");
        app.setGaidAidThreshold(1);app.setWhetherTimezoneRestrict(0);
        String statement = AppManagerMapper.class.getName()+".updateAppManager";
        String sql = configuration.getMappedStatement(statement).getBoundSql(app).getSql();
        var safety = new com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor();
        assertDoesNotThrow(()->safety.parserMulti(sql,null),"real host interceptor must accept generated edit SQL");
    }

    @Test
    void explicitHistoricalIdsAreInsertedAndReadBackThroughMyBatis() throws Exception {
        String url = System.getenv("PACKAGE_TEST_MYSQL_URL");
        String user = System.getenv("PACKAGE_TEST_MYSQL_USER");
        String password = System.getenv("PACKAGE_TEST_MYSQL_PASSWORD");
        assumeTrue(url != null && !url.isBlank() && user != null && !user.isBlank()
                && password != null && !password.isBlank(), "local package test database is not configured");

        // This test writes only to the explicitly named, local disposable database.
        assertTrue(url.startsWith("jdbc:mysql://"), "test URL must use MySQL JDBC");
        URI target = URI.create(url.substring("jdbc:".length()));
        assertEquals("127.0.0.1", target.getHost());
        assertEquals(13317, target.getPort());
        assertEquals("/package_migration_test", target.getPath());
        assertEquals("package_test", user);

        Configuration configuration = new Configuration();
        configuration.setEnvironment(new Environment("package-migration-test",
                new JdbcTransactionFactory(),
                new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, user, password)));
        loadMapper(configuration, "AppManagerMapper.xml");
        loadMapper(configuration, "StoreStatusMapper.xml");
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);

        long managerId = ThreadLocalRandom.current().nextLong(6_000_000_000_000L, 7_000_000_000_000L);
        long storeId = ThreadLocalRandom.current().nextLong(7_000_000_000_000L, 8_000_000_000_000L);
        String code = "migration-test-" + managerId;
        try (SqlSession session = factory.openSession(false)) {
            try {
                AppManager manager = new AppManager();
                manager.setId(managerId);
                manager.setPackageCode(code);
                AppManagerMapper managers = session.getMapper(AppManagerMapper.class);
                assertEquals(1, managers.insertAppManager(manager));
                AppManager storedManager = managers.selectAppManagerById(managerId);
                assertNotNull(storedManager);
                assertEquals(managerId, storedManager.getId());
                assertEquals(code, storedManager.getPackageCode());

                StoreStatus status = new StoreStatus();
                status.setId(storeId);
                status.setPackageCode(code);
                StoreStatusMapper stores = session.getMapper(StoreStatusMapper.class);
                assertEquals(1, stores.insertStoreStatus(status));
                StoreStatus storedStatus = stores.selectStoreStatusById(storeId);
                assertNotNull(storedStatus);
                assertEquals(storeId, storedStatus.getId());
                assertEquals(code, storedStatus.getPackageCode());
            } finally {
                session.rollback(true);
            }
        }
    }

    private void loadMapper(Configuration configuration, String filename) throws Exception {
        String resource = XML_ROOT + filename;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }
}
