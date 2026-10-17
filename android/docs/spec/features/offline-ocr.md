# Offline OCR

## Goal

Add a real, on-device Chinese/English OCR engine to `feature-ocr`. OCR must be available without network access, retain source imagery for visual review, and map detected text, confidence, and page-relative geometry into the existing `OcrResult` contract.

## Scope

- Use the official PaddleOCR Android ONNX Runtime pipeline, adapted into this module without changing app wiring.
- Bundle the PP-OCRv6 tiny detection and recognition ONNX models and recognition dictionary/config as Android assets. Model loading must use only bundled assets; no runtime downloader or network client is allowed.
- Expose an engine boundary that accepts an Android `Bitmap` and returns the existing `OcrResult` value type. Normalize quadrilateral boxes into the existing 0..1000 page coordinate space.
- Keep original image ownership outside the OCR engine; an OCR failure must not discard or mutate the caller's source image.
- Preserve the existing OCR trigger and result policies.

## Module Non-goals

- This module does not directly connect to `MainActivity`, PDF rendering, page-preparation caching, or project persistence; the reader action is specified separately in `reader-ocr.md`.
- Changing native PDF text extraction, OCR trigger heuristics, or page image generation.
- Downloading models after install, using ML Kit, or making network OCR fallbacks.
- Claiming device-level speed/accuracy until exercised on Android hardware.

## Requirements

1. When the caller supplies a non-empty bitmap and bundled assets are valid, the engine shall run both text detection and recognition locally and return recognized text with confidence and normalized bounding boxes.
2. When the caller supplies an empty bitmap, the engine shall reject the request before allocating inference resources.
3. When a bundled model, recognition dictionary, ONNX Runtime, or image preprocessing dependency is unavailable or invalid, the engine shall return/raise a clear actionable failure; it shall never return a successful empty result as a fallback.
4. When OCR inference fails, the result shall preserve the failure status and a warning while the source bitmap remains unmodified and owned by the caller.
5. When any recognized block confidence is below `OcrResultPolicy.LOW_CONFIDENCE`, the existing visual-review policy shall flag the result.
6. When the device has no network connectivity, the packaged models shall remain sufficient to initialize the engine and perform recognition; inference shall make no network requests.
7. The model and adapted upstream source provenance and licenses shall be recorded in the Android module. PP-OCRv6 tiny det/rec artifacts are sourced from PaddlePaddle's official model releases and are marked Apache-2.0; upstream project notices must be preserved with copied code.

## Design

- `OfflineOcrEngine` is the small public boundary; an Android implementation owns one reusable ONNX-backed pipeline and exposes explicit close/release semantics.
- Engine creation and recognition are suspendable/blocking-work-safe APIs so callers can keep inference off the UI thread. The caller owns the `Bitmap` and retains its original page image independently.
- Pipeline implementation follows the official `deploy/ppocr-android/ppocr-sdk` stages: model asset validation/copy to app-private files if required by ONNX Runtime, bitmap normalization, text detection, quadrilateral crop, recognition, and conversion to `OcrBlock`.
- Asset names and the engine identifier are versioned constants. The result keeps the existing status vocabulary (`ok`, `partial`, `failed`); low confidence and runtime warnings remain visible through `OcrResultPolicy`.
- Dependencies are pinned and recorded in the Android module. The current implementation uses official OpenCV `4.14.0` and ONNX Runtime Android `1.21.1`; ELF/ZIP page-size alignment does not by itself prove that the native library executes correctly on every ARM64 runtime.
- The shipped ARM64 preprocessing runtime shall not execute SVE instructions on a runtime whose advertised SVE capability is not executable. The supported API 35 ARM64 test image currently advertises SVE through `AT_HWCAP2` but traps on `RDVL`; native compatibility must be validated by execution as well as ELF/ZIP alignment.

## Provenance

