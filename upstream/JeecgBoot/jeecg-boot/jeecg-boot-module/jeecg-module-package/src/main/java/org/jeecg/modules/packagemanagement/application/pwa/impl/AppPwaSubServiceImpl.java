package org.jeecg.modules.packagemanagement.application.pwa.impl;

import org.jeecg.modules.packagemanagement.application.legacy.StringUtils;
import org.jeecg.modules.packagemanagement.domain.pwa.AppPwa;
import org.jeecg.modules.packagemanagement.domain.pwa.AppPwaSub;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppPwaMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppPwaSubMapper;
import org.jeecg.modules.packagemanagement.application.pwa.IAppPwaSubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;


/**
 * pwaService业务层处理
 *
 * @author Murphy
 * @date 2024-08-09
 */
@Service
public class AppPwaSubServiceImpl implements IAppPwaSubService
{
    @Autowired
    private AppPwaSubMapper appPwaSubMapper;
    @Autowired
    private AppPwaMapper appPwaMapper;
    @Autowired(required=false) private org.jeecg.modules.packagemanagement.infrastructure.identity.PackagePublicOwnership publicOwnership;


    /**
     * 查询pwa
     *
     * @param id pwa主键
     * @return pwa
     */
    @Override
    public AppPwaSub selectAppPwaSubById(Long id)
    {
        return appPwaSubMapper.selectAppPwaSubById(id);
    }

    /**
     * 查询pwa列表
     *
     * @param appPwa pwa
     * @return pwa
     */
    @Override
    public List<AppPwaSub> selectAppPwaSubList(AppPwaSub appPwa) {
        return appPwaSubMapper.selectAppPwaSubList(appPwa);
    }

    /**
     * 新增pwa
     *
     * @param appPwa pwa
     * @return 结果
     */
    @Override
    @org.springframework.transaction.annotation.Transactional
    public int insertAppPwaSub(AppPwaSub appPwa)
    {
        if (StringUtils.isEmpty(appPwa.getHost())){
            return 0;
        }
        if (StringUtils.isEmpty(appPwa.getEndpoint())){
            return 0;
        }
        if (org.springframework.util.ObjectUtils.isEmpty(appPwa.getKeys())){
            return 0;
        }
        appPwa.setAuth(appPwa.getKeys().getAuth());
        appPwa.setP256dh(appPwa.getKeys().getP256dh());
        AppPwa app = new AppPwa();
        app.setAppHost(appPwa.getHost().substring(0,appPwa.getHost().indexOf(".")));
        app.setAppSub(appPwa.getHost().substring(appPwa.getHost().indexOf(".")+1));
        List<AppPwa> list = appPwaMapper.selectAppPwaList(app);
        if (CollectionUtils.isEmpty(list) || list.size() != 1){
            return 0;
        }
        List<AppPwaSub> appPwaSubList =  appPwaSubMapper.selectAppPwaSubList(appPwa);
        if (CollectionUtils.isEmpty(appPwaSubList)){
            appPwa.setAppId(list.get(0).getId());
            appPwa.setCreateTime(new Date());
            int affected=appPwaSubMapper.insertAppPwaSub(appPwa);
            if(affected>0&&publicOwnership!=null)publicOwnership.inherit("pkg_pwa_subscription",appPwa.getId(),"pkg_pwa",appPwa.getAppId());
            return affected;
        }else {
            appPwa.setAppId(list.get(0).getId());
            appPwa.setId(appPwaSubList.get(0).getId());
            return appPwaSubMapper.updateAppPwaSub(appPwa);
        }
    }


    /**
     * 修改pwa
     *
     * @param appPwa pwa
     * @return 结果
     */
    @Override
    public int updateAppPwaSub(AppPwaSub appPwa)
    {

        return appPwaSubMapper.updateAppPwaSub(appPwa);
    }

    /**
     * 批量删除pwa
     *
     * @param ids 需要删除的pwa主键
     * @return 结果
     */
    @Override
    public int deleteAppPwaSubByIds(Long[] ids) {

        return appPwaSubMapper.deleteAppPwaSubByIds(ids);
    }

    /**
     * 删除pwa信息
     *
     * @param id pwa主键
     * @return 结果
     */
    @Override
    public int deleteAppPwaSubById(Long id) {
        return appPwaSubMapper.deleteAppPwaSubById(id);
    }


}
