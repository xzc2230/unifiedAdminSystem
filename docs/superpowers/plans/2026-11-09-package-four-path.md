# 四段客户端路径迁移实施计划

**Goal:** 迁移当前源项目的 POST /{q}/{w}/{e}/{r}，保持实际协议与业务判断。
**Architecture:** 在 package 模块内增加独立 application/apps/risk/app2；控制器经 ClientService.app2 委托，持久化使用 pkg_ 表、Redis 使用 package:legacy:app2: 前缀。
**Tech Stack:** Spring MVC / MyBatis / StringRedisTemplate / Fastjson2 / Vue BasicForm。

## 约束
源项目只读；保留其 InstallReferrer 查询串解析返回空对象的既有行为；时区/SIM 判断仍停用。使用既有 PACKAGE_DEFAULT_AES_KEY 配置回退密钥。不会修改其他归因模块、生产环境入口或数据。本次不提交 Git。

## 实施步骤
- [x] 先增加 MockMvc 四段路径测试，确认当前返回 404；精确路由 login 优先，GET/非 JSON 不接收。
- [x] 移植源 App2 的九个类和 XOR Codec；HttpHelper 替换为请求属性缓存正文（解析与日志重复读取）；RedisCache 替换为模块专用 StringRedisTemplate。
- [x] 复制源四类黑名单领域及 MyBatis 查询映射，保留 enabled 与 priority 顺序；AppManager 增加五项实际使用配置。生成独立增量 SQL，字段存在时不重复添加。
- [x] 添加 app2 委托、AES 响应及日志；补齐 Geo ASN 原始字段。测试解码、开关、地区、黑名单、Referrer、Redis 阈值和响应。
- [x] 应用管理现有表单增加风控配置，与后端允许编辑字段保持一致。
- [x] 执行 package 模块回归、MySQL/Redis 集成与前端测试；更新迁移文档，记录请求协议、SQL 和源实现疑点。

## 验证命令
后端：Maven reactor -pl jeecg-boot-module/jeecg-module-package -am -DskipTests=false -Dtest=Package*Test -Dsurefire.failIfNoSpecifiedTests=false test。
前端：node --test --test-isolation=none tests/package-management.test.mjs。
新增测试将真实 XOR JSON 后调用 /package-legacy/a/b/c/d，解密 AES 验证 code/data/byme/ps；使用专用测试 MySQL 和 Redis，不调用生产。

执行完成：后端181项（180通过，1项Windows符号链接权限跳过），前端15项通过；新增MySQL/Redis测试未跳过。只读审查的Geo缺值问题已修复并复核。
