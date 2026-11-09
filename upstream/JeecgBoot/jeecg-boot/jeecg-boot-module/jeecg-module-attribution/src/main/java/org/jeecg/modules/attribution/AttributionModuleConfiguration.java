package org.jeecg.modules.attribution;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AttributionProperties.class)
public class AttributionModuleConfiguration {
  @Bean
  public AttributionRepository attributionRepository(
      JdbcTemplate jdbc, PlatformTransactionManager manager) {
    return new AttributionRepository(jdbc, manager);
  }

  @Bean
  public AttributionService attributionService(
      AttributionRepository repository, AttributionProperties properties) {
    return new AttributionService(repository, properties);
  }
}
