package org.jeecg.modules.packagemanagement.application.openapi;

import java.util.*;
import org.jeecg.modules.packagemanagement.application.apps.IAppManagerService;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.domain.openapi.OpenApiToken;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.StoreStatusMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.OpenApiManagerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Token identity is independent of the logged-in management user. */
@Service
public class OpenApiManagerService {
 private final IAppManagerService apps;private final StoreStatusMapper stores;private final PackageAccess access;private final OpenApiManagerMapper lockedApps;
 public OpenApiManagerService(IAppManagerService apps,StoreStatusMapper stores,PackageAccess access,OpenApiManagerMapper lockedApps){this.apps=apps;this.stores=stores;this.access=access;this.lockedApps=lockedApps;}
 public PackageActor actor(OpenApiToken token){return new PackageActor(token.getTenantId(),token.getOwnerUserId(),0L,false);}
 public List<AppManager> list(OpenApiToken token,AppManager query){
  query.setUserId(token.getId());query.setUserName("api");
  return access.filter(actor(token),"pkg_app_manager",apps.selectAppManagerList(query),AppManager::getId);
 }
 public AppManager detail(OpenApiToken token,long id){AppManager row=apps.selectAppManagerById(id);requireOwned(token,row);access.requireRow(actor(token),"pkg_app_manager",id);return row;}
 private void requireOwned(OpenApiToken token,AppManager row){
  if(row==null)throw new PackageFailure(404,"包不存在");
  if(!Objects.equals(token.getId(),row.getUserId())||!"api".equals(row.getUserName()))throw new PackageFailure(403,"无权操作该包");
 }
 @Transactional public int create(OpenApiToken token,AppManager row){
  row.setId(null);row.setPackageCode(row.getPackageCode()==null?"":row.getPackageCode().trim());
  if(row.getPackageCode().isEmpty())throw new PackageFailure(400,"包名不能为空");
  if(apps.selectAppManagerByPackageName(row.getPackageCode())!=null)return -1;
  PackageActor actor=actor(token);StoreStatus previous=stores.selectStoreStatusByPackageName(row.getPackageCode());
  if(previous!=null)requireStoreTenant(actor,previous.getId());
  row.setUserId(token.getId());row.setUserName("api");int count=apps.insertAppManager(row);
  if(count>0){
   if(row.getId()==null){AppManager saved=apps.selectAppManagerByPackageName(row.getPackageCode());if(saved!=null)row.setId(saved.getId());}
   if(row.getId()==null)throw new IllegalStateException("App insert did not return an ID");
   access.claimCreated(actor,"pkg_app_manager",row.getId());
   if(previous==null){StoreStatus created=stores.selectStoreStatusByPackageName(row.getPackageCode());if(created!=null)access.claimCreated(actor,"pkg_store_status",created.getId());}
  }return count;
 }
 @Transactional public int update(OpenApiToken token,AppManager row){
  if(row.getId()==null)throw new PackageFailure(400,"包不存在");AppManager previous=detail(token,row.getId());PackageActor actor=actor(token);
  access.lockRows(actor,"pkg_app_manager",List.of(row.getId()));previous=lockedApps.lockById(row.getId());requireOwned(token,previous);
  if(row.getPackageCode()!=null&&!row.getPackageCode().trim().equals(previous.getPackageCode()))throw new PackageFailure(400,"包名不可修改");
  row.setPackageCode(previous.getPackageCode());row.setUserId(token.getId());row.setUserName("api");
  StoreStatus oldStore=stores.selectStoreStatusByPackageName(row.getPackageCode());
  if(oldStore!=null)requireStoreTenant(actor,oldStore.getId());
  int changed=apps.updateAppManager(row);
  if(oldStore==null){StoreStatus created=stores.selectStoreStatusByPackageName(row.getPackageCode());if(created!=null)access.claimCreated(actor,"pkg_store_status",created.getId());}
  return changed;
 }
 private void requireStoreTenant(PackageActor actor,long storeId){if(!Objects.equals(lockedApps.lockStoreTenant(storeId,actor.tenantId()),storeId))throw new PackageFailure(403,"关联商店状态不属于当前租户");}
 @Transactional public int delete(OpenApiToken token,List<Long> ids){
  if(ids.isEmpty())return 0;for(Long id:ids)detail(token,id);PackageActor actor=actor(token);access.lockRows(actor,"pkg_app_manager",ids);for(Long id:ids)requireOwned(token,lockedApps.lockById(id));
  int n=apps.deleteAppManagerByIds(ids.toArray(Long[]::new));access.removeOwners(actor,"pkg_app_manager",ids);return n;
 }
}
