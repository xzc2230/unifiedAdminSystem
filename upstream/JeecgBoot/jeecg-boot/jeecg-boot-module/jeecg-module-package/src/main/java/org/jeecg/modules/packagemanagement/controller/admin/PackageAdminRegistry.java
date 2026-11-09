package org.jeecg.modules.packagemanagement.controller.admin;

import java.util.*;
import org.jeecg.modules.packagemanagement.application.apps.IAppManagerService;
import org.jeecg.modules.packagemanagement.application.event.*;
import org.jeecg.modules.packagemanagement.application.notification.IAppNotifyService;
import org.jeecg.modules.packagemanagement.application.pwa.*;
import org.jeecg.modules.packagemanagement.application.statistics.IAppStatisticsService;
import org.jeecg.modules.packagemanagement.application.store.IStoreStatusService;
import org.jeecg.modules.packagemanagement.domain.app.AppManager;
import org.jeecg.modules.packagemanagement.domain.event.*;
import org.jeecg.modules.packagemanagement.domain.notify.AppNotify;
import org.jeecg.modules.packagemanagement.domain.pwa.AppPwa;
import org.jeecg.modules.packagemanagement.domain.statistics.AppStatistics;
import org.jeecg.modules.packagemanagement.domain.store.StoreStatus;
import org.jeecg.modules.packagemanagement.domain.system.*;
import org.jeecg.modules.packagemanagement.exception.PackageFailure;
import org.jeecg.modules.packagemanagement.infrastructure.identity.PackageAccess;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public final class PackageAdminRegistry {
    private final Map<String,ManagedResource<?>> resources=new HashMap<>();
    public PackageAdminRegistry(IAppManagerService apps,IStoreStatusService store,IAppStatisticsService statistics,
            IEventConfigService configs,IEvnetHistoryService history,IEventErrorService errors,
            IAppPwaService pwa,ILoadCountService counts,IAppEventRecordService appEvents,
            IReviewsRecordService reviews,IAppNotifyService notifications,PackageAccess access,
            TransactionTemplate tx,PackageResourcePolicy policy) {
        resources.put("apps",new ManagedResource<>("pkg_app_manager",AppManager.class,AppManager::getId,
                apps::selectAppManagerList,apps::selectAppManagerById,apps::insertAppManager,apps::updateAppManager,
                apps::deleteAppManagerByIds,(r,a)->r.setUserId(access.effectiveLegacyUserId(a)),
                List.of("appName","areas","author","mark","packageCode","status","url","whetherRestrict","whetherUs","whetherUse","gaidAidThreshold","aidDeviceThreshold","aidGaidSwitchThreshold","riskWindowSeconds","referrerBlacklistJson"),
                cols("packageCode","包名","appName","应用名称","status","状态","url","地址","areas","地区"),access,tx));
        resources.put("store",new ManagedResource<>("pkg_store_status",StoreStatus.class,StoreStatus::getId,
                store::selectStoreStatusList,store::selectStoreStatusById,store::insertStoreStatus,store::updateStoreStatus,
                store::deleteStoreStatusByIds,(r,a)->{},List.of("packageCode","storeUrl","status","successEmail","failEmail"),
                cols("packageCode","包名","storeUrl","商店地址","status","状态","successDate","成功时间","failDate","失败时间"),access,tx));
        resources.put("statistics",new ManagedResource<>("pkg_app_statistics",AppStatistics.class,AppStatistics::getId,
                statistics::selectAppStatisticsList,statistics::selectAppStatisticsById,statistics::insertAppStatistics,
                statistics::updateAppStatistics,statistics::deleteAppStatisticsByIds,
                (r,a)->r.setUserId(access.effectiveLegacyUserIdInt(a)),
                List.of("packageCode","status","url","subAreas","subUrl","shunt"),
                cols("packageCode","包名","reigisterNum","注册总数","paymentNum","充值总数","paymentTotal","充值总额","url","主链接","subAreas","子地区","subUrl","子链接","status","状态","shunt","隐藏开关"),access,tx));
        resources.put("event-configs",new ManagedResource<>("pkg_event_config",EventConfig.class,EventConfig::getId,
                configs::selectEventConfigList,configs::selectEventConfigById,configs::insertEventConfig,
                configs::updateEventConfig,configs::deleteEventConfigByIds,(r,a)->{},
                List.of("packageId","packageToken","packagePixel","appName","url"),
                cols("packageId","包名","appName","应用名称","url","地址"),access,tx));
        resources.put("event-history",new ManagedResource<>("pkg_event_history",EvnetHistory.class,EvnetHistory::getId,
                history::selectEvnetHistoryList,history::selectEvnetHistoryById,history::insertEvnetHistory,
                history::updateEvnetHistory,history::deleteEvnetHistoryByIds,(r,a)->{},
                List.of("eventName","eventTime","eventSource","packageName","status","mediaSource","campaign","afSub1","afSub2","afSub3","region","countryCode","state","city","postalCode","dma","ip","wifi","operator","language","appsflyerId","platform","osVersion","appVersion","sdkVersion","appId","appName","bundleId","isRetargeting","userAgent","httpReferrer","originalUrl","deviceDownloadTime","conversionType","deviceModel","jsonStr","path","selected_currency","event_revenue_usd"),
                cols("eventName","事件名","eventTime","事件时间","eventSource","事件来源","mediaSource","媒体来源","campaign","广告活动","afSub1","子参数1","afSub2","子参数2","afSub3","子参数3","region","区域","countryCode","国家代码","state","州省","city","城市","postalCode","邮编","dma","市场区域","ip","IP","wifi","无线网络","operator","运营商","language","语言","appsflyerId","归因ID","platform","平台","osVersion","系统版本","appVersion","应用版本","sdkVersion","SDK版本","appId","应用ID","appName","应用名","bundleId","包ID","isRetargeting","再营销","userAgent","用户代理","httpReferrer","来源地址","originalUrl","原始地址","deviceDownloadTime","设备下载时间","conversionType","转化类型","deviceModel","设备型号","jsonStr","事件内容","path","路径","status","同步状态","packageName","包名","selected_currency","币种","event_revenue_usd","收入美元"),access,tx));
        resources.put("event-errors",new ManagedResource<>("pkg_event_error",EventError.class,EventError::getId,
                errors::selectEventErrorList,errors::selectEventErrorById,errors::insertEventError,
                errors::updateEventError,errors::deleteEventErrorByIds,(r,a)->{},
                List.of("errorPackage","errorMessage","errorContent"),
                cols("errorPackage","包名","errorMessage","错误消息","errorContent","错误详情"),access,tx));
        resources.put("pwa",new ManagedResource<>("pkg_pwa",AppPwa.class,AppPwa::getId,
                pwa::selectAppPwaList,pwa::selectAppPwaById,r->pwa.insertAppPwa(r).isError()?0:1,
                r->pwa.updateAppPwa(r).isError()?0:1,pwa::deleteAppPwaByIds,
                (r,a)->{r.setUserId(access.effectiveLegacyUserId(a));},
                List.of("appName","appIcon","appAdvertising","appDescription","appLanguage","appHost",
                        "appSubHost","appSub","appUrl","appEvent","appEventConf","downloadUrl","appType",
                        "score","tags","reviews","downloadCount","reviewsRecords"),
                cols("appName","应用名","appIcon","应用icon","appAdvertising","应用五图","appDescription","应用描述","appLanguage","应用语言","appHost","域名","appSubHost","子域名ID","appSub","子域名","appUrl","包网链接","appEvent","应用事件","appEventConf","事件配置","linkId","推广链接ID"),access,tx));
        resources.put("landing-counts",new ManagedResource<>("pkg_load_count",LoadCount.class,LoadCount::getId,
                counts::selectLoadCountList,counts::selectLoadCountById,counts::insertLoadCount,counts::updateLoadCount,
                counts::deleteLoadCountByIds,(r,a)->r.setUserId(access.effectiveLegacyUserId(a)),
                List.of("hostName","appName","time","access","download"),
                cols("hostName","域名","time","时间","access","访问量","download","下载量"),access,tx));
        resources.put("app-events",new ManagedResource<>("pkg_app_event",AppEventRecord.class,AppEventRecord::getEventId,
                appEvents::selectAppEventRecordList,appEvents::selectAppEventRecordByEventId,
                appEvents::insertAppEventRecord,appEvents::updateAppEventRecord,appEvents::deleteAppEventRecordByEventIds,
                (r,a)->r.setUserId(access.effectiveLegacyUserId(a)),
                List.of("appId","hostName","eventType","eventTypeName","deviceNo","ip"),
                cols("hostName","域名","eventType","事件类型","eventTypeName","事件名称","deviceNo","设备编号","ip","IP"),access,tx));
        resources.put("reviews",new ManagedResource<>("pkg_review",ReviewsRecord.class,ReviewsRecord::getReviewsId,
                reviews::selectReviewsRecordList,reviews::selectReviewsRecordByReviewsId,
                reviews::insertReviewsRecord,reviews::updateReviewsRecord,reviews::deleteReviewsRecordByReviewsIds,
                (r,a)->{},List.of("appId","headPortraitUrl","name","content","score"),
                cols("headPortraitUrl","头像","name","名字","content","内容","score","评分"),access,tx));
        resources.put("notifications",new ManagedResource<>("pkg_notification",AppNotify.class,AppNotify::getId,
                notifications::selectAppNotifyList,notifications::selectAppNotifyById,
                r->notifications.insertAppNotify(r).isError()?0:1,notifications::updateAppNotify,
                notifications::deleteAppNotifyByIds,(r,a)->r.setUserId(access.effectiveLegacyUserId(a)),
                List.of("appId","notifyTitle","notifyMessage","notifyIcon"),
                cols("appId","appId","appName","app名称","appIcon","ap图标","appHost","ap域名","notifyTitle","通知标题","userId","用户ID","notifyMessage","通知内容","notifyIcon","通知图标"),access,tx));
        resources.values().forEach(policy::attach);
    }
    private static Map<String,String> cols(String... names) {
        Map<String,String> map=new LinkedHashMap<>();
        for(int i=0;i<names.length;i+=2) map.put(names[i],names[i+1]);
        return map;
    }
    public ManagedResource<?> get(String domain) {
        ManagedResource<?> resource=resources.get(domain);
        if(resource==null) throw new PackageFailure(404,"未知管理域");
        return resource;
    }
}
