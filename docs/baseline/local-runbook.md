# 本地基线运行记录

日期：2026-11-06。命令使用 PowerShell，项目根目录为 `D:/workspace/JEECG`。

## 推荐启停方式与加载失败排查

2026-11-06 复查加载失败时，13100、18080 都没有监听进程，HTTP 报连接拒绝；本项目 MySQL、Redis 容器仍 healthy。此前临时命令启动的 Java/Node 已不在，日志没有记录明确的崩溃退出原因。ResizeObserver 的开发警告不能解释两个服务端口同时消失。临时命令会话结束后进程被清理是合理推测，尚无退出审计证明。

已新增 `deploy/local/services.ps1`，通过隐藏后台进程启动服务，保存 PID、启动时间与命令行标识到 `.runtime/services.json`；标准输出与错误各自写入带时间戳的日志。脚本只管理身份匹配的进程，拒绝接管陌生端口；重复启动复用已有服务。后台进程与启动命令分离，但不承诺 Windows 重启后自动运行或崩溃后自动重启。

在项目根目录执行：

```powershell
pwsh -NoProfile -File .\deploy\local\services.ps1 -Action Start
pwsh -NoProfile -File .\deploy\local\services.ps1 -Action Status
pwsh -NoProfile -File .\deploy\local\services.ps1 -Action Stop
```

Start 会检查数据库与缓存，并等待前端、后端、代理就绪；失败返回非零退出码。Stop 只停止本脚本记录的前后台服务，保留数据库容器和数据。

恢复启动中发现，直接启动 Node 缺少 pnpm 启动器提供的 NODE_PATH，导致 Vite 配置加载报 `Cannot find module 'debug'`。脚本已补齐本项目 pnpm 的提升依赖目录，进程启动后恢复父进程环境变量。Java 继续使用独立临时目录与原本的本机配置。

恢复后已验证三项检查 HTTP 200，浏览器实际显示已登录管理员首页、菜单及统计卡片。下文原始前台启动命令用于诊断参考，日常使用上述后台启停脚本。

## 已验证环境

- Java 默认版本为 8；已有 `C:/Program Files/Java/jdk-21`，版本 21.0.9。构建和运行通过进程级设置使用它，不修改全局 Java。
- Maven 3.8.8、Node 24.15.0、pnpm 12.6.0、Docker Server 29.3.1。
- 其他项目已有 landing-mysql/landing-redis，本项目使用 `jeecg-baseline` 独立容器与卷。
- MySQL 实际镜像版本 8.0.46，Redis 使用已缓存的 7.2 标签；生产需要另固定镜像 digest。

## 编译验证

