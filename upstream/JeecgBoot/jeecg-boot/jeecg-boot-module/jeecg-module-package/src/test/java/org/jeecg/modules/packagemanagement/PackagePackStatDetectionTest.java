package org.jeecg.modules.packagemanagement;
import java.time.LocalDate;import java.util.*;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.jeecg.modules.packagemanagement.application.packstat.detect.*;
import org.jeecg.modules.packagemanagement.domain.packstat.*;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.*;
class PackagePackStatDetectionTest {
 private final PackStatMapper rows=mock(PackStatMapper.class);private final PackStatDetectConfigMapper configs=mock(PackStatDetectConfigMapper.class);
 private final PackStatDetectHistoryMapper histories=mock(PackStatDetectHistoryMapper.class);private final PackStatMailNotifier mail=mock(PackStatMailNotifier.class);
 private final PackStatHttpClient http=mock(PackStatHttpClient.class);private final PackStatDetectService service=new PackStatDetectService();
 private PackStat setup(PackStatDetectResult result){
  ReflectionTestUtils.setField(service,"packStatMapper",rows);ReflectionTestUtils.setField(service,"detectConfigMapper",configs);ReflectionTestUtils.setField(service,"detectHistoryMapper",histories);ReflectionTestUtils.setField(service,"mailNotifier",mail);ReflectionTestUtils.setField(service,"httpClient",http);
  PackStatDetectConfig config=new PackStatDetectConfig();config.setId(1L);config.setApiIntervalSeconds(0);config.setThreadCount(1);when(configs.selectOne()).thenReturn(config);
  PackStat row=new PackStat();row.setId(10L);row.setStoreUrl("http://127.0.0.1/store");row.setPassDate(java.sql.Date.valueOf(LocalDate.now().minusDays(9)));row.setCustomerUseDate(java.sql.Date.valueOf(LocalDate.now().minusDays(7)));when(rows.selectPendingDetectList()).thenReturn(List.of(row));when(http.probe(row.getStoreUrl())).thenReturn(new PackStatHttpClient.ProbeOutcome(result,result==PackStatDetectResult.DELISTED?404:200,"test"));return row;
 }
 @Test void invalidResultKeepsDatesButRecordsHistoryAndRaisesDelay(){setup(PackStatDetectResult.INVALID);service.runRound();verify(rows,never()).updateDetectStats(anyLong(),any(),any(),any());verifyNoInteractions(mail);verify(histories).insertDetectHistory(argThat(h->h.getDetectResult().equals("INVALID")));verify(histories).deleteOlderThanDays(5);verify(configs).updateApiIntervalSeconds(argThat(c->c.getApiIntervalSeconds()==1));}
 @Test void normalRefreshesDurationsWithoutDropOrMail(){setup(PackStatDetectResult.NORMAL);service.runRound();verify(rows).updateDetectStats(eq(10L),isNull(),eq(9),eq(7));verifyNoInteractions(mail);}
 @Test void delistedRefreshesDurationsAndNotifiesOnceForPendingRow(){PackStat row=setup(PackStatDetectResult.DELISTED);service.runRound();verify(rows).updateDetectStats(eq(10L),notNull(),eq(9),eq(7));verify(mail).notifyDelisted(eq(row),any(),any(),eq("test"));}
}
