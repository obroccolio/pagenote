# pagenote Android 端任务拆解

> 执行顺序：先契约与骨架，再阅读与笔记，再标注与笔迹，再 OCR 与准备管线，再 Agent 与模型，最后集成与真机。
> 每完成一个任务更新复选框，保持变更可回链到 `requirements.md` 验收。

-# Implementation Plan

- [x] 1. 跨端契约与测试夹具（`ProjectAssets` 导出/导入往返与缺文件跳过、`saveNote` 过期 revision 冲突拒绝已由共享回归 pin 住，契约见 `features/shared-contract-assets-revision.md`）
  - [x] 在 `android/docs/spec/contracts/` 落 `project.schema.json`、`annotations.schema.json`、`ai-reviews.schema.json`、`prepared-manifest.schema.json` 与分页示例。
  - [x] 在 `android/shared-tests/` 覆盖分页、空页、标注和准备 manifest 的核心桌面语义。
  - [x] 补充 assets 与 `expectedRevision` 冲突的共享回归夹具和断言。
  - _Requirement: 项目与数据兼容，构建与测试_

- [x] 2. 安卓工程骨架与构建隔离
  - 新建 Gradle 工程与 `app/core-project/core-reader/feature-notes/feature-annotations/feature-pen/feature-ocr/agent/shared-tests` 模块。
  - 接本地 SDK 与 Gradle Wrapper，`assembleDebug` 按 ABI 可出可安装产物。
  - 桌面构建忽略 `android/`，安卓构建只认 `android/`；验证互不干扰。
  - _Requirement: 构建与测试，约束与非功能_

- [x] 3. 项目管理与分页笔记（SAF 打开/创建、分页读写与 revision 冲突、按页历史、assets 直读均已落地，真机待 task 12）
  - [x] 解码并校验现有项目清单，通过 SAF 打开现有 PDF 项目。
  - [x] 实现 `splitPageNotes` 同语义分页与合并、空页占位，以及整页 SHA-256 revision 和 `expectedRevision` 冲突拒绝。
  - [x] 接入按页历史快照/列表/恢复工作流（保存成功自动快照去重，历史弹窗预览与二次确认恢复，`expectedRevision` 冲突不覆盖；契约见 `features/note-history-restore.md`）。
  - [x] 接入 SAF 项目创建（名称校验、目录选择、完整结构落盘、失败清理、创建后直接打开；契约见 `features/project-create.md`）。
  - [x] 接入资源引用直读（SAF 项目即源目录：打开时扫描 `assets/` 引用并提示缺失，保存时直写引用不搬运；契约见 `features/project-assets-saf.md`）。
  - _Requirement: 项目与数据兼容，阅读与笔记_

- [x] 4. 阅读器与双栏自适应（SAF 入口、PdfRenderer 翻页、原生文字/视口/缓存、窄屏堆叠/宽屏并排、Markdown 源阅读均已落地，真机待 task 12）
  - [x] 4.1 SAF 打开项目目录、解码并校验 `project.pagenote`、使用 `PdfRenderer` 翻页（`ProjectDocumentOpener` + `PdfDocumentSession` + 上下页已接通）。
  - [x] 4.2 原生文字/文字块抽取、整页与区域 PNG、缩放平移及缓存接入（API 35 原生文本、`PdfPagePolicy` 整页渲染、`toPixelRect` + `createBitmap` 区域图、`PdfViewportTransform` 缩放平移与 `ProjectPreparedCache` 均已接通）。
  - [x] 4.3 手机上下页、平板左右对照、Markdown 源文档阅读（Markdown 源按 `PageNotes.split` 分页、页数取 max、对齐笔记页，PDF 专属入口禁用；契约见 `features/markdown-source-reading.md`）。
  - PDF 按页渲染、原生文字与文字块抽取、整页 PNG。
  - 手机竖屏上下页，平板横屏左右对照；缩放、平移、适应宽度、重置缩放。
  - Markdown 源文档阅读（已落地，JVM 单测 + 仪器编译 + 238 任务全量构建与 16 KB 检查通过，真机待 task 12）。
  - _Requirement: 阅读与笔记，布局与适配_

