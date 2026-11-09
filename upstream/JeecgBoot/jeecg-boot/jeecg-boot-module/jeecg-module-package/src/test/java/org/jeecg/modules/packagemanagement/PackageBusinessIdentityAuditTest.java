package org.jeecg.modules.packagemanagement;

import java.net.URI;
import java.util.*;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.packagemanagement.application.identity.PackageBusinessIdentityService;
import org.jeecg.modules.packagemanagement.controller.PackageJson;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PackageBusinessIdentityAuditTest {
    @Test void nonemptyAuditSerializesThroughPackageCodecWithFormattedCreatedAt() {
        String url=System.getenv("PACKAGE_TEST_MYSQL_URL"),user=System.getenv("PACKAGE_TEST_MYSQL_USER"),password=System.getenv("PACKAGE_TEST_MYSQL_PASSWORD");
        assumeTrue(url!=null && user!=null && password!=null,"local package MySQL is not configured");
        URI target=URI.create(url.substring(5));
        assertEquals("127.0.0.1",target.getHost()); assertEquals(13317,target.getPort());
        assertEquals("/package_migration_test",target.getPath()); assertEquals("package_test",user);
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        var service=new PackageBusinessIdentityService(jdbc,new TransactionTemplate());
        String marker="audit-test-"+UUID.randomUUID(); int tenant=799999991;
        try {
            jdbc.update("INSERT INTO pkg_business_identity_audit(operator_id,operator_name,target_user_id,tenant_id,legacy_user_id,action,before_state,after_state,reason,created_at)"
                    +" VALUES (?,?,?,?,123,'OPEN','UNOPENED','ACTIVE','serialization regression','2026-11-09 08:07:06')",marker,marker,marker,tenant);
            var page=service.audit(tenant,marker,1,10);
            assertEquals(1,page.total());
            Object converted=assertDoesNotThrow(()->PackageJson.mapper().convertValue(Result.ok(page),Object.class));
            assertEquals("2026-11-09 08:07:06",page.records().get(0).get("createdAt"));
            Map<?,?> result=(Map<?,?>)((Map<?,?>)converted).get("result");
            Map<?,?> serializedRow=(Map<?,?>)((List<?>)result.get("records")).get(0);
            assertEquals("2026-11-09 08:07:06",serializedRow.get("createdAt"));
            assertEquals("123",serializedRow.get("legacyUserId"));
        } finally {
            jdbc.update("DELETE FROM pkg_business_identity_audit WHERE operator_id=? AND target_user_id=?",marker,marker);
        }
    }
}
