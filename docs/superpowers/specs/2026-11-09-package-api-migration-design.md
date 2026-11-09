# package-api 迁入 JEECG：项目分析与整体迁移方案

日期：2026-11-09。

状态：用户已确认，2026-11-09 开始实施。实施进度与验证见 docs/migration/package-api/implementation-status.md；尚未切换生产流量。

2026-11-09 复核修订：补充全源接口、全部前端 src 文件去向、前端业务规则；按用户要求改为独立一级菜单及独立 Maven 业务模块。第 5、7 节为修订后的确定架构，取代上一版将业务放入 jeecg-system-biz 的安排。

用户要求：将 `D:/workspace/package-api` 的业务接口迁入当前项目，管理前端遵循当前项目风格，业务逻辑保持不变。用户已确认：**完全兼容旧客户端**，已发布 App、SDK 和落地页无需修改接口地址、参数和返回内容。

## 1. 结论与推荐路线

推荐将业务整合为独立 Maven 模块 `jeecg-module-package`，Java 包为 `org.jeecg.modules.packagemanagement`，前端归入唯一一级菜单“应用包管理”，同时提供两个入口：

1. 管理入口：使用 JEECG 登录、菜单、权限、分页和返回格式，前端按当前 Ant Design Vue 风格重建。
2. 旧协议入口：保留旧域名上的客户端路径、参数、方法、响应编码、跳转和业务分支，由独立兼容路径调用迁移后的业务服务。

业务数据进入当前项目的独立业务表；旧系统的用户、角色、菜单、基础框架不整体复制。第一阶段保留源业务规则与现有 ES 日志能力，不把应用下发逻辑替换成当前站点分流规则，也不把源业务统计改为 ClickHouse 汇总。

### 可选路线对比

| 路线 | 优点 | 代价 | 判断 |
| --- | --- | --- | --- |
| JEECG 内原生业务模块 + 旧协议适配层 | 管理、权限、部署统一；可保留客户端契约 | 需要框架适配、页面重建、协议回归 | 推荐作为最终形态 |
| 保留 RuoYi 服务，JEECG 页面通过网关调用 | 初期业务改动少，可作为过渡 | 长期维护双系统、双权限和数据同步 | 仅用于迁移期间回退 |
| 将源控制器和 Element Plus 页面直接复制进来 | 表面改动快 | 认证、路由、依赖、返回结构和视觉风格冲突 | 不采用 |

## 2. 真实源码现状

### 2.1 源项目

实际根目录为 `D:/workspace/package-api/package-api`，外层只有一层同名目录。

| 项目部分 | 源码事实 |
| --- | --- |
| PackServer | RuoYi 3.8.6；Spring Boot 2.5.15；Java 8；WAR；Spring Security；javax.servlet；MyBatis XML；PageHelper |
| PackClient | Vue 3.2；JavaScript；Vite 3；Element Plus 2；Pinia |
| 自定义接口 | 21 个有效控制器，95 个映射：75 个管理接口、20 个客户端及回调入口 |
| 配置下发 | ClientServiceImpl 约 1,600 行，11 个服务入口，多套不同客户端协议 |
| 业务存储 | 12 张业务 MySQL 表的 Mapper；实际日志写入 ES 的 applog 索引 |
| 附带 SQL | 只有 RuoYi 基础表和 Quartz 表；未提供上述业务表的 CREATE TABLE |
| README | 上游默认模板，不能作为业务设计依据 |

计数排除了通用系统管理、监控、代码生成、Swagger 示例、注释代码。`AppLogController` 整体被注释，真正生效的是 `EsAppLogController`；`AppEventApi` 没有处理方法，不计作可调用接口。

逐项清单见 `docs/migration/package-api/source-endpoints.md`，机器可读清单为同目录 `source-endpoints.json`。其中 ANY 表示源 `@RequestMapping` 没有限定 HTTP 方法。

### 2.2 当前项目

| 项目部分 | 本地代码事实及迁移影响 |
| --- | --- |
| 当前启动与系统模块 | `jeecg-module-system/jeecg-system-start` 加载 `jeecg-system-biz`；迁移业务新增独立 `jeecg-boot-module/jeecg-module-package`，由启动模块装配 |
| 版本 | 根 POM 为 JEECG 3.9.5、Spring Boot 4.1.0、Java 最低 17；已有运行手册使用 JDK 21；以实际 POM 为准 |
| 认证 | Shiro + JEECG LoginUser；不能直接使用 RuoYi SecurityUtils、LoginUser 或 @PreAuthorize |
| 前端 | `upstream/JeecgBoot/jeecgboot-vue3`，Vue 3 + TypeScript + Ant Design Vue 4 |
| 请求规范 | defHttp；Result.result；pageNo/pageSize；records/total |
| 已有定制功能 | 站点资源、成员权限、域名验证、分流审批发布、独立 site-router，以及默认关闭的 ClickHouse API 元数据统计 |
| 数据隔离 | 当前站点模块显式核实有效租户成员关系；不能只加 tenant_id 就宣称隔离完成 |

