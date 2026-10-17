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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import io.pagenote.feature.ocr.OfflineOcrEngine
import io.pagenote.feature.ocr.OcrPolicy
import io.pagenote.feature.ocr.OcrResult
import io.pagenote.feature.ocr.OcrResultMapper
import io.pagenote.feature.ocr.OcrResultPolicy
import io.pagenote.feature.pen.PenCanvasTool
import io.pagenote.feature.pen.PenCanvasView
import io.pagenote.feature.pen.PenPageStore
import io.pagenote.feature.pen.PenSide
import io.pagenote.feature.pen.PenStroke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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

private data class ReaderProjectData(
  val project: OpenedProject,
  val noteText: String,
  val renderer: PdfDocumentSession,
  val noteWritable: Boolean,
  val penStore: ProjectPenStore,
  val penPageStore: PenPageStore,
  val penWritable: Boolean,
)

private data class PdfViewportBounds(
  val viewportWidthPx: Float,
  val viewportHeightPx: Float,
  val pageWidthPx: Float,
  val pageHeightPx: Float,
) {
  companion object {
    val ZERO = PdfViewportBounds(0f, 0f, 0f, 0f)
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
  var viewportTransform by remember(project?.sourceUri, pageIndex) {
    mutableStateOf(PdfViewportTransform.DEFAULT)
  }
  var viewportBounds by remember(project?.sourceUri, pageIndex) {
    mutableStateOf(PdfViewportBounds.ZERO)
  }
  var notesMarkdown by remember { mutableStateOf("") }
  var noteDraft by remember { mutableStateOf("") }
  var noteBaseRevision by remember { mutableStateOf(PageNotes.revision("")) }
  var noteWritable by remember { mutableStateOf(false) }
  var noteStatus by remember { mutableStateOf<String?>(null) }
  var penStore by remember { mutableStateOf<ProjectPenStore?>(null) }
  var penPageStore by remember { mutableStateOf(PenPageStore()) }
  var penStrokes by remember { mutableStateOf<List<PenStroke>>(emptyList()) }
  var penWritable by remember { mutableStateOf(false) }
  var penTool by remember { mutableStateOf(PenCanvasTool.PEN) }
  var penStatus by remember { mutableStateOf<String?>(null) }
  var failedPenSave by remember { mutableStateOf<Pair<Int, List<PenStroke>>?>(null) }
  var pendingPenSaves by remember { mutableIntStateOf(0) }
  val penSaveMutex = remember { Mutex() }
  var ocrEngine by remember { mutableStateOf<OfflineOcrEngine?>(null) }
  var ocrResult by remember { mutableStateOf<OcrResult?>(null) }
  var ocrResultPage by remember { mutableIntStateOf(0) }
  var showOcrResult by remember { mutableStateOf(false) }
  var isOcrRunning by remember { mutableStateOf(false) }
  var isOpening by remember { mutableStateOf(false) }
  var isRendering by remember { mutableStateOf(false) }
  var isSaving by remember { mutableStateOf(false) }
  val isPenSaving = pendingPenSaves > 0
  var errorMessage by remember { mutableStateOf<String?>(null) }
  val savedPageText = NotePage.pageAt(notesMarkdown, pageIndex + 1)
  val hasUnsavedChanges = noteDraft != savedPageText

  fun applyViewportZoom(zoomChange: Float) {
    val bounds = viewportBounds
    viewportTransform = PdfViewportTransform.applyGesture(
      current = viewportTransform,
      viewportWidthPx = bounds.viewportWidthPx,
      viewportHeightPx = bounds.viewportHeightPx,
      pageWidthPx = bounds.pageWidthPx,
      pageHeightPx = bounds.pageHeightPx,
      centroidX = bounds.viewportWidthPx / 2f,
      centroidY = bounds.viewportHeightPx / 2f,
      zoomChange = zoomChange,
      panX = 0f,
      panY = 0f,
    )
  }

  fun fitViewportWidth() {
    viewportTransform = PdfViewportTransform.fitWidth(
      viewportWidthPx = viewportBounds.viewportWidthPx,
      pageWidthPx = viewportBounds.pageWidthPx,
    )
  }

  suspend fun savePenPage(targetPage: Int, strokes: List<PenStroke>) {
    val store = penStore ?: return
    if (!penWritable) {
      penStatus = "只读项目：无法保存笔迹"
      return
    }
    val snapshot = strokes.toList()
    pendingPenSaves += 1
    try {
      penSaveMutex.withLock {
        withContext(Dispatchers.IO) {
          store.writePage(PenSide.DOCUMENT, targetPage, snapshot)
        }
      }
      if (failedPenSave?.first == targetPage) failedPenSave = null
      penStatus = "笔迹已保存"
    } catch (error: Exception) {
      if (error is CancellationException) throw error
      failedPenSave = targetPage to snapshot
      penStatus = "笔迹未保存：${error.message ?: "写入失败"}"
    } finally {
      pendingPenSaves -= 1
    }
  }

  fun applyPenStroke(stroke: PenStroke) {
    val targetPage = pageIndex + 1
    penPageStore.addStroke(PenSide.DOCUMENT, targetPage, stroke)
    val updated = penPageStore.getItems(PenSide.DOCUMENT, targetPage)
    penStrokes = updated
    scope.launch { savePenPage(targetPage, updated) }
  }

  fun erasePenStroke(index: Int) {
    val targetPage = pageIndex + 1
    if (penPageStore.eraseAt(PenSide.DOCUMENT, targetPage, index)) {
      val updated = penPageStore.getItems(PenSide.DOCUMENT, targetPage)
      penStrokes = updated
      scope.launch { savePenPage(targetPage, updated) }
    }
  }

  fun clearPenPage() {
    val targetPage = pageIndex + 1
    if (penPageStore.clearPage(PenSide.DOCUMENT, targetPage)) {
      penStrokes = emptyList()
      scope.launch { savePenPage(targetPage, emptyList()) }
    }
  }

  fun undoPenPage() {
    val targetPage = pageIndex + 1
    if (penPageStore.undo(PenSide.DOCUMENT, targetPage)) {
      val updated = penPageStore.getItems(PenSide.DOCUMENT, targetPage)
      penStrokes = updated
      scope.launch { savePenPage(targetPage, updated) }
    }
  }

  fun redoPenPage() {
    val targetPage = pageIndex + 1
    if (penPageStore.redo(PenSide.DOCUMENT, targetPage)) {
      val updated = penPageStore.getItems(PenSide.DOCUMENT, targetPage)
      penStrokes = updated
      scope.launch { savePenPage(targetPage, updated) }
    }
  }

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
    showOcrResult = false
    pageIndex = nextPage
  }

  fun recognizeCurrentPage() {
    val activeRenderer = renderer ?: return
    val targetPage = pageIndex
    showOcrResult = false
    scope.launch {
      isOcrRunning = true
      try {
        val result = withTimeout(OcrPolicy.TIMEOUT_MS) {
          val engine = ocrEngine ?: OfflineOcrEngine.create(context).also { ocrEngine = it }
          val input = withContext(Dispatchers.IO) { activeRenderer.render(targetPage) }
          try {
            engine.recognize(input)
          } finally {
            input.recycle()
          }
        }
        ocrResult = result
        ocrResultPage = targetPage
        showOcrResult = true
      } catch (timeout: TimeoutCancellationException) {
        ocrResult = OcrResult(
          engine = OcrResultMapper.ENGINE,
          status = "failed",
          warnings = listOf("OCR 超时。原始页面未改变，请稍后重试。"),
        )
        ocrResultPage = targetPage
        showOcrResult = true
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        ocrResult = OcrResult(
          engine = OcrResultMapper.ENGINE,
          status = "failed",
          warnings = listOf(error.message ?: "离线 OCR 初始化失败"),
        )
        ocrResultPage = targetPage
        showOcrResult = true
      } finally {
        isOcrRunning = false
      }
    }
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
            val projectPenStore = ProjectPenStore(
              context.contentResolver,
              opened.sourceDirectoryUri,
              opened.annotationsUri,
            )
            val loadedPenPages = PenPageStore().apply {
              loadAnnotationsMap(
                mapOf(PenSide.DOCUMENT.key to projectPenStore.readPages(PenSide.DOCUMENT)),
              )
            }
            val supportsPenWrite = projectPenStore.supportsWrite()
            val pdf = PdfDocumentSession.open(context.contentResolver, opened.sourceUri)
            ReaderProjectData(
              project = opened,
              noteText = noteText,
              renderer = pdf,
              noteWritable = writable && supportsNoteWrite,
              penStore = projectPenStore,
              penPageStore = loadedPenPages,
              penWritable = writable && supportsPenWrite,
            )
          }
          renderer = loaded.renderer
          project = loaded.project
          notesMarkdown = loaded.noteText
          penStore = loaded.penStore
          penPageStore = loaded.penPageStore
          penStrokes = loaded.penPageStore.getItems(PenSide.DOCUMENT, 1)
          penWritable = loaded.penWritable
          failedPenSave = null
          penStatus = if (loaded.penWritable) null else "只读项目：笔迹不可编辑"
          val firstPage = NotePage.pageAt(loaded.noteText, 1)
          noteDraft = firstPage
          noteBaseRevision = PageNotes.revision(firstPage)
          noteWritable = loaded.noteWritable
          noteStatus = if (noteWritable) null else "只读项目：存储位置未授予笔记写入权限"
          ocrResult = null
          showOcrResult = false
          pageIndex = 0
        } catch (error: Exception) {
          errorMessage = error.message ?: "项目打开失败"
        } finally {
          isOpening = false
        }
      }
    }
  }

  val rendererToClose = renderer
  DisposableEffect(rendererToClose) {
    onDispose { rendererToClose?.close() }
  }
  DisposableEffect(Unit) {
    onDispose { pageBitmap?.recycle() }
  }
  val ocrEngineToClose = ocrEngine
  DisposableEffect(ocrEngineToClose) {
    onDispose {
      ocrEngineToClose?.let { engine ->
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { engine.close() }
      }
    }
  }

  LaunchedEffect(notesMarkdown, pageIndex) {
    val markdown = NotePage.pageAt(notesMarkdown, pageIndex + 1)
    noteDraft = markdown
    noteBaseRevision = PageNotes.revision(markdown)
  }

  LaunchedEffect(pageIndex) {
    noteStatus = null
  }

  LaunchedEffect(pageIndex, penPageStore) {
    penStrokes = penPageStore.getItems(PenSide.DOCUMENT, pageIndex + 1)
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
        enabled = !isOpening && !isSaving && !isPenSaving && failedPenSave == null && !isOcrRunning,
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
          enabled = pageIndex > 0 && !isRendering && !isSaving && !isPenSaving &&
            failedPenSave == null && !isOcrRunning,
        ) {
          Text("上一页")
        }
        Text("${pageIndex + 1} / ${activeRenderer.pageCount}")
        Button(
          onClick = ::recognizeCurrentPage,
          enabled = !isRendering && !isOcrRunning,
        ) {
          Text(if (isOcrRunning) "识别中" else "OCR")
        }
        Button(
          onClick = {
            scope.launch {
              changePage((pageIndex + 1).coerceAtMost(activeRenderer.pageCount - 1))
            }
          },
          enabled = pageIndex < activeRenderer.pageCount - 1 &&
            !isRendering && !isSaving && !isPenSaving && failedPenSave == null && !isOcrRunning,
        ) {
          Text("下一页")
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
          .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          onClick = { applyViewportZoom(0.8f) },
          enabled = viewportTransform.scale > PdfViewportTransform.MIN_SCALE,
        ) { Text("−") }
        Text("${(viewportTransform.scale * 100f).roundToInt()}%")
        TextButton(
          onClick = { applyViewportZoom(1.25f) },
          enabled = viewportTransform.scale < PdfViewportTransform.MAX_SCALE,
        ) { Text("+") }
        TextButton(onClick = ::fitViewportWidth) { Text("适宽") }
        TextButton(onClick = { viewportTransform = PdfViewportTransform.DEFAULT }) {
          Text("适页")
        }
        listOf(
          PenCanvasTool.PEN to "笔",
          PenCanvasTool.HIGHLIGHTER to "荧光笔",
          PenCanvasTool.ERASER to "橡皮",
        ).forEach { (tool, label) ->
          if (penTool == tool) {
            Button(onClick = { penTool = tool }, enabled = penWritable) { Text(label) }
          } else {
            TextButton(onClick = { penTool = tool }, enabled = penWritable) { Text(label) }
          }
        }
        TextButton(
          onClick = ::undoPenPage,
          enabled = penWritable && penPageStore.canUndo(PenSide.DOCUMENT, pageIndex + 1),
        ) { Text("撤销") }
        TextButton(
          onClick = ::redoPenPage,
          enabled = penWritable && penPageStore.canRedo(PenSide.DOCUMENT, pageIndex + 1),
        ) { Text("重做") }
        TextButton(
          onClick = ::clearPenPage,
          enabled = penWritable && penStrokes.isNotEmpty(),
        ) { Text("清空本页") }
      }
      penStatus?.let { status ->
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = status,
            modifier = Modifier.weight(1f),
            color = if (status.contains("未保存") || status.contains("只读")) {
              MaterialTheme.colorScheme.error
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelMedium,
          )
          failedPenSave?.let { (targetPage, strokes) ->
            TextButton(
              onClick = { scope.launch { savePenPage(targetPage, strokes) } },
              enabled = !isPenSaving,
            ) { Text("重试") }
          }
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
              strokes = penStrokes,
              tool = penTool,
              inputEnabled = penWritable,
              onStrokeAdded = ::applyPenStroke,
              onStrokeErased = ::erasePenStroke,
              transform = viewportTransform,
              onTransformChanged = { viewportTransform = it },
              onBoundsChanged = { viewportBounds = it },
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
              strokes = penStrokes,
              tool = penTool,
              inputEnabled = penWritable,
              onStrokeAdded = ::applyPenStroke,
              onStrokeErased = ::erasePenStroke,
              transform = viewportTransform,
              onTransformChanged = { viewportTransform = it },
              onBoundsChanged = { viewportBounds = it },
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

  if (showOcrResult) {
    ocrResult?.let { result ->
      OcrResultDialog(
        page = ocrResultPage + 1,
        result = result,
        onDismiss = { showOcrResult = false },
      )
    }
  }
}

