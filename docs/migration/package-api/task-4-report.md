# Task 4 前端与单根菜单交付

日期：2026-11-09。实施状态：**DONE（代码与清单完成）；生产构建和真实浏览器验证 BLOCKED（环境子进程限制），不声称整体发布验收通过**。未提交 Git，未修改源 PackClient、siteplatform、用户 README 或 AWS 文件。

## 范围与文件

- `upstream/JeecgBoot/jeecgboot-vue3/src/views/packagemanagement/`：13 个独立页面入口；`package.data.ts` 提供逐业务字段、查询和权限定义；`components/PackagePage.vue` 使用当前 BasicTable/内置 BasicForm 搜索、Ant Form、modal/drawer。PWA/W2A 使用实际 `PwaEditor.vue`，不以通用 CRUD 取代业务编辑器。
- `src/api/packagemanagement/index.ts`：当前 defHttp、租户/token/sign 注入；管理命名空间 `/package-admin`；`records/total` 与 `pageNo/pageSize`；创建 POST、修改 PUT、删除 DELETE、导出 POST。导出及清理筛选显式拼接 query，避免 defHttp 将 params 转为 JSON body 后服务端丢失筛选。
- `src/types/packagemanagement/index.ts`：记录、分页、字段与页面定义。ID 为 string/number，不将 ES 字符串 ID 转为数字；app-events/reviews 分别使用 eventId/reviewsId。
- `src/views/packagemanagement/package-conversions.mjs`：可直接测试的标签、评分、地区、日期、图片及菜单转换。
- `src/store/modules/permission.ts`：仅在 BACK 分支调用包管理根菜单纯函数，再执行既有路由变换；其他根菜单对象不改变。
- 新后端模块 `src/main/resources/db/package/V20261109_04__package_menus.sql`：1 根、13 子菜单、66 按钮，共 80 行。稳定 ID 与 upsert，可重复执行；不写角色授权表。W2A 与 PWA 共用 pwa 权限，store 无 export。
- `frontend-targets.json`：261 个源文件逐项映射，含 source/targetFiles/disposition/reason/sourceActions/verification。基础能力采用 JEECG 对应文件并明记模型与协议差异，不把 119 条基础接口复用声称为原协议等价。12 个源业务页面重建并增加独立 reviews 入口。

## 源功能到目标操作

所有业务入口位于 `src/views/packagemanagement/<下列路由>/index.vue`；字段定义、列表/编辑操作位于上述 data/Page 文件，特殊功能另列。

| 源页面 | 目标路由 | 保留的活跃行为与核验 |
| --- | --- | --- |
| system/manager | apps | 包名查询；应用名、作者、包名、URL、开关、白名单、地区、禁美国、自用、备注；地区数组/逗号串双向转换；修改包名锁定；仅提交字段白名单。转移经真实 `/apps/transfer-targets` picker，提交 ids/targetUser，不提交旧 numeric userId/userName。地区转换单测通过。 |
| system/store | store | 包名/0、1、2 状态筛选；状态编辑；过审/下架邮箱；日期只读；按包名生成 Google Play 市场链接；导出不显示。后端白名单测试通过。 |
| system/statistics | statistics | 包名、隐藏地区查询；独立汇总查询表单和 `/getTotal`，不取分页求和；金额两位；创建可填包名、修改锁定；保留主链接、分流、隐藏地区/链接；注册、充值次数与金额只读。 |
| system/event_config | event-configs | 应用/包名/FB token/pixel/落地页 URL；源必填校验；查询、CRUD、详情、导出。 |
| system/history | event-history | 事件、归因、地域、设备、应用、原始 URL、JSON 内容、币种及收入；按最终 Registry **39 个可编辑字段**逐字段对照测试（含 selected_currency/event_revenue_usd），非缩略模板；查询、详情、CRUD、导出；无 `${comment}` 标签。 |
| system/error | event-errors | 包名查询，错误消息/内容，CRUD、完整详情与导出。 |
| system/pwa | pwa | 固定 appType=0；应用、图标、五图、描述、语言、推广子域名、load_host 后缀、包网 URL、事件配置、下载数、JSON 标签/评分、评论数与评论集合。PwaEditor 实现实际校验与编辑。 |
| system/w2a | w2a | 固定 appType=1，同 pwa API/权限域，增加必填 downloadUrl；独立入口。 |
| system/count | landing-counts | 应用名/日期查询，附加域名查询；应用名/域名/日期/访问/下载编辑；CRUD、详情、导出。 |
| system/record | app-events | 应用 ID/域名/事件类型及名称/设备/IP；eventId 主键；所有权/审计字段不编辑；CRUD、详情、导出。 |
| system/applog | request-logs | 包名与开始/结束时间筛选；ES total 分页；请求参数/头/响应/异常完整抽屉详情；批删、清理、导出使用当前筛选；保留源未开放新增/修改按钮的状态。 |
| system/notify | notifications | 当前用户有权访问的 PWA（appType=0）选择；检查 package:pwa:query；展示应用名/图标/域名；标题/消息/图片；新增明确发送语义，只在真实响应成功后提示。无可用应用显示空状态。 |
| api/system/comment_record（源无独立页面） | reviews | 独立评论管理；有权访问的父应用选择；头像/姓名/内容/评分；父归属由后端校验；创建时间只读，内嵌评论保留编辑日期。 |

