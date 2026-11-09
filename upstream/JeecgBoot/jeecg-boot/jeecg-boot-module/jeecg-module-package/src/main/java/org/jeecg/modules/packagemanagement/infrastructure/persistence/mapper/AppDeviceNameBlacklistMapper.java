package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;

import org.jeecg.modules.packagemanagement.domain.app.AppDeviceNameBlacklist;

import java.util.List;

@org.apache.ibatis.annotations.Mapper
public interface AppDeviceNameBlacklistMapper {

    AppDeviceNameBlacklist selectAppDeviceNameBlacklistById(Long id);

    List<AppDeviceNameBlacklist> selectAppDeviceNameBlacklistList(AppDeviceNameBlacklist query);

    int insertAppDeviceNameBlacklist(AppDeviceNameBlacklist row);

    int updateAppDeviceNameBlacklist(AppDeviceNameBlacklist row);

    int deleteAppDeviceNameBlacklistById(Long id);

    int deleteAppDeviceNameBlacklistByIds(Long[] ids);
}
