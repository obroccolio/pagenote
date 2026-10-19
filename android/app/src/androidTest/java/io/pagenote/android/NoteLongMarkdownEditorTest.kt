package io.pagenote.android

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NoteLongMarkdownEditorTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun longUnbrokenToken_laysOutAndRemainsEditableWithoutChangingSource() {
    val token = "A".repeat(40_000)
    val markdown = "# before\n\n$dataUriPrefix$token\n\n## after"
    val notes = PageNotes.join(listOf("short note", "neighbor page"))
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findEditor()?.text?.toString() == "short note" }
    val editor = requireNotNull(findEditor())

    val startedAt = SystemClock.elapsedRealtime()
    composeRule.runOnUiThread { session.noteDraft.value = markdown }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      editor.isLaidOut && !editor.isLayoutRequested && editor.text.length == markdown.length
    }
    composeRule.waitForIdle()
    val layoutElapsedMs = SystemClock.elapsedRealtime() - startedAt
    assertTrue("long note update and layout took ${layoutElapsedMs}ms", layoutElapsedMs < 5_000)
    assertEquals(markdown, editor.text.toString())
    assertEquals(markdown.length, editor.selectionStart)
    val editStartedAt = SystemClock.elapsedRealtime()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.setSelection(2, 8)
      editor.text.insert(8, " edited")
    }
    val edited = markdown.replaceRange(8, 8, " edited")
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.noteDraft.value == edited && !editor.isLayoutRequested
    }
    composeRule.waitForIdle()
    val editElapsedMs = SystemClock.elapsedRealtime() - editStartedAt
    assertTrue("editing the long token took ${editElapsedMs}ms", editElapsedMs < 5_000)
    assertEquals(edited, editor.text.toString())

    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == edited
    }
    val saved = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(edited, NotePage.pageAt(saved, 1))
    assertEquals("neighbor page", NotePage.pageAt(saved, 2))
  }

  @Test
  fun pageJumpToLongMarkdown_remainsResponsiveAndSavesOnlyTargetPage() {
    val longNote = "# before\n\n$dataUriPrefix${"A".repeat(40_000)}\n\n## after"
    val pages = (1..31).map { page -> if (page == 31) longNote else "Page $page note" }
    val notes = PageNotes.join(pages)
    val project = installProject(notes)
    val session = attachProjectToReader(project, notes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findEditor("Page 1 note") != null }

    composeRule.onNodeWithTag("reader_page_status").assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("page_jump_dialog").assertIsDisplayed()
    composeRule.onNodeWithTag("page_jump_input").performTextClearance()
    composeRule.onNodeWithTag("page_jump_input").performTextInput("31")

    val startedAt = SystemClock.elapsedRealtime()
    composeRule.onNodeWithTag("page_jump_confirm").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      session.pageIndex.intValue == 30 && findEditor(longNote)?.let {
        it.isLaidOut && !it.isLayoutRequested
      } == true
    }
    composeRule.waitForIdle()
    val jumpElapsedMs = SystemClock.elapsedRealtime() - startedAt
    assertTrue("jump to the long note took ${jumpElapsedMs}ms", jumpElapsedMs < 5_000)
    val editor = requireNotNull(findEditor(longNote))
    assertEquals(longNote, editor.text.toString())

    val edited = longNote.replaceRange(8, 8, " edited")
    InstrumentationRegistry.getInstrumentation().runOnMainSync { editor.text.insert(8, " edited") }
    composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == edited }
    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 31) == edited
    }
    val saved = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(edited, NotePage.pageAt(saved, 31))
    assertEquals("Page 30 note", NotePage.pageAt(saved, 30))
  }

  private fun attachProjectToReader(project: OpenedProject, notes: String): ReaderSessionViewModel {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("reader_menu_command_open_project").fetchSemanticsNodes().isNotEmpty()
    }
    val session = ViewModelProvider(composeRule.activity)[ReaderSessionViewModel::class.java]
    composeRule.runOnUiThread {
      session.project.value = project
      session.markdownSource.value = "Source document text"
      session.notesMarkdown.value = notes
      session.noteDraft.value = NotePage.pageAt(notes, 1)
      session.noteBaseRevision.value = PageNotes.revision(NotePage.pageAt(notes, 1))
      session.noteWritable.value = true
      session.noteStatus.value = null
    }
    return session
  }

  private fun installProject(notes: String): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"e".repeat(64)}","name":"Long Markdown fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
    """.trimIndent().toByteArray(Charsets.UTF_8)
    testResolver.call(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.METHOD_INSTALL,
      null,
      Bundle().apply {
        putByteArray(ExistingProjectSafTestProvider.EXTRA_MANIFEST, manifest)
        putByteArray(ExistingProjectSafTestProvider.EXTRA_PDF, "%PDF-fixture".toByteArray())
        putByteArray(ExistingProjectSafTestProvider.EXTRA_NOTES, notes.toByteArray(Charsets.UTF_8))
        putByteArray(ExistingProjectSafTestProvider.EXTRA_REVIEWS, "[]".toByteArray())
      },
    )
    return ProjectDocumentOpener(testResolver).open(treeUri)
  }

  private fun findEditor(): EditText? {
    val result = AtomicReference<EditText?>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val editors = mutableListOf<EditText>()
      composeRule.activity.window.decorView.collectEditors(editors)
      result.set(editors.firstOrNull { it.tag == "note_markdown_editor" })
    }
    return result.get()
  }

  private fun findEditor(expectedText: String): EditText? =
    findEditor()?.takeIf { it.text.toString() == expectedText }

  private fun View.collectEditors(destination: MutableList<EditText>) {
    if (this is EditText) destination += this
    if (this is ViewGroup) for (index in 0 until childCount) getChildAt(index).collectEditors(destination)
  }

  private val testResolver get() = InstrumentationRegistry.getInstrumentation().context.contentResolver
  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )

  private companion object {
    const val dataUriPrefix = "data:image/png;base64,"
  }
}
