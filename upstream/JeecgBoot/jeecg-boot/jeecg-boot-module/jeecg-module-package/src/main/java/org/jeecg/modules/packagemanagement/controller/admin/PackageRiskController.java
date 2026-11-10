package org.jeecg.modules.packagemanagement.controller.admin;

import java.util.*;
import java.util.function.*;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.packagemanagement.application.apps.support.AsnLookup;
import org.jeecg.modules.packagemanagement.application.apps.risk.app2.App2PkgRiskRedisService;
import org.jeecg.modules.packagemanagement.domain.PackageEntity;
import org.jeecg.modules.packagemanagement.domain.app.*;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageAccess;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.*;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.web.bind.annotation.*;

/** Global risk rules intentionally apply to all packages, exactly as in the source.
 * Only explicitly granted risk administrators can manage them. Per-package Redis lists additionally require ownership. */
@RestController
@RequestMapping("/package-admin/risk")
public class PackageRiskController {
 private record Binding<T extends PackageEntity>(Class<T> type,Function<T,List<T>> list,Function<Long,T> find,ToIntFunction<T> add,ToIntFunction<T> edit,ToIntFunction<Long[]> remove,List<String> fields){}
 private final Map<String,Binding<?>> bindings=new HashMap<>();
 private final PackageAdminSupport guard;private final PackageAccess access;private final App2PkgRiskRedisService redis;private final AsnLookup asn;
 private final com.fasterxml.jackson.databind.ObjectMapper json=org.jeecg.modules.packagemanagement.controller.PackageJson.mapper();
 public PackageRiskController(PackageAdminSupport guard,PackageAccess access,App2PkgRiskRedisService redis,AsnLookup asn,AppAsnBlacklistMapper a,AppDeviceBrandBlacklistMapper b,AppCpuAbiBlacklistMapper c,AppDeviceNameBlacklistMapper d){
  this.guard=guard;this.access=access;this.redis=redis;this.asn=asn;
  bindings.put("asn",new Binding<>(AppAsnBlacklist.class,a::selectAppAsnBlacklistList,a::selectAppAsnBlacklistById,a::insertAppAsnBlacklist,a::updateAppAsnBlacklist,a::deleteAppAsnBlacklistByIds,List.of("asn","asnName","orgKeyword","matchType","action","enabled","priority","remark")));
  bindings.put("brand",new Binding<>(AppDeviceBrandBlacklist.class,b::selectAppDeviceBrandBlacklistList,b::selectAppDeviceBrandBlacklistById,b::insertAppDeviceBrandBlacklist,b::updateAppDeviceBrandBlacklist,b::deleteAppDeviceBrandBlacklistByIds,List.of("brandKeyword","matchMode","caseSensitive","action","enabled","priority","remark")));
  bindings.put("abi",new Binding<>(AppCpuAbiBlacklist.class,c::selectAppCpuAbiBlacklistList,c::selectAppCpuAbiBlacklistById,c::insertAppCpuAbiBlacklist,c::updateAppCpuAbiBlacklist,c::deleteAppCpuAbiBlacklistByIds,List.of("abiKeyword","matchMode","caseSensitive","action","enabled","priority","remark")));
  bindings.put("device",new Binding<>(AppDeviceNameBlacklist.class,d::selectAppDeviceNameBlacklistList,d::selectAppDeviceNameBlacklistById,d::insertAppDeviceNameBlacklist,d::updateAppDeviceNameBlacklist,d::deleteAppDeviceNameBlacklistByIds,List.of("deviceNameKeyword","matchMode","caseSensitive","action","enabled","priority","remark")));
 }
 @SuppressWarnings("unchecked") private Binding<PackageEntity> binding(String kind){var value=bindings.get(kind);if(value==null)throw new PackageFailure(404,"未知风控规则");return (Binding<PackageEntity>)value;}
 private PackageEntity row(Binding<PackageEntity> b,Map<String,Object> values){var allowed=new LinkedHashMap<String,Object>();for(String f:b.fields())if(values.containsKey(f))allowed.put(f,values.get(f));return json.convertValue(allowed,b.type());}
 @GetMapping("/{kind}/list") public Result<Map<String,Object>> list(@PathVariable String kind,@RequestParam Map<String,String> filters,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="10") int pageSize){
  guard.actor("risk","query");var b=binding(kind);var values=new HashMap<String,Object>(filters);var rows=b.list().apply(row(b,values));
  int size=Math.max(1,Math.min(500,pageSize));long start=(long)(Math.max(1,pageNo)-1)*size;
  return Result.ok(Map.of("records",start>=rows.size()?List.of():rows.subList((int)start,Math.min(rows.size(),(int)start+size)),"total",(long)rows.size()));
 }
 @GetMapping("/asn/lookup/{number}") public Result<Map<String,String>> lookup(@PathVariable String number){guard.actor("risk","query");return Result.ok(asn.preview(number));}
 @GetMapping("/{kind}/{id:[0-9]+}") public Result<PackageEntity> detail(@PathVariable String kind,@PathVariable long id){guard.actor("risk","query");return Result.ok(existing(binding(kind),id));}
 private PackageEntity existing(Binding<PackageEntity> b,long id){var row=b.find().apply(id);if(row==null)throw new PackageFailure(404,"规则不存在");return row;}
 @PostMapping("/{kind}") public Result<PackageEntity> add(@PathVariable String kind,@RequestBody Map<String,Object> values){
  var actor=guard.actor("risk","add");var b=binding(kind);var row=row(b,values);var bean=new BeanWrapperImpl(row);
  defaults(bean,"action","block");defaults(bean,"enabled",1);defaults(bean,"priority",0);
  if(kind.equals("asn")){
   String n=AsnLookup.normalize((String)bean.getPropertyValue("asn"));if(n==null)throw new PackageFailure(400,"Invalid ASN: use 1-4294967295, optional AS prefix.");
   bean.setPropertyValue("asn",n);String holder=asn.holder(n);bean.setPropertyValue("asnName",holder==null?null:holder.substring(0,Math.min(256,holder.length())));defaults(bean,"matchType","asn_exact");
  }else{defaults(bean,"matchMode","contains");defaults(bean,"caseSensitive",0);}
  row.setCreateTime(new Date());row.setCreateBy(actor.userId());if(b.add().applyAsInt(row)<=0)throw new PackageFailure(400,"新增失败");return Result.ok(row);
 }
 private void defaults(BeanWrapperImpl bean,String key,Object value){Object current=bean.getPropertyValue(key);if(current==null||current instanceof String s&&s.isEmpty())bean.setPropertyValue(key,value);}
 @PutMapping("/{kind}") public Result<PackageEntity> edit(@PathVariable String kind,@RequestBody Map<String,Object> values){
  var actor=guard.actor("risk","edit");var b=binding(kind);Object key=values.get("id");if(key==null)throw new PackageFailure(400,"规则ID不能为空");long id=Long.parseLong(String.valueOf(key));existing(b,id);
  var row=row(b,values);new BeanWrapperImpl(row).setPropertyValue("id",id);row.setUpdateTime(new Date());row.setUpdateBy(actor.userId());
  if(b.edit().applyAsInt(row)<=0)throw new PackageFailure(400,"修改失败");return Result.ok(b.find().apply(id));
 }
 @DeleteMapping("/{kind}/{ids}") public Result<Integer> remove(@PathVariable String kind,@PathVariable String ids){guard.actor("risk","remove");var b=binding(kind);Long[] keys=Arrays.stream(ids.split(",")).map(Long::valueOf).toArray(Long[]::new);return Result.ok(b.remove().applyAsInt(keys));}
 private String owned(String pkg,String alias,boolean remove){
  var actor=guard.actor("risk",remove?"remove":"query");String code=pkg==null?alias:pkg;
  if(code==null||code.isBlank()||code.chars().anyMatch(ch->"*?[]".indexOf(ch)>=0))throw new PackageFailure(400,"pkg 无效");
  access.requireLogPackage(actor,code);return code;
 }
 @GetMapping("/auto/list") public Result<List<Map<String,String>>> automatic(@RequestParam(required=false) String pkg,@RequestParam(required=false) String packageCode,@RequestParam(required=false) String type){
  String code=owned(pkg,packageCode,false);if(type!=null&&!type.isBlank()&&!Set.of("gaid","aid").contains(type))throw new PackageFailure(400,"type 无效");
  var rows=new ArrayList<Map<String,String>>();for(String t:List.of("gaid","aid"))if(type==null||type.isBlank()||type.equals(t))for(var e:redis.listAutoBlocked(code,t))rows.add(Map.of("type",t,"value",e.getValue(),"reason",e.getReason()));return Result.ok(rows);
 }
 @DeleteMapping("/auto") public Result<Boolean> unblock(@RequestParam(required=false) String pkg,@RequestParam(required=false) String packageCode,@RequestParam String type,@RequestParam String value){
  String code=owned(pkg,packageCode,true);if(!Set.of("gaid","aid").contains(type)||value.isBlank())throw new PackageFailure(400,"拉黑参数无效");return Result.ok(redis.removeAutoBlock(code,type,value));
 }
}
