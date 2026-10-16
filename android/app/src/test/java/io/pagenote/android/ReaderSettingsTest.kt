package io.pagenote.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSettingsTest {
  @Test
  fun defaultsPass() {
    ReaderSettingsPolicy.validate(ReaderSettings())
  }

  @Test(expected = IllegalArgumentException::class)
  fun badThemeRejected() {
    ReaderSettingsPolicy.validate(ReaderSettings(theme = "neon"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun badSplitRejected() {
    ReaderSettingsPolicy.validate(ReaderSettings(leftPercent = 5))
  }

  @Test
  fun phonePortraitUsesStackedMode() {
    assertFalse(ReaderSettingsPolicy.useSideBySide(412, 915, false))
  }

  @Test
  fun tabletLandscapeUsesSideBySide() {
    assertTrue(ReaderSettingsPolicy.useSideBySide(1280, 800, true))
  }
}
