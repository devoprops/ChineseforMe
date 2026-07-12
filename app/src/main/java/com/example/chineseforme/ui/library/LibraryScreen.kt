package com.example.chineseforme.ui.library

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chineseforme.data.db.TextWorkEntity
import com.example.chineseforme.data.importing.BundledText
import com.example.chineseforme.domain.model.StudyMode
import com.example.chineseforme.ui.theme.Parchment
import com.example.chineseforme.ui.theme.TileFace

private enum class ImportMode { Library, Paste, File, Link, Bundled }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenMode: (Long, StudyMode) -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showImport by remember { mutableStateOf(false) }
    var importStartMode by remember { mutableStateOf(ImportMode.Paste) }

    Scaffold(
        containerColor = Parchment,
        topBar = {
            TopAppBar(
                title = { Text("Chinese for Me") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            HomeSection(title = "Current text") {
                CurrentTextBlock(
                    work = state.currentWork,
                    onClear = viewModel::clearCurrentWork
                )
            }

            HomeSection(title = "Import") {
                Text(
                    "Bring a text in, or choose one already stored. It becomes the current text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            importStartMode = ImportMode.Library
                            showImport = true
                        }
                    ) { Text("Library") }
                    OutlinedButton(
                        onClick = {
                            importStartMode = ImportMode.Paste
                            showImport = true
                        }
                    ) { Text("Paste") }
                    OutlinedButton(
                        onClick = {
                            importStartMode = ImportMode.File
                            showImport = true
                        }
                    ) { Text("File") }
                    OutlinedButton(
                        onClick = {
                            importStartMode = ImportMode.Link
                            showImport = true
                        }
                    ) { Text("Link") }
                    OutlinedButton(
                        onClick = {
                            importStartMode = ImportMode.Bundled
                            showImport = true
                        }
                    ) { Text("Bundled") }
                }
            }

            HomeSection(title = "Study modes") {
                Text(
                    "Opens sentence selection for that mode, using the current text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val workId = state.currentWork?.id
                val enabled = workId != null
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { workId?.let { onOpenMode(it, StudyMode.Standard) } },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Standard") }
                    Button(
                        onClick = { workId?.let { onOpenMode(it, StudyMode.Memorize) } },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Memorize") }
                    Button(
                        onClick = { workId?.let { onOpenMode(it, StudyMode.Stroke) } },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Stroke practice") }
                }
                if (!enabled) {
                    Text(
                        "Import or select a text first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showImport) {
        ImportDialog(
            initialMode = importStartMode,
            libraryWorks = state.libraryWorks,
            bundled = state.bundled,
            importing = state.importing,
            error = state.error,
            onDismiss = {
                viewModel.clearError()
                showImport = false
            },
            onSelectLibraryWork = { work ->
                viewModel.setCurrentWork(work.id)
                showImport = false
            },
            onPaste = { title, content ->
                viewModel.importPaste(title, content) {
                    showImport = false
                }
            },
            onFile = { uri, name, mime ->
                viewModel.importUri(uri, name, mime) {
                    showImport = false
                }
            },
            onLink = { url ->
                viewModel.importUrl(url) {
                    showImport = false
                }
            },
            onBundled = { item ->
                viewModel.loadBundled(item) {
                    showImport = false
                }
            },
            onClearError = viewModel::clearError
        )
    }

    if (state.importing && !showImport) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Loading") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Preparing text…")
                }
            },
            confirmButton = {}
        )
    }

    state.error?.takeIf { !showImport }?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Import error") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("OK") }
            }
        )
    }
}

@Composable
private fun HomeSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun CurrentTextBlock(
    work: TextWorkEntity?,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = TileFace,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (work == null) {
                Text(
                    "None selected",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Import a text or pick one from your library to begin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(work.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    work.content.take(120).replace("\n", " ") +
                        if (work.content.length > 120) "…" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Last at sentence ${work.lastOpenedSentenceIndex + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onClear,
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) { Text("Clear current") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportDialog(
    initialMode: ImportMode,
    libraryWorks: List<TextWorkEntity>,
    bundled: List<BundledText>,
    importing: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSelectLibraryWork: (TextWorkEntity) -> Unit,
    onPaste: (String, String) -> Unit,
    onFile: (android.net.Uri, String?, String?) -> Unit,
    onLink: (String) -> Unit,
    onBundled: (BundledText) -> Unit,
    onClearError: () -> Unit
) {
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        var displayName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) {
                displayName = cursor.getString(idx)
            }
        }
        val mime = context.contentResolver.getType(uri)
        onFile(uri, displayName, mime)
    }

    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        title = { Text("Import text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = mode == ImportMode.Library,
                        onClick = { mode = ImportMode.Library },
                        label = { Text("Library") }
                    )
                    FilterChip(
                        selected = mode == ImportMode.Paste,
                        onClick = { mode = ImportMode.Paste },
                        label = { Text("Paste") }
                    )
                    FilterChip(
                        selected = mode == ImportMode.File,
                        onClick = { mode = ImportMode.File },
                        label = { Text("File") }
                    )
                    FilterChip(
                        selected = mode == ImportMode.Link,
                        onClick = { mode = ImportMode.Link },
                        label = { Text("Link") }
                    )
                    FilterChip(
                        selected = mode == ImportMode.Bundled,
                        onClick = { mode = ImportMode.Bundled },
                        label = { Text("Bundled") }
                    )
                }

                when (mode) {
                    ImportMode.Library -> {
                        Text(
                            "Stored texts not currently loaded. Tap one to make it current.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (libraryWorks.isEmpty()) {
                            Text(
                                "No other stored texts yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            libraryWorks.forEach { work ->
                                TextButton(
                                    onClick = { onSelectLibraryWork(work) },
                                    enabled = !importing
                                ) { Text(work.title) }
                            }
                        }
                    }
                    ImportMode.Paste -> {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("Chinese text") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5
                        )
                    }
                    ImportMode.File -> {
                        Text(
                            "Choose a .txt, .pdf, or .docx file. Text is extracted for study.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(
                            onClick = {
                                filePicker.launch(
                                    arrayOf(
                                        "text/*",
                                        "application/pdf",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "application/msword"
                                    )
                                )
                            },
                            enabled = !importing
                        ) { Text("Browse files") }
                    }
                    ImportMode.Link -> {
                        Text(
                            "Paste a direct URL to a .txt, .pdf, or .docx file.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("https://…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ImportMode.Bundled -> {
                        if (bundled.isEmpty()) {
                            Text("No bundled texts found in assets/texts.")
                        } else {
                            bundled.forEach { item ->
                                TextButton(
                                    onClick = { onBundled(item) },
                                    enabled = !importing
                                ) { Text(item.title) }
                            }
                        }
                    }
                }

                if (importing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Importing…")
                    }
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            when (mode) {
                ImportMode.Paste -> TextButton(
                    onClick = {
                        onClearError()
                        onPaste(title.ifBlank { "Untitled" }, content)
                    },
                    enabled = content.isNotBlank() && !importing
                ) { Text("Import") }
                ImportMode.Link -> TextButton(
                    onClick = {
                        onClearError()
                        onLink(url.trim())
                    },
                    enabled = url.isNotBlank() && !importing
                ) { Text("Download") }
                ImportMode.Library, ImportMode.File, ImportMode.Bundled -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !importing) { Text("Cancel") }
        }
    )
}
