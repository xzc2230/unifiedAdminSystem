# package-api dev → JEECG 迁移缺口审计

审计日期：2026-11-09。结论：**原 main 迁移基线不能代表 dev 全量覆盖；当前 dev 尚未迁完。** 本次只排查与生成清单，没有修改业务代码、生产数据库或部署配置。

## 1. 对比范围与可信边界

| 项目 | 本次依据 |
| --- | --- |
| 源目录 | `D:/workspace/package-api/package-api` |
| 源 main | `39b1f92af432bed135f9c639b936f7faffbe64af` |
| 源 dev | `0f83f8128df8c9978c49479775f86fa716fb435d`（已 fetch origin main/dev 核对） |
| JEECG 分支 | `codex/package-api-migration` |
| JEECG HEAD | `160d9effa6432b9b0f930fcc16409ada5fd5513a` |
| 原快照验证 | 330 个源文件与 main 全部相符（允许 LF/CRLF 差异）；与 dev 相符 319 个，11 个发生修改 |
| main → dev 全仓差异 | 193 个文件：新增 167、修改 24、删除 2 |

源工作区 `PackServer/sql/app3.sql` 存在未提交修改，本审计按 **origin/dev 已提交版本**，未将该本地修改纳入已发布功能结论。JEECG 工作区其他任务的未提交文件保持原状。

原 `source-snapshot.json`、`full-coverage-audit.json`、`target-endpoints.json` 是旧迁移范围的证据，不会因 dev 增加文件自动扩大覆盖范围；已有覆盖检查通过不代表 dev 新增内容已迁。本次另建审计文件，不覆盖旧基线。

本报告为源码、路由、Mapper、SQL、页面及权限静态核对。没有连接线上数据库、导入历史数据或验证线上 Nginx 与定时任务，因此不声称线上已部署这些能力。

## 2. 缺口总览

| 功能 | 当前状态 | 主要缺口 | 新增接口缺口 |
| --- | --- | --- | ---: |
| 四段客户端协议及核心风控 | 已迁入 | 管理界面另列；不需要重迁协议 | 0（新增的 1 个已迁） |
| 全局风控与自动拉黑管理 | 部分迁入 | 四类黑名单 CRUD、ASN 信息查询、自动拉黑列表/解除、管理页面 | 23 |
| OpenAPI | 未迁入 | Token 生命周期、独立鉴权、应用管理 API、调用日志、3 个页面 | 11 |
| 包统计 packstat | 未迁入 | 独立业务、统计报表、商店检测、调度、邮件、页面 | 8 |
| 应用字段权限 | 未迁入 | 字段级编辑与查询授权、后端校验/脱敏、前端联动 | 原接口内部逻辑变化 |
| 应用管理附加变化 | 部分未对齐 | 包名 trim、Redis 镜像维护、OpenAPI 专用归属访问 | 原接口内部逻辑变化 |
| 原商店状态检测 | 未完全对齐 dev | HTTP 结果上下文、异常传播和日志行为 | 原接口内部逻辑变化 |
| 应用日志、字典提示 | 部分增强未迁 | 参数 JSON 格式化/复制、字典备注提示 | 原接口内部逻辑变化 |
| 时区/SIM 配置 | 未迁入，源端停用 | 4 个存储字段及停用 UI/辅助数据，不是生效风控缺口 | 0 |
| 修改角色后的会话处理 | 未发现等效强制下线链路 | 需要按 JEECG 会话和权限体系适配验证 | 宿主适配项 |

**dev 新增控制器共 43 个 HTTP 方法+路径声明，已迁 1 个，缺少对应功能实现 42 个。** 此数字只统计新增控制器的路由，不代表所有业务差异只有 42 项。原控制器内部行为变化另列；不以路径通配匹配或非 404 认定功能已实现。

新增 Vue 页面共 **5 个未迁入**：风控 1、包统计 1、OpenAPI 3。新增业务表共 10 张，其中四类黑名单表已迁，另外 **6 张未迁入**。

## 3. 已有功能，不重复计算为缺失