本次分析时工作区已有 README、AWS 部署目录及部署计划的未提交变更，迁移方案不覆盖这些改动。

### 2.3 全源覆盖复核与边界修正

上一版 95 条清单只覆盖自定义业务，不能称为全项目所有接口。重新扫描整个 PackServer Java 源码，得到 46 个有效控制器声明，其中 45 个有处理方法、1 个 AppEventApi 为空；234 个方法级映射注解展开为 235 条路径（用户详情的单个注解包含两个路径）。

| 接口分类 | 路径数 | 本方案处理 |
| --- | --- | --- |
| 自定义管理业务 | 75 | 全部迁移到独立业务模块及 /package-admin |
| 原客户端/回调 | 20 | 全部迁移到 /package-legacy，外部原 URL 保持不变 |
| 通用资源上传下载 | 4 | 补充兼容桥接及历史文件迁移，原方案未逐条列出 |
| 系统/监控基础能力 | 119 | 逐条登记由宿主复用/适配；不承诺旧后台协议和每个细节已经等价 |
| RuoYi 代码生成工具 | 12 | 登记与 JEECG 生成器/Online 的差异；旧模板和生成结果不等价，非静默漏迁 |
| Swagger 示例 | 5 | 明确不迁移样例用户接口，不计为生产业务能力 |

全量接口见 `docs/migration/package-api/all-source-endpoints.md`。前端 src 共 261 个文件：50 个视图、31 个 API 文件、180 个公共组件/样式/资源/基础设施文件；全部登记于 `full-coverage-audit.json`，页面与 API 的可读清单见 `frontend-coverage.md`。

业务逻辑另登记 24 个业务服务实现/辅助文件中的 132 个方法声明，再结合控制器内逻辑、SQL、任务和启动配置审阅。静态“有去向”不等于所有分支已经证明等价，必须按第 11 节验收。具体缺口、前端规则和复用边界见 `docs/migration/package-api/coverage-review.md`。

## 3. 迁移业务范围

下表列出全部 75 个源管理接口对应的业务域。新管理路径是拟定规范；准确旧路径、方法和源文件行号以接口清单为准。

| 业务域 | 旧管理入口 | 拟定新管理入口 | 必须保留的行为 |
| --- | --- | --- | --- |
| 应用/包管理 | /system/manager | /package-admin/apps | 包名判重、开关、地区规则、链接、备注、归属转移；创建时关联商店记录 |
| 包统计 | /system/statistics | /package-admin/statistics | 注册数、支付次数、金额、总计；地区分流和原取整规则 |
| 商店状态 | /system/store | /package-admin/store | 0/1/2 状态、上架/下架时间、邮件提醒、状态探测任务 |
| 事件配置 | /system/event_config | /package-admin/event-configs | 包标识、Token、Pixel、链接配置 |
| 事件历史 | /system/history | /package-admin/event-history | 原事件字段、查询、明细和导出 |
| 事件上报日志 | /system/error | /package-admin/event-errors | 保留成功和失败记录；源实现也把成功反馈写入该表 |
| 落地页访问 | /system/count | /package-admin/landing-counts | host 对应应用、访问数、下载数和筛选 |
| PWA/W2A | /system/pwa | /package-admin/pwa | 同一模型按 appType 区分；推广链接、评论、图片、VAPID、外部代理关联 |
| 通知 | /system/notify | /package-admin/notifications | 选择应用、保存通知并触发推送、订阅关系 |
| 应用事件记录 | /system/record | /package-admin/app-events | 事件记录管理及导出 |
| 评论记录 | /system/reviewsrecord | /package-admin/reviews | 与 PWA 应用关联，兼顾编辑器内嵌维护及原记录能力 |
| 请求日志 | /system/applog | /package-admin/request-logs | ES 筛选、时间范围、分页、明细、导出及授权删除/清理 |

RuoYi 用户、角色、组织、字典、任务、日志管理等基础能力使用 JEECG 对应能力。源角色数据范围、岗位、在线强退、缓存清理、账号解锁、配置 key 及生成器模板存在语义或实现差异，逐项核验后适配；不能笼统声称全部现成等价。业务相关字典、归属关系和任务定义需要转换，不能因此漏迁。

旧管理 API 不在“旧 App/SDK/落地页完全兼容”的默认范围内；本方案迁移其所有业务能力，使用 JEECG 登录和新入口。如果还存在调用旧管理 API 的外部脚本，需在实施清单中增加管理协议适配，不能让它们误入客户端通配路由。

## 4. 旧客户端协议兼容设计

### 4.1 11 个配置下发入口

