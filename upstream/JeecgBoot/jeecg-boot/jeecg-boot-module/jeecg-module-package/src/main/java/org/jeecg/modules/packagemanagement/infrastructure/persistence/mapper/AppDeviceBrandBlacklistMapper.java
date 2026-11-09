package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;

import org.jeecg.modules.packagemanagement.domain.app.AppDeviceBrandBlacklist;

import java.util.List;

@org.apache.ibatis.annotations.Mapper
public interface AppDeviceBrandBlacklistMapper {

    AppDeviceBrandBlacklist selectAppDeviceBrandBlacklistById(Long id);

    List<AppDeviceBrandBlacklist> selectAppDeviceBrandBlacklistList(AppDeviceBrandBlacklist query);

    int insertAppDeviceBrandBlacklist(AppDeviceBrandBlacklist row);

    int updateAppDeviceBrandBlacklist(AppDeviceBrandBlacklist row);

    int deleteAppDeviceBrandBlacklistById(Long id);

    int deleteAppDeviceBrandBlacklistByIds(Long[] ids);
}
