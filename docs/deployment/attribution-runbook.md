# 归因管理运行说明

归因管理作为独立 Maven 模块 `jeecg-module-attribution` 随 JEECG 主服务运行，前端入口为一级菜单“归因管理”，地址 `/attribution`。页面只有应用列表和新建/编辑弹窗，保存成功后配置立即生效。

## 数据库和菜单安装

使用 MySQL 8，先备份目标数据库。增量业务表位于：

`upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-attribution/src/main/resources/db/attribution/001-schema.sql`

菜单和按钮权限位于 `deploy/attribution/002-menu.sql`。用 `mysql --default-character-set=utf8mb4` 导入，或在 PowerShell 中设置进程级 `MYSQL_PWD` 后执行：

```powershell
./deploy/attribution/install.ps1 -Database 'jeecg-boot' -User '<数据库用户>' -Server '127.0.0.1' -Port 3306
```

脚本只创建独立业务表和归因菜单，不清空旧表、不删除现有业务数据。重复执行保留角色授权关系。增量 SQL 不会自动运行；仅更新 JAR 不会完成建表或菜单安装。

角色管理员在 JEECG 角色授权中添加“归因管理”和所需按钮权限。脚本可通过 `-RoleId '<已有角色ID>'` 显式为该角色授予本模块全部六项权限，适用于可信配置管理员；普通运营和只读角色应在界面按最小权限授权。权限变更后重新登录。

| 权限 | 用途 |
| --- | --- |
| attribution:application:list | 应用列表、条件展开、配置读取 |
| attribution:application:add | 新建应用 |
| attribution:application:edit | 修改已授权应用 |
| attribution:diagnostics:view | 脱敏归因排查 |
| attribution:security:manage | 地址安全管理 |
| attribution:integration:manage | 受控接入、主体凭据及归因证据 |

管理用户必须属于当前有效租户，并拥有对应应用的授权；拥有菜单不能绕过应用数据权限。归因模块不依赖应用包管理的旧用户映射。

## 应用配置

启动时导入 `deploy/attribution/attribution.properties`，例如添加 `--spring.config.import=optional:file:./deploy/attribution/attribution.properties`。配置前缀为 `attribution.binding.v12`。

| 环境变量 | 默认值或要求 |
| --- | --- |
| ATTRIBUTION_ENABLED | false，仅控制公开归因流量 |
| ATTRIBUTION_ENABLED_APPLICATIONS | 获准应用 code，逗号分隔；空集合不开放任何应用 |
| ATTRIBUTION_ALLOWED_HOSTS | 获准的精确主机名，逗号分隔；保存也需要域许可 |
| ATTRIBUTION_HMAC_KEY | 至少 32 字节的外部密钥，无内置生产值 |
| ATTRIBUTION_SUBJECT_RPM / ATTRIBUTION_APP_RPM | 每主体 60 / 每应用 1000 次每分钟 |
| ATTRIBUTION_ADMIN_RPM | 每管理用户 120 次每分钟 |
| ATTRIBUTION_CREDENTIAL_TTL_SECONDS | 3600 |
| ATTRIBUTION_TOKEN_TTL_SECONDS | 600 |
| ATTRIBUTION_TRUSTED_EVIDENCE_SOURCES | 空集合；非空真实媒体字段只允许来自已核实的接入源 |

部署方应根据实际容量调整限流和时效。密钥不写入 Git、命令行、普通日志或前端环境变量。上述默认参数是可配置保护值，未作为生产性能承诺。

新建时填写应用名、渠道名和必填 HTTPS 兜底 URL。Offer 可为空；有 Offer 时至少一条权重大于 0。每条 Offer 独立设置条件，所有匹配且可用的正权重条目共同参与抽选，权重不要求合计 100。0 权重条目保留展示但不分配新点击。

初始仅开放操作系统条件。国家条件必须有可靠来源才开放，不能使用任意客户端查询参数或未经核验的转发头。未知操作系统或国家既不满足“是”，也不满足“不是”。

