# 应用包管理子菜单渲染排查

2026-11-09，在线浏览器刷新后仍可复现：应用包管理为没有展开箭头的叶子项，点击没有子菜单；服务器实际权限 API 根组件为 LAYOUT，13 个子节点完整，中文名称正常。

根因在渲染环节：`compUtils.ts` 的 `checkChildrenHidden` 对 `alwaysShow` 为真时直接返回 false。BasicSubMenuItem 和 SimpleSubMenu 均依赖此结果决定是否渲染 SubMenu。此宿主将该标志用于聚合路由，之前将它误解为“始终显示子菜单”，数据库种子与前端归一化均设为 true，导致 LAYOUT 修复后仍渲染为叶子。

本次仅调整 package 根：种子 always_show=0，前端归一化 alwaysShow=false（含 meta），提供精确匹配根 ID 与 URL 的数据库修复脚本。保留宿主通用菜单语义、其他菜单及权限。

回归测试执行真实 checkChildrenHidden 以及两个侧栏组件的 menuHasChildren 判断，覆盖布尔值及数字形式的旧标志，并验证全隐藏子菜单仍不展示。测试修复前按预期失败，修复后完整 15 项通过。部署后还需实际浏览器展开并进入子页面验证。
