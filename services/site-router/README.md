# Site router

独立的域名/路径反向代理执行器。配套管理端位于 JEECG 的 `siteplatform` 模块，接口与部署见 [运行手册](../../docs/deployment/routing-publication-runbook.md)。

要求 Node.js 24，无第三方运行依赖。不要将开发期 Vite 服务用作生产站点入口。

```powershell
node --test --test-isolation=none services/site-router/test/*.test.mjs
```

准备运行手册中的进程环境变量后，在此目录运行 `node src/main.mjs`。节点凭证必须与后台单节点配置匹配，站点授权由后台固定。

支持 HTTP/HTTPS 上游、流式请求与响应、域名/路径段匹配、完整快照原子替换及本地恢复。默认拒绝私网上游；只有运维可以按精确主机、端口和IP配置例外。代理不跟随重定向，不自动改写Cookie/Location或HTML，不提供任意转发目标。

本阶段不支持 WebSocket、无限时长SSE、地域/用户组/权重分流、健康切换或访问统计；节点 ACK 是版本确认，不是实时健康信号。Windows开发验证不承诺断电后的目录项持久性；Linux生产在目录fsync成功后才加载并确认。
