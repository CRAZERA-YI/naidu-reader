package com.lingoreader.app.translation

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

/**
 * Pronunciation manager prioritizing authentic human-recorded English MP3 audio (US & UK)
 * with local disk caching, falling back to system TTS only when offline & uncached or disabled.
 */
class TtsManager(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheDir = File(appContext.cacheDir, "audio_cache").apply { mkdirs() }

    private var tts: TextToSpeech? = null
    @Volatile
    private var ttsReady = false

    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayJob: Job? = null
    private val playerLock = Any()

    private val _playingTag = MutableStateFlow<String?>(null)
    val playingTag: StateFlow<String?> = _playingTag.asStateFlow()

    companion object {
        private const val MAX_CACHE_BYTES = 50L * 1024L * 1024L // 50 MB audio cache cap
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"
    }

    init {
        tts = TextToSpeech(appContext) { status ->
            ttsReady = (status == TextToSpeech.SUCCESS)
            if (ttsReady) {
                tts?.language = Locale.US
            }
        }
    }

    /**
     * Speak text using real-human online MP3 (cached locally) when [preferHumanVoice] is true,
     * with automatic offline fallback to system TTS.
     */
    fun speak(
        text: String,
        accent: String = "US",
        speed: Float = 0.95f,
        preferHumanVoice: Boolean = true,
        tag: String? = null
    ) {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return

        stop()

        if (!preferHumanVoice || clean.length > 260) {
            speakWithSystemTts(clean, accent, speed)
            return
        }

        val voiceType = if (accent.equals("UK", ignoreCase = true)) 1 else 2
        val activeTag = tag ?: "${clean.lowercase()}_$voiceType"
        _playingTag.value = activeTag

        currentPlayJob = scope.launch {
            val audioFile = getOrDownloadHumanAudio(clean, voiceType)
            if (audioFile != null && audioFile.exists() && audioFile.length() > 512L) {
                val started = playMp3File(audioFile, speed, activeTag)
                if (!started) {
                    _playingTag.value = null
                    speakWithSystemTts(clean, accent, speed)
                }
            } else {
                _playingTag.value = null
                speakWithSystemTts(clean, accent, speed)
            }
        }
    }

    private suspend fun getOrDownloadHumanAudio(text: String, voiceType: Int): File? = withContext(Dispatchers.IO) {
        val cacheKey = sha256("${voiceType}:${text.lowercase()}")
        val cachedFile = File(cacheDir, "$cacheKey.mp3")
        if (cachedFile.exists() && cachedFile.length() > 512L) {
            cachedFile.setLastModified(System.currentTimeMillis())
            return@withContext cachedFile
        }

        runCatching {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val urlStr = "https://dict.youdao.com/dictvoice?audio=$encoded&type=$voiceType"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 8000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "audio/mpeg, audio/*, */*")
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val contentType = conn.contentType.orEmpty()
                if (contentType.contains("text/html", ignoreCase = true)) return@runCatching null

                val tempFile = File(cacheDir, "$cacheKey.tmp")
                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.length() < 512L) {
                    tempFile.delete()
                    return@runCatching null
                }
                if (cachedFile.exists()) cachedFile.delete()
                tempFile.renameTo(cachedFile)
                pruneCacheIfNeeded()
                cachedFile
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private suspend fun playMp3File(file: File, speed: Float, tag: String): Boolean = withContext(Dispatchers.Main) {
        runCatching {
            synchronized(playerLock) {
                releaseMediaPlayerLocked()
                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    setOnCompletionListener {
                        if (_playingTag.value == tag) {
                            _playingTag.value = null
                        }
                        synchronized(playerLock) {
                            releaseMediaPlayerLocked()
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        if (_playingTag.value == tag) {
                            _playingTag.value = null
                        }
                        synchronized(playerLock) {
                            releaseMediaPlayerLocked()
                        }
                        true
                    }
                    prepare()
                    // Apply gentle playback speed adjustment if supported (API 23+)
                    runCatching {
                        val safeSpeed = speed.coerceIn(0.75f, 1.35f)
                        if (kotlin.math.abs(safeSpeed - 1.0f) > 0.04f) {
                            playbackParams = playbackParams.setSpeed(safeSpeed)
                        }
                    }
                    start()
                }
                mediaPlayer = mp
            }
            true
        }.getOrDefault(false)
    }

    private fun speakWithSystemTts(text: String, accent: String, speed: Float) {
        val engine = tts ?: return
        if (!ttsReady || text.isBlank()) return
        val locale = if (accent.equals("UK", ignoreCase = true)) Locale.UK else Locale.US
        val langResult = engine.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.ENGLISH)
        }
        engine.setSpeechRate(speed.coerceIn(0.6f, 1.5f))
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lingo_tts_${System.currentTimeMillis()}")
    }

    fun stop() {
        currentPlayJob?.cancel()
        currentPlayJob = null
        _playingTag.value = null
        synchronized(playerLock) {
            releaseMediaPlayerLocked()
        }
        tts?.stop()
    }

    private fun releaseMediaPlayerLocked() {
        mediaPlayer?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        mediaPlayer = null
    }

    fun clearAudioCache() {
        scope.launch {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun pruneCacheIfNeeded() {
        val files = cacheDir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return
        for (f in files.sortedBy { it.lastModified() }) {
            val len = f.length()
            if (f.delete()) {
                total -= len
            }
            if (total <= MAX_CACHE_BYTES * 0.75) break
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }
}
