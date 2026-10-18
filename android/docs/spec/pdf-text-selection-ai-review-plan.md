# PDF Text Selection AI Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Android 14 searchable-PDF text selection and save the selection as a desktop-compatible AI review.

**Architecture:** Keep `PdfRenderer` for rendering. Add a PDFBox-Android adapter over the existing private PDF snapshot, map ordered text positions into platform-independent page text geometry, and route a drag-selection draft through the existing review dialog/store. Store the PDF context revision separately from the captured target note revision so Agent apply authorization remains valid.

**Tech Stack:** Kotlin, PdfBox-Android 2.0.27.0 (Apache-2.0), existing PdfRenderer/Jetpack Compose/SAF review store, JUnit and Android instrumentation.

**Spec:** `android/docs/spec/features/pdf-text-selection-ai-review.md`

## Global Constraints

- Supported device: Xiaomi Pad 5 Pro / Snapdragon 870 / Android 14 (API 34).
- Preserve the existing `PdfRenderer` rendering path and 16-megapixel cap.
- Keep scanned-page OCR region selection; do not invent character offsets from OCR blocks.
- Preserve desktop `width-1000` anchors and existing note-review behavior.
- Run affected module tests, Debug build, and only one focused API 34 instrumentation workflow for this UI/platform change.

## Review Focus

- Reversed drag must produce the same reading-order quote as forward drag; the JVM selection test covers this.
- PDF source revision and note target revision must not be conflated; the Agent authorization assertion in the focused workflow covers this.
- Empty/scan-only pages must not create character reviews; the focused workflow covers explicit unavailable selection while OCR remains enabled.
- Zoom/pan must not shift the saved anchor; the focused workflow selects after a non-default viewport transform.
- Stale note changes must not write a review; the focused workflow verifies unchanged review JSON after rejection.

---

### Task 1: Extract Android 14 PDF text geometry

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `android/core-reader/src/main/java/io/pagenote/core/reader/PdfTextExtraction.kt`
- Create: `android/app/src/main/java/io/pagenote/android/PdfBoxTextExtractor.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/PdfDocumentSession.kt`
- Test: `android/core-reader/src/test/java/io/pagenote/core/reader/PdfTextSelectionTest.kt`

**Interfaces:**
- Produce `PdfPageText(text: String, revision: String, glyphs: List<PdfTextGlyph>)`, with each glyph holding its UTF-16 `[start,end)` span and page-point bounds.
- `PdfDocumentSession.extractSelectableText(pageIndex: Int): PdfPageText?` returns null when there is no local snapshot or no usable text.

- [ ] Write one JVM test for forward/reversed drag over ordered glyphs, exact quote/context substring, and width-1000 bounding-rectangle mapping.
- [ ] Run `cd android && ./gradlew :core-reader:testDebugUnitTest --offline`; verify it fails because the selection model is absent.
- [ ] Implement the pure selection model and mapper in `core-reader`; run the same focused test.
- [ ] Add PDFBox-Android 2.0.27.0 and implement one-page extraction from the existing snapshot using `PDFTextStripper` positions; keep the adapter in `app` and keep rendering on `PdfRenderer`.
- [ ] Run the focused core-reader test, affected `:app:testDebugUnitTest`, and `:app:assembleDebug`.

### Task 2: Add PDF drag selection and review persistence

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/ReaderOcrSelection.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/ProjectReviewStore.kt`
- Modify: `android/feature-annotations/src/main/java/io/pagenote/feature/annotations/ReviewStore.kt`
- Modify: Android string resources for selection mode/status.
- Test: one focused test in `android/app/src/androidTest/java/io/pagenote/android/PdfTextSelectionReviewTest.kt`.

**Interfaces:**
- `PdfPageViewport` accepts the current `PdfPageText`, a `textSelectionEnabled` state, and an `onTextSelectionChanged` callback; it emits a selected quote range and page-width-1000 shape.
- `ReviewCreate` gains nullable `targetNoteRevision`; stores persist it only when supplied.

- [ ] Add one API 34 instrumentation workflow that selects extracted text in the real reader, saves the document review, and asserts the quote, context hash, anchor, and target note revision in `ai-reviews.json`.
- [ ] Run only `:app:connectedDebugAndroidTest --tests io.pagenote.android.PdfTextSelectionReviewTest` on the existing API 34 tablet AVD; verify it fails before UI/store implementation.
- [ ] Implement the AI menu selection mode, transformed highlight overlay, cancel/empty-text behavior, dirty-note save guard, and review creation.
- [ ] In the same test workflow, mutate the target note after capture and assert the review file is unchanged after the stale save is rejected.
- [ ] Run the focused test, affected app/agent JVM tests, and Debug build.

### Task 3: Preserve Agent apply semantics for PDF reviews

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/ProjectReviewStore.kt`
- Test: `android/app/src/test/java/io/pagenote/android/ProjectReviewStoreTest.kt`.

**Interfaces:**
- For `side="document"`, `authorizesApplied` compares the changed note write's expected revision with `targetNoteRevision`; for `side="note"`, keep comparing with `revision`.

- [ ] Add one unit assertion proving document review applies against `targetNoteRevision`, not the PDF context `revision`.
- [ ] Run the focused store test and affected Agent/app JVM test tasks.
- [ ] Run `:app:assembleDebug` and `git diff --check`.
