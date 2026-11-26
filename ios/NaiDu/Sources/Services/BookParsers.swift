import CoreFoundation
import Foundation

enum BookParserError: LocalizedError {
    case unreadableFile
    case invalidEpubArchive
    case emptyContent

    var errorDescription: String? {
        switch self {
        case .unreadableFile: return "无法读取书籍文件或编码不支持"
        case .invalidEpubArchive: return "EPUB 文件结构损坏或不受支持"
        case .emptyContent: return "未能从书籍中解析出有效正文"
        }
    }
}

enum BookParsers {
    static func parseBook(at fileURL: URL, extractedAssetsDir: URL) throws -> ParsedBook {
        let ext = fileURL.pathExtension.lowercased()
        let data = try Data(contentsOf: fileURL)
        let defaultTitle = fileURL.deletingPathExtension().lastPathComponent
            .replacingOccurrences(of: "_", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)

        switch ext {
        case "epub":
            return try parseEpub(data: data, fallbackTitle: defaultTitle, assetsDir: extractedAssetsDir)
        case "md", "markdown":
            let text = decodeText(data)
            return parseMarkdown(text: text, fallbackTitle: defaultTitle)
        default:
            let text = decodeText(data)
            return parseTxt(text: text, fallbackTitle: defaultTitle)
        }
    }

    // MARK: - Smart Encoding Detection (UTF-8 / UTF-16 / GB18030 / Latin1)

    static func decodeText(_ data: Data) -> String {
        if data.starts(with: [0xEF, 0xBB, 0xBF]), let s = String(data: data.dropFirst(3), encoding: .utf8) {
            return s
        }
        if data.starts(with: [0xFF, 0xFE]), let s = String(data: data, encoding: .utf16LittleEndian) {
            return s
        }
        if data.starts(with: [0xFE, 0xFF]), let s = String(data: data, encoding: .utf16BigEndian) {
            return s
        }
        if let utf8 = String(data: data, encoding: .utf8) {
            return utf8
        }
        let gbEnc = CFStringConvertEncodingToNSStringEncoding(CFStringEncodings.GB_18030_2000.rawValue)
        if let gb = String(data: data, encoding: String.Encoding(rawValue: gbEnc)) {
            return gb
        }
        return String(data: data, encoding: .isoLatin1) ?? ""
    }

    // MARK: - TXT Parser (Chapter Regex + Fallback Chunking)

    static func parseTxt(text: String, fallbackTitle: String) -> ParsedBook {
        let normalized = text.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n")
        let lines = normalized.components(separatedBy: "\n")
        let chapterRegex = try? NSRegularExpression(
            pattern: #"^\s*(?:Chapter\s+\d+|CHAPTER\s+[IVXLCDM\d]+|Part\s+\d+|Book\s+\d+|第[零一二三四五六七八九十百千万\d]+[章节回卷部篇]).{0,60}$"#,
            options: [.caseInsensitive]
        )

        var rawSections: [(String, [String])] = []
        var currentTitle = ""
        var currentBuffer: [String] = []

        for line in lines {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            let range = NSRange(location: 0, length: (trimmed as NSString).length)
            let isHeading = trimmed.count >= 2 && trimmed.count <= 72 && (chapterRegex?.firstMatch(in: trimmed, range: range) != nil)

            if isHeading {
                if !currentBuffer.joined().trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    rawSections.append((currentTitle.isEmpty ? "序章 / 引言" : currentTitle, currentBuffer))
                }
                currentTitle = trimmed
                currentBuffer = []
            } else {
                currentBuffer.append(line)
            }
        }

