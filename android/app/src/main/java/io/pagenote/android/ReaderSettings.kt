package io.pagenote.android

data class ReaderSettings(
  val theme: String = "system",
  val language: String = "system",
  val leftPercent: Int = 42,
  val noteAnimation: Boolean = true,
)

object ReaderSettingsPolicy {
  private val THEMES = setOf("system", "light", "dark", "paper")
  private val LANGUAGES = setOf("system", "zh-CN", "en-US")

  fun sanitize(settings: ReaderSettings): ReaderSettings {
    val defaults = ReaderSettings()
    return settings.copy(
      theme = settings.theme.takeIf(THEMES::contains) ?: defaults.theme,
      language = settings.language.takeIf(LANGUAGES::contains) ?: defaults.language,
      leftPercent = settings.leftPercent.takeIf { it in 20..80 } ?: defaults.leftPercent,
    )
  }

  fun validate(settings: ReaderSettings) {
    require(THEMES.contains(settings.theme)) { "bad theme" }
    require(LANGUAGES.contains(settings.language)) { "bad language" }
    require(settings.leftPercent in 20..80) { "bad split percent" }
  }

  fun isTabletLandscape(widthDp: Int, heightDp: Int, orientationLandscape: Boolean): Boolean {
    if (!orientationLandscape) return false
    return maxOf(widthDp, heightDp) >= 900
  }

  fun useSideBySide(widthDp: Int, heightDp: Int, orientationLandscape: Boolean): Boolean {
    return isTabletLandscape(widthDp, heightDp, orientationLandscape)
  }
}
