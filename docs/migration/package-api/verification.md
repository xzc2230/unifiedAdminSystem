# 实施验证记录

日期：2026-11-09。本文区分静态清单、构建、定向测试和实际运行证据；它们不等于生产数据或所有业务分支已经验收。最终状态见implementation-status.md。

## 数据及应用层

- 独立模块jeecg-module-package，12套业务Mapper/XML、领域类型；14张pkg表包含12业务表和2归属映射表。开发DDL根据源码推断，正式部署前须与源实库对照。
- PackagePersistence*Test：5项通过，覆盖Mapper绑定、显式原ID插入、真实MyBatis插入/回读/事务回滚；.runtime/package-mysql/mybatis-live.log。
- Task2的Package*Test：40项通过、0跳过/失败/错误，含真实MySQL；.runtime/package-mysql/task2-all-live.log。独立复审见task-2-review.md。
- Task3复审修复后的Package*Test：159项，156通过、3跳过、0失败/错误；.runtime/package-task3-review-fixes.log。跳过为2项按环境启用的MySQL测试和1项受操作系统权限限制的符号链接测试。
- MySQL测试另行启用运行：2项通过、0跳过；.runtime/package-mysql/task3-live.log。接收人混合字符集查询实库回归另1项通过；task3-transfer-targets.log。测试库仅127.0.0.1:13317/package_migration_test，凭据不输出、不提交。
- Task3独立复审见task-3-review.md。实际HTTP发现的模块异常状态改写问题已通过局部advice优先级修正；独立增量复审通过，6项覆盖回归通过，.runtime/package-task3-exception-green.log。
- 最终启用隔离MySQL的Package*Test完整定向门禁：161项、160通过、1跳过、0失败/错误，2026-11-09 13:40:28 BUILD SUCCESS；.runtime/package-final-all-live.log。唯一跳过为操作系统拒绝Java符号链接创建，真实HTTP的Windows junction边界已补验。
- 宿主base-core原有全量测试曾有DNS/loopback相关失败。新增定向测试通过不表示宿主全套测试通过。

## 实际宿主联调

- 最终候选宿主11模块reactor打包成功，跳过测试，记录.runtime/package-task3-exception-host-package.log。候选副本JAR已在18081启动，记录package-task3-exception-host-start.log。包含异常优先级修复，相关实际HTTP已重跑。
- 宿主使用隔离MySQL、Redis16390、本地替身18089；Geo、VAPID、NPM、ES和FB均未访问生产服务。没有发送真实邮件或推送。
- .runtime/package-http-smoke-report.json：17项通过，涵盖匿名管理拒绝、Base64/原始字符串/跳转/ANY方法/动态AES协议、PWA安全字段和日期、SDK重复提交、CORS、单/多文件上传、下载后删除、越界保护及有权管理查询/转移接收人。
- Windows junction越界通过真实HTTP验证：profile目录内链接指向外部测试文件，访问被拒绝；没有以单元测试中跳过符号链接的结果冒充验证。
- .runtime/package-protocol-live-report.json：4项通过，补验home编码后缀、app/start字段和摘要、单段协议日期、双段JSON配置；双段测试通过管理API创建临时应用并在finally删除。
- .runtime/package-management-live-report.json：14项检查通过。含11类实际XLSX导出（检查类型、ZIP文件头/目录和工作簿入口）、store禁用导出的HTTP404、独立统计筛选/分页无关金额及跨租户拒绝。后端实际HTTP三个报告合计35通过、0失败。

## 前端

- 本地开发前端13101通过进程环境代理18081，保留已有.env.development.local。NODE_PATH指向node_modules/.pnpm/node_modules；ESBUILD_BINARY_PATH指向隔离环境官方Windows二进制。
- 内置Browser控制工具没有可用执行接口，使用bundled Playwright及Chrome临时无痕上下文。登录页实际加载、填写测试账号、验证码登录并跳转/dashboard/analysis已通过；.runtime/package-dashboard-baseline.png。该结果不代表迁移业务页面完成验收。
- Task4已实现13个独立菜单页、完整业务字段/编辑器、菜单权限归一化及旧/profile图片上传/预览适配。4项Important修复后独立代码规格/质量复审通过，见task-4-review.md。根代理执行node --test --test-isolation=none tests/package-management.test.mjs：12通过、0失败/跳过，.runtime/package-task4-root-final-tests.log；覆盖17个SFC编译、字段/菜单/转换、实际生产插槽Ant Table SSR、并发评论删除及重试。
- 新菜单SQL在隔离MySQL重复导入两次，均保持80行、13直接子菜单；仅向本地测试admin角色分配这80项，移除了临时package:*测试许可。实际用户权限接口返回单根13子。
- .runtime/package-frontend-http-report.json：35项通过，覆盖实际权限菜单、17个SFC及辅助/API/permission模块的Vite HTTP转换、13子页对应真实列表接口及PWA/W2A分型；不表示浏览器已经渲染或点击。
- pnpm build失败在Vite externalize-deps Windows realpath子进程spawn EPERM，提权申请被自动审批以只读/禁止提权理由拒绝；.runtime/package-task4-build.log。实际Playwright浏览器启动亦spawn EPERM，.runtime/package-ui-smoke.log。未绕过限制；生产构建、布局截图及真实交互验收仍未完成。

## 清单与数据预检

- source-snapshot.json保存330个清单相关源文件SHA-256指纹；full-coverage-audit.json包含235条源路由、261个源前端文件。
- target-endpoints.json记录99条迁移路由；119条宿主基础能力见foundation-targets.json，17条代码生成/示例差异见nonmigrated-targets.json；component-targets.json登记24项业务/辅助实现及12业务表。
- 99项动态路由测试验证具体方法和路径绑定；这不等于99项完整业务流程均已执行。基础能力和前端支持文件复用亦不保证旧后台页面URL等价。
- data-preflight.sql在隔离空库和本地样例库执行成功，记录.runtime/package-mysql/preflight.log、preflight-fixtures.log；未验证生产历史数据。
- 根代理执行node docs/migration/package-api/verify-coverage.mjs --source-root D:/workspace/package-api/package-api，通过235路由、99迁移、119基础、17工具/示例处置、261前端、24辅助、12表及330源指纹核对。它检查清单完整性与目标存在，不替代业务运行验收。

## 部署前仍需验证

最终修复与本轮复测详见final-review.md：166项后端定向测试165通过/1OS跳过、宿主11模块打包成功、35项后端HTTP重新通过、首次SDK上报和6项日期HTTP通过、前端12项重新通过。整体审查识别的4项问题已修复；独立最终复审因审查服务403 billing disabled中断，仍未完成。当前会话原Vite进程已退出，本轮未重新执行前一轮35项前端HTTP报告，未重试受阻的构建或浏览器。

- 源生产DDL/数据、身份映射及业务字典，完整PWA/W2A部署静态资源未随源目录提供。
- 实际第三方服务、旧API域名TLS/代理及有效10MB单文件/20MB整请求限制，需要部署环境验证。
- 没有切换生产流量，没有创建或启用生产定时任务。切换/回退步骤见runbook.md和deploy/package-api/README.md。
- Git索引写入被当前自动审批器拒绝；Task3及后续改动保留授权工作区，已有Task1/2提交不受影响。未绕过索引权限。
