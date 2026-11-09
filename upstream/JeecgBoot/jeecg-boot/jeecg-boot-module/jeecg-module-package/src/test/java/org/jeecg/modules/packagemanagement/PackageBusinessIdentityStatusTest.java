package org.jeecg.modules.packagemanagement;

import java.sql.Connection;
import java.util.*;
import org.jeecg.modules.packagemanagement.controller.admin.*;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackageBusinessIdentityStatusTest {
    @Test void resolutionPreservesAmbiguityAcrossDisabledRows() throws Exception {
        try (Connection connection = PackageBusinessIdentityMigrationTest.localConnection()) {
            JdbcTemplate jdbc = fixture(connection);
            PackageIdentityRepository repository = new PackageIdentityRepository(jdbc);
            PackageAccess access = new PackageAccess(repository);
            assertEquals(11L, access.resolve("active", "1000", false).legacyUserId());
            assertEquals(List.of(), repository.legacyIds(1000, "disabled"));
            assertThrows(PackageFailure.class, () -> access.resolve("disabled", "1000", false));
            assertEquals(List.of(), repository.legacyIds(1000, "invalid"));
            assertEquals(2, repository.legacyIds(1000, "mixed").size());
            assertEquals(2, repository.legacyIds(1000, "duplicates").size());
            assertEquals(2, repository.legacyIds(1000, "both-disabled").size());
            for (String user : List.of("mixed", "duplicates", "both-disabled", "invalid", "missing")) {
                assertThrows(PackageFailure.class, () -> access.resolve(user, "1000", false), user);
            }
            assertEquals(List.of(), repository.legacyIds(1001, "active"));
        }
    }

    @Test void transferAndPickerRequireOneActiveMappingIncludingIdenticalDuplicates() throws Exception {
        try (Connection connection = PackageBusinessIdentityMigrationTest.localConnection()) {
            JdbcTemplate jdbc = fixture(connection);
            PackageIdentityRepository repository = new PackageIdentityRepository(jdbc);
            PackageActor actor = new PackageActor(1000, "active", 11L, true);
            PackageAdminSupport guard = mock(PackageAdminSupport.class);
            when(guard.actor("apps", "transfer")).thenReturn(actor);
            var controller = new PackageAdminActionsController(null, guard, new PackageAccess(repository), null, jdbc);
            var targets = controller.targets().getResult();
            assertEquals(Set.of("active"), targets.stream().map(row -> row.get("id").toString())
                    .collect(java.util.stream.Collectors.toSet()));
            for (String user : List.of("disabled", "mixed", "duplicates", "both-disabled", "invalid", "missing")) {
                assertThrows(PackageFailure.class, () -> repository.transfer(actor, "pkg_app_manager", List.of(1L), user), user);
                assertEquals("original", jdbc.queryForObject("SELECT owner_user_id FROM pkg_resource_owner", String.class));
                assertEquals(99L, jdbc.queryForObject("SELECT user_id FROM pkg_app_manager", Long.class));
            }
            repository.transfer(actor, "pkg_app_manager", List.of(1L), "active");
            assertEquals("active", jdbc.queryForObject("SELECT owner_user_id FROM pkg_resource_owner", String.class));
            assertEquals(11L, jdbc.queryForObject("SELECT user_id FROM pkg_app_manager", Long.class));
        }
    }

    private JdbcTemplate fixture(Connection connection) {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        jdbc.execute("CREATE TEMPORARY TABLE pkg_user_identity(legacy_user_id BIGINT,tenant_id INT,jeecg_user_id VARCHAR(64),status VARCHAR(16))");
        jdbc.execute("CREATE TEMPORARY TABLE sys_user(id VARCHAR(64),username VARCHAR(64),realname VARCHAR(64),status INT,del_flag INT)");
        jdbc.execute("CREATE TEMPORARY TABLE sys_tenant(id INT,status INT,del_flag INT,begin_date DATETIME,end_date DATETIME)");
        jdbc.execute("CREATE TEMPORARY TABLE sys_user_tenant(user_id VARCHAR(64),tenant_id INT,status VARCHAR(16))");
        jdbc.execute("CREATE TEMPORARY TABLE pkg_resource_owner(resource_type VARCHAR(64),resource_id BIGINT,tenant_id INT,owner_user_id VARCHAR(64))");
        jdbc.execute("CREATE TEMPORARY TABLE pkg_app_manager(id BIGINT,user_id BIGINT)");
        jdbc.update("INSERT INTO sys_tenant VALUES(1000,1,0,NULL,NULL)");
        for (String user : List.of("active", "disabled", "mixed", "duplicates", "both-disabled", "invalid", "missing")) {
            jdbc.update("INSERT INTO sys_user VALUES(?,?,?,1,0)", user, user, user);
            jdbc.update("INSERT INTO sys_user_tenant VALUES(?,1000,'1')", user);
        }
        jdbc.update("INSERT INTO pkg_user_identity VALUES(11,1000,'active','ACTIVE'),(12,1000,'disabled','DISABLED'),"
                + "(13,1000,'mixed','ACTIVE'),(14,1000,'mixed','DISABLED'),"
                + "(15,1000,'duplicates','ACTIVE'),(15,1000,'duplicates','ACTIVE'),"
                + "(16,1000,'both-disabled','DISABLED'),(17,1000,'both-disabled','DISABLED'),(0,1000,'invalid','ACTIVE')");
        jdbc.update("INSERT INTO pkg_resource_owner VALUES('pkg_app_manager',1,1000,'original')");
        jdbc.update("INSERT INTO pkg_app_manager VALUES(1,99)");
        return jdbc;
    }
}