- [x] 5. 笔记渲染与轻量编辑（Markwon 编辑/预览、GFM 表格、富块透出、超长页截断提示均已落地；编辑器沿用 Markwon 高亮编辑，不自研，真机待 task 12）
  - [x] 富渲染透出已落地：mermaid/math 围栏预处理转引用块（源码单次出现）、附件图经 `pagenote-asset://` + Markwon image 插件加载本地 `src/assets/`、代码语言别名归一、未闭合围栏与外链图失败态明确；契约见 `features/note-rich-render.md`。
  - [x] 超长页截断呈现已落地：预览复用 `NotePage.preview` 8000 字符阈值 + 字数提示，编辑仍为全文；契约见 `features/note-overlong-preview.md`。
  - _Requirement: 阅读与笔记_

- [x] 6. 标注与 AI 标注工作台（数据模型、SAF 边界、文字/区域批注、AI review、handoff 均已落地，真机落点精度待 task 12）
  - [x] 实现 annotation、AI review、handoff 数据模型、校验和本地 store 单测。
  - [x] 通用标注 SAF 数据边界已落地（`ProjectAnnotationStore` 全类型读写/删页，`AnnotationSafBoundary` 纯函数，与笔迹写共存；契约见 `features/annotation-saf-boundary.md`）。
  - [x] 文字批注创建/列表/删除 UI 已落地（批注模式点选落点、文本输入、全量页写回、非笔迹列表与删除；契约见 `features/annotation-text-ui.md`）。
  - [x] 区域批注创建/列表/删除 UI 已落地（批注模式 rect 对角落盘、零面积拒绝、空文本允许、列表可删；契约见 `features/annotation-region-ui.md`）。
  - [x] AI review 创建/列表/状态流转 UI 已落地（本页 open 列表、新建 overall、applied/dismissed 流转、删除；契约见 `features/review-handoff-ui.md`）。
  - [x] 交接读写 UI 与 stale 处理已落地（本页交接预览、stale 警告、过期 revision 拒绝覆盖；契约见 `features/review-handoff-ui.md`）。
  - _Requirement: 标注_

- [ ] 7. 手写笔迹（部分：原生 View、压感、分页编辑和 SAF 项目保存已实现；真机验收待做）
  - [x] 原生 View 笔/荧光笔/橡皮、stylus 侧键与硬件橡皮输入、撤销、重做、按页清除。
  - [x] 笔迹按页存取并保留 annotations.json 中的其他批注、页面和未知字段。
  - [ ] 主动笔与被动笔、边缘、断触、误触抑制真机验证。
  - _Requirement: 笔迹，布局与适配_

- [x] 8. 页面准备管线（原生文字/整页渲染/区域 PNG/`MarkdownTables`/`RegionOcrProcessor.refresh` 缓存刷新均已接通，Mermaid PNG 按 Non-goals 不移植）
  - [x] 原生文字/文字块抽取、整页 `PdfRenderer` 渲染、缓存 key、manifest 与 1000x1000 区域契约。
  - [x] 接通区域 PNG 生成（复用 `toPixelRect` + `createBitmap`，floor/ceil + 裁剪）、Markdown 表格抽取（`MarkdownTables`，headers/rows/source）、项目缓存 `refresh` 重做（`RegionOcrProcessor.refresh` 跳读强写），由页面准备流程统一调用；契约见 `features/page-prepare-pipeline.md`。Mermaid PNG 渲染按 Non-goals 暂不移植。
  - _Requirement: 页面准备与 OCR_

- [x] 9. 离线 OCR（中英文整页/区域识别、自动触发规则、超时与失败标记、页面准备缓存、区域重识别均已落地，真机待 task 12）
  - 中英文整页与区域识别、坐标、置信度、警告、原图保留。
  - 触发规则（`OcrTrigger`：不可用/空/图多字少自动补 OCR，Agent 循环内不触发，page tutor 统一接入）、超时处理、失败明确标记、飞行模式可用。
  - [x] 区域重识别 API 与全页坐标映射，契约见 `features/ocr-region-recognition.md`。
  - [x] 阅读器区域选择、离线识别与 SAF 项目缓存；JVM 回归、仪器测试编译、四 ABI APK 构建及 16 KB 对齐检查通过，真机手写笔/启动验收待完成，契约见 `features/reader-ocr-selection.md`，实施拆分见 `reader-ocr-selection-plan.md`。
  - _Requirement: 页面准备与 OCR_

