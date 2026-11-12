package org.jeecg.modules.packagemanagement;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PackagePersistenceMappingTest {
    private static final String ROOT = "org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/xml/";
    private static final String MAPPER_PACKAGE = "org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.";
    private static final Map<String, String> TABLES = new LinkedHashMap<>();

    static {
        TABLES.put("AppManager", "pkg_app_manager");
        TABLES.put("AppStatistics", "pkg_app_statistics");
        TABLES.put("StoreStatus", "pkg_store_status");
        TABLES.put("EventConfig", "pkg_event_config");
        TABLES.put("EvnetHistory", "pkg_event_history");
        TABLES.put("EventError", "pkg_event_error");
        TABLES.put("LoadCount", "pkg_load_count");
        TABLES.put("AppPwa", "pkg_pwa");
        TABLES.put("AppPwaSub", "pkg_pwa_subscription");
        TABLES.put("AppNotify", "pkg_notification");
        TABLES.put("AppEventRecord", "pkg_app_event");
        TABLES.put("ReviewsRecord", "pkg_review");
        TABLES.put("ClientDomain", "pkg_client_domain");
    }

    @Test
    void everyBusinessMapperXmlParsesAndBindsEveryInterfaceMethod() throws Exception {
        Configuration config = new Configuration();
        for (String name : TABLES.keySet()) {
            String resource = ROOT + name + "Mapper.xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
            Class<?> mapper = Class.forName(MAPPER_PACKAGE + name + "Mapper");
            for (Method method : mapper.getDeclaredMethods()) {
                assertTrue(config.hasStatement(mapper.getName() + "." + method.getName()),
                        mapper.getName() + "." + method.getName());
            }
        }
    }

    @Test
    void mappedStatementsUseAllTwelvePackageTablesAndKeepEventHistoryColumns() throws Exception {
        for (Map.Entry<String, String> entry : TABLES.entrySet()) {
            String resource = ROOT + entry.getKey() + "Mapper.xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                String xml = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(xml.contains(entry.getValue()), resource);
                assertFalse(xml.contains("com.ruoyi"), resource);
            }
        }
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(ROOT + "EvnetHistoryMapper.xml")) {
            String xml = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(java.util.regex.Pattern.compile("property=\"appsflyerId\"\\s+column=\"appsflyer_id\"").matcher(xml).find());
            assertTrue(java.util.regex.Pattern.compile("property=\"jsonStr\"\\s+column=\"json_str\"").matcher(xml).find());
            assertTrue(xml.contains("from pkg_event_history"));
        }
    }

    @Test
    void appManagerExplicitIdInsertUsesIdColumnAndBoundParameter() throws Exception {
        AppManager manager = new AppManager();
        manager.setId(17L);
        manager.setPackageCode("legacy.app");
        assertExplicitIdInsert("AppManager", "pkg_app_manager", manager);
    }

    @Test
    void storeStatusExplicitIdInsertUsesIdColumnAndBoundParameter() throws Exception {
        StoreStatus status = new StoreStatus();
        status.setId(19L);
        status.setPackageCode("legacy.app");
        assertExplicitIdInsert("StoreStatus", "pkg_store_status", status);
    }

    private void assertExplicitIdInsert(String name, String table, Object parameter) throws Exception {
        Configuration config = new Configuration();
        String resource = ROOT + name + "Mapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        BoundSql bound = config.getMappedStatement(MAPPER_PACKAGE + name + "Mapper.insert" + name)
                .getBoundSql(parameter);
        String sql = bound.getSql().replaceAll("\\s+", " ").trim();
        assertTrue(sql.matches("(?i)insert into " + table
                + " \\( id, package_code \\) values \\( \\?, \\? \\)"), sql);
        assertEquals(List.of("id", "packageCode"), bound.getParameterMappings().stream()
                .map(mapping -> mapping.getProperty()).toList(), sql);
    }
}
