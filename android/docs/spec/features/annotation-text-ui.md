# 文字批注创建/列表/删除 UI

## Scope

Task 6 第一切片：只做 text 类型批注的创建、列表、删除 UI，经 `ProjectAnnotationStore` 全量页替换落盘。图形/图片/富文本、AI review、handoff 均不在本片。

## Interaction

- 阅读器工具栏新增 `Annotate` 按钮（笔工具旁），点击进入批注模式：笔迹输入暂停，点页面即落一个文字锚点并弹文本输入框。
- 输入框确认后，经 `AnnotationTextDraft.toItem` 生成条目（type=text，当前笔色/笔宽，points 取落点单点，text 截断 10000），读最新页 items 追加后 `writePage` 全量写回。
- 当前页批注列表入口在笔记头 `History` 旁（`Notes` 按钮）：弹窗列出该页全部非笔迹条目（text  shows 文本，其它类型 shows 类型名 + 不可编辑提示），每行可删除（二次确认）。
- 删除经 `readPage` 滤掉目标 index 后 `writePage`；只有 text 类型可编辑（改文本后同样全量写回），其它类型只读展示。

## Data Contract

- 新增 `AnnotationTextDraft.toItem(x, y, color, width, text)` 纯函数：校验坐标有限、颜色 `#RRGGBB`、宽度 0.1..100、文本非空≤10000；points 为 `[[x, y]]`。
- 读写经 `ProjectAnnotationStore`；并发写串行（复用笔保存 Mutex 思路，调用方持锁）；写前 `AnnotationValidation.validateItems` 已在 store 内。
- 笔迹 freehand 条目在列表中隐藏（笔迹仍在画布上），避免两处编辑打架。

## Acceptance (EARS)

- When 在批注模式点页面并确认文本时，阅读器 shall 追加 text 条目并落盘，其它条目保留。
- When 删除某 text 条目时，shall 只移除该条目，其它页与未知字段保留。
- When 列表含非 text 类型时，shall 只读展示，不提供编辑。

## Verification

- JVM：draft 生成校验、追加/删除纯函数（经 `AnnotationSafBoundary`）。
- 仪器测试编译：批注弹窗打开、确认创建、删除。
- `cd android && ./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --offline` 通过；真机落点精度待 task 12。
