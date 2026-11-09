package org.jeecg.modules.packagemanagement;

import org.jeecg.modules.packagemanagement.infrastructure.identity.*;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PackageIdentityTest {
 @Test void spoofedTenantAndUnmappedIdentityAreDenied(){
  PackageIdentityRepository repository=mock(PackageIdentityRepository.class);
  PackageAccess access=new PackageAccess(repository);
  assertThrows(PackageFailure.class,()->access.resolve("u","2",false));
  when(repository.activeMember(1,"u")).thenReturn(true);
  assertThrows(PackageFailure.class,()->access.resolve("u","1",false));
  when(repository.legacyIds(1,"u")).thenReturn(List.of(17L));
  assertEquals(17L,access.resolve("u","1",false).legacyUserId());
 }
 @Test void adminPermissionNeverBypassesTenantOrMissingOwner(){
  PackageIdentityRepository repository=mock(PackageIdentityRepository.class);PackageAccess access=new PackageAccess(repository);
  PackageActor actor=new PackageActor(1,"u",17L,true);
  when(repository.activeMember(1,"u")).thenReturn(true);
  when(repository.ownedIds(actor,"pkg_pwa")).thenReturn(Set.of(10L));
  assertThrows(PackageFailure.class,()->access.requireRows(actor,"pkg_pwa",List.of(10L,11L)));
  verify(repository,never()).transfer(any(),anyString(),anyCollection(),anyString());
  assertEquals(List.of(10L),access.filterIds(actor,"pkg_pwa",List.of(10L,11L)));
 }
 @Test void transferValidatesEntireBatchBeforeAnyWrite(){
  PackageIdentityRepository repository=mock(PackageIdentityRepository.class);PackageAccess access=new PackageAccess(repository);
  PackageActor actor=new PackageActor(1,"u",17L,false);
  when(repository.activeMember(1,"u")).thenReturn(true);
  when(repository.ownedIds(actor,"pkg_app_manager")).thenReturn(Set.of(10L));
  assertThrows(PackageFailure.class,()->access.transfer(actor,"pkg_app_manager",List.of(10L,11L),"other"));
  verify(repository,never()).transfer(any(),anyString(),anyCollection(),anyString());
 }
}
