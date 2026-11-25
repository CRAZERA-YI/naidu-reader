package com.lingoreader.app.translation

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lingoreader.app.data.model.AppSettings
import com.lingoreader.app.data.model.CollinsSense
import com.lingoreader.app.data.model.DetailedSense
import com.lingoreader.app.data.model.ExampleSentence
import com.lingoreader.app.data.model.PhraseItem
import com.lingoreader.app.data.model.RelatedWordEntry
import com.lingoreader.app.data.model.RelatedWordGroup
import com.lingoreader.app.data.model.SenseGroup
import com.lingoreader.app.data.model.SynonymGroup
import com.lingoreader.app.data.model.TranslationResult
import com.lingoreader.app.data.model.WordForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OnlineTranslator {

    private const val CONNECT_TIMEOUT_MS = 6000
    private const val READ_TIMEOUT_MS = 10000
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36"

    suspend fun lookupYoudao(query: String): TranslationResult? = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val body = httpGet("https://dict.youdao.com/jsonapi?q=$encoded")
            parseYoudaoJson(query.trim(), body)
        }.getOrNull()
    }

    /**
     * Pure JSON parser for Youdao rich dictionary API (ec + expand_ec + collins + wfs + phrs + syno + rel_word + web_trans + blng_sents).
     */
    fun parseYoudaoJson(query: String, body: String): TranslationResult? {
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null

        var ukPhone = ""
        var usPhone = ""
        var returnPhrase = ""
        val meanings = mutableListOf<String>()
        val wordForms = mutableListOf<WordForm>()
        val senseGroups = mutableListOf<SenseGroup>()
        val collinsSenses = mutableListOf<CollinsSense>()
        val phrases = mutableListOf<PhraseItem>()
        val synonyms = mutableListOf<SynonymGroup>()
        val relatedWords = mutableListOf<RelatedWordGroup>()
        val webTranslations = mutableListOf<String>()
        val examples = mutableListOf<ExampleSentence>()
        val englishDefs = mutableListOf<String>()

        // 1. Primary EC dictionary (phonetics, comprehensive POS meanings, word forms)
        root.obj("ec")?.arr("word")?.firstOrNull()?.asObjOrNull()?.let { wordObj ->
            returnPhrase = wordObj.str("return-phrase")
            ukPhone = wordObj.str("ukphone")
            usPhone = wordObj.str("usphone")

            wordObj.arr("trs")?.forEach { trElem ->
                val trObj = trElem.asObjOrNull() ?: return@forEach
                trObj.arr("tr")?.forEach { inner ->
                    inner.asObjOrNull()?.obj("l")?.arr("i")?.forEach { line ->
                        val text = cleanHtml(line.asStrOrEmpty())
                        if (text.isNotEmpty() && text !in meanings) {
                            meanings.add(text)
                        }
                    }
                }
            }

            wordObj.arr("wfs")?.forEach { wfElem ->
                val wf = wfElem.asObjOrNull()?.obj("wf") ?: return@forEach
                val name = wf.str("name")
                val value = wf.str("value")
                if (name.isNotEmpty() && value.isNotEmpty()) {
                    wordForms.add(WordForm(name, value))
                }
            }
        }

        // 2. Expanded EC (structured meanings grouped by POS, each with authoritative examples)
        root.obj("expand_ec")?.arr("word")?.forEach { wElem ->
            val wObj = wElem.asObjOrNull() ?: return@forEach
            val pos = wObj.str("pos").ifBlank { "释义" }
            val detailedList = mutableListOf<DetailedSense>()

            wObj.arr("transList")?.forEach { tElem ->
                val tObj = tElem.asObjOrNull() ?: return@forEach
                val trans = cleanHtml(tObj.str("trans"))
                if (trans.isEmpty()) return@forEach
                val contentObj = tObj.obj("content")
                val detailPos = contentObj?.str("detailPos").orEmpty()
                val senseExamples = mutableListOf<ExampleSentence>()
                contentObj?.arr("sents")?.take(2)?.forEach { sElem ->
                    val sObj = sElem.asObjOrNull() ?: return@forEach
                    val eng = cleanHtml(sObj.str("sentOrig"))
                    val chn = cleanHtml(sObj.str("sentTrans"))
                    val src = sObj.str("source")
                    if (eng.isNotEmpty() && chn.isNotEmpty()) {
                        senseExamples.add(ExampleSentence(eng, chn, src))
                    }
                }
                detailedList.add(DetailedSense(trans, detailPos, senseExamples))
            }

            if (detailedList.isNotEmpty()) {
                senseGroups.add(SenseGroup(pos, detailedList))
            }
        }

        // 3. Collins English-Chinese Bilingual Dictionary (ALL senses, full explanations + examples)
        var collinsIdx = 1
        root.obj("collins")?.arr("collins_entries")?.forEach { ceElem ->
            val ceObj = ceElem.asObjOrNull() ?: return@forEach
            ceObj.obj("entries")?.arr("entry")?.forEach { entryElem ->
                val tranEntries = entryElem.asObjOrNull()?.arr("tran_entry") ?: return@forEach
                for (teElem in tranEntries) {
                    val teObj = teElem.asObjOrNull() ?: continue
                    val explanation = cleanHtml(teObj.str("tran"))
                    if (explanation.isEmpty()) continue
                    val posObj = teObj.obj("pos_entry")
                    val pos = posObj?.str("pos").orEmpty()
                    val posTips = posObj?.str("pos_tips").orEmpty()

                    val cExamples = mutableListOf<ExampleSentence>()
                    teObj.obj("exam_sents")?.arr("sent")?.take(2)?.forEach { sentElem ->
                        val sObj = sentElem.asObjOrNull() ?: return@forEach
                        val eng = cleanHtml(sObj.str("eng_sent"))
                        val chn = cleanHtml(sObj.str("chn_sent"))
                        if (eng.isNotEmpty() && chn.isNotEmpty()) {
                            cExamples.add(ExampleSentence(eng, chn, "柯林斯"))
                        }
                    }
                    collinsSenses.add(
                        CollinsSense(
                            index = collinsIdx++,
                            pos = pos,
                            posTips = posTips,
                            explanation = explanation,
                            examples = cExamples
                        )
                    )
                }
            }
        }

        // 4. Common Phrases & Idioms (phrs)
        root.obj("phrs")?.arr("phrs")?.take(12)?.forEach { pElem ->
            val phrObj = pElem.asObjOrNull()?.obj("phr") ?: return@forEach
            val headword = cleanHtml(phrObj.obj("headword")?.obj("l")?.str("i").orEmpty())
            val tran = cleanHtml(
                phrObj.arr("trs")?.firstOrNull()?.asObjOrNull()
                    ?.obj("tr")?.obj("l")?.str("i").orEmpty()
            )
            if (headword.isNotEmpty() && tran.isNotEmpty()) {
                phrases.add(PhraseItem(headword, tran))
            }
        }

        // 5. Synonyms (syno)
        root.obj("syno")?.arr("synos")?.take(6)?.forEach { sElem ->
            val sObj = sElem.asObjOrNull()?.obj("syno") ?: return@forEach
            val pos = sObj.str("pos")
            val tran = cleanHtml(sObj.str("tran"))
            val ws = sObj.arr("ws")?.mapNotNull { w ->
                w.asObjOrNull()?.str("w")?.takeIf { it.isNotEmpty() }
            }.orEmpty()
            if (ws.isNotEmpty()) {
                synonyms.add(SynonymGroup(pos, tran, ws))
            }
        }

        // 6. Related Derivative Words (rel_word)
        root.obj("rel_word")?.arr("rels")?.forEach { rElem ->
            val relObj = rElem.asObjOrNull()?.obj("rel") ?: return@forEach
            val pos = relObj.str("pos")
            val entries = relObj.arr("words")?.mapNotNull { wElem ->
                val wObj = wElem.asObjOrNull() ?: return@mapNotNull null
                val w = wObj.str("word")
                val t = cleanHtml(wObj.str("tran"))
                if (w.isNotEmpty() && t.isNotEmpty()) RelatedWordEntry(w, t) else null
            }.orEmpty()
            if (entries.isNotEmpty()) {
                relatedWords.add(RelatedWordGroup(pos, entries))
            }
        }

        // 7. Web & domain-specific translations (web_trans)
        root.obj("web_trans")?.arr("web-translation")?.firstOrNull()?.asObjOrNull()
            ?.arr("trans")?.take(6)?.forEach { tElem ->
                val v = cleanHtml(tElem.asObjOrNull()?.str("value").orEmpty())
                if (v.isNotEmpty() && v !in webTranslations) {
                    webTranslations.add(v)
                }
            }

        // 8. English-English WordNet definitions (ee)
        root.obj("ee")?.obj("word")?.arr("trs")?.forEach { posElem ->
            val posObj = posElem.asObjOrNull() ?: return@forEach
            val pos = posObj.str("pos")
            posObj.arr("tr")?.take(4)?.forEach { trElem ->
                val def = cleanHtml(trElem.asObjOrNull()?.obj("l")?.str("i").orEmpty())
                if (def.isNotEmpty()) {
                    englishDefs.add(if (pos.isNotEmpty()) "$pos $def" else def)
                }
            }
        }

        // 9. Bilingual example sentences (blng_sents_part)
        root.obj("blng_sents_part")?.arr("sentence-pair")?.take(6)?.forEach { pairElem ->
            val obj = pairElem.asObjOrNull() ?: return@forEach
            val eng = cleanHtml(obj.str("sentence"))
            val chn = cleanHtml(obj.str("sentence-translation"))
            val src = obj.str("source")
            if (eng.isNotEmpty() && chn.isNotEmpty()) {
                examples.add(ExampleSentence(eng, chn, src))
            }
        }

        if (meanings.isEmpty() && webTranslations.isNotEmpty()) {
            meanings.add("网络/专业释义：${webTranslations.joinToString("；")}")
        }

        if (meanings.isEmpty() && senseGroups.isEmpty() && collinsSenses.isEmpty() && examples.isEmpty()) {
            return null
        }

        val sourceParts = mutableListOf("有道词典")
        if (collinsSenses.isNotEmpty()) sourceParts.add("柯林斯双解")

        return TranslationResult(
            query = query,
            displayWord = returnPhrase.ifBlank { query },
            phoneticUk = ukPhone,
            phoneticUs = usPhone,
            chineseMeaning = meanings.joinToString("\n"),
            englishDef = englishDefs.joinToString("\n"),
            senseGroups = senseGroups,
            collinsSenses = collinsSenses,
            wordForms = wordForms,
            phrases = phrases,
            synonyms = synonyms,
            relatedWords = relatedWords,
            webTranslations = webTranslations,
            examples = examples,
            isPhrase = query.trim().contains(' '),
            isRichLoaded = true,
            sourceLabel = sourceParts.joinToString(" · ")
        )
    }

    suspend fun translateMyMemory(text: String): TranslationResult? = withContext(Dispatchers.IO) {
        runCatching {
            val clean = text.trim().take(500)
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val body = httpGet("https://api.mymemory.translated.net/get?q=$encoded&langpair=en|zh-CN")
            val root = JsonParser.parseString(body).asJsonObject
            val translated = root.obj("responseData")?.str("translatedText").orEmpty()
            if (translated.isBlank() || (translated.equals(clean, ignoreCase = true) && clean.length > 4)) {
                return@runCatching null
            }
            if (translated.contains("MYMEMORY WARNING", ignoreCase = true)) {
                return@runCatching null
            }
            TranslationResult(
                query = clean,
                displayWord = clean,
                chineseMeaning = translated,
                isPhrase = clean.contains(' '),
                isRichLoaded = true,
                sourceLabel = "MyMemory 在线翻译"
            )
        }.getOrNull()
    }

    suspend fun translateGoogle(text: String): TranslationResult? = withContext(Dispatchers.IO) {
        runCatching {
            val clean = text.trim().take(1500)
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val body = httpGet(
                "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=zh-CN&dt=t&q=$encoded"
            )
            val root = JsonParser.parseString(body).asJsonArray
            val segments = root.get(0)?.asJsonArray ?: return@runCatching null
            val sb = StringBuilder()
            for (seg in segments) {
                val arr = seg.asJsonArray
                if (arr.size() > 0 && !arr.get(0).isJsonNull) {
                    sb.append(arr.get(0).asString)
                }
            }
            val translated = sb.toString().trim()
            if (translated.isBlank()) return@runCatching null
            TranslationResult(
                query = clean,
                displayWord = clean,
                chineseMeaning = translated,
                isPhrase = clean.contains(' '),
                isRichLoaded = true,
                sourceLabel = "Google 在线翻译"
            )
        }.getOrNull()
    }

    suspend fun translateWithAi(
        text: String,
        contextSentence: String,
        settings: AppSettings
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = settings.aiApiKey.trim()
            val baseUrl = settings.aiBaseUrl.trim().trimEnd('/')
            val model = settings.aiModel.trim().ifBlank { "deepseek-chat" }
            require(apiKey.isNotBlank()) { "请先在「设置」中填写 AI API Key" }
            require(baseUrl.isNotBlank()) { "请先在「设置」中填写 AI Base URL" }

            val endpoint = if (baseUrl.endsWith("/chat/completions")) {
                baseUrl
            } else {
                "$baseUrl/chat/completions"
            }

            val isSentence = text.trim().split(Regex("\\s+")).size >= 4
            val systemPrompt = if (isSentence) {
                "你是一位精炼的英语阅读助手。请用简体中文先给出流畅准确的中文译文，再简要点出1-2个核心词组或语法结构（总共控制在140字以内）。"
            } else {
                "你是一位专业的英语词典助手。请结合给定原句语境，用简体中文说明目标单词/短语在此处的准确义项，并补充它的其他常见义项及搭配（控制在140字以内）。"
            }
            val userPrompt = buildString {
                append("目标文本：").append(text.trim())
                if (contextSentence.isNotBlank() && contextSentence.trim() != text.trim()) {
                    append("\n所在原句：").append(contextSentence.trim())
                }
            }

            val payload = JsonObject().apply {
                addProperty("model", model)
                addProperty("temperature", 0.3)
                addProperty("max_tokens", 350)
                add("messages", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "system")
                        addProperty("content", systemPrompt)
                    })
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", userPrompt)
                    })
                })
            }

            val respText = httpPostJson(endpoint, payload.toString(), apiKey)
            val respJson = JsonParser.parseString(respText).asJsonObject
            val content = respJson.arr("choices")
                ?.firstOrNull()?.asObjOrNull()
                ?.obj("message")
                ?.str("content")
                .orEmpty()

            if (content.isBlank()) {
                throw IllegalStateException("AI 未返回有效译文")
            }
            content
        }
    }

    private fun cleanHtml(raw: String): String {
        return raw
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun JsonElement?.asObjOrNull(): JsonObject? =
        if (this != null && this.isJsonObject) this.asJsonObject else null

    private fun JsonElement?.asStrOrEmpty(): String =
        if (this != null && this.isJsonPrimitive) this.asString.trim() else ""

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(key: String): JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.str(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json, text/plain, */*")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${text.take(120)}")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun httpPostJson(urlStr: String, jsonBody: String, bearerToken: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Authorization", "Bearer $bearerToken")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBody)
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("AI 请求失败 (HTTP $code): ${text.take(160)}")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }
}
