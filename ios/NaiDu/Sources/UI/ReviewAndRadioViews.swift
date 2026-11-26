import Combine
import SwiftUI

// MARK: - 1. Word Detail View (Multi-Context Jump-Back + Rich Dictionary + Note)

struct WordDetailView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let wordId: String

    @State private var richResult: TranslationResult?
    @State private var noteDraft: String = ""

    private var item: VocabWord? {
        store.words.first { $0.id == wordId }
    }

    var body: some View {
        Group {
            if let w = item {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 6) {
                                Text(w.word)
                                    .font(.largeTitle.bold())
                                HStack(spacing: 10) {
                                    Button {
                                        var s = store.settings; s.ttsAccent = .us
                                        SpeechManager.shared.speakWord(w.word, settings: s)
                                    } label: {
                                        Label(w.phoneticUs.isEmpty ? "美音" : "美 /\(w.phoneticUs)/", systemImage: "speaker.wave.2.fill")
                                            .font(.caption)
                                    }
                                    .buttonStyle(.bordered)

                                    Button {
                                        var s = store.settings; s.ttsAccent = .uk
                                        SpeechManager.shared.speakWord(w.word, settings: s)
                                    } label: {
                                        Label(w.phoneticUk.isEmpty ? "英音" : "英 /\(w.phoneticUk)/", systemImage: "speaker.wave.2")
                                            .font(.caption)
                                    }
                                    .buttonStyle(.bordered)
                                }
                            }
                            Spacer()
                            Button {
                                store.toggleStar(wordId: w.id)
                            } label: {
                                Image(systemName: w.isStarred ? "star.fill" : "star")
                                    .font(.title2)
                                    .foregroundStyle(w.isStarred ? .yellow : .secondary)
                            }
                        }

                        // SM-2 Memory Metrics
                        HStack(spacing: 12) {
                            StatMiniPill(title: "状态", value: w.masteryLabel)
                            StatMiniPill(title: "阅读偶遇", value: "\(w.encounterCount) 次")
                            StatMiniPill(title: "复习间隔", value: "\(w.intervalDays) 天")
                            StatMiniPill(title: "遗忘次数", value: "\(w.lapseCount)")
                        }

                        // Meaning & Enriched Dictionary
                        VStack(alignment: .leading, spacing: 8) {
                            Text("中文释义")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(.secondary)
                            Text(richResult?.chineseMeaning.isEmpty == false ? richResult!.chineseMeaning : w.meaning)
                                .font(.body)
                                .textSelection(.enabled)
                        }

                        if let rich = richResult, !rich.collinsSenses.isEmpty {
                            Divider()
                            VStack(alignment: .leading, spacing: 8) {
                                Text("柯林斯英汉双解精选")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(.secondary)
                                ForEach(rich.collinsSenses.prefix(5), id: \.index) { cs in
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("\(cs.index). [\(cs.pos)] \(cs.explanation)")
                                            .font(.subheadline)
                                        if let ex = cs.examples.first {
                                            Text("例：\(ex.english) \(ex.chinese)")
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                                }
                            }
                        }

                        // Personal Note
                        Divider()
                        VStack(alignment: .leading, spacing: 8) {
                            Text("个人助记笔记")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(.secondary)
                            TextField("输入词根拆解、联想记忆或用法心得...", text: $noteDraft, axis: .vertical)
                                .textFieldStyle(.roundedBorder)
                                .lineLimit(2...5)
                                .onSubmit {
                                    store.updateNote(wordId: w.id, note: noteDraft)
                                }
                            Button("保存笔记") {
                                store.updateNote(wordId: w.id, note: noteDraft)
                            }
                            .font(.caption.weight(.semibold))
                            .buttonStyle(.bordered)
                        }

                        // Captured Reading Contexts with Jump-Back
                        if !w.contexts.isEmpty {
                            Divider()
                            VStack(alignment: .leading, spacing: 10) {
                                Text("原书阅读语境 (\(w.contexts.count) 处)")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(.secondary)
                                ForEach(w.contexts) { ctx in
                                    VStack(alignment: .leading, spacing: 6) {
                                        Text(ctx.sentence)
                                            .font(.subheadline)
                                        HStack {
                                            Text("《\(ctx.bookTitle)》· \(ctx.chapterTitle)")
                                                .font(.caption2)
                                                .foregroundStyle(.secondary)
                                            Spacer()
                                            if !ctx.bookId.isEmpty, store.books.contains(where: { $0.id == ctx.bookId }) {
                                                Button("跳回原书") {
                                                    store.jumpTargetBook = (ctx.bookId, ctx.chapterIndex)
                                                    dismiss()
                                                }
                                                .font(.caption2.weight(.bold))
                                            }
                                        }
                                    }
                                    .padding(10)
                                    .background(Color(.secondarySystemBackground))
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                                }
                            }
                        }

                        Divider()
                        HStack {
                            Button(w.isMastered ? "移回学习列表" : "标为已掌握") {
                                store.toggleMastered(wordId: w.id)
                            }
                            .buttonStyle(.bordered)

                            Button("重置记忆进度") {
                                store.resetWordProgress(wordId: w.id)
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    .padding(16)
                }
                .navigationTitle(w.word)
                .navigationBarTitleDisplayMode(.inline)
                .onAppear {
                    noteDraft = w.note
                }
                .task(id: w.word) {
                    richResult = await store.lookupWordOrPhrase(w.word, sentence: w.contexts.first?.sentence ?? "")
                }
            } else {
                Text("单词已移除")
            }
        }
    }
}

