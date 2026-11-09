# 业务账号管理升级与操作说明

## 功能与入口

系统管理 → 业务账号管理。首期只允许有效的平台管理员（admin 角色），同时要求对应按钮权限：

| 操作 | 权限 |
| --- | --- |
| 查看列表、租户与操作记录 | package:identity:query |
| 新账号开户 | package:identity:open |
| 历史编号预检与绑定 | package:identity:bind |
| 停用与恢复 | package:identity:status |

管理员自身无业务身份或未加入目标租户时仍可配置。普通租户管理员即使获得上述按钮权限，也不能调用这些配置接口。

开户与 JEECG 角色授权分别管理：开户不自动授予应用菜单权限，授予应用菜单权限也不自动开户。

## 升级顺序

升级文件位于 `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/db/package/`。

1. 暂停应用管理写入和历史数据导入，备份数据库及当前发布版本。备份应覆盖 `pkg_user_identity`、资源及资源归属表、`sys_permission`、`sys_role_permission`；全库备份更便于恢复。
2. 对原数据库执行 `009-business-accounts.sql`。MySQL 客户端必须遇错停止，禁止使用 `--force`。脚本依赖 MySQL 8、存储过程及相应 DDL 权限；检查失败先排查原数据，不绕过唯一约束。
3. 执行 `V20261109_05__business_identity_menu.sql`，使用 `--default-character-set=utf8mb4`。此处文件名不表示宿主会自动执行：当前模块的 `db/package` 目录需要按部署流程显式导入。
4. 在角色管理给平台管理员分配业务账号管理菜单和所需四个按钮权限。保留现有应用权限分配。
5. 发布配套后端和前端，管理员退出重登刷新菜单与权限缓存。不能先发布依赖新字段的后端，再补数据库结构。
6. 验证未开通成员可见、新用户可开户、历史编号冲突被拦截、停用后业务接口拒绝、恢复沿用原编号，以及普通账号无法访问配置接口。

示例（在服务器仓库根目录执行，路径通过 `pwd` 获取；只导入升级文件，不替用户开户）：

```bash
set -euo pipefail
repo_dir="$PWD"
sql_dir="$repo_dir/upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/resources/db/package"
cd /opt/middleware
sudo docker compose exec -T mysql sh -c 'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysql -uroot --default-character-set=utf8mb4 jeecg-boot' < "$sql_dir/009-business-accounts.sql"
sudo docker compose exec -T mysql sh -c 'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysql -uroot --default-character-set=utf8mb4 jeecg-boot' < "$sql_dir/V20261109_05__business_identity_menu.sql"
```

两个脚本均需检查退出状态；第一个失败时不要继续执行后续步骤。DDL 会隐式提交，数据库事务不能整体撤回结构升级；维护窗口内阻止其他进程写映射和导入历史数据。

## 编号与已有数据

保留已有数字业务编号。新增复合唯一约束确保每个租户成员最多一条身份。升级同时将旧统计表 pkg_app_statistics.user_id 从 INT 扩为 BIGINT，配合 Java Long，支持新开户分配的编号；已有记录和值保留。

独立编号占用表预留已有映射和业务表的正数字用户编号，新开户由独立分配序列从 `1000000000000` 起产生候选值，再原子预留。历史编号只写入占用表，不推进新开户序列，防止极大历史编号耗尽分号空间。编号对 API 和前端始终作为十进制字符串处理。禁止人工复用已占用编号；后续历史导入也必须同步占用表并遵守全局唯一规则。

历史绑定会展示占用和历史记录概况；管理员必须先核实原系统用户身份。绑定时再次校验编号占用和已登记的资源归属冲突，不自动添加或转移资源归属。缺少资源归属的历史记录继续由原迁移流程处理。

停用保留身份、编号及数据；恢复继续使用原编号。不要通过删除映射模拟停用，或用换号方式纠正历史绑定。绑定错误且已产生数据时，需先制定资源核对与纠错迁移方案。

## 为 wang 配置

1. 在原用户管理确认 `wang` 账号正常、已加入 1002（归因系统），并授予所需应用菜单和操作权限。
2. 打开业务账号管理，选择 1002，搜索 `wang`。
3. 若为新用户，选择新账号开户并填写原因；若来自旧系统，选择历史绑定，输入经核实的数字编号、预检并确认。
4. `wang` 退出重登，选择归因系统后使用应用包管理。新开户账号初始资源为空；租户 1000 中 admin 的应用不会因此出现在租户 1002。

## 回退边界

优先保留新结构与新增审计、编号占用记录，禁止删除新增账号数据回退。旧版后端不认识业务身份停用状态，因此不能直接回退到旧版后端并继续开放应用管理；需要暂时关闭应用管理入口，或在回退版本保留状态校验。回退前核对新开户期间创建的业务资源，避免丢失归属关系。

本次实现与验收针对本地工作区和本地候选环境；不代表服务器已发布，也不代表已替 wang 建立实际映射。

## 本地验证记录（2026-11-09）

- 真实候选服务 HTTP 验收：31 项通过，覆盖无映射管理员开户、普通账号拒绝、并发幂等、空初始资源、统计大编号读写、租户隔离、停用恢复、Long 最大历史编号及审计；菜单 API 已确认返回页面路由。临时账号、资源和映射已清理。
- 前端相关测试：67 项通过；前端生产构建通过。
- 后端 package 完整回归：212 项，211 项通过，0 失败、0 错误，1 项因 Windows 符号链接权限跳过；包含真实 MySQL 和 Redis 集成验证。
- 覆盖并发幂等开户、事务失败回滚、历史编号冲突、Long 最大值、编号碰撞、租户隔离、停用恢复，以及软删除账号和重复成员关系的状态输出。
- 宿主完整打包通过；最终服务修改按 Java 17 编译参数重编译后，再次执行全套测试，强制重新生成模块 JAR 和宿主包，并检查可执行包包含最终修复。后端/数据库和前端独立代码复核完成。数据库升级在本地重复执行，验证保留已有映射与角色授权。
- 浏览器自动化因本机连接限制未完成可视验收；已用 Vue 组件行为测试和模板编译验证交互，页面视觉仍需在目标环境抽查。

测试日志：`.runtime/business-identity-backend-final.log`、`.runtime/business-identity-frontend-build.log`。这些日志为本地工作文件，不随发布包上传。