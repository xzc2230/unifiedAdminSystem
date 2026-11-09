# 源项目全量接口去向清单

日期：2026-11-09。静态复核，不代表已迁移或通过行为回归。

共 234 个方法级映射注解，展开为 235 条路径；45 个有处理方法的控制器，另有 1 个空控制器。

| 控制器/方法 | HTTP | 源路径 | 处理方式 | 目标/边界 | 源文件行 |
| --- | --- | --- | --- | --- | --- |
| AppManagerController.list | GET | `/system/manager/list` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/app/controller/AppManagerController.java:41 |
| AppManagerController.export | POST | `/system/manager/export` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/app/controller/AppManagerController.java:53 |
| AppManagerController.getInfo | GET | `/system/manager/selbyid/{id}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/app/controller/AppManagerController.java:65 |
| AppManagerController.add | POST | `/system/manager/add` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/app/controller/AppManagerController.java:75 |
| AppManagerController.edit | PUT | `/system/manager/update` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/app/controller/AppManagerController.java:91 |
| AppManagerController.remove | DELETE | `/system/manager/delete/{ids}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/app/controller/AppManagerController.java:101 |
| AppManagerController.transfer | GET | `/system/manager/transfer/{id}/{userId}/{userName}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/app/controller/AppManagerController.java:114 |
| Client1Controller.login | ANY | `/v2/api/user/login` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/app/controller/Client1Controller.java:23 |
| Client1Controller.redirected | ANY | `/v2/api/retry/{a}` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/app/controller/Client1Controller.java:29 |
| Client1Controller.getBanner | ANY | `/v2/api/banner/list/{a}` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/app/controller/Client1Controller.java:34 |
| Client1Controller.getUrlBy | ANY | `/v2/api/check/url` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/app/controller/Client1Controller.java:40 |
| Client2Controller.getAesData | ANY | `/{a}/{b}/{c}` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/app/controller/Client2Controller.java:21 |
| ClientController.api4 | ANY | `/system/{a}` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/app/controller/ClientController.java:19 |
| ClientController.loadAdj | ANY | `/dynamic/{a}` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/app/controller/ClientController.java:25 |
| ClientController.base64Data | ANY | `/home/index/jsp` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/app/controller/ClientController.java:30 |
| ClientController.base64Data2 | ANY | `/{l}` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/app/controller/ClientController.java:36 |
| ClientController.base64Data3 | ANY | `/{l}/{a}` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/app/controller/ClientController.java:42 |
| ClientController.getUserInfo | ANY | `/app/start` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/app/controller/ClientController.java:48 |
| EsAppLogController.list | GET | `/system/applog/list` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/applog/controller/EsAppLogController.java:74 |
| EsAppLogController.export | POST | `/system/applog/export` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/applog/controller/EsAppLogController.java:109 |
| EsAppLogController.getInfo | GET | `/system/applog/{id}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/applog/controller/EsAppLogController.java:125 |
| EsAppLogController.add | POST | `/system/applog/add` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/applog/controller/EsAppLogController.java:139 |
| EsAppLogController.edit | PUT | `/system/applog/update` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/applog/controller/EsAppLogController.java:150 |
| EsAppLogController.remove | DELETE | `/system/applog/{ids}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/applog/controller/EsAppLogController.java:161 |
| EsAppLogController.clean | DELETE | `/system/applog/clean` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/applog/controller/EsAppLogController.java:173 |
| CaptchaController.getCode | GET | `/captchaImage` | 复用：宿主基础能力 | JEECG 登录验证码 | PackServer/src/main/java/com/ruoyi/project/common/CaptchaController.java:51 |
| CommonController.fileDownload | GET | `/common/download` | 补充迁移：资源兼容 | /package-legacy/common/...；新管理上传使用 JEECG 组件适配 | PackServer/src/main/java/com/ruoyi/project/common/CommonController.java:49 |
| CommonController.uploadFile | POST | `/common/upload` | 补充迁移：资源兼容 | /package-legacy/common/...；新管理上传使用 JEECG 组件适配 | PackServer/src/main/java/com/ruoyi/project/common/CommonController.java:78 |
| CommonController.uploadFiles | POST | `/common/uploads` | 补充迁移：资源兼容 | /package-legacy/common/...；新管理上传使用 JEECG 组件适配 | PackServer/src/main/java/com/ruoyi/project/common/CommonController.java:104 |
| CommonController.resourceDownload | GET | `/common/download/resource` | 补充迁移：资源兼容 | /package-legacy/common/...；新管理上传使用 JEECG 组件适配 | PackServer/src/main/java/com/ruoyi/project/common/CommonController.java:141 |
| EventConfigController.list | GET | `/system/event_config/list` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EventConfigController.java:36 |
| EventConfigController.export | POST | `/system/event_config/export` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EventConfigController.java:49 |
| EventConfigController.getInfo | GET | `/system/event_config/{id}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EventConfigController.java:61 |
| EventConfigController.add | POST | `/system/event_config` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EventConfigController.java:72 |
| EventConfigController.edit | PUT | `/system/event_config` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EventConfigController.java:83 |
| EventConfigController.remove | DELETE | `/system/event_config/{ids}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EventConfigController.java:94 |
| EventErrorController.list | GET | `/system/error/list` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EventErrorController.java:35 |
| EventErrorController.export | POST | `/system/error/export` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EventErrorController.java:48 |
| EventErrorController.getInfo | GET | `/system/error/{id}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EventErrorController.java:60 |
| EventErrorController.add | POST | `/system/error` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EventErrorController.java:71 |
| EventErrorController.edit | PUT | `/system/error` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EventErrorController.java:82 |
| EventErrorController.remove | DELETE | `/system/error/{ids}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EventErrorController.java:93 |
| EvnetHistoryController.list | GET | `/system/history/list` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EvnetHistoryController.java:36 |
| EvnetHistoryController.export | POST | `/system/history/export` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EvnetHistoryController.java:49 |
| EvnetHistoryController.getInfo | GET | `/system/history/{id}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EvnetHistoryController.java:61 |
| EvnetHistoryController.add | POST | `/system/history` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EvnetHistoryController.java:72 |
| EvnetHistoryController.edit | PUT | `/system/history` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EvnetHistoryController.java:83 |
| EvnetHistoryController.remove | DELETE | `/system/history/{ids}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/EvnetHistoryController.java:94 |
| FBController.fbEvent | ANY | `/fb/{packageId}` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/event/controller/FBController.java:23 |
| LoadCountAccessController.addAccess | ANY | `/loading/access/access` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/event/controller/LoadCountAccessController.java:23 |
| LoadCountAccessController.addDownload | ANY | `/loading/access/download` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/event/controller/LoadCountAccessController.java:47 |
| LoadCountController.list | GET | `/system/count/list` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/LoadCountController.java:36 |
| LoadCountController.export | POST | `/system/count/export` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/LoadCountController.java:49 |
| LoadCountController.getInfo | GET | `/system/count/{id}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/LoadCountController.java:61 |
| LoadCountController.add | POST | `/system/count` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/LoadCountController.java:72 |
| LoadCountController.edit | PUT | `/system/count` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/LoadCountController.java:83 |
| LoadCountController.remove | DELETE | `/system/count/{ids}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/event/controller/LoadCountController.java:94 |
| PageController.page | ANY | `/page/{page}` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/event/controller/PageController.java:24 |
| CacheController.getInfo | GET | `/monitor/cache` | 复用：宿主基础能力 | JEECG 缓存监控；清理粒度和权限待核对 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/CacheController.java:48 |
| CacheController.cache | GET | `/monitor/cache/getNames` | 复用：宿主基础能力 | JEECG 缓存监控；清理粒度和权限待核对 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/CacheController.java:72 |
| CacheController.getCacheKeys | GET | `/monitor/cache/getKeys/{cacheName}` | 复用：宿主基础能力 | JEECG 缓存监控；清理粒度和权限待核对 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/CacheController.java:79 |
| CacheController.getCacheValue | GET | `/monitor/cache/getValue/{cacheName}/{cacheKey}` | 复用：宿主基础能力 | JEECG 缓存监控；清理粒度和权限待核对 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/CacheController.java:87 |
| CacheController.clearCacheName | DELETE | `/monitor/cache/clearCacheName/{cacheName}` | 复用：宿主基础能力 | JEECG 缓存监控；清理粒度和权限待核对 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/CacheController.java:96 |
| CacheController.clearCacheKey | DELETE | `/monitor/cache/clearCacheKey/{cacheKey}` | 复用：宿主基础能力 | JEECG 缓存监控；清理粒度和权限待核对 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/CacheController.java:105 |
| CacheController.clearCacheAll | DELETE | `/monitor/cache/clearCacheAll` | 复用：宿主基础能力 | JEECG 缓存监控；清理粒度和权限待核对 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/CacheController.java:113 |
| ServerController.getInfo | GET | `/monitor/server` | 复用：宿主基础能力 | JEECG 系统监控 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/ServerController.java:20 |
| SysJobController.list | GET | `/monitor/job/list` | 复用：宿主基础能力 | JEECG Quartz 任务；业务调用表达式与调度定义转换 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysJobController.java:46 |
| SysJobController.export | POST | `/monitor/job/export` | 复用：宿主基础能力 | JEECG Quartz 任务；业务调用表达式与调度定义转换 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysJobController.java:59 |
| SysJobController.getInfo | GET | `/monitor/job/{jobId}` | 复用：宿主基础能力 | JEECG Quartz 任务；业务调用表达式与调度定义转换 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysJobController.java:71 |
| SysJobController.add | POST | `/monitor/job` | 复用：宿主基础能力 | JEECG Quartz 任务；业务调用表达式与调度定义转换 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysJobController.java:82 |
| SysJobController.edit | PUT | `/monitor/job` | 复用：宿主基础能力 | JEECG Quartz 任务；业务调用表达式与调度定义转换 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysJobController.java:118 |
| SysJobController.changeStatus | PUT | `/monitor/job/changeStatus` | 复用：宿主基础能力 | JEECG Quartz 任务；业务调用表达式与调度定义转换 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysJobController.java:154 |
| SysJobController.run | PUT | `/monitor/job/run` | 复用：宿主基础能力 | JEECG Quartz 任务；业务调用表达式与调度定义转换 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysJobController.java:167 |
| SysJobController.remove | DELETE | `/monitor/job/{jobIds}` | 复用：宿主基础能力 | JEECG Quartz 任务；业务调用表达式与调度定义转换 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysJobController.java:179 |
| SysJobLogController.list | GET | `/monitor/jobLog/list` | 复用：宿主基础能力 | JEECG 任务执行日志 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysJobLogController.java:38 |
| SysJobLogController.export | POST | `/monitor/jobLog/export` | 复用：宿主基础能力 | JEECG 任务执行日志 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysJobLogController.java:51 |
| SysJobLogController.getInfo | GET | `/monitor/jobLog/{jobLogId}` | 复用：宿主基础能力 | JEECG 任务执行日志 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysJobLogController.java:63 |
| SysJobLogController.remove | DELETE | `/monitor/jobLog/{jobLogIds}` | 复用：宿主基础能力 | JEECG 任务执行日志 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysJobLogController.java:75 |
| SysJobLogController.clean | DELETE | `/monitor/jobLog/clean` | 复用：宿主基础能力 | JEECG 任务执行日志 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysJobLogController.java:86 |
| SysLogininforController.list | GET | `/monitor/logininfor/list` | 复用：宿主基础能力 | JEECG 登录日志/解锁；需逐项核对 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysLogininforController.java:39 |
| SysLogininforController.export | POST | `/monitor/logininfor/export` | 复用：宿主基础能力 | JEECG 登录日志/解锁；需逐项核对 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysLogininforController.java:49 |
| SysLogininforController.remove | DELETE | `/monitor/logininfor/{infoIds}` | 复用：宿主基础能力 | JEECG 登录日志/解锁；需逐项核对 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysLogininforController.java:59 |
| SysLogininforController.clean | DELETE | `/monitor/logininfor/clean` | 复用：宿主基础能力 | JEECG 登录日志/解锁；需逐项核对 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysLogininforController.java:67 |
| SysLogininforController.unlock | GET | `/monitor/logininfor/unlock/{userName}` | 复用：宿主基础能力 | JEECG 登录日志/解锁；需逐项核对 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysLogininforController.java:76 |
| SysOperlogController.list | GET | `/monitor/operlog/list` | 复用：宿主基础能力 | JEECG 操作日志 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysOperlogController.java:35 |
| SysOperlogController.export | POST | `/monitor/operlog/export` | 复用：宿主基础能力 | JEECG 操作日志 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysOperlogController.java:45 |
| SysOperlogController.remove | DELETE | `/monitor/operlog/{operIds}` | 复用：宿主基础能力 | JEECG 操作日志 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysOperlogController.java:55 |
| SysOperlogController.clean | DELETE | `/monitor/operlog/clean` | 复用：宿主基础能力 | JEECG 操作日志 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysOperlogController.java:63 |
| SysUserOnlineController.list | GET | `/monitor/online/list` | 复用：宿主基础能力 | JEECG 在线用户/强退；会话行为待核对 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysUserOnlineController.java:42 |
| SysUserOnlineController.forceLogout | DELETE | `/monitor/online/{tokenId}` | 复用：宿主基础能力 | JEECG 在线用户/强退；会话行为待核对 | PackServer/src/main/java/com/ruoyi/project/monitor/controller/SysUserOnlineController.java:77 |
| AppNotifyController.list | GET | `/system/notify/list` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/notify/controller/AppNotifyController.java:36 |
| AppNotifyController.export | POST | `/system/notify/export` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/notify/controller/AppNotifyController.java:49 |
| AppNotifyController.getInfo | GET | `/system/notify/{id}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/notify/controller/AppNotifyController.java:61 |
| AppNotifyController.add | POST | `/system/notify` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/notify/controller/AppNotifyController.java:72 |
| AppNotifyController.edit | PUT | `/system/notify` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/notify/controller/AppNotifyController.java:83 |
| AppNotifyController.remove | DELETE | `/system/notify/{ids}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/notify/controller/AppNotifyController.java:94 |
| AppPwaController.list | GET | `/system/pwa/list` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/pwa/controller/AppPwaController.java:36 |
| AppPwaController.all | GET | `/system/pwa/all` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/pwa/controller/AppPwaController.java:47 |
| AppPwaController.export | POST | `/system/pwa/export` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/pwa/controller/AppPwaController.java:58 |
| AppPwaController.getInfo | GET | `/system/pwa/{id}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/pwa/controller/AppPwaController.java:70 |
| AppPwaController.add | POST | `/system/pwa` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/pwa/controller/AppPwaController.java:81 |
| AppPwaController.edit | PUT | `/system/pwa` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/pwa/controller/AppPwaController.java:92 |
| AppPwaController.remove | DELETE | `/system/pwa/{ids}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/pwa/controller/AppPwaController.java:104 |
| PwaController.getInfo | GET | `/pwa/{path}` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/pwa/controller/PwaController.java:29 |
| PwaNotifyController.subscribe | ANY | `/notify/subscribe` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/pwa/controller/PwaNotifyController.java:54 |
| PwaNotifyController.get | ANY | `/notify/notifyweb` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/pwa/controller/PwaNotifyController.java:61 |
| AppStatisticsController.upload | ANY | `/systemSDK/upload/data` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/statistics/controller/AppStatisticsController.java:24 |
| AppStatisticsController.getSwitch | ANY | `/systemSDK/{a}` | 迁移：旧客户端兼容 | /package-legacy + 原路径 | PackServer/src/main/java/com/ruoyi/project/statistics/controller/AppStatisticsController.java:30 |
| WebStatisticsController.list | GET | `/system/statistics/list` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/statistics/controller/WebStatisticsController.java:46 |
| WebStatisticsController.export | POST | `/system/statistics/export` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/statistics/controller/WebStatisticsController.java:58 |
| WebStatisticsController.getInfo | GET | `/system/statistics/{id}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/statistics/controller/WebStatisticsController.java:69 |
| WebStatisticsController.add | POST | `/system/statistics` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/statistics/controller/WebStatisticsController.java:79 |
| WebStatisticsController.edit | PUT | `/system/statistics` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/statistics/controller/WebStatisticsController.java:95 |
| WebStatisticsController.remove | DELETE | `/system/statistics/{ids}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/statistics/controller/WebStatisticsController.java:111 |
| WebStatisticsController.getTotal | GET | `/system/statistics/getTotal` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/statistics/controller/WebStatisticsController.java:117 |
| StoreStatusController.list | GET | `/system/store/list` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/store/controller/StoreStatusController.java:29 |
| StoreStatusController.add | POST | `/system/store/add` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/store/controller/StoreStatusController.java:42 |
| StoreStatusController.select | GET | `/system/store/{id}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/store/controller/StoreStatusController.java:58 |
| StoreStatusController.remove | DELETE | `/system/store/{ids}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/store/controller/StoreStatusController.java:71 |
| StoreStatusController.edit | PUT | `/system/store/update` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/store/controller/StoreStatusController.java:82 |
| AppEventRecordController.list | GET | `/system/record/list` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/system/controller/AppEventRecordController.java:41 |
| AppEventRecordController.export | POST | `/system/record/export` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/system/controller/AppEventRecordController.java:54 |
| AppEventRecordController.getInfo | GET | `/system/record/{eventId}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/system/controller/AppEventRecordController.java:66 |
| AppEventRecordController.add | POST | `/system/record` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/system/controller/AppEventRecordController.java:77 |
| AppEventRecordController.edit | PUT | `/system/record` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/system/controller/AppEventRecordController.java:88 |
| AppEventRecordController.remove | DELETE | `/system/record/{eventIds}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/system/controller/AppEventRecordController.java:99 |
| ReviewsRecordController.list | GET | `/system/reviewsrecord/list` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/system/controller/ReviewsRecordController.java:41 |
| ReviewsRecordController.export | POST | `/system/reviewsrecord/export` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/system/controller/ReviewsRecordController.java:54 |
| ReviewsRecordController.getInfo | GET | `/system/reviewsrecord/{reviewsId}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/system/controller/ReviewsRecordController.java:66 |
| ReviewsRecordController.add | POST | `/system/reviewsrecord` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/system/controller/ReviewsRecordController.java:77 |
| ReviewsRecordController.edit | PUT | `/system/reviewsrecord` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/system/controller/ReviewsRecordController.java:88 |
| ReviewsRecordController.remove | DELETE | `/system/reviewsrecord/{reviewsIds}` | 迁移：管理业务 | /package-admin 对应业务域 | PackServer/src/main/java/com/ruoyi/project/system/controller/ReviewsRecordController.java:99 |
| SysConfigController.list | GET | `/system/config/list` | 复用：宿主基础能力 | JEECG 参数配置；源 key 及缓存刷新语义需转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysConfigController.java:41 |
| SysConfigController.export | POST | `/system/config/export` | 复用：宿主基础能力 | JEECG 参数配置；源 key 及缓存刷新语义需转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysConfigController.java:51 |
| SysConfigController.getInfo | GET | `/system/config/{configId}` | 复用：宿主基础能力 | JEECG 参数配置；源 key 及缓存刷新语义需转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysConfigController.java:63 |
| SysConfigController.getConfigKey | GET | `/system/config/configKey/{configKey}` | 复用：宿主基础能力 | JEECG 参数配置；源 key 及缓存刷新语义需转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysConfigController.java:72 |
| SysConfigController.add | POST | `/system/config` | 复用：宿主基础能力 | JEECG 参数配置；源 key 及缓存刷新语义需转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysConfigController.java:83 |
| SysConfigController.edit | PUT | `/system/config` | 复用：宿主基础能力 | JEECG 参数配置；源 key 及缓存刷新语义需转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysConfigController.java:99 |
| SysConfigController.remove | DELETE | `/system/config/{configIds}` | 复用：宿主基础能力 | JEECG 参数配置；源 key 及缓存刷新语义需转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysConfigController.java:115 |
| SysConfigController.refreshCache | DELETE | `/system/config/refreshCache` | 复用：宿主基础能力 | JEECG 参数配置；源 key 及缓存刷新语义需转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysConfigController.java:127 |
| SysDeptController.list | GET | `/system/dept/list` | 复用：宿主基础能力 | JEECG 组织树 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDeptController.java:41 |
| SysDeptController.excludeChild | GET | `/system/dept/list/exclude/{deptId}` | 复用：宿主基础能力 | JEECG 组织树 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDeptController.java:52 |
| SysDeptController.getInfo | GET | `/system/dept/{deptId}` | 复用：宿主基础能力 | JEECG 组织树 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDeptController.java:64 |
| SysDeptController.add | POST | `/system/dept` | 复用：宿主基础能力 | JEECG 组织树 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDeptController.java:76 |
| SysDeptController.edit | PUT | `/system/dept` | 复用：宿主基础能力 | JEECG 组织树 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDeptController.java:92 |
| SysDeptController.remove | DELETE | `/system/dept/{deptId}` | 复用：宿主基础能力 | JEECG 组织树 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDeptController.java:118 |
| SysDictDataController.list | GET | `/system/dict/data/list` | 复用：宿主基础能力 | JEECG 字典数据 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDictDataController.java:44 |
| SysDictDataController.export | POST | `/system/dict/data/export` | 复用：宿主基础能力 | JEECG 字典数据 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDictDataController.java:54 |
| SysDictDataController.getInfo | GET | `/system/dict/data/{dictCode}` | 复用：宿主基础能力 | JEECG 字典数据 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDictDataController.java:66 |
| SysDictDataController.dictType | GET | `/system/dict/data/type/{dictType}` | 复用：宿主基础能力 | JEECG 字典数据 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDictDataController.java:75 |
| SysDictDataController.add | POST | `/system/dict/data` | 复用：宿主基础能力 | JEECG 字典数据 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDictDataController.java:91 |
| SysDictDataController.edit | PUT | `/system/dict/data` | 复用：宿主基础能力 | JEECG 字典数据 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDictDataController.java:103 |
| SysDictDataController.remove | DELETE | `/system/dict/data/{dictCodes}` | 复用：宿主基础能力 | JEECG 字典数据 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDictDataController.java:115 |
| SysDictTypeController.list | GET | `/system/dict/type/list` | 复用：宿主基础能力 | JEECG 字典类型 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDictTypeController.java:38 |
| SysDictTypeController.export | POST | `/system/dict/type/export` | 复用：宿主基础能力 | JEECG 字典类型 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDictTypeController.java:48 |
| SysDictTypeController.getInfo | GET | `/system/dict/type/{dictId}` | 复用：宿主基础能力 | JEECG 字典类型 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDictTypeController.java:60 |
| SysDictTypeController.add | POST | `/system/dict/type` | 复用：宿主基础能力 | JEECG 字典类型 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDictTypeController.java:71 |
| SysDictTypeController.edit | PUT | `/system/dict/type` | 复用：宿主基础能力 | JEECG 字典类型 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDictTypeController.java:87 |
| SysDictTypeController.remove | DELETE | `/system/dict/type/{dictIds}` | 复用：宿主基础能力 | JEECG 字典类型 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDictTypeController.java:103 |
| SysDictTypeController.refreshCache | DELETE | `/system/dict/type/refreshCache` | 复用：宿主基础能力 | JEECG 字典类型 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDictTypeController.java:115 |
| SysDictTypeController.optionselect | GET | `/system/dict/type/optionselect` | 复用：宿主基础能力 | JEECG 字典类型 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysDictTypeController.java:125 |
| SysIndexController.index | ANY | `/` | 复用：宿主基础能力 | 当前后端根页/版本响应；不占用旧客户端根通配 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysIndexController.java:25 |
| SysLoginController.login | POST | `/login` | 复用：宿主基础能力 | JEECG 登录/登出、用户信息、动态菜单 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysLoginController.java:43 |
| SysLoginController.getInfo | GET | `getInfo` | 复用：宿主基础能力 | JEECG 登录/登出、用户信息、动态菜单 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysLoginController.java:59 |
| SysLoginController.getRouters | GET | `getRouters` | 复用：宿主基础能力 | JEECG 登录/登出、用户信息、动态菜单 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysLoginController.java:79 |
| SysMenuController.list | GET | `/system/menu/list` | 复用：宿主基础能力 | JEECG 菜单和按钮权限 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysMenuController.java:40 |
| SysMenuController.getInfo | GET | `/system/menu/{menuId}` | 复用：宿主基础能力 | JEECG 菜单和按钮权限 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysMenuController.java:51 |
| SysMenuController.treeselect | GET | `/system/menu/treeselect` | 复用：宿主基础能力 | JEECG 菜单和按钮权限 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysMenuController.java:60 |
| SysMenuController.roleMenuTreeselect | GET | `/system/menu/roleMenuTreeselect/{roleId}` | 复用：宿主基础能力 | JEECG 菜单和按钮权限 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysMenuController.java:70 |
| SysMenuController.add | POST | `/system/menu` | 复用：宿主基础能力 | JEECG 菜单和按钮权限 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysMenuController.java:85 |
| SysMenuController.edit | PUT | `/system/menu` | 复用：宿主基础能力 | JEECG 菜单和按钮权限 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysMenuController.java:105 |
| SysMenuController.remove | DELETE | `/system/menu/{menuId}` | 复用：宿主基础能力 | JEECG 菜单和按钮权限 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysMenuController.java:129 |
| SysNoticeController.list | GET | `/system/notice/list` | 复用：宿主基础能力 | JEECG 通知公告 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysNoticeController.java:39 |
| SysNoticeController.getInfo | GET | `/system/notice/{noticeId}` | 复用：宿主基础能力 | JEECG 通知公告 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysNoticeController.java:51 |
| SysNoticeController.add | POST | `/system/notice` | 复用：宿主基础能力 | JEECG 通知公告 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysNoticeController.java:62 |
| SysNoticeController.edit | PUT | `/system/notice` | 复用：宿主基础能力 | JEECG 通知公告 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysNoticeController.java:74 |
| SysNoticeController.remove | DELETE | `/system/notice/{noticeIds}` | 复用：宿主基础能力 | JEECG 通知公告 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysNoticeController.java:86 |
| SysPostController.list | GET | `/system/post/list` | 复用：宿主基础能力 | JEECG 岗位；需核对源岗位与宿主组织/职务映射 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysPostController.java:41 |
| SysPostController.export | POST | `/system/post/export` | 复用：宿主基础能力 | JEECG 岗位；需核对源岗位与宿主组织/职务映射 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysPostController.java:51 |
| SysPostController.getInfo | GET | `/system/post/{postId}` | 复用：宿主基础能力 | JEECG 岗位；需核对源岗位与宿主组织/职务映射 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysPostController.java:63 |
| SysPostController.add | POST | `/system/post` | 复用：宿主基础能力 | JEECG 岗位；需核对源岗位与宿主组织/职务映射 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysPostController.java:74 |
| SysPostController.edit | PUT | `/system/post` | 复用：宿主基础能力 | JEECG 岗位；需核对源岗位与宿主组织/职务映射 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysPostController.java:94 |
| SysPostController.remove | DELETE | `/system/post/{postIds}` | 复用：宿主基础能力 | JEECG 岗位；需核对源岗位与宿主组织/职务映射 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysPostController.java:114 |
| SysPostController.optionselect | GET | `/system/post/optionselect` | 复用：宿主基础能力 | JEECG 岗位；需核对源岗位与宿主组织/职务映射 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysPostController.java:123 |
| SysProfileController.profile | GET | `/system/user/profile` | 复用：宿主基础能力 | JEECG 个人中心、头像及密码 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysProfileController.java:44 |
| SysProfileController.updateProfile | PUT | `/system/user/profile` | 复用：宿主基础能力 | JEECG 个人中心、头像及密码 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysProfileController.java:59 |
| SysProfileController.updatePwd | PUT | `/system/user/profile/updatePwd` | 复用：宿主基础能力 | JEECG 个人中心、头像及密码 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysProfileController.java:89 |
| SysProfileController.avatar | POST | `/system/user/profile/avatar` | 复用：宿主基础能力 | JEECG 个人中心、头像及密码 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysProfileController.java:117 |
| SysRegisterController.register | POST | `/register` | 复用：宿主基础能力 | JEECG 注册策略（配置启用状态待核对） | PackServer/src/main/java/com/ruoyi/project/system/controller/SysRegisterController.java:28 |
| SysRoleController.list | GET | `/system/role/list` | 复用：宿主基础能力 | JEECG 角色、授权、数据范围语义转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysRoleController.java:59 |
| SysRoleController.export | POST | `/system/role/export` | 复用：宿主基础能力 | JEECG 角色、授权、数据范围语义转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysRoleController.java:69 |
| SysRoleController.getInfo | GET | `/system/role/{roleId}` | 复用：宿主基础能力 | JEECG 角色、授权、数据范围语义转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysRoleController.java:81 |
| SysRoleController.add | POST | `/system/role` | 复用：宿主基础能力 | JEECG 角色、授权、数据范围语义转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysRoleController.java:93 |
| SysRoleController.edit | PUT | `/system/role` | 复用：宿主基础能力 | JEECG 角色、授权、数据范围语义转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysRoleController.java:114 |
| SysRoleController.dataScope | PUT | `/system/role/dataScope` | 复用：宿主基础能力 | JEECG 角色、授权、数据范围语义转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysRoleController.java:149 |
| SysRoleController.changeStatus | PUT | `/system/role/changeStatus` | 复用：宿主基础能力 | JEECG 角色、授权、数据范围语义转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysRoleController.java:162 |
| SysRoleController.remove | DELETE | `/system/role/{roleIds}` | 复用：宿主基础能力 | JEECG 角色、授权、数据范围语义转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysRoleController.java:176 |
| SysRoleController.optionselect | GET | `/system/role/optionselect` | 复用：宿主基础能力 | JEECG 角色、授权、数据范围语义转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysRoleController.java:186 |
| SysRoleController.allocatedList | GET | `/system/role/authUser/allocatedList` | 复用：宿主基础能力 | JEECG 角色、授权、数据范围语义转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysRoleController.java:196 |
| SysRoleController.unallocatedList | GET | `/system/role/authUser/unallocatedList` | 复用：宿主基础能力 | JEECG 角色、授权、数据范围语义转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysRoleController.java:208 |
| SysRoleController.cancelAuthUser | PUT | `/system/role/authUser/cancel` | 复用：宿主基础能力 | JEECG 角色、授权、数据范围语义转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysRoleController.java:221 |
| SysRoleController.cancelAuthUserAll | PUT | `/system/role/authUser/cancelAll` | 复用：宿主基础能力 | JEECG 角色、授权、数据范围语义转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysRoleController.java:232 |
| SysRoleController.selectAuthUserAll | PUT | `/system/role/authUser/selectAll` | 复用：宿主基础能力 | JEECG 角色、授权、数据范围语义转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysRoleController.java:243 |
| SysRoleController.deptTree | GET | `/system/role/deptTree/{roleId}` | 复用：宿主基础能力 | JEECG 角色、授权、数据范围语义转换 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysRoleController.java:254 |
| SysUserController.list | GET | `/system/user/list` | 复用：宿主基础能力 | JEECG 用户/角色/组织及导入导出 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysUserController.java:54 |
| SysUserController.export | POST | `/system/user/export` | 复用：宿主基础能力 | JEECG 用户/角色/组织及导入导出 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysUserController.java:64 |
| SysUserController.importData | POST | `/system/user/importData` | 复用：宿主基础能力 | JEECG 用户/角色/组织及导入导出 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysUserController.java:74 |
| SysUserController.importTemplate | POST | `/system/user/importTemplate` | 复用：宿主基础能力 | JEECG 用户/角色/组织及导入导出 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysUserController.java:84 |
| SysUserController.getInfo | GET | `/system/user/` | 复用：宿主基础能力 | JEECG 用户/角色/组织及导入导出 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysUserController.java:95 |
| SysUserController.getInfo | GET | `/system/user/{userId}` | 复用：宿主基础能力 | JEECG 用户/角色/组织及导入导出 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysUserController.java:95 |
| SysUserController.add | POST | `/system/user` | 复用：宿主基础能力 | JEECG 用户/角色/组织及导入导出 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysUserController.java:118 |
| SysUserController.edit | PUT | `/system/user` | 复用：宿主基础能力 | JEECG 用户/角色/组织及导入导出 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysUserController.java:143 |
| SysUserController.remove | DELETE | `/system/user/{userIds}` | 复用：宿主基础能力 | JEECG 用户/角色/组织及导入导出 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysUserController.java:169 |
| SysUserController.resetPwd | PUT | `/system/user/resetPwd` | 复用：宿主基础能力 | JEECG 用户/角色/组织及导入导出 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysUserController.java:184 |
| SysUserController.changeStatus | PUT | `/system/user/changeStatus` | 复用：宿主基础能力 | JEECG 用户/角色/组织及导入导出 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysUserController.java:199 |
| SysUserController.authRole | GET | `/system/user/authRole/{userId}` | 复用：宿主基础能力 | JEECG 用户/角色/组织及导入导出 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysUserController.java:212 |
| SysUserController.insertAuthRole | PUT | `/system/user/authRole` | 复用：宿主基础能力 | JEECG 用户/角色/组织及导入导出 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysUserController.java:228 |
| SysUserController.deptTree | GET | `/system/user/deptTree` | 复用：宿主基础能力 | JEECG 用户/角色/组织及导入导出 | PackServer/src/main/java/com/ruoyi/project/system/controller/SysUserController.java:240 |
| GenController.genList | GET | `/tool/gen/list` | 工具差异登记 | JEECG 代码生成/Online；不承诺兼容 RuoYi 模板与生成产物 | PackServer/src/main/java/com/ruoyi/project/tool/gen/controller/GenController.java:50 |
| GenController.getInfo | GET | `/tool/gen/{talbleId}` | 工具差异登记 | JEECG 代码生成/Online；不承诺兼容 RuoYi 模板与生成产物 | PackServer/src/main/java/com/ruoyi/project/tool/gen/controller/GenController.java:62 |
| GenController.dataList | GET | `/tool/gen/db/list` | 工具差异登记 | JEECG 代码生成/Online；不承诺兼容 RuoYi 模板与生成产物 | PackServer/src/main/java/com/ruoyi/project/tool/gen/controller/GenController.java:79 |
| GenController.columnList | GET | `/tool/gen/column/{tableId}` | 工具差异登记 | JEECG 代码生成/Online；不承诺兼容 RuoYi 模板与生成产物 | PackServer/src/main/java/com/ruoyi/project/tool/gen/controller/GenController.java:91 |
| GenController.importTableSave | POST | `/tool/gen/importTable` | 工具差异登记 | JEECG 代码生成/Online；不承诺兼容 RuoYi 模板与生成产物 | PackServer/src/main/java/com/ruoyi/project/tool/gen/controller/GenController.java:106 |
| GenController.editSave | PUT | `/tool/gen` | 工具差异登记 | JEECG 代码生成/Online；不承诺兼容 RuoYi 模板与生成产物 | PackServer/src/main/java/com/ruoyi/project/tool/gen/controller/GenController.java:121 |
| GenController.remove | DELETE | `/tool/gen/{tableIds}` | 工具差异登记 | JEECG 代码生成/Online；不承诺兼容 RuoYi 模板与生成产物 | PackServer/src/main/java/com/ruoyi/project/tool/gen/controller/GenController.java:134 |
| GenController.preview | GET | `/tool/gen/preview/{tableId}` | 工具差异登记 | JEECG 代码生成/Online；不承诺兼容 RuoYi 模板与生成产物 | PackServer/src/main/java/com/ruoyi/project/tool/gen/controller/GenController.java:145 |
| GenController.download | GET | `/tool/gen/download/{tableName}` | 工具差异登记 | JEECG 代码生成/Online；不承诺兼容 RuoYi 模板与生成产物 | PackServer/src/main/java/com/ruoyi/project/tool/gen/controller/GenController.java:157 |
| GenController.genCode | GET | `/tool/gen/genCode/{tableName}` | 工具差异登记 | JEECG 代码生成/Online；不承诺兼容 RuoYi 模板与生成产物 | PackServer/src/main/java/com/ruoyi/project/tool/gen/controller/GenController.java:169 |
| GenController.synchDb | GET | `/tool/gen/synchDb/{tableName}` | 工具差异登记 | JEECG 代码生成/Online；不承诺兼容 RuoYi 模板与生成产物 | PackServer/src/main/java/com/ruoyi/project/tool/gen/controller/GenController.java:181 |
| GenController.batchGenCode | GET | `/tool/gen/batchGenCode` | 工具差异登记 | JEECG 代码生成/Online；不承诺兼容 RuoYi 模板与生成产物 | PackServer/src/main/java/com/ruoyi/project/tool/gen/controller/GenController.java:193 |
| TestController.userList | GET | `/test/user/list` | 示例不迁移 | Swagger UserEntity 示例，无业务页面依赖 | PackServer/src/main/java/com/ruoyi/project/tool/swagger/TestController.java:42 |
| TestController.getUser | GET | `/test/user/{userId}` | 示例不迁移 | Swagger UserEntity 示例，无业务页面依赖 | PackServer/src/main/java/com/ruoyi/project/tool/swagger/TestController.java:51 |
| TestController.save | POST | `/test/user/save` | 示例不迁移 | Swagger UserEntity 示例，无业务页面依赖 | PackServer/src/main/java/com/ruoyi/project/tool/swagger/TestController.java:71 |
| TestController.update | PUT | `/test/user/update` | 示例不迁移 | Swagger UserEntity 示例，无业务页面依赖 | PackServer/src/main/java/com/ruoyi/project/tool/swagger/TestController.java:83 |
| TestController.delete | DELETE | `/test/user/{userId}` | 示例不迁移 | Swagger UserEntity 示例，无业务页面依赖 | PackServer/src/main/java/com/ruoyi/project/tool/swagger/TestController.java:101 |
