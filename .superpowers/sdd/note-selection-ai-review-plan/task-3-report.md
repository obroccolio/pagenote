# Task 3: Note Editor Selection and Review UI

## Implementation

- Report native `EditText` selection as a half-open UTF-16 `TextRange`; clear it when the note page, source content, or edit/preview surface changes.
- Replace the pending `selection_to_annotation` menu entry with a state-gated action. It requires a valid non-whitespace selection, write access, and an idle reader. The action captures the immutable note/page snapshot, awaits the existing revision-checked save when dirty, and opens the review draft only when that save succeeds.
- Extend `ReviewListDialog` with an optional selection draft, localized quote display and stale-note feedback. Immediately before creation, read the latest SAF note, confirm the draft still belongs to the current page, and validate the captured page revision before issuing `ReviewCreate`.
- Add English and Simplified Chinese strings. Cancel, stale content, and cross-page drafts do not create a review.

## Changed Files

- `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/res/values-zh-rCN/strings.xml`
- `android/app/src/androidTest/java/io/pagenote/android/NoteSelectionReviewUiTest.kt`
- `CHANGELOG.md`
- `.superpowers/sdd/note-selection-ai-review-plan/task-3-report.md`

No Task 1/2 source, report, spec, or plan ledger file was edited. The existing manual review-applied UI and Agent apply behavior remain untouched (Task 4 scope).

## TDD Evidence

Tests were written before their corresponding UI implementation.

### RED

The first native-selection instrumentation test attempted to pass `onSelectionChanged` into `NotePanel`. Before production edits, Android test compilation failed with the expected missing `onSelectionChanged` parameter and unresolved callback lambda. This demonstrated the missing native selection-reporting behavior.

The expanded menu/dialog tests were also compiled before their implementation. They produced the expected missing `selectionToAnnotationEnabled`, `ReviewListDialog.selectionDraft`, and `review_selection_stale` diagnostics.

### GREEN

The first native-selection test passed after implementing the callback: selecting UTF-16 indices `1..3` in `A😀B` reported `TextRange(1, 3)` and captured exactly `😀`.

The final focused run used:

```sh
ANDROID_HOME=/Users/broccoli/Library/Android/sdk ANDROID_SDK_ROOT=/Users/broccoli/Library/Android/sdk ./gradlew :app:connectedDebugAndroidTest --offline -Pandroid.testInstrumentationRunnerArguments.class=io.pagenote.android.NoteSelectionReviewUiTest
```

Result: `BUILD SUCCESSFUL`; all 7 tests passed on both connected API 35 devices: `Pixel_Tablet` (4 KB) and `Pixel_Tablet_16KB` (16 KB). Coverage includes native selection and exact quote, reset on source/page/preview changes, command enablement semantics, quote display, cancel without a write, persisted review fields/anchor, stale revision rejection, and cross-page draft rejection when both pages have matching content.

The final compile and app JVM run used:

```sh
ANDROID_HOME=/Users/broccoli/Library/Android/sdk ANDROID_SDK_ROOT=/Users/broccoli/Library/Android/sdk ./gradlew :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest --offline
```

Result: `BUILD SUCCESSFUL`; Android test Kotlin compilation passed and all 166 app JVM tests passed (0 skipped, 0 failures, 0 errors).

## Limits and Self-Review

- Menu command state is exercised through the existing `ReaderAdaptiveMenu` semantics and the pure enablement predicate. The full Activity open-project menu route and an unsaved-note save-conflict/failure interaction were not exercised end-to-end; the Activity action delegates to the existing revision-checked `saveCurrentNote()` and returns without showing a draft when it returns false.
- Dialog persistence and stale checks use the existing `ExistingProjectSafTestProvider`; no DocumentsUI setup was added.
- Initial test-harness corrections addressed the pre-populated Compose Activity, the existing provider's fixture filename, and the dialog's separate accessibility window. These did not require expanding production scope.
- After all edits, `git diff --cached --check` passed; the six staged paths are exactly the Task 3 allowlist.
- No screenshots were captured. No push was performed.
