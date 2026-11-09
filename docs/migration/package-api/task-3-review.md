# Task 3 independent review

2026-11-09. Reviewer: review_controllers. Reviewed `.runtime/package-review-task3.diff` (working tree, base 59cc625), requirements and implementation report. Read-only; no suite rerun or Git changes. Initial verdict: **Spec not satisfied / quality NeedsFixes**.

Three Important findings, no confirmed Critical finding:

1. `PackageFileController.java:66`: download path normalized only against profile root, so `fileName=../upload/existing.png&delete=true` could delete a sibling upload anonymously. Source rejects traversal. Reject traversal and enforce the real download subtree, including link targets; verify sibling survives.
2. `PackageResourcePolicy.java:107`: new nested comments claimed by acting administrator instead of the mapped PWA owner. Parent owner would subsequently fail nested-comment ownership validation when saving their PWA. Inherit actual parent ownership in the same transaction and test administrator insertion followed by owner edit.
3. `PackageResourcePolicy.java:102`: nested comment publication dates overwritten with current/previous dates. Source PWA service passes submitted dates directly to the review mapper and active source form exposes a date picker. Preserve supplied valid dates on insertion and editing, with fallback only when absent. Standalone source review creation has separate server-date semantics.

Strengths verified: isolated namespaces, narrow Shiro allowance, per-action permissions, resource scoping, transactional SQL changes, mutation allowlists, private-key redaction and local serialization. The 99 dynamic cases prove route resolution, not all business branches.

Outside-diff checks were confined to named risks: source CommonController/FileUtils/MimeTypeUtils/LoadCountAccessController/PwaNotifyController, PWA form dates, migrated PackageAccess/PackageIdentityRepository, truncated AppPwaServiceImpl and AppManagerServiceImpl/StoreStatusMapper.xml. Root separately verified the two MySQL tests (2 passed) in `.runtime/package-mysql/task3-live.log`. Candidate-host and junction checks were pending at review time.

Independent repair review: **Spec Approved / quality Approved**. All three Important findings resolved, no remaining Critical/Important finding. Downloads now enforce the canonical download subtree and reject traversal/separators/drive inputs; nested and standalone new reviews inherit the actual mapped parent owner in the SQL transaction; nested review publication dates preserve supplied dates and fall back only when absent.

An actual-host transfer-picker failure additionally exposed mixed utf8mb3/utf8mb4 ID columns. Both join operands now convert to utf8mb4 before COLLATE. The rollback-only real MySQL controller regression passed (1 passed, no skips), `.runtime/package-mysql/task3-transfer-targets.log`. Repair gate: 159 discovered, 156 passed, 3 skipped, no failures/errors, `.runtime/package-task3-review-fixes.log`. Two skipped MySQL checks were separately executed successfully; the OS symlink restriction is covered by root's real-HTTP junction check.

Evidence limitation: review-owner regression verifies policy delegation with a mocked PackageAccess. New repository INSERT…SELECT inheritance SQL was inspected; that assertion alone is not a real database owner-inheritance test. Rebuilt-candidate HTTP evidence is recorded separately in verification.md.

2026-11-09 增量复审：真实HTTP发现的异常处理优先级问题已修正，Spec Approved / Quality Approved。最高优先级只作用于controller.admin，回归将实际宿主异常处理器按两种顺序一起注册，验证模块状态码并确认无关宿主控制器行为保持。6项覆盖测试通过。根代理已重新打包、启动候选，35项实际HTTP检查全部通过（17基础/安全、4补充协议、14管理/导出），包含store禁用导出的HTTP404。
