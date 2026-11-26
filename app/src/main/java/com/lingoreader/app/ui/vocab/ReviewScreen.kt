package com.lingoreader.app.ui.vocab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingoreader.app.AppContainer
import com.lingoreader.app.data.model.AppSettings
import com.lingoreader.app.data.model.ReviewMode
import com.lingoreader.app.data.model.ReviewRating
import com.lingoreader.app.data.model.ReviewScope
import com.lingoreader.app.data.model.VocabWord
import com.lingoreader.app.data.sm2.Sm2Scheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val FALLBACK_MEANING_DISTRACTORS = listOf(
    "v. 坚持，持续；顽强存在",
    "adj. 微妙的，精细的；不易察觉的",
    "n. 歧义，模棱两可",
    "v. 缓解，减轻（痛苦等）",
    "adj. 普遍存在的，无处不在的",
    "n. 韧性，恢复力，弹力",
    "v. 仔细检查，审视",
    "adj. 短暂的，转瞬即逝的"
)

private val FALLBACK_WORD_DISTRACTORS = listOf(
    "subtle", "ephemeral", "ubiquitous", "resilient", "scrutinize",
    "alleviate", "ambiguous", "serendipity", "persevere", "eloquent"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    initialMode: ReviewMode,
    reviewScope: ReviewScope,
    bookId: String,
    limit: Int,
    container: AppContainer,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val allWords by container.vocabRepo.words.collectAsState()
    val settings by container.settingsRepo.settingsFlow.collectAsState()

    var mode by remember { mutableStateOf(initialMode) }
    val queue = remember { mutableStateListOf<VocabWord>() }
    var initialized by remember { mutableStateOf(false) }

    var currentIndex by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var maskInContext by remember { mutableStateOf(false) }
    var reviewedCount by remember { mutableIntStateOf(0) }
    var rememberedCount by remember { mutableIntStateOf(0) }

    // Quiz / Listening / Cloze choice state
    var selectedChoice by remember { mutableStateOf<String?>(null) }

    // Spelling state
    var spellingInput by remember { mutableStateOf("") }
    var spellingSubmitted by remember { mutableStateOf(false) }
    var spellingHintChars by remember { mutableIntStateOf(0) }

    fun speak(text: String, accent: String = settings.ttsAccent) {
        container.ttsManager.speak(
            text = text,
            accent = accent,
            speed = settings.ttsSpeed,
            preferHumanVoice = settings.preferHumanVoice
        )
    }

    // Build filtered review queue according to scope + limit
    LaunchedEffect(allWords, initialized) {
        if (!initialized && allWords.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val candidates = when (reviewScope) {
                ReviewScope.DUE -> {
                    val due = container.vocabRepo.getDueWords(allWords)
                    if (due.isNotEmpty()) due else allWords.shuffled()
                }
                ReviewScope.STUBBORN -> {
                    val st = allWords.filter { it.isStubborn }.sortedByDescending { it.lapseCount * 3 + it.encounterCount }
                    if (st.isNotEmpty()) st else allWords.filter { !it.isMastered }.ifEmpty { allWords }
                }
                ReviewScope.STARRED -> {
                    val st = allWords.filter { it.starred }.shuffled()
                    if (st.isNotEmpty()) st else allWords.shuffled()
                }
                ReviewScope.BOOK -> {
                    val bk = allWords.filter { it.bookId == bookId || it.allContexts.any { c -> c.bookId == bookId } }
                    if (bk.isNotEmpty()) bk.sortedBy { if (it.isDue(now)) 0 else 1 } else allWords.shuffled()
                }
                ReviewScope.ALL -> allWords.shuffled()
            }
            val finalQueue = if (limit > 0) candidates.take(limit) else candidates
            queue.clear()
            queue.addAll(finalQueue)
            initialized = true
        }
    }

    val currentWord = queue.getOrNull(currentIndex)
    val isFinished = initialized && currentWord == null

    // Reset per-card UI state & auto-play audio for Listening / Spelling modes
    LaunchedEffect(currentWord?.id, currentIndex, mode) {
        if (currentWord != null) {
            revealed = false
            selectedChoice = null
            spellingInput = ""
            spellingSubmitted = false
            spellingHintChars = 0
            if (mode == ReviewMode.LISTENING || mode == ReviewMode.SPELLING) {
                delay(180)
                speak(currentWord.word)
            }
        }
    }

    fun submitRating(word: VocabWord, rating: ReviewRating) {
        scope.launch {
            val updated = container.vocabRepo.recordReview(word, rating)
            reviewedCount++
            if (rating != ReviewRating.AGAIN) {
                rememberedCount++
            } else {
                queue.add(updated)
            }
            currentIndex++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isFinished) "复习完成" else "${reviewScope.label} (${(currentIndex + 1).coerceAtMost(queue.size)}/${queue.size})",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
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
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // 5-Mode Switcher Strip
            if (!isFinished && queue.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReviewMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(m.label, fontSize = 12.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (currentIndex.toFloat() / queue.size.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.height(12.dp))
            }

            when {
                allWords.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("单词本暂无生词", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onBack) { Text("返回") }
                        }
                    }
                }
                isFinished -> {
                    ReviewSummaryView(
                        reviewedCount = reviewedCount,
                        rememberedCount = rememberedCount,
                        onDone = onBack
                    )
                }
                currentWord != null -> {
                    when (mode) {
                        ReviewMode.FLASHCARD -> {
                            FlashcardView(
                                word = currentWord,
                                revealed = revealed,
                                maskInContext = maskInContext,
                                onToggleMask = { maskInContext = !maskInContext },
                                onReveal = { revealed = true },
                                onSpeak = ::speak,
                                onRate = { rating -> submitRating(currentWord, rating) }
                            )
                        }
                        ReviewMode.QUIZ -> {
                            val choices = remember(currentWord.id, currentIndex) {
                                buildMeaningChoices(currentWord, allWords)
                            }
                            MeaningChoiceQuizView(
                                word = currentWord,
                                hideWordUntilAnswered = false,
                                choices = choices,
                                selectedChoice = selectedChoice,
                                onSpeak = ::speak,
                                onSelectChoice = { choice ->
                                    if (selectedChoice != null) return@MeaningChoiceQuizView
                                    selectedChoice = choice
                                    val correct = choice == shortMeaning(currentWord.meaning)
                                    scope.launch {
                                        delay(780)
                                        submitRating(currentWord, if (correct) ReviewRating.GOOD else ReviewRating.AGAIN)
                                    }
                                }
                            )
                        }
                        ReviewMode.LISTENING -> {
                            val choices = remember(currentWord.id, currentIndex) {
                                buildMeaningChoices(currentWord, allWords)
                            }
                            MeaningChoiceQuizView(
                                word = currentWord,
                                hideWordUntilAnswered = true,
                                choices = choices,
                                selectedChoice = selectedChoice,
                                onSpeak = ::speak,
                                onSelectChoice = { choice ->
                                    if (selectedChoice != null) return@MeaningChoiceQuizView
                                    selectedChoice = choice
                                    val correct = choice == shortMeaning(currentWord.meaning)
                                    scope.launch {
                                        delay(950)
                                        submitRating(currentWord, if (correct) ReviewRating.GOOD else ReviewRating.AGAIN)
                                    }
                                }
                            )
                        }
                        ReviewMode.SPELLING -> {
                            SpellingPracticeView(
                                word = currentWord,
                                input = spellingInput,
                                submitted = spellingSubmitted,
                                hintChars = spellingHintChars,
                                onInputChange = { spellingInput = it },
                                onSpeak = ::speak,
                                onHint = {
                                    val target = currentWord.word.trim()
                                    val nextLen = (spellingHintChars + 1).coerceAtMost(target.length)
                                    spellingHintChars = nextLen
                                    spellingInput = target.take(nextLen)
                                },
                                onSubmit = { spellingSubmitted = true },
                                onNext = { isCorrect ->
                                    submitRating(
                                        currentWord,
                                        if (isCorrect && spellingHintChars == 0) ReviewRating.GOOD
                                        else if (isCorrect) ReviewRating.HARD
                                        else ReviewRating.AGAIN
                                    )
                                }
                            )
                        }
                        ReviewMode.CLOZE -> {
                            val wordChoices = remember(currentWord.id, currentIndex) {
                                buildWordChoices(currentWord, allWords)
                            }
                            ClozeContextView(
                                word = currentWord,
                                choices = wordChoices,
                                selectedChoice = selectedChoice,
                                onSpeak = ::speak,
                                onSelectChoice = { choice ->
                                    if (selectedChoice != null) return@ClozeContextView
                                    selectedChoice = choice
                                    val correct = choice.equals(currentWord.word.trim(), ignoreCase = true)
                                    if (correct) speak(currentWord.word)
                                    scope.launch {
                                        delay(900)
                                        submitRating(currentWord, if (correct) ReviewRating.GOOD else ReviewRating.AGAIN)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashcardView(
    word: VocabWord,
    revealed: Boolean,
    maskInContext: Boolean,
    onToggleMask: () -> Unit,
    onReveal: () -> Unit,
    onSpeak: (String, String) -> Unit,
    onRate: (ReviewRating) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .clickable(enabled = !revealed, onClick = onReveal),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = word.word,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = if (word.word.length > 22) 22.sp else 32.sp
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))

                // Dual US / UK Real-Human Pronunciation Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReviewVoiceBtn("美", word.phonetic) { onSpeak(word.word, "US") }
                    ReviewVoiceBtn("英", word.phoneticUk.ifBlank { word.phonetic }) { onSpeak(word.word, "UK") }
                }

                val ctxSentence = word.allContexts.firstOrNull()?.sentence ?: word.contextSentence
                if (ctxSentence.isNotBlank()) {
                    Spacer(Modifier.height(20.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (word.bookTitle.isNotBlank()) "原句 · 《${word.bookTitle}》" else "原句语境",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row {
                                    IconButton(onClick = onToggleMask, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            imageVector = if (maskInContext) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = "遮挡目标词",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(onClick = { onSpeak(ctxSentence, "US") }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "朗读例句", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = highlightWordInSentence(
                                    sentence = ctxSentence,
                                    target = word.word,
                                    masked = maskInContext && !revealed,
                                    accentColor = MaterialTheme.colorScheme.secondary
                                ),
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)
                            )
                        }
                    }
                }

                if (!revealed) {
                    Spacer(Modifier.height(26.dp))
                    Text("点击卡片查看释义", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                AnimatedVisibility(visible = revealed) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        HorizontalDivider()
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = word.meaning.ifBlank { "(暂无中文释义)" },
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp)
                        )
                        if (word.englishDef.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = word.englishDef.lineSequence().take(3).joinToString("\n"),
                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (word.note.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("笔记：${word.note}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (!revealed) {
            Button(
                onClick = onReveal,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("显示答案", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingButton(ReviewRating.AGAIN, Sm2Scheduler.previewIntervalLabel(word, ReviewRating.AGAIN), Color(0xFFFCE8E6), Color(0xFFB3261E), { onRate(ReviewRating.AGAIN) }, Modifier.weight(1f))
                RatingButton(ReviewRating.HARD, Sm2Scheduler.previewIntervalLabel(word, ReviewRating.HARD), Color(0xFFFFF0DB), Color(0xFF9A5400), { onRate(ReviewRating.HARD) }, Modifier.weight(1f))
                RatingButton(ReviewRating.GOOD, Sm2Scheduler.previewIntervalLabel(word, ReviewRating.GOOD), Color(0xFFD5E8E0), Color(0xFF144736), { onRate(ReviewRating.GOOD) }, Modifier.weight(1f))
                RatingButton(ReviewRating.EASY, Sm2Scheduler.previewIntervalLabel(word, ReviewRating.EASY), Color(0xFFD9ECF7), Color(0xFF0E4A6D), { onRate(ReviewRating.EASY) }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MeaningChoiceQuizView(
    word: VocabWord,
    hideWordUntilAnswered: Boolean,
    choices: List<String>,
    selectedChoice: String?,
    onSpeak: (String, String) -> Unit,
    onSelectChoice: (String) -> Unit
) {
    val correctAnswer = shortMeaning(word.meaning)
    val answered = selectedChoice != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (hideWordUntilAnswered && !answered) {
                    Text(
                        text = "听音辨义 · 请听发音选择释义",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            onClick = { onSpeak(word.word, "US") },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "美音", tint = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(6.dp))
                                Text("美音重播", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Surface(
                            onClick = { onSpeak(word.word, "UK") },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "英音", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.width(6.dp))
                                Text("英音", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    Text(
                        text = word.word,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReviewVoiceBtn("美", word.phonetic) { onSpeak(word.word, "US") }
                        ReviewVoiceBtn("英", word.phoneticUk.ifBlank { word.phonetic }) { onSpeak(word.word, "UK") }
                    }
                    if (word.contextSentence.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = word.contextSentence,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Text("选择最匹配的中文释义：", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        choices.forEach { choice ->
            val isSelected = selectedChoice == choice
            val isCorrect = choice == correctAnswer
            val bgColor = when {
                !answered -> MaterialTheme.colorScheme.surface
                isCorrect -> Color(0xFFD5E8E0)
                isSelected -> Color(0xFFFCE8E6)
                else -> MaterialTheme.colorScheme.surface
            }
            val borderColor = when {
                !answered -> MaterialTheme.colorScheme.outlineVariant
                isCorrect -> Color(0xFF2E5E4E)
                isSelected -> Color(0xFFB3261E)
                else -> MaterialTheme.colorScheme.outlineVariant
            }

            Surface(
                onClick = { onSelectChoice(choice) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = bgColor,
                border = BorderStroke(1.5.dp, borderColor)
            ) {
                Text(
                    text = choice,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun SpellingPracticeView(
    word: VocabWord,
    input: String,
    submitted: Boolean,
    hintChars: Int,
    onInputChange: (String) -> Unit,
    onSpeak: (String, String) -> Unit,
    onHint: () -> Unit,
    onSubmit: () -> Unit,
    onNext: (isCorrect: Boolean) -> Unit
) {
    val target = word.word.trim()
    val isCorrect = input.trim().equals(target, ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("听音看义 · 拼写默写", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewVoiceBtn("美", word.phonetic) { onSpeak(target, "US") }
                    ReviewVoiceBtn("英", word.phoneticUk.ifBlank { word.phonetic }) { onSpeak(target, "UK") }
                }

                Text(
                    text = word.meaning.ifBlank { "(请根据发音拼写)" },
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                    textAlign = TextAlign.Center
                )

                val ctx = word.allContexts.firstOrNull()?.sentence ?: word.contextSentence
                if (ctx.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = highlightWordInSentence(ctx, target, masked = !submitted, accentColor = MaterialTheme.colorScheme.secondary),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { if (!submitted) onInputChange(it) },
            label = { Text("输入英文拼写 (${target.length} 字母)") },
            singleLine = true,
            enabled = !submitted,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) onSubmit() }),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        if (!submitted) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onHint,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Rounded.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (hintChars == 0) "提示首字母" else "再提示一位")
                }
                Button(
                    onClick = onSubmit,
                    enabled = input.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.4f).height(48.dp)
                ) {
                    Text("核对拼写", fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            // Character-by-character diff feedback card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (isCorrect) Color(0xFFD5E8E0) else Color(0xFFFCE8E6)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isCorrect) "✓ 拼写完全正确！" else "✗ 拼写有误，逐字比对：",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCorrect) Color(0xFF144736) else Color(0xFFB3261E)
                    )
                    if (!isCorrect) {
                        Text(
                            text = buildSpellingDiff(input.trim(), target),
                            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 1.sp)
                        )
                        Text(
                            text = "正确拼写：$target",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF144736)
                        )
                    }
                }
            }
            Button(
                onClick = { onNext(isCorrect) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(if (isCorrect) "继续下一词" else "记住了 · 稍后重测", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ClozeContextView(
    word: VocabWord,
    choices: List<String>,
    selectedChoice: String?,
    onSpeak: (String, String) -> Unit,
    onSelectChoice: (String) -> Unit
) {
    val answered = selectedChoice != null
    val target = word.word.trim()
    val sentence = word.allContexts.firstOrNull()?.sentence ?: word.contextSentence

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (sentence.isNotBlank()) "阅读原句完形填空" else "看释义选词（暂无原句）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (sentence.isNotBlank()) {
                    Text(
                        text = highlightWordInSentence(
                            sentence = sentence,
                            target = target,
                            masked = !answered,
                            accentColor = MaterialTheme.colorScheme.secondary
                        ),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp)
                    )
                    if (word.bookTitle.isNotBlank()) {
                        Text(
                            text = "—— 出自《${word.bookTitle}》",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "释义提示：${shortMeaning(word.meaning)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (answered) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReviewVoiceBtn("美", word.phonetic) { onSpeak(target, "US") }
                        if (sentence.isNotBlank()) {
                            OutlinedButton(
                                onClick = { onSpeak(sentence, "US") },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("朗读完整原句", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Text("选择填入空缺处的单词：", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        choices.forEach { choice ->
            val isSelected = selectedChoice == choice
            val isCorrect = choice.equals(target, ignoreCase = true)
            val bgColor = when {
                !answered -> MaterialTheme.colorScheme.surface
                isCorrect -> Color(0xFFD5E8E0)
                isSelected -> Color(0xFFFCE8E6)
                else -> MaterialTheme.colorScheme.surface
            }
            val borderColor = when {
                !answered -> MaterialTheme.colorScheme.outlineVariant
                isCorrect -> Color(0xFF2E5E4E)
                isSelected -> Color(0xFFB3261E)
                else -> MaterialTheme.colorScheme.outlineVariant
            }

            Surface(
                onClick = { onSelectChoice(choice) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = bgColor,
                border = BorderStroke(1.5.dp, borderColor)
            ) {
                Text(
                    text = choice,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun ReviewVoiceBtn(label: String, phonetic: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            if (phonetic.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                Text("/$phonetic/", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RatingButton(
    rating: ReviewRating,
    interval: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(14.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(rating.label, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), color = contentColor)
            Text(interval, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = contentColor.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun ReviewSummaryView(
    reviewedCount: Int,
    rememberedCount: Int,
    onDone: () -> Unit
) {
    val accuracy = if (reviewedCount > 0) (rememberedCount * 100 / reviewedCount) else 100
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(14.dp))
                Text("本轮复习已完成！", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text("共复习 $reviewedCount 次 · 记忆保持率 $accuracy%", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDone,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
                ) {
                    Text("返回单词本", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun shortMeaning(meaning: String): String {
    return meaning.replace("\n", "；").trim().take(80).ifBlank { "(无释义)" }
}

private fun buildMeaningChoices(target: VocabWord, allWords: List<VocabWord>): List<String> {
    val correct = shortMeaning(target.meaning)
    val others = allWords
        .filter { it.id != target.id && it.meaning.isNotBlank() }
        .map { shortMeaning(it.meaning) }
        .distinct()
        .filter { it != correct }
        .shuffled()
        .take(3)
        .toMutableList()

    val fallbackPool = FALLBACK_MEANING_DISTRACTORS.shuffled()
    var idx = 0
    while (others.size < 3 && idx < fallbackPool.size) {
        val cand = fallbackPool[idx++]
        if (cand != correct && cand !in others) others.add(cand)
    }
    return (others + correct).shuffled()
}

private fun buildWordChoices(target: VocabWord, allWords: List<VocabWord>): List<String> {
    val correct = target.word.trim()
    val others = allWords
        .filter { it.id != target.id && !it.word.equals(correct, ignoreCase = true) }
        .map { it.word.trim() }
        .distinctBy { it.lowercase() }
        .shuffled()
        .take(3)
        .toMutableList()

    val fallbackPool = FALLBACK_WORD_DISTRACTORS.shuffled()
    var idx = 0
    while (others.size < 3 && idx < fallbackPool.size) {
        val cand = fallbackPool[idx++]
        if (!cand.equals(correct, ignoreCase = true) && others.none { it.equals(cand, ignoreCase = true) }) {
            others.add(cand)
        }
    }
    return (others + correct).shuffled()
}

private fun buildSpellingDiff(typed: String, target: String) = buildAnnotatedString {
    val maxLen = maxOf(typed.length, target.length)
    for (i in 0 until maxLen) {
        val tc = typed.getOrNull(i)
        val ec = target.getOrNull(i)
        if (tc != null) {
            val match = ec != null && tc.equals(ec, ignoreCase = true)
            withStyle(
                SpanStyle(
                    color = if (match) Color(0xFF144736) else Color(0xFFB3261E),
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(tc)
            }
        }
    }
}

private fun highlightWordInSentence(
    sentence: String,
    target: String,
    masked: Boolean,
    accentColor: Color
) = buildAnnotatedString {
    val cleanTarget = target.trim()
    if (cleanTarget.isEmpty()) {
        append(sentence)
        return@buildAnnotatedString
    }
    val regex = Regex(Regex.escape(cleanTarget), RegexOption.IGNORE_CASE)
    var lastIdx = 0
    for (m in regex.findAll(sentence)) {
        if (m.range.first > lastIdx) {
            append(sentence.substring(lastIdx, m.range.first))
        }
        withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)) {
            append(if (masked) "_______" else m.value)
        }
        lastIdx = m.range.last + 1
    }
    if (lastIdx < sentence.length) {
        append(sentence.substring(lastIdx))
    }
}
