# package-api dev 补迁实施记录

日期：2026-11-10。源 dev：`0f83f8128df8c9978c49479775f86fa716fb435d`；JEECG 基线：`160d9effa6432b9b0f930fcc16409ada5fd5513a`。本记录承接 `dev-branch-gap-audit.md`，历史审计中的“未迁”描述保留作为补迁前证据。本次实现位于现有 `codex/package-api-migration` 分支，提交记录以 Git 历史为准，尚未部署生产。

## 本轮实现范围

| 审计缺口 | 实施结果 |
| --- | --- |
| 风控管理 23 个路由 | ASN、品牌、CPU ABI、设备型号四类黑名单 CRUD、ASN 信息查询、按应用查看/解除自动拉黑 |
| OpenAPI 11 个路由 | 公开 `/openapi/manager` 五个方法，Token 后台管理五个方法，调用日志查询一个方法 |
| 包统计 8 个路由 | 记录 CRUD、作者/地区选项、月份筛选、互斥存活分桶汇总 |
| 四段客户端 1 个路由 | 沿用既有四段协议实现，没有重复迁移或替换加密协议 |
| 五个前端页面 | 全局风控、包统计、OpenAPI Token、OpenAPI 调用日志、OpenAPI 文档 |
| 六张新增业务表 | 四张 `pkg_pack_stat*` 表和两张 `pkg_app_open_api*` 表 |
| 应用字段权限 | 14 项编辑权限、11 项查询权限；后端比对旧值和响应脱敏，前端只提交改动字段 |
| 原应用管理增量 | 包名 trim/空值检查、事务提交后维护 Redis 镜像、普通更新不修改归属、专用转移 SQL 保留 |
| 商店检测和日志 | 网络异常继续抛出；保留 HTTP 状态、响应头、最多 2048 字节正文诊断；应用请求参数格式化与复制、字典备注提示 |
| 角色会话 | 适配 JEECG 角色编辑/权限保存后的相关用户其他 JWT 会话失效和 Shiro 用户 ID 权限缓存清理，保留当前会话 |
| 停用时区/SIM | 补四个模型/存储字段；保持源端停用状态，没有启用对应风控或编辑界面 |

逐项源/目标方法、路径和 Controller 对照见 `dev-migration-targets.json`，共 43 项，其中本轮新增 42 项，四段协议 1 项沿用既有实现。管理接口改为 `/package-admin/*` 和 JEECG 分页/返回格式；公开 OpenAPI 保留旧客户端路径、参数、Token 和响应信封。

## 菜单与代码层级

五个新页面都直接挂在现有 `pkg_mgmt_root`（应用包管理）下。与原 13 个页面合计 18 个业务子页面；按钮权限不是额外页面。应用页增加自动拉黑弹窗和字段权限联动，沿用当前 Vue3/Ant Design 组件。

后端继续放在 `jeecg-module-package` 的 `org.jeecg.modules.packagemanagement` 包内：

- `controller/admin`：JEECG 管理端；`controller/admin/openapi`：Token 和日志管理。
- `controller/openapi`、`application/openapi`、`domain/openapi`：独立公开 Token 协议、鉴权、审计与业务。
- `application/packstat`、`domain/packstat`、`job/PackagePackStatJob`：包统计及检测调度。
- `application/apps/support`：应用字段权限、缓存和 ASN 查询适配；既有 `application/apps/risk/app2` 承接客户端风控。
- `infrastructure/persistence/mapper` 和对应资源 XML：模块独立持久化；`infrastructure/identity`：宿主身份和会话适配。

数据库沿用 `pkg_` 前缀，应用镜像缓存使用 `package:legacy:app_manager:package:`。没有复制 RuoYi 登录、菜单或用户体系到 JEECG 系统模块。

## 兼容行为与有意保留的目标约束

公开 OpenAPI 使用 `X-Api-Token`，按 `user_id=token.id AND user_name='api'` 限制资源，同时验证 Token 对应 JEECG 用户的有效租户成员关系和资源登记。保留源 `pageNum/pageSize`、`code/msg/data` 或 `code/msg/rows/total`、ISO 日期和日期筛选格式。专用审计 Filter 记录实际响应及原始请求，包含参数绑定失败和畸形 JSON；未知 Token 不落库保存原始令牌。

包统计维持互斥 7～14 天与 15+ 分桶、日期/月口径、404 下架、200/301/302 正常、其他响应/网络错误无效；无效结果不更新业务统计，保留动态间隔、五天历史清理、首次下架通知。修正了源代码中畸形商店 URL 在异常处理外构建请求而中断整轮检测的问题，将其作为无效检测处理。

应用已有包名不可变保护继续生效。字段编辑和查询权限分别采用 `package:apps:editField:<field>` 与 `package:apps:queryField:<field>`；未授予该类细分权限时沿用模块权限回退，一旦授予细分权限则按字段限制。前端对原值做快照，只提交实际改动字段，避免脱敏字段或隐藏地区被清空。

