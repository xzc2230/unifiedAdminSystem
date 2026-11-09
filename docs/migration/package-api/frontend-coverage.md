# 源项目前端全量页面去向

日期：2026-11-09。共 50 个 Vue 视图文件，31 个 API 模块。业务页面重建，其余逐项登记复用或差异；工具复用不等于模板兼容。

| 源视图 | 处理方式 | 目标 | 活跃方法 |
| --- | --- | --- | --- |
| PackClient/src/views/error/401.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | back |
| PackClient/src/views/error/404.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | — |
| PackClient/src/views/index.vue | 空首页复用宿主 | 当前 JEECG 首页；源模板无业务内容 | — |
| PackClient/src/views/login.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | handleLogin、getCode、getCookie |
| PackClient/src/views/monitor/cache/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getList |
| PackClient/src/views/monitor/cache/list.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getCacheNames、refreshCacheNames、handleClearCacheName、getCacheKeys、refreshCacheKeys、handleClearCacheKey、nameFormatter、keyFormatter、handleCacheValue、handleClearCacheAll |
| PackClient/src/views/monitor/druid/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | — |
| PackClient/src/views/monitor/job/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getList、jobGroupFormat、cancel、reset、handleQuery、resetQuery、handleSelectionChange、handleCommand、handleStatusChange、handleRun、handleView、handleShowCron、crontabFill、handleJobLog、handleAdd、handleUpdate、submitForm、handleDelete、handleExport |
| PackClient/src/views/monitor/job/log.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getList、handleClose、handleQuery、resetQuery、handleSelectionChange、handleView、handleDelete、handleClean、handleExport |
| PackClient/src/views/monitor/logininfor/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getList、handleQuery、resetQuery、handleSelectionChange、handleSortChange、handleDelete、handleClean、handleUnlock、handleExport |
| PackClient/src/views/monitor/online/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getList、handleQuery、resetQuery、handleForceLogout |
| PackClient/src/views/monitor/operlog/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getList、typeFormat、handleQuery、resetQuery、handleSelectionChange、handleSortChange、handleView、handleDelete、handleClean、handleExport |
| PackClient/src/views/monitor/server/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getList |
| PackClient/src/views/redirect/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | — |
| PackClient/src/views/register.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | handleRegister、getCode |
| PackClient/src/views/system/applog/index.vue | 业务页面重建 | /package-management/request-logs | getList、cancel、reset、handleQuery、resetQuery、handleSelectionChange、handleAdd、handleUpdate、submitForm、handleDelete、handleClean、handleExport |
| PackClient/src/views/system/config/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getList、cancel、reset、handleQuery、resetQuery、handleSelectionChange、handleAdd、handleUpdate、submitForm、handleDelete、handleExport、handleRefreshCache |
| PackClient/src/views/system/count/index.vue | 业务页面重建 | /package-management/landing-counts | getList、cancel、reset、handleQuery、resetQuery、handleSelectionChange、handleAdd、handleUpdate、submitForm、handleDelete、handleExport |
| PackClient/src/views/system/dept/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getList、cancel、reset、handleQuery、resetQuery、handleAdd、toggleExpandAll、handleUpdate、submitForm、handleDelete |
| PackClient/src/views/system/dict/data.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getTypes、getTypeList、getList、cancel、reset、handleQuery、handleClose、resetQuery、handleAdd、handleSelectionChange、handleUpdate、submitForm、handleDelete、handleExport |
| PackClient/src/views/system/dict/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getList、cancel、reset、handleQuery、resetQuery、handleAdd、handleSelectionChange、handleUpdate、submitForm、handleDelete、handleExport、handleRefreshCache |
| PackClient/src/views/system/error/index.vue | 业务页面重建 | /package-management/event-errors | getList、cancel、reset、handleQuery、resetQuery、handleSelectionChange、handleAdd、handleUpdate、submitForm、handleDelete、handleExport |
| PackClient/src/views/system/event_config/index.vue | 业务页面重建 | /package-management/event-configs | getList、cancel、reset、handleQuery、resetQuery、handleSelectionChange、handleAdd、handleUpdate、submitForm、handleDelete、handleExport |
| PackClient/src/views/system/history/index.vue | 业务页面重建 | /package-management/event-history | getList、cancel、reset、handleQuery、resetQuery、handleSelectionChange、handleAdd、handleUpdate、submitForm、handleDelete、handleExport |
| PackClient/src/views/system/manager/index.vue | 业务页面重建 | /package-management/apps | getList、cancel、reset、handleQuery、resetQuery、handleSelectionChange、handleAreasChange、handleTransferChange、handleAdd、handleUpdate、submitForm、handleDelete、handlerTransfer、handlerTransferCancel、submitTransfer |
| PackClient/src/views/system/menu/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getList、getTreeselect、cancel、reset、showSelectIcon、selected、hideSelectIcon、handleQuery、resetQuery、handleAdd、toggleExpandAll、handleUpdate、submitForm、handleDelete |
| PackClient/src/views/system/notice/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getList、cancel、reset、handleQuery、resetQuery、handleSelectionChange、handleAdd、handleUpdate、submitForm、handleDelete |
| PackClient/src/views/system/notify/index.vue | 业务页面重建 | /package-management/notifications | getList、cancel、reset、handleQuery、resetQuery、handleSelectionChange、handleAdd、handleUpdate、submitForm、handleDelete、handleExport、getPwaList、handlePWANameChange |
| PackClient/src/views/system/post/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getList、cancel、reset、handleQuery、resetQuery、handleSelectionChange、handleAdd、handleUpdate、submitForm、handleDelete、handleExport |
| PackClient/src/views/system/pwa/index.vue | 业务页面重建 | /package-management/pwa | setup、created、delAllTag、tagClose、addTag、deleteComment、addComment、scoreValue、getList、cancel、reset、handleQuery、resetQuery、handleSelectionChange、handleAdd、handleUpdate、submitForm、handleDelete、handleExport、getSubHost、formatDateTime、padZero |
| PackClient/src/views/system/record/index.vue | 业务页面重建 | /package-management/app-events | getList、cancel、reset、handleQuery、resetQuery、handleSelectionChange、handleAdd、handleUpdate、submitForm、handleDelete、handleExport |
| PackClient/src/views/system/role/authUser.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getList、handleClose、handleQuery、resetQuery、handleSelectionChange、openSelectUser、cancelAuthUser、cancelAuthUserAll |
| PackClient/src/views/system/role/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getList、handleQuery、resetQuery、handleDelete、handleExport、handleSelectionChange、handleStatusChange、handleCommand、handleAuthUser、getMenuTreeselect、getDeptAllCheckedKeys、reset、handleAdd、handleUpdate、getRoleMenuTreeselect、getDeptTree、handleCheckedTreeExpand、handleCheckedTreeNodeAll、handleCheckedTreeConnect、getMenuAllCheckedKeys、submitForm、cancel、dataScopeSelectChange、handleDataScope、submitDataScope、cancelDataScope |
| PackClient/src/views/system/role/selectUser.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | show、clickRow、handleSelectionChange、getList、handleQuery、resetQuery、handleSelectUser |
| PackClient/src/views/system/statistics/index.vue | 业务页面重建 | /package-management/statistics | setup、created、getList、checkMoney、getTotal、cancel、reset、resetTotal、handleQuery、resetQuery、handleSelectionChange、handleAdd、handleUpdate、submitForm、handleDelete、handleExport |
| PackClient/src/views/system/store/index.vue | 业务页面重建 | /package-management/store | setup、created、getList、cancel、reset、handleQuery、resetQuery、handleSelectionChange、handleAdd、submitForm、handleDelete、handleUpdate |
| PackClient/src/views/system/user/authRole.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | clickRow、handleSelectionChange、getRowKey、close、submitForm |
| PackClient/src/views/system/user/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getDeptTree、getList、handleNodeClick、handleQuery、resetQuery、handleDelete、handleExport、handleStatusChange、handleCommand、handleAuthRole、handleResetPwd、handleSelectionChange、handleImport、importTemplate、submitFileForm、reset、cancel、handleAdd、handleUpdate、submitForm |
| PackClient/src/views/system/user/profile/index.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | getUser |
| PackClient/src/views/system/user/profile/resetPwd.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | submit、close |
| PackClient/src/views/system/user/profile/userAvatar.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | editCropper、modalOpened、requestUpload、rotateLeft、rotateRight、changeScale、beforeUpload、uploadImg、realTime、closeDialog |
| PackClient/src/views/system/user/profile/userInfo.vue | 基础页面复用 | JEECG 对应登录/个人中心/系统/监控/异常页；逐功能核验，不承诺旧页面路径兼容 | submit、close |
| PackClient/src/views/system/w2a/index.vue | 业务页面重建 | /package-management/w2a | setup、created、delAllTag、tagClose、addTag、deleteComment、addComment、scoreValue、getList、cancel、reset、handleQuery、resetQuery、handleSelectionChange、handleAdd、handleUpdate、submitForm、handleDelete、handleExport、getSubHost、formatDateTime、padZero |
| PackClient/src/views/tool/build/index.vue | 工具页面差异登记 | JEECG 工具/代码生成/Online 或接口文档；不照搬 RuoYi 模板 | — |
| PackClient/src/views/tool/gen/basicInfoForm.vue | 工具页面差异登记 | JEECG 工具/代码生成/Online 或接口文档；不照搬 RuoYi 模板 | — |
| PackClient/src/views/tool/gen/editTable.vue | 工具页面差异登记 | JEECG 工具/代码生成/Online 或接口文档；不照搬 RuoYi 模板 | submitForm、getFormPromise、close |
| PackClient/src/views/tool/gen/genInfoForm.vue | 工具页面差异登记 | JEECG 工具/代码生成/Online 或接口文档；不照搬 RuoYi 模板 | subSelectChange、tplSelectChange、setSubTableColumns、getMenuTreeselect |
| PackClient/src/views/tool/gen/importTable.vue | 工具页面差异登记 | JEECG 工具/代码生成/Online 或接口文档；不照搬 RuoYi 模板 | show、clickRow、handleSelectionChange、getList、handleQuery、resetQuery、handleImportTable |
| PackClient/src/views/tool/gen/index.vue | 工具页面差异登记 | JEECG 工具/代码生成/Online 或接口文档；不照搬 RuoYi 模板 | getList、handleQuery、handleGenTable、handleSynchDb、openImportTable、resetQuery、handlePreview、copyTextSuccess、handleSelectionChange、handleEditTable、handleDelete |
| PackClient/src/views/tool/swagger/index.vue | 工具页面差异登记 | JEECG 工具/代码生成/Online 或接口文档；不照搬 RuoYi 模板 | — |

