package org.jeecg.modules.packagemanagement;
import java.net.URI;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.jeecg.modules.packagemanagement.domain.openapi.*;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
class PackageOpenApiMySqlIntegrationTest {
 @Test void schemaIsIdempotentAndRealMappersRoundTripWithTenantIsolation()throws Exception {
  String url=System.getenv("PACKAGE_TEST_MYSQL_URL"),user=System.getenv("PACKAGE_TEST_MYSQL_USER"),password=System.getenv("PACKAGE_TEST_MYSQL_PASSWORD");
  assumeTrue(url!=null&&user!=null&&password!=null,"isolated local MySQL not configured");
  URI target=URI.create(url.substring("jdbc:".length()));assertEquals("127.0.0.1",target.getHost());assertEquals(13317,target.getPort());assertEquals("/package_migration_test",target.getPath());assertEquals("package_test",user);
  var datasource=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password);
  try(var conn=datasource.getConnection()){
   for(int i=0;i<2;i++)org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(conn,new org.springframework.core.io.ClassPathResource("db/package/010-dev-openapi.sql"));
  }
  var configuration=new Configuration();configuration.setEnvironment(new Environment("openapi-test",new JdbcTransactionFactory(),datasource));
  for(String name:new String[]{"AppManagerMapper","OpenApiTokenMapper","OpenApiCallLogMapper","OpenApiManagerMapper"}){
   String resource="org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/xml/"+name+".xml";
   try(var input=getClass().getClassLoader().getResourceAsStream(resource)){assertNotNull(input);new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();}
  }
  try(var session=new SqlSessionFactoryBuilder().build(configuration).openSession(false)){
   try{
    var tokens=session.getMapper(OpenApiTokenMapper.class);var row=new OpenApiToken();row.setToken(UUID.randomUUID().toString().replace("-","").toLowerCase());row.setTenantId(1);row.setOwnerUserId("openapi-test");row.setStatus(1);row.setCreateTime(new Date());assertEquals(1,tokens.insert(row));assertNotNull(row.getId());
    assertNotNull(tokens.byToken(row.getToken()));assertNull(tokens.byToken(row.getToken().toUpperCase()));assertEquals("openapi-test",tokens.byId(row.getId()).getOwnerUserId());
    var query=new OpenApiToken();query.setTenantId(2);query.setToken(row.getToken());assertTrue(tokens.list(query).isEmpty());query.setTenantId(1);query.setOwnerUserId("other");assertTrue(tokens.list(query).isEmpty());query.setOwnerUserId("openapi-test");assertEquals(1,tokens.list(query).size());
    row.setStatus(0);assertEquals(1,tokens.update(row));assertEquals(0,tokens.byId(row.getId()).getStatus());
    var logs=session.getMapper(OpenApiCallLogMapper.class);var log=new OpenApiCallLog();log.setApiPath("/openapi/manager/list");log.setRequestBody("{}");log.setResponseBody("{}");log.setCallTime(new Date());log.setToken(row.getToken());log.setTokenId(row.getId());log.setTenantId(1);log.setOwnerUserId("openapi-test");assertEquals(1,logs.insert(log));assertNotNull(log.getId());
    var lq=new OpenApiCallLog();lq.setTenantId(1);lq.setOwnerUserId("openapi-test");lq.setToken(row.getToken());assertEquals(1,logs.list(lq).size());lq.setTenantId(2);assertTrue(logs.list(lq).isEmpty());
    var app=new AppManager();app.setPackageCode("openapi-it-"+row.getToken());app.setUserId(row.getId());app.setUserName("api");assertEquals(1,session.getMapper(AppManagerMapper.class).insertAppManager(app));var locked=session.getMapper(OpenApiManagerMapper.class).lockById(app.getId());assertNotNull(locked);assertEquals(row.getId(),locked.getUserId());assertEquals("api",locked.getUserName());
    assertEquals(0,tokens.delete(row.getId(),2));assertEquals(1,tokens.delete(row.getId(),1));
   }finally{session.rollback(true);}
  }
 }
}
