package com.example.chineseforme.ui.library

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.chineseforme.data.importing.BundledText
import com.example.chineseforme.ui.theme.Parchment
import com.example.chineseforme.ui.theme.TileFace

private enum class ImportMode { Paste, File, Link, Library }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenWork: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val works by viewModel.works.collectAsStateWithLifecycle()
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    var showImport by remember { mutableStateOf(false) }

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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showImport = true }) {
                Icon(Icons.Default.Add, contentDescription = "Import text")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (extras.bundled.isNotEmpty()) {
                item {
                    Text("Bundled texts", style = MaterialTheme.typography.titleMedium)
                }
                items(extras.bundled, key = { it.sourceKey }) { bundled ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.loadBundled(bundled) { id -> onOpenWork(id) }
                            },
                        color = TileFace,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(bundled.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (bundled.parallelEnglishAssetPath != null) {
                                    "Tap to open · English 2014 paired for notional translations"
                                } else {
                                    "Tap to open · select a paragraph to study"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    if (works.isEmpty()) "Your imported works" else "Imported works",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (works.isEmpty()) {
                item {
                    Text(
                        "Import via paste, file (.txt / .pdf / .docx), or link. Bundled texts stay available above.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(works, key = { it.id }) { work ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenWork(work.id) },
                        color = TileFace,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(work.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                work.content.take(80).replace("\n", " ") +
                                    if (work.content.length > 80) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showImport) {
        ImportDialog(
            bundled = extras.bundled,
            importing = extras.importing,
            error = extras.error,
            onDismiss = {
                viewModel.clearError()
                showImport = false
            },
            onPaste = { title, content ->
                viewModel.importPaste(title, content) { id ->
                    showImport = false
                    onOpenWork(id)
                }
            },
            onFile = { uri, name, mime ->
                viewModel.importUri(uri, name, mime) { id ->
                    showImport = false
                    onOpenWork(id)
                }
            },
            onLink = { url ->
                viewModel.importUrl(url) { id ->
                    showImport = false
                    onOpenWork(id)
                }
            },
            onBundled = { item ->
                viewModel.loadBundled(item) { id ->
                    showImport = false
                    onOpenWork(id)
                }
            },
            onClearError = viewModel::clearError
        )
    }

    if (extras.importing && !showImport) {
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

    extras.error?.takeIf { !showImport }?.let { message ->
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
private fun ImportDialog(
    bundled: List<BundledText>,
    importing: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onPaste: (String, String) -> Unit,
    onFile: (android.net.Uri, String?, String?) -> Unit,
    onLink: (String) -> Unit,
    onBundled: (BundledText) -> Unit,
    onClearError: () -> Unit
) {
    var mode by remember { mutableStateOf(ImportMode.Paste) }
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        selected = mode == ImportMode.Library,
                        onClick = { mode = ImportMode.Library },
                        label = { Text("Bundled") }
                    )
                }

                when (mode) {
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
                            "Choose a .txt, .pdf, or .docx file. Text is extracted for paragraph study.",
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
                    ImportMode.Library -> {
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
                ImportMode.File, ImportMode.Library -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !importing) { Text("Cancel") }
        }
    )
}
