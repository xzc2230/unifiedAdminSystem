# Package API 迁移实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 package-api 的全部有效业务能力迁入 JEECG，并保留旧客户端协议。

**Architecture:** 新增 jeecg-module-package 独立 Maven 模块。管理 API、旧协议控制器、应用服务、领域模型、持久化、外部集成分别分层，前端使用独立 packagemanagement 目录及一个根菜单。

**Tech Stack:** Java 21、Spring Boot 4.1、MyBatis、JEECG/Shiro、Vue 3、TypeScript、Ant Design Vue。

## Global Constraints

- 源码只读：D:/workspace/package-api/package-api；目标：D:/workspace/JEECG。
- 保留已有 README/AWS 工作区改动；不提交不相关文件。
- 旧客户端方法、路径、参数、编码、响应、重定向和分支顺序保持兼容；管理端采用 JEECG Result 和 records/total。
- 管理前缀 /package-admin，旧协议内部前缀 /package-legacy，外部原域名由代理保持路径。
- Java 根包 org.jeecg.modules.packagemanagement；表 pkg_*；权限 package:*；配置 jeecg.package-management.*。
- 禁止复制旧认证框架；禁止信任前端 userId；所有管理读取与变更都核验身份及归属。
- 不把生产密钥写入代码；测试不向真实第三方发消息、邮件或创建域名。
- 全量依据 docs/migration/package-api/full-coverage-audit.json；99 条迁移路径、13 个业务菜单。
- 源业务 DDL 和 PWA 部署静态目录未提供，开发 DDL 必须标明推断；不得宣称生产数据已验证。

### Task 1: 独立模块、领域与持久化

**Files:** upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/pom.xml；父聚合 POM、启动 POM；新模块 src/main/java/org/jeecg/modules/packagemanagement/{domain,infrastructure/persistence,config}；src/main/resources/{db/package,org/jeecg/modules/packagemanagement/infrastructure/persistence/mapper/xml}。

**Interfaces:** 领域类型保留源 Java 类名和业务属性；Mapper 保留源签名；业务领域对象放 domain，Mapper 放 infrastructure.persistence.mapper。公共字段抽到 domain.PackageEntity。后续任务直接依赖这些类型。

- [x] 按 source-data-mapping.json 的12张表核对源领域与XML；复制业务字段、SQL条件、排序及聚合，重命名表与 namespace，移除 RuoYi Excel/Swagger 注解。
- [x] 新模块只依赖 base-core；挂接父模块与启动依赖，禁止依赖 system-biz 内部实现。
- [x] 编写映射测试：验证所有 XML 可解析、namespace 与方法对应、pkg_ 表名完整且保留 evnet_history 字段映射。
- [x] 生成带说明的开发建表脚本，所有字段依据源 Java/Mapper，保留原 ID，不伪造生产精确类型。用户映射及所有权字段独立建表。
- [x] 运行 Maven focused tests，保存输出及差异报告，审查通过后记账。

### Task 2: 应用服务、身份与外部集成

**Files:** 新模块 application/{apps,statistics,store,event,pwa,notification,landing,applog,port}、infrastructure/{identity,integration}、job、exception、config。

**Interfaces:** 原服务业务方法签名保留，包路径按领域分类；PackageActor 提供 current/requireOwner，PackageProperties 提供所有第三方配置；外部 HTTP/邮件/推送依赖可替换 port。

- [x] 先编写行为测试：重复包名拒绝、统计累计与金额 HALF_UP2、商店0→1→2且2不再探测、越权单条/批量/导出拒绝、PWA评论保存与删除、随机候选边界。
- [x] 迁移24个业务实现/辅助文件中有效能力；遗留 SQL 日志只记录去向，ES保留实际索引行为。
- [x] 移植 ClientServiceImpl 的11入口，保持原分支与序列化；javax.servlet改jakarta，RuoYi工具替换为模块适配器；AES默认值由配置注入。
- [x] 迁移 FB、IP、Nginx、VAPID、WebPush、邮件集成，外置密钥和部署目录；使用测试替身验证请求，不触发真实外发。
- [x] 身份将源数值用户映射到 JEECG 字符串 ID；使用主机 Shiro 登录与租户有效成员核验，批量先全部验证再写入。
- [x] 运行服务与协议行为测试，审查并保存报告。

