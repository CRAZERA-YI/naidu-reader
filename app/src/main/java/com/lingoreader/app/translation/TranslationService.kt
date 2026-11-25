package com.lingoreader.app.translation

import com.google.gson.Gson
import com.lingoreader.app.data.db.AppDatabaseHelper
import com.lingoreader.app.data.model.AppSettings
import com.lingoreader.app.data.model.OnlineEngine
import com.lingoreader.app.data.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TranslationService(
    val offlineDictionary: OfflineDictionary,
    private val db: AppDatabaseHelper
) {
    private val gson = Gson()

    /**
     * Instant lookup (<10ms): checks local rich dictionary cache first, then falls back to offline ECDICT.
     */
    suspend fun lookupWordFast(rawWord: String): TranslationResult? = withContext(Dispatchers.IO) {
        val cleaned = OfflineDictionary.cleanQuery(rawWord)
        if (cleaned.isEmpty()) return@withContext null

        // 1. Check local SQLite rich cache first for instant full multi-sense dictionary
        val cachedDirect = loadCachedRich(cleaned)
        if (cachedDirect != null) return@withContext cachedDirect

        // 2. Query offline ECDICT
        val offline = offlineDictionary.lookup(cleaned)
        if (offline != null && offline.lemma.isNotBlank() && !offline.lemma.equals(cleaned, ignoreCase = true)) {
            val cachedLemma = loadCachedRich(offline.lemma)
            if (cachedLemma != null) {
                return@withContext cachedLemma.copy(
                    query = cleaned,
                    displayWord = cleaned,
                    lemma = offline.lemma,
                    formNote = offline.formNote.ifBlank { cachedLemma.formNote }
                )
            }
        }
        offline
    }

    /**
     * Enrich or look up a word online with full Youdao multi-sense definitions (ec + expand_ec + collins + phrs + syno + rel_word + examples)
     * and persist into local SQLite cache for instant offline reuse later.
     */
    suspend fun lookupWordOnline(rawWord: String, existingOffline: TranslationResult?): TranslationResult =
        withContext(Dispatchers.IO) {
            val cleaned = OfflineDictionary.cleanQuery(rawWord).ifBlank { rawWord.trim() }
            val lemma = existingOffline?.lemma?.takeIf { it.isNotBlank() } ?: ""

            // Fetch online rich entry for the tapped word
            var youdao = OnlineTranslator.lookupYoudao(cleaned)

            // If tapped word is an inflected form (e.g., "charged", "running", "went") and Youdao didn't return
            // full Collins/expanded senses for the inflected form, also fetch the base lemma so ALL meanings appear!
            val needsLemmaLookup = lemma.isNotBlank() &&
                !lemma.equals(cleaned, ignoreCase = true) &&
                (youdao == null || (youdao.collinsSenses.isEmpty() && youdao.senseGroups.isEmpty()))

            if (needsLemmaLookup) {
                val baseYoudao = OnlineTranslator.lookupYoudao(lemma)
                if (baseYoudao != null) {
                    youdao = if (youdao == null) {
                        baseYoudao
                    } else {
                        mergeYoudaoBase(inflected = youdao, base = baseYoudao)
                    }
                }
            }

            if (youdao != null) {
                val merged = mergeOfflineAndOnline(cleaned, existingOffline, youdao)
                saveCachedRich(cleaned, merged)
                if (lemma.isNotBlank() && !lemma.equals(cleaned, ignoreCase = true) && merged.collinsSenses.isNotEmpty()) {
                    saveCachedRich(lemma, merged.copy(query = lemma, displayWord = lemma, formNote = ""))
                }
                return@withContext merged
            }

            if (existingOffline != null) return@withContext existingOffline

            val fallback = OnlineTranslator.translateMyMemory(cleaned)
                ?: OnlineTranslator.translateGoogle(cleaned)
            fallback ?: TranslationResult(
                query = cleaned,
                displayWord = cleaned,
                error = "离线词库未收录该词，且当前网络不可用"
            )
        }

    private fun mergeYoudaoBase(inflected: TranslationResult, base: TranslationResult): TranslationResult {
        return base.copy(
            query = inflected.query,
            displayWord = inflected.displayWord,
            phoneticUk = inflected.phoneticUk.ifBlank { base.phoneticUk },
            phoneticUs = inflected.phoneticUs.ifBlank { base.phoneticUs },
            chineseMeaning = base.chineseMeaning.ifBlank { inflected.chineseMeaning },
            examples = if (base.examples.isNotEmpty()) base.examples else inflected.examples
        )
    }

    private fun mergeOfflineAndOnline(
        cleanedQuery: String,
        offline: TranslationResult?,
        online: TranslationResult
    ): TranslationResult {
        if (offline == null) {
            return online.copy(
                query = cleanedQuery,
                lemma = online.lemma.ifBlank { online.displayWord }
            )
        }
        // Prefer richer Youdao multi-POS Chinese meaning when available, keeping ECDICT as fallback
        val bestChinese = when {
            online.chineseMeaning.length >= offline.chineseMeaning.length && online.chineseMeaning.isNotBlank() ->
                online.chineseMeaning
            offline.chineseMeaning.isNotBlank() && online.chineseMeaning.isNotBlank() ->
                "${online.chineseMeaning}\n${offline.chineseMeaning}"
            else -> online.chineseMeaning.ifBlank { offline.chineseMeaning }
        }

        val bestEnglish = online.englishDef.ifBlank { offline.englishDef }

        return online.copy(
            query = cleanedQuery,
            displayWord = cleanedQuery,
            lemma = offline.lemma.ifBlank { online.displayWord },
            formNote = offline.formNote,
            phoneticUk = online.phoneticUk.ifBlank { offline.phoneticUk },
            phoneticUs = online.phoneticUs.ifBlank { offline.phoneticUs },
            chineseMeaning = bestChinese,
            englishDef = bestEnglish,
            tags = offline.tags.ifEmpty { online.tags },
            collins = maxOf(offline.collins, online.collins),
            oxford = offline.oxford || online.oxford,
            isRichLoaded = true,
            sourceLabel = buildString {
                append(online.sourceLabel.ifBlank { "有道词典" })
                if (offline.tags.isNotEmpty() || offline.oxford) {
                    append(" + ECDICT")
                }
            }
        )
    }

    private fun loadCachedRich(word: String): TranslationResult? {
        val json = db.getCachedDictJson(word) ?: return null
        return runCatching {
            gson.fromJson(json, TranslationResult::class.java)?.copy(isRichLoaded = true)
        }.getOrNull()
    }

    private fun saveCachedRich(word: String, result: TranslationResult) {
        if (!result.hasRichDetails && result.chineseMeaning.isBlank()) return
        runCatching {
            db.putCachedDictJson(word, gson.toJson(result))
        }
    }

    /**
     * Translate a selected phrase or full sentence (划词 / 划句翻译).
     */
    suspend fun translateSelection(
        rawText: String,
        contextSentence: String,
        settings: AppSettings
    ): TranslationResult = withContext(Dispatchers.IO) {
        val clean = rawText.trim().replace(Regex("\\s+"), " ")
        val words = clean.split(" ").filter { it.isNotBlank() }

        // Single word selected via drag handles -> use full rich lookup
        if (words.size == 1) {
            val fast = lookupWordFast(clean)
            if (fast != null && fast.isRichLoaded) return@withContext fast
            return@withContext lookupWordOnline(clean, fast)
        }

        val offlinePhrase = if (words.size <= 4) offlineDictionary.lookup(clean) else null

        if (settings.aiEnabled && settings.aiApiKey.isNotBlank()) {
            val aiRes = OnlineTranslator.translateWithAi(clean, contextSentence, settings)
            if (aiRes.isSuccess) {
                val aiText = aiRes.getOrThrow()
                return@withContext (offlinePhrase ?: TranslationResult(
                    query = clean,
                    displayWord = clean,
                    isPhrase = true
                )).copy(
                    chineseMeaning = offlinePhrase?.chineseMeaning?.ifBlank { aiText } ?: aiText,
                    aiExplanation = if (offlinePhrase != null) aiText else "",
                    isRichLoaded = true,
                    sourceLabel = "AI 翻译 (${settings.aiModel})"
                )
            }
        }

        val youdaoPhrase = if (words.size <= 4) OnlineTranslator.lookupYoudao(clean) else null

        val onlineSentence: TranslationResult? = when (settings.onlineEngine) {
            OnlineEngine.AUTO -> {
                if (words.size <= 3 && youdaoPhrase != null && youdaoPhrase.chineseMeaning.isNotBlank()) {
                    youdaoPhrase
                } else {
                    OnlineTranslator.translateMyMemory(clean)
                        ?: OnlineTranslator.translateGoogle(clean)
                        ?: youdaoPhrase
                }
            }
            OnlineEngine.MYMEMORY -> {
                OnlineTranslator.translateMyMemory(clean) ?: OnlineTranslator.translateGoogle(clean)
            }
            OnlineEngine.GOOGLE -> {
                OnlineTranslator.translateGoogle(clean) ?: OnlineTranslator.translateMyMemory(clean)
            }
            OnlineEngine.YOUDAO_ONLY -> {
                youdaoPhrase
            }
        }

        if (onlineSentence != null) {
            return@withContext onlineSentence.copy(
                examples = onlineSentence.examples.ifEmpty { youdaoPhrase?.examples.orEmpty() },
                phrases = onlineSentence.phrases.ifEmpty { youdaoPhrase?.phrases.orEmpty() },
                isPhrase = true,
                isRichLoaded = true
            )
        }

        if (offlinePhrase != null) {
            return@withContext offlinePhrase
        }

        val glossary = buildOfflineGlossary(words)
        if (glossary.isNotBlank()) {
            return@withContext TranslationResult(
                query = clean,
                displayWord = clean,
                chineseMeaning = glossary,
                isPhrase = true,
                sourceLabel = "离线逐词解析（当前无网络）"
            )
        }

        TranslationResult(
            query = clean,
            displayWord = clean,
            isPhrase = true,
            error = "在线翻译暂时不可用，请检查网络连接或在设置中切换翻译引擎"
        )
    }

    suspend fun requestAiExplanation(
        text: String,
        contextSentence: String,
        settings: AppSettings
    ): Result<String> {
        return OnlineTranslator.translateWithAi(text, contextSentence, settings)
    }

    private suspend fun buildOfflineGlossary(words: List<String>): String {
        val stopWords = setOf(
            "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "at", "for", "with",
            "by", "is", "are", "was", "were", "be", "been", "being", "it", "this", "that",
            "as", "from", "he", "she", "they", "we", "you", "i", "his", "her", "their", "my"
        )
        val lines = mutableListOf<String>()
        val seen = HashSet<String>()
        for (raw in words) {
            val cleaned = OfflineDictionary.cleanQuery(raw).lowercase()
            if (cleaned.length < 2 || cleaned in stopWords || !seen.add(cleaned)) continue
            val hit = offlineDictionary.lookup(cleaned)
            if (hit != null && hit.chineseMeaning.isNotBlank()) {
                val firstLine = hit.chineseMeaning.lineSequence().firstOrNull().orEmpty()
                lines.add("• ${hit.displayWord}: $firstLine")
            }
            if (lines.size >= 8) break
        }
        return lines.joinToString("\n")
    }
}
