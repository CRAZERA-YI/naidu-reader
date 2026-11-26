package com.lingoreader.app.ui.vocab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingoreader.app.AppContainer
import com.lingoreader.app.data.model.AppSettings
import com.lingoreader.app.data.model.TranslationResult
import com.lingoreader.app.data.model.VocabContext
import com.lingoreader.app.data.model.VocabWord
import com.lingoreader.app.data.sm2.StudyStatsCalculator
import kotlinx.coroutines.launch

private enum class DetailTab(val label: String) {
    OVERVIEW("释义 · 笔记"),
    COLLINS("柯林斯双解"),
    PHRASES("搭配 · 词族"),
    CONTEXTS("阅读原句")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordDetailScreen(
    wordId: Long,
    container: AppContainer,
    onBack: () -> Unit,
    onJumpToBook: (bookId: String, chapterIndex: Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val allWords by container.vocabRepo.words.collectAsState()
    val settings by container.settingsRepo.settingsFlow.collectAsState()
    val playingTag by container.ttsManager.playingTag.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val word = remember(allWords, wordId) { allWords.find { it.id == wordId } }

    var richDict by remember(wordId) { mutableStateOf<TranslationResult?>(null) }
    var isLoadingRich by remember(wordId) { mutableStateOf(false) }
    var activeTab by remember(wordId) { mutableStateOf(DetailTab.OVERVIEW) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Load full rich dictionary (cached first, then online enrichment if needed)
    LaunchedEffect(word?.id, word?.word) {
        val w = word ?: return@LaunchedEffect
        val fast = container.translationService.lookupWordFast(w.word)
        if (fast != null) {
            richDict = fast
        }
        if (fast == null || !fast.isRichLoaded) {
            isLoadingRich = true
            val online = container.translationService.lookupWordOnline(w.word, fast)
            richDict = online
            isLoadingRich = false
        }
    }

    if (word == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("单词详情") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { pad ->
            Box(modifier = Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("词条不存在或已被删除", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    fun speak(text: String, accent: String, tag: String) {
        container.ttsManager.speak(
            text = text,
            accent = accent,
            speed = settings.ttsSpeed,
            preferHumanVoice = settings.preferHumanVoice,
            tag = tag
        )
    }

    val contexts = word.allContexts
    val availableTabs = remember(richDict, contexts.size) {
        buildList {
            add(DetailTab.OVERVIEW)
            if (!richDict?.collinsSenses.isNullOrEmpty()) add(DetailTab.COLLINS)
            if (!richDict?.phrases.isNullOrEmpty() ||
                !richDict?.relatedWords.isNullOrEmpty() ||
                !richDict?.synonyms.isNullOrEmpty()
            ) {
                add(DetailTab.PHRASES)
            }
            add(DetailTab.CONTEXTS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("单词详情", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { container.vocabRepo.toggleStar(word) } }) {
                        Icon(
                            imageVector = if (word.starred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = "收藏标星",
                            tint = if (word.starred) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "编辑词条")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero Word Header Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // US & UK Real-Human Pronunciation Pills
                    val usPhone = richDict?.phoneticUs?.ifBlank { word.phonetic } ?: word.phonetic
                    val ukPhone = richDict?.phoneticUk?.ifBlank { word.phoneticUk } ?: word.phoneticUk
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val usTag = "det_us_${word.id}"
                        val ukTag = "det_uk_${word.id}"
                        DetailVoiceChip("美", usPhone, playingTag == usTag) { speak(word.word, "US", usTag) }
                        DetailVoiceChip("英", ukPhone.ifBlank { usPhone }, playingTag == ukTag) { speak(word.word, "UK", ukTag) }
                        if (isLoadingRich) {
                            CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                        }
                    }

                    // Status, Frequency & Tags Row
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val masteryText = when (word.masteryLevel) {
                            0 -> "新词"
                            1 -> "初记"
                            2 -> "熟悉"
                            else -> "已掌握"
                        }
                        SmallInfoChip(masteryText, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        if (word.isStubborn) {
                            SmallInfoChip("高频顽固词", Color(0xFFFCE8E6), Color(0xFFB3261E))
                        }
                        if (word.customGroup.isNotBlank()) {
                            SmallInfoChip("分组: ${word.customGroup}", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        SmallInfoChip("遇词 ${word.encounterCount} 次", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        SmallInfoChip("复习 ${word.reviewCount} 次", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        if (word.lapseCount > 0) {
                            SmallInfoChip("遗忘 ${word.lapseCount} 次", Color(0xFFFFF0DB), Color(0xFF9A5400))
                        }
                        word.tags.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { tag ->
                            SmallInfoChip(tag, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Next review & quick SM-2 actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = StudyStatsCalculator.formatNextReviewCountdown(word.nextReviewAt),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (word.isDue()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!word.isMastered) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            container.vocabRepo.markMastered(word)
                                            snackbarHostState.showSnackbar("已标为掌握")
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("标为掌握", fontSize = 12.sp)
                                }
                            }
                            if (word.reviewCount > 0 || word.repetitions > 0) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            container.vocabRepo.resetProgress(word)
                                            snackbarHostState.showSnackbar("已重置复习进度")
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("重置", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Tab Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableTabs.forEach { tab ->
                    val suffix = when (tab) {
                        DetailTab.COLLINS -> " (${richDict?.collinsSenses?.size ?: 0})"
                        DetailTab.CONTEXTS -> " (${contexts.size})"
                        else -> ""
                    }
                    FilterChip(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        label = { Text("${tab.label}$suffix") }
                    )
                }
            }

            // Tab Body
            when (activeTab) {
                DetailTab.OVERVIEW -> {
                    DetailOverviewSection(
                        word = word,
                        richDict = richDict,
                        playingTag = playingTag,
                        onEditClick = { showEditDialog = true },
                        onSpeak = ::speak
                    )
                }
                DetailTab.COLLINS -> {
                    DetailCollinsSection(
                        richDict = richDict,
                        playingTag = playingTag,
                        onSpeak = ::speak
                    )
                }
                DetailTab.PHRASES -> {
                    DetailPhrasesSection(
                        richDict = richDict,
                        playingTag = playingTag,
                        onSpeak = ::speak
                    )
                }
                DetailTab.CONTEXTS -> {
                    DetailContextsSection(
                        word = word.word,
                        contexts = contexts,
                        playingTag = playingTag,
                        onSpeak = ::speak,
                        onJumpToBook = onJumpToBook
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showEditDialog) {
        EditWordDetailDialog(
            word = word,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                showEditDialog = false
                scope.launch {
                    container.vocabRepo.updateWord(updated)
                    snackbarHostState.showSnackbar("已保存修改")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除单词") },
            text = { Text("确定要从单词本删除「${word.word}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        container.vocabRepo.removeWord(word.id)
                        onBack()
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailOverviewSection(
    word: VocabWord,
    richDict: TranslationResult?,
    playingTag: String?,
    onEditClick: () -> Unit,
    onSpeak: (String, String, String) -> Unit
) {
    val fullMeaning = richDict?.chineseMeaning?.takeIf { it.length > word.meaning.length } ?: word.meaning

    // Comprehensive Chinese Meaning
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("中文释义", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            Text(
                text = fullMeaning.ifBlank { "(暂无中文释义，可点击右上角编辑)" },
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            val enDef = richDict?.englishDef?.ifBlank { word.englishDef } ?: word.englishDef
            if (enDef.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = enDef.lineSequence().take(5).joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Personal Note & Group card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(5.dp))
                    Text("我的助记笔记与分组", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onEditClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(28.dp)) {
                    Text("编辑", fontSize = 12.sp)
                }
            }
            Text(
                text = "所属分组：${word.customGroup.ifBlank { "默认（未分组）" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = word.note.ifBlank { "还没有写助记笔记，点击「编辑」添加词根联想法或易混辨析。" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (word.note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface
            )
        }
    }

    // Word forms
    if (!richDict?.wordForms.isNullOrEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            richDict!!.wordForms.forEach { wf ->
                SmallInfoChip("${wf.label}: ${wf.value}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // Expanded POS detailed senses
    if (!richDict?.senseGroups.isNullOrEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("分词性详解", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            richDict!!.senseGroups.forEach { group ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallInfoChip(group.pos, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        group.senses.forEachIndexed { idx, sense ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "${idx + 1}. ${sense.meaning}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                sense.examples.firstOrNull()?.let { ex ->
                                    val tag = "det_exp_${group.pos}_$idx"
                                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 14.dp)) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(ex.english, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(ex.chinese, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
                                        }
                                        IconButton(onClick = { onSpeak(ex.english, "US", tag) }, modifier = Modifier.size(28.dp)) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.VolumeUp,
                                                contentDescription = "朗读",
                                                modifier = Modifier.size(16.dp),
                                                tint = if (playingTag == tag) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
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
}

@Composable
private fun DetailCollinsSection(
    richDict: TranslationResult?,
    playingTag: String?,
    onSpeak: (String, String, String) -> Unit
) {
    val senses = richDict?.collinsSenses.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        senses.forEach { sense ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                            Text(
                                text = sense.index.toString(),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                            )
                        }
                        if (sense.pos.isNotBlank()) {
                            SmallInfoChip(
                                text = if (sense.posTips.isNotBlank()) "${sense.pos} · ${sense.posTips}" else sense.pos,
                                bg = MaterialTheme.colorScheme.secondaryContainer,
                                fg = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Text(sense.explanation, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp))
                    sense.examples.forEachIndexed { eIdx, ex ->
                        val tag = "det_col_${sense.index}_$eIdx"
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ex.english, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
                                    Text(ex.chinese, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onSpeak(ex.english, "US", tag) }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = "朗读",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (playingTag == tag) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailPhrasesSection(
    richDict: TranslationResult?,
    playingTag: String?,
    onSpeak: (String, String, String) -> Unit
) {
    if (richDict == null) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (richDict.phrases.isNotEmpty()) {
            Text("常用短语搭配", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    richDict.phrases.forEachIndexed { idx, item ->
                        val tag = "det_phr_$idx"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.phrase, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary)
                                Text(item.meaning, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
                            }
                            IconButton(onClick = { onSpeak(item.phrase, "US", tag) }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = "朗读",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (playingTag == tag) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (idx < richDict.phrases.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }

        if (richDict.relatedWords.isNotEmpty()) {
            Text("同根派生词", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    richDict.relatedWords.forEach { group ->
                        if (group.pos.isNotBlank()) {
                            Text(group.pos, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.secondary)
                        }
                        group.words.forEach { entry ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.word, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text(entry.meaning, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onSpeak(entry.word, "US", "det_rel_${entry.word}") }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "朗读", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (richDict.synonyms.isNotEmpty()) {
            Text("同近义词", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            richDict.synonyms.forEach { syn ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${syn.pos} ${syn.meaning}".trim(), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            syn.words.forEach { w ->
                                Surface(
                                    onClick = { onSpeak(w, "US", "det_syn_$w") },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Text(w, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailContextsSection(
    word: String,
    contexts: List<VocabContext>,
    playingTag: String?,
    onSpeak: (String, String, String) -> Unit,
    onJumpToBook: (bookId: String, chapterIndex: Int) -> Unit
) {
    if (contexts.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Text(
                text = "暂无阅读原句记录。在书籍中再次遇到并双击该词时，会自动将新原句追加到这里。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        contexts.forEachIndexed { idx, ctx ->
            val tag = "det_ctx_$idx"
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = buildHighlightedContext(ctx.sentence, word, MaterialTheme.colorScheme.secondary),
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (ctx.bookTitle.isNotBlank()) {
                                Text(
                                    text = "《${ctx.bookTitle}》" + if (ctx.chapterTitle.isNotBlank()) " · ${ctx.chapterTitle}" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onSpeak(ctx.sentence, "US", tag) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = "朗读原句",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (playingTag == tag) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                            }
                            if (ctx.bookId.isNotBlank()) {
                                TextButton(
                                    onClick = { onJumpToBook(ctx.bookId, ctx.chapterIndex) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("回原书", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailVoiceChip(
    label: String,
    phonetic: String,
    playing: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (playing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
        border = if (playing) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            if (phonetic.isNotBlank()) {
                Spacer(Modifier.width(5.dp))
                Text("/$phonetic/", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(5.dp))
            Icon(
                Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (playing) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SmallInfoChip(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(7.dp), color = bg) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun EditWordDetailDialog(
    word: VocabWord,
    onDismiss: () -> Unit,
    onSave: (VocabWord) -> Unit
) {
    var meaning by remember { mutableStateOf(word.meaning) }
    var group by remember { mutableStateOf(word.customGroup) }
    var note by remember { mutableStateOf(word.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑「${word.word}」") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = meaning, onValueChange = { meaning = it }, label = { Text("中文释义") }, maxLines = 4)
                OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text("自定义分组（如：核心词/专业词）") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("个人助记笔记") }, maxLines = 4)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(word.copy(meaning = meaning.trim(), customGroup = group.trim(), note = note.trim()))
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun buildHighlightedContext(sentence: String, target: String, accent: Color) = buildAnnotatedString {
    val cleanTarget = target.trim()
    if (cleanTarget.isEmpty()) {
        append(sentence)
        return@buildAnnotatedString
    }
    val regex = Regex(Regex.escape(cleanTarget), RegexOption.IGNORE_CASE)
    var last = 0
    for (m in regex.findAll(sentence)) {
        if (m.range.first > last) append(sentence.substring(last, m.range.first))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
            append(m.value)
        }
        last = m.range.last + 1
    }
    if (last < sentence.length) append(sentence.substring(last))
}
