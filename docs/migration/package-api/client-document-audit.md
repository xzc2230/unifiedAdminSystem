# 用户接口文档逐项核对

核对日期：2026-11-09。依据：用户提供的12组接口说明、只读源项目D:/workspace/package-api/package-api、迁移模块当前源码及线上Nginx实际配置。未使用文档所附账号密码登录；不在仓库记录这些凭据。本次为代码/部署配置核查，未逐项执行12组生产业务请求。

## 四段接口增量更新

用户随后授权迁移第12组，现已在当前工作区补齐 `LegacyClientController.fourPath → ClientService.app2 → App2ClientService`、XOR解析、四类全局规则、Redis关联计数、Referrer以及应用五项风控配置。详细协议、SQL、验证和范围见 `four-path-migration.md`。下面的初次排查结论保留为历史记录；其中“第12组缺少”已由本次增量解决，公网部署状态未改变。当前12组均有代码入口，但第12组按源实际POST/JSON/x-pkg契约迁移，并非旧文档UA/query契约。全局风控CRUD页面和自动拉黑管理按钮未在本次新增。

## 对照结果（初次排查）

下列路径为旧协议路径；迁移后控制器统一在`/package-legacy`下。后端完整路径为`/jeecg-boot/package-legacy`加旧路径。旧客户端原地址不改，需要专用域名代理映射；当前管理站点的`/jeecgboot/package-legacy/**`被Nginx明确403拦截，已实际请求确认，不能将代码存在视为公网可调用。

| 文档序号 | 旧路径 | 迁移处理器/服务方法 | 核对 |
| --- | --- | --- | --- |
| 1 | /system/{随机串} | LegacyClientController.system → api | 已有；id做Base64解码，af_id；整体JSON Base64、url再次Base64，status/url/code |
| 2 | /v2/api/user/login | login → login | 已有；username/password参数，业务结果data含show/path/useAF；不是后台管理员登录接口 |
| 3 | /v2/api/retry/{随机串} | redirected → redirected | 已有；packName/af_id，保留源重定向分支 |
| 4 | /v2/api/banner/list/{随机串} | banner → getBanner | 已有；systemName/systemID，保留原始字符串响应 |
| 5 | /dynamic/{随机串} | dynamic → dynamicAdj | 已有；id，app_name/app_cache/app_id/app_dynamic/app_limit |
| 6 | /v2/api/check/url | checkUrl → checkUrl | 已有；tokenID/baseTime是请求参数名称的动态选择器；event/canLoad/load/canAppend/token |
| 7 | /app/start | start → getUserInfo | 已有；user_id/user_token，ty透传User-Agent；timestamp=当前毫秒-65535，token=MD5(当前毫秒)，不是MD5(timestamp字段) |
| 8 | /home/index/jsp | home → base64Data | 已有；pack_a/data_a，sim_a存在且不含字符5时为空；ip_a可用于地区判断；s_coe/link_oth/by_me；后缀为包名删除所有点号后的字符串 |
| 9 | /{任意单段} | single → base64Data2 | 已有；不限制为int/long；index参数；total_pages/list_details/max_page/data_type/group/isNow；实际字段date_time，文档写data_time；后缀同样为去点包名 |
| 10 | /{任意两段} | doublePath → base64Data3 | 已有；timestamp参数，Base64 JSON含pid/origin/spid/mark；origin可为含cityCode的JSON字符串 |
| 11 | /{任意三段} | triple → app1 | 已有；从参数值中选择第一个含点号的值作为包名；AES/ECB/PKCS5Padding，密文Base64；code/data/byme/ps |
| 12 | /{任意四段} | 迁移模块无 | 当前源目录已出现Client3Controller → app2、ClientRiskPayloadCodec和app/risk/app2整套实现；当前迁移模块缺少这些实现。源接口为POST、application/json、请求头x-pkg及body {t,p}，与本文档的UA/XOR和query契约有差异 |

前11组均使用未限制HTTP method的@RequestMapping，因此包含GET/POST。具体路由优先于泛化路径。固定四段地址例如banner/list等不等于任意四段AES接口。

## 兼容性差异与材料缺口

- 第12组是明确未迁移项。当前源目录新增四段POST入口、app2服务、XOR载荷codec、入参解析及风控依赖，不能只补控制器路由。当前源实现读x-pkg与JSON {t,p}，p为XOR后Base64的设备JSON；支持t/f1/b/abi等别名但并非直接按本文档UA处理。返回还含byme，而文档第12项只列code/data/ps。本次仅排查，未实现该增量。
- 重新运行verify-coverage.mjs发现11个已有指纹文件与迁移基线不符，包括ClientServiceImpl、AppManagerController/Service和部分前端。该校验只检查原登记文件，不穷举新增文件；当前源目录还出现Client3Controller及risk/app2等新增文件。因此原235路由/330指纹是旧审计快照，不能代表当前源目录全量覆盖。
- 文档中第7组token、第9组date_time和第8/9组后缀，均应以源实现及客户端真实解析为准，不应在已有兼容入口直接改名或改算法。
- 第11组AES优先使用应用名称，空名称时读取PACKAGE_DEFAULT_AES_KEY；迁移不再硬编码源默认密钥。线上application.env当前未配置该变量，模块默认值为空。本次未输出任何实际密钥。应用名称或默认密钥必须满足AES所需的UTF-8字节长度（16/24/32）；文档中的两组默认密钥字符串与提供源代码中的值不一致，不能未经核实直接采用。
- 代码证据：controller/legacy/LegacyClientController.java（11个客户端入口），application/apps/impl/ClientServiceImpl.java，application/legacy/{AESUtils,ParamsUtils}.java。源证据：PackServer的ClientController、Client1Controller、Client2Controller及ClientServiceImpl。
- 现有PackageProtocolTest覆盖前11组服务主要字段/编码/边界；PackageEndpointCoverageTest覆盖迁移路由绑定。这些既有测试不证明第12组存在，也不表示当前生产专用入口已启用。
- 本轮重新执行PackageProtocolTest与PackageLegacyRoutesTest：15项全部通过、0失败/错误/跳过，日志.runtime/package-client-doc-audit-tests.log。未执行12组生产业务调用。

结论：按用户本次文档为11/12组存在对应迁移实现，第12组是当前源项目新增而迁移模块尚未包含的缺口。当前线上管理入口拒绝旧协议，需按专用域名部署方案单独启用；本次未改动线上安全入口、身份映射或AES配置。前11项“已迁移”表示路由与主要既有协议已存在，不表示与当前变动后的源实现逐分支完全相同。
