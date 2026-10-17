# Android Reader OCR Selection Implementation Plan

> **For agentic workers:** Use the `superpowers:subagent-driven-development` workflow task by task. Each task has a failing-test-first step, verification command, and commit boundary.

**Goal:** Add transformed-page region selection to the Android PDF reader, persist offline OCR results in the opened pagenote project, and retain the existing manual full-page OCR action.

**Architecture:** Keep viewport geometry and prepared-manifest policy platform-independent where practical. Put SAF document access and Compose gestures in the Android app module. The app maps a drag to `NormalizedOcrRegion`, checks the project cache, then invokes the existing `OfflineOcrEngine` only on a miss.

**Tech Stack:** Kotlin, Jetpack Compose, Android Storage Access Framework, `PdfRenderer`, Kotlinx Serialization JSON, existing PP-OCRv6 ONNX engine.

**Spec:** `android/docs/spec/features/reader-ocr-selection.md`; contracts: `android/docs/spec/contracts/prepared-manifest.schema.json` and the existing `OcrResult` model.

## Global Constraints

- Keep Android SPECs and implementation plans under `android/docs/spec/`; do not move them to root `docs/`.
- The existing full-page OCR action remains explicit and unchanged; do not add automatic whole-document OCR or network OCR.
- Region coordinates use the original page's normalized 0..1000 coordinate space.
- Keep the displayed PDF bitmap separate from the bitmap passed to OCR; recycle OCR-owned bitmaps in `finally`.
- Persist entries only under `src/prepared/<page>-<key-prefix>/` and write `manifest.json` last as the completion marker.
- Cache only `ok` and `partial` results; report SAF/hash failures without claiming persistence.
- Keep `OcrResult`, the 16-megapixel render cap, and prepared-manifest version 2 compatible.
- Every pushed feature commit includes an Unreleased `CHANGELOG.md` entry; push only to `fork/codex/android-port`, with eventual PR target `beta`.

## Review Focus

- Non-square pages with letterbox margins under fit-width and zoom/pan: inverse mapping must not skew or select outside the PDF.
- Reverse drags and endpoints outside the page: normalize direction and clip the endpoint, but reject a start outside the page.
- SAF provider rejects directory/file creation or truncation: recognition still succeeds, no incomplete cache is treated as a hit, and the UI reports the result was not cached.
- Inference failure/cancellation or page changes while work is active: preserve the displayed page, recycle temporary images, and never associate a result with another page.

## File Map

- `android/app/src/main/java/io/pagenote/android/PdfViewportTransform.kt`: viewport bounds and pure screen-to-page selection mapping.
- `android/core-reader/src/main/java/io/pagenote/core/reader/PreparedManifest.kt`: complete version-2 manifest model and validation.
- `android/core-reader/src/main/java/io/pagenote/core/reader/PreparedManifestCodec.kt`: JSON representation of the prepared-manifest contract.
- `android/app/src/main/java/io/pagenote/android/PreparedDocumentAccess.kt`: fakeable SAF document-provider boundary.
- `android/app/src/main/java/io/pagenote/android/ProjectPreparedCache.kt`: SAF tree adapter and prepared-region cache lifecycle.
- `android/feature-ocr/src/main/java/io/pagenote/feature/ocr/OcrResultJsonCodec.kt`: stable JSON round-trip for the existing `OcrResult` fields.
- `android/app/src/main/java/io/pagenote/android/ReaderOcrSelection.kt`: Compose selection overlay, action state, and input locking.
- `android/app/src/main/java/io/pagenote/android/MainActivity.kt`: wire toolbar actions, viewport selection, cache lookup, OCR, and result lifecycle.
- `android/app/src/main/res/values/strings.xml` and `values-zh-rCN/strings.xml`: localized selection, validation, progress, and cache warnings.

---

### Task 1: Viewport Selection Geometry

