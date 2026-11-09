# package-api 源业务接口清单

日期：2026-11-09。来自本地 Java 控制器静态扫描，排除注释代码及通用 RuoYi 基础功能。

共 21 个有效业务控制器，95 个接口映射：75 个管理接口、20 个客户端/回调入口。`ANY` 表示源码未限制 HTTP 方法。

`类匿名` 与 `方法权限` 分别保留源信息；它们并存时仍需运行时验证，不能视为匿名可操作。行号指向源映射注解。

| 控制器 / 方法 | HTTP | 原路径 | 类匿名 | 方法权限 | 源文件行 |
| --- | --- | --- | --- | --- | --- |
| AppManagerController.list | GET | `/system/manager/list` | 否 | system:manager:query | PackServer/src/main/java/com/ruoyi/project/app/controller/AppManagerController.java:41 |
| AppManagerController.export | POST | `/system/manager/export` | 否 | system:manager:export | PackServer/src/main/java/com/ruoyi/project/app/controller/AppManagerController.java:53 |
| AppManagerController.getInfo | GET | `/system/manager/selbyid/{id}` | 否 | system:manager:query | PackServer/src/main/java/com/ruoyi/project/app/controller/AppManagerController.java:65 |
| AppManagerController.add | POST | `/system/manager/add` | 否 | system:manager:add | PackServer/src/main/java/com/ruoyi/project/app/controller/AppManagerController.java:75 |
| AppManagerController.edit | PUT | `/system/manager/update` | 否 | system:manager:edit | PackServer/src/main/java/com/ruoyi/project/app/controller/AppManagerController.java:91 |
| AppManagerController.remove | DELETE | `/system/manager/delete/{ids}` | 否 | system:manager:remove | PackServer/src/main/java/com/ruoyi/project/app/controller/AppManagerController.java:101 |
| AppManagerController.transfer | GET | `/system/manager/transfer/{id}/{userId}/{userName}` | 否 | system:manager:transfer | PackServer/src/main/java/com/ruoyi/project/app/controller/AppManagerController.java:114 |
| Client1Controller.login | ANY | `/v2/api/user/login` | 是 | — | PackServer/src/main/java/com/ruoyi/project/app/controller/Client1Controller.java:23 |
| Client1Controller.redirected | ANY | `/v2/api/retry/{a}` | 是 | — | PackServer/src/main/java/com/ruoyi/project/app/controller/Client1Controller.java:29 |
| Client1Controller.getBanner | ANY | `/v2/api/banner/list/{a}` | 是 | — | PackServer/src/main/java/com/ruoyi/project/app/controller/Client1Controller.java:34 |
| Client1Controller.getUrlBy | ANY | `/v2/api/check/url` | 是 | — | PackServer/src/main/java/com/ruoyi/project/app/controller/Client1Controller.java:40 |
| Client2Controller.getAesData | ANY | `/{a}/{b}/{c}` | 是 | — | PackServer/src/main/java/com/ruoyi/project/app/controller/Client2Controller.java:21 |
| ClientController.api4 | ANY | `/system/{a}` | 是 | — | PackServer/src/main/java/com/ruoyi/project/app/controller/ClientController.java:19 |
| ClientController.loadAdj | ANY | `/dynamic/{a}` | 是 | — | PackServer/src/main/java/com/ruoyi/project/app/controller/ClientController.java:25 |
| ClientController.base64Data | ANY | `/home/index/jsp` | 是 | — | PackServer/src/main/java/com/ruoyi/project/app/controller/ClientController.java:30 |
| ClientController.base64Data2 | ANY | `/{l}` | 是 | — | PackServer/src/main/java/com/ruoyi/project/app/controller/ClientController.java:36 |
| ClientController.base64Data3 | ANY | `/{l}/{a}` | 是 | — | PackServer/src/main/java/com/ruoyi/project/app/controller/ClientController.java:42 |
| ClientController.getUserInfo | ANY | `/app/start` | 是 | — | PackServer/src/main/java/com/ruoyi/project/app/controller/ClientController.java:48 |
| EsAppLogController.list | GET | `/system/applog/list` | 是 | system:applog:query | PackServer/src/main/java/com/ruoyi/project/applog/controller/EsAppLogController.java:74 |
| EsAppLogController.export | POST | `/system/applog/export` | 是 | system:applog:export | PackServer/src/main/java/com/ruoyi/project/applog/controller/EsAppLogController.java:109 |
| EsAppLogController.getInfo | GET | `/system/applog/{id}` | 是 | system:applog:query | PackServer/src/main/java/com/ruoyi/project/applog/controller/EsAppLogController.java:125 |
| EsAppLogController.add | POST | `/system/applog/add` | 是 | system:applog:add | PackServer/src/main/java/com/ruoyi/project/applog/controller/EsAppLogController.java:139 |
| EsAppLogController.edit | PUT | `/system/applog/update` | 是 | system:applog:edit | PackServer/src/main/java/com/ruoyi/project/applog/controller/EsAppLogController.java:150 |
| EsAppLogController.remove | DELETE | `/system/applog/{ids}` | 是 | system:applog:remove | PackServer/src/main/java/com/ruoyi/project/applog/controller/EsAppLogController.java:161 |
| EsAppLogController.clean | DELETE | `/system/applog/clean` | 是 | system:applog:clean | PackServer/src/main/java/com/ruoyi/project/applog/controller/EsAppLogController.java:173 |
| EventConfigController.list | GET | `/system/event_config/list` | 否 | system:event_config:query | PackServer/src/main/java/com/ruoyi/project/event/controller/EventConfigController.java:36 |
| EventConfigController.export | POST | `/system/event_config/export` | 否 | system:event_config:export | PackServer/src/main/java/com/ruoyi/project/event/controller/EventConfigController.java:49 |
| EventConfigController.getInfo | GET | `/system/event_config/{id}` | 否 | system:event_config:query | PackServer/src/main/java/com/ruoyi/project/event/controller/EventConfigController.java:61 |
| EventConfigController.add | POST | `/system/event_config` | 否 | system:event_config:add | PackServer/src/main/java/com/ruoyi/project/event/controller/EventConfigController.java:72 |
| EventConfigController.edit | PUT | `/system/event_config` | 否 | system:event_config:edit | PackServer/src/main/java/com/ruoyi/project/event/controller/EventConfigController.java:83 |
| EventConfigController.remove | DELETE | `/system/event_config/{ids}` | 否 | system:event_config:remove | PackServer/src/main/java/com/ruoyi/project/event/controller/EventConfigController.java:94 |
| EventErrorController.list | GET | `/system/error/list` | 否 | system:error:query | PackServer/src/main/java/com/ruoyi/project/event/controller/EventErrorController.java:35 |
| EventErrorController.export | POST | `/system/error/export` | 否 | system:error:export | PackServer/src/main/java/com/ruoyi/project/event/controller/EventErrorController.java:48 |
| EventErrorController.getInfo | GET | `/system/error/{id}` | 否 | system:error:query | PackServer/src/main/java/com/ruoyi/project/event/controller/EventErrorController.java:60 |
| EventErrorController.add | POST | `/system/error` | 否 | system:error:add | PackServer/src/main/java/com/ruoyi/project/event/controller/EventErrorController.java:71 |
| EventErrorController.edit | PUT | `/system/error` | 否 | system:error:edit | PackServer/src/main/java/com/ruoyi/project/event/controller/EventErrorController.java:82 |
| EventErrorController.remove | DELETE | `/system/error/{ids}` | 否 | system:error:remove | PackServer/src/main/java/com/ruoyi/project/event/controller/EventErrorController.java:93 |
| EvnetHistoryController.list | GET | `/system/history/list` | 否 | system:history:query | PackServer/src/main/java/com/ruoyi/project/event/controller/EvnetHistoryController.java:36 |
| EvnetHistoryController.export | POST | `/system/history/export` | 否 | system:history:export | PackServer/src/main/java/com/ruoyi/project/event/controller/EvnetHistoryController.java:49 |
| EvnetHistoryController.getInfo | GET | `/system/history/{id}` | 否 | system:history:query | PackServer/src/main/java/com/ruoyi/project/event/controller/EvnetHistoryController.java:61 |
| EvnetHistoryController.add | POST | `/system/history` | 否 | system:history:add | PackServer/src/main/java/com/ruoyi/project/event/controller/EvnetHistoryController.java:72 |
| EvnetHistoryController.edit | PUT | `/system/history` | 否 | system:history:edit | PackServer/src/main/java/com/ruoyi/project/event/controller/EvnetHistoryController.java:83 |
| EvnetHistoryController.remove | DELETE | `/system/history/{ids}` | 否 | system:history:remove | PackServer/src/main/java/com/ruoyi/project/event/controller/EvnetHistoryController.java:94 |
| FBController.fbEvent | ANY | `/fb/{packageId}` | 是 | — | PackServer/src/main/java/com/ruoyi/project/event/controller/FBController.java:23 |
| LoadCountAccessController.addAccess | ANY | `/loading/access/access` | 是 | — | PackServer/src/main/java/com/ruoyi/project/event/controller/LoadCountAccessController.java:23 |
| LoadCountAccessController.addDownload | ANY | `/loading/access/download` | 是 | — | PackServer/src/main/java/com/ruoyi/project/event/controller/LoadCountAccessController.java:47 |
| LoadCountController.list | GET | `/system/count/list` | 否 | system:count:list | PackServer/src/main/java/com/ruoyi/project/event/controller/LoadCountController.java:36 |
| LoadCountController.export | POST | `/system/count/export` | 否 | system:count:export | PackServer/src/main/java/com/ruoyi/project/event/controller/LoadCountController.java:49 |
| LoadCountController.getInfo | GET | `/system/count/{id}` | 否 | system:count:query | PackServer/src/main/java/com/ruoyi/project/event/controller/LoadCountController.java:61 |
| LoadCountController.add | POST | `/system/count` | 否 | system:count:add | PackServer/src/main/java/com/ruoyi/project/event/controller/LoadCountController.java:72 |
| LoadCountController.edit | PUT | `/system/count` | 否 | system:count:edit | PackServer/src/main/java/com/ruoyi/project/event/controller/LoadCountController.java:83 |
| LoadCountController.remove | DELETE | `/system/count/{ids}` | 否 | system:count:remove | PackServer/src/main/java/com/ruoyi/project/event/controller/LoadCountController.java:94 |
| PageController.page | ANY | `/page/{page}` | 是 | — | PackServer/src/main/java/com/ruoyi/project/event/controller/PageController.java:24 |
| AppNotifyController.list | GET | `/system/notify/list` | 否 | system:notify:list | PackServer/src/main/java/com/ruoyi/project/notify/controller/AppNotifyController.java:36 |
| AppNotifyController.export | POST | `/system/notify/export` | 否 | system:notify:export | PackServer/src/main/java/com/ruoyi/project/notify/controller/AppNotifyController.java:49 |
| AppNotifyController.getInfo | GET | `/system/notify/{id}` | 否 | system:notify:query | PackServer/src/main/java/com/ruoyi/project/notify/controller/AppNotifyController.java:61 |
| AppNotifyController.add | POST | `/system/notify` | 否 | system:notify:add | PackServer/src/main/java/com/ruoyi/project/notify/controller/AppNotifyController.java:72 |
| AppNotifyController.edit | PUT | `/system/notify` | 否 | system:notify:edit | PackServer/src/main/java/com/ruoyi/project/notify/controller/AppNotifyController.java:83 |
| AppNotifyController.remove | DELETE | `/system/notify/{ids}` | 否 | system:notify:remove | PackServer/src/main/java/com/ruoyi/project/notify/controller/AppNotifyController.java:94 |
| AppPwaController.list | GET | `/system/pwa/list` | 否 | system:pwa:list | PackServer/src/main/java/com/ruoyi/project/pwa/controller/AppPwaController.java:36 |
| AppPwaController.all | GET | `/system/pwa/all` | 否 | system:pwa:list | PackServer/src/main/java/com/ruoyi/project/pwa/controller/AppPwaController.java:47 |
| AppPwaController.export | POST | `/system/pwa/export` | 否 | system:pwa:export | PackServer/src/main/java/com/ruoyi/project/pwa/controller/AppPwaController.java:58 |
| AppPwaController.getInfo | GET | `/system/pwa/{id}` | 否 | system:pwa:list | PackServer/src/main/java/com/ruoyi/project/pwa/controller/AppPwaController.java:70 |
| AppPwaController.add | POST | `/system/pwa` | 否 | system:pwa:add | PackServer/src/main/java/com/ruoyi/project/pwa/controller/AppPwaController.java:81 |
| AppPwaController.edit | PUT | `/system/pwa` | 否 | system:pwa:edit | PackServer/src/main/java/com/ruoyi/project/pwa/controller/AppPwaController.java:92 |
| AppPwaController.remove | DELETE | `/system/pwa/{ids}` | 否 | system:pwa:remove | PackServer/src/main/java/com/ruoyi/project/pwa/controller/AppPwaController.java:104 |
| PwaController.getInfo | GET | `/pwa/{path}` | 是 | — | PackServer/src/main/java/com/ruoyi/project/pwa/controller/PwaController.java:29 |
| PwaNotifyController.subscribe | ANY | `/notify/subscribe` | 是 | — | PackServer/src/main/java/com/ruoyi/project/pwa/controller/PwaNotifyController.java:54 |
| PwaNotifyController.get | ANY | `/notify/notifyweb` | 是 | — | PackServer/src/main/java/com/ruoyi/project/pwa/controller/PwaNotifyController.java:61 |
| AppStatisticsController.upload | ANY | `/systemSDK/upload/data` | 是 | — | PackServer/src/main/java/com/ruoyi/project/statistics/controller/AppStatisticsController.java:24 |
| AppStatisticsController.getSwitch | ANY | `/systemSDK/{a}` | 是 | — | PackServer/src/main/java/com/ruoyi/project/statistics/controller/AppStatisticsController.java:30 |
| WebStatisticsController.list | GET | `/system/statistics/list` | 否 | system:statistics:query | PackServer/src/main/java/com/ruoyi/project/statistics/controller/WebStatisticsController.java:46 |
| WebStatisticsController.export | POST | `/system/statistics/export` | 否 | system:statistics:export | PackServer/src/main/java/com/ruoyi/project/statistics/controller/WebStatisticsController.java:58 |
| WebStatisticsController.getInfo | GET | `/system/statistics/{id}` | 否 | system:statistics:query | PackServer/src/main/java/com/ruoyi/project/statistics/controller/WebStatisticsController.java:69 |
| WebStatisticsController.add | POST | `/system/statistics` | 否 | system:statistics:add | PackServer/src/main/java/com/ruoyi/project/statistics/controller/WebStatisticsController.java:79 |
| WebStatisticsController.edit | PUT | `/system/statistics` | 否 | system:statistics:edit | PackServer/src/main/java/com/ruoyi/project/statistics/controller/WebStatisticsController.java:95 |
| WebStatisticsController.remove | DELETE | `/system/statistics/{ids}` | 否 | system:statistics:remove | PackServer/src/main/java/com/ruoyi/project/statistics/controller/WebStatisticsController.java:111 |
| WebStatisticsController.getTotal | GET | `/system/statistics/getTotal` | 否 | system:statistics:query | PackServer/src/main/java/com/ruoyi/project/statistics/controller/WebStatisticsController.java:117 |
| StoreStatusController.list | GET | `/system/store/list` | 是 | system:store:query | PackServer/src/main/java/com/ruoyi/project/store/controller/StoreStatusController.java:29 |
| StoreStatusController.add | POST | `/system/store/add` | 是 | system:store:add | PackServer/src/main/java/com/ruoyi/project/store/controller/StoreStatusController.java:42 |
| StoreStatusController.select | GET | `/system/store/{id}` | 是 | system:store:query | PackServer/src/main/java/com/ruoyi/project/store/controller/StoreStatusController.java:58 |
| StoreStatusController.remove | DELETE | `/system/store/{ids}` | 是 | system:store:remove | PackServer/src/main/java/com/ruoyi/project/store/controller/StoreStatusController.java:71 |
| StoreStatusController.edit | PUT | `/system/store/update` | 是 | system:manager:edit | PackServer/src/main/java/com/ruoyi/project/store/controller/StoreStatusController.java:82 |
| AppEventRecordController.list | GET | `/system/record/list` | 否 | system:record:list | PackServer/src/main/java/com/ruoyi/project/system/controller/AppEventRecordController.java:41 |
| AppEventRecordController.export | POST | `/system/record/export` | 否 | system:record:export | PackServer/src/main/java/com/ruoyi/project/system/controller/AppEventRecordController.java:54 |
| AppEventRecordController.getInfo | GET | `/system/record/{eventId}` | 否 | system:record:query | PackServer/src/main/java/com/ruoyi/project/system/controller/AppEventRecordController.java:66 |
| AppEventRecordController.add | POST | `/system/record` | 否 | system:record:add | PackServer/src/main/java/com/ruoyi/project/system/controller/AppEventRecordController.java:77 |
| AppEventRecordController.edit | PUT | `/system/record` | 否 | system:record:edit | PackServer/src/main/java/com/ruoyi/project/system/controller/AppEventRecordController.java:88 |
| AppEventRecordController.remove | DELETE | `/system/record/{eventIds}` | 否 | system:record:remove | PackServer/src/main/java/com/ruoyi/project/system/controller/AppEventRecordController.java:99 |
| ReviewsRecordController.list | GET | `/system/reviewsrecord/list` | 否 | system:record:list | PackServer/src/main/java/com/ruoyi/project/system/controller/ReviewsRecordController.java:41 |
| ReviewsRecordController.export | POST | `/system/reviewsrecord/export` | 否 | system:record:export | PackServer/src/main/java/com/ruoyi/project/system/controller/ReviewsRecordController.java:54 |
| ReviewsRecordController.getInfo | GET | `/system/reviewsrecord/{reviewsId}` | 否 | system:record:query | PackServer/src/main/java/com/ruoyi/project/system/controller/ReviewsRecordController.java:66 |
| ReviewsRecordController.add | POST | `/system/reviewsrecord` | 否 | system:record:add | PackServer/src/main/java/com/ruoyi/project/system/controller/ReviewsRecordController.java:77 |
| ReviewsRecordController.edit | PUT | `/system/reviewsrecord` | 否 | system:record:edit | PackServer/src/main/java/com/ruoyi/project/system/controller/ReviewsRecordController.java:88 |
| ReviewsRecordController.remove | DELETE | `/system/reviewsrecord/{reviewsIds}` | 否 | system:record:remove | PackServer/src/main/java/com/ruoyi/project/system/controller/ReviewsRecordController.java:99 |

注意：AppLogController 整体被注释，实际日志控制器为 EsAppLogController；AppEventApi 只有类映射，没有处理方法，未计入有效接口。
