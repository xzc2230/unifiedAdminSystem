package org.jeecg.modules.packagemanagement.application.event.impl;


import org.jeecg.modules.packagemanagement.domain.event.EventConfig;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.EventConfigMapper;
import org.jeecg.modules.packagemanagement.application.event.IEventConfigService;
import org.jeecg.modules.packagemanagement.application.port.GeoPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * fb_configService业务层处理
 *
 * @author ruoyi
 * @date 2024-04-16
 */
@Service
public class EventConfigServiceImpl implements IEventConfigService {
    @Autowired
    private org.jeecg.modules.packagemanagement.config.PackageManagementProperties settings;
    @Autowired
    private GeoPort geo;

    @Autowired
    private EventConfigMapper eventConfigMapper;

    /**
     * 查询fb_config
     *
     * @param id fb_config主键
     * @return fb_config
     */
    @Override
    public EventConfig selectEventConfigById(Long id) {
        return eventConfigMapper.selectEventConfigById(id);
    }

    @Override
    public EventConfig selectEventConfigByPackageId(String packageId) {
        return eventConfigMapper.selectEventConfigByPackageId(packageId);
    }

    /**
     * 查询fb_config列表
     *
     * @param eventConfig fb_config
     * @return fb_config
     */
    @Override
    public List<EventConfig> selectEventConfigList(EventConfig eventConfig) {
        return eventConfigMapper.selectEventConfigList(eventConfig);
    }

    /**
     * 新增fb_config
     *
     * @param eventConfig fb_config
     * @return 结果
     */
    @Override
    public int insertEventConfig(EventConfig eventConfig) {
        return eventConfigMapper.insertEventConfig(eventConfig);
    }

    /**
     * 修改fb_config
     *
     * @param eventConfig fb_config
     * @return 结果
     */
    @Override
    public int updateEventConfig(EventConfig eventConfig) {
        return eventConfigMapper.updateEventConfig(eventConfig);
    }

    /**
     * 批量删除fb_config
     *
     * @param ids 需要删除的fb_config主键
     * @return 结果
     */
    @Override
    public int deleteEventConfigByIds(Long[] ids) {
        return eventConfigMapper.deleteEventConfigByIds(ids);
    }

    /**
     * 删除fb_config信息
     *
     * @param id fb_config主键
     * @return 结果
     */
    @Override
    public int deleteEventConfigById(Long id) {
        return eventConfigMapper.deleteEventConfigById(id);
    }

    @Override
    public void page(String page, HttpServletRequest request, HttpServletResponse response) throws IOException {
        EventConfig eventConfig = eventConfigMapper.selectEventConfigByPackageId(page);
        if (eventConfig != null) {
            Map<String, String[]> parameterMap = request.getParameterMap();
            String ua = request.getHeader("User-Agent");
            if (parameterMap != null && !parameterMap.isEmpty() && parameterMap.containsKey("fbclid") && ua != null && (!ua.trim().contains("Android") || !ua.trim().contains("android"))) {
                response.sendRedirect(geo.passFb(geo.getIpAddress(request), eventConfig.getUrl()));
            } else {
                response.sendRedirect(settings.getLandingFallbackUrl());
            }

        } else {
            response.sendRedirect(settings.getLandingFallbackUrl());
        }
    }
}
