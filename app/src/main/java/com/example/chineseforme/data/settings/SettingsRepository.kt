package com.example.chineseforme.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.chineseforme.domain.memorize.MemorizeHintStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class AppSettings(
    val showPinyinOnTiles: Boolean = true,
    /** Pinyin under blank skeleton tiles (expected reading). Off by default. */
    val showPinyinOnMemorizeSkeleton: Boolean = false,
    /** Pinyin under pool candidates. On by default; correct picks keep pinyin on the filled slot. */
    val showPinyinOnMemorizePool: Boolean = true,
    val glossDensity: Int = 5,
    val verticalText: Boolean = false,
    val distractorCount: Int = 8,
    /**
     * Wrong picks allowed before the memorize attempt resets.
     * Reset when mistakes exceed this value (default 1 ⇒ one free miss).
     */
    val memorizeAllowedMistakes: Int = 1,
    val memorizeHintsPerAttempt: Int = 3,
    val memorizeHintStyle: MemorizeHintStyle = MemorizeHintStyle.NarrowPool,
    val strokeHintsPerAttempt: Int = 3,
    /** Wrong strokes allowed before the stroke attempt fails. */
    val strokeAllowedMistakes: Int = 3,
    /** Work currently loaded for study; null if none selected. */
    val currentWorkId: Long? = null
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val showPinyin = booleanPreferencesKey("show_pinyin_on_tiles")
        val showPinyinMemorizeSkeleton = booleanPreferencesKey("show_pinyin_on_memorize_skeleton")
        val showPinyinMemorizePool = booleanPreferencesKey("show_pinyin_on_memorize_pool")
        val glossDensity = intPreferencesKey("gloss_density")
        val verticalText = booleanPreferencesKey("vertical_text")
        val distractorCount = intPreferencesKey("distractor_count")
        val restartOnMistake = booleanPreferencesKey("restart_on_mistake") // legacy
        val memorizeAllowedMistakes = intPreferencesKey("memorize_allowed_mistakes")
        val memorizeHints = intPreferencesKey("memorize_hints")
        val memorizeHintStyle = stringPreferencesKey("memorize_hint_style")
        val strokeHints = intPreferencesKey("stroke_hints")
        val strokeAllowedMistakes = intPreferencesKey("stroke_allowed_mistakes")
        val currentWorkId = longPreferencesKey("current_work_id")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val workId = prefs[Keys.currentWorkId]
        val memorizeMistakes = prefs[Keys.memorizeAllowedMistakes]
            ?: when (prefs[Keys.restartOnMistake]) {
                false -> 20
                else -> 1
            }
        AppSettings(
            showPinyinOnTiles = prefs[Keys.showPinyin] ?: true,
            showPinyinOnMemorizeSkeleton = prefs[Keys.showPinyinMemorizeSkeleton] ?: false,
            showPinyinOnMemorizePool = prefs[Keys.showPinyinMemorizePool] ?: true,
            glossDensity = prefs[Keys.glossDensity] ?: 5,
            verticalText = prefs[Keys.verticalText] ?: false,
            distractorCount = prefs[Keys.distractorCount] ?: 8,
            memorizeAllowedMistakes = memorizeMistakes.coerceIn(0, 20),
            memorizeHintsPerAttempt = prefs[Keys.memorizeHints] ?: 3,
            memorizeHintStyle = MemorizeHintStyle.fromStorage(prefs[Keys.memorizeHintStyle]),
            strokeHintsPerAttempt = prefs[Keys.strokeHints] ?: 3,
            strokeAllowedMistakes = (prefs[Keys.strokeAllowedMistakes] ?: 3).coerceIn(0, 20),
            currentWorkId = workId?.takeIf { it > 0L }
        )
    }

    suspend fun setShowPinyin(value: Boolean) {
        context.dataStore.edit { it[Keys.showPinyin] = value }
    }

    suspend fun setShowPinyinOnMemorizeSkeleton(value: Boolean) {
        context.dataStore.edit { it[Keys.showPinyinMemorizeSkeleton] = value }
    }

    suspend fun setShowPinyinOnMemorizePool(value: Boolean) {
        context.dataStore.edit { it[Keys.showPinyinMemorizePool] = value }
    }

    suspend fun setGlossDensity(value: Int) {
        context.dataStore.edit { it[Keys.glossDensity] = value.coerceIn(1, 20) }
    }

    suspend fun setVerticalText(value: Boolean) {
        context.dataStore.edit { it[Keys.verticalText] = value }
    }

    suspend fun setDistractorCount(value: Int) {
        context.dataStore.edit { it[Keys.distractorCount] = value.coerceIn(2, 20) }
    }

    suspend fun setMemorizeAllowedMistakes(value: Int) {
        context.dataStore.edit { it[Keys.memorizeAllowedMistakes] = value.coerceIn(0, 20) }
    }

    suspend fun setMemorizeHints(value: Int) {
        context.dataStore.edit { it[Keys.memorizeHints] = value.coerceIn(0, 10) }
    }

    suspend fun setMemorizeHintStyle(style: MemorizeHintStyle) {
        context.dataStore.edit { it[Keys.memorizeHintStyle] = style.name }
    }

    suspend fun setStrokeHints(value: Int) {
        context.dataStore.edit { it[Keys.strokeHints] = value.coerceIn(0, 10) }
    }

    suspend fun setStrokeAllowedMistakes(value: Int) {
        context.dataStore.edit { it[Keys.strokeAllowedMistakes] = value.coerceIn(0, 20) }
    }

    suspend fun setCurrentWorkId(workId: Long?) {
        context.dataStore.edit { prefs ->
            if (workId != null && workId > 0L) {
                prefs[Keys.currentWorkId] = workId
            } else {
                prefs.remove(Keys.currentWorkId)
            }
        }
    }
}
