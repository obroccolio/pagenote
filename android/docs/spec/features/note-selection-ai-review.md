# Note Text Selection to AI Review

## Goal

Let a user turn selected Markdown note source into a located `selection` AI review that the Android Agent can read and apply without changing the project format.

## Scope

- Selection is captured from the current page's native Markdown `EditText` in Edit mode. The existing Word menu command `Selected text to AI annotation` is enabled only while a valid selection exists and the note is writable.
- Source-PDF character selection, Office editing/selection, and visual selection highlights are not implemented by this slice. It must not manufacture character-level PDF anchors from page text blocks or OCR rectangles.
- Review records remain in `src/ai-reviews.json`; normal point/region annotations remain in `src/annotations.json`.

## Interaction

1. The user selects a non-whitespace range in the current Markdown editor and chooses `Selected text to AI annotation`.
2. The app captures the exact Markdown source quote, full current page context, zero-based half-open UTF-16 source offsets, page, and note revision. If the note has unsaved changes, the existing revision-checked save runs first; a save failure aborts review creation.
3. The existing review dialog shows the captured quote and an instruction field. The user may save or cancel. Saving creates an open, page-scoped review; it never changes note text.
4. The Agent's `read_review` tool can list review summaries and fetch one record by ID, returning its quote, context, revision, and anchor. Existing `write_note`/`patch_note` tools remain the only way the Agent changes note content.
5. A review can be marked applied only after the same Agent run successfully changes that review's target note page. A no-op write, failed write, unrelated page write, or user-only status action cannot mark it applied. Users may still dismiss or delete reviews.

## Data Contract

- `side="note"`, `kind="selection"`, `scope="page"`, and the one-based current page.
- `quote` is the exact selected Markdown source and must be non-empty, non-whitespace, and at most 100,000 UTF-16 code units.
- `context` is the complete page Markdown and must be at most 1,000,000 UTF-16 code units. Oversized data is rejected visibly rather than silently truncated.
- `revision` is the SHA-256 revision of `context` as defined by `PageNotes.revision`.
- `anchor` is `{ "coordinateSystem": "markdown-source-utf16", "start": <start>, "end": <end> }`; `0 <= start < end <= context.length`, and `context.substring(start, end) == quote`.
- The SAF adapter preserves `quote`, `context`, `revision`, and `anchor` using the existing `ReviewCreate` fields. Unknown review fields remain untouched.
- Agent review reads support optional `id`. A list read returns at most 20 records, each with id/side/kind/page/status/revision and instruction/quote previews capped at 160/320 UTF-16 code units with an explicit truncation marker; it omits context/anchor. An ID read returns the full stored record, including quote/context/anchor, for that one record.
- Agent application authorization is scoped to one handler/Agent run and records only a successful note write whose resulting page revision differs from its expected revision. The write's expected revision must equal the review's captured revision, and the target page must match the review page.

## Acceptance (EARS)

- When the current editable note has a valid non-empty selection, the app shall enable `Selected text to AI annotation`; without a valid selection or write access, it shall keep the action disabled.
- When the user invokes the action with unsaved note text, the app shall save against the existing expected revision before showing the review draft, and shall not create a review if that save conflicts or fails.
- When the user saves a selection review, the app shall persist the exact quote, full page context, page revision, and matching UTF-16 anchor as an open `kind=selection` record without modifying the note.
- When selection bounds, quote, or context exceed the contract, the app shall report an error and shall not write a partial review.
- When the Agent lists or reads a review, the tool shall return enough record metadata to identify and apply the selected request; ID reads shall include quote, context, revision, and anchor.
- When an Agent requests `status=applied`, the tool shall accept it only if that same run has already changed the review's target note page through a successful `write_note` or `patch_note` whose `expectedRevision` equals the review revision; otherwise it shall return a tool failure.
- When the user dismisses or deletes a review, the app shall not modify its note page.

## Verification

- JVM tests cover valid/invalid selection bounds, whitespace-only selections, exact UTF-16 offsets including supplementary characters, quote/context limits, and review-apply authorization for changed, unchanged, failed, and wrong-page writes.
- Instrumentation tests cover native EditText selection reporting, menu enablement, quote display, cancel-without-write, and a real SAF-provider review JSON round trip.
- Agent tests cover list summaries, ID detail reads, rich selection metadata, and rejection of premature `applied` status.
- Run the full Android JVM suite, app/test APK compilation, and `assembleDebug`; run focused instrumentation tests on API 34 and API 35 16 KB AVDs when available.

## Implementation Tasks

- [ ] Add a pure selection-draft model and boundary validation.
- [ ] Track note-editor selection and wire the existing menu action to the review dialog after revision-checked note save.
- [ ] Extend SAF review creation/list rendering to preserve and display selection fields.
- [ ] Return selection details through Agent review reads and guard applied status using same-run changed-page evidence; remove manual apply status control.
- [ ] Add JVM, Compose/AndroidView, SAF-provider, and Agent regression coverage; build and run focused AVD instrumentation.
