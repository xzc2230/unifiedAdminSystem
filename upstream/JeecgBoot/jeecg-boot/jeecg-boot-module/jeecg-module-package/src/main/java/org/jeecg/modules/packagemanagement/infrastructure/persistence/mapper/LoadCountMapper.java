package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;


import org.jeecg.modules.packagemanagement.domain.event.LoadCount;

import java.util.List;

/**
 * 落地页访问量Mapper接口
 *
 * @author ruoyi
 * @date 2024-05-07
 */
public interface LoadCountMapper
{
    /**
     * 查询落地页访问量
     *
     * @param id 落地页访问量主键
     * @return 落地页访问量
     */
    public LoadCount selectLoadCountById(Long id);

    public LoadCount selectLoadCountByHostName(String hoseName);

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
     * 删除落地页访问量
     *
     * @param id 落地页访问量主键
     * @return 结果
     */
    public int deleteLoadCountById(Long id);

    /**
     * 批量删除落地页访问量
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteLoadCountByIds(Long[] ids);
}
