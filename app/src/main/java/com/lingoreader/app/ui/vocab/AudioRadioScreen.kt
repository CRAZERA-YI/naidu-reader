package com.lingoreader.app.ui.vocab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingoreader.app.AppContainer
import com.lingoreader.app.data.model.AppSettings
import com.lingoreader.app.data.model.ReviewScope
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRadioScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val view = LocalView.current
    val allWords by container.vocabRepo.words.collectAsState()
    val settings by container.settingsRepo.settingsFlow.collectAsState()

    var scopeFilter by remember { mutableStateOf(ReviewScope.DUE) }
    var repeatCount by remember { mutableIntStateOf(2) }
    var pauseMs by remember { mutableIntStateOf(2200) }
    var readContextSentence by remember { mutableStateOf(true) }
    var alternateAccents by remember { mutableStateOf(true) }
    var loopPlaylist by remember { mutableStateOf(true) }

    var isPlaying by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("点击下方播放键开始随身磨耳朵") }

    val playlist = remember(allWords, scopeFilter) {
        val now = System.currentTimeMillis()
        when (scopeFilter) {
            ReviewScope.DUE -> allWords.filter { it.isDue(now) }.ifEmpty { allWords }
            ReviewScope.STUBBORN -> allWords.filter { it.isStubborn }.ifEmpty { allWords }
            ReviewScope.STARRED -> allWords.filter { it.starred }.ifEmpty { allWords }
            else -> allWords
        }
    }

    LaunchedEffect(playlist.size) {
        if (currentIndex >= playlist.size) currentIndex = 0
    }

    // Keep screen awake while listening
    DisposableEffect(view, isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose {
            view.keepScreenOn = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            container.ttsManager.stop()
        }
    }

    // Automatic hands-free word & sentence playback loop
    LaunchedEffect(
        isPlaying,
        currentIndex,
        playlist,
        repeatCount,
        pauseMs,
        readContextSentence,
        alternateAccents,
        loopPlaylist
    ) {
        if (!isPlaying || playlist.isEmpty()) return@LaunchedEffect
        val w = playlist.getOrNull(currentIndex) ?: return@LaunchedEffect

        for (rep in 1..repeatCount) {
            if (!isPlaying) return@LaunchedEffect
            val accent = if (alternateAccents && rep % 2 == 0) {
                if (settings.ttsAccent == "US") "UK" else "US"
            } else {
                settings.ttsAccent
            }
            statusText = "正在播报 (${if (accent == "UK") "英音" else "美音"} · 第 $rep/$repeatCount 遍)"
            container.ttsManager.speak(
                text = w.word,
                accent = accent,
                speed = settings.ttsSpeed,
                preferHumanVoice = settings.preferHumanVoice
            )
            // Wait for word audio + breathing gap
            delay(pauseMs.toLong())
        }

        val ctxSentence = w.allContexts.firstOrNull()?.sentence ?: w.contextSentence
        if (isPlaying && readContextSentence && ctxSentence.isNotBlank()) {
            statusText = "正在朗读阅读原句..."
            container.ttsManager.speak(
                text = ctxSentence,
                accent = settings.ttsAccent,
                speed = settings.ttsSpeed,
                preferHumanVoice = settings.preferHumanVoice
            )
            val estDurationMs = (ctxSentence.length * 68L).coerceIn(2800L, 9500L)
            delay(estDurationMs)
        }

        if (!isPlaying) return@LaunchedEffect
        if (currentIndex < playlist.lastIndex) {
            currentIndex++
        } else if (loopPlaylist) {
            currentIndex = 0
        } else {
            isPlaying = false
            statusText = "本轮磨耳朵已全部播完"
        }
    }

    val currentWord = playlist.getOrNull(currentIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("随身磨耳朵", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = {
                        isPlaying = false
                        container.ttsManager.stop()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Playlist Scope Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(ReviewScope.DUE, ReviewScope.STUBBORN, ReviewScope.STARRED, ReviewScope.ALL).forEach { sc ->
                    FilterChip(
                        selected = scopeFilter == sc,
                        onClick = {
                            scopeFilter = sc
                            currentIndex = 0
                        },
                        label = { Text(sc.label) }
                    )
                }
            }

            if (playlist.isEmpty() || currentWord == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("单词本暂无可播报的词条", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LinearProgressIndicator(
                    progress = { ((currentIndex + 1).toFloat() / playlist.size.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )

                // Giant Editorial Now-Playing Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "${currentIndex + 1} / ${playlist.size} · $statusText",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = currentWord.word,
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp),
                            textAlign = TextAlign.Center
                        )
                        if (currentWord.phonetic.isNotBlank()) {
                            Text(
                                text = "/${currentWord.phonetic}/",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = currentWord.meaning.ifBlank { "(无中文释义)" },
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                            textAlign = TextAlign.Center
                        )
                        val sent = currentWord.allContexts.firstOrNull()?.sentence ?: currentWord.contextSentence
                        if (sent.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ) {
                                Text(
                                    text = "“$sent”",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                // Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { loopPlaylist = !loopPlaylist }) {
                        Icon(
                            Icons.Rounded.Repeat,
                            contentDescription = "循环",
                            tint = if (loopPlaylist) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(
                        onClick = {
                            container.ttsManager.stop()
                            currentIndex = if (currentIndex > 0) currentIndex - 1 else playlist.lastIndex
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一词", modifier = Modifier.size(32.dp))
                    }
                    Surface(
                        onClick = {
                            if (isPlaying) {
                                isPlaying = false
                                container.ttsManager.stop()
                                statusText = "已暂停"
                            } else {
                                isPlaying = true
                            }
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(66.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            container.ttsManager.stop()
                            currentIndex = if (currentIndex < playlist.lastIndex) currentIndex + 1 else 0
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "下一词", modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.width(48.dp))
                }

                // Playback Settings Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("播报偏好", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("每词读", style = MaterialTheme.typography.bodyMedium)
                            listOf(1, 2, 3).forEach { r ->
                                FilterChip(
                                    selected = repeatCount == r,
                                    onClick = { repeatCount = r },
                                    label = { Text("${r}遍") }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("停顿", style = MaterialTheme.typography.bodyMedium)
                            listOf(1500 to "1.5s", 2200 to "2.2s", 3500 to "3.5s").forEach { (ms, lbl) ->
                                FilterChip(
                                    selected = pauseMs == ms,
                                    onClick = { pauseMs = ms },
                                    label = { Text(lbl) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("英美发音交替播报", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = alternateAccents, onCheckedChange = { alternateAccents = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("附带朗读阅读原句", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = readContextSentence, onCheckedChange = { readContextSentence = it })
                        }
                    }
                }
            }
        }
    }
}
