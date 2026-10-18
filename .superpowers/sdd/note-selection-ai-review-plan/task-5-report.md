# Task 5 Report: Build and Device Verification

## Build and JVM Results

- Requested command: `./gradlew :app:testDebugUnitTest :agent:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug --offline` from `android/`. Result: `BUILD SUCCESSFUL`. The two JVM tasks and Android test Kotlin compilation were up-to-date; `assembleDebug` completed.
- Fresh JVM rerun: `./gradlew :app:testDebugUnitTest :agent:testDebugUnitTest --offline --rerun-tasks`. Result: `BUILD SUCCESSFUL`; app 171/171 and Agent 56/56 tests, zero failures, errors, or skips.

## Focused Instrumentation

Command:

```sh
./gradlew :app:connectedDebugAndroidTest --offline '-Pandroid.testInstrumentationRunnerArguments.class=io.pagenote.android.NoteSelectionReviewUiTest,io.pagenote.android.ExistingProjectSafRoundTripTest,io.pagenote.android.ReviewUiErrorMessageTest,io.pagenote.android.ReviewHandoffUiTest'
```

The classes contain 7, 6, 2, and 5 tests respectively (20 total). Per-device result XML reports 20 tests, zero failures, zero errors, and zero skips for each:

| AVD | Observed SDK | Observed page size | Result |
| --- | --- | --- | --- |
| `Pixel_Tablet_API34` | API 34 | 4096 bytes (4 KB) | 20/20 passed |
| `Pixel_Tablet` | API 35 | 4096 bytes (4 KB) | 20/20 passed |
| `Pixel_Tablet_16KB` | API 35 | 16384 bytes (16 KB) | 20/20 passed |

API 35 4 KB and 16 KB AVDs were already connected. `Pixel_Tablet_API34` was started for this run and stopped afterward with `adb emu kill`; the two pre-existing API 35 AVDs were left running. No command was blocked.

## Limits

- Full Activity open-project and dirty-save failure interactions were not end-to-end tested.
- Source-PDF and Office text selection are not implemented. Physical-device and physical-pen behavior were not tested.
- Generic SAF has no cross-process compare-and-swap; serialization is app-process only.
- Unsaved-draft process-reclamation recovery is already implemented and tested separately under task 15, including an API 34 SIGKILL task-recovery smoke test. This is separate from Task 5 selection-review verification.

No code was changed. No public Android binary was released and nothing was pushed.
