package com.lingoreader.app

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dbHelper = AppDatabaseHelper(context)
    val settingsRepo = SettingsRepository(context)
    val bookRepo = BookRepository(context, dbHelper, settingsRepo)
    val vocabRepo = VocabRepository(context, dbHelper)
    val offlineDictionary = OfflineDictionary(context)
    val translationService = TranslationService(offlineDictionary, dbHelper)
    val ttsManager = TtsManager(context)

    // Registered by ReaderScreen while active to intercept hardware Volume Up/Down for page turning
    @Volatile
    var volumeKeyPageHandler: ((isNext: Boolean) -> Boolean)? = null

    fun warmUp() {
        applicationScope.launch {
            bookRepo.initialize()
            vocabRepo.refresh()
        }
        applicationScope.launch {
            offlineDictionary.ensureReady()
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
