package com.lingoreader.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lingoreader.app.data.db.AppDatabaseHelper
import com.lingoreader.app.data.model.Book
import com.lingoreader.app.data.model.BookFormat
import com.lingoreader.app.data.model.ParsedBook
import com.lingoreader.app.data.prefs.SettingsRepository
import com.lingoreader.app.parser.BookParserFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BookRepository(
    private val context: Context,
    private val db: AppDatabaseHelper,
    private val settingsRepo: SettingsRepository
) {
    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val parsedCache = ConcurrentHashMap<String, ParsedBook>()

    private val booksRootDir: File
        get() = File(context.filesDir, "books").apply { mkdirs() }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (!settingsRepo.isSamplesSeeded()) {
            seedSampleBooks()
            settingsRepo.markSamplesSeeded()
        }
        refreshBooks()
    }

    suspend fun refreshBooks() = withContext(Dispatchers.IO) {
        _books.value = db.getAllBooks()
    }

    suspend fun getBookById(bookId: String): Book? = withContext(Dispatchers.IO) {
        db.getBookById(bookId)
    }

    suspend fun loadParsedBook(book: Book): ParsedBook = withContext(Dispatchers.IO) {
        parsedCache[book.id]?.let { return@withContext it }
        val parsed = BookParserFactory.parseBook(book)
        parsedCache[book.id] = parsed
        if (parsed.chapters.size != book.totalChapters) {
            db.updateBookProgress(book.id, book.currentChapter, book.scrollProgress, parsed.chapters.size)
            refreshBooks()
        }
        parsed
    }

    suspend fun importFromUri(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = resolveDisplayName(uri) ?: "Imported_${System.currentTimeMillis()}.txt"
            val mime = context.contentResolver.getType(uri).orEmpty()
            val format = BookFormat.fromFileName(displayName)
                ?: when {
                    mime.contains("epub", true) -> BookFormat.EPUB
                    mime.contains("markdown", true) -> BookFormat.MD
                    mime.startsWith("text/", true) -> BookFormat.TXT
                    else -> throw IllegalArgumentException("暂不支持此文件格式，请导入 TXT、MD 或 EPUB 文件")
                }

            val bookId = UUID.randomUUID().toString()
            val bookDir = File(booksRootDir, bookId).apply { mkdirs() }
            val safeFileName = "book.${format.extension}"
            val destFile = File(bookDir, safeFileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("无法读取所选文件")

            val fallbackTitle = displayName.substringBeforeLast('.').ifBlank { displayName }
            val parsed = BookParserFactory.parseFile(destFile, format, fallbackTitle)

            val book = Book(
                id = bookId,
                title = parsed.title.ifBlank { fallbackTitle },
                author = parsed.author,
                format = format,
                filePath = destFile.absolutePath,
                coverPath = parsed.coverPath,
                totalChapters = parsed.chapters.size,
                currentChapter = 0,
                scrollProgress = 0f,
                fileSize = destFile.length(),
                addedAt = System.currentTimeMillis(),
                lastReadAt = 0L
            )
            db.upsertBook(book)
            parsedCache[book.id] = parsed
            refreshBooks()
            book
        }
    }

    suspend fun updateProgress(bookId: String, chapterIndex: Int, scrollProgress: Float, totalChapters: Int) =
        withContext(Dispatchers.IO) {
            val clampedProgress = scrollProgress.coerceIn(0f, 1f)
            val now = System.currentTimeMillis()
            db.updateBookProgress(bookId, chapterIndex, clampedProgress, totalChapters)
            _books.value = _books.value.map { b ->
                if (b.id == bookId) {
                    b.copy(
                        currentChapter = chapterIndex,
                        scrollProgress = clampedProgress,
                        totalChapters = if (totalChapters > 0) totalChapters else b.totalChapters,
                        lastReadAt = now
                    )
                } else {
                    b
                }
            }
        }

    suspend fun deleteBook(book: Book) = withContext(Dispatchers.IO) {
        parsedCache.remove(book.id)
        db.deleteBook(book.id)
        val bookDir = File(book.filePath).parentFile
        if (bookDir != null && bookDir.parentFile?.absolutePath == booksRootDir.absolutePath) {
            bookDir.deleteRecursively()
        }
        refreshBooks()
    }

    private fun seedSampleBooks() {
        val samples = listOf(
            Triple("samples/Welcome_Guide.md", "The Art of Reading Deeply", BookFormat.MD),
            Triple("samples/Aesops_Fables_Sample.epub", "Aesop's Fables (Selected)", BookFormat.EPUB),
            Triple("samples/The_Gift_of_the_Magi.txt", "The Gift of the Magi", BookFormat.TXT)
        )
        val now = System.currentTimeMillis()
        samples.forEachIndexed { idx, (assetPath, fallbackTitle, format) ->
            runCatching {
                val bookId = "sample_${idx + 1}"
                val bookDir = File(booksRootDir, bookId).apply { mkdirs() }
                val destFile = File(bookDir, "book.${format.extension}")
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val parsed = BookParserFactory.parseFile(destFile, format, fallbackTitle)
                val book = Book(
                    id = bookId,
                    title = parsed.title.ifBlank { fallbackTitle },
                    author = parsed.author.ifBlank {
                        when (idx) {
                            0 -> "奶读使用指南"
                            2 -> "O. Henry"
                            else -> ""
                        }
                    },
                    format = format,
                    filePath = destFile.absolutePath,
                    coverPath = parsed.coverPath,
                    totalChapters = parsed.chapters.size,
                    currentChapter = 0,
                    scrollProgress = 0f,
                    fileSize = destFile.length(),
                    addedAt = now - (idx * 1000L),
                    lastReadAt = 0L
                )
                db.upsertBook(book)
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return c.getString(idx)
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
