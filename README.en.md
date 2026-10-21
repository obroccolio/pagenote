# pagenote

**Usage policy: This project is for non-commercial use only. Commercial use is not permitted.**

**[⬇ Download the Android 14 Beta APK (ARM64)](https://github.com/obroccolio/pagenote/releases/download/android-v1.0.0-beta.5/pagenote-android-1.0.0-beta.5-arm64-v8a.apk)** · [Release notes and checksum](https://github.com/obroccolio/pagenote/releases/tag/android-v1.0.0-beta.5)

This beta targets Android 14 (API 34) ARM64 tablets, with the Xiaomi Pad 5 Pro (Snapdragon 870) as the intended device; other Android versions and devices are outside the current support scope. It uses the separate application ID `io.pagenote.android.beta` and separate app data. The sideload APK uses a local debug signing key and is not a Play Store build. A Windows beta is unavailable; the Mac 1.0 stable build remains on the [main branch](https://github.com/obroccolio/pagenote/tree/main) and is not part of this Android beta.

**[⬇ Download Mac 1.0.0 (Apple Silicon · DMG)](https://github.com/obroccolio/pagenote/releases/download/v1.0.0/pagenote-1.0.0-mac-arm64.dmg)** · [Intel DMG](https://github.com/obroccolio/pagenote/releases/download/v1.0.0/pagenote-1.0.0-mac-x64.dmg) · [Apple Silicon ZIP](https://github.com/obroccolio/pagenote/releases/download/v1.0.0/pagenote-1.0.0-mac-arm64.zip) · [Intel ZIP](https://github.com/obroccolio/pagenote/releases/download/v1.0.0/pagenote-1.0.0-mac-x64.zip) · [Checksums and release notes](https://github.com/obroccolio/pagenote/releases/tag/v1.0.0)

The first stable Mac release, **1.0.0**. Download the DMG for your chip and drag it into Applications. To make changes, download the source and follow the build instructions below.

<img src="build/icon.png" alt="pagenote paired-page icon" width="88">

English · [简体中文](README.md)

pagenote puts a document on the left and notes for the current page on the right.

## Where pagenote started

As AI becomes part of everyday life, using it to study is a familiar choice. We give it slides and papers, hoping it will build on what we already know and guide us, step by step, toward an understanding of difficult material.

Yet this way of learning has practical shortcomings. The source and explanation sit in separate windows, and switching between them makes it easy to lose our place. AI sometimes rearranges the material, disrupting the order of the lesson. Asking it to explain an entire document at once can run into context and output limits, producing a summary that skips derivations, unfamiliar notation and examples. We may feel we have understood it, only to struggle when solving a problem or putting it into practice.

These frustrations led to Pagenote. The idea is to keep each source page beside its explanation and give it the attention of a separate request: start from familiar knowledge, fill in the intermediate steps and continue from the preceding page. When something is unclear, select a passage or a figure and leave a request for another example or a different explanation. Notes can be revised as we learn, without repeatedly copying text and explaining the background. Illustrations, runnable code, mathematical notation and flowcharts give AI more ways to make abstract ideas understandable. That was the original purpose of Pagenote.

## Android beta

- Native Kotlin / Jetpack Compose reader for PDF and Markdown notes, page-linked projects, PDF annotations and pen strokes.
- Android 14 minimum; bundled offline OCR models do not require network access.
- OpenAI-compatible models can be configured with a user-provided endpoint; the app includes no model or API key.
- In-process Office editing is not implemented. Complex Word/PPT edits remain desktop work; physical-device endurance, pen latency and a complete live AI-service workflow need more validation.

## Mac stable-build features

The following describes the separately released Mac stable app and does not claim equivalent Android acceptance.

- Native behavior: Finder project opens, Dock reactivation, standard menus with Command shortcuts, and save-before-hide.
- Local Office integration: Word/PPT import, project preview and post-save refresh need locally licensed **Microsoft Word / PowerPoint**. On first use, allow pagenote to control Word / PowerPoint; if denied, enable it under **System Settings → Privacy & Security → Automation**. Mac Word editing uses a separate window with MCP read, selection, revision-checked replacement and PDF-rendered screenshots. See [Mac Office notes](docs/MAC-OFFICE.md).
- Offline OCR: bundled Chinese/English recognition prepares per-page text, coordinates and confidence, page/region images and native Markdown table/Mermaid assets, cached with the project. Sources and visuals are preserved; uncertain formulas and charts still need checking. See [local page preparation](docs/LOCAL-PREPARATION.md).
- AI collaboration: page-linked notes, AI annotations and an MCP bridge. The app does not include a model; it needs an AI client with the relevant capabilities. Follow the [page-by-page teaching workflow](docs/MCP.en-US.md#page-by-page-teaching).

## Features in pictures

These are real screenshots of 1.0, using original demonstration content. The interface shown is in Chinese.

### Read the source beside its explanation

The left pane shows the document; the right holds Markdown notes for the active page. Notes follow page changes, and each pane scrolls independently. Read PDF and Markdown directly; Word and PowerPoint conversion uses locally installed Microsoft Office.

![Source document and notes for the current page](docs/images/paired-reading.png)

### Formulas, code and diagrams

Notes support LaTeX, syntax-highlighted code, Mermaid diagrams, tables and pasted images. Keep derivations and verification code on the same page, with the results rendered for reading.

Code highlighting runs locally, without downloading grammars. If rendering fails, include the app version, a minimal code block and a screenshot in your report; see the [rendering notes](docs/CODE-RENDERING.md).

![Rendered formulas, Python highlighting and a Mermaid flowchart](docs/images/markdown-rendering.png)

### Expand the editor when needed

Choose the notes pane from the editing menu to expand it and reveal its tools. Edit rendered content directly, with code highlighting updating as you type. Source edits for Word, PDF and PowerPoint open in the system default app; save and return to refresh the preview.

![Expanded note editor with its contextual tools](docs/images/note-editing.png)

### Leave feedback where it belongs

Select text or an area and add an AI annotation, such as “Add an example here” or “Explain this in words before introducing the formula.” A movable panel opens in the same pane.

![Writing a revision request beside selected text](docs/images/ai-annotation.png)

Annotations collect in a workbench for the corresponding pane. You can also record requests about the whole document. Double-click a marker to open its workbench, or right-click to delete it. A connected AI client can read the requests through MCP and revise the notes.

![AI annotation workbench with a local revision request](docs/images/ai-workbench.png)

Pagenote currently supports two-pane reading, page-linked notes and AI annotations. Its MCP interface lets AI read and edit page content. The app does not include a model; detailed page-by-page explanations and image generation require an AI client with the relevant capabilities.

Follow the [page-by-page teaching workflow](docs/MCP.en-US.md#page-by-page-teaching): make a separate generation request for each page, give it sufficient current-page context and include the previous and next source pages without waiting for earlier explanations. Do not generate a whole chapter and split it into pages afterward.

Beta.5 includes a [page tutor skill](skills/pagenote-page-tutor/SKILL.md). Use **AI Annotations → Copy page-by-page teaching prompt**, or select the MCP prompt `pagenote-page-tutor` and supply a learner background, page range and goal. AI clients can also read `page-notes://skills/page-tutor`. The template guides a connected client; it does not call a model by itself.

Use **Settings → AI settings** to edit or restore the default system prompt. General document tasks keep their scope; the page tutor applies only to relevant learning requests. The prompt is saved locally. Reconnect the AI client after saving. MCP supplies the instructions; the client controls its actual system-prompt layer.

Beta.5 reads source and notes separately, fetches only the needed note passages, and saves concise page handoffs inside the project. Handoffs are marked stale when source or notes change, leaving more context for the current explanation. See the [MCP read options](docs/MCP.en-US.md#token-efficient-reads).

It also bundles offline Chinese/English OCR and prepares reusable positioned text, confidence, page/region images and native Markdown table/Mermaid assets. Sources and visuals remain available for checking uncertain formulas and charts. See [local page preparation](docs/LOCAL-PREPARATION.md).

**Current versions: Mac 1.0.0 stable / Android 1.0.0-beta.5.**

## Download and install

[Apple Silicon DMG](https://github.com/obroccolio/pagenote/releases/download/v1.0.0/pagenote-1.0.0-mac-arm64.dmg) · [Intel DMG](https://github.com/obroccolio/pagenote/releases/download/v1.0.0/pagenote-1.0.0-mac-x64.dmg) · [Apple Silicon ZIP](https://github.com/obroccolio/pagenote/releases/download/v1.0.0/pagenote-1.0.0-mac-arm64.zip) · [Intel ZIP](https://github.com/obroccolio/pagenote/releases/download/v1.0.0/pagenote-1.0.0-mac-x64.zip) · [Android 14 beta APK (ARM64)](https://github.com/obroccolio/pagenote/releases/download/android-v1.0.0-beta.5/pagenote-android-1.0.0-beta.5-arm64-v8a.apk) · [Release notes and checksums](https://github.com/obroccolio/pagenote/releases)

1. Download the DMG for your chip from this repository's **Releases** (arm64 for Apple Silicon, x64 for Intel).
2. Open the DMG and drag pagenote into Applications.
3. Choose Simplified Chinese or English under **Settings → Language / 语言**.

Alternatively, extract the ZIP and run pagenote directly. The current build is not Apple-signed; if Gatekeeper blocks the first launch, allow it under **System Settings → Privacy & Security**.

### macOS status

macOS remains experimental; it is not offered as a stable download here. Check the [beta branch](https://github.com/laihong06/pagenote/tree/beta) for matching test builds and availability. Contributor and Office documentation remain in [Mac setup](docs/MAC-OFFICE.md).

## Other controls

- **Hand** pans the page. **Pen** provides ink, highlighting and erasing.
- Local development builds support continuous two-finger pinch on the document or notes page under the pointer. Text, images and annotations scale together; PDF detail renders after the gesture settles. Two-finger scrolling pans enlarged pages in both axes with system momentum. Ctrl / Command + mouse wheel also zooms.
- Use Fit for the source or Reset notes zoom from the menu to restore the default. Small pages stay centered; enlarged pages can pan in both axes.
- Notes follow the active source page as page boundaries cross the viewport midpoint. Page-transition animation can be disabled in settings.
- Automatic note history and restore. Projects, ink and annotations stay on the local machine.
- Data shared with an external AI client is governed by that client's privacy policy.

## Portable projects

Create a project using **File → New project** and choose its name and folder:

```text
My project/
  project.pagenote          # JSON manifest with relative links
  src/
    left.md                # Source: may become left.pdf, left.docx, etc.
    right.md               # Page notes separated by <!-- pagebreak -->
    assets/                # Images and attachments
    annotations.json       # Ink, created when used
    ai-reviews.json         # AI annotations, created when used
```

With the installed app, double-click `project.pagenote`, or choose it under **File → Open project**. Legacy `project.page-notes.json` files remain supported. Move the complete folder, not just its manifest.

On Mac, move pagenote to Applications and open projects through Finder or **File → Open project**. If needed, select pagenote under **Open With**; Windows `.cmd` registration scripts do not apply to Mac.

## Requirements

- macOS 12 or later, with Apple Silicon and Intel builds.
- Android 14 (API 34) or later on ARM64 tablets; the Xiaomi Pad 5 Pro is the intended device, and other Android versions and devices are outside the current scope.
- PDF and Markdown do not require Office. Word/PPT import, project preview and post-save refresh need locally licensed **Microsoft Word / PowerPoint**.
- The distribution includes the Node.js runtime used by MCP. End users do not need a Node.js installation.

## Build from source

Install Node.js 22.13 or newer, then run:

```bash
npm ci
npm run build
npm run desktop
```

Use `npm run dev` for browser development. Use `npm run package:mac` for macOS DMG and ZIP (Apple Silicon / arm64 only), or `npm run package:mac:local` for an arm64 folder build. Tests: `npm run test:release`, `npm run test:mcp`, `npm run test:prepared`, `npm run test:mac-office` (requires locally activated Word / PowerPoint).

See the [release guide](docs/RELEASING.en-US.md), [terminology specification](docs/TERMINOLOGY.md) and [release-readiness report](docs/RELEASE-READINESS.md). Bug reports should include the version, macOS version, reproduction steps and redacted logs. Do not upload private projects or MCP connection keys.

## Feedback and contact

Please [open an issue](https://github.com/obroccolio/pagenote/issues/new) to report a bug, with the app version, reproduction steps and a screenshot.

## License

The author prohibits commercial use. The current [LICENSE](LICENSE) remains MIT and is inconsistent with this usage policy; it has not yet been changed, and this statement does not replace its existing terms. Dependencies have separate licenses; retain `THIRD-PARTY-NOTICES.txt`, the Node.js license and Electron/Chromium license files when distributing. Microsoft Office licensing is the user's responsibility.

MCP integration: [English](docs/MCP.en-US.md) · [中文](MCP接口说明.md)

Editor and icon changes: [reference](docs/EDITOR-REFERENCE.md).
Standalone imports edit the managed local copy; projects edit the linked file in `src`. Save, then return to pagenote.

Beta.5 routes tasks through `page-notes://prompts/index`. Read only relevant, changed modules via `page_notes_read_prompt`. AI settings can customize each module. Page requests may run concurrently: recommend `max(1, floor(total pages / 10))`, capped by remaining pages, client/service limits and available resources. `page_notes_validate_note` checks formatting and local image assets before publication; it is not a guarantee of factual correctness or cross-device rendering.

## Contributors

Thanks to [obroccolio](https://github.com/obroccolio) for Mac support, the Android port and downloadable builds, and [LTH0001](https://github.com/LTH0001) for formula regression coverage and acceptance-test improvements. Forks and PRs are welcome; include a changelog entry and describe the platforms and scope tested.

[Release policy](docs/RELEASE-POLICY.md) · [Known issues](KNOWN_ISSUES.md)
