# 应用共享实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. 当前用户已确认设计，连续执行至本地验收完成。

**Goal:** 创建者保留权限，接收者获得应用查看和编辑权限，不能删除或转授。

**Architecture:** 新增共享表与事务审计服务，应用读写范围区分共享与归属，前端共享管理替换旧转移入口。

**Tech Stack:** Java 21/Java17字节码、Spring、JdbcTemplate、MySQL8、Vue3、TypeScript、Ant Design Vue。

## Global Constraints

遵循同日 app-sharing-design.md。保留工作区已有修改；只本地实现验证，不部署、不修改生产映射、不提交Git。跨租户禁止；关联资源不扩大；共享写入不改变业务编号。接口字段、权限与批量上限按设计执行。

## Task 1：访问边界（主任务）

- [x] 新增失败测试验证原归属不变、接收者读写与管理隔离、普通通配权限不等于管理员。
- [x] 修改 PackageAccess/PackageIdentityRepository，将 scope 对应用扩为所有者+共享；lockRows 保持仅管理归属，新增 lockEditableRows 对共享应用编辑事务重检。ManagedResource.edit 改用编辑锁；AppManager 增加服务端计算 canManage 并在应用响应装饰。
- [x] current 管理员判定使用有效 admin 角色而非通配权限；新增必要的真实角色校验。
- [x] 删除应用清理共享，保留审计。执行针对测试。

## Task 2：共享管理及迁移（独立子任务）

- [x] 先写迁移及事务服务失败测试；新增014 SQL、共享服务、控制器，按设计契约。
- [x] 旧 transfer 入口改为共享兼容入口，禁止调用旧更换归属逻辑；保留 transfer-targets。
- [x] 验证批量幂等、全批回滚、有效成员、禁止接收者管理、审计原子、日期序列化及迁移两次。

## Task 3：页面（独立子任务）

- [x] 先写组件行为失败测试，再改 PackagePage.vue 与 API，新增独立 AppSharingModal.vue。
- [x] canManage 控制行和混合选择管理按钮，新增共享原因、共享列表/撤销和审计；复用 transfer 权限。
- [x] 验证失效响应、重复提交、失败输入保留，运行前端相关回归和构建。

## Task 4：集成与交付

- [x] 审查各任务与整体权限边界，完成所有失败修复。
- [x] 执行后端完整回归，宿主打包，前端回归构建和真实HTTP场景，清理临时数据。
- [x] 编写部署说明、历史转移限制与测试证据，完成差异检查。
