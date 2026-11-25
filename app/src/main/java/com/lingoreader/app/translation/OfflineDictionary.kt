package com.lingoreader.app.translation

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.lingoreader.app.data.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

class OfflineDictionary(private val context: Context) {

    private val mutex = Mutex()
    @Volatile
    private var db: SQLiteDatabase? = null
    @Volatile
    private var cachedWordCount: Int = 0

    companion object {
        private const val ASSET_GZ = "dictionary.zdb"
        private const val DB_FILE_NAME = "ecdict_offline_v1.db"

        private val TAG_DISPLAY_MAP = mapOf(
            "zk" to "中考",
            "gk" to "高考",
            "cet4" to "CET4",
            "cet6" to "CET6",
            "ky" to "考研",
            "toefl" to "TOEFL",
            "ielts" to "IELTS",
            "gre" to "GRE"
        )

        fun cleanQuery(raw: String): String {
            return raw.trim()
                .replace('\u2019', '\'')
                .replace('\u2018', '\'')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .trim { ch -> !ch.isLetterOrDigit() && ch != '\'' && ch != '-' }
                .removeSuffix("'s")
                .removeSuffix("'S")
                .trim()
        }

        fun generateMorphCandidates(word: String): List<Pair<String, String>> {
            val out = mutableListOf<Pair<String, String>>()
            if (word.length < 3 || word.contains(' ')) return out

            if (word.endsWith("ies") && word.length > 4) {
                out.add(word.dropLast(3) + "y" to "复数/三单")
            }
            if (word.endsWith("es") && word.length > 3) {
                out.add(word.dropLast(2) to "复数/三单")
                out.add(word.dropLast(1) to "复数/三单")
            }
            if (word.endsWith("s") && !word.endsWith("ss") && word.length > 3) {
                out.add(word.dropLast(1) to "复数/三单")
            }
            if (word.endsWith("ied") && word.length > 4) {
                out.add(word.dropLast(3) + "y" to "过去式/过去分词")
            }
            if (word.endsWith("ed") && word.length > 4) {
                val stem = word.dropLast(2)
                out.add(stem to "过去式/过去分词")
                out.add(stem + "e" to "过去式/过去分词")
                if (stem.length >= 2 && stem.last() == stem[stem.length - 2]) {
                    out.add(stem.dropLast(1) to "过去式/过去分词")
                }
            }
            if (word.endsWith("ing") && word.length > 5) {
                val stem = word.dropLast(3)
                out.add(stem to "现在分词")
                out.add(stem + "e" to "现在分词")
                if (stem.length >= 2 && stem.last() == stem[stem.length - 2]) {
                    out.add(stem.dropLast(1) to "现在分词")
                }
                if (stem.endsWith("y")) {
                    out.add(stem.dropLast(1) + "ie" to "现在分词")
                }
            }
            if (word.endsWith("ier") && word.length > 4) {
                out.add(word.dropLast(3) + "y" to "比较级")
            }
            if (word.endsWith("er") && word.length > 4) {
                val stem = word.dropLast(2)
                out.add(stem to "比较级/派生")
                out.add(stem + "e" to "比较级/派生")
                if (stem.length >= 2 && stem.last() == stem[stem.length - 2]) {
                    out.add(stem.dropLast(1) to "比较级")
                }
            }
            if (word.endsWith("iest") && word.length > 5) {
                out.add(word.dropLast(4) + "y" to "最高级")
            }
            if (word.endsWith("est") && word.length > 5) {
                val stem = word.dropLast(3)
                out.add(stem to "最高级")
                out.add(stem + "e" to "最高级")
            }
            if (word.endsWith("ly") && word.length > 4) {
                val stem = word.dropLast(2)
                out.add(stem to "副词形式")
                if (stem.endsWith("i")) {
                    out.add(stem.dropLast(1) + "y" to "副词形式")
                }
            }
            return out.distinctBy { it.first }
        }
    }

