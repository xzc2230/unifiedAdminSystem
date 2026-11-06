# 站点资源模块安装与验收

日期：2026-11-06。此模块交付站点资源登记、目标地址、域名 DNS TXT 验证、成员授权与操作记录。资源启停及域名验证不会切换公网流量。分流执行器、发布版本与统计采集仍按后续阶段建设。

## 安装

1. 备份数据库及原发布包，在预发布环境验证。使用同一次构建的前端与后端。
2. DBA 对现有 JEECG 数据库执行 `upstream/JeecgBoot/jeecg-boot/db/site-platform/V20261106_1__site_resources.sql`。该脚本只适用于首次安装，不可重复导入；失败时先检查已创建的表和菜单再处理，MySQL DDL 不随菜单事务回滚。
3. 脚本新增 5 张 sp_ 表及“站点资源”菜单，默认只给已有 role_code=admin 的角色分配菜单权限，不创建用户、不增加租户成员关系。普通角色需由授权管理员按需分配菜单及 read/create/write/manage 按钮权限。
4. 重启后端并部署前端；重新登录以刷新菜单/权限缓存，进入 `/site-platform/resources`。
5. 从右上角用户菜单的部门/租户切换入口选择已经加入且有效的租户。租户必须状态正常、未删除、在有效期内；用户及租户成员关系也须正常。没有有效租户时应先由系统管理员建立真实租户与成员关系，接口不回退到租户 0，也不自动给 admin 开放跨租户访问。

## 使用

- 创建站点：名称、环境 dev/test/staging/prod、资源启用状态。环境创建后不能修改；创建者自动为站点 ADMIN。
- 目标地址：只登记 HTTP/HTTPS 域名地址，不接受 IP、localhost、凭证或 fragment，不探测 URL。没有实际执行器，不会访问目标地址。后续执行器必须独立校验 DNS、内网地址和重定向，不能把本模块的格式检查当作 SSRF 防护。
- 域名：登记裸主机名，不填协议、端口或路径。大小写、尾点、国际化名称规范化后全局唯一。不能绑定到多个环境；如不同环境需要域名，使用不同子域名。
- DNS 验证：在域名面板查看 TXT 记录，把返回的名称和值配置到 DNS 服务商，再点击验证。挑战 30 分钟过期，可重新生成；重新生成使旧值失效。云 DNS 控制台可能自动附加根域，避免重复后缀。
- 验证成功只记录一次 DNS 控制权，不签发证书、不修改 DNS、不切流。当前尚无证书状态管理和周期性归属复验。
- 成员：填真实用户 ID，并选择 ADMIN/EDITOR/VIEWER。候选人必须是当前租户的正常成员。ADMIN 管理成员，EDITOR 编辑资源，VIEWER 只读；同时还需对应平台按钮权限。管理员不能移除或降级自己。
- 操作记录：按站点显示最近 100 条，无修改/删除入口。不保存挑战值与认证令牌。目前是资源操作记录，不是发布审批链或防 DBA 篡改日志。

## 数据库权限

资源表与系统表沿用两台服务器文档的业务账户。若要数据库层面限制审计只追加，必须先撤销原先库级 UPDATE/DELETE，再按实际系统业务表逐表授权：对 sp_resource_audit 仅 SELECT/INSERT，对其他所需表赋予对应最小权限。MySQL 表级 GRANT 不会抵消已有库级或全局授权。

不要直接在生产撤销全库权限而不准备完整授权清单，这会影响其他 JEECG 模块。上线前用业务账户验证所需功能，以及 `UPDATE/DELETE sp_resource_audit` 被拒绝。当前本机 root 开发账户仅用于本地验证，不代表数据库权限验收通过。

## 接口与隔离

入口为 `/site-platform/sites`（前端代理前缀 `/jeecgboot`）。每个接口要求已登录及对应 Shiro 权限。租户来自 X-Tenant-Id，但会与当前登录用户的真实成员关系核对；不能通过请求体指定 tenant_id。子资源均从 tenant+site+id 定位。

站点列表仅返回本人有站点成员关系的数据。操作权限双重校验：平台权限与站点角色均满足才可执行。首期不暴露批量、导出、站点删除、AI 工具或公开匿名 API。

## 定向验证

PowerShell，在工作区根目录执行，JDK 21 路径及 Maven 缓存沿用基线手册：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path=$env:JAVA_HOME+'\bin;'+$env:Path
mvn -B '-Dmaven.repo.local=D:\workspace\JEECG\.runtime\m2' -f upstream/JeecgBoot/jeecg-boot/pom.xml -pl jeecg-module-system/jeecg-system-biz -am test '-Dtest=Resource*Test' '-Dsurefire.failIfNoSpecifiedTests=false' '-DskipTests=false'
```

默认跳过真实数据库测试。要运行 ResourceRepositoryTest，先设置进程环境 JEECG_RESOURCE_DB_TEST=true 和 JEECG_LOCAL_DB_PASSWORD（取自本项目忽略目录，不输出）。测试连接本机 13316，创建 `sp_test_<随机UUID>` 临时库，结束仅删除该测试库；需要测试账户具备建库权限，不能指向生产。覆盖跨租户/站点查询、写入与成员限制、全局域名唯一及挑战更新。测试不使用正在运行的 JEECG 数据库保存业务数据。

浏览器还需验收：菜单加载、租户切换、创建站点、只读角色、目标及域名面板、DNS 成功与失败、权限撤销后禁止写入。真实域名 DNS 控制权验证需要运维提供可控制域名，不通过修改公共 DNS 制造测试数据。

## 当前验证记录

本轮已通过 21 项单元/真实 MySQL 集成测试，包含排队事务在撤权后不能恢复自身授权/继续写入、真实 DATETIME 类型下的 DNS 成功和过期处理回归。DNS 成功测试使用可控解析器替身；未对真实公共域名执行 DNS 变更或验证。

新增建表与菜单脚本已在本项目开发数据库执行，5 张资源表及 admin 角色的 5 项菜单/按钮权限记录均已确认。前后端完整打包通过；服务重启后前端、后端和代理检查均为 HTTP 200，未登录访问新资源 API 为 HTTP 401。

浏览器已确认站点列表、登记表单、目标地址、域名、成员和操作记录面板可加载；观察到已有站点及创建审计关联正常，未通过验收操作修改该站点、域名、目标或成员。多角色及跨租户场景已由定向测试覆盖，真实账号完整浏览器操作、公共 DNS 和生产审计表最小权限仍需上线前验收。代码复核发现的并发权限快照和 DATETIME 类型问题均已修复并经回归验证。

构建与测试日志保存在忽略目录：`.runtime/site-resources-verified.log`、`.runtime/site-resources-regression-red.log`、`.runtime/site-resources-frontend-build.log`。前端保留上游已有构建警告，不能将打包成功等同于全项目类型检查或生产安全审计通过。
