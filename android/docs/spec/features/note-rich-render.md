# 笔记富渲染：公式/代码/Mermaid/图片

## Scope

补齐 Android 阅读器的富内容渲染。普通 Markdown、公式和代码使用 Markwon 原生栈；项目附件图片只从 SAF 项目资源读取，内联 `data:` 图片不访问文件或网络。Mermaid 为复用桌面端完整语法，可在单个图表预览区域使用受限、仅加载 APK 内资源的 Android WebView；PDF、笔迹、编辑器、Agent 和其余界面仍为原生 Kotlin。任何富内容失败均保留可读源码并显示状态。页面准备管线不要求额外生成 Mermaid PNG。

## Render Contract

- 公式：行内 `$...$` 与块级 `$$...$$` 以及 ```math/latex/tex 代码块在预览中由原生 LaTeX renderer 实际绘制；编辑内容保留原 Markdown。未闭合/无效公式须保留原文并给出可见失败提示，不吞后文。
- 代码：围栏代码块按本地 grammar 真实高亮。`codeLanguage` 别名归一（python3→python、c++→cpp、shellscript→bash 等，与桌面一致）；未知语言与未闭合围栏保留纯文本，不丢源码。
- Mermaid：Markdown 围栏必须离线绘制成图；空源、语法错误和 renderer 失败时保留源码并显示明确状态。仅使用 APK 内固定版本的 renderer 与 `WebViewAssetLoader` 本地资源；关闭文件/内容访问、拦截所有非应用资源请求、不暴露 JavaScript 原生桥接、不允许外部导航。每个原生 Markdown 预览宿主最多复用一个隐藏 WebView，串行生成 SVG，再交给 Markwon 图片 span 绘制；其余 Markdown、PDF 和笔记仍由原生 UI 绘制。Mermaid 原文经 URL-safe Base64 私有图片 URI 保存，调用 JS 时作为 JSON 字符串数据传递；SVG 由 DOMPurify 过滤，移除 href 引用，且返回体不超过 256 KiB。100,000 字符以上的围栏继续显示原始代码；语法错误提示最多显示 180 字符源码片段，原笔记编辑内容不改动。以仓库锁定的 Mermaid 12.2.0 browser bundle 与 DOMPurify 3.4.17 为准，不在运行时联网加载依赖。
- 图片：`/attachments/<uuid>.<ext>` 经 `ProjectAssets.importAssets` 反向解析到 `src/assets/` 本地文件；存在时从 SAF 项目目录解码显示，缺失、不可读或解码失败时显示可见占位 + warning。SAF 查询、打开流返回 null/抛错，以及已打开流在读取中抛出 I/O 异常，都必须收敛为可见占位，不得留下空白图片或把异常逸出异步渲染。外部 http(s) 图只显示带来源信息的阻断占位 + warning，严禁发起网络请求；Markdown 图片离线策略独立于 Agent 的网络权限。Markwon 默认网络 scheme handler 必须移除或由本地阻断 handler 覆盖。
- 图片 scheme 白名单：仅允许本地 `pagenote-asset:` 与内联 `data:`；`http:`、`https:` 显示阻断占位，其他未识别 scheme 不得访问文件或网络。错误占位只显示安全来源摘要，不回显查询参数、片段、data URI 负载或本地绝对路径。
- 超长页：沿用 `NotePage.preview` 8000 字符截断 + hasMore 标记，透出统计不受截断影响（统计走全文）。

## Data Contract

- 新增 `NoteRichContent`：`extract(markdown)` 返回 `formulas: List<FormulaSpan>`、`codeBlocks: List<CodeBlock>`、`diagrams: List<MermaidBlock>`、`images: List<ImageRef>`、`errors: List<String>`、`warnings: List<String>`。
- `FormulaSpan(start, end, display)`、`CodeBlock(lang, source, closed)`、`MermaidBlock(source, state)`（state: READY/EMPTY/SUSPECT）、`ImageRef(url, kind)`（kind: LOCAL/MISSING/REMOTE）。
- 校验语义对齐桌面 `validateNote`：围栏未闭合→error；公式疑似未渲染分隔符→warning；mermaid 空源→warning。

## Non-goals

- 不把应用、Markdown 编辑器或 PDF 阅读器改成 WebView 壳；WebView 只用于离线 Mermaid 图表预览，不加载网页或远端 Mermaid 服务。
- 不在页面准备管线生成 Mermaid PNG；图表仅在笔记/Markdown 预览时绘制。
- 图片编辑（裁剪/粘贴上传）不在本片，只做显示解析。

## Acceptance (EARS)

- When 预览含公式/代码块时，阅读器 shall 离线绘制公式并高亮已支持语言的代码。
- When 预览含 Mermaid 块时，阅读器 shall 离线绘制图表；渲染失败时保留源码并显示明确错误态。
- When 图片缺失、不可读或无法解码时，shall 显示可见占位 + warning，不以透明图静默吞错。
- When SAF 图片流在解码期间抛出 I/O 异常时，shall 将其转换为本地化可见 warning，不让异常逃逸图片异步加载器。
- When Markdown 包含 HTTP(S) 图片且应用拥有 Agent 网络权限时，shall 显示阻断占位 + warning，且不得连接该 URL。
- When 图片加载失败时，shall 只显示安全来源摘要，不回显 URL 查询参数、片段、data URI 负载或本地绝对路径。
- When 围栏未闭合时，shall 按纯文本显示并记 error。

## Verification

- JVM：公式预处理、别名归一、未闭合围栏、缺失/外链图片判定。
- Android instrumentation：LaTeX drawable/span、已知代码语言高亮、未知语言源码回退、缺图与损坏内联图占位、抛出 I/O 异常的本地图片 `InputStream` 占位、HTTP(S) handler 不发起请求、敏感来源脱敏，以及 Mermaid WebView 的真实 SVG 绘制、源码安全传递、错误回退、离线资源白名单和无外部网络请求。
- `cd android && ./gradlew :feature-notes:testDebugUnitTest :app:assembleDebug` 通过；四 ABI APK 继续执行 16 KB 检查。
