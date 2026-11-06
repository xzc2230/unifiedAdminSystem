# 域名/路径分流与配置发布运行手册

日期：2026-11-06。适用当前工作区新增的 routing 模块与 `services/site-router`。本文区分本地验证与生产接入，不把测试结果当作公网部署完成。

## 1. 本阶段边界

基于现有站点资源，增加域名/路径规则、默认目标、草稿校验与模拟、提交/审批/发布、不可变历史、回滚提案和单节点快照确认。执行器为独立 Node.js24 服务，反向代理保持浏览器访问域名。

尚未提供地域/用户组/权重灰度、主动健康检查/自动备用、维护页、页面搭建与统计。这些按完整方案继续建设。本阶段也不自动修改公共 DNS、自动签发证书、或验证证书与上游站点全部兼容。

WebSocket Upgrade 显式返回501，HTTP CONNECT 返回405；不要将需要这些协议的上游标记为已兼容。HTTP/S 请求与分块响应为流式传输；SSE 受总响应时限约束，并非无限长连接。默认只解析并允许公网IPv4，IPv6目标暂不开放，除非运维按精确目标IP批准。

## 2. 代码与数据库

- 管理端：`upstream/JeecgBoot/jeecg-boot/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/siteplatform/Routing*.java`。
- 界面：`upstream/JeecgBoot/jeecgboot-vue3/src/views/siteplatform/routing`，从站点资源详情进入。
- 执行器：`services/site-router/src`，无第三方npm运行依赖；运行Node24，部署时固定经过验证的补丁版本。
- 迁移：先安装资源阶段 V20261106_1，再安装 `upstream/JeecgBoot/jeecg-boot/db/site-platform/V20261106_2__routing_publication.sql`。这是手工增量SQL，先备份并核对目标库，不能重复盲执行。

新增权限单独授予发布和审批；平台权限与站点角色必须同时满足。生产提交者不能审批自己的内容。测试环境允许同一ADMIN演练，不代表生产有相同例外。

## 3. 使用流程

1. 登记目标、绑定域名并完成TXT验证。目标URL不含查询串/凭证/片段；代理保留请求路径，在目标路径前缀后拼接。上游需要支持公开访问域名、登录回调与Cookie配置。
2. 选择本次发布域名和默认目标，添加EXACT或PREFIX规则、唯一优先级和目标。优先级数值越大越先匹配，PREFIX按路径段匹配，`/api`不会匹配`/apian`。
3. 保存草稿并模拟域名/路径。模拟不访问上游，也不验证外网连通性。未保存内容可模拟；发布使用保存并审批的内容。
4. 提交生成候选快照。审批后资源发生变化需重新提交审批；生产由其他获授权管理员审批，发布人再发布。
5. 查看“待节点确认”和具体版本。只有执行器完成hash验证、落盘、加载及ACK后才显示对应确认。ACK表示曾确认该版本，不是实时节点存活保证。
6. 回滚按钮生成历史版本的草稿，重新校验、提交、审批和发布，形成新版本。目标或域名已不兼容时阻断，而不是悄悄换成新地址。不会删除站点业务数据。

修改资源登记或草稿不会原地修改已发布快照；运行中的目标删除被阻断。已有任何发布历史的域名都禁止直接释放，即使最新版本已不再引用，以避免旧节点离线持有快照时发生域名跨租户转移。本阶段不提供这类域名的平台转移入口；后续须建设节点撤除/退役流程后才开放。单节点ACK不能替代所有曾持有该域名配置的机器的撤除确认。

## 4. 节点授权

后台默认不启用内部节点接口。启用时，通过受保护的配置文件设置：

```yaml
siteplatform:
  node:
    id: edge-b
    token: ${SP_NODE_TOKEN}
    assignments: '100/实际站点ID,100/另一个站点ID'
```

token使用至少32字节随机值，由运维生成并以环境变量注入；不提交仓库、不写在命令行URL、不发给浏览器。assignments是服务端确定的租户/站点对，不能只凭请求头更换租户。当前只支持一个配置节点；增加多个节点前需要扩展节点注册、逐节点权限和部分生效汇总。

