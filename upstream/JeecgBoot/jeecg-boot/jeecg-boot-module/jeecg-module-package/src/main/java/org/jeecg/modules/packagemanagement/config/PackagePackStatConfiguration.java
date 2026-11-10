package org.jeecg.modules.packagemanagement.config;
import org.springframework.context.annotation.*;
/** Isolated components and SMTP configuration; no production scheduler is enabled automatically. */
@Configuration(proxyBeanMethods=false)
@ComponentScan(basePackages="org.jeecg.modules.packagemanagement.application.packstat.detect")
public class PackagePackStatConfiguration {}
