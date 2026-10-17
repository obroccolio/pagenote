package io.pagenote.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
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
    composeRule.onNodeWithTag("configure_agent_model")
      .performScrollTo()
      .assertIsDisplayed()
      .assertIsEnabled()
      .performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("agent_model_settings_title").fetchSemanticsNodes().isNotEmpty() ||
        composeRule.onAllNodesWithTag("agent_model_settings_load_failed").fetchSemanticsNodes().isNotEmpty() ||
        composeRule.onAllNodesWithTag("settings_save_error").fetchSemanticsNodes().isNotEmpty() ||
        composeRule.onAllNodesWithTag("reader_settings_load_failed").fetchSemanticsNodes().isNotEmpty()
    }
    val settingsErrors = composeRule.onAllNodesWithTag("settings_save_error").fetchSemanticsNodes()
    assertTrue("reader settings could not be saved: ${settingsErrors.firstOrNull()?.config}", settingsErrors.isEmpty())
    try {
      composeRule.waitUntil(timeoutMillis = 10_000) {
        composeRule.onAllNodesWithTag("save_agent_model_settings").fetchSemanticsNodes().isNotEmpty() ||
          composeRule.onAllNodesWithTag("agent_model_settings_load_error").fetchSemanticsNodes().isNotEmpty()
      }
    } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
      val saving = composeRule.onAllNodesWithTag("reader_settings_configure_saving").fetchSemanticsNodes().isNotEmpty()
      val configureAction = composeRule.onAllNodesWithTag("configure_agent_model").fetchSemanticsNodes().isNotEmpty()
      val readerLoading = composeRule.onAllNodesWithTag("reader_settings_loading").fetchSemanticsNodes().isNotEmpty()
      throw AssertionError(
        "settings transition timed out (saving=$saving, actionVisible=$configureAction, readerLoading=$readerLoading)",
        error,
      )
    }
    val loadErrors = composeRule.onAllNodesWithTag("agent_model_settings_load_error").fetchSemanticsNodes()
    assertTrue("model settings could not load: ${loadErrors.firstOrNull()?.config}", loadErrors.isEmpty())
    composeRule.onNodeWithTag("save_agent_model_settings").assertIsDisplayed()
  }
}
