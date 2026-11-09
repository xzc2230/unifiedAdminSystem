package org.jeecg.modules.packagemanagement.domain.pwa;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

public class PwaBody {
    public static String CreateBoody(String advanced_config,int certificate_id ,String...domain){
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("forward_scheme","http");
        jsonObject.put("forward_host","127.17.0.1");
        jsonObject.put("forward_port","80");
        jsonObject.put("access_list_id","0");
        jsonObject.put("certificate_id",certificate_id);
        jsonObject.put("ssl_forced",true);
        jsonObject.put("locations", new JSONArray());
        jsonObject.put("block_exploits", false);
        jsonObject.put("caching_enabled", false);
        jsonObject.put("allow_websocket_upgrade", false);
        jsonObject.put("http2_support", false);
        jsonObject.put("hsts_enabled", false);
        jsonObject.put("hsts_subdomains", false);
        JSONObject meta = new JSONObject();
        meta.put("letsencrypt_agree", false);
        meta.put("dns_challenge", false);
        jsonObject.put("meta", meta);
        jsonObject.put("advanced_config",advanced_config);
        jsonObject.put("domain_names",domain);
        return jsonObject.toString();
    }

}