URL 必须使用获准域；未批准域的保存会提示联系管理员。域许可与地址撤销在服务端受控维护，不在普通编辑弹窗额外填写。配置版本和幂等键由系统维护，冲突时保留输入，重新读取后再人工合并修改。

## 客户端解析契约

接口为 `POST /jeecg-boot/api/v1/attribution/resolve`，通过 TLS 网关对外暴露。请求需要独立主体认证 Header，以及 JSON 中的 applicationCode 和 resolveToken。可选 applicationId 仅用于一致性核验，不能替代应用编码。

resolveTokenType 省略时为 ATTRIBUTION_TOKEN；另支持 DEVICE_ID 和 INSTALL_ID。所有 ID，包括雪花安装 ID，都必须使用 JSON 字符串，不能使用 number。

成功响应平铺以下九字段：clickRef、resolveToken、applicationCode、promotionCode、resolutionState、parameterSource、destinationUrl、afLinkParams、fbLinkParams。它不使用后台管理接口的 Result 包装。

真实令牌读取原快照，状态为 PENDING/MATCHED/UNMATCHED，参数来源为 REAL。无效或过期令牌拒绝，不自动转模拟。设备或安装模式返回稳定的 FALLBACK/SIMULATED 参数，设备标识不用于读取真实媒体数据，也不作为认证凭据。

原点击目标失效后只降级至当前同应用默认地址，不重新抽选其他 Offer，原广告字段和历史决策保持。没有安全目标时返回 503；HTTP 200 的 destinationUrl 保证非空。

| HTTP | 含义 |
| --- | --- |
| 400 | 参数或配置非法 |
| 401 | 主体凭据或归因令牌无效/过期 |
| 403 | 主体、应用、点击绑定或权限不符 |
| 404 | 应用或真实记录不存在 |
| 409 | 配置版本或幂等冲突 |
| 429 | 限流，按 Retry-After 有限退避 |
| 503 | 功能未启用、无安全目标或强依赖不可用 |

令牌和设备明文不能进入普通访问日志、追踪载荷或响应缓存。不要把独立主体凭据、归因令牌放到查询串，也不要将长期共享管理密钥写入 App。

## 受控服务端接入

以下路径均位于 `/jeecg-boot/attribution-admin` 下，使用 JEECG 管理登录 `X-Access-Token` 和宿主标准租户头 `X-Tenant-Id`。服务端会核验有效租户成员关系，不能只凭请求头取得权限。不能直接在移动客户端保存管理凭据。`{app}`、`{channel}` 来自配置保存响应的字符串 ID。

| 方法和路径 | 权限及用途 |
| --- | --- |
| POST `/applications/{app}/subjects` | integration:manage，绑定可信主体及标识并签发短期主体凭据 |
| POST `/applications/{app}/channels/{channel}/clicks` | integration:manage，首次点击快照及令牌 |
| POST `/applications/{app}/clicks/{click}/evidence` | integration:manage，版本校验并追加真实状态证据 |
| POST `/applications/{app}/safety` | security:manage，独立撤销 URL 或 Offer |
| POST `/applications/{app}/grants` | integration:manage 且为应用所有者，为当前租户成员授予本应用读写权限 |
| GET `/applications/{app}/clicks/{click}/diagnostics` | diagnostics:view，读取决策及版本，不返回凭据或媒体明文 |

主体请求示例：

```json
{"subjectId":"verified-subject-001","identifiers":[{"type":"INSTALL_ID","value":"1930000000000000001"}]}
```

返回的 `result.credential` 由可信后端通过受保护通道交付给该主体。客户端使用 `Authorization: Bearer <credential>` 调用公开 resolve。标识和主体关系必须由接入后端先核实，不能把任意客户端自报身份当成核实结果。

点击请求示例：

```json
{"subjectId":"verified-subject-001","clickRef":"owned-click-001","entryKey":"owned-entry-001","afLinkParams":{},"fbLinkParams":{}}
```

返回 `result.resolveToken`；重复 clickRef 或 entryKey 复用首次决策，不延长已签发令牌时效。真实参数缺失时使用空对象。携带非空媒体参数需要额外提供已配置的 `evidenceSource`，部署方必须先完成该来源的事实核验。

