package io.pagenote.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pagenote.agent.PageTutorResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageTutorDialogTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun showsInstructionAndGenerateAction() {
    setDialog()

    composeRule.onNodeWithTag("page_tutor_instruction").assertIsDisplayed()
    composeRule.onNodeWithTag("page_tutor_explain").assertIsDisplayed()
    composeRule.onNodeWithTag("page_tutor_dismiss").assertIsDisplayed()
  }

  @Test
  fun keepsModelOutputInPreviewUntilAppend() {
    var appendCalls = 0
    setDialog(
      preview = PageTutorResult(
        page = 2,
        noteRevision = "revision-2",
        markdown = "**AI preview**",
        warnings = emptyList(),
      ),
      onAppend = { appendCalls += 1 },
    )

    composeRule.onNodeWithTag("page_tutor_preview").assertIsDisplayed()
    composeRule.onNodeWithTag("page_tutor_append").assertIsDisplayed()
    assertEquals(0, appendCalls)
    composeRule.onNodeWithTag("page_tutor_append").performClick()
    assertEquals(1, appendCalls)
  }

  @Test
  fun disablesAppendForReadOnlyNote() {
    setDialog(
      noteWritable = false,
      preview = PageTutorResult(
        page = 2,
        noteRevision = "revision-2",
        markdown = "AI preview",
        warnings = emptyList(),
      ),
    )

    composeRule.onNodeWithTag("page_tutor_append").assertIsNotEnabled()
  }

  @Test
  fun preventsDismissalWhileRequestIsRunning() {
    setDialog(isRunning = true)

    composeRule.onNodeWithTag("page_tutor_dismiss").assertIsNotEnabled()
    composeRule.onNodeWithTag("page_tutor_explain").assertIsNotEnabled()
  }

  @Test
  fun preventsDuplicateAppendWhileSaving() {
    var appendCalls = 0
    setDialog(
      isSaving = true,
      preview = PageTutorResult(
        page = 2,
        noteRevision = "revision-2",
        markdown = "AI preview",
        warnings = emptyList(),
      ),
      onAppend = { appendCalls += 1 },
    )

    composeRule.onNodeWithTag("page_tutor_append").assertIsNotEnabled()
    composeRule.onNodeWithTag("page_tutor_dismiss").assertIsNotEnabled()
    assertEquals(0, appendCalls)
  }

  private fun setDialog(
    isRunning: Boolean = false,
    isSaving: Boolean = false,
    noteWritable: Boolean = true,
    preview: PageTutorResult? = null,
    onAppend: () -> Unit = {},
  ) {
    composeRule.setContent {
      MaterialTheme {
        PageTutorDialog(
          page = 2,
          instruction = "Explain this page.",
          isRunning = isRunning,
          isSaving = isSaving,
          errorMessage = null,
          preview = preview,
          noteWritable = noteWritable,
          onInstructionChange = {},
          onExplain = {},
          onAppend = onAppend,
          onDismiss = {},
        )
      }
    }
  }
}
