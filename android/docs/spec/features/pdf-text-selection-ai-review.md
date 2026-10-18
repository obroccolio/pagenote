# PDF Text Selection to AI Review

## Goal

On the supported Android 14 Xiaomi Pad 5 Pro, let a user drag-select searchable PDF text and save it as a page-scoped AI review that remains compatible with the desktop review record format.

## Scope

- Keep Android `PdfRenderer` as the displayed-page renderer. Use `com.tom-roush:pdfbox-android:2.0.27.0` only to read ordered text positions from the app-private PDF snapshot; initialize its resource loader once before extraction.
- Do not add a second native PDF ABI or replace the current renderer. Keep the existing offline OCR path for scanned pages; region OCR remains the supported selection action when no searchable text is available.
- Support touch and stylus drag selection on PDF text. Selection mode locks page zoom/pan, ink input, page changes, and annotation gestures until the user saves or cancels.
- Source-PDF review records belong in `ai-reviews.json`; normal visual annotations remain in `annotations.json`.
- Markdown note selection keeps its existing behavior. Office selection is out of scope.

## Interaction

1. The user enters PDF text-selection mode from the AI annotations menu.
2. A one-finger or stylus drag selects the ordered text range between the nearest start/end glyphs. The selected glyphs are highlighted over the rendered PDF. A tap or empty range does not create a draft.
3. The user chooses the existing selected-text review command, reviews the exact quote, and enters an instruction. If the current note page is dirty, it must save against its captured revision before the review draft opens.
4. Save creates an open page-scoped document selection review. A note revision change after selection capture rejects the save and preserves the project file.
5. If the PDF page has no selectable text, show a clear message and leave region OCR available; do not create fake character anchors from OCR rectangles.

## Data Contract

- `side="document"`, `kind="selection"`, `scope="page"`, and the one-based PDF page number.
- `context` is the complete text extracted for that PDF page. `revision` is the lowercase SHA-256 of the context's UTF-8 bytes.
- `quote` is the exact non-whitespace substring represented by the selected ordered glyph range; existing review limits apply (quote <= 100,000 UTF-16 units, context <= 1,000,000 UTF-16 units).
- `anchor` uses the desktop-compatible `coordinateSystem="width-1000"`, a `shape.type="rect"` bounding the selected glyphs, and `text=quote`. Its rectangle scales both page axes against the displayed page width. Do not write PDFBox offsets as desktop `textStart`/`textEnd`, because those offsets are not PDF.js text-layer offsets.
- Add optional top-level `targetNoteRevision` to document selection reviews. It captures the note page revision when selection is confirmed, allowing same-run Agent writes to mark the source review applied without confusing PDF text revision with note revision. Note-side reviews retain their existing `revision` authorization behavior.
- Existing readers preserve this optional field as an unknown JSON field; no project manifest/schema version change is required.

## Acceptance

- On API 34, a searchable PDF page produces ordered text with usable glyph bounds from the local snapshot; the original PDF displayed by `PdfRenderer` is unchanged.
- Dragging in either direction selects the same logical text range and draws highlights at the corresponding page coordinates after viewport zoom/pan transforms.
- Saving a selection review persists its quote, full-page context, source revision, width-1000 shape anchor, and target note revision. A changed note revision prevents the write.
- Agent `applied` authorization for a document-side review matches a changed note write against `targetNoteRevision`; legacy note-side authorization remains unchanged.
- Scanned pages without text remain on the existing OCR-region flow and never claim character-level selection.

## Verification

- One JVM test covers ordered/reversed selection, exact quote boundaries, and width-1000 bounding-rectangle mapping.
- One API 34 tablet instrumentation workflow uses the real reader and local PDF snapshot to select text, save the document review, and verify the stored fields plus stale-note rejection.
- Run affected JVM tests and `:app:assembleDebug`; do not run the full instrumentation suite or any other device/API matrix. Xiaomi Pad 5 Pro remains the only hardware target.

## Verification Limits

- PDFBox text order can differ from PDF.js for unusual multi-column, rotated, or malformed text. The saved quote/context are internally consistent; desktop may use the shape anchor if its text layer cannot match the quote.
- Scanned-only PDFs require OCR region selection; per-character selection is not promised without an extractable text layer.
