package org.jeecg.modules.packagemanagement;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.jeecg.modules.packagemanagement.application.apps.risk.app2.*;
import org.jeecg.modules.packagemanagement.domain.app.*;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppManagerMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.jeecg.modules.packagemanagement.PackageBusinessTest.inject;

class PackageFourPathIntegrationTest {
    private static final String XML="org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/xml/";
    private static final String JAVA="org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.";

    @Test void riskConfigurationAndFourBlacklistMappersRoundTripOnLocalMySql() throws Exception {
        String url=System.getenv("PACKAGE_TEST_MYSQL_URL"),user=System.getenv("PACKAGE_TEST_MYSQL_USER"),password=System.getenv("PACKAGE_TEST_MYSQL_PASSWORD");
        assumeTrue(url!=null && user!=null && password!=null,"local disposable MySQL is not configured");
        URI target=URI.create(url.substring("jdbc:".length()));
        assertEquals("127.0.0.1",target.getHost()); assertEquals(13317,target.getPort());
        assertEquals("/package_migration_test",target.getPath()); assertEquals("package_test",user);
        var ds=new UnpooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password);
        // Existing installation migration must be repeatable, including prepared ALTER statements.
        try(var connection=ds.getConnection()) {
            var runner=new ScriptRunner(connection); runner.setLogWriter(null); runner.setErrorLogWriter(null); runner.setStopOnError(true);
            for(int i=0;i<2;i++) try(var input=getClass().getClassLoader().getResourceAsStream("db/package/008-four-path-risk.sql")) {
                assertNotNull(input); runner.runScript(new InputStreamReader(input,StandardCharsets.UTF_8));
            }
        }
        Configuration config=new Configuration(); config.setEnvironment(new Environment("package-v4-test",new JdbcTransactionFactory(),ds));
        for(String name:List.of("AppManager","AppAsnBlacklist","AppDeviceBrandBlacklist","AppCpuAbiBlacklist","AppDeviceNameBlacklist")) {
            String resource=XML+name+"Mapper.xml";
            try(var input=getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input); new XMLMapperBuilder(input,config,resource,config.getSqlFragments()).parse();
            }
        }
        try(var session=new SqlSessionFactoryBuilder().build(config).openSession(false)) {
            try {
                AppManager app=new AppManager(); app.setPackageCode("v4-test-"+UUID.randomUUID());
                app.setGaidAidThreshold(2); app.setAidDeviceThreshold(3); app.setAidGaidSwitchThreshold(4);
                app.setRiskWindowSeconds(120); app.setReferrerBlacklistJson("{\"flag\":1}");
                var managers=session.getMapper(AppManagerMapper.class); assertEquals(1,managers.insertAppManager(app));
                AppManager read=managers.selectAppManagerByPackageName(app.getPackageCode());
                assertEquals(2,read.getGaidAidThreshold()); assertEquals(3,read.getAidDeviceThreshold());
                assertEquals(4,read.getAidGaidSwitchThreshold()); assertEquals(120,read.getRiskWindowSeconds());
                assertEquals(app.getReferrerBlacklistJson(),read.getReferrerBlacklistJson());
                app.setGaidAidThreshold(5); app.setAidDeviceThreshold(6); app.setAidGaidSwitchThreshold(7);
                app.setRiskWindowSeconds(240); app.setReferrerBlacklistJson(""); managers.updateAppManager(app);
                read=managers.selectAppManagerById(app.getId());
                assertEquals(5,read.getGaidAidThreshold()); assertEquals(6,read.getAidDeviceThreshold());
                assertEquals(7,read.getAidGaidSwitchThreshold()); assertEquals(240,read.getRiskWindowSeconds()); assertEquals("",read.getReferrerBlacklistJson());
                for(var entry:Map.of("AppAsnBlacklist","asn","AppDeviceBrandBlacklist","brandKeyword",
                        "AppCpuAbiBlacklist","abiKeyword","AppDeviceNameBlacklist","deviceNameKeyword").entrySet()) {
                    String name=entry.getKey(),field=entry.getValue();
                    Class<?> type=Class.forName("org.jeecg.modules.packagemanagement.domain.app."+name);
                    Object row=type.getConstructor().newInstance();
                    type.getMethod("set"+Character.toUpperCase(field.charAt(0))+field.substring(1),String.class).invoke(row,"123");
                    type.getMethod("setEnabled",Integer.class).invoke(row,1); type.getMethod("setPriority",Integer.class).invoke(row,999);
                    Class<?> mapperType=Class.forName(JAVA+name+"Mapper"); Object mapper=session.getMapper(mapperType);
                    assertEquals(1,mapperType.getMethod("insert"+name,type).invoke(mapper,row));
                    Long id=(Long)type.getMethod("getId").invoke(row); assertNotNull(id);
                    assertNotNull(mapperType.getMethod("select"+name+"ById",Long.class).invoke(mapper,id));
                    List<?> rows=(List<?>)mapperType.getMethod("select"+name+"List",type).invoke(mapper,row);
                    assertTrue(rows.stream().anyMatch(value->{try{return id.equals(type.getMethod("getId").invoke(value));}catch(Exception e){throw new AssertionError(e);}}));
                    type.getMethod("setEnabled",Integer.class).invoke(row,0);
                    assertEquals(1,mapperType.getMethod("update"+name,type).invoke(mapper,row));
                    assertEquals(1,mapperType.getMethod("delete"+name+"ById",Long.class).invoke(mapper,id));
                }
            } finally { session.rollback(true); }
        }
    }

    @Test void redisCountsThresholdsExpiryIsolationAndUnblockOnLocalRedis() {
        assumeTrue("true".equals(System.getenv("PACKAGE_TEST_REDIS")),"local disposable Redis is not configured");
        var factory=new LettuceConnectionFactory("127.0.0.1",16390); factory.afterPropertiesSet();
        var template=new StringRedisTemplate(factory); template.afterPropertiesSet();
        var service=new App2PkgRiskRedisService(); inject(service,"redisTemplate",template);
        var app=new AppManager(); String pkg="v4-test-"+UUID.randomUUID(); app.setPackageCode(pkg); app.setRiskWindowSeconds(120);
        String prefix=App2RiskConstants.REDIS_PREFIX+"pkg:"+pkg+":";
        try {
            assertTrue(service.evaluate(app,device("a1","g1","Phone")).isPass());
            assertTrue(service.evaluate(app,device("a1","g1","Phone")).isPass(),"repeat pair does not increase distinct count");
            assertFalse(service.evaluate(app,device("a2","g1","Phone")).isPass());
            assertEquals(1,service.listAutoBlocked(pkg,"gaid").size());
            Long ttl=template.getExpire(prefix+"block:gaid:g1",TimeUnit.SECONDS); assertTrue(ttl>0 && ttl<=120);
            assertTrue(service.removeAutoBlock(pkg,"gaid","g1"));
            assertTrue(service.evaluate(app,device("a3","g1","Phone")).isPass(),"unblock clears tracked aids");
            assertFalse(service.evaluate(app,device("a3","g2","Other Phone")).isPass(),"device count threshold exceeded");
            assertTrue(service.removeAutoBlock(pkg,"aid","a3"));
            app.setAidDeviceThreshold(10); app.setGaidAidThreshold(10);
            assertTrue(service.evaluate(app,device("a4","g3","Phone")).isPass());
            assertTrue(service.evaluate(app,device("a4","g4","Phone")).isPass());
            assertFalse(service.evaluate(app,device("a4","g5","Phone")).isPass(),"second switch exceeds default 1");
            assertTrue(service.removeAutoBlock(pkg,"aid","a4"));
            assertTrue(service.evaluate(app,device("a4","g5","Phone")).isPass());
            app.setRiskWindowSeconds(1); assertTrue(service.evaluate(app,device("new","new","Phone")).isPass());
            ttl=template.getExpire(prefix+"aid:new:last_gaid",TimeUnit.SECONDS); assertTrue(ttl>86300 && ttl<=86400);
            app.setPackageCode(pkg+"-other"); assertTrue(service.evaluate(app,device("a2","g1","Phone")).isPass());
        } finally {
            Set<String> keys=template.keys(App2RiskConstants.REDIS_PREFIX+"pkg:"+pkg+"*"); if(keys!=null&&!keys.isEmpty()) template.delete(keys);
            factory.destroy();
        }
    }
    private App2DevicePayload device(String aid,String gaid,String name) {
        return new App2DevicePayload("samsung","arm64-v8a",name,aid,gaid,"","",null);
    }
}
