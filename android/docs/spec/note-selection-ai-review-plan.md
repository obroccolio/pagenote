# Note Text Selection to AI Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a revision-bound AI review from selected Markdown note source and expose it safely to the Android Agent.

**Architecture:** Track the native note editor's UTF-16 selection, build a validated immutable review draft, and pass the existing shared `ReviewCreate` contract through the SAF store. Agent review reads expose bounded list summaries and one-record details; applied status is authorized only after a successful changed-page note write in the same dispatcher run.

**Tech Stack:** Kotlin, Jetpack Compose, Android `EditText`/`AndroidView`, kotlinx.serialization JSON, JUnit, AndroidX Compose instrumentation, Gradle.

**Spec:** `android/docs/spec/features/note-selection-ai-review.md`

## Global Constraints

- Android minimum API remains 34; do not rely on API-35-only PDF text extraction for note-editor selection.
- Preserve the existing `.pagenote` format and `src/ai-reviews.json` compatibility.
- Never create a PDF character anchor from OCR or block-level bounds.
- Every push includes an Unreleased `CHANGELOG.md` entry; no binary release is implied.
- User-facing strings are localized in English and Simplified Chinese.

## Review Focus

- Reversed, empty, whitespace-only, out-of-range, and supplementary-character selections must not produce invalid anchors.
- An external note edit between draft capture and review save must reject the stale review instead of associating it with newer text.
- Large quote/context/anchor data must fail visibly before any SAF write; no truncated quote may disagree with offsets.
- A failed or no-op note write, or a write to another page, must not authorize `applied`.
- Dismissing/deleting a review must leave the note content and sibling pages unchanged.

---

### Task 1: Selection Draft Contract

**Files:**
- Create: `android/app/src/main/java/io/pagenote/android/NoteSelectionReview.kt`
- Test: `android/app/src/test/java/io/pagenote/android/NoteSelectionReviewTest.kt`

**Interfaces:**
- Produces `NoteSelectionReviewDraft.capture(page: Int, context: String, start: Int, end: Int): NoteSelectionReviewDraft` with `quote`, `context`, `page`, `revision`, and JSON `anchor`.
- Produces `NoteSelectionReviewDraft.requireCurrentPage(currentPageText: String)` to reject stale context before persistence after the user edits the review draft.
- Throws `IllegalArgumentException` for invalid page/range, whitespace-only quote, quote over 100,000 UTF-16 code units, context over 1,000,000, or quote/offset mismatch.

- [ ] Write failing tests for exact quote/offsets, reversed/empty/invalid ranges, whitespace, supplementary characters, limits, and stale-page rejection.
- [ ] Run `./gradlew :app:testDebugUnitTest --offline --tests io.pagenote.android.NoteSelectionReviewTest` and observe the expected missing API failure.
- [ ] Implement the pure draft contract using `PageNotes.revision(context)` and `coordinateSystem=markdown-source-utf16`.
- [ ] Re-run the focused test and then all app JVM tests.

### Task 2: SAF Review Persistence

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/ProjectReviewStore.kt`
- Modify: `android/app/src/androidTest/java/io/pagenote/android/ExistingProjectSafTestProvider.java`
- Modify: `android/app/src/androidTest/java/io/pagenote/android/ExistingProjectSafRoundTripTest.kt`

**Interfaces:**
- `ProjectReviewStore.create(input: ReviewCreate): JsonObject` persists all supported fields with desktop-compatible limits.
- `ProjectReviewStore.find(id: String): JsonObject?` returns a single record without altering unknown fields.

- [ ] Add instrumentation assertions for selection record JSON round trip and quote/context/revision/anchor retention, including preservation of unknown fields.
- [ ] Compile/run the focused test and observe failure before production edits.
- [ ] Implement `ReviewCreate` persistence and exact-ID lookup in the SAF adapter.
- [ ] Run the focused provider test and existing SAF round-trip tests.

### Task 3: Note Editor Selection and Review UI

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Create: `android/app/src/androidTest/java/io/pagenote/android/NoteSelectionReviewUiTest.kt`

**Interfaces:**
- `NotePanel` reports a nullable half-open selection range from its native EditText and clears it when the page/content surface changes.
- `ReviewListDialog` accepts an optional `NoteSelectionReviewDraft`; when present, it shows the quote and creates `ReviewCreate(side="note", kind="selection", scope="page", ...)` after a latest-note revision check.
- The dialog applies `NoteSelectionReviewDraft.requireCurrentPage` against the latest `ProjectNoteStore` content immediately before writing the review.
- The existing `selection_to_annotation` command is enabled only for a valid selected note range, a writable project, and an idle reader; it saves an unsaved note before opening the draft.

- [ ] Add instrumentation tests for EditText selection callback, menu disabled/enabled behavior, quote rendering, cancel without review creation, and stale-note rejection before save.
- [ ] Run the focused instrumentation test and observe the expected missing behavior.
- [ ] Implement selection tracking, menu wiring, localized feedback, and selection-aware review dialog creation.
- [ ] Run focused UI tests and all Android unit tests.

### Task 4: Agent Context and Apply Guard

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/ProjectAgentTools.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/ProjectReviewStore.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/test/java/io/pagenote/android/ProjectAgentToolsTest.kt`
- Create: `android/app/src/androidTest/java/io/pagenote/android/ReviewHandoffUiTest.kt`

**Interfaces:**
- `read_review` accepts optional `id`; list mode returns at most 20 summaries with 160-code-unit instruction and 320-code-unit quote previews, ID mode returns exact quote/context/revision/anchor.
- Run-local rewrite evidence is populated only by successful `write_note`/`patch_note` results whose page revision changed, retaining the write's expected revision.
- `write_review_status(status=applied)` verifies the target review page and captured review revision match that evidence; the model-provided `noteRewritten` flag is not trusted.

- [ ] Add tests for ID detail formatting, bounded list output, and apply authorization for matching revision/page writes plus no-op, failed, stale-revision, and unrelated-page writes.
- [ ] Run focused tests and observe expected failure.
- [ ] Implement rich review reads, changed-page tracking, and same-run apply enforcement; remove the manual `applied` control.
- [ ] Run focused tests and all app/Agent JVM tests.

### Task 5: Build and Device Verification

**Files:**
- Modify: `android/docs/spec/features/note-selection-ai-review.md`
- Modify: `android/docs/spec/tasks.md`
- Modify: `CHANGELOG.md`

- [ ] Run full JVM tests, `:app:compileDebugAndroidTestKotlin`, and `:app:assembleDebug`.
- [ ] Run focused selection/review instrumentation tests on API 34 and API 35 16 KB AVDs; verify observed page sizes and test counts.
- [ ] Record physical-device and PDF/Office selection limitations without treating them as completed.
- [ ] Update the Android SPEC checklist and root changelog, then commit and push the feature slice.
