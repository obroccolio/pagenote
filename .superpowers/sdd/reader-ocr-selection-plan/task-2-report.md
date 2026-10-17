# Task 2: Prepared Manifest and OCR Result Codecs

## Status

Implemented and committed locally on `codex/android-port`; no push performed. The implementation follows the Task 2 brief and is limited to its Android files, one Unreleased changelog entry, and this report.

## Implementation

- Added `PreparedManifest` v2 metadata: `originalPreserved`, native/OCR/total character counts, block/table counts, extensible `ocr` JSON object, project-relative file list, and extensible `visuals` JSON array.
- Added `PreparedManifestCodec` with explicit JSON value construction and strict required-field/type parsing. Unknown object members are ignored. Optional `originalPreserved` and count fields default to `true` and `0` respectively, matching their optional status in `android/docs/spec/contracts/prepared-manifest.schema.json`; encoding always writes every field.
- Kept `ocr` and `visuals` as JSON values rather than constraining their nested structures. Validates version, identity, page, revision, kind, normalized region, dimensions, scale, non-negative counts, required visual review, and safe relative file paths (no absolute path, empty/dot/parent segments, or NUL).
- Added `OcrResultJsonCodec`, explicitly encoding and decoding engine, status, all block text/coordinate/confidence doubles, warnings, and `imagePreserved`, and applying the existing `OcrResultPolicy` on both directions.
- Added `kotlinx-serialization-json:1.7.3` to `core-reader` and `feature-ocr`; no serialization compiler plugin was added.
- Added expected-manifest cache matching across version, document id, page, source revision, region, and cache key. Retained the existing revision/key overload for compatibility.
- Added one bilingual Unreleased `CHANGELOG.md` entry.

## TDD Evidence

Added the four requested test names before production code:

- `versionTwoManifest_roundTripsEveryContractField`
- `manifestRejectsInvalidIdentityRegionAndCounts`
- `ocrResultRoundTripsCoordinatesWarningsAndOwnership`
- `unknownManifestFieldsAreIgnored`

The first focused run was intentionally RED: `:core-reader:compileDebugUnitTestKotlin` reported missing `PreparedManifestCodec`, missing manifest constructor fields, and unavailable JSON dependency symbols; `:feature-ocr:compileDebugUnitTestKotlin` reported the missing `OcrResultJsonCodec`. This was the expected missing-implementation failure.

After implementation and test hardening, ran:

```text
./gradlew :core-reader:testDebugUnitTest :feature-ocr:testDebugUnitTest
BUILD SUCCESSFUL
30 actionable tasks: 8 executed, 22 up-to-date
```

The tests now cover every manifest field in a version-2 PDF entry, invalid page/document/revision/region/counts and unsafe file paths, unknown-field tolerance, exact hit identity changes for page/document/region, and round-trip preservation of OCR doubles, status, warnings, and image ownership.

## Concerns

- Gradle emitted existing Kotlin `kotlinOptions` deprecation and Gradle 10 compatibility warnings; the test tasks completed successfully.
- Four pre-existing untracked paths under `build/` and `tests/` were left untouched.

## Commit

`b89db22 feat(android): model prepared OCR manifests`

## Task 2 Fix Round 1: Windows Drive Paths

Added regression assertions rejecting both `C:\\outside.png` and `C:/outside.png`. `validateProjectRelativeFile` now rejects an ASCII drive-letter prefix followed by a colon before splitting and validating path segments.

RED command:

```text
./gradlew :core-reader:testDebugUnitTest --offline --tests io.pagenote.core.reader.PreparedManifestCodecTest.manifestRejectsInvalidIdentityRegionAndCounts
```

Observed expected failure before the production fix:

```text
PreparedManifestCodecTest > manifestRejectsInvalidIdentityRegionAndCounts FAILED
java.lang.AssertionError at PreparedManifestCodecTest.kt:80
1 test completed, 1 failed
BUILD FAILED
```

Focused GREEN command, run after the production fix:

```text
./gradlew :core-reader:testDebugUnitTest --offline --tests io.pagenote.core.reader.PreparedManifestCodecTest.manifestRejectsInvalidIdentityRegionAndCounts
BUILD SUCCESSFUL in 2s
15 actionable tasks: 5 executed, 10 up-to-date
```

Full scoped verification:

```text
./gradlew :core-reader:testDebugUnitTest :feature-ocr:testDebugUnitTest --offline
BUILD SUCCESSFUL in 2s
30 actionable tasks: 4 executed, 26 up-to-date
```

Both successful runs emitted only the existing Gradle 10 compatibility warning. No app test was run because the fix changes only the private path validator and its core-reader test; no shared API changed.

## Task 2 Fix Round 2: App JSON API Visibility

Added `android/app/src/test/java/io/pagenote/android/PreparedManifestApiVisibilityTest.kt`. The test constructs `PreparedManifest` with `JsonObject`/`JsonArray` values and reads the public JSON fields from the app module. Changed `core-reader`'s `kotlinx-serialization-json` dependency from `implementation` to `api`, retaining version `1.7.3`.

RED command before changing the dependency configuration:

```text
./gradlew :app:testDebugUnitTest --offline --tests io.pagenote.android.PreparedManifestApiVisibilityTest
```

Observed expected app compile failure:

```text
Task :app:compileDebugUnitTestKotlin FAILED
Unresolved reference 'serialization' for the kotlinx.serialization.json imports.
Cannot access class 'kotlinx.serialization.json.JsonObject' / 'JsonArray' from the public PreparedManifest constructor and fields.
BUILD FAILED
```

Passing command after changing `implementation` to `api` (the filtered app consumer test runs first, followed by both module suites and the complete app unit-test suite):

```text
./gradlew :app:testDebugUnitTest --tests io.pagenote.android.PreparedManifestApiVisibilityTest :core-reader:testDebugUnitTest :feature-ocr:testDebugUnitTest :app:testDebugUnitTest --offline
BUILD SUCCESSFUL in 3s
130 actionable tasks: 6 executed, 124 up-to-date
```

The successful run emitted the existing Gradle 10 compatibility warning. No unrelated paths were modified.

Fix round 2 commit: `4deb79c9b37382c592c0ad2bf66d91222e0a6b72 fix(android): expose manifest JSON API`.
