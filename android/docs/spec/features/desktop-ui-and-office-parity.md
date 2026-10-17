# Android 桌面功能区与 Office 编辑对齐

> 状态：桌面菜单分组和自适应侧栏方向已由发起人确认；导航实施契约见 `adaptive-menu-navigation.md`。本文记录完整桌面功能/Office 终态，Office 引擎实现仍需单独完成可行性验证，不视为已批准或已实现。

## 1. 目标与边界

Android 端须让熟悉 pagenote 桌面版的用户识别相同的菜单分组、命令语义、颜色和阅读/笔记工作区；交互改为 Compose 原生、触控与手写笔优先。目标是信息架构和功能一致，不是把桌面 42px 工具条缩小贴到手机上。

桌面当前接受 PDF、Word、PowerPoint 和 Markdown 文件。Android 终态须在本机读取、预览并编辑 `.doc`、`.docx`、`.ppt`、`.pptx`，不能把 Office 文档排除在首版范围之外，也不能把“安装另一个 Office app”作为完成条件。Android 不提供桌面 MCP server；端内 Agent 承接相同学习工作流和提示词/数据契约。

不把电子表格、账号体系、云协作或远程文档转换服务纳入此 SPEC；它们不是当前桌面导入格式/本机流程的等价移植目标。

## 2. 响应式导航

使用 Compose Material 3 Window Size Class 决定布局，不只根据设备品牌或方向判断：

- Compact（手机）：应用栏放文档标题、当前页和菜单按钮；菜单以模态抽屉打开，按桌面类别呈现命令。抽屉关闭后回到原文/笔记，不重建项目状态。
- Expanded（平板/大窗口）：显示常驻但可折叠的侧栏。展开态显示分组标题和命令；收起态显示图标导航，图标有 TalkBack 标签和 tooltip，点击后展示对应命令面板。
- Medium：在可用宽度允许双栏时用可折叠侧栏；空间不足时使用抽屉。根据窗口宽度和两栏最小可读宽度决定，不硬编码“平板”设备名单。
- 阅读主体：宽窗口中原文在左、逐页笔记在右并可调分隔；紧凑窗口提供原文/笔记切换或纵向堆叠。翻页继续保持同页同步，侧栏不得覆盖/重置 PDF 缩放、滚动或笔迹草稿。
- 进入全屏 Office 编辑时仍保留 pagenote 返回/保存流程；可将编辑 surface 扩展至全屏，但退出后回到原项目、页码和双栏状态。

### 桌面分组映射

| 桌面分组 | Android 命令面板 |
| --- | --- |
| 文件 | 打开/新建项目、最近文档、附件、导入/导出笔记、历史版本 |
| AI 批注 | 创建/查看批注、整体或选区需求、调用端内 Agent、处理结果 |
| 编辑 | 撤销、重做、剪切、复制、全选 |
| 段落 | 字号、标题级别、无序/有序/任务列表、引用 |
| 格式 | 粗体、斜体、删除线、下划线 |
| 插入 | 代码、公式、表格、链接、图片、Markdown 格式粘贴 |
| 阅读 | 页码、上一页/下一页、缩放、适合窗口、重置视口 |
| 手写批注 | 选择、笔、荧光笔、橡皮、线/箭头/形状/文字、颜色/宽度、撤销/重做、复制/粘贴/图片/删除 |
| Word 编辑 | 打开当前 Office 文档编辑、选中文字转 AI 批注、保存并返回阅读 |
| 设置 | 外观、Markdown 主题、语言、笔记分页动画、双栏比例、模型设置 |
| 使用说明 | 当前版本帮助、快捷键、隐私/许可证与诊断入口 |

菜单保留桌面分组标签及组内命令顺序。不可用命令应显示禁用态和原因；不得因“移动端简化”而隐藏桌面已有功能。高频翻页、缩放、笔迹工具可在内容区保留快捷入口，但仍需能从同名分组找到。

## 3. 视觉基线