公开请求示例：

```json
{"applicationCode":"<保存返回的应用编码>","resolveToken":"<点击返回的令牌>"}
```

安装模式改为 `{"applicationCode":"<应用编码>","resolveTokenType":"INSTALL_ID","resolveToken":"1930000000000000001"}`；主体凭据仍然必传。

证据请求为 `{"expectedSnapshotVersion":"1","evidenceId":"owned-event-001","state":"MATCHED","reason":"已核实的自有事件"}`。状态允许 PENDING/MATCHED/UNMATCHED；纠正须提交新的 evidenceId 和当前版本，历史状态不覆盖。

撤销请求为 `{"kind":"URL","value":"https://example.com/offer","blocked":true,"reason":"停止投递"}`，kind 也支持 OFFER，此时 value 为 Offer ID。应用授权请求为 `{"userId":"<当前租户成员ID>","canWrite":false,"revoked":false}`；revoked=true 撤回授权。

JSON 请求最大 64 KiB，拒绝重复字段和过深嵌套。URL 域需要能解析为公网地址；DNS 失败时拒绝保存或投递，不抓取远端推广页面。

## 构建与验证

后端使用 JDK 21，在 `upstream/JeecgBoot/jeecg-boot` 中运行：

```powershell
mvn -B '-Dmaven.repo.local=D:/workspace/JEECG/.runtime/m2' -pl jeecg-module-system/jeecg-system-start -am package '-DskipTests'
mvn -B '-Dmaven.repo.local=D:/workspace/JEECG/.runtime/m2' -pl jeecg-boot-module/jeecg-module-attribution -am test '-DskipTests=false' '-Dtest=Attribution*Test' '-Dsurefire.failIfNoSpecifiedTests=false'
```

前端在 `upstream/JeecgBoot/jeecgboot-vue3` 中运行：

```powershell
node --test tests/attribution-form.test.mjs tests/attribution-api.test.mjs tests/attribution-components.test.mjs
pnpm build
```

### 2026-11-09 本地验收记录

- 后端归因模块 26 项测试、Shiro 入口隔离 1 项测试通过。完整启动模块 Maven 打包通过，前端 31 项测试及生产构建通过。
- MySQL 8 原生数据库上的建表、菜单及显式角色授权脚本可重复执行；实际验证 PowerShell 7 和 Windows PowerShell 5.1，中文名称正确，13 张归因表均为 InnoDB / utf8mb4_bin。
- 真实本地 HTTP 管理检查通过：空 Offer、新建、并发幂等、读取、展开、编辑、版本冲突、显式移除及无效租户拒绝。
- 在公开流量默认关闭时，管理保存可用，有效主体的公开解析返回 503。只对精确的本地测试应用白名单启用后，完成 17 项归因接口检查，包括 100 次重复点击、并发模拟首次创建、真实九字段、两类模拟、跨应用/主体拒绝、配置编辑不重抽、可信状态更新、URL 撤销降级、无安全地址 503 和过期令牌拒绝。普通服务日志未发现本次签发凭据、令牌和设备明文。
- 浏览器通过真实接口完成新建、编辑读取、逐条条件、权重 1/0、保存后刷新、0/多 Offer 展开、配置变化后刷新和服务端字段错误保留；390 px 宽度验证内部滚动及固定保存按钮。

上述是本机受控环境的功能验收，不是生产容量或外部媒体接入验收。国家来源仍关闭；未执行生产发布。

## 灰度和恢复

公开归因处理默认关闭。配置独立密钥、获准域和应用白名单后，先在受控环境验证独立主体、首次点击、真实解析、两类模拟、重复并发和地址撤销，再按应用启用。管理保存不需要额外发布操作。

应用商店透传、实际深链、第三方媒体可信证据、国家数据、生产保留期、密钥轮换和容量目标都需要实际接入配置和验证。受控本地调用成功不代表这些外部能力已经开通。

恢复时关闭公开入口或缩小白名单，保留已提交快照和配置。程序版本回退必须能够读取已提交的数据结构；不删表、不清空历史，不通过恢复旧配置复活已撤销 URL。