JEECG 模块位于 `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package`；以下路径均相对此模块 Java 包 `org.jeecg.modules.packagemanagement`。

- `controller/legacy/LegacyClientController.fourPath` 已接入四段 POST/JSON 协议，调用 `application/apps/risk/app2/App2ClientService`。对应源 `Client3Controller`、App2 风控、XOR 载荷与 AES 响应；详见同目录 `four-path-migration.md`。
- 四类黑名单的 domain、Mapper/XML、`pkg_app_*_blacklist` 表已存在；`db/package/008-four-path-risk.sql` 已补 5 个有效风控字段，应用表单可编辑。
- `App2PkgRiskRedisService` 已有查询/解除自动拉黑的内部方法，缺的是后台 API、授权及前端入口。
- 当前应用归属、租户隔离、资源访问校验以及身份管理已有实现；它们不能替代新 OpenAPI Token 与字段权限，但也不能计为“完全没有权限隔离”。

四段迁移快照中的 26 个源文件与本次 origin/dev 全部相符（允许 LF/CRLF 差异），没有发现其后新增的源代码差异。本次按源 dev 实际实现核对四段协议，不按旧客户端文档中 GET/UA 参数说明推断实现。

## 4. 必须补迁的完整功能

### 4.1 包统计：整套未迁

源后端：`PackServer/src/main/java/com/ruoyi/project/packstat/`、`resources/mybatis/packstat/`。
源前端：`PackClient/src/views/system/packstat/index.vue`、`packstatUtils.js`、`src/api/system/packstat.js`。

缺少包记录维护、作者/地区/月份筛选、打包人/客户/平台/日期及补包信息；存活和使用天数按日期计算，7～14 天采用互斥分桶，15 天以上单独汇总。缺少 `surviveSummary` 聚合及配套报表。

缺少 `PackStatTask.runRound` 调度及检测链路：待检测包筛选、商店 URL 解析、404 下架判断、200/301/302 正常判断、其他结果无效；有效结果更新日期/天数，无效结果不更新。还包括按结果调整检测间隔、线程/频率配置、保留 5 天历史以及首次下架邮件。

**现有“应用统计”记录 SDK 注册/支付等数据，现有“商店状态”是另一套检测，二者都不是包统计 packstat。**

### 4.2 OpenAPI：整套未迁

源后端：`PackServer/src/main/java/com/ruoyi/project/openapi/`。
源前端：`PackClient/src/views/system/openapi/token.vue`、`calllog.vue`、`docs.vue` 及 `src/api/system/openapi.js`。

缺少 `X-Api-Token` 拦截鉴权、Token 生成/启停/过期、外部应用管理 CRUD、请求响应日志、后台日志查询和在线文档/测试页面。

源 OpenAPI 以 `user_id=token.id AND user_name='api'` 隔离应用记录。迁入时应显式保存 Token 与 JEECG 租户/资源的关系，不能将 Token ID 当作 JEECG 用户 ID，也不能直接借用当前后台登录身份。

### 4.3 风控管理：运行规则已有，后台操作缺失

源控制器：`AppAsnBlacklistController`、`AppDeviceBrandBlacklistController`、`AppCpuAbiBlacklistController`、`AppDeviceNameBlacklistController`、`App2AutoBlacklistController`。
源前端：`PackClient/src/views/system/risk/index.vue`、`src/api/system/riskBlacklist.js`；应用管理页还新增自动拉黑管理入口。

缺少四类黑名单的列表/详情/新增/修改/删除、后台默认值及审计字段填充；缺少 `RipeStatAsnLookup` 的 ASN 校验、外部持有人信息查询；缺少自动拉黑列表与解除入口。已有 Mapper 并不等于这些管理能力已开放。

### 4.4 应用字段权限：后端与前端一起补

源依据：`app/support/AppManagerFieldPermissionPolicy.java`、`AppManagerServiceImpl.java`、前端 `constants/managerFieldPermissions.js`，以及两份 `app_manager_*field_permissions.sql`。

编辑授权覆盖 14 项：`appName`、`author`、`packageCode`、`url`、`status`、`whetherRestrict`、`areas`、`mark`、`whetherUs`、`gaidAidThreshold`、`aidDeviceThreshold`、`aidGaidSwitchThreshold`、`riskWindowSeconds`、`referrerBlacklistJson`。

查询授权覆盖 11 项：`appName`、`author`、`userName`、`packageCode`、`status`、`url`、`whetherRestrict`、`areas`、`whetherUs`、`mark`、`updateDate`。

源规则包含超级权限 `*:*:*`、没有授予细分字段权限时使用模块权限的兼容回退；授予细分权限后按字段限制。修改时比对数据库旧值防止绕过表单提交，查询时屏蔽无权字段，同时保留分页总数。JEECG 当前模块/操作权限、字段编辑白名单及资源归属控制不能等价替代该规则。

迁入时还需明确源可编辑包名与目标现有包名不可变保护的冲突；当前保护是有意设计，不能在补权限时顺带放开。

## 5. 原有功能的 dev 增量差异

### 应用查询与缓存

源 `AppManagerServiceImpl` 对包名做空值检查与 trim，每次仍查数据库，并同步写入/删除 `app_manager:package:` Redis 镜像，增改也维护镜像。目标尚无等效完整链路。该源逻辑不是“优先缓存读取”；不要误写成已缺少缓存加速。带前后空格的客户端包名查找可能产生行为差异，应补对比测试。

源 OpenAPI 增加按指定归属访问的方法，属于 OpenAPI 一起补迁。源普通修改 SQL 不再改归属，只由转移处理；目标已有归属保护，应保留并核对语义。

### 原商店检测的异常与诊断

源 `HttpClientUtil.getStatus` 由 boolean 改为包含在线状态、状态码、消息、响应头和最多 2048 字节正文的结果，并将 I/O 异常作为异常向外传播；`StoreStatusServiceImpl` 增加响应与状态转移日志。

目标仍以 `StoreProbePort` 的 boolean 结果为主，适配层可将请求异常转为 false。因此异常时可能按离线处理，与 dev 异常中止/上抛的行为不同。此项应优先做网络错误、404、正常响应的对照验证。源通知收件人列表也有变化，应由目标配置承接，不复制源码中的地址。

### 日志和字典

源应用日志接口增加参数 JSON 格式化，前端请求参数列增加格式化悬浮展示和复制。目标详情虽可展示字段，但没有完整对齐该增强。源字典加载增加备注支持，用于提示信息；目标现有 value/label 映射未完整承接。

### 时区与 SIM：明确为停用项

dev 新增 `systemTimezone`、`whetherTimezoneRestrict`、`simCountry`、`whetherSimCountryRestrict` 的模型/SQL、选项及联动资料，目标未完整迁入。

但源 `manager/index.vue` 的相关编辑 UI 被注释，提交时显式把两个开关设 0、两个值设 null（约 780～784 行）；`App2GlobalRiskEvaluator` 也停用对应校验。因此它们应列为数据结构/保留代码差异，**不能报告为线上已生效但漏迁的设备风控规则，也不应直接启用。**

### 修改角色后的会话处理

源 `SysRoleController` 修改角色后调用 `TokenService.forceLogoutByUserIds`，保留当前会话并清理相关其他会话；对应服务/Mapper 有用户 ID 查询变化。已查看 JEECG 角色编辑与角色权限保存链路，未发现同等的批量强制下线调用。

这是宿主框架适配项：继续核对 JEECG Shiro/JWT、权限缓存和会话失效行为后实现，不能直接复制 RuoYi TokenService，也不据此声称 JEECG 现有权限立即生效机制完全失效。

## 6. 表结构缺口

| 源新增表 | 状态 |
| --- | --- |
| `app_open_api_token` | 未迁：Token 配置 |
| `app_open_api_call_log` | 未迁：调用日志 |
| `app_pack_stat` | 未迁：包统计主表 |
| `app_pack_stat_region` | 未迁：地区配置 |
| `app_pack_stat_detect_config` | 未迁：检测配置 |
| `app_pack_stat_detect_history` | 未迁：检测历史 |
| 四类 `app_*_blacklist` | 已有 `pkg_` 对应表；后台管理未完成 |

