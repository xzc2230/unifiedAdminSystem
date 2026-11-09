# 应用包管理迁移运行说明

当前实现与验证状态以implementation-status.md、verification.md为准。本说明随接口、前端实施继续补全，不能据此认定生产切换已完成。

## 本机隔离环境

| 服务 | 地址 | 数据/配置 |
| --- | --- | --- |
| 临时MySQL 8 | 127.0.0.1:13317/package_migration_test | .runtime/package-mysql/my.ini、data；限定package_test用户 |
| 临时Redis | 127.0.0.1:16390 | .runtime/package-redis/redis.conf，关闭持久化 |
| 临时宿主后端 | 127.0.0.1:18081/jeecg-boot | .runtime/package-application.yml；已运行通过Task3复审的候选JAR，副本.runtime/package-candidate.jar避免Windows文件锁 |
| 临时前端 | 127.0.0.1:13101 | 通过进程环境覆盖VITE_PROXY，不修改已有本地env |

本机测试密码只存.runtime/package-mysql/credentials.env；会话只存.runtime/package-session.json，不提交、不打印。测试用库已经导入宿主基线结构和14张pkg表。MySQL集成测试写入回滚；页面联调另有明确归属的本地样例，均只位于隔离库。不能将临时库密码用于部署环境。

## 模块配置

模块配置前缀jeecg.package-management，环境变量由package-management.properties映射。仅在实际需要时配置对应外部服务，未配置时不能将调用失败显示为成功。

后端独立Maven模块jeecg-module-package，根包org.jeecg.modules.packagemanagement。controller.admin与controller.legacy分别承接管理接口和旧协议；application按业务域组织；domain保留业务属性；infrastructure分持久化、身份和外部集成，另有config/job。管理请求使用宿主登录令牌X-Access-Token及实际租户头X-Tenant-Id，并核验有效租户成员、明确旧用户映射和逐记录归属。菜单权限并不替代数据归属校验。

| 用途 | 环境变量 |
| --- | --- |
| 旧AES兼容默认密钥 | PACKAGE_DEFAULT_AES_KEY（由受保护配置提供原系统有效值） |
| 地区服务 | PACKAGE_GEO_URL、PACKAGE_GEO_API_KEY |
| ES请求日志 | PACKAGE_ES_URL、PACKAGE_ES_USERNAME、PACKAGE_ES_PASSWORD |
| 域名服务NPM | PACKAGE_NPM_BASE_URL、PACKAGE_NPM_IDENTITY、PACKAGE_NPM_SECRET |
| PWA密钥生成/目录 | PACKAGE_VAPID_URL、PACKAGE_PWA_ROOT |
| 邮件 | PACKAGE_SMTP_HOST、PACKAGE_SMTP_PORT、PACKAGE_SMTP_USER、PACKAGE_SMTP_PASSWORD、PACKAGE_SMTP_FROM、PACKAGE_SMTP_START_TLS、PACKAGE_STORE_RECIPIENTS |
| FB与落地页 | PACKAGE_FACEBOOK_BASE_URL、PACKAGE_FACEBOOK_BLOCKED_URL、PACKAGE_LANDING_FALLBACK_URL |
| WebPush | PACKAGE_PUSH_SUBJECT；每个应用沿用其已保存的VAPID公私钥 |
| 历史文件与可信代理 | PACKAGE_PROFILE_ROOT、PACKAGE_TRUSTED_PROXIES |

profile-root必须指向历史文件持久目录，包含upload、download子目录。保留原存储相对路径，避免旧页面图片失效。代理仅信任直接连接的明确IP；部署说明及Nginx模板在deploy/package-api，模板需替换旧域名、上游及真实TLS证书并通过nginx -t。旧域名代理保留客户端路径、查询和Host。管理入口仍从现有JEECG域名访问。

源应用有效上传限制为单文件10MB、整请求20MB；宿主当前整请求默认10MB。部署候选profile需明确设置spring.servlet.multipart.max-file-size=10MB、spring.servlet.multipart.max-request-size=20MB，并验证与宿主现有上传功能的关系。模板Nginx的21m仅为请求封装预留空间，Spring仍执行20MB限制。

