# dev 包统计后端迁移

源：package-api dev `0f83f81`。目标：独立 jeecg-module-package，不调用旧 statistics/store 业务。

## 接口与权限

管理路径 `/package-admin/packstat`：GET `/list`、`/authors`、`/regions`、`/surviveSummary`、`/{id}`；POST 和 PUT 根路径；DELETE `/{ids}`。所有入口通过 PackageAdminSupport 核验登录、有效租户/映射及 `package:packstat:{query,add,edit,remove}` 权限。

list 采用 JEECG `pageNo/pageSize`，返回 `Result.result={records,total}`；日期字段仍为 yyyy-MM-dd。summary 保持源 s7/u7 到 s15/u15，未来月份返回 noRecord。作者选项、汇总与列表都受相同租户/用户限制。

新主表内置 tenant_id/owner_user_id，由后端赋值，普通账号仅访问本人记录，模块管理员仍限制当前租户。导入源历史记录时必须明确补齐目标租户和用户，不能让请求自行认领旧数据。

## 规则与依赖

PackStatDateCalc、MonthFilter、UrlParser、DetectResult、DetectOutcomeApply、IntervalSupport、MailContentBuilder、NotifyEmailSupport 保留源规则。实际汇总 SQL 使用互斥 7~14 天分桶与 15+，按 pass_date 月份过滤，不用旧的生命周期重叠过滤或窗口天数求和。

检测只选 drop_date 为空且有商店链接的记录：404下架，200/301/302正常，其他响应/网络错误无效。无效结果记录历史和调整间隔，不回写日期/天数；有效结果回写日期口径天数，首次发现下架通知。线程数、请求间隔、自适应0~60秒节流、5天历史清理保持源实现。

邮件独立使用 `jeecg.package-management.packstat.mail` 配置：enabled、host、port、username、password、from、ssl-enable。默认 enabled=false，host/username/password/from 均为空。按源规则优先行 notifyEmail，否则检测配置全局 notify_email。没有复制源SMTP账号或密钥。源邮件发送错误只记日志，不改变已保存的下架结果；此次保持该语义。

## 数据库与定时任务

部署先人工执行模块资源 `db/package/009-dev-packstat.sql`。创建 pkg_pack_stat、pkg_pack_stat_region、pkg_pack_stat_detect_config、pkg_pack_stat_detect_history 四表，包含全部源地区种子与默认检测配置。CREATE IF NOT EXISTS / INSERT IGNORE / 默认配置不存在才插入可重复执行；本轮没有连接生产库或导入历史数据。

JEECG Quartz 中配置 Job 类 `org.jeecg.modules.packagemanagement.job.PackagePackStatJob`，参考 cron `0 0/10 * * * ?`。该 Job 带 DisallowConcurrentExecution；本轮只提供实现，不自动创建/启用线上任务。源 detection_frequency_minutes 是与 Quartz cron 对齐的配置说明，和源一样不会自动重写 Quartz；修改检测频率时需同时修改任务 cron。不要复制源 sys_job 的 RuoYi invoke_target。

检测跨租户扫描属于后台受信调度；管理HTTP不会提供任意执行检测、配置或历史表的匿名入口。

## 验证

先落地源业务契约测试，RED 日志 `.runtime/package-dev-packstat-red.log` 确认包统计业务类缺失。新增租户/所有者访问测试，后续补 Mapper XML绑定与SQL范围、CRUD默认值、汇总参数、跨租户批量删除拒绝测试。

最终定向 Maven 命令：`mvn -q -f upstream/JeecgBoot/jeecg-boot/pom.xml -pl jeecg-boot-module/jeecg-module-package -am test -DskipTests=false -Dtest=PackagePackStat*Test -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.repo.local=D:/workspace/JEECG/.runtime/m2`（JAVA_HOME 使用本机 JDK21）。41 项通过，0 失败/错误/跳过；见 `.runtime/package-dev-packstat-final.log` 与模块 target/surefire-reports 的 12 份 PackagePackStat 报告。覆盖源纯业务规则、租户隔离、Mapper绑定/SQL范围与互斥桶、服务默认值/归属保护、检测有效/无效回写与首次通知。尚未连接真实商店、发真实邮件或在本轮验证生产数据。
