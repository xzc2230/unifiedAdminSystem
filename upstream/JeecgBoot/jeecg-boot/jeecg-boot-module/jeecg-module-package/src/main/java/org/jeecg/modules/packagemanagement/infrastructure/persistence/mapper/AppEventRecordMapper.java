package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;

import java.util.List;
import org.jeecg.modules.packagemanagement.domain.system.AppEventRecord;

/**
 * 事件统计Mapper接口
 *
 * @author ruoyi
 * @date 2024-10-11
 */
public interface AppEventRecordMapper
{
    /**
     * 查询事件统计
     *
     * @param eventId 事件统计主键
     * @return 事件统计
     */
    public AppEventRecord selectAppEventRecordByEventId(Long eventId);

    /**
     * 查询事件统计列表
     *
     * @param appEventRecord 事件统计
     * @return 事件统计集合
     */
    public List<AppEventRecord> selectAppEventRecordList(AppEventRecord appEventRecord);

    /**
     * 新增事件统计
     *
     * @param appEventRecord 事件统计
     * @return 结果
     */
    public int insertAppEventRecord(AppEventRecord appEventRecord);

    /**
     * 修改事件统计
     *
     * @param appEventRecord 事件统计
     * @return 结果
     */
    public int updateAppEventRecord(AppEventRecord appEventRecord);

    /**
     * 删除事件统计
     *
     * @param eventId 事件统计主键
     * @return 结果
     */
    public int deleteAppEventRecordByEventId(Long eventId);

    /**
     * 批量删除事件统计
     *
     * @param eventIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAppEventRecordByEventIds(Long[] eventIds);
}
