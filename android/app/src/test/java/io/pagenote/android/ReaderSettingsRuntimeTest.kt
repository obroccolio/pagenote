package io.pagenote.android

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSettingsRuntimeTest {
  @Test
  fun systemThemeFollowsDeviceAndExplicitThemesOverrideIt() {
    assertEquals(ReaderThemeMode.DARK, resolveReaderTheme("system", systemIsDark = true))
    assertEquals(ReaderThemeMode.LIGHT, resolveReaderTheme("system", systemIsDark = false))
    assertEquals(ReaderThemeMode.LIGHT, resolveReaderTheme("light", systemIsDark = true))
    assertEquals(ReaderThemeMode.DARK, resolveReaderTheme("dark", systemIsDark = false))
    assertEquals(ReaderThemeMode.PAPER, resolveReaderTheme("paper", systemIsDark = true))
  }

  @Test
  fun systemLanguageFollowsDeviceAndExplicitLanguageOverridesIt() {
    val deviceLocale = Locale("fr", "FR")

    assertEquals(deviceLocale, resolveReaderLocale("system", deviceLocale))
    assertEquals(Locale.SIMPLIFIED_CHINESE, resolveReaderLocale("zh-CN", deviceLocale))
    assertEquals(Locale.US, resolveReaderLocale("en-US", Locale.SIMPLIFIED_CHINESE))
  }

  @Test
  fun splitPercentProducesMatchingPaneWeightsAtBothBounds() {
    assertEquals(20f to 80f, readerPaneWeights(20))
    assertEquals(42f to 58f, readerPaneWeights(42))
    assertEquals(80f to 20f, readerPaneWeights(80))
  }

  @Test
  fun animationPreferenceControlsNoteTransitionDuration() {
    assertEquals(220, notePageTransitionDurationMillis(enabled = true))
    assertEquals(0, notePageTransitionDurationMillis(enabled = false))
  }
}
