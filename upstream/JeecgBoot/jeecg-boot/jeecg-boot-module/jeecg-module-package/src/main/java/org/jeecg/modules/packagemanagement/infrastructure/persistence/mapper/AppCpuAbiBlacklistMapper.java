package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;

import org.jeecg.modules.packagemanagement.domain.app.AppCpuAbiBlacklist;

import java.util.List;

@org.apache.ibatis.annotations.Mapper
public interface AppCpuAbiBlacklistMapper {

    AppCpuAbiBlacklist selectAppCpuAbiBlacklistById(Long id);

    List<AppCpuAbiBlacklist> selectAppCpuAbiBlacklistList(AppCpuAbiBlacklist query);

    int insertAppCpuAbiBlacklist(AppCpuAbiBlacklist row);

    int updateAppCpuAbiBlacklist(AppCpuAbiBlacklist row);

    int deleteAppCpuAbiBlacklistById(Long id);

    int deleteAppCpuAbiBlacklistByIds(Long[] ids);
}
