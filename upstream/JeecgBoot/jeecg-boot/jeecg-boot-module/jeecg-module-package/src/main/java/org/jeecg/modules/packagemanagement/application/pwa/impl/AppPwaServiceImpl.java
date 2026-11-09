package org.jeecg.modules.packagemanagement.application.pwa.impl;

import com.alibaba.fastjson2.JSONObject;



import org.jeecg.modules.packagemanagement.application.legacy.LegacyAjaxResult;
import org.jeecg.modules.packagemanagement.domain.pwa.AppPwa;
import org.jeecg.modules.packagemanagement.domain.pwa.PwaBody;
import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.AppPwaMapper;
import org.jeecg.modules.packagemanagement.application.pwa.IAppPwaService;
import org.jeecg.modules.packagemanagement.domain.system.ReviewsRecord;

import org.jeecg.modules.packagemanagement.infrastructure.persistence.mapper.ReviewsRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.jeecg.modules.packagemanagement.application.legacy.StringUtils;

import java.util.List;


/**
 * pwaService业务层处理
 *
 * @author Murphy
 * @date 2024-08-09
 */
@Service
public class AppPwaServiceImpl implements IAppPwaService
{
    private static final Logger log = LoggerFactory.getLogger(AppPwaServiceImpl.class);
    @Autowired
    private AppPwaMapper appPwaMapper;
    @Autowired
    private org.jeecg.modules.packagemanagement.config.PackageSnowflake snowflake;
    @Autowired
    private org.jeecg.modules.packagemanagement.application.port.PwaGateway gateway;
    @Autowired
    private org.jeecg.modules.packagemanagement.config.PackageManagementProperties settings;
    @Autowired
    private ReviewsRecordMapper reviewsRecordMapper;

    /**
     * 查询pwa
     *
     * @param id pwa主键
     * @return pwa
     */
    @Override
    public AppPwa selectAppPwaById(Long id)
    {
        AppPwa appPwa = appPwaMapper.selectAppPwaById(id);
        ReviewsRecord reviewsRecord = new ReviewsRecord();
        reviewsRecord.setAppId(id);
        List<ReviewsRecord> reviewsRecords =  reviewsRecordMapper.selectReviewsRecordList(reviewsRecord);
        appPwa.setReviewsRecords(reviewsRecords);
        return appPwa;
    }

    /**
     * 查询pwa列表
     *
     * @param appPwa pwa
     * @return pwa
     */
    @Override
    public List<AppPwa> selectAppPwaList(AppPwa appPwa) {

        return appPwaMapper.selectAppPwaList(appPwa);
    }

    @Override
    public List<AppPwa> selectAppPwaAppList(AppPwa appPwa) {
        return appPwaMapper.selectAppPwaList(appPwa);
    }

    /**
     * 新增pwa
     *
     * @param appPwa pwa
     * @return 结果
     */
    @Override
    @Transactional
    public LegacyAjaxResult insertAppPwa(AppPwa appPwa)
    {
        String rootPath = "autopwa";
        if (appPwa.getAppType().equals("1")){
            rootPath = "autow2a";
        }
        AppPwa app = new AppPwa();
        app.setAppHost(appPwa.getAppHost());
        app.setAppSub(appPwa.getAppSub());
        List<AppPwa> list = appPwaMapper.selectAppPwaList(app);
        if (!CollectionUtils.isEmpty(list) || list.size() > 0){
            return LegacyAjaxResult.error("推广链接重复");
        }

        String VAPID = gateway.generateVapidKeys();
        String s = gateway.createHost(
                PwaBody.CreateBoody("location  /{\n" +
                                "         root    "+settings.getPwaRoot()+rootPath+";\n" +
                                "         index   index.html;\n" +
                                "}",
                        Integer.parseInt(appPwa.getAppSubHost()),
                        appPwa.getAppHost() + appPwa.getAppSub()));

        JSONObject parse = JSONObject.parse(s);
        String id = parse.getString("id");
        appPwa.setLinkId(id);
        appPwa.setAppCode(String.valueOf(snowflake.nextId()));
        appPwa.setPublicKey(JSONObject.parse(VAPID).getString("publicKey"));
        appPwa.setPrivateKey(JSONObject.parse(VAPID).getString("privateKey"));
        int result = appPwaMapper.insertAppPwa(appPwa);
        if (result>0){
            appPwa.getReviewsRecords().forEach(e->{
                e.setAppId(appPwa.getId());
                if (!StringUtils.isEmpty(e.getReviewsId())){
                    reviewsRecordMapper.updateReviewsRecord(e);
                }else {
                    reviewsRecordMapper.insertReviewsRecord(e);
                }
            });
        }

        return result>0?LegacyAjaxResult.success():LegacyAjaxResult.error("添加失败！");
    }

    /**
     * 修改pwa
     *
     * @param appPwa pwa
     * @return 结果
     */
    @Override
    public LegacyAjaxResult updateAppPwa(AppPwa appPwa)
    {
        String rootPath = "autopwa";
        if (appPwa.getAppType().equals("1")){
            rootPath = "autow2a";
        }
        if (!StringUtils.isEmpty(appPwa.getAppHost()) && !StringUtils.isEmpty(appPwa.getAppSub())){
            AppPwa app = new AppPwa();
            app.setAppHost(appPwa.getAppHost());
            app.setAppSub(appPwa.getAppSub());
            List<AppPwa> list = appPwaMapper.selectAppPwaList(app);
            if (!CollectionUtils.isEmpty(list) && list.size() > 1){
                return LegacyAjaxResult.error("推广链接重复");
            }

        }




        gateway.updateHost(appPwa.getLinkId(),
                PwaBody.CreateBoody("location /{\n" +
                                "         root    "+settings.getPwaRoot()+rootPath+";\n" +
                                "         index   index.html;\n" +
                                "}",
                        Integer.parseInt(appPwa.getAppSubHost()),
                        appPwa.getAppHost() + appPwa.getAppSub()));

        Integer r = appPwaMapper.updateAppPwa(appPwa);
        if (r>0){
            appPwa.getReviewsRecords().forEach(e->{
                e.setAppId(appPwa.getId());
                if (!StringUtils.isEmpty(e.getReviewsId())){
                    reviewsRecordMapper.updateReviewsRecord(e);
                }else {
                    reviewsRecordMapper.insertReviewsRecord(e);
                }
            });
        }
        return r>0?LegacyAjaxResult.success():LegacyAjaxResult.error("修改失败！");
    }

    /**
     * 批量删除pwa
     *
     * @param ids 需要删除的pwa主键
     * @return 结果
     */
    @Override
    public int deleteAppPwaByIds(Long[] ids) {
        for (Long id : ids) {
            AppPwa appPwa = selectAppPwaById(id);
            String linkId = appPwa.getLinkId();
            gateway.deleteHost(linkId);
        }

        return appPwaMapper.deleteAppPwaByIds(ids);
    }

    /**
     * 删除pwa信息
     *
     * @param id pwa主键
     * @return 结果
     */
    @Override
    public int deleteAppPwaById(Long id) {
        AppPwa appPwa = selectAppPwaById(id);
        String linkId = appPwa.getLinkId();
        gateway.deleteHost(linkId);

        return appPwaMapper.deleteAppPwaById(id);
    }


}
