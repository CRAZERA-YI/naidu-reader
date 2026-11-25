package com.lingoreader.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lingoreader.app.data.model.Book
import com.lingoreader.app.data.model.BookFormat
import com.lingoreader.app.data.model.DailyStudyStat
import com.lingoreader.app.data.model.VocabContext
import com.lingoreader.app.data.model.VocabWord

class AppDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    private val gson = Gson()
    private val contextsType = object : TypeToken<List<VocabContext>>() {}.type

    companion object {
        private const val DATABASE_NAME = "lingoreader_app.db"
        private const val DATABASE_VERSION = 3

        private const val TABLE_BOOKS = "books"
        private const val TABLE_VOCAB = "vocab_words"
        private const val TABLE_DICT_CACHE = "dict_cache"
        private const val TABLE_STUDY_LOGS = "study_logs"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_BOOKS (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                author TEXT NOT NULL,
                format TEXT NOT NULL,
                file_path TEXT NOT NULL,
                cover_path TEXT,
                total_chapters INTEGER NOT NULL DEFAULT 1,
                current_chapter INTEGER NOT NULL DEFAULT 0,
                scroll_progress REAL NOT NULL DEFAULT 0,
                file_size INTEGER NOT NULL DEFAULT 0,
                added_at INTEGER NOT NULL,
                last_read_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_books_last_read ON $TABLE_BOOKS(last_read_at DESC, added_at DESC)")

        db.execSQL(
            """
            CREATE TABLE $TABLE_VOCAB (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word TEXT NOT NULL,
                word_lower TEXT NOT NULL UNIQUE,
                lemma TEXT NOT NULL DEFAULT '',
                phonetic TEXT NOT NULL DEFAULT '',
                phonetic_uk TEXT NOT NULL DEFAULT '',
                meaning TEXT NOT NULL DEFAULT '',
                english_def TEXT NOT NULL DEFAULT '',
                tags TEXT NOT NULL DEFAULT '',
                context_sentence TEXT NOT NULL DEFAULT '',
                book_id TEXT NOT NULL DEFAULT '',
                book_title TEXT NOT NULL DEFAULT '',
                chapter_index INTEGER NOT NULL DEFAULT -1,
                contexts_json TEXT NOT NULL DEFAULT '[]',
                custom_group TEXT NOT NULL DEFAULT '',
                note TEXT NOT NULL DEFAULT '',
                starred INTEGER NOT NULL DEFAULT 0,
                encounter_count INTEGER NOT NULL DEFAULT 1,
                lapse_count INTEGER NOT NULL DEFAULT 0,
                repetitions INTEGER NOT NULL DEFAULT 0,
                interval_days INTEGER NOT NULL DEFAULT 0,
                ease_factor REAL NOT NULL DEFAULT 2.5,
                next_review_at INTEGER NOT NULL,
                review_count INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_vocab_next_review ON $TABLE_VOCAB(next_review_at ASC)")
        db.execSQL("CREATE INDEX idx_vocab_lemma ON $TABLE_VOCAB(lemma)")

        createDictCacheTable(db)
        createStudyLogsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createDictCacheTable(db)
        }
        if (oldVersion < 3) {
            val alters = listOf(
                "ALTER TABLE $TABLE_VOCAB ADD COLUMN phonetic_uk TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE $TABLE_VOCAB ADD COLUMN chapter_index INTEGER NOT NULL DEFAULT -1",
                "ALTER TABLE $TABLE_VOCAB ADD COLUMN contexts_json TEXT NOT NULL DEFAULT '[]'",
                "ALTER TABLE $TABLE_VOCAB ADD COLUMN custom_group TEXT NOT NULL DEFAULT ''",
                "ALTER TABLE $TABLE_VOCAB ADD COLUMN encounter_count INTEGER NOT NULL DEFAULT 1",
                "ALTER TABLE $TABLE_VOCAB ADD COLUMN lapse_count INTEGER NOT NULL DEFAULT 0"
            )
            for (sql in alters) {
                runCatching { db.execSQL(sql) }
            }
            createStudyLogsTable(db)
        }
    }

    private fun createDictCacheTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_DICT_CACHE (
                word_lower TEXT PRIMARY KEY,
                payload_json TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createStudyLogsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_STUDY_LOGS (
                date_key TEXT PRIMARY KEY,
                reviewed_count INTEGER NOT NULL DEFAULT 0,
                remembered_count INTEGER NOT NULL DEFAULT 0,
                added_count INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    // ---- Rich Dictionary Cache ----

    fun getCachedDictJson(wordLower: String): String? {
        readableDatabase.rawQuery(
            "SELECT payload_json FROM $TABLE_DICT_CACHE WHERE word_lower = ? LIMIT 1",
            arrayOf(wordLower.trim().lowercase())
        ).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    fun putCachedDictJson(wordLower: String, json: String) {
        val key = wordLower.trim().lowercase()
        if (key.isEmpty()) return
        val cv = ContentValues().apply {
            put("word_lower", key)
            put("payload_json", json)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(TABLE_DICT_CACHE, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---- Study Logs ----

    fun incrementStudyLog(dateKey: String, reviewedDelta: Int = 0, rememberedDelta: Int = 0, addedDelta: Int = 0) {
        val db = writableDatabase
        db.execSQL(
            """
            INSERT INTO $TABLE_STUDY_LOGS (date_key, reviewed_count, remembered_count, added_count)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(date_key) DO UPDATE SET
                reviewed_count = reviewed_count + excluded.reviewed_count,
                remembered_count = remembered_count + excluded.remembered_count,
                added_count = added_count + excluded.added_count
            """.trimIndent(),
            arrayOf(dateKey, reviewedDelta, rememberedDelta, addedDelta)
        )
    }

    fun getAllStudyLogs(): List<DailyStudyStat> {
        val out = mutableListOf<DailyStudyStat>()
        readableDatabase.rawQuery(
            "SELECT date_key, reviewed_count, remembered_count, added_count FROM $TABLE_STUDY_LOGS ORDER BY date_key DESC LIMIT 120",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    DailyStudyStat(
                        dateKey = c.getString(0),
                        reviewedCount = c.getInt(1),
                        rememberedCount = c.getInt(2),
                        addedCount = c.getInt(3)
                    )
                )
            }
        }
        return out
    }

    // ---- Books ----

    fun getAllBooks(): List<Book> {
        val list = mutableListOf<Book>()
        readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_BOOKS ORDER BY CASE WHEN last_read_at > 0 THEN last_read_at ELSE added_at END DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.toBook())
            }
        }
        return list
    }

    fun getBookById(id: String): Book? {
        readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_BOOKS WHERE id = ? LIMIT 1",
            arrayOf(id)
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toBook() else null
        }
    }

    fun upsertBook(book: Book) {
        val cv = ContentValues().apply {
            put("id", book.id)
            put("title", book.title)
            put("author", book.author)
            put("format", book.format.name)
            put("file_path", book.filePath)
            put("cover_path", book.coverPath)
            put("total_chapters", book.totalChapters)
            put("current_chapter", book.currentChapter)
            put("scroll_progress", book.scrollProgress)
            put("file_size", book.fileSize)
            put("added_at", book.addedAt)
            put("last_read_at", book.lastReadAt)
        }
        writableDatabase.insertWithOnConflict(TABLE_BOOKS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateBookProgress(bookId: String, chapter: Int, scrollProgress: Float, totalChapters: Int) {
        val cv = ContentValues().apply {
            put("current_chapter", chapter)
            put("scroll_progress", scrollProgress.coerceIn(0f, 1f))
            if (totalChapters > 0) put("total_chapters", totalChapters)
            put("last_read_at", System.currentTimeMillis())
        }
        writableDatabase.update(TABLE_BOOKS, cv, "id = ?", arrayOf(bookId))
    }

    fun deleteBook(bookId: String) {
        writableDatabase.delete(TABLE_BOOKS, "id = ?", arrayOf(bookId))
    }

    private fun Cursor.toBook(): Book {
        val fmtStr = getString(getColumnIndexOrThrow("format"))
        val fmt = runCatching { BookFormat.valueOf(fmtStr) }.getOrDefault(BookFormat.TXT)
        return Book(
            id = getString(getColumnIndexOrThrow("id")),
            title = getString(getColumnIndexOrThrow("title")),
            author = getString(getColumnIndexOrThrow("author")),
            format = fmt,
            filePath = getString(getColumnIndexOrThrow("file_path")),
            coverPath = getString(getColumnIndexOrThrow("cover_path")),
            totalChapters = getInt(getColumnIndexOrThrow("total_chapters")),
            currentChapter = getInt(getColumnIndexOrThrow("current_chapter")),
            scrollProgress = getFloat(getColumnIndexOrThrow("scroll_progress")),
            fileSize = getLong(getColumnIndexOrThrow("file_size")),
            addedAt = getLong(getColumnIndexOrThrow("added_at")),
            lastReadAt = getLong(getColumnIndexOrThrow("last_read_at"))
        )
    }

    // ---- Vocabulary ----

    fun getAllVocab(): List<VocabWord> {
        val list = mutableListOf<VocabWord>()
        readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_VOCAB ORDER BY created_at DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.toVocabWord())
            }
        }
        return list
    }

    fun getVocabById(id: Long): VocabWord? {
        readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_VOCAB WHERE id = ? LIMIT 1",
            arrayOf(id.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toVocabWord() else null
        }
    }

    fun findVocabByWordOrLemma(word: String, lemma: String = ""): VocabWord? {
        val wl = word.trim().lowercase()
        val ll = lemma.trim().lowercase()
        val sql = if (ll.isNotBlank() && ll != wl) {
            "SELECT * FROM $TABLE_VOCAB WHERE word_lower = ? OR word_lower = ? OR lemma = ? LIMIT 1"
        } else {
            "SELECT * FROM $TABLE_VOCAB WHERE word_lower = ? OR lemma = ? LIMIT 1"
        }
        val args = if (ll.isNotBlank() && ll != wl) arrayOf(wl, ll, ll) else arrayOf(wl, wl)
        readableDatabase.rawQuery(sql, args).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toVocabWord() else null
        }
    }

    fun upsertVocab(vocab: VocabWord): Long {
        val wl = vocab.word.trim().lowercase()
        val existing = findVocabByWordOrLemma(wl)
        val cv = vocab.toContentValues(wl)
        return if (existing != null) {
            writableDatabase.update(TABLE_VOCAB, cv, "id = ?", arrayOf(existing.id.toString()))
            existing.id
        } else {
            cv.put("created_at", vocab.createdAt)
            writableDatabase.insert(TABLE_VOCAB, null, cv)
        }
    }

    fun updateVocab(vocab: VocabWord) {
        val cv = vocab.toContentValues(vocab.word.trim().lowercase())
        writableDatabase.update(TABLE_VOCAB, cv, "id = ?", arrayOf(vocab.id.toString()))
    }

    fun deleteVocab(id: Long) {
        writableDatabase.delete(TABLE_VOCAB, "id = ?", arrayOf(id.toString()))
    }

    fun deleteVocabByWord(word: String) {
        writableDatabase.delete(TABLE_VOCAB, "word_lower = ?", arrayOf(word.trim().lowercase()))
    }

    fun batchDeleteVocab(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) {
                db.delete(TABLE_VOCAB, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun batchUpdateVocab(ids: Collection<Long>, cv: ContentValues) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) {
                db.update(TABLE_VOCAB, cv, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun VocabWord.toContentValues(wl: String): ContentValues {
        return ContentValues().apply {
            put("word", word.trim())
            put("word_lower", wl)
            put("lemma", lemma.trim().lowercase())
            put("phonetic", phonetic)
            put("phonetic_uk", phoneticUk)
            put("meaning", meaning)
            put("english_def", englishDef)
            put("tags", tags)
            put("context_sentence", contextSentence)
            put("book_id", bookId)
            put("book_title", bookTitle)
            put("chapter_index", chapterIndex)
            put("contexts_json", gson.toJson(contexts))
            put("custom_group", customGroup.trim())
            put("note", note)
            put("starred", if (starred) 1 else 0)
            put("encounter_count", encounterCount.coerceAtLeast(1))
            put("lapse_count", lapseCount.coerceAtLeast(0))
            put("repetitions", repetitions)
            put("interval_days", intervalDays)
            put("ease_factor", easeFactor)
            put("next_review_at", nextReviewAt)
            put("review_count", reviewCount)
            put("updated_at", System.currentTimeMillis())
        }
    }

    private fun Cursor.toVocabWord(): VocabWord {
        val ctxJson = getStringOrDefault("contexts_json", "[]")
        val parsedContexts: List<VocabContext> = runCatching {
            gson.fromJson<List<VocabContext>>(ctxJson, contextsType) ?: emptyList()
        }.getOrDefault(emptyList())

        return VocabWord(
            id = getLong(getColumnIndexOrThrow("id")),
            word = getString(getColumnIndexOrThrow("word")),
            wordLower = getString(getColumnIndexOrThrow("word_lower")),
            lemma = getString(getColumnIndexOrThrow("lemma")),
            phonetic = getString(getColumnIndexOrThrow("phonetic")),
            phoneticUk = getStringOrDefault("phonetic_uk", ""),
            meaning = getString(getColumnIndexOrThrow("meaning")),
            englishDef = getString(getColumnIndexOrThrow("english_def")),
            tags = getString(getColumnIndexOrThrow("tags")),
            contextSentence = getString(getColumnIndexOrThrow("context_sentence")),
            bookId = getString(getColumnIndexOrThrow("book_id")),
            bookTitle = getString(getColumnIndexOrThrow("book_title")),
            chapterIndex = getIntOrDefault("chapter_index", -1),
            contexts = parsedContexts,
            customGroup = getStringOrDefault("custom_group", ""),
            note = getString(getColumnIndexOrThrow("note")),
            starred = getInt(getColumnIndexOrThrow("starred")) == 1,
            encounterCount = getIntOrDefault("encounter_count", 1),
            lapseCount = getIntOrDefault("lapse_count", 0),
            repetitions = getInt(getColumnIndexOrThrow("repetitions")),
            intervalDays = getInt(getColumnIndexOrThrow("interval_days")),
            easeFactor = getDouble(getColumnIndexOrThrow("ease_factor")),
            nextReviewAt = getLong(getColumnIndexOrThrow("next_review_at")),
            reviewCount = getInt(getColumnIndexOrThrow("review_count")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at"))
        )
    }

    private fun Cursor.getStringOrDefault(col: String, defaultVal: String): String {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else defaultVal
    }

    private fun Cursor.getIntOrDefault(col: String, defaultVal: Int): Int {
        val idx = getColumnIndex(col)
        return if (idx >= 0 && !isNull(idx)) getInt(idx) else defaultVal
    }
}
