# Task 2 独立审查（修复复审完成）

## Spec compliance — ✅ Spec compliant（Task 2 范围）

- 审查基线 `603c8f6`；初审快照 `.runtime/package-task2/review-working.diff`，复审冻结差异 `.runtime/package-task2/review-fixes.diff`。本次结论覆盖初审及修复 delta。
- 初审 3 项 Important 均已闭环，通知全局密钥失败边界亦已恢复；当前没有未解决的 Critical / Important。Controller 和前端属于 Task 3/4，未将其缺失认作 Task 2 遗漏。
- 代码引用根目录 `M` = `upstream/JeecgBoot/jeecg-boot/jeecg-boot-module/jeecg-module-package/src/main/java/org/jeecg/modules/packagemanagement`；`T` = 同模块 `src/test/java/org/jeecg/modules/packagemanagement`；`S` = `D:/workspace/package-api/package-api/PackServer/src/main/java/com/ruoyi`。

## Strengths

- `M/application/apps/impl/ClientServiceImpl.java:42`、`:700`、`:1120`、`:1432`：11 个协议保留各自参数、条件分支、返回字段、AF 拼接、Base64/AES 路径。固定时钟和 GeoPort 支持离线测试；`T/PackageProtocolTest.java:27` 起核验实际响应内容。
- `M/application/statistics/impl/AppStatisticsServiceImpl.java:107`、`M/application/store/impl/StoreStatusServiceImpl.java:116`：计数字段拼写、金额 HALF_UP、状态 0→1/1→2 发信、邮件失败后继续后续包等行为保留。
- `M/application/pwa/impl/AppPwaServiceImpl.java:88`、`:138`、`:186` 与 `M/config/PackageSnowflake.java:3`：VAPID/NPM linkId、Snowflake 参数、更新不重建密钥、删除外部 linkId 和嵌套评论路径完整。服务保留为可信内部原语，管理入口授权属于外层。
- `M/application/event/impl/EvnetHistoryServiceImpl.java:105`、`:150` 与 `M/infrastructure/integration/FacebookHttpAdapter.java:16`：保存事件→解析原 URL→条件发送→成功/失败日志的次序和 SDK 原 payload 字段得到保留。
- `M/infrastructure/identity/PackageAccess.java:15`、`:20`、`:78` 与 `PackageIdentityRepository.java:14`、`:20`、`:36`：Shiro 用户和 TenantContext、有效成员/租户/用户状态与时间、缺映射拒绝、租户内管理员、显式资源归属、整批校验后写入均实现。动态表名受白名单控制，旧 userId=1/角色101不赋权。
- `M/config/PackageManagementConfiguration.java:8`、`:13` 与 `M/application/notification/impl/AppNotifyServiceImpl.java:43`：限定服务扫描、HTTP 懒初始化、私有通知 ObjectMapper 不注册为宿主 Bean。`src/main/resources/package-management.properties:3` 起从环境注入凭据，无源硬编码密钥迁入。

## 初审问题闭环

| 原问题 | 修复及证据 | 结论 |
| --- | --- | --- |
| Geo 查询异常后错误放行落地页 | `M/infrastructure/integration/GeoHttpAdapter.java:41` 命中 Facebook 时专用 URL 为空则使用 landing fallback，异常时也返回 fallback。`T/PackageIntegrationAdapterTest.java:25`、`:57` 覆盖普通提供者、Facebook、专用 URL、缺 as、畸形 JSON、HTTP 异常。 | 已修复 |
| ES 写入/读取与列表时间范围相差 8 小时 | `M/infrastructure/integration/ElasticsearchAppLogAdapter.java:13`、`:49`、`:62` 使用 UTC 存取；列表范围保留上海转换。`T/PackageIntegrationAdapterTest.java:31` 模拟 ES 对存储时间和范围的转换并验证跨日窄窗口命中、读回 instant；`:48`、`:58` 检查 UTC 字面量和旧 mapping/query 字段。 | 已修复 |
| export/builder 误复用列表时区 | `M/infrastructure/integration/ElasticsearchAppLogAdapter.java:72`、`:75`、`:114` 区分 list 和 export：list 带上海 time_zone，export 不带；`M/application/applog/EsAppLogServiceImpl.java:5` 使用 exportQueryBody。`T/PackageIntegrationAdapterTest.java:38` 锁定原 builder 差异。 | 已修复 |
| 全局推送密钥解析错误被当作单订阅失败，最终返回成功 | `M/application/notification/impl/AppNotifyServiceImpl.java:124` 在循环外 validateKeys；`M/infrastructure/integration/WebPushAdapter.java:13`、`:21` 使用与 send 相同的真实 key 初始化。`T/PackageIntegrationAdapterTest.java:43` 使用真实适配器的坏 key 验证整体失败，`:62` 保留单条异常后继续并整体成功。 | 已修复 |

