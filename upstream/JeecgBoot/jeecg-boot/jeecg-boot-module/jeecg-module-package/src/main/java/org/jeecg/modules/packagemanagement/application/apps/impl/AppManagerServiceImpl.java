package org.jeecg.modules.packagemanagement.application.apps.impl;

import org.jeecg.modules.packagemanagement.application.legacy.DateUtils;


import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppManagerMapper;
import org.jeecg.modules.packagemanagement.application.apps.IAppManagerService;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.StoreStatusMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 【请填写功能名称】Service业务层处理
 *
 * @author ruoyi
 * @date 2023-10-19
 */
@Service
public class AppManagerServiceImpl implements IAppManagerService {
    @Autowired
    private AppManagerMapper appManagerMapper;
    @Autowired
    private StoreStatusMapper storeStatusMapper;

    /**
     * 查询【请填写功能名称】
     *
     * @param id 【请填写功能名称】主键
     * @return 【请填写功能名称】
     */
    @Override
    public AppManager selectAppManagerById(Long id) {
        return appManagerMapper.selectAppManagerById(id);
    }

    @Override
    public AppManager selectAppManagerByPackageName(String packageName) {
        return appManagerMapper.selectAppManagerByPackageName(packageName);
    }

    /**
     * 查询【请填写功能名称】列表
     *
     * @param appManager 【请填写功能名称】
     * @return 【请填写功能名称】
     */
    @Override
    public List<AppManager> selectAppManagerList(AppManager appManager) {

        return appManagerMapper.selectAppManagerList(appManager);
    }

    /**
     * 新增【请填写功能名称】
     *
     * @param appManager 【请填写功能名称】
     * @return 结果
     */
    @Override
    @Transactional
    public int insertAppManager(AppManager appManager) {

        AppManager appManager1 = appManagerMapper.selectAppManagerByPackageName(appManager.getPackageCode());
        if (appManager1 != null) {
            return -1;
        } else {
            StoreStatus storeStatus = storeStatusMapper.selectStoreStatusByPackageName(appManager.getPackageCode());
            if (storeStatus == null) {
                StoreStatus ss = new StoreStatus();
                ss.setStatus(0);
                ss.setPackageCode(appManager.getPackageCode());
                ss.setCreateDate(DateUtils.getNowDate());
                ss.setStoreUrl("https://play.google.com/store/apps/details?id=" + appManager.getPackageCode());
                storeStatusMapper.insertStoreStatus(ss);
            }
            appManager.setCreateDate(DateUtils.getNowDate());
            return appManagerMapper.insertAppManager(appManager);
        }
    }

    /**
     * 修改【请填写功能名称】
     *
     * @param appManager 【请填写功能名称】
     * @return 结果
     */
    @Override
    public int updateAppManager(AppManager appManager) {

        appManager.setUpdateDate(DateUtils.getNowDate());
        StoreStatus storeStatus = storeStatusMapper.selectStoreStatusByPackageName(appManager.getPackageCode());
        if (storeStatus == null) {
            StoreStatus ss = new StoreStatus();
            ss.setStatus(0);
            ss.setPackageCode(appManager.getPackageCode());
            ss.setCreateDate(DateUtils.getNowDate());
            ss.setStoreUrl("https://play.google.com/store/apps/details?id=" + appManager.getPackageCode());
            storeStatusMapper.insertStoreStatus(ss);
        } else {
            storeStatusMapper.updateStoreStatusAndDate(storeStatus);
        }
        appManager.setCreateDate(DateUtils.getNowDate());

        return appManagerMapper.updateAppManager(appManager);
    }

    /**
     * 批量删除【请填写功能名称】
     *
     * @param ids 需要删除的【请填写功能名称】主键
     * @return 结果
     */
    @Override
    public int deleteAppManagerByIds(Long[] ids) {
        return appManagerMapper.deleteAppManagerByIds(ids);
    }

    /**
     * 删除【请填写功能名称】信息
     *
     * @param id 【请填写功能名称】主键
     * @return 结果
     */
    @Override
    public int deleteAppManagerById(Long id) {
        return appManagerMapper.deleteAppManagerById(id);
    }

    @Override
    public int transferAppManagerById(String id, Integer userId, String userName) {
        return appManagerMapper.transferAppManagerById(id, userId, userName);
    }

}
