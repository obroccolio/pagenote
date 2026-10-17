# 项目资源 SAF 直读写

## Scope

补 Task 3 最后一块。安卓 SAF 项目即源目录本身（直读直写），无需桌面式 uploads 中转；本片只做引用校验 + 缺失报告 + 预览侧已落地的本地加载，不做搬运。

## Semantics

- 打开项目时：扫描 `right.md` 的 `assets/<name>` 引用，经 `DocumentsContract` 查 `src/assets/<name>` 是否存在；缺失列表记入 `noteStatus` 级 warning（页面照常打开，不阻断）。
- 保存笔记时：文本中的 `assets/<name>` 引用随 `right.md` 直写，不搬运；新增附件由用户经系统文件管理器放入 `src/assets/`，应用只读。
- 预览时：`NoteAssetSchemeHandler` 已接本地加载（存在显示，缺失透明占位）；本片补缺失 warning 文案透出。
- `ProjectAssets` File 版 helper 保留给 shared-tests 契约回归，不删。

## Acceptance (EARS)

- When 打开含缺失资源引用的项目时，阅读器 shall 照常打开并提示缺失文件名。
- When 保存含资源引用的笔记时，shall 直写引用文本，不搬运文件。
- When 预览缺失资源时，shall 显示占位并记 warning。

## Verification

- JVM：引用扫描（存在/缺失/无引用三态）纯函数测试。
- `cd android && ./gradlew :app:testDebugUnitTest --offline` 通过；真机待 task 12。
