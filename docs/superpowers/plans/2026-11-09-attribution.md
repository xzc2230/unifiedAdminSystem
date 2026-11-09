# 归因管理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 attribution v1.2 实现为独立归因业务模块和一级菜单。

**Architecture:** 独立 Maven 模块随 JEECG 启动，独立 attr_ 表及服务端身份边界。管理页面使用宿主登录与响应格式，公开解析遵循九字段原协议。

**Tech Stack:** Java 17/21、Spring Boot、JdbcTemplate、MySQL 8、Vue 3、TypeScript、Ant Design Vue。

## Global Constraints

- 条件和权重保存在渠道 Offer 绑定层。
- resolve 不重新抽选；真实和模拟快照隔离。
- 对外所有 ID 均使用字符串。
- 保存成功即生效；不新增发布页面和应用删除按钮。
- 不覆盖工作区已有部署修改。
- 新归因流量处理默认关闭，按应用白名单启用。

## 统一接口

所有管理接口返回 JEECG Result，result 为以下业务内容；字段沿用设计文档。

- GET `/attribution-admin/applications?q=&pageNo=1&pageSize=20`：`{records,total}`，记录包含 applicationId/applicationCode/applicationName/channelId/channelName/channelCode/fallbackUrl/firstOfferUrl/offerCount/configVersion/editable。
- GET `/attribution-admin/application-configurations/{app}/channels/{channel}`：完整表单和 managedBindingIds、expectedConfigVersion。
- GET `/attribution-admin/applications/{app}/channels/{channel}/offers?configVersion=`：`{configVersion,offers}`。
- POST `/attribution-admin/application-configurations`：创建，Idempotency-Key 必传。
- PUT `/attribution-admin/application-configurations/{app}/channels/{channel}`：更新，Idempotency-Key 必传。
- GET `/attribution-admin/capabilities`：`{countryEnabled:false}`，后续按可信来源配置开放。
- POST `/api/v1/attribution/resolve`：独立主体认证，九字段平铺响应。
- 权限：`attribution:application:list`、`attribution:application:add`、`attribution:application:edit`、`attribution:diagnostics:view`、`attribution:security:manage`、`attribution:integration:manage`。

## Task 1 后端事务与归因链路

**Files:** 新建 `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-attribution/` 下的 POM、Java、SQL 与测试。包名 `org.jeecg.modules.attribution`。

**Interfaces:** 产出上述管理 API、公开 resolve、受控点击/主体凭据及 URL 安全/证据管理操作；SQL 存于 `src/main/resources/db/attribution/001-schema.sql`。父 POM 和菜单由 Task 3 处理。

- [x] 先编写并运行规则与协议失败测试，覆盖未知值、逐条条件和全部候选。
- [x] 实现配置 DTO、白名单校验、条件匹配、稳定加权选择及 URL 门禁。
- [x] 实现独立应用授权、有效租户校验、数据库事务、版本 CAS 与管理幂等。
- [x] 实现真实点击 first-wins、令牌摘要、主体凭据验证和稳定模拟快照。
- [x] 实现当前地址安全检查、脱敏诊断、独立安全撤销和受控归因状态证据。
- [x] 用数据库集成测试验证并发/回滚/隔离；执行 Maven 目标测试。

Run: 设置 JAVA_HOME 为 `C:/Program Files/Java/jdk-21`，在后端根目录执行 `mvn -B -Dmaven.repo.local=D:/workspace/JEECG/.runtime/m2 -pl jeecg-boot-module/jeecg-module-attribution -am test -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=Attribution*Test`。

Expected: 针对新模块的规则、数据库和协议测试成功，失败日志及通过日志存 `.runtime/attribution-*.log`。

## Task 2 前端管理页

**Files:** 新建 `upstream/JeecgBoot/jeecgboot-vue3/src/views/attribution/`，包括 `index.vue`、`attribution.api.ts`、`attribution.types.ts`、编辑弹窗及 Offer 条件组件。

**Interfaces:** 消费统一管理接口；直接菜单组件 `attribution/index`；列表遵循五列，Offer 编辑遵循文档图片。

- [x] 编写表单转换/验证测试，验证首条权重 1、后续 0、空 Offer 合法、有条目全 0 阻断及显式移除集合。
- [x] 实现分页搜索、0/1/多 Offer 和可重试行展开，禁止误展示空成功。
- [x] 实现独立卡片条件、固定底栏、保存失败保留输入、取消无确认弹窗。
- [x] 接入 v-auth 和服务端 capabilities，保持 COUNTRY 未就绪不可添加。
- [x] 执行相关逻辑测试和前端生产构建。

Run: `node --test` 针对新增表单测试；前端目录运行 `pnpm build`。Expected: 测试和生产构建成功。

## Task 3 宿主接入及部署

**Files:** 修改父模块和启动模块 POM；新增 `deploy/attribution/`、`docs/deployment/attribution-runbook.md` 及菜单 SQL；必要时修改 Shiro 扩展入口。

- [x] 确认 Shiro 独立认证入口扩展方式，避免公开归因请求误入 JEECG 登录。
- [x] 装配独立模块，新增单页一级菜单“归因管理”及按钮权限，中文使用 utf8mb4。
- [x] 编写配置样例、可重复执行安装脚本和授权说明，不启用生产流量。
- [x] 本地导入增量 SQL，构建并启动候选服务；沿用本机受保护凭据，不输出秘密。
- [x] 使用真实本地数据库执行管理保存/展开、冲突、真实解析、模拟首次重复、URL 撤销降级、身份隔离检查。

## Task 4 审阅与交付

- [x] 审阅后端、前端、接口和菜单整体差异；修复发现的缺陷。
- [x] 执行启动模块打包和前端构建，验证菜单与页面可达性。
- [x] 回写验收结果，区分受控链路验证和未配置的移动端/真实媒体外部交付。
- [x] 保留全部既有用户修改；不执行生产部署、DNS 切换或历史数据删除。

## 实际验收

2026-11-09：26 项模块测试 + 1 项入口隔离、31 项前端测试、完整后端打包、前端生产构建通过。原生 MySQL 增量安装重复验证（含 Windows PowerShell 5.1），12 项管理 HTTP 检查、默认关闭检查、17 项归因链路检查及浏览器桌面/390px 弹窗验收完成。详细记录见 docs/deployment/attribution-runbook.md。未发布生产、未开通真实媒体/移动端商店/国家外部能力。