| 外部旧路径 | 源服务方法 | 已发现的输入/输出特征 |
| --- | --- | --- |
| /system/{a} | api | id 为 Base64 包名，af_id；结果整体 Base64，内部 url 也有 Base64 编码 |
| /v2/api/user/login | login | username/password；这是客户端协议，不是 JEECG 账号登录 |
| /v2/api/retry/{a} | redirected | packName、af_id；HTTP 跳转及 Location 需保留 |
| /v2/api/banner/list/{a} | getBanner | systemName、systemID；独立的嵌套响应 |
| /v2/api/check/url | checkUrl | 参数集合解析；canLoad、canAppend、load、token 等专有字段 |
| /dynamic/{a} | dynamicAdj | id；app_id、app_cache、app_limit 等字段 |
| /app/start | getUserInfo | user_id、user_token；专有 user_info、timestamp 和摘要字段 |
| /home/index/jsp | base64Data | pack_a、data_a；Base64 响应 |
| /{l} | base64Data2 | index；Base64，含时间和 group 等字段 |
| /{l}/{a} | base64Data3 | timestamp；Base64，含国家信息注入 |
| /{a}/{b}/{c} | app1 | 扫描参数值推断包名；AES/ECB/PKCS5Padding 后 Base64 |

以上为识别到的协议特征，不代替各方法的完整输入分支。实施时以源代码和特征测试为准，不能按表格直接重写逻辑。

其余 9 个客户端/回调入口也全部纳入：

- `/fb/{packageId}`：接收事件、写历史、条件满足时调用 Facebook API，写反馈日志。
- `/page/{page}`：按 fbclid、User-Agent、IP 规则选择跳转。
- `/loading/access/access`、`/loading/access/download`：按 hostname 更新计数。
- `/pwa/{path}`：PWA 公共配置、图片、评论及公钥，不返回私钥。
- `/notify/subscribe`：保存订阅。
- `/notify/notifyweb`：源匿名推送触发入口，接收 notifyId/pubKey/priKey；须单列风险，不能在“等价迁移”中悄悄改成 JEECG 登录。
- `/systemSDK/upload/data`：上报注册、支付次数和金额。
- `/systemSDK/{a}`：SDK 开关及地区分流；源有 1 秒 RepeatSubmit 行为，也须验证迁移效果。

补充资源契约：`GET /common/download`、`POST /common/upload`、`POST /common/uploads`、`GET /common/download/resource`。统一进入资源兼容控制器，保留原文件字段名、单/多文件字段结构、附件名及 download 的 delete 参数行为；若原调用实际依赖匿名访问，鉴权收紧需单列审阅。新管理页使用 JEECG 上传组件，通过管理资源适配服务处理文件；不能把“换组件”当作旧资源 URL 已经兼容。

源 `ResourcesConfig` 还注册 `/profile/**` 静态映射。它不是上述注解接口，但属于迁移交付：历史文件目录、URL、Host、预览和下载必须保留或经可验证代理映射。Swagger/Druid 等框架端点单列为运维工具，不随客户端通配路由开放。

### 4.2 路由隔离

新代码中将旧客户端控制器统一加内部前缀 `/package-legacy`，管理控制器使用 `/package-admin`。二者复用业务服务，管理和公共 DTO 分开。

流量关系：

```text
已发布 App/SDK/落地页
  → 既有 API 域名 + 原路径 + 原查询串
  → 域名对应入口代理
  → /jeecg-boot/package-legacy + 原路径
  → 旧协议控制器 → 迁移后的业务服务 → 业务表 / 外部适配器

JEECG 管理页面
  → /jeecg-boot/package-admin/...
  → Shiro 登录 + 菜单/操作权限 + 归属校验
  → 管理控制器 → 同一业务服务
```

前提：保持旧客户端访问的域名、证书和公网路径不变，只调整服务端转发。若原来同域同时承载旧管理后台或其他服务，必须先盘点已有 location 和域名职责，配置精确优先路由，不能把全站所有请求盲目转发到通配接口。

兼容入口的通配路径仅存在于 `/package-legacy` 内部，不能注册为 JEECG 根路径通配，不能使用全站 `/** = anon`。Shiro 仅放行经过审核的兼容映射，优先精确路径规则；最终启动测试验证匹配顺序和管理接口保护。

传递原 Host、原始 URI 和经可信代理处理的客户端 IP。源日志、地区规则和可能的路径计算使用原请求语义，而非新增内部前缀。查询串、重复参数、多值顺序、请求体、Content-Type 和重定向响应均需要代理链回归，避免重写时丢参或重复编码。

### 4.3 “逻辑不变”的验收边界

必须保持：包不存在/关闭时的默认响应、地区及运营商分支、URL 参数拼接、各入口不同的字段名/类型、空字符串/null、Base64 层数、AES 算法与有效密钥、HTTP 状态和 Location、SDK 金额舍入、分流条件及概率、事件字段映射、商店状态流转。

管理层允许且需要适配：JEECG Token、Shiro 权限、用户 ID、分页、Result 包装、组件和上传接口。兼容客户端响应不能统一包成 Result，也不能增加 Token、签名或 tenant-id 请求要求。

时间戳、随机数和外部 IP 结果在对照测试中固定；可确定的密文、Base64 及响应字节应逐一比对。JSON 对象对照还需核对字段类型、缺失/null 区别；不得仅比较“包含某个成功字段”。源环境的默认字符集和实际序列化行为需固定后再确认等价。

源代码存在疑似缺陷的分支先纳入现状记录，作为独立变更审阅，不能以顺手优化为由改变客户端行为。

