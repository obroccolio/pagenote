# Changelog / 更新日志

## Unreleased / 尚未发布

- Clarify Android OCR cache metadata and crop contracts, Agent tool validation requirements, and the limits of current device smoke evidence. / 明确安卓 OCR 缓存元数据与裁剪契约、Agent 工具校验要求及当前真机烟测证据边界。

- Preserve valid Android region OCR selections across invalid drags, use consistent crop rounding, and store OCR counts with the actual PDF render scale. / 安卓区域 OCR 无效拖动时保留有效选区、统一裁剪取整，并记录 OCR 计数与 PDF 实际渲染比例。

- Reject invalid Android agent tool arguments before project handlers can perform writes. / 安卓 Agent 工具参数无效时，在项目写入处理器执行前拒绝请求。

- Converge Android agent tools to the seven wired handlers with matching schemas and scope docs. / 安卓 Agent 工具收敛到 7 个已接线实现，声明与范围文档对齐。

- Complete Android automation gates with full builds, 16 KB checks, and relay verification; device acceptance pending. / 安卓自动化门禁收尾：全量构建、16 KB 检查与中转验证通过，真机待续。

- Mark Android pen input complete with pressure, page-scoped editing, and SAF persistence; device validation pending. / 安卓手写笔迹收尾：压感、按页编辑与 SAF 保存已落地，真机验证待续。

- Mark Android reader settings complete with persisted theme, language, split ratio, and animation. / 安卓阅读设置收尾：主题、语言、分栏比例与动画均已持久化生效。

- Run batched Android agent page tasks with serial execution and honest scope reports. / 安卓 Agent 支持批量页任务：串行执行并如实报告完成范围。

- Unify Android offline OCR auto-triggering with a shared native/bitmap rule. / 安卓离线 OCR 自动触发统一为共享的原文/位图判定规则。

- Add Android region annotations with normalized rect items, list, and delete. / 安卓新增区域批注：归一化 rect 落盘、列表与删除。

- Truncate overlong Android note and source previews with explicit length hints while keeping full-text editing. / 安卓超长笔记与源文预览截断并提示字数，编辑仍为全文。

- Mark Android project, reader, and page-preparation milestones complete with SAF entry, viewport, and cache coverage. / 安卓项目管理、阅读器与页面准备里程碑收尾：SAF 入口、视口与缓存覆盖已对齐。

- Run single-page Android agent tasks from the reader with live project tools and true completion scope. / 安卓阅读器可执行单页 Agent 任务：接真实项目工具并报告真实完成范围。

- Read Markdown-source pagenote projects on Android with paged Markwon preview aligned to note pages and PDF-only actions disabled. / 安卓支持 Markdown 源项目阅读：按页 Markwon 预览与笔记页对齐，PDF 专属入口禁用。

- Wire Android agent write tools with mandatory revision guards and updated-page reporting. / 安卓 Agent 接入写工具：强制 revision 保护并报告实际修改页。

- Wire read-only Android agent project tools for page text, reviews, and handoffs. / 安卓 Agent 接入只读项目工具：页面文本、审阅与交接。

- Add Android AI review and handoff dialogs with SAF persistence, status transitions, and stale guards. / 安卓新增 AI 审阅与交接弹窗：SAF 落盘、状态流转与过期保护。

- Add Android text annotation mode with tap-to-place, list, and delete backed by the generic SAF store. / 安卓新增文字批注模式：点选落点、列表与删除，经通用 SAF 存储落盘。

- Report missing Android project asset references on open while keeping direct SAF reads and writes. / 安卓打开项目时报告缺失资源引用，读写仍走 SAF 直写。

- Create new Android pagenote projects from a SAF folder with validated names and direct open. / 安卓新增 SAF 项目创建：名称校验、目录选择、建完直接打开。

- Preview Android rich note blocks with single-source mermaid/math quotes and local SAF asset images. / 安卓笔记预览新增富块透出：mermaid/math 源码单次引用展示，附件图加载本地 SAF 资源。

- Add a generic Android SAF annotation store covering all item types with page delete and pen-write coexistence. / 安卓新增通用 SAF 标注存储，覆盖全部批注类型与按页删除，并与手写笔迹写入共存。