        if !currentBuffer.joined().trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            rawSections.append((currentTitle.isEmpty ? fallbackTitle : currentTitle, currentBuffer))
        }

        // Split overly huge single-section TXT books into comfortable chapters (~3500 words)
        var chapters: [Chapter] = []
        var idx = 0
        for (secTitle, secLines) in rawSections {
            let paragraphs = splitParagraphs(secLines)
            if paragraphs.count > 120 && rawSections.count == 1 {
                let chunkSize = 70
                var part = 1
                var start = 0
                while start < paragraphs.count {
                    let slice = Array(paragraphs[start..<min(start + chunkSize, paragraphs.count)])
                    let title = "\(secTitle) (\(part))"
                    let html = "<h2>\(escapeHtml(title))</h2>\n" + slice.map { "<p>\(escapeHtml($0))</p>" }.joined(separator: "\n")
                    chapters.append(Chapter(index: idx, title: title, htmlContent: html, plainWordCount: countWords(slice.joined(separator: " "))))
                    idx += 1
                    part += 1
                    start += chunkSize
                }
            } else {
                let html = "<h2>\(escapeHtml(secTitle))</h2>\n" + paragraphs.map { "<p>\(escapeHtml($0))</p>" }.joined(separator: "\n")
                chapters.append(Chapter(index: idx, title: secTitle, htmlContent: html, plainWordCount: countWords(paragraphs.joined(separator: " "))))
                idx += 1
            }
        }

        if chapters.isEmpty {
            chapters.append(Chapter(index: 0, title: fallbackTitle, htmlContent: "<p>(空白文档)</p>", plainWordCount: 0))
        }

        return ParsedBook(title: fallbackTitle, author: "本地导入", format: .txt, chapters: chapters, coverImageData: nil)
    }

    // MARK: - Markdown Parser

    static func parseMarkdown(text: String, fallbackTitle: String) -> ParsedBook {
        let normalized = text.replacingOccurrences(of: "\r\n", with: "\n")
        let lines = normalized.components(separatedBy: "\n")
        var sections: [(String, [String])] = []
        var curTitle = ""
        var curLines: [String] = []
        var detectedBookTitle: String?

        for line in lines {
            if line.hasPrefix("# ") {
                let h1 = String(line.dropFirst(2)).trimmingCharacters(in: .whitespaces)
                if detectedBookTitle == nil && !h1.isEmpty { detectedBookTitle = h1 }
                if !curLines.joined().trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    sections.append((curTitle.isEmpty ? (detectedBookTitle ?? fallbackTitle) : curTitle, curLines))
                }
                curTitle = h1
                curLines = [line]
            } else if line.hasPrefix("## ") {
                let h2 = String(line.dropFirst(3)).trimmingCharacters(in: .whitespaces)
                if !curLines.joined().trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    sections.append((curTitle.isEmpty ? (detectedBookTitle ?? fallbackTitle) : curTitle, curLines))
                }
                curTitle = h2
                curLines = [line]
            } else {
                curLines.append(line)
            }
        }

        if !curLines.joined().trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            sections.append((curTitle.isEmpty ? (detectedBookTitle ?? fallbackTitle) : curTitle, curLines))
        }

        let chapters = sections.enumerated().map { idx, sec in
            let html = markdownLinesToHtml(sec.1)
            return Chapter(index: idx, title: sec.0, htmlContent: html, plainWordCount: countWords(sec.1.joined(separator: " ")))
        }

        return ParsedBook(
            title: detectedBookTitle ?? fallbackTitle,
            author: "Markdown 文档",
            format: .markdown,
            chapters: chapters.isEmpty ? [Chapter(index: 0, title: fallbackTitle, htmlContent: "<p></p>", plainWordCount: 0)] : chapters,
            coverImageData: nil
        )
    }

    private static func markdownLinesToHtml(_ lines: [String]) -> String {
        var html: [String] = []
        var inCode = false
        var codeBuf: [String] = []
        var inList = false

        func closeListIfNeeded() {
            if inList {
                html.append("</ul>")
                inList = false
            }
        }

        for raw in lines {
            let trimmed = raw.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("```") {
                closeListIfNeeded()
                if inCode {
                    html.append("<pre><code>\(escapeHtml(codeBuf.joined(separator: "\n")))</code></pre>")
                    codeBuf.removeAll()
                    inCode = false
                } else {
                    inCode = true
                }
                continue
            }
            if inCode {
                codeBuf.append(raw)
                continue
            }
            if trimmed.isEmpty {
                closeListIfNeeded()
                continue
            }
            if trimmed == "---" || trimmed == "***" {
                closeListIfNeeded()
                html.append("<hr/>")
            } else if trimmed.hasPrefix("### ") {
                closeListIfNeeded()
                html.append("<h3>\(inlineMarkdown(String(trimmed.dropFirst(4))))</h3>")
            } else if trimmed.hasPrefix("## ") {
                closeListIfNeeded()
                html.append("<h2>\(inlineMarkdown(String(trimmed.dropFirst(3))))</h2>")
            } else if trimmed.hasPrefix("# ") {
                closeListIfNeeded()
                html.append("<h1>\(inlineMarkdown(String(trimmed.dropFirst(2))))</h1>")
            } else if trimmed.hasPrefix("> ") {
                closeListIfNeeded()
                html.append("<blockquote><p>\(inlineMarkdown(String(trimmed.dropFirst(2))))</p></blockquote>")
            } else if trimmed.hasPrefix("- ") || trimmed.hasPrefix("* ") {
                if !inList {
                    html.append("<ul>")
                    inList = true
                }
                html.append("<li>\(inlineMarkdown(String(trimmed.dropFirst(2))))</li>")
            } else {
                closeListIfNeeded()
                html.append("<p>\(inlineMarkdown(trimmed))</p>")
            }
        }
        closeListIfNeeded()
        if inCode, !codeBuf.isEmpty {
            html.append("<pre><code>\(escapeHtml(codeBuf.joined(separator: "\n")))</code></pre>")
        }
        return html.joined(separator: "\n")
    }

    private static func inlineMarkdown(_ raw: String) -> String {
        var s = escapeHtml(raw)
        s = s.replacingOccurrences(of: #"\*\*(.+?)\*\*"#, with: "<strong>$1</strong>", options: .regularExpression)
        s = s.replacingOccurrences(of: #"\*(.+?)\*"#, with: "<em>$1</em>", options: .regularExpression)
        s = s.replacingOccurrences(of: #"`([^`]+)`"#, with: "<code>$1</code>", options: .regularExpression)
        return s
    }

    // MARK: - Pure Swift EPUB 2/3 Parser (ZIP Central Directory + Raw Deflate)

    static func parseEpub(data: Data, fallbackTitle: String, assetsDir: URL) throws -> ParsedBook {
        let entries = try SimpleZipReader.extractAll(from: data)
        guard !entries.isEmpty else { throw BookParserError.invalidEpubArchive }

        // Normalize lookup map by case-insensitive relative path
        var pathMap: [String: Data] = [:]
        for (k, v) in entries {
            let cleanKey = k.replacingOccurrences(of: "\\", with: "/").trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            pathMap[cleanKey] = v
        }

        // Write image assets to assetsDir so WKWebView can display illustrations
        try? FileManager.default.createDirectory(at: assetsDir, withIntermediateDirectories: true)
        for (relPath, entryData) in pathMap {
            let lower = relPath.lowercased()
            if lower.hasSuffix(".png") || lower.hasSuffix(".jpg") || lower.hasSuffix(".jpeg") || lower.hasSuffix(".gif") || lower.hasSuffix(".webp") || lower.hasSuffix(".svg") {
                let target = assetsDir.appendingPathComponent(relPath)
                try? FileManager.default.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
                try? entryData.write(to: target)
            }
        }

        // 1. Locate OPF via META-INF/container.xml
        var opfPath = pathMap.keys.first(where: { $0.lowercased().hasSuffix(".opf") }) ?? ""
        if let containerData = pathMap["META-INF/container.xml"] ?? pathMap.first(where: { $0.key.lowercased() == "meta-inf/container.xml" })?.value {
            let xml = decodeText(containerData)
            if let m = firstRegexCapture(#"full-path\s*=\s*["']([^"']+)["']"#, in: xml) {
                opfPath = m
            }
        }
        guard let opfData = pathMap[opfPath] ?? pathMap.first(where: { $0.key.caseInsensitiveCompare(opfPath) == .orderedSame })?.value else {
            throw BookParserError.invalidEpubArchive
        }

        let opfDir = (opfPath as NSString).deletingLastPathComponent
        let opfXml = decodeText(opfData)

        let title = cleanInlineTags(firstRegexCapture(#"<dc:title[^>]*>(.*?)</dc:title>"#, in: opfXml, dotMatchesNewlines: true) ?? "")
        let author = cleanInlineTags(firstRegexCapture(#"<dc:creator[^>]*>(.*?)</dc:creator>"#, in: opfXml, dotMatchesNewlines: true) ?? "")

        // Parse manifest items: id -> (href, mediaType, properties)
        var manifest: [String: (href: String, mediaType: String, props: String)] = [:]
        let itemTags = allRegexMatches(#"<item\b[^>]*>"#, in: opfXml)
        for tag in itemTags {
            let id = firstRegexCapture(#"\bid\s*=\s*["']([^"']+)["']"#, in: tag) ?? ""
            let href = (firstRegexCapture(#"\bhref\s*=\s*["']([^"']+)["']"#, in: tag) ?? "").removingPercentEncoding ?? ""
            let media = firstRegexCapture(#"\bmedia-type\s*=\s*["']([^"']+)["']"#, in: tag) ?? ""
            let props = firstRegexCapture(#"\bproperties\s*=\s*["']([^"']+)["']"#, in: tag) ?? ""
            if !id.isEmpty, !href.isEmpty {
                manifest[id] = (resolveRelativePath(baseDir: opfDir, href: href), media, props)
            }
        }

        // Detect cover image
        var coverData: Data?
        let coverMetaId = firstRegexCapture(#"<meta[^>]+name\s*=\s*["']cover["'][^>]+content\s*=\s*["']([^"']+)["']"#, in: opfXml)
            ?? firstRegexCapture(#"<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+name\s*=\s*["']cover["']"#, in: opfXml)
        if let cid = coverMetaId, let item = manifest[cid] {
            coverData = pathMap[item.href]
        }
        if coverData == nil, let coverItem = manifest.values.first(where: { $0.props.contains("cover-image") || ($0.mediaType.hasPrefix("image/") && $0.href.lowercased().contains("cover")) }) {
            coverData = pathMap[coverItem.href]
        }

        // Parse TOC titles from NCX if present
        var hrefTitles: [String: String] = [:]
        if let ncxItem = manifest.values.first(where: { $0.href.lowercased().hasSuffix(".ncx") }),
           let ncxData = pathMap[ncxItem.href] {
            let ncxDir = (ncxItem.href as NSString).deletingLastPathComponent
            let ncxXml = decodeText(ncxData)
            let navPoints = allRegexMatches(#"<navPoint\b[\s\S]*?</navPoint>"#, in: ncxXml)
            for np in navPoints {
                let label = cleanInlineTags(firstRegexCapture(#"<text[^>]*>([\s\S]*?)</text>"#, in: np) ?? "")
                let srcRaw = (firstRegexCapture(#"<content[^>]+src\s*=\s*["']([^"'#]+)"#, in: np) ?? "").removingPercentEncoding ?? ""
                if !label.isEmpty, !srcRaw.isEmpty {
                    let fullHref = resolveRelativePath(baseDir: ncxDir, href: srcRaw)
                    if hrefTitles[fullHref] == nil {
                        hrefTitles[fullHref] = label
                    }
                }
            }
        }

        // Parse spine order
        let itemRefs = allRegexMatches(#"<itemref\b[^>]*>"#, in: opfXml)
        var spineIds: [String] = []
        for ref in itemRefs {
            if let idref = firstRegexCapture(#"\bidref\s*=\s*["']([^"']+)["']"#, in: ref) {
                spineIds.append(idref)
            }
        }

        var chapters: [Chapter] = []
        for idref in spineIds {
            guard let item = manifest[idref],
                  item.mediaType.contains("html") || item.href.lowercased().hasSuffix("html") || item.href.lowercased().hasSuffix("xhtml"),
                  let rawHtmlData = pathMap[item.href] ?? pathMap.first(where: { $0.key.caseInsensitiveCompare(item.href) == .orderedSame })?.value else {
                continue
            }

            let rawHtml = decodeText(rawHtmlData)
            let chapterDir = (item.href as NSString).deletingLastPathComponent
            let cleanedBody = sanitizeEpubBodyHtml(rawHtml, chapterDir: chapterDir, assetsBaseURL: assetsDir)
            let plain = cleanInlineTags(cleanedBody)
            let words = countWords(plain)
            if plain.trimmingCharacters(in: .whitespacesAndNewlines).count < 8 && !cleanedBody.contains("<img") {
                continue
            }

            let extractedHeading = cleanInlineTags(
                firstRegexCapture(#"<h[1-3][^>]*>([\s\S]*?)</h[1-3]>"#, in: cleanedBody)
                ?? firstRegexCapture(#"<title[^>]*>([\s\S]*?)</title>"#, in: rawHtml)
                ?? ""
            )
            let chTitle = hrefTitles[item.href]
                ?? (extractedHeading.isEmpty ? "Chapter \(chapters.count + 1)" : extractedHeading)

            chapters.append(
                Chapter(
                    index: chapters.count,
                    title: chTitle,
                    htmlContent: cleanedBody,
                    plainWordCount: words
                )
            )
        }

        guard !chapters.isEmpty else { throw BookParserError.emptyContent }

        return ParsedBook(
            title: title.isEmpty ? fallbackTitle : title,
            author: author.isEmpty ? "未知作者" : author,
            format: .epub,
            chapters: chapters,
            coverImageData: coverData
        )
    }

    private static func sanitizeEpubBodyHtml(_ html: String, chapterDir: String, assetsBaseURL: URL) -> String {
        var body = firstRegexCapture(#"<body[^>]*>([\s\S]*?)</body>"#, in: html) ?? html
        body = body.replacingOccurrences(of: #"<script[\s\S]*?</script>"#, with: "", options: .regularExpression)
        body = body.replacingOccurrences(of: #"<style[\s\S]*?</style>"#, with: "", options: .regularExpression)

        // Rewrite relative <img src="..."> to extracted file:// URL inside book assetsDir
        if let regex = try? NSRegularExpression(pattern: #"(<img\b[^>]*?\bsrc\s*=\s*["'])([^"']+)(["'])"#, options: [.caseInsensitive]) {
            let ns = body as NSString
            let matches = regex.matches(in: body, range: NSRange(location: 0, length: ns.length)).reversed()
            let mutable = NSMutableString(string: body)
            for m in matches {
                let prefix = ns.substring(with: m.range(at: 1))
                let rawSrc = ns.substring(with: m.range(at: 2)).removingPercentEncoding ?? ns.substring(with: m.range(at: 2))
                let suffix = ns.substring(with: m.range(at: 3))
                if !rawSrc.hasPrefix("http") && !rawSrc.hasPrefix("data:") {
                    let rel = resolveRelativePath(baseDir: chapterDir, href: rawSrc)
                    let fileUrl = assetsBaseURL.appendingPathComponent(rel).absoluteString
                    mutable.replaceCharacters(in: m.range, with: "\(prefix)\(fileUrl)\(suffix)")
                }
            }
            body = mutable as String
        }
        return body
    }

    private static func resolveRelativePath(baseDir: String, href: String) -> String {
        let combined = baseDir.isEmpty ? href : "\(baseDir)/\(href)"
        var stack: [String] = []
        for part in combined.replacingOccurrences(of: "\\", with: "/").split(separator: "/") {
            if part == "." || part.isEmpty { continue }
            if part == ".." {
                if !stack.isEmpty { stack.removeLast() }
            } else {
                stack.append(String(part))
            }
        }
        return stack.joined(separator: "/")
    }

    private static func splitParagraphs(_ lines: [String]) -> [String] {
        var out: [String] = []
        var cur: [String] = []
        for line in lines {
            let t = line.trimmingCharacters(in: .whitespaces)
            if t.isEmpty {
                if !cur.isEmpty {
                    out.append(cur.joined(separator: " "))
                    cur.removeAll()
                }
            } else {
                cur.append(t)
            }
        }
        if !cur.isEmpty { out.append(cur.joined(separator: " ")) }
        return out
    }

    private static func countWords(_ text: String) -> Int {
        let englishWords = text.split { !$0.isLetter && $0 != "'" }.count
        return max(1, englishWords)
    }

    static func escapeHtml(_ s: String) -> String {
        s.replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
    }

    private static func cleanInlineTags(_ s: String) -> String {
        s.replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression)
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func firstRegexCapture(_ pattern: String, in text: String, dotMatchesNewlines: Bool = false) -> String? {
        var opts: NSRegularExpression.Options = [.caseInsensitive]
        if dotMatchesNewlines { opts.insert(.dotMatchesLineSeparators) }
        guard let regex = try? NSRegularExpression(pattern: pattern, options: opts) else { return nil }
        let ns = text as NSString
        guard let m = regex.firstMatch(in: text, range: NSRange(location: 0, length: ns.length)), m.numberOfRanges > 1 else {
            return nil
        }
        return ns.substring(with: m.range(at: 1))
    }

    private static func allRegexMatches(_ pattern: String, in text: String) -> [String] {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return [] }
        let ns = text as NSString
        return regex.matches(in: text, range: NSRange(location: 0, length: ns.length)).map { ns.substring(with: $0.range) }
    }
}

// MARK: - Zero-Dependency Pure Swift ZIP Extractor (Supports STORE & DEFLATE)

enum SimpleZipReader {
    static func extractAll(from archive: Data) throws -> [String: Data] {
        let bytes = [UInt8](archive)
        let total = bytes.count
        guard total >= 22 else { throw BookParserError.invalidEpubArchive }

        // Locate End of Central Directory (EOCD: 0x06054b50)
        let minOffset = max(0, total - 65557)
        var eocdOffset = -1
        for i in stride(from: total - 22, through: minOffset, by: -1) {
            if bytes[i] == 0x50 && bytes[i + 1] == 0x4B && bytes[i + 2] == 0x05 && bytes[i + 3] == 0x06 {
                eocdOffset = i
                break
            }
        }
        guard eocdOffset >= 0 else { throw BookParserError.invalidEpubArchive }

        let entryCount = Int(readU16(bytes, eocdOffset + 10))
        let cdOffset = Int(readU32(bytes, eocdOffset + 16))
        guard cdOffset >= 0, cdOffset < total else { throw BookParserError.invalidEpubArchive }

        var result: [String: Data] = [:]
        var ptr = cdOffset

        for _ in 0..<entryCount {
            guard ptr + 46 <= total,
                  bytes[ptr] == 0x50, bytes[ptr + 1] == 0x4B, bytes[ptr + 2] == 0x01, bytes[ptr + 3] == 0x02 else {
                break
            }
            let method = readU16(bytes, ptr + 10)
            let compSize = Int(readU32(bytes, ptr + 20))
            let nameLen = Int(readU16(bytes, ptr + 28))
            let extraLen = Int(readU16(bytes, ptr + 30))
            let commentLen = Int(readU16(bytes, ptr + 32))
            let localHeaderOffset = Int(readU32(bytes, ptr + 42))

            guard ptr + 46 + nameLen <= total else { break }
            let nameData = Data(bytes[(ptr + 46)..<(ptr + 46 + nameLen)])
            let fileName = String(data: nameData, encoding: .utf8) ?? String(data: nameData, encoding: .isoLatin1) ?? ""
            ptr += 46 + nameLen + extraLen + commentLen

            if fileName.isEmpty || fileName.hasSuffix("/") { continue }
            guard localHeaderOffset + 30 <= total,
                  bytes[localHeaderOffset] == 0x50, bytes[localHeaderOffset + 1] == 0x4B,
                  bytes[localHeaderOffset + 2] == 0x03, bytes[localHeaderOffset + 3] == 0x04 else {
                continue
            }

            let lNameLen = Int(readU16(bytes, localHeaderOffset + 26))
            let lExtraLen = Int(readU16(bytes, localHeaderOffset + 28))
            let dataStart = localHeaderOffset + 30 + lNameLen + lExtraLen
            guard dataStart >= 0, dataStart + compSize <= total else { continue }

            let payload = Data(bytes[dataStart..<(dataStart + compSize)])
            if method == 0 {
                result[fileName] = payload
            } else if method == 8 {
                if let inflated = try? (payload as NSData).decompressed(using: .zlib) as Data {
                    result[fileName] = inflated
                }
            }
        }
        return result
    }

    private static func readU16(_ b: [UInt8], _ off: Int) -> UInt16 {
        UInt16(b[off]) | (UInt16(b[off + 1]) << 8)
    }

    private static func readU32(_ b: [UInt8], _ off: Int) -> UInt32 {
        UInt32(b[off]) | (UInt32(b[off + 1]) << 8) | (UInt32(b[off + 2]) << 16) | (UInt32(b[off + 3]) << 24)
    }
}