## 5. 独立后端模块、架构层级和依赖适配

### 5.1 Maven 隔离与装配

新增 `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package`，与现有 jeecg-module-demo、jeecg-boot-module-airag 并列。父聚合 POM 注册新模块，jeecg-system-start 增加该模块依赖，沿用当前应用启动和部署。

新模块依赖 jeecg-boot-base-core 及必要的宿主公开契约，不依赖 jeecg-system-biz、siteplatform、analytics 的业务实现，不复制或继承它们的服务类。租户和用户校验通过本模块身份适配层访问宿主公开能力或明确的只读查询，不直接调用 ResourceAccess 等其他业务域内部类。

```text
jeecg-boot/
  jeecg-boot-module/
    jeecg-module-package/
      pom.xml
      src/main/java/org/jeecg/modules/packagemanagement/
        controller/
          admin/                 JEECG 管理入口 /package-admin
          legacy/                原协议入口 /package-legacy，含资源兼容
        dto/
          admin/                 管理查询/表单/结果，按业务域分类
          legacy/                旧客户端字段、类型、响应和协议载体
        application/
          apps/                  包配置、归属转移和下发用例
          statistics/            注册/支付统计与 SDK 用例
          store/                 商店状态和探测用例
          event/                 配置、历史、反馈与 FB 上报
          pwa/                   PWA/W2A、订阅和评论编排
          notification/          通知与推送用例
          landing/               落地页计数
          applog/                请求日志查询、导出、清理
          port/                  持久化及外部依赖契约
        domain/                  按业务域划分的规则、状态与值对象
        infrastructure/
          persistence/entity/    业务数据实体
          persistence/mapper/    Mapper，配套 XML 在资源目录
          integration/           IP、代理管理、WebPush、FB、邮件、ES
          identity/              JEECG 用户/租户/角色适配
        job/                     商店探测等本模块任务
        config/                  本模块配置、开关、Bean 装配
        exception/               管理错误和原协议错误转换
      src/main/resources/
        org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/xml/
        db/package/              结构、数据映射、字典与菜单脚本
        application-package.yml 本模块配置，由宿主显式导入
      src/test/                  下发协议、业务、权限和集成测试
  jeecg-module-system/
    jeecg-system-start/           仅增加依赖及必要的接入配置
```

调用方向为 controller → application → domain/port，由 infrastructure 实现 port；job 调用 application。禁止控制器直接访问 ES/Mapper、前端引用其他业务模块私有 API、跨域调用其他模块内部服务。现有 ClientService 分支先以协议处理器保留在 application/apps，不为分层强行重写其算法。

业务实体和 Mapper 按实际业务域命名，所有新增 Spring Bean、任务类及 mapper namespace 避免与源/现有系统重名。JEECG 当前启动类在 org.jeecg 下扫描，Mapper 配置包含 org.jeecg.**.mapper*；实施阶段验证新模块组件和 XML 被正确加载，不能只证明 Maven 编译成功。

### 5.2 与现有业务的命名边界

| 维度 | 新业务固定边界 |
| --- | --- |
| Maven / Java | jeecg-module-package / org.jeecg.modules.packagemanagement |
| 管理 API | /package-admin/{业务域}/...；不新增 /sys、/system、/site-platform 下的业务接口 |
| 客户端兼容 API | 内部 /package-legacy + 原路径；外部旧地址不变 |
| API 文档 | 分组“应用包管理-管理接口”“应用包管理-旧协议接口” |
| 权限 | package:{业务域}:{动作}，例如 package:apps:list、package:apps:transfer |
| 数据表 / SQL | pkg_*；db/package 下分结构、菜单、字典和数据转换脚本 |
| 字典 | 新业务字典使用 pkg_ 前缀；适配旧值，不覆盖宿主同名字典 |
| 配置 / 缓存 | jeecg.package-management.* / pkg:*；环境变量使用 PACKAGE_* |
| 任务 / 日志 | Package*Job；本模块任务组和日志 logger/事件标签，不混用站点路由任务 |
| 部署 | deploy/package-api/ 兼容入口、资源和外部服务模板 |

初始脚本显式安装并校验版本，不因放入资源目录就自动执行生产 DDL。对宿主的修改限制为 Maven 装配、配置导入、兼容路由鉴权接入和菜单注册；业务实现全部保留在独立模块。

### 5.3 依赖适配

