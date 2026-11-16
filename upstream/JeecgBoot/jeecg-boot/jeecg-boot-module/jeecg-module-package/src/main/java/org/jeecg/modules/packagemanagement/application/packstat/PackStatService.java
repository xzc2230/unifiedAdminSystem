package org.jeecg.modules.packagemanagement.application.packstat;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.jeecg.modules.packagemanagement.domain.packstat.*;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.*;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageActor;
import org.jeecg.modules.packagemanagement.application.packstat.util.*;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
@Service
public class PackStatService {
 private final PackStatMapper mapper;
 private final PackStatRegionMapper regions;
 public PackStatService(PackStatMapper mapper,PackStatRegionMapper regions){this.mapper=mapper;this.regions=regions;}
 public List<PackStat> list(PackStat query,PackageActor actor){PackStatAccess.scope(query,actor);return mapper.selectPackStatList(query);}
 public PackStat detail(long id,PackageActor actor){return PackStatAccess.require(mapper.selectPackStatById(id),actor);}
 @Transactional public int add(PackStat row,PackageActor actor){
  row.setId(null);row.setTenantId(actor.tenantId());row.setOwnerUserId(actor.userId());hints(row);
  Date today=java.sql.Date.valueOf(LocalDate.now());
  if(row.getPassDate()==null)row.setPassDate(today);
  if(row.getCustomerUseDate()==null)row.setCustomerUseDate(today);
  if(row.getIsReplenish()==null)row.setIsReplenish(0);
  row.setCreateTime(new Date());row.setCreateBy(actor.userId());row.setUpdateBy(null);row.setUpdateTime(null);
  category(row);
  return mapper.insertPackStat(row);
 }
 @Transactional public int edit(PackStat row,PackageActor actor){
  if(row.getId()==null)throw new PackageFailure(400,"缺少包统计ID");
  PackStat existing=detail(row.getId(),actor);hints(row);
  row.setTenantId(existing.getTenantId());row.setOwnerUserId(existing.getOwnerUserId());
  category(row);
  row.setUpdateBy(actor.userId());row.setUpdateTime(new Date());return mapper.updatePackStat(row);
 }
 @Transactional public int delete(Long[] ids,PackageActor actor){
  if(ids==null||ids.length==0)throw new PackageFailure(400,"请选择记录");
  for(Long id:ids){if(id==null)throw new PackageFailure(400,"无效记录ID");detail(id,actor);}
  return mapper.deletePackStatByIds(ids);
 }
 public List<String> authors(PackageActor actor){PackStat scope=new PackStat();PackStatAccess.scope(scope,actor);List<String> result=mapper.selectDistinctPackers(scope);return result==null?List.of():result;}
 public PackStatCommission.Report commission(String useMonth,String category,String packer,PackageActor actor){
  if(useMonth==null||!useMonth.matches("\\d{4}-\\d{2}"))throw new PackageFailure(400,"请选择月份");
  YearMonth month;try{month=YearMonth.parse(useMonth);}catch(RuntimeException invalid){throw new PackageFailure(400,"请选择月份");}
  if(!"公司".equals(category)&&!"CT".equals(category))throw new PackageFailure(400,"分类无效");
  String person=packer==null?"":packer.trim();
  PackStat query=new PackStat();PackStatAccess.scope(query,actor);query.setUseMonth(useMonth);query.setCustomerCategory(category);if(!person.isEmpty())query.setPacker(person);
  List<PackStat> rows=list(query,actor);if(rows==null)rows=List.of();
  return PackStatCommission.build(category,person,month,LocalDate.now(ZoneId.of("Asia/Shanghai")),rows.stream().map(PackStatService::source).toList());
 }
 private static PackStatCommission.Source source(PackStat row){
  return new PackStatCommission.Source(row.getPlatform(),row.getPacker(),row.getStoreUrl(),row.getCustomer(),Integer.valueOf(1).equals(row.getIsReplenish()),row.getReplenishUrl(),date(row.getCustomerUseDate()),date(row.getDropDate()),row.getDesigner());
 }
 private static LocalDate date(java.util.Date value){
  if(value==null)return null;
  if(value instanceof java.sql.Date sql)return sql.toLocalDate();
  return value.toInstant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
 }
 public List<PackStatRegion> regions(){return regions.selectEnabledRegions();}
 public Map<String,Object> summary(String packer,String month,PackageActor actor){
  LinkedHashMap<String,Object> result=new LinkedHashMap<>();
  for(int n=7;n<=15;n++){result.put("s"+n,0);result.put("u"+n,0);}
  if(packer==null||packer.isEmpty())return result;
  if(PackStatDateCalc.resolveSummaryAnchor(month,LocalDate.now())==null)return Map.of("noRecord",true);
  Map<String,Object> raw=mapper.selectSurviveSummary(packer,month,actor.tenantId(),actor.administrator()?null:actor.userId());
  if(raw==null)return result;
  for(String key:result.keySet()){
   Object value=raw.get(key);if(value==null)value=raw.get(key.toUpperCase(Locale.ROOT));
   if(value instanceof Number n)result.put(key,n.intValue());
   else if(value!=null){try{result.put(key,Integer.valueOf(value.toString()));}catch(NumberFormatException ignored){}}
  }
  return result;
 }
 private static void hints(PackStat row){
  if(row.getStoreUrl()==null||row.getStoreUrl().isEmpty())return;
  PackStatUrlParser.Parsed parsed=PackStatUrlParser.tryParse(row.getStoreUrl());if(parsed==null)return;
  if(row.getPlatform()==null||row.getPlatform().isEmpty())row.setPlatform(parsed.getPlatform());
  if(row.getRegionCode()==null||row.getRegionCode().isEmpty())row.setRegionCode(parsed.getRegion());
 }
 private static void category(PackStat row){
  String value=row.getCustomerCategory();
  if(value==null||value.isBlank()){row.setCustomerCategory("公司");return;}
  if(!value.equals("CT")&&!value.equals("公司"))throw new PackageFailure(400,"分类无效");
 }
}
