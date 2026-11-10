# AWS 更新部署记录（2026-11-09）

## 管理域名绑定

- 新管理入口：`https://www.attrnivo.com/`；源站 `13.222.19.112`，本次未变更代码发布版本或数据库。
- 用户已在 Cloudflare 更新 DNS 并放行 AWS TCP443。实际 DNS 仍返回 Cloudflare 代理地址，通过独立 ACME 探针确认到达本服务器。
- Certbot webroot 签发源站证书，有效期至 2027-02-07，`certbot.timer` 自动续期，deploy hook 校验并重载 Nginx。
- 服务器 Compose 增加 ACME 和 `/etc/letsencrypt` 只读挂载；Nginx 增加 www HTTP→HTTPS 跳转和 TLS 监听，原 IP HTTP 保持可访问。中间件仍监听回环地址。
- 后端 JEECG_PUBLIC_URL 指向 HTTPS 域名；运行时前端接口基址改为 `/jeecgboot`，服务器及本地发布脚本均保留该设置。运行发布校验和重新生成。
- 变更前配置及运行时前端配置备份：`/opt/unifiedAdminSystem/backups/domain-20261109T080040Z`。
- 公网 HTTPS、源站直接 TLS 证书校验、HTTP301跳转、213个资源、真实管理员登录、用户信息、13个子菜单、租户1000应用列表和受限接口验证通过。日志 `/tmp/attrnivo-verification.log`，输出 DOMAIN_VERIFICATION_SUCCESS。
- Certbot 续期演练通过（all simulated renewals succeeded），Nginx 续期重载 hook 手动验证通过；浏览器已正常显示域名 HTTPS 登录页。本地非敏感验收日志保存于 `.runtime/aws-deploy/domain/`。

## 聚合标志修复发布（当前版本，覆盖以下历史记录）

- 提交 `5de44f7c3a673099bc6e89d799f41685d2147121` 已推送并由服务器拉取、构建和发布。
- 发布目录：`/opt/unifiedAdminSystem/releases/20261109T075245Z-5de44f7c3a67`。
- 更新前数据库和配置备份：`/opt/unifiedAdminSystem/backups/20261109T075244Z-5de44f7c3a67`，gzip 完整性通过。
- 在线浏览器刷新后复现目录仍为叶子项，说明上次 LAYOUT 修复不足。实际宿主 `checkChildrenHidden` 会对 alwaysShow=true 返回 false，两个侧栏组件因此不渲染子菜单。
- 本次修复 package 根的前端 alwaysShow 与数据库 always_show，关闭聚合标志，保留 LAYOUT。执行 `repair-menu-aggregation.sql`；无表结构变更。
- 新增真实宿主 helper 和两个菜单组件判断的回归测试：先失败后通过；完整 15 项测试通过。前后端构建成功。
- 五个容器健康；静态资源、管理员登录、ClickHouse、菜单中文、根 LAYOUT、关闭聚合标志以及 13 个子菜单的接口验证通过。租户1000应用列表成功，total=1。
- 日志：`/opt/src/unifiedAdminSystem/.runtime/aws-build/5de44f7/`，记录 REDEPLOY_SUCCESS 及目标提交验证。
- 浏览器最终验收受工具连接超时阻塞（现有页 CDP 连接及新页附着均超时），未声称已在线目视确认展开后的菜单。用户刷新或重新登录后可检查。

## 子菜单修复发布（当前运行版本）

- 分支 `codex/package-api-migration`，服务器拉取并构建提交 `0b3d2fffec7ab64008dfc3dec1bdb0fb53ca3256`。
- 当前发布：`/opt/unifiedAdminSystem/releases/20261109T074710Z-0b3d2fffec7a`。
- 发布前数据库及配置备份：`/opt/unifiedAdminSystem/backups/20261109T074709Z-0b3d2fffec7a`，数据库 gzip 完整性验证通过。
- 无表结构变化；执行仅修复根菜单旧组件值的 `deploy/package-api/repair-menu-root.sql`，前端同步发布根目录 `LAYOUT` 兼容处理。
- 前后端构建成功，前端 14 项回归测试通过，包含实际宿主路由转换及 13 个子节点保留验证。
- 五个服务均健康；管理员登录、213 个静态资源、ClickHouse 查询及公网访问检查通过。
- 实际权限 API 根组件为 `LAYOUT`，包含 13 个直接子菜单；中文标题正确。租户 1000 应用列表接口成功，验证时 total=1，保留现有业务数据。
- 日志：`/opt/src/unifiedAdminSystem/.runtime/aws-build/0b3d2ff/`，已记录 `REDEPLOY_SUCCESS` 和完整发布提交验证。
- 用户需退出重新登录以刷新缓存的动态菜单与路由。以下章节为此前发布记录。

## 后续重新部署（最新）

