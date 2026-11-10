package org.jeecg.modules.packagemanagement;
import java.io.*;import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStat;
class PackagePackStatMapperTest {
 private static final String BASE="org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.";
 private Configuration config(){
  Configuration c=new Configuration();
  for(String type:List.of("PackStat","PackStatRegion","PackStatDetectConfig","PackStatDetectHistory")){
   String path=BASE.replace('.','/')+type+"Mapper.xml";
   try(InputStream in=getClass().getClassLoader().getResourceAsStream(path)){assertNotNull(in,path);new XMLMapperBuilder(in,c,path,c.getSqlFragments()).parse();}
   catch(IOException e){throw new UncheckedIOException(e);}
  }return c;
 }
 private String sql(Configuration c,String statement,Object data){return c.getMappedStatement(BASE+"PackStatMapper."+statement).getBoundSql(data).getSql().replaceAll("\\s+"," ").trim().toLowerCase(Locale.ROOT);}
 @Test void allMappersBindAndListHasTrustedScope(){Configuration c=config();PackStat row=new PackStat();row.setTenantId(8);row.setOwnerUserId("alice");
  String sql=sql(c,"selectPackStatList",row);assertTrue(sql.contains("tenant_id = ?"));assertTrue(sql.contains("owner_user_id = ?"));
 }
 @Test void summaryKeepsExclusiveBucketsAndScopesOwner(){Configuration c=config();Map<String,Object> q=new HashMap<>();q.put("tenantId",8);q.put("ownerUserId","alice");q.put("packer","demo");q.put("passMonth","2026-09");
  String sql=sql(c,"selectSurviveSummary",q);assertTrue(sql.contains("tenant_id = ?"));assertTrue(sql.contains("owner_user_id = ?"));
  for(int n=7;n<15;n++)assertTrue(sql.contains(">= "+n+" and"));assertTrue(sql.contains("< 15"));assertTrue(sql.contains(">= 15 then"));assertTrue(sql.contains("and pass_date >= str_to_date"));
 }
 @Test void updatingKeepsTenantAndOwnerInPredicate(){Configuration c=config();PackStat row=new PackStat();row.setTenantId(8);row.setOwnerUserId("alice");row.setId(1L);
  String sql=sql(c,"updatePackStat",row);assertTrue(sql.contains("where id = ? and tenant_id = ?"));assertTrue(sql.contains("and owner_user_id = ?"));
 }
}
