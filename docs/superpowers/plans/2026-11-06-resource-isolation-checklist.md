# 资源与隔离阶段启动清单

前提：v3.9.5 后台已可打包并在本机启动。此文件仅定义下一阶段需要完成的设计与验证，不代表业务模块已实现。

## 源码映射

- 系统后台：`upstream/JeecgBoot/jeecg-boot/jeecg-module-system/jeecg-system-biz`。
- 单体启动及本项目迁移入口：`upstream/JeecgBoot/jeecg-boot/jeecg-module-system/jeecg-system-start`。
- 租户拦截：`upstream/JeecgBoot/jeecg-boot/jeecg-boot-base-core/src/main/java/org/jeecg/config/mybatis/MybatisPlusSaasConfig.java`。
- 前端业务页面：`upstream/JeecgBoot/jeecgboot-vue3/src/views`，接口：`src/api`；遵守前端 AGENTS.md 中可适用的约定。
- 上游 AGENTS.md 的 SVN、本机路径与 3.8.3 描述与本次 Git v3.9.5 检出不符，版本及路径以实际检出为准。

## 按顺序交付

1. 明确共享用户与租户成员关系、站点授权规则，以及系统管理员跨租户的显式操作方式。
2. 定义 site、site_target、domain_binding、environment 与站点成员授权模型，明确 tenant_id、主键、唯一约束和软删除行为。
3. 域名规范化处理大小写、尾点和国际化主机名；绑定使用一次性 DNS TXT 验证令牌，约束跨租户重复绑定与解除绑定。
4. 对新业务表配置强制租户过滤，不允许请求参数直接决定当前用户的租户归属；核查后台任务及凭证调用的上下文建立方式。
5. 实现站点及目标登记 CRUD；目标地址仅接受批准的 HTTP/HTTPS 地址，并定义跨站点引用规则。
6. 验证两个租户、多站点以及只读/运营/管理员角色的查询、详情、更新、导出、批量操作与越权 ID 请求。
7. 输出该阶段确切迁移文件、后端类、前端页面及测试命令后，执行实现与代码审查。

分流究竟采用跳转还是反向代理，仍应在下一阶段路由设计时确认；首期资源表不以二者之一作为隐含前提。
