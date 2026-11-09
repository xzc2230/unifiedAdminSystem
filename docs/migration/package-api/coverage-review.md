# package-api 迁移覆盖复核与修订结论

日期：2026-11-09。对应主方案 `docs/superpowers/specs/2026-11-09-package-api-migration-design.md`。

## 1. 对上一版的判断

上一版覆盖了 95 个自定义业务映射及主要业务域，但不能宣称覆盖源项目全部接口、全部前端和所有分支：

- 仅对业务控制器计数，缺少所有基础、工具、资源接口的逐项去向。
- 上传、下载和 `/profile/**` 历史资源只有概括描述，缺少具体接口契约。
- 页面只有菜单级范围，未逐项登记 50 个视图和 31 个 API 文件。
- PWA 评分/标签/评论等前端业务规则未充分展开。
- 未明确一个根菜单，也没有独立 Maven 模块边界。
- 只登记了主要业务规则，没有建立可据此声称“全部分支等价”的运行验证结果。

本次已修订设计及静态覆盖清单。**本次成果是范围完整登记和方案修订，不是业务迁移完成或行为等价证明。**

## 2. 全源计数及去向

### 2.1 后端入口

扫描 `PackServer/src/main/java` 所有 Java 文件中的有效 Controller，而非只扫描文件名为 Controller.java 的文件。保留源文件行号，排除注释方法。

| 范围 | 数量 | 去向 |
| --- | --- | --- |
| 有效控制器声明 | 46 | 45 个有处理方法；AppEventApi 为无处理方法的空控制器 |
| 方法级映射注解 | 234 | 用户详情包含两个路径，展开后共 235 条 |
| 自定义管理业务 | 75 | 完整迁移业务能力，接入 JEECG 管理契约 |
| 旧客户端/回调 | 20 | 完整迁移并保持外部旧契约 |
| 资源兼容 | 4 | 本次补入；迁移后合计 99 条注解接口路径 |
| 基础系统/监控 | 119 | 每条登记宿主复用/适配目标，逐项验证差异 |
| 代码生成工具 | 12 | 登记与宿主生成器的差异，不承诺旧模板兼容 |
| Swagger 示例 | 5 | 明确排除生产业务迁移 |

完整证据：`all-source-endpoints.md` 和 `full-coverage-audit.json.endpoints`。原 `source-endpoints.json` 的 95 条仍作为自定义业务基线，不应与全量 235 条相加。

以下是配置或框架注册的入口，**不计入 235 条注解接口**，但有明确去向：

| 入口/配置 | 源证据 | 处理 |
| --- | --- | --- |
| /logout | framework/config/SecurityConfig.java 中 logoutUrl | 改用 JEECG 登出及 Token 失效；旧后台会话协议不列为 App 协议 |
| /profile/** | framework/config/ResourcesConfig.java 中 addResourceHandlers | 保留历史文件及原 URL，或兼容代理映射；必须测试图片/附件访问 |
| Swagger UI / API docs | Springfox、资源映射与安全配置 | 使用当前文档工具；不把样例和匿名文档配置复制到业务入口 |
| Druid 监控 | 源配置及 monitor/druid 页面 | 核对宿主部署是否提供对应监控和权限；不存在时明确差异，不能假定现成等价 |
| 默认错误响应、过滤器入口 | 全局异常处理、Spring Security、CORS、XSS、RepeatSubmit | 兼容接口按原请求和错误契约局部适配，不改变宿主全局行为 |

### 2.2 前端

| 范围 | 数量 | 去向 |
| --- | --- | --- |
| 自定义业务视图 | 12 | 全部在“应用包管理”下重建 |
| 系统/监控/登录/异常等视图 | 30 | 复用宿主对应功能，逐功能验证差异 |
| 空首页 | 1 | 源 index.vue 没有业务内容，使用当前首页 |
| 工具视图 | 7 | 生成器、构建器、Swagger 等登记差异 |
| 业务 API 文件 | 12 | 重建于 src/api/packagemanagement |
| 基础及工具 API 文件 | 19 | 宿主适配或工具差异登记 |
| 公共组件/资源/基础设施 | 180 | 逐文件登记；复用基础设施并核对调用行为和必要资产 |
| src 总文件数 | 261 | 50 个视图 + 31 个 API + 180 个支持文件，无未分类文件 |

支持文件包含 102 个 assets、31 个组件文件、13 个 layout 文件、12 个 utils、7 个 store、6 个 plugins、4 个 directive，以及路由/根入口/配置共 5 个文件。视图清单见 `frontend-coverage.md`，具体方法、导入、活跃字段和逐文件去向见机器可读清单。

该计数覆盖 src，不把 node_modules、编译输出当作迁移源码。根目录环境文件、Vite 配置、public/html/WEB-INF 属于构建部署及框架替换范围；使用当前构建体系，另核对其静态引用和环境变量。服务端引用的 autopwa/autow2a 部署资源在给定仓库未找到，不能声称已覆盖其内容或终端页面逻辑。

## 3. 业务规则与源证据矩阵

下表规定迁移验收必须检查的行为。业务服务/辅助文件 24 个、方法声明 132 个的逐项定位在 `full-coverage-audit.json.logic`；其中包括历史 MySQL 日志实现，不能把方法声明数当作有效功能数或分支覆盖率。

源路径均相对 `PackServer/src/main/java/com/ruoyi`，前端路径相对 `PackClient/src`。

| 业务链 | 源证据 | 需要核对的行为 |
| --- | --- | --- |
| 包列表/新增/更新 | project/app/service/impl/AppManagerServiceImpl.java | 包名查询及判重、创建/更新时间、自动关联商店记录、归属处理、返回错误 |
| 归属转移 | AppManagerController.transfer、AppManagerServiceImpl.transferAppManagerById | 目标用户映射、原管理角色语义、单/批量边界及持久化值 |
| 下发 11 个入口 | ClientController、Client1Controller、Client2Controller、ClientServiceImpl | 每个入口的输入解析、字段、编码、默认结果、地区限制、运营商规则、链接拼接、日志条件 |
| 加密与参数推断 | tool/gen/util/AESUtils.java、ParamsUtils.java | AES 模式/填充/有效密钥；第一个含点参数值的选择及多值顺序 |
| IP 规则 | tool/gen/util/IpAddrUtil.java | country/countryCode/运营商、外部调用失败回退、代理链与原 IP |
| 注册支付统计 | AppStatisticsServiceImpl.uploadData | register/payment 字符串判断、次数累加、BigDecimal 金额、两位小数舍入、失败码 |
| SDK 配置/分流 | AppStatisticsServiceImpl.getDetails | 缺包名/无包/关闭响应、地区匹配、主副链接、随机分流分支 |
| 管理总计 | WebStatisticsController.getTotal | 聚合查询范围、包数/注册/充值次数/金额；不能改为仅当前分页总计 |
| 事件接收 | FBController、EvnetHistoryServiceImpl.fbEvent | 参数绑定、完整字段映射、先写历史、originalUrl 解析、上报条件、异常反馈 |
| Facebook 上报 | EvnetHistoryServiceImpl.fbLog | Token/Pixel、用户信息、币种金额、事件时间和内容、成功也写反馈记录 |
| 页面跳转 | EventConfigServiceImpl.page、IpAddrUtil.passFb | fbclid、User-Agent 条件、IP 判定、默认 Location 和状态 |
| 访问/下载 | LoadCountAccessController、LoadCountServiceImpl | hostname 查找和解析、首次创建、计数、时间、app/user 关联及应用名查询 |
| PWA/W2A 保存 | AppPwaServiceImpl.insert/update/delete | appType、推广地址重复判断、Snowflake appCode、外部 proxy-host 及 linkId、VAPID、评论子表 |
| PWA 公共配置 | PwaController.getInfo | 字段名、banner 拆分、tags 解析、评分/评论、pubKey、空结果和固定更新时间 |
| 订阅 | PwaNotifyController.subscribe、AppPwaSubServiceImpl | appId、endpoint、auth、p256dh 等订阅字段及返回结果 |
| 通知保存与发送 | AppNotifyServiceImpl.insertAppNotify/sendPwaNotify/push | 应用信息快照、订阅选择、事务行为、无订阅错误、发送内容、部分失败和结果 |
| 匿名主动通知 | PwaNotifyController.get | notifyId、pubKey/priKey 输入、订阅范围、错误/成功表现；鉴权改变另行审阅 |
| 应用事件/评论管理 | AppEventRecordServiceImpl、ReviewsRecordServiceImpl | CRUD/导出、主键与 appId 关联、批量行为、日期与文本 |
| ES 日志 | EsAppLogController、EsAppLogServiceImpl、ClientServiceImpl.log | 包名通配、时间范围/时区、倒序、总命中数、导出范围、详情、增改删/清理与记录失败回退 |
| 历史 MySQL 日志 | AppLogController（整体注释）、AppLogServiceImpl、AppLogMapper.xml | 说明历史实现状态；如有历史数据则规划读取/归档，不能当作当前有效 HTTP 入口 |
| 商店状态 | StoreStatusServiceImpl.getStatus | 0→1、1→2、日期、status=2 不再探测、默认和附加收件人、HTTP 探测结果 |
| 任务与 ID/时区 | framework/task/RyTask.ryGetStatus、RuoYiApplication | 业务任务调度定义、异常表现、Asia/Shanghai、Snowflake appCode 不碰撞；迁移局部配置 |
| 通用资源 | CommonController、ResourcesConfig、FileUploadUtils | 单/多文件参数、响应字段、路径、文件名、下载 delete 参数、历史静态文件 |

HTTP 工具、代理登录 Token 缓存、邮件、UUIDv7、NginxTools 等辅助代码全部登记去向，按实际调用链迁移；源代码生成器目录下的业务辅助工具不能因为位于 tool/gen 而一起排除。

## 4. 前端业务规则验收矩阵

| 页面 | 关键方法/证据 | 迁移要求 |
| --- | --- | --- |
| manager | handleAreasToStr、handleAreasChange、handlerTransfer/submitTransfer；admin/statistics 条件 | 保留地区多选转换、开关默认值、转移可见条件、目标人选择和名称；改为后端可信归属校验 |
| statistics | checkMoney、getTotal、queryParams/queryTotal、candisabled | 保留输入两位小数、编辑禁改计数/包名、列表与总计的各自参数范围；不能让搜索意外改变总计口径 |
| store | submitForm、reset、状态字典 | 保留提交时按包名生成商店链接、默认 status=0、附加邮箱及日期显示 |
| pwa/w2a | scoreValue、tags、addTag/tagClose/delAllTag | 评分为各星级人数加权平均，保留一位小数、无数据处理、评分人数 JSON 和标签 JSON |
| pwa/w2a | addComment/deleteComment、submitForm、formatDateTime | 新评论默认分值/日期；头像/姓名/内容/时间必填；已有评论删除立即请求后端，即使最终取消主编辑也可能已删除，不能默默改成保存时删除 |
| pwa/w2a | getSubHost、load_host、appHost/appSub、appCode | 保留字典 value→label 后缀映射、推广 URL 拼接和编码；PWA appType=0/W2A appType=1 |
| pwa/w2a | submitForm、handleUpdate、ImageUpload/ImagePreview | 保留非空标签/评分/评论检查、素材顺序、旧图片访问、编辑时 JSON 还原和评论日期 |
| notify | getPwaList、handlePWANameChange | 应用选择联动、通知图标/内容、成功/失败提示及应用归属范围 |
| applog | 日期范围、handleClean、handleExport | 保留起止参数、详情字段、清理确认、批量删除和导出范围 |
| count/record/config/history/error | 页面活跃模板、methods、API 导入 | 保留实际启用的筛选、分页、字段、详情、增改删与导出；源 ${comment} 残留和注释控件单列，不当作有效产品文案 |
| 所有业务页 | handleSelectionChange、reset、rules、权限指令、download | 保留单选/多选按钮条件、默认值、必填/长度/类型、页码重置、错误提示、文件导出及用户角色语义 |

业务逻辑等价与 UI 风格统一同时验收。基础设施、主题、菜单、HTTP 和 Token 使用 JEECG；不能因为 Element Plus 组件被替换而省掉对应回调及值转换。

## 5. 独立菜单与模块边界

唯一一级目录为“应用包管理”，路由 `/package-management`。其下 13 个子菜单固定为：包配置、商店状态、包统计、事件配置、事件历史、上报日志、PWA 应用、W2A 应用、落地页统计、应用事件、评论管理、消息推送、请求日志。

评论管理是为完整承接已有评论接口而新增的导航入口，源只有 PWA/W2A 内嵌评论。所有编辑和详情仍归各自父菜单；公共 App 接口没有前端菜单。

| 隔离面 | 修订后规范 |
| --- | --- |
| 后端 Maven | jeecg-boot-module/jeecg-module-package |
| Java 根包 | org.jeecg.modules.packagemanagement |
| 架构层 | controller/admin、controller/legacy、dto、application、domain、infrastructure、job、config、exception |
| 管理/兼容前缀 | /package-admin / /package-legacy |
| 前端页面/API/类型 | views/packagemanagement / api/packagemanagement / types/packagemanagement |
| 权限 | package:{domain}:{action} |
| 表/字典/缓存 | pkg_* / pkg_* / pkg:* |
| 配置/部署 | jeecg.package-management.* / deploy/package-api |

新模块不依赖现有 siteplatform/analytics 内部实现，不把迁移控制器放入 system 或 openapi 包。宿主只做装配和必要配置；禁止把迁移逻辑分散写入现有模块。完整依赖方向及目录见主方案第 5 节。

## 6. 明确未证明的内容与交付门槛

当前没有运行旧服务，也没有取得源业务数据库 DDL、部署静态资源及真实外部测试环境。因此不能声称以下事项已验证：全部分支等价、所有基础能力细节等价、旧前端动态菜单在生产是否启用每个视图、外部 Push/FB/代理实际调用成功、历史数据与文件完整性、完整 PWA/W2A 终端页面。

实施交付时以全覆盖矩阵逐项标记“已实现、已验证、复用并验证、明确差异/排除”，每项关联测试或人工验收证据。99 条迁移接口、12 个原业务页面及新增评论导航必须有目标实现；119 条基础能力不得仅凭名称判定等价；工具差异和示例排除必须在交付清单保留。

此次方案范围已补全登记，但不承诺把 RuoYi 框架和所有示例原封不动搬进 JEECG。“业务全迁、基础复用、工具差异明示、旧客户端兼容”是可检查的迁移边界。
