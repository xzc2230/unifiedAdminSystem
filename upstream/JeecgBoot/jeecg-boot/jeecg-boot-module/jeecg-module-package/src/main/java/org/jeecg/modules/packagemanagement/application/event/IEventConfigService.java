package org.jeecg.modules.packagemanagement.application.event;


import org.jeecg.modules.packagemanagement.domain.event.EventConfig;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * fb_configService接口
 *
 * @author ruoyi
 * @date 2024-04-16
 */
public interface IEventConfigService {
    /**
     * 查询fb_config
     *
     * @param id fb_config主键
     * @return fb_config
     */
    public EventConfig selectEventConfigById(Long id);

    public EventConfig selectEventConfigByPackageId(String packageId);

    /**
     * 查询fb_config列表
     *
     * @param eventConfig fb_config
     * @return fb_config集合
     */
    public List<EventConfig> selectEventConfigList(EventConfig eventConfig);

    /**
     * 新增fb_config
     *
     * @param eventConfig fb_config
     * @return 结果
     */
    public int insertEventConfig(EventConfig eventConfig);

    /**
     * 修改fb_config
     *
     * @param eventConfig fb_config
     * @return 结果
     */
    public int updateEventConfig(EventConfig eventConfig);

    /**
     * 批量删除fb_config
     *
     * @param ids 需要删除的fb_config主键集合
     * @return 结果
     */
    public int deleteEventConfigByIds(Long[] ids);

    /**
     * 删除fb_config信息
     *
     * @param id fb_config主键
     * @return 结果
     */
    public int deleteEventConfigById(Long id);
    public void page(@PathVariable String page, HttpServletRequest request, HttpServletResponse response) throws IOException;
}