    suspend fun ensureReady(): Boolean = withContext(Dispatchers.IO) {
        if (db?.isOpen == true) return@withContext true
        mutex.withLock {
            if (db?.isOpen == true) return@withLock true
            runCatching {
                val targetFile = context.getDatabasePath(DB_FILE_NAME)
                if (!targetFile.exists() || targetFile.length() < 1_000_000L) {
                    targetFile.parentFile?.mkdirs()
                    val tempFile = File(targetFile.parentFile, "$DB_FILE_NAME.tmp")
                    context.assets.open(ASSET_GZ).use { rawIn ->
                        GZIPInputStream(rawIn, 32768).use { gzIn ->
                            FileOutputStream(tempFile).use { out ->
                                gzIn.copyTo(out, 32768)
                            }
                        }
                    }
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)
                }
                val opened = SQLiteDatabase.openDatabase(
                    targetFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )
                db = opened
                opened.rawQuery("SELECT COUNT(*) FROM words", null).use { c ->
                    if (c.moveToFirst()) cachedWordCount = c.getInt(0)
                }
                true
            }.getOrDefault(false)
        }
    }

    fun getWordCount(): Int = cachedWordCount

    suspend fun lookup(rawQuery: String): TranslationResult? = withContext(Dispatchers.IO) {
        if (!ensureReady()) return@withContext null
        val database = db ?: return@withContext null

        val cleaned = cleanQuery(rawQuery)
        if (cleaned.isEmpty()) return@withContext null
        val lower = cleaned.lowercase()

        // 1. Direct exact match
        val exact = queryWordExact(database, lower)
        val lemmaInfo = queryLemma(database, lower)

        if (exact != null) {
            // If exact entry is just a pointer like "xxx的过去式" and we have lemma, enrich or keep lemma info
            val formNote = if (lemmaInfo != null && lemmaInfo.first != lower) {
                "${lemmaInfo.first} 的${lemmaInfo.second}"
            } else {
                ""
            }
            val lemmaWord = lemmaInfo?.first ?: exact.displayWord.lowercase()
            return@withContext exact.copy(
                query = cleaned,
                lemma = lemmaWord,
                formNote = formNote
            )
        }

        // 2. Lemma table lookup (handles irregular verbs, plurals, participles, etc.)
        if (lemmaInfo != null) {
            val baseResult = queryWordExact(database, lemmaInfo.first)
            if (baseResult != null) {
                return@withContext baseResult.copy(
                    query = cleaned,
                    displayWord = cleaned,
                    lemma = baseResult.displayWord,
                    formNote = "${baseResult.displayWord} 的${lemmaInfo.second}"
                )
            }
        }

        // 3. Rule-based morphological stemming fallback
        for ((candidate, formLabel) in generateMorphCandidates(lower)) {
            val hit = queryWordExact(database, candidate)
            if (hit != null) {
                return@withContext hit.copy(
                    query = cleaned,
                    displayWord = cleaned,
                    lemma = hit.displayWord,
                    formNote = "${hit.displayWord} 的$formLabel"
                )
            }
        }

        // 4. Strip-word normalized fallback (e.g., hyphenated variants)
        val sw = lower.replace(Regex("[^a-z0-9]"), "")
        if (sw.isNotEmpty() && sw != lower) {
            val swHit = queryBySw(database, sw)
            if (swHit != null) {
                return@withContext swHit.copy(query = cleaned)
            }
        }

        null
    }

    private fun queryWordExact(database: SQLiteDatabase, wordLower: String): TranslationResult? {
        database.rawQuery(
            "SELECT word, phonetic, definition, translation, tag, collins, oxford FROM words WHERE word_lower = ? LIMIT 1",
            arrayOf(wordLower)
        ).use { c ->
            if (!c.moveToFirst()) return null
            val word = c.getString(0)
            val phonetic = c.getString(1)
            val definition = normalizeNewlines(c.getString(2))
            val translation = normalizeNewlines(c.getString(3))
            val tagRaw = c.getString(4)
            val collins = c.getInt(5)
            val oxford = c.getInt(6) == 1

            val tags = tagRaw.split(Regex("\\s+"))
                .mapNotNull { TAG_DISPLAY_MAP[it.lowercase()] }
                .distinct()

            return TranslationResult(
                query = word,
                displayWord = word,
                lemma = word,
                phoneticUs = phonetic,
                chineseMeaning = translation,
                englishDef = definition,
                tags = tags,
                collins = collins,
                oxford = oxford,
                isPhrase = word.contains(' '),
                sourceLabel = "离线词典 (ECDICT)"
            )
        }
    }

    private fun queryBySw(database: SQLiteDatabase, sw: String): TranslationResult? {
        database.rawQuery(
            "SELECT word, phonetic, definition, translation, tag, collins, oxford FROM words WHERE sw = ? LIMIT 1",
            arrayOf(sw)
        ).use { c ->
            if (!c.moveToFirst()) return null
            val word = c.getString(0)
            return TranslationResult(
                query = word,
                displayWord = word,
                lemma = word,
                phoneticUs = c.getString(1),
                englishDef = normalizeNewlines(c.getString(2)),
                chineseMeaning = normalizeNewlines(c.getString(3)),
                tags = c.getString(4).split(Regex("\\s+")).mapNotNull { TAG_DISPLAY_MAP[it.lowercase()] },
                collins = c.getInt(5),
                oxford = c.getInt(6) == 1,
                isPhrase = word.contains(' '),
                sourceLabel = "离线词典 (ECDICT)"
            )
        }
    }

    private fun queryLemma(database: SQLiteDatabase, inflected: String): Pair<String, String>? {
        database.rawQuery(
            "SELECT base_word, form_type FROM lemmas WHERE inflected = ? LIMIT 1",
            arrayOf(inflected)
        ).use { c ->
            return if (c.moveToFirst()) {
                c.getString(0) to c.getString(1)
            } else {
                null
            }
        }
    }

    private fun normalizeNewlines(raw: String): String {
        return raw.replace("\\n", "\n").replace("\r\n", "\n").trim()
    }
}

