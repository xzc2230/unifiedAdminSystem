package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;

import org.jeecg.modules.packagemanagement.domain.packstat.PackStatDetectConfig;

@org.apache.ibatis.annotations.Mapper
public interface PackStatDetectConfigMapper
{
    PackStatDetectConfig selectOne();

    int insertDetectConfig(PackStatDetectConfig row);

    int updateApiIntervalSeconds(PackStatDetectConfig row);
}
