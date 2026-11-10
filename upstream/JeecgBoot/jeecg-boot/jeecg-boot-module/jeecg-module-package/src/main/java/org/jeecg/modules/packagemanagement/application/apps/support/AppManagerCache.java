package org.jeecg.modules.packagemanagement.application.apps.support;

import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppManagerMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Source mirrors are written after commit; public lookups always read the database. */
@Service
public class AppManagerCache {
 private final StringRedisTemplate redis;private final AppManagerMapper apps;
 public AppManagerCache(StringRedisTemplate redis,AppManagerMapper apps){this.redis=redis;this.apps=apps;}
 public void refresh(String code){
  if(code==null||code.isBlank())return;String normalized=code.trim();
  Runnable action=()->{
   try{var row=apps.selectAppManagerByPackageName(normalized);String key="package:legacy:app_manager:package:"+normalized;
    if(row==null)redis.delete(key);else redis.opsForValue().set(key,com.alibaba.fastjson2.JSON.toJSONString(row));
   }catch(RuntimeException failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Package cache mirror refresh failed",failure);}
  };
  if(TransactionSynchronizationManager.isSynchronizationActive()&&TransactionSynchronizationManager.isActualTransactionActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){action.run();}});
  else action.run();
 }
}