主要源 SQL：`app_open_api.sql`、`app_pack_stat.sql`、`app_pack_stat_detect_stats.sql`、`app_pack_stat_notify_email.sql`、`app3.sql` 与两份字段权限 SQL。迁入须转成目标 `pkg_` 表、JEECG 菜单权限和任务配置；不能直接执行 RuoYi 的菜单/用户/Quartz 初始化 SQL。是否已导入历史业务数据不在本次源码核对结论内。

## 7. 补迁的菜单与架构安排（建议，尚未实施）

全部放在现有 **“应用包管理”** 根菜单下，沿用当前 JEECG 页面样式：

| 子菜单/入口 | 安排 |
| --- | --- |
| 现有应用、渠道、分组、白名单、统计等 | 保留；应用页增加字段权限和自动拉黑入口 |
| 包统计 | 新增独立子菜单，避免和现有应用统计混淆 |
| 全局风控 | 新增子菜单，以标签展示 ASN/品牌/ABI/型号；自动拉黑可从应用页进入 |
| OpenAPI Token | 新增子菜单 |
| OpenAPI 调用日志 | 新增子菜单 |
| OpenAPI 文档 | 新增子菜单 |

后台接口使用独立管理层，例如 `/package-admin/packstat`、`/package-admin/risk/*`、`/package-admin/openapi/*`。公开 OpenAPI 保持源 `/openapi/manager` 协议，通过独立入口与专用 Token 鉴权接入；旧 App/SDK 客户端路径继续走既有 legacy 入口。具体路由是后续设计建议，不代表当前已经存在。

后端继续隔离在 `jeecg-module-package`：`controller/admin`、独立公开 OpenAPI controller/interceptor、`application/packstat`、`application/openapi`、`application/apps/risk`、对应 domain/Mapper 与基础设施适配器。邮件、外部 ASN、Redis、调度采用目标的端口/适配层；数据库使用 `pkg_` 前缀，Redis 使用模块命名空间。不能把 RuoYi 的用户、菜单、登录代码整体塞进 JEECG 系统模块。

推荐顺序：先对齐字段权限和原商店检测异常语义，再补风控管理闭环；随后独立补 OpenAPI 与包统计，最后日志展示和停用字段的兼容存储。每批核对接口协议、租户/归属授权、SQL 幂等、前端入口和关键业务分支；包统计特别验证互斥分桶、下架日期、无效响应不更新、邮件去重与调度。

## 8. 新增控制器逐接口清单

以下为源 dev 路径；管理路径迁入时按 JEECG 模块前缀适配。旧客户端路径兼容要求不等于后台 RuoYi 管理路径必须照搬。通配客户端路由即使接到请求，也不视为已实现 OpenAPI/管理业务。

