package io.pagenote.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownSourcePaneTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun markdownSourcePane_rendersPagedPreview() {
    composeRule.setContent {
      MarkdownSourcePane(
        markdown = "# one\n\n<!-- pagebreak -->\n\n# two\n",
        sourceName = "left.md",
        page = 2,
        sourceDirectoryUri = null,
      )
    }

    composeRule.onNodeWithTag("markdown_source_pane").assertIsDisplayed()
  }
}
