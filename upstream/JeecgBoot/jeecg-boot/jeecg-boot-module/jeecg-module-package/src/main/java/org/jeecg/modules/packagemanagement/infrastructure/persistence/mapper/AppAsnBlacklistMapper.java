package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;

import org.jeecg.modules.packagemanagement.domain.app.AppAsnBlacklist;

import java.util.List;

@org.apache.ibatis.annotations.Mapper
public interface AppAsnBlacklistMapper {

    AppAsnBlacklist selectAppAsnBlacklistById(Long id);

    List<AppAsnBlacklist> selectAppAsnBlacklistList(AppAsnBlacklist query);

    int insertAppAsnBlacklist(AppAsnBlacklist row);

    int updateAppAsnBlacklist(AppAsnBlacklist row);

    int deleteAppAsnBlacklistById(Long id);

    int deleteAppAsnBlacklistByIds(Long[] ids);
}
