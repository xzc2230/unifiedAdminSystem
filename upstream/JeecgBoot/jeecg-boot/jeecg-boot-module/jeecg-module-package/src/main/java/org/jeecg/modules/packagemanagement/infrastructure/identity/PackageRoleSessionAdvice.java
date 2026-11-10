package org.jeecg.modules.packagemanagement.infrastructure.identity;

import java.util.Map;
import com.alibaba.fastjson2.JSONObject;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.jeecg.common.api.vo.Result;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;

@Aspect
@Component
public class PackageRoleSessionAdvice {
 private final PackageRoleSessionInvalidator sessions;
 public PackageRoleSessionAdvice(PackageRoleSessionInvalidator sessions){this.sessions=sessions;}
 @AfterReturning(pointcut="execution(* org.jeecg.modules.system.controller.SysPermissionController.saveRolePermission(..)) || execution(* org.jeecg.modules.system.controller.SysRoleController.edit(..))",returning="result")
 public void roleChanged(JoinPoint point,Object result){
  if(!(result instanceof Result<?> response)||!response.isSuccess())return;
  Object[] args=point.getArgs();if(args.length==0||args[0]==null)return;Object value=args[0];String roleId;
  if(value instanceof JSONObject json)roleId=json.getString("roleId");
  else if(value instanceof Map<?,?> map)roleId=String.valueOf(map.get("roleId"));
  else {var bean=new org.springframework.beans.BeanWrapperImpl(value);roleId=bean.isReadableProperty("id")?String.valueOf(bean.getPropertyValue("id")):null;}
  var attributes=RequestContextHolder.getRequestAttributes();String token=null;
  if(attributes instanceof ServletRequestAttributes servlet)token=servlet.getRequest().getHeader("X-Access-Token");
  sessions.invalidateRole(roleId,token);
 }
}