private struct StatMiniPill: View {
    let title: String
    let value: String
    var body: some View {
        VStack(spacing: 2) {
            Text(value).font(.footnote.weight(.bold))
            Text(title).font(.caption2).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - 2. Five-Mode Active Review Screen

enum ActiveReviewMode: String, CaseIterable, Identifiable {
    case flashcard = "记忆卡片"
    case quiz4 = "四选一"
    case spelling = "拼写默写"
    case listening = "听音辨义"
    case cloze = "原句填空"

    var id: String { rawValue }
}

struct ReviewView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss

    @State private var mode: ActiveReviewMode = .flashcard
    @State private var queue: [VocabWord] = []
    @State private var index: Int = 0
    @State private var isFlipped: Bool = false
    @State private var quizOptions: [String] = []
    @State private var selectedOption: String?
    @State private var spellingInput: String = ""
    @State private var spellingChecked: Bool = false

    private var currentWord: VocabWord? {
        guard queue.indices.contains(index) else { return nil }
        return queue[index]
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Picker("复习模式", selection: $mode) {
                    ForEach(ActiveReviewMode.allCases) { m in
                        Text(m.rawValue).tag(m)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .onChange(of: mode) { _ in
                    prepareCurrentStep()
                }

                if let word = currentWord {
                    Text("进度 \(index + 1) / \(queue.count)")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    VStack(spacing: 18) {
                        switch mode {
                        case .flashcard:
                            flashcardContent(word)
                        case .quiz4:
                            quizContent(word, hideWord: false)
                        case .listening:
                            quizContent(word, hideWord: true)
                        case .spelling:
                            spellingContent(word)
                        case .cloze:
                            clozeContent(word)
                        }
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                } else {
                    VStack(spacing: 14) {
                        Spacer()
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: 56))
                            .foregroundStyle(.green)
                        Text("本轮复习全部完成！")
                            .font(.title3.bold())
                        Button("返回单词本") { dismiss() }
                            .buttonStyle(.borderedProminent)
                        Spacer()
                    }
                }
            }
            .padding(.top, 8)
            .navigationTitle("主动记忆训练")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("关闭") { dismiss() }
                }
            }
            .onAppear {
                let due = store.words.filter { $0.isDue }
                queue = due.isEmpty ? Array(store.words.filter { !$0.isMastered }.prefix(25)) : due
                if queue.isEmpty { queue = Array(store.words.prefix(20)) }
                index = 0
                prepareCurrentStep()
            }
        }
    }

    @ViewBuilder
    private func flashcardContent(_ word: VocabWord) -> some View {
        Spacer()
        Text(word.word)
            .font(.system(size: 32, weight: .bold))
        Button {
            SpeechManager.shared.speakWord(word.word, settings: store.settings)
        } label: {
            Label(word.phoneticUs.isEmpty ? "播放发音" : "/\(word.phoneticUs)/", systemImage: "speaker.wave.2.fill")
        }
        .buttonStyle(.bordered)

        if isFlipped {
            Divider()
            Text(word.meaning)
                .font(.body)
                .multilineTextAlignment(.center)
            if let ctx = word.contexts.first {
                Text(ctx.sentence)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            Spacer()
            HStack(spacing: 10) {
                gradeButton("忘了", quality: 1, color: .red)
                gradeButton("模糊", quality: 3, color: .orange)
                gradeButton("记得", quality: 4, color: .blue)
                gradeButton("秒懂", quality: 5, color: .green)
            }
        } else {
            Spacer()
            Button {
                isFlipped = true
            } label: {
                Text("显示释义与原句")
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
            }
            .buttonStyle(.borderedProminent)
        }
    }

    @ViewBuilder
    private func quizContent(_ word: VocabWord, hideWord: Bool) -> some View {
        Spacer()
        if hideWord {
            Button {
                SpeechManager.shared.speakWord(word.word, settings: store.settings)
            } label: {
                VStack(spacing: 8) {
                    Image(systemName: "speaker.wave.3.fill")
                        .font(.system(size: 40))
                    Text("点击重播读音，选择正确释义")
                        .font(.footnote)
                }
                .padding(20)
            }
            .buttonStyle(.bordered)
            if selectedOption != nil {
                Text(word.word).font(.title2.bold())
            }
        } else {
            Text(word.word).font(.system(size: 30, weight: .bold))
        }
        Spacer()
        VStack(spacing: 10) {
            ForEach(quizOptions, id: \.self) { opt in
                let isCorrect = (opt == word.meaning)
                let chosen = (selectedOption == opt)
                Button {
                    guard selectedOption == nil else { return }
                    selectedOption = opt
                    let q = isCorrect ? 4 : 1
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.75) {
                        advanceAfterGrade(quality: q)
                    }
                } label: {
                    Text(opt.replacingOccurrences(of: "\n", with: "；"))
                        .font(.footnote)
                        .lineLimit(2)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(
                            selectedOption == nil
                                ? Color(.systemBackground)
                                : (isCorrect ? Color.green.opacity(0.22) : (chosen ? Color.red.opacity(0.22) : Color(.systemBackground)))
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
            }
        }
    }

    @ViewBuilder
    private func spellingContent(_ word: VocabWord) -> some View {
        Spacer()
        Text(word.meaning)
            .font(.headline)
            .multilineTextAlignment(.center)
        Button {
            SpeechManager.shared.speakWord(word.word, settings: store.settings)
        } label: {
            Label("听发音提示", systemImage: "speaker.wave.2.fill")
                .font(.caption)
        }
        .buttonStyle(.bordered)

        TextField("拼写英文单词...", text: $spellingInput)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .textFieldStyle(.roundedBorder)
            .font(.title3.monospaced())
            .multilineTextAlignment(.center)

        if spellingChecked {
            let ok = spellingInput.trimmingCharacters(in: .whitespacesAndNewlines).caseInsensitiveCompare(word.word) == .orderedSame
            Text(ok ? "拼写完全正确！" : "正确拼写：\(word.word)")
                .font(.subheadline.bold())
                .foregroundStyle(ok ? .green : .red)
            Button("下一词") {
                advanceAfterGrade(quality: ok ? 5 : 1)
            }
            .buttonStyle(.borderedProminent)
        } else {
            Button("提交核对") {
                spellingChecked = true
            }
            .buttonStyle(.borderedProminent)
        }
        Spacer()
    }

    @ViewBuilder
    private func clozeContent(_ word: VocabWord) -> some View {
        Spacer()
        let rawSentence = word.contexts.first?.sentence ?? "Please remember the word \(word.word) in context."
        let masked = rawSentence.replacingOccurrences(of: word.word, with: "______", options: .caseInsensitive)
        Text(masked)
            .font(.body)
            .multilineTextAlignment(.center)
        Text("提示释义：\(word.meaning)")
            .font(.footnote)
            .foregroundStyle(.secondary)
            .lineLimit(3)

        if isFlipped {
            Text("答案：\(word.word)")
                .font(.title2.bold())
                .foregroundStyle(.green)
            HStack(spacing: 12) {
                gradeButton("没想出", quality: 2, color: .orange)
                gradeButton("答对了", quality: 5, color: .green)
            }
        } else {
            Button("揭晓答案") {
                isFlipped = true
                SpeechManager.shared.speakWord(word.word, settings: store.settings)
            }
            .buttonStyle(.borderedProminent)
        }
        Spacer()
    }

    private func gradeButton(_ label: String, quality: Int, color: Color) -> some View {
        Button {
            advanceAfterGrade(quality: quality)
        } label: {
            Text(label)
                .font(.footnote.weight(.bold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
        }
        .buttonStyle(.borderedProminent)
        .tint(color)
    }

    private func advanceAfterGrade(quality: Int) {
        if let w = currentWord {
            store.recordReview(wordId: w.id, quality: quality)
        }
        index += 1
        prepareCurrentStep()
    }

    private func prepareCurrentStep() {
        isFlipped = false
        selectedOption = nil
        spellingInput = ""
        spellingChecked = false
        guard let w = currentWord else { return }

        var distractors = store.words.filter { $0.id != w.id }.shuffled().prefix(3).map(\.meaning)
        while distractors.count < 3 {
            distractors.append(["n. 概念；印象", "v. 维持；断言", "adj. 微妙的；敏锐的"][distractors.count % 3])
        }
        quizOptions = ([w.meaning] + distractors).shuffled()

        if mode == .flashcard || mode == .listening {
            SpeechManager.shared.speakWord(w.word, settings: store.settings)
        }
    }
}

// MARK: - 3. Audio Radio ("随身磨耳朵" Background Player)

struct AudioRadioView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss

    @State private var playlist: [VocabWord] = []
    @State private var currentIdx: Int = 0
    @State private var isPlaying: Bool = false
    @State private var stepPhase: Int = 0
    private let ticker = Timer.publish(every: 2.6, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationStack {
            VStack(spacing: 22) {
                Spacer()
                Image(systemName: isPlaying ? "waveform.circle.fill" : "headphones.circle.fill")
                    .font(.system(size: 72))
                    .foregroundStyle(Color.accentColor)

                if playlist.indices.contains(currentIdx) {
                    let w = playlist[currentIdx]
                    Text(w.word)
                        .font(.largeTitle.bold())
                    if !w.phoneticUs.isEmpty {
                        Text("/\(w.phoneticUs)/")
                            .foregroundStyle(.secondary)
                    }
                    Text(w.meaning)
                        .font(.body)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 28)
                    Text("第 \(currentIdx + 1) / \(playlist.count) 词 · 支持锁屏后台连播")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                HStack(spacing: 28) {
                    Button {
                        guard !playlist.isEmpty else { return }
                        currentIdx = (currentIdx - 1 + playlist.count) % playlist.count
                        stepPhase = 0
                        playCurrentPhase()
                    } label: {
                        Image(systemName: "backward.fill").font(.title2)
                    }

                    Button {
                        isPlaying.toggle()
                        if isPlaying { playCurrentPhase() } else { SpeechManager.shared.stop() }
                    } label: {
                        Image(systemName: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 56))
                    }

                    Button {
                        guard !playlist.isEmpty else { return }
                        currentIdx = (currentIdx + 1) % playlist.count
                        stepPhase = 0
                        playCurrentPhase()
                    } label: {
                        Image(systemName: "forward.fill").font(.title2)
                    }
                }
                Spacer()
            }
            .navigationTitle("随身磨耳朵电台")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("关闭") {
                        SpeechManager.shared.stop()
                        dismiss()
                    }
                }
            }
            .onAppear {
                playlist = store.words.filter { !$0.isMastered }
                if playlist.isEmpty { playlist = store.words }
            }
            .onDisappear {
                isPlaying = false
                SpeechManager.shared.stop()
            }
            .onReceive(ticker) { _ in
                guard isPlaying, !playlist.isEmpty else { return }
                stepPhase += 1
                if stepPhase > 2 {
                    stepPhase = 0
                    currentIdx = (currentIdx + 1) % playlist.count
                }
                playCurrentPhase()
            }
        }
    }

    private func playCurrentPhase() {
        guard playlist.indices.contains(currentIdx) else { return }
        let w = playlist[currentIdx]
        switch stepPhase {
        case 0, 1:
            SpeechManager.shared.speakWord(w.word, settings: store.settings)
        default:
            SpeechManager.shared.speakChinese(w.meaning, speed: store.settings.ttsSpeed)
        }
    }
}

