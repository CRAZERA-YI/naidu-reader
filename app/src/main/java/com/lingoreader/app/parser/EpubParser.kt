package com.lingoreader.app.parser

import android.util.Xml
import com.lingoreader.app.data.model.Chapter
import com.lingoreader.app.data.model.ParsedBook
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.net.URLDecoder
import java.util.zip.ZipFile

object EpubParser {

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String
    )

    fun parse(epubFile: File, extractDir: File, fallbackTitle: String): ParsedBook {
        ensureExtracted(epubFile, extractDir)

        val opfRelativePath = findOpfPath(extractDir)
        if (opfRelativePath == null) {
            return parseFallbackHtmlFiles(extractDir, fallbackTitle)
        }

        val opfFile = File(extractDir, opfRelativePath)
        if (!opfFile.exists()) {
            return parseFallbackHtmlFiles(extractDir, fallbackTitle)
        }

        val opfDir = opfFile.parentFile ?: extractDir
        var title = fallbackTitle
        var author = ""
        var metaCoverId: String? = null
        val manifest = LinkedHashMap<String, ManifestItem>()
        val spineIdRefs = mutableListOf<String>()
        var spineTocId: String? = null

        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(opfFile.readText(Charsets.UTF_8)))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val localName = parser.name.substringAfterLast(':').lowercase()
                    when (localName) {
                        "title" -> {
                            val text = parser.nextText().trim()
                            if (text.isNotEmpty() && title == fallbackTitle) {
                                title = text
                            }
                        }
                        "creator" -> {
                            val text = parser.nextText().trim()
                            if (text.isNotEmpty() && author.isEmpty()) {
                                author = text
                            }
                        }
                        "meta" -> {
                            val name = parser.getAttributeValue(null, "name")
                            val content = parser.getAttributeValue(null, "content")
                            if (name.equals("cover", ignoreCase = true) && !content.isNullOrBlank()) {
                                metaCoverId = content
                            }
                        }
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id").orEmpty()
                            val href = parser.getAttributeValue(null, "href").orEmpty()
                            val mediaType = parser.getAttributeValue(null, "media-type").orEmpty()
                            val properties = parser.getAttributeValue(null, "properties").orEmpty()
                            if (id.isNotEmpty() && href.isNotEmpty()) {
                                manifest[id] = ManifestItem(id, decodeHref(href), mediaType, properties)
                            }
                        }
                        "spine" -> {
                            spineTocId = parser.getAttributeValue(null, "toc")
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            val linear = parser.getAttributeValue(null, "linear")
                            if (!idref.isNullOrBlank() && !linear.equals("no", ignoreCase = true)) {
                                spineIdRefs.add(idref)
                            }
                        }
                    }
                }
                event = parser.next()
            }
        }

        // Resolve cover image path
        val coverItem = manifest[metaCoverId]
            ?: manifest.values.firstOrNull { it.properties.contains("cover-image", ignoreCase = true) }
            ?: manifest.values.firstOrNull {
                it.mediaType.startsWith("image/", ignoreCase = true) &&
                    (it.id.contains("cover", ignoreCase = true) || it.href.contains("cover", ignoreCase = true))
            }
        val coverPath = coverItem?.let {
            val f = File(opfDir, it.href)
            if (f.exists() && f.isFile) f.absolutePath else null
        }

        // Parse Table of Contents titles map (canonical absolute path -> chapter title)
        val tocTitles = LinkedHashMap<String, String>()
        val navItem = manifest.values.firstOrNull { it.properties.contains("nav", ignoreCase = true) }
        if (navItem != null) {
            val navFile = File(opfDir, navItem.href)
            if (navFile.exists()) {
                parseEpub3Nav(navFile, tocTitles)
            }
        }
        if (tocTitles.isEmpty()) {
            val ncxItem = spineTocId?.let { manifest[it] }
                ?: manifest.values.firstOrNull { it.mediaType.contains("dtbncx", ignoreCase = true) || it.href.endsWith(".ncx", true) }
            if (ncxItem != null) {
                val ncxFile = File(opfDir, ncxItem.href)
                if (ncxFile.exists()) {
                    parseEpub2Ncx(ncxFile, tocTitles)
                }
            }
        }

        val chapters = mutableListOf<Chapter>()
        var chapterIdx = 0
        for (idref in spineIdRefs) {
            val item = manifest[idref] ?: continue
            val chFile = File(opfDir, item.href)
            if (!chFile.exists() || !chFile.isFile) continue
            val rawHtml = runCatching { chFile.readText(Charsets.UTF_8) }.getOrNull() ?: continue
            val bodyHtml = extractBodyHtml(rawHtml)
            if (bodyHtml.isBlank()) continue

            val canonicalKey = runCatching { chFile.canonicalPath }.getOrDefault(chFile.absolutePath)
            val chTitle = tocTitles[canonicalKey]
                ?: extractFirstHeadingOrTitle(rawHtml)
                ?: "Chapter ${chapterIdx + 1}"

            val baseUrl = chFile.parentFile?.toURI()?.toString()
            chapters.add(
                Chapter(
                    index = chapterIdx++,
                    title = chTitle,
                    htmlContent = bodyHtml,
                    baseUrl = baseUrl
                )
            )
        }

        if (chapters.isEmpty()) {
            return parseFallbackHtmlFiles(extractDir, title)
        }

        return ParsedBook(
            title = title,
            author = author,
            coverPath = coverPath,
            chapters = chapters
        )
    }

    private fun ensureExtracted(epubFile: File, extractDir: File) {
        val marker = File(extractDir, ".extracted_ok")
        if (marker.exists() && extractDir.listFiles()?.isNotEmpty() == true) {
            return
        }
        extractDir.mkdirs()
        val destCanonical = extractDir.canonicalPath + File.separator
        ZipFile(epubFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val outFile = File(extractDir, entry.name)
                val outCanonical = outFile.canonicalPath
                // Guard against Zip-Slip path traversal
                if (!outCanonical.startsWith(destCanonical)) continue
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
        marker.writeText("ok")
    }

    private fun findOpfPath(extractDir: File): String? {
        val container = File(extractDir, "META-INF/container.xml")
        if (container.exists()) {
            runCatching {
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(StringReader(container.readText(Charsets.UTF_8)))
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name.substringAfterLast(':').equals("rootfile", true)) {
                        val path = parser.getAttributeValue(null, "full-path")
                        if (!path.isNullOrBlank()) return decodeHref(path)
                    }
                    event = parser.next()
                }
            }
        }
        // Fallback: search for first .opf file
        return extractDir.walkTopDown()
            .firstOrNull { it.isFile && it.extension.equals("opf", ignoreCase = true) }
            ?.relativeTo(extractDir)?.path
    }

    private fun parseEpub2Ncx(ncxFile: File, out: MutableMap<String, String>) {
        val ncxDir = ncxFile.parentFile ?: return
        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(ncxFile.readText(Charsets.UTF_8)))
            var currentLabel: String? = null
            var inNavLabel = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.substringAfterLast(':').lowercase()
                        when (tag) {
                            "navlabel" -> inNavLabel = true
                            "text" -> if (inNavLabel) {
                                currentLabel = parser.nextText().trim()
                            }
                            "content" -> {
                                val src = parser.getAttributeValue(null, "src")
                                if (!src.isNullOrBlank() && !currentLabel.isNullOrBlank()) {
                                    val cleanHref = decodeHref(src.substringBefore('#'))
                                    val target = File(ncxDir, cleanHref)
                                    val key = runCatching { target.canonicalPath }.getOrDefault(target.absolutePath)
                                    out.putIfAbsent(key, currentLabel)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.substringAfterLast(':').equals("navlabel", true)) {
                            inNavLabel = false
                        }
                    }
                }
                event = parser.next()
            }
        }
    }

    private fun parseEpub3Nav(navFile: File, out: MutableMap<String, String>) {
        val navDir = navFile.parentFile ?: return
        val html = runCatching { navFile.readText(Charsets.UTF_8) }.getOrNull() ?: return
        val anchorRegex = Regex("""<a\b[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (m in anchorRegex.findAll(html)) {
            val href = decodeHref(m.groupValues[1].substringBefore('#'))
            val label = stripTags(m.groupValues[2]).trim()
            if (href.isNotBlank() && label.isNotBlank()) {
                val target = File(navDir, href)
                val key = runCatching { target.canonicalPath }.getOrDefault(target.absolutePath)
                out.putIfAbsent(key, label)
            }
        }
    }

    private fun parseFallbackHtmlFiles(extractDir: File, fallbackTitle: String): ParsedBook {
        val files = extractDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("xhtml", "html", "htm") }
            .sortedBy { it.path }
            .toList()

        val chapters = files.mapIndexedNotNull { idx, f ->
            val raw = runCatching { f.readText(Charsets.UTF_8) }.getOrNull() ?: return@mapIndexedNotNull null
            val body = extractBodyHtml(raw)
            if (body.isBlank()) return@mapIndexedNotNull null
            Chapter(
                index = idx,
                title = extractFirstHeadingOrTitle(raw) ?: "Section ${idx + 1}",
                htmlContent = body,
                baseUrl = f.parentFile?.toURI()?.toString()
            )
        }

        return ParsedBook(
            title = fallbackTitle,
            author = "",
            coverPath = null,
            chapters = chapters.ifEmpty { listOf(Chapter(0, fallbackTitle, "<p>(无法解析此 EPUB 内容)</p>")) }
        )
    }

    fun extractBodyHtml(rawHtml: String): String {
        val bodyMatch = Regex("""<body\b[^>]*>(.*)</body>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(rawHtml)
        var body = bodyMatch?.groupValues?.get(1) ?: rawHtml
        // Strip script and style tags to keep reader styles uniform & safe
        body = Regex("""<script\b[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(body, "")
        body = Regex("""<style\b[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(body, "")
        return body.trim()
    }

    private fun extractFirstHeadingOrTitle(rawHtml: String): String? {
        val h = Regex("""<h[1-3]\b[^>]*>(.*?)</h[1-3]>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(rawHtml)?.groupValues?.get(1)?.let { stripTags(it).trim() }
        if (!h.isNullOrBlank() && h.length <= 120) return h

        val t = Regex("""<title\b[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(rawHtml)?.groupValues?.get(1)?.let { stripTags(it).trim() }
        if (!t.isNullOrBlank() && t.length <= 120) return t
        return null
    }

    private fun stripTags(html: String): String {
        return html
            .replace(Regex("""<[^>]+>"""), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun decodeHref(href: String): String {
        return runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
    }
}
