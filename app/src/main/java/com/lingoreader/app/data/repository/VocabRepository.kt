package com.lingoreader.app.data.repository

import android.content.ContentValues
import android.content.Context
import com.lingoreader.app.data.db.AppDatabaseHelper
import com.lingoreader.app.data.model.DailyStudyStat
import com.lingoreader.app.data.model.ReviewRating
import com.lingoreader.app.data.model.TranslationResult
import com.lingoreader.app.data.model.VocabContext
import com.lingoreader.app.data.model.VocabWord
import com.lingoreader.app.data.sm2.Sm2Scheduler
import com.lingoreader.app.data.sm2.StudyStatsCalculator
import com.lingoreader.app.translation.OfflineDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BatchImportReport(
    val addedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int
)

class VocabRepository(
    private val context: Context,
    private val db: AppDatabaseHelper
) {
    private val _words = MutableStateFlow<List<VocabWord>>(emptyList())
    val words: StateFlow<List<VocabWord>> = _words.asStateFlow()

    private val _studyLogs = MutableStateFlow<List<DailyStudyStat>>(emptyList())
    val studyLogs: StateFlow<List<DailyStudyStat>> = _studyLogs.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _words.value = db.getAllVocab()
        _studyLogs.value = db.getAllStudyLogs()
    }

    suspend fun getWordById(id: Long): VocabWord? = withContext(Dispatchers.IO) {
        db.getVocabById(id)
    }

    suspend fun findSavedWord(word: String, lemma: String = ""): VocabWord? = withContext(Dispatchers.IO) {
        db.findVocabByWordOrLemma(word, lemma)
    }

    suspend fun saveFromTranslation(
        result: TranslationResult,
        contextSentence: String,
        bookId: String,
        bookTitle: String,
        chapterIndex: Int = -1,
        chapterTitle: String = "",
        incrementEncounter: Boolean = true
    ): VocabWord = withContext(Dispatchers.IO) {
        val targetWord = if (!result.isPhrase && result.lemma.isNotBlank()) {
            result.lemma
        } else {
            result.displayWord.ifBlank { result.query }
        }.trim()

        val existing = db.findVocabByWordOrLemma(targetWord, result.lemma)
        val now = System.currentTimeMillis()
        val cleanSentence = contextSentence.trim()

        val newContext = if (cleanSentence.isNotBlank()) {
            VocabContext(
                sentence = cleanSentence,
                bookId = bookId,
                bookTitle = bookTitle,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                addedAt = now
            )
        } else {
            null
        }

        val toSave = if (existing != null) {
            val mergedContexts = mergeContexts(existing.allContexts, newContext)
            existing.copy(
                phonetic = result.phoneticUs.ifBlank { result.bestPhonetic.ifBlank { existing.phonetic } },
                phoneticUk = result.phoneticUk.ifBlank { existing.phoneticUk },
                meaning = if (result.chineseMeaning.length > existing.meaning.length) {
                    result.chineseMeaning
                } else {
                    existing.meaning.ifBlank { result.chineseMeaning }
                },
                englishDef = if (result.englishDef.length > existing.englishDef.length) {
                    result.englishDef
                } else {
                    existing.englishDef.ifBlank { result.englishDef }
                },
                tags = existing.tags.ifBlank { result.tags.joinToString(" ") },
                contextSentence = cleanSentence.ifBlank { existing.contextSentence },
                bookId = existing.bookId.ifBlank { bookId },
                bookTitle = existing.bookTitle.ifBlank { bookTitle },
                chapterIndex = if (existing.chapterIndex >= 0) existing.chapterIndex else chapterIndex,
                contexts = mergedContexts,
                encounterCount = if (incrementEncounter) existing.encounterCount + 1 else existing.encounterCount,
                updatedAt = now
            )
        } else {
            db.incrementStudyLog(StudyStatsCalculator.todayKey(), addedDelta = 1)
            VocabWord(
                word = targetWord,
                wordLower = targetWord.lowercase(),
                lemma = result.lemma.ifBlank { targetWord }.lowercase(),
                phonetic = result.phoneticUs.ifBlank { result.bestPhonetic },
                phoneticUk = result.phoneticUk,
                meaning = result.chineseMeaning,
                englishDef = result.englishDef,
                tags = result.tags.joinToString(" "),
                contextSentence = cleanSentence,
                bookId = bookId,
                bookTitle = bookTitle,
                chapterIndex = chapterIndex,
                contexts = listOfNotNull(newContext),
                encounterCount = 1,
                nextReviewAt = now,
                createdAt = now,
                updatedAt = now
            )
        }
        val id = db.upsertVocab(toSave)
        refresh()
        toSave.copy(id = id)
    }

    /**
     * Records a passive re-encounter when the user looks up a word that is ALREADY in the vocabulary book.
     * Increments encounter count and appends any new reading context sentence automatically.
     */
    suspend fun recordEncounterIfSaved(
        word: String,
        lemma: String,
        contextSentence: String,
        bookId: String,
        bookTitle: String,
        chapterIndex: Int,
        chapterTitle: String
    ) = withContext(Dispatchers.IO) {
        val existing = db.findVocabByWordOrLemma(word, lemma) ?: return@withContext
        val now = System.currentTimeMillis()
        val cleanSentence = contextSentence.trim()
        val newCtx = if (cleanSentence.isNotBlank()) {
            VocabContext(
                sentence = cleanSentence,
                bookId = bookId,
                bookTitle = bookTitle,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                addedAt = now
            )
        } else {
            null
        }
        val mergedContexts = mergeContexts(existing.allContexts, newCtx)
        val updated = existing.copy(
            contexts = mergedContexts,
            contextSentence = existing.contextSentence.ifBlank { cleanSentence },
            encounterCount = existing.encounterCount + 1,
            updatedAt = now
        )
        db.updateVocab(updated)
        replaceWordInMemory(updated)
    }

    suspend fun addManualWord(
        word: String,
        phonetic: String,
        meaning: String,
        contextSentence: String,
        note: String,
        customGroup: String = ""
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val clean = word.trim()
        if (clean.isEmpty()) return@withContext
        val existing = db.findVocabByWordOrLemma(clean)
        val cleanSent = contextSentence.trim()
        val ctx = if (cleanSent.isNotBlank()) listOf(VocabContext(sentence = cleanSent, addedAt = now)) else emptyList()
        if (existing == null) {
            db.incrementStudyLog(StudyStatsCalculator.todayKey(), addedDelta = 1)
        }
        db.upsertVocab(
            VocabWord(
                word = clean,
                wordLower = clean.lowercase(),
                lemma = clean.lowercase(),
                phonetic = phonetic.trim(),
                meaning = meaning.trim(),
                contextSentence = cleanSent,
                contexts = ctx,
                customGroup = customGroup.trim(),
                note = note.trim(),
                nextReviewAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
        refresh()
    }

    /**
     * Batch-import word list (one per line, supports plain words or `word<tab>meaning` / `word,meaning`)
     * with automatic offline ECDICT enrichment!
     */
    suspend fun batchImportWordList(
        rawText: String,
        offlineDictionary: OfflineDictionary,
        groupName: String = ""
    ): BatchImportReport = withContext(Dispatchers.IO) {
        offlineDictionary.ensureReady()
        val now = System.currentTimeMillis()
        val lines = rawText.replace("\r\n", "\n").replace("\r", "\n").split("\n")

        var added = 0
        var updated = 0
        var skipped = 0

        for (rawLine in lines) {
            val trimmed = rawLine.trim().removePrefix("\uFEFF")
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            // Skip CSV header line if present
            if (trimmed.equals("word,phonetic,meaning", ignoreCase = true) ||
                trimmed.startsWith("Word,Phonetic,", ignoreCase = true)
            ) {
                continue
            }

            // Support tab-separated or comma-separated `word\tmeaning` or plain word/comma list
            val parts = when {
                trimmed.contains('\t') -> trimmed.split('\t', limit = 2)
                trimmed.contains('：') -> trimmed.split('：', limit = 2)
                else -> listOf(trimmed)
            }

            val wordCandidate = parts[0].trim().trim('"', '\'')
            val providedMeaning = parts.getOrNull(1)?.trim()?.trim('"').orEmpty()

            // If a single line has comma-separated English words with no Chinese (e.g. "subtle, ephemeral, ubiquitous")
            val entriesToProcess = if (parts.size == 1 && wordCandidate.contains(',') && !wordCandidate.any { it.code > 127 }) {
                wordCandidate.split(',').map { it.trim() to "" }
            } else {
                listOf(wordCandidate to providedMeaning)
            }

            for ((rawWord, manualMeaning) in entriesToProcess) {
                val cleaned = OfflineDictionary.cleanQuery(rawWord)
                if (cleaned.length < 2 || !cleaned.any { it.isLetter() }) {
                    skipped++
                    continue
                }
                val dictHit = offlineDictionary.lookup(cleaned)
                val canonicalWord = dictHit?.lemma?.ifBlank { dictHit.displayWord } ?: cleaned
                val existing = db.findVocabByWordOrLemma(canonicalWord, dictHit?.lemma.orEmpty())

                val finalMeaning = manualMeaning.ifBlank { dictHit?.chineseMeaning.orEmpty() }
                if (existing != null) {
                    if (groupName.isNotBlank() && existing.customGroup.isBlank()) {
                        db.updateVocab(existing.copy(customGroup = groupName.trim(), updatedAt = now))
                        updated++
                    } else {
                        skipped++
                    }
                    continue
                }

                db.upsertVocab(
                    VocabWord(
                        word = canonicalWord,
                        wordLower = canonicalWord.lowercase(),
                        lemma = (dictHit?.lemma ?: canonicalWord).lowercase(),
                        phonetic = dictHit?.bestPhonetic.orEmpty(),
                        phoneticUk = dictHit?.phoneticUk.orEmpty(),
                        meaning = finalMeaning,
                        englishDef = dictHit?.englishDef.orEmpty(),
                        tags = dictHit?.tags?.joinToString(" ").orEmpty(),
                        customGroup = groupName.trim(),
                        nextReviewAt = now,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                added++
            }
        }

        if (added > 0) {
            db.incrementStudyLog(StudyStatsCalculator.todayKey(), addedDelta = added)
        }
        refresh()
        BatchImportReport(addedCount = added, updatedCount = updated, skippedCount = skipped)
    }

    suspend fun updateWord(word: VocabWord) = withContext(Dispatchers.IO) {
        val updated = word.copy(updatedAt = System.currentTimeMillis())
        db.updateVocab(updated)
        replaceWordInMemory(updated)
    }

    suspend fun toggleStar(word: VocabWord) = withContext(Dispatchers.IO) {
        val updated = word.copy(starred = !word.starred, updatedAt = System.currentTimeMillis())
        db.updateVocab(updated)
        replaceWordInMemory(updated)
    }

    suspend fun markMastered(word: VocabWord) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val thirtyDays = 30L * 24L * 60L * 60L * 1000L
        val updated = word.copy(
            repetitions = maxOf(4, word.repetitions),
            intervalDays = maxOf(30, word.intervalDays),
            nextReviewAt = now + thirtyDays,
            updatedAt = now
        )
        db.updateVocab(updated)
        replaceWordInMemory(updated)
    }

    suspend fun resetProgress(word: VocabWord) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val updated = word.copy(
            repetitions = 0,
            intervalDays = 0,
            easeFactor = 2.5,
            nextReviewAt = now,
            updatedAt = now
        )
        db.updateVocab(updated)
        replaceWordInMemory(updated)
    }

    suspend fun removeWord(id: Long) = withContext(Dispatchers.IO) {
        db.deleteVocab(id)
        _words.value = _words.value.filterNot { it.id == id }
    }

    suspend fun removeWordByText(word: String) = withContext(Dispatchers.IO) {
        val wl = word.trim().lowercase()
        db.deleteVocabByWord(word)
        _words.value = _words.value.filterNot { it.wordLower == wl }
    }

    private fun replaceWordInMemory(updated: VocabWord) {
        _words.value = _words.value.map { if (it.id == updated.id) updated else it }
    }

    // ---- Batch Operations ----

    suspend fun batchMarkMastered(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("repetitions", 4)
            put("interval_days", 30)
            put("next_review_at", now + 30L * 24L * 60L * 60L * 1000L)
            put("updated_at", now)
        }
        db.batchUpdateVocab(ids, cv)
        refresh()
    }

    suspend fun batchResetProgress(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("repetitions", 0)
            put("interval_days", 0)
            put("ease_factor", 2.5)
            put("next_review_at", now)
            put("updated_at", now)
        }
        db.batchUpdateVocab(ids, cv)
        refresh()
    }

    suspend fun batchSetStarred(ids: Collection<Long>, starred: Boolean) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("starred", if (starred) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        db.batchUpdateVocab(ids, cv)
        refresh()
    }

    suspend fun batchAssignGroup(ids: Collection<Long>, groupName: String) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("custom_group", groupName.trim())
            put("updated_at", System.currentTimeMillis())
        }
        db.batchUpdateVocab(ids, cv)
        refresh()
    }

    suspend fun batchDelete(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        db.batchDeleteVocab(ids)
        refresh()
    }

    suspend fun recordReview(word: VocabWord, rating: ReviewRating): VocabWord = withContext(Dispatchers.IO) {
        val updated = Sm2Scheduler.schedule(word, rating)
        db.updateVocab(updated)
        db.incrementStudyLog(
            dateKey = StudyStatsCalculator.todayKey(),
            reviewedDelta = 1,
            rememberedDelta = if (rating != ReviewRating.AGAIN) 1 else 0
        )
        replaceWordInMemory(updated)
        _studyLogs.value = db.getAllStudyLogs()
        updated
    }

    fun getDueWords(all: List<VocabWord> = _words.value): List<VocabWord> {
        val now = System.currentTimeMillis()
        return all.filter { it.isDue(now) }.sortedBy { it.nextReviewAt }
    }

    /**
     * Graded highlight sets for Reader linkage:
     * - first: due or stubborn words (highlighted prominently so user reinforces weak words!)
     * - second: other actively learning words (soft subtle highlight)
     * Mastered words are intentionally omitted to keep pages clean as vocabulary grows.
     */
    fun getGradedHighlightWordSets(all: List<VocabWord> = _words.value): Pair<Set<String>, Set<String>> {
        val now = System.currentTimeMillis()
        val dueOrStubborn = HashSet<String>()
        val learning = HashSet<String>()

        fun addTokens(target: MutableSet<String>, w: VocabWord) {
            if (!w.wordLower.contains(' ') && w.wordLower.length >= 2) target.add(w.wordLower)
            if (w.lemma.isNotBlank() && !w.lemma.contains(' ') && w.lemma.length >= 2) target.add(w.lemma)
        }

        for (w in all) {
            if (w.isMastered) continue
            if (w.isDue(now) || w.isStubborn) {
                addTokens(dueOrStubborn, w)
            } else {
                addTokens(learning, w)
            }
        }
        learning.removeAll(dueOrStubborn)
        return dueOrStubborn to learning
    }

    suspend fun exportToCsv(): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(dir, "NaiDu_Vocab_$stamp.csv")
        val sb = StringBuilder("\uFEFF")
        sb.appendLine("Word,Phonetic,Meaning,ContextSentence,Book,Group,Note,Encounters,Lapses,Mastery,Reviews")
        for (w in _words.value) {
            sb.appendLine(
                listOf(
                    w.word,
                    w.phonetic,
                    w.meaning.replace("\n", " ; "),
                    w.contextSentence,
                    w.bookTitle,
                    w.customGroup,
                    w.note,
                    w.encounterCount.toString(),
                    w.lapseCount.toString(),
                    if (w.isMastered) "Mastered" else "Learning",
                    w.reviewCount.toString()
                ).joinToString(",") { csvEscape(it) }
            )
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        file
    }

    suspend fun exportToMarkdown(): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(dir, "NaiDu_Vocab_$stamp.md")
        val sb = StringBuilder()
        sb.appendLine("# 奶读 单词本导出")
        sb.appendLine()
        sb.appendLine("共收录 **${_words.value.size}** 个词条。")
        sb.appendLine()
        for (w in _words.value) {
            val ph = if (w.phonetic.isNotBlank()) " `/ ${w.phonetic} /`" else ""
            sb.appendLine("## ${w.word}$ph")
            if (w.meaning.isNotBlank()) {
                sb.appendLine("- **释义**：${w.meaning.replace("\n", "；")}")
            }
            if (w.customGroup.isNotBlank()) {
                sb.appendLine("- **分组**：${w.customGroup}")
            }
            for (ctx in w.allContexts) {
                val src = if (ctx.bookTitle.isNotBlank()) " ——《${ctx.bookTitle}》" else ""
                sb.appendLine("- **原句**：> ${ctx.sentence}$src")
            }
            if (w.note.isNotBlank()) {
                sb.appendLine("- **笔记**：${w.note}")
            }
            sb.appendLine()
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        file
    }

    private fun mergeContexts(existing: List<VocabContext>, added: VocabContext?): List<VocabContext> {
        if (added == null || added.sentence.isBlank()) return existing
        val normNew = added.sentence.trim().lowercase()
        val alreadyExists = existing.any { it.sentence.trim().lowercase() == normNew }
        return if (alreadyExists) {
            existing
        } else {
            (listOf(added) + existing).take(12)
        }
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
