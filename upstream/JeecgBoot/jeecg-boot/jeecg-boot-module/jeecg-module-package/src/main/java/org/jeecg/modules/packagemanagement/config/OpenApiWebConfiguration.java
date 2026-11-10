package org.jeecg.modules.packagemanagement.config;
import java.util.LinkedHashMap;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.jeecg.modules.packagemanagement.application.openapi.*;
import org.jeecg.modules.packagemanagement.controller.openapi.OpenApiTokenInterceptor;
import org.jeecg.modules.packagemanagement.controller.openapi.OpenApiAuditFilter;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.*;

@Configuration(proxyBeanMethods=false)
public class OpenApiWebConfiguration implements WebMvcConfigurer {
 private final OpenApiTokenInterceptor interceptor;
 public OpenApiWebConfiguration(OpenApiTokenService tokens,OpenApiCallLogService logs){this.interceptor=new OpenApiTokenInterceptor(tokens,logs);}
 @Bean public org.springframework.boot.web.servlet.FilterRegistrationBean<OpenApiAuditFilter> packageOpenApiAudit(OpenApiCallLogService logs){var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(new OpenApiAuditFilter(logs));registration.addUrlPatterns("/openapi/manager","/openapi/manager/*");registration.setOrder(-109);return registration;}
 @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(interceptor).addPathPatterns("/openapi/manager","/openapi/manager/**");}
 /** Runs before the FactoryBean creates its filter chain; MVC still always requires X-Api-Token. */
 @Bean public static BeanPostProcessor packageOpenApiShiroBoundary(){return new BeanPostProcessor(){
  @Override public Object postProcessBeforeInitialization(Object bean,String name){
   if(bean instanceof ShiroFilterFactoryBean shiro){var chain=new LinkedHashMap<String,String>();chain.put("/openapi/manager","anon");chain.put("/openapi/manager/**","anon");chain.putAll(shiro.getFilterChainDefinitionMap());shiro.setFilterChainDefinitionMap(chain);}return bean;
  }
 };}
}
