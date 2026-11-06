# 能力与源码对应

基线：v3.9.5，2026-11-06。路径相对 `upstream/JeecgBoot`；存在源码不代表业务验收通过。

| 需求 | 源码入口 | 首期处理 |
| --- | --- | --- |
| 用户、组织、角色 | `jeecg-boot/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/system` | 复用，补业务授权测试 |
| 租户 | 同目录的 `controller/SysTenantController.java`；`jeecg-boot/jeecg-boot-base-core/src/main/java/org/jeecg/config/mybatis/MybatisPlusSaasConfig.java` | 必须扩展并验证；系统租户控制常量默认 false，隔离表显式配置 |
| 数据权限 | `jeecg-boot/jeecg-boot-base-core/src/main/java/org/jeecg/common/aspect/PermissionDataAspect.java` | 复用机制；站点、字段读写、导出与批量接口另做后端校验 |
| 定制 API | `jeecg-boot/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/openapi` | 已有凭证、授权、日志、过滤器与文档代码；首期增加业务范围与限流验证 |
| 报表与 BI | `jeecg-boot/jeecg-module-system/jeecg-system-biz/pom.xml` 中的外部组件 | 组件安全及授权核验后复用；访问采集自行建设 |
| AI | `jeecg-boot/jeecg-boot-module/jeecg-boot-module-airag` | 仅测试环境；提案、工具白名单与发布审批另开发 |
| 站点、目标地址、域名归属 | 新增 `jeecg-boot/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/siteplatform`；页面 `jeecgboot-vue3/src/views/siteplatform/resources` | 已交付资源登记、DNS 挑战验证、成员授权与操作记录；安装见 `docs/deployment/site-resources-runbook.md` |
| 证书管理 | 项目业务尚未实现 | 生产 Nginx 模板使用运维提供的证书，尚无平台证书管理 |
| 分流执行与快照 | 工作区 `services/site-router`；管理端 `siteplatform/Routing*` | 已实现域名/路径反向代理、不可变快照、落盘及重启恢复；单节点，需按运行手册配置部署，尚未接管公网流量 |
| 审批、生效确认、配置回滚 | `siteplatform/Routing*`；前端 `siteplatform/routing` | 已实现草稿、模拟、审批、发布、版本ACK及历史回滚提案；生产禁止自审，回滚须重新审批发布，ACK不代表实时健康 |

## 明确发现

1. 系统租户控制默认关闭，不能将现成租户菜单等同于已落实隔离。
2. 上游开发配置包含敏感 AI 节点启用值，本地覆盖同时清空 `jeecg.airag.allow-sensitive-nodes` 与 `jeecg.ai-rag.allow-sensitive-nodes`。前者对应 `AiRagConfigBean.PREFIX`，不得仅依据文案配置。
3. `ShiroConfig` 对 `/jmreport/**` 配置匿名链，组件可能有自己的鉴权；生产前须验证组件实际鉴权，不能据此直接判断接口安全或不安全。
4. 现有审计不能替代本项目所需的版本、审批、节点生效与回滚关联记录。
5. 2026-11-06 新资源模块自行核实用户与所选租户的有效成员关系，并显式按租户及站点授权查询，不依赖全局租户开关。21 项定向测试（含真实 MySQL 和撤权并发回归）通过；前后端构建、菜单与资源面板已在本地验证。真实公共 DNS 和生产数据库最小权限仍需验收。
6. 2026-11-06 分流发布扩展：最终资源及分流后端63项测试通过（0失败、0错误、0跳过），Maven打包成功；执行器22项测试通过，前端完整生产构建通过。五张路由表已安装本地。历史发布引用的域名暂不允许释放，同平台已发布域名不得充当上游；详见工作区 `docs/deployment/routing-publication-runbook.md`。地域、用户组、权重灰度、维护页、站点搭建、访问统计及完整定制API仍未交付。
