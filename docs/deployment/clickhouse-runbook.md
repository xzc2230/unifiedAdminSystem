# ClickHouse 统计接入运行手册

日期：2026-11-06。适用当前 JeecgBoot v3.9.5 项目；这是基础接入，不是完整统计平台或吞吐量验收。

## 1. 本轮交付

- 独立 Compose 项目 `jeecg-analytics`，同机部署 MySQL、Redis、Java、Nginx 时可选启用。
- 镜像固定 `clickhouse/clickhouse-server:25.8.24.21` 及摘要 `sha256:0fa332a9a05ce4138b16d883f9c9d124c8d9d81cf4e52046878d537558626e49`。
- HTTP 只绑定 `127.0.0.1:18123`，不发布9000端口；不要开放云安全组中的18123/8123/9000。
- 表 `jeecg_analytics.api_call`，使用UTC毫秒时间、月分区、7天TTL。字段仅有事件ID、完成时间、API ID、HTTP方法、HTTP状态、耗时毫秒。
- 独立writer仅INSERT、reader仅SELECT；bootstrap管理员只允许容器内部loopback连接。没有匿名default数据访问。
- 后端通过JDK HTTP客户端接入，不替换MySQL默认数据源，也不增加全局JDBC驱动。
- OpenAPI调用完成后入有界内存队列，后台批量写入；提供平台管理员统计状态与汇总接口。

未交付：新统计前端看板、PV/UV、页面SDK、代理请求统计、租户/站点自助统计、物化聚合表、持久消息队列、历史MySQL日志迁移、生产备份恢复及QPS验收。

## 2. 本地启动与配置

需要Docker Compose和PowerShell7。在工作区根目录运行：

```powershell
pwsh -NoProfile -File deploy/clickhouse/setup.ps1 -Action Setup
pwsh -NoProfile -File deploy/clickhouse/setup.ps1 -Action Status
pwsh -NoProfile -File deploy/clickhouse/setup.ps1 -Action Verify
```

Setup生成随机凭证，保存在 `deploy/clickhouse/.runtime/clickhouse.env`，该目录被Git忽略并限制访问权限。脚本不打印凭证；不要使用 `docker compose config` 的完整输出或把env文件上传仓库。

脚本仅管理自己的Compose项目和持久卷 `jeecg-analytics_clickhouse-data`。有数据卷但缺少原凭证时拒绝自动生成，须恢复原文件。重新Setup会重设受限账号的密码与直接授权，应仅在受控维护时执行；不提供通用表结构迁移。

本地 `deploy/local/services.ps1` 会读取其中六项后端变量：

```dotenv
CLICKHOUSE_ENABLED=true
CLICKHOUSE_ENDPOINT=http://127.0.0.1:18123
CLICKHOUSE_WRITER_USER=jeecg_writer
CLICKHOUSE_WRITER_PASSWORD=由Setup生成
CLICKHOUSE_READER_USER=jeecg_reader
CLICKHOUSE_READER_PASSWORD=由Setup生成的不同密码
```

原始bootstrap密码和哈希只供ClickHouse初始化，不注入Java。没有配置时Java默认关闭统计；不要求ClickHouse可达才能启动。配置启用但缺少凭证等格式错误会拒绝启动，以免静默使用错误账号。

已有后端须重新启动以加载配置：

```powershell
pwsh -NoProfile -File deploy/local/services.ps1 -Action Stop
pwsh -NoProfile -File deploy/local/services.ps1 -Action Start
```

部署到Ubuntu时可安装PowerShell7使用相同准备脚本，或由运维按env示例生成独立64位hex凭证和SHA256密码摘要、设置目录0700与文件0600，再运行相同Compose及初始化。生产步骤需在目标机验证，不能直接复制Windows路径。复制资源时保留shell/XML/SQL文件LF换行。

生产Java沿用 `application-server.yml` 及 `jeecg.env.example` 中的六项变量，环境文件限制权限。跨主机使用经过证书验证的私网HTTPS入口，Java客户端拒绝非loopback明文HTTP；不通过扩大同机模板的公网绑定实现跨主机访问。

## 3. Java配置与统计交付语义

配置前缀 `siteplatform.analytics.clickhouse`：

| 属性 | 默认值 | 含义 |
| --- | --- | --- |
| enabled | false | 是否启用 |
| endpoint | http://127.0.0.1:18123 | 固定受控服务地址，不接受浏览器输入 |
| capacity | 10000 | 有界事件队列，最大100000 |
| batch-size | 1000 | 每批最多10000，不能超过队列容量 |
| flush-seconds | 5 | 稀疏流量最长等待，范围1至60秒 |
| timeout-seconds | 5 | HTTP连接和整个响应的截止时间，范围1至30秒 |

后台每100毫秒检查一次，达到batch-size即发送；未满时到时间发送。只有单个写入工作器，不随积压无限创建任务。HTTP响应最多2MiB，失败与超时不把服务器错误正文、连接凭证暴露到浏览器。

重要边界：

