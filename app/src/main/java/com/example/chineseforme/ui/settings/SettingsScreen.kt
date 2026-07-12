package com.example.chineseforme.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.chineseforme.data.settings.SettingsRepository
import com.example.chineseforme.ui.theme.Parchment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    scope: CoroutineScope,
    onBack: () -> Unit
) {
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = com.example.chineseforme.data.settings.AppSettings()
    )

    Scaffold(
        containerColor = Parchment,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingSwitch(
                title = "Show pinyin on study tiles",
                checked = settings.showPinyinOnTiles,
                onCheckedChange = { scope.launch { settingsRepository.setShowPinyin(it) } }
            )
            SettingSwitch(
                title = "Vertical text (preview)",
                checked = settings.verticalText,
                onCheckedChange = { scope.launch { settingsRepository.setVerticalText(it) } }
            )
            SettingSlider(
                title = "Gloss density (senses shown)",
                value = settings.glossDensity.toFloat(),
                range = 1f..12f,
                label = settings.glossDensity.toString(),
                onChange = { scope.launch { settingsRepository.setGlossDensity(it.toInt()) } }
            )
            Text("Memorize (Phase 2)", style = MaterialTheme.typography.titleMedium)
            SettingSlider(
                title = "Distractor count",
                value = settings.distractorCount.toFloat(),
                range = 2f..20f,
                label = settings.distractorCount.toString(),
                onChange = { scope.launch { settingsRepository.setDistractorCount(it.toInt()) } }
            )
            SettingSwitch(
                title = "Restart on mistake",
                checked = settings.restartOnMistake,
                onCheckedChange = { scope.launch { settingsRepository.setRestartOnMistake(it) } }
            )
            SettingSlider(
                title = "Memorize hints per attempt",
                value = settings.memorizeHintsPerAttempt.toFloat(),
                range = 0f..10f,
                label = settings.memorizeHintsPerAttempt.toString(),
                onChange = { scope.launch { settingsRepository.setMemorizeHints(it.toInt()) } }
            )
            SettingSlider(
                title = "Stroke hints per attempt",
                value = settings.strokeHintsPerAttempt.toFloat(),
                range = 0f..10f,
                label = settings.strokeHintsPerAttempt.toString(),
                onChange = { scope.launch { settingsRepository.setStrokeHints(it.toInt()) } }
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = ((range.endInclusive - range.start).toInt() - 1).coerceAtLeast(0))
    }
}
