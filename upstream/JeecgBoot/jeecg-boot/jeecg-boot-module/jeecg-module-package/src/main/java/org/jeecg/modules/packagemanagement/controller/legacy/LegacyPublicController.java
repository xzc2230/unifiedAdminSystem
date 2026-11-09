package org.jeecg.modules.packagemanagement.controller.legacy;

import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import org.jeecg.modules.packagemanagement.application.event.*;
import org.jeecg.modules.packagemanagement.application.legacy.LegacyAjaxResult;
import org.jeecg.modules.packagemanagement.application.notification.IAppNotifyService;
import org.jeecg.modules.packagemanagement.application.pwa.*;
import org.jeecg.modules.packagemanagement.application.statistics.IAppStatisticsService;
import org.jeecg.modules.packagemanagement.domain.event.*;
import org.jeecg.modules.packagemanagement.domain.pwa.*;
import org.jeecg.modules.packagemanagement.domain.notify.AppNotify;
import org.jeecg.modules.packagemanagement.domain.system.ReviewsRecord;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/package-legacy")
public class LegacyPublicController {
    @org.springframework.beans.factory.annotation.Autowired private org.jeecg.modules.packagemanagement.application.port.PushPort push;
    @org.springframework.beans.factory.annotation.Autowired private PackageRepeatSubmit repeat;
    @org.springframework.beans.factory.annotation.Autowired private org.jeecg.modules.packagemanagement.infrastructure.identity.PackagePublicOwnership ownership;
    private final IEvnetHistoryService history;
    private final IEventConfigService events;
    private final ILoadCountService counts;
    private final IAppPwaService pwa;
    private final IAppStatisticsService statistics;
    private final IAppPwaSubService subscriptions;
    private final IAppNotifyService notifications;
    private final IReviewsRecordService reviews;
    public LegacyPublicController(IEvnetHistoryService history, IEventConfigService events, ILoadCountService counts,
            IAppPwaService pwa, IAppStatisticsService statistics, IAppPwaSubService subscriptions,
            IAppNotifyService notifications, IReviewsRecordService reviews) {
        this.history=history; this.events=events; this.counts=counts; this.pwa=pwa; this.statistics=statistics;
        this.subscriptions=subscriptions; this.notifications=notifications; this.reviews=reviews;
    }

    @RequestMapping("/fb/{packageId}")
    public void fb(@PathVariable String packageId, FBEvent event) {
        EvnetHistory row = new EvnetHistory();
        row.setEventName(event.getEvent_name()); row.setEventTime(event.getEvent_time());
        row.setEventSource(event.getEvent_source()); row.setMediaSource(event.getMedia_source());
        row.setCampaign(event.getCampaign()); row.setAfSub1(event.getAf_sub1()); row.setAfSub2(event.getAf_sub2());
        row.setAfSub3(event.getAf_sub3()); row.setRegion(event.getRegion()); row.setCountryCode(event.getCountry_code());
        row.setState(event.getState()); row.setCity(event.getCity()); row.setPostalCode(event.getPostal_code());
        row.setDma(event.getDma()); row.setIp(event.getIp()); row.setWifi(event.getWifi());
        row.setOperator(event.getOperator()); row.setLanguage(event.getLanguage());
        row.setAppsflyerId(event.getAppsflyer_id()); row.setPlatform(event.getPlatform());
        row.setOsVersion(event.getOs_version()); row.setAppVersion(event.getApp_version());
        row.setSdkVersion(event.getSdk_version()); row.setAppId(event.getApp_id());
        row.setAppName(event.getApp_name()); row.setBundleId(event.getBundle_id());
        row.setIsRetargeting(event.getIs_retargeting()); row.setUserAgent(event.getUser_agent());
        row.setHttpReferrer(event.getHttp_referrer()); row.setOriginalUrl(event.getOriginal_url());
        row.setDeviceDownloadTime(event.getDevice_download_time()); row.setConversionType(event.getConversion_type());
        row.setDeviceModel(event.getDevice_model()); row.setJsonStr(event.getJson_str());
        row.setPath(event.getPath()); row.setStatus(event.getStatus()); row.setPackageName(event.getPackage_name());
        row.setSelected_currency(event.getSelected_currency()); row.setEvent_revenue_usd(event.getEvent_revenue_usd());
        history.fbEvent(packageId,row);
    }

    @RequestMapping("/page/{page}")
    public void page(@PathVariable String page, HttpServletRequest request, HttpServletResponse response) throws IOException {
        events.page(page, LegacyRequest.wrap(request), response);
    }

