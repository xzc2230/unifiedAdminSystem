# 前端加载排查与优化方案

排查日期：2026-11-11。用户确认的主要症状：登录后页面切换慢。

## 结论与证据边界

优先解决业务页面缓存失效和切换遮罩，再处理首次访问的依赖加载与聊天模块裁剪。删除聊天路由不能替代切换性能优化。

本次已检查当前源码、菜单 SQL、本地历史菜单响应、Vue 编译结果和现有 dist；未修改业务代码、数据库或部署配置。本地 `127.0.0.1:13100` 连接被拒绝，因此没有复现登录后的实际点击，没有得到线上接口耗时或浏览器性能轨迹。以下区分确定的代码行为与仍需运行验证的影响。dist 的 index.html 修改时间为 2026-11-10 12:37，本报告的产物数据不能代替当前提交重新构建的结果。

## 一、为什么登录后切换慢

### 1. 包管理页面缓存关闭，返回页面会重新挂载

证据链：

- `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/db/package/V20261109_04__package_menus.sql` 中，应用管理、请求日志等菜单的 `keep_alive` 为 0。
- 历史 `.runtime/package-permission-menu.json` 中，这两个页面的 `meta.keepAlive` 也为 false。这是历史响应，当前数据库状态仍需实时核验。
- `src/router/helper/routeHelper.ts` 将它转换为 `ignoreKeepAlive = !meta.keepAlive`。
- `src/store/modules/multipleTab.ts` 的 `updateCacheTab()` 跳过这些页面。
- `src/layouts/page/index.vue` 的 KeepAlive 使用该缓存名单。
- `src/components/Table/src/hooks/useDataSource.ts` 在挂载后发起列表查询；`src/views/packagemanagement/components/PackagePage.vue` 在挂载时读取字典，统计页另外发起汇总查询。

结果：在这些配置下，A→B→A 会重新创建 A 的实例，列表请求也会重新发出，筛选和分页状态可能重置。字典工具已经优先读本地缓存，只有未命中才请求网络，不能把每次读取字典都算成一次 HTTP 请求。

### 2. 只开启 keep_alive 仍不能解决全部缓存问题

用项目实际安装的 `@vue/compiler-sfc` 对以下两个单文件组件执行 `parse + compileScript`：

| 页面 | 当前编译推断名 | 菜单/路由名 |
| --- | --- | --- |
| 应用管理 | index | Packageapps |
| 请求日志 | index | Packagerequestlogs |

两者都没有显式声明组件名称。当前多标签缓存名单使用路由名，KeepAlive 的 include 按组件名匹配，因而还存在名称不匹配问题。其他业务页面需要逐项检查，不能假定全部相同。

`PageLayout` 同时使用 `route.fullPath` 作为实例 key：查询参数变化会产生不同实例。普通列表页应评估是否使用稳定的页面 key；详情页仍须保留记录 ID 等必要维度，不能全局粗暴改成 `route.path` 或路由名。

### 3. 页面完成导航后，仍固定等待 220ms 才关闭遮罩

`src/router/guard/index.ts` 的 `createPageLoadingGuard()` 在 afterEach 中使用 `setTimeout(..., 220)` 关闭页面 loading，页面 loading 默认开启。

这增加遮罩可见时间，并不代表接口本身慢了 220ms。已访问页面的 beforeEach 会跳过开启遮罩，因此不能解释为每一次切换都固定慢 220ms。store 另有 50ms 延迟开启逻辑，快速连续导航时，还应验证前一次关闭任务是否误关后一次 loading，并补齐导航失败时的清理。

### 4. 首次进入业务模块存在较宽的同步依赖边界

`PackagePage.vue` 同步导入 PwaEditor、AppEditor、AppSharingModal、PackageField。请求日志等只读页面也依赖同一通用页面模块，所以首次访问仍需解析这些同步依赖，尽管没有打开编辑弹窗。

这是可缩减的依赖范围，尚未通过浏览器测得它在实际切换中的耗时占比。`PackagePage` 自身现有压缩后 JS 约 19.6KB，不能把整个系统变慢归因于这个文件的自身大小。

### 5. 暂未发现普通切换必然反复获取权限菜单

权限守卫在 `getIsDynamicAddedRoute` 为 true 时直接返回；后台菜单正常只在动态路由初始化时构建。不能未经测量把“每次切换重新拉权限”作为根因。若运行时每次都发生，需要追踪谁重置了权限 store。

页面主体的 transition 模板已注释，因此当前不应优先通过关闭切换动画来解决问题。

## 二、聊天路由为什么存在

