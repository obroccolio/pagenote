package io.pagenote.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
  fun noteFontSizeUsesDesktopOptionsAndDefaultsToFifteen() {
    assertEquals(listOf(12, 15, 18, 20, 24, 28, 32), ReaderSettingsPolicy.NOTE_FONT_SIZES)
    assertEquals(15, ReaderSettings().noteFontSize)
  }

  @Test(expected = IllegalArgumentException::class)
  fun unsupportedNoteFontSizeRejected() {
    ReaderSettingsPolicy.validate(ReaderSettings(noteFontSize = 17))
  }

  @Test
  fun everyDesktopNoteFontSizePassesValidation() {
    ReaderSettingsPolicy.NOTE_FONT_SIZES.forEach { size ->
      ReaderSettingsPolicy.validate(ReaderSettings(noteFontSize = size))
    }
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
