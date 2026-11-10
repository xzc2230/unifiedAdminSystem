package org.jeecg.modules.packagemanagement;

import java.sql.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class PackageCountryRegionMigrationTest {
    @Test void migrationPreservesBackupsNormalizesLegacyDataAndIsRepeatable() throws Exception {
        String url=System.getenv("PACKAGE_TEST_MYSQL_URL"),user=System.getenv("PACKAGE_TEST_MYSQL_USER"),password=System.getenv("PACKAGE_TEST_MYSQL_PASSWORD");
        assumeTrue(url!=null && user!=null && password!=null);
        assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:13317/package_migration_test"));
        assertEquals("package_test",user);
        String pkg="country-migration-test-"+System.nanoTime();
        try(Connection connection=DriverManager.getConnection(url,user,password); Statement sql=connection.createStatement()) {
            try {
                sql.executeUpdate("INSERT INTO pkg_app_manager(package_code,areas) VALUES('"+pkg+"',' 巴西,BR,菲律賓, SG ,,未识别地区 ')");
                sql.executeUpdate("INSERT INTO pkg_app_statistics(package_code,sub_areas) VALUES('"+pkg+"',' 中國 ')");
                for(int i=0;i<2;i++) {
                    ScriptUtils.executeSqlScript(connection,new EncodedResource(new ClassPathResource("db/package/013-country-code-regions.sql"),StandardCharsets.UTF_8));
                    assertEquals("br,ph,sg,未识别地区",scalar(sql,"SELECT areas FROM pkg_app_manager WHERE package_code='"+pkg+"'"));
                    assertEquals("cn",scalar(sql,"SELECT sub_areas FROM pkg_app_statistics WHERE package_code='"+pkg+"'"));
                    assertEquals("49",scalar(sql,"SELECT COUNT(DISTINCT i.item_value) FROM sys_dict_item i JOIN sys_dict d ON d.id=i.dict_id WHERE d.dict_code='app_area' AND i.status=1"));
                }
                assertEquals(" 巴西,BR,菲律賓, SG ,,未识别地区 ",scalar(sql,"SELECT b.original_areas FROM pkg_country_area_backup b JOIN pkg_app_manager a ON b.resource_type='apps' AND b.resource_id=a.id WHERE a.package_code='"+pkg+"'"));
            } finally {
                sql.executeUpdate("DELETE b FROM pkg_country_area_backup b JOIN pkg_app_manager a ON b.resource_type='apps' AND b.resource_id=a.id WHERE a.package_code='"+pkg+"'");
                sql.executeUpdate("DELETE b FROM pkg_country_area_backup b JOIN pkg_app_statistics a ON b.resource_type='statistics' AND b.resource_id=a.id WHERE a.package_code='"+pkg+"'");
                sql.executeUpdate("DELETE FROM pkg_app_manager WHERE package_code='"+pkg+"'");
                sql.executeUpdate("DELETE FROM pkg_app_statistics WHERE package_code='"+pkg+"'");
            }
        }
    }
    private static String scalar(Statement sql,String query) throws SQLException {
        try(ResultSet rows=sql.executeQuery(query)) { assertTrue(rows.next());return rows.getString(1); }
    }
}
