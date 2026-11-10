package org.jeecg.modules.packagemanagement;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.jeecg.modules.packagemanagement.domain.openapi.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PackageOpenApiPersistenceTest {
 private final String root="org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/xml/";
 @Test void mapperStatementsBindTenantAndOwnerAndDoNotInterpolateSql()throws Exception {
  var configuration=new Configuration();for(String name:new String[]{"AppManagerMapper","OpenApiTokenMapper","OpenApiCallLogMapper","OpenApiManagerMapper"}){
   String resource=root+name+".xml";try(var input=getClass().getClassLoader().getResourceAsStream(resource)){assertNotNull(input);new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();}
  }
  String ns="org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.";
  var query=new OpenApiToken();query.setTenantId(1);query.setOwnerUserId("u1");query.setToken("unsafe' OR 1=1 --");
  var tokenSql=configuration.getMappedStatement(ns+"OpenApiTokenMapper.list").getBoundSql(query);assertTrue(tokenSql.getSql().contains("tenant_id=?"));assertTrue(tokenSql.getSql().contains("owner_user_id=?"));assertFalse(tokenSql.getSql().contains("unsafe"));
  var log=new OpenApiCallLog();log.setTenantId(1);log.setOwnerUserId("u1");log.setBeginTime("2026-11-01");log.setEndTime("2026-11-10");var logSql=configuration.getMappedStatement(ns+"OpenApiCallLogMapper.list").getBoundSql(log);assertTrue(logSql.getSql().contains("DATE(call_time) >= ?"));assertTrue(logSql.getSql().contains("DATE(call_time) <= ?"));
  String locked=configuration.getMappedStatement(ns+"OpenApiManagerMapper.lockById").getBoundSql(Map.of("id",1L)).getSql();assertTrue(locked.endsWith("FOR UPDATE"));
  String store=configuration.getMappedStatement(ns+"OpenApiManagerMapper.lockStoreTenant").getBoundSql(Map.of("id",1L,"tenant",1)).getSql();assertTrue(store.contains("tenant_id=?"));assertTrue(store.contains("pkg_store_status"));
 }
}
