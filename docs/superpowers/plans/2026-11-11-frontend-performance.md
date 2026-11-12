# 前端切换性能实施计划

> 按 executing-plans 在当前会话逐项执行；需求以用户已确认的 `docs/baseline/frontend-performance-investigation-2026-11-11.md` 为准。

目标：修复包管理列表反复挂载、关闭多余 loading 等待，并统一控制 AI 功能及按需加载。

架构：保留后端菜单驱动权限。组件显式名称与菜单名称一致，缓存通过新增菜单 SQL 开启；数据通过激活时效刷新。AI 使用构建时开关，Online 继续独立注册。部署优化只修改可审阅模板，不发布线上。

技术栈：Vue 3、Pinia、Vue Router、Vite 8、Node test runner。

约束：保留已有用户改动；当前 `codex/package-api-migration` 分支原地实现；不升级依赖、不批量格式化、不推送、不操作生产数据；原始 SQL 不改写。

## 1. 缓存与 loading

- [x] 先添加 `tests/frontend-performance.test.mjs`，用真实 Vue 编译器及自定义 renderer 验证 A→B→A 的挂载次数、状态保留、身份变化和刷新时效，并检查缓存上限配置；用路由内存 history 验证并发/失败导航的 loading。
- [x] 对包管理可缓存页面显式声明与菜单相同的组件名称；为对应精确菜单 ID 添加增量 SQL `V20261111_06__package_page_cache.sql`，仅修改 keep_alive/component_name。
- [x] `src/layouts/page/index.vue` 限制 KeepAlive 为 12 个实例，并在身份/租户变化时清空缓存。普通路由仍保留 fullPath key，避免改变带参数页面语义。
- [x] `src/hooks/web/useCachedPageRefresh.ts` 与纯行为模块负责首次不重复刷新、返回过期页刷新、并发去重；接入 PackagePage 和 DevRecordPage，保存后沿用原刷新逻辑。
- [x] `src/router/guard/pageLoading.ts` 单独管理导航 loading，导航结束立即清除 store 的延迟开启任务，并防止旧导航关闭新导航 loading。仅成功导航记 loaded。

## 2. AI 与业务依赖

- [x] 测试 AI 关闭时过滤 AI 菜单而保留其他菜单、启用时保留原行为。
- [x] `.env` 默认 `VITE_ENABLE_AI=false`；`src/settings/features.ts` 统一开关；registerSuper 分开注册 Online/聊天，staticRouter 和 useRootSetting 同步受控。
- [x] dynamicPages 分开普通与 AI glob，生产禁用时裁剪 AI 页面；copyChat 按相同 mode/environment 判断是否复制。
- [x] PackagePage 的 AppEditor、PwaEditor、PackageField 改为异步组件，弹窗使用时加载。转移弹窗保留同步 ref，先验证首次点击安全；若改异步必须补加载后打开的测试。
- [x] 删除 registerThirdComp 中未使用的同步重量级导入，通过构建产物检查 TinyMCE、表情库为何进入入口，再针对证据调整 chunk 边界。

## 3. 资源缓存与验证

- [x] Nginx 模板添加 gzip/gzip_static，仅 `/js/`、`/assets/` 内容哈希资源长期缓存，保留 index、运行时配置和 SW 更新语义。
- [x] SW 对哈希 JS 使用 CacheFirst，收窄预缓存到入口基础资源，避免离线预取全部重量级 vendor；不缓存业务 API。
- [x] 执行 `node --test tests/*.test.mjs tests/business-identity-behavior.mjs`，运行完整 `pnpm build`，保存前后预加载统计；验证开启 AI 的构建入口可用。
- [x] 如本地服务可启动，验证真实权限菜单与页面；否则通过真实 Vue/Router 行为测试和生产构建验证并明确浏览器/线上指标边界。
- [x] 更新方案实施记录、菜单 SQL 应用/回退说明及实际验证结果。

## 执行结果

- 全套 Node 测试 107 项通过；缓存、刷新、导航、请求乱序及真实 Rolldown 分包行为已验证。
- AI 开启/关闭构建均通过；默认关闭产物无 AI 页面入口和 chat 挂件目录，Online 保留。
- 两份 Nginx 模板已通过官方 Windows Nginx 的本地语法检查；生产副本去除了生产证书指令，目标服务器仍需按真实路径/证书执行 nginx -t。
- 菜单迁移只应用于本地测试库并备份；生产未操作。
- 浏览器自动化受 URL 识别限制而停止；启动本地前端服务被自动审批策略阻止，因此未测页面切换耗时。真实权限菜单接口、Vue/Router 行为和构建产物已完成替代验证。
- 实施数据见 docs/baseline/frontend-performance-investigation-2026-11-11.md 第八节，部署与回退见 docs/deployment/frontend-performance-2026-11-11.md。
