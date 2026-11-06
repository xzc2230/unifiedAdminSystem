# Unified Admin System

基于 JeecgBoot v3.9.5 的多站点管理项目，包含站点资源、域名验证、域名/路径分流及审批发布能力。

## 目录

- `upstream/JeecgBoot`：完整 JEECG 前后端源码及本项目扩展。
- `services/site-router`：独立 Node.js 24 反向代理执行器。
- `deploy`：本地启停脚本和生产部署模板。
- `config`：代码生成模板。
- `docs`：设计、实施、部署与验证记录。

## 运行与部署

- 本地运行：`docs/baseline/local-runbook.md`
- 两服务器部署：`docs/deployment/two-server-deployment.md`
- 站点资源：`docs/deployment/site-resources-runbook.md`
- 分流发布：`docs/deployment/routing-publication-runbook.md`
- ClickHouse 统计接入：`docs/deployment/clickhouse-runbook.md`
- 能力范围：`docs/baseline/capability-matrix.md`

本地 `.runtime`、凭证、依赖和构建输出不纳入版本控制。克隆后需依据运行手册创建环境配置并安装依赖。生产模板中的域名、IP和凭证须自行配置。

ClickHouse 首期接入提供 OpenAPI 调用元数据的异步批量采集与平台管理员汇总接口，默认关闭，通过受保护配置启用。队列为有界内存、尽力交付，不能用于计费或安全审计；尚无统计前端看板、站点PV/UV及代理访问采集，也尚未验证对外 API 的容量目标。

## 来源与许可证

上游基线提交：`e3b9dc0aefe1943d9772b026f64ed671a7c82802`。许可证及附加组件说明见 `docs/baseline/source-and-license.md` 与源码目录中的 LICENSE 文件。