| 功能 | 方法 | 源路径 | 迁移状态 |
| --- | --- | --- | --- |
| 风控管理 | GET | `/system/app2AutoBlacklist/list` | 缺少对应功能实现 |
| 风控管理 | DELETE | `/system/app2AutoBlacklist` | 缺少对应功能实现 |
| 风控管理 | GET | `/system/appAsnBlackList/list` | 缺少对应功能实现 |
| 风控管理 | GET | `/system/appAsnBlackList/lookup/{asn}` | 缺少对应功能实现 |
| 风控管理 | GET | `/system/appAsnBlackList/{id}` | 缺少对应功能实现 |
| 风控管理 | POST | `/system/appAsnBlackList` | 缺少对应功能实现 |
| 风控管理 | PUT | `/system/appAsnBlackList` | 缺少对应功能实现 |
| 风控管理 | DELETE | `/system/appAsnBlackList/{ids}` | 缺少对应功能实现 |
| 风控管理 | GET | `/system/appCpuAbiBlackList/list` | 缺少对应功能实现 |
| 风控管理 | GET | `/system/appCpuAbiBlackList/{id}` | 缺少对应功能实现 |
| 风控管理 | POST | `/system/appCpuAbiBlackList` | 缺少对应功能实现 |
| 风控管理 | PUT | `/system/appCpuAbiBlackList` | 缺少对应功能实现 |
| 风控管理 | DELETE | `/system/appCpuAbiBlackList/{ids}` | 缺少对应功能实现 |
| 风控管理 | GET | `/system/appDeviceBrandBlackList/list` | 缺少对应功能实现 |
| 风控管理 | GET | `/system/appDeviceBrandBlackList/{id}` | 缺少对应功能实现 |
| 风控管理 | POST | `/system/appDeviceBrandBlackList` | 缺少对应功能实现 |
| 风控管理 | PUT | `/system/appDeviceBrandBlackList` | 缺少对应功能实现 |
| 风控管理 | DELETE | `/system/appDeviceBrandBlackList/{ids}` | 缺少对应功能实现 |
| 风控管理 | GET | `/system/appDeviceNameBlackList/list` | 缺少对应功能实现 |
| 风控管理 | GET | `/system/appDeviceNameBlackList/{id}` | 缺少对应功能实现 |
| 风控管理 | POST | `/system/appDeviceNameBlackList` | 缺少对应功能实现 |
| 风控管理 | PUT | `/system/appDeviceNameBlackList` | 缺少对应功能实现 |
| 风控管理 | DELETE | `/system/appDeviceNameBlackList/{ids}` | 缺少对应功能实现 |
| 四段客户端 | POST | `/{q}/{w}/{e}/{r}` | 已迁移 |
| OpenAPI | GET | `/system/openapi/calllog/list` | 缺少对应功能实现 |
| OpenAPI | GET | `/openapi/manager/list` | 缺少对应功能实现 |
| OpenAPI | GET | `/openapi/manager/{id}` | 缺少对应功能实现 |
| OpenAPI | POST | `/openapi/manager` | 缺少对应功能实现 |
| OpenAPI | PUT | `/openapi/manager` | 缺少对应功能实现 |
| OpenAPI | DELETE | `/openapi/manager/{ids}` | 缺少对应功能实现 |
| OpenAPI | GET | `/system/openapi/token/list` | 缺少对应功能实现 |
| OpenAPI | GET | `/system/openapi/token/{id}` | 缺少对应功能实现 |
| OpenAPI | POST | `/system/openapi/token` | 缺少对应功能实现 |
| OpenAPI | PUT | `/system/openapi/token` | 缺少对应功能实现 |
| OpenAPI | DELETE | `/system/openapi/token/{ids}` | 缺少对应功能实现 |
| 包统计 | GET | `/system/packstat/list` | 缺少对应功能实现 |
| 包统计 | GET | `/system/packstat/authors` | 缺少对应功能实现 |
| 包统计 | GET | `/system/packstat/regions` | 缺少对应功能实现 |
| 包统计 | GET | `/system/packstat/surviveSummary` | 缺少对应功能实现 |
| 包统计 | GET | `/system/packstat/{id}` | 缺少对应功能实现 |
| 包统计 | POST | `/system/packstat` | 缺少对应功能实现 |
| 包统计 | PUT | `/system/packstat` | 缺少对应功能实现 |
| 包统计 | DELETE | `/system/packstat/{ids}` | 缺少对应功能实现 |

## 9. 可复核附件及验证说明

同目录 `dev-branch-gap-audit.json` 记录源/目标完整提交号、193 个文件的差异清单与处置类别、43 个新增接口的源文件/声明行号、6 张缺失表。文件类别只用于分组，不把测试/文档或停用代码算作线上缺口，也不把“已迁移”解释为未经验证的逐字一致。

本次检查了分支提交、差异统计、快照与 main/dev 对应关系、新增控制器路由、5 个新增 Vue 页、表定义及目标代码对应物，并核对已迁四段说明和目标运行依赖。目标源码检索未找到 OpenAPI/packstat 对应实现或 6 张表定义。环境配置、IDE 文件、构建 ZIP、开发技能及源框架初始化 SQL 删除不属于必须搬入的业务功能。

本次仅生成报告和清单，没有执行应用回归测试；此前迁移测试结果属于此前代码验证，不能据此宣称 dev 缺口已实现或通过验收。
