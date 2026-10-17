package io.pagenote.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("open_project").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("open_project").assertIsDisplayed()
  }

  @Test
  fun readerSettingsCanOpenOpenAiModelSettings() {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("settings_action").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("settings_action").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("configure_agent_model").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("configure_agent_model").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("agent_model_settings_title").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("agent_model_settings_title").assertIsDisplayed()
    composeRule.onNodeWithTag("save_agent_model_settings").assertIsDisplayed()
  }
}