## API 模块去向

| 源模块 | 处理方式 | 导出函数 |
| --- | --- | --- |
| PackClient/src/api/login.js | 宿主能力适配/工具差异登记 | login、register、getInfo、logout、getCodeImg |
| PackClient/src/api/menu.js | 宿主能力适配/工具差异登记 |  |
| PackClient/src/api/monitor/cache.js | 宿主能力适配/工具差异登记 | getCache、listCacheName、listCacheKey、getCacheValue、clearCacheName、clearCacheKey、clearCacheAll |
| PackClient/src/api/monitor/job.js | 宿主能力适配/工具差异登记 | listJob、getJob、addJob、updateJob、delJob、changeJobStatus、runJob |
| PackClient/src/api/monitor/jobLog.js | 宿主能力适配/工具差异登记 | listJobLog、delJobLog、cleanJobLog |
| PackClient/src/api/monitor/logininfor.js | 宿主能力适配/工具差异登记 | list、delLogininfor、unlockLogininfor、cleanLogininfor |
| PackClient/src/api/monitor/online.js | 宿主能力适配/工具差异登记 | list、forceLogout |
| PackClient/src/api/monitor/operlog.js | 宿主能力适配/工具差异登记 | list、delOperlog、cleanOperlog |
| PackClient/src/api/monitor/server.js | 宿主能力适配/工具差异登记 | getServer |
| PackClient/src/api/system/applog.js | 业务 API 重建 | listLog、getLog、addLog、updateLog、delLog、cleanAppLogs |
| PackClient/src/api/system/comment_record.js | 业务 API 重建 | listReviewsRecord、getReviewsRecord、addReviewsRecord、updateReviewsRecord、delReviewsRecord |
| PackClient/src/api/system/config.js | 宿主能力适配/工具差异登记 | listConfig、getConfig、getConfigKey、addConfig、updateConfig、delConfig、refreshCache |
| PackClient/src/api/system/count.js | 业务 API 重建 | listCount、getCount、addCount、updateCount、delCount |
| PackClient/src/api/system/dept.js | 宿主能力适配/工具差异登记 | listDept、listDeptExcludeChild、getDept、addDept、updateDept、delDept |
| PackClient/src/api/system/dict/data.js | 宿主能力适配/工具差异登记 | listData、getData、getDicts、addData、updateData、delData |
| PackClient/src/api/system/dict/type.js | 宿主能力适配/工具差异登记 | listType、getType、addType、updateType、delType、refreshCache、optionselect |
| PackClient/src/api/system/error.js | 业务 API 重建 | listError、getError、addError、updateError、delError |
| PackClient/src/api/system/event_config.js | 业务 API 重建 | listEvent_config、getEvent_config、addEvent_config、updateEvent_config、delEvent_config |
| PackClient/src/api/system/history.js | 业务 API 重建 | listHistory、getHistory、addHistory、updateHistory、delHistory |
| PackClient/src/api/system/manager.js | 业务 API 重建 | listManager、getManager、addManager、handleAreasToStr、updateManager、delManager、transferApp |
| PackClient/src/api/system/menu.js | 宿主能力适配/工具差异登记 | listMenu、getMenu、treeselect、roleMenuTreeselect、addMenu、updateMenu、delMenu |
| PackClient/src/api/system/notice.js | 宿主能力适配/工具差异登记 | listNotice、getNotice、addNotice、updateNotice、delNotice |
| PackClient/src/api/system/notify.js | 业务 API 重建 | listNotify、getNotify、addNotify、updateNotify、delNotify、listPwa |
| PackClient/src/api/system/post.js | 宿主能力适配/工具差异登记 | listPost、getPost、addPost、updatePost、delPost |
| PackClient/src/api/system/pwa.js | 业务 API 重建 | listPwa、getPwa、addPwa、updatePwa、delPwa |
| PackClient/src/api/system/record.js | 业务 API 重建 | listRecord、getRecord、addRecord、updateRecord、delRecord |
| PackClient/src/api/system/role.js | 宿主能力适配/工具差异登记 | listRole、getRole、addRole、updateRole、dataScope、changeRoleStatus、delRole、allocatedUserList、unallocatedUserList、authUserCancel、authUserCancelAll、authUserSelectAll、deptTreeSelect |
| PackClient/src/api/system/statistics.js | 业务 API 重建 | listStatistics、listStatisticsNum、getStatistics、addStatistics、updateStatistics、delStatistics |
| PackClient/src/api/system/store.js | 业务 API 重建 | listStore、getStore、addStore、delStore、updateStore |
| PackClient/src/api/system/user.js | 宿主能力适配/工具差异登记 | listUser、getUser、addUser、updateUser、delUser、resetUserPwd、changeUserStatus、getUserProfile、updateUserProfile、updateUserPwd、uploadAvatar、getAuthRole、updateAuthRole、deptTreeSelect |
| PackClient/src/api/tool/gen.js | 宿主能力适配/工具差异登记 | listTable、listDbTable、getGenTable、updateGenTable、importTable、previewTable、delTable、genCode、synchDb |

## 公共组件、资源及基础设施

额外 180 个 src 文件逐项登记于 full-coverage-audit.json 的 frontendSupport。包含 assets 102、components 31、layout 13、utils 12、store 7、plugins 6、directive 4，以及路由/根入口/配置共 5 个文件；不因不复制主题代码而遗漏图片上传/预览、评分、日期、导出、鉴权或路由行为。
