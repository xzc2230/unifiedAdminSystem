# apivo 客户端接口部署记录

部署日期：2026-11-09。服务器：13.222.19.112。

## 发布结果

- 应用分支：`codex/package-api-migration`；拉取并部署提交 `845ed733cc3287bf86458c5bf67563926abbf519`。
- 前后端在服务器构建，通过后复制到 `/opt/unifiedAdminSystem/releases/20261109T093651Z-845ed733cc32`，由 `current` 指向该发布目录；使用现有 Docker Compose 启动。
- 中间件仍位于 `/opt/middleware`。MySQL、Redis、ClickHouse、后端和前端共五个容器健康。
- 后台：`https://www.attrnivo.com/`；客户端 API：`https://apivo.attrnivo.com`。客户端保持原始路径，不添加 `/jeecgboot` 或 `/package-legacy`。
- 本轮未部署本地尚未提交的 attribution 模块开发内容。本文和 Nginx 快照为部署记录文件，不改变已发布应用提交。

## 数据库

执行 `008-four-path-risk.sql`，新增四表：`pkg_app_device_name_blacklist`、`pkg_app_asn_blacklist`、`pkg_app_device_brand_blacklist`、`pkg_app_cpu_abi_blacklist`。

`pkg_app_manager` 新增五字段：`gaid_aid_threshold`、`aid_device_threshold`、`aid_gaid_switch_threshold`、`risk_window_seconds`、`referrer_blacklist_json`。最终数据库共 171 张表，其中 18 张 `pkg_` 表。此次没有填造租户映射、导入历史业务数据或预置黑名单规则。

迁移前停止后端并备份数据库、配置和持久文件，备份目录：`/opt/unifiedAdminSystem/backups/20261109T093649Z-845ed733cc32`。`database.sql.gz` 的 gzip 完整性和 SHA256 校验通过。增量 SQL 及校验值保存在 `/opt/middleware/migrations/845ed733cc3287bf86458c5bf67563926abbf519/`。

回滚不能仅切换旧二进制：应先停写，核对 `PREVIOUS_RELEASE`、备份配置及数据库兼容性；如需恢复数据库，须先保存发布后的新增业务数据，再决定恢复范围，避免覆盖业务新增记录。

## 域名与代理

生产 API 虚拟主机的已验证快照：`deploy/package-api/nginx-apivo.conf`。它是现有 `/opt/unifiedAdminSystem/nginx.conf` 的附加虚拟主机，不能覆盖管理站点配置。

- HTTPS 代理至 `http://127.0.0.1:18080/jeecg-boot/package-legacy/<原路径>`；保留方法、参数和请求体。
- HTTP 用 308 转到 HTTPS，保留 POST 方法。客户端应直接配置 HTTPS，避免依赖客户端跟随跳转。
- 支持任意一至四段路径，以及专门的五段 `/v2/api/banner/list/{随机串}`。仅开放 GET、POST；第 12 类由后端进一步限制为 POST JSON。
- API 域名屏蔽后台、文档、上传等保留路径；管理域名继续拒绝 `/jeecgboot/package-legacy/**`。
- 仅信任 Cloudflare 官方 IPv4/IPv6 网段提供的 `CF-Connecting-IP`，再向后端传递访客 IP。快照中的 CIDR 获取于部署当天；Cloudflare 调整网段后需要重新核对更新。后端仅信任本机代理。
- apivo 源站证书有效至 2027-02-07，公网及源站直连均已验证 TLS；`certbot.timer` 为 active，现有续期 deploy hook 在 Nginx 检查通过后 reload。此次没有额外执行 apivo 的续期演练。
- 修改前 Nginx 备份：`/opt/unifiedAdminSystem/backups/apivo-nginx-20261109T093900Z`。

## 12 类接口实测

使用独立临时应用，通过公网 HTTPS 检查请求、响应字段、Base64/AES 解码、重定向及部分关闭分支。临时应用只按本轮唯一包名、密钥和作者精确删除，最终验证无测试应用残留；未修改真实应用配置。

