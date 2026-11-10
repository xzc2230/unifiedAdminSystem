# dev OpenAPI 补迁记录

日期：2026-11-10。源 dev `0f83f81`；属于本次 dev 补迁实现，尚未部署生产。

## 入口与权限

管理端：`/package-admin/openapi/token`（GET list/详情、POST、PUT、DELETE ids），`/package-admin/openapi/calllog/list`。使用当前 JEECG `PackageAdminSupport` 登录、有效租户成员、旧身份和 `package:openapi-token:{query,add,edit,remove}` / `package:openapi-calllog:query` 权限。

公开入口：`/openapi/manager` 原 5 个方法，路径、`X-Api-Token`、`pageNum/pageSize`、AjaxResult/TableDataInfo 的 code/msg/data 或 code/msg/rows/total 保留。响应由 OpenAPI 专用 ISO 日期 JSON codec 输出，避免 JEECG Result 改写。错误处理保留源 code/msg 信封；无 Token/无效/禁用/过期 HTTP401。Token 还要求所属 JEECG 用户仍是有效租户成员。

`OpenApiWebConfiguration` 在 Shiro FactoryBean 初始化前仅放行 `/openapi/manager` 与其下路径，由专属 MVC interceptor 强制校验 Token；没有开放管理路径。公开业务不使用当前浏览器登录身份。

## 分层与隔离

独立 `application/openapi`、`domain/openapi`、`controller/openapi`、`controller/admin/openapi` 和三个 MyBatis Mapper/XML。公开业务委托已有应用服务维持原商店关联和缓存行为；业务层强制 `user_id=token.id AND user_name='api'`，并验证资源归属注册表与 Token 的租户/JEECG 所有者。

新增应用和商店状态在同一事务中登记资源归属。已有商店状态须属于同租户。修改/删除在归属锁之后重新锁定读取应用并再次检查 Token 归属，防止同一 JEECG 用户下不同 Token 越权。批量删除先验证全部记录。应用包名继续使用目标项目的不可变保护。

Token/调用日志的租户及所有者由服务端确定，客户端输入的 tenantId/ownerUserId 不参与授权。后台列表普通用户仅能看自己数据，管理员可查看当前租户数据。调用日志保留请求路径/参数/响应，正文按源 16000 字符截断；记录失败不影响 API 业务。参数绑定失败、异常请求由专属缓存 Filter 记录实际响应和原始请求体；不会因 HTTP200 误写成业务成功。鉴权分类记录 accepted/missing_token/unknown_token/disabled_expired_or_inactive_token，不保存未知原始令牌；已知拒绝令牌的日志仍归属原租户和所有者。

## SQL 和部署

`db/package/010-dev-openapi.sql` 创建 `pkg_app_open_api_token`、`pkg_app_open_api_call_log`；可重复执行。Token 列使用二进制排序规则，确保访问令牌区分大小写。菜单权限由统一 `011-dev-menu.sql` 提供。

未导入源历史 Token/日志。若需要旧 Token 继续使用，必须保留其 token 和 ID、显式指定有效 tenant_id/owner_user_id，并将该 Token 的历史应用/商店登记至相同租户资源归属；不能按常规 JEECG 用户映射改写这些应用的 token ID。生产 SQL、历史导入及域名代理未在本任务执行。

## 验证

先运行存在性测试，实际失败为缺失公开 OpenAPI controller，再实现。

独立 JDK21 javac + JUnit 初版共 18 项全部通过，0 失败、0 跳过：Token 生成/启停/过期/租户有效性、管理归属、外部 Token 与 legacy 身份分离、包名保护、锁后归属再检查、跨租户商店拒绝、真实 MockMvc 401 和源分页/错误结构、日志截断/降级、Shiro FactoryBean 实际生成过滤器链、Mapper SQL 绑定。

真实 MySQL 测试仅连接 `127.0.0.1:13317/package_migration_test`，重复执行 SQL 两次，验证 Token 增改删、区分大小写、跨租户列表、日志及应用 FOR UPDATE 查询。业务测试记录事务回滚；新测试表保留在隔离库。日志 `.runtime/package-dev-openapi-tests.log`；未复用或输出生产凭据。

执行入口：JDK21 `javac @.runtime/openapi-green-javac.args`，`java @.runtime/openapi-tests-java.args`。MySQL 环境沿用既有 PACKAGE_TEST_MYSQL_* 本地测试配置。独立类目录避免与其他任务 Maven 编译竞争；最终全模块 Maven 验证由根协调者统一执行。没有声称已完成生产 HTTP 联调。

## 独立审查修复

审查发现原异常兜底日志没有记录真实 code500 响应且丢失 malformed JSON 原文，已用内容级 MockMvc 测试复现失败。改用仅处理公开 manager 路径的 `OpenApiAuditFilter`，在请求完成后一次记录方法、query、原始 body、鉴权分类与实际响应，复制缓存响应给客户端；不提前消费请求，不依赖 Controller 参数绑定成功。

审查还发现公开应用 createDate/updateDate 不应沿用管理端固定日期格式。源 Boot2.5 默认 Jackson 对未注解 Date 使用 ISO（WRITE_DATES_AS_TIMESTAMPS=false），源 ApplicationConfig 使用服务器默认时区；专用 codec 保留 ISO 毫秒和时区偏移。局部 InitBinder 恢复源 DateUtils 的 12 种日期筛选格式，未更改管理端和旧客户端 codec。

审查后测试见 `.runtime/package-dev-openapi-review-green.log`，包含真实 malformed JSON、类型绑定失败的日志与响应逐字一致、已知/未知令牌拒绝日志分类和原始请求内容、成功 JSON 请求仍可绑定、非空日期响应及多种日期筛选。最后统一 Maven/宿主回归由根任务执行。
