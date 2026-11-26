import Foundation
import SwiftUI

@MainActor
final class AppStore: ObservableObject {
    @Published var books: [Book] = []
    @Published var words: [VocabWord] = []
    @Published var dailyStats: [String: DailyStudyStat] = [:]
    @Published var settings: AppSettings = AppSettings()
    @Published var jumpTargetBook: (bookId: String, chapterIndex: Int)?

    private let rootDir: URL
    private let booksDir: URL
    private let booksJsonURL: URL
    private let vocabJsonURL: URL
    private let statsJsonURL: URL
    private let settingsJsonURL: URL
    private let cacheJsonURL: URL

    private var translationCache: [String: TranslationResult] = [:]
    private let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .millisecondsSince1970
        return e
    }()
    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .millisecondsSince1970
        return d
    }()

    init() {
        let fm = FileManager.default
        let appSupport = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        rootDir = appSupport.appendingPathComponent("NaiDuData", isDirectory: true)
        booksDir = rootDir.appendingPathComponent("books", isDirectory: true)
        try? fm.createDirectory(at: booksDir, withIntermediateDirectories: true)

        booksJsonURL = rootDir.appendingPathComponent("books.json")
        vocabJsonURL = rootDir.appendingPathComponent("vocab.json")
        statsJsonURL = rootDir.appendingPathComponent("daily_stats.json")
        settingsJsonURL = rootDir.appendingPathComponent("settings.json")
        cacheJsonURL = rootDir.appendingPathComponent("trans_cache.json")

        loadAll()
        OfflineDictionary.shared.prepareInBackground()
        seedSampleBooksIfNeeded()
    }

    var dueCount: Int {
        let now = Date()
        return words.filter { !$0.isMastered && $0.nextReviewAt <= now }.count
    }

    // MARK: - Persistence

    private func loadAll() {
        if let d = try? Data(contentsOf: settingsJsonURL), let s = try? decoder.decode(AppSettings.self, from: d) {
            settings = s
        }
        if let d = try? Data(contentsOf: booksJsonURL), let b = try? decoder.decode([Book].self, from: d) {
            books = b
        }
        if let d = try? Data(contentsOf: vocabJsonURL), let w = try? decoder.decode([VocabWord].self, from: d) {
            words = w
        }
        if let d = try? Data(contentsOf: statsJsonURL), let st = try? decoder.decode([String: DailyStudyStat].self, from: d) {
            dailyStats = st
        }
        if let d = try? Data(contentsOf: cacheJsonURL), let c = try? decoder.decode([String: TranslationResult].self, from: d) {
            translationCache = c
        }
    }

    func updateSettings(_ mutate: (inout AppSettings) -> Void) {
        var copy = settings
        mutate(&copy)
        guard copy != settings else { return }
        settings = copy
        if let d = try? encoder.encode(copy) {
            try? d.write(to: settingsJsonURL, options: .atomic)
        }
        AutoSignRenewalManager.shared.refreshStatusAndSchedule(settings: copy)
    }

    private func saveBooks() {
        if let d = try? encoder.encode(books) {
            try? d.write(to: booksJsonURL, options: .atomic)
        }
    }

    private func saveWords() {
        if let d = try? encoder.encode(words) {
            try? d.write(to: vocabJsonURL, options: .atomic)
        }
    }

    private func saveStats() {
        if let d = try? encoder.encode(dailyStats) {
            try? d.write(to: statsJsonURL, options: .atomic)
        }
    }

    private func saveCache() {
        if translationCache.count > 1200 {
            let keysToKeep = Array(translationCache.keys.prefix(900))
            translationCache = translationCache.filter { keysToKeep.contains($0.key) }
        }
        if let d = try? encoder.encode(translationCache) {
            try? d.write(to: cacheJsonURL, options: .atomic)
        }
    }

    // MARK: - Sample Seeding & Book Import

    private func seedSampleBooksIfNeeded() {
        let seededKey = "naidu_samples_seeded_v1"
        guard !UserDefaults.standard.bool(forKey: seededKey), books.isEmpty else { return }
        UserDefaults.standard.set(true, forKey: seededKey)

        let sampleNames = ["Welcome_Guide.md", "The_Gift_of_the_Magi.txt", "Aesops_Fables_Sample.epub"]
        for name in sampleNames {
            let ns = name as NSString
            let res = ns.deletingPathExtension
            let ext = ns.pathExtension
            let url = Bundle.main.url(forResource: res, withExtension: ext, subdirectory: "samples")
                ?? Bundle.main.url(forResource: res, withExtension: ext)
            if let validURL = url {
                _ = try? importBookFromURL(validURL, securityScoped: false)
            }
        }
    }

    @discardableResult
    func importBookFromURL(_ url: URL, securityScoped: Bool = true) throws -> Book {
        let accessed = securityScoped ? url.startAccessingSecurityScopedResource() : false
        defer {
            if accessed { url.stopAccessingSecurityScopedResource() }
        }

        let bookId = UUID().uuidString
        let folderURL = booksDir.appendingPathComponent(bookId, isDirectory: true)
        let assetsURL = folderURL.appendingPathComponent("assets", isDirectory: true)
        try FileManager.default.createDirectory(at: assetsURL, withIntermediateDirectories: true)

        let parsed = try BookParsers.parseBook(at: url, extractedAssetsDir: assetsURL)
        let chaptersData = try encoder.encode(parsed.chapters)
        try chaptersData.write(to: folderURL.appendingPathComponent("chapters.json"), options: .atomic)

        var coverFileName: String?
        if let coverData = parsed.coverImageData, !coverData.isEmpty {
            coverFileName = "cover.img"
            try? coverData.write(to: folderURL.appendingPathComponent("cover.img"), options: .atomic)
        }

        let totalWords = parsed.chapters.reduce(0) { $0 + $1.plainWordCount }
        let book = Book(
            id: bookId,
            title: parsed.title,
            author: parsed.author,
            format: parsed.format,
            localFolderName: bookId,
            coverFileName: coverFileName,
            totalChapters: parsed.chapters.count,
            totalWords: totalWords,
            currentChapterIndex: 0,
            currentChapterProgress: 0.0,
            addedAt: Date(),
            lastReadAt: Date()
        )
        books.insert(book, at: 0)
        saveBooks()
        return book
    }

    func loadChapters(for book: Book) -> [Chapter] {
        let url = booksDir.appendingPathComponent(book.localFolderName).appendingPathComponent("chapters.json")
        guard let data = try? Data(contentsOf: url),
              let chs = try? decoder.decode([Chapter].self, from: data) else {
            return []
        }
        return chs
    }

    func coverURL(for book: Book) -> URL? {
        guard let cover = book.coverFileName else { return nil }
        return booksDir.appendingPathComponent(book.localFolderName).appendingPathComponent(cover)
    }

    func bookAssetsBaseURL(for book: Book) -> URL {
        booksDir.appendingPathComponent(book.localFolderName).appendingPathComponent("assets", isDirectory: true)
    }

    func updateBookProgress(bookId: String, chapterIndex: Int, progress: Double) {
        guard let idx = books.firstIndex(where: { $0.id == bookId }) else { return }
        books[idx].currentChapterIndex = chapterIndex
        books[idx].currentChapterProgress = max(0.0, min(1.0, progress))
        books[idx].lastReadAt = Date()
        saveBooks()
    }

    func deleteBook(_ book: Book) {
        books.removeAll { $0.id == book.id }
        let folderURL = booksDir.appendingPathComponent(book.localFolderName)
        try? FileManager.default.removeItem(at: folderURL)
        saveBooks()
    }

    // MARK: - Dictionary Lookup + SQLite/JSON Cache

    func lookupWordOrPhrase(_ query: String, sentence: String) async -> TranslationResult {
        let cleaned = OfflineDictionary.cleanQuery(query)
        let cacheKey = cleaned.lowercased()

        if settings.aiApiKey.isEmpty, let cached = translationCache[cacheKey] {
            return cached
        }

        let offline = await OfflineDictionary.shared.lookup(cleaned)
        let rich = await OnlineTranslator.lookupRich(
            query: cleaned,
            sentenceContext: sentence,
            offlineBase: offline,
            settings: settings
        )

        if !rich.chineseMeaning.isEmpty && (!rich.senseGroups.isEmpty || !rich.collinsSenses.isEmpty || offline != nil) {
            translationCache[cacheKey] = rich
            saveCache()
        }
        return rich
    }

    // MARK: - Vocabulary 2.0 Operations

    func findSavedWord(_ raw: String) -> VocabWord? {
        let lower = OfflineDictionary.cleanQuery(raw).lowercased()
        return words.first { $0.word.lowercased() == lower || $0.lemma.lowercased() == lower }
    }

    func saveOrUpdateWord(
        result: TranslationResult,
        sentence: String,
        book: Book?,
        chapterIndex: Int,
        chapterTitle: String
    ) {
        let keyWord = (result.lemma.isEmpty ? result.displayWord : result.lemma).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !keyWord.isEmpty else { return }

        let newContext: VocabContext? = sentence.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? nil
            : VocabContext(
                sentence: sentence,
                bookId: book?.id ?? "",
                bookTitle: book?.title ?? "手动添加",
                chapterIndex: chapterIndex,
                chapterTitle: chapterTitle,
                capturedAt: Date()
            )

        if let idx = words.firstIndex(where: { $0.word.caseInsensitiveCompare(keyWord) == .orderedSame || $0.lemma.caseInsensitiveCompare(keyWord) == .orderedSame }) {
            words[idx].encounterCount += 1
            if words[idx].phoneticUs.isEmpty { words[idx].phoneticUs = result.phoneticUs }
            if words[idx].phoneticUk.isEmpty { words[idx].phoneticUk = result.phoneticUk }
            if let ctx = newContext, !words[idx].contexts.contains(where: { $0.sentence == ctx.sentence }) {
                words[idx].contexts.insert(ctx, at: 0)
            }
        } else {
            let item = VocabWord(
                id: UUID().uuidString,
                word: keyWord,
                lemma: result.lemma.isEmpty ? keyWord.lowercased() : result.lemma.lowercased(),
                phoneticUs: result.phoneticUs,
                phoneticUk: result.phoneticUk,
                meaning: result.chineseMeaning,
                englishDef: result.englishDef,
                tags: result.tags,
                note: "",
                isStarred: false,
                isMastered: false,
                repetitions: 0,
                intervalDays: 0,
                easeFactor: 2.5,
                lapseCount: 0,
                encounterCount: 1,
                nextReviewAt: Date(),
                lastReviewedAt: nil,
                createdAt: Date(),
                contexts: newContext != nil ? [newContext!] : []
            )
            words.insert(item, at: 0)
            bumpDailyStat(addedNew: 1, reviewed: 0)
        }
        saveWords()
    }

    func recordEncounterIfSaved(rawWord: String, sentence: String, book: Book?, chapterIndex: Int, chapterTitle: String) {
        let lower = OfflineDictionary.cleanQuery(rawWord).lowercased()
        guard let idx = words.firstIndex(where: { $0.word.lowercased() == lower || $0.lemma.lowercased() == lower }) else { return }
        words[idx].encounterCount += 1
        if !sentence.isEmpty, !words[idx].contexts.contains(where: { $0.sentence == sentence }) {
            let ctx = VocabContext(
                sentence: sentence,
                bookId: book?.id ?? "",
                bookTitle: book?.title ?? "",
                chapterIndex: chapterIndex,
                chapterTitle: chapterTitle,
                capturedAt: Date()
            )
            words[idx].contexts.insert(ctx, at: 0)
        }
        saveWords()
    }

    func toggleStar(wordId: String) {
        guard let idx = words.firstIndex(where: { $0.id == wordId }) else { return }
        words[idx].isStarred.toggle()
        saveWords()
    }

    func toggleMastered(wordId: String) {
        guard let idx = words.firstIndex(where: { $0.id == wordId }) else { return }
        words[idx].isMastered.toggle()
        saveWords()
    }

    func updateNote(wordId: String, note: String) {
        guard let idx = words.firstIndex(where: { $0.id == wordId }) else { return }
        words[idx].note = note
        saveWords()
    }

    func resetWordProgress(wordId: String) {
        guard let idx = words.firstIndex(where: { $0.id == wordId }) else { return }
        words[idx].isMastered = false
        words[idx].repetitions = 0
        words[idx].intervalDays = 0
        words[idx].nextReviewAt = Date()
        saveWords()
    }

    func deleteWords(ids: Set<String>) {
        words.removeAll { ids.contains($0.id) }
        saveWords()
    }

    func recordReview(wordId: String, quality: Int) {
        guard let idx = words.firstIndex(where: { $0.id == wordId }) else { return }
        words[idx] = Sm2Scheduler.review(word: words[idx], quality: quality)
        saveWords()
        bumpDailyStat(addedNew: 0, reviewed: 1)
    }

    func batchImportWords(rawText: String) async -> Int {
        let tokens = rawText
            .components(separatedBy: CharacterSet(charactersIn: "\n,;\t "))
            .map { OfflineDictionary.cleanQuery($0) }
            .filter { $0.count >= 2 }

        var unique: [String] = []
        var seen = Set<String>()
        for t in tokens where seen.insert(t.lowercased()).inserted {
            unique.append(t)
        }

        var added = 0
        for token in unique.prefix(500) {
            if findSavedWord(token) != nil { continue }
            if let hit = await OfflineDictionary.shared.lookup(token) {
                saveOrUpdateWord(result: hit, sentence: "", book: nil, chapterIndex: 0, chapterTitle: "批量导入")
                added += 1
            }
        }
        return added
    }

    func exportVocabAsCSV() -> String {
        var lines = ["Word,PhoneticUS,PhoneticUK,Meaning,Mastery,Starred,Encounters,Note,Context"]
        for w in words {
            let ctx = w.contexts.first?.sentence ?? ""
            let fields = [
                w.word,
                w.phoneticUs,
                w.phoneticUk,
                w.meaning.replacingOccurrences(of: "\n", with: " | "),
                w.masteryLabel,
                w.isStarred ? "Yes" : "No",
                "\(w.encounterCount)",
                w.note,
                ctx
            ].map { "\"\($0.replacingOccurrences(of: "\"", with: "\"\""))\"" }
            lines.append(fields.joined(separator: ","))
        }
        return lines.joined(separator: "\n")
    }

    func exportVocabAsMarkdown() -> String {
        var lines = ["# 奶读单词本导出 (\(words.count) 词)\n"]
        for w in words {
            let ph = w.phoneticUs.isEmpty ? "" : " `/\(w.phoneticUs)/`"
            let star = w.isStarred ? " ⭐" : ""
            lines.append("## \(w.word)\(ph)\(star) · \(w.masteryLabel)")
            lines.append(w.meaning)
            if !w.note.isEmpty {
                lines.append("> 笔记：\(w.note)")
            }
            if let firstCtx = w.contexts.first {
                lines.append("- 原句：*\(firstCtx.sentence)* （《\(firstCtx.bookTitle)》）")
            }
            lines.append("")
        }
        return lines.joined(separator: "\n")
    }

    private func bumpDailyStat(addedNew: Int, reviewed: Int) {
        let key = Self.todayKey()
        var cur = dailyStats[key] ?? DailyStudyStat(dateKey: key, reviewedCount: 0, newWordsAdded: 0)
        cur.newWordsAdded += addedNew
        cur.reviewedCount += reviewed
        dailyStats[key] = cur
        saveStats()
    }

    static func todayKey(for date: Date = Date()) -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"
        return fmt.string(from: date)
    }

    var gradedHighlightWordSets: (due: [String], learning: [String]) {
        let now = Date()
        var due: [String] = []
        var learning: [String] = []
        for w in words where !w.isMastered {
            let key = w.word.lowercased()
            if w.nextReviewAt <= now || w.lapseCount >= 2 {
                due.append(key)
            } else {
                learning.append(key)
            }
        }
        return (due, learning)
    }
}