| 源实现 | 迁移处理 |
| --- | --- |
| javax.servlet / javax.validation / javax.annotation | 适配宿主使用的 Jakarta API；javax.crypto 属于 JDK，不能机械替换 |
| RuoYi BaseController、AjaxResult、TableDataInfo | 管理端用 Result 和 records/total；客户端使用独立原协议 DTO |
| @PreAuthorize + @ss | 映射为 @RequiresPermissions；建立对应菜单、按钮权限 |
| SecurityUtils、Long 用户 ID、roleId=101 | 后端从 JEECG 当前用户解析操作者；显式迁移用户映射和“统计管理员”角色语义 |
| MyBatis XML + PageHelper | 优先保留业务 SQL 与字段语义，管理分页适配宿主 MyBatis-Plus；不导入整套旧数据源配置 |
| Springfox、旧 SecurityConfig、RuoYi 启动器 | 不导入宿主；使用 JEECG 现有基础设施 |
| ES 旧客户端 API | 日志能力放在独立适配器，按宿主依赖与源 ES 实际版本选择兼容客户端；保留查询和导出行为 |
| Facebook SDK 开放版本区间 | 在验证兼容后固定具体版本，不照搬无限上界依赖 |
| WebPush、BouncyCastle、邮件 | 单独验证 JDK 21/宿主依赖兼容；通过适配器模拟，不在本地测试中触发真实发送 |
| 硬编码地址、凭证、默认加密配置 | 改为受保护环境配置；既有客户端所依赖的有效加密配置保持一致，不在文档/仓库复制凭证 |

依赖版本选择须通过真实构建和连接测试确认，本方案不宣称把 imports 替换完即可运行。

## 6. 数据迁移与归属

### 6.1 业务表

推荐新增 `pkg_` 前缀表，避免与现有和未来上游表冲突。源主键值优先保留，单独记录旧用户到新用户的映射。

| 源表 | 拟定目标表 | 主要关联 |
| --- | --- | --- |
| app_manager | pkg_app_manager | 包名、归属用户；与商店状态按包关联 |
| app_statistics | pkg_app_statistics | 包名、金额、计数、分流配置 |
| app_store_status | pkg_store_status | 包名、商店地址、状态和时间 |
| event_config | pkg_event_config | 包标识、事件 Token/Pixel |
| evnet_history | pkg_event_history | 保留源拼写对应关系，保存原事件与包关联 |
| event_error | pkg_event_error | 包标识及上报反馈 |
| load_count | pkg_load_count | host、应用 ID、归属 |
| app_pwa | pkg_pwa | appCode、appType、linkId、域名、VAPID、归属 |
| app_pwa_sub | pkg_pwa_subscription | appId、endpoint、订阅密钥 |
| app_notify | pkg_notification | appId、通知内容和归属 |
| app_event_record | pkg_app_event | appId、事件及设备信息 |
| reviews_record | pkg_review | appId、评论 |

字段映射见 `docs/migration/package-api/source-data-mapping.json`。这是 Mapper 证据，不是已确认的生产表结构。源 `app_log` SQL Mapper 是历史实现；实际日志按 `applog` ES 索引单独处理。

### 6.2 标识与租户策略

- 旧业务 ID、appCode、包名、推广路径和关联 ID 保持稳定；保留现有 VAPID 密钥对，否则已订阅浏览器可能失效。
- 源 Long 用户 ID 对应 JEECG 字符串用户 ID，建立 `legacy_user_id → jeecg_user_id` 显式映射；不复制两套账号表或假设密码可以直接通用。
- 初期将源业务映射到明确的导入租户，并保留原归属关系。是否与站点实体关联作为独立能力，不要求每个旧包立即绑定当前站点。
- 旧客户端没有租户参数，按原包名、appCode、host 等业务键定位归属。包名和 appCode 等原有全局查找键继续保持全局唯一；不能直接放宽成“租户内唯一”导致客户端查到多个对象。
- 管理端依据当前有效租户成员、业务归属和权限查询；详情、更新、转移、导出、批量删除都在后端校验。源页面传入 userId 不能作为权限凭证。
- 源“管理员/统计角色可看全部”的语义映射到明确 JEECG 角色，不能把所有已登录用户设为管理员。归属和租户接入是管理层适配，不改变公共下发规则；正式实施前审阅角色可见范围矩阵。
- 管理 DTO 对超出 JavaScript 安全整数范围的 ID 使用字符串；旧客户端 JSON 数字/字符串类型按原协议保留。

### 6.3 导入与核验顺序

1. 取得只读业务 DDL：类型、精度、字符集、默认值、唯一约束、索引、真实关联，以及源业务字典和任务定义。
2. 在隔离环境按 Mapper 和 DDL 建目标表；所有迁移脚本支持重复运行检测，禁止覆盖当前 sys_* 表。
3. 建立用户映射、导入归属，再导入包/商店/事件配置/PWA，再导入子表、统计、历史和通知；设置正确的自增起点。
4. 将 `app_status`、`app_where`、`app_area`、`store_status`、`event_name`、`country_name`、`load_host` 等业务字典导入对应 `pkg_*` 字典，前端查新编码、提交/下发仍保留旧值类型和含义；不覆盖现有同名字典。
5. 按表核对记录数、主键集合、关键字段校验值、包名重复、孤儿记录、金额汇总、VAPID/appCode/linkId 一致性。
6. ES 历史日志保留旧索引只读或导入目标日志索引；查询层按明确时间边界合并，避免重复计数。不要将无租户信息的历史日志直接开放给所有租户。

实际数据量、源数据库和 ES 服务可用性尚未获取；本轮没有连接源运行环境，因此不宣称完成数据迁移。

## 7. 前端重建方案

### 7.1 唯一一级菜单与子菜单