以 `src/desktop.css` 和 `src/theme.css` 为单一视觉参照：

| Token | Light | Dark |
| --- | --- | --- |
| Accent | `#185abd` | `#8bb8ff` |
| Page | `#eef1f5` | `#17191d` |
| Surface | `#ffffff` | `#22252b` |
| Raised surface | `#f5f8fd` | `#2a2e36` |
| Text | `#253247` | `#e7eaf0` |
| Muted text | 桌面 `#536276` 系 | 桌面 `#aab4c3` 系 |

Android 使用原生 sans-serif 字体 fallback；Segoe UI/Microsoft YaHei 只作桌面字形参考，不打包私有字体。保持桌面蓝色焦点/选中语义、轻分隔线、紧凑信息密度和克制圆角；触控目标不低于 Android 无障碍建议尺寸。深浅主题、focus、disabled、pressed、错误和保存状态都须有独立可读对比度。文档像素不随 UI 主题反色。

## 4. Office 文档集成

### 4.1 用户可见行为

- 支持桌面已有的 `.doc`、`.docx`、`.ppt`、`.pptx` 输入；保持源文件格式，源文件仍是项目权威数据。
- 阅读模式以 PDF 页/幻灯片预览表现，并将页数与右侧逐页笔记对齐。转换只在本机完成，不上传文档。
- 编辑在 pagenote 进程/Activity 内完成，不启动外部 Office 应用。返回时可保存、放弃或取消；保存成功必须重新生成预览。
- 有原生文字选区的引擎能力时，允许“选中文字 → AI 批注”；否则仅对真实支持的文字提供选区入口，图像页/幻灯片仍可区域框选和 OCR，不能创建伪造文字锚点。
- 若格式、字体或复杂版式存在引擎限制，编辑前显示明确兼容提示；保存不得悄悄降为 PDF/图片或覆盖原文件。

### 4.2 引擎边界与候选

`OfficeEditorAdapter` 隔离引擎、SAF 与 pagenote 状态。建议先验证 Collabora Office Android / LibreOfficeKit；它是具备 Android 原生应用的开源路线，但官方构建说明指出 Android native parts 需在 Linux 构建，代码位于 online monorepo，集成不是引入一个轻量 Maven widget。ONLYOFFICE 官方 Android mobile integration 需要 Document Server URL，因此不满足本地离线约束。此处不把任一候选写成已验证的嵌入式 SDK。

引擎选择是 SPEC 后的第一个技术门：先做可编译 spike，证明可复用的 Activity/view API、打包体积、许可证、文件读写/保存回调、PDF 导出、手写触控兼容、Android 14 和 16 KB ABI。若 Collabora 不能被嵌入并由 pagenote 控制生命周期，暂停引擎接线并提交证据和替代候选，得到发起人确认后再定实现方案；禁止偷偷改成仅依赖用户安装的外部 app 或云端 server。

### 4.3 数据与保存事务

- 项目 `manifest.left` 可指向桌面已支持的 Office 源文件；Android 需要扩大现有 PDF/Markdown extension allowlist，并补齐 project schema 示例/夹具。
- `src/` 中保留原始 Office 文件。PDF 页面、缩略图和 OCR/页面准备结果均为可丢弃派生缓存；缓存键至少包含源 SHA-256、格式、转换引擎和版本。
- 编辑会话使用临时副本。保存前比较启动时的源 revision；源已被外部修改时提示重载/另存/取消，不覆盖新内容。支持原子替换的 SAF Provider 上原子写回；不支持时先保留带明确名称的恢复副本并在 UI 中说明。
- 保存成功后重新计算源 revision、PDF 页数与缓存键；旧预览和旧 OCR 结果不得继续被 Agent 当作当前内容。笔记、笔迹、AI 批注不得静默删除；页数变化时将越界数据保留并提示用户处理。
- 进程被系统回收、空间不足、权限撤销或转换失败时，项目源文件仍保持最后一次已保存版本；恢复副本与失败状态可查询。