### Task 3: 管理与兼容接口

**Files:** 新模块 controller/{admin,legacy}、dto/{admin,legacy}、config；主机 Shiro 配置必要窄路径适配；deploy/package-api/。

**Interfaces:** 管理接口统一 /package-admin/{domain}；前端用 pageNo/pageSize；旧接口 /package-legacy 加源路径。服务对旧响应不包 Result。

- [x] 根据99条迁移清单逐条绑定控制器与业务方法，编写方法、路径和参数契约测试。
- [x] 使用 MockMvc 验证 Base64/AES、重定向状态及 Location、通配路径优先级、匿名旧入口与管理鉴权隔离、1秒重复提交语义。
- [x] 资源上传下载保持源字段契约并使用当前上传目录；校验路径边界与下载删除行为。
- [x] 添加原域名代理配置示例，保留 Host、原 URI、真实IP和查询参数；提供部署与回退步骤。
- [x] 全量 API 映射差异校验，审查并保存报告。

### Task 4: JEECG 前端及单根菜单

**Files:** upstream/JeecgBoot/jeecgboot-vue3/src/{views,api,types}/packagemanagement；新模块 db/package 菜单权限SQL。

**Interfaces:** 一个应用包管理根菜单 /package-management，13子菜单：apps/store/statistics/event-configs/event-history/event-errors/pwa/w2a/landing-counts/app-events/reviews/notifications/request-logs。

- [x] 阅读前端 AGENTS.md，使用当前 BasicTable/BasicForm/modal/drawer/字典/上传组件，按域建立类型与API文件。
- [x] 逐项迁移12业务页面和独立评论页，按 frontend-coverage.md 对照所有活跃按钮、校验、筛选、导出和弹窗。
- [x] 保留标签JSON、五档评分、日期格式、域名字典、W2A下载地址、评论立即删除、统计汇总独立查询与金额精度、包归属转移权限。
- [x] 菜单与按钮权限SQL保持可重复执行；根菜单 alwaysShow，默认首个可见子页，无子菜单权限时根菜单隐藏。
- [x] 编写非平凡转换/校验测试（12项通过，含SFC编译和实际Ant Table SSR）。
- [ ] 执行前端生产构建并使用本地界面核验迁移页面布局及关键操作（spawn EPERM，自动审批拒绝提权）。
- [x] 审查前后端契约与权限，保存报告。

### Task 5: 联调与完整性验收

**Files:** docs/migration/package-api/{implementation-status,verification,runbook}.md；验证脚本及上述涉及的修正文件。

- [x] 按235源映射逐条核对处置，其中99迁移、119复用适配、12生成器差异、5示例排除；全部有目标文件及验证状态。
- [x] 核对261前端源文件去向、24业务实现及辅助文件、13菜单权限、12数据表与ES索引。
- [x] Maven测试及启动模块打包、git diff --check；记录准确命令和结果（最终166项165通过/1OS跳过，宿主11模块打包成功）。
- [ ] 前端生产构建及迁移页面浏览器验收（权限受阻，详见final-review.md）。
- [x] 使用本地隔离配置联调，未得到真实生产数据或静态资源的验收项明确记录，不虚报通过。
- [x] 整体审查发现的4项重要问题已修复并完成真实MySQL/HTTP复测；交付迁移结果、启动/切换/回退步骤与限制。
- [ ] 完成修复后的全分支独立最终复审（审查代理因服务403 billing disabled中断，未获得最终审批意见）。
