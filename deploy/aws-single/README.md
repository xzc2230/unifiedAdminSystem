# AWS 同机 Docker Compose 部署

## 当前管理域名（2026-11-09）

后台入口为 `https://www.attrnivo.com/`，源站 `13.222.19.112`。www 目前经 Cloudflare 代理访问源站，域名 HTTP 自动跳转 HTTPS。仅配置 www，未修改根域名入口。源站证书位于 `/etc/letsencrypt/live/www.attrnivo.com/`，挂载至前端容器；`certbot.timer` 自动续期，deploy hook 校验后重载 Nginx。

`JEECG_PUBLIC_URL=https://www.attrnivo.com`；前端运行时 `VITE_GLOB_DOMAIN_URL=/jeecgboot`，避免域名切换产生混合内容请求。`publish.sh` 已保留该同域设置，`verify.py` 接受同域运行配置。当前服务器 Compose 和 Nginx 包含证书、ACME 挂载与 HTTPS 配置，本目录首次初始化模板不含已签发证书；不要用模板直接覆盖运行配置。

变更前配置备份在 `/opt/unifiedAdminSystem/backups/domain-20261109T080040Z`。TLS、HTTP 跳转、213 个静态资源、域名下真实管理员登录、菜单和租户1000应用查询均验证通过。后续历史部署记录中的 IP 地址为首次部署信息。

目标主机：`ubuntu@13.222.19.112`，区域 `us-east-1`。本次从 GitHub `xzc2230/unifiedAdminSystem` 的 `main` 拉取提交 `81ee2102c884660a2c703cc67f564351c5eae7cb`，在服务器内完成构建。

## 目录与服务

| 目录 | 用途 |
| --- | --- |
| `/opt/src/unifiedAdminSystem` | Git 源码与构建输出 |
| `/opt/src/unifiedAdminSystem/.runtime/aws-build` | 构建、初始化和发布日志 |
| `/opt/unifiedAdminSystem/compose.yaml` | 后端、Nginx 前端；可选 site-router |
| `/opt/unifiedAdminSystem/releases/<UTC时间>-<Git提交>` | 同次构建的 JAR、前端 dist、分流执行器源码、SHA256SUMS |
| `/opt/unifiedAdminSystem/current` | 当前发布版本符号链接 |
| `/opt/unifiedAdminSystem/data` | 上传文件、日志、工作目录与执行器快照 |
| `/opt/middleware/compose.yaml` | MySQL、Redis、ClickHouse |
| `/opt/middleware/data` | 三项中间件持久化数据 |
| `/opt/middleware/secrets` | 初始化凭证，root 可读 |

当前入口：`http://13.222.19.112/`。Nginx 监听 TCP 80，后端监听 `127.0.0.1:18080`；MySQL、Redis、ClickHouse 分别只映射 `127.0.0.1:3306`、`:6379`、`:18123`。应用容器使用 Linux host 网络，与现有生产模板中的回环地址一致。Docker 开机启用，五个运行服务均配置 `restart: unless-stopped`。

没有域名和证书时仅提供 HTTP；配置域名和 HTTPS 后，需同步修改 Nginx、前端运行时地址、`JEECG_PUBLIC_URL`。数据库、Redis、后端和 ClickHouse 无需公网安全组规则。

## 首次初始化（已执行，不要重复）

本目录的配置和脚本需位于服务器源码目录下；`initialize.sh` 会将它们复制到运行目录。首次部署前确认服务器端源码已通过 `git pull --ff-only` 更新。

```bash
sudo bash /opt/src/unifiedAdminSystem/deploy/aws-single/initialize.sh
```

初始化脚本仅允许空环境，检测到凭证或既有数据库时拒绝执行。它导入上游基础库及两份 `db/site-platform/*.sql`，创建仅有业务库 CRUD 权限的应用账号，生成独立随机密码，替换 `admin` 密码，冻结其余示例用户，停止示例定时任务。ClickHouse 使用现有仓库的初始化脚本和表级读写账号；后端已启用 ClickHouse 统计接入。

读取管理员登录信息：