- Add GFM table extraction for Android page preparation and refresh-forced region OCR cache rewrite. / 安卓页面准备新增 GFM 表格抽取与区域 OCR 缓存 refresh 强制重做。

- Add per-page Android note history with save-time snapshots, a version preview dialog, and confirmed revision-checked restore. / 安卓笔记新增按页历史：保存时自动快照、版本预览弹窗、二次确认的 revision 校验恢复。

- Add shared Android project-asset export/import helpers with missing-file skip semantics and pin stale-revision note conflicts in shared contract tests. / 安卓新增项目资源导入导出共享 helper（含缺文件跳过语义），并在共享契约测试中锁定过期 revision 笔记冲突。

- Harden Android region OCR selection with a recoverable invalid-drag message, inference-only timeout, logged snapshot fallback, and IO-bound PDF open. / 安卓区域 OCR 选区加固：无效拖动可恢复提示、超时仅限推理、快照回退打日志、PDF 打开走 IO 线程。

- Add transformed-page region OCR selection with offline recognition, project-backed cache reuse, and explicit persistence warnings; render PDFs from the exact hashed snapshot used for cache identity, with uncached SAF fallback. / 安卓阅读器新增变换页面区域 OCR 选择、离线识别、项目缓存复用和持久化告警；PDF 从用于缓存身份校验的精确哈希快照渲染，并在快照不可用时回退为不缓存的 SAF 阅读。

- Treat failed SAF child queries as cache-write failures and retain both caller and OCR warnings in Android prepared manifests. / 将 SAF 子项查询失败视为缓存写入失败，并在安卓预处理清单中同时保留调用方与 OCR 警告。

- Cache successful and partial Android region OCR results in SAF projects with source/region/engine/version identity checks, validated images and completion manifests written last; retain OCR results when project persistence fails. / 安卓区域 OCR 的成功及部分结果可缓存到 SAF 项目，并校验原文、区域、引擎和版本身份、验证图片且最后写入完成清单；项目持久化失败时仍保留 OCR 结果。

- Harden Android page-tutor saves against stale previews and partial SAF writes; fail closed when recovery-copy capability discovery or an advertised create/write operation fails, report retained-copy names when cleanup fails, report only warnings for context actually sent, and cover retry/warning states. / 加固安卓逐页讲解保存：防止旧指令预览被追加；恢复副本能力查询异常或 SAF 明确支持创建但实际创建/写入失败时停止覆盖，并在清理失败时报告残留副本名称；只提示实际发送上下文的截断，并覆盖错误重试和警告状态。

- Add Android page tutoring with bounded PDF/OCR context, a Markdown preview, and explicit revision-checked append to the current page note. / 安卓新增逐页 AI 讲解，基于有界 PDF/OCR 上下文生成 Markdown 预览，并经用户确认和 revision 校验后追加到当前页笔记。

- Add Android OpenAI-compatible model settings with Android Keystore AES-GCM key storage, localized controls, and debug-only HTTP for local relay development. / 安卓新增 OpenAI 兼容模型设置，使用 Android Keystore AES-GCM 保存 Key，提供本地化控件，并仅在调试版开放本地中转所需 HTTP。

- Specify Android OpenAI-compatible model settings, Android Keystore-backed key storage, and acceptance tests under `android/docs/spec/`. / 在 `android/docs/spec/` 中规定安卓 OpenAI 兼容模型设置、Android Keystore Key 存储与验收测试。

- Make Android reader startup instrumentation locale-independent with a stable project-picker test tag. / 为安卓阅读器启动仪器测试增加稳定的项目选择器标签，避免受界面语言影响。

- Correct the Android manifest API implementation commit reference in the review ledger. / 修正安卓 manifest API 审查记录中的实现提交引用。

- Add a reusable Android APK checker for 16 KB ELF and ZIP alignment. / 新增可复用的安卓 APK 16 KB ELF 与 ZIP 对齐检查脚本。

- Replace the legacy OpenCV AAR with official OpenCV 4.14.0, whose 64-bit OpenCV and C++ runtime libraries are 16 KB ELF-aligned. / 将旧版 OpenCV AAR 替换为官方 4.14.0，其 64 位 OpenCV 与 C++ 运行库均采用 16 KB ELF 对齐。

