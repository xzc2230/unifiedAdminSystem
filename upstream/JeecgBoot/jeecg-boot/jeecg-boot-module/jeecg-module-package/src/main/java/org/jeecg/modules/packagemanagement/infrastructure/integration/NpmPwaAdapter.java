package org.jeecg.modules.packagemanagement.infrastructure.integration;
import java.util.Map;
import com.alibaba.fastjson2.JSONObject;
import org.jeecg.modules.packagemanagement.application.port.*;
import org.jeecg.modules.packagemanagement.config.PackageManagementProperties;
public class NpmPwaAdapter implements PwaGateway  {
    private final HttpPort http;
    private final PackageManagementProperties settings;
    private volatile String token;
    public NpmPwaAdapter(HttpPort http,PackageManagementProperties settings) {
        this.http=http;
        this.settings=settings;
    }
    private synchronized String token() {
        if(token==null) {
            String result=http.json("POST",settings.getNpmBaseUrl()+"/api/tokens",JSONObject.toJSONString(Map.of("identity",settings.getNpmIdentity(),"secret",settings.getNpmSecret())),Map.of("Content-Type","application/json"));
            token=JSONObject.parseObject(result).getString("token");
            if(token==null)throw new org.jeecg.modules.packagemanagement.exception.PackageFailure(502,"NPM token missing");
        }
        return token;
    }
    private String call(String method,String suffix,String body) {
        for(int attempt=0;attempt<2;attempt++) {
            HttpPort.Reply r=http.exchange(method,settings.getNpmBaseUrl()+"/api/nginx/proxy-hosts"+suffix,body,Map.of("Content-Type","application/json","Authorization","Bearer "+token()));
            if(r.status()==401&&attempt==0) {
                token=null;
                continue;
            }
            if(!r.success())throw new org.jeecg.modules.packagemanagement.exception.PackageFailure(502,"NPM HTTP status "+r.status());
            return r.body();
        }
        throw new IllegalStateException();
    }
    public String generateVapidKeys() {
        return http.json("GET",settings.getVapidUrl(),null,Map.of());
    }
    public String createHost(String body) {
        return call("POST","",body);
    }
    public void updateHost(String id,String body) {
        call("PUT","/"+id,body);
    }
    public void deleteHost(String id) {
        call("DELETE","/"+id,null);
    }
}