- ES 等价依据已核实，不是按理想行为改旧协议：源 Boot 2.5.15 → spring-data-bom 2021.0.12 → SDE 4.2.12；根任务取得的精确版本源码中，`.runtime/package-task2/SimpleElasticsearchPersistentProperty.java:228` 对 Date 调 converter，`ElasticsearchDateConverter.java:106` 转 Instant，`:265` 按 UTC 格式化，`:291` 按 UTC 读回。实体 Jackson `@JsonFormat(GMT+8)` 不介入此路径。
- 源 `S/project/applog/controller/EsAppLogController.java:85` 的 list 显式使用上海 time_zone，`:115` 的 export 调用 `S/project/applog/service/impl/EsAppLogServiceImpl.java:29` 无 time_zone 的 builder；最终实现保留了这一真实差异。
- 新身份/集成代码已展开方法，初审整方法单行的可读性意见得到改善。对 delta 前后进行去空白字符比较：PackageAccess、PackageActor、PackageIdentityRepository、FacebookHttpAdapter、JdkHttpAdapter、NpmPwaAdapter、SmtpMailAdapter 均完全一致；结合逐项阅读，权限条件、SQL、锁和事务注解没有因格式展开改变。

## Remaining issues

### Critical / Important

- 无未解决项。

### Minor

- `T/PackageConfigurationTest.java:20` 预注册 mapper mock 再触发 MapperScan，最终 `.runtime/package-task2/review-fixes-green.log:209` 起仍有 duplicate mapper / no-mapper 警告。日志还保留预期失败路径的日志/堆栈、既有宿主编译与 Mockito 动态 agent 警告。测试通过但输出不干净；建议测试夹具隔离 mapper 扫描并捕获预期异常日志，既有宿主警告另行处理。本项不阻断 Task 2。

## ⚠️ 跨层或外部验证边界

- `M/infrastructure/identity/PackageAccess.java:55`、`:60`、`:85` 的锁、创建和删除归属需由 Task 3 放入业务修改同一外层事务。`T/PackageIdentityMySqlIntegrationTest.java:20` 是手动 JDBC 事务，证明 SQL 语义，不证明未来 Spring 代理/controller 事务与 MyBatis/JdbcTemplate 同连接协作。
- `M/application/pwa/impl/AppPwaServiceImpl.java:120`、`:169` 等内部方法按传入评论 ID 更新；Task 3 必须先核验嵌套记录归属和父子关系，保护订阅/通知的父资源，并在分页、统计、导出之前限制范围。原服务不直接承担 HTTP 权限符合本任务分层。
- `M/application/apps/impl/ClientServiceImpl.java:47` 直接读 request URI；可信代理包装还原、公开白名单和宿主最终响应序列化需在 Task 3 联调。
- `M/infrastructure/integration/ElasticsearchAppLogAdapter.java:35`、`NpmPwaAdapter.java:14`、`FacebookHttpAdapter.java:16`、`SmtpMailAdapter.java:12`、`WebPushAdapter.java:24` 未对真实服务验证；ES 时间回归使用模拟比较，不是实集群。部署仍需检查历史文档样本、mapping、真实端点及凭据。未连接生产服务。
- `M/job/PackageStoreJob.java:5` 直接执行已有测试；实际宿主 Quartz 创建、SpringContextUtils 回退取 Bean 及调度注册需宿主运行验证。

## Verification and scope

- 初审阅读提供 diff；首次工具输出截断后分片补齐。复审仅阅读冻结 delta 和更新报告/日志，另做差异字符比较；没有重跑套件、git 操作或修改业务代码，仅写本报告。
- 定向外部源码核对：Geo 失败分支（IpAddrUtil）、ES mapping/list/export（AppLog、EsAppLogServiceImpl、EsAppLogController、精确 SDE converter）、FB 字段与顺序（EvnetHistoryServiceImpl）、NPM 路径（NginxTools）、商店成功条件（HttpClientUtil）。源凭据未纳入报告。
- 定向宿主核对：sys_user_tenant 正常状态、租户有效日期/删除字段（SysUserTenant、SysTenant、CommonConstant）；业务 user_id 类型、registry 唯一约束（001-schema.sql）。未扩展成宿主全量审查。
- 已读取最终 `.runtime/package-task2/review-fixes-green.log:422`、`:432`：40 discovered，38 passed，2 opt-in DB tests skipped，0 failures/errors，BUILD SUCCESS。38 项中包含 11 集成适配器回归、12 协议测试、配置隔离和 Quartz 测试。单独真实 MySQL 身份与此前 MyBatis 通过结果作为已有证据接受，未重复执行。

## Task quality — Approved

3 项 Important 和推送全局 key 边界均按源行为修复，补充回归测试可重现原失配并验证修复。Task 2 可通过；保留上述已知 Minor 与明确跨层/外部验证边界。
