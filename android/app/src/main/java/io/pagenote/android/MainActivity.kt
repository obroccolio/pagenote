package io.pagenote.android

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import io.noties.markwon.ext.tables.TablePlugin
import io.pagenote.core.project.PageNotes
import io.pagenote.feature.notes.NotePage
import io.pagenote.feature.notes.PageNoteConflictException
import io.pagenote.feature.notes.PageNoteEditor
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: android.os.Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          ReaderHome()
        }
      }
    }
  }
}

@Composable
private fun ReaderHome() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var project by remember { mutableStateOf<OpenedProject?>(null) }
  var renderer by remember { mutableStateOf<PdfDocumentSession?>(null) }
  var pageIndex by remember { mutableIntStateOf(0) }
  var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var notesMarkdown by remember { mutableStateOf("") }
  var noteDraft by remember { mutableStateOf("") }
  var noteBaseRevision by remember { mutableStateOf(PageNotes.revision("")) }
  var noteWritable by remember { mutableStateOf(false) }
  var noteStatus by remember { mutableStateOf<String?>(null) }
  var isOpening by remember { mutableStateOf(false) }
  var isRendering by remember { mutableStateOf(false) }
  var isSaving by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  val savedPageText = NotePage.pageAt(notesMarkdown, pageIndex + 1)
  val hasUnsavedChanges = noteDraft != savedPageText

  suspend fun saveCurrentNote(): Boolean {
    val activeProject = project ?: return false
    if (!noteWritable) {
      noteStatus = "此项目为只读，无法保存笔记"
      return false
    }
    if (!hasUnsavedChanges) return true

    isSaving = true
    noteStatus = null
    val targetPage = pageIndex + 1
    val draftToSave = noteDraft
    val expectedRevision = noteBaseRevision
    return try {
      val result = withContext(Dispatchers.IO) {
        val store = ProjectNoteStore(context.contentResolver, activeProject.notesUri)
        val latest = store.read()
        val edited = PageNoteEditor.replacePage(
          latestMarkdown = latest,
          page = targetPage,
          replacement = draftToSave,
          expectedRevision = expectedRevision,
        )
        store.write(edited.markdown)
        edited
      }
      notesMarkdown = result.markdown
      noteBaseRevision = result.pageRevision
      noteStatus = "已保存"
      true
    } catch (conflict: PageNoteConflictException) {
      noteStatus = conflict.message
      false
    } catch (error: Exception) {
      noteStatus = error.message ?: "笔记保存失败"
      false
    } finally {
      isSaving = false
    }
  }

  fun reloadCurrentNote() {
    val activeProject = project ?: return
    scope.launch {
      try {
        val latest = withContext(Dispatchers.IO) {
          ProjectNoteStore(context.contentResolver, activeProject.notesUri).read()
        }
        notesMarkdown = latest
        val freshPage = NotePage.pageAt(latest, pageIndex + 1)
        noteDraft = freshPage
        noteBaseRevision = PageNotes.revision(freshPage)
        noteStatus = "已重新载入"
      } catch (error: Exception) {
        noteStatus = error.message ?: "重新载入笔记失败"
      }
    }
  }

  suspend fun changePage(nextPage: Int) {
    if (hasUnsavedChanges && !saveCurrentNote()) return
    noteStatus = null
    pageIndex = nextPage
  }

  val projectPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { treeUri: Uri? ->
    if (treeUri != null) {
      scope.launch {
        isOpening = true
        errorMessage = null
        try {
          var writable = true
          try {
            context.contentResolver.takePersistableUriPermission(
              treeUri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
          } catch (_: SecurityException) {
            writable = false
            context.contentResolver.takePersistableUriPermission(
              treeUri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
          }
          val loaded = withContext(Dispatchers.IO) {
            val opened = ProjectDocumentOpener(context.contentResolver).open(treeUri)
            require(opened.sourceExtension == "pdf") {
              "当前版本暂不支持 ${opened.sourceExtension.ifBlank { "此" }} 格式阅读"
            }
            val noteStore = ProjectNoteStore(context.contentResolver, opened.notesUri)
            val noteText = noteStore.read()
            val supportsNoteWrite = noteStore.supportsWrite()
            val pdf = PdfDocumentSession.open(context.contentResolver, opened.sourceUri)
            Pair(Triple(opened, noteText, pdf), supportsNoteWrite)
          }
          renderer = loaded.first.third
          project = loaded.first.first
          notesMarkdown = loaded.first.second
          val firstPage = NotePage.pageAt(loaded.first.second, 1)
          noteDraft = firstPage
          noteBaseRevision = PageNotes.revision(firstPage)
          noteWritable = writable && loaded.second
          noteStatus = if (noteWritable) null else "只读项目：存储位置未授予笔记写入权限"
          pageIndex = 0
        } catch (error: Exception) {
          errorMessage = error.message ?: "项目打开失败"
        } finally {
          isOpening = false
        }
      }
    }
  }

  DisposableEffect(renderer) {
    onDispose { renderer?.close() }
  }
  DisposableEffect(Unit) {
    onDispose { pageBitmap?.recycle() }
  }

  LaunchedEffect(notesMarkdown, pageIndex) {
    val markdown = NotePage.pageAt(notesMarkdown, pageIndex + 1)
    noteDraft = markdown
    noteBaseRevision = PageNotes.revision(markdown)
  }

  LaunchedEffect(pageIndex) {
    noteStatus = null
  }

  LaunchedEffect(renderer, pageIndex) {
    val activeRenderer = renderer ?: return@LaunchedEffect
    isRendering = true
    try {
      val rendered = withContext(Dispatchers.IO) { activeRenderer.render(pageIndex) }
      if (renderer === activeRenderer) {
        pageBitmap?.recycle()
        pageBitmap = rendered
        errorMessage = null
      } else {
        rendered.recycle()
      }
    } catch (error: Exception) {
      errorMessage = error.message ?: "页面渲染失败"
    } finally {
      isRendering = false
    }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = project?.name ?: "pagenote",
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Button(
        onClick = {
          scope.launch {
            if (hasUnsavedChanges && !saveCurrentNote()) return@launch
            projectPicker.launch(null)
          }
        },
        enabled = !isOpening && !isSaving,
      ) {
        Text(if (isOpening) "正在打开" else "打开项目")
      }
    }

    errorMessage?.let { message ->
      Text(
        text = message,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
      )
    }

    val activeRenderer = renderer
    val activeProject = project
    if (activeRenderer != null && activeProject != null) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(
          onClick = { scope.launch { changePage((pageIndex - 1).coerceAtLeast(0)) } },
          enabled = pageIndex > 0 && !isRendering && !isSaving,
        ) {
          Text("上一页")
        }
        Text("${pageIndex + 1} / ${activeRenderer.pageCount}")
        Button(
          onClick = {
            scope.launch {
              changePage((pageIndex + 1).coerceAtMost(activeRenderer.pageCount - 1))
            }
          },
          enabled = pageIndex < activeRenderer.pageCount - 1 && !isRendering && !isSaving,
        ) {
          Text("下一页")
        }
      }
    }

    if (activeRenderer != null && activeProject != null) {
      BoxWithConstraints(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
      ) {
        if (maxWidth >= 840.dp) {
          Row(modifier = Modifier.fillMaxSize()) {
            PdfPageViewport(
              bitmap = pageBitmap,
              sourceName = activeProject.sourceName,
              page = pageIndex,
              loading = isRendering,
              modifier = Modifier.weight(0.56f).fillMaxHeight(),
            )
            Box(
              modifier = Modifier.fillMaxHeight().padding(horizontal = 5.dp)
                .width(1.dp).background(MaterialTheme.colorScheme.outlineVariant),
            )
            NotePanel(
              page = pageIndex + 1,
              markdown = noteDraft,
              dirty = hasUnsavedChanges,
              writable = noteWritable,
              saving = isSaving,
              status = noteStatus,
              onMarkdownChange = { noteDraft = it },
              onSave = { scope.launch { saveCurrentNote() } },
              onReload = ::reloadCurrentNote,
              modifier = Modifier.weight(0.44f).fillMaxHeight(),
            )
          }
        } else {
          Column(modifier = Modifier.fillMaxSize()) {
            PdfPageViewport(
              bitmap = pageBitmap,
              sourceName = activeProject.sourceName,
              page = pageIndex,
              loading = isRendering,
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Box(
              modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                .height(1.dp).background(MaterialTheme.colorScheme.outlineVariant),
            )
            NotePanel(
              page = pageIndex + 1,
              markdown = noteDraft,
              dirty = hasUnsavedChanges,
              writable = noteWritable,
              saving = isSaving,
              status = noteStatus,
              onMarkdownChange = { noteDraft = it },
              onSave = { scope.launch { saveCurrentNote() } },
              onReload = ::reloadCurrentNote,
              modifier = Modifier.weight(1f).fillMaxWidth(),
            )
          }
        }
      }
    } else {
      Box(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (isOpening) {
          Text("正在打开项目…", style = MaterialTheme.typography.bodyLarge)
        } else {
          Text("选择一个 pagenote 项目文件夹", style = MaterialTheme.typography.bodyLarge)
        }
      }
    }
  }
}