**Files:**
- Modify: `android/app/src/main/java/io/pagenote/android/PdfViewportTransform.kt`
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt` (move `PdfViewportBounds` from its private local declaration to the shared geometry file)
- Test: `android/app/src/test/java/io/pagenote/android/PdfViewportTransformTest.kt`

**Interfaces:**
- Consumes: `PdfViewportTransform(scale, translationX, translationY)` and `NormalizedOcrRegion(left, top, right, bottom)`.
- Produces: `PdfViewportBounds(viewportWidthPx, viewportHeightPx, pageWidthPx, pageHeightPx)`, `PdfSelectionPoint(x, y)`, and `mapPdfSelectionToOcrRegion(start, end, bounds, transform): NormalizedOcrRegion?`.

- [ ] **Step 1: Add failing tests** named `fitWidthLetterbox_mapsToFullPageCoordinates`, `zoomPan_mapsSelectionThroughInverseTransform`, `startInLetterbox_isRejected`, `dragEndOutsidePage_isClipped`, `reverseDrag_isNormalized`, and `invalidBounds_areRejected`. Pin a fitted non-square page (`1000x800` viewport, `600x800` page, drag `(300,200)` to `(700,600)` -> `(166,250,834,750)`) and a zoomed/panned page (scale `2`, translation `(50,-100)`, drag `(350,200)` to `(750,600)` -> `(333,437,667,688)`).
- [ ] **Step 2: Run tests and verify RED.** Run `cd android && ./gradlew :app:testDebugUnitTest --tests io.pagenote.android.PdfViewportTransformTest --offline`; expect unresolved mapper symbols or failing new cases before implementation.
- [ ] **Step 3: Implement the pure mapper.** Invert scale/translation around the measured viewport/page centers, reject starts outside the page, clip end coordinates, then floor left/top and ceil right/bottom into 0..1000 integer bounds.
- [ ] **Step 4: Run focused and module tests.** The focused mapper tests and `:app:testDebugUnitTest` must pass.
- [ ] **Step 5: Commit.** `feat(android): map transformed page selections`; include an Unreleased changelog line.

### Task 2: Prepared Manifest and OCR Result Codecs

**Files:**
- Modify: `android/core-reader/build.gradle.kts`
- Modify: `android/core-reader/src/main/java/io/pagenote/core/reader/PreparedManifest.kt`
- Create: `android/core-reader/src/main/java/io/pagenote/core/reader/PreparedManifestCodec.kt`
- Modify: `android/feature-ocr/build.gradle.kts`
- Create: `android/feature-ocr/src/main/java/io/pagenote/feature/ocr/OcrResultJsonCodec.kt`
- Test: `android/core-reader/src/test/java/io/pagenote/core/reader/PreparedManifestCodecTest.kt`
- Test: `android/feature-ocr/src/test/java/io/pagenote/feature/ocr/OcrResultJsonCodecTest.kt`
- Test: `android/app/src/test/java/io/pagenote/android/PreparedManifestApiVisibilityTest.kt` (verify the app can consume the manifest's public JSON model through the core-reader module boundary)

**Interfaces:**
- Consumes: `PageRegion`, `PreparedCache.VERSION`, and the existing `OcrResult`/`OcrBlock` models.
- Produces: `PreparedManifestCodec.encode(manifest): String`, `PreparedManifestCodec.decode(json): PreparedManifest`, and `OcrResultJsonCodec.encode(result): String` / `decode(json): OcrResult`.
- `PreparedManifest` carries `originalPreserved: Boolean`, the five non-negative character/block/table counts from the schema, `ocr: JsonObject`, `files: List<String>`, and `visuals: JsonArray`, in addition to its existing identity, region, dimensions, scale, warnings, and visual-review fields.

- [ ] **Step 1: Add failing tests** named `versionTwoManifest_roundTripsEveryContractField`, `manifestRejectsInvalidIdentityRegionAndCounts`, `ocrResultRoundTripsCoordinatesWarningsAndOwnership`, and `unknownManifestFieldsAreIgnored`. Round-trip all schema fields for a version-2 PDF region entry; reject invalid page/revision/region/counts; preserve all `OcrResult` doubles, status, warnings and `imagePreserved`. Add an app-module consumer test that constructs a manifest with a `JsonObject` and reads its JSON fields, proving the core-reader public dependency is visible transitively.
- [ ] **Step 2: Run tests and verify RED.** Run the two focused Gradle test tasks; expect missing codec symbols or missing manifest fields.
- [ ] **Step 3: Implement the complete version-2 model and explicit JSON codecs.** Add the existing project version `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3` as an `api` dependency of `core-reader`, because its public manifest model exposes `JsonObject` and `JsonArray` (no serialization compiler plugin; codecs build JSON values explicitly); keep `ocr` and `visuals` JSON data extensible, validate required manifest fields and safe project-relative file names, and make `PreparedManifestStore.isHit(cached, expected)` compare document id, page, source revision, region, cache key, and version.
- [ ] **Step 4: Run focused and module tests.** `:core-reader:testDebugUnitTest :feature-ocr:testDebugUnitTest :app:testDebugUnitTest` must pass.
- [ ] **Step 5: Commit.** `feat(android): model prepared OCR manifests`; include an Unreleased changelog line.

### Task 3: SAF Project Region Cache

**Files:**
- Create: `android/app/src/main/java/io/pagenote/android/PreparedDocumentAccess.kt`
- Create: `android/app/src/main/java/io/pagenote/android/ProjectPreparedCache.kt`
- Test: `android/app/src/test/java/io/pagenote/android/ProjectPreparedCacheTest.kt`

**Interfaces:**
- Consumes: Task 2 codecs and manifest policy; `ProjectDocumentOpener`'s `sourceDirectoryUri`; PNG bytes and an `OcrResult`.
- Produces: `PreparedRegionCacheStore.read(srcDirectoryUri, documentId, page, sourceRevision, region, engine): PreparedOcrCacheEntry?` and `write(srcDirectoryUri, manifest, result, pagePng, regionPng): PreparedOcrCacheWriteResult`.
- `PreparedOcrCacheWriteResult` distinguishes `Saved` from `NotSaved(reason)`; cache I/O failure must not discard an otherwise valid recognition result.
- `PreparedDocumentAccess` exposes `findChild`, `createDirectory`, `createFile`, `delete`, `openInput`, and `openOutput`; production uses `ContentResolver`/`DocumentsContract`, tests use an in-memory fake. `PreparedImageValidator` is injected so local tests can reject malformed images and the Android adapter validates with `BitmapFactory`.

- [ ] **Step 1: Add failing tests** named `emptyTreeIsMiss`, `savedEntryRoundTripsResultAndImages`, `changedDocumentPageSourceRegionEngineOrVersionIsMiss`, `malformedManifestOrMissingFileIsMiss`, `invalidImageOrResultIsMiss`, `writeFailureReturnsNotSaved`, and `manifestIsRequiredAsCompletionMarker`, using an in-memory `PreparedDocumentAccess` and injected image validator.
- [ ] **Step 2: Run tests and verify RED.** Run `cd android && ./gradlew :app:testDebugUnitTest --tests io.pagenote.android.ProjectPreparedCacheTest --offline`; expect missing store/API or failing cache cases.
- [ ] **Step 3: Implement SAF-backed storage.** Create/find `src/prepared/<page>-<key-prefix>` through `DocumentsContract`; read and validate every required file; encode `manifest.json`, `page.png`, `region.png`, `ocr.txt`, and `blocks.json`; remove an old completion manifest before replacement and write the new manifest last. Add the `ContentResolver` adapter and fakeable document-access interface.
- [ ] **Step 4: Run cache, codec, and app tests.** Run `:app:testDebugUnitTest :core-reader:testDebugUnitTest :feature-ocr:testDebugUnitTest :app:assembleDebug --offline`.
- [ ] **Step 5: Commit.** `feat(android): cache region OCR in SAF projects`; include an Unreleased changelog line.

### Task 4: Reader Selection and OCR Integration

**Files:**
- Create: `android/app/src/main/java/io/pagenote/android/ReaderOcrSelection.kt`
- Modify: `android/app/build.gradle.kts` (Compose UI instrumentation-test dependencies)
- Modify: `android/app/src/main/java/io/pagenote/android/MainActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-zh-rCN/strings.xml`
- Test: `android/app/src/test/java/io/pagenote/android/ReaderOcrSelectionTest.kt`
- Test: `android/app/src/androidTest/java/io/pagenote/android/ReaderOcrSelectionTest.kt`

**Interfaces:**
- Consumes: Task 1 mapper, Task 3 cache store, the existing `OfflineOcrEngine.recognize(bitmap, region)` and `OcrResultDialog`.
- Produces: a native selection overlay with `Region OCR`, `Recognize`, and `Cancel` actions; result lifecycle that preserves page/source identity and restores normal input after result dismissal.

- [ ] **Step 1: Add failing interaction/policy tests.** Add Compose UI test dependencies and instrumentation cases named `regionActionEntersSelectionAndCancelRestoresInput`, `dragSelectsRegionWithoutAddingPenStrokeOrZoom`, and `recognizeUsesCurrentSelectionAndResultDismissalRestoresInput`. Verify selection and OCR lock pen/pan/zoom/navigation, a cache hit bypasses inference, a write failure adds a visible not-cached warning without hiding OCR output, and closing the result restores input. Stylus event injection remains device acceptance.
- [ ] **Step 2: Run tests and verify RED.** Run the focused app unit test and `:app:compileDebugAndroidTestKotlin --offline`; expect missing selection composable/state before implementation.
- [ ] **Step 3: Implement Compose integration.** Add touch/stylus rectangle selection on the displayed transformed page; route selection gestures before the pen canvas; render a separate full-page OCR bitmap and region PNG; hash the active PDF source on `Dispatchers.IO`; check cache before inference; lock page/input while selecting or showing/running OCR; keep the existing full-page action unchanged; localize all new controls and warnings.
- [ ] **Step 4: Run Android verification.** Run `cd android && ./gradlew testDebugUnitTest :feature-ocr:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin :app:assembleDebug --offline --rerun-tasks`. On an available Android device/emulator, verify finger/stylus selection, cache hit, offline recognition, read-only fallback, and input restoration.
- [ ] **Step 5: Commit.** `feat(android): add reader region OCR selection`; include an Unreleased changelog line and mark the completed 9.2 subtask in `android/docs/spec/tasks.md`.

**Dependencies:** Task 1 and Task 2 are independent. Task 3 depends on Task 2. Task 4 depends on Tasks 1–3.

**Review Focus:** Explicitly inspect the four input/failure classes listed in the feature SPEC's test plan; device-only acceptance remains a separate completion requirement and must not be claimed when not run.
