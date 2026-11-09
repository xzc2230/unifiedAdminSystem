package org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper;

import org.jeecg.modules.packagemanagement.domain.statistics.AppStatistics;

import java.util.List;

/**
 * 包统计Mapper接口
 *
 * @author ruoyi
 * @date 2023-11-04
 */
public interface AppStatisticsMapper
{
    /**
     * 查询包统计
     *
     * @param id 包统计主键
     * @return 包统计
     */
    public AppStatistics selectAppStatisticsById(Long id);

    public AppStatistics selectAppStatisticsByPackageName(String  packageName);


    /**
     * 查询包统计列表
     *
     * @param appStatistics 包统计
     * @return 包统计集合
     */
    public List<AppStatistics> selectAppStatisticsList(AppStatistics appStatistics);


    /**
     * 新增包统计
     *
     * @param appStatistics 包统计
     * @return 结果
     */
    public int insertAppStatistics(AppStatistics appStatistics);

    /**
     * 修改包统计
     *
     * @param appStatistics 包统计
     * @return 结果
     */
    public int updateAppStatistics(AppStatistics appStatistics);

    /**
     * 删除包统计
     *
     * @param id 包统计主键
     * @return 结果
     */
    public int deleteAppStatisticsById(Long id);

    /**
     * 批量删除包统计
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAppStatisticsByIds(Long[] ids);
}