- **尽力统计，无持久队列**。满队列拒绝新事件并累计rejected；进程崩溃或关闭会丢失未发送事件。计数仅限本进程生命周期。
- 写入成功计written；网络错误可能发生在数据库提交之后，因此记为failedOrUnknown，不自动重试。不能把它精确解释为丢失数，也不能声称恰好一次。
- 请求线程只尝试内存入队，不等待ClickHouse；统计故障不修改业务响应。关键审计继续使用原有链路。
- **原有OpenAPI同步MySQL调用日志保留**，本轮没有消除这部分数据库写入或既有日志敏感字段问题。查询缓存、索引、限流与签名加固仍需另做。
- 仅采集 `/openapi/call/*`，不采集普通管理接口。失败认证也可产生事件；无法定位已登记API时api_id为空。
- errors指HTTP状态大于等于400，server_errors指大于等于500；业务JSON返回失败但HTTP仍为200时不会计为HTTP错误。
- event_time为事件采集完成时间。duration_ms沿用过滤器中调用链耗时，不包含其后的MySQL日志写入及事件排队时间，不等于客户端完整网络延迟。
- 不记录AK/SK、请求头、IP、查询参数或正文。本模块不是API鉴权替代品。

## 4. 查询接口

后端上下文为 `/jeecg-boot`；经前端代理时为 `/jeecgboot`。

| 接口 | 作用 |
| --- | --- |
| GET `/openapi/statistics/status` | enabled、queued、inFlight、accepted、rejected、written、failedOrUnknown、lastSuccessEpochMillis |
| GET `/openapi/statistics/summary?start=<UTC毫秒>&end=<UTC毫秒>&apiId=<可选>` | 按API和方法返回calls、errors、server_errors、avg_ms及近似p95_ms/p99_ms |

两个接口同时要求平台 `admin` 角色和 `openapi:open_api_log:list` 权限，普通租户/站点用户不能查询全平台明细。没有新增匿名放行或原始SQL执行接口。

区间为左闭右开，必须正向、最长24小时；API ID最多64字符；SQL固定且使用参数绑定。每Java实例只允许1个并发汇总查询。reader profile固定只读、最多2线程、单查询256MiB、3秒、最多扫描1000万行和返回1000组，超限返回503而不是返回静默截断的聚合。高流量下24小时原始明细可能超限，先缩短窗口；长期/大跨度报表需要另建预聚合模型。

status的enabled不代表存储健康；结合队列积压、失败/拒绝计数和最近成功时间监控。禁用时summary返回503；未登录401，无相应权限403；不回传底层SQL错误。

## 5. 同机资源、保留与备份

Compose默认限制ClickHouse为2个CPU、3GiB容器内存、无额外swap，服务跟踪内存目标约2.5GiB，后台任务池受限。这是试运行预算，不能证明16GiB同机足够，也不保证API达到500或1000QPS。MySQL、Java和Redis需一起做资源预算和压测。

数据持久卷存于Docker主机；本地Docker Desktop实际占用其Linux磁盘。7天TTL依赖后台合并，不能期待到期立即释放空间。100GiB整机磁盘不能承诺持续全量高QPS明细保留；按实测字节/事件、业务量、合并空间和备份余量决定容量，监控磁盘水位。

首次生产部署前制定并实际演练备份恢复：

1. 关闭Java统计采集或暂停入口，停止ClickHouse服务，避免在线文件拷贝得到不一致备份。
2. 通过受控备份任务对专用数据卷做一致性备份，保存镜像摘要、配置和受保护账号配置；备份放异机/S3，不只存同一磁盘。
3. 恢复到独立测试卷/隔离Compose项目，使用对应镜像与原凭证验证表、行数、权限及汇总，再制定生产恢复步骤。

本次没有执行生产备份恢复演练；这些是运维要求，不是已验证恢复能力。不要使用 `docker compose down -v`，它会删除统计数据卷。

```powershell
pwsh -NoProfile -File deploy/clickhouse/setup.ps1 -Action Stop
pwsh -NoProfile -File deploy/clickhouse/setup.ps1 -Action Start
```

禁用采集：将受保护env中的CLICKHOUSE_ENABLED设false，重启Java，再停止ClickHouse。MySQL及关键审计保留。只停ClickHouse而保持Java采集启用时，将产生failedOrUnknown或rejected事件，不会自动回灌。

## 6. 本地验证记录（2026-11-06）

- 固定版本服务已启动，健康检查及HTTP ping通过，数据表初始化成功。
- 最终Maven package成功；76项测试0失败/错误/跳过，含真实ClickHouse批量写入/汇总/读写权限、Shiro角色与权限拦截、响应体超时及大小限制、队列失败隔离、原资源与分流的真实MySQL回归。日志 `.runtime/clickhouse-final-package.log`。
- 前后端已恢复，Frontend/Backend/Proxy就绪均200；统计status及经前端代理的summary未登录均401。
- 使用现有登记API发起无凭证只读请求得到401，未授权调用业务目标；运行中后端异步写入对应401统计事件，已确认1条。
- 部署Verify及Java集成测试产生少量synthetic probe/integration记录，本地统计表含测试数据，待TTL清理；不得将其当真实业务统计。
- 未进行生产部署、备份恢复、同机容量压测或前端统计看板验收。本轮变更尚未推送GitHub。

## 7. 官方协议依据

- HTTP接口、参数绑定与JSONEachRow：`https://clickhouse.com/docs/interfaces/http`
- 批量写入：`https://clickhouse.com/docs/best-practices/selecting-an-insert-strategy`

本轮采用客户端组批、等待数据库写入确认，不设置服务器异步插入，不使用未确认写入作为成功依据。
