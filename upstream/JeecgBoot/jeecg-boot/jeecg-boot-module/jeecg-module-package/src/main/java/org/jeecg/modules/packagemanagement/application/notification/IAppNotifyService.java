package org.jeecg.modules.packagemanagement.application.notification;

import org.jeecg.modules.packagemanagement.application.legacy.LegacyAjaxResult;
import org.jeecg.modules.packagemanagement.domain.notify.AppNotify;

import java.util.List;

/**
 * 消息推送Service接口
 *
 * @author ruoyi
 * @date 2024-09-27
 */
public interface IAppNotifyService
{
    /**
     * 查询消息推送
     *
     * @param id 消息推送主键
     * @return 消息推送
     */
    public AppNotify selectAppNotifyById(Long id);

    /**
     * 查询消息推送列表
     *
     * @param appNotify 消息推送
     * @return 消息推送集合
     */
    public List<AppNotify> selectAppNotifyList(AppNotify appNotify);

    /**
     * 新增消息推送
     *
     * @param appNotify 消息推送
     * @return 结果
     */
    public LegacyAjaxResult insertAppNotify(AppNotify appNotify);

    /**
     * 修改消息推送
     *
     * @param appNotify 消息推送
     * @return 结果
     */
    public int updateAppNotify(AppNotify appNotify);

    /**
     * 批量删除消息推送
     *
     * @param ids 需要删除的消息推送主键集合
     * @return 结果
     */
    public int deleteAppNotifyByIds(Long[] ids);

    /**
     * 删除消息推送信息
     *
     * @param id 消息推送主键
     * @return 结果
     */
    public int deleteAppNotifyById(Long id);
}
