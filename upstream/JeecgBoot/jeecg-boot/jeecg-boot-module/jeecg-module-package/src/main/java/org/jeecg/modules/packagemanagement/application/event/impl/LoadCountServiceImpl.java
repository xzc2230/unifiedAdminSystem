package org.jeecg.modules.packagemanagement.application.event.impl;



import org.jeecg.modules.packagemanagement.domain.event.LoadCount;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.LoadCountMapper;
import org.jeecg.modules.packagemanagement.application.event.ILoadCountService;
import org.jeecg.modules.packagemanagement.domain.pwa.AppPwa;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppPwaMapper;
import org.jeecg.modules.packagemanagement.application.pwa.IAppPwaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.jeecg.modules.packagemanagement.application.legacy.StringUtils;

import java.util.List;

/**
 * 落地页访问量Service业务层处理
 *
 * @author ruoyi
 * @date 2024-05-07
 */
@Service
public class LoadCountServiceImpl implements ILoadCountService
{
    @Autowired
    private LoadCountMapper loadCountMapper;
    @Autowired
    private AppPwaMapper appPwaMapper;

    /**
     * 查询落地页访问量
     *
     * @param id 落地页访问量主键
     * @return 落地页访问量
     */
    @Override
    public LoadCount selectLoadCountById(Long id)
    {
        return loadCountMapper.selectLoadCountById(id);
    }

    @Override
    public LoadCount selectLoadCountByHostName(String hostName) {
        return loadCountMapper.selectLoadCountByHostName(hostName);
    }

    /**
     * 查询落地页访问量列表
     *
     * @param loadCount 落地页访问量
     * @return 落地页访问量
     */
    @Override
    public List<LoadCount> selectLoadCountList(LoadCount loadCount)
    {

        if (!StringUtils.isEmpty(loadCount.getAppName())){
            AppPwa app = new AppPwa();
            app.setAppName(loadCount.getAppName());
            List<AppPwa> list = appPwaMapper.selectAppPwaList(app);
            if (!CollectionUtils.isEmpty(list)){
                loadCount.setAppId(list.get(0).getId());
            }
        }
        List<LoadCount> list = loadCountMapper.selectLoadCountList(loadCount);
        list.forEach(e ->{
            AppPwa appPwa = appPwaMapper.selectAppPwaById(e.getAppId());
            if (!StringUtils.isEmpty(appPwa)){
                e.setAppName(appPwa.getAppName());
            }
        });
        return list;
    }

    /**
     * 新增落地页访问量
     *
     * @param loadCount 落地页访问量
     * @return 结果
     */
    @Override
    public int insertLoadCount(LoadCount loadCount)
    {
        if (!StringUtils.isEmpty(loadCount.getHostName())){
            AppPwa app = new AppPwa();
            app.setAppHost(loadCount.getHostName().substring(0,loadCount.getHostName().indexOf(".")));
            app.setAppSub(loadCount.getHostName().substring(loadCount.getHostName().indexOf(".")+1));
            List<AppPwa> list = appPwaMapper.selectAppPwaList(app);
            if (CollectionUtils.isEmpty(list) || list.size() > 1){
                return 0;
            }
            loadCount.setAppId(list.get(0).getId());
            loadCount.setUserId(list.get(0).getUserId());
        }
        return loadCountMapper.insertLoadCount(loadCount);
    }

    /**
     * 修改落地页访问量
     *
     * @param loadCount 落地页访问量
     * @return 结果
     */
    @Override
    public int updateLoadCount(LoadCount loadCount)
    {
        if (!StringUtils.isEmpty(loadCount.getHostName())){
            AppPwa app = new AppPwa();
            app.setAppHost(loadCount.getHostName().substring(0,loadCount.getHostName().indexOf(".")));
            app.setAppSub(loadCount.getHostName().substring(loadCount.getHostName().indexOf(".")+1));
            List<AppPwa> list = appPwaMapper.selectAppPwaList(app);
            if (CollectionUtils.isEmpty(list) || list.size() > 1){
                return 0;
            }
            loadCount.setAppId(list.get(0).getId());
            loadCount.setUserId(list.get(0).getUserId());
        }
        return loadCountMapper.updateLoadCount(loadCount);
    }

    /**
     * 批量删除落地页访问量
     *
     * @param ids 需要删除的落地页访问量主键
     * @return 结果
     */
    @Override
    public int deleteLoadCountByIds(Long[] ids)
    {
        return loadCountMapper.deleteLoadCountByIds(ids);
    }

    /**
     * 删除落地页访问量信息
     *
     * @param id 落地页访问量主键
     * @return 结果
     */
    @Override
    public int deleteLoadCountById(Long id)
    {
        return loadCountMapper.deleteLoadCountById(id);
    }
}