- Specify a repeatable Android APK check for 16 KB ELF and ZIP alignment. / 规定安卓 APK 的 16 KB ELF 与 ZIP 对齐可重复检查流程。

- Preserve the Android activity-result owner through localized reader contexts so the SAF launcher cannot crash startup. / 在安卓本地化阅读上下文中保留 Activity Result owner，避免 SAF 启动器导致应用闪退。

- Add Android instrumentation coverage for launching the localized reader after settings load. / 增加安卓本地化阅读器在设置加载后的启动仪器测试。

- Specify Android startup-owner and 16 KB native-library compatibility acceptance under `android/docs/spec/`. / 在 `android/docs/spec/` 中规定安卓启动 owner 与 16 KB 原生库兼容性验收。

- Expose the prepared manifest JSON types transitively to Android app consumers. / 通过 Android `core-reader` 向应用消费者传递公开的预处理清单 JSON 类型。

- Specify an Android app-module consumer test for the prepared manifest JSON API boundary. / 补充安卓 app 模块对预处理清单 JSON 公开 API 边界的消费测试要求。

- Reject Windows drive-qualified paths in Android prepared-manifest file entries. / 拒绝安卓预处理清单文件项中的 Windows 驱动器限定路径。

- Reject Android OCR selections whose normalized integer bounds have zero width or height. / 拒绝归一化整数边界中宽度或高度为零的安卓 OCR 选区。

- Add explicit version-2 prepared-manifest and OCR-result JSON codecs with validation and exact cache identity matching. / 新增带校验的 v2 预处理清单与 OCR 结果 JSON 编解码，并按完整缓存身份精确匹配。

- Map Android reader selections through the centered page transform into clipped, normalized OCR coordinates. / 将安卓阅读器选区按页面居中变换映射为裁剪后的归一化 OCR 坐标。

- Specify the Android reader region-OCR selection flow, transformed-page coordinate contract, and project-backed cache under `android/docs/spec/`. / 在 `android/docs/spec/` 中规定安卓阅读器区域 OCR 选区、变换坐标契约及项目内缓存流程。

- Harden Android region OCR bitmap cleanup on inference failure and cancellation, while preserving caller-owned page images. / 加固安卓选区 OCR 在推理失败或取消时的位图清理，并保留调用方所有的页面原图。

- Add Android reader settings for theme, language, pane ratio, and note transitions, with runtime application and localized controls. / 安卓阅读器新增主题、语言、分栏比例和笔记过渡动画设置，并支持运行时生效与中英文界面。

- Keep the Android reader recoverable when saved settings cannot be read, and adapt note controls to narrow split panes. / 安卓阅读设置读取失败时显示可重试状态，并让笔记栏控件适配窄分栏。

- Recognize selected page regions with the bundled offline OCR engine and map detections back to full-page coordinates while preserving caller-owned images. / 新增端侧离线 OCR 选区识别，将检测框映射回整页坐标并保留调用方原图所有权。

- Define Android-only SPECs for reader settings and OCR region recognition under `android/docs/spec/`. / 在 `android/docs/spec/` 中定义安卓阅读器设置与选区 OCR 的独立 SPEC。

- Add API 35 native PDF text extraction with an explicit OCR fallback on Android 29–34, plus two-finger zoom/pan, fit-width, and reset controls in the Android reader. / 安卓阅读器新增 API 35 原生 PDF 文本抽取（Android 29–34 明确回退离线 OCR），并支持双指缩放/平移、适宽和重置视图。

- Add Android port skeleton under `android/` (SPEC, Gradle modules, project/reader/notes/annotations/pen/OCR/agent) with unit tests; desktop builds ignore `android/`. / 新增安卓移植骨架（SPEC、Gradle 模块、项目/阅读/笔记/标注/笔迹/OCR/Agent）并附单测；桌面构建忽略 `android/`。

- Match Android annotation validation to the desktop contract for shape points while preserving highlight rectangles. / 安卓标注校验对齐桌面契约，形状标注必须包含点坐标，同时保留荧光标注矩形格式。

