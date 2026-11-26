import AVFoundation
import Combine
import MediaPlayer
import SwiftUI
import WebKit

struct LookupRequest: Identifiable, Equatable {
    let id = UUID()
    let query: String
    let sentence: String
}

struct ReaderView: View {
    @EnvironmentObject private var store: AppStore
    let book: Book
    let onClose: () -> Void

    @State private var chapters: [Chapter] = []
    @State private var currentChapterIndex: Int = 0
    @State private var initialRatio: Double = 0.0
    @State private var currentPage: Int = 0
    @State private var totalPages: Int = 1
    @State private var chapterLoadToken: UUID = UUID()

    @State private var showChrome: Bool = false
    @State private var showTocSheet: Bool = false
    @State private var showSettingsSheet: Bool = false
    @State private var isAutoTurning: Bool = false

    @State private var activeLookup: LookupRequest?
    @State private var pendingSelection: LookupRequest?
    @State private var webController = ReaderWebController()

    private let autoTurnTicker = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    @State private var autoTurnElapsed: Int = 0

    private var currentChapter: Chapter? {
        guard chapters.indices.contains(currentChapterIndex) else { return nil }
        return chapters[currentChapterIndex]
    }

    var body: some View {
        let theme = store.settings.readerTheme

        ZStack {
            Color(hex: theme.bgHex).ignoresSafeArea()

            if let chapter = currentChapter {
                VStack(spacing: 0) {
                    // Subtle Top Chapter Status Bar
                    HStack {
                        Text(chapter.title)
                            .font(.caption)
                            .foregroundStyle(Color(hex: theme.secondaryHex))
                            .lineLimit(1)
                        Spacer()
                        if isAutoTurning {
                            Text("自动翻页 \(max(1, store.settings.autoTurnIntervalSec - autoTurnElapsed))s")
                                .font(.caption2.weight(.semibold))
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                .background(Color(hex: theme.accentHex).opacity(0.16))
                                .foregroundStyle(Color(hex: theme.accentHex))
                                .clipShape(Capsule())
                        }
                    }
                    .padding(.horizontal, Double(store.settings.horizontalMarginDp))
                    .padding(.top, 4)

                    ReaderWebViewRepresentable(
                        controller: webController,
                        chapter: chapter,
                        chapterLoadToken: chapterLoadToken,
                        initialRatio: initialRatio,
                        assetsBaseURL: store.bookAssetsBaseURL(for: book),
                        settings: store.settings,
                        gradedHighlights: store.gradedHighlightWordSets,
                        onPageChanged: { cur, total in
                            currentPage = cur
                            totalPages = max(1, total)
                            let ratio = total <= 1 ? 0.0 : Double(cur) / Double(total - 1)
                            store.updateBookProgress(bookId: book.id, chapterIndex: currentChapterIndex, progress: ratio)
                        },
                        onWordTapped: { word, sentence in
                            showChrome = false
                            activeLookup = LookupRequest(query: word, sentence: sentence)
                            store.recordEncounterIfSaved(
                                rawWord: word,
                                sentence: sentence,
                                book: book,
                                chapterIndex: currentChapterIndex,
                                chapterTitle: chapter.title
                            )
                        },
                        onSelectionChanged: { text, sentence in
                            pendingSelection = LookupRequest(query: text, sentence: sentence)
                        },
                        onSelectionCleared: {
                            pendingSelection = nil
                        },
                        onBlankTapped: {
                            withAnimation(.easeOut(duration: 0.16)) {
                                showChrome.toggle()
                            }
                        },
                        onNextChapter: {
                            if currentChapterIndex + 1 < chapters.count {
                                currentChapterIndex += 1
                                initialRatio = 0.0
                                chapterLoadToken = UUID()
                            }
                        },
                        onPrevChapter: {
                            if currentChapterIndex > 0 {
                                currentChapterIndex -= 1
                                initialRatio = -1.0 // Jump to last page of previous chapter
                                chapterLoadToken = UUID()
                            }
                        }
                    )

                    // Subtle Bottom Page Counter Bar
                    HStack {
                        Text("\(currentChapterIndex + 1)/\(max(1, chapters.count)) 章")
                            .font(.caption2)
                            .foregroundStyle(Color(hex: theme.secondaryHex))
                        Spacer()
                        Text("第 \(currentPage + 1) / \(totalPages) 页")
                            .font(.caption2.monospacedDigit())
                            .foregroundStyle(Color(hex: theme.secondaryHex))
                    }
                    .padding(.horizontal, Double(store.settings.horizontalMarginDp))
                    .padding(.bottom, 6)
                }
            } else {
                ProgressView("正在加载章节...")
            }

            // Floating Action Pill when user long-presses & drags a multi-word selection
            if let sel = pendingSelection, activeLookup == nil {
                VStack {
                    Spacer()
                    Button {
                        activeLookup = sel
                        pendingSelection = nil
                        webController.clearSelection()
                    } label: {
                        Label("翻译选中内容", systemImage: "character.bubble.fill")
                            .font(.subheadline.weight(.semibold))
                            .padding(.horizontal, 18)
                            .padding(.vertical, 10)
                            .background(Color.accentColor)
                            .foregroundStyle(.white)
                            .clipShape(Capsule())
                            .shadow(radius: 6)
                    }
                    .padding(.bottom, 54)
                }
            }

            // Hardware Volume-Key Paging Interceptor (active only when enabled in settings)
            if store.settings.volumeKeyPaging {
                VolumeButtonPagingView(
                    onVolumeDown: { webController.nextPage() },
                    onVolumeUp: { webController.prevPage() }
                )
                .frame(width: 1, height: 1)
                .opacity(0.01)
                .allowsHitTesting(false)
            }

            // Top & Bottom Reader Chrome Overlay
            if showChrome {
                VStack {
                    HStack(spacing: 14) {
                        Button(action: onClose) {
                            Image(systemName: "chevron.left")
                                .font(.title3.weight(.semibold))
                        }
                        Text(book.title)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                        Spacer()
                        Button {
                            isAutoTurning.toggle()
                            autoTurnElapsed = 0
                        } label: {
                            Image(systemName: isAutoTurning ? "pause.circle.fill" : "play.circle")
                                .font(.title3)
                        }
                        Button {
                            showTocSheet = true
                        } label: {
                            Image(systemName: "list.bullet")
                                .font(.title3)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(.ultraThinMaterial)

                    Spacer()

                    VStack(spacing: 12) {
                        if totalPages > 1 {
                            Slider(
                                value: Binding(
                                    get: { Double(currentPage) },
                                    set: { webController.goToPage(Int($0.rounded()), animated: false) }
                                ),
                                in: 0...Double(max(1, totalPages - 1)),
                                step: 1
                            )
                        }
                        HStack {
                            Button("上一章") {
                                if currentChapterIndex > 0 {
                                    currentChapterIndex -= 1
                                    initialRatio = 0.0
                                    chapterLoadToken = UUID()
                                }
                            }
                            .disabled(currentChapterIndex == 0)

                            Spacer()

                            Button {
                                showSettingsSheet = true
                            } label: {
                                Label("排版与翻页设置", systemImage: "textformat.size")
                                    .font(.subheadline.weight(.medium))
                            }

                            Spacer()

                            Button("下一章") {
                                if currentChapterIndex + 1 < chapters.count {
                                    currentChapterIndex += 1
                                    initialRatio = 0.0
                                    chapterLoadToken = UUID()
                                }
                            }
                            .disabled(currentChapterIndex + 1 >= chapters.count)
                        }
                        .font(.footnote)
                    }
                    .padding(16)
                    .background(.ultraThinMaterial)
                }
            }
        }
        .statusBarHidden(!showChrome)
        .onAppear {
            chapters = store.loadChapters(for: book)
            currentChapterIndex = min(max(0, book.currentChapterIndex), max(0, chapters.count - 1))
            initialRatio = book.currentChapterProgress
            chapterLoadToken = UUID()
        }
        .onReceive(autoTurnTicker) { _ in
            guard isAutoTurning, activeLookup == nil, !showChrome else { return }
            autoTurnElapsed += 1
            if autoTurnElapsed >= max(2, store.settings.autoTurnIntervalSec) {
                autoTurnElapsed = 0
                webController.nextPage()
            }
        }
        .sheet(item: $activeLookup, onDismiss: {
            webController.setPopupOpen(false)
        }) { req in
            WordLookupSheet(
                request: req,
                book: book,
                chapterIndex: currentChapterIndex,
                chapterTitle: currentChapter?.title ?? ""
            )
            .environmentObject(store)
            .presentationDetents([.medium, .large])
            .onAppear { webController.setPopupOpen(true) }
        }
        .sheet(isPresented: $showTocSheet) {
            NavigationStack {
                List(chapters) { ch in
                    Button {
                        currentChapterIndex = ch.index
                        initialRatio = 0.0
                        chapterLoadToken = UUID()
                        showTocSheet = false
                        showChrome = false
                    } label: {
                        HStack {
                            Text(ch.title)
                                .foregroundStyle(ch.index == currentChapterIndex ? Color.accentColor : .primary)
                                .fontWeight(ch.index == currentChapterIndex ? .bold : .regular)
                            Spacer()
                            Text("\(ch.plainWordCount) 词")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .navigationTitle("目录 (\(chapters.count) 章)")
                .navigationBarTitleDisplayMode(.inline)
            }
            .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $showSettingsSheet) {
            ReaderQuickSettingsSheet(isAutoTurning: $isAutoTurning) { updated in
                webController.applyLiveSettings(updated)
            }
            .environmentObject(store)
            .presentationDetents([.medium, .large])
        }
    }
}

// MARK: - Rich Word / Sentence Lookup Sheet

struct WordLookupSheet: View {
    @EnvironmentObject private var store: AppStore
    let request: LookupRequest
    let book: Book?
    let chapterIndex: Int
    let chapterTitle: String

    @State private var result: TranslationResult?
    @State private var isLoading: Bool = true
    @State private var showCollins: Bool = false

    private var savedWord: VocabWord? {
        store.findSavedWord(result?.lemma.isEmpty == false ? result!.lemma : request.query)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if let res = result {
                        // Header + US/UK Audio + Save Button
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 6) {
                                Text(res.displayWord)
                                    .font(.title2.bold())
                                if !res.formNote.isEmpty {
                                    Text(res.formNote)
                                        .font(.caption)
                                        .foregroundStyle(.orange)
                                }
                                HStack(spacing: 10) {
                                    Button {
                                        var s = store.settings; s.ttsAccent = .us
                                        SpeechManager.shared.speakWord(res.displayWord, settings: s)
                                    } label: {
                                        Label(res.phoneticUs.isEmpty ? "美音" : "美 /\(res.phoneticUs)/", systemImage: "speaker.wave.2.fill")
                                            .font(.caption)
                                    }
                                    .buttonStyle(.bordered)

                                    Button {
                                        var s = store.settings; s.ttsAccent = .uk
                                        SpeechManager.shared.speakWord(res.displayWord, settings: s)
                                    } label: {
                                        Label(res.phoneticUk.isEmpty ? "英音" : "英 /\(res.phoneticUk)/", systemImage: "speaker.wave.2")
                                            .font(.caption)
                                    }
                                    .buttonStyle(.bordered)
                                }
                            }
                            Spacer()

                            if !res.isPhrase {
                                Button {
                                    if let existing = savedWord {
                                        store.deleteWords(ids: [existing.id])
                                    } else {
                                        store.saveOrUpdateWord(
                                            result: res,
                                            sentence: request.sentence,
                                            book: book,
                                            chapterIndex: chapterIndex,
                                            chapterTitle: chapterTitle
                                        )
                                    }
                                } label: {
                                    Label(savedWord != nil ? "已收词" : "加入生词本", systemImage: savedWord != nil ? "bookmark.fill" : "bookmark")
                                        .font(.caption.weight(.semibold))
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(savedWord != nil ? .green : .accentColor)
                            }
                        }

                        // Frequency Badges
                        if !res.tags.isEmpty || res.collins > 0 || res.oxford {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 6) {
                                    if res.oxford { BadgePill(text: "牛津3000", color: .blue) }
                                    if res.collins > 0 { BadgePill(text: "柯林斯 \(String(repeating: "★", count: res.collins))", color: .orange) }
                                    ForEach(res.tags, id: \.self) { t in
                                        BadgePill(text: t, color: .secondary)
                                    }
                                }
                            }
                        }

                        // AI Contextual Translation
                        if !res.aiContextualTranslation.isEmpty {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("AI 原句语境精译")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(.purple)
                                Text(res.aiContextualTranslation)
                                    .font(.subheadline)
                            }
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.purple.opacity(0.08))
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                        }

                        // Core Chinese Meanings
                        VStack(alignment: .leading, spacing: 6) {
                            Text("核心释义 · \(res.sourceLabel)")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                            Text(res.chineseMeaning)
                                .font(.body)
                                .textSelection(.enabled)
                        }

                        // Detailed POS Sense Groups
                        if !res.senseGroups.isEmpty {
                            Divider()
                            VStack(alignment: .leading, spacing: 10) {
                                Text("详尽词性义项")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(.secondary)
                                ForEach(res.senseGroups, id: \.self) { group in
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(group.pos)
                                            .font(.caption.weight(.bold))
                                            .foregroundStyle(Color.accentColor)
                                        ForEach(Array(group.senses.enumerated()), id: \.offset) { idx, sense in
                                            Text("\(idx + 1). \(sense.translation)")
                                                .font(.subheadline)
                                            if let ex = sense.examples.first {
                                                Text("例：\(ex.english)\n\(ex.chinese)")
                                                    .font(.caption)
                                                    .foregroundStyle(.secondary)
                                                    .padding(.leading, 10)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Collapsible Collins Bilingual Dictionary
                        if !res.collinsSenses.isEmpty {
                            DisclosureGroup("柯林斯英汉双解 (\(res.collinsSenses.count) 条)", isExpanded: $showCollins) {
                                VStack(alignment: .leading, spacing: 10) {
                                    ForEach(res.collinsSenses, id: \.index) { cs in
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text("\(cs.index). [\(cs.pos)] \(cs.explanation)")
                                                .font(.subheadline)
                                            ForEach(cs.examples, id: \.self) { ex in
                                                Text("• \(ex.english)\n  \(ex.chinese)")
                                                    .font(.caption)
                                                    .foregroundStyle(.secondary)
                                            }
                                        }
                                    }
                                }
                                .padding(.top, 6)
                            }
                            .font(.subheadline.weight(.semibold))
                        }

                        // Common Phrases
                        if !res.phrases.isEmpty {
                            Divider()
                            VStack(alignment: .leading, spacing: 6) {
                                Text("常用短语搭配")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(.secondary)
                                ForEach(res.phrases.prefix(8), id: \.self) { p in
                                    HStack(alignment: .top) {
                                        Text(p.phrase).font(.footnote.weight(.semibold))
                                        Spacer()
                                        Text(p.meaning).font(.footnote).foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }

                        // Bilingual Examples
                        if !res.examples.isEmpty {
                            Divider()
                            VStack(alignment: .leading, spacing: 6) {
                                Text("权威双语例句")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(.secondary)
                                ForEach(res.examples, id: \.self) { ex in
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(ex.english).font(.footnote)
                                        Text(ex.chinese).font(.caption).foregroundStyle(.secondary)
                                    }
                                }
                            }
                        }
                    } else if isLoading {
                        HStack(spacing: 10) {
                            ProgressView()
                            Text("正在查询详尽词典...")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 32)
                    }
                }
                .padding(18)
            }
            .navigationTitle("词典详解")
            .navigationBarTitleDisplayMode(.inline)
        }
        .task(id: request.id) {
            isLoading = true
            // Fast offline preview first, then full enrichment
            if let fastOffline = await OfflineDictionary.shared.lookup(request.query) {
                result = fastOffline
            }
            if store.settings.autoPronounce && !request.query.contains(" ") {
                SpeechManager.shared.speakWord(request.query, settings: store.settings)
            }
            let full = await store.lookupWordOrPhrase(request.query, sentence: request.sentence)
            result = full
            isLoading = false
        }
    }
}

private struct BadgePill: View {
    let text: String
    let color: Color
    var body: some View {
        Text(text)
            .font(.system(size: 11, weight: .semibold))
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(color.opacity(0.14))
            .foregroundStyle(color)
            .clipShape(Capsule())
    }
}

// MARK: - Reader Typography & Page-Turn Quick Settings Sheet

private struct ReaderQuickSettingsSheet: View {
    @EnvironmentObject private var store: AppStore
    @Binding var isAutoTurning: Bool
    let onLiveChanged: (AppSettings) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("翻页动画模式 (5 种)") {
                    Picker("翻页模式", selection: Binding(
                        get: { store.settings.pageTurnMode },
                        set: { mode in
                            store.updateSettings { $0.pageTurnMode = mode }
                            onLiveChanged(store.settings)
                        }
                    )) {
                        ForEach(PageTurnMode.allCases) { mode in
                            Text(mode.label).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section("护眼阅读主题与字体") {
                    HStack(spacing: 10) {
                        ForEach(ReaderTheme.allCases) { theme in
                            Button {
                                store.updateSettings { $0.readerTheme = theme }
                                onLiveChanged(store.settings)
                            } label: {
                                Text(theme.label)
                                    .font(.caption.weight(.semibold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(Color(hex: theme.bgHex))
                                    .foregroundStyle(Color(hex: theme.textHex))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(store.settings.readerTheme == theme ? Color.accentColor : Color.gray.opacity(0.3), lineWidth: store.settings.readerTheme == theme ? 2 : 1)
                                    )
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    Picker("正文字体", selection: Binding(
                        get: { store.settings.fontFamily },
                        set: { f in
                            store.updateSettings { $0.fontFamily = f }
                            onLiveChanged(store.settings)
                        }
                    )) {
                        ForEach(ReaderFont.allCases) { f in
                            Text(f.label).tag(f)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section("字号 / 行距 / 页边距") {
                    Stepper("字号大小：\(store.settings.fontSizeSp) px", value: Binding(
                        get: { store.settings.fontSizeSp },
                        set: { v in
                            store.updateSettings { $0.fontSizeSp = v }
                            onLiveChanged(store.settings)
                        }
                    ), in: 14...32)

                    HStack {
                        Text("行距 \(String(format: "%.2f", store.settings.lineHeight))")
                        Slider(value: Binding(
                            get: { store.settings.lineHeight },
                            set: { v in
                                store.updateSettings { $0.lineHeight = v }
                                onLiveChanged(store.settings)
                            }
                        ), in: 1.35...2.20, step: 0.05)
                    }

                    Stepper("左右边距：\(store.settings.horizontalMarginDp) px", value: Binding(
                        get: { store.settings.horizontalMarginDp },
                        set: { v in
                            store.updateSettings { $0.horizontalMarginDp = v }
                            onLiveChanged(store.settings)
                        }
                    ), in: 12...36, step: 2)
                }

                Section("翻页辅助与查词控制") {
                    Toggle("音量键翻页", isOn: Binding(
                        get: { store.settings.volumeKeyPaging },
                        set: { v in store.updateSettings { $0.volumeKeyPaging = v } }
                    ))
                    Toggle("开启自动翻页", isOn: $isAutoTurning)
                    Stepper("自动翻页间隔：\(store.settings.autoTurnIntervalSec) 秒", value: Binding(
                        get: { store.settings.autoTurnIntervalSec },
                        set: { v in store.updateSettings { $0.autoTurnIntervalSec = v } }
                    ), in: 2...60, step: 2)
                    Toggle("双击单词查词", isOn: Binding(
                        get: { store.settings.tapToTranslate },
                        set: { v in
                            store.updateSettings { $0.tapToTranslate = v }
                            onLiveChanged(store.settings)
                        }
                    ))
                    Toggle("生词本分级高亮", isOn: Binding(
                        get: { store.settings.highlightVocab },
                        set: { v in store.updateSettings { $0.highlightVocab = v } }
                    ))
                }
            }
            .navigationTitle("阅读排版设置")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

// MARK: - WKWebView Controller & SwiftUI Bridge

@MainActor
final class ReaderWebController {
    weak var webView: WKWebView?

    func nextPage() { webView?.evaluateJavascriptSafe("window.nextPage && window.nextPage();") }
    func prevPage() { webView?.evaluateJavascriptSafe("window.prevPage && window.prevPage();") }
    func goToPage(_ idx: Int, animated: Bool) {
        webView?.evaluateJavascriptSafe("window.goToPage && window.goToPage(\(idx), \(animated ? "true" : "false"));")
    }
    func clearSelection() { webView?.evaluateJavascriptSafe("window.clearSelection && window.clearSelection();") }
    func setPopupOpen(_ open: Bool) {
        webView?.evaluateJavascriptSafe("window.__lingoPopupOpen = \(open ? "true" : "false"); if (!window.__lingoPopupOpen && window.clearTapHighlight) window.clearTapHighlight();")
    }
    func applyLiveSettings(_ s: AppSettings) {
        let t = s.readerTheme
        let js = "window.applyStyle && window.applyStyle('\(t.bgHex)','\(t.textHex)','\(t.secondaryHex)','\(t.accentHex)','\(t.quoteBgHex)',\(s.fontSizeSp),\(s.lineHeight),\"\(s.fontFamily.cssFamily)\",\(s.horizontalMarginDp),\(s.tapToTranslate ? "true" : "false"),'\(s.pageTurnMode.rawValue)');"
        webView?.evaluateJavascriptSafe(js)
    }
}

private extension WKWebView {
    func evaluateJavascriptSafe(_ script: String) {
        evaluateJavaScript(script, completionHandler: nil)
    }
}

private final class WeakScriptBridge: NSObject, WKScriptMessageHandler {
    weak var delegate: WKScriptMessageHandler?
    init(delegate: WKScriptMessageHandler) { self.delegate = delegate }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        delegate?.userContentController(userContentController, didReceive: message)
    }
}

private struct ReaderWebViewRepresentable: UIViewRepresentable {
    let controller: ReaderWebController
    let chapter: Chapter
    let chapterLoadToken: UUID
    let initialRatio: Double
    let assetsBaseURL: URL
    let settings: AppSettings
    let gradedHighlights: (due: [String], learning: [String])
    let onPageChanged: (Int, Int) -> Void
    let onWordTapped: (String, String) -> Void
    let onSelectionChanged: (String, String) -> Void
    let onSelectionCleared: () -> Void
    let onBlankTapped: () -> Void
    let onNextChapter: () -> Void
    let onPrevChapter: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.userContentController.add(WeakScriptBridge(delegate: context.coordinator), name: "lingoBridge")

        let wv = WKWebView(frame: .zero, configuration: config)
        wv.isOpaque = false
        wv.backgroundColor = .clear
        wv.scrollView.isScrollEnabled = false
        wv.scrollView.bounces = false
        wv.scrollView.contentInsetAdjustmentBehavior = .never
        controller.webView = wv
        return wv
    }

    func updateUIView(_ wv: WKWebView, context: Context) {
        context.coordinator.parent = self
        if context.coordinator.loadedToken != chapterLoadToken {
            context.coordinator.loadedToken = chapterLoadToken
            let html = ReaderHtmlBuilder.buildDocument(
                chapterBodyHtml: chapter.htmlContent,
                settings: settings,
                dueWords: gradedHighlights.due,
                learningWords: gradedHighlights.learning,
                initialPageRatio: initialRatio
            )
            let htmlURL = assetsBaseURL.deletingLastPathComponent().appendingPathComponent("render.html")
            try? html.data(using: .utf8)?.write(to: htmlURL, options: .atomic)
            wv.loadFileURL(htmlURL, allowingReadAccessTo: assetsBaseURL.deletingLastPathComponent())
        }
    }

    final class Coordinator: NSObject, WKScriptMessageHandler {
        var parent: ReaderWebViewRepresentable
        var loadedToken: UUID?

        init(parent: ReaderWebViewRepresentable) { self.parent = parent }

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard let body = message.body as? [String: Any],
                  let type = body["type"] as? String else { return }
            switch type {
            case "pageChanged":
                let cur = (body["current"] as? Int) ?? 0
                let total = (body["total"] as? Int) ?? 1
                parent.onPageChanged(cur, total)
            case "wordTapped":
                parent.onWordTapped((body["word"] as? String) ?? "", (body["sentence"] as? String) ?? "")
            case "selectionChanged":
                parent.onSelectionChanged((body["text"] as? String) ?? "", (body["sentence"] as? String) ?? "")
            case "selectionCleared":
                parent.onSelectionCleared()
            case "blankTapped":
                parent.onBlankTapped()
            case "nextChapter":
                parent.onNextChapter()
            case "prevChapter":
                parent.onPrevChapter()
            default:
                break
            }
        }
    }
}

// MARK: - iOS Hardware Volume-Key Paging Helper

private struct VolumeButtonPagingView: UIViewRepresentable {
    let onVolumeDown: () -> Void
    let onVolumeUp: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onDown: onVolumeDown, onUp: onVolumeUp) }

    func makeUIView(context: Context) -> MPVolumeView {
        let view = MPVolumeView(frame: CGRect(x: -1000, y: -1000, width: 1, height: 1))
        context.coordinator.attach(to: view)
        return view
    }

    func updateUIView(_ uiView: MPVolumeView, context: Context) {
        context.coordinator.onDown = onVolumeDown
        context.coordinator.onUp = onVolumeUp
    }

    static func dismantleUIView(_ uiView: MPVolumeView, coordinator: Coordinator) {
        coordinator.detach()
    }

    final class Coordinator: NSObject {
        var onDown: () -> Void
        var onUp: () -> Void
        private var observation: NSKeyValueObservation?
        private var isResetting = false
        private weak var slider: UISlider?

        init(onDown: @escaping () -> Void, onUp: @escaping () -> Void) {
            self.onDown = onDown
            self.onUp = onUp
        }

        func attach(to volumeView: MPVolumeView) {
            let session = AVAudioSession.sharedInstance()
            try? session.setCategory(.ambient, options: [.mixWithOthers])
            try? session.setActive(true)
            slider = volumeView.subviews.compactMap { $0 as? UISlider }.first

            observation = session.observe(\.outputVolume, options: [.old, .new]) { [weak self] _, change in
                guard let self = self, !self.isResetting,
                      let oldVal = change.oldValue, let newVal = change.newValue,
                      abs(newVal - oldVal) > 0.001 else { return }

                DispatchQueue.main.async {
                    if newVal < oldVal {
                        self.onDown()
                    } else {
                        self.onUp()
                    }
                    if newVal <= 0.08 || newVal >= 0.92 {
                        self.isResetting = true
                        self.slider?.setValue(0.5, animated: false)
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                            self.isResetting = false
                        }
                    }
                }
            }
        }

        func detach() {
            observation?.invalidate()
            observation = nil
        }
    }
}