各业务表格包含查询重置、分页、行详情、权限受控编辑/删除和已支持的批量/导出操作。详情显示完整返回字段，不截断请求/事件内容。界面错误不构造模拟成功响应。

## PWA/W2A 及图片的具体语义

- load_host 的 value 用作 appSubHost，label 原样保存为 appSub；country_name/event_name/app_status/app_where/app_area 使用宿主字典读取。缺少字典时显示明确空状态，域名不硬编码；缺失域名选项禁止保存。
- tags 保存 JSON 字符串数组，至少一个非空标签。score 保存 score1..score5 JSON 对象；严格按 1..5 权重求均值、1 位小数；零总数显示 0 并阻止提交。
- 至少一条评论，每条须有 name/content/headPortraitUrl/createTime；评论日期使用本地 `yyyy-MM-dd HH:mm:ss`；已有 reviewsId 与用户所选日期保留。已持久化评论删除立即调用 reviews DELETE，明确提示取消主弹窗也不恢复，并检查 remove 权限。
- `PackageImageUpload.vue` 使用当前 Ant Upload/预览风格，独立适配 `/package-legacy/common/upload`；成功时保存旧响应 `fileName`，即 `/profile/upload/...`，多图逗号拼接。历史 `/profile/...` 预览映射为后端 `/package-legacy/profile/...`；绝对 URL 原样。图标、五图、评论头像、通知图片及列表预览均已接入；未修改宿主全局 JImageUpload 行为。单文件 10MB、图片类型检查与错误提示。初版默认宿主上传与旧存储契约不一致，已在根代理审查后修复并新增测试。

## 实际验证

在前端目录执行：

```powershell
$env:NODE_PATH='D:/workspace/JEECG/upstream/JeecgBoot/jeecgboot-vue3/node_modules/.pnpm/node_modules'
node --test --test-isolation=none tests/package-management.test.mjs
```

结果：**9 tests / 9 passed / 0 failed / 0 skipped**。包含转换/校验、菜单全量/部分/空权限、图片旧契约、17 个 Vue SFC 的 script/template 编译、13 页面/最终后端 editable allowlist、稳定菜单 ID/无自动授权。最初测试因转换模块尚未存在而失败；图片新增测试亦先因函数未实现失败，补齐后通过。默认 node:test 子进程执行遇到 EPERM；使用官方同进程测试选项完成测试，无修改系统限制。

在仓库根执行：

```powershell
node docs/migration/package-api/verify-coverage.mjs
```

结果：235 source paths、99 migrated、119 foundation、261 frontend、24 helpers、12 tables 全部清单与文件存在性校验通过。根代理另行执行了含 330 源指纹的校验并报告通过。

根代理联调证据（由根代理维护最终记录）：隔离数据库菜单 SQL 两次导入保持 80 行/13 子页，实际权限响应返回 1 根 13 子；apps 的 Vite HTTP transform 200；后端独立真实 HTTP 共 35 项通过。这些不替代交互浏览器检查。

