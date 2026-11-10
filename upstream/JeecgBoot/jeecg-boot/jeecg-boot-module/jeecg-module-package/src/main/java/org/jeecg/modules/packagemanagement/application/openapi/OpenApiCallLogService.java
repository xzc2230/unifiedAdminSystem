package org.jeecg.modules.packagemanagement.application.openapi;
import java.time.Clock;
import java.util.*;
import org.jeecg.modules.packagemanagement.domain.openapi.*;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageActor;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.OpenApiCallLogMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
@Service
public class OpenApiCallLogService {
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(OpenApiCallLogService.class);
 private final OpenApiCallLogMapper mapper;private final Clock clock;
 public OpenApiCallLogService(OpenApiCallLogMapper mapper,@Qualifier("packageClock") Clock clock){this.mapper=mapper;this.clock=clock;}
 public List<OpenApiCallLog> list(PackageActor actor,OpenApiCallLog query){validateDate(query.getBeginTime());validateDate(query.getEndTime());query.setTenantId(actor.tenantId());query.setOwnerUserId(actor.administrator()?null:actor.userId());return mapper.list(query);}
 public void record(String path,String request,String response,OpenApiToken token){
  try {OpenApiCallLog row=new OpenApiCallLog();row.setApiPath(cut(path,255));row.setRequestBody(cut(request,16000));row.setResponseBody(cut(response,16000));row.setCallTime(Date.from(clock.instant()));
   if(token!=null){row.setToken(token.getToken());row.setTokenId(token.getId());row.setTenantId(token.getTenantId());row.setOwnerUserId(token.getOwnerUserId());}mapper.insert(row);
  } catch(Exception e){LOG.warn("OpenAPI call log insert failed: {}",e.getClass().getSimpleName());}
 }
 private void validateDate(String value){if(value==null||value.isBlank())return;try{java.time.LocalDate.parse(value);}catch(java.time.format.DateTimeParseException e){throw new org.jeecg.modules.packagemanagement.exception.PackageFailure(400,"日期格式应为yyyy-MM-dd");}}
 private String cut(String value,int limit){return value==null?null:value.length()<=limit?value:value.substring(0,limit)+"...(truncated)";}
}
