# 后台业务账号管理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 管理员在后台完成业务身份开户、历史绑定及停用恢复。

**Architecture:** 新增独立配置权限边界和事务开户服务，保留既有身份及资源归属模型；前端独立系统管理页面调用 /package-admin/identities。

**Tech Stack:** Java 21、Spring Boot、JdbcTemplate、MySQL 8、Shiro、Vue 3、TypeScript、Ant Design Vue。

## Global Constraints

遵守已批准设计 2026-11-09-package-business-accounts-design.md 的 HTTP 契约、平台管理员限制、编号预留规则和事务审计规则。保留当前工作区其他修改。无线上数据修改。

## Task 1：数据库与身份状态

- [x] 新增 db/package/009-business-accounts.sql，先检查重复映射，再建立状态/来源字段、复合唯一索引、编号占用表及审计表；可重复执行，保留已有映射。
- [x] 新增稳定 ID 的系统管理菜单及 query/open/bind/status 权限 SQL，默认不向普通角色授权。
- [x] PackageIdentityRepository 和转移候选 SQL 排除停用映射，同时继续拒绝重复身份。
- [x] 在真实本地 MySQL 验证迁移重复执行、已有数据保留与状态隔离。

## Task 2：后台接口

- [x] 先编写并执行鉴权与事务行为测试，再实现独立配置 Guard、Service、Controller，接口按设计文档。
- [x] 未开户列表以租户成员为基础，数字编号使用字符串；所有写入加锁校验，事务内登记编号、映射与审计。
- [x] 历史绑定预检业务表与资源归属，提交重检；重复操作幂等，不提供改号/删号接口。
- [x] 执行新增单测和 MySQL 集成测试，验证失败回滚与并发开户。

## Task 3：管理页面

- [x] 新增 src/views/system/businessIdentity/index.vue、API、类型与独立弹窗，复用宿主组件。
- [x] 支持租户筛选、搜索、状态筛选、开户、历史预检绑定、停用恢复原因和操作记录。
- [x] 添加行为校验测试，执行测试和生产构建。

## Task 4：集成验收

- [x] 执行 package 模块回归、宿主打包、前端测试与构建，审查权限隔离及事务边界。
- [x] 在本地候选环境验证无业务映射的平台管理员可配置，普通账号不可配置，开户后资源初始为空，停用后无法访问。
- [x] 编写升级、权限分配、回退及 wang 操作说明，报告测试证据和部署边界。
