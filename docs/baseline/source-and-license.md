# 源码与许可证基线

核验日期：2026-11-06。以下是仓库许可证与依赖来源盘点，不替代附加组件授权确认。

- 仓库：`https://github.com/jeecgboot/JeecgBoot.git`
- 标签：`v3.9.5`
- 完整提交：`e3b9dc0aefe1943d9772b026f64ed671a7c82802`
- 本地目录：`D:/workspace/JEECG/upstream/JeecgBoot`
- 本地分支：`work/baseline-v3.9.5`
- 根目录 LICENSE 与后端 LICENSE：Apache-2.0。
- 前端 `jeecgboot-vue3/LICENSE`：MIT；发布时保留各部分相应声明。
- 同日远端可见 `v3.9.6`，提交 `49c29032e18df784f1a75123d90647bc8f74cedb`；没有擅自切换已确认基线。

## 唯一已跟踪源码差异

前端 `pnpm-lock.yaml` 的 1440 处 `https://registry.npmmirror.com/` 下载前缀替换为 `https://registry.npmjs.org/`。包版本及 integrity 摘要不变，保持 pnpm 供应链校验开启，冻结锁文件安装成功。

- 原文件 SHA256：`1CAED12D6488174C7DC7DD3FBC79A7DEB0BF6003C76CAE9C2E1DDF7563D39FD6`
- 调整后 SHA256：`AD21E7E247E7D88038CC370A57F4F8E4092CF8CC0ECF83B8451051F2DD455728`
- 前端本机 `.env.development.local` 已被上游忽略，设置独立本地端口及代理。

## 附加组件清单与限制

| 组件 | 源码证据 | 当前结论 |
| --- | --- | --- |
| Online 表单 | `jeecg-system-biz/pom.xml` 的 `org.jeecgframework.boot3:jeecg-online` | 以外部依赖提供；不能由根许可证推定其全部源码与授权 |
| 积木报表 | `jimureport-spring-boot4-starter`，父 POM 版本 2.5.1 | 已在构建中使用，生产前核验组件许可证及修复状态 |
| 积木 BI | `jimubi-spring-boot4-starter`，版本 2.5.0 | 外部依赖，具体设计器权限和授权单独确认 |
| ChatBI | `jimuchatbi-spring-boot4-starter:1.0.0` | 外部依赖，未作为首期自然语言变更入口 |
| AI Flow 前端 | `@jeecg/aiflow:3.9.5-beta` | 预览依赖，仅测试验证 |
| AI 后端 | `jeecg-boot-module/jeecg-boot-module-airag` | 源码可见，仍需受控工具与环境隔离 |

实际已解析的传递依赖还没有逐包完成许可证与漏洞审查，不将该盘点标记为完整 SBOM。
