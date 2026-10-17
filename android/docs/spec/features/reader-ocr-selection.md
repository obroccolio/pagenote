# Reader OCR Selection and Cache

## Scope

Extend the Android PDF reader's explicit OCR actions with manual region selection and project-backed result caching. This Android-only SPEC belongs under `android/docs/spec/`. The existing full-page OCR action remains available and manual. This feature does not add automatic whole-document OCR or model/network calls.

## Interaction

- The PDF reader exposes a distinct `Region OCR` action. Activating it enters selection mode for the current page; it does not start recognition.
- While selection mode is active, one-finger or stylus drag draws a rectangle over the rendered PDF page. The stylus is treated as a selector only in this explicit mode; pen/highlighter input and pinch-to-zoom are suspended until the user cancels or starts recognition. Once recognition starts, page gestures stay locked until the result surface closes or the request is cancelled, then normal input resumes.
- A drag may start only on the rendered page. Its end point is clipped to the page bounds. A drag wholly outside the page, or one that quantizes to zero width or height, is rejected without calling OCR.
- A new drag replaces the current rectangle. `Recognize` starts OCR for that rectangle; `Cancel` exits selection mode and restores normal pan/zoom and pen input.
- The selection overlay follows the page through fit, zoom, and pan. The saved region uses the original page's normalized 0..1000 coordinate space, not screen coordinates.
- While OCR is running, page navigation, project switching, and page gestures remain disabled. The result is associated with the page and source revision captured when the request began.
- Successful and partial results are shown through the existing OCR result surface. Low confidence, partial status, and persistence failures remain explicit warnings. Failed recognition is not shown as a successful empty result and is not cached.

## Coordinate Contract

- The viewport-to-page mapper inverts the current `PdfViewportTransform` using measured viewport/page bounds, scale, and translation.
- The resulting rectangle is clipped to `[0, 1000]` on both axes. Left/top use floor and right/bottom use ceil to avoid losing selected edge pixels; the stored region has positive integer width and height.
- Blank letterbox margins are not selectable as page content. Non-square pages, zoomed/panned pages, and selections touching each page edge preserve independent x/y scaling.
- The OCR engine receives the original rendered page bitmap plus `NormalizedOcrRegion`; its existing ownership contract remains unchanged. The reader renders an OCR-owned bitmap separately from the bitmap displayed by the PDF view and recycles it in `finally`.

## Cache Contract

- Cache entries live under `src/prepared/<page>-<cacheKey-prefix>/` in the opened pagenote project.
- `sourceRevision` is the lowercase SHA-256 of the exact source PDF snapshot used by the active `PdfDocumentSession`. Compute it on an IO dispatcher, reuse it only for that renderer/session, and clear it when the opened source changes; if hashing or SAF writes fail, OCR may still complete but the result is explicitly reported as not cached.
- The cache key is `PreparedCache.cacheKey(sourceRevision, "region-ocr", region, engine)`, with the page encoded by `PreparedCache.folderName(page, key)`. The cache key therefore changes when the source, region, engine, or prepared-cache version changes.
- Each complete cache entry contains `manifest.json`, `page.png`, `region.png`, `ocr.txt`, and `blocks.json`. `manifest.json` conforms to `contracts/prepared-manifest.schema.json`, identifies the document/page/source revision/region/key, records actual bitmap dimensions and scale, retains warnings, and sets `visualReviewRequired` to `true`.
- The saved manifest warnings are the stable, de-duplicated union of warnings already supplied by the caller and warnings produced by the OCR result; neither source may be silently dropped.
- `blocks.json` stores the existing `OcrResult` fields: engine, status, imagePreserved, warnings, and blocks with text, confidence, x, y, width, and height in full-page normalized coordinates. `ocr.txt` is `OcrResultPolicy.textOf(result)`.
- Write image and result files before `manifest.json`; the manifest is the completion marker. A cache hit requires a valid manifest with an exact document/page/source-revision/region/key match and every listed file to exist and decode. Invalid, incomplete, or stale entries are misses and must never be returned as successful results.
- SAF child enumeration distinguishes a successful query with no matching name from a failed query, including a null query cursor. Enumeration failures are I/O failures; a write must stop before changing existing entry files when it cannot determine whether the prior completion manifest exists.
- Only `ok` and `partial` OCR results may be cached. A read-only project or a provider that rejects writes still allows recognition, reports that persistence was unavailable, and does not claim a cache hit.

## Acceptance (EARS)

- When the user enters region selection, the Android reader shall preserve the current page and allow a finger or stylus drag to select only within the rendered PDF page.
- While region selection is active, the Android reader shall not add pen strokes or apply pinch-to-zoom from the selection gesture.
- When a valid rectangle is selected on a fitted, zoomed, or panned page, the Android reader shall map it to the corresponding full-page 0..1000 region with independent x/y scaling.
- When a selection is empty or outside the page, the Android reader shall show a recoverable validation message and shall not invoke the OCR engine.
- When the selected region has a valid cache hit, the Android reader shall display the cached result without running OCR again.
- When the source PDF bytes, page, region, OCR engine, or cache version differ, the Android reader shall treat the prior cache as stale and perform recognition for the current selection.
- When recognition succeeds or is partial, the Android reader shall preserve the full-page image and region image in the project cache when the SAF provider permits writes, and shall keep block coordinates in full-page normalized space.
- When cache persistence is unavailable, the Android reader shall still show the OCR result and explicitly report that it was not cached.
- When recognition fails or is cancelled, the Android reader shall not cache a successful result, shall preserve the source page, and shall release temporary bitmaps.
- While recognition is active, the Android reader shall prevent a page or project change from associating a result with the wrong source page.
- While recognition is active, the Android reader shall prevent selection, pan/zoom, and pen input; when the result surface closes or the request is cancelled, the reader shall restore the normal input mode.

## Verification

- JVM tests cover viewport-to-page conversion for fit, non-square pages, zoom, pan, letterbox margins, edge clipping, and empty selections.
- Cache tests cover exact hits, changed source/page/region/engine/version misses, malformed/incomplete entries, write failure, and serialized `OcrResult` round trips.
- Android tests compile the Compose selection integration and verify touch selection mode does not produce pen strokes or zoom gestures; stylus routing is verified on-device.
- Device acceptance uses a Chinese/English PDF in airplane mode; it checks finger and stylus selection, all four page edges, zoom/pan alignment, cache reuse, read-only SAF behavior, and restored pen input after cancel/recognition.
- The existing full-page OCR behavior, model privacy boundary, `OcrResult` contract, 16-megapixel render cap, and project format remain unchanged.
