package com.lingoreader.app.data.model

import androidx.compose.runtime.Immutable

enum class BookFormat(val label: String, val extension: String) {
    TXT("TXT", "txt"),
    MD("MD", "md"),
    EPUB("EPUB", "epub");

    companion object {
        fun fromFileName(name: String): BookFormat? {
            val lower = name.lowercase()
            return when {
                lower.endsWith(".epub") -> EPUB
                lower.endsWith(".md") || lower.endsWith(".markdown") -> MD
                lower.endsWith(".txt") || lower.endsWith(".text") -> TXT
                else -> null
            }
        }
    }
}

@Immutable
data class Book(
    val id: String,
    val title: String,
    val author: String = "",
    val format: BookFormat,
    val filePath: String,
    val coverPath: String? = null,
    val totalChapters: Int = 1,
    val currentChapter: Int = 0,
    val scrollProgress: Float = 0f,
    val fileSize: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = 0L
) {
    val overallProgress: Float
        get() {
            if (totalChapters <= 0) return 0f
            val base = currentChapter.coerceIn(0, totalChapters - 1).toFloat()
            return ((base + scrollProgress.coerceIn(0f, 1f)) / totalChapters.toFloat()).coerceIn(0f, 1f)
        }
}

@Immutable
data class Chapter(
    val index: Int,
    val title: String,
    val htmlContent: String,
    val baseUrl: String? = null
)

@Immutable
data class ParsedBook(
    val title: String,
    val author: String,
    val coverPath: String? = null,
    val chapters: List<Chapter>
)

