# pagenote Android Beta

**[⬇ 下载 Android 14 测试版 APK（ARM64）](https://github.com/obroccolio/pagenote/releases/download/android-v1.0.0-beta.5/pagenote-android-1.0.0-beta.5-arm64-v8a.apk)** · [Android 测试版说明与校验值](https://github.com/obroccolio/pagenote/releases/tag/android-v1.0.0-beta.5)

本测试版针对 Android 14（API 34）ARM64 平板开发，重点目标为小米平板 5 Pro（骁龙 870）；其他 Android 版本和设备不在当前适配范围。Beta 使用独立包名 `io.pagenote.android.beta` 和独立应用数据；侧载 APK 使用本机 debug 签名，不是 Play 商店版本。Windows Beta 暂不可用；Mac 1.0 稳定版仍在 [main 分支](https://github.com/obroccolio/pagenote/tree/main)，不是本 Android 测试包的一部分。

**[⬇ 下载 Mac 版 1.0.0（Apple Silicon · DMG）](https://github.com/obroccolio/pagenote/releases/download/v1.0.0/pagenote-1.0.0-mac-arm64.dmg)** · [Intel DMG](https://github.com/obroccolio/pagenote/releases/download/v1.0.0/pagenote-1.0.0-mac-x64.dmg) · [Apple Silicon ZIP](https://github.com/obroccolio/pagenote/releases/download/v1.0.0/pagenote-1.0.0-mac-arm64.zip) · [Intel ZIP](https://github.com/obroccolio/pagenote/releases/download/v1.0.0/pagenote-1.0.0-mac-x64.zip) · [校验值与发布说明](https://github.com/obroccolio/pagenote/releases/tag/v1.0.0)

Mac 首个正式版 **1.0.0**。下载对应芯片的 DMG，拖进应用程序即可；想修改项目，可下载源码按下方说明构建。

<img src="build/icon.png" alt="pagenote 双页图标" width="88">

[English](README.en.md) · 简体中文

pagenote 把课件和讲解放在一起：左边读原文，右边看对应页的笔记。

## 做这个工具的初衷

AI 浪潮扑面而来，用 AI 辅助学习早已是寻常选择。大家常常把课件、论文交给 AI，期待它顺着已有的知识基础慢慢铺陈，拾级而上，一步步把难懂的知识点讲明白。

但这套学习方式，一直有着很现实的短板。阅读时总要在两个窗口来回切换，原文和讲解各占一边，翻着翻着就对不上内容。AI 有时还会调整原文的行文顺序，打乱课件本身的逻辑，跟着读的时候很容易跟不上节奏。如果一次性让 AI 解析完整份材料，又容易受上下文和输出长度制约，大多只能拿到概括性的总结，细致的推导、专业符号和配套例子经常被省略。看似已经看懂了内容，等到真正做题实操，依旧难以灵活运用。

这些细碎又恼人的痛点，催生了 Pagenote 的想法。它希望把原文和讲解按页面牢牢对应在一起，每一页都如同一次独立的请教，从既有认知出发，补齐所有省略的中间步骤，承接前文继续往下推进。遇到难以理解的片段，可以选中文字、框选图片，简单备注需求，让 AI 换例子、换思路重新解读；笔记能够随学随改，不用反复复制内容，一次次重复交代前置背景。同时充分发挥 AI 多样的能力：抽象概念搭配示意图，算法附上可运行代码，数理推导生成公式，思考流程绘成框图，让抽象的知识变得直观易懂，这就是 Pagenote 最初的设计初衷。

## Android 测试版

- 原生 Kotlin / Jetpack Compose 阅读器，支持 PDF 与 Markdown 笔记、按页项目、PDF 批注和手写笔迹。
- Android 14 最低版本；离线 OCR 使用随包模型，不需要网络。
- OpenAI 兼容模型通过用户配置的 API 端点接入；应用不内置模型或密钥。
- 进程内 Office 编辑尚未实现，复杂 Word/PPT 编辑仍需桌面版；实体设备长时间/笔迹延迟及真实 AI 服务完整链路仍待进一步验证。

## Mac 稳定版功能

以下介绍的是单独发布的 Mac 稳定版，不代表 Android 测试包已通过相同范围的验收。

- 原生体验：Finder 双击打开项目、Dock 重开窗口、标准菜单与 Command 快捷键、关闭前自动保存。
- 本机 Office 接入：Word/PPT 导入、项目预览及保存后刷新需要已授权的 **Microsoft Word / PowerPoint**。首次使用时在系统提示中允许 pagenote 控制 Word / PowerPoint；被拒绝后可在 **系统设置 → 隐私与安全性 → 自动化** 中开启。Word 编辑使用独立窗口，MCP 支持读取、选区、带版本检查的替换及 PDF 渲染截图。详见 [Mac Office 说明](docs/MAC-OFFICE.md)。
- 离线 OCR：内置中英文识别，按页准备文字、坐标和置信度、页面与区域图片、Markdown 表格及 Mermaid 图像，结果随项目缓存。原文和原图保留，识别不准的公式或图表仍需核对，详见 [本地页面准备](docs/LOCAL-PREPARATION.md)。
- AI 协作：逐页笔记、AI 标注与 MCP 接口，软件本身不内置大模型，需搭配具备对应能力的 AI 客户端。连接 AI 后请按 [逐页精讲约定](docs/AI-TEACHING.zh-CN.md) 执行。

## 功能一览

以下为当前 1.0 的实际截图，使用自编的学习示例。

### 原文与讲解，按页对照

左边阅读文档，右边查看当前页的 Markdown 笔记。翻到下一页，对应讲解随之切换，原文与笔记各自滚动。支持 PDF、Markdown；Word 和 PowerPoint 借助本机 Microsoft Office 转换阅读。

![双栏阅读：左侧原文，右侧对应页讲解](docs/images/paired-reading.png)

### 公式、代码和流程图

笔记支持 LaTeX 公式、代码高亮、Mermaid 图表、表格和图片粘贴。推导、验证代码和流程可以放在同一页，阅读时直接显示渲染结果。

代码高亮在本机完成，不需要联网下载语法。显示异常时，可参考 [代码渲染说明](docs/CODE-RENDERING.md) 提供版本和最小示例，便于排查。

![Markdown 中的公式、Python 代码高亮和 Mermaid 流程图](docs/images/markdown-rendering.png)

### 需要修改时，再展开编辑

通过编辑菜单选择右侧笔记，展开编辑区域和对应工具。直接修改渲染后的内容，代码高亮随输入更新；完成后回到双栏阅读。Word、PDF 和 PPT 的源文件编辑交给本机默认应用，保存后回到 pagenote 更新预览。

![笔记展开后的编辑区域与工具菜单](docs/images/note-editing.png)

### 把修改要求留在具体位置

选中文字或框选区域，就能添加 AI 标注。比如“这一段没懂，补个例子”，或“这里公式太密，先用文字解释”。批注窗口在对应一侧打开，可以拖动，不必离开当前页面。

![选中文字后，在同侧浮窗填写 AI 修改要求](docs/images/ai-annotation.png)

标注汇集到对应一侧的工作台，也可以记录整篇的讲述风格要求。双击页上的标注编号可以打开工作台，右键可以删除标注。连接的 AI 可通过 MCP 读取这些要求，再修改对应笔记。

![AI 标注工作台中的局部修改记录](docs/images/ai-workbench.png)

**当前公开版本：1.0.0。** 欢迎拿真实的学习材料试用，遇到问题请附版本、操作步骤和截图反馈。

## 下载与安装

[Apple Silicon DMG](https://github.com/obroccolio/pagenote/releases/download/v1.0.0/pagenote-1.0.0-mac-arm64.dmg) · [Intel DMG](https://github.com/obroccolio/pagenote/releases/download/v1.0.0/pagenote-1.0.0-mac-x64.dmg) · [Apple Silicon ZIP](https://github.com/obroccolio/pagenote/releases/download/v1.0.0/pagenote-1.0.0-mac-arm64.zip) · [Intel ZIP](https://github.com/obroccolio/pagenote/releases/download/v1.0.0/pagenote-1.0.0-mac-x64.zip) · [发布说明与校验值](https://github.com/obroccolio/pagenote/releases/tag/v1.0.0)

1. 在本仓库的 **Releases** 中下载对应芯片的 DMG（Apple Silicon 选 arm64，Intel 选 x64）。
2. 打开 DMG，把 pagenote 拖进应用程序文件夹。
3. 首次启动后，从 **设置 → 语言 / Language** 选择简体中文或 English。

ZIP 版解压后直接运行其中的 pagenote 即可。当前构建没有 Apple 开发者签名，首次打开若被 Gatekeeper 拦截，请在 **系统设置 → 隐私与安全性** 中允许运行。

## 其他操作

- **手形工具**用于平移页面；**画笔**提供书写、荧光笔和橡皮擦。
- 本地开发版支持触控板双指捏合，连续缩放指针所在的文档或笔记整页；文字、图片与批注同步缩放，松手后 PDF 补充清晰渲染。双指上下、左右滑动可平移放大的页面，沿用系统惯性；也可使用 Ctrl / Command + 鼠标滚轮。
- 左侧“适应”恢复文档比例；菜单“重置笔记缩放”恢复笔记比例。页面小于视口时居中，放大后可以双向移动。
- 左侧页间分界越过视图中线时，切换对应笔记；可在设置中关闭切换动画。
- 自动保留笔记历史，支持恢复；项目、笔迹和标注保存在本机。
- 项目交给外部 AI 客户端处理时，数据的使用方式取决于该客户端的隐私政策。

## 项目格式

从 **文件 → 新建项目**创建，指定名称和保存目录。项目可以整体移动：

```text
我的项目/
  project.pagenote          # JSON 项目入口，使用相对链接
  src/
    left.md                # 左侧源文件；导入后可能为 left.pdf / left.docx 等
    right.md               # 右侧笔记，以 <!-- pagebreak --> 分页
    assets/                # 图片及附件
    annotations.json       # 笔迹（产生笔迹后保存）
    ai-reviews.json         # AI 标注（产生标注后保存）
```

安装版可以双击 `project.pagenote` 打开；也可以在软件里用 **文件 → 打开项目**。

旧版 `project.page-notes.json` 也可从软件里打开。入口中的 `left` 与 `right` 链接是文件来源，打开时按链接重新读取 `src`；不要只移动入口文件。

## 系统要求

- macOS 12 或更高；提供 Apple Silicon 与 Intel 构建。
- PDF 与 Markdown 不要求安装 Office。Word/PPT 导入、项目预览及保存后刷新需要本机已授权的 **Microsoft Word / PowerPoint**。
- 安装包内含 MCP 所需 Node.js 运行时，普通用户不需要安装 Node.js。

## 从源码运行

安装 Node.js 22.13 或更高版本，然后在项目根目录执行：

```bash
npm ci
npm run build
npm run desktop
```

开发网页模式：`npm run dev`。生成 macOS DMG 与 ZIP（仅 Apple Silicon / arm64）：`npm run package:mac`；arm64 文件夹版：`npm run package:mac:local`。测试套件：`npm run test:release`、`npm run test:mcp`、`npm run test:prepared`、`npm run test:mac-office`（需要已激活的本机 Word / PowerPoint）。发布流程见 [中文发布教程](docs/RELEASING.zh-CN.md) 与 [English release guide](docs/RELEASING.en-US.md)。

## 工程与发布状态

术语和翻译集中在 `locales/catalog.json`，语言设置持久化保存；用户文档内容不参与界面翻译。详细术语依据见 [术语规范](docs/TERMINOLOGY.md)。

验收范围及仍需完成的工作见 [发布准备报告](docs/RELEASE-READINESS.md)。提交问题时请注明版本、系统、复现步骤和脱敏日志；不要上传个人项目或 MCP 连接密钥。

## 反馈与联系

使用中遇到 Bug，欢迎[提交 Issue](https://github.com/obroccolio/pagenote/issues/new)。附上软件版本、操作步骤和截图，会更方便复现和排查。

## 许可

本项目采用 [MIT License](LICENSE)，允许商业使用。依赖组件有各自的许可；分发时须保留生成的 `THIRD-PARTY-NOTICES.txt`、Node.js 许可及 Electron/Chromium 许可。Microsoft Office 的授权由使用者负责。

MCP 接入文档：[中文](MCP接口说明.md) · [English](docs/MCP.en-US.md)

编辑方式和图标说明：[本轮调整](docs/EDITOR-REFERENCE.md)。
单独导入的文件编辑的是 pagenote 的本地副本；项目编辑打开 `src` 中链接的文件。保存后切回 pagenote。