- Persist Android reader theme, language, split ratio, and note-animation preferences with validated DataStore settings. / 使用带校验的 DataStore 持久化安卓阅读器主题、语言、分栏比例和笔记动画设置。

- Open Android pagenote project folders through SAF, validate desktop manifests and source paths, and render bounded PDF pages with native `PdfRenderer`. / 安卓端通过 SAF 打开 pagenote 项目文件夹，校验桌面清单与原文路径，并使用系统 `PdfRenderer` 渲染受像素上限保护的 PDF 页面。

- Report only actual Android Agent page results, continue after ordinary page failures, and stop cleanly on rate limits. / 安卓 Agent 仅报告真实完成的目标页，普通页失败后继续处理，并在遇到限流时停止且明确未完成范围。

- Add page-matched Android Markdown editing and preview with Markwon, SAF note writes, per-page revision conflict checks, and adaptive stacked/side-by-side reading layout. / 安卓端新增按页 Markdown 编辑与预览，使用 Markwon 和 SAF 保存笔记，按页 revision 检查冲突，并适配窄屏上下布局与宽屏并排阅读。

- Bundle PP-OCRv6 tiny for offline Chinese/English recognition and add a current-page reader action with confidence, normalized boxes, timeout, and review warnings. / 内置 PP-OCRv6 tiny 中英文离线识别，并在阅读器增加当前页识别入口，展示置信度、归一化坐标、超时和原图复核警告。

- Build Android debug APKs per ABI so devices do not download native OCR libraries for unrelated architectures. / 安卓 debug APK 改为按 ABI 分包，避免设备下载其他架构的 OCR 原生库。

- Add native Android pen/highlighter/eraser input with pressure-aware strokes, page-scoped undo/redo, SAF persistence, and preservation of unrelated annotation data. / 新增安卓原生手写笔、荧光笔与橡皮，支持压感、按页撤销/重做及 SAF 保存，并保留无关批注数据。
- Look up draft releases through the authenticated release list: GitHub's tag endpoint can return 404 until publication. / 修正草稿发布查询：公开标签接口可能返回 404，改为在授权可见的发布列表中匹配。

- Allow a versioned beta upload request on the beta branch, without depending on a workflow being present on main; the upload still requires an exact successful validation/tag match. / Beta 分支可通过记录构建编号和标签触发直传，无需把测试流程混入 main。

- Add a manual cloud-to-draft Mac upload workflow that verifies the successful CI commit, exact beta tag, draft status and package checksums. It never publishes a release or replaces published files. / 增加云端直传草稿发布流程，核对 CI 提交、Beta 标签和校验值，不自动公开发布、不替换已发布文件。

## 1.1.0-beta.4 — 2026-10-17

- Keep the Windows executable-name override off macOS: electron-builder uses it for the entire app bundle name. Mac beta now builds as pagenote Beta.app with a matching binary path, alongside stable pagenote.app. / Windows 的可执行文件名覆盖不再影响 Mac；Mac 测试版正确生成独立的 pagenote Beta.app，避免与正式应用重名。

- Dismiss the pen popover before test pan/ink drag actions: on compact displays it can cover the source pane and legitimately receive those events. Preserve the actual native-pan and ink-position assertions. / 小屏幕交互测试先收起覆盖文档的画笔面板，再验证平移和手写拖动；保留实际效果断言。

- Adapt the reading zoom test to compact displays, where the optional Fit label is intentionally hidden; keep all focal-point and page-transition assertions. / 阅读缩放测试兼容小屏幕隐藏“适合窗口”文字按钮的布局，保留全部缩放与翻页断言。

- Record the failed Mac Paper/ink drag assertion as a non-blocking beta diagnostic instead of claiming a pass; retain the failing assertions and screenshots. Keep web prompt-control tests on the web fallback while separately testing native settings. / 如实记录 Mac 纸张主题手写拖动断言异常，保留测试；区分网页控件与原生设置验证。

- Explicitly install the Electron runtime before native/UI tests on clean CI, where npm lifecycle scripts are disabled by repository policy. / 干净 CI 按仓库策略禁用 npm 生命周期脚本，测试前显式准备 Electron 运行库。

