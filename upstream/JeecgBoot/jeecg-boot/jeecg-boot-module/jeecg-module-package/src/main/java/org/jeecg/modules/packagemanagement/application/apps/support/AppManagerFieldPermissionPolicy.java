package org.jeecg.modules.packagemanagement.application.apps.support;

import java.util.*;
import java.util.function.Predicate;
import org.apache.shiro.SecurityUtils;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.springframework.beans.BeanWrapperImpl;

/** Administrative field grants only. Public client/Token services never use Shiro field grants. */
public final class AppManagerFieldPermissionPolicy {
 public static final List<String> EDIT=List.of("appName","author","platform","appleAppId","storeRegion","packageCode","url","status","whetherRestrict","areas","mark","whetherUs","gaidAidThreshold","aidDeviceThreshold","aidGaidSwitchThreshold","riskWindowSeconds","referrerBlacklistJson");
 public static final List<String> QUERY=List.of("appName","author","userName","platform","appleAppId","storeRegion","packageCode","status","url","whetherRestrict","areas","whetherUs","mark","updateDate");
 private static final Set<String> LISTING=Set.of("platform","appleAppId","storeRegion");
 private final Predicate<String> granted;
 public AppManagerFieldPermissionPolicy(){this(p->SecurityUtils.getSubject().isPermitted(p));}
 public AppManagerFieldPermissionPolicy(Predicate<String> granted){this.granted=granted;}
 private boolean full(String action,List<String> fields){
  if(granted.test("package:*"))return true;
  return granted.test("package:apps:"+(action.equals("editField")?"edit":"query"))&&fields.stream().noneMatch(f->granted.test("package:apps:"+action+":"+f));
 }
 public void check(AppManager request,AppManager stored){
  if(full("editField",EDIT))return;
  var requested=new BeanWrapperImpl(request);var existing=new BeanWrapperImpl(stored);
  List<String> denied=new ArrayList<>();
  for(String field:EDIT){Object v=requested.getPropertyValue(field);
   if(v!=null&&!Objects.equals(v,existing.getPropertyValue(field))&&!(LISTING.contains(field)&&granted.test("package:apps:edit"))&&!granted.test("package:apps:editField:"+field))denied.add(field);
  }
  // whetherUse is outside the source field rules: it remains controlled by module edit.
  if(!denied.isEmpty())throw new PackageFailure(403,"无权修改字段："+String.join("、",denied));
 }
 public void mask(AppManager row){
  if(full("queryField",QUERY))return;
  var bean=new BeanWrapperImpl(row);
  for(String field:QUERY)if(!(LISTING.contains(field)&&granted.test("package:apps:query"))&&!granted.test("package:apps:queryField:"+field))bean.setPropertyValue(field,null);
 }
}
