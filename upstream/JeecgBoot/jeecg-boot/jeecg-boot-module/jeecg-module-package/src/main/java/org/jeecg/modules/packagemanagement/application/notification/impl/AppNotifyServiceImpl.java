package org.jeecg.modules.packagemanagement.application.notification.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jeecg.modules.packagemanagement.application.legacy.DateUtils;


import org.jeecg.modules.packagemanagement.application.legacy.LegacyAjaxResult;
import org.jeecg.modules.packagemanagement.domain.notify.AppNotify;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppNotifyMapper;
import org.jeecg.modules.packagemanagement.application.notification.IAppNotifyService;
import org.jeecg.modules.packagemanagement.domain.pwa.AppPwa;
import org.jeecg.modules.packagemanagement.domain.pwa.AppPwaSub;
import org.jeecg.modules.packagemanagement.domain.pwa.WebPushMessage;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppPwaMapper;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppPwaSubMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.CollectionUtils;
import org.jeecg.modules.packagemanagement.application.legacy.StringUtils;

import java.util.List;

/**
 * 消息推送Service业务层处理
 *
 * @author ruoyi
 * @date 2024-09-27
 */
@Service
public class AppNotifyServiceImpl implements IAppNotifyService
{
    @Autowired
    private org.jeecg.modules.packagemanagement.application.port.PushPort pushPort;
    @Autowired
    private AppNotifyMapper appNotifyMapper;
    @Autowired
    private AppPwaMapper appPwaMapper;
    // Legacy notification encoding stays private; never alter the host's Jackson beans.
    private ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private AppPwaSubMapper appPwaSubMapper;
    /**
     * 查询消息推送
     *
     * @param id 消息推送主键
     * @return 消息推送
     */
    @Override
    public AppNotify selectAppNotifyById(Long id)
    {
        return appNotifyMapper.selectAppNotifyById(id);
    }

    /**
     * 查询消息推送列表
     *
     * @param appNotify 消息推送
     * @return 消息推送
     */
    @Override
    public List<AppNotify> selectAppNotifyList(AppNotify appNotify)
    {

        return appNotifyMapper.selectAppNotifyList(appNotify);
    }

    /**
     * 新增消息推送
     *
     * @param appNotify 消息推送
     * @return 结果
     */
    @Override
    @Transactional
    public LegacyAjaxResult insertAppNotify(AppNotify appNotify)
    {
        if (StringUtils.isEmpty(appNotify.getAppId())){
            return LegacyAjaxResult.error("appId不能为空");
        }
        AppPwa appPwa = appPwaMapper.selectAppPwaById(appNotify.getAppId());
        appNotify.setAppName(appPwa.getAppName());
        appNotify.setAppIcon(appPwa.getAppIcon());
        appNotify.setAppHost(appPwa.getAppHost()+appPwa.getAppSub());
        appNotify.setAppId(appPwa.getId());
        appNotify.setUserId(appPwa.getUserId());
        appNotify.setCreateTime(DateUtils.getNowDate());
        int result = appNotifyMapper.insertAppNotify(appNotify);
        if (result==0){
            return LegacyAjaxResult.error("新增失败");
        }
        LegacyAjaxResult ajaxResult = sendPwaNotify(appNotify.getId(), appPwa.getPublicKey(),appPwa.getPrivateKey());
        if (ajaxResult.isError()){
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return ajaxResult;
    }

    /**
     * 发送pwa推送
     * @param notifyId
     * @param pubKey
     * @param priKey
     * @return
     */
    public LegacyAjaxResult sendPwaNotify(Long notifyId, String pubKey, String priKey){
        try {
            objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            if (org.springframework.util.ObjectUtils.isEmpty(notifyId)) {
                return LegacyAjaxResult.error("notifyId不能为空");
            }
            AppNotify notifyInfo = appNotifyMapper.selectAppNotifyById(Long.valueOf(notifyId));
            AppPwaSub appPwaSub = new AppPwaSub();
            appPwaSub.setAppId(notifyInfo.getAppId());
            List<AppPwaSub> appPwaSubList = appPwaSubMapper.selectAppPwaSubList(appPwaSub);
            if (CollectionUtils.isEmpty(appPwaSubList)) {
                return LegacyAjaxResult.error("订阅信息不存在");
            }

            pushPort.validateKeys(pubKey, priKey);
            for (AppPwaSub e : appPwaSubList){
                try {
                    WebPushMessage webPushMessage= new WebPushMessage(notifyInfo.getNotifyTitle(),notifyInfo.getNotifyIcon(),notifyInfo.getNotifyMessage(),notifyInfo.getAppId().toString());
                    String auth =e.getAuth();
                    String p256dh = e.getP256dh();
                    pushPort.send(pubKey,priKey,e,objectMapper.writeValueAsBytes(webPushMessage));
                }catch (Exception exception){
                    exception.printStackTrace();
                }
            }
            return LegacyAjaxResult.success("推送成功");
        }catch (Exception e) {
            e.printStackTrace();
        }
        return LegacyAjaxResult.error("推送失败");
    }

    /**
     * 修改消息推送
     *
     * @param appNotify 消息推送
     * @return 结果
     */
    @Override
    public int updateAppNotify(AppNotify appNotify)
    {
        return appNotifyMapper.updateAppNotify(appNotify);
    }

    /**
     * 批量删除消息推送
     *
     * @param ids 需要删除的消息推送主键
     * @return 结果
     */
    @Override
    public int deleteAppNotifyByIds(Long[] ids)
    {
        return appNotifyMapper.deleteAppNotifyByIds(ids);
    }

    /**
     * 删除消息推送信息
     *
     * @param id 消息推送主键
     * @return 结果
     */
    @Override
    public int deleteAppNotifyById(Long id)
    {
        return appNotifyMapper.deleteAppNotifyById(id);
    }
}
