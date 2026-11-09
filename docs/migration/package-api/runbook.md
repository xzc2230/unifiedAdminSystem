# 应用包管理迁移运行说明

当前实现与验证状态以implementation-status.md、verification.md为准。本说明随接口、前端实施继续补全，不能据此认定生产切换已完成。

## 本机隔离环境

| 服务 | 地址 | 数据/配置 |
| --- | --- | --- |
| 临时MySQL 8 | 127.0.0.1:13317/package_migration_test | .runtime/package-mysql/my.ini、data；限定package_test用户 |
| 临时Redis | 127.0.0.1:16390 | .runtime/package-redis/redis.conf，关闭持久化 |
| 临时宿主后端 | 127.0.0.1:18081/jeecg-boot | .runtime/package-application.yml；已运行包含Task2审查期间快照的新JAR，后续修正及控制器须重打包 |
| 临时前端 | 127.0.0.1:13101 | 通过进程环境覆盖VITE_PROXY，不修改已有本地env |

本机测试密码只存.runtime/package-mysql/credentials.env；会话只存.runtime/package-session.json，不提交、不打印。测试用库已经导入宿主基线结构和14张pkg表。测试插入由事务回滚。不能将临时库密码用于部署环境。

## 模块配置

模块配置前缀jeecg.package-management，环境变量由package-management.properties映射。仅在实际需要时配置对应外部服务，未配置时不能将调用失败显示为成功。

| 用途 | 环境变量 |
| --- | --- |
| 旧AES兼容默认密钥 | PACKAGE_DEFAULT_AES_KEY（由受保护配置提供原系统有效值） |
| 地区服务 | PACKAGE_GEO_URL、PACKAGE_GEO_API_KEY |
| ES请求日志 | PACKAGE_ES_URL、PACKAGE_ES_USERNAME、PACKAGE_ES_PASSWORD |
| 域名服务NPM | PACKAGE_NPM_BASE_URL、PACKAGE_NPM_IDENTITY、PACKAGE_NPM_SECRET |
| PWA密钥生成/目录 | PACKAGE_VAPID_URL、PACKAGE_PWA_ROOT |
| 邮件 | PACKAGE_SMTP_HOST、PACKAGE_SMTP_PORT、PACKAGE_SMTP_USER、PACKAGE_SMTP_PASSWORD、PACKAGE_SMTP_FROM、PACKAGE_SMTP_START_TLS、PACKAGE_STORE_RECIPIENTS |
| FB与落地页 | PACKAGE_FACEBOOK_BASE_URL、PACKAGE_FACEBOOK_BLOCKED_URL、PACKAGE_LANDING_FALLBACK_URL |
| WebPush | PACKAGE_PUSH_SUBJECT；每个应用沿用其已保存的VAPID公私钥 |

## 数据准备与核验

001-schema.sql为根据源Mapper/Java推断的开发DDL，正式导入前必须与源实库结构对比，尤其字段类型、长度、精度、默认值及唯一索引。先检查package_code、app_code重复，保留原数字主键和关联。源业务表→pkg表对照见task-1-report.md。

pkg_user_identity用于旧数字用户到JEECG用户/租户的明确映射；每个旧ID只对应一个有效宿主身份。pkg_resource_owner逐资源登记归属，历史记录不得根据请求传入userId自动认领。先确认宿主用户、租户、成员状态及有效期，再导入映射。缺少或歧义的映射默认拒绝管理访问。

核对12表数量、父子关联、统计金额、评论、订阅、通知、NPM linkId和VAPID密钥；ES applog索引及字段映射另行迁移。上传文件、PWA/W2A静态站点目录、Nginx域名关系不随Java代码自动迁移。

## 生产切换前

必须具备源业务DDL/数据备份、有效身份映射、完整PWA静态资源、外部服务测试配置及旧域名代理规则。先在隔离环境完成新旧协议对照与页面验收，再准备目标数据快照与回退入口。旧API域名保持原方法/路径/参数，由代理映射到独立/package-legacy内部空间；管理API使用/package-admin，独立鉴权。

测试不发送真实邮件/推送/FB事件、不操作生产域名。当前没有生产流量或数据切换授权执行记录；正式切换步骤及最终验证结果将在后续任务补全。
