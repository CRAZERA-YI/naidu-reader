package com.lingoreader.app.parser

import com.lingoreader.app.data.model.Chapter
import com.lingoreader.app.data.model.ParsedBook
import java.io.File

object MarkdownParser {

    private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.+?)\s*#*$""")
    private val HR_REGEX = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private val UL_REGEX = Regex("""^\s*[-*+]\s+(.+)$""")
    private val OL_REGEX = Regex("""^\s*\d+[.)]\s+(.+)$""")
    private val TABLE_SEP_REGEX = Regex("""^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$""")

    fun parse(file: File, fallbackTitle: String): ParsedBook {
        val raw = TxtParser.readTextWithAutoCharset(file)
        return parseMarkdown(raw, fallbackTitle, file.parentFile?.toURI()?.toString())
    }

    fun parseMarkdown(rawText: String, fallbackTitle: String, baseUrl: String? = null): ParsedBook {
        val normalized = rawText
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val lines = normalized.split("\n")

        // Detect first H1 as book title if present
        var bookTitle = fallbackTitle
        for (line in lines) {
            val m = HEADING_REGEX.matchEntire(line.trim())
            if (m != null && m.groupValues[1].length == 1) {
                bookTitle = stripInlineMd(m.groupValues[2])
                break
            }
        }

        // Split into chapters at H1/H2 boundaries when the document has multiple major headings and decent length
        val splitLevel = detectSplitHeadingLevel(lines)
        val sections = mutableListOf<Pair<String, MutableList<String>>>()
        var currentTitle = bookTitle
        var currentLines = mutableListOf<String>()
        var inCodeFence = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inCodeFence = !inCodeFence
            }
            if (!inCodeFence && splitLevel > 0) {
                val m = HEADING_REGEX.matchEntire(trimmed)
                if (m != null && m.groupValues[1].length == splitLevel) {
                    if (currentLines.any { it.isNotBlank() }) {
                        sections.add(currentTitle to currentLines)
                    }
                    currentTitle = stripInlineMd(m.groupValues[2])
                    currentLines = mutableListOf(line)
                    continue
                }
            }
            currentLines.add(line)
        }
        if (currentLines.any { it.isNotBlank() } || sections.isEmpty()) {
            sections.add(currentTitle to currentLines)
        }

        val chapters = sections.mapIndexed { idx, (secTitle, secLines) ->
            Chapter(
                index = idx,
                title = secTitle,
                htmlContent = markdownLinesToHtml(secLines),
                baseUrl = baseUrl
            )
        }

        return ParsedBook(
            title = bookTitle,
            author = "",
            coverPath = null,
            chapters = chapters.ifEmpty { listOf(Chapter(0, bookTitle, "<p>(空 Markdown 文档)</p>", baseUrl)) }
        )
    }

    private fun detectSplitHeadingLevel(lines: List<String>): Int {
        if (lines.sumOf { it.length } < 1800) return 0
        var h1Count = 0
        var h2Count = 0
        var inCode = false
        for (line in lines) {
            val t = line.trim()
            if (t.startsWith("```") || t.startsWith("~~~")) {
                inCode = !inCode
                continue
            }
            if (inCode) continue
            val m = HEADING_REGEX.matchEntire(t)
            if (m != null) {
                when (m.groupValues[1].length) {
                    1 -> h1Count++
                    2 -> h2Count++
                }
            }
        }
        return when {
            h1Count >= 2 -> 1
            h2Count >= 2 -> 2
            else -> 0
        }
    }

    fun markdownLinesToHtml(lines: List<String>): String {
        val sb = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Blank line
            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // Fenced code block
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                val fence = trimmed.substring(0, 3)
                val lang = trimmed.substring(3).trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++ // consume closing fence
                val langAttr = if (lang.isNotEmpty()) " class=\"lang-${HtmlEscape.escape(lang)}\"" else ""
                sb.append("<pre><code").append(langAttr).append(">")
                    .append(HtmlEscape.escape(codeLines.joinToString("\n")))
                    .append("</code></pre>\n")
                continue
            }

            // Heading
            val headingMatch = HEADING_REGEX.matchEntire(trimmed)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length.coerceIn(1, 6)
                val text = renderInline(headingMatch.groupValues[2])
                sb.append("<h$level>").append(text).append("</h$level>\n")
                i++
                continue
            }

            // Horizontal rule
            if (HR_REGEX.matches(trimmed)) {
                sb.append("<hr/>\n")
                i++
                continue
            }

            // Blockquote
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
                    i++
                }
                sb.append("<blockquote>").append(markdownLinesToHtml(quoteLines)).append("</blockquote>\n")
                continue
            }

            // Table (header + separator)
            if (trimmed.contains('|') && i + 1 < lines.size && TABLE_SEP_REGEX.matches(lines[i + 1].trim())) {
                val headerCells = splitTableRow(trimmed)
                i += 2
                sb.append("<div class=\"table-wrap\"><table><thead><tr>")
                for (cell in headerCells) {
                    sb.append("<th>").append(renderInline(cell)).append("</th>")
                }
                sb.append("</tr></thead><tbody>")
                while (i < lines.size && lines[i].trim().contains('|') && lines[i].trim().isNotEmpty()) {
                    val rowCells = splitTableRow(lines[i].trim())
                    sb.append("<tr>")
                    for (cell in rowCells) {
                        sb.append("<td>").append(renderInline(cell)).append("</td>")
                    }
                    sb.append("</tr>")
                    i++
                }
                sb.append("</tbody></table></div>\n")
                continue
            }

            // Unordered list
            if (UL_REGEX.matches(line)) {
                sb.append("<ul>\n")
                while (i < lines.size) {
                    val m = UL_REGEX.matchEntire(lines[i]) ?: break
                    sb.append("<li>").append(renderInline(m.groupValues[1])).append("</li>\n")
                    i++
                }
                sb.append("</ul>\n")
                continue
            }

            // Ordered list
            if (OL_REGEX.matches(line)) {
                sb.append("<ol>\n")
                while (i < lines.size) {
                    val m = OL_REGEX.matchEntire(lines[i]) ?: break
                    sb.append("<li>").append(renderInline(m.groupValues[1])).append("</li>\n")
                    i++
                }
                sb.append("</ol>\n")
                continue
            }

            // Paragraph
            val paraLines = mutableListOf<String>()
            while (i < lines.size) {
                val t = lines[i].trim()
                if (t.isEmpty() ||
                    t.startsWith("```") || t.startsWith("~~~") ||
                    t.startsWith(">") ||
                    HEADING_REGEX.matches(t) ||
                    HR_REGEX.matches(t) ||
                    UL_REGEX.matches(lines[i]) ||
                    OL_REGEX.matches(lines[i])
                ) {
                    break
                }
                paraLines.add(t)
                i++
            }
            if (paraLines.isNotEmpty()) {
                sb.append("<p>").append(renderInline(paraLines.joinToString(" "))).append("</p>\n")
            } else {
                i++
            }
        }
        return sb.toString()
    }

    private fun splitTableRow(row: String): List<String> {
        val stripped = row.trim().removePrefix("|").removeSuffix("|")
        return stripped.split("|").map { it.trim() }
    }

    fun renderInline(raw: String): String {
        // Protect inline code spans first
        val codeTokens = mutableListOf<String>()
        var text = Regex("""`([^`]+)`""").replace(raw) { m ->
            val idx = codeTokens.size
            codeTokens.add("<code>${HtmlEscape.escape(m.groupValues[1])}</code>")
            "\u0000CODE${idx}\u0000"
        }

        // Escape remaining HTML characters safely
        text = HtmlEscape.escape(text)

        // Images: ![alt](url)
        text = Regex("""!\[([^\]]*)\]\(([^)]+)\)""").replace(text) { m ->
            val alt = m.groupValues[1]
            val src = m.groupValues[2].trim()
            "<img src=\"$src\" alt=\"$alt\" />"
        }

        // Links: [label](url)
        text = Regex("""\[([^\]]+)\]\(([^)]+)\)""").replace(text) { m ->
            val label = m.groupValues[1]
            val href = m.groupValues[2].trim()
            "<a href=\"$href\">$label</a>"
        }

        // Bold + Italic
        text = Regex("""\*\*\*(.+?)\*\*\*""").replace(text, "<strong><em>$1</em></strong>")
        // Bold
        text = Regex("""\*\*(.+?)\*\*""").replace(text, "<strong>$1</strong>")
        text = Regex("""__(.+?)__""").replace(text, "<strong>$1</strong>")
        // Strikethrough
        text = Regex("""~~(.+?)~~""").replace(text, "<del>$1</del>")
        // Italic (avoid breaking snake_case words)
        text = Regex("""(?<!\*)\*(?!\s)(.+?)(?<!\s)\*(?!\*)""").replace(text, "<em>$1</em>")
        text = Regex("""(?<![A-Za-z0-9])_(?!\s)(.+?)(?<!\s)_(?![A-Za-z0-9])""").replace(text, "<em>$1</em>")

        // Restore inline code spans
        for (idx in codeTokens.indices) {
            text = text.replace("\u0000CODE${idx}\u0000", codeTokens[idx])
        }
        return text
    }

    private fun stripInlineMd(s: String): String {
        return s.replace(Regex("""[*_`~]"""), "").trim()
    }
}