@Composable
private fun PdfPageViewport(
  bitmap: Bitmap?,
  sourceName: String,
  page: Int,
  loading: Boolean,
  strokes: List<PenStroke>,
  tool: PenCanvasTool,
  inputEnabled: Boolean,
  onStrokeAdded: (PenStroke) -> Unit,
  onStrokeErased: (Int) -> Unit,
  transform: PdfViewportTransform,
  onTransformChanged: (PdfViewportTransform) -> Unit,
  onBoundsChanged: (PdfViewportBounds) -> Unit,
  modifier: Modifier = Modifier,
) {
  val latestTransform by rememberUpdatedState(transform)
  val latestOnTransformChanged by rememberUpdatedState(onTransformChanged)

  BoxWithConstraints(
    modifier = modifier.clipToBounds(),
    contentAlignment = Alignment.Center,
  ) {
    if (bitmap != null) {
      val pageAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
      val viewportAspectRatio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
      val contentWidth = if (viewportAspectRatio > pageAspectRatio) {
        maxHeight * pageAspectRatio
      } else {
        maxWidth
      }
      val contentHeight = contentWidth / pageAspectRatio
      val density = LocalDensity.current
      val bounds = with(density) {
        PdfViewportBounds(
          viewportWidthPx = maxWidth.toPx(),
          viewportHeightPx = maxHeight.toPx(),
          pageWidthPx = contentWidth.toPx(),
          pageHeightPx = contentHeight.toPx(),
        )
      }
      LaunchedEffect(page, bounds) { onBoundsChanged(bounds) }

      Box(
        modifier = Modifier.fillMaxSize().pointerInput(page, bounds) {
          awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
              val event = awaitPointerEvent()
              val touchChanges = event.changes.filter {
                it.type == PointerType.Touch && it.pressed && it.previousPressed
              }
              val pointers = touchChanges.map { change ->
                PdfViewportPointer(change.id.value, change.position.x, change.position.y)
              }
              val previousPointers = touchChanges.map { change ->
                PdfViewportPointer(
                  change.id.value,
                  change.previousPosition.x,
                  change.previousPosition.y,
                )
              }
              val gesture = PdfViewportTransform.calculateTwoPointerGesture(
                previous = previousPointers,
                current = pointers,
              )
              if (gesture != null) {
                latestOnTransformChanged(
                  PdfViewportTransform.applyGesture(
                    current = latestTransform,
                    viewportWidthPx = bounds.viewportWidthPx,
                    viewportHeightPx = bounds.viewportHeightPx,
                    pageWidthPx = bounds.pageWidthPx,
                    pageHeightPx = bounds.pageHeightPx,
                    centroidX = gesture.centroidX,
                    centroidY = gesture.centroidY,
                    zoomChange = gesture.zoomChange,
                    panX = gesture.panX,
                    panY = gesture.panY,
                  ),
                )
                touchChanges.forEach { it.consume() }
              }
            } while (event.changes.any { it.pressed })
          }
        },
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier = Modifier.width(contentWidth).height(contentHeight).graphicsLayer {
            scaleX = transform.scale
            scaleY = transform.scale
            translationX = transform.translationX
            translationY = transform.translationY
          },
          contentAlignment = Alignment.Center,
        ) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "$sourceName 第 ${page + 1} 页",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
          )
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext -> PenCanvasView(viewContext) },
            update = { view ->
              view.strokes = strokes
              view.selectedTool = tool
              view.inputEnabled = inputEnabled
              view.onStrokeAdded = onStrokeAdded
              view.onStrokeErased = onStrokeErased
            },
          )
        }
      }
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

@Composable
private fun OcrResultDialog(
  page: Int,
  result: OcrResult,
  onDismiss: () -> Unit,
) {
  val needsReview = OcrResultPolicy.needsVisualReview(result)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("第 $page 页 OCR") },
    text = {
      Column(
        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text("状态：${result.status} · ${result.engine}")
        if (needsReview) {
          Text("识别结果需要对照原图核实", color = MaterialTheme.colorScheme.error)
        }
        result.warnings.forEach { warning ->
          Text(warning, color = MaterialTheme.colorScheme.error)
        }
        if (result.blocks.isEmpty()) {
          Text("没有可显示的识别文字。")
        }
        result.blocks.forEachIndexed { index, block ->
          Text("${index + 1}. ${block.text}")
          Text(
            "置信度 ${(block.confidence * 100).toInt()}% · " +
              "位置 x=${block.x.toInt()}‰ y=${block.y.toInt()}‰ " +
              "宽=${block.width.toInt()}‰ 高=${block.height.toInt()}‰",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
  )
}
