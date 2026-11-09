package org.jeecg.modules.packagemanagement.application.event;

import org.jeecg.modules.packagemanagement.domain.event.EvnetHistory;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * af事件记录Service接口
 *
 * @author ruoyi
 * @date 2024-04-16
 */
public interface IEvnetHistoryService {
    /**
     * 查询af事件记录
     *
     * @param id af事件记录主键
     * @return af事件记录
     */
    public EvnetHistory selectEvnetHistoryById(Long id);

    /**
     * 查询af事件记录列表
     *
     * @param evnetHistory af事件记录
     * @return af事件记录集合
     */
    public List<EvnetHistory> selectEvnetHistoryList(EvnetHistory evnetHistory);

    /**
     * 新增af事件记录
     *
     * @param evnetHistory af事件记录
     * @return 结果
     */
    public int insertEvnetHistory(EvnetHistory evnetHistory);

    /**
     * 修改af事件记录
     *
     * @param evnetHistory af事件记录
     * @return 结果
     */
    public int updateEvnetHistory(EvnetHistory evnetHistory);

    /**
     * 批量删除af事件记录
     *
     * @param ids 需要删除的af事件记录主键集合
     * @return 结果
     */
    public int deleteEvnetHistoryByIds(Long[] ids);

    /**
     * 删除af事件记录信息
     *
     * @param id af事件记录主键
     * @return 结果
     */
    public int deleteEvnetHistoryById(Long id);

    public void fbEvent(@PathVariable String packageId, EvnetHistory fbEvent);

}