- Initialize the temporary test directory on clean macOS CI checkouts; the Swift helper and application compile successfully there. / 修复干净 CI 检出时测试临时目录未创建的问题；已确认 Swift 原生组件与应用可编译。

- Integrate obroccolio PR #6 into beta: native Mac settings, system/light/dark themes, Paper reading layout and first-run welcome. Windows retains the web settings window. / 合入 Mac 主题与原生设置，Windows 使用网页设置窗口。
- Bring native prompt editing up to the current routing architecture and forward expected revisions to prevent overwriting concurrent AI preference updates. / 补齐入口、生成策略、用户画像模块及提示词保存版本保护。
- Keep Mermaid measurement outside the window grid and clean it after success/failure; cover cold rendering, repeated navigation and invalid diagrams. / 修复 Mermaid 测量节点顶起底栏的问题并验证翻页与异常图表。
- Publish matching Windows x64 and Mac Apple Silicon arm64 beta packages; isolate beta identity/data from stable. Intel Mac builds are not included because the native helper is arm64. / 分发同版本 Windows 与 Apple Silicon 测试包，正式版不变；不提供夹带 arm64 组件的 Intel 包。
- Add a dedicated macOS CI build/native/UI/reading/package validation workflow; retain the existing exhaustive workflow and documented MCP limitations. / 增加针对 Mac 改动的分级构建验证，保留既有完整测试与已知限制。

- Redraw the bilingual beta README workflow using aligned generation/revision columns, shared guidance and a learner-feedback loop, following the founder's reference diagram. Documentation only; release binaries unchanged. / 按参考图重排中英文路由图，保留生成、修改、材料准备与用户画像回路；仅更新文档，不改安装包。

- Clarify beta project opening: use File → Open project; remove inherited stable association/registration instructions. Documentation only; beta.3 binaries unchanged. / 修正 beta 项目打开说明，不引导注册正式版文件关联；安装包不变。

## 1.1.0-beta.3 — 2026-10-17

- Separate beta READMEs/downloads from main: publish Windows beta installer, ZIP, source and checksums as a prerelease; keep stable v1.0 untouched. / beta 文档仅提供对应测试包，正式版保持独立；Mac 当前测试包未提供。
- Install as pagenote Beta with a distinct app ID, shortcuts and local data directory; do not migrate stable/legacy data or claim project associations. Explicitly opened project files remain shared. / 分离安装和本地数据，不接管正式版关联；主动打开的项目文件仍需自行复制隔离。
- Include the beta.1 layered workflows and beta.2 routing diagrams/Windows gestures in this beta distribution. / 本次测试包包含此前分层提示词、路由图及触控板改进。

### 1.1.0-beta.2（仅 beta，尚未公开发布）

- Add bilingual SVG prompt/data-routing diagrams to beta READMEs. / 为 beta 中英文 README 增加分层路由图。
- Adapt the gesture engine from obroccolio PR #6 (77d30dd) for Windows: proportional pinch, native two-axis pan, anchored pane zoom, retained PDF/text layers, refreshed bitmap detail and zoom-aware Tag positions. / 沿用贡献者手势逻辑适配 Windows，支持连续捏合、双向平移、锚点缩放与批注定位。
- Observe outer viewport size and reserve scrollbar gutters so Windows scrollbar appearance does not rebuild pages during pinch. / 修正 Windows 非覆盖滚动条引起的尺寸变化与文字层重建。


### 1.1.0-beta.1（仅 beta 分支，尚未公开发布安装包）

- Synchronize the English MCP tool count with the beta catalog (24 tools). / 同步英文 MCP 文档的测试版工具数量为 24。

- Support direct revision feedback receipts as profile evidence after content changes are saved; retain the unresolved full-MCP navigation timeout in KNOWN_ISSUES.md. / 支持直接修改意见的偏好来源凭据；如实保留完整 MCP 回归翻页超时的未通过记录。

