package com.lingoreader.app.ui.vocab

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.lingoreader.app.AppContainer
import com.lingoreader.app.data.model.AppSettings
import com.lingoreader.app.data.model.ReviewMode
import com.lingoreader.app.data.model.ReviewScope
import com.lingoreader.app.data.model.VocabMaskMode
import com.lingoreader.app.data.model.VocabSortMode
import com.lingoreader.app.data.model.VocabWord
import com.lingoreader.app.data.sm2.StudyStatsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class VocabFilter(val label: String) {
    ALL("全部"),
    DUE("待复习"),
    STUBBORN("顽固词"),
    STARRED("重点星标"),
    MASTERED("已掌握")
}

private val EXAM_TAGS = listOf("CET4", "CET6", "考研", "IELTS", "TOEFL", "GRE", "高考")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabScreen(
    container: AppContainer,
    onStartReview: (mode: ReviewMode, scope: ReviewScope, bookId: String, limit: Int) -> Unit,
    onOpenWordDetail: (Long) -> Unit,
    onOpenAudioRadio: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val words by container.vocabRepo.words.collectAsState()
    val studyLogs by container.vocabRepo.studyLogs.collectAsState()
    val settings by container.settingsRepo.settingsFlow.collectAsState(initial = AppSettings())
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(VocabFilter.ALL) }
    var sortMode by remember { mutableStateOf(VocabSortMode.RECENT) }
    var maskMode by remember { mutableStateOf(VocabMaskMode.NONE) }
    val peekedWordIds = remember { mutableStateListOf<Long>() }

    // Extra multi-dimensional filters
    var showAdvancedFilters by remember { mutableStateOf(false) }
    var selectedBookTitle by remember { mutableStateOf<String?>(null) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var selectedExamTag by remember { mutableStateOf<String?>(null) }

    // Batch multi-select mode
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var showBatchGroupDialog by remember { mutableStateOf(false) }

    // Menus & sheets
    var showAddMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showStatsSheet by remember { mutableStateOf(false) }
    var showReviewSetupSheet by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val analytics = remember(words, studyLogs) { StudyStatsCalculator.compute(words, studyLogs) }
    val dueCount = remember(words) { words.count { it.isDue(now) } }
    val stubbornWords = remember(words) {
        words.filter { it.isStubborn }.sortedByDescending { it.lapseCount * 3 + it.encounterCount }
    }

    val availableBooks = remember(words) {
        words.mapNotNull { w ->
            if (w.bookTitle.isNotBlank()) w.bookId to w.bookTitle else null
        }.distinctBy { it.second }
    }
    val availableGroups = remember(words) {
        words.map { it.customGroup.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val filteredWords = remember(
        words, searchQuery, filter, sortMode, selectedBookTitle, selectedGroup, selectedExamTag
    ) {
        val base = words.filter { w ->
            val statusOk = when (filter) {
                VocabFilter.ALL -> true
                VocabFilter.DUE -> w.isDue(now)
                VocabFilter.STUBBORN -> w.isStubborn
                VocabFilter.STARRED -> w.starred
                VocabFilter.MASTERED -> w.isMastered
            }
            val bookOk = selectedBookTitle == null ||
                w.bookTitle == selectedBookTitle ||
                w.allContexts.any { it.bookTitle == selectedBookTitle }
            val groupOk = selectedGroup == null || w.customGroup == selectedGroup
            val tagOk = selectedExamTag == null || w.tags.contains(selectedExamTag!!, ignoreCase = true)
            val queryOk = searchQuery.isBlank() ||
                w.word.contains(searchQuery, ignoreCase = true) ||
                w.meaning.contains(searchQuery, ignoreCase = true) ||
                w.contextSentence.contains(searchQuery, ignoreCase = true) ||
                w.bookTitle.contains(searchQuery, ignoreCase = true) ||
                w.note.contains(searchQuery, ignoreCase = true) ||
                w.customGroup.contains(searchQuery, ignoreCase = true)
            statusOk && bookOk && groupOk && tagOk && queryOk
        }

        when (sortMode) {
            VocabSortMode.RECENT -> base.sortedByDescending { it.createdAt }
            VocabSortMode.DUE_FIRST -> base.sortedBy { it.nextReviewAt }
            VocabSortMode.STUBBORN -> base.sortedByDescending { it.lapseCount * 3 + it.encounterCount }
            VocabSortMode.ENCOUNTER -> base.sortedByDescending { it.encounterCount }
            VocabSortMode.ALPHA -> base.sortedBy { it.wordLower }
            VocabSortMode.MASTERY -> base.sortedBy { it.masteryLevel }
        }
    }

    // File picker for batch TXT/CSV word list import
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                }
                val report = container.vocabRepo.batchImportWordList(text, container.offlineDictionary)
                snackbarHostState.showSnackbar("导入完成：新增 ${report.addedCount} 词，跳过 ${report.skippedCount} 词")
            }.onFailure {
                snackbarHostState.showSnackbar("导入词表失败：${it.message}")
            }
        }
    }

    fun shareExportFile(asCsv: Boolean) {
        scope.launch {
            if (words.isEmpty()) {
                snackbarHostState.showSnackbar("单词本暂无词条可导出")
                return@launch
            }
            runCatching {
                val file = if (asCsv) container.vocabRepo.exportToCsv() else container.vocabRepo.exportToMarkdown()
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = if (asCsv) "text/csv" else "text/markdown"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "导出单词本"))
            }.onFailure {
                snackbarHostState.showSnackbar("导出失败：${it.message}")
            }
        }
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedIds.clear()
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("已选 ${selectedIds.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = ::exitSelectionMode) {
                            Icon(Icons.Rounded.Close, contentDescription = "退出多选")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (selectedIds.size == filteredWords.size) {
                                selectedIds.clear()
                            } else {
                                selectedIds.clear()
                                selectedIds.addAll(filteredWords.map { it.id })
                            }
                        }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = "全选")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = "单词本",
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp)
                        )
                    },
                    actions = {
                        IconButton(onClick = { showStatsSheet = true }) {
                            Icon(Icons.Rounded.BarChart, contentDescription = "学习统计")
                        }
                        IconButton(onClick = onOpenAudioRadio) {
                            Icon(Icons.Rounded.Headphones, contentDescription = "随身磨耳朵")
                        }
                        Box {
                            IconButton(onClick = { showAddMenu = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = "添加或导入单词")
                            }
                            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("手动添加单个生词") },
                                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                    onClick = {
                                        showAddMenu = false
                                        showAddDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("粘贴词表批量导入") },
                                    leadingIcon = { Icon(Icons.Rounded.FileUpload, contentDescription = null) },
                                    onClick = {
                                        showAddMenu = false
                                        showImportDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("从 TXT / CSV 文件导入词表") },
                                    leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                                    onClick = {
                                        showAddMenu = false
                                        importFileLauncher.launch(arrayOf("*/*"))
                                    }
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(Icons.Rounded.FileDownload, contentDescription = "导出单词本")
                            }
                            DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("导出为 CSV (Excel / Anki)") },
                                    onClick = {
                                        showExportMenu = false
                                        shareExportFile(asCsv = true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("导出为 Markdown 笔记") },
                                    onClick = {
                                        showExportMenu = false
                                        shareExportFile(asCsv = false)
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (selectionMode && selectedIds.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        BatchActionBtn(Icons.Rounded.Star, "标星") {
                            val ids = selectedIds.toList()
                            exitSelectionMode()
                            scope.launch {
                                container.vocabRepo.batchSetStarred(ids, true)
                                snackbarHostState.showSnackbar("已批量标星 ${ids.size} 词")
                            }
                        }
                        BatchActionBtn(Icons.Rounded.CheckCircle, "已掌握") {
                            val ids = selectedIds.toList()
                            exitSelectionMode()
                            scope.launch {
                                container.vocabRepo.batchMarkMastered(ids)
                                snackbarHostState.showSnackbar("已标为掌握 ${ids.size} 词")
                            }
                        }
                        BatchActionBtn(Icons.Rounded.Folder, "分组") {
                            showBatchGroupDialog = true
                        }
                        BatchActionBtn(Icons.Rounded.Replay, "重置") {
                            val ids = selectedIds.toList()
                            exitSelectionMode()
                            scope.launch {
                                container.vocabRepo.batchResetProgress(ids)
                                snackbarHostState.showSnackbar("已重置 ${ids.size} 词复习进度")
                            }
                        }
                        BatchActionBtn(Icons.Rounded.DeleteOutline, "删除", isDestructive = true) {
                            val ids = selectedIds.toList()
                            exitSelectionMode()
                            scope.launch {
                                container.vocabRepo.batchDelete(ids)
                                snackbarHostState.showSnackbar("已删除 ${ids.size} 词")
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Learning Dashboard & Daily Goal Card
            item {
                VocabDashboardCard(
                    total = words.size,
                    due = dueCount,
                    stubborn = stubbornWords.size,
                    mastered = analytics.masteredCount,
                    todayReviewed = analytics.todayReviewed,
                    dailyGoal = settings.dailyReviewGoal,
                    streakDays = analytics.streakDays,
                    onOpenStats = { showStatsSheet = true },
                    onStartReviewClick = { showReviewSetupSheet = true },
                    onAudioRadioClick = onOpenAudioRadio
                )
            }

            // 2. Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索单词、释义、例句、笔记或书名...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "清空")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. Status Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VocabFilter.entries.forEach { f ->
                        val suffix = when (f) {
                            VocabFilter.DUE -> if (dueCount > 0) " ($dueCount)" else ""
                            VocabFilter.STUBBORN -> if (stubbornWords.isNotEmpty()) " (${stubbornWords.size})" else ""
                            else -> ""
                        }
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text("${f.label}$suffix") }
                        )
                    }
                }
            }

            // 4. Quick Study Tools Bar: Red-Film Mask Mode + Sort + Advanced Filters Toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mask Mode Pill (红膜速览遮罩)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        VocabMaskMode.entries.forEach { m ->
                            val active = maskMode == m
                            Surface(
                                onClick = {
                                    maskMode = m
                                    peekedWordIds.clear()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (m != VocabMaskMode.NONE) {
                                        Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(3.dp))
                                    }
                                    Text(
                                        text = m.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 11.sp,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val hasAdv = selectedBookTitle != null || selectedGroup != null || selectedExamTag != null
                        IconButton(
                            onClick = { showAdvancedFilters = !showAdvancedFilters },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Rounded.FilterList,
                                contentDescription = "按书/分组/考纲筛选",
                                tint = if (hasAdv || showAdvancedFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "排序")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                VocabSortMode.entries.forEach { sm ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (sortMode == sm) "✓ ${sm.label}" else sm.label,
                                                fontWeight = if (sortMode == sm) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            sortMode = sm
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Collapsible Multi-Dimensional Filter Panel (Book / Group / Exam Tag)
            item {
                AnimatedVisibility(visible = showAdvancedFilters) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (availableBooks.isNotEmpty()) {
                                Text("按来源书籍", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedBookTitle == null,
                                        onClick = { selectedBookTitle = null },
                                        label = { Text("全部书籍", fontSize = 12.sp) }
                                    )
                                    availableBooks.forEach { (_, title) ->
                                        FilterChip(
                                            selected = selectedBookTitle == title,
                                            onClick = { selectedBookTitle = if (selectedBookTitle == title) null else title },
                                            label = { Text(title.take(16), fontSize = 12.sp) }
                                        )
                                    }
                                }
                            }

                            if (availableGroups.isNotEmpty()) {
                                Text("按自定义分组", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedGroup == null,
                                        onClick = { selectedGroup = null },
                                        label = { Text("全部分组", fontSize = 12.sp) }
                                    )
                                    availableGroups.forEach { g ->
                                        FilterChip(
                                            selected = selectedGroup == g,
                                            onClick = { selectedGroup = if (selectedGroup == g) null else g },
                                            label = { Text(g, fontSize = 12.sp) }
                                        )
                                    }
                                }
                            }

                            Text("按考纲级别", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = selectedExamTag == null,
                                    onClick = { selectedExamTag = null },
                                    label = { Text("全部", fontSize = 12.sp) }
                                )
                                EXAM_TAGS.forEach { tag ->
                                    FilterChip(
                                        selected = selectedExamTag == tag,
                                        onClick = { selectedExamTag = if (selectedExamTag == tag) null else tag },
                                        label = { Text(tag, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Word List
            if (filteredWords.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (words.isEmpty()) "还没有收录任何生词" else "当前筛选下没有单词",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (words.isEmpty()) "阅读时双击或长按划词点「记生词」，或点右上角「+」批量导入词表" else "切换其他筛选条件或清空搜索试试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredWords, key = { it.id }) { word ->
                    val isSelected = word.id in selectedIds
                    val isPeeked = word.id in peekedWordIds
                    VocabWordCard(
                        word = word,
                        maskMode = maskMode,
                        isPeeked = isPeeked,
                        selectionMode = selectionMode,
                        isSelected = isSelected,
                        onSpeak = {
                            container.ttsManager.speak(
                                text = word.word,
                                accent = settings.ttsAccent,
                                speed = settings.ttsSpeed,
                                preferHumanVoice = settings.preferHumanVoice
                            )
                        },
                        onToggleStar = {
                            scope.launch { container.vocabRepo.toggleStar(word) }
                        },
                        onTogglePeek = {
                            if (isPeeked) peekedWordIds.remove(word.id) else peekedWordIds.add(word.id)
                        },
                        onClick = {
                            if (selectionMode) {
                                if (isSelected) selectedIds.remove(word.id) else selectedIds.add(word.id)
                            } else if (maskMode != VocabMaskMode.NONE && !isPeeked) {
                                peekedWordIds.add(word.id)
                            } else {
                                onOpenWordDetail(word.id)
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                selectionMode = true
                                selectedIds.clear()
                                selectedIds.add(word.id)
                            } else {
                                if (isSelected) selectedIds.remove(word.id) else selectedIds.add(word.id)
                            }
                        }
                    )
                }
            }
        }
    }

    // ---- Modal Sheets & Dialogs ----

    if (showStatsSheet) {
        ModalBottomSheet(onDismissRequest = { showStatsSheet = false }) {
            VocabStatsSheetContent(
                analytics = analytics,
                dailyGoal = settings.dailyReviewGoal,
                stubbornWords = stubbornWords,
                onChangeDailyGoal = { g -> scope.launch { container.settingsRepo.updateDailyReviewGoal(g) } },
                onAttackStubborn = {
                    showStatsSheet = false
                    onStartReview(ReviewMode.FLASHCARD, ReviewScope.STUBBORN, "", 30)
                },
                onOpenWord = { id ->
                    showStatsSheet = false
                    onOpenWordDetail(id)
                }
            )
        }
    }

    if (showReviewSetupSheet) {
        ModalBottomSheet(onDismissRequest = { showReviewSetupSheet = false }) {
            ReviewSetupSheetContent(
                dueCount = dueCount,
                stubbornCount = stubbornWords.size,
                starredCount = words.count { it.starred },
                totalCount = words.size,
                availableBooks = availableBooks,
                onLaunch = { mode, reviewScope, bookId, limit ->
                    showReviewSetupSheet = false
                    onStartReview(mode, reviewScope, bookId, limit)
                }
            )
        }
    }

    if (showAddDialog) {
        AddVocabDialog(
            onDismiss = { showAddDialog = false },
            onSave = { word, phonetic, meaning, sentence, note, group ->
                showAddDialog = false
                scope.launch {
                    container.vocabRepo.addManualWord(word, phonetic, meaning, sentence, note, group)
                    snackbarHostState.showSnackbar("已添加 \"$word\"")
                }
            }
        )
    }

    if (showImportDialog) {
        BatchImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { rawText, group ->
                showImportDialog = false
                scope.launch {
                    val report = container.vocabRepo.batchImportWordList(rawText, container.offlineDictionary, group)
                    snackbarHostState.showSnackbar("批量导入完成：新增 ${report.addedCount} 词，跳过 ${report.skippedCount} 词")
                }
            }
        )
    }

    if (showBatchGroupDialog) {
        BatchGroupDialog(
            existingGroups = availableGroups,
            onDismiss = { showBatchGroupDialog = false },
            onConfirm = { groupName ->
                showBatchGroupDialog = false
                val ids = selectedIds.toList()
                exitSelectionMode()
                scope.launch {
                    container.vocabRepo.batchAssignGroup(ids, groupName)
                    snackbarHostState.showSnackbar("已将 ${ids.size} 词移入分组「${groupName.ifBlank { "默认" }}」")
                }
            }
        )
    }
}

@Composable
private fun VocabDashboardCard(
    total: Int,
    due: Int,
    stubborn: Int,
    mastered: Int,
    todayReviewed: Int,
    dailyGoal: Int,
    streakDays: Int,
    onOpenStats: () -> Unit,
    onStartReviewClick: () -> Unit,
    onAudioRadioClick: () -> Unit
) {
    val progress = (todayReviewed.toFloat() / dailyGoal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Today's goal & streak header (clickable -> opens full analytics sheet)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = Color(0xFFD9822B), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "连续打卡 $streakDays 天 · 今日 $todayReviewed/$dailyGoal 词",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                TextButton(
                    onClick = onOpenStats,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("统计热力图 →", fontSize = 11.sp)
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn(due.toString(), "待复习", MaterialTheme.colorScheme.secondary)
                StatColumn(stubborn.toString(), "顽固词", Color(0xFFB3261E))
                StatColumn(total.toString(), "累计词", MaterialTheme.colorScheme.primary)
                StatColumn(mastered.toString(), "已掌握", MaterialTheme.colorScheme.primary)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStartReviewClick,
                    enabled = total > 0,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.6f)
                ) {
                    Icon(Icons.Rounded.School, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (total == 0) "收录生词后开启复习" else if (due > 0) "智能复习 ($due)" else "自由复习",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                OutlinedButton(
                    onClick = onAudioRadioClick,
                    enabled = total > 0,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("磨耳朵", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
            color = accent
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VocabWordCard(
    word: VocabWord,
    maskMode: VocabMaskMode,
    isPeeked: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onSpeak: () -> Unit,
    onToggleStar: () -> Unit,
    onTogglePeek: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val masteryLabel = when (word.masteryLevel) {
        0 -> "新词"
        1 -> "初记"
        2 -> "熟悉"
        else -> "已掌握"
    }
    val masteryBg = when (word.masteryLevel) {
        0 -> MaterialTheme.colorScheme.secondaryContainer
        3 -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val hideWord = maskMode == VocabMaskMode.HIDE_WORD && !isPeeked
    val hideMeaning = maskMode == VocabMaskMode.HIDE_MEANING && !isPeeked

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 1.6.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (hideWord) {
                            Surface(
                                onClick = onTogglePeek,
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "点击显示英文 (${word.word.length} 字母)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Text(
                                text = word.word,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(shape = RoundedCornerShape(6.dp), color = masteryBg) {
                            Text(
                                text = masteryLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (word.isStubborn) {
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFCE8E6)) {
                                Text(
                                    text = "顽固",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                                    color = Color(0xFFB3261E),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (!hideWord && word.phonetic.isNotBlank()) {
                        Text(
                            text = "/${word.phonetic}/" + if (word.encounterCount > 1) " · 遇 ${word.encounterCount} 次" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onSpeak, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "真人发音",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (!selectionMode) {
                    IconButton(onClick = onToggleStar, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (word.starred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = "收藏",
                            tint = if (word.starred) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (word.meaning.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                if (hideMeaning) {
                    Surface(
                        onClick = onTogglePeek,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "已遮罩中文释义 · 点击揭晓",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                } else {
                    Text(
                        text = word.meaning,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!hideWord && word.contextSentence.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
                ) {
                    Text(
                        text = "“${word.contextSentence}”",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp, fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (word.customGroup.isNotBlank() || word.bookTitle.isNotBlank() || word.allContexts.size > 1) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val leftMeta = buildString {
                        if (word.customGroup.isNotBlank()) append("[${word.customGroup}] ")
                        if (word.allContexts.size > 1) append("${word.allContexts.size} 条阅读原句")
                    }
                    Text(
                        text = leftMeta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (word.bookTitle.isNotBlank()) {
                        Text(
                            text = "《${word.bookTitle}》",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchActionBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = tint)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewSetupSheetContent(
    dueCount: Int,
    stubbornCount: Int,
    starredCount: Int,
    totalCount: Int,
    availableBooks: List<Pair<String, String>>,
    onLaunch: (ReviewMode, ReviewScope, String, Int) -> Unit
) {
    var selectedMode by remember { mutableStateOf(ReviewMode.FLASHCARD) }
    var selectedScope by remember { mutableStateOf(if (dueCount > 0) ReviewScope.DUE else ReviewScope.ALL) }
    var selectedBookId by remember { mutableStateOf(availableBooks.firstOrNull()?.first.orEmpty()) }
    var selectedLimit by remember { mutableIntStateOf(20) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("定制本次复习计划", style = MaterialTheme.typography.titleLarge)

        // 1. Mode Selection (5 modes)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("选择训练模式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReviewMode.entries.forEach { m ->
                    FilterChip(
                        selected = selectedMode == m,
                        onClick = { selectedMode = m },
                        label = { Text(m.label) }
                    )
                }
            }
            Text(selectedMode.desc, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 2. Scope Selection
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("选择词条范围", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReviewScope.entries.forEach { sc ->
                    if (sc == ReviewScope.BOOK && availableBooks.isEmpty()) return@forEach
                    val cnt = when (sc) {
                        ReviewScope.DUE -> " ($dueCount)"
                        ReviewScope.STUBBORN -> " ($stubbornCount)"
                        ReviewScope.STARRED -> " ($starredCount)"
                        ReviewScope.ALL -> " ($totalCount)"
                        ReviewScope.BOOK -> ""
                    }
                    FilterChip(
                        selected = selectedScope == sc,
                        onClick = { selectedScope = sc },
                        label = { Text("${sc.label}$cnt") }
                    )
                }
            }
            if (selectedScope == ReviewScope.BOOK && availableBooks.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableBooks.forEach { (id, title) ->
                        FilterChip(
                            selected = selectedBookId == id,
                            onClick = { selectedBookId = id },
                            label = { Text(title.take(18), fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        // 3. Session Size
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("本轮词量", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15 to "15词", 20 to "20词", 30 to "30词", 50 to "50词", 0 to "不限").forEach { (num, lbl) ->
                    FilterChip(
                        selected = selectedLimit == num,
                        onClick = { selectedLimit = num },
                        label = { Text(lbl) }
                    )
                }
            }
        }

        Button(
            onClick = {
                onLaunch(selectedMode, selectedScope, if (selectedScope == ReviewScope.BOOK) selectedBookId else "", selectedLimit)
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Rounded.School, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("开始 ${selectedMode.label}", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AddVocabDialog(
    onDismiss: () -> Unit,
    onSave: (word: String, phonetic: String, meaning: String, sentence: String, note: String, group: String) -> Unit
) {
    var word by remember { mutableStateOf("") }
    var phonetic by remember { mutableStateOf("") }
    var meaning by remember { mutableStateOf("") }
    var sentence by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动添加生词") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = word, onValueChange = { word = it }, label = { Text("英文单词或短语 *") }, singleLine = true)
                OutlinedTextField(value = phonetic, onValueChange = { phonetic = it }, label = { Text("音标（选填）") }, singleLine = true)
                OutlinedTextField(value = meaning, onValueChange = { meaning = it }, label = { Text("中文释义 *") }, maxLines = 3)
                OutlinedTextField(value = sentence, onValueChange = { sentence = it }, label = { Text("例句（选填）") }, maxLines = 2)
                OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text("自定义分组（选填）") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("助记笔记（选填）") }, maxLines = 2)
            }
        },
        confirmButton = {
            TextButton(
                enabled = word.isNotBlank() && meaning.isNotBlank(),
                onClick = { onSave(word, phonetic, meaning, sentence, note, group) }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun BatchImportDialog(
    onDismiss: () -> Unit,
    onImport: (rawText: String, group: String) -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("粘贴词表批量导入") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "每行一个单词（或逗号分隔），也支持「单词 + Tab + 释义」。内置 4.1 万离线词库将自动补全音标、释义与考纲标签。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text("导入到分组（选填，如：雅思核心）") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("在此粘贴英文单词列表") },
                    placeholder = { Text("serendipity\nubiquitous\nephemeral") },
                    minLines = 5,
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = rawText.isNotBlank(),
                onClick = { onImport(rawText, group) }
            ) { Text("开始导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchGroupDialog(
    existingGroups: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var group by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量设置分组") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text("分组名称（留空则移回默认）") },
                    singleLine = true
                )
                if (existingGroups.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        existingGroups.forEach { g ->
                            FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(group) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
