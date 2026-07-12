package com.example.chineseforme.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chineseforme.domain.model.SentenceReading
import android.os.SystemClock
import com.example.chineseforme.ui.components.GlossPopup
import com.example.chineseforme.ui.components.GroupedTileRow
import com.example.chineseforme.ui.theme.Parchment

private fun isSameGlossFocus(current: GlossPopup?, next: GlossPopup): Boolean {
    return when {
        current == null -> false
        current is GlossPopup.Group && next is GlossPopup.Group ->
            current.group.groupId == next.group.groupId
        current is GlossPopup.Character && next is GlossPopup.Character ->
            current.tile.index == next.tile.index
        else -> false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    viewModel: StudyViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var glossPopup by remember { mutableStateOf<GlossPopup?>(null) }
    var glossDismissIgnoreUntil by remember { mutableStateOf(0L) }
    var showParallelEditor by remember { mutableStateOf(false) }

    fun setGlossPopup(next: GlossPopup?) {
        // Avoid Popup outside-dismiss clearing a freshly selected tile in the same gesture.
        glossDismissIgnoreUntil = SystemClock.uptimeMillis() + 150
        glossPopup = next
    }

    LaunchedEffect(state.sentence?.id) {
        glossPopup = null
    }

    val glossSurface = when (val popup = glossPopup) {
        is GlossPopup.Character -> popup.tile.char
        is GlossPopup.Group -> popup.group.surface
        null -> null
    }
    val glossNeedsOnline = when (val popup = glossPopup) {
        is GlossPopup.Character -> popup.tile.senses.isEmpty()
        is GlossPopup.Group -> popup.group.senses.isEmpty()
        null -> false
    }

    LaunchedEffect(glossSurface, glossNeedsOnline) {
        if (glossSurface != null && glossNeedsOnline) {
            viewModel.requestOnlineGloss(glossSurface)
        }
    }

    LaunchedEffect(state.analysis, state.onlineGlossCache, glossPopup) {
        val popup = glossPopup ?: return@LaunchedEffect
        when (popup) {
            is GlossPopup.Character -> {
                if (popup.tile.senses.isNotEmpty()) return@LaunchedEffect
                val fromAnalysis = state.analysis?.tiles?.find { it.index == popup.tile.index }
                when {
                    fromAnalysis != null && fromAnalysis.senses.isNotEmpty() -> {
                        glossPopup = GlossPopup.Character(fromAnalysis)
                    }
                    state.onlineGlossCache[popup.tile.char] != null -> {
                        glossPopup = GlossPopup.Character(
                            popup.tile.copy(
                                senses = listOf(state.onlineGlossCache.getValue(popup.tile.char))
                            )
                        )
                    }
                }
            }
            is GlossPopup.Group -> {
                if (popup.group.senses.isNotEmpty()) return@LaunchedEffect
                val fromAnalysis = state.analysis?.groups?.find { it.groupId == popup.group.groupId }
                when {
                    fromAnalysis != null && fromAnalysis.senses.isNotEmpty() -> {
                        glossPopup = GlossPopup.Group(fromAnalysis)
                    }
                    state.onlineGlossCache[popup.group.surface] != null -> {
                        glossPopup = GlossPopup.Group(
                            popup.group.copy(
                                senses = listOf(
                                    state.onlineGlossCache.getValue(popup.group.surface)
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = Parchment,
        topBar = {
            TopAppBar(
                title = {
                    Text("Study ${state.sentenceIndex + 1}/${state.sentenceCount.coerceAtLeast(1)}")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::goPrev, enabled = state.sentenceIndex > 0) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous")
                    }
                    IconButton(
                        onClick = viewModel::goNext,
                        enabled = state.sentenceIndex < state.sentenceCount - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Parchment)
            )
        }
    ) { padding ->
        when {
            state.loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Loading dictionary / analysis…")
                }
            }
            state.error != null -> {
                Text(
                    state.error.orEmpty(),
                    modifier = Modifier.padding(padding).padding(16.dp)
                )
            }
            else -> {
                val analysis = state.analysis ?: return@Scaffold
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(analysis.text, style = MaterialTheme.typography.titleLarge)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = state.groupMode,
                            onClick = {
                                glossPopup = null
                                viewModel.toggleGroupMode()
                            },
                            label = { Text(if (state.groupMode) "Group mode on" else "Group mode") }
                        )
                        if (state.groupMode) {
                            Button(
                                onClick = viewModel::mergeSelected,
                                enabled = state.selectedIndices.size >= 2
                            ) { Text("Merge") }
                            OutlinedButton(
                                onClick = viewModel::splitSelectedGroup,
                                enabled = state.selectedIndices.size == 1
                            ) { Text("Split") }
                        }
                    }

                    if (state.groupMode) {
                        Text(
                            "Select contiguous characters, then Merge. Tap one char in a group and Split to break it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Tap a group for phrase senses · long-press for character senses",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    GroupedTileRow(
                        groups = analysis.groups,
                        showPinyin = settings.showPinyinOnTiles,
                        selectedIndices = state.selectedIndices,
                        glossPopup = glossPopup,
                        groupMode = state.groupMode,
                        onGroupTap = { group, tile ->
                            val hanCount = group.tiles.count { !it.isPunctuation }
                            val next = if (hanCount <= 1) {
                                GlossPopup.Character(tile)
                            } else {
                                GlossPopup.Group(group)
                            }
                            setGlossPopup(
                                if (isSameGlossFocus(glossPopup, next)) null else next
                            )
                        },
                        onCharacterLongPress = { tile ->
                            val next = GlossPopup.Character(tile)
                            setGlossPopup(
                                if (isSameGlossFocus(glossPopup, next)) null else next
                            )
                        },
                        onGroupModeTileClick = viewModel::onTileClick,
                        onDismissGloss = {
                            if (SystemClock.uptimeMillis() >= glossDismissIgnoreUntil) {
                                glossPopup = null
                            }
                        },
                        glossLoading = glossSurface != null &&
                            glossSurface in state.onlineGlossLoading,
                        glossFailed = glossSurface != null &&
                            glossSurface in state.onlineGlossFailed,
                        fromOnline = glossSurface != null &&
                            glossSurface in state.onlineGlossCache
                    )

                    Spacer(Modifier.height(4.dp))
                    Text("Notional translation", style = MaterialTheme.typography.titleMedium)
                    if (state.translating) {
                        Text(
                            "Generating sentence translation…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val fullReadings = analysis.readings.filter {
                        it.kind == SentenceReading.Kind.FullSentence
                    }
                    if (!state.translating && fullReadings.isEmpty()) {
                        Text(
                            "No stored notional translation yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        fullReadings.forEach { reading ->
                            Text(
                                reading.text,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showParallelEditor = true }) {
                            Text("Edit translation")
                        }
                        TextButton(
                            onClick = viewModel::regenerateNotional,
                            enabled = !state.translating
                        ) { Text("Regenerate") }
                        TextButton(
                            onClick = viewModel::clearAllNotionalsForWork,
                            enabled = !state.translating
                        ) { Text("Clear book drafts") }
                    }

                    val glossChain = analysis.readings.firstOrNull {
                        it.kind == SentenceReading.Kind.GlossChain
                    }
                    if (glossChain != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Word gloss chain (rough)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            glossChain.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showParallelEditor) {
        var draft by remember(state.sentence?.id) {
            mutableStateOf(state.sentence?.parallelEnglish.orEmpty())
        }
        AlertDialog(
            onDismissRequest = { showParallelEditor = false },
            title = { Text("Notional translation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Stored one-for-one with this sentence in your library. Edits persist until you delete the book.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("English") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveParallelEnglish(draft)
                        showParallelEditor = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showParallelEditor = false }) { Text("Cancel") }
            }
        )
    }
}
