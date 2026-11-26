package com.lingoreader.app.ui.bookshelf

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingoreader.app.AppContainer
import com.lingoreader.app.data.model.Book
import com.lingoreader.app.data.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    container: AppContainer,
    onOpenBook: (String) -> Unit
) {
    val books by container.bookRepo.books.collectAsState()
    val vocabWords by container.vocabRepo.words.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val bookVocabCounts = remember(vocabWords) {
        val counts = HashMap<String, Int>()
        for (w in vocabWords) {
            val bookIds = buildSet {
                if (w.bookId.isNotBlank()) add(w.bookId)
                w.allContexts.forEach { if (it.bookId.isNotBlank()) add(it.bookId) }
            }
            for (bId in bookIds) {
                counts[bId] = (counts[bId] ?: 0) + 1
            }
        }
        counts
    }

    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var selectedFormat by remember { mutableStateOf<BookFormat?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }

    // Use "*/*" so Android file pickers never grey out .md or .epub files regardless of ROM MIME mapping
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isImporting = true
            val res = container.bookRepo.importFromUri(uri)
            isImporting = false
            res.onSuccess { imported ->
                snackbarHostState.showSnackbar("已导入《${imported.title}》")
            }.onFailure { err ->
                snackbarHostState.showSnackbar(err.message ?: "导入失败，请确认文件为 TXT / MD / EPUB")
            }
        }
    }

    val filteredBooks = remember(books, searchQuery, selectedFormat) {
        books.filter { book ->
            val fmtOk = selectedFormat == null || book.format == selectedFormat
            val queryOk = searchQuery.isBlank() ||
                book.title.contains(searchQuery, ignoreCase = true) ||
                book.author.contains(searchQuery, ignoreCase = true)
            fmtOk && queryOk
        }
    }

    val recentBook = remember(books) {
        books.firstOrNull { it.lastReadAt > 0L }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "奶读",
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp)
                        )
                        Text(
                            text = "支持 TXT · Markdown · EPUB 双击查词与划词翻译",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "搜索书架"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openDocLauncher.launch(arrayOf("*/*")) },
                icon = {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                    }
                },
                text = { Text(if (isImporting) "正在导入..." else "导入书籍") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showSearch) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索书名或作者...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Recent reading hero card
            if (recentBook != null && searchQuery.isBlank() && selectedFormat == null) {
                item {
                    ContinueReadingCard(
                        book = recentBook,
                        onClick = { onOpenBook(recentBook.id) }
                    )
                }
            }

            // Format filter chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedFormat == null,
                        onClick = { selectedFormat = null },
                        label = { Text("全部 (${books.size})") }
                    )
                    BookFormat.entries.forEach { fmt ->
                        val count = books.count { it.format == fmt }
                        FilterChip(
                            selected = selectedFormat == fmt,
                            onClick = { selectedFormat = if (selectedFormat == fmt) null else fmt },
                            label = { Text("${fmt.label} ($count)") }
                        )
                    }
                }
            }

            if (filteredBooks.isEmpty()) {
                item {
                    EmptyBookshelfState(
                        isFiltered = searchQuery.isNotBlank() || selectedFormat != null,
                        onImportClick = { openDocLauncher.launch(arrayOf("*/*")) }
                    )
                }
            } else {
                items(filteredBooks, key = { it.id }) { book ->
                    BookListCard(
                        book = book,
                        vocabCount = bookVocabCounts[book.id] ?: 0,
                        onClick = { onOpenBook(book.id) },
                        onDeleteClick = { bookToDelete = book }
                    )
                }
            }
        }
    }

    bookToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("移除书籍") },
            text = { Text("确定要从书架移除《${target.title}》吗？已收录的单词本词条不会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        bookToDelete = null
                        scope.launch {
                            container.bookRepo.deleteBook(target)
                            snackbarHostState.showSnackbar("已移除《${target.title}》")
                        }
                    }
                ) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ContinueReadingCard(
    book: Book,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverThumb(book = book, widthDp = 52, heightDp = 72)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "继续阅读",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { book.overallProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "第 ${book.currentChapter + 1}/${book.totalChapters.coerceAtLeast(1)} 章 · ${(book.overallProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "继续阅读",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun BookListCard(
    book: Book,
    vocabCount: Int,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverThumb(book = book, widthDp = 58, heightDp = 82)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FormatChipBadge(book.format)
                    if (vocabCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "生词 $vocabCount",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (book.author.isNotBlank()) {
                        Text(
                            text = book.author,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { book.overallProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${book.totalChapters} 章 · 已读 ${(book.overallProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatFileSize(book.fileSize),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "删除书籍",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val CoverBitmapCache = object : LruCache<String, ImageBitmap>(32) {}

@Composable
private fun BookCoverThumb(book: Book, widthDp: Int, heightDp: Int) {
    val coverBitmap by produceState<ImageBitmap?>(
        initialValue = book.coverPath?.let { CoverBitmapCache.get(it) },
        key1 = book.coverPath
    ) {
        val path = book.coverPath
        if (path.isNullOrBlank()) {
            value = null
            return@produceState
        }
        CoverBitmapCache.get(path)?.let {
            value = it
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = File(path)
                if (!f.exists()) return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 240 || bounds.outHeight / sample > 340) {
                    sample *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(path, opts)?.asImageBitmap()?.also { bmp ->
                    CoverBitmapCache.put(path, bmp)
                }
            }.getOrNull()
        }
    }

    val gradientColors = when (book.format) {
        BookFormat.EPUB -> listOf(Color(0xFF2E5E4E), Color(0xFF1D3F34))
        BookFormat.MD -> listOf(Color(0xFF3E5A75), Color(0xFF263B50))
        BookFormat.TXT -> listOf(Color(0xFF8C5B32), Color(0xFF5F3C1E))
    }

    Box(
        modifier = Modifier
            .size(width = widthDp.dp, height = heightDp.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = coverBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoStories,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontFamily = FontFamily.Serif
                    ),
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.format.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFFFFD8A8)
                )
            }
        }
    }
}

@Composable
private fun FormatChipBadge(format: BookFormat) {
    val bg = when (format) {
        BookFormat.EPUB -> Color(0xFFD5E8E0)
        BookFormat.MD -> Color(0xFFDCE7F3)
        BookFormat.TXT -> Color(0xFFF6E6D2)
    }
    val fg = when (format) {
        BookFormat.EPUB -> Color(0xFF1C4A3A)
        BookFormat.MD -> Color(0xFF234261)
        BookFormat.TXT -> Color(0xFF6B4118)
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = format.label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EmptyBookshelfState(
    isFiltered: Boolean,
    onImportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isFiltered) "没有找到匹配的书籍" else "书架还是空的",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isFiltered) "试试切换格式筛选或清空搜索词" else "点击下方按钮导入本地 TXT、Markdown 或 EPUB 英语电子书",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!isFiltered) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onImportClick) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("导入第一本书")
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "%.1fMB".format(bytes / (1024.0 * 1024.0))
    }
}
