package com.example.chineseforme.ui.stroke

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chineseforme.ui.components.AnchoredGlossPopup
import com.example.chineseforme.ui.theme.Parchment
import com.example.chineseforme.ui.theme.TileEdge
import com.example.chineseforme.ui.theme.TileFace
import com.example.chineseforme.ui.theme.TileSelected

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StrokeScreen(
    viewModel: StrokeViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val practice = state.practice
    val glossPopup = viewModel.glossPopup()

    Scaffold(
        containerColor = Parchment,
        topBar = {
            TopAppBar(
                title = {
                    Text("Stroke ${state.sentenceIndex + 1}/${state.sentenceCount.coerceAtLeast(1)}")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::goPrevSentence,
                        enabled = state.sentenceIndex > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous")
                    }
                    IconButton(
                        onClick = viewModel::goNextSentence,
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
                    Text("Loading sentence…")
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Characters in sentence", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.characters.forEachIndexed { index, ch ->
                            val selected = index == state.selectedIndex
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 40.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (selected) TileSelected else TileFace)
                                    .border(1.dp, TileEdge, RoundedCornerShape(4.dp))
                                    .clickable { viewModel.selectCharacter(index) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(ch.toString(), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    if (state.loadingChar) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Fetching stroke data…")
                        }
                    }

                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }

                    // Pinyin + character info strip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(TileFace)
                            .border(1.dp, TileEdge, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = state.pinyin.ifBlank { "—" },
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val strokeCount = state.strokeData?.strokeCount
                        if (strokeCount != null) {
                            Text(
                                "$strokeCount strokes",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.radicalLabel.isNotBlank()) {
                            Text(
                                state.radicalLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val defButton: @Composable () -> Unit = {
                            TextButton(
                                onClick = viewModel::toggleGlossPopup,
                                enabled = state.strokeData != null
                            ) {
                                Text("Def")
                            }
                        }
                        if (state.showGlossPopup && glossPopup != null) {
                            AnchoredGlossPopup(
                                popup = glossPopup,
                                glossLoading = state.sensesLoading,
                                glossFailed = !state.sensesLoading && state.senses.isEmpty(),
                                fromOnline = state.sensesFromOnline,
                                onDismiss = viewModel::dismissGlossPopup,
                                content = defButton
                            )
                        } else {
                            defButton()
                        }
                    }

                    StrokeCanvas(
                        data = state.strokeData,
                        completedStrokeCount = practice?.completedStrokeCount ?: 0,
                        hintStrokeIndex = practice?.hintFlashStrokeIndex,
                        guideDimLevel = practice?.guideDimLevel ?: 0,
                        interactive = practice != null &&
                            !practice.failed &&
                            !practice.complete &&
                            !state.loadingChar,
                        onStrokeFinished = viewModel::submitStroke
                    )

                    if (practice != null) {
                        Text(
                            "Stroke ${practice.completedStrokeCount}/${practice.strokeCount}" +
                                " · guide ${practice.guideDimLevel + 1}/5" +
                                if (practice.complete) " · complete" else
                                    if (practice.failed) " · failed" else "",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Mistakes ${practice.mistakes}/${practice.allowedMistakes}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        practice.statusMessage?.let { msg ->
                            Text(
                                msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (practice.complete || practice.failed) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = viewModel::retry) {
                                    Text("Retry")
                                }
                                OutlinedButton(onClick = viewModel::advanceAndRetry) {
                                    Text(
                                        if (practice.guideDimLevel >=
                                            com.example.chineseforme.domain.stroke.STROKE_MAX_DIM_LEVEL
                                        ) {
                                            "Retry (max dim)"
                                        } else {
                                            "Advance & retry"
                                        }
                                    )
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = viewModel::hint) {
                                    Text("Hint (${practice.hintsRemaining})")
                                }
                                OutlinedButton(onClick = viewModel::resetAttempt) {
                                    Text("Reset attempt")
                                }
                            }
                        }
                        state.stats?.let { stats ->
                            Text(
                                "Stats · ${stats.successes}/${stats.attempts} ok · " +
                                    "fails ${stats.fails} · best streak ${stats.bestStreak}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        "Stroke outlines from Make Me a Hanzi / Hanzi Writer data (Arphic Public License).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
