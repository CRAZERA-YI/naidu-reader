package com.lingoreader.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lingoreader.app.data.model.AppSettings
import com.lingoreader.app.data.model.OnlineEngine
import com.lingoreader.app.data.model.PageTurnMode
import com.lingoreader.app.data.model.ReaderFont
import com.lingoreader.app.data.model.ReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lingoreader_settings")

class SettingsRepository(
    private val context: Context,
    scope: CoroutineScope
) {

    private object Keys {
        val READER_THEME = stringPreferencesKey("reader_theme")
        val FONT_SIZE = intPreferencesKey("font_size_sp")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val H_MARGIN = intPreferencesKey("horizontal_margin_dp")
        val PAGE_TURN_MODE = stringPreferencesKey("page_turn_mode")
        val VOLUME_KEY_PAGING = booleanPreferencesKey("volume_key_paging")
        val AUTO_TURN_SEC = intPreferencesKey("auto_turn_interval_sec")
        val DAILY_REVIEW_GOAL = intPreferencesKey("daily_review_goal")
        val TAP_TRANSLATE = booleanPreferencesKey("tap_to_translate")
        val HIGHLIGHT_VOCAB = booleanPreferencesKey("highlight_vocab")
        val AUTO_PRONOUNCE = booleanPreferencesKey("auto_pronounce")
        val PREFER_HUMAN_VOICE = booleanPreferencesKey("prefer_human_voice")
        val TTS_ACCENT = stringPreferencesKey("tts_accent")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val ONLINE_ENGINE = stringPreferencesKey("online_engine")
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val SAMPLES_SEEDED = booleanPreferencesKey("samples_seeded_v1")
    }

    val settingsFlow: StateFlow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            readerTheme = ReaderTheme.fromId(prefs[Keys.READER_THEME]),
            fontSizeSp = prefs[Keys.FONT_SIZE] ?: 19,
            lineHeight = prefs[Keys.LINE_HEIGHT] ?: 1.72f,
            fontFamily = ReaderFont.fromId(prefs[Keys.FONT_FAMILY]),
            horizontalMarginDp = prefs[Keys.H_MARGIN] ?: 22,
            pageTurnMode = PageTurnMode.fromId(prefs[Keys.PAGE_TURN_MODE]),
            volumeKeyPaging = prefs[Keys.VOLUME_KEY_PAGING] ?: false,
            autoTurnIntervalSec = (prefs[Keys.AUTO_TURN_SEC] ?: 8).coerceIn(2, 60),
            dailyReviewGoal = (prefs[Keys.DAILY_REVIEW_GOAL] ?: 20).coerceIn(5, 100),
            tapToTranslate = prefs[Keys.TAP_TRANSLATE] ?: true,
            highlightVocab = prefs[Keys.HIGHLIGHT_VOCAB] ?: true,
            autoPronounceOnTap = prefs[Keys.AUTO_PRONOUNCE] ?: false,
            preferHumanVoice = prefs[Keys.PREFER_HUMAN_VOICE] ?: true,
            ttsAccent = prefs[Keys.TTS_ACCENT] ?: "US",
            ttsSpeed = prefs[Keys.TTS_SPEED] ?: 0.95f,
            onlineEngine = OnlineEngine.fromId(prefs[Keys.ONLINE_ENGINE]),
            aiEnabled = prefs[Keys.AI_ENABLED] ?: false,
            aiBaseUrl = prefs[Keys.AI_BASE_URL] ?: "https://api.deepseek.com/v1",
            aiApiKey = prefs[Keys.AI_API_KEY] ?: "",
            aiModel = prefs[Keys.AI_MODEL] ?: "deepseek-chat"
        )
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun getSettingsOnce(): AppSettings = settingsFlow.first()

    suspend fun isSamplesSeeded(): Boolean {
        return context.dataStore.data.map { it[Keys.SAMPLES_SEEDED] ?: false }.first()
    }

    suspend fun markSamplesSeeded() {
        context.dataStore.edit { it[Keys.SAMPLES_SEEDED] = true }
    }

    suspend fun updateReaderTheme(theme: ReaderTheme) {
        context.dataStore.edit { it[Keys.READER_THEME] = theme.id }
    }

    suspend fun updateFontSize(sizeSp: Int) {
        context.dataStore.edit { it[Keys.FONT_SIZE] = sizeSp.coerceIn(14, 30) }
    }

    suspend fun updateLineHeight(lineHeight: Float) {
        context.dataStore.edit { it[Keys.LINE_HEIGHT] = lineHeight.coerceIn(1.3f, 2.3f) }
    }

    suspend fun updateFontFamily(font: ReaderFont) {
        context.dataStore.edit { it[Keys.FONT_FAMILY] = font.id }
    }

    suspend fun updateHorizontalMargin(marginDp: Int) {
        context.dataStore.edit { it[Keys.H_MARGIN] = marginDp.coerceIn(12, 40) }
    }

    suspend fun updatePageTurnMode(mode: PageTurnMode) {
        context.dataStore.edit { it[Keys.PAGE_TURN_MODE] = mode.id }
    }

    suspend fun updateVolumeKeyPaging(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VOLUME_KEY_PAGING] = enabled }
    }

    suspend fun updateAutoTurnIntervalSec(seconds: Int) {
        context.dataStore.edit { it[Keys.AUTO_TURN_SEC] = seconds.coerceIn(2, 60) }
    }

    suspend fun updateDailyReviewGoal(goal: Int) {
        context.dataStore.edit { it[Keys.DAILY_REVIEW_GOAL] = goal.coerceIn(5, 100) }
    }

    suspend fun updateTapToTranslate(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TAP_TRANSLATE] = enabled }
    }

    suspend fun updateHighlightVocab(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HIGHLIGHT_VOCAB] = enabled }
    }

    suspend fun updateAutoPronounce(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PRONOUNCE] = enabled }
    }

    suspend fun updatePreferHumanVoice(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PREFER_HUMAN_VOICE] = enabled }
    }

    suspend fun updateTtsSettings(accent: String, speed: Float) {
        context.dataStore.edit {
            it[Keys.TTS_ACCENT] = accent
            it[Keys.TTS_SPEED] = speed.coerceIn(0.6f, 1.5f)
        }
    }

    suspend fun updateOnlineEngine(engine: OnlineEngine) {
        context.dataStore.edit { it[Keys.ONLINE_ENGINE] = engine.id }
    }

    suspend fun updateAiConfig(enabled: Boolean, baseUrl: String, apiKey: String, model: String) {
        context.dataStore.edit {
            it[Keys.AI_ENABLED] = enabled
            it[Keys.AI_BASE_URL] = baseUrl.trim()
            it[Keys.AI_API_KEY] = apiKey.trim()
            it[Keys.AI_MODEL] = model.trim()
        }
    }
}