两个内部接口：GET `/site-platform/node/snapshots`、POST `/site-platform/node/ack`。它们不使用浏览器登录，Shiro仅放行这两个精确路径，控制器使用独立Bearer验证。外层Nginx限制为本机/可信私网，跨主机使用HTTPS。其余管理接口仍要求JEECG登录和平台权限。

`nginx-jeecg.conf`已补充阻断公网管理域名下的节点路径；同机执行器直接调用loopback后端。跨主机部署时应新增受私网来源限制的独立内部HTTPS入口，不能为方便访问删除公网阻断规则。本机未运行Linux Nginx，模板上线前仍须在服务器执行`nginx -t`。

## 5. 在服务器B安装执行器

沿用两服务器部署手册，A仍是MySQL/Redis，B增加此独立服务。B需要Node24运行时，此前“B无需Node”的说明仅适用于原JEECG静态前端+Java后台。不要用`apt install nodejs`后假定版本符合；检查`/usr/bin/node --version`，固定实际验收版本。

创建专用系统用户`jeecg-router`；代码放`/opt/jeecg/site-router/releases/<版本>`，`current`指向本次版本；写目录`/srv/jeecg/site-router`只允许该用户写入。部署复制本项目 `services/site-router`，无需npm install。

`/etc/jeecg/site-router.env`（root可读，0600；下列为模板，不能照抄token）：

```dotenv
SP_NODE_ID=edge-b
SP_NODE_TOKEN=由运维生成并替换的随机凭证
SP_CONTROL_URL=http://127.0.0.1:18080/jeecg-boot
SP_SNAPSHOT_FILE=/srv/jeecg/site-router/snapshots.json
SP_BIND=127.0.0.1
SP_PORT=18090
SP_POLL_MS=5000
SP_TIMEOUT_MS=30000
SP_MAX_BODY_BYTES=67108864
SP_EXTERNAL_PROTO=https
SP_ALLOWED_UPSTREAMS=
```

SP_CONTROL_URL只允许HTTPS或loopback HTTP，不能带凭证、查询参数或片段；若执行器和后台分机，用可信证书HTTPS内部入口。SP_ALLOWED_UPSTREAMS默认留空，拒绝上游解析到私网、环回或元数据IP；确有内部上游时运维可填写精确的`origin.example.com:8080=10.20.0.25`，逗号分隔，不支持通配符。该例外会将指定IP固定给目标连接，绝不向普通运营用户开放此环境配置。

