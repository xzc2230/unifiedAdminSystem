# Routing and Publication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Track steps with checkboxes.

**Goal:** 将已登记资源接入可验证的域名/路径反向代理及版本发布闭环。

**Architecture:** Java 管理端保存草稿、审批和不可变快照；独立 Node.js 24 流式执行器拉取快照、原子落盘和回报版本。执行器使用运行时内置 HTTP/TLS/stream，无新增 npm 依赖；本阶段不提供地域、用户组、权重、页面搭建或统计。

**Tech Stack:** 现有 JeecgBoot v3.9.5 / Java21 / JdbcTemplate / MySQL8 / Vue3；执行器 Node24，依赖锁定及独立服务运维另列。

## Global Constraints

- 反向代理，浏览器保留当前访问域名。
- 草稿不影响已发布流量；已发布快照不可变，回滚新增记录。
- 生产环境提交人不得审批自己的变更。
- 所有管理接口核实真实租户成员及站点角色，写事务先锁站点再读当前成员。
- 不修改已有站点数据；数据库集成测试创建并清理专用临时库。
- 公网 DNS/证书、多账号生产权限和容量实测保留单独验收，不伪报完成。
- 继续现有工作分支及未提交资源模块；保留锁文件修改，不另建丢失这些改动的工作树。

## 契约（前后端和执行器共同遵守）

管理接口前缀 `/site-platform/sites/{site}/routing`。Result 封装与既有接口一致。

DraftInput：`{revision:number, defaultTargetId:string, domainIds:string[], rules:RuleInput[]}`。
RuleInput：`{id:string, domainId:string|null, path:string, matchType:'EXACT'|'PREFIX', priority:number, targetId:string}`。规则 id 为不超过32位的 ASCII 标识符；priority 在站点内唯一，数值越大越优先；路径段前缀；不开放 regex、权重或自定义头。

- `GET /routing` 返回 `{draft:null|{revision,status,input,submittedBy,approvedBy},releases:ReleaseView[]}`，历史最近50项。
- `PUT /routing/draft` 保存 DraftInput，返回 `{revision}`；创建 revision=0；变更递增 revision、清空审批及候选内容。
- `POST /routing/simulate` 输入 `{hostname,path,input:DraftInput}`，返回 `{ruleId:string|null,targetId,url}`。无网络请求；也能模拟未发布草稿。
- `POST /routing/submit` 输入 `{revision}`，校验资源并生成候选快照，status=SUBMITTED，revision递增。
- `POST /routing/approve` 输入 `{revision}`，ADMIN + `siteplatform:approve`，生产与 submittedBy 不同，status=APPROVED，revision递增。
- `POST /routing/publish` 输入 `{revision}`，ADMIN + `siteplatform:publish`；重核候选资源与摘要，再写 release 及 desired 指针；draft 归为 DRAFT 并revision递增、清空审批。
- `POST /routing/rollback/{releaseId}` 输入 `{revision}`，ADMIN + publish；历史版本变成新草稿，核对资源未变；需再提交审批发布，返回 `{revision}`。

ReleaseView：`{id,hash,createdAt,createdBy,sourceReleaseId:null|string,desired:boolean,ack:null|{releaseId,hash,appliedAt}}`。单节点先显示待确认/当前已确认；历史 ACK 不能证明节点此刻健康，页面明示。执行器健康观测和多节点部分生效是后续扩展。

快照 Envelope：`{payload:string,hash:string}`，hash 为 payload 原始 UTF-8 字节 SHA256 小写hex。payload JSON：

```json
{"schemaVersion":1,"tenantId":100,"siteId":"site-id","environment":"test","releaseId":"release-id","domains":["www.example.com"],"defaultTarget":{"id":"target-id","url":"https://origin.example.com"},"rules":[{"id":"api","hostname":null,"path":"/api","matchType":"PREFIX","priority":10,"target":{"id":"target-id","url":"https://origin.example.com"}}]}
```

Node 内部 API 仅两个精确 Shiro anon 路径，控制器自行验证独立 Bearer Token；缺少配置默认404，错误凭证401，不能凭租户请求头扩大范围。

