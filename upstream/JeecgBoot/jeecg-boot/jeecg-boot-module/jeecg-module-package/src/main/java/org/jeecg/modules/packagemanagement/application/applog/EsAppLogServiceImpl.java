package org.jeecg.modules.packagemanagement.application.applog;
import java.util.*;import org.jeecg.modules.packagemanagement.application.port.AppLogPort;import org.jeecg.modules.packagemanagement.domain.applog.AppLog;import org.jeecg.modules.packagemanagement.infrastructure.integration.ElasticsearchAppLogAdapter;
public class EsAppLogServiceImpl {
 private final ElasticsearchAppLogAdapter adapter;public EsAppLogServiceImpl(ElasticsearchAppLogAdapter adapter){this.adapter=adapter;}
 public Map<String,Object> builder(AppLog filter){return ElasticsearchAppLogAdapter.exportQueryBody(filter,null);}
 public AppLogPort.Page list(AppLog filter,int page,int size,Collection<String> packages){var result=adapter.query(filter,page,size,packages);result.rows().forEach(this::pretty);return result;}
 private void pretty(AppLog row){if(row.getReqParams()!=null&&!row.getReqParams().isBlank())try{row.setReqParams(com.alibaba.fastjson2.JSON.toJSONString(com.alibaba.fastjson2.JSON.parse(row.getReqParams()),com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat));}catch(RuntimeException invalid){/* Preserve source text for non-JSON requests. */}}
 public List<AppLog> export(AppLog filter,Collection<String> packages){return adapter.export(filter,packages);}
 public Optional<AppLog> findById(String id){return adapter.findById(id);}
 public AppLog save(AppLog log){return adapter.save(log);}
 public void deleteAllById(Iterable<String> ids){adapter.deleteAllById(ids);}
 public long clean(AppLog filter,Collection<String> packages){return adapter.clean(filter,packages);}
}
