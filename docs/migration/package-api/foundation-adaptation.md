# 宿主基础能力与源后台差异

范围：full-coverage-audit.json 中的119条基础接口。它们与99条迁移业务/资源路径分别验收。此处记录宿主已有对应能力和实际差异，不表示原后台请求协议、表数据或全部按钮行为等价；旧客户端业务兼容范围不因此缩减。

宿主Java路径前缀：upstream/JeecgBoot/jeecg-boot/jeecg-module-system/jeecg-system-biz/src/main/java/org/jeecg/modules/。除明确说明外，下面仅完成源码核验，尚未逐一运行验证。

| 源控制器（路径数） | 宿主文件及实际入口 | 适配/差异 |
| --- | --- | --- |
| CaptchaController（1） | system/controller/LoginController.java；GET /sys/randomImage/{key} | 验证码字段、缓存键与登录请求采用JEECG；隔离环境实际成功 |
| SysLoginController（3） | system/controller/LoginController.java；POST /sys/login；system/controller/SysPermissionController.java 的 /getUserPermissionByToken | 使用JEECG token/用户信息/动态菜单，源 /login、getInfo、getRouters 不在宿主根路径重建；隔离环境登录成功 |
| SysRegisterController（1） | system/controller/SysUserController.java；POST /sys/user/register | 注册请求及启用策略采用宿主；不导入RuoYi账号体系 |
| SysProfileController（4） | system/controller/SysUserController.java；/appEdit、/changePassword、/getUserInformation；头像上传沿用宿主公共上传 | 当前个人中心代替旧页面，字段和上传返回采用宿主；源4条协议不重建 |
| SysUserController（14） | system/controller/SysUserController.java；/list、/queryById、/add、/edit、/deleteBatch、/resetPassword、/frozenBatch、/exportXls、/importExcel、/queryUserRole、/addSysUserRole | 用户字符串ID、租户、部门、岗位及角色关联采用宿主。源导入模板及错误回执不同；旧数值ID仅通过pkg_user_identity映射 |
| SysRoleController（15） | system/controller/SysRoleController.java；/sys/role/list、/queryById、/add、/edit、/deleteBatch、/exportXls、/datarule；SysPermissionController.java 的 /queryRolePermission、/saveRolePermission；SysUserController.java 的 /userRoleList、/deleteUserRoleBatch | 源dataScope数值与JEECG数据规则非同一模型，须明确重配；角色状态/组织树/未分配用户列表不承诺一一等价 |
| SysDeptController（6） | system/controller/SysDepartController.java；/sys/sysDepart/queryTreeList、/queryByIds、/add、/edit、/delete | 用宿主组织树；excludeChild在选父节点时按当前组织能力处理，源整型部门ID不直接复用 |
| SysPostController（7） | system/controller/SysPositionController.java；/sys/position/list、/queryById、/add、/edit、/deleteBatch、/exportXls | 岗位/职务映射到宿主position，旧岗位ID与人员关联需要核对转换 |
| SysMenuController（7） | system/controller/SysPermissionController.java；/sys/permission/list、/queryTreeList、/queryRolePermission、/add、/edit、/deleteBatch | 采用宿主菜单和按钮授权；13个迁移业务菜单在独立根目录，源菜单表不直接导入 |
| SysDictTypeController（8） | system/controller/SysDictController.java；/sys/dict/list、/add、/edit、/deleteBatch、/exportXls、/refleshCache、/queryAllDictItems | 字典类型采用JEECG；无相同旧dictId详情协议，需按宿主列表/树读取 |
| SysDictDataController（7） | system/controller/SysDictItemController.java；/sys/dictItem/list、/add、/edit、/deleteBatch；SysDictController.java 的 /getDictItems/{dictCode} | 保持业务字典value/label含义；导出由宿主字典整体导出，旧单类型导出协议不同 |
| SysConfigController（8） | 源后台通用key/value参数CRUD没有相同宿主控制器；模块业务外部配置统一jeecg.package-management.* | 明确差异：不虚构已复用的通用参数管理页；RuoYi登录/注册等框架参数不迁入业务模块。源业务配置和域名字典按独立模块配置/JEECG字典落地 |
| SysNoticeController（5） | system/controller/SysAnnouncementController.java；/sys/annountCement/list、/queryById、/add、/edit、/deleteBatch | 通知公告沿用宿主发布、接收人和已读模型；源notice表不直接导入 |
| SysJobController（8） | quartz/controller/QuartzJobController.java；/sys/quartzJob/list、/queryById、/add、/edit、/deleteBatch、/pause、/resume、/execute、/exportXls | Quartz调用表达式转换为宿主Job类和参数；源ryTask.ryGetStatus需绑定迁移模块Job，不能照抄方法表达式 |
| SysJobLogController（5） | 宿主现有Quartz任务及系统日志入口；无源sys_job_log同结构CRUD控制器 | 明确差异：源日志详情、导出及clean协议不作为已等价能力；历史任务日志另行归档 |
| SysLogininforController（5） | system/controller/SysLogController.java；/sys/log/list、/deleteBatch、/exportXls（登录日志筛选） | 源info表和状态字段不同；源unlock及一键clean没有直接一一对应入口，不宣称已实现 |
| SysOperlogController（4） | system/controller/SysLogController.java；/sys/log/list、/deleteBatch、/exportXls（操作日志筛选） | 使用宿主操作审计；源一键clean及历史operlog数据没有直接一一对应协议 |
| SysUserOnlineController（2） | system/controller/SysUserOnlineController.java；GET /sys/online/list；POST /sys/online/forceLogout | 使用宿主在线会话和token强退语义，不能拿旧tokenId直接调用 |
| CacheController（7） | monitor/controller/ActuatorRedisController.java；/sys/actuator/redis/info、/keysSize、/memoryInfo | 监控概览复用；源缓存名/键值浏览及按名/按键/全部清除没有同等入口，不扩大宿主缓存清理权限 |
| ServerController（1） | 前端src/views/monitor/server/server.api.ts及monitor/controller/ActuatorRedisController.java的queryDiskInfo | 使用宿主Actuator/服务器/磁盘监控，源单一/monitor/server响应不重建；生产Actuator暴露策略依部署配置 |
| SysIndexController（1） | 宿主启动/首页路由 | 不将旧服务根提示页覆盖宿主入口或业务通配路径 |

上述路径数合计119。19个基础/工具前端API模块的去向已在frontend-coverage.md逐文件列出；迁移业务页面仅调用新的业务API和宿主现有字典/上传/用户选择能力。未建立旧后台的通用登录、用户、菜单和监控代理。