- `GET /site-platform/node/snapshots` 返回**直接 JSON** `{nodeId:string,snapshots:Envelope[]}`，不使用 Result；配置的单节点仅获取明确授权 tenant/site 对的当前 desired 快照。
- `POST /site-platform/node/ack` 输入 `{siteId,releaseId,hash}`，核对授权站点、当前 desired、摘要后持久化，直接返回 `{accepted:true}`。旧版本 ack 返回409，不影响新版本。
- 后台属性：`siteplatform.node.id`、`siteplatform.node.token`（至少32字节随机）、`siteplatform.node.assignments`（逗号分隔 `tenantId/siteId`）。缺任一项关闭节点接口。
- 执行器环境：`SP_CONTROL_URL`（含 `/jeecg-boot` 上下文）、`SP_NODE_ID`、`SP_NODE_TOKEN`、`SP_SNAPSHOT_FILE`、`SP_PORT=18090`、`SP_BIND=127.0.0.1`。控制面HTTPS，开发仅允许 loopback HTTP。
- 每次成功拉取是授权站点的**完整快照集合**，为空表示撤除本节点所有路由；拉取失败保留上次集合。每条快照hash校验、全局域名冲突校验与站点身份校验后，先落盘再切换；落盘失败不加载/ACK。
- 目标每次解析后校验并将已批准IP固定给本次连接，默认禁私网/loopback/metadata；仅运维环境 `SP_ALLOWED_UPSTREAMS` 明确 `hostname:port=ip` 批准内网，不能由页面配置绕过。上游 Host/SNI 为目标hostname，外部域名通过清洗后的Forwarded头提供。

## Task 1：管理端、迁移与回归

Files：新建 `jeecg-boot/.../org/jeecg/modules/siteplatform/RoutingRequests.java`、`RoutingRules.java`、`RoutingRepository.java`、`RoutingService.java`、`RoutingController.java`、`RoutingNodeController.java`；SQL `jeecg-boot/db/site-platform/V20261106_2__routing_publication.sql`；修改现有 `ResourceService.java` 阻止当前发布中的域名解除和目标删除；精确修改 `ShiroConfig.java` 节点认证入口。均位于 `upstream/JeecgBoot`。

- [x] 先写规则匹配、草稿版本冲突、生产自审拒绝、域名/目标状态和审批失效测试，并运行确认新功能尚不存在。
- [x] 增加 `sp_route_draft`、`sp_route_release`、`sp_route_live`、`sp_route_ack`，租户/站点定位及唯一约束；补充单行 `sp_route_publish_lock` 串行校验完整发布集合。
- [x] 按上文契约实现生命周期，所有写入审计与业务同事务，资源重核采用锁定/当前读。
- [x] 内部节点控制器默认关闭，凭证范围服务端固定；模拟不连接目标。
- [x] 临时数据库覆盖资源更改、跨租户、撤权并发、旧ACK拒绝与不可变历史。
- [x] 在Maven reactor内跑 Routing*Test 与 Resource*Test，结果记录日志。

## Task 2：独立执行器

Files：`services/site-router/package.json`、`src/snapshot.mjs`、`src/proxy.mjs`、`src/main.mjs`、`test/*.test.mjs`，均在工作区根目录。

- [x] 先用 node:test 固定用例验证hash篡改拒绝、路径段前缀、跨站点Host、目标地址限制和坏快照保留旧版本，观察预期失败。
- [x] 实现独立执行器拉取、原子快照落盘、重启恢复及ACK；请求全过程流式转发与超时取消。
- [x] 清理连接级头及客户端伪造代理头，禁止转发代理自身控制路径；未知Host拒绝。
- [x] 集成测试使用临时目录、本机受控上游与显式运维IP批准；覆盖GET/POST流式、Cookie/重定向不盲改、上游失败、配置更新、控制面停止后重启继续执行。
- [x] `node --test --test-isolation=none services/site-router/test/*.test.mjs` 22项通过，记录支持限制（WebSocket显式501，当前不宣称支持）。

## Task 3：管理界面

Files：新建 `jeecgboot-vue3/src/views/siteplatform/routing/{RoutingPanel.vue,routing.api.ts}`；修改现有资源页接入独立分流发布页签。

- [x] 提供表单化默认目标、域名、路径与目标编辑、优先级、模拟、草稿状态、提交/审批/发布及历史回滚提案。
- [x] 所有修改包含当前revision；409提示刷新，角色及平台权限控制动作。
- [x] 租户/站点切换销毁面板，异步响应不得污染新上下文；错误和loading就地显示。
- [x] 明示域名验证、证书、节点配置以及待确认区别，不能将登记启用显示为流量生效。
- [x] 定向ESLint与完整构建通过；浏览器已检查空态、编辑/模拟入口及历史空态。真实业务站点未写入测试草稿，模拟与生命周期由自动化覆盖，真实多账号发布待验收。

## Task 4：集成与运行手册

Files：`docs/deployment/routing-publication-runbook.md`、`deploy/production/application/site-router.service`、已有能力矩阵与交付计划。

- [x] 代码复核租户隔离、版本竞态、HTTP头、SSRF、域名撤销与节点快照语义，修复并回归。
- [x] 仅安装本项目新增SQL，保留已有用户站点；打包前停止本项目进程解决Windows JAR占用，随后恢复服务。
- [x] 记录测试、构建、未登录拒绝、执行器离线恢复及浏览器结果；真实公共DNS/证书、生产隔离与容量未测项如实保留。
- [x] 更新能力矩阵与阶段进展，只将有证据的工作勾选完成。
