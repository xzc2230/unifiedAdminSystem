package org.jeecg.modules.packagemanagement.application.openapi;

import java.time.Clock;
import java.util.*;
import org.jeecg.modules.packagemanagement.domain.openapi.OpenApiToken;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.OpenApiTokenMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenApiTokenService {
 private final OpenApiTokenMapper mapper;
 private final PackageIdentityRepository identities;
 private final Clock clock;
 public OpenApiTokenService(OpenApiTokenMapper mapper,PackageIdentityRepository identities,@Qualifier("packageClock") Clock clock) {this.mapper=mapper;this.identities=identities;this.clock=clock;}
 public OpenApiToken valid(String raw) {
  if(raw==null||raw.isBlank())return null;
  OpenApiToken row=mapper.byToken(raw.trim());
  if(row==null||!Integer.valueOf(1).equals(row.getStatus())||row.getTenantId()==null||row.getOwnerUserId()==null
    ||row.getExpireTime()!=null&&row.getExpireTime().before(Date.from(clock.instant())))return null;
  return identities.activeMember(row.getTenantId(),row.getOwnerUserId())?row:null;
 }
 /** Metadata only for audit attribution; never authorizes a request. */
 public OpenApiToken auditCandidate(String raw){return raw==null||raw.isBlank()?null:mapper.byToken(raw.trim());}
 public List<OpenApiToken> list(PackageActor actor,OpenApiToken filter) {
  filter.setTenantId(actor.tenantId());filter.setOwnerUserId(actor.administrator()?null:actor.userId());return mapper.list(filter);
 }
 public OpenApiToken detail(PackageActor actor,long id) {
  OpenApiToken row=mapper.byId(id);require(actor,row);return row;
 }
 private void require(PackageActor actor,OpenApiToken row) {
  if(row==null)throw new PackageFailure(404,"Token不存在");
  if(!Objects.equals(row.getTenantId(),actor.tenantId())||!actor.administrator()&&!Objects.equals(row.getOwnerUserId(),actor.userId()))throw new PackageFailure(403,"Token不属于当前租户和用户");
 }
 @Transactional public OpenApiToken create(PackageActor actor,OpenApiToken row) {
  row.setId(null);row.setTenantId(actor.tenantId());row.setOwnerUserId(actor.userId());
  row.setToken(row.getToken()==null||row.getToken().isBlank()?UUID.randomUUID().toString().replace("-",""):row.getToken().trim());
  if(row.getStatus()==null)row.setStatus(1);validate(row);row.setCreateBy(actor.userId());row.setCreateTime(Date.from(clock.instant()));mapper.insert(row);return row;
 }
 @Transactional public int update(PackageActor actor,OpenApiToken row) {
  if(row.getId()==null)throw new PackageFailure(400,"缺少Token ID");OpenApiToken previous=detail(actor,row.getId());
  row.setTenantId(previous.getTenantId());row.setOwnerUserId(previous.getOwnerUserId());
  if(row.getToken()!=null)row.setToken(row.getToken().trim());validate(row);
  row.setUpdateBy(actor.userId());row.setUpdateTime(Date.from(clock.instant()));return mapper.update(row);
 }
 @Transactional public int delete(PackageActor actor,List<Long> ids) {
  if(ids.isEmpty())return 0;for(Long id:ids)detail(actor,id);
  int count=0;for(Long id:ids)count+=mapper.delete(id,actor.tenantId());return count;
 }
 private void validate(OpenApiToken row) {
  if(row.getToken()!=null&&row.getToken().length()>128)throw new PackageFailure(400,"Token长度不能超过128");
  if(row.getStatus()!=null&&row.getStatus()!=0&&row.getStatus()!=1)throw new PackageFailure(400,"Token状态无效");
 }
}
