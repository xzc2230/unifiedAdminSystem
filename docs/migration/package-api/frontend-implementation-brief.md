# 前端与单根菜单实施补充说明

本文件配合执行计划 Task 4；实际源代码及控制器最终契约为依据。源：D:/workspace/package-api/package-api/PackClient。目标：upstream/JeecgBoot/jeecgboot-vue3。先读目标 AGENTS.md。保留当前 JEECG 风格，使用 BasicTable/BasicForm、Ant Design Vue、modal/drawer、当前上传和字典能力。

## 文件与菜单

页面 src/views/packagemanagement；API src/api/packagemanagement；类型 src/types/packagemanagement。独立目录，不塞进现有 system 或 siteplatform 页面。一个根菜单「应用包管理」/package-management，13个子菜单，名称/路由参照执行计划。PWA/W2A共享实际业务编辑器可以复用组件，但必须有各自页面入口并固定 appType=0/1。

菜单SQL放新模块 src/main/resources/db/package，使用稳定ID和可重复执行插入；按钮权限 package:{domain}:{action} 与控制器一致。不要自动赋给全体用户。根菜单 alwaysShow，首个有权限子菜单作为重定向；没有可见子页则隐藏根菜单。现有 store/modules/permission.ts 在 BACK 分支调用 changePermissionCode→addSlashToRouteComponent→transformObjToRoute→transformRouteToMenu，可在变换前调用仅作用于 /package-management 的纯函数，避免改变其他菜单行为。测试全量/部分/空子菜单三种情况。

## 页面核对

frontend-coverage.md 和 full-coverage-audit.json 包含12业务页面及全部API/支持文件。必须逐项对照源页面的活跃按钮、查询、字段、详情、弹窗、校验与导出；不要只建13张通用表格。源代码注释掉的脚手架与活跃功能应区分。`${comment}`占位标签应换为清楚的中文业务标签。

- apps：包名查询；完整应用/作者/包名/URL/开关/白名单/地区/禁美国/自用/备注；地区数组与逗号串转换；有权限的接收人选择和转移；归属不由前端userId授权。
- store：包名和状态筛选；0/1/2状态；日期和过审/下架邮箱；市场链接按源Google Play地址生成；编辑状态与权限跟接口一致。
- statistics：包名/隐藏地区查询；独立汇总查询条件，汇总不取当前分页；金额2位；新增与修改包名锁定语义；注册/充值字段源码已注释的编辑控件不要当成必须开放的功能。
- event-configs：应用/包名/FB token/pixel/落地页URL；event-history：全部有效事件字段、查询和详情；event-errors：包名/消息/内容；支持源中的CRUD和导出能力。
- landing-counts：应用/日期/访问/下载；app-events：应用ID/域名/事件类型和名称/设备/IP；请求日志：包名/日期区间，ES总数、请求参数/响应/异常详情、清理和导出（按活跃源功能与API）。
- notifications：选择当前用户有权访问的PWA，展示应用信息，标题/消息/图标，新增触发的发送语义保持，不能模拟成功。
- reviews：独立评论管理并保留PWA内嵌评论编辑，父PWA校验归属。

## PWA/W2A必须保留

应用名、图标、五图、描述、语言、推广子域名、域名后缀、包网URL、事件和配置、下载数、标签、评分、评论数、评论集合；W2A增加downloadUrl。图片使用当前上传/预览组件，保留源存储字段格式。域名后缀从 load_host 字典 value→label 得到 appSub，不硬编码生产域名。保留 event_name、country_name 等实际使用字典，缺少字典时有明确空状态。

tags 为 JSON 数组字符串。score 为 JSON 对象 score1..score5；加权公式严格按源码 score1*1+score2*2+score3*3+score4*4+score5*5，除以总数，1位小数，零总数显示0。源码输入标签排列与公式有反向关系，先保留字段及公式，不擅自交换数据含义。提交需非空标签、非零评分、至少一条评论，每条包含name/content/headPortraitUrl/createTime。评论日期本地 yyyy-MM-dd HH:mm:ss。已经持久化的评论删除会立即调用删除API，即使随后取消主表弹窗也不会恢复，保持此行为并让用户明确操作结果。

## 验证与交付

用 node:test 或现有测试方式覆盖标签/评分/地区/日期转换和菜单权限归一化；构建必须通过。检查所有页面API地址、HTTP方法、参数和响应与Task 3最终契约一致，错误状态不可伪装成功。写 docs/migration/package-api/task-4-report.md，含源功能→目标文件/操作→验证的对照，以及实际运行命令。不要修改用户已有README/AWS工作区改动。
