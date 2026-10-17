# 笔记富渲染：公式/代码/Mermaid/图片透出

## Scope

补 Task 5 剩余渲染：公式、代码高亮、Mermaid 成功与失败态、图片。不引入 KaTeX WebView、highlight.js 全量、Mermaid 运行时——移动端离线优先，首版做结构化透出 + 失败态明确 + 图片本地解析；完整渲染为后续增强，原因记入 Non-goals。

## Render Contract

- 公式：行内 `$...$` 与块级 `$$...$$` 以及 ```math/latex/tex 代码块在预览中透出为独立公式段（等宽+左右留白），未闭合分隔符原样显示并记一条 warning，不吞后文。
- 代码：围栏代码块透出语言标签（`codeLanguage` 别名归一：python3→python、c++→cpp、shellscript→bash 等，与桌面一致）；未知语言原样显示不报错；未闭合围栏按纯文本显示并记 error。
- Mermaid：```mermaid 块透出源码 + 状态行（待后续渲染 / 源码为空 / 语法疑似错误启发式）；绝不静默吞图；`MarkdownTables` 已有表格能力〗。
- 图片：`/attachments/<uuid>.<ext>` 经 `ProjectAssets.importAssets` 反向解析到 `src/assets/` 本地文件；存在显示本地图，缺失显示占位 + warning；外部 http(s) 图只显示占位 + 未验证 warning，不发起网络请求（飞行模式可用）。
- 超长页：沿用 `NotePage.preview` 8000 字符截断 + hasMore 标记，透出统计不受截断影响（统计走全文）。

## Data Contract

- 新增 `NoteRichContent`：`extract(markdown)` 返回 `formulas: List<FormulaSpan>`、`codeBlocks: List<CodeBlock>`、`diagrams: List<MermaidBlock>`、`images: List<ImageRef>`、`errors: List<String>`、`warnings: List<String>`。
- `FormulaSpan(start, end, display)`、`CodeBlock(lang, source, closed)`、`MermaidBlock(source, state)`（state: READY/EMPTY/SUSPECT）、`ImageRef(url, kind)`（kind: LOCAL/MISSING/REMOTE）。
- 校验语义对齐桌面 `validateNote`：围栏未闭合→error；公式疑似未渲染分隔符→warning；mermaid 空源→warning。

## Non-goals

- 不引入 WebView KaTeX 渲染、不打包 highlight.js、不运行 Mermaid：包体积、离线、启动耗时都不划算；源码透出已满足批注/讲解可读需求。
- 图片编辑（裁剪/粘贴上传）不在本片，只做显示解析。

## Acceptance (EARS)

- When 预览含公式/代码/mermaid/图片的页时，阅读器 shall 结构化透出四类块且失败态明确。
- When 图片缺失或为外链时，shall 显示占位 + warning，不发起网络请求。
- When 围栏未闭合时，shall 按纯文本显示并记 error。

## Verification

- JVM：四类抽取、别名归一、未闭合围栏、缺失/外链图片判定。
- `cd android && ./gradlew :feature-notes:testDebugUnitTest --offline` 通过。
