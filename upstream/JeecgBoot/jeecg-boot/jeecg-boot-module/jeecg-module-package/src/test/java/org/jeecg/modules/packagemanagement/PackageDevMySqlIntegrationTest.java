package org.jeecg.modules.packagemanagement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import org.apache.ibatis.session.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStat;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.*;
class PackageDevMySqlIntegrationTest {
 @Test void packstatMapperAutoLoadsXmlAndRealDatabaseEnforcesSummaryAndScope()throws Exception{
  String url=System.getenv("PACKAGE_TEST_MYSQL_URL"),user=System.getenv("PACKAGE_TEST_MYSQL_USER"),password=System.getenv("PACKAGE_TEST_MYSQL_PASSWORD");assumeTrue(url!=null&&user!=null&&password!=null);
  assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:13317/package_migration_test"));assertEquals("package_test",user);
  var config=new Configuration();config.setEnvironment(new Environment("dev-local",new JdbcTransactionFactory(),new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password)));
  config.addMapper(PackStatMapper.class);config.addMapper(PackStatRegionMapper.class);config.addMapper(PackStatDetectConfigMapper.class);config.addMapper(PackStatDetectHistoryMapper.class);
  try(var session=new SqlSessionFactoryBuilder().build(config).openSession(false)){
   try{
    var mapper=session.getMapper(PackStatMapper.class);var row=new PackStat();row.setTenantId(88731);row.setOwnerUserId("dev-test");row.setPlatform("android");row.setStoreUrl("https://example.test/pack");row.setPacker("dev-test");row.setPassDate(java.sql.Date.valueOf("2026-11-01"));row.setCustomerUseDate(java.sql.Date.valueOf("2026-11-02"));row.setDropDate(java.sql.Date.valueOf("2026-11-09"));row.setSurviveDays(8);row.setUseDays(7);row.setIsReplenish(0);
    assertEquals(1,mapper.insertPackStat(row));assertNotNull(row.getId());assertEquals("dev-test",mapper.selectPackStatById(row.getId()).getOwnerUserId());
    var scope=new PackStat();scope.setTenantId(88732);assertTrue(mapper.selectPackStatList(scope).isEmpty());scope.setTenantId(88731);scope.setOwnerUserId("other");assertTrue(mapper.selectPackStatList(scope).isEmpty());scope.setOwnerUserId("dev-test");assertEquals(1,mapper.selectPackStatList(scope).size());
    var summary=mapper.selectSurviveSummary("dev-test","2026-11",88731,"dev-test");assertEquals(1,((Number)summary.get("s8")).intValue());assertEquals(0,((Number)summary.get("s7")).intValue());assertEquals(1,((Number)summary.get("u7")).intValue());
    row.setOwnerUserId("other");assertEquals(0,mapper.updatePackStat(row));
   }finally{session.rollback();}
  }
 }

 @Test void appOwnerChangesOnlyThroughDedicatedTransferStatement()throws Exception{
  var config=new Configuration();
  String resource="org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/xml/AppManagerMapper.xml";
  try(var input=getClass().getClassLoader().getResourceAsStream(resource)){
   assertNotNull(input,resource);
   new org.apache.ibatis.builder.xml.XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();
  }
  var row=new org.jeecg.modules.packagemanagement.domain.app.AppManager();row.setId(1L);row.setUserId(2L);row.setUserName("new-owner");row.setAppName("app");
  String base="org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppManagerMapper.";
  String update=config.getMappedStatement(base+"updateAppManager").getBoundSql(row).getSql();assertFalse(update.contains("user_id ="));assertFalse(update.contains("user_name ="));
  String transfer=config.getMappedStatement(base+"transferAppManagerById").getBoundSql(java.util.Map.of("id","com.test","userId",2,"userName","new-owner")).getSql();assertTrue(transfer.contains("user_id ="));assertTrue(transfer.contains("user_name ="));
 }
}
