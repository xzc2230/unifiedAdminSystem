package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;


import org.jeecg.modules.packagemanagement.domain.event.EventConfig;

import java.util.List;

/**
 * fb_configMapper接口
 *
 * @author ruoyi
 * @date 2024-04-16
 */
public interface EventConfigMapper
{
    /**
     * 查询fb_config
     *
     * @param id fb_config主键
     * @return fb_config
     */
    public EventConfig selectEventConfigById(Long id);

    public EventConfig selectEventConfigByPackageId(String packageId);

    /**
     * 查询fb_config列表
     *
     * @param eventConfig fb_config
     * @return fb_config集合
     */
    public List<EventConfig> selectEventConfigList(EventConfig eventConfig);

    /**
     * 新增fb_config
     *
     * @param eventConfig fb_config
     * @return 结果
     */
    public int insertEventConfig(EventConfig eventConfig);

    /**
     * 修改fb_config
     *
     * @param eventConfig fb_config
     * @return 结果
     */
    public int updateEventConfig(EventConfig eventConfig);

    /**
     * 删除fb_config
     *
     * @param id fb_config主键
     * @return 结果
     */
    public int deleteEventConfigById(Long id);

    /**
     * 批量删除fb_config
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteEventConfigByIds(Long[] ids);
}
