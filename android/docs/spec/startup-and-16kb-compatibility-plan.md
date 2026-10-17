# Android Startup and 16 KB Compatibility Plan

**Goal:** Remove the reader's reproducible launch crash and package 16 KB-compatible 64-bit native libraries.

**SPEC:** `android/docs/spec/features/startup-and-16kb-compatibility.md`.

**Constraints:** Keep all Android-only planning under `android/docs/spec/`; preserve SAF picker behavior and the existing OpenCV API surface; verify actual ELF and APK alignment; include a matching Unreleased changelog entry in every pushed implementation commit.

## Task 1: Reproduce the Reader Launch Regression

- [x] Add an app instrumentation test that launches `MainActivity` and waits for the localized home screen.
- [x] Reproduce the current APK crash from device logcat: `No ActivityResultRegistryOwner` from `ReaderHome` during settings-backed composition.
- [ ] Run the instrumentation test itself. The runner compiled, but device installation returned `INSTALL_FAILED_USER_RESTRICTED` before any test executed.
- [x] Commit the test and test-dependency change with an Unreleased changelog entry.

## Task 2: Preserve the Activity Result Owner

- [x] Capture the hosting `ActivityResultRegistryOwner` before constructing the locale-specific configuration context.
- [x] Provide that owner alongside the localized `LocalContext` at the reader composition root.
- [x] Run app JVM tests, compile the instrumentation test, and build all configured APKs.
- [ ] Install and launch the built app on the connected device and verify the crash is gone; device policy currently cancels APK installation.
- [x] Commit the implementation and its changelog entry separately from the regression-test commit.

## Task 3: Replace Misaligned OpenCV Binaries

- [x] Add `android/scripts/check_16kb_apks.py` to inspect every packaged 64-bit ELF `LOAD` segment and APK ZIP alignment with the Android SDK/NDK tools.
- [x] Replace `com.quickbirdstudios:opencv:4.5.3.0` with the API-compatible official OpenCV AAR selected by inspecting its actual 64-bit ELF segments; verify the packaged C++ runtime is aligned too.
- [x] Build all configured ABI APKs and run all JVM unit-test tasks.
- [x] Inspect every resulting `arm64-v8a` and `x86_64` library with NDK `llvm-objdump -p`; all `LOAD` alignments are `>= 2**14`.
- [x] Run `python3 android/scripts/check_16kb_apks.py <apk>...` on all four APKs. ELF and SDK `zipalign -c -P 16` checks pass.
- [x] Commit the dependency and verified build change with its own Unreleased changelog entry.

## Task 4: Device Smoke Check and Sync

- [ ] Install the new ARM64 debug APK on the connected device, launch it, confirm the home screen remains visible, and open then dismiss the SAF project picker without selecting a project. `adb install -r` returned `INSTALL_FAILED_USER_RESTRICTED` and no emulator is configured.
- [x] Record that the connected device uses 4 KB pages; it does not validate 16 KB runtime behavior.
- [x] Run `testDebugUnitTest`, `assembleDebug`, and `compileDebugAndroidTestKotlin`; `connectedDebugAndroidTest` stopped before executing tests because device installation was cancelled.
- [ ] Push reviewed feature commits to `fork/codex/android-port` only after all relevant checks and ensure the pushed range includes the Unreleased changelog updates.

## Task 5: Android 15 16 KB Runtime Verification

- [x] Install the official Android 15 `google_apis_playstore_ps16k` ARM64 system image and create the dedicated `Pixel_Tablet_16KB` AVD without replacing the existing 4 KB AVDs.
- [x] Confirm `getconf PAGESIZE` reports `16384`; record the Google Play ARM64 image and API 35.
- [x] Build/install the current ARM64 debug APK and launch `MainActivity`; no startup fatal crash was observed. OCR's lazily loaded native runtime is validated separately below.
- [x] Run all 69 app instrumentation tests on the 16 KB AVD; all pass, including reader/PDF instrumentation.
- [ ] Run `feature-ocr` instrumentation on the same AVD and require successful bundled Chinese/English recognition. The module runner was missing, so the prior zero-test Gradle success was invalid; after adding the runner, seven tests are discovered but the process crashes in `libopencv_java4.so` at SVE `RDVL`. The same failure reproduces on the 4 KB API 35 AVD; root cause is unresolved.
- [ ] Verify the app can open the SAF picker and render a representative project page on the 16 KB AVD.
- [x] Record runtime results separately from ELF/ZIP alignment checks and physical-device validation; root Unreleased changelog is updated before pushing.

**Expected:** The Android 15 16 KB AVD reports a 16,384-byte page size, the app launches, all 69 app tests and the nonzero OCR model-integration suite execute, and representative reader/native OCR flows pass. A zero-test Gradle task is not acceptance evidence. Current OCR runtime failure is tracked as unresolved; no production OCR change should be made until the SVE failure is isolated and covered by a regression test.
