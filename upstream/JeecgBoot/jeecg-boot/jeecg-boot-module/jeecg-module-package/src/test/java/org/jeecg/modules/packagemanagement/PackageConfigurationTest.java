package org.jeecg.modules.packagemanagement;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.MapPropertySource;
import org.jeecg.modules.packagemanagement.config.PackageManagementConfiguration;
import org.jeecg.modules.packagemanagement.config.PackageManagementProperties;
import org.jeecg.modules.packagemanagement.application.apps.ClientService;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageAccess;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackageConfigurationTest {
 @Test void explicitImportStartsWithoutCircularDependencyOrExternalCalls()throws Exception{
  try(var context=new AnnotationConfigApplicationContext()){
   context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("PACKAGE_DEFAULT_AES_KEY","test-externalized-key")));
   context.registerBean(org.springframework.transaction.PlatformTransactionManager.class,()->mock(org.springframework.transaction.PlatformTransactionManager.class));
   context.registerBean(JdbcTemplate.class,()->mock(JdbcTemplate.class));
   for(String name:new String[]{"AppManager","AppStatistics","StoreStatus","EventConfig","EventError","EvnetHistory","LoadCount","AppPwa","AppPwaSub","AppNotify","AppEventRecord","ReviewsRecord","AppAsnBlacklist","AppDeviceBrandBlacklist","AppCpuAbiBlacklist","AppDeviceNameBlacklist","OpenApiCallLog","OpenApiToken","OpenApiManager","PackStat","PackStatRegion","PackStatDetectConfig","PackStatDetectHistory"}){
    Class<?> type=Class.forName("org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper."+name+"Mapper");
    register(context,Character.toLowerCase(name.charAt(0))+name.substring(1)+"Mapper",type);
   }
   context.registerBean(org.springframework.data.redis.core.StringRedisTemplate.class,()->mock(org.springframework.data.redis.core.StringRedisTemplate.class));
   context.scan("org.jeecg.modules.packagemanagement.application");
   context.register(PackageManagementConfiguration.class,org.jeecg.modules.packagemanagement.config.PackagePackStatConfiguration.class);context.refresh();
   assertNotNull(context.getBean(ClientService.class));assertNotNull(context.getBean(PackageAccess.class));
   assertEquals(1,context.getBeansOfType(ClientService.class).size());
   assertTrue(context.getBeansOfType(com.fasterxml.jackson.databind.ObjectMapper.class).isEmpty(),"Module must not register a host-wide Jackson mapper");
   assertEquals("test-externalized-key",context.getBean(PackageManagementProperties.class).getDefaultAesKey());
  }
 }
 private <T> void register(AnnotationConfigApplicationContext context,String name,Class<T> type){context.registerBean(name,type,()->mock(type));}
}