## 5. EARS 验收

- When 用户在手机点击菜单按钮，the app shall 打开可滚动的模态抽屉，显示桌面同名菜单组，并在返回键/选取命令后按规则关闭。
- When 用户在平板收起或展开侧栏，the app shall 保持所选分组、当前项目/页码、滚动/缩放和未保存编辑状态。
- When 用户由手机切换到平板窗口或旋转设备，the app shall 从抽屉切换为侧栏并保持当前操作不中断。
- When 某菜单命令依赖当前编辑模式或文件类型时，the app shall 显示正确启用状态；禁用时能访问原因和相应替代动作。
- When 用户打开 `.doc/.docx/.ppt/.pptx` 项目时，the app shall 在离线状态生成可读预览，保留原文件并对齐分页笔记。
- When 用户保存 Office 文档时，the app shall revision-check、写回源格式、刷新 PDF/OCR 派生缓存并保持用户笔记/笔迹/批注。
- When Office 保存、SAF 权限或格式处理失败时，the app shall 保留最后一次有效源文件及可恢复副本，并明确报告失败，绝不提示“已保存”。
- When Office 编辑器没有可靠的文本选区或页锚点时，the app shall 不创建假的 AI 批注定位数据。
- When Android 无网时，Office 阅读和编辑、Markdown/Mermaid 预览、离线 OCR 均不得尝试访问远程 renderer 或 Office server。

## 6. 验证矩阵

### Compose / UI

- Compact phone：抽屉打开/关闭、滚动、TalkBack 节点、每个分组进入、系统返回、选择命令后状态保留。
- Expanded tablet：侧栏展开/收起、各菜单组命令、横竖窗口变化、宽度阈值往返、笔记编辑和笔迹草稿不丢失。
- 桌面功能清单按 §2 表格逐项追踪到 Android command/action 和测试 ID；不能只测按钮存在，还要测命令效果与禁用条件。
- 在平板虚拟设备和手机小屏分别进行截图/实际触控检查；测试系统状态栏/导航栏/insets、不遮挡 PDF 和笔迹层。

### Office

- Fixture 覆盖 `.doc`/`.docx`/`.ppt`/`.pptx`、多页/多幻灯片、常见字体、表格、图片、公式、页眉页脚和大文件。
- 飞行模式执行导入 → 阅读 → 编辑一处文字/对象 → 保存 → 关闭 → 重开；断言源格式、内容、revision、预览和页码关系。
- 验证未保存退出、外部修改冲突、SAF 只读/权限撤回、Provider 不支持 rename、磁盘空间不足和引擎崩溃恢复。
- 验证 Office native libraries 的 16 KB ELF LOAD 与 APK/AAB ZIP 对齐，Android 14(API 34) 上启动/读写，当前目标 API 上做全流程回归。

## 7. 关联

- 菜单导航实施契约：`adaptive-menu-navigation.md`；任务与逐项验证：`../adaptive-menu-navigation-plan.md`。
- 父规格：`../requirements.md`、`../design.md`、`../tasks.md`。
- 当前功能契约：`reader-shell.md`、`reader-viewport.md`、`pen-canvas.md`、`note-rich-render.md`、`openai-model-settings.md`。
- 桌面实现参照：仓库根目录 `index.html`、`src/main.js`、`src/theme.css`、`src/desktop.css`、`desktop/word.cjs`、`desktop/mac-office.cjs`。
- Collabora：[Android build guide](https://github.com/CollaboraOnline/CollaboraOnline.github.io/blob/master/content/post/build-code-android.md)、[LibreOfficeKit API](https://docs.libreoffice.org/libreofficekit.html)。
- ONLYOFFICE：[Android mobile integration](https://github.com/ONLYOFFICE/api.onlyoffice.com/blob/master/site/docs/docs-api/get-started/how-it-works/mobile-integration/mobile-integration-on-android-devices.md)。
