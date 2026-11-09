package org.jeecg.modules.packagemanagement;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PackageBusinessIdentityMigrationTest {
    private static final String MIGRATION = "009-business-accounts.sql";
    private static final List<String> BUSINESS = List.of("pkg_app_manager", "pkg_app_statistics", "pkg_load_count", "pkg_pwa", "pkg_notification", "pkg_app_event");

    static Connection localConnection() throws Exception {
        String url = System.getenv("PACKAGE_TEST_MYSQL_URL"), user = System.getenv("PACKAGE_TEST_MYSQL_USER"), password = System.getenv("PACKAGE_TEST_MYSQL_PASSWORD");
        assumeTrue(url != null && user != null && password != null, "local package test database is not configured");
        URI target = URI.create(url.substring("jdbc:".length()));
        assertEquals("127.0.0.1", target.getHost());
        assertEquals(13317, target.getPort());
        assertEquals("/package_migration_test", target.getPath());
        assertEquals("package_test", user);
        return DriverManager.getConnection(url, user, password);
    }

    @Test void migrationReservesHistoricalIdsAndIsRepeatableWithoutChangingIdentityState() throws Exception {
        try (Fixture f = new Fixture()) {
            f.execute("INSERT INTO pkg_user_identity VALUES(7,'mapped',1000)");
            long high = 1000000000042L;
            for (int i = 0; i < BUSINESS.size(); i++) {
                f.execute("INSERT INTO " + BUSINESS.get(i) + " VALUES(" + (high + i) + "),(7),(NULL),(0),(-1)");
            }
            f.migrate();
            assertEquals("ACTIVE:MIGRATED", f.scalar("SELECT CONCAT(status,':',source) FROM pkg_user_identity"));
            assertEquals("7", f.scalar("SELECT COUNT(*) FROM pkg_business_id_reservation"));
            assertEquals("7", f.scalar("SELECT COUNT(*) FROM pkg_business_id_reservation WHERE source='HISTORICAL'"));
            assertThrows(SQLException.class, () -> f.execute("INSERT INTO pkg_user_identity(legacy_user_id,jeecg_user_id,tenant_id) VALUES(8,'mapped',1000)"));
            f.execute("UPDATE pkg_user_identity SET status='DISABLED',source='LEGACY'");
            f.execute("UPDATE pkg_business_id_reservation SET source='NEW' WHERE legacy_user_id=7");
            f.migrate();
            assertEquals("DISABLED:LEGACY", f.scalar("SELECT CONCAT(status,':',source) FROM pkg_user_identity"));
            assertEquals("NEW", f.scalar("SELECT source FROM pkg_business_id_reservation WHERE legacy_user_id=7"));
            f.execute("INSERT INTO pkg_business_id_allocator() VALUES()");
            assertEquals("1000000000000", f.scalar("SELECT id FROM pkg_business_id_allocator"));
            f.execute("INSERT INTO pkg_business_id_reservation(legacy_user_id,source) SELECT id,'NEW' FROM pkg_business_id_allocator");
            assertEquals(high + 5, Long.parseLong(f.scalar("SELECT MAX(legacy_user_id) FROM pkg_business_id_reservation")));
            assertEquals("4", f.scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_user_identity' AND column_name IN ('status','source','created_at','updated_at') AND is_nullable='NO'"));
            f.execute("INSERT INTO pkg_business_identity_audit(operator_id,operator_name,target_user_id,tenant_id,legacy_user_id,action,before_state,after_state,reason) VALUES('operator','Admin','mapped',1000,7,'STATUS','ACTIVE','DISABLED','test')");
            assertEquals("1", f.scalar("SELECT COUNT(*) FROM pkg_business_identity_audit WHERE created_at IS NOT NULL"));
            assertEquals("tenant_id,target_user_id,created_at", f.scalar("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='pkg_business_identity_audit' AND index_name='idx_pkg_identity_audit_target'"));
        }
    }

    @Test void duplicateAndInvalidMappingsFailBeforeAnySchemaOrDataMutation() throws Exception {
        for (String rows : List.of("(1,'same',1000),(2,'same',1000)", "(1,'a',1000),(1,'b',1001)",
                "(0,'a',1000)", "(-1,'a',1000)", "(NULL,'a',1000)", "(1,'',1000)", "(1,'   ',1000)", "(1,NULL,1000)", "(1,'a',0)", "(1,'a',NULL)")) {
            try (Fixture f = new Fixture()) {
                f.execute("INSERT INTO pkg_user_identity VALUES" + rows);
                String before = f.scalar("SELECT COUNT(*) FROM pkg_user_identity");
                SQLException failure = assertThrows(SQLException.class, f::migrate, rows);
                assertEquals("45000", failure.getSQLState(), rows);
                assertEquals(before, f.scalar("SELECT COUNT(*) FROM pkg_user_identity"));
                assertEquals("0", f.scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_user_identity' AND column_name='status'"));
                assertEquals("0", f.scalar("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('pkg_business_id_reservation','pkg_business_id_allocator','pkg_business_identity_audit')"));
            }
        }
    }

    @Test void emptyAllocatorStartsAtApprovedRangeAndMenuUsesCanonicalRootWithoutGrants() throws Exception {
        try (Fixture f = new Fixture()) {
            f.migrate();
            f.execute("INSERT INTO pkg_business_id_reservation(legacy_user_id,source) VALUES(9223372036854775807,'LEGACY')");
            f.execute("INSERT INTO pkg_business_id_allocator() VALUES()");
            assertEquals("1000000000000", f.scalar("SELECT id FROM pkg_business_id_allocator"));
            assertEquals("1", f.scalar("SELECT COUNT(*) FROM pkg_business_id_allocator WHERE created_at IS NOT NULL"));
            try (Statement statement = f.connection.createStatement()) {
                statement.execute("CREATE TABLE " + f.prefix + "sys_permission LIKE sys_permission");
            }
            f.execute("INSERT INTO sys_permission(id,name,url,menu_type,del_flag) VALUES('canonical','System','/isystem',0,0)");
            String menu = resource("V20261109_05__business_identity_menu.sql");
            assertFalse(menu.toLowerCase(Locale.ROOT).matches("(?s).*insert\\s+into\\s+`?sys_role_permission.*"));
            f.run(menu);
            f.run(menu);
            assertEquals("canonical", f.scalar("SELECT parent_id FROM sys_permission WHERE id='pkg_identity_menu'"));
            assertEquals("/system/business-identity:system/businessIdentity/index", f.scalar("SELECT CONCAT(url,':',component) FROM sys_permission WHERE id='pkg_identity_menu'"));
            assertEquals("4", f.scalar("SELECT COUNT(*) FROM sys_permission WHERE parent_id='pkg_identity_menu' AND id IN ('pkg_identity_query','pkg_identity_open','pkg_identity_bind','pkg_identity_status') AND perms IN ('package:identity:query','package:identity:open','package:identity:bind','package:identity:status')"));
        }
    }

    @Test void exhaustedLegacyReservationCounterIsRemovedWithoutAffectingIndependentAllocator() throws Exception {
        try (Fixture f = new Fixture()) {
            f.execute("CREATE TABLE pkg_business_id_reservation(legacy_user_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,source VARCHAR(16) NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) AUTO_INCREMENT=1000000000000");
            f.execute("INSERT INTO pkg_business_id_reservation(legacy_user_id,source) VALUES(9223372036854775807,'LEGACY')");
            String created = f.scalar("SELECT created_at FROM pkg_business_id_reservation");
            f.execute("INSERT INTO pkg_app_event VALUES(9223372036854775807)");
            f.migrate();
            f.migrate();
            assertEquals("", f.scalar("SELECT extra FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_business_id_reservation' AND column_name='legacy_user_id'"));
            assertEquals("LEGACY", f.scalar("SELECT source FROM pkg_business_id_reservation"));
            assertEquals(created, f.scalar("SELECT created_at FROM pkg_business_id_reservation"));
            f.execute("INSERT INTO pkg_business_id_allocator() VALUES()");
            assertEquals("1000000000000", f.scalar("SELECT id FROM pkg_business_id_allocator"));
            f.execute("INSERT INTO pkg_business_id_reservation(legacy_user_id,source) SELECT id,'NEW' FROM pkg_business_id_allocator");
            assertEquals("2", f.scalar("SELECT COUNT(*) FROM pkg_business_id_reservation"));
        }
    }

    @Test void historicalMaximumDoesNotRaiseAllocatorAndRerunPreservesGeneratedSequence() throws Exception {
        try (Fixture f = new Fixture()) {
            f.execute("CREATE TABLE pkg_business_id_allocator(id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) AUTO_INCREMENT=7");
            f.execute("INSERT INTO pkg_business_id_allocator() VALUES()");
            f.execute("INSERT INTO pkg_app_manager VALUES(9223372036854775807)");
            f.migrate();
            assertEquals("HISTORICAL", f.scalar("SELECT source FROM pkg_business_id_reservation WHERE legacy_user_id=9223372036854775807"));
            f.execute("INSERT INTO pkg_business_id_allocator() VALUES()");
            assertEquals("1000000000000", f.scalar("SELECT MAX(id) FROM pkg_business_id_allocator"));
            f.migrate();
            f.execute("INSERT INTO pkg_business_id_allocator() VALUES()");
            assertEquals("1000000000001", f.scalar("SELECT MAX(id) FROM pkg_business_id_allocator"));
        }
    }

    private static String resource(String name) throws Exception {
        try (var stream = PackageBusinessIdentityMigrationTest.class.getResourceAsStream("/db/package/" + name)) {
            assertNotNull(stream, "Missing migration " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test void legacyStatisticsIntAcceptsGeneratedIdentityAfterRepeatableWidening() throws Exception {
        try (Fixture f = new Fixture()) {
            // 001-schema.sql: statistics alone still uses INT NULL; the other five use BIGINT NULL.
            f.execute("ALTER TABLE pkg_app_statistics MODIFY COLUMN user_id INT NULL DEFAULT NULL");
            f.execute("INSERT INTO pkg_app_statistics VALUES(2147483647),(NULL),(-1)");
            SQLException before = assertThrows(SQLException.class,
                    () -> f.execute("INSERT INTO pkg_app_statistics VALUES(1000000000000)"));
            assertEquals("22001", before.getSQLState());
            f.migrate();
            String definition = f.scalar("SELECT CONCAT(data_type,':',is_nullable,':',COALESCE(column_default,'NULL')) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_app_statistics' AND column_name='user_id'");
            assertEquals("bigint:YES:NULL", definition);
            f.execute("INSERT INTO pkg_business_id_allocator() VALUES()");
            f.execute("INSERT INTO pkg_app_statistics(user_id) SELECT id FROM pkg_business_id_allocator");
            f.migrate();
            assertEquals(definition, f.scalar("SELECT CONCAT(data_type,':',is_nullable,':',COALESCE(column_default,'NULL')) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_app_statistics' AND column_name='user_id'"));
            assertEquals("1", f.scalar("SELECT COUNT(*) FROM pkg_app_statistics WHERE user_id=1000000000000"));
            assertEquals("1", f.scalar("SELECT COUNT(*) FROM pkg_app_statistics WHERE user_id=2147483647"));
            assertEquals("1", f.scalar("SELECT COUNT(*) FROM pkg_app_statistics WHERE user_id IS NULL"));
            assertEquals("1", f.scalar("SELECT COUNT(*) FROM pkg_app_statistics WHERE user_id=-1"));
            assertEquals("HISTORICAL", f.scalar("SELECT source FROM pkg_business_id_reservation WHERE legacy_user_id=2147483647"));
            assertEquals("6", f.scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('pkg_app_manager','pkg_app_statistics','pkg_load_count','pkg_pwa','pkg_notification','pkg_app_event') AND column_name='user_id' AND data_type='bigint' AND is_nullable='YES' AND column_default IS NULL"));
        }
    }

    @Test void statisticsWideningPreservesNonNullDefaultAndColumnComment() throws Exception {
        try (Fixture f = new Fixture()) {
            f.execute("ALTER TABLE pkg_app_statistics MODIFY COLUMN user_id INT NOT NULL DEFAULT 7 COMMENT 'legacy statistics owner'");
            f.execute("INSERT INTO pkg_app_statistics() VALUES()");
            f.migrate();
            f.migrate();
            assertEquals("bigint:NO:7:legacy statistics owner", f.scalar("SELECT CONCAT(data_type,':',is_nullable,':',column_default,':',column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='pkg_app_statistics' AND column_name='user_id'"));
            f.execute("INSERT INTO pkg_app_statistics() VALUES()");
            f.execute("INSERT INTO pkg_app_statistics VALUES(1000000000000)");
            assertEquals("2", f.scalar("SELECT COUNT(*) FROM pkg_app_statistics WHERE user_id=7"));
        }
    }

    // Unique persistent fixture names let information_schema inspect real MySQL DDL without touching shared tables.
    private static class Fixture implements AutoCloseable {
        final Connection connection = localConnection();
        final String prefix = "bi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "_";
        final List<String> names = new ArrayList<>(BUSINESS);
        Fixture() throws Exception {
            names.addAll(List.of("pkg_user_identity", "pkg_business_id_reservation", "pkg_business_id_allocator", "pkg_business_identity_audit", "sys_permission"));
            execute("CREATE TABLE pkg_user_identity(legacy_user_id BIGINT,jeecg_user_id VARCHAR(64),tenant_id INT)");
            for (String table : BUSINESS) execute("CREATE TABLE " + table + "(user_id BIGINT)");
        }
        String replace(String sql) {
            for (String name : names) sql = sql.replace(name, prefix + name);
            return sql.replace("migrate_pkg_business_accounts", prefix + "migrate_accounts")
                    .replace("migrate_pkg_business_identity_menu", prefix + "migrate_menu");
        }
        void execute(String sql) throws SQLException {
            try (Statement statement = connection.createStatement()) { statement.execute(replace(sql)); }
        }
        String scalar(String sql) throws SQLException {
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(replace(sql))) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
        void migrate() throws Exception { run(resource(MIGRATION)); }
        void run(String sql) throws SQLException {
            String delimiter = ";";
            StringBuilder pending = new StringBuilder();
            for (String line : sql.split("\\R")) {
                if (line.stripLeading().startsWith("--") || line.isBlank()) continue;
                if (line.stripLeading().toUpperCase(Locale.ROOT).startsWith("DELIMITER ")) {
                    delimiter = line.trim().substring(10).trim();
                    continue;
                }
                pending.append(line).append('\n');
                if (line.stripTrailing().endsWith(delimiter)) {
                    String command = pending.toString().trim();
                    execute(command.substring(0, command.length() - delimiter.length()));
                    pending.setLength(0);
                }
            }
            assertTrue(pending.toString().isBlank(), "Unterminated migration statement");
        }
        @Override public void close() throws SQLException {
            try {
                for (String name : names) execute("DROP TABLE IF EXISTS " + name);
                execute("DROP PROCEDURE IF EXISTS migrate_pkg_business_accounts");
                execute("DROP PROCEDURE IF EXISTS migrate_pkg_business_identity_menu");
            } finally { connection.close(); }
        }
    }
}
