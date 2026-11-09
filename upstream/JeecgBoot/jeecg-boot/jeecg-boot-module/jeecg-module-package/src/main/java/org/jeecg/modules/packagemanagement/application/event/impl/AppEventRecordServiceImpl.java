package org.jeecg.modules.packagemanagement.application.event.impl;

import java.util.List;
import org.jeecg.modules.packagemanagement.application.legacy.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppEventRecordMapper;
import org.jeecg.modules.packagemanagement.domain.system.AppEventRecord;
import org.jeecg.modules.packagemanagement.application.event.IAppEventRecordService;

/**
 * 事件统计Service业务层处理
 *
 * @author ruoyi
 * @date 2024-10-11
 */
@Service
public class AppEventRecordServiceImpl implements IAppEventRecordService
{
    @Autowired
    private AppEventRecordMapper appEventRecordMapper;

    /**
     * 查询事件统计
     *
     * @param eventId 事件统计主键
     * @return 事件统计
     */
    @Override
    public AppEventRecord selectAppEventRecordByEventId(Long eventId)
    {
        return appEventRecordMapper.selectAppEventRecordByEventId(eventId);
    }

    /**
     * 查询事件统计列表
     *
     * @param appEventRecord 事件统计
     * @return 事件统计
     */
    @Override
    public List<AppEventRecord> selectAppEventRecordList(AppEventRecord appEventRecord)
    {
        return appEventRecordMapper.selectAppEventRecordList(appEventRecord);
    }

    /**
     * 新增事件统计
     *
     * @param appEventRecord 事件统计
     * @return 结果
     */
    @Override
    public int insertAppEventRecord(AppEventRecord appEventRecord)
    {
        appEventRecord.setCreateTime(DateUtils.getNowDate());
        return appEventRecordMapper.insertAppEventRecord(appEventRecord);
    }

    /**
     * 修改事件统计
     *
     * @param appEventRecord 事件统计
     * @return 结果
     */
    @Override
    public int updateAppEventRecord(AppEventRecord appEventRecord)
    {
        return appEventRecordMapper.updateAppEventRecord(appEventRecord);
    }

    /**
     * 批量删除事件统计
     *
     * @param eventIds 需要删除的事件统计主键
     * @return 结果
     */
    @Override
    public int deleteAppEventRecordByEventIds(Long[] eventIds)
    {
        return appEventRecordMapper.deleteAppEventRecordByEventIds(eventIds);
    }

    /**
     * 删除事件统计信息
     *
     * @param eventId 事件统计主键
     * @return 结果
     */
    @Override
    public int deleteAppEventRecordByEventId(Long eventId)
    {
        return appEventRecordMapper.deleteAppEventRecordByEventId(eventId);
    }
}