- Soft current-page edge highlight; layered, editable entry/generation/revision/teaching/profile prompts with host-Agent scheduling autonomy. / 当前页柔和边缘高亮，分层可编辑提示词，尊重原厂 Agent 的会话与任务调度。
- Add prepare_task for local cached source/OCR/visual data, neighbor pages, note revisions and selected feedback; include continuations and live tool routing. / 新增按场景材料准备与工具目录，减少重复预处理。
- Persist evidence-backed candidate preferences from applied Tags; support editable/resettable profile and revision-conflict protection. / 从已完成批注提炼带来源的候选偏好，可编辑、清空或恢复默认，并发修改有版本保护。
- Add end-to-end workflow, cache, continuation, preference evidence and live-entry regression coverage. / 增加路由、缓存、续读、偏好与入口实时更新验证。

- Establish `beta` for new features and routine fixes; reserve `main` for founder-approved stable promotion after real-use testing. / 建立 beta 测试分支，新功能与常规修复先测试，实测并经发起人批准后再通过 PR 晋级 main 正式分支。

- Surface Mac Office bridge limits, PDF screenshot permissions and recoverable working copies in both READMEs; distinguish Windows file-association scripts from Finder usage. / 将 Mac 配套文档中的 Office 限制、截图权限与恢复副本说明提到主 README，区分 Windows 注册脚本与 Mac 项目打开方式。

- Expand both READMEs with verified contributor-hosted Mac DMG/ZIP links for Apple Silicon and Intel, installation/Office permissions, platform status and contributor credits. Documentation only; published binaries remain unchanged. / 完善中英文 README，加入贡献者提供的 Mac 双架构安装包、安装与 Office 权限说明及贡献致谢，明确其测试版和独立构建身份；不替换已发布程序。

## 1.0.0 — 2026-10-17

- Attribute acceptance to LTH0001's Windows tests (PR #4) plus the founder's supplementary local tests, with final release approval by the founder. / 发布依据为 LTH0001 测试加发起人本机补测，由发起人综合结果放行。

- Freeze Windows v1.0 under the founder-approved early-stage release baseline; macOS remains experimental (1.0.0-beta.1 builds). / 依据发起人确认的早期分级质量门槛冻结 Windows v1.0 正式版，Mac 继续作为测试版。
- Integrate PR #3 and #4, preserve both test suites, and retain PR #2 regression coverage without replacing the equivalent existing formula fix. / 合入 Mac 适配与候选版验收，保留双方测试和公式修复回归。
- Record non-blocking defects and environment gaps in KNOWN_ISSUES.md; include DMG checksums. / 非阻断问题及未验证范围统一归档，补齐 DMG 校验值。

### Candidate preparation history / 候选版准备记录

- Freeze the beta.5 feature scope on `release/2.0`; this candidate is for acceptance testing and is not a stable release. / 在 `release/2.0` 冻结 beta.5 功能范围，本候选版只用于验收，不代表正式放行。
- Restore the existing version-history dialog through the native File menu after the old toolbar was hidden. / 修复旧工具栏隐藏后版本历史无可见入口的问题，可从原生“文件”菜单打开并恢复历史笔记。
- Fix the English-Windows prompt-settings test timeout by checking save responses instead of Chinese status text; wait for asynchronous Tag cards and update continuous-reading tests to the current editor. / 修复英文 Windows 下提示词设置测试误等中文文案的超时，等待异步 Tag 卡片更新，并使连续阅读用例适配当前编辑方式。
- Add an independent-terminal acceptance record and retain failed-run evidence separately from candidate retests. / 补充独立终端验收记录，保留原始失败与候选版复测证据。
- Make the continuous-reading test use a desktop wide enough for its footer controls, and keep the soak test in note-edit mode between saved edits. These are test-harness corrections only. / 连续阅读测试固定足够宽的桌面，长时间测试避免重复切换笔记编辑状态；本次仅修正测试脚本。
- Wait for the Electron window before resizing it in the continuous-reading harness. / 连续阅读测试等待 Electron 窗口创建后再调整大小，消除冷启动竞态。
- Type soak-test edits into a normal paragraph so revisiting a page retains its expected heading. / 长时间测试在普通正文段落输入，避免测试文字改变用于定位的标题。
- Explicitly reopen the test document before checking all 60 saved pages after restart; add a short packaged smoke run for this harness, separate from one-hour stability acceptance. / 长测重启后显式打开测试文档并核对 60 页数量；CI 增加打包版脚本短跑，明确不替代一小时稳定性验收。

