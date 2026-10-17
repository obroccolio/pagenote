package io.pagenote.feature.ocr

object OcrTrigger {
  const val SPARSE_CHARS_WITH_BITMAP = 20

  fun shouldRun(nativeAvailable: Boolean, nativeText: String, hasBitmap: Boolean): Boolean {
    if (!nativeAvailable) return true
    val trimmed = nativeText.trim()
    if (trimmed.isEmpty()) return true
    if (hasBitmap && trimmed.length < SPARSE_CHARS_WITH_BITMAP) return true
    return false
  }
}
