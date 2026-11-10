# package-api dev 补迁执行计划

> 执行方式：使用 subagent-driven-development 分任务实现和审查；按 test-driven-development 先验证缺失行为。

**Goal:** 将 dev-branch-gap-audit.md 中 dev 新增业务补入 JEECG。
**Architecture:** 继续使用 jeecg-module-package，管理端 /package-admin，公开 OpenAPI 独立 Token 鉴权，旧客户端协议保持兼容。菜单全部在应用包管理根菜单下，前端沿用现有 Vue3/Ant Design 管理组件。
**Tech Stack:** 当前 JEECG Java/Spring/MyBatis、Vue3/TypeScript；不升级依赖。

## 全局约束

- 源 dev 0f83f81，目标起点 160d9ef；源只读，不使用源未提交 SQL，不复制凭据。
- 保持租户/归属/现有包名不可变保护；公开 OpenAPI 按 Token 专属归属访问。
- 新表 pkg_、Redis 使用 package 命名空间。生产部署/SQL执行/历史数据导入不在授权范围内。
- 不提交或覆盖其他任务工作区变更。当前用户要求执行迁移，沿用已存在迁移分支，不另开 checkout。
- 新功能写关键业务和授权测试，先执行失败再实现；最终统一后端回归和前端验证。

## 任务与边界

- [x] T1 包统计。源 project/packstat、mybatis/packstat、app_pack_stat*.sql → 目标 application/packstat、domain/packstat、persistence/mapper、controller/admin/PackagePackStatController。管理 API /package-admin/packstat：list、authors、regions、surviveSummary、{id}、POST、PUT、DELETE/{ids}。保留互斥分桶、日期口径、检测状态、无效结果不更新、节流、历史清理、首次下架邮件和调度。独立增量 SQL 009-dev-packstat.sql；独立测试。
- [x] T2 OpenAPI。源 project/openapi → 目标 application/openapi、domain/openapi、controller/openapi、controller/admin。管理 token /package-admin/openapi/token，日志 /package-admin/openapi/calllog/list；公开 /openapi/manager 原协议 CRUD，X-Api-Token 专属鉴权且不借用后台身份。缺失/禁用/过期/跨Token访问测试。SQL 010-dev-openapi.sql。只修改新建文件及必要独立配置，不动根协调者负责的应用服务。
- [x] T3 前端和菜单。新增 risk、packstat、openapi token/calllog/docs 五页面，统一应用包管理菜单；应用页字段权限/自动拉黑增强，日志 JSON 格式化复制、字典备注。独立 SQL 011-dev-menu.sql；风控管理 API /package-admin/risk/{asn,brand,abi,device}、/package-admin/risk/auto，列表/list、详情/{id}、POST、PUT、DELETE/{ids}，ASN /asn/lookup/{asn}；自动拉黑 GET/DELETE 使用查询参数 pkg、type、value，packageCode 为兼容别名。前端按已有 auth/请求约定接入，不复制ElementUI。
- [x] T4 根协调者：后端风控 CRUD、自动拉黑管理、ASN查询；应用字段授权、包名 trim/缓存维护；旧商店响应与异常语义、日志格式化。字段权限映射沿用迁移模块权限前缀，字段级权限显式独立；前端与后端共享权限名。
- [x] T5 集成与审查。核对新增43路由逐项有对应实现，缺失表/页面/任务可定位；运行模块测试与前端相关测试/类型验证；按失败修正；更新迁移清单、部署步骤与剩余限制。对角色会话行为仅在证实安全宿主适配后实施，不复制RuoYi登录体系；停用时区/SIM不启用。

## 测试命令

在已有 .runtime 工具与项目验证文档确定 Maven/JDK 路径后运行模块测试。前端执行 node --test --test-isolation=none tests/package-management.test.mjs，并增加覆盖五页与字段权限行为的测试；执行已有项目可运行的编译检查。每任务记录实际命令、失败/通过结果，未执行项目明确列出。

## 进度

本轮审计缺口已完成代码补迁及独立审查。最终整模块 291 测试：290 通过、0 失败/错误、1 Windows 符号链接权限跳过；前端 22 测试和生产构建通过；宿主 12 模块打包通过，并完成独立端口实际启动和 5 次 HTTP 鉴权边界检查。009～012 SQL 在本地隔离库各执行两次成功。交付及部署边界见 docs/migration/package-api/dev-migration-status.md。未执行生产 SQL/部署/历史导入，未提交 Git。
