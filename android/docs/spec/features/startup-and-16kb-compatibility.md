# Android Startup and 16 KB Page-Size Compatibility

## Scope

Keep the Android reader launchable when it applies a user-selected locale through a configuration context, and package 16 KB-compatible native libraries for 64-bit Android devices. This Android-only SPEC belongs under `android/docs/spec/`. Desktop builds and data formats remain unchanged.

## Startup Owner Contract

- `MainActivity` owns the AndroidX activity-result registry used by the SAF project picker.
- When the reader replaces `LocalContext` with a localized configuration context, it must explicitly preserve the original `ActivityResultRegistryOwner` in the Compose tree.
- The project picker remains registered unconditionally during Compose initialization and can still launch after the app has started.
- A missing activity-result owner is a startup failure and must be covered by an app-launch regression test.

## Native Library Contract

- Android Gradle Plugin remains at least 8.5.1 so uncompressed native libraries are ZIP-aligned for 16 KB devices.
- Every `arm64-v8a` and `x86_64` shared library packaged in each produced APK has all ELF `LOAD` segments aligned to at least `2**14` bytes.
- The produced APK passes `zipalign -c -P 16 -v 4`.
- The OpenCV dependency and its `libc++_shared.so` must be validated from the resolved AAR and again from the final APK; a version number alone is not evidence of alignment.
- Android 32-bit ABI libraries are retained only as build targets; page-size acceptance is based on the 64-bit ABIs supported by Android 16 KB kernels.

## Acceptance (EARS)

- When the app loads saved reader settings and applies a localized configuration context, the Android reader shall retain the hosting activity's result registry and render its home screen without an uncaught exception.
- When the user selects a project, the reader shall continue to launch the SAF tree picker through its registered activity-result launcher.
- When an ABI APK is assembled, the Android build shall package the pinned OpenCV API-compatible artifact and its matching C++ runtime without any unaligned 64-bit native library.
- When a debug or release APK is inspected, every `arm64-v8a` and `x86_64` ELF `LOAD` segment shall have an alignment of at least `2**14`, and 16 KB ZIP alignment verification shall pass.
- When the app runs on a 4 KB device, existing reader launch behavior shall remain functional; the 16 KB ELF check remains independently mandatory.

## Verification

- Instrumentation coverage launches the actual `MainActivity`, waits for the localized reader home screen, and fails on the previous `ActivityResultRegistryOwner` crash.
- JVM tests and Android app compilation pass after the context-owner change and OpenCV dependency update.
- Each 64-bit APK is extracted and checked with NDK `llvm-objdump -p`; no `LOAD` line may report an alignment below `2**14`.
- Each APK passes SDK Build Tools `zipalign -c -P 16 -v 4`.
- Device smoke testing confirms launch and SAF project-picker interaction on the connected device. A 4 KB device does not substitute for a 16 KB runtime emulator/device when one is available.
