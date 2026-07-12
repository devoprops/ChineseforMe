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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
    modifier: Modifier = Modifier,
    glossLoading: Boolean = false,
    glossFailed: Boolean = false,
    fromOnline: Boolean = false
) {
    Surface(
        modifier = modifier
            .widthIn(min = 160.dp, max = 280.dp)
            .heightIn(max = 320.dp)
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
                    GlossSensesBody(
                        senses = group.senses,
                        loading = glossLoading,
                        failed = glossFailed,
                        fromOnline = fromOnline,
                        emptyDictionaryLabel = "No group gloss in dictionary"
                    )
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
                    GlossSensesBody(
                        senses = tile.senses,
                        loading = glossLoading,
                        failed = glossFailed,
                        fromOnline = fromOnline,
                        emptyDictionaryLabel = "No character gloss in dictionary"
                    )
                }
            }
        }
    }
}

@Composable
private fun GlossSensesBody(
    senses: List<String>,
    loading: Boolean,
    failed: Boolean,
    fromOnline: Boolean,
    emptyDictionaryLabel: String
) {
    when {
        senses.isNotEmpty() -> {
            if (fromOnline) {
                Text(
                    "Google draft",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            senses.forEachIndexed { i, sense ->
                Text("${i + 1}. $sense", style = MaterialTheme.typography.bodyMedium)
            }
        }
        loading -> {
            Text(
                "Looking up online…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        failed -> {
            Text(
                "$emptyDictionaryLabel · online lookup failed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> {
            Text(
                emptyDictionaryLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Anchors the gloss card just below [content]. Prefers horizontal centering under the
 * focus, but clamps so the card stays fully within the screen left/right edges.
 */
@Composable
fun AnchoredGlossPopup(
    popup: GlossPopup,
    glossLoading: Boolean,
    glossFailed: Boolean,
    fromOnline: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val gapPx = with(density) { 4.dp.roundToPx() }
    val edgeMarginPx = with(density) { 8.dp.roundToPx() }
    val positionProvider = remember(gapPx, edgeMarginPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val preferredX =
                    anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                val minX = edgeMarginPx
                val maxX = (windowSize.width - popupContentSize.width - edgeMarginPx)
                    .coerceAtLeast(minX)
                val x = preferredX.coerceIn(minX, maxX)
                val y = anchorBounds.bottom + gapPx
                return IntOffset(x, y)
            }
        }
    }
    Box {
        content()
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismiss,
            properties = PopupProperties(
                focusable = false,
                dismissOnClickOutside = true,
                dismissOnBackPress = true,
                clippingEnabled = false
            )
        ) {
            GlossPopupCard(
                popup = popup,
                glossLoading = glossLoading,
                glossFailed = glossFailed,
                fromOnline = fromOnline
            )
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
    groupMode: Boolean,
    onGroupTap: (WordGroup, CharTile) -> Unit,
    onCharacterLongPress: (CharTile) -> Unit,
    onGroupModeTileClick: (CharTile) -> Unit,
    onDismissGloss: () -> Unit = {},
    glossLoading: Boolean = false,
    glossFailed: Boolean = false,
    fromOnline: Boolean = false,
    modifier: Modifier = Modifier
) {
    val phraseShape = RoundedCornerShape(6.dp)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.forEach { group ->
            val multi = group.tiles.count { !it.isPunctuation } > 1
            val showGroupPopup = glossPopup is GlossPopup.Group &&
                glossPopup.group.groupId == group.groupId
            val groupChrome = Modifier.then(
                if (multi) {
                    Modifier
                        .clip(phraseShape)
                        .background(GroupBand.copy(alpha = 0.35f))
                        .border(1.dp, GroupBand, phraseShape)
                        .padding(3.dp)
                } else {
                    Modifier
                }
            )

            val tileRow: @Composable () -> Unit = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    group.tiles.forEach { tile ->
                        val showCharPopup = glossPopup is GlossPopup.Character &&
                            glossPopup.tile.index == tile.index
                        val tileContent: @Composable () -> Unit = {
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
                        }
                        if (showCharPopup && !showGroupPopup) {
                            AnchoredGlossPopup(
                                popup = glossPopup,
                                glossLoading = glossLoading,
                                glossFailed = glossFailed,
                                fromOnline = fromOnline,
                                onDismiss = onDismissGloss,
                                content = tileContent
                            )
                        } else {
                            tileContent()
                        }
                    }
                }
            }

            if (showGroupPopup) {
                Box(modifier = groupChrome) {
                    AnchoredGlossPopup(
                        popup = glossPopup,
                        glossLoading = glossLoading,
                        glossFailed = glossFailed,
                        fromOnline = fromOnline,
                        onDismiss = onDismissGloss,
                        content = tileRow
                    )
                }
            } else {
                Box(modifier = groupChrome) {
                    tileRow()
                }
            }
        }
    }
}