根代理随后新增 `.runtime/package-frontend-http-report.json`：35 项前端相关 HTTP 检查全部通过，覆盖真实 1 根 13 子权限菜单、全部模块 Vite HTTP transform、13 子页对应的 list API 与 PWA/W2A 分型。仍不包含浏览器渲染或生产构建。

生产构建尝试：

```powershell
$env:NODE_PATH='D:/workspace/JEECG/upstream/JeecgBoot/jeecgboot-vue3/node_modules/.pnpm/node_modules'
$env:ESBUILD_BINARY_PATH='D:/workspace/JEECG/.runtime/esbuild-official/package/esbuild.exe'
pnpm.cmd build *> D:/workspace/JEECG/.runtime/package-task4-build.log
```

实际失败在 Vite externalize-deps 的 Windows realpath 子进程：`spawn EPERM`，尚未进入应用生产打包。正规提升权限申请被自动审批拒绝，理由称只读环境且 never 禁止提升；未绕过或反复申请。根代理复核 esbuild 子进程同样 EPERM。**不能写“生产构建通过”**。

真实浏览器同样由根代理实际启动后遇到 `spawn EPERM`，日志 `.runtime/package-ui-smoke.log`。现有 Vite 服务的 HTTP 编译可读检查与 SFC 编译不等于真实页面点击、上传和 CRUD 联调。待环境允许子进程后，必须完成生产构建与用户角色下的 13 页交互验收，尤其图片上传/回显、PWA 内嵌评论、独立汇总、转移与导出。

## 限制与交接

代码实施与静态/组件验证完成；构建和真实浏览器是明确未完成的验收项。没有新建生产字典值、没有生产部署、没有面向所有用户授权。真实可用域名与业务字典必须由环境配置提供；本地测试字典只能使用隔离 `.invalid` 值。源主题/工具/基础页面采用宿主工作流，具体差异在 261 清单中记录，不承诺旧路由逐字兼容。

## Task 4 独立审查修复

2026-11-09，独立审查提出 4 项 Important，现已统一修复，等待复审：

1. `PackagePage.vue` 的 bodyCell 仅渲染明确的业务字段与 action，不再对所有列输出兜底 text。宿主插入的选择列没有 dataIndex、依赖 customRender，先前兜底会覆盖复选框并泄漏整条记录文本；序号列同样需要保留默认 renderer。新增回归直接读取**生产页面实际 bodyCell 模板**，用 Vue compile 交给当前安装的真实 Ant Table 与 Checkbox 执行 SSR，检查复选框、序号 customRender 和业务文本均存在，且没有整条记录 JSON。修复前实际复现 checkbox 缺失，修复后通过；不是仅检查正则，也不是手写另一份修复 slot。SSR 是组件契约验证，不替代真实浏览器。
2. `landing-counts` 恢复源 appName/time 查询，hostName 保留为附加条件，使后端能按 appName 解析 PWA。字段契约测试增加 appName/time 筛选断言。
3. apps 的 url 恢复 required 校验，字段契约测试增加 required=true 断言。
4. PWA 评论删除不再在 await 后使用旧数组下标。`createReviewRemover` 在提交时捕获评论对象/ID，响应完成后按身份查找并移除；pending Set 防止同一评论重复请求，按钮显示 loading/disabled。失败保留原评论并释放 pending 以允许重试。新增异步回归同时删除 A/B、重复点击 B、先完成 A 再完成 B，确认 C 不被误删；另一例验证失败保留及随后成功重试。

验证命令（前端目录，普通权限，同进程）：

```powershell
$env:NODE_PATH='D:/workspace/JEECG/upstream/JeecgBoot/jeecgboot-vue3/node_modules/.pnpm/node_modules'
node --test --test-isolation=none tests/package-management.test.mjs
```

实际红阶段：12 tests，8 passed / 4 failed（筛选字段、真实表格渲染、两个新评论删除行为）。修复后：**12 tests / 12 passed / 0 failed / 0 skipped**，含原 17 SFC 编译和全部已有契约。没有重新尝试 Git 写入、生产构建、浏览器或权限提升；先前两个环境阻塞保持如实记录。