| 序号 | 公网路径 | 验证方式与结果 |
| --- | --- | --- |
| 1 | `/system/{随机串}` | GET/POST；id Base64；整体 JSON Base64，url 再次 Base64，status/url/code 正确 |
| 2 | `/v2/api/user/login` | GET/POST；username/password；data 中 show/path/useAF 正确。这是客户端配置接口 |
| 3 | `/v2/api/retry/{随机串}` | GET/POST；packName/af_id；开启时 302 和 Location 正确 |
| 4 | `/v2/api/banner/list/{随机串}` | GET/POST；systemName/systemID；字符串结果正确 |
| 5 | `/dynamic/{随机串}` | GET/POST；id；app_cache/app_id/app_limit 正确 |
| 6 | `/v2/api/check/url` | GET/POST；tokenID/baseTime 指定参数名；event/canLoad/load/canAppend 正确 |
| 7 | `/app/start` | GET/POST；user_id/user_token；imgs/isApp 与时间戳摘要正确 |
| 8 | `/home/index/jsp` | GET/POST；pack_a/data_a；Base64 JSON 和包名后缀正确 |
| 9 | `/{单段}` | GET/POST；index；Base64 JSON、时间字段和包名后缀正确 |
| 10 | `/{两段路径}` | GET/POST；timestamp；Base64 JSON 的 pid/origin/spid/mark 正确 |
| 11 | `/{三段路径}` | GET/POST；参数值中的包名；AES 解密后 code/data/byme/ps 正确 |
| 12 | `/{四段路径}` | POST JSON，头 x-pkg，体 {t,p}；XOR 设备载荷和 AES 响应正确 |

另外验证：第 1/12 类关闭状态，API 域名后台及其他保留路径返回 404；后台登录、213 个 JS/CSS 引用、同源 API 配置、租户 1000 应用查询和 13 个子菜单均通过。

服务器独立 MySQL/Redis 测试环境运行后端 181 项测试，0 失败/错误/跳过；前端 15 项测试通过，测试容器随后删除。生产测试只证明上述协议和测试场景，不代表所有地区、归因、历史风控规则或真实客户端设备组合均已验收；这些仍取决于业务应用配置和真实数据。

非敏感验证脚本及日志目录：`/opt/src/unifiedAdminSystem/.runtime/aws-build/845ed73/`。公网协议结果包含 `APIVO_12_PROTOCOLS_SUCCESS`，最终检查包含 `FINAL_DEPLOYMENT_VERIFIED`。

## 与用户文档不同之处

1. 第 12 类按源代码实际协议部署：`Content-Type: application/json`，请求头 `x-pkg: 包名`，body 为 `{t,p}`。`p` 是设备 JSON UTF-8 字节用应用密钥循环 XOR 后再 Base64；响应为 AES 密文 Base64，解密 JSON 含 code/data/byme/ps。没有改成旧文档所述 query/UA 拼接模式。
2. 第 12 类 GET 被现有 JEECG 异常处理器包装成 HTTP 200、`success=false`、业务 `code=405`；非 JSON POST 同样 HTTP 200、业务 `code=500`、message 含 Content-Type。不能只按 HTTP 200 判断成功。
3. 第 7 类 token 是原始毫秒时间戳的 MD5；返回 timestamp 是该毫秒值减 65535。
4. 第 8/9 类 Base64 后缀是去掉点号的包名；第 9 类实际字段为 `date_time`，而非文档的 `data_time`。单段路径不限定数字。
5. 第 11/12 类采用 AES/ECB/PKCS5Padding，应用密钥须为 16/24/32 个 UTF-8 字节。用户文档列出的默认字符串与源实现不同，本次未替换应用密钥、未在仓库记录生产密钥；应用名称为空时还需核对服务器默认密钥配置，不能假定示例字符串有效。

迁移实现细节见 `docs/migration/package-api/four-path-migration.md`；早期 `client-document-audit.md` 中“尚未公网启用”等描述为初次排查历史，本记录取代其部署状态结论。
