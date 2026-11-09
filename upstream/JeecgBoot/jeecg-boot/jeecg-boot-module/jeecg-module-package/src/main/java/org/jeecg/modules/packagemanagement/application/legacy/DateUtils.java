package org.jeecg.modules.packagemanagement.application.legacy;
public final class DateUtils {
 public static java.util.Date getNowDate(){return new java.util.Date();}
 public static String getTime(){return java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));}
}
