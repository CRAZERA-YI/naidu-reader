package com.lingoreader.app

import android.app.Application
import android.content.Context
import android.webkit.WebSettings
import androidx.compose.runtime.Stable
import com.lingoreader.app.data.db.AppDatabaseHelper
import com.lingoreader.app.data.prefs.SettingsRepository
import com.lingoreader.app.data.repository.BookRepository
import com.lingoreader.app.data.repository.VocabRepository
import com.lingoreader.app.translation.OfflineDictionary
import com.lingoreader.app.translation.TranslationService
import com.lingoreader.app.translation.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
class AppContainer(private val appContext: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dbHelper = AppDatabaseHelper(appContext)
    val settingsRepo = SettingsRepository(appContext, applicationScope)
    val bookRepo = BookRepository(appContext, dbHelper, settingsRepo)
    val vocabRepo = VocabRepository(appContext, dbHelper)
    val offlineDictionary = OfflineDictionary(appContext)
    val translationService = TranslationService(offlineDictionary, dbHelper)
    val ttsManager = TtsManager(appContext)

    // Registered by ReaderScreen while active to intercept hardware Volume Up/Down for page turning
    @Volatile
    var volumeKeyPageHandler: ((isNext: Boolean) -> Boolean)? = null

    fun warmUp() {
        applicationScope.launch(Dispatchers.IO) {
            bookRepo.initialize()
            vocabRepo.refresh()
        }
        applicationScope.launch(Dispatchers.IO) {
            offlineDictionary.ensureReady()
        }
        // Pre-warm Android WebView engine in background after first UI frames settle
        applicationScope.launch(Dispatchers.IO) {
            delay(400)
            runCatching { WebSettings.getDefaultUserAgent(appContext) }
        }
    }
}

class LingoReaderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.warmUp()
    }
}
