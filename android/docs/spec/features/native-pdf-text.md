# Native PDF Text

## Goal

Extract selectable text and page-relative geometric blocks from Android PDF pages when the platform supports native text extraction. Report an explicit OCR fallback state on older supported Android versions.

## Behavior

- On API 35 and later, read text with `PdfRenderer.Page.getTextContents()`.
- Keep every non-empty platform text item in page full text, including items whose bounds list is empty (unbounded text).
- Emit one geometric block per text item with usable bounds. The block carries the text item once; when the platform provides multiple line rectangles, use their clipped union as the block bounds.
- Treat bounds as PDF points. Clip each rectangle to the page, omit non-finite, zero-area, inverted, or wholly out-of-page rectangles, and omit a block when no valid area remains.
- Normalize left, top, width, and height independently against the page width and height into the existing 0..1000 coordinate space. This must work for non-square pages.
- On API 34 (the Android 14 minimum), return an unavailable result explicitly marked for offline OCR fallback. Do not report native extraction as successful and do not attempt `getTextContents()`.
- Close each opened `PdfRenderer.Page` on success and failure. Keep the current 16-megapixel rendering cap unchanged.

## Boundary

`core-reader` owns platform-independent text result types, bounds normalization, clipping, and API support policy. `PdfDocumentSession` owns page access and calls Android text APIs only inside the API-35 guarded path. This slice does not wire extraction or OCR into `MainActivity`.

## Verification

- JVM tests cover independent x/y normalization on non-square pages, clipping and invalid bounds, empty and unbounded text, and unsupported API fallback policy.
- Android app compilation verifies the API-35 extraction path while retaining the existing renderer behavior.
