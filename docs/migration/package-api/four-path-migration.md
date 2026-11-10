# 四段客户端接口迁移说明

日期：2026-11-09。源：D:/workspace/package-api/package-api/PackServer。对应新增源文件指纹见 four-path-source-snapshot.json；原全量审计快照不因此更新。

## 实际请求与响应

内部路径：`POST /jeecg-boot/package-legacy/{q}/{w}/{e}/{r}`。四段均可为随机字符串，具体固定路由优先（例如 `/v2/api/user/login`）。请求 Content-Type 为 `application/json`；GET 返回405，非JSON POST返回415。

请求头 `x-pkg` 为包名，正文 `{ "t": "客户端时间", "p": "加密设备JSON" }`。t仅记录，可不传；p必传。设备JSON以UTF-8编码，逐字节与重复密钥XOR，再Base64；沿用源标准、URL安全及MIME Base64和hex兼容解码。密钥优先应用名称，名称为空时优先 PACKAGE_DEFAULT_AES_KEY，再回退源默认密钥。源默认密钥不在本说明重复展示，生产建议通过环境变量明确配置。名称与配置密钥用于AES时必须为16/24/32个UTF-8字节。

设备字段：brand/b、cpuAbi/abi、deviceName、aid、gaid、tz/timezone/t、f1、InstallReferrer。InstallReferrer为对象，含installVersion、googlePlayInstantParam、installReferrer。旧用户文档中的query包名、UA XOR和GET协议不属于当前源四段实现。

成功响应为原始AES/ECB/PKCS5Padding密文Base64字符串，不包JEECG Result。解密JSON含 code、data、byme、ps；data若为JSON链接配置则增加cityCode后作为JSON字符串返回。缺包、非法载荷、风控拒绝和AES失败保持空响应。日志保存原始旧路径、解密p、时间及拒绝原因。

## 后端层级和规则

控制器 LegacyClientController.fourPath → ClientService.app2 → 独立 application/apps/risk/app2/App2ClientService；入参解析、全局规则、Redis规则、Referrer判断各自独立。四类黑名单在domain/app及infrastructure/persistence/mapper，数据表使用pkg_前缀。不会合并到当前其他业务或归因模块。

顺序保持源实现：解析 → status=0拒绝 → IP禁美/运营商和地区白名单 → ASN/品牌/ABI/型号全局规则 → 单包Redis关联计数 → InstallReferrer。status=3仅跳过Referrer；1为全开规则，2为归因规则。其他非零状态沿用源实现，执行前面的判断后放行。

Redis前缀隔离为 `package:legacy:app2:`；默认阈值1，配置小于1也回退1；窗口小于60秒回退86400秒。保持源set去重、超过阈值才拉黑、窗口续期和解除拉黑时清理关联计数行为。旧库app2:键不自动搬入新命名空间。

保留源实际行为：时区/SIM规则仍停用；Referrer模板只匹配嵌套key存在，不比较value；内层installReferrer为查询字符串时，源函数丢弃解析结果并使用空对象，这次也保留，修改需另行确认兼容影响。

## 数据库与后台配置

已有数据库部署前执行模块资源 `db/package/008-four-path-risk.sql`：创建四类pkg_app_*_blacklist表，补齐pkg_app_manager的gaid_aid_threshold、aid_device_threshold、aid_gaid_switch_threshold、risk_window_seconds、referrer_blacklist_json。脚本逐列判断存在，可重复执行；未执行增量SQL时新版应用查询会因缺列报错。全新库先执行001-schema.sql，再执行008；本次不自动操作生产数据库。

五项风控配置已放入“应用包管理 → 应用管理”的现有编辑表单，沿用现有样式与apps权限；后端允许编辑字段及XML读写已对齐。2026-11-10 补正应用开关选项：应用管理按客户端协议固定提供 0 全关、1 全开、2 归因、3 调试，新建/编辑及列表翻译使用同一组定义，不再因宿主 app_status 字典缺项而遗漏归因或调试。其他业务页面继续使用各自字典，未改写共享字典数据；后端数字编码及风控处理保持不变。

四类全局规则由对应pkg_app_asn_blacklist、pkg_app_device_brand_blacklist、pkg_app_cpu_abi_blacklist、pkg_app_device_name_blacklist读取enabled=1记录，按priority/ID倒序。必须另行导入源规则数据，否则空表即无规则。此次迁移的是四段客户端接口及运行依赖，未新增源全局风控CRUD页面、ASN查询外部服务或自动拉黑管理按钮；内部查询/解除方法保留。上述后台工具不属于本次已迁移页面。

## 部署边界

未开放管理域名被Nginx拒绝的/package-legacy入口；旧客户端地址保持不变仍需专用域名代理映射至本内部路径。未改生产Nginx、默认密钥、身份映射或导入生产规则数据。代码版本以 Git 提交记录为准。

## 验证

新增路由先验证404失败，再接入实现。MockMvc验证POST/JSON约束、固定路由优先、真实XOR入参及AES解密、原路径及重复读取日志。业务覆盖开关、地区、四类全局黑名单/observe、Referrer规则及源查询串行为。专用本地MySQL验证增量SQL重复执行、5字段增改读回及4类mapper CRUD；专用本地Redis验证去重、阈值、TTL、包隔离和解除拉黑。

最终后端回归：181项，180通过、0失败/错误、1跳过；唯一跳过是Windows无符号链接创建权限的既有文件越界测试。四段专用12项业务测试和2项MySQL/Redis集成均通过且未跳过。前端15项全部通过。日志：.runtime/package-four-path-final.log；前端执行 node --test --test-isolation=none tests/package-management.test.mjs。独立只读代码审查已完成，修复了Geo字段缺失的源行为差异；新增四段专用Geo方法，前11组沿用旧方法。

初次四段路由404及Geo缺字段拒绝均先观测失败再修复，分别见 .runtime/package-four-path-red.log 与 .runtime/package-four-path-geo-red.log。

Windows JDK测试需设置 `-Djdk.net.unixdomain.tmpdir=D:\workspace\JEECG\.runtime\package-redis`，避免默认临时目录的Unix域套接字连接失败；该设置仅用于本地测试，不改生产配置。
