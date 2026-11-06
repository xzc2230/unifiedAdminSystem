# JEECG 版本选择记录

核对日期：2026-11-05（Asia/Shanghai）。

2026-11-06 复核：官方 Git 远端已存在 v3.9.6 标签，提交为 `49c29032e18df784f1a75123d90647bc8f74cedb`。下表保留前一天的网页观测，不能继续作为“3.9.6 无标签”的依据。本次按用户确认接入 v3.9.5，完整提交为 `e3b9dc0aefe1943d9772b026f64ed671a7c82802`；尚未完成两个版本的源码差异与回归比较，不宣称 3.9.5 已被证明优于 3.9.6。

## 选择

采用 **JeecgBoot 开源版 v3.9.5 + 同标签 Vue3 前端 + 单体管理后台 + 独立入口分流服务**，作为开发和集成验证基线。生产基线在组件补丁核验、依赖扫描、隔离验收及恢复演练后固定。

此选择面向当前方案：多站点后台、定制业务 API、配置发布审批、权限隔离、统计及测试环境 AI。不是对该版本已经完成生产安全验证的声明。

## 官方信息核对

| 项目 | 观测结果 | 来源 |
| --- | --- | --- |
| 官网及 main README | 已标识 3.9.6，README 日期为 2026-11-05 | 官网、main README |
| GitHub Release | 本次访问 Latest 为 v3.9.5；v3.9.6 Release 页面返回 404 | Release 列表及版本页面 |
| v3.9.5 日期 | 标签 README 标记 2026-08-27 | 标签 README |
| 标签提交 | Release 显示短提交 e3b9dc0；完整 SHA 需下载时解析并记录 | v3.9.5 Release |
| 后端 | 标签内 jeecg-boot/pom.xml 标记 3.9.5，Spring Boot 4.1.0，Java 编译目标 17 | 标签 POM |
| 前端 | 标签内 package.json 标记 3.9.5；@jeecg/aiflow 为 3.9.5-beta | 标签 package.json |
| 根许可证 | 标签 LICENSE 为 Apache License 2.0 | 标签 LICENSE |

官网版本标识与可核验的 GitHub Release 存在差异，不能将 main 的最新代码视作已验证的固定发布基线。未找到 v3.9.6 Release 不代表它在所有渠道均不存在；正式标签、发布说明及依赖齐备后再评估。

## 选择依据与替代项

- v3.9.5：存在可固定标签，发布说明包括 OpenAPI 调用日志与权限改进，以及接口、跨租户、AI 工具和脚本相关加固，适合当前需求的验证起点。
- v3.9.6/main：列入后续评估，当前不直接用浮动主分支建基线。
- v3.9.3：是 Spring Boot 4 升级版本；既然没有旧项目兼容负担，优先使用包含后续修正的 3.9.5。
- v3.8.x/Boot 2 路线：本项目不因版本较老就假定更稳定；回退需额外处理能力差异与后续安全修复迁移，目前没有采用理由。
- 商业版本：当前首期以定制业务与受控发布为主，先不建立商业设计器或 BPM 依赖。未来如引入特定组件，单独核对功能、源码交付及授权。

后台采用单体是本项目的架构选择，目的是减少首期部署和故障排查复杂度；分流执行服务仍独立，保持后台故障不阻断已发布访问的边界。

## 依赖与生产准入

1. 前后端固定同一标签，下载后记录完整提交 SHA；保留并审查锁文件，构建产物记录依赖与摘要。
2. 建议先用 Java 21 验证。标签 POM 的编译目标为 17，并标注支持 17/21/24/25；实际启动、AI 与组件兼容性仍需测试。
3. 沿用标签声明的 Spring Boot 4.1.0，首轮不独立替换核心框架版本；依赖修复需在可复现分支验证。
4. 前端 AI Flow 依赖含 beta 标识，AI Flow 先限定测试环境；正式 Release 标签不代表全部依赖均为非预览组件。
5. v3.9.5 发布说明明确写到积木报表未授权导出问题 CVE-2026-58375 的修复将在其后续版本生效。上线前核对实际报表组件修复版本并验证；未解决时禁用相关报表功能及接口，不以隐藏菜单代替。
6. 根 LICENSE 不代表每个外部 JAR、前端包或商业附加组件均使用相同许可证；源码接入时建立依赖及授权清单。
7. 版本固定后复查发布后的安全修复，验证租户隔离、接口授权、凭证脱敏、发布审批、节点失败和配置恢复。记录尚未解决的问题，再决定生产版本。

## 本次完成范围

选型当天完成官方公开资料核对。2026-11-06 已下载源码、解析完整提交、安装前端依赖并完成后台打包；后续运行状态见 `local-runbook.md`。尚未完成漏洞扫描与生产验收。

## 来源

- 官网：https://www.jeecg.com/
- main README：https://raw.githubusercontent.com/jeecgboot/JeecgBoot/main/README.md
- Release 列表：https://github.com/jeecgboot/JeecgBoot/releases
- v3.9.5 Release：https://github.com/jeecgboot/JeecgBoot/releases/tag/v3.9.5
- v3.9.6 Release 核对入口：https://github.com/jeecgboot/JeecgBoot/releases/tag/v3.9.6
- v3.9.5 README：https://raw.githubusercontent.com/jeecgboot/JeecgBoot/v3.9.5/README.md
- 后端 POM：https://raw.githubusercontent.com/jeecgboot/JeecgBoot/v3.9.5/jeecg-boot/pom.xml
- 前端清单：https://raw.githubusercontent.com/jeecgboot/JeecgBoot/v3.9.5/jeecgboot-vue3/package.json
- LICENSE：https://raw.githubusercontent.com/jeecgboot/JeecgBoot/v3.9.5/LICENSE
