package org.jeecg.modules.packagemanagement.config;
import org.springframework.context.annotation.*;import org.springframework.boot.context.properties.EnableConfigurationProperties;import org.mybatis.spring.annotation.MapperScan;
import org.jeecg.modules.packagemanagement.application.port.*;import org.jeecg.modules.packagemanagement.infrastructure.integration.*;import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
@Configuration(proxyBeanMethods=false)
@PropertySource("classpath:package-management.properties")
@EnableConfigurationProperties({PackageManagementProperties.class,RequestLogClickHouseProperties.class})
@MapperScan("org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper")
@ComponentScan(basePackages="org.jeecg.modules.packagemanagement.application",useDefaultFilters=false,
 includeFilters=@ComponentScan.Filter(org.springframework.stereotype.Service.class))
public class PackageManagementConfiguration {
 @Bean public org.springframework.transaction.support.TransactionTemplate packageTransactionTemplate(org.springframework.transaction.PlatformTransactionManager manager){return new org.springframework.transaction.support.TransactionTemplate(manager);}
 @Bean public java.time.Clock packageClock(){return java.time.Clock.system(java.time.ZoneId.of("Asia/Shanghai"));}
 @Bean public PackageSnowflake packageSnowflake(){return new PackageSnowflake();}
 @Bean public HttpPort packageHttpPort(){return new JdkHttpAdapter();}
 @Bean public GeoPort packageGeoPort(HttpPort http,PackageManagementProperties settings){return new GeoHttpAdapter(http,settings);}
 @Bean public ClickHouseAppLogAdapter packageAppLogPort(RequestLogClickHouseProperties clickHouse){return new ClickHouseAppLogAdapter(clickHouse);}
 @Bean public org.jeecg.modules.packagemanagement.application.applog.EsAppLogServiceImpl packageAppLogService(ClickHouseAppLogAdapter adapter,org.jeecg.common.api.CommonAPI dicts){return new org.jeecg.modules.packagemanagement.application.applog.EsAppLogServiceImpl(adapter,()->appAreaLabels(dicts));}
 private static java.util.Map<String,String> appAreaLabels(org.jeecg.common.api.CommonAPI dicts){
  var items=dicts.queryEnableDictItemsByCode("app_area");var labels=new java.util.HashMap<String,String>();
  if(items!=null)for(var item:items){if(item.getValue()==null||item.getText()==null||item.getText().isBlank())continue;String code=item.getValue().trim().toLowerCase(java.util.Locale.ROOT);if(!code.isEmpty())labels.putIfAbsent(code,item.getText().trim());}
  return labels;
 }
 @Bean public StoreProbePort packageStoreProbe(HttpPort http){return url->{
  var reply=http.exchange("GET",url,null,java.util.Map.of());
  byte[] bytes=(reply.body()==null?"":reply.body()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
  String peek=new String(bytes,0,Math.min(2048,bytes.length),java.nio.charset.StandardCharsets.UTF_8);
  org.slf4j.LoggerFactory.getLogger("package.store.probe").info("Store probe url={} status={} headers={} body={}",url,reply.status(),reply.headers(),peek);
  return reply.success()?StoreProbePort.Result.listed():StoreProbePort.Result.delisted(org.jeecg.modules.packagemanagement.application.store.StoreDelistReason.http(reply.status(),reply.body()));
 };}
 @Bean public MailPort packageMailPort(PackageManagementProperties settings){return new SmtpMailAdapter(settings);}
 @Bean public PwaGateway packagePwaGateway(HttpPort http,PackageManagementProperties settings){return new NpmPwaAdapter(http,settings);}
 @Bean public FacebookPort packageFacebookPort(HttpPort http,PackageManagementProperties settings,@org.springframework.beans.factory.annotation.Qualifier("packageClock") java.time.Clock clock){return new FacebookHttpAdapter(http,settings,clock);}
 @Bean public PushPort packagePushPort(PackageManagementProperties settings){return new WebPushAdapter(settings);}
 @Bean public PackageIdentityRepository packageIdentityRepository(org.springframework.jdbc.core.JdbcTemplate jdbc){return new PackageIdentityRepository(jdbc);}
 @Bean public PackageAccess packageAccess(PackageIdentityRepository repository){return new PackageAccess(repository);}
 @Bean public org.jeecg.modules.packagemanagement.job.PackageStoreJob packageStoreJob(org.jeecg.modules.packagemanagement.application.store.IStoreStatusService service){return new org.jeecg.modules.packagemanagement.job.PackageStoreJob(service);}
 @Bean public org.jeecg.modules.packagemanagement.job.PackageDomainJob packageDomainJob(org.jeecg.modules.packagemanagement.application.domains.IClientDomainService service){return new org.jeecg.modules.packagemanagement.job.PackageDomainJob(service);}
}
