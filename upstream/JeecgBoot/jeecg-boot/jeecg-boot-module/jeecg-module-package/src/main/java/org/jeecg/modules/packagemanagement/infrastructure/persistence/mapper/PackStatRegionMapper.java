package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;

import java.util.List;
import org.jeecg.modules.packagemanagement.domain.packstat.PackStatRegion;

@org.apache.ibatis.annotations.Mapper
public interface PackStatRegionMapper
{
    List<PackStatRegion> selectEnabledRegions();

    PackStatRegion selectByCode(String code);
}