在 `upstream/JeecgBoot/jeecg-boot` 下：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path=$env:JAVA_HOME+'\bin;'+$env:Path
mvn -B '-Dmaven.repo.local=D:\workspace\JEECG\.runtime\m2' -pl jeecg-module-system/jeecg-system-start -am package -DskipTests -l D:\workspace\JEECG\.runtime\backend-package.log
```

实测 10 个模块 BUILD SUCCESS。该命令跳过测试，不等于测试或隔离验收通过。产物为 `jeecg-module-system/jeecg-system-start/target/jeecg-system-start-3.9.5.jar`。

在 `upstream/JeecgBoot/jeecgboot-vue3` 下：

```powershell
pnpm install --frozen-lockfile --ignore-scripts --store-dir D:\workspace\JEECG\.runtime\pnpm-store
```

实测 1719 个锁文件条目通过供应链校验，安装 1603 个包。安装脚本禁用；如后续功能需要原生构建脚本，应逐项确认。

## 本机依赖启动与关闭

本次生成的数据库随机密码保存在忽略目录 `.runtime/local.env`，不输出到文档。首次部署需先生成此文件，Compose 缺少该变量会拒绝执行。

```powershell
docker compose --env-file .runtime/local.env -f deploy/local/compose.yaml config --quiet
docker compose --env-file .runtime/local.env -f deploy/local/compose.yaml up -d --wait --wait-timeout 150
docker compose --env-file .runtime/local.env -f deploy/local/compose.yaml ps
```

本次数据库初始化完成，两项服务 healthy。MySQL 仅监听 `127.0.0.1:13316`，Redis 仅监听 `127.0.0.1:16389`。健康检查使用 TCP 查询初始化后的 sys_user 表。

停止并保留数据：

```powershell
docker compose --env-file .runtime/local.env -f deploy/local/compose.yaml down
```

不得为重启直接删除命名卷。SQL 初始化仅在新卷首次启动时运行。

## 后台启动

在项目根目录执行：

```powershell
$taskLocalEnv=Get-Content -LiteralPath '.runtime\local.env'
$env:JEECG_LOCAL_DB_PASSWORD=($taskLocalEnv | Where-Object { $_.StartsWith('JEECG_LOCAL_DB_PASSWORD=') }).Substring('JEECG_LOCAL_DB_PASSWORD='.Length)
New-Item -ItemType Directory -Force -Path '.runtime\tmp' | Out-Null
& 'C:\Program Files\Java\jdk-21\bin\java.exe' '-Djdk.net.unixdomain.tmpdir=D:\workspace\JEECG\.runtime\tmp' -jar upstream/JeecgBoot/jeecg-boot/jeecg-module-system/jeecg-system-start/target/jeecg-system-start-3.9.5.jar --spring.profiles.active=dev --spring.config.additional-location=file:./deploy/local/application-local.yml
```

后台仅监听 `127.0.0.1:18080`，上下文 `/jeecg-boot`。本地覆盖关闭敏感 AI 节点并设置独立数据库与缓存。此开发配置不适合生产。成功启动日志为 `.runtime/backend-ready.log`；早期失败日志为 `.runtime/backend-start.log`、`.runtime/backend-local.log`。

实测 Flyway 校验 20 个迁移，数据库版本 3.9.5.0；后台启动成功，`/jeecg-boot/sys/randomImage/baseline-check` 返回 HTTP 200、`success=true`。默认临时目录下 Windows JDK Selector 曾发生 UnixDomainSockets `Invalid argument: connect`；最小 Selector 诊断及后台启动均验证了指定独立临时目录的办法。

## 前端启动

本机 `.env.development.local` 将端口设为 13100，代理指向后台 18080，关闭 mock 与微前端。该文件受上游 gitignore 保护。

```powershell
Set-Location D:\workspace\JEECG\upstream\JeecgBoot\jeecgboot-vue3
pnpm dev --host 127.0.0.1 --port 13100 --strictPort
```

实测 Vite 就绪，`http://127.0.0.1:13100/` 返回 HTTP 200。存在 clean-css/chalk 未解析警告；完整生产构建在修复本机工具路径后通过，浏览器渲染及登录还需验证，HTTP 200 不能替代这些检查。受限执行中曾发生 spawn EPERM，正常本机权限重试后 Vite 启动成功。

代理链路 `/jeecgboot/sys/randomImage/baseline-final` 返回 HTTP 200 且业务成功；未携带认证的后台 `/jeecg-boot/sys/user/list` 返回 HTTP 401。这里只覆盖可访问性与一个未认证拒绝场景，不覆盖角色、字段和跨租户验收。

前后台前台运行可用 Ctrl+C 关闭；后台托管进程应先核对 PID 与命令行再停止，避免结束其他 Java/Node 服务。

## 前端生产打包的本机工具问题

首次 `pnpm build` 的 Vite 阶段完成，但 esno 后处理启动 esbuild.exe 发生 EBUSY。直接调用安装目录中的二进制也返回文件占用错误。重新获取同版本官方包，使用锁文件 SHA512 校验通过后，独立目录下的二进制运行与两项后处理均成功。

```powershell
# 根目录下获取官方同版本工具包，不升级依赖
npm pack @esbuild/win32-x64@0.28.1 --pack-destination D:\workspace\JEECG\.runtime --ignore-scripts --json
```

本次压缩包实际 SHA512 与锁文件一致：`sha512-bm4Mowrv+GXMlpWX++EcXw/iLyd1o3+bJkC2DkWXYVvgZCqD/bSj9ctZeAMC3cIxgjRVR2Dufaiu4YPxr5gW1A==`。解压至 `.runtime/esbuild-official` 后，在前端目录以进程级变量运行：

```powershell
$env:ESBUILD_BINARY_PATH='D:\workspace\JEECG\.runtime\esbuild-official\package\esbuild.exe'
pnpm build
```

独立后处理已生成 `dist/_app.config.js` 并复制 `dist/chat`；完整重跑退出码为 0，结果记录于 `.runtime/frontend-build-verified.log`，2026-11-06 10:45 完成。生产部署前仍需浏览器渲染、登录及资源加载验收，不能只依靠 Vite 输出成功判断。
