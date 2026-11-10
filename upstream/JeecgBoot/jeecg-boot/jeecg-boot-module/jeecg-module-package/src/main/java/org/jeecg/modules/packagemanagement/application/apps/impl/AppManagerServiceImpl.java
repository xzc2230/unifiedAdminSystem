package org.jeecg.modules.packagemanagement.application.apps.impl;

import org.jeecg.modules.packagemanagement.application.legacy.DateUtils;


import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppManagerMapper;
import org.jeecg.modules.packagemanagement.application.apps.IAppManagerService;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.StoreStatusMapper;
import org.jeecg.modules.packagemanagement.application.store.StoreListing;

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
    @Autowired(required=false)
    private org.jeecg.modules.packagemanagement.application.apps.support.AppManagerCache cache;
    private void refresh(String code){if(cache!=null)cache.refresh(code);}

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
        if(packageName==null||packageName.isBlank())return null;
        String normalized=packageName.trim();
        AppManager row=appManagerMapper.selectAppManagerByPackageName(normalized);
        refresh(normalized);return row;
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

        appManager.setAreas(org.jeecg.modules.packagemanagement.application.apps.support.PackageCountryRegions.normalizeAreas(appManager.getAreas()));
        if(appManager.getPackageCode()!=null)appManager.setPackageCode(appManager.getPackageCode().trim());
        StoreListing.normalize(appManager);
        AppManager appManager1 = appManagerMapper.selectAppManagerByPlatformAndPackage(appManager.getPlatform(), appManager.getPackageCode());
        if (appManager1 != null) {
            return -1;
        } else {
            syncStore(appManager, false);
            appManager.setCreateDate(DateUtils.getNowDate());
            int count=appManagerMapper.insertAppManager(appManager);refresh(appManager.getPackageCode());return count;
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

        appManager.setAreas(org.jeecg.modules.packagemanagement.application.apps.support.PackageCountryRegions.normalizeAreas(appManager.getAreas()));
        if(appManager.getPackageCode()!=null)appManager.setPackageCode(appManager.getPackageCode().trim());
        StoreListing.normalize(appManager);
        AppManager previous = appManager.getId() == null ? null : appManagerMapper.selectAppManagerById(appManager.getId());
        boolean identityChanged = false;
        if (previous != null) {
            if (previous.getPlatform() == null || previous.getPlatform().isBlank()) {
                previous.setPlatform("android");
            }
            identityChanged = StoreListing.identityChanged(previous, appManager);
        }
        syncStore(appManager, identityChanged);
        appManager.setUpdateDate(DateUtils.getNowDate());
        appManager.setCreateDate(DateUtils.getNowDate());

        int count = appManagerMapper.updateAppManager(appManager);
        if ("android".equals(appManager.getPlatform()) && identityChanged) {
            appManagerMapper.clearAppManagerAppleStoreIdentity(appManager.getId());
        }
        refresh(appManager.getPackageCode());
        return count;
    }

    private void syncStore(AppManager app, boolean identityChanged) {
        StoreStatus storeStatus = storeStatusMapper.selectStoreStatusByPlatformAndPackage(app.getPlatform(), app.getPackageCode());
        if (storeStatus == null) {
            StoreStatus ss = new StoreStatus();
            ss.setStatus(0);
            ss.setPackageCode(app.getPackageCode());
            ss.setPlatform(app.getPlatform());
            if ("ios".equals(app.getPlatform())) {
                ss.setAppleAppId(app.getAppleAppId());
                ss.setStoreRegion(app.getStoreRegion());
            }
            ss.setCreateDate(DateUtils.getNowDate());
            ss.setStoreUrl(StoreListing.url(app));
            storeStatusMapper.insertStoreStatus(ss);
        } else if (identityChanged) {
            storeStatus.setPackageCode(app.getPackageCode());
            storeStatus.setPlatform(app.getPlatform());
            storeStatus.setStatus(0);
            storeStatus.setStoreUrl(StoreListing.url(app));
            if ("android".equals(app.getPlatform())) {
                storeStatus.setAppleAppId(null);
                storeStatus.setStoreRegion(null);
            } else {
                storeStatus.setAppleAppId(app.getAppleAppId());
                storeStatus.setStoreRegion(app.getStoreRegion());
            }
            storeStatusMapper.resetStoreListing(storeStatus);
        }
    }

    /**
     * 批量删除【请填写功能名称】
     *
     * @param ids 需要删除的【请填写功能名称】主键
     * @return 结果
     */
    @Override
    public int deleteAppManagerByIds(Long[] ids) {
        var codes=java.util.Arrays.stream(ids).map(appManagerMapper::selectAppManagerById).filter(java.util.Objects::nonNull).map(AppManager::getPackageCode).toList();
        int count=appManagerMapper.deleteAppManagerByIds(ids);codes.forEach(this::refresh);return count;
    }

    /**
     * 删除【请填写功能名称】信息
     *
     * @param id 【请填写功能名称】主键
     * @return 结果
     */
    @Override
    public int deleteAppManagerById(Long id) {
        return deleteAppManagerByIds(new Long[]{id});
    }

    @Override
    public int transferAppManagerById(String id, Integer userId, String userName) {
        int count=appManagerMapper.transferAppManagerById(id, userId, userName);
        refresh(id);return count;
    }

}