- Adapt the beta.5 release tooling for macOS: route desktop packaging through a cache-aware wrapper, add version-consistency checks to both release jobs, and keep Mac menus/packaging coverage. / 适配 beta.5 发布流程：打包走缓存脚本、两条发布流水线接入版本检查，保留 Mac 菜单与打包覆盖。

## 2.0.0-beta.5 — 2026-10-16

- Use a project-local packaging cache by default to avoid cross-device rename failures in redirected Windows profiles. / 打包默认使用项目内缓存，避免 Windows 用户目录被重定向时发生跨盘重命名失败。

- Publish beta.5 installer, complete Windows ZIP, source archive and checksums together; align download links and enforce package/lock/tag/changelog consistency. / 统一发布 beta.5 安装包、完整 Windows ZIP、源码和校验值，同步下载入口，并增加版本、锁文件、标签与更新日志一致性检查。

- Update the native-menu test to match the current page-tutor wording after the upstream prompt rewrite. / 菜单测试已跟随上游逐页精讲新文案更新。

- Restore native macOS Finder/Dock/window/menu behavior, including save-before-hide, explicit quit, Command editing shortcuts and Backspace annotation deletion. / 恢复 macOS Finder、Dock、窗口与原生菜单操作，包括关闭前保存、退出、Command 快捷键和 Backspace 删除批注。
- Connect macOS Word/PowerPoint conversion, project refresh and revision-checked Word edits; render Word screenshots from temporary PDFs without replacing committed previews. / 接入 macOS Word/PPT 转换、项目刷新和版本校验的 Word 编辑；Word 截图使用临时 PDF，不覆盖已保存预览。
- Build universal MCP Node runtimes for Apple Silicon and Intel, and retain OCR native dependencies and licenses in both Mac and Windows packaging. / 为 Apple Silicon 与 Intel 构建通用 MCP 运行时，Mac 与 Windows 软件包均保留 OCR 原生依赖和许可。
- Add a versioned prompt index with task routes for initial teaching, AI Tag edits and ordinary editing; customize individual modules in AI settings. Recommend resource-capped parallel page requests using adjacent source pages, without mandatory handoff waits. Add local Markdown/math/Mermaid/image preflight before saving. / 新增统一提示词索引及初始讲解、AI Tag 修改等任务路由；AI 设置可分别编辑模块，按版本增量读取。逐页采用受资源限制的并发建议与相邻源页上下文，不再等待上一页交接；新增保存前本地格式和附件预检。

- Render multiline display formulas next to prose even when their delimiter lines have no surrounding blank paragraphs. / 修复独立公式虽已分行、但与正文之间缺少空行时仍显示为反斜杠文本的问题。

- Preserve consecutive empty note pages when opening partially generated projects, and reuse the source fingerprint after project cache refresh. / 修复讲解逐页生成时连续空白笔记页被误解析成分页符的问题；修复项目源文件缓存刷新后重复重开的性能问题。

- Bundle offline Chinese/English PP-OCRv4 on CPU; prepare per-page text, coordinates/confidence, page/region PNGs and native Markdown tables/Mermaid assets for AI. Cache prepared data in portable projects; preserve source channels and report uncertain/failed OCR instead of claiming lossless extraction. / 内置离线中英文 OCR，按页准备文字、坐标和置信度、页面与区域图片、Markdown 表格及 Mermaid 图像；结果随项目缓存，保留原文与原图，明确识别局限和失败状态。
- Update image dependencies and bundle PaddleOCR model licensing notices. / 更新图像依赖，随包提供 OCR 模型许可说明。

- Add selective page reads, compact MCP state/results, revision-based cache reuse and portable per-page knowledge handoffs. / 支持原文、笔记及局部段落按需读取，精简 MCP 状态与返回，复用未变化内容，并保存可迁移的页间知识交接。
- Record the project rule that every push must include a matching changelog entry. / 将每次推送必须同步更新日志写入项目协作规则。

