# Android Mermaid Layout Regression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure an asynchronously rendered Mermaid block in Android Markdown reserves its actual vertical space and never paints over surrounding note text.

**Architecture:** Keep the existing native TextView/Markwon path and isolated offline Mermaid WebView. First reproduce the imported page-12 layout at the tablet note-pane width and trace the async drawable/span measurement boundary; then make one minimal production change at the layer proven to be wrong. Do not migrate the Markdown renderer as part of this fix.

**Tech Stack:** Kotlin, Jetpack Compose AndroidView, Markwon 4.6.2, AndroidSVG 1.4, Android instrumentation tests, Pixel Tablet API 34/35 AVD.

**Spec:** `android/docs/spec/features/note-rich-render.md`; library decisions: `android/docs/spec/renderer-library-evaluation.md`.

## Global Constraints

- Android `minSdk` remains 34.
- Mermaid scripts and sanitization stay bundled and offline; no external requests or JS bridge.
- Project Markdown images remain restricted to Pagenote's SAF-authorized assets; do not enable remote image loading.
- Keep the original 69-page PDF/project unmodified; use only the disposable imported test copy or synthetic regression text.
- Preserve source text, surrounding Markdown order, and formula rendering on failure or re-layout.
- Commit this feature only after its regression test, full Android tests, APK build, and all-ABI 16 KB checks pass; update root `CHANGELOG.md` in the same commit.

## Review Focus

- Long Chinese paragraphs before and after Mermaid at narrow tablet note-pane width do not paint beneath the SVG.
- The async loading placeholder-to-SVG transition does not change source order or erase later formulas/text.
- A Mermaid parse/render failure remains readable and does not reserve an unbounded height.
- Rebinding the same Markdown, navigating away/back, and recomposition do not leave stale drawable bounds or duplicate diagrams.
- Mermaid line breaks and SVG intrinsic dimensions are measured in the same pixel coordinate system as the host TextView.

---

### Task 1: Reproduce and fix Mermaid block overlap

**Files:**
- Modify: `android/app/src/androidTest/java/io/pagenote/android/MarkdownMermaidPreviewTest.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MermaidPreviewHost.kt` or the specific Markwon image/span integration proven responsible by investigation
- Modify: `android/docs/spec/features/note-rich-render.md`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: the existing `MermaidPreviewHost.setMarkdown`, `NotePreviewRewrite.forPreview`, `MermaidRenderCoordinator`, and bundled Mermaid renderer.
- Produces: a passing instrumentation regression named `mermaidBlockDoesNotOverlapFollowingParagraphAtTabletNotePaneWidth`, asserting the rendered SVG block's measured/drawn region is disjoint from preceding and following Markdown text after async load.

- [ ] Capture a fresh baseline screenshot of disposable imported project page 12 on the Pixel Tablet API 35 AVD, then record the overlap and measured host/diagram dimensions.
- [ ] Add or adjust the named instrumentation regression using synthetic long Chinese paragraphs, the page-12 graph structure, a display formula after the graph, and the measured tablet note-pane width; run it and observe the expected failure before changing production code.
- [ ] Trace the failing assertion through Markwon's image-span size callback, `MermaidSvgMediaDecoder` intrinsic dimensions, and the real `TextView` host; record the root cause in the Android SPEC implementation notes.
- [ ] Implement the smallest production change that makes the test pass without weakening the offline image policy or replacing the Markdown renderer.
- [ ] Run the focused instrumentation regression on API 35, then API 34; verify the imported project's page 12 visually and inspect pages 1, 35, and 69 for unintended layout changes.
- [ ] Run `:app:testDebugUnitTest`, `:feature-notes:testDebugUnitTest`, and the full Android instrumentation suite on API 34 and API 35; run `:app:assembleDebug` and the four-ABI 16 KB APK checks.
- [ ] Update the feature/task docs and Unreleased changelog with only verified behavior, then commit as `fix(android): prevent Mermaid preview overlap` and push to `fork/codex/android-port`.
