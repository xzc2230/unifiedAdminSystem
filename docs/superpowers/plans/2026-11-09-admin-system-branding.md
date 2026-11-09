# 后台管理系统品牌文案替换 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将前端用户可见的 JeecgBoot 品牌文案统一替换为“后台管理系统”。

**Architecture:** 直接修改 Vue 页面和中文 locale 中的显示字符串，不改变 API、组件名、依赖名、链接或注释。完成后通过源码复查和现有生产构建验证。

**Tech Stack:** Vue 3、TypeScript、Vite、pnpm。

## Global Constraints

- 仅修改 `upstream/JeecgBoot/jeecgboot-vue3` 前端用户可见文案。
- 保留代码标识、接口路径、依赖名、链接、注释和数据库示例连接字符串。
- 使用简体中文“后台管理系统”作为统一品牌文案。

---

### Task 1: 替换前端可见品牌文案

**Files:**
- Modify: `upstream/JeecgBoot/jeecgboot-vue3/src/locales/lang/zh-CN/sys.ts`
- Modify: `upstream/JeecgBoot/jeecgboot-vue3/src/views/sys/login/TokenLoginPage.vue`
- Modify: `upstream/JeecgBoot/jeecgboot-vue3/src/views/sys/about/index.vue`
- Modify: `upstream/JeecgBoot/jeecgboot-vue3/src/views/demo/jeecg/JCodeEditDemo.vue`
- Modify: `upstream/JeecgBoot/jeecgboot-vue3/src/views/super/airag/aiapp/chat/AiChat.vue`

**Interfaces:**
- Consumes: 现有 Vue 模板和中文 locale 文本。
- Produces: 所有指定页面显示“后台管理系统”，不改变运行时接口和内部命名。

- [ ] **Step 1: 修改登录页和 locale 文案**

将 `signInTitle: 'Jeecg Boot'` 改为 `signInTitle: '后台管理系统'`，将登录加载标题 `JeecgBoot 企业级低代码平台` 改为 `后台管理系统`。

- [ ] **Step 2: 修改关于页文案**

将关于页品牌链接文字改为“后台管理系统”，并将产品介绍改为“是一款功能完善、易于使用的后台管理系统，提供用户管理、权限管理、菜单管理、日志管理等常用功能，帮助团队高效构建和维护业务应用。”；保留 `GITHUB_URL` 和链接结构不变。

- [ ] **Step 3: 修改示例文案**

将代码编辑示例的 `defaultValue: 'Hello JeecgBoot'` 改为 `defaultValue: 'Hello 后台管理系统'`，将 AI 示例的 `介绍一下JeecgBoot` 改为 `介绍一下后台管理系统`，对应描述同步修改。

- [ ] **Step 4: 复查用户可见命中**

在前端目录执行：

```powershell
rg -n -i 'jeecgboot|jeecg boot' src
```

预期只剩非显示内容，例如 GitHub 链接、代码注释和数据库示例连接字符串；不得剩余登录、关于、示例提示中的品牌文案。

- [ ] **Step 5: 运行生产构建**

在前端目录执行：

```powershell
pnpm build
```

预期：命令成功退出，Vue/TypeScript/Vite 打包无错误。

- [ ] **Step 6: 提交变更**

```powershell
git add docs/superpowers/specs/2026-11-09-admin-system-branding-design.md docs/superpowers/plans/2026-11-09-admin-system-branding.md upstream/JeecgBoot/jeecgboot-vue3/src
git commit -m "feat: replace frontend branding with admin system"
```