启动链路：

```text
src/main.ts
  → await registerSuper(app)
  → import.meta.glob('./**/register.ts') 并逐项执行
  → src/views/super/airag/aiapp/chat/route/register.ts
  → router.addRoute(...)
  → 输出“[聊天路由] 注册完成！”
```

它是上游 JEECG AI 模块自带的路由注册逻辑，与本项目 `services/site-router` 的域名/路径分流功能无关。

该注册文件声明了 `/ai/app/chat/:appId`、`/ai/app/chatIcon/:appId`、`/ai/chat`、`/ai/chat/:appId`、`/ai/chat/portal` 等路由。另外 `src/router/routes/staticRouter.ts` 通过 `AI_ROUTE` 添加 `/ai`。

`src/settings/projectSetting.ts` 中的 `aiIconShow: false` 只控制图标展示，不控制上述路由注册。前两个对外聊天路由还设有 `ignoreAuth: true`，是否开放必须按产品用途统一管理；后端鉴权仍需独立保留。

聊天组件使用动态 import，所以注册路由和下载完整聊天页面是两个不同阶段。仅看控制台日志，不能判定聊天页面已全部加载，也不能判定它是登录后切换慢的主要原因。

现有生产 Nginx 模板已经阻断 airag 接口，但前端路由依然注册，反映出前后端功能开关没有统一。

## 三、现有生产产物还有哪些加载负担

只读扫描现有 `dist/index.html` 的 modulepreload 链接，并统计对应文件实际字节数：

| 指标 | 结果 |
| --- | --- |
| modulepreload 链接 | 207 个 |
| 对应 JS 文件大小合计 | 4,050,735 字节，约 4.05MB，未压缩传输口径 |
| 其中 TinyMCE vendor | 1,094,154 字节 |
| 其中表情库 | 940,377 字节 |
| 其中动态页面映射 | 673,342 字节 |
| dist/js 的 JS 文件总量 | 1,434 个、18,510,934 字节 |

全量 JS 不等于一次页面访问的下载量，modulepreload 大小也不等于实际网络传输量：HTTP 压缩、缓存和预加载行为都需要浏览器验证。TinyMCE、表情库出现在入口预加载列表，说明已有懒加载配置没有达到预期的入口隔离效果，需要重新构建并分析共享依赖及 manualChunks。

此外：

- `src/utils/dynamicPages.ts` 扫描 views 下几乎所有 Vue/TSX 文件，包括业务内部组件。先生成实际页面清单，再收窄 glob，保留后台动态菜单、Online 和外部填报需要的入口。
- `build/vite/plugin/pwa.ts` 的 JS 运行时缓存是 NetworkFirst，设置 3 秒网络回退时间；这不是每次固定等待 3 秒，但弱网和缺失 chunk 场景值得测试。
- `deploy/production/application/nginx-jeecg.conf`、`deploy/aws-single/nginx.conf` 未显式配置 gzip/gzip_static，静态资源有效期为 1 小时。实际环境是否从上层继承压缩配置未知，必须检查响应头；构建生成 .gz 文件并不保证服务端会使用。
- Vite warmup 目前主要覆盖系统、仪表盘、Online、AI 页面，没有覆盖包管理常用页面。仅在实际使用开发服务器且冷切换较慢时，定向加入常用入口；不要预热整个 views。

## 四、方案选择

| 方案 | 内容 | 取舍 |
| --- | --- | --- |
| A：分阶段优化，推荐 | 先修页面缓存、刷新策略和 loading，再关闭不用的 AI 功能及隔离重依赖 | 对现有业务改动可控，直接覆盖登录后切换 |
| B：大幅裁剪上游功能 | A 的基础上移除不用的 AI、演示、编辑器、报表依赖 | 减包空间更大，需要梳理 Online、报表和编辑器的真实使用范围 |
| C：重建轻量后台外壳 | 保留业务 API，替换上游布局、权限、通用组件体系 | 成本与回归范围最大，不适合作为当前首步 |

建议选择 A；确认长期不用的上游模块后，再局部采用 B。

## 五、推荐落地顺序

### P0：先让反复切换真正复用页面