@Composable
private fun PdfPageViewport(
  bitmap: Bitmap?,
  sourceName: String,
  page: Int,
  loading: Boolean,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    if (bitmap != null) {
      Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "$sourceName 第 ${page + 1} 页",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
      )
    } else if (loading) {
      Text("正在读取页面…", style = MaterialTheme.typography.bodyLarge)
    }
  }
}

@Composable
private fun NotePanel(
  page: Int,
  markdown: String,
  dirty: Boolean,
  writable: Boolean,
  saving: Boolean,
  status: String?,
  onMarkdownChange: (String) -> Unit,
  onSave: () -> Unit,
  onReload: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val markwon = remember(context) {
    Markwon.builder(context).usePlugin(TablePlugin.create(context)).build()
  }
  val markwonEditor = remember(markwon) { MarkwonEditor.create(markwon) }
  val executor = remember { Executors.newSingleThreadExecutor() }
  var preview by remember { mutableStateOf(false) }

  DisposableEffect(executor) {
    onDispose { executor.shutdownNow() }
  }

  Column(modifier = modifier.padding(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("第 $page 页笔记", style = MaterialTheme.typography.titleSmall)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onReload, enabled = !saving) { Text("重载") }
        Button(onClick = onSave, enabled = writable && dirty && !saving) {
          Text(if (saving) "保存中" else "保存")
        }
      }
    }
    TabRow(selectedTabIndex = if (preview) 1 else 0) {
      Tab(selected = !preview, onClick = { preview = false }, text = { Text("编辑") })
      Tab(selected = preview, onClick = { preview = true }, text = { Text("预览") })
    }
    if (!writable) {
      Text(
        "只读项目",
        modifier = Modifier.padding(top = 5.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.labelMedium,
      )
    }
    status?.let {
      Text(
        it,
        modifier = Modifier.padding(top = 5.dp),
        color = if (it.contains("冲突") || it.contains("失败")) {
          MaterialTheme.colorScheme.error
        } else {
          MaterialTheme.colorScheme.onSurfaceVariant
        },
        style = MaterialTheme.typography.labelMedium,
      )
    }
    key(page, preview) {
      if (preview) {
        AndroidView(
          modifier = Modifier.fillMaxSize().padding(top = 8.dp),
          factory = { viewContext ->
            TextView(viewContext).apply {
              textSize = 16f
              gravity = Gravity.TOP or Gravity.START
              setPadding(12, 10, 12, 10)
            }
          },
          update = { view -> markwon.setMarkdown(view, markdown) },
        )
      } else {
        AndroidView(
          modifier = Modifier.fillMaxSize().padding(top = 8.dp),
          factory = { viewContext ->
            EditText(viewContext).apply {
              gravity = Gravity.TOP or Gravity.START
              inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
              textSize = 16f
              isEnabled = writable && !saving
              setPadding(12, 10, 12, 10)
              setText(markdown)
              setSelection(text.length)
              addTextChangedListener(
                MarkwonEditorTextWatcher.withPreRender(markwonEditor, executor, this),
              )
              addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(editable: Editable?) {
                  onMarkdownChange(editable?.toString().orEmpty())
                }
              })
            }
          },
          update = { editText ->
            editText.isEnabled = writable && !saving
            if (editText.text.toString() != markdown) {
              editText.setText(markdown)
              editText.setSelection(editText.text.length)
            }
          },
        )
      }
    }
  }
}