```bash
sudo cat /opt/unifiedAdminSystem/admin-credentials.txt
```

请勿将该文件、`application.env`、`site-router.env`、`clickhouse.env`、中间件 secrets 或 SSH 私钥提交到 Git。

## 更新：服务器拉代码、构建、复制、Compose 发布

```bash
cd /opt/src/unifiedAdminSystem
bash deploy/aws-single/build.sh
sudo bash deploy/aws-single/publish.sh
sudo python3 deploy/aws-single/verify.py
```

`build.sh` 先 `git pull --ff-only`，然后分别使用 Maven 3.9.13 / Java 21、Node 24 / pnpm 12.6.0 容器构建服务器中的源码。前端沿用锁文件并禁用依赖安装脚本。后端打包跳过测试；构建成功不代表完整业务回归。

`publish.sh` 将服务器上的 JAR 和完整 dist 复制到 `/opt/unifiedAdminSystem/releases`，生成校验文件，切换 current，执行 `nginx -t`，再通过 Compose 重建并等待健康。运行镜像拉取并验收后写入 `.env` 的不可变摘要。前端上游 postBuild 不读取 `.env.production.local`，所以发布脚本还会修改专门用于部署配置的 `_app.config.js`，验证公网 API URL 并关闭微前端模式。

发布涉及短时中断。发布前备份数据库、配置和上传文件。脚本不会自动执行增量数据库迁移；后续迁移需单独审核和执行。`PREVIOUS_RELEASE` 记录上一 current 目标；应用回滚需同时核对数据库兼容性，不能直接恢复旧数据库覆盖新数据。

## 运行检查

```bash
sudo docker compose --project-directory /opt/middleware ps
sudo docker compose --project-directory /opt/unifiedAdminSystem ps
sudo docker compose --project-directory /opt/unifiedAdminSystem logs --tail 100 backend
sudo python3 /opt/src/unifiedAdminSystem/deploy/aws-single/verify.py
```

验收脚本检查静态资源、深层路由、验证码、真实密码登录、已认证菜单、未认证拒绝、关闭入口，以及 ClickHouse 读账号和后端聚合查询。验证码保持开启，脚本通过本实例的 Redis 读取本次测试生成的验证码，不输出密码或令牌。

`site-router` 使用 `routing` profile，默认不启动。后台节点授权需要真实的租户/站点 assignments，业务域名还需 DNS 和 Nginx/TLS 配置；这些条件没有提供，本次不创建虚构业务站点。配置完成后通过 `--profile routing` 启动，并验收真实路由。

## AWS 网络

安全组：`launch-wizard-19` / `sg-0458a840682342491`。用户已在 AWS 控制台放行 HTTP，公网入口现已验收成功。后续调整访问范围时，将 TCP 80 入站来源设置为实际访问网段。实例没有配置 IAM 运维角色，单凭 SSH 无法更改 AWS 安全组。

持久化目前位于同一台服务器，尚未配置异机备份或高可用。备份至少覆盖 MySQL、上传文件、应用环境配置；ClickHouse 的统计数据按仓库配置保留七天。

## 本次验收记录（2026-11-06）

发布版本 `20261106T104620Z-81ee2102c884`。服务器内五个容器健康；153 张业务库表，含 10 张站点/分流表。212 个首页引用的 JS/CSS 资源通过，验证码、真实管理员登录、用户信息与菜单、统计状态和后端 ClickHouse 汇总查询通过。未登录用户接口返回 401，注册、内部节点与调试入口按配置拒绝。容器重启并重新创建后恢复健康。

构建日志中前后端均成功；后端构建跳过测试，未执行全量业务回归。用户放行安全组后，公网首页 HTTP 200，212 个引用的 JS/CSS 资源全部通过，生产 API 地址及深层路由通过，验证码业务成功，未登录访问用户接口返回 401；浏览器已显示登录页面，未捕获浏览器错误或警告。浏览器中未重复输入管理员凭证登录；实际登录和认证后菜单已通过服务器验收脚本验证。没有域名/证书和真实站点授权，不宣称 HTTPS 或业务分流已经启用。
