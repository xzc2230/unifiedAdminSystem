package org.jeecg.modules.packagemanagement.config;

import org.springframework.context.annotation.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.cors.*;

@Configuration(proxyBeanMethods=false)
public class PackageHttpConfiguration {
    @Bean public org.springdoc.core.models.GroupedOpenApi packageApiDocs() {
        return org.springdoc.core.models.GroupedOpenApi.builder().group("package-management").pathsToMatch("/package-admin/**","/package-legacy/**").build();
    }
    @Bean public FilterRegistrationBean<CorsFilter> packageLegacyCors() {
        var config=new CorsConfiguration();config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");config.addAllowedHeader("*");config.addAllowedMethod("*");config.setMaxAge(1800L);
        var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/package-legacy/**",config);
        var bean=new FilterRegistrationBean<>(new CorsFilter(source));bean.setOrder(-110);bean.addUrlPatterns("/package-legacy/*");return bean;
    }
}
