package io.pagenote.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class AnnotationTextUiTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun inputDialogConfirms() {
    var confirmed = false
    composeRule.setContent {
      MaterialTheme {
        var text by remember { mutableStateOf("") }
        AnnotationTextInputDialog(
          text = text,
          onTextChange = { text = it },
          onConfirm = { confirmed = true },
          onDismiss = {},
        )
      }
    }

    composeRule.onNodeWithTag("annotation_cancel").assertIsDisplayed()
    composeRule.runOnIdle { assert(!confirmed) }
  }
}
