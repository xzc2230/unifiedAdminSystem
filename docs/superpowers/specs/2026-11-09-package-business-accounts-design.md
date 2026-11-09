# 后台业务账号管理设计

用户已确认本方案，2026-11-09。

## 目标与范围

在系统管理新增业务账号管理，按租户成员展示未开通/已开通/已停用/映射异常；支持新账号自动分号、历史账号预检绑定、停用恢复与操作审计。既有用户角色分配、加入租户及资源转移沿用宿主功能。开户不自动授予应用菜单或历史数据。

## 安全与数据约束

- 首期只允许有效的平台 admin 角色用户，且必须具有对应 package:identity:query/open/bind/status 权限。配置接口不调用 PackageAccess.current，不要求操作者自身有业务映射。
- 目标用户、租户、成员关系必须有效；管理接口显式选择目标租户，平台权限允许跨租户配置，普通租户成员不可使用。
- legacy_user_id 全局唯一；(tenant_id,jeecg_user_id) 唯一；已有映射不换号、不删除、不认领资源。
- 使用独立自增分配序列产生候选编号，并使用普通 BIGINT 主键占用表统一预留编号；历史绑定不得推进新开户序列，以免绑定或回滚极大历史编号耗尽全局分号。新开户起点 1000000000000；接口统一将编号输出为字符串。历史绑定必须同时预留同一编号。迁移检查已存在编号和业务表 user_id，避免新开户占用现有历史资源编号。
- 停用保留映射，访问解析与转移目标检查同步过滤；映射歧义默认拒绝。
- 写入使用事务，锁定目标宿主用户与编号分配状态，数据库唯一约束兜底；重复开户返回原身份，不能把停用身份隐式恢复，历史重复绑定只对同一编号幂等。
- 绑定前展示该编号占用情况、按业务表统计历史记录及已登记资源归属的冲突。绑定不更改 pkg_resource_owner。历史编号需人工核实，预览结果不是认证依据，提交时重新检查。
- 记录操作人、目标用户、租户、编号、动作、前后状态、原因、时间。拒绝空原因与过长输入。

## HTTP 契约

基础路径 /package-admin/identities，使用 JEECG Result。

- GET /tenants：租户选项 [{tenantId,tenantName,status}]。
- GET /list?tenantId=&keyword=&state=&pageNo=&pageSize=：{records,total}；行字段 userId,username,realname,tenantId,tenantName,userStatus,memberStatus,tenantActive,legacyUserId,identityState,source；identityState 为 UNOPENED/ACTIVE/DISABLED/ANOMALOUS。
- POST /open：{tenantId,userId,reason}，返回身份行。
- GET /legacy-preview?tenantId=&userId=&legacyUserId=：{legacyUserId,available,conflict,resourceCounts:[{resourceType,count}],ownershipConflicts}。
- POST /bind：{tenantId,userId,legacyUserId,reason}，返回身份行。
- POST /status：{tenantId,userId,enabled,reason}，返回身份行。
- GET /audit?tenantId=&userId=&pageNo=&pageSize=：{records,total}；行字段 id,operatorId,operatorName,action,legacyUserId,beforeState,afterState,reason,createdAt。

编号输入输出使用十进制字符串，后端严格校验正 Long 范围。停用/恢复 enabled 必须明确提供。

## 验证与交付

测试覆盖独立配置鉴权、有效成员校验、未映射列表、分号冲突、并发/重复提交、事务回滚、历史预检、停用恢复、审计和租户隔离。执行模块回归、真实 MySQL 集成测试与前端构建。提供可重复执行的增量迁移、菜单 SQL 和部署说明；当前工作只修改本地项目，不直接配置 wang 或变更服务器数据库。
