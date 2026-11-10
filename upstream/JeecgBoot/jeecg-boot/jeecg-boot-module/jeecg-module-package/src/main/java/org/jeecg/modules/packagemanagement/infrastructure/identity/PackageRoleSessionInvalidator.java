package org.jeecg.modules.packagemanagement.infrastructure.identity;

import java.util.*;
import java.util.function.Function;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.system.util.JwtUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Adapts source role-change logout to JEECG's token registry, retaining the editing session. */
@Component
public class PackageRoleSessionInvalidator {
 private final JdbcTemplate jdbc;private final StringRedisTemplate redis;private final Function<String,String> username;
 @org.springframework.beans.factory.annotation.Autowired
 public PackageRoleSessionInvalidator(JdbcTemplate jdbc,StringRedisTemplate redis){this(jdbc,redis,JwtUtil::getUsername);}
 public PackageRoleSessionInvalidator(JdbcTemplate jdbc,StringRedisTemplate redis,Function<String,String> username){this.jdbc=jdbc;this.redis=redis;this.username=username;}
 public void invalidateRole(String roleId,String keepToken){
  if(roleId==null||roleId.isBlank())return;
  Set<String> users=new HashSet<>(jdbc.queryForList("SELECT DISTINCT u.username FROM sys_user u JOIN sys_user_role r ON r.user_id=u.id WHERE r.role_id=?",String.class,roleId));
  if(users.isEmpty())return;
  var options=org.springframework.data.redis.core.ScanOptions.scanOptions().match(CommonConstant.PREFIX_USER_TOKEN+"*").count(200).build();
  try(var keys=redis.scan(options)){
   while(keys.hasNext()){
    String key=keys.next();String token=key.substring(CommonConstant.PREFIX_USER_TOKEN.length());if(token.equals(keepToken))continue;
    String user;try{user=username.apply(token);}catch(RuntimeException malformed){continue;}
    if(users.contains(user))redis.delete(key);
   }
  }
  // Host ShiroConfig uses principalIdFieldName="id" for authorization cache keys.
  List<String> ids=jdbc.queryForList("SELECT DISTINCT user_id FROM sys_user_role WHERE role_id=?",String.class,roleId);
  for(String id:ids)redis.delete(CommonConstant.PREFIX_USER_SHIRO_CACHE+id);

 }
}
