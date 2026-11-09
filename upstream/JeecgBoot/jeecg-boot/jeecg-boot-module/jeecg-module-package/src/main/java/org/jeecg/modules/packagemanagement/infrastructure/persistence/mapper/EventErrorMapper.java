package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;


import org.jeecg.modules.packagemanagement.domain.event.EventError;

import java.util.List;

/**
 * event_errorMapper接口
 *
 * @author ruoyi
 * @date 2024-04-16
 */
public interface EventErrorMapper
{
    /**
     * 查询event_error
     *
     * @param id event_error主键
     * @return event_error
     */
    public EventError selectEventErrorById(Long id);

    /**
     * 查询event_error列表
     *
     * @param eventError event_error
     * @return event_error集合
     */
    public List<EventError> selectEventErrorList(EventError eventError);

    /**
     * 新增event_error
     *
     * @param eventError event_error
     * @return 结果
     */
    public int insertEventError(EventError eventError);

    /**
     * 修改event_error
     *
     * @param eventError event_error
     * @return 结果
     */
    public int updateEventError(EventError eventError);

    /**
     * 删除event_error
     *
     * @param id event_error主键
     * @return 结果
     */
    public int deleteEventErrorById(Long id);

    /**
     * 批量删除event_error
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteEventErrorByIds(Long[] ids);
}
