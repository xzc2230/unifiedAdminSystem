package org.jeecg.modules.packagemanagement.application.apps.support;
import com.alibaba.fastjson2.JSON;
import org.jeecg.modules.packagemanagement.application.port.HttpPort;
import org.springframework.stereotype.Service;
@Service
public class AsnLookup {
 private final HttpPort http;
 public AsnLookup(HttpPort http){this.http=http;}
 public static String normalize(String raw){
  if(raw==null)return null;String value=raw.trim().replaceFirst("(?i)^AS", "").trim();
  if(!value.matches("[0-9]{1,10}"))return null;
  try{long n=Long.parseLong(value);return n>0&&n<=4294967295L?Long.toString(n):null;}catch(NumberFormatException e){return null;}
 }
 public String holder(String digits){
  if(normalize(digits)==null)return null;
  try{
   var root=JSON.parseObject(http.json("GET","https://stat.ripe.net/data/as-overview/data.json?resource=AS"+normalize(digits),null,java.util.Map.of("Accept","application/json")));
   if(root==null||!"ok".equals(root.getString("status"))||root.getJSONObject("data")==null)return null;
   String result=root.getJSONObject("data").getString("holder");return result==null||result.isBlank()?null:result.trim();
  }catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("ASN holder lookup failed",failure);return null;}
 }
 public java.util.Map<String,String> preview(String raw){
  String n=normalize(raw);var result=new java.util.LinkedHashMap<String,String>();result.put("asn",n);result.put("asnName",n==null?null:holder(n));result.put("valid",n==null?"false":"true");return result;
 }
}
