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
