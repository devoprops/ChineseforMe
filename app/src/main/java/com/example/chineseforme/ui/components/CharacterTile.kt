package com.example.chineseforme.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chineseforme.domain.model.CharTile
import com.example.chineseforme.domain.model.WordGroup
import com.example.chineseforme.ui.theme.GroupBand
import com.example.chineseforme.ui.theme.TileEdge
import com.example.chineseforme.ui.theme.TileFace
import com.example.chineseforme.ui.theme.TileSelected

@Composable
fun CharacterTile(
    tile: CharTile,
    showPinyin: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier = modifier
            .widthIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) TileSelected else TileFace)
            .border(1.dp, TileEdge, shape)
            .clickable(onClick = onClick)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupedTileRow(
    groups: List<WordGroup>,
    showPinyin: Boolean,
    selectedIndices: Set<Int>,
    onTileClick: (CharTile) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.forEach { group ->
            val multi = group.tiles.count { !it.isPunctuation } > 1
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
                        CharacterTile(
                            tile = tile,
                            showPinyin = showPinyin,
                            selected = tile.index in selectedIndices,
                            onClick = { onTileClick(tile) }
                        )
                    }
                }
            }
        }
    }
}
