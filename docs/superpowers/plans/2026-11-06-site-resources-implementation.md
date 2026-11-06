# 站点资源与隔离实施计划

> **For agentic workers:** Use executing-plans to execute each task and retain verification evidence. Steps use checkbox syntax.

**Goal:** 交付可操作且具备后端资源隔离的站点、目标、域名及成员管理。

**Architecture:** 现有 JEECG 单体新增 siteplatform 包，使用参数化 JDBC 显式范围查询。复用 Shiro 登录和平台权限；独立 SQL 安装资源表和菜单。

**Tech Stack:** Java 21、Spring JDBC/事务、JUnit Jupiter/Mockito、Vue3/Ant Design Vue。

## Global Constraints

- 按 `2026-11-06-site-resources-design.md` 实施。资源登记不改变当前流量。
- 不启用全系统租户开关，不修改上游共享业务授权，不覆盖原有锁文件修改。
- 使用已有检出与依赖，不迁移运行目录，不新增运行中间件。

## Task 1：验证与授权规则

Files（以下源码路径相对 upstream/JeecgBoot）：
- Create: `jeecg-boot/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/siteplatform/ResourceRules.java`、`ResourceAccess.java`。
- Test: 同模块 `src/test/java/org/jeecg/modules/siteplatform/ResourceRulesTest.java`、`ResourceAccessTest.java`。

- [x] 写失败测试并运行，覆盖主机名、目标地址、成员角色及伪造租户头。
- [x] 实现验证和从登录身份建立的租户上下文；测试由红转绿。

## Task 2：持久化与业务接口

- Create: 同包 `ResourceRepository.java`、`ResourceService.java`、`ResourceController.java`、`DnsTxtResolver.java`、`ResourceRequests.java`。
- Create: `jeecg-boot/db/site-platform/V20261106_1__site_resources.sql`。
- Test: 同模块 `src/test/java/org/jeecg/modules/siteplatform/ResourceServiceTest.java`。

- [x] 先写跨站点、只读修改、非管理员授权及过期挑战测试并确认失败。
- [x] 实现带 tenant/site 条件的查询、行锁与事务审计、DNS 挑战条件更新。
- [x] 运行定向测试与后台打包；核对所有入口具有权限校验。

## Task 3：后台页面与安装验收

- Create: `jeecgboot-vue3/src/views/siteplatform/resources/index.vue`、`resources.api.ts`、`ResourceEditor.vue`。
- Create: `docs/deployment/site-resources-runbook.md`（工作区根目录）。

- [x] 实现站点列表、资源与成员面板；使用 backend 动态菜单，无匿名静态路由。
- [x] 对新增文件格式检查及前端构建；执行 SQL 安装/菜单验收，不创建演示用户或修改真实租户成员关系。
- [x] 输出验收记录与未完成边界，更新能力矩阵。
