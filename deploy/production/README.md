# 两台服务器部署配置

操作步骤与参数解释见 `docs/deployment/two-server-deployment.md`。

- `middleware/`：在中间件服务器使用，部署 MySQL、Redis。
- `application/`：在应用服务器使用，提供 Spring 配置、环境变量示例、systemd 和 Nginx 模板。

示例私网 IP、域名、密码和镜像必须按文档替换；生产密码不提交到 Git。
这些是待目标服务器验收的模板，不能将本地 Windows 构建成功等同于两台 Linux 服务器部署通过。

2026-11-11 前端缓存迁移、AI 开关、资源缓存验收与回退步骤见 `docs/deployment/frontend-performance-2026-11-11.md`。
