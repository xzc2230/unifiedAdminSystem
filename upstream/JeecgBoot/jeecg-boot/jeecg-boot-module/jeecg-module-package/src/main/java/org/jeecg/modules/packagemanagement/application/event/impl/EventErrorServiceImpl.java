package org.jeecg.modules.packagemanagement.application.event.impl;


import org.jeecg.modules.packagemanagement.domain.event.EventError;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.EventErrorMapper;
import org.jeecg.modules.packagemanagement.application.event.IEventErrorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * event_errorService业务层处理
 *
 * @author ruoyi
 * @date 2024-04-16
 */
@Service
public class EventErrorServiceImpl implements IEventErrorService
{
    @Autowired
    private EventErrorMapper eventErrorMapper;

    /**
     * 查询event_error
     *
     * @param id event_error主键
     * @return event_error
     */
    @Override
    public EventError selectEventErrorById(Long id)
    {
        return eventErrorMapper.selectEventErrorById(id);
    }

    /**
     * 查询event_error列表
     *
     * @param eventError event_error
     * @return event_error
     */
    @Override
    public List<EventError> selectEventErrorList(EventError eventError)
    {
        return eventErrorMapper.selectEventErrorList(eventError);
    }

    /**
     * 新增event_error
     *
     * @param eventError event_error
     * @return 结果
     */
    @Override
    public int insertEventError(EventError eventError)
    {
        return eventErrorMapper.insertEventError(eventError);
    }

    /**
     * 修改event_error
     *
     * @param eventError event_error
     * @return 结果
     */
    @Override
    public int updateEventError(EventError eventError)
    {
        return eventErrorMapper.updateEventError(eventError);
    }

    /**
     * 批量删除event_error
     *
     * @param ids 需要删除的event_error主键
     * @return 结果
     */
    @Override
    public int deleteEventErrorByIds(Long[] ids)
    {
        return eventErrorMapper.deleteEventErrorByIds(ids);
    }

    /**
     * 删除event_error信息
     *
     * @param id event_error主键
     * @return 结果
     */
    @Override
    public int deleteEventErrorById(Long id)
    {
        return eventErrorMapper.deleteEventErrorById(id);
    }
}