新增唯一一级菜单 **应用包管理**，根路由 `/package-management`，所有迁移业务均放在其下，不散落到现有系统管理、站点资源或统计菜单。根为目录，设置 alwaysShow；由有权限的首个子菜单承接默认跳转，不能固定跳转到未授权页面。

| 子菜单 | 页面路由后缀 | 页面目录 | 主要后端域 |
| --- | --- | --- | --- |
| 包配置 | apps | apps | /package-admin/apps |
| 商店状态 | store | store | /package-admin/store |
| 包统计 | statistics | statistics | /package-admin/statistics |
| 事件配置 | event-configs | event-configs | /package-admin/event-configs |
| 事件历史 | event-history | event-history | /package-admin/event-history |
| 上报日志 | event-errors | event-errors | /package-admin/event-errors |
| PWA 应用 | pwa | pwa | /package-admin/pwa，appType=0 |
| W2A 应用 | w2a | w2a | /package-admin/pwa，appType=1 |
| 落地页统计 | landing-counts | landing-counts | /package-admin/landing-counts |
| 应用事件 | app-events | app-events | /package-admin/app-events |
| 评论管理 | reviews | reviews | /package-admin/reviews |
| 消息推送 | notifications | notifications | /package-admin/notifications |
| 请求日志 | request-logs | request-logs | /package-admin/request-logs |

共 13 个业务子菜单：源 12 个业务页面全部有落点；评论另提供独立子菜单，保留 PWA/W2A 内嵌编辑。详情、编辑和转移使用对应页面的弹窗/抽屉或隐藏路由，不产生游离的一级菜单。按钮权限按 package 前缀注册，根无可访问子菜单时隐藏。

共享的 JEECG 用户、角色、基础字典及通用监控页面继续使用宿主入口；它们是基础能力复用，不新建一套源后台菜单。业务中的用户选择、字典和任务关联由独立模块适配。

### 7.2 前端目录隔离

在 `jeecgboot-vue3/src/views/packagemanagement` 新增业务页面，默认按当前项目主题和交互规范实现：

```text
src/views/packagemanagement/{上表页面目录}/
  index.vue、*.data.ts、*Modal.vue 或 *Drawer.vue
src/views/packagemanagement/components/
  PwaEditor、ReviewEditor、RatingEditor 等本业务共享组件
src/api/packagemanagement/{业务域}.ts
src/types/packagemanagement/{业务域}.ts
```

API 统一归入 src/api/packagemanagement，页面不再单独散放同用途的 *.api.ts。路由 name、组件 name、局部持久状态 key 使用 Package 前缀，缓存、样式和选择状态不能与 siteplatform/analytics 混用。业务样式采用 scoped/Less，不覆盖宿主全局主题。

- 列表使用 BasicTable、BasicForm、useListPage；查询、重置、分页、批量操作与当前后台一致。
- 简单新增/编辑使用 BasicModal；PWA/W2A 多字段编辑使用 Drawer，按基础信息、推广与域名、素材、事件、评论分组。
- 页面/数据定义采用 `index.vue`、`*.data.ts`、`*Modal.vue` 或 `*Drawer.vue`；API 统一在上述专属目录；TypeScript、script setup、Less。
- API 使用 defHttp；权限使用 v-auth/usePermission，菜单从后端权限加载。
- 图片、上传、字典、日期与导出使用 JEECG 现有组件。旧图片和附件 URL 保持可访问，不能只替换上传按钮而丢失历史资源。
- 保留字段、默认值、按钮条件、开关数值、筛选、表单校验及业务操作；只调整视觉和框架连接方式。

### 7.3 前端业务逻辑完整迁移

前端不只是 CRUD 外观，以下源码行为必须逐项迁移：PWA/W2A 标签 JSON、五档人数加权评分和一位小数显示、评论必填与时间格式、删除已有评论立即调用删除接口、load_host 的 value→label 域名后缀转换；统计表金额输入两位小数、编辑时禁改计数/包名及列表与总计各自查询参数；商店提交时生成 Google Play 链接；包管理多选地区序列化、admin/statistics 转移按钮与接收人选择；日志日期范围、导出、清理确认、单选多选状态；通知应用选择及应用信息联动。

完整页面方法及 API 导入见 frontend-coverage.md，规则验收矩阵见 coverage-review.md。源界面注释掉的控件、未调用方法和 ${comment} 生成器残留单列记录，不把它们误认为已上线的功能；后端真实存在的接口仍按清单迁移。

PWA 与 W2A 复用编辑器，以 appType 区分；保留原 0/1 语义。包统计保留现有总计卡片，不把 ClickHouse API 调用量误当作注册或支付指标。

本次管理 UI 风格统一不自动包括重做面向终端用户的 PWA/W2A 推广页。源码没有提供服务端引用的 `/data/pwa/autopwa`、`/data/pwa/autow2a` 完整部署资源；需要保留或补齐原静态站点、Service Worker 和资源后才能验收完整链路。

## 8. 外部服务与副作用

