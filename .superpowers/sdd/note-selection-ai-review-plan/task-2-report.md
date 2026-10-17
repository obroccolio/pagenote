# Task 2 Report: SAF Review Persistence

## Scope

Implemented Task 2 only. `ProjectReviewStore` now accepts `ReviewCreate`, persists quote/context/revision/anchor, validates desktop-compatible field limits before touching SAF output, and finds records by exact ID. Existing records remain raw `JsonObject` values while a new record is appended, preserving unknown fields. The existing create overload remains and delegates to the new API.

Instrumentation coverage adds a persisted JSON round trip, exact-ID lookup, unknown-field preservation, and rejection of oversized quote, context, and anchor values without provider writes. The test provider exposes `ai-reviews.json` and counts write opens.

## TDD Evidence

Before production edits, ran:

```text
./gradlew :app:compileDebugAndroidTestKotlin --offline
```

RED: exit code 1 at `ExistingProjectSafRoundTripTest.kt`: the new `create(ReviewCreate)` calls resolved only to the old String-argument overload, and `find` was unresolved. The Java test provider and test source compiled far enough to report those intended missing-store APIs.

After implementing the store API, the same compile command passed (`BUILD SUCCESSFUL`). The focused instrumentation run then passed:

```text
./gradlew :app:connectedDebugAndroidTest --offline -Pandroid.testInstrumentationRunnerArguments.class=io.pagenote.android.ExistingProjectSafRoundTripTest
```

All 6 tests passed on each connected Android 15 emulator: `Pixel_Tablet` (4 KB pages) and `Pixel_Tablet_16KB` (16 KB pages). This includes the two pre-existing SAF round-trip tests and the four new review persistence/rejection tests.

After the small test-helper cleanup, the final focused invocation also returned exit code 0 and `BUILD SUCCESSFUL`. Its per-device XML reports `tests=6`, `failures=0`, and `errors=0` on both AVDs. The run had completed when the stop signal was delivered, so it was not blocked on an AVD. The connected task compiled the Java/Kotlin instrumentation sources and produced `app-debug-androidTest.apk`.

## Review Notes

- The rejected-input assertions compare both the review JSON bytes and provider write count before and after the call.
- Quote/context lengths use Kotlin UTF-16 code-unit length. Anchor size uses serialized JSON length, matching the desktop store limit.
- No push or public binary release was performed.
- API 34 and physical DocumentsUI/storage-provider behavior were not part of this focused run; the two available connected API 35 AVDs were used.

## Review Fix Round: Localized Validation Feedback

The review follow-up traced raw validation text to the review-create catch in `ReviewListDialog`, which previously passed every exception message into the save-failure string. The create boundary now maps `IllegalArgumentException` to a localized invalid-review message in English and Simplified Chinese; other exceptions retain the existing detailed `review_save_failed` formatting.

TDD evidence:

- Added `ReviewUiErrorMessageTest` first. The initial `:app:compileDebugAndroidTestKotlin --offline` run failed as expected because the new mapper and resource ID were absent.
- The first implementation compile exposed a missing `android.content.Context` import in `MainActivity.kt`; after adding it, `:app:compileDebugAndroidTestKotlin --offline` passed.
- `:app:connectedDebugAndroidTest --offline -Pandroid.testInstrumentationRunnerArguments.class=io.pagenote.android.ReviewUiErrorMessageTest` passed both tests on `Pixel_Tablet` (Android 15, 4 KB pages) and `Pixel_Tablet_16KB` (Android 15, 16 KB pages). The tests verify localized validation output does not expose the raw limit message and that provider/I/O details remain visible.
- The connected run compiled the Kotlin/Java instrumentation sources and assembled both app and androidTest APKs.

This is a separate local fix commit; no push or public binary release was performed.
