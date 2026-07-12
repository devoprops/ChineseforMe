package com.example.chineseforme.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chineseforme.domain.model.CharTile
import com.example.chineseforme.domain.model.WordGroup
import com.example.chineseforme.ui.theme.GroupBand
import com.example.chineseforme.ui.theme.TileEdge
import com.example.chineseforme.ui.theme.TileFace
import com.example.chineseforme.ui.theme.TileSelected

sealed class GlossPopup {
    data class Group(val group: WordGroup) : GlossPopup()
    data class Character(val tile: CharTile) : GlossPopup()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CharacterTile(
    tile: CharTile,
    showPinyin: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier = modifier
            .widthIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) TileSelected else TileFace)
            .border(1.dp, TileEdge, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = tile.char,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (tile.isPunctuation) 18.sp else 28.sp,
                fontWeight = FontWeight.Medium
            ),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (showPinyin && !tile.isPunctuation) {
            Text(
                text = tile.pinyinCandidates.firstOrNull().orEmpty(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
fun GlossPopupCard(
    popup: GlossPopup,
    glossDensity: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(min = 160.dp, max = 280.dp)
            .heightIn(max = 240.dp)
            .shadow(6.dp, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = TileFace,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (popup) {
                is GlossPopup.Group -> {
                    val group = popup.group
                    Text(
                        "Phrase · ${group.surface}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (group.pinyinCandidates.isNotEmpty()) {
                        Text(
                            group.pinyinCandidates.joinToString(" / "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (group.senses.isEmpty()) {
                        Text(
                            "No group gloss in dictionary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        group.senses.take(glossDensity).forEachIndexed { i, sense ->
                            Text("${i + 1}. $sense", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (group.senses.size > glossDensity) {
                            Text(
                                "… ${group.senses.size - glossDensity} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val hanCount = group.tiles.count { !it.isPunctuation }
                    if (hanCount > 1) {
                        Text(
                            "Long-press a character for character senses",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is GlossPopup.Character -> {
                    val tile = popup.tile
                    Text(
                        "Character · ${tile.char}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (tile.pinyinCandidates.isNotEmpty()) {
                        Text(
                            tile.pinyinCandidates.joinToString(" / "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (tile.senses.isEmpty()) {
                        Text(
                            "No character gloss in dictionary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        tile.senses.take(glossDensity).forEachIndexed { i, sense ->
                            Text("${i + 1}. $sense", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (tile.senses.size > glossDensity) {
                            Text(
                                "… ${tile.senses.size - glossDensity} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun GroupedTileRow(
    groups: List<WordGroup>,
    showPinyin: Boolean,
    selectedIndices: Set<Int>,
    glossPopup: GlossPopup?,
    glossDensity: Int,
    groupMode: Boolean,
    onDismissPopup: () -> Unit,
    onGroupTap: (WordGroup, CharTile) -> Unit,
    onCharacterLongPress: (CharTile) -> Unit,
    onGroupModeTileClick: (CharTile) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.forEach { group ->
            val multi = group.tiles.count { !it.isPunctuation } > 1
            val anchorTile = group.tiles.firstOrNull { !it.isPunctuation } ?: group.tiles.firstOrNull()
            Box(
                modifier = Modifier
                    .then(
                        if (multi) {
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(GroupBand.copy(alpha = 0.35f))
                                .padding(3.dp)
                        } else Modifier
                    )
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    group.tiles.forEach { tile ->
                        val showMenuForTile = when (val p = glossPopup) {
                            is GlossPopup.Character -> p.tile.index == tile.index
                            is GlossPopup.Group ->
                                p.group.groupId == group.groupId && tile.index == anchorTile?.index
                            null -> false
                        }
                        Box {
                            CharacterTile(
                                tile = tile,
                                showPinyin = showPinyin,
                                selected = tile.index in selectedIndices ||
                                    (glossPopup is GlossPopup.Group &&
                                        glossPopup.group.groupId == group.groupId &&
                                        !tile.isPunctuation) ||
                                    (glossPopup is GlossPopup.Character &&
                                        glossPopup.tile.index == tile.index),
                                onClick = {
                                    if (groupMode) {
                                        onGroupModeTileClick(tile)
                                    } else if (!tile.isPunctuation) {
                                        onGroupTap(group, tile)
                                    }
                                },
                                onLongClick = if (!groupMode && !tile.isPunctuation) {
                                    { onCharacterLongPress(tile) }
                                } else {
                                    null
                                }
                            )
                            DropdownMenu(
                                expanded = showMenuForTile,
                                onDismissRequest = onDismissPopup,
                                offset = DpOffset(0.dp, 4.dp)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    if (glossPopup != null) {
                                        GlossPopupCard(
                                            popup = glossPopup,
                                            glossDensity = glossDensity
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
