# ClickHouse Integration Implementation Plan

**Goal:** 接入同机 ClickHouse，提供对外 API 的脱敏调用元数据统计，保留现有业务库和关键审计。

**Architecture:** 独立 Compose 服务、固定表结构与最小读写账号；Java 使用 JDK HTTP 客户端批量写入，无新增 JDBC 依赖。请求线程仅尝试入有界内存队列，后台单线程定期批量写入。非持久统计队列不用于计费或安全审计；失败数量与待发送数量可查，不承诺恰好一次或不丢失。

**Tech Stack:** Java21、Spring、JUnit5、ClickHouse HTTP/JSONEachRow、Docker Compose。

## Global Constraints

- 默认关闭，通过独立环境配置启用；不改变 MySQL 默认数据源或已有 API 认证。
- 不采集 AK/SK、请求头、IP、查询参数、响应正文；仅随机事件ID、时间、API ID、方法、HTTP状态及耗时。
- 汇总查询限平台 admin 角色和已有API日志读取权限；首期全平台统计，不冒充按租户/站点隔离统计。
- 不公开 ClickHouse 端口；本地18123只绑定loopback，受限账号及资源。
- 不在现有用户站点或API上写测试数据；集成测试使用隔离表/临时服务并清理。
- 统计故障不改变业务返回；满队列丢弃并计数，网络失败记录失败/未知批次，不自动重试导致重复。
- 此次不实现访问PV/UV、站点埋点、代理事件、聚合物化视图、新前端看板或Kafka。

## Task 1: Deployment

- [x] 新增 `deploy/clickhouse/` compose、初始化与配置模板、启动脚本；仅自己的项目/卷。
- [x] 创建 `jeecg_analytics.api_call`、7天TTL、受限writer和reader，并真实服务核验权限及持久卷配置；冷备恢复另待验收。

## Task 2: Backend (TDD)

- [x] 新增 `modules/analytics/` 的配置、事件、HTTP存储、有界队列、定时工作器及受权查询控制器。
- [x] 先测试默认关闭、队列溢出、批量失败、脱敏序列化、HTTP状态/超时和固定参数查询，观察失败再实现。
- [x] 在 `ApiAuthFilter` 完成分支追加统计采集；现有MySQL日志保留，完整替代需后续迁移确认。
- [x] 管理入口 `/openapi/statistics/status` 与 `/openapi/statistics/summary` 仅admin+日志读取权限，禁用503、错误脱敏。

## Task 3: Integration and delivery

- [x] Maven定向测试与打包，真实ClickHouse验证写入、汇总、权限、关闭服务故障不阻塞采集。
- [x] 独立env注入本地服务（密码不输出），恢复后端并验证未登录管理接口拒绝。
- [x] 更新运行手册/能力矩阵，记录队列丢失语义、同机资源预算、磁盘增长和未做QPS验收。

本轮用户请求是对前文ClickHouse基础接入方案的实施授权。继续当前工作目录，保留现有内层/外层Git仓库；不自动推送后续变更。

## 验证记录（2026-11-06）

- 最终 Maven package 成功；76项测试，0失败/错误/跳过，含真实ClickHouse读写与权限、Shiro角色/权限拦截、超时与队列故障、原资源/分流真实MySQL回归。日志 .runtime/clickhouse-final-package.log。
- 本地前后端已恢复，Frontend/Backend/Proxy均200；统计status和summary未登录均401。
- 真实现有API无凭证请求返回401，其脱敏元数据经运行中后端写入ClickHouse，已核对1条新事件；未修改API授权或业务站点。
- 新功能未推送GitHub；无前端看板，无QPS压测，无生产部署。
