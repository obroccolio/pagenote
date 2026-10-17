# Android Page Notes

## Scope

This slice connects an opened desktop pagenote project to an Android-native Markdown editor. Android SPEC remains under `android/docs/spec/`; this file is not part of the desktop documentation set.

The reader loads `src/right.md`, selects the note page matching the current one-based PDF page, and offers edit and rendered preview modes. On narrow layouts the PDF and note pane stack vertically; on wide layouts they appear side by side. Markwon's Apache-2.0 Android library supplies Markdown editing spans and native text rendering; the application does not implement its own Markdown editor.

This slice covers basic Markdown and GFM tables. Mermaid rendering, LaTeX, code highlighting, image asset resolution, note history UI, and advanced editor ergonomics remain required follow-up work under the parent Android SPEC.

## Data and Save Contract

- `<!-- pagebreak -->` splitting and joining follow `PageNotes` and preserve empty pages.
- Page numbers exposed by the reader are one-based.
- Each page's SHA-256 revision is calculated from the full page source.
- Before save, the app rereads `right.md`. If the target page's revision differs from the editor's base revision, save is rejected and the user is asked to reload; it never silently overwrites an edit to the same page.
- If only another page changed, the requested page edit is applied to the latest full document so unrelated changes are preserved.
- Saving a page beyond the current note count pads earlier pages with empty pages.
- SAF write permission is persisted when granted. Projects whose provider or grant does not permit writes remain readable and show a clear read-only state.

## Acceptance

- Opening a PDF project loads its notes and shows the note page for the current PDF page.
- Editing and saving one page leaves every other page, including empty pages, unchanged.
- A stale same-page revision prevents writing and reports a conflict.
- A concurrent update to a different page is retained when the current page is saved.
- Phone-width layout stacks source and notes; wide layout presents them side by side.
- Markwon is used for editor highlighting and rendered Markdown preview.
- Missing note data, lost SAF access, and unsupported writes produce visible errors rather than false save success.

## Verification

- JVM tests cover empty-page preservation, missing-page extension, target-page revision conflicts, and merging against the latest document.
- Android app compilation verifies the Markwon and Compose AndroidView integration.
- SAF write behavior and adaptive layout require later device acceptance because no Android device/emulator is available in this environment.

## References

- Markwon project and Apache-2.0 license: https://github.com/noties/Markwon
- Markwon editor API: https://noties.io/Markwon/docs/v4/editor/
- Android SAF permissions and editing documents: https://developer.android.com/training/data-storage/shared/documents-files
