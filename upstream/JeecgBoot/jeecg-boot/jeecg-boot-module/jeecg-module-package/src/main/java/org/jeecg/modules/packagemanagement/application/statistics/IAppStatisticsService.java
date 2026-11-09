package org.jeecg.modules.packagemanagement.application.statistics;

import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.domain.statistics.AppStatistics;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 包统计Service接口
 *
 * @author ruoyi
 * @date 2023-11-04
 */
public interface IAppStatisticsService
{
    /**
     * 查询包统计
     *
     * @param id 包统计主键
     * @return 包统计
     */
    public AppStatistics selectAppStatisticsById(Long id);

    /**
     * 查询包统计列表
     *
     * @param appStatistics 包统计
     * @return 包统计集合
     */
    public List<AppStatistics> selectAppStatisticsList(AppStatistics appStatistics);


    public AppStatistics selectAppStatisticsByPackageName(String packageName);


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

    public Object uploadData(HttpServletRequest request);
    public Object getDetails(HttpServletRequest request);


    /**
     * 批量删除包统计
     *
     * @param ids 需要删除的包统计主键集合
     * @return 结果
     */
    public int deleteAppStatisticsByIds(Long[] ids);

    /**
     * 删除包统计信息
     *
     * @param id 包统计主键
     * @return 结果
     */
    public int deleteAppStatisticsById(Long id);
}
