package io.pagenote.feature.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTriggerTest {
  @Test
  fun unavailableNativeTriggers() {
    assertTrue(OcrTrigger.shouldRun(nativeAvailable = false, nativeText = "", hasBitmap = false))
  }

  @Test
  fun blankNativeTriggers() {
    assertTrue(OcrTrigger.shouldRun(nativeAvailable = true, nativeText = "  \n ", hasBitmap = false))
  }

  @Test
  fun bitmapSparseTextTriggers() {
    assertTrue(OcrTrigger.shouldRun(nativeAvailable = true, nativeText = "图1", hasBitmap = true))
  }

  @Test
  fun richNativeSkips() {
    assertFalse(OcrTrigger.shouldRun(nativeAvailable = true, nativeText = "x".repeat(50), hasBitmap = true))
    assertFalse(OcrTrigger.shouldRun(nativeAvailable = true, nativeText = "hello", hasBitmap = false))
  }
}