1. 对实际慢的菜单记录“点击→导航完成→页面绘制→列表完成”四个时间点，并抓取接口次数、耗时及组件 mount/activate 次数。至少覆盖应用管理、请求日志、包统计。
2. 为允许缓存的业务路由显式声明稳定且唯一的组件名，与缓存名单一致。以新增迁移修改对应菜单缓存配置，不直接修改已执行过的历史 SQL。
3. 默认缓存应用管理等编辑型列表的筛选、分页和滚动位置；保存成功后刷新对应数据。请求日志等实时性高的页面保留界面状态，并按可见时长/数据失效标记刷新，避免一律永久不刷新。
4. 核查查询参数对应的实例 key 和缓存数量；限制缓存规模。失活时暂停轮询，关闭标签后释放实例。
5. loading 由导航生命周期驱动，保留短暂延迟开启来避免闪烁，移除无条件 220ms 延迟关闭；覆盖连续导航、失败、取消和异常的清理。

### P1：清理聊天入口与首次业务访问的依赖

1. 增加明确的 AI 功能配置，例如构建配置 `VITE_ENABLE_AI=false`。同时控制 registerSuper 的聊天注册分支、静态 AI_ROUTE、布局 AI 入口以及 copyChat 后处理，保留 Online 路由注册。
2. 对后台返回的 AI 菜单同步处理，关闭时直接访问 AI URL 应得到明确的未开放/404 结果，避免菜单指向不存在页面。
3. 禁用运行入口与彻底排除构建分两步验收：仅跳过注册不保证 AI 不进入构建，dynamicPages 的扫描范围及其他静态引用也要同步核查。
4. 编辑器、转移弹窗按使用条件异步加载。异步弹窗先完成组件加载/挂载，再调用 ref.open，避免第一次点击失效；上传、校验和权限行为保持一致。
5. 复用现有字典缓存，在确有重复请求时增加按用户/租户及字典编码区分的并发去重与失败重试，不缓存鉴权失败结果。退出、租户切换和字典更新时使缓存失效。
6. 基于新的构建报告重划重依赖边界，避免 TinyMCE、表情库因共享 chunk 提前进入入口。先验证原因再修改 manualChunks，保留 TinyMCE 的全局初始化顺序。

### P2：部署资源传输和缓存

1. 检查线上静态 JS/CSS 的 Content-Encoding、Cache-Control 和实际传输大小，再补齐 gzip_static 或 gzip；Brotli 取决于部署支持情况。
2. 仅内容哈希资源使用长期 immutable 缓存，HTML 和运行时配置继续保持及时更新；非哈希公共文件和 Service Worker 使用独立缓存策略。
3. 评估后台是否需要 Service Worker。若保留，对哈希 chunk 采用 CacheFirst 并设计版本清理；若关闭，需要迁移卸载旧 SW 并清理本项目缓存，不能只删注册代码。
4. 发布期间保留旧版本 chunk 的合理可用窗口，或提供受控的新版本提示，验证旧标签页继续使用时不出现循环刷新和资源 404。
5. 若用户在 Vite 开发环境使用后台，对确有冷编译耗时的业务页面定向 warmup，并与生产构建对照测量；开发模式的耗时不作为线上验收值。

## 六、验收与回退

建议验收目标，均不是本次已实测结果：在明确记录的同一设备、网络及生产构建下，连续执行 A→B→A 至少 20 轮，分别报告首次访问、热切换与接口加载的分位数。

- 缓存命中的普通列表，点击到内容可见的热切换 P95 目标低于 300ms；接口数据完成另计。
- 缓存有效期内返回列表不重建实例，不重复请求相同列表；主动刷新、保存成功、过期和权限/租户变化仍正确更新。
- 请求日志保持刷新能力；页面切走后旧请求结果不得覆盖新条件的数据。
- 切换时保留既有筛选/分页；退出重登和切换租户后不复用上一个身份的数据。
- AI 关闭时无聊天注册日志、菜单与可访问路由；普通业务访问不请求 AI/表情专用资源。重新启用时验证聊天及其后端权限。
- 编辑、转移、上传和弹窗首次点击可用；Online、外部填报、权限控制保持工作。
- 完整生产构建成功，新旧版本切换无 chunk 404；量化入口预加载数量、原始/压缩字节、长任务及关键页面请求数的前后差异。

变更按缓存/loading、AI 开关与依赖隔离、部署缓存分批交付。缓存配置使用有记录的菜单清单和迁移回退方案；代码和部署模板独立回退。没有浏览器测量前，不承诺具体提速百分比。

## 七、参考

- Vite 官方性能指南：https://vite.dev/guide/performance
- Vue 官方性能指南：https://vuejs.org/guide/best-practices/performance
- Vue KeepAlive：https://vuejs.org/guide/built-ins/keep-alive.html

官方资料用于验证优化机制；本项目的问题判断来自以上源码、编译输出及现有产物证据。

## 八、确认后的实施记录

用户确认采用方案 A 后，已在当前工作区完成以下变更，未提交或发布生产：

