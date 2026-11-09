package org.jeecg.modules.packagemanagement.application.event;


import org.jeecg.modules.packagemanagement.domain.event.LoadCount;

import java.util.List;

/**
 * 落地页访问量Service接口
 *
 * @author ruoyi
 * @date 2024-05-07
 */
public interface ILoadCountService
{
    /**
     * 查询落地页访问量
     *
     * @param id 落地页访问量主键
     * @return 落地页访问量
     */
    public LoadCount selectLoadCountById(Long id);
    public LoadCount selectLoadCountByHostName(String hostName);

    /**
     * 查询落地页访问量列表
     *
     * @param loadCount 落地页访问量
     * @return 落地页访问量集合
     */
    public List<LoadCount> selectLoadCountList(LoadCount loadCount);

    /**
     * 新增落地页访问量
     *
     * @param loadCount 落地页访问量
     * @return 结果
     */
    public int insertLoadCount(LoadCount loadCount);

    /**
     * 修改落地页访问量
     *
     * @param loadCount 落地页访问量
     * @return 结果
     */
    public int updateLoadCount(LoadCount loadCount);

    /**
     * 批量删除落地页访问量
     *
     * @param ids 需要删除的落地页访问量主键集合
     * @return 结果
     */
    public int deleteLoadCountByIds(Long[] ids);

    /**
     * 删除落地页访问量信息
     *
     * @param id 落地页访问量主键
     * @return 结果
     */
    public int deleteLoadCountById(Long id);
}
