package org.jeecg.modules.packagemanagement.config;
import org.springframework.context.annotation.*;import org.springframework.boot.context.properties.EnableConfigurationProperties;import org.mybatis.spring.annotation.MapperScan;
import org.jeecg.modules.packagemanagement.application.port.*;import org.jeecg.modules.packagemanagement.infrastructure.integration.*;import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
@Configuration(proxyBeanMethods=false)
@PropertySource("classpath:package-management.properties")
@EnableConfigurationProperties(PackageManagementProperties.class)
@MapperScan("org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper")
@ComponentScan(basePackages="org.jeecg.modules.packagemanagement.application",useDefaultFilters=false,
 includeFilters=@ComponentScan.Filter(org.springframework.stereotype.Service.class))
public class PackageManagementConfiguration {
 @Bean public java.time.Clock packageClock(){return java.time.Clock.system(java.time.ZoneId.of("Asia/Shanghai"));}
 @Bean public PackageSnowflake packageSnowflake(){return new PackageSnowflake();}
 @Bean public HttpPort packageHttpPort(){return new JdkHttpAdapter();}
 @Bean public GeoPort packageGeoPort(HttpPort http,PackageManagementProperties settings){return new GeoHttpAdapter(http,settings);}
 @Bean public ElasticsearchAppLogAdapter packageAppLogPort(HttpPort http,PackageManagementProperties settings){return new ElasticsearchAppLogAdapter(http,settings);}
 @Bean public org.jeecg.modules.packagemanagement.application.applog.EsAppLogServiceImpl packageAppLogService(ElasticsearchAppLogAdapter adapter){return new org.jeecg.modules.packagemanagement.application.applog.EsAppLogServiceImpl(adapter);}
 @Bean public StoreProbePort packageStoreProbe(HttpPort http){return url->{try{return http.exchange("GET",url,null,java.util.Map.of()).success();}catch(RuntimeException e){return false;}};}
 @Bean public MailPort packageMailPort(PackageManagementProperties settings){return new SmtpMailAdapter(settings);}
 @Bean public PwaGateway packagePwaGateway(HttpPort http,PackageManagementProperties settings){return new NpmPwaAdapter(http,settings);}
 @Bean public FacebookPort packageFacebookPort(HttpPort http,PackageManagementProperties settings,@org.springframework.beans.factory.annotation.Qualifier("packageClock") java.time.Clock clock){return new FacebookHttpAdapter(http,settings,clock);}
 @Bean public PushPort packagePushPort(PackageManagementProperties settings){return new WebPushAdapter(settings);}
 @Bean public PackageIdentityRepository packageIdentityRepository(org.springframework.jdbc.core.JdbcTemplate jdbc){return new PackageIdentityRepository(jdbc);}
 @Bean public PackageAccess packageAccess(PackageIdentityRepository repository){return new PackageAccess(repository);}
 @Bean public org.jeecg.modules.packagemanagement.job.PackageStoreJob packageStoreJob(org.jeecg.modules.packagemanagement.application.store.IStoreStatusService service){return new org.jeecg.modules.packagemanagement.job.PackageStoreJob(service);}
}