- 为 14 个包管理页面设置与后台菜单一致的组件名，新增精确菜单缓存 SQL；KeepAlive 最多 12 个实例，用户、租户或权限菜单变化时重建缓存。
- 普通列表以 30 秒、日志/事件/统计/上架状态以 5 秒为刷新有效期，首次挂载不重复请求，过期返回时刷新，失败可重试。包统计同时更新出包人/地区选项。
- 移除导航完成后固定 220ms 遮罩等待；覆盖旧导航取消、重复导航和加载失败。列表及新鲜度均以最新请求为准，防止旧请求覆盖新筛选结果或关闭新请求 loading。
- 默认 `VITE_ENABLE_AI=false`，统一关闭菜单、静态路由、聊天注册、布局入口、AI 页面 glob、aiflow 包与挂件复制。原聊天路由来源是上游 `registerSuper` 扫描所有 register.ts 后自动调用聊天注册；Online 改为独立注册并保留。
- 包管理编辑器和表单组件改为按需加载；首次打开先等待依赖加载，转移弹窗保留同步 ref。删除两处未使用的 JVxe 同步导入。
- 原 manualChunks 递归纳入依赖，把共享 Vue/runtime 带进 emoji/TinyMCE 包，使普通入口也依赖这些包。改为自动分包，只把 TinyMCE 核心、插件及全局 shim 显式归组，禁止递归纳入共享依赖并保持执行顺序。用真实 Rolldown 最小构建验证入口隔离及 shim 先于插件执行。
- 修复旧 minify 参数警告，采用当前已安装 Rolldown 支持的 `dropConsole`/`dropDebugger`。Nginx 模板添加 gzip 和哈希资源长期缓存；SW 移除重量 JS 预缓存并将哈希 JS 改为 CacheFirst，保留 API NetworkOnly。

构建对比（入口 HTML 的 modulepreload JS，原始文件字节；不是网络下载实测）：

| 指标 | 11 月 10 日基线产物 | 优化后关闭 AI 的产物 |
| --- | ---: | ---: |
| 预加载 JS 数量 | 207 | 142 |
| 预加载 JS 原始字节 | 4,050,735 | 2,677,772 |
| 预加载中 TinyMCE/表情专用包 | 存在 | 无 |
| JS 文件总数 | 1,434 | 1,065 |
| AI 页面构建入口 | 存在 | 0 |

预加载 JS 原始体积减少约 33.9%；优化后逐文件 gzip 合计 776,290 字节，仅作压缩估算。Service Worker 预缓存 8 项、约 65.7 KiB。基线 gzip 未重新测量，不推断传输提速比例。

验证记录：

- Node 全套 107 项通过，包括真实 Vue wrapper 缓存、租户变化、刷新时效、初次查询去重、请求乱序、真实 Router 导航取消/失败及构建分包执行顺序。日志：`.runtime/frontend-performance-final-tests.log`。
- AI 关闭的完整 `pnpm build` 成功；AI 开启的独立 Vite 构建成功，包含 33 个 AI 页面/组件入口，聊天注册和静态 AI 页面均存在。挂件复制 true/false 分别验证成功。开启构建位于 `.runtime/frontend-ai-enabled`，不覆盖默认关闭 AI 的 dist。
- 本地测试库 14 条菜单迁移已应用并备份；真实认证菜单接口 HTTP 200，当前账号可见的 13 个缓存页面返回 `keepAlive=true` 及正确路由名，包统计不在该账号授权菜单中，未新增授权。
- 官方 Windows Nginx 1.28.0 对两个模板的本地副本 `nginx -t` 通过；生产模板副本仅移除 TLS 证书指令并将 SSL listen 改为普通端口，以隔离本地没有生产证书的限制。目标 Linux 服务器仍需校验实际模块、路径和证书。检查发现并修复了哈希正则 `{8,}` 需要引号的问题。
- 浏览器自动验证因工具无法可靠确认当前 URL 而停止；启动本地前端服务又被自动审批策略阻止。因此没有浏览器切换 P95、绘制耗时或线上响应头实测，不宣称具体页面提速。测试后端仍为本机 127.0.0.1:18080，无生产操作。
- 构建仍有上游的 clean-css external 提示及 `_app.config.js` 非 module 提示；完整构建退出码为 0。本次没有升级依赖或扩大修改范围。

部署迁移与回退步骤见 `docs/deployment/frontend-performance-2026-11-11.md`。生产生效需要发布前端、应用菜单迁移并让用户重新登录；当前只完成本地工作区与测试库验证。