- 已推送并由服务器拉取 `codex/package-api-migration` 最新提交 `24c1c3ecd12f9cf2a7a1929757d97588eece16bb`，服务器重新构建前后端并通过 Compose 发布。
- 当前发布目录：`/opt/unifiedAdminSystem/releases/20261109T073050Z-24c1c3ecd12f`。
- 本次备份：`/opt/unifiedAdminSystem/backups/20261109T073049Z-24c1c3ecd12f`，包含完整数据库与运行配置；数据库 gzip 完整性验证通过。
- 本次提交修复菜单 SQL 导入字符集，无表结构变化。数据库中 80 项规范菜单及权限名称已全部精确匹配，无需再次修复或重建表。
- 前后端生产构建成功，13 项前端回归测试通过。五个服务均健康；静态资源、管理员真实登录、权限、ClickHouse 及新增业务接口访问限制通过验收。
- 管理员权限 API 返回的根菜单及 13 个子菜单中文标题全部匹配；本机公网检查首页、新增页面、接口鉴权通过。
- 发布日志明确记录 `REDEPLOY_SUCCESS`，验证日志记录完整目标 Git 提交。日志目录：`/opt/src/unifiedAdminSystem/.runtime/aws-build/24c1c3e/`。
- 以下章节保留首次更新记录；最新运行版本以上述后续重新部署为准。

## 发布结果

- 服务器：`ubuntu@13.222.19.112`。
- 仓库：`https://github.com/xzc2230/unifiedAdminSystem.git`。
- 发布分支：`codex/package-api-migration`；提交：`8f606a72be48453703d3561d9be2646bbc1381a8`。本次将当前项目分支推送到 Git 后，由服务器拉取；未合并到 main。
- 服务器源码：`/opt/src/unifiedAdminSystem`，前后端均在服务器构建。
- 应用目录：`/opt/unifiedAdminSystem`，Compose 项目 `unified-admin`。
- 中间件目录：`/opt/middleware`，Compose 项目 `unified-middleware`。
- 当前发布：`/opt/unifiedAdminSystem/releases/20261109T070959Z-8f606a72be48`。
- 上个发布：`/opt/unifiedAdminSystem/releases/20261106T104620Z-81ee2102c884`。
- 访问地址：`http://13.222.19.112/`。

## 数据库变更

此次代码包含 package 模块，数据库需要同步更新，已执行：

1. 更新前停止后端写入，备份数据库、上传文件和应用配置。
2. 确认原库没有 `pkg_` 表后执行 `001-schema.sql`，新增 14 张表；数据库现有 167 张表。
3. 执行 `V20261109_04__package_menus.sql`，安装模块菜单和操作权限（共 80 条权限记录），授予现有 `admin` 角色。
4. 校验全部新增表字段与发布 DDL 一致；保留原有基础表及数据。

迁移 SQL、校验和与执行时间保存在：
`/opt/middleware/migrations/8f606a72be48453703d3561d9be2646bbc1381a8/`。

备份目录：`/opt/unifiedAdminSystem/backups/20261109T070955Z-8f606a72be48`。
数据库 gzip 完整性、SHA256 和迁移 SQL 校验均通过。备份含敏感配置，仅供服务器管理员读取。

## 验证结果

- 后端 11 个模块构建成功，前端生产构建成功。
- 后端 package 测试 166 项：164 通过、2 跳过（未配置独立 MySQL 测试库），无失败；前端 12 项测试全部通过。
- 前端、后端、MySQL、Redis、ClickHouse 五个容器均健康。
- 213 个 JS/CSS 引用、生产接口地址、页面深层路由验证通过。
- 验证码、管理员实际登录、用户信息、动态菜单及 ClickHouse 查询通过。
- 管理员可见新增应用和 PWA 菜单；未登录访问新增接口返回 401。
- 已登录但无旧用户身份映射时返回预期 403，无服务端异常。
- 从本机公网验证首页及新增页面路由返回 200，新增接口鉴权返回 401，旧接口入口返回 403。
- 服务器记录 `UPDATE_SUCCESS`，实际发布提交与目标一致。

服务器构建、测试和发布日志：
`/opt/src/unifiedAdminSystem/.runtime/aws-build/20261109/`。

## 业务启用边界

本次完成代码、表结构和菜单部署，不代表历史业务数据已迁移。原 package 生产 DDL、历史数据及第三方配置未提供，新表结构来自迁移代码。`pkg_user_identity` 未填充虚构映射；完整业务操作仍需真实旧用户映射、资源归属、历史数据以及相关第三方服务配置。旧接口 `/package-legacy/**` 在当前管理入口保持关闭，待专用入口及集成准备完成后再启用。

管理员重新登录可刷新菜单。未修改现有管理员密码。

## 回退注意事项

回退代码可将 `current` 指回上个发布并重新创建应用容器。此次同时调整了应用环境、Nginx 和持久化目录，完整回退需要核对上述备份中的配置。不要直接覆盖当前数据库；如确需数据库恢复，应先停止写入并另做当前备份。新增表包含后续数据时不得直接删除。