- Publish AI notes after each saved page without navigating or resetting the reader's scroll position. / AI 讲解逐页保存后静默更新，保留用户阅读页码和滚动位置。

- Slim the tool rail from 72 to 60 CSS pixels and fit its buttons and popovers. / 左侧工具栏从 72 收窄到 60 像素，同步调整按钮和弹出菜单。

- Align the side tool rail with the workspace below the native menu, removing the duplicated top spacer. / 移除左侧工具栏多余的顶部偏移，使其与原生菜单下的工作区对齐。

- Dismiss selection-tool options on outside clicks or Escape while keeping text selection as the default. / 点击文档、笔记或按 Escape 收起选择工具菜单，默认使用文字选择。

- Handle adjacent multiline dollar display-math delimiters (issue #1). / 修复跨行公式分隔符紧贴正文时显示 LaTeX 源码的问题。
- Add editable local default AI instructions and route the tutor skill only to relevant tasks, across subjects and document types. / 新增可保存、恢复的默认 AI 系统提示词；精讲技能按任务启用，适配不同学科和材料。

- Bundle a page tutor SKILL.md, expose it through MCP resource/prompt discovery and add a copy-prompt menu action. / 内置逐页精讲技能，通过 MCP 资源与提示模板提供，并支持从菜单复制提示词。

- Recognize copied double-escaped TeX delimiters and consistently escaped commands without changing code blocks or normal matrix row breaks. / 兼容复制后的双重转义公式，保留代码块和正常矩阵换行；修复公式显示反斜杠源码的问题。

- Bundle common code-language grammars, normalize aliases/case and preserve plain code when highlighting fails. / 本地打包常用代码语言，统一大小写和别名，高亮失败时保留原文。
- Add a per-page model-request policy with learner context and knowledge handoffs to MCP initialization and documentation. / MCP 初始化指令和文档明确每页单独请求、充分使用上下文及页间知识交接。

- Queue project-open requests during startup; report errors instead of silently dropping them. / 启动期间保留项目打开请求，失败时显示具体错误。
- Show the existing window on file activation and prevent source-refresh races from reopening the previous project. / 文件唤起时显示已有窗口，防止源文档刷新冲突将界面切回旧项目。
- Include current-user file-association setup for ZIP distributions. / 为 ZIP 解压版提供当前用户的项目文件关联脚本。
- Rewrite the bilingual introduction around the motivation for page-by-page AI-assisted learning. / 用更自然的中英文介绍逐页 AI 辅助学习的初心。

## 2.0.0-beta.4 — 2026-10-15

- Regenerate the dependency lockfile from the official npm registry, replacing unavailable tarball versions exposed by the first clean GitHub build. / 按 npm 官方源重新生成依赖锁，修正首次 GitHub 干净构建发现的不可下载版本。
- Retain beta.3 functionality and Windows file-operation fixes. / 保留 beta.3 的功能与 Windows 文件操作修复。
- Normalize source line endings on Windows and prepare Electron's lazily downloaded runtime before source tests. / 固定 Windows 检出的源码换行格式，并在源码测试前准备 Electron 延迟下载的运行时。

## 2.0.0-beta.3 — 2026-10-15

First public beta / 首次公开测试版。

- Read source documents beside page-linked Markdown notes. Render LaTeX, Mermaid, code highlighting, tables and pasted images. / 双栏阅读与逐页 Markdown 笔记，支持公式、图表、代码高亮、表格和图片。
- Chinese/English interface, portable projects, annotation tools, note history, and an authenticated MCP bridge. / 中英文界面、可迁移项目、批注工具、笔记历史与带凭据校验的 MCP 接口。
- Use the system default application for Word/PDF/PPT edits. / Word、PDF、PPT 调用系统默认应用编辑。
- Fix EXDEV data migration and file replacement, encrypted cross-volume file copies, and project sync without a request body. / 修复 EXDEV 数据迁移、文件替换、加密文件跨盘复制和无请求体项目同步问题。

Unsigned Windows x64 prerelease. Signing, vendor review and clean-system acceptance remain pending; see the release-readiness report. / Windows x64 未签名测试版，签名、厂商复核与独立系统验收仍待完成，详见发布准备报告。
