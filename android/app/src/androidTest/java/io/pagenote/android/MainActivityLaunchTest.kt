package io.pagenote.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun localizedReaderHome_remainsAvailableAfterSettingsLoad() {
    val openProject = composeRule.activity.getString(R.string.open_project)

    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithText(openProject).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithText(openProject).assertIsDisplayed()
  }
}
