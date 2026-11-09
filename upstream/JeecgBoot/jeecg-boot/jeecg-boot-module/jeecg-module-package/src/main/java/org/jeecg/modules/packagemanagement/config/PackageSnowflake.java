package org.jeecg.modules.packagemanagement.config;
public class PackageSnowflake {
 private final cn.hutool.core.lang.Snowflake snowflake=new cn.hutool.core.lang.Snowflake(1,1,true);
 public long nextId(){return snowflake.nextId();}
}