复制 `deploy/production/application/site-router.service` 到`/etc/systemd/system/`，核对Node路径、用户与目录，然后：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now site-router
sudo systemctl status site-router --no-pager
sudo journalctl -u site-router -n 50 --no-pager
```

服务仅监听loopback，公网HTTPS由Nginx完成；参考`nginx-site-router.conf.example`另建业务域名server，不改后台域名配置。上传/续期证书、验证`nginx -t`后再reload，DNS A/AAAA指向B由运维执行。没有可控真实域名和证书时，只验证本机代理，不宣称公网已接管。

## 6. 故障与安全约束

- 默认每5秒串行拉取完整授权集合；成功空集合意味着撤除此节点全部路由。拉取失败保留最后有效配置，不将“无法获取”解释为空集合。
- 快照校验版本、摘要、站点、域名冲突及规则，并拒绝目标域名直接指向本节点托管域名的明显循环；先写临时文件并fsync，再同目录rename，Linux上fsync父目录后切换内存，最后ACK。落盘或验证失败不加载坏版本，重启从本地快照恢复。Windows开发环境不能通过Node可移植接口同步目录，因此只验证原子替换，不承诺断电时目录项持久性；Linux生产需在目标文件系统演练恢复。
- 本地快照包含目标地址和路由，不含节点密钥；保护其目录，hash只用于完整性核验，不替代控制面认证或文件权限。
- 若目录持久化失败，会撤销磁盘替换以保留上次可恢复版本，首次提交失败则移除候选文件。若存储故障连撤销也失败，日志记录` snapshot_recovery_failed`及人工恢复标记，尽可能保留previous备份；运维需先停止更新并核对文件再重启。故障注入测试不等于真实断电或损坏文件系统下的数据持久性证明。
- 目标每次连接重新解析和校验后固定IP；本阶段不做跨请求连接复用。证书正常校验，重定向不跟随，Cookie与Location原样返回。兼容问题通过上游公开URL配置解决，不能承诺通用HTML改写。
- 内部节点路径包含`/site-platform/node/`，在入口请求和上游路径拼接后均禁止转发，即使上游被运维批准为后台地址。上游提前结束响应时关闭未完成上传，避免遗留连接等待请求超时。
- 入口不信任客户端的代理头、租户和内部身份头；本阶段转发的来源IP为直连端地址，经Nginx时是Nginx地址。真实客户端IP及地域判定将在可信代理链阶段实现。
- 默认总请求/响应时限30秒、请求体64MiB；超过限制分别失败。统计尚未实现，stdout仅记录节点事件，不是访问统计。
- 数据库或后台故障时已发布的外部上游代理可继续；表单/API若依赖后台仍会失败，不作全业务可用承诺。
- 当前TLS材料由运维管理，平台尚不验证证书有效性。将节点已确认显示为“HTTPS已安全接入”属于错误验收。
- 本阶段无域名撤销实时广播或离线节点租约；域名释放、转移、节点退役必须先确认旧节点停止服务/移除快照，避免旧配置继续代理。生产接入前执行撤除演练。
- 同一平台不允许将已发布业务域名再作为代理上游，跨站点也一样；上游使用独立origin域名。发布阶段会串行校验完整期望配置集合，避免单个站点的链式代理冲突导致执行器拒绝整个快照集合。该限制是本阶段的明确策略，不提供跨站点代理链。

## 7. 验证命令及待验收项

工作区根目录：

```powershell
node --test --test-isolation=none services/site-router/test/*.test.mjs
```

Maven沿用JDK21、本地缓存与`Resource*Test,Routing*Test`，真实DB测试启用`JEECG_RESOURCE_DB_TEST=true`，密码仅注入进程环境。真实数据库测试必须创建独立随机测试库，不能用在线业务库做写入断言。

上线前另需真实公共DNS、有效证书、两个生产管理员分离审批、多租户真实账号、上游登录/上传兼容、容量与恢复演练。两台服务器没有入口高可用。

### 本地验证记录（2026-11-06）

- 最终Maven reactor打包成功，Resource*Test与Routing*Test共63项，0失败、0错误、0跳过；包含独立临时MySQL库及撤权并发回归。日志 `.runtime/routing-final-package.log`。
- 执行器最终回归22/22通过，覆盖受控上游、独立进程、离线重启、失败快照不激活、落盘失败恢复、保留路径拒绝与流式上传；未部署常驻本地执行器。
- 前端15项验证测试通过、定向ESLint无警告，完整生产构建成功（保留上游构建警告），日志 `.runtime/routing-frontend-build.log`。
- 安装第二份资源迁移前已备份本地库；新增5张路由表及管理员审批/发布权限。没有为验收发布或修改已有业务站点。
- 更新后的前后端均已重新启动，Frontend/Backend/Proxy就绪检查均200。未登录的管理路由请求直连及经前端代理均401；默认未配置的节点快照接口404。
- 浏览器确认已有站点“分流与发布”面板成功加载：无草稿/无发布历史、待验证域名禁用、草稿编辑与模拟入口可见，提交/审批/发布按状态禁用。未在用户业务站点保存测试草稿；完整生命周期由隔离数据库测试覆盖，真实浏览器多账号发布仍待验收。
- 本阶段禁止释放任何历史发布引用的域名，即使新版本已移除该域名；安全转移必须待节点退役/撤除协议实现。资源停用仅阻止后续发布，不即时撤除旧快照；租户失效会在下次成功拉取时撤除其授权快照。
- 真实DNS、证书、服务器节点部署、Linux文件系统断电恢复、Nginx配置检查、生产账号及负载尚未验收，没有切换公网流量。
