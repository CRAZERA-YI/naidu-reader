import Foundation
import SwiftUI

// MARK: - Core Reading & Book Models

enum BookFormat: String, Codable, CaseIterable {
    case txt = "TXT"
    case markdown = "MD"
    case epub = "EPUB"
}

struct Chapter: Codable, Identifiable, Equatable {
    var id: Int { index }
    let index: Int
    let title: String
    let htmlContent: String
    let plainWordCount: Int
}

struct ParsedBook {
    let title: String
    let author: String
    let format: BookFormat
    let chapters: [Chapter]
    let coverImageData: Data?
}

struct Book: Codable, Identifiable, Equatable {
    let id: String
    var title: String
    var author: String
    var format: BookFormat
    var localFolderName: String
    var coverFileName: String?
    var totalChapters: Int
    var totalWords: Int
    var currentChapterIndex: Int
    var currentChapterProgress: Double
    var addedAt: Date
    var lastReadAt: Date

    var overallProgressPercent: Int {
        guard totalChapters > 0 else { return 0 }
        let raw = ((Double(currentChapterIndex) + currentChapterProgress) / Double(totalChapters)) * 100.0
        return min(100, max(0, Int(raw.rounded())))
    }
}

// MARK: - Dictionary & Translation Models

struct WordForm: Codable, Equatable, Hashable {
    let name: String
    let value: String
}

struct ExampleSentence: Codable, Equatable, Hashable {
    let english: String
    let chinese: String
    let source: String
}

struct DetailedSense: Codable, Equatable, Hashable {
    let translation: String
    let detailPos: String
    let examples: [ExampleSentence]
}

struct SenseGroup: Codable, Equatable, Hashable {
    let pos: String
    let senses: [DetailedSense]
}

struct CollinsSense: Codable, Equatable, Hashable {
    let index: Int
    let pos: String
    let posTips: String
    let explanation: String
    let examples: [ExampleSentence]
}

struct PhraseItem: Codable, Equatable, Hashable {
    let phrase: String
    let meaning: String
}

struct RelatedWordEntry: Codable, Equatable, Hashable {
    let word: String
    let meaning: String
}

struct RelatedWordGroup: Codable, Equatable, Hashable {
    let pos: String
    let entries: [RelatedWordEntry]
}

struct TranslationResult: Codable, Equatable {
    var query: String
    var displayWord: String
    var lemma: String = ""
    var formNote: String = ""
    var phoneticUk: String = ""
    var phoneticUs: String = ""
    var chineseMeaning: String = ""
    var englishDef: String = ""
    var aiContextualTranslation: String = ""
    var tags: [String] = []
    var collins: Int = 0
    var oxford: Bool = false
    var wordForms: [WordForm] = []
    var senseGroups: [SenseGroup] = []
    var collinsSenses: [CollinsSense] = []
    var phrases: [PhraseItem] = []
    var relatedWords: [RelatedWordGroup] = []
    var examples: [ExampleSentence] = []
    var isPhrase: Bool = false
    var sourceLabel: String = "离线词典 (ECDICT)"
}

// MARK: - Vocabulary 2.0 & SM-2 Models

struct VocabContext: Codable, Equatable, Hashable, Identifiable {
    var id: String { "\(bookId)_\(chapterIndex)_\(sentence.prefix(32))" }
    let sentence: String
    let bookId: String
    let bookTitle: String
    let chapterIndex: Int
    let chapterTitle: String
    let capturedAt: Date
}

struct VocabWord: Codable, Identifiable, Equatable {
    let id: String
    var word: String
    var lemma: String
    var phoneticUs: String
    var phoneticUk: String
    var meaning: String
    var englishDef: String
    var tags: [String]
    var note: String
    var isStarred: Bool
    var isMastered: Bool
    var repetitions: Int
    var intervalDays: Int
    var easeFactor: Double
    var lapseCount: Int
    var encounterCount: Int
    var nextReviewAt: Date
    var lastReviewedAt: Date?
    var createdAt: Date
    var contexts: [VocabContext]

    var isDue: Bool {
        !isMastered && nextReviewAt <= Date()
    }

    var isStubborn: Bool {
        !isMastered && lapseCount >= 2
    }

    var masteryLabel: String {
        if isMastered { return "已掌握" }
        if repetitions == 0 { return "新词" }
        if intervalDays >= 14 { return "熟悉" }
        return "学习中"
    }
}

struct DailyStudyStat: Codable, Equatable {
    let dateKey: String // yyyy-MM-dd
    var reviewedCount: Int
    var newWordsAdded: Int
}

