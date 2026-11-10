package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStat;

@org.apache.ibatis.annotations.Mapper
public interface PackStatMapper
{
    PackStat selectPackStatById(Long id);

    List<PackStat> selectPackStatList(PackStat query);

    int insertPackStat(PackStat row);

    int updatePackStat(PackStat row);

    int deletePackStatById(Long id);

    int deletePackStatByIds(Long[] ids);

    List<String> selectDistinctPackers(PackStat scope);

    Map<String, Object> selectSurviveSummary(@Param("packer") String packer,
            @Param("passMonth") String passMonth, @Param("tenantId") int tenantId, @Param("ownerUserId") String ownerUserId);

    List<PackStat> selectPendingDetectList();

    int updateDropDateIfAbsent(@Param("id") Long id, @Param("dropDate") Date dropDate);

    /**
     * Refresh durations after each valid detect. When dropDate is non-null and current
     * drop_date is null, also write drop_date.
     */
    int updateDetectStats(@Param("id") Long id, @Param("dropDate") Date dropDate,
            @Param("surviveDays") Integer surviveDays, @Param("useDays") Integer useDays);
}
