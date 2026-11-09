package org.jeecg.modules.packagemanagement.application.legacy;
public final class StringUtils {
 public static boolean isEmpty(Object value){return value==null || value instanceof CharSequence s && s.isEmpty();}
 public static boolean isNotEmpty(Object value){return !isEmpty(value);}
}
