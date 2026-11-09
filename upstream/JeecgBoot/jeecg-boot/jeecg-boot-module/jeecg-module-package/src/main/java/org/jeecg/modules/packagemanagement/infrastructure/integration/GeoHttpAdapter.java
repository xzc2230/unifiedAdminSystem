package org.jeecg.modules.packagemanagement.infrastructure.integration;
import com.alibaba.fastjson2.JSONObject;
import java.util.*;
import org.jeecg.modules.packagemanagement.application.port.*;
import org.jeecg.modules.packagemanagement.config.PackageManagementProperties;
public class GeoHttpAdapter implements GeoPort  {
    private final HttpPort http;
    private final PackageManagementProperties settings;
    public GeoHttpAdapter(HttpPort http,PackageManagementProperties settings) {
        this.http=http;
        this.settings=settings;
    }
    private JSONObject lookup(String ip) {
        String url=settings.getGeoUrl().replace("{ip}",java.net.URLEncoder.encode(ip,java.nio.charset.StandardCharsets.UTF_8)).replace("{key}",java.net.URLEncoder.encode(settings.getGeoApiKey(),java.nio.charset.StandardCharsets.UTF_8));
        return JSONObject.parseObject(http.json("GET",url,null,Map.of()));
    }
    public Map<String,String> getCountryAndCountryCodeByIP(String ip) {
        Map<String,String> r=new HashMap<>();
        r.put("country","本地");
        r.put("countryCode","本地");
        r.put("status","false");
        if("0:0:0:0:0:0:0:1".equals(ip))return r;
        try {
            JSONObject j=lookup(ip);
            String country=j.getString("country"),code=j.getString("countryCode").toLowerCase(),as=j.getString("as").toLowerCase(),org=j.getString("org").toLowerCase(),isp=j.getString("isp").toLowerCase();
            r.put("country",country);
            r.put("countryCode",code);
            r.put("status",String.valueOf(code.contains("us")||as.contains("google")||as.contains("apple")||as.contains("amazon")||org.contains("google")||isp.contains("google")));
        }
        catch(Exception e) {
            r.put("country","美国");
            r.put("countryCode","us");
            r.put("status","true");
        }
        return r;
    }
    @Override
    public Map<String,String> getFourPathCountryAndCountryCodeByIP(String ip) {
        Map<String,String> result=new HashMap<>();
        result.put("country","本地"); result.put("countryCode","本地"); result.put("status","false");
        if ("0:0:0:0:0:0:0:1".equals(ip)) return result;
        try {
            JSONObject json=lookup(ip);
            String country=Objects.requireNonNullElse(json.getString("country"),"本地");
            String countryCode=Objects.requireNonNullElse(json.getString("countryCode"),"本地").toLowerCase();
            String asRaw=Objects.requireNonNullElse(json.getString("as"),"");
            String org=Objects.requireNonNullElse(json.getString("org"),"");
            String isp=Objects.requireNonNullElse(json.getString("isp"),"");
            result.put("country",country); result.put("countryCode",countryCode);
            result.put("as",asRaw); result.put("org",org); result.put("isp",isp);
            result.put("city",Objects.requireNonNullElse(json.getString("city"),""));
            result.put("timezone",Objects.requireNonNullElse(json.getString("timezone"),""));
            result.put("query",Objects.requireNonNullElse(json.getString("query"),ip));
            var asn=java.util.regex.Pattern.compile("(?i)^AS(\\d{1,10})\\b").matcher(asRaw.trim());
            result.put("asn",asn.find()?asn.group(1):"");
            String as=asRaw.toLowerCase();
            result.put("status",String.valueOf(countryCode.contains("us")||as.contains("google")||as.contains("apple")
                ||as.contains("amazon")||org.toLowerCase().contains("google")||isp.toLowerCase().contains("google")));
        } catch(Exception e) {
            result.put("country","美国"); result.put("countryCode","us"); result.put("status","true");
        }
        return result;
    }
    public String getCityByIp(String ip) {
        Map<String,String> r=getCountryAndCountryCodeByIP(ip);
        return "true".equals(r.get("status"))?"美国":r.get("country");
    }
    public String passFb(String ip,String uri) {
        if(!"0:0:0:0:0:0:0:1".equals(ip)) {
            try {
                if(lookup(ip).getString("as").toLowerCase().contains("facebook"))return settings.getFacebookBlockedUrl().isBlank()?settings.getLandingFallbackUrl():settings.getFacebookBlockedUrl();
            }
            catch(Exception ignored) {
                return settings.getLandingFallbackUrl();
            }
        }
        return uri;
    }
}
