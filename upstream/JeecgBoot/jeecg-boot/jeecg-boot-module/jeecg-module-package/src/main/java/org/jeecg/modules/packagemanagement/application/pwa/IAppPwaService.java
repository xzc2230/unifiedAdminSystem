package org.jeecg.modules.packagemanagement.application.pwa;

import org.jeecg.modules.packagemanagement.application.legacy.LegacyAjaxResult;
import org.jeecg.modules.packagemanagement.domain.pwa.AppPwa;

import java.util.List;

/**
 * pwaService接口
 *
 * @author Murphy
 * @date 2024-08-09
 */
public interface IAppPwaService
{
    /**
     * 查询pwa
     *
     * @param id pwa主键
     * @return pwa
     */
    public AppPwa selectAppPwaById(Long id);

    /**
     * 查询pwa列表
     *
     * @param appPwa pwa
     * @return pwa集合
     */
    public List<AppPwa> selectAppPwaList(AppPwa appPwa);
    /**
     * app查询pwa列表
     *
     * @param appPwa pwa
     * @return pwa集合
     */
    public List<AppPwa> selectAppPwaAppList(AppPwa appPwa);

    /**
     * 新增pwa
     *
     * @param appPwa pwa
     * @return 结果
     */
    public LegacyAjaxResult insertAppPwa(AppPwa appPwa);

    /**
     * 修改pwa
     *
     * @param appPwa pwa
     * @return 结果
     */
    public LegacyAjaxResult updateAppPwa(AppPwa appPwa);

    /**
     * 批量删除pwa
     *
     * @param ids 需要删除的pwa主键集合
     * @return 结果
     */
    public int deleteAppPwaByIds(Long[] ids);

    /**
     * 删除pwa信息
     *
     * @param id pwa主键
     * @return 结果
     */
    public int deleteAppPwaById(Long id);
}
