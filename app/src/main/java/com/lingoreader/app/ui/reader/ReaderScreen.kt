package com.lingoreader.app.ui.reader

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import com.lingoreader.app.AppContainer
import com.lingoreader.app.data.model.AppSettings
import com.lingoreader.app.data.model.Book
import com.lingoreader.app.data.model.PageTurnMode
import com.lingoreader.app.data.model.ParsedBook
import com.lingoreader.app.data.model.ReaderFont
import com.lingoreader.app.data.model.ReaderTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    container: AppContainer,
    onBack: () -> Unit,
    initialChapterOverride: Int = -1,
    onOpenWordDetail: (Long) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val settings by container.settingsRepo.settingsFlow.collectAsState(initial = AppSettings())
    val vocabList by container.vocabRepo.words.collectAsState()
    val playingAudioTag by container.ttsManager.playingTag.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var book by remember { mutableStateOf<Book?>(null) }
    var parsedBook by remember { mutableStateOf<ParsedBook?>(null) }
    var isLoadingBook by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var currentChapterIndex by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var totalPagesInChapter by remember { mutableIntStateOf(1) }
    var currentScrollRatio by remember { mutableFloatStateOf(0f) }
    var pendingRestoreScroll by remember { mutableFloatStateOf(0f) }

    var showChrome by remember { mutableStateOf(false) }
    var showQuickThemeBar by remember { mutableStateOf(false) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showStyleSheet by remember { mutableStateOf(false) }
    var showAutoTurnSheet by remember { mutableStateOf(false) }

    // Auto page-turn state
    var isAutoTurning by remember { mutableStateOf(false) }
    var autoTurnRemainingSec by remember { mutableIntStateOf(8) }

    // Selection pill state (drag-selected text inside WebView)
    var activeSelection by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Translation popup state
    var popupState by remember { mutableStateOf<TranslationPopupState?>(null) }
    var lookupJob by remember { mutableStateOf<Job?>(null) }

    var webViewRef by remember { mutableStateOf<LingoWebView?>(null) }
    var loadedChapterKey by remember { mutableStateOf("") }
    val gson = remember { Gson() }

    val gradedHighlightSets = remember(vocabList) {
        container.vocabRepo.getGradedHighlightWordSets(vocabList)
    }
    val bookVocabWords = remember(vocabList, bookId) {
        vocabList.filter { w ->
            w.bookId == bookId || w.allContexts.any { it.bookId == bookId }
        }
    }

    // Load book & chapters
    LaunchedEffect(bookId, initialChapterOverride) {
        isLoadingBook = true
        loadError = null
        try {
            val loadedBook = container.bookRepo.getBookById(bookId)
                ?: throw IllegalStateException("找不到该书籍")
            val parsed = container.bookRepo.loadParsedBook(loadedBook)
            book = loadedBook
            parsedBook = parsed
            val targetChapter = if (initialChapterOverride >= 0) initialChapterOverride else loadedBook.currentChapter
            val safeChapter = targetChapter.coerceIn(0, (parsed.chapters.size - 1).coerceAtLeast(0))
            currentChapterIndex = safeChapter
            currentScrollRatio = if (initialChapterOverride >= 0) 0f else loadedBook.scrollProgress.coerceIn(0f, 1f)
            pendingRestoreScroll = currentScrollRatio
        } catch (e: Exception) {
            loadError = e.message ?: "打开书籍失败"
        } finally {
            isLoadingBook = false
        }
    }

    // Keep screen awake while Auto Page-Turn is running
    DisposableEffect(view, isAutoTurning) {
        view.keepScreenOn = isAutoTurning
        onDispose {
            view.keepScreenOn = false
        }
    }

    // Register hardware Volume Up / Volume Down page-turning when enabled
    DisposableEffect(settings.volumeKeyPaging, webViewRef) {
        if (settings.volumeKeyPaging) {
            container.volumeKeyPageHandler = { isNext ->
                val wv = webViewRef
                if (wv != null) {
                    if (popupState != null) {
                        container.ttsManager.stop()
                        popupState = null
                    }
                    showChrome = false
                    showQuickThemeBar = false
                    val js = if (isNext) {
                        "window.nextPage && window.nextPage();"
                    } else {
                        "window.prevPage && window.prevPage();"
                    }
                    wv.post { wv.evaluateJavascript(js, null) }
                    true
                } else {
                    false
                }
            }
        } else {
            container.volumeKeyPageHandler = null
        }
        onDispose {
            container.volumeKeyPageHandler = null
        }
    }

    // Auto Page-Turn countdown loop (automatically pauses when popup, menu, sheets, or text selection are active!)
    val autoTurnPaused = popupState != null ||
        showChrome ||
        showTocSheet ||
        showStyleSheet ||
        showAutoTurnSheet ||
        activeSelection != null

    LaunchedEffect(
        isAutoTurning,
        autoTurnPaused,
        settings.autoTurnIntervalSec,
        currentChapterIndex,
        currentPageIndex
    ) {
        if (!isAutoTurning) return@LaunchedEffect
        val interval = settings.autoTurnIntervalSec.coerceIn(2, 60)
        autoTurnRemainingSec = interval
        if (autoTurnPaused) return@LaunchedEffect

        while (isAutoTurning && !autoTurnPaused) {
            for (sec in interval downTo 1) {
                autoTurnRemainingSec = sec
                delay(1000L)
            }
            val totalChapters = parsedBook?.chapters?.size ?: 1
            val isVeryLastPage = currentChapterIndex >= totalChapters - 1 &&
                currentPageIndex >= totalPagesInChapter - 1
            if (isVeryLastPage) {
                isAutoTurning = false
                snackbarHostState.showSnackbar("已读完本书，自动翻页已停止")
                break
            }
            webViewRef?.evaluateJavascript("window.nextPage && window.nextPage();", null)
            // Small pause to let nextPage update state cleanly before restarting countdown
            delay(450L)
        }
    }

    // Persist reading progress when leaving reader
    DisposableEffect(bookId) {
        onDispose {
            val total = parsedBook?.chapters?.size ?: 1
            container.applicationScope.launch {
                container.bookRepo.updateProgress(bookId, currentChapterIndex, currentScrollRatio, total)
            }
            container.ttsManager.stop()
        }
    }

    // Helper: trigger double-tap single-word lookup popup (instant cache/offline + automatic rich multi-sense enrichment!)
    fun triggerWordLookup(word: String, sentence: String) {
        lookupJob?.cancel()
        activeSelection = null
        showChrome = false
        showQuickThemeBar = false
        val clean = word.trim()
        if (clean.isEmpty()) return

        if (settings.autoPronounceOnTap) {
            container.ttsManager.speak(
                text = clean,
                accent = settings.ttsAccent,
                speed = settings.ttsSpeed,
                preferHumanVoice = settings.preferHumanVoice
            )
        }

        lookupJob = scope.launch {
            popupState = TranslationPopupState(
                query = clean,
                contextSentence = sentence,
                isLoading = true
            )
            val fast = container.translationService.lookupWordFast(clean)
            val existingSaved = container.vocabRepo.findSavedWord(
                word = fast?.lemma?.ifBlank { clean } ?: clean,
                lemma = fast?.lemma.orEmpty()
            )
            val curChTitle = parsedBook?.chapters?.getOrNull(currentChapterIndex)?.title.orEmpty()
            if (existingSaved != null) {
                container.vocabRepo.recordEncounterIfSaved(
                    word = existingSaved.word,
                    lemma = existingSaved.lemma,
                    contextSentence = sentence,
                    bookId = bookId,
                    bookTitle = book?.title.orEmpty(),
                    chapterIndex = currentChapterIndex,
                    chapterTitle = curChTitle
                )
            }
            if (fast != null) {
                val shouldAutoEnrich = !fast.isRichLoaded
                popupState = TranslationPopupState(
                    query = clean,
                    contextSentence = sentence,
                    isLoading = false,
                    result = fast,
                    isSaved = existingSaved != null,
                    isEnrichingOnline = shouldAutoEnrich
                )
                if (shouldAutoEnrich) {
                    val enriched = container.translationService.lookupWordOnline(clean, fast)
                    val currentState = popupState
                    if (currentState != null && currentState.query == clean) {
                        popupState = currentState.copy(
                            result = enriched,
                            isEnrichingOnline = false
                        )
                        if (currentState.isSaved && enriched.chineseMeaning.isNotBlank()) {
                            container.vocabRepo.saveFromTranslation(
                                result = enriched,
                                contextSentence = sentence,
                                bookId = bookId,
                                bookTitle = book?.title.orEmpty(),
                                chapterIndex = currentChapterIndex,
                                chapterTitle = curChTitle,
                                incrementEncounter = false
                            )
                        }
                    }
                }
            } else {
                val online = container.translationService.lookupWordOnline(clean, null)
                val savedAfter = container.vocabRepo.findSavedWord(online.displayWord, online.lemma)
                popupState = TranslationPopupState(
                    query = clean,
                    contextSentence = sentence,
                    isLoading = false,
                    result = online,
                    isSaved = savedAfter != null
                )
            }
        }
    }

    // Helper: trigger phrase / sentence translation popup
    fun triggerSelectionTranslate(selectedText: String, sentence: String, autoSave: Boolean = false) {
        lookupJob?.cancel()
        activeSelection = null
        showChrome = false
        showQuickThemeBar = false
        val clean = selectedText.trim()
        if (clean.isEmpty()) return

        lookupJob = scope.launch {
            popupState = TranslationPopupState(
                query = clean,
                contextSentence = sentence,
                isLoading = true
            )
            val res = container.translationService.translateSelection(clean, sentence, settings)
            var saved = container.vocabRepo.findSavedWord(res.displayWord, res.lemma) != null
            if (autoSave && !saved && res.chineseMeaning.isNotBlank()) {
                val curChTitle = parsedBook?.chapters?.getOrNull(currentChapterIndex)?.title.orEmpty()
                container.vocabRepo.saveFromTranslation(
                    result = res,
                    contextSentence = sentence,
                    bookId = bookId,
                    bookTitle = book?.title.orEmpty(),
                    chapterIndex = currentChapterIndex,
                    chapterTitle = curChTitle
                )
                saved = true
                snackbarHostState.showSnackbar("已加入单词本")
            }
            popupState = TranslationPopupState(
                query = clean,
                contextSentence = sentence,
                isLoading = false,
                result = res,
                isSaved = saved
            )
        }
    }

    // Live-apply reader style & page-turn mode changes to WebView without reloading page
    LaunchedEffect(
        settings.readerTheme,
        settings.fontSizeSp,
        settings.lineHeight,
        settings.fontFamily,
        settings.horizontalMarginDp,
        settings.tapToTranslate,
        settings.pageTurnMode,
        webViewRef
    ) {
        val wv = webViewRef ?: return@LaunchedEffect
        val t = settings.readerTheme
        val js = "window.applyStyle && window.applyStyle(" +
            "${gson.toJson(t.bgHex)}, ${gson.toJson(t.textHex)}, ${gson.toJson(t.secondaryHex)}, " +
            "${gson.toJson(t.accentHex)}, ${gson.toJson(t.quoteBgHex)}, " +
            "${settings.fontSizeSp}, ${settings.lineHeight}, ${gson.toJson(settings.fontFamily.cssFamily)}, " +
            "${settings.horizontalMarginDp}, ${settings.tapToTranslate}, ${gson.toJson(settings.pageTurnMode.id)});"
        wv.evaluateJavascript(js, null)
    }

    // Live-apply graded vocabulary highlight changes to WebView (due/stubborn vs learning; mastered omitted)
    LaunchedEffect(gradedHighlightSets, settings.highlightVocab, loadedChapterKey, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (loadedChapterKey.isEmpty()) return@LaunchedEffect
        val dueJson = gson.toJson(gradedHighlightSets.first.take(2000))
        val learnJson = gson.toJson(gradedHighlightSets.second.take(2000))
        wv.evaluateJavascript(
            "window.applyVocabHighlights && window.applyVocabHighlights($dueJson, $learnJson, ${settings.highlightVocab});",
            null
        )
    }

    // Sync popup open state into WebView so blank taps dismiss popup instead of flipping page
    LaunchedEffect(popupState != null, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        wv.evaluateJavascript("window.__lingoPopupOpen = ${popupState != null};", null)
    }

    val themeBgColor = remember(settings.readerTheme) {
        Color(AndroidColor.parseColor(settings.readerTheme.bgHex))
    }
    val themeTextColor = remember(settings.readerTheme) {
        Color(AndroidColor.parseColor(settings.readerTheme.textHex))
    }

    fun switchChapter(newIdx: Int, openAtLastPage: Boolean = false) {
        val chapters = parsedBook?.chapters ?: return
        val clamped = newIdx.coerceIn(0, chapters.size - 1)
        if (clamped == currentChapterIndex) return
        popupState = null
        activeSelection = null
        currentChapterIndex = clamped
        currentPageIndex = 0
        totalPagesInChapter = 1
        currentScrollRatio = if (openAtLastPage) 1f else 0f
        pendingRestoreScroll = if (openAtLastPage) -1f else 0f
        scope.launch {
            container.bookRepo.updateProgress(bookId, clamped, currentScrollRatio, chapters.size)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBgColor)
    ) {
        when {
            isLoadingBook -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("正在解析书籍...", color = themeTextColor)
                }
            }
            loadError != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = loadError ?: "加载失败",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onBack) {
                        Text("返回书架")
                    }
                }
            }
            parsedBook != null -> {
                val chapters = parsedBook!!.chapters
                val chapter = chapters.getOrNull(currentChapterIndex) ?: chapters.first()
                val chapterKey = "${bookId}_${chapter.index}"

                // Reader WebView
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    factory = { ctx ->
                        LingoWebView(ctx).apply {
                            setBackgroundColor(AndroidColor.parseColor(settings.readerTheme.bgHex))
                            this.settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                builtInZoomControls = false
                                displayZoomControls = false
                                cacheMode = WebSettings.LOAD_NO_CACHE
                            }
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            overScrollMode = View.OVER_SCROLL_NEVER
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    return true
                                }
                            }
                            addJavascriptInterface(
                                ReaderJsBridge(
                                    onWordTappedCb = { w, s ->
                                        post { triggerWordLookup(w, s) }
                                    },
                                    onBlankTappedCb = {
                                        post {
                                            if (popupState != null) {
                                                container.ttsManager.stop()
                                                popupState = null
                                            } else {
                                                val nextChrome = !showChrome
                                                showChrome = nextChrome
                                                if (!nextChrome) showQuickThemeBar = false
                                            }
                                        }
                                    },
                                    onSelectionChangedCb = { sel, sent ->
                                        post { activeSelection = sel to sent }
                                    },
                                    onSelectionClearedCb = {
                                        post { activeSelection = null }
                                    },
                                    onPageChangedCb = { pageIdx, totalPages ->
                                        post {
                                            val safeTotal = totalPages.coerceAtLeast(1)
                                            val safePage = pageIdx.coerceIn(0, safeTotal - 1)
                                            currentPageIndex = safePage
                                            totalPagesInChapter = safeTotal
                                            val ratio = if (safeTotal <= 1) 0f else safePage.toFloat() / (safeTotal - 1).toFloat()
                                            currentScrollRatio = ratio
                                            if (popupState != null) {
                                                container.ttsManager.stop()
                                                popupState = null
                                            }
                                            val totalChapters = parsedBook?.chapters?.size ?: 1
                                            scope.launch {
                                                container.bookRepo.updateProgress(
                                                    bookId,
                                                    currentChapterIndex,
                                                    ratio,
                                                    totalChapters
                                                )
                                            }
                                        }
                                    },
                                    onRequestNextChapterCb = {
                                        post {
                                            val totalChapters = parsedBook?.chapters?.size ?: 1
                                            if (currentChapterIndex < totalChapters - 1) {
                                                switchChapter(currentChapterIndex + 1, openAtLastPage = false)
                                            } else {
                                                isAutoTurning = false
                                                scope.launch { snackbarHostState.showSnackbar("已经是最后一页了") }
                                            }
                                        }
                                    },
                                    onRequestPrevChapterCb = {
                                        post {
                                            if (currentChapterIndex > 0) {
                                                switchChapter(currentChapterIndex - 1, openAtLastPage = true)
                                            } else {
                                                scope.launch { snackbarHostState.showSnackbar("已经是第一页了") }
                                            }
                                        }
                                    }
                                ),
                                "LingoBridge"
                            )
                            onActionTranslateSelected = { sel, sent ->
                                post { triggerSelectionTranslate(sel, sent, autoSave = false) }
                            }
                            onActionSaveSelected = { sel, sent ->
                                post { triggerSelectionTranslate(sel, sent, autoSave = true) }
                            }
                            webViewRef = this
                        }
                    },
                    update = { wv ->
                        wv.setBackgroundColor(AndroidColor.parseColor(settings.readerTheme.bgHex))
                        if (loadedChapterKey != chapterKey) {
                            loadedChapterKey = chapterKey
                            val html = ReaderHtmlBuilder.buildDocument(
                                chapterBodyHtml = chapter.htmlContent,
                                settings = settings,
                                dueWords = gradedHighlightSets.first,
                                learningWords = gradedHighlightSets.second,
                                initialPageRatio = pendingRestoreScroll
                            )
                            pendingRestoreScroll = 0f
                            wv.loadDataWithBaseURL(
                                chapter.baseUrl ?: "file:///android_asset/",
                                html,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    }
                )

                // Subtle bottom reading status strip (always visible when chrome & popup are hidden)
                val overallPercent = remember(currentChapterIndex, currentScrollRatio, chapters.size) {
                    val total = chapters.size.coerceAtLeast(1)
                    (((currentChapterIndex + currentScrollRatio.coerceIn(0f, 1f)) / total.toFloat()) * 100f)
                        .toInt()
                        .coerceIn(0, 100)
                }
                if (!showChrome && popupState == null && activeSelection == null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .clickable { showChrome = true }
                            .padding(horizontal = 18.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "☰  ${chapter.title}",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                            color = themeTextColor.copy(alpha = 0.56f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "第 ${currentPageIndex + 1}/${totalPagesInChapter} 页 · $overallPercent%",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                            color = themeTextColor.copy(alpha = 0.56f)
                        )
                    }
                }

                // Floating Auto Page-Turn Pill when actively running in immersive mode
                AnimatedVisibility(
                    visible = isAutoTurning && !showChrome && popupState == null && activeSelection == null,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 14.dp, bottom = 36.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "自动翻页 ${autoTurnRemainingSec}s",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.clickable { showAutoTurnSheet = true }
                            )
                            IconButton(
                                onClick = { isAutoTurning = false },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Stop,
                                    contentDescription = "停止自动翻页",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Top Reader Bar
                AnimatedVisibility(
                    visible = showChrome,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                        tonalElevation = 4.dp,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = book?.title.orEmpty(),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${chapter.title} · ${settings.pageTurnMode.label}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // Quick toggle double-tap word lookup
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val next = !settings.tapToTranslate
                                        container.settingsRepo.updateTapToTranslate(next)
                                        snackbarHostState.showSnackbar(
                                            if (next) "已开启双击单词查词" else "已关闭双击查词（仍可长按划词）"
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.TouchApp,
                                    contentDescription = "双击查词开关",
                                    tint = if (settings.tapToTranslate) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    }
                                )
                            }
                        }
                    }
                }

                // Novel-App Style Bottom Reader Toolbar (Page Slider + Quick Theme/Font Panel + 4 Action Buttons)
                AnimatedVisibility(
                    visible = showChrome && popupState == null && activeSelection == null,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                        tonalElevation = 6.dp,
                        shadowElevation = 10.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            // Expandable inline Quick Theme & Font Size Panel (1 tap from bottom bar!)
                            AnimatedVisibility(visible = showQuickThemeBar) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Quick Font Size control
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("字号", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(34.dp))
                                        Surface(
                                            onClick = { scope.launch { container.settingsRepo.updateFontSize(settings.fontSizeSp - 1) } },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text("A-", modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Slider(
                                            value = settings.fontSizeSp.toFloat(),
                                            onValueChange = { sz ->
                                                scope.launch { container.settingsRepo.updateFontSize(sz.toInt()) }
                                            },
                                            valueRange = 14f..30f,
                                            modifier = Modifier.weight(1f).height(26.dp)
                                        )
                                        Surface(
                                            onClick = { scope.launch { container.settingsRepo.updateFontSize(settings.fontSizeSp + 1) } },
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text("A+", modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Text("${settings.fontSizeSp}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(24.dp))
                                    }

                                    // Quick 4-Theme Swatches
                                    ThemeSwatchRow(
                                        selectedTheme = settings.readerTheme,
                                        onThemeSelected = { t -> scope.launch { container.settingsRepo.updateReaderTheme(t) } }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }

                            // Chapter & Page Scrub Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(
                                    onClick = { switchChapter(currentChapterIndex - 1, openAtLastPage = false) },
                                    enabled = currentChapterIndex > 0,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null)
                                    Text("上一章", fontSize = 13.sp)
                                }
                                Text(
                                    text = "第 ${currentChapterIndex + 1}/${chapters.size} 章 · 第 ${currentPageIndex + 1}/${totalPagesInChapter} 页",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = { switchChapter(currentChapterIndex + 1, openAtLastPage = false) },
                                    enabled = currentChapterIndex < chapters.size - 1,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("下一章", fontSize = 13.sp)
                                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                                }
                            }

                            if (totalPagesInChapter > 1) {
                                Slider(
                                    value = currentPageIndex.toFloat().coerceIn(0f, (totalPagesInChapter - 1).toFloat()),
                                    onValueChange = { target ->
                                        val p = target.toInt().coerceIn(0, totalPagesInChapter - 1)
                                        currentPageIndex = p
                                        webViewRef?.evaluateJavascript("window.goToPage && window.goToPage($p, false);", null)
                                    },
                                    valueRange = 0f..(totalPagesInChapter - 1).toFloat(),
                                    modifier = Modifier.height(28.dp)
                                )
                            } else if (chapters.size > 1) {
                                Slider(
                                    value = currentChapterIndex.toFloat(),
                                    onValueChange = { switchChapter(it.toInt(), openAtLastPage = false) },
                                    valueRange = 0f..(chapters.size - 1).toFloat(),
                                    steps = (chapters.size - 2).coerceAtLeast(0),
                                    modifier = Modifier.height(28.dp)
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            // 4-Action Novel Reader Bottom Row: 目录 | 自动翻页 | 主题字号 | 翻页与排版
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BottomChromeAction(
                                    icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                                    label = "目录",
                                    active = false,
                                    onClick = { showTocSheet = true }
                                )
                                BottomChromeAction(
                                    icon = if (isAutoTurning) Icons.Rounded.Pause else Icons.Rounded.Timer,
                                    label = if (isAutoTurning) "自动(${settings.autoTurnIntervalSec}s)" else "自动翻页",
                                    active = isAutoTurning,
                                    onClick = { showAutoTurnSheet = true }
                                )
                                BottomChromeAction(
                                    icon = Icons.Rounded.Palette,
                                    label = "主题字号",
                                    active = showQuickThemeBar,
                                    onClick = { showQuickThemeBar = !showQuickThemeBar }
                                )
                                BottomChromeAction(
                                    icon = Icons.Rounded.Tune,
                                    label = "翻页设置",
                                    active = showStyleSheet,
                                    onClick = {
                                        showQuickThemeBar = false
                                        showStyleSheet = true
                                    }
                                )
                            }
                        }
                    }
                }

                // Floating Selection Action Pill (for drag-selection phrase / sentence translation)
                AnimatedVisibility(
                    visible = activeSelection != null && popupState == null,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 22.dp)
                ) {
                    val selPair = activeSelection
                    if (selPair != null) {
                        val wordCount = selPair.first.split(Regex("\\s+")).count { it.isNotBlank() }
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 10.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        webViewRef?.evaluateJavascript("window.clearSelection()", null)
                                        triggerSelectionTranslate(selPair.first, selPair.second, autoSave = false)
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                                ) {
                                    Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (wordCount <= 1) "翻译单词" else "划词翻译 ($wordCount 词)",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(20.dp)
                                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f))
                                )
                                TextButton(
                                    onClick = {
                                        webViewRef?.evaluateJavascript("window.clearSelection()", null)
                                        triggerSelectionTranslate(selPair.first, selPair.second, autoSave = true)
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                                ) {
                                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("记生词", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // Floating Translation Popup Card
                AnimatedVisibility(
                    visible = popupState != null,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                ) {
                    val state = popupState
                    if (state != null) {
                        TranslationPopupCard(
                            state = state,
                            aiConfigured = settings.aiApiKey.isNotBlank(),
                            playingAudioTag = playingAudioTag,
                            onDismiss = {
                                container.ttsManager.stop()
                                popupState = null
                                webViewRef?.evaluateJavascript("window.clearTapHighlight && window.clearTapHighlight()", null)
                            },
                            onSpeak = { txt, accent, tag ->
                                container.ttsManager.speak(
                                    text = txt,
                                    accent = accent,
                                    speed = settings.ttsSpeed,
                                    preferHumanVoice = settings.preferHumanVoice,
                                    tag = tag
                                )
                            },
                            onToggleSave = {
                                val res = state.result ?: return@TranslationPopupCard
                                scope.launch {
                                    if (state.isSaved) {
                                        val target = if (!res.isPhrase && res.lemma.isNotBlank()) res.lemma else res.displayWord
                                        container.vocabRepo.removeWordByText(target)
                                        popupState = state.copy(isSaved = false)
                                        snackbarHostState.showSnackbar("已从单词本移除")
                                    } else {
                                        container.vocabRepo.saveFromTranslation(
                                            result = res,
                                            contextSentence = state.contextSentence,
                                            bookId = bookId,
                                            bookTitle = book?.title.orEmpty(),
                                            chapterIndex = currentChapterIndex,
                                            chapterTitle = chapter.title
                                        )
                                        popupState = state.copy(isSaved = true)
                                        snackbarHostState.showSnackbar("已加入单词本")
                                    }
                                }
                            },
                            onEnrichOnline = {
                                scope.launch {
                                    popupState = popupState?.copy(isEnrichingOnline = true)
                                    val enriched = container.translationService.lookupWordOnline(state.query, state.result)
                                    popupState = popupState?.copy(
                                        result = enriched,
                                        isEnrichingOnline = false
                                    )
                                }
                            },
                            onTranslateSentence = {
                                scope.launch {
                                    popupState = popupState?.copy(isTranslatingSentence = true)
                                    val sentRes = container.translationService.translateSelection(
                                        state.contextSentence,
                                        state.contextSentence,
                                        settings
                                    )
                                    popupState = popupState?.copy(
                                        isTranslatingSentence = false,
                                        sentenceTranslation = sentRes.chineseMeaning.ifBlank { sentRes.error ?: "翻译失败" }
                                    )
                                }
                            },
                            onRequestAi = {
                                scope.launch {
                                    popupState = popupState?.copy(isLoadingAi = true, aiError = null)
                                    val aiRes = container.translationService.requestAiExplanation(
                                        state.query,
                                        state.contextSentence,
                                        settings
                                    )
                                    popupState = if (aiRes.isSuccess) {
                                        popupState?.copy(isLoadingAi = false, aiText = aiRes.getOrNull())
                                    } else {
                                        popupState?.copy(
                                            isLoadingAi = false,
                                            aiError = aiRes.exceptionOrNull()?.message ?: "AI 翻译失败"
                                        )
                                    }
                                }
                            }
                        )
                    }
                }

                // TOC & Current Book Vocabulary 2-Tab Sheet
                if (showTocSheet) {
                    var drawerTab by remember { mutableIntStateOf(0) }
                    ModalBottomSheet(
                        onDismissRequest = { showTocSheet = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 24.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = drawerTab == 0,
                                    onClick = { drawerTab = 0 },
                                    label = { Text("章节目录 (${chapters.size})") }
                                )
                                FilterChip(
                                    selected = drawerTab == 1,
                                    onClick = { drawerTab = 1 },
                                    label = { Text("本书生词 (${bookVocabWords.size})") }
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            if (drawerTab == 0) {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 420.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    items(chapters) { ch ->
                                        val isCurrent = ch.index == currentChapterIndex
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    switchChapter(ch.index)
                                                    showTocSheet = false
                                                },
                                            color = if (isCurrent) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                            } else {
                                                Color.Transparent
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${ch.index + 1}.",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.width(30.dp)
                                                )
                                                Text(
                                                    text = ch.title,
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                                    ),
                                                    color = if (isCurrent) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (bookVocabWords.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("本书暂未收录生词，双击正文单词点「记生词」即可收录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.heightIn(max = 420.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(bookVocabWords, key = { it.id }) { vw ->
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable {
                                                        showTocSheet = false
                                                        onOpenWordDetail(vw.id)
                                                    },
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = vw.word,
                                                            style = MaterialTheme.typography.titleMedium
                                                        )
                                                        Text(
                                                            text = vw.meaning.replace("\n", "；"),
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            container.ttsManager.speak(
                                                                text = vw.word,
                                                                accent = settings.ttsAccent,
                                                                speed = settings.ttsSpeed,
                                                                preferHumanVoice = settings.preferHumanVoice
                                                            )
                                                        },
                                                        modifier = Modifier.size(34.dp)
                                                    ) {
                                                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "发音", tint = MaterialTheme.colorScheme.primary)
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

                // Auto Page-Turn Settings Sheet
                if (showAutoTurnSheet) {
                    ModalBottomSheet(onDismissRequest = { showAutoTurnSheet = false }) {
                        AutoTurnSheetContent(
                            isRunning = isAutoTurning,
                            intervalSec = settings.autoTurnIntervalSec,
                            onIntervalChange = { sec ->
                                scope.launch { container.settingsRepo.updateAutoTurnIntervalSec(sec) }
                            },
                            onToggleRunning = {
                                isAutoTurning = !isAutoTurning
                                if (isAutoTurning) {
                                    showAutoTurnSheet = false
                                    showChrome = false
                                    showQuickThemeBar = false
                                    scope.launch {
                                        snackbarHostState.showSnackbar("已开启自动翻页（每 ${settings.autoTurnIntervalSec} 秒翻一页）")
                                    }
                                }
                            }
                        )
                    }
                }

                // Full Reader Style & Page-Turn Settings Sheet
                if (showStyleSheet) {
                    ModalBottomSheet(onDismissRequest = { showStyleSheet = false }) {
                        ReaderStyleSheetContent(
                            settings = settings,
                            isAutoTurning = isAutoTurning,
                            onThemeChange = { scope.launch { container.settingsRepo.updateReaderTheme(it) } },
                            onFontSizeChange = { scope.launch { container.settingsRepo.updateFontSize(it) } },
                            onLineHeightChange = { scope.launch { container.settingsRepo.updateLineHeight(it) } },
                            onFontFamilyChange = { scope.launch { container.settingsRepo.updateFontFamily(it) } },
                            onMarginChange = { scope.launch { container.settingsRepo.updateHorizontalMargin(it) } },
                            onPageTurnModeChange = { scope.launch { container.settingsRepo.updatePageTurnMode(it) } },
                            onVolumeKeyPagingChange = { scope.launch { container.settingsRepo.updateVolumeKeyPaging(it) } },
                            onAutoTurnClick = {
                                showStyleSheet = false
                                showAutoTurnSheet = true
                            },
                            onTapTranslateChange = { scope.launch { container.settingsRepo.updateTapToTranslate(it) } },
                            onHighlightVocabChange = { scope.launch { container.settingsRepo.updateHighlightVocab(it) } },
                            onAutoPronounceChange = { scope.launch { container.settingsRepo.updateAutoPronounce(it) } }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 60.dp)
        )
    }
}

@Composable
private fun BottomChromeAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
            ),
            color = tint
        )
    }
}

@Composable
private fun ThemeSwatchRow(
    selectedTheme: ReaderTheme,
    onThemeSelected: (ReaderTheme) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ReaderTheme.entries.forEach { theme ->
            val selected = selectedTheme == theme
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onThemeSelected(theme) },
                shape = RoundedCornerShape(12.dp),
                color = Color(AndroidColor.parseColor(theme.bgHex)),
                border = BorderStroke(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(AndroidColor.parseColor(theme.accentHex)))
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = theme.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(AndroidColor.parseColor(theme.textHex))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoTurnSheetContent(
    isRunning: Boolean,
    intervalSec: Int,
    onIntervalChange: (Int) -> Unit,
    onToggleRunning: () -> Unit
) {
    val presets = listOf(3, 5, 8, 12, 15, 20, 30)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("自动翻页", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "每隔设定秒数自动翻到下一页；双击查词或打开菜单时会自动暂停计时，并保持屏幕常亮。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Speed slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("间隔", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(46.dp))
            Slider(
                value = intervalSec.toFloat(),
                onValueChange = { onIntervalChange(it.toInt()) },
                valueRange = 2f..60f,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${intervalSec} 秒",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(54.dp)
            )
        }

        // Quick preset chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { sec ->
                FilterChip(
                    selected = intervalSec == sec,
                    onClick = { onIntervalChange(sec) },
                    label = { Text("${sec}秒") }
                )
            }
        }

        Button(
            onClick = onToggleRunning,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = if (isRunning) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = null
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isRunning) "停止自动翻页" else "开始自动翻页（每 ${intervalSec} 秒）",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ReaderStyleSheetContent(
    settings: AppSettings,
    isAutoTurning: Boolean,
    onThemeChange: (ReaderTheme) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onFontFamilyChange: (ReaderFont) -> Unit,
    onMarginChange: (Int) -> Unit,
    onPageTurnModeChange: (PageTurnMode) -> Unit,
    onVolumeKeyPagingChange: (Boolean) -> Unit,
    onAutoTurnClick: () -> Unit,
    onTapTranslateChange: (Boolean) -> Unit,
    onHighlightVocabChange: (Boolean) -> Unit,
    onAutoPronounceChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("阅读排版与翻页设置", style = MaterialTheme.typography.titleLarge)

        // Page-Turn Modes (5 smooth options!)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("翻页动画模式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PageTurnMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.pageTurnMode == mode,
                        onClick = { onPageTurnModeChange(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
        }

        // Themes
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("阅读主题", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            ThemeSwatchRow(selectedTheme = settings.readerTheme, onThemeSelected = onThemeChange)
        }

        // Font Size
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("字号", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(44.dp))
            Surface(
                onClick = { onFontSizeChange(settings.fontSizeSp - 1) },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text("A-", modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), fontWeight = FontWeight.Bold)
            }
            Slider(
                value = settings.fontSizeSp.toFloat(),
                onValueChange = { onFontSizeChange(it.toInt()) },
                valueRange = 14f..30f,
                modifier = Modifier.weight(1f)
            )
            Surface(
                onClick = { onFontSizeChange(settings.fontSizeSp + 1) },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text("A+", modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), fontWeight = FontWeight.Bold)
            }
            Text("${settings.fontSizeSp}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(26.dp))
        }

        // Line height & Margins
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("行距", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(56.dp))
            Slider(
                value = settings.lineHeight,
                onValueChange = onLineHeightChange,
                valueRange = 1.35f..2.2f,
                modifier = Modifier.weight(1f)
            )
            Text("%.2f".format(settings.lineHeight), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("边距", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(56.dp))
            Slider(
                value = settings.horizontalMarginDp.toFloat(),
                onValueChange = { onMarginChange(it.toInt()) },
                valueRange = 12f..38f,
                modifier = Modifier.weight(1f)
            )
            Text("${settings.horizontalMarginDp}dp", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
        }

        // Font family
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReaderFont.entries.forEach { font ->
                FilterChip(
                    selected = settings.fontFamily == font,
                    onClick = { onFontFamilyChange(font) },
                    label = { Text(font.label) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalDivider()

        // Volume key paging & Auto page-turn
        ToggleRow(
            title = "音量键翻页",
            subtitle = "阅读时按音量下键翻下一页，音量上键翻上一页",
            checked = settings.volumeKeyPaging,
            onCheckedChange = onVolumeKeyPagingChange
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("自动翻页", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (isAutoTurning) "运行中 · 每 ${settings.autoTurnIntervalSec} 秒翻一页" else "当前间隔：每 ${settings.autoTurnIntervalSec} 秒",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onAutoTurnClick, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                Text(if (isAutoTurning) "管理 / 停止" else "设置秒数")
            }
        }

        HorizontalDivider()

        ToggleRow("双击单词弹出翻译小窗", "双击任意英文单词查词，单击屏幕两侧翻页不误触", settings.tapToTranslate, onTapTranslateChange)
        ToggleRow("生词本单词自动标亮", "正文已收录单词下方显示琥珀色虚线", settings.highlightVocab, onHighlightVocabChange)
        ToggleRow("查词时自动真人发音", "双击查词后自动播报真人发音", settings.autoPronounceOnTap, onAutoPronounceChange)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