- Android deployment and API: [PaddleOCR PP-OCRv6 Android Demo](https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/inference_deployment/cross_platform/android_deployment.en.md)
- Detection model: [PaddlePaddle/PP-OCRv6_tiny_det_onnx](https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_det_onnx)
- Recognition model: [PaddlePaddle/PP-OCRv6_tiny_rec_onnx](https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_rec_onnx)
- PaddleOCR Android source snapshot: `dab3fe35379033fdcb2d0e9572fac0b36c9a9ebf`.
- Detection model revision: `2ba1506c0380b8f0b03dd142459aac66d4421f6c`; SHA-256 `193bab7a04fca699a6c82e6abb5b81bdb28177f0abd4062552b04908dafb19f8`.
- Recognition model revision: `2612ab37152ae0a677521bae4e1e3d4fb4cf7c30`; SHA-256 `9ef676d6ed3c88256a2d92c640c44f25b0c40947e111b14b8be8f594091563e6`.
- Recognition config at the same revision; SHA-256 `66170210bad538e83fff3c4a3867e547d6bf20b50d64b20347c4b913f3034ea1`.
- Both model cards declare Apache-2.0. The PaddleOCR repository is Apache-2.0; its license text is retained with the feature module.
- ONNX Runtime Android `1.21.1` is MIT and official OpenCV Android `4.14.0` is Apache-2.0; license texts and third-party notices are retained in the module.

## Verification

- JVM tests cover bitmap preconditions, geometry normalization, Paddle result mapping, confidence preservation, and explicit failure mapping.
- Module build verifies Android/ONNX integration compiles and packages model assets.
- Android instrumentation must execute a nonzero test count, initialize both models, and recognize a fixed Chinese/English fixture with network disabled. A successful Gradle task that discovers zero tests is not evidence.
- Inspect the built APK to confirm both model assets are present and that no lazy model-download path exists.

## Runtime Validation Status

- On 2026-10-18, the official Android 15 Google Play ARM64 16 KB AVD reported `getconf PAGESIZE = 16384`; the app launched and all 69 app instrumentation tests passed.
- The OCR library instrumentation runner was then configured explicitly after the task had previously reported success while starting zero tests. The corrected task discovers seven tests, but the instrumentation process terminates with `SIGILL` while initializing `org.opencv:opencv:4.14.0`, in `OpenCVUtils.init` inside `libopencv_java4.so`. The faulting instruction was decoded as SVE `RDVL`.
- The same crash and library build ID reproduce on the Android 15 4 KB ARM64 AVD, confirming that page size is not the trigger. The tombstone PC resolves to `RDVL` at OpenCV `libopencv_java4.so` offset `0x168e258`, before JNI initialization completes. Disassembly of the caller shows a guarded call after `getauxval(AT_HWCAP2)` tests bit 1 (`HWCAP2_SVE`). On the 16 KB AVD, `AT_HWCAP2` includes that bit and `/proc/cpuinfo` lists SVE/SVE2, but executing the guarded `RDVL` still raises `SIGILL`. This is strong evidence of an emulator CPU-feature advertisement/execution mismatch; physical ARM64 behavior remains unverified.
- A local artifact comparison found official OpenCV `4.11.0` still contains `RDVL`. Official `4.10.0` has no `RDVL` in its ARM64 disassembly, but its OpenCV and libc++ ELF `LOAD` segments are only 4 KB aligned. Therefore neither version is ready to replace the current dependency unchanged.
- OpenCV's packaged 64-bit ELF `LOAD` segments and APK ZIP entries pass the 16 KB alignment checks, but this is separate from the unresolved execution failure. Offline OCR is not currently runtime-verified and must not be marked complete.
- The selected remediation is to preserve PaddleOCR, ONNX Runtime, and their Kotlin API, and build OpenCV 4.10.0 from pinned source commit `71d3237a093b60a27601c20e9ee6c3e52154e8b1` with Android NDK r28+, JDK 17 for the upstream Gradle wrappers, packager compile/target SDK 34, an ARM64 NEON baseline, TBB and IPP disabled, and statically linked OCR modules. IPP is excluded because its bundled x86 archive does not link with NDK r28 and is not required by this OCR pipeline. Both generated Android library source sets must exclude only `NativeCameraView.java`, whose `videoio` dependency belongs to an unrelated camera example. The resulting AAR must retain all app-supported ABIs, have no unresolved OpenCV `DT_NEEDED` dependencies, and pass both the existing OCR recognition suite and 16 KB ELF/ZIP checks. The implementation procedure is in `../offline-ocr-native-runtime-plan.md`.
