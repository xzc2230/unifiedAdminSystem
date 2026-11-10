package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;

import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.apache.ibatis.annotations.Param;

import java.util.List;


/**
 * 【请填写功能名称】Mapper接口
 *
 * @author ruoyi
 * @date 2023-10-19
 */
public interface AppManagerMapper {
    /**
     * 查询【请填写功能名称】
     *
     * @param id 【请填写功能名称】主键
     * @return 【请填写功能名称】
     */
    public AppManager selectAppManagerById(Long id);

    public AppManager selectAppManagerByPackageName(String packageName);

    public AppManager selectAppManagerByPlatformAndPackage(@Param("platform") String platform, @Param("packageCode") String packageCode);

    /**
     * 查询【请填写功能名称】列表
     *
     * @param appManager 【请填写功能名称】
     * @return 【请填写功能名称】集合
     */
    public List<AppManager> selectAppManagerList(AppManager appManager);

    /**
     * 新增【请填写功能名称】
     *
     * @param appManager 【请填写功能名称】
     * @return 结果
     */
    public int insertAppManager(AppManager appManager);

    /**
     * 修改【请填写功能名称】
     *
     * @param appManager 【请填写功能名称】
     * @return 结果
     */
    public int updateAppManager(AppManager appManager);

    /**
     * 删除【请填写功能名称】
     *
     * @param id 【请填写功能名称】主键
     * @return 结果
     */
    public int deleteAppManagerById(Long id);

    /**
     * 批量删除【请填写功能名称】
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAppManagerByIds(Long[] ids);

    public int transferAppManagerById(@Param("id") String id, @Param("userId") Integer userId, @Param("userName") String userName);
}
