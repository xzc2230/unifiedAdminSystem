package org.jeecg.modules.packagemanagement.infrastructure.integration;
import com.alibaba.fastjson2.JSONObject;
import java.util.*;
import java.util.Locale;
import org.jeecg.modules.packagemanagement.application.apps.support.BlockedProviders;
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
        r.put("providers","");
        if("0:0:0:0:0:0:0:1".equals(ip))return r;
        try {
            JSONObject j=lookup(ip);
            String country=text(j,"country"),code=text(j,"countryCode").toLowerCase(Locale.ROOT),as=text(j,"as"),org=text(j,"org"),isp=text(j,"isp");
            if(country.isEmpty()) country="本地";
            if(code.isEmpty()) code="本地";
            r.put("country",country);
            r.put("countryCode",code);
            r.put("status",String.valueOf(code.contains("us")));
            r.put("providers",BlockedProviders.detect(as,org,isp));
        }
        catch(Exception e) {
            r.put("country","美国");
            r.put("countryCode","us");
            r.put("status","true");
            r.put("providers","");
        }
        return r;
    }
    @Override
    public Map<String,String> getFourPathCountryAndCountryCodeByIP(String ip) {
        Map<String,String> result=new HashMap<>();
        result.put("country","本地"); result.put("countryCode","本地"); result.put("status","false"); result.put("providers","");
        if ("0:0:0:0:0:0:0:1".equals(ip)) return result;
        try {
            JSONObject json=lookup(ip);
            String country=text(json,"country");
            String countryCode=text(json,"countryCode").toLowerCase(Locale.ROOT);
            String asRaw=text(json,"as");
            String org=text(json,"org");
            String isp=text(json,"isp");
            if(country.isEmpty()) country="本地";
            if(countryCode.isEmpty()) countryCode="本地";
            result.put("country",country); result.put("countryCode",countryCode);
            result.put("as",asRaw); result.put("org",org); result.put("isp",isp);
            result.put("city",text(json,"city"));
            result.put("timezone",text(json,"timezone"));
            result.put("query",text(json,"query").isEmpty()?ip:text(json,"query"));
            var asn=java.util.regex.Pattern.compile("(?i)^AS(\\d{1,10})\\b").matcher(asRaw.trim());
            result.put("asn",asn.find()?asn.group(1):"");
            result.put("status",String.valueOf(countryCode.contains("us")));
            result.put("providers",BlockedProviders.detect(asRaw,org,isp));
        } catch(Exception e) {
            result.put("country","美国"); result.put("countryCode","us"); result.put("status","true"); result.put("providers","");
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
    private static String text(JSONObject json,String key) {
        String value=json.getString(key);
        return value==null?"":value;
    }
}
