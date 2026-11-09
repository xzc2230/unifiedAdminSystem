# 应用服务实施补充说明

源目录：D:/workspace/package-api/package-api/PackServer/src/main/java/com/ruoyi。目标独立模块：upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package。

## 服务组织

原 app 服务 → application/apps；statistics → application/statistics；store → application/store；event → application/event；pwa → application/pwa；notify → application/notification；system/AppEventRecord → application/event；system/ReviewsRecord → application/pwa；applog ES → application/applog。

业务服务保留源接口名、方法名、核心条件与返回内容。只替换基础框架调用。LegacyAjaxResult 是模块内 Map 类型，保留 code/msg/data 及 isError，供旧公开接口使用；管理接口在外层转换为 JEECG Result。不要引入 RuoYi 包名。

## 协议测试最低矩阵

| 方法 | 必测 |
| --- | --- |
| api | 缺id、非法Base64、包不存在/停用、美国限制、单/多地区白名单、AF参数拼接、URL再次Base64 |
| login | 缺username、空包/停用、有效包响应 code/message/data.show/path/useAF |
| redirected | 缺packName、有效URL与已有query、af_id开关、响应状态与Location |
| getBanner | systemName/systemID命名、缺包、有效包结构 |
| checkUrl | tokenID/baseTime请求头指定参数名、默认event/canLoad/canAppend/load、有效包布尔值 |
| dynamicAdj | 默认与有效 app_id/app_cache/app_limit/app_name/app_dynamic |
| getUserInfo | user_id/user_token、timestamp偏移、User-Agent、摘要与响应字段 |
| base64Data | pack_a/data_a、缺参数、合法配置、JSON编码 |
| base64Data2 | index、默认值、Base64时间戳与时区字符串 |
| base64Data3 | timestamp、IP countryCode补充、pid/origin/spid/mark |
| app1 | ParamsUtils首个含点值、可配置原默认AES key、appName key优先、AES/ECB/PKCS5Padding和Base64 |

使用固定/可替换时钟与地区测试替身；测试不能访问源IP服务。保留请求原URI，代理新增内部前缀时通过可信转发头/请求包装还原，不能把package-legacy写进旧响应日志或URI推断。

## 集成边界

- 不复制源码硬编码凭据；用配置指定IP服务、NPM登录/Host API、VAPID生成服务、SMTP、AES兼容默认key及静态目录。
- ES通过独立HTTP适配器保持applog索引、ID、字段、wildcard包名、时间范围、排序与totalHits语义；配置没有开启时不假装保存成功。
- FB按原先保存事件→解析原URL→有条件发远程请求→保存成功或失败上报日志的次序。
- Store只探测状态不是2的包，0→1及1→2才发邮件。
- PWA新增/修改/删除同步原NPM linkId；创建保留Snowflake(1,1,true)语义；旧VAPID密钥不重建。
- 推送保留部分订阅失败仍可能整体成功的现有行为，不擅自引入队列或重试。

## 管理归属

所有管理控制器必须从宿主Shiro principal解析用户，从TenantContext读取租户，并核实sys_user_tenant/sys_tenant/sys_user的有效状态与时间。不能仅信请求头租户。

前端传入userId不决定授权。旧数值ID通过pkg_user_identity映射；新业务归属使用pkg_resource_owner明确tenant/user/resourceType/resourceId。未映射历史资源默认拒绝管理访问。单条、批量、导出、汇总、清理及转移用同一规则。公开协议查询不依赖后台登录或tenant头，保留全局唯一包名/appCode。内部应用服务与公开查询可以绕开后台用户过滤，但不能作为管理入口。
