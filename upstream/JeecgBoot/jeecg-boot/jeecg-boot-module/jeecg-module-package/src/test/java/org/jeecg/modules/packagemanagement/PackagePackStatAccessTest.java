package org.jeecg.modules.packagemanagement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.jeecg.modules.packagemanagement.application.packstat.PackStatAccess;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStat;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageActor;
class PackagePackStatAccessTest {
 @Test void ownerScopeCannotBeSuppliedByClient(){
  PackStat query=new PackStat(); query.setTenantId(99);query.setOwnerUserId("other");
  PackStatAccess.scope(query,new PackageActor(8,"alice",11,false));
  assertEquals(8,query.getTenantId());assertEquals("alice",query.getOwnerUserId());
 }
 @Test void administratorStillHasTenantBoundary(){
  PackStat query=new PackStat();PackStatAccess.scope(query,new PackageActor(8,"alice",11,true));
  assertEquals(8,query.getTenantId());assertNull(query.getOwnerUserId());
  PackStat row=new PackStat();row.setTenantId(9);row.setOwnerUserId("alice");
  assertThrows(RuntimeException.class,()->PackStatAccess.require(row,new PackageActor(8,"alice",11,true)));
 }
 @Test void missingOrOtherOwnerCannotBeRead(){
  var actor=new PackageActor(8,"alice",11,false);PackStat row=new PackStat();row.setTenantId(8);row.setOwnerUserId("bob");
  assertThrows(RuntimeException.class,()->PackStatAccess.require(row,actor));
  assertThrows(RuntimeException.class,()->PackStatAccess.require(null,actor));
 }
}
