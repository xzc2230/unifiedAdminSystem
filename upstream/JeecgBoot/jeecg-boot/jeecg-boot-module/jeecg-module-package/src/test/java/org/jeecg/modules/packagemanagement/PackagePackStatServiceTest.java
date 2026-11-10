package org.jeecg.modules.packagemanagement;
import java.util.*;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import org.jeecg.modules.packagemanagement.application.packstat.PackStatService;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStat;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageActor;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.*;
class PackagePackStatServiceTest {
 private final PackStatMapper mapper=mock(PackStatMapper.class);private final PackStatRegionMapper regions=mock(PackStatRegionMapper.class);
 private final PackStatService service=new PackStatService(mapper,regions);private final PackageActor actor=new PackageActor(8,"alice",11,false);
 @Test void insertionDefaultsDatesInfersPlatformAndIgnoresClientOwnership(){
  PackStat row=new PackStat();row.setId(900L);row.setTenantId(99);row.setOwnerUserId("bob");row.setStoreUrl("https://apps.apple.com/sg/app/demo/id123456");service.add(row,actor);
  assertNull(row.getId());assertEquals(8,row.getTenantId());assertEquals("alice",row.getOwnerUserId());assertEquals("ios",row.getPlatform());assertEquals("sg",row.getRegionCode());assertEquals(0,row.getIsReplenish());assertNotNull(row.getPassDate());assertNotNull(row.getCustomerUseDate());verify(mapper).insertPackStat(row);
 }
 @Test void batchDeleteRejectsForeignRecordBeforeAnyDelete(){PackStat own=new PackStat();own.setTenantId(8);own.setOwnerUserId("alice");PackStat foreign=new PackStat();foreign.setTenantId(9);foreign.setOwnerUserId("alice");when(mapper.selectPackStatById(1L)).thenReturn(own);when(mapper.selectPackStatById(2L)).thenReturn(foreign);
  assertThrows(RuntimeException.class,()->service.delete(new Long[]{1L,2L},actor));verify(mapper,never()).deletePackStatByIds(any());
 }
 @Test void summaryPassesMonthAndTrustedScopeAndNormalizesKeys(){when(mapper.selectSurviveSummary("demo","2026-09",8,"alice")).thenReturn(Map.of("S7",3,"u7",2));Map<String,Object> result=service.summary("demo","2026-09",actor);assertEquals(3,result.get("s7"));assertEquals(2,result.get("u7"));assertEquals(18,result.size());}
 @Test void futureMonthHasNoRecordAndDoesNotQuery(){assertEquals(Map.of("noRecord",true),service.summary("demo","2999-01",actor));verifyNoInteractions(mapper);}
}