四类全局黑名单沿用源“作用于所有应用”的语义，拥有 `package:risk:*` 操作权限的管理员可维护全局规则。自动拉黑是应用维度，额外校验应用归属。包统计、Token、日志的普通管理查询均限制当前租户和所有者。

## 部署顺序与配置

在已完成原迁移和 `008-four-path-risk.sql` 的环境中，依次执行模块资源目录 `src/main/resources/db/package/` 下：

1. `009-dev-packstat.sql`：四张包统计表、地区种子及默认检测配置。
2. `010-dev-openapi.sql`：Token 和调用日志两表。
3. `011-dev-menu.sql`：五页、操作和字段权限，共 44 条权限记录；前提是原菜单迁移 `V20261109_04__package_menus.sql` 已执行。
4. `012-dev-retained-settings.sql`：应用时区/SIM 四个保留字段。

四份脚本均支持重复执行。`011` 不自动给角色授权：在 JEECG 角色管理勾选五个新页面及所需操作、字段权限，保存后重新登录。缺少菜单授权时，发布前端不会自动让账号看见子菜单。后端 JAR 与前端 dist 应使用同一批代码发布。

包统计定时任务类为 `org.jeecg.modules.packagemanagement.job.PackagePackStatJob`，参考 Quartz cron 为 `0 0/10 * * * ?`。检测频率配置不会自动改 Quartz cron，需同步配置。任务未自动注册/启用。邮件位于 `jeecg.package-management.packstat.mail`，默认关闭，SMTP 参数为空；启用前配置目标环境自己的服务和收件人。详细规则见 `dev-packstat-migration.md`。

本轮未执行生产 SQL、历史导入、生产部署、真实商店巡检或真实邮件发送。若需沿用历史 OpenAPI Token，须保留其 Token 和 ID，显式指定有效租户/用户，并将其历史应用与商店登记在相同资源归属下；不能把 Token ID 直接改成 JEECG 用户 ID。详细约束见 `dev-openapi-migration.md`。

## 验证记录

整模块 Maven 回归：291 项，290 项通过，0 失败、0 错误、1 项跳过；记录 `.runtime/package-dev-verification-final.log`。唯一跳过项是原有 `PackageFileRoutesTest.refusesSymbolicLinkOutsideConfiguredRoot`，Windows 主机不允许创建符号链接，不是本轮缺口功能被跳过。真实 MySQL 集成测试已运行，覆盖包统计 Mapper 自动 XML 绑定、CRUD/归属/互斥桶、OpenAPI Token/日志/锁定读取及原应用历史 ID 持久化。Redis 集成按本地测试配置运行。

前端两套测试合计 22 项全部通过，0 跳过，24 个页面/编辑组件 SFC 编译通过；`npm run build` 退出 0。记录 `.runtime/package-dev-frontend-final-tests.log`、`.runtime/package-dev-frontend-final-build.log`。构建仍有项目现存依赖外部化等警告，未导致失败。

本地隔离 MySQL `127.0.0.1:13317/package_migration_test` 上，009～012 四份 SQL 各执行两次全部成功。查询确认新增 6 表、44 条权限记录、4 个保留字段，根菜单合计 18 个页面；记录 `.runtime/package-dev-sql-local.log`。没有执行生产库操作。

独立审查已覆盖 OpenAPI、包统计、前端/风控及根业务增量；发现的响应审计、日期协议、脱敏字段误回写、畸形 URL、归属转移 SQL 和 Shiro 缓存键问题已修正并加入回归。测试装配错误也已修正：使用现有 `mapper/xml` 目录的 AppManagerMapper 测试须显式加载 XML，不能依赖接口同目录自动加载。

宿主 12 模块打包成功，Maven 退出 0，记录 `.runtime/package-dev-host-package.log`。原输出名被现有后端进程占用，验证时临时使用 `jeecg-system-start-3.9.5-dev-verify.jar`，打包后已恢复宿主 POM，没有保留构建配置修改。

该候选 JAR 已实际在 `127.0.0.1:18083/jeecg-boot` 启动，Quartz 显式关闭，Spring 完整装配成功。真实 HTTP 检查五次通过：公开 `/openapi/manager/list` 缺失/未知 Token 分别返回 HTTP401 及源 `code/msg` 信封；包统计、Token 管理、全局风控三个管理入口无 JEECG 登录令牌时均被宿主以 HTTP401 拒绝。日志 `.runtime/package-dev-host.stdout.log`、`.runtime/package-dev-host-http-smoke.json`。这项启动检查验证宿主过滤器与模块边界；授权后的 CRUD/跨归属/异常审计由模块 MockMvc 和 MySQL 测试覆盖，未冒充完整浏览器或生产代理联调。

以上验证不代表生产域名代理、真实邮件、真实商店和生产历史数据已联调。
