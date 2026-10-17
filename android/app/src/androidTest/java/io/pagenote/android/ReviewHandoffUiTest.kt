package io.pagenote.android

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.agent.ToolRequest
import io.pagenote.agent.ToolResult
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewHandoffUiTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext
  private val testResolver get() = InstrumentationRegistry.getInstrumentation().context.contentResolver
  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )

  @Test
  fun reviewCanBeDismissedOrDeletedButNotManuallyApplied() {
    val project = installProject()
    val reviewStore = ProjectReviewStore(testResolver, project.sourceDirectoryUri, project.reviewsUri)
    val created = reviewStore.create(
      side = "note",
      kind = "overall",
      page = 1,
      instruction = "Review the page",
      revision = PageNotes.revision("Review fixture note"),
    )
    val id = created.getValue("id").jsonPrimitive.content
    val appliedLabel = targetContext.getString(R.string.review_applied)
    val dismissedLabel = targetContext.getString(R.string.review_dismissed)
    val deleteLabel = targetContext.getString(R.string.delete)

    composeRule.setContent {
      MaterialTheme {
        ReviewListDialog(
          page = 1,
          project = project,
          noteRevision = PageNotes.revision("Review fixture note"),
          onDismiss = {},
          onChanged = {},
        )
      }
    }

    composeRule.onNodeWithText("Review the page").assertIsDisplayed()
    assertTrue(composeRule.onAllNodesWithText(appliedLabel).fetchSemanticsNodes().isEmpty())
    composeRule.onNodeWithText(dismissedLabel).assertIsDisplayed().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithText("dismissed").fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals("dismissed", reviewStore.find(id)?.get("status")?.jsonPrimitive?.content)

    composeRule.onNodeWithText(deleteLabel).assertIsDisplayed().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithText("Review the page").fetchSemanticsNodes().isEmpty()
    }
    assertEquals(null, reviewStore.find(id))
    assertEquals(0, reviewStore.list(1).size)
  }

  @Test
  fun applyRequiresSameRunChangedWriteForTheReviewPageAndRevision() = runBlocking {
    val initialNotes = PageNotes.join(listOf("First page", "Second page"))
    val project = installProject(initialNotes)
    val noteStore = ProjectNoteStore(testResolver, project.notesUri)
    val originalNotes = noteStore.read()
    val firstPage = NotePage.pageAt(originalNotes, 1)
    val secondPage = NotePage.pageAt(originalNotes, 2)
    val firstRevision = PageNotes.revision(firstPage)
    val secondRevision = PageNotes.revision(secondPage)
    val reviewStore = ProjectReviewStore(testResolver, project.sourceDirectoryUri, project.reviewsUri)
    val firstReview = reviewStore.create("note", "overall", 1, "Review first page", firstRevision)
    val secondReview = reviewStore.create("note", "overall", 2, "Review second page", secondRevision)
    val firstId = firstReview.getValue("id").jsonPrimitive.content
    val secondId = secondReview.getValue("id").jsonPrimitive.content
    val runHandlers = ProjectAgentTools.handlers(testResolver, project, null, currentPage = 1)

    suspend fun call(
      handlers: Map<String, suspend (ToolRequest) -> ToolResult>,
      name: String,
      args: Map<String, String>,
    ): ToolResult =
      handlers.getValue(name)(ToolRequest(name, args))

    val noOp = call(
      runHandlers,
      "write_note",
      mapOf("page" to "1", "markdown" to firstPage, "expectedRevision" to firstRevision),
    )
    assertTrue(noOp.ok)
    assertEquals(null, noOp.updatedPage)
    assertFalse(
      call(
        runHandlers,
        "write_review_status",
        mapOf("id" to firstId, "status" to "applied", "noteRewritten" to "true"),
      ).ok,
    )

    val staleWrite = call(
      runHandlers,
      "write_note",
      mapOf("page" to "1", "markdown" to "stale", "expectedRevision" to "stale-revision"),
    )
    assertFalse(staleWrite.ok)
    assertFalse(
      call(
        runHandlers,
        "write_review_status",
        mapOf("id" to firstId, "status" to "applied", "noteRewritten" to "true"),
      ).ok,
    )

    val changedOtherPage = call(
      runHandlers,
      "write_note",
      mapOf("page" to "2", "markdown" to "Second page revised", "expectedRevision" to secondRevision),
    )
    assertEquals(2, changedOtherPage.updatedPage)
    assertFalse(
      call(
        runHandlers,
        "write_review_status",
        mapOf("id" to firstId, "status" to "applied"),
      ).ok,
    )

    val otherRun = ProjectAgentTools.handlers(testResolver, project, null, currentPage = 1)
    assertFalse(
      call(
        otherRun,
        "write_review_status",
        mapOf("id" to secondId, "status" to "applied", "noteRewritten" to "true"),
      ).ok,
    )
    assertTrue(
      call(
        runHandlers,
        "write_review_status",
        mapOf("id" to secondId, "status" to "applied", "noteRewritten" to "false"),
      ).ok,
    )

    val changedTargetPage = call(
      runHandlers,
      "patch_note",
      mapOf(
        "page" to "1",
        "start" to "0",
        "end" to "0",
        "replacement" to "Updated: ",
        "expectedRevision" to firstRevision,
      ),
    )
    assertEquals(1, changedTargetPage.updatedPage)
    assertTrue(
      call(
        runHandlers,
        "write_review_status",
        mapOf("id" to firstId, "status" to "applied", "noteRewritten" to "false"),
      ).ok,
    )
  }

  private fun installProject(): OpenedProject {
    return installProject("Review fixture note")
  }

  private fun installProject(note: String): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"a".repeat(64)}","name":"Review UI fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, "%PDF-fixture".toByteArray())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, note.toByteArray(Charsets.UTF_8))
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray(Charsets.UTF_8))
      },
    )
    return ProjectDocumentOpener(testResolver).open(treeUri)
  }
}