enum Sm2Scheduler {
    static func review(word: VocabWord, quality: Int, now: Date = Date()) -> VocabWord {
        var updated = word
        let q = max(0, min(5, quality))
        updated.lastReviewedAt = now

        if q < 3 {
            updated.repetitions = 0
            updated.intervalDays = 1
            updated.lapseCount += 1
            updated.easeFactor = max(1.3, updated.easeFactor - 0.2)
            updated.nextReviewAt = Calendar.current.date(byAdding: .hour, value: 8, to: now) ?? now
        } else {
            if updated.repetitions == 0 {
                updated.intervalDays = (q == 5) ? 3 : 1
            } else if updated.repetitions == 1 {
                updated.intervalDays = (q == 5) ? 7 : 4
            } else {
                let mult = (q == 5) ? updated.easeFactor * 1.15 : updated.easeFactor
                updated.intervalDays = max(1, Int((Double(updated.intervalDays) * mult).rounded()))
            }
            updated.repetitions += 1
            let delta = 0.1 - Double(5 - q) * (0.08 + Double(5 - q) * 0.02)
            updated.easeFactor = max(1.3, updated.easeFactor + delta)
            if updated.intervalDays >= 45 && q >= 4 {
                updated.isMastered = true
            }
            updated.nextReviewAt = Calendar.current.date(byAdding: .day, value: updated.intervalDays, to: now) ?? now
        }
        return updated
    }
}

// MARK: - Reader Themes, Modes & App Settings

enum ReaderTheme: String, Codable, CaseIterable, Identifiable {
    case sepia = "SEPIA"
    case light = "LIGHT"
    case green = "GREEN"
    case night = "NIGHT"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .sepia: return "羊皮护眼"
        case .light: return "纯白日光"
        case .green: return "豆沙绿"
        case .night: return "深夜暗黑"
        }
    }

    var bgHex: String {
        switch self {
        case .sepia: return "#F6EFE2"
        case .light: return "#FFFFFF"
        case .green: return "#DFEDDF"
        case .night: return "#16181D"
        }
    }

    var textHex: String {
        switch self {
        case .sepia: return "#2C241B"
        case .light: return "#1E2024"
        case .green: return "#1F2D22"
        case .night: return "#D7DCE6"
        }
    }

    var secondaryHex: String {
        switch self {
        case .sepia: return "#7C6F60"
        case .light: return "#6E737D"
        case .green: return "#556E59"
        case .night: return "#8B93A4"
        }
    }

    var accentHex: String {
        switch self {
        case .sepia: return "#B85D19"
        case .light: return "#2563EB"
        case .green: return "#2B7A4B"
        case .night: return "#E59947"
        }
    }

    var quoteBgHex: String {
        switch self {
        case .sepia: return "#ECE2CF"
        case .light: return "#F3F5F8"
        case .green: return "#D1E3D1"
        case .night: return "#22262F"
        }
    }
}

enum ReaderFont: String, Codable, CaseIterable, Identifiable {
    case serif = "SERIF"
    case sans = "SANS"
    case mono = "MONO"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .serif: return "衬线文学"
        case .sans: return "现代无衬线"
        case .mono: return "等宽代码"
        }
    }

    var cssFamily: String {
        switch self {
        case .serif: return "Georgia, 'Iowan Old Style', 'Times New Roman', serif"
        case .sans: return "-apple-system, BlinkMacSystemFont, 'SF Pro Text', sans-serif"
        case .mono: return "'SF Mono', Menlo, monospace"
        }
    }
}

enum PageTurnMode: String, Codable, CaseIterable, Identifiable {
    case cover = "cover"
    case simulation = "simulation"
    case slide = "slide"
    case fade = "fade"
    case none = "none"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .cover: return "覆盖翻页"
        case .simulation: return "仿真卷曲"
        case .slide: return "平滑平移"
        case .fade: return "柔和淡入"
        case .none: return "极速无动画"
        }
    }
}

enum TtsAccent: String, Codable, CaseIterable, Identifiable {
    case us = "US"
    case uk = "UK"

    var id: String { rawValue }
    var label: String { self == .us ? "美式发音 (US)" : "英式发音 (UK)" }
}

struct AppSettings: Codable, Equatable {
    var readerTheme: ReaderTheme = .sepia
    var fontFamily: ReaderFont = .serif
    var fontSizeSp: Int = 19
    var lineHeight: Double = 1.65
    var horizontalMarginDp: Int = 20
    var pageTurnMode: PageTurnMode = .cover
    var volumeKeyPaging: Bool = true
    var autoTurnIntervalSec: Int = 12
    var tapToTranslate: Bool = true
    var highlightVocab: Bool = true
    var autoPronounce: Bool = true
    var preferHumanVoice: Bool = true
    var ttsAccent: TtsAccent = .us
    var ttsSpeed: Double = 1.0
    var enableOnlineDict: Bool = true
    var aiApiBaseUrl: String = "https://api.openai.com/v1"
    var aiApiKey: String = ""
    var aiModel: String = "gpt-4o-mini"

    // SideStore Auto-Renewal Preferences
    var autoRenewReminderEnabled: Bool = true
    var autoOpenSideStoreWhenExpiring: Bool = true
    var renewAlertThresholdHours: Int = 48
}

// MARK: - Color Hex Extension

extension Color {
    init(hex: String) {
        let cleaned = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: cleaned).scanHexInt64(&int)
        let r, g, b: UInt64
        switch cleaned.count {
        case 6:
            (r, g, b) = ((int >> 16) & 0xFF, (int >> 8) & 0xFF, int & 0xFF)
        default:
            (r, g, b) = (246, 239, 226)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255.0,
            green: Double(g) / 255.0,
            blue: Double(b) / 255.0,
            opacity: 1.0
        )
    }
}
