package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;

import org.jeecg.modules.packagemanagement.domain.pwa.AppPwaSub;

import java.util.List;


/**
 * pwaMapper接口
 *
 * @author Murphy
 * @date 2024-08-09
 */
public interface AppPwaSubMapper
{
    /**
     * 查询pwa
     *
     * @param id pwa主键
     * @return pwa
     */
    public AppPwaSub selectAppPwaSubById(Long id);

    /**
     * 查询pwa列表
     *
     * @param appPwaSub pwa
     * @return pwa集合
     */
    public List<AppPwaSub> selectAppPwaSubList(AppPwaSub appPwaSub);

    /**
     * 新增pwa
     *
     * @param appPwaSub pwa
     * @return 结果
     */
    public int insertAppPwaSub(AppPwaSub appPwaSub);

    /**
     * 修改pwa
     *
     * @param appPwaSub pwa
     * @return 结果
     */
    public int updateAppPwaSub(AppPwaSub appPwaSub);

    /**
     * 删除pwa
     *
     * @param id pwa主键
     * @return 结果
     */
    public int deleteAppPwaSubById(Long id);

    /**
     * 批量删除pwa
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAppPwaSubByIds(Long[] ids);
}
