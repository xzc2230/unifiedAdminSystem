package org.jeecg.modules.packagemanagement.infrastructure.integration;
import java.time.Clock;
import java.util.*;
import com.alibaba.fastjson2.JSON;
import org.jeecg.modules.packagemanagement.application.port.*;
import org.jeecg.modules.packagemanagement.config.PackageManagementProperties;
import org.jeecg.modules.packagemanagement.domain.event.*;
public class FacebookHttpAdapter implements FacebookPort  {
    private final HttpPort http;
    private final PackageManagementProperties settings;
    private final Clock clock;
    public FacebookHttpAdapter(HttpPort http,PackageManagementProperties settings,Clock clock) {
        this.http=http;
        this.settings=settings;
        this.clock=clock;
    }
    public String send(EventConfig config,EvnetHistory event) {
        Map<String,Object> user=new LinkedHashMap<>();
        user.put("client_ip_address",event.getIp());
        user.put("client_user_agent",event.getUserAgent());
        Map<String,Object> custom=new LinkedHashMap<>();
        custom.put("contents",List.of(Map.of("id","product123","quantity",1,"delivery_category","home_delivery")));
        custom.put("currency",event.getSelected_currency());
        custom.put("value",event.getEvent_revenue_usd());
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("event_name",event.getEventName());
        data.put("event_time",clock.instant().getEpochSecond());
        data.put("user_data",user);
        data.put("custom_data",custom);
        data.put("action_source","website");
        return http.json("POST",settings.getFacebookBaseUrl()+"/"+config.getPackagePixel()+"/events",JSON.toJSONString(Map.of("data",List.of(data),"access_token",config.getPackageToken())),Map.of("Content-Type","application/json"));
    }
}
