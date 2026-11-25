package com.lingoreader.app.parser

import com.lingoreader.app.data.model.Chapter
import com.lingoreader.app.data.model.ParsedBook
import org.mozilla.universalchardet.UniversalDetector
import java.io.File
import java.nio.charset.Charset

object TxtParser {

    private const val MAX_CHAPTER_CHARS = 24_000

    private val CHAPTER_HEADING_REGEX = Regex(
        """^\s*((Chapter|CHAPTER|Part|PART|Book|BOOK|Section|SECTION|Act|ACT)\s+([0-9IVXLCDMivxlcdm]+|One|Two|Three|Four|Five|Six|Seven|Eight|Nine|Ten).*|(Prologue|PROLOGUE|Epilogue|EPILOGUE|Preface|PREFACE|Introduction|INTRODUCTION)\b.*|第[零一二三四五六七八九十百千万两\d]+[章节回卷部集].*)$"""
    )

    fun parse(file: File, fallbackTitle: String): ParsedBook {
        val rawText = readTextWithAutoCharset(file)
        return parseText(rawText, fallbackTitle)
    }

    fun parseText(rawText: String, fallbackTitle: String): ParsedBook {
        val normalized = rawText
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val lines = normalized.split("\n")
        val rawSections = mutableListOf<Pair<String, MutableList<String>>>()

        var currentTitle = fallbackTitle
        var currentLines = mutableListOf<String>()
        var foundExplicitHeading = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && trimmed.length <= 120 && CHAPTER_HEADING_REGEX.matches(trimmed)) {
                if (currentLines.any { it.isNotBlank() }) {
                    rawSections.add(currentTitle to currentLines)
                }
                foundExplicitHeading = true
                currentTitle = trimmed
                currentLines = mutableListOf()
            } else {
                currentLines.add(line)
            }
        }
        if (currentLines.any { it.isNotBlank() } || rawSections.isEmpty()) {
            rawSections.add(currentTitle to currentLines)
        }

        // If first section before "Chapter 1" is just tiny title/author metadata (<= 4 non-empty lines), keep as intro or merge
        val chapters = mutableListOf<Chapter>()
        var chapterIdx = 0

        for ((secTitle, secLines) in rawSections) {
            val paragraphs = buildParagraphs(secLines)
            if (paragraphs.isEmpty()) continue

            val totalLen = paragraphs.sumOf { it.length }
            if (totalLen <= MAX_CHAPTER_CHARS) {
                val html = buildChapterHtml(
                    title = if (foundExplicitHeading || rawSections.size > 1) secTitle else null,
                    paragraphs = paragraphs
                )
                chapters.add(Chapter(index = chapterIdx++, title = secTitle, htmlContent = html))
            } else {
                // Split huge chapters cleanly at paragraph boundaries
                var chunk = mutableListOf<String>()
                var chunkLen = 0
                var partNo = 1
                for (para in paragraphs) {
                    if (chunkLen + para.length > MAX_CHAPTER_CHARS && chunk.isNotEmpty()) {
                        val partTitle = if (foundExplicitHeading) "$secTitle ($partNo)" else "Part $partNo"
                        chapters.add(
                            Chapter(
                                index = chapterIdx++,
                                title = partTitle,
                                htmlContent = buildChapterHtml(partTitle, chunk)
                            )
                        )
                        chunk = mutableListOf()
                        chunkLen = 0
                        partNo++
                    }
                    chunk.add(para)
                    chunkLen += para.length
                }
                if (chunk.isNotEmpty()) {
                    val partTitle = if (foundExplicitHeading) "$secTitle ($partNo)" else "Part $partNo"
                    chapters.add(
                        Chapter(
                            index = chapterIdx++,
                            title = partTitle,
                            htmlContent = buildChapterHtml(partTitle, chunk)
                        )
                    )
                }
            }
        }

        if (chapters.isEmpty()) {
            chapters.add(Chapter(0, fallbackTitle, "<p>(空文本文件)</p>"))
        }

        return ParsedBook(
            title = fallbackTitle,
            author = "",
            coverPath = null,
            chapters = chapters
        )
    }

    private fun buildParagraphs(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        val buf = StringBuilder()

        fun flush() {
            val s = buf.toString().trim()
            if (s.isNotEmpty()) result.add(s)
            buf.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flush()
            } else {
                if (buf.isNotEmpty()) {
                    // Join wrapped lines cleanly with a space if English, or directly if CJK
                    val lastChar = buf[buf.length - 1]
                    if (lastChar.code < 128 && trimmed[0].code < 128) {
                        buf.append(' ')
                    }
                }
                buf.append(trimmed)
            }
        }
        flush()
        return result
    }

    private fun buildChapterHtml(title: String?, paragraphs: List<String>): String {
        val sb = StringBuilder()
        if (!title.isNullOrBlank()) {
            sb.append("<h1>").append(HtmlEscape.escape(title)).append("</h1>\n")
        }
        for (p in paragraphs) {
            sb.append("<p>").append(HtmlEscape.escape(p)).append("</p>\n")
        }
        return sb.toString()
    }

    fun readTextWithAutoCharset(file: File): String {
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return ""

        // Check BOM first
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }

        val detector = UniversalDetector(null)
        val sampleLen = minOf(bytes.size, 65536)
        detector.handleData(bytes, 0, sampleLen)
        detector.dataEnd()
        val detected = detector.detectedCharset
        detector.reset()

        val charset = when {
            detected.isNullOrBlank() -> Charsets.UTF_8
            detected.equals("GB18030", ignoreCase = true) || detected.equals("GB2312", ignoreCase = true) || detected.equals("GBK", ignoreCase = true) ->
                runCatching { Charset.forName("GB18030") }.getOrDefault(Charsets.UTF_8)
            else -> runCatching { Charset.forName(detected) }.getOrDefault(Charsets.UTF_8)
        }
        return String(bytes, charset)
    }
}

object HtmlEscape {
    fun escape(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (ch in text) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
