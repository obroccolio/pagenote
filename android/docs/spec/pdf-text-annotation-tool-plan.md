# Android PDF Text 工具实施计划

**Goal:** 将桌面 Handwriting > Text 点击放置流程接入 Android 原生 PDF Reader。

**Architecture:** 复用已有 `annotateMode` tap-to-place、`AnnotationTextInputDialog`、`AnnotationTextDraft` 和 `ProjectAnnotationStore`。增加专用 text-only 模式状态；它隐藏通用 Annotate 的文字/区域切换，取消后保留放置模式，成功保存后清除模式。持久化仍经现有恢复式 SAF 写入和当前页 overlay 刷新。

**Spec:** `android/docs/spec/features/pdf-text-annotation-tool.md`

## 约束

- 只测试 Android 14/API 34 平板 AVD `emulator-5554`；不增加设备/API/性能矩阵。
- Text 命令沿用 `canUsePen` 门控；笔迹、线/箭头/形状和通用 Annotate 行为不改变。
- 只允许 app 内 MainActivity/Compose 仪器测试，不启动 DocumentsUI 或其他外部 app；使用测试生成 PDF 和现有 SAF provider。
- 每次 push 均更新根 `CHANGELOG.md`；推送到 `fork/codex/android-port`，不发布公开二进制。

## Steps

1. 在 `android/app/src/androidTest/java/io/pagenote/android/PdfTextAnnotationToolTest.kt` 先写失败流程：无项目/Markdown 禁用；有可写 PDF 时启用；点击后纯文字 dialog 不显示区域切换；取消/空白不写入；提交后检查单点坐标、颜色、宽度、页隔离和 overlay。
2. 在唯一 API 34 AVD 用显式 `ANDROID_SERIAL=emulator-5554` 运行该单项，确认 Text 目前 disabled，观察 RED。
3. 增加 Text-only 状态并接通 menu action；复用 tap 事件和 annotation writer，只在 Text 模式隐藏类型切换，成功或取消的生命周期按 SPEC 清理。
4. 运行聚焦 AVD 流程至 GREEN，随后运行 `:app:testDebugUnitTest`、`:app:assembleDebug` 和 `git diff --check`。
5. 更新 task 6.6、SPEC/计划进度和根 `CHANGELOG.md`，提交实现并推送 fork 同名分支。
