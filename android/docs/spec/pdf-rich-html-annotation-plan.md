# Android Rich HTML Annotation Implementation Plan

**Spec:** `features/pdf-rich-html-annotation.md`

**Goal:** Render and edit desktop-compatible PDF HTML annotations safely and offline on Android.

**Architecture:** Preserve sanitized `type: "html"` items in `ProjectAnnotationStore`. Reuse the APK-bundled DOMPurify asset from a local, network-blocked WebView to sanitize and rasterize content. Keep hit testing and all mutations in the existing Kotlin annotation clipboard/geometry/history path. Keep all WebView work off the Compose UI's blocking path and discard stale page/project results.

**Constraints:** Android 14/API 34 minimum; no feature gating by Android version; no runtime network, external Office app, new project fields or project format migration. Test only one API 34 tablet AVD plus affected JVM tests. Every push updates root `CHANGELOG.md` and targets `fork/codex/android-port`.

## Task 1: HTML Contract Tests

- [ ] Replace the current expectation that HTML overlay/clipboard items are unsupported.
- [ ] Add failing tests for HTML item parsing, desktop marker round-trip, bounds/hit-testing, translation/resize, unknown-field preservation and malformed/oversized rejection.
- [ ] Run the focused JVM test and confirm it fails because HTML is currently rejected/skipped.
- [ ] Implement the minimal data/codec/geometry changes; rerun the focused test.

## Task 2: Offline Sanitizer and Rasterizer

- [ ] Add a local packaged HTML renderer page with strict CSP and a pinned DOMPurify script from the existing APK asset. Accept markup only as a JSON-quoted value; block navigation, network, file/content access and non-allowlisted resources.
- [ ] Implement a single-request-at-a-time renderer that reports sanitized markup and bounded layout dimensions, then snapshots the visible HTML into a bounded bitmap.
- [ ] Reject inputs/outputs above 100,000 code units and avoid allocations beyond 16,384 px per side / 4,194,304 page pixels. Preserve malformed source annotations and return a placeholder result instead of writing partial data.
- [ ] Add failing instrumentation assertions for safe formatting retention, script/event/unsafe URL removal, bitmap pixels, oversized content and a blocked external request; verify RED on the API 34 tablet AVD.
- [ ] Implement and verify the sanitizer/rasterizer against those focused cases.

## Task 3: Reader Overlay and Clipboard Flow

- [ ] Carry sanitized HTML on `PdfAnnotationOverlayItem`, hit-test HTML by its full bounds, and draw its page raster at the annotation's page coordinates.
- [ ] Render existing HTML annotations after page load on a worker; enforce page aggregate bitmap budgets and recycle replaced/stale results using the existing two-frame rule.
- [ ] Support desktop marker `type: "html"` paste after sanitization; support explicit `ClipData.Item.htmlText` paste in PDF annotation mode by measuring a 500-unit-wide block and creating desktop-compatible points.
- [ ] Preserve selection, move/resize, copy, delete and page-scoped undo/redo. Do not mutate pages if sanitization, rendering setup, project identity or SAF writes fail.
- [ ] Add one focused API 34 AVD Activity flow for rendering, marker paste, system HTML paste, selection/move/delete/undo and page-data isolation; run it and inspect the result.

## Task 4: Delivery

- [ ] Update task 29, add task 38, and correct stale clipboard-image status under task 5.8; keep tasks 35/37 open while any desktop gaps remain.
- [ ] Update `CHANGELOG.md` with the user-visible capability and focused verification record.
- [ ] Run affected JVM tests, the single API 34 AVD flow, `:app:compileDebugAndroidTestKotlin`, `:app:assembleDebug`, and `git diff --check`.
- [ ] Commit this feature slice and push it to `fork/codex/android-port`.