// MARK: - 4. 28-Day Heatmap & 7-Day Review Forecast Sheet

struct VocabStatsSheet: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss

    private var last28Days: [(key: String, count: Int)] {
        let cal = Calendar.current
        return (0..<28).reversed().map { offset in
            let d = cal.date(byAdding: .day, value: -offset, to: Date()) ?? Date()
            let key = AppStore.todayKey(for: d)
            let st = store.dailyStats[key]
            return (key, (st?.reviewedCount ?? 0) + (st?.newWordsAdded ?? 0))
        }
    }

    private var next7Days: [(label: String, count: Int)] {
        let cal = Calendar.current
        let fmt = DateFormatter()
        fmt.dateFormat = "MM/dd"
        let startOfToday = cal.startOfDay(for: Date())

        return (0..<7).map { offset in
            let dayStart = cal.date(byAdding: .day, value: offset, to: startOfToday) ?? startOfToday
            let dayEnd = cal.date(byAdding: .day, value: 1, to: dayStart) ?? dayStart
            let c = store.words.filter { w in
                guard !w.isMastered else { return false }
                if offset == 0 { return w.nextReviewAt < dayEnd }
                return w.nextReviewAt >= dayStart && w.nextReviewAt < dayEnd
            }.count
            return (offset == 0 ? "今日" : fmt.string(from: dayStart), c)
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    // Mastery Summary
                    HStack(spacing: 12) {
                        StatMiniPill(title: "总词汇", value: "\(store.words.count)")
                        StatMiniPill(title: "今日待复习", value: "\(store.dueCount)")
                        StatMiniPill(title: "已掌握", value: "\(store.words.filter(\.isMastered).count)")
                    }

                    // 28-Day Check-In Heatmap
                    VStack(alignment: .leading, spacing: 10) {
                        Text("近 28 天学习热力图")
                            .font(.subheadline.bold())
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 7), spacing: 6) {
                            ForEach(last28Days, id: \.key) { day in
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(heatColor(day.count))
                                    .frame(height: 28)
                                    .overlay(
                                        Text(day.count > 0 ? "\(day.count)" : "")
                                            .font(.system(size: 9, weight: .bold))
                                            .foregroundStyle(day.count >= 15 ? .white : .secondary)
                                    )
                            }
                        }
                    }
                    .padding(14)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    // 7-Day Review Forecast
                    VStack(alignment: .leading, spacing: 10) {
                        Text("未来 7 天复习负载预测")
                            .font(.subheadline.bold())
                        let maxVal = max(1, next7Days.map(\.count).max() ?? 1)
                        HStack(alignment: .bottom, spacing: 10) {
                            ForEach(next7Days, id: \.label) { item in
                                VStack(spacing: 4) {
                                    Text("\(item.count)")
                                        .font(.caption2.monospacedDigit())
                                    RoundedRectangle(cornerRadius: 6)
                                        .fill(Color.accentColor)
                                        .frame(height: max(8, CGFloat(item.count) / CGFloat(maxVal) * 90))
                                    Text(item.label)
                                        .font(.system(size: 10))
                                        .foregroundStyle(.secondary)
                                }
                                .frame(maxWidth: .infinity)
                            }
                        }
                        .frame(height: 130, alignment: .bottom)
                    }
                    .padding(14)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .padding(16)
            }
            .navigationTitle("学习记忆曲线")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { dismiss() }
                }
            }
        }
    }

    private func heatColor(_ count: Int) -> Color {
        if count == 0 { return Color.gray.opacity(0.16) }
        if count < 8 { return Color.green.opacity(0.35) }
        if count < 20 { return Color.green.opacity(0.65) }
        return Color.green
    }
}