| 外部依赖 | 源行为 | 迁移约束 |
| --- | --- | --- |
| IP 地理/运营商服务 | 参与多个配置下发和跳转判断 | 首期保留原判定及失败回退；可信代理 IP 传递必须测试 |
| Nginx 管理 API | PWA 新增、修改、删除会增改删 proxy-hosts | 保留 linkId 关联；独立适配器和配置；不能误认为当前 site-router 可直接替换 |
| VAPID 生成服务 | 新建 PWA 调外部服务取得密钥对 | 已有密钥原样迁移；新建流程按源行为适配，替换生成机制另行审阅 |
| 浏览器 Push 服务 | 保存通知触发推送，另有匿名触发接口 | 保持内容和订阅协议；测试使用模拟发送器；生产联调先使用专用测试订阅 |
| Facebook 事件 API | 事件先入库，再按条件发送并记录反馈 | 测试阻断真实副作用；不能拿生产历史事件做无保护回放 |
| 商店页 HTTP + 邮件 | 状态变化后记录时间并发送提醒 | 迁移 ryTask.ryGetStatus 的业务调用到 JEECG 任务；实际 cron 从源任务配置核对，初期暂停 |
| ES | 记录请求和结果，支持日志检索/清理 | 独立日志适配器，保留检索字段和时间语义；清理能力必须有单独权限 |

当前 site-router 的审批发布流程与源 PWA 外部代理即刻增删改不是同一种行为。本次先保持旧适配器语义；若要统一为审批发布，需要另行确认业务流程变化。

## 9. 已发现的问题与处理边界

下列问题来自代码证据，直接影响迁移，而非通用风险列表：

1. **缺业务 DDL 和线上配置**：Mapper 无法证明实际字段长度、索引、NULL/default；先出隔离环境结构，再核对真实 DDL，不能直接生产导入。
2. **宽泛动态路由**：隔离在兼容前缀和旧域名代理内；必须做完整宿主路由回归。
3. **匿名与权限注解并存**：EsAppLogController、StoreStatusController 有类匿名和方法权限标注；不能按类匿名注解批量放行管理能力。
4. **归属依赖请求参数**：多个页面传 userId，部分后端只在特定条件清空或调整它；迁入当前权限体系时统一用后端操作者与归属校验，并明确角色范围。
5. **外部副作用不受数据库事务回滚控制**：PWA 代理配置、推送等可能已执行但数据库失败；保持正常业务结果，另增加失败记录和人工对账路径，避免自动重试重复发送/创建。
6. **推送状态不代表到达**：源代码捕获部分发送异常后仍可能返回“推送成功”，@Async 存在同类内部调用；不能把源码注解等同于实际异步送达保证。第一阶段先对照记录真实行为，队列/送达状态升级另行设计。
7. **计数为读改写**：访问、下载、注册和支付计数存在并发丢更新可能。迁移保留指标含义，原子累加及去重作为单列改进；不与协议迁移混在一起悄悄改动。
8. **商店状态 2 不再探测**：源 getStatus 排除 status=2，是否持续重新上架检测不能凭需求想象；默认按源逻辑保留。
9. **历史字段拼写与可疑条件**：如 reigisterNum、evnet_history、ReqHeards、page 的 User-Agent 条件、ParamsUtils 取第一个含点的参数值。先保留可见语义，并建立相应样例。
10. **匿名主动推送和硬编码配置**：匿名触发接口、请求携带私钥、硬编码服务地址/邮件收件人需独立列出。更改鉴权、收件人、重试、加密模式等属于额外业务/协议变更，应明确审阅；本地验证不调用这些真实服务。

## 10. 分阶段实施与每阶段交付

| 阶段 | 工作 | 退出条件 |
| --- | --- | --- |
| 0：清单与基线 | 审阅本方案、235 条全源路径去向、261 个前端 src 文件、角色矩阵、12 表字段；补足 DDL、协议样例和依赖配置 | 99 条迁移接口、基础能力复用及工具差异边界固定 |
| 1：宿主接入 | 独立 Maven 模块、分层目录、配置、目标 SQL、唯一一级菜单及 13 个子菜单、权限和用户映射 | 模块被正确装配；原模块受保护；脚本可验证 |
| 2：包管理与下发 | 应用/商店 CRUD、11 个下发协议、兼容路由；JEECG 包管理页面 | 全部下发正常/失败分支对照，旧 URL 代理链通过 |
| 3：统计与事件 | 统计、总计、SDK、FB、跳转、访问/下载、历史与错误记录 | 指标、金额、分流、事件及跳转对照；模拟副作用通过 |
| 4：PWA/W2A 与通知 | 配置、评论、订阅、外部代理、VAPID、通知及前端 | 同一 appCode/密钥和素材可用；完整测试订阅链路通过 |
| 5：日志与任务 | ES 请求日志、导出/清理、商店探测任务、邮件适配 | 权限、检索、任务状态变化及失败分支通过 |
| 6：数据演练和交付 | 隔离导入、4 个资源接口及历史静态资源、基础能力差异验收、全量回归、构建、部署模板、切换和回退演练 | 全覆盖矩阵逐项签收；无未交代的依赖或工具差异 |

