# 归因管理部署入口

完整说明见 `docs/deployment/attribution-runbook.md`。

1. 使用 JDK 21 构建 JEECG 启动模块，并构建 Vue 前端。
2. 备份数据库后，使用 MySQL 8 导入归因模块的 `001-schema.sql` 和本目录 `002-menu.sql`；或运行本目录 `install.ps1`。
3. 在角色授权中添加独立一级菜单“归因管理”和所需按钮权限，重新登录。
4. 导入 `attribution.properties`，配置获准域；普通管理表单可在公开流量关闭时使用。
5. 配置外部 HMAC 密钥、独立主体交付和应用白名单，验证后启用公开入口。

本目录不会默认开放公开流量、自动授权所有角色或自动迁移旧应用。公开接口只通过现有固定的 `/api/v1/attribution/resolve` 接收请求；管理接口均在 `/attribution-admin` 下。

Windows PowerShell 5.1 与 PowerShell 7 均使用脚本中的显式 UTF-8 编码导入，防止中文菜单乱码。密码从当前进程 `MYSQL_PWD` 读取，不作为命令行参数传递。
