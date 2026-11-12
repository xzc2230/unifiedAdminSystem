# 前端缓存与 AI 开关部署说明（2026-11-11）

本次变更针对登录后页面切换。代码及模板已在本地修改，未发布生产。

## 应用顺序

1. 备份目标库 `sys_permission` 中迁移 SQL 涉及的 14 行，保留 `id`、`keep_alive`、`component_name` 原值。
2. 由已有迁移流程执行 `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/db/package/V20261111_06__package_page_cache.sql`。发布脚本不自动执行此 SQL；它仅更新精确匹配的包管理菜单，不授予角色权限。
3. 默认构建配置是 `VITE_ENABLE_AI=false`。按目标 mode 构建完整前端 dist；部署时替换目录，避免残留旧版 `chat/`。若启用 AI，须同时检查后端权限及 Nginx 的 airag 拒绝规则，前端开关不会开放后端接口。
4. 采用对应 Nginx 模板中的 gzip、哈希资源 immutable、HTML/config no-store、SW no-cache 配置。生产机先执行 `nginx -t`；`gzip_static` 需要相应模块。不要把本地去除 TLS 证书路径后的语法检查等同于目标服务器完整配置验收。
5. 用户重新登录，或刷新页面重新构建权限路由。旧标签页不会自动取得菜单缓存配置。发布时保留上一版本哈希 chunk 的可用窗口，避免旧标签页动态加载资源 404。

## 验证

- 普通包管理页 A→B→A：保留筛选和分页；30 秒内不重复查询，超过有效期自动查询。请求日志、事件、统计及上架状态有效期为 5 秒。包统计会先更新出包人/地区选项，再刷新列表。
- 新增、编辑、转移首次打开可用，保存成功刷新；快速连续筛选时旧请求不覆盖新结果。
- 用户、租户或权限菜单重建后，丢弃旧页面缓存；最多缓存 12 个页面实例。
- 关闭 AI 后：菜单、静态路由、聊天动态注册及布局入口均关闭；Online 独立注册。构建清单不含 AI 页面入口，dist 不含 chat 目录。
- 资源响应需检查 `Content-Encoding` 和 `Cache-Control`；业务 API 不进入 Service Worker 缓存。构建产物大小不能直接换算成页面切换耗时。

## 回退

恢复上一版前端及 Nginx 配置，校验配置后重载。按备份中的具体 ID 恢复菜单的 `keep_alive` 和 `component_name` 原值，再重新登录。不要统一把所有菜单缓存设为 0，也不修改角色授权。

本地测试库已有迁移前备份：`.runtime/frontend-performance-menu-before.json`，精确回退脚本为 `.runtime/frontend-performance-menu-rollback.sql`。两者仅适用于本次本地数据库，不能直接用于生产。
