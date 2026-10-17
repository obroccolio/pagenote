# Android Startup and 16 KB Compatibility Plan

**Goal:** Remove the reader's reproducible launch crash and package 16 KB-compatible 64-bit native libraries.

**SPEC:** `android/docs/spec/features/startup-and-16kb-compatibility.md`.

**Constraints:** Keep all Android-only planning under `android/docs/spec/`; preserve SAF picker behavior and the existing OpenCV API surface; verify actual ELF and APK alignment; include a matching Unreleased changelog entry in every pushed implementation commit.

## Task 1: Reproduce the Reader Launch Regression

- Add an app instrumentation test that launches `MainActivity` and waits for the localized home screen.
- Run it against the current APK on an available Android device and confirm it fails with the missing `ActivityResultRegistryOwner` exception.
- Commit the failing-test and test-dependency change with an Unreleased changelog entry.

## Task 2: Preserve the Activity Result Owner

- Capture the hosting `ActivityResultRegistryOwner` before constructing the locale-specific configuration context.
- Provide that owner alongside the localized `LocalContext` at the reader composition root.
- Run the launch test and app JVM tests; install and launch the built app on the connected device and verify the crash is gone.
- Commit the implementation and its changelog entry separately from the regression-test commit.

## Task 3: Replace Misaligned OpenCV Binaries

- Replace `com.quickbirdstudios:opencv:4.5.3.0` with the API-compatible official OpenCV AAR selected by inspecting its actual 64-bit ELF segments; verify the packaged C++ runtime is aligned too.
- Build all configured ABI APKs and run JVM tests.
- Inspect every resulting `arm64-v8a` and `x86_64` library with NDK `llvm-objdump -p`; require `LOAD` alignment `>= 2**14`.
- Run `zipalign -c -P 16 -v 4` on each APK. Fail the task if any packaged 64-bit dependency remains misaligned.
- Commit the dependency and verified build change with its own Unreleased changelog entry.

## Task 4: Device Smoke Check and Sync

- Install the new ARM64 debug APK on the connected device, launch it, confirm the home screen remains visible, and open then dismiss the SAF project picker without selecting a project.
- Record that the device uses 4 KB pages; do not claim it validates 16 KB runtime behavior.
- Run `testDebugUnitTest`, `assembleDebug`, and `connectedDebugAndroidTest` when the instrumentation target is available.
- Push reviewed feature commits to `fork/codex/android-port` only after all relevant checks and ensure the pushed range includes the Unreleased changelog updates.