使用宿主Quartz配置PackageStoreJob的完整类名org.jeecg.modules.packagemanagement.job.PackageStoreJob；先核对原调度周期和邮件收件人，避免新旧任务同时发送。迁移代码不自动启用生产任务。

## 数据准备与核验

001-schema.sql为根据源Mapper/Java推断的开发DDL，正式导入前必须与源实库结构对比，尤其字段类型、长度、精度、默认值及唯一索引。先检查package_code、app_code重复，保留原数字主键和关联。源业务表→pkg表对照见task-1-report.md。

pkg_user_identity用于旧数字用户到JEECG用户/租户的明确映射；每个旧ID只对应一个有效宿主身份。pkg_resource_owner逐资源登记归属，历史记录不得根据请求传入userId自动认领。先确认宿主用户、租户、成员状态及有效期，再导入映射。缺少或歧义的映射默认拒绝管理访问。

核对12表数量、父子关联、统计金额、评论、订阅、通知、NPM linkId和VAPID密钥；ES applog索引及字段映射另行迁移。上传文件、PWA/W2A静态站点目录、Nginx域名关系不随Java代码自动迁移。

导入后在选定目标库执行deploy/package-api/data-preflight.sql（只读）：检查一对一身份、成员有效性、逐资源归属、孤立评论/订阅/通知、全局包/appCode重复及12表数量/金额汇总。只报告密钥是否存在，不输出私钥内容。预检在本机隔离空库及样例库均已执行成功，日志.runtime/package-mysql/preflight.log、preflight-fixtures.log；这些检查不代表历史生产数据已验证。宿主与新增表排序规则可能不同，预检的宿主身份字符串比较已明确转换为utf8mb4再使用utf8mb4_general_ci。

业务字典app_status、app_where、app_area、store_status、event_name、country_name、load_host均被源页面使用，但提供的源SQL未含这些业务字典数据。应从原系统导出value/label/排序后转换为JEECG字典；尤其load_host的value→label决定appSub，不能用示例域名代替。用户数值ID和业务字典都不能仅凭显示名称自动匹配。

## 生产切换前

必须具备源业务DDL/数据备份、有效身份映射、完整PWA静态资源、外部服务测试配置及旧域名代理规则。先在隔离环境完成新旧协议对照与页面验收，再准备目标数据快照与回退入口。旧API域名保持原方法/路径/参数，由代理映射到独立/package-legacy内部空间；管理API使用/package-admin，独立鉴权。

按以下顺序在候选环境验收并切换：

1. 备份旧数据库、ES索引、上传目录、静态站点、域名配置和Nginx配置，记录切换时间及旧上游。
2. 对比实库DDL后创建新增pkg表，按source-data-mapping.json保留原主键导入12张业务表，导入明确身份和逐记录归属；迁移ES applog及历史文件。执行只读data-preflight.sql并核对数量、金额和关联。
3. 导入原业务字典和独立菜单SQL，只对指定角色分配菜单、按钮权限。用普通用户及跨租户账号验证可见性，核对13个子页和PWA/W2A完整表单。
4. 配置候选外部依赖、密钥、文件根目录、代理IP、上传限制及旧域名映射；用非生产替身先验证成功和失败分支，再在获准环境验证实际服务连通性。
5. 停止旧系统写入和定时任务，补齐最后增量，重新核对数据；nginx -t后将专用旧API域名的上游指向候选。抽验App/SDK、落地页、历史图片、跳转及后台操作后才启用新调度任务。
6. 观察错误率及外部服务日志。需要回退时恢复旧上游并nginx -t/reload，停用新Quartz任务；保留新产生的数据和文件，按切换时间对账并补偿。不要删除新增表或上传文件；数据库回滚无法撤销NPM、推送、邮件及ES外部副作用。

测试不发送真实邮件/推送/FB事件、不操作生产域名。本次交付不包含生产流量切换。生产数据和实际第三方服务仍须在具备相应材料与授权的部署环境验收。