    @org.springframework.transaction.annotation.Transactional
    @RequestMapping("/loading/access/access")
    public void access(HttpServletRequest request) { increment(request.getParameter("hostname"), true); }
    @org.springframework.transaction.annotation.Transactional
    @RequestMapping("/loading/access/download")
    public void download(HttpServletRequest request) { increment(request.getParameter("hostname"), false); }
    private void increment(String hostname, boolean access) {
        LoadCount row=counts.selectLoadCountByHostName(hostname);
        boolean fresh=row==null;
        if(fresh) { row=new LoadCount(); row.setHostName(hostname); }
        if(access) row.setAccess((row.getAccess()==null?0:row.getAccess())+1);
        else row.setDownload((row.getDownload()==null?0:row.getDownload())+1);
        row.setTime(new Date());
        if(fresh) { if(counts.insertLoadCount(row)>0&&row.getAppId()!=null) ownership.inherit("pkg_load_count",row.getId(),"pkg_pwa",row.getAppId()); } else counts.updateLoadCount(row);
    }

    @GetMapping("/pwa/{path}")
    public Object pwa(@PathVariable String path) {
        try {
            AppPwa filter=new AppPwa(); filter.setAppCode(path);
            List<AppPwa> rows=pwa.selectAppPwaAppList(filter);
            if(rows==null||rows.isEmpty()) return "";
            AppPwa row=rows.get(0);
            Map<String,Object> data=new HashMap<>();
            data.put("sysLanguage",row.getAppLanguage()); data.put("applicationName",row.getAppName());
            data.put("developerName",row.getAppName()); data.put("appDesc",row.getAppDescription());
            data.put("baseUrl",row.getAppUrl()); data.put("icon",row.getAppIcon());
            data.put("bannerImg",row.getAppAdvertising().split(",")); data.put("eventName",row.getAppEvent());
            data.put("eventConfig",row.getAppEventConf()); data.put("updateTime","2023-03-01");
            data.put("pubKey",row.getPublicKey()); data.put("downloadUrl",row.getDownloadUrl());
            data.put("types",JSON.parseArray(row.getTags(),String.class)); data.put("score",row.getScore());
            ReviewsRecord filterReview=new ReviewsRecord(); filterReview.setAppId(row.getId());
            data.put("reviewsRecordList",reviews.selectReviewsRecordList(filterReview));
            data.put("reviews",row.getReviews()); data.put("downloadCount",row.getDownloadCount());
            return data;
        } catch(Exception failure) { return ""; }
    }

    @RequestMapping("/notify/subscribe")
    public LegacyAjaxResult subscribe(@RequestBody AppPwaSub row) {
        row.setId(null); row.setAppId(null);
        int affected=subscriptions.insertAppPwaSub(row);

        return affected>0?LegacyAjaxResult.success():LegacyAjaxResult.error();
    }
    @RequestMapping("/notify/notifyweb")
    public Object notifyWeb(HttpServletRequest request) {
        String id=request.getParameter("notifyId");
        if(id==null||id.isEmpty()) return LegacyAjaxResult.error("notifyId不能为空");
        AppNotify notify=notifications.selectAppNotifyById(Long.valueOf(id));
        if(notify==null) return LegacyAjaxResult.error("订阅信息不存在");
        AppPwaSub filter=new AppPwaSub(); filter.setAppId(notify.getAppId());
        List<AppPwaSub> rows=subscriptions.selectAppPwaSubList(filter);
        if(rows==null||rows.isEmpty()) return LegacyAjaxResult.error("订阅信息不存在");
        try {
            String publicKey=request.getParameter("pubKey"),privateKey=request.getParameter("priKey");
            push.validateKeys(publicKey,privateKey);
            var json=new com.fasterxml.jackson.databind.ObjectMapper();
            for(var row:rows)try { push.send(publicKey,privateKey,row,json.writeValueAsBytes(new WebPushMessage(notify.getNotifyTitle(),notify.getNotifyIcon(),notify.getNotifyMessage(),notify.getAppId().toString()))); } catch(Exception ignored) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Package public push subscription failed"); }
            return LegacyAjaxResult.success("推送成功");
        } catch(Exception failure) { return LegacyAjaxResult.error("推送失败"); }
    }

    @RequestMapping("/systemSDK/upload/data")
    public Object uploadData(HttpServletRequest request) { return statistics.uploadData(LegacyRequest.wrap(request)); }
    @RequestMapping("/systemSDK/{a}")
    public Object systemSdk(HttpServletRequest request,@RequestBody(required=false) String body) {
        if(repeat!=null&&repeat.repeated(request,body))return LegacyAjaxResult.error("请求过于频繁");
        return statistics.getDetails(LegacyRequest.wrap(request));
    }
}