- [ ] 10. 端侧 Agent 与 OpenAI 兼容接入（部分：客户端、任务循环与数据契约已实现；项目工具接入及端到端闭环待续）
  - OpenAI 兼容客户端，支持 BaseURL/Key/模型名配置，默认可配本地中转与指定模型。
  - [x] Android 模型设置 UI、Keystore 加密 Key 存储与本地中转可达性说明，契约见 `features/openai-model-settings.md`，实施拆分见 `openai-model-settings-plan.md`；debug/release 网络策略和本地构建验证通过，Keystore/Compose 仪器测试待设备执行。
  - [x] 单页讲解预览和显式追加，按页 OCR 回退、相邻页上下文、20,000 字符截断、旧提示词预览禁用及 SAF 写入恢复/revision 冲突保护；SAF 父目录/能力查询异常或备份准备失败时 fail closed，明确不支持备份才使用回滚兜底，清理失败报告残留名称；契约见 `features/page-tutor-preview-and-append.md`，单测、APK 构建及 16 KB 检查通过，仪器测试仅编译未在设备执行。
  - [x] 只读项目工具已接入（read_page/read_review/read_handoff：笔记+原生文字+revision/记录摘要/stale，异常转 ok=false 不中断循环；契约见 `features/agent-project-tools.md`）。
  - [x] 写工具已接入（write_note/patch_note/write_review_status/write_handoff：revision 必填，冲突转 ok=false 不覆盖并报告 updatedPage；契约见 `features/agent-write-tools.md`）。
  - [x] 单页任务 UI 闭环已接入（工具栏 Agent 任务入口、当前页上下文组装、工具循环、Done/Failed 范围报告、缺配置开模型设置、当前页被改刷新；契约见 `features/agent-task-run.md`）。
  - 任务循环、本地工具收敛集、多步上限、revision 冲突处理、实际完成范围报告。
  - 提示词索引与按 revision 分批读，逐页独立请求加相邻源页。
  - 单页串行汇总只报告真实完成/修改的请求页；限流停止，普通失败继续。
  - 密钥不落项目目录，未配置明确提示。
  - _Requirement: Agent 与模型_

- [ ] 11. 设置、语言、主题（部分：DataStore 与设置面板、运行时主题/语言/比例/动画已实现；真机验收待续）
  - 主题、语言、双栏比例、动画开关持久化。
  - 语言键语义复用桌面 `locales/catalog.json`。
  - [x] 原生设置面板与运行时生效，契约见 `features/reader-settings-ui.md`。
  - _Requirement: 布局与适配_

- [ ] 12. 自动化与真机验收
  - 契约回归、单元、集成、模型真接口（本地中转）测试。
  - 手机与平板走通阅读、笔记、标注、Agent、笔迹闭环。
  - 飞行模式 OCR、超大页、限流、冲突、缺模型配置异常用例。
  - [ ] 安卓启动与 16 KB 页面兼容性修复，契约见 `features/startup-and-16kb-compatibility.md`，实施拆分见 `startup-and-16kb-compatibility-plan.md`。
    - [x] 保留本地化 Compose 下的 Activity Result owner；应用单测、instrumentation 编译与 APK 编译通过，设备启动复测待完成。
    - [x] 切换到已核验的官方 OpenCV 4.14.0 AAR，并通过全部 ABI APK 的 ELF/ZIP 16 KB 检查。
    - [ ] 连接设备拒绝 APK 与测试 runner 安装（`INSTALL_FAILED_USER_RESTRICTED`）；重新安装、启动和 SAF 选择器仍待真机验收。
  - _Requirement: 构建与测试，全部 EARS_

- [ ] 13. 分支、变更日志、PR 准备
  - `codex/android-port` 基于 `beta`，PR 目标 `beta`。
  - 每次推送附 `CHANGELOG.md` 未发布区条目。
  - 安卓产物独立，不混入桌面 `release/`。
  - _Requirement: 构建与测试_
