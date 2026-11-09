# 管理与旧协议控制器实施补充说明

源代码为契约依据；source-endpoints.json 包含95业务接口，all-source-endpoints.md 包含补充4通用资源接口。不要将宿主119基础接口整体复制。

这是已确认执行计划Task 3的完整实施要求。目标模块：upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package；Java根包org.jeecg.modules.packagemanagement。层级controller/admin、controller/legacy、dto/admin、dto/legacy、config；业务判断调用现有application，权限/归属复用infrastructure/identity。服务准确签名与交接注意事项见task-2-report.md。新增部署配置放deploy/package-api/，主机只做必要窄路径Shiro接入；不要修改现有siteplatform业务。保留用户已有README/AWS变更。

## 管理域命名

| 源控制器 | 管理域 |
| --- | --- |
| AppManagerController | apps |
| StoreStatusController | store |
| WebStatisticsController | statistics |
| EventConfigController | event-configs |
| EvnetHistoryController | event-history |
| EventErrorController | event-errors |
| AppPwaController | pwa（appType=0/1分别前端PWA/W2A） |
| LoadCountController | landing-counts |
| AppEventRecordController | app-events |
| ReviewsRecordController | reviews |
| AppNotifyController | notifications |
| EsAppLogController | request-logs |

全部管理入口使用 /package-admin，JEECG Result，records/total，pageNo/pageSize。每个源管理动作都必须有目标路由记录；可以采用更清楚的管理路由，但前端与对照清单必须同步。包转移服务端核实目标用户是有效租户成员并有旧数值身份映射，归属更新同时更新业务user_id/user_name及归属表。不能仅相信userName/旧id路径参数。

## 身份及权限

每个管理动作同时检查 package:{domain}:{action} 与PackageActor归属。列表、详情、统计汇总、导出、批量删除、清理、转移必须一致。批量先检查所有ID，全部合法后才写入；删除/更新与归属变更用事务。无法识别归属的历史记录默认管理拒绝。公开接口仍可按包名/appCode处理旧客户端。

新增记录由当前Actor设置归属；嵌套评论归属继承PWA；用户不可将评论挂到别人的PWA。由公开回调生成的日志/事件/计数通过父包或PWA解析归属，不由匿名参数决定。管理导出使用真实业务列/中文列名，不能用JSON文本假装Excel。

管理请求需限制可变字段：新增主键由服务端生成，编辑身份/归属字段由已有记录与Actor确定，PWA appCode/linkId/publicKey/privateKey等不允许普通表单随意替换；详情可提供页面必要字段，不能向不需要密钥的表格/导出泄露privateKey。统计只开放源活跃编辑字段，不能通过请求篡改SDK累计计数或金额。关联ID更新同样核对新旧父资源归属，不只校验主记录ID。

## 旧协议控制器

源20公开路径和4common资源路径加统一内部前缀 /package-legacy，不包装JEECG Result。ANY语义保持ANY，源GET/POST保持方法；具体路径优先于单层/双层/三层通配路径。Shiro仅放行独立兼容命名空间，不能放行宿主根/**。管理路由仍鉴权。

- ClientController、Client1Controller、Client2Controller 对接迁移ClientService。
- FBController、PageController、LoadCountAccessController、PwaController、PwaNotifyController、AppStatisticsController（/systemSDK）保留源参数和响应。
- /systemSDK/{a} 的1秒RepeatSubmit规则要检查源拦截实现，保留真实比较键和响应。
- /common/upload、/uploads、/download、/download/resource 与 /profile/** 静态资源契约完整登记；原始fileName/delete、names/urls/newFileNames字段保留。使用本地配置目录并防止目录穿越、符号链接越界。

Source checkUrl 实际通过 tokenID/baseTime 请求头指定参数名，以实际代码为准。不要根据早期摘要改为任意遍历。

请求包装统一移除内部 servletContext/package-legacy 前缀，使日志URI/源URI推断匹配原请求。外部代理保留Host/IP/query/originalURI，只信任明确代理配置，避免任意客户端Header覆盖身份或路由。支持新增单独API文档分组。

旧控制器返回Object里的String会走String消息转换器，不能自动加JSON引号；/system/{a}保留Base64正文和源Content-Type。源评论createTime等使用旧日期格式，Boot4宿主的全局序列化配置不能直接假定等价；兼容域用专用转换/DTO或局部序列化器，分别测试成功/空返回/错误和日期。不要注册通用ObjectMapper bean或修改宿主全局转换器/时区。

PwaController.getInfo的字段需按源码逐项保留：sysLanguage/applicationName/developerName/appDesc/baseUrl/icon/bannerImg/eventName/eventConfig/updateTime固定源值/pubKey/downloadUrl/types/score/reviewsRecordList/reviews/downloadCount。不存在或异常时空字符串。不要直接返回完整AppPwa实体，避免暴露privateKey和管理字段。

## 验证

MockMvc覆盖所有目标路由存在、ANY路由HTTP语义、具体路径与通配优先级、匿名旧入口和管理401/403、Base64字符串消息转换及Content-Type、跳转Location、1秒重复提交、文件上传/下载与越界拒绝。至少真实本地调用关键旧协议默认/有效包两类分支；外部服务使用替身。为源99条迁移路径逐条输出source→target→测试映射，不可仅统计控制器个数。

## 部署与交付

提供可审阅的Nginx示例：仅旧API专用server/location映射/jeecg-boot/package-legacy，保留Host/query/原URI/IP，不将通配放到宿主根，不把管理入口误放行。部署配置使用环境变量，上传目录可配置，历史/profile文件访问兼容。提供回退步骤及必要配置，生产实际切换不在此次自动执行范围。

必须完成Maven定向测试并保存实际日志；追加docs/migration/package-api/task-3-report.md，列出每个管理域的最终方法/路由/请求响应（供前端直接接线）、99条机器可读完整source→target→test对照及异常/权限覆盖。业务层若有具体缺陷修复应补覆盖该修复的测试并在报告指出；无关变更不做。不要只搭控制器骨架，所有活跃接口实际调用业务与归属层。写完后报告DONE/concerns，不提交，由根任务审查后提交。
