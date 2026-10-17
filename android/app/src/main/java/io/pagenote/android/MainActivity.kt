package io.pagenote.android

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
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
  var isOpening by remember { mutableStateOf(false) }
  var isRendering by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val projectPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { treeUri: Uri? ->
    if (treeUri != null) {
      scope.launch {
        isOpening = true
        errorMessage = null
        try {
          context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
          val opened = withContext(Dispatchers.IO) {
            ProjectDocumentOpener(context.contentResolver).open(treeUri)
          }
          require(opened.sourceExtension == "pdf") {
            "当前版本暂不支持 ${opened.sourceExtension.ifBlank { "此" }} 格式阅读"
          }
          val newRenderer = withContext(Dispatchers.IO) {
            PdfDocumentSession.open(context.contentResolver, opened.sourceUri)
          }
          renderer = newRenderer
          project = opened
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
      Button(onClick = { projectPicker.launch(null) }, enabled = !isOpening) {
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
          onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
          enabled = pageIndex > 0 && !isRendering,
        ) {
          Text("上一页")
        }
        Text("${pageIndex + 1} / ${activeRenderer.pageCount}")
        Button(
          onClick = { pageIndex = (pageIndex + 1).coerceAtMost(activeRenderer.pageCount - 1) },
          enabled = pageIndex < activeRenderer.pageCount - 1 && !isRendering,
        ) {
          Text("下一页")
        }
      }
    }

    Box(
      modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
      contentAlignment = Alignment.Center,
    ) {
      val image = pageBitmap
      if (image != null) {
        Image(
          bitmap = image.asImageBitmap(),
          contentDescription = "${activeProject?.sourceName.orEmpty()} 第 ${pageIndex + 1} 页",
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Fit,
        )
      } else if (isOpening || isRendering) {
        Text("正在读取页面…", style = MaterialTheme.typography.bodyLarge)
      } else if (activeProject == null) {
        Text("选择一个 pagenote 项目文件夹", style = MaterialTheme.typography.bodyLarge)
      }
    }
  }
}
