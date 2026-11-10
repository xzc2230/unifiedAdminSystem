package org.jeecg.modules.packagemanagement.application.packstat;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStat;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageActor;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
/** New module records carry their own tenant/owner columns; these values never bind from public requests. */
public final class PackStatAccess {
 private PackStatAccess(){}
 public static void scope(PackStat query,PackageActor actor){query.setTenantId(actor.tenantId());query.setOwnerUserId(actor.administrator()?null:actor.userId());}
 public static PackStat require(PackStat row,PackageActor actor){
  if(row==null||row.getTenantId()==null||row.getTenantId()!=actor.tenantId()||(!actor.administrator()&&!actor.userId().equals(row.getOwnerUserId())))
   throw new PackageFailure(403,"包统计记录不属于当前租户或用户");
  return row;
 }
}
