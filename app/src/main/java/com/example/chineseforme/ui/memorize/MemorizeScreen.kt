package com.example.chineseforme.ui.memorize

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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chineseforme.domain.memorize.MemorizeSlot
import com.example.chineseforme.ui.theme.Parchment
import com.example.chineseforme.ui.theme.TileEdge
import com.example.chineseforme.ui.theme.TileFace
import com.example.chineseforme.ui.theme.TileSelected

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemorizeScreen(
    viewModel: MemorizeViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val session = state.session
    val showSkeletonPinyin = settings.showPinyinOnMemorizeSkeleton
    val showPoolPinyin = settings.showPinyinOnMemorizePool

    Scaffold(
        containerColor = Parchment,
        topBar = {
            TopAppBar(
                title = {
                    Text("Memorize ${state.sentenceIndex + 1}/${state.sentenceCount.coerceAtLeast(1)}")
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
                    Text("Preparing memorize session…")
                }
            }
            state.error != null -> {
                Text(
                    state.error.orEmpty(),
                    modifier = Modifier.padding(padding).padding(16.dp)
                )
            }
            session != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "${session.currentFilled}/${session.totalHan} filled · best ${session.bestFilled}/${session.totalHan}" +
                            " · mistakes ${session.mistakes}/${session.allowedMistakes}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    session.statusMessage?.let { msg ->
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text("Sentence", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        session.slots.forEach { slot ->
                            SkeletonTile(
                                slot = slot,
                                focused = slot.index == session.focusIndex,
                                // Blanks: optional expected reading. Filled: always carry pool pinyin.
                                showPinyin = if (slot.isBlankHan) {
                                    showSkeletonPinyin
                                } else {
                                    !slot.isPunctuation
                                },
                                pinyin = state.pinyinByChar[slot.expected].orEmpty()
                            )
                        }
                    }

                    if (!session.complete) {
                        Text("Choose the next character", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            session.pool.forEach { ch ->
                                PoolTile(
                                    char = ch,
                                    highlighted = ch in session.flashChars,
                                    showPinyin = showPoolPinyin,
                                    pinyin = state.pinyinByChar[ch].orEmpty(),
                                    onClick = { viewModel.pick(ch) }
                                )
                            }
                        }
                    } else {
                        Text(
                            "Sentence complete. Use next/previous or reset to practice again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::hint,
                            enabled = !session.complete
                        ) {
                            Text("Hint (${session.hintsRemaining})")
                        }
                        OutlinedButton(onClick = viewModel::resetAttempt) {
                            Text("Reset attempt")
                        }
                    }

                    state.sentence?.parallelEnglish?.takeIf { it.isNotBlank() }?.let { english ->
                        Spacer(Modifier.height(4.dp))
                        Text("Notional translation", style = MaterialTheme.typography.titleSmall)
                        Text(
                            english,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonTile(
    slot: MemorizeSlot,
    focused: Boolean,
    showPinyin: Boolean,
    pinyin: String
) {
    val shape = RoundedCornerShape(4.dp)
    val blank = slot.isBlankHan
    Column(
        modifier = Modifier
            .widthIn(min = 40.dp)
            .clip(shape)
            .background(if (focused) TileSelected else TileFace)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = TileEdge,
                shape = shape
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when {
                slot.isPunctuation -> slot.expected.toString()
                blank -> " "
                else -> slot.filled!!.toString()
            },
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (slot.isPunctuation) 18.sp else 28.sp,
                fontWeight = FontWeight.Medium
            ),
            textAlign = TextAlign.Center,
            color = if (blank) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.widthIn(min = 28.dp)
        )
        if (showPinyin && !slot.isPunctuation && pinyin.isNotEmpty()) {
            Text(
                text = pinyin,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PoolTile(
    char: Char,
    highlighted: Boolean,
    showPinyin: Boolean,
    pinyin: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier = Modifier
            .widthIn(min = 44.dp)
            .clip(shape)
            .background(if (highlighted) TileSelected else TileFace)
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = TileEdge,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = char.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium
            ),
            textAlign = TextAlign.Center
        )
        if (showPinyin && pinyin.isNotEmpty()) {
            Text(
                text = pinyin,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
