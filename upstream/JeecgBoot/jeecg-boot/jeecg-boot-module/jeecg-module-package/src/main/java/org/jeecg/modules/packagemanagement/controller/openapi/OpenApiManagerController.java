package org.jeecg.modules.packagemanagement.controller.openapi;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Supplier;
import org.jeecg.modules.packagemanagement.application.openapi.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.springframework.web.bind.annotation.*;

/** Original public wire contract. Never returns the JEECG Result wrapper. */
@RestController
@RequestMapping(value="/openapi/manager",produces="application/json;charset=UTF-8")
public class OpenApiManagerController {
 private final OpenApiManagerService apps;private final OpenApiCallLogService logs;
 public OpenApiManagerController(OpenApiManagerService apps,OpenApiCallLogService logs){this.apps=apps;this.logs=logs;}
 @GetMapping("/list") public String list(HttpServletRequest request,AppManager query,@RequestParam(name="pageNum",defaultValue="1")int pageNum,@RequestParam(name="pageSize",defaultValue="10")int pageSize){
  return execute(request,query,()->{
   var rows=apps.list(OpenApiAuth.require(request),query);int size=Math.max(1,Math.min(500,pageSize));long start=(long)(Math.max(1,pageNum)-1)*size;
   Map<String,Object> result=new LinkedHashMap<>();result.put("total",rows.size());result.put("rows",start>=rows.size()?List.of():rows.subList((int)start,Math.min(rows.size(),(int)start+size)));result.put("code",200);result.put("msg","查询成功");return result;
  });
 }
 @GetMapping("/{id}") public String get(HttpServletRequest request,@PathVariable("id") long id){return execute(request,id,()->success(apps.detail(OpenApiAuth.require(request),id)));}
 @PostMapping public String add(HttpServletRequest request,@RequestBody AppManager row){return execute(request,row,()->{int n=apps.create(OpenApiAuth.require(request),row);return n==-1?error(500,"添加失败：包名重复"):ajax(n);});}
 @PutMapping public String edit(HttpServletRequest request,@RequestBody AppManager row){return execute(request,row,()->ajax(apps.update(OpenApiAuth.require(request),row)));}
 @DeleteMapping("/{ids}") public String delete(HttpServletRequest request,@PathVariable("ids") Long[] ids){return execute(request,ids,()->ajax(apps.delete(OpenApiAuth.require(request),Arrays.asList(ids))));}
 private String execute(HttpServletRequest request,Object body,Supplier<Object> action){
  OpenApiAuth.require(request);try{return json(action.get());}catch(PackageFailure e){return json(error(500,e.getMessage()));}
 }
 /** Source Boot2 Jackson uses ISO dates and the server default timezone for unannotated dates. */
 private static final ObjectMapper JSON=new ObjectMapper().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  .setDateFormat(new StdDateFormat().withTimeZone(TimeZone.getDefault()).withColonInTimeZone(true));
 @InitBinder public void dateFilters(org.springframework.web.bind.WebDataBinder binder){
  binder.registerCustomEditor(Date.class,new java.beans.PropertyEditorSupport(){@Override public void setAsText(String value){
   try{setValue(org.apache.commons.lang3.time.DateUtils.parseDate(value,"yyyy-MM-dd","yyyy-MM-dd HH:mm:ss","yyyy-MM-dd HH:mm","yyyy-MM","yyyy/MM/dd","yyyy/MM/dd HH:mm:ss","yyyy/MM/dd HH:mm","yyyy/MM","yyyy.MM.dd","yyyy.MM.dd HH:mm:ss","yyyy.MM.dd HH:mm","yyyy.MM"));}
   catch(Exception e){setValue(null);}
  }});
 }
 private static Map<String,Object> ajax(int count){return count>0?success(null):error(500,"操作失败");}
 private static Map<String,Object> success(Object data){var result=new LinkedHashMap<String,Object>();result.put("code",200);result.put("msg","操作成功");if(data!=null)result.put("data",data);return result;}
 public static Map<String,Object> error(int code,String message){return Map.of("code",code,"msg",message==null?"操作失败":message);}
 public static String json(Object value){try{return JSON.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("OpenAPI JSON serialization failed",e);}}
}
