# 项目创建（SAF）

## Scope

补 Task 3 最后一块：安卓端经 SAF 目录选择器创建新 pagenote 项目（`project.pagenote` + `src/left.md` + `src/right.md` + `src/assets/`），创建后直接打开。与桌面 `POST /api/projects` 同 manifest 语义；assets 导入导出与 SAF 原子写已有实现，本片只做创建与打开。

## Interaction

- 阅读器运行时无项目状态（`project == null`）显示 `Open project` 旁新增 `New project` 按钮；点击弹创建弹窗（名称输入 + 目录选择器）。
- 目录选择经 `ActivityResultContracts.OpenDocumentTree` 拿持久权限；名称校验与桌面一致（非空，不含 `<>:"/\|?*`）。
- 创建中禁用按钮并显示进度；重名/无权限/创建失败显示可恢复错误，不闪退；成功后直接打开新项目（复用现有 open 流程）。

## Data Contract

- 经 `DocumentsContract.createDocument` 在所选树下建 `<name>/` → `src/` → `left.md`（`# <name>\n`）、`right.md`（空）、`assets/` 目录、`project.pagenote`（manifest JSON，字段与 `ProjectStore.create` 一致：format/version/id/name/created/left/right）。
- id 为随机 UUID 的 SHA-256 hex；created 为 ISO-8601；left/right 固定 `src/left.md`/`src/right.md`。
- 同名目录存在时报错（桌面 `fs.mkdir` EEXIST 语义），不覆盖；任一步失败后尝试清理已建的空目录残留，失败则报告残留名。

## Acceptance (EARS)

- When 输入合法名称并选定目录时，阅读器 shall 建出完整项目结构并直接打开。
- When 名称非法或目录同名已存在时，shall 拒绝并提示，不创建半截项目。
- When 创建中途失败时，shall 报告错误并尽力清理残留。

## Verification

- JVM：名称校验、manifest 字段、SAF 创建顺序（fake DocumentsContract 层）；仪器测试编译：创建弹窗打开+确认。
- `cd android && ./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --offline` 通过；真机建项目待 task 12。
