package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;

import org.jeecg.modules.packagemanagement.domain.packstat.PackStatDetectHistory;

@org.apache.ibatis.annotations.Mapper
public interface PackStatDetectHistoryMapper
{
    int insertDetectHistory(PackStatDetectHistory row);

    int deleteOlderThanDays(int days);
}
