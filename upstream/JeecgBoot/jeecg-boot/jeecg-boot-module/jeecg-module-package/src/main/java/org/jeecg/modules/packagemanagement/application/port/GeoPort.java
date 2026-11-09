package org.jeecg.modules.packagemanagement.application.port;
public interface GeoPort {
String getCityByIp(String ip); java.util.Map<String,String> getCountryAndCountryCodeByIP(String ip); String passFb(String ip,String uri);
 /** Four-path source tolerates absent optional Geo provider fields. */
 default java.util.Map<String,String> getFourPathCountryAndCountryCodeByIP(String ip){return getCountryAndCountryCodeByIP(ip);}
 default String getIpAddress(jakarta.servlet.http.HttpServletRequest request){String ip=request.getHeader("X-Forwarded-For"); if(ip==null||ip.isEmpty()||"unknown".equalsIgnoreCase(ip)){for(String h:new String[]{"Proxy-Client-IP","WL-Proxy-Client-IP","HTTP_CLIENT_IP","HTTP_X_FORWARDED_FOR"}){ip=request.getHeader(h);if(ip!=null&&!ip.isEmpty()&&!"unknown".equalsIgnoreCase(ip))return ip;}return request.getRemoteAddr();}if(ip.length()>15){for(String part:ip.split(","))if(!"unknown".equalsIgnoreCase(part))return part;}return ip;}
}
