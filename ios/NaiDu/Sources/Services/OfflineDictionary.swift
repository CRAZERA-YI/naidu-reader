import Foundation
import SQLite3
import zlib

private let SQLITE_TRANSIENT_DESTRUCTOR = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

final class OfflineDictionary: @unchecked Sendable {
    static let shared = OfflineDictionary()

    private let queue = DispatchQueue(label: "com.lingoreader.naidu.offlinedict", qos: .userInitiated)
    private var db: OpaquePointer?
    private(set) var wordCount: Int = 0

    private static let tagDisplayMap: [String: String] = [
        "zk": "中考",
        "gk": "高考",
        "cet4": "CET4",
        "cet6": "CET6",
        "ky": "考研",
        "toefl": "TOEFL",
        "ielts": "IELTS",
        "gre": "GRE"
    ]

    private init() {}

    deinit {
        if let db = db {
            sqlite3_close(db)
        }
    }

    static func cleanQuery(_ raw: String) -> String {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\u{2019}", with: "'")
            .replacingOccurrences(of: "\u{2018}", with: "'")
            .replacingOccurrences(of: "\u{2013}", with: "-")
            .replacingOccurrences(of: "\u{2014}", with: "-")

        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "'- "))
        s = s.trimmingCharacters(in: allowed.inverted)
        if s.hasSuffix("'s") || s.hasSuffix("'S") {
            s = String(s.dropLast(2))
        }
        return s.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func generateMorphCandidates(_ word: String) -> [(String, String)] {
        var out: [(String, String)] = []
        guard word.count >= 3, !word.contains(" ") else { return out }

        if word.hasSuffix("ies"), word.count > 4 {
            out.append((String(word.dropLast(3)) + "y", "复数/三单"))
        }
        if word.hasSuffix("es"), word.count > 3 {
            out.append((String(word.dropLast(2)), "复数/三单"))
            out.append((String(word.dropLast(1)), "复数/三单"))
        }
        if word.hasSuffix("s"), !word.hasSuffix("ss"), word.count > 3 {
            out.append((String(word.dropLast(1)), "复数/三单"))
        }
        if word.hasSuffix("ied"), word.count > 4 {
            out.append((String(word.dropLast(3)) + "y", "过去式/过去分词"))
        }
        if word.hasSuffix("ed"), word.count > 4 {
            let stem = String(word.dropLast(2))
            out.append((stem, "过去式/过去分词"))
            out.append((stem + "e", "过去式/过去分词"))
            let chars = Array(stem)
            if chars.count >= 2, chars[chars.count - 1] == chars[chars.count - 2] {
                out.append((String(stem.dropLast(1)), "过去式/过去分词"))
            }
        }
        if word.hasSuffix("ing"), word.count > 5 {
            let stem = String(word.dropLast(3))
            out.append((stem, "现在分词"))
            out.append((stem + "e", "现在分词"))
            let chars = Array(stem)
            if chars.count >= 2, chars[chars.count - 1] == chars[chars.count - 2] {
                out.append((String(stem.dropLast(1)), "现在分词"))
            }
            if stem.hasSuffix("y") {
                out.append((String(stem.dropLast(1)) + "ie", "现在分词"))
            }
        }
        if word.hasSuffix("ier"), word.count > 4 {
            out.append((String(word.dropLast(3)) + "y", "比较级"))
        }
        if word.hasSuffix("er"), word.count > 4 {
            let stem = String(word.dropLast(2))
            out.append((stem, "比较级/派生"))
            out.append((stem + "e", "比较级/派生"))
            let chars = Array(stem)
            if chars.count >= 2, chars[chars.count - 1] == chars[chars.count - 2] {
                out.append((String(stem.dropLast(1)), "比较级"))
            }
        }
        if word.hasSuffix("iest"), word.count > 5 {
            out.append((String(word.dropLast(4)) + "y", "最高级"))
        }
        if word.hasSuffix("est"), word.count > 5 {
            let stem = String(word.dropLast(3))
            out.append((stem, "最高级"))
            out.append((stem + "e", "最高级"))
        }
        if word.hasSuffix("ly"), word.count > 4 {
            let stem = String(word.dropLast(2))
            out.append((stem, "副词形式"))
            if stem.hasSuffix("i") {
                out.append((String(stem.dropLast(1)) + "y", "副词形式"))
            }
        }

        var seen = Set<String>()
        return out.filter { seen.insert($0.0).inserted }
    }

    func prepareInBackground() {
        queue.async { [weak self] in
            _ = self?.ensureOpenLocked()
        }
    }

    func lookup(_ rawQuery: String) async -> TranslationResult? {
        await withCheckedContinuation { continuation in
            queue.async { [weak self] in
                guard let self = self, self.ensureOpenLocked(), let database = self.db else {
                    continuation.resume(returning: nil)
                    return
                }
                let cleaned = Self.cleanQuery(rawQuery)
                guard !cleaned.isEmpty else {
                    continuation.resume(returning: nil)
                    return
                }
                let lower = cleaned.lowercased()

                let exact = self.queryWordExact(database, wordLower: lower)
                let lemmaInfo = self.queryLemma(database, inflected: lower)

                if var hit = exact {
                    hit.query = cleaned
                    hit.lemma = lemmaInfo?.0 ?? hit.displayWord.lowercased()
                    if let lemma = lemmaInfo, lemma.0 != lower {
                        hit.formNote = "\(lemma.0) 的\(lemma.1)"
                    }
                    continuation.resume(returning: hit)
                    return
                }

                if let lemma = lemmaInfo, var baseHit = self.queryWordExact(database, wordLower: lemma.0) {
                    baseHit.query = cleaned
                    baseHit.displayWord = cleaned
                    baseHit.lemma = baseHit.lemma.isEmpty ? lemma.0 : baseHit.lemma
                    baseHit.formNote = "\(lemma.0) 的\(lemma.1)"
                    continuation.resume(returning: baseHit)
                    return
                }

                for (candidate, label) in Self.generateMorphCandidates(lower) {
                    if var stemHit = self.queryWordExact(database, wordLower: candidate) {
                        let baseWord = stemHit.displayWord
                        stemHit.query = cleaned
                        stemHit.displayWord = cleaned
                        stemHit.lemma = baseWord
                        stemHit.formNote = "\(baseWord) 的\(label)"
                        continuation.resume(returning: stemHit)
                        return
                    }
                }

                let sw = lower.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) }.map(String.init).joined()
                if !sw.isEmpty, sw != lower, var swHit = self.queryBySw(database, sw: sw) {
                    swHit.query = cleaned
                    continuation.resume(returning: swHit)
                    return
                }

                continuation.resume(returning: nil)
            }
        }
    }

    private func ensureOpenLocked() -> Bool {
        if db != nil { return true }
        let fm = FileManager.default
        guard let appSupport = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
            return false
        }
        try? fm.createDirectory(at: appSupport, withIntermediateDirectories: true)
        let dbURL = appSupport.appendingPathComponent("ecdict_offline_v1.db")

        let attrs = try? fm.attributesOfItem(atPath: dbURL.path)
        let size = (attrs?[.size] as? NSNumber)?.int64Value ?? 0

        if size < 1_000_000 {
            guard let gzURL = Bundle.main.url(forResource: "dictionary", withExtension: "zdb") else {
                return false
            }
            let tmpURL = appSupport.appendingPathComponent("ecdict_offline_v1.db.tmp")
            try? fm.removeItem(at: tmpURL)
            guard Self.gunzipFile(from: gzURL, to: tmpURL) else {
                return false
            }
            try? fm.removeItem(at: dbURL)
            do {
                try fm.moveItem(at: tmpURL, to: dbURL)
            } catch {
                return false
            }
        }

        var handle: OpaquePointer?
        if sqlite3_open_v2(dbURL.path, &handle, SQLITE_OPEN_READONLY | SQLITE_OPEN_NOMUTEX, nil) == SQLITE_OK, let opened = handle {
            db = opened
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(opened, "SELECT COUNT(*) FROM words", -1, &stmt, nil) == SQLITE_OK {
                if sqlite3_step(stmt) == SQLITE_ROW {
                    wordCount = Int(sqlite3_column_int(stmt, 0))
                }
            }
            sqlite3_finalize(stmt)
            return true
        } else {
            if let handle = handle { sqlite3_close(handle) }
            return false
        }
    }

    private static func gunzipFile(from srcURL: URL, to dstURL: URL) -> Bool {
        guard let gz = srcURL.path.withCString({ p in "rb".withCString { m in gzopen(p, m) } }) else {
            return false
        }
        defer { gzclose(gz) }

        FileManager.default.createFile(atPath: dstURL.path, contents: nil)
        guard let outHandle = try? FileHandle(forWritingTo: dstURL) else {
            return false
        }
        defer { try? outHandle.close() }

        let chunkSize = 65536
        var buffer = [UInt8](repeating: 0, count: chunkSize)
        while true {
            let readBytes = buffer.withUnsafeMutableBytes { rawBuf -> Int32 in
                guard let base = rawBuf.baseAddress else { return -1 }
                return gzread(gz, base, UInt32(chunkSize))
            }
            if readBytes > 0 {
                outHandle.write(Data(buffer.prefix(Int(readBytes))))
            } else if readBytes == 0 {
                break
            } else {
                return false
            }
        }
        return true
    }

    private func queryWordExact(_ database: OpaquePointer, wordLower: String) -> TranslationResult? {
        let sql = "SELECT word, phonetic, definition, translation, tag, collins, oxford FROM words WHERE word_lower = ? LIMIT 1"
        var stmt: OpaquePointer?
        defer { sqlite3_finalize(stmt) }
        guard sqlite3_prepare_v2(database, sql, -1, &stmt, nil) == SQLITE_OK else { return nil }
        sqlite3_bind_text(stmt, 1, wordLower, -1, SQLITE_TRANSIENT_DESTRUCTOR)
        guard sqlite3_step(stmt) == SQLITE_ROW else { return nil }
        return readWordRow(stmt)
    }

    private func queryBySw(_ database: OpaquePointer, sw: String) -> TranslationResult? {
        let sql = "SELECT word, phonetic, definition, translation, tag, collins, oxford FROM words WHERE sw = ? LIMIT 1"
        var stmt: OpaquePointer?
        defer { sqlite3_finalize(stmt) }
        guard sqlite3_prepare_v2(database, sql, -1, &stmt, nil) == SQLITE_OK else { return nil }
        sqlite3_bind_text(stmt, 1, sw, -1, SQLITE_TRANSIENT_DESTRUCTOR)
        guard sqlite3_step(stmt) == SQLITE_ROW else { return nil }
        return readWordRow(stmt)
    }

    private func queryLemma(_ database: OpaquePointer, inflected: String) -> (String, String)? {
        let sql = "SELECT base_word, form_type FROM lemmas WHERE inflected = ? LIMIT 1"
        var stmt: OpaquePointer?
        defer { sqlite3_finalize(stmt) }
        guard sqlite3_prepare_v2(database, sql, -1, &stmt, nil) == SQLITE_OK else { return nil }
        sqlite3_bind_text(stmt, 1, inflected, -1, SQLITE_TRANSIENT_DESTRUCTOR)
        guard sqlite3_step(stmt) == SQLITE_ROW else { return nil }
        let base = columnString(stmt, 0)
        let form = columnString(stmt, 1)
        return base.isEmpty ? nil : (base, form)
    }

    private func readWordRow(_ stmt: OpaquePointer?) -> TranslationResult {
        let word = columnString(stmt, 0)
        let phonetic = columnString(stmt, 1)
        let definition = normalizeNewlines(columnString(stmt, 2))
        let translation = normalizeNewlines(columnString(stmt, 3))
        let tagRaw = columnString(stmt, 4)
        let collins = Int(sqlite3_column_int(stmt, 5))
        let oxford = sqlite3_column_int(stmt, 6) == 1

        let tags = tagRaw
            .split(whereSeparator: { $0.isWhitespace })
            .compactMap { Self.tagDisplayMap[$0.lowercased()] }

        return TranslationResult(
            query: word,
            displayWord: word,
            lemma: word,
            phoneticUs: phonetic,
            chineseMeaning: translation,
            englishDef: definition,
            tags: tags,
            collins: collins,
            oxford: oxford,
            isPhrase: word.contains(" "),
            sourceLabel: "离线词典 (ECDICT)"
        )
    }

    private func columnString(_ stmt: OpaquePointer?, _ index: Int32) -> String {
        guard let cStr = sqlite3_column_text(stmt, index) else { return "" }
        return String(cString: cStr)
    }

    private func normalizeNewlines(_ raw: String) -> String {
        raw.replacingOccurrences(of: "\\n", with: "\n")
            .replacingOccurrences(of: "\r\n", with: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