@Immutable
data class VocabContext(
    val sentence: String,
    val bookId: String = "",
    val bookTitle: String = "",
    val chapterIndex: Int = -1,
    val chapterTitle: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@Immutable
data class VocabWord(
    val id: Long = 0L,
    val word: String,
    val wordLower: String = word.trim().lowercase(),
    val lemma: String = "",
    val phonetic: String = "",
    val phoneticUk: String = "",
    val meaning: String = "",
    val englishDef: String = "",
    val tags: String = "",
    val contextSentence: String = "",
    val bookId: String = "",
    val bookTitle: String = "",
    val chapterIndex: Int = -1,
    val contexts: List<VocabContext> = emptyList(),
    val customGroup: String = "",
    val note: String = "",
    val starred: Boolean = false,
    val encounterCount: Int = 1,
    val lapseCount: Int = 0,
    val repetitions: Int = 0,
    val intervalDays: Int = 0,
    val easeFactor: Double = 2.5,
    val nextReviewAt: Long = System.currentTimeMillis(),
    val reviewCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isMastered: Boolean
        get() = repetitions >= 3 && intervalDays >= 14

    val isStubborn: Boolean
        get() = lapseCount >= 2 || (encounterCount >= 3 && !isMastered)

    val masteryLevel: Int
        get() = when {
            repetitions == 0 -> 0
            repetitions == 1 -> 1
            intervalDays < 14 -> 2
            else -> 3
        }

    val allContexts: List<VocabContext>
        get() = if (contexts.isNotEmpty()) {
            contexts
        } else if (contextSentence.isNotBlank()) {
            listOf(
                VocabContext(
                    sentence = contextSentence,
                    bookId = bookId,
                    bookTitle = bookTitle,
                    chapterIndex = chapterIndex,
                    addedAt = createdAt
                )
            )
        } else {
            emptyList()
        }

    fun isDue(now: Long = System.currentTimeMillis()): Boolean = nextReviewAt <= now
}

@Immutable
data class DailyStudyStat(
    val dateKey: String,
    val reviewedCount: Int = 0,
    val rememberedCount: Int = 0,
    val addedCount: Int = 0
)

enum class VocabSortMode(val label: String) {
    RECENT("最近添加"),
    DUE_FIRST("复习紧迫"),
    STUBBORN("顽固难词"),
    ENCOUNTER("遇词频次"),
    ALPHA("字母 A-Z"),
    MASTERY("熟练度")
}

enum class VocabMaskMode(val label: String) {
    NONE("正常显示"),
    HIDE_MEANING("遮中文"),
    HIDE_WORD("遮英文")
}

enum class ReviewMode(val id: String, val label: String, val desc: String) {
    FLASHCARD("flashcard", "记忆卡片", "看词想义 · SM-2 遗忘曲线"),
    QUIZ("quiz", "释义四选一", "快速词义辨析测验"),
    SPELLING("spelling", "拼写默写", "听音看义拼写 · 逐字纠错"),
    LISTENING("listening", "听音辨义", "盲听美/英真人发音选义"),
    CLOZE("cloze", "原句填空", "还原阅读原句语境完形");

    companion object {
        fun fromId(id: String?): ReviewMode = entries.find { it.id == id } ?: FLASHCARD
    }
}

enum class ReviewScope(val id: String, val label: String) {
    DUE("due", "今日待复习"),
    STUBBORN("stubborn", "顽固难词"),
    STARRED("starred", "重点星标"),
    BOOK("book", "指定书籍"),
    ALL("all", "全部词条");

    companion object {
        fun fromId(id: String?): ReviewScope = entries.find { it.id == id } ?: DUE
    }
}

enum class ReviewRating(val label: String, val quality: Int) {
    AGAIN("重来", 1),
    HARD("困难", 3),
    GOOD("记得", 4),
    EASY("简单", 5)
}

@Immutable
data class ExampleSentence(
    val english: String,
    val chinese: String,
    val source: String = ""
)

@Immutable
data class DetailedSense(
    val meaning: String,
    val detailPos: String = "",
    val examples: List<ExampleSentence> = emptyList()
)

@Immutable
data class SenseGroup(
    val pos: String,
    val senses: List<DetailedSense>
)

@Immutable
data class CollinsSense(
    val index: Int,
    val pos: String,
    val posTips: String = "",
    val explanation: String,
    val examples: List<ExampleSentence> = emptyList()
)

@Immutable
data class WordForm(
    val label: String,
    val value: String
)

@Immutable
data class PhraseItem(
    val phrase: String,
    val meaning: String
)

@Immutable
data class SynonymGroup(
    val pos: String,
    val meaning: String,
    val words: List<String>
)

@Immutable
data class RelatedWordEntry(
    val word: String,
    val meaning: String
)

@Immutable
data class RelatedWordGroup(
    val pos: String,
    val words: List<RelatedWordEntry>
)

@Immutable
data class TranslationResult(
    val query: String,
    val displayWord: String = query,
    val lemma: String = "",
    val formNote: String = "",
    val phoneticUk: String = "",
    val phoneticUs: String = "",
    val chineseMeaning: String = "",
    val englishDef: String = "",
    val tags: List<String> = emptyList(),
    val collins: Int = 0,
    val oxford: Boolean = false,
    val senseGroups: List<SenseGroup> = emptyList(),
    val collinsSenses: List<CollinsSense> = emptyList(),
    val wordForms: List<WordForm> = emptyList(),
    val phrases: List<PhraseItem> = emptyList(),
    val synonyms: List<SynonymGroup> = emptyList(),
    val relatedWords: List<RelatedWordGroup> = emptyList(),
    val webTranslations: List<String> = emptyList(),
    val examples: List<ExampleSentence> = emptyList(),
    val isPhrase: Boolean = false,
    val isRichLoaded: Boolean = false,
    val sourceLabel: String = "",
    val aiExplanation: String = "",
    val error: String? = null
) {
    val bestPhonetic: String
        get() = phoneticUs.ifBlank { phoneticUk }

    val hasRichDetails: Boolean
        get() = senseGroups.isNotEmpty() || collinsSenses.isNotEmpty() || phrases.isNotEmpty() || examples.isNotEmpty()
}

enum class ReaderTheme(
    val id: String,
    val label: String,
    val bgHex: String,
    val textHex: String,
    val secondaryHex: String,
    val accentHex: String,
    val quoteBgHex: String,
    val isDark: Boolean
) {
    SEPIA("sepia", "羊皮纸", "#F7F1E3", "#2B251D", "#7A6F60", "#2E5E4E", "#EFE6D5", false),
    LIGHT("light", "纯净白", "#FFFFFF", "#1E1E1E", "#6E6E6E", "#2E5E4E", "#F4F5F7", false),
    SAGE("sage", "护眼绿", "#E9F1E7", "#1F2E22", "#5A6E5E", "#24543F", "#DDE9DA", false),
    NIGHT("night", "柔和夜", "#15181C", "#D4D8DF", "#8C94A0", "#6BB39B", "#1F242B", true);

    companion object {
        fun fromId(id: String?): ReaderTheme = entries.find { it.id == id } ?: SEPIA
    }
}

enum class ReaderFont(val id: String, val label: String, val cssFamily: String) {
    SERIF("serif", "衬线体", "Georgia, 'Times New Roman', serif"),
    SANS("sans", "无衬线", "-apple-system, 'Roboto', 'Noto Sans', sans-serif"),
    MONO("mono", "等宽体", "'JetBrains Mono', 'Roboto Mono', monospace");

    companion object {
        fun fromId(id: String?): ReaderFont = entries.find { it.id == id } ?: SERIF
    }
}

enum class PageTurnMode(val id: String, val label: String) {
    COVER("cover", "覆盖翻页"),
    SIMULATION("simulation", "仿真翻页"),
    SLIDE("slide", "平滑平移"),
    FADE("fade", "淡入淡出"),
    NONE("none", "无动画");

    companion object {
        fun fromId(id: String?): PageTurnMode = entries.find { it.id == id } ?: COVER
    }
}

enum class OnlineEngine(val id: String, val label: String, val desc: String) {
    AUTO("auto", "智能容错 (推荐)", "有道全义项词典 + MyMemory / Google 自动切换"),
    MYMEMORY("mymemory", "MyMemory 翻译", "国内直连稳定，适合短语和长句"),
    GOOGLE("google", "Google 翻译", "句意自然流畅（需网络可达）"),
    YOUDAO_ONLY("youdao", "仅离线词典 + 有道", "单词短语极速响应");

    companion object {
        fun fromId(id: String?): OnlineEngine = entries.find { it.id == id } ?: AUTO
    }
}

@Immutable
data class AppSettings(
    val readerTheme: ReaderTheme = ReaderTheme.SEPIA,
    val fontSizeSp: Int = 19,
    val lineHeight: Float = 1.72f,
    val fontFamily: ReaderFont = ReaderFont.SERIF,
    val horizontalMarginDp: Int = 22,
    val pageTurnMode: PageTurnMode = PageTurnMode.COVER,
    val volumeKeyPaging: Boolean = false,
    val autoTurnIntervalSec: Int = 8,
    val dailyReviewGoal: Int = 20,
    val tapToTranslate: Boolean = true,
    val highlightVocab: Boolean = true,
    val autoPronounceOnTap: Boolean = false,
    val preferHumanVoice: Boolean = true,
    val ttsAccent: String = "US",
    val ttsSpeed: Float = 0.95f,
    val onlineEngine: OnlineEngine = OnlineEngine.AUTO,
    val aiEnabled: Boolean = false,
    val aiBaseUrl: String = "https://api.deepseek.com/v1",
    val aiApiKey: String = "",
    val aiModel: String = "deepseek-chat"
)
