package org.jeecg.modules.packagemanagement.infrastructure.integration;
import com.alibaba.fastjson2.*;
import java.util.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.jeecg.modules.packagemanagement.application.port.*;
import org.jeecg.modules.packagemanagement.domain.applog.AppLog;
import org.jeecg.modules.packagemanagement.config.PackageManagementProperties;
public class ElasticsearchAppLogAdapter implements AppLogPort  {
    private final HttpPort http;
    private final PackageManagementProperties settings;
    private volatile boolean indexReady;
    private static final DateTimeFormatter STORAGE_DATE=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneOffset.UTC);
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));
    public ElasticsearchAppLogAdapter(HttpPort http,PackageManagementProperties settings) {
        this.http=http;
        this.settings=settings;
    }
    private String uri(String path) {
        if(settings.getEsUrl().isBlank())throw new org.jeecg.modules.packagemanagement.exception.PackageFailure(503,"Package Elasticsearch is not configured");
        return settings.getEsUrl().replaceAll("/$","")+path;
    }
    private Map<String,String> headers() {
        Map<String,String> h=new HashMap<>();
        h.put("Content-Type","application/json");
        if(!settings.getEsUsername().isBlank())h.put("Authorization","Basic "+Base64.getEncoder().encodeToString((settings.getEsUsername()+":"+settings.getEsPassword()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return h;
    }
    private String id(String id) {
        return java.net.URLEncoder.encode(id,java.nio.charset.StandardCharsets.UTF_8);
    }
    private JSONObject call(String method,String path,Object body) {
        return JSONObject.parseObject(http.json(method,uri(path),body==null?null:JSON.toJSONString(body),headers()));
    }
    public synchronized void ensureIndex() {
        if(indexReady)return;
        HttpPort.Reply exists=http.exchange("HEAD",uri("/applog"),null,headers());
        if(exists.status()==404) {
            Map<String,Object> properties=new LinkedHashMap<>();
            for(String field:List.of("id","packageName","reqPath","reqIp","reqParams","reqHeards","reqMethod","resultMsg","errorMsg"))properties.put(field,Map.of("type","keyword"));
            properties.put("reqTime",Map.of("type","date","format","yyyy-MM-dd HH:mm:ss"));
            properties.put("isMobile",Map.of("type","integer"));
            HttpPort.Reply created=http.exchange("PUT",uri("/applog"),JSON.toJSONString(Map.of("mappings",Map.of("properties",properties))),headers());
            if(!created.success()&&!(created.status()==400&&created.body().contains("resource_already_exists_exception")))throw new org.jeecg.modules.packagemanagement.exception.PackageFailure(502,"Elasticsearch index creation failed");
        }
        else if(!exists.success())throw new org.jeecg.modules.packagemanagement.exception.PackageFailure(502,"Elasticsearch index check failed");
        indexReady=true;
    }
    public AppLog save(AppLog log) {
        ensureIndex();
        JSONObject body=(JSONObject)JSON.toJSON(log);
        if(log.getReqTime()!=null)body.put("reqTime",STORAGE_DATE.format(log.getReqTime().toInstant()));
        call("PUT","/applog/_doc/"+id(log.getId()),body);
        return log;
    }
    public Optional<AppLog> findById(String id) {
        HttpPort.Reply r=http.exchange("GET",uri("/applog/_doc/"+id(id)),null,headers());
        if(r.status()==404)return Optional.empty();
        if(!r.success())throw new org.jeecg.modules.packagemanagement.exception.PackageFailure(502,"Elasticsearch HTTP status "+r.status());
        return Optional.of(decode(JSONObject.parseObject(r.body())));
    }
    private AppLog decode(JSONObject hit) {
        JSONObject source=hit.getJSONObject("_source");
        String time=source.getString("reqTime");
        if(time!=null&&!time.isBlank()&&time.matches("\\d{4}-.*")) {
            source.put("reqTime",Date.from(java.time.LocalDateTime.parse(time,DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toInstant(java.time.ZoneOffset.UTC)).getTime());
        }
        AppLog log=source.toJavaObject(AppLog.class);
        if(hit.getString("_id")!=null)log.setId(hit.getString("_id"));
        return log;
    }
    public static Map<String,Object> queryBody(AppLog filter,Collection<String> allowedPackages) {
        return queryBody(filter,allowedPackages,true);
    }
    public static Map<String,Object> exportQueryBody(AppLog filter,Collection<String> allowedPackages) {
        return queryBody(filter,allowedPackages,false);
    }
    private static Map<String,Object> queryBody(AppLog filter,Collection<String> allowedPackages,boolean listTimezone) {
        List<Object> must=new ArrayList<>();
        if(filter!=null&&filter.getPackageName()!=null)must.add(Map.of("wildcard",Map.of("packageName","*"+filter.getPackageName()+"*")));
        if(filter!=null&&filter.getStartReqTime()!=null&&filter.getEndReqTime()!=null) {
            Map<String,Object> range=new LinkedHashMap<>();
            range.put("gte",DATE.format(filter.getStartReqTime().toInstant()));
            range.put("lte",DATE.format(filter.getEndReqTime().toInstant()));
            if(listTimezone)range.put("time_zone","Asia/Shanghai");
            must.add(Map.of("range",Map.of("reqTime",range)));
        }
        ;
        if(allowedPackages!=null)must.add(allowedPackages.isEmpty()?Map.of("match_none",Map.of()):Map.of("terms",Map.of("packageName",allowedPackages)));
        return Map.of("bool",Map.of("must",must));
    }
    private Map<String,Object> body(AppLog filter,Collection<String> allowedPackages) {
        Map<String,Object> b=new LinkedHashMap<>();
        b.put("query",queryBody(filter,allowedPackages));
        b.put("sort",List.of(Map.of("reqTime",Map.of("order","desc"))));
        b.put("track_total_hits",true);
        return b;
    }
    public Page query(AppLog filter,int page,int size,Collection<String> allowedPackages) {
        if(page<1||size<1||size>10000)throw new IllegalArgumentException("Invalid page");
        Map<String,Object>b=body(filter,allowedPackages);
        b.put("from",Math.multiplyExact(page-1,size));
        b.put("size",size);
        return page(call("POST","/applog/_search",b));
    }
    private Page page(JSONObject response) {
        JSONObject hits=response.getJSONObject("hits");
        Object total=hits.get("total");
        long count=total instanceof Number n?n.longValue():hits.getJSONObject("total").getLongValue("value");
        List<AppLog> rows=new ArrayList<>();
        for(Object hit:hits.getJSONArray("hits"))rows.add(decode((JSONObject)hit));
        return new Page(rows,count);
    }
    public List<AppLog> export(AppLog filter,Collection<String> allowedPackages) {
        Map<String,Object>b=body(filter,allowedPackages);
        b.put("query",exportQueryBody(filter,allowedPackages));
        b.put("size",1000);
        JSONObject result=call("POST","/applog/_search?scroll=1m",b);
        String scroll=result.getString("_scroll_id");
        List<AppLog> rows=new ArrayList<>();
        try {
            while(true) {
                Page page=page(result);
                if(page.rows().isEmpty())break;
                rows.addAll(page.rows());
                if(scroll==null)break;
                result=call("POST","/_search/scroll",Map.of("scroll","1m","scroll_id",scroll));
                scroll=result.getString("_scroll_id");
            }
            return rows;
        }
        finally {
            if(scroll!=null)call("DELETE","/_search/scroll",Map.of("scroll_id",List.of(scroll)));
        }
    }
    public void deleteAllById(Iterable<String> ids) {
        for(String value:ids) {
            HttpPort.Reply r=http.exchange("DELETE",uri("/applog/_doc/"+id(value)),null,headers());
            if(!r.success()&&r.status()!=404)throw new org.jeecg.modules.packagemanagement.exception.PackageFailure(502,"Elasticsearch delete failed");
        }
    }
    public long clean(AppLog filter,Collection<String> allowedPackages) {
        JSONObject response=call("POST","/applog/_delete_by_query?refresh=true",Map.of("query",queryBody(filter,allowedPackages)));
        if(response.getJSONArray("failures")!=null&&!response.getJSONArray("failures").isEmpty())throw new org.jeecg.modules.packagemanagement.exception.PackageFailure(502,"Elasticsearch clean partially failed");
        return response.getLongValue("deleted");
    }
}
