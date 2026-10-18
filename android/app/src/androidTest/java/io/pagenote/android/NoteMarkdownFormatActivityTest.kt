package io.pagenote.android

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.core.spans.EmphasisSpan
import io.noties.markwon.core.spans.StrongEmphasisSpan
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NoteMarkdownFormatActivityTest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun menuFormattingCommandsWrapSelectionAndSaveOnlyCurrentPage() {
    val savedPages = listOf("Lead Selected tail", "Second page remains unchanged")
    val savedNotes = PageNotes.join(savedPages)
    val project = installProject(savedNotes)

    formatCommand("bold").assertIsNotEnabled()

    val session = attachProjectToReader(project, savedNotes)
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }
    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() == null }
    formatCommand("bold").assertIsNotEnabled()
    composeRule.onNodeWithTag("note_tab_edit").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findNoteEditor() != null }

    listOf("bold", "italic", "strikethrough").forEach { command ->
      selectCurrentWord()
      formatCommand(command).assertIsEnabled().performClick()
      val expected = when (command) {
        "bold" -> "Lead **Selected** tail"
        "italic" -> "Lead ***Selected*** tail"
        else -> "Lead ***~~Selected~~*** tail"
      }
      try {
        composeRule.waitUntil(timeoutMillis = 10_000) { session.noteDraft.value == expected }
      } catch (error: Throwable) {
        throw AssertionError(
          "Expected draft <$expected>, got draft <${session.noteDraft.value}> and editor <${findNoteEditor()?.text}>",
          error,
        )
      }
    }

    val expectedCurrentPage = "Lead ***~~Selected~~*** tail"
    composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) {
      NotePage.pageAt(ProjectNoteStore(testResolver, project.notesUri).read(), 1) == expectedCurrentPage
    }
    val saved = ProjectNoteStore(testResolver, project.notesUri).read()
    assertEquals(expectedCurrentPage, NotePage.pageAt(saved, 1))
    assertEquals(savedPages[1], NotePage.pageAt(saved, 2))
    assertEquals(expectedCurrentPage, session.noteDraft.value)

    composeRule.onNodeWithTag("note_tab_preview").performClick()
    composeRule.waitUntil(timeoutMillis = 10_000) { findRenderedPreview() != null }
    val rendered = findRenderedPreview()?.text as? Spanned
      ?: error("formatted Markdown preview must expose native spans")
    val spans = rendered.getSpans(0, rendered.length, Any::class.java).toList()
    assertTrue("preview must render bold", spans.any { it is StrongEmphasisSpan })
    assertTrue("preview must render italic", spans.any { it is EmphasisSpan })
    assertTrue("preview must render strikethrough", spans.any { it is StrikethroughSpan })
  }

  private fun formatCommand(id: String) = run {
    val commandTag = "reader_menu_command_$id"
    if (composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isEmpty()) {
      val collapsed = composeRule.onAllNodesWithTag("reader_menu_sidebar_collapsed").fetchSemanticsNodes()
      if (collapsed.isNotEmpty()) {
        composeRule.onNodeWithTag("reader_menu_group_format").performClick()
      } else {
        composeRule.onNodeWithTag("reader_menu_trigger").performClick()
      }
    }
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag(commandTag).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag(commandTag).performScrollTo()
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

  private fun selectCurrentWord() {
    val editor = requireNotNull(findNoteEditor())
    val text = editor.text.toString()
    val start = text.indexOf("Selected")
    check(start >= 0) { "formatted word must remain in the Markdown source" }
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      editor.requestFocus()
      editor.setSelection(start, start + "Selected".length)
    }
    composeRule.waitForIdle()
  }

  private fun installProject(notes: String): OpenedProject {
    val manifest = """
      {"format":"pagenote-project","version":1,"id":"${"c".repeat(64)}","name":"Markdown format fixture","left":"src/pattern-recognition.pdf","right":"src/notes.md","leftName":"pattern-recognition.pdf"}
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

  private fun findNoteEditor(): EditText? {
    val result = AtomicReference<EditText?>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val matches = mutableListOf<EditText>()
      composeRule.activity.window.decorView.collectEditTexts(matches)
      result.set(matches.firstOrNull { it.tag == "note_markdown_editor" })
    }
    return result.get()
  }

  private fun findRenderedPreview(): TextView? {
    val result = AtomicReference<TextView?>()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val hosts = mutableListOf<MermaidPreviewHost>()
      composeRule.activity.window.decorView.collectPreviewHosts(hosts)
      result.set(hosts.firstOrNull { it.markdownView.text.toString().contains("Selected") }?.markdownView)
    }
    return result.get()
  }

  private fun View.collectEditTexts(destination: MutableList<EditText>) {
    if (this is EditText) destination += this
    if (this is ViewGroup) for (index in 0 until childCount) getChildAt(index).collectEditTexts(destination)
  }

  private fun View.collectPreviewHosts(destination: MutableList<MermaidPreviewHost>) {
    if (this is MermaidPreviewHost) destination += this
    if (this is ViewGroup) for (index in 0 until childCount) getChildAt(index).collectPreviewHosts(destination)
  }

  private val testResolver get() = InstrumentationRegistry.getInstrumentation().context.contentResolver
  private val treeUri: Uri
    get() = DocumentsContract.buildTreeDocumentUri(
      ExistingProjectSafTestProvider.AUTHORITY,
      ExistingProjectSafTestProvider.ROOT_ID,
    )
}