实施可先使用模拟外部适配器继续开发，不因缺少生产凭证而停在空目录或伪接口。模拟模式必须在页面/运行记录中清楚区分，不冒充生产链路验收。

工作量初估为 18–28 人日：基线与数据 3–4、核心下发 4–6、统计事件 3–5、PWA 通知 4–6、日志任务及全链路验证 4–7。该区间为范围估算，不是交付承诺；数据量、实际 DDL、旧客户端样本和外部服务是否可复现会显著影响工期。

## 11. 验证标准

### 11.1 行为对照

为 20 个旧公共入口逐一建立样例。11 个配置服务覆盖：缺参数、错误编码、包不存在、关闭、地区命中/未命中、运营商限制、不同自用开关、已有/没有 query 的 URL、异常 JSON、有效/无效 AES 密钥、时间和随机分支。逐项保留原 HTTP 方法兼容性。

另对 4 个资源兼容接口和 /profile/** 验证单/多文件、字段名、历史路径、文件名、删除参数和实际响应。源 Asia/Shanghai 默认时区、Snowflake 标识生成、JSON/日期格式、异常处理、RepeatSubmit、CORS、可信代理 IP 及实际 HTTP 方法由模块局部适配，禁止为迁移而更改宿主全局时区、ObjectMapper 或其他模块的拦截策略。

同一数据、时钟、随机数和 IP 结果下，对照旧实现与新实现的响应、Location 和业务持久化变化。通知、Facebook、邮件、代理管理采用记录型模拟适配器，比较调用条件、参数及次数，不能双重触发生产操作。

### 11.2 管理与权限

75 个源管理映射逐一对应迁移能力：列表、详情、创建、修改、删除、导出，以及转移、总计、PWA 选项和日志清理等特殊能力。验证无权限、越权 ID、跨用户/租户、批量混入他人记录、导出绕过、撤权后操作等场景。

### 11.3 页面和集成

前端完整生产构建、后端定向测试和完整打包；逐页验证菜单、查询、表单、字典、素材、评论、分页、按钮和错误提示。兼容域名模拟测试验证原路径、可信 IP、Host、查询参数和跳转，且 JEECG 原登录、菜单、站点资源与分流流程不受影响。

新增结构验收：业务实现只存在于独立 Maven 模块；75+20+4 条迁移接口全部有目标和用例；13 个子菜单全部挂在唯一根目录；新增管理权限仅使用 package 前缀；业务 API/类型/页面均位于专属前端目录；宿主修改符合第 5 节接入范围。119 条基础接口和 19 个基础/工具 API 模块逐项核对调用场景，工具差异和示例排除须在交付清单中可见。

### 11.4 数据和部署

12 表记录及关联、金额总计、源业务键、账号映射、ES 时间范围和 PWA 密钥核验通过；新建数据与历史数据均可读写。没有原 DDL、部署静态资源和真实测试凭证时，只报告已通过的代码/模拟验证，并明确未完成的端到端验收项。

## 12. 切换与回退

1. 保留旧服务和其原数据备份；新模块使用独立路由和受控开关先在隔离环境运行。
2. 执行首次导入与只读对照。状态写入、事件、计数、邮件、Push、代理操作禁止无保护镜像回放，避免重复副作用。
3. 正式切换前短暂冻结管理写入；客户端写入安排短维护窗口或在入口可靠缓冲，明确恢复和重放去重策略后再实施，不允许假设“只读影子”已经同步最后增量。
4. 按源可用方式导出最后增量或停写快照，核对后将旧域名入口转到新服务。邮件、Push 和商店定时任务只在单一系统启用。
5. 回退优先通过入口代理切回旧服务；回退前需要把新系统产生的业务写入按预先验证的同步脚本回补或保留为待恢复增量，不能只切代理就宣称数据无损。
6. 任何真实域名切换、历史数据覆盖、通知发送及生产任务启用必须在对应演练结果可审阅后执行。

## 13. 已完成与后续输入

本轮完成：读取两个项目实际源码；全源复核 235 条接口路径并登记去向；整理 50 个视图、31 个 API 和 180 个前端支持文件；登记业务服务/辅助方法；补齐 4 个资源接口和静态文件契约；整理 12 张业务表 Mapper 字段；补充前端业务规则；确定独立一级菜单、13 个子菜单和独立 Maven 分层方案。

尚未执行：业务代码迁移、业务表创建/导入、前端页面开发、源应用启动、真实旧新协议联调、生产部署和流量切换。

实施所需补充信息可在相应阶段提供，无需在方案阶段暴露凭证：源业务 DDL/脱敏样例、用户和角色映射、旧 API 域名及现有代理规则、源 ES 版本和索引结构、PWA/W2A 静态站点资源、外部服务的测试环境配置。敏感值通过受保护的本地配置交付。

下一步：审阅此设计后编写详细实施计划，再按阶段迁移和验证。用户已确认旧客户端完全兼容；待审阅的是本文件中的整体架构、管理侧权限适配和迁移边界。

