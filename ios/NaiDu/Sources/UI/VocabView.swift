import SwiftUI

enum VocabFilter: String, CaseIterable, Identifiable {
    case all = "全部"
    case due = "待复习"
    case starred = "星标"
    case stubborn = "顽固词"
    case learning = "学习中"
    case mastered = "已掌握"

    var id: String { rawValue }
}

enum VocabSort: String, CaseIterable, Identifiable {
    case recent = "最新添加"
    case dueFirst = "优先复习"
    case encounters = "阅读高频"
    case alphabetical = "A–Z 字母"

    var id: String { rawValue }
}

struct VocabView: View {
    @EnvironmentObject private var store: AppStore

    @State private var searchText: String = ""
    @State private var filter: VocabFilter = .all
    @State private var sort: VocabSort = .recent
    @State private var redFilmMode: Bool = false
    @State private var revealedIds: Set<String> = []

    @State private var isBatchMode: Bool = false
    @State private var selectedIds: Set<String> = []

    @State private var showReviewSheet: Bool = false
    @State private var showRadioSheet: Bool = false
    @State private var showStatsSheet: Bool = false
    @State private var showBatchImportSheet: Bool = false

    private var filteredWords: [VocabWord] {
        let q = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let base = store.words.filter { w in
            let matchesQuery = q.isEmpty
                || w.word.lowercased().contains(q)
                || w.meaning.lowercased().contains(q)
                || w.note.lowercased().contains(q)
            guard matchesQuery else { return false }

            switch filter {
            case .all: return true
            case .due: return w.isDue
            case .starred: return w.isStarred
            case .stubborn: return w.isStubborn
            case .learning: return !w.isMastered && w.repetitions > 0
            case .mastered: return w.isMastered
            }
        }

        switch sort {
        case .recent:
            return base.sorted { $0.createdAt > $1.createdAt }
        case .dueFirst:
            return base.sorted { $0.nextReviewAt < $1.nextReviewAt }
        case .encounters:
            return base.sorted { $0.encounterCount > $1.encounterCount }
        case .alphabetical:
            return base.sorted { $0.word.localizedCaseInsensitiveCompare($1.word) == .orderedAscending }
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Action Dashboard Row (5-Mode Review / Audio Radio / 28-Day Stats / Red-Film)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        Button {
                            showReviewSheet = true
                        } label: {
                            Label("开始复习 (\(store.dueCount))", systemImage: "bolt.heart.fill")
                                .font(.footnote.weight(.semibold))
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(store.words.isEmpty)

                        Button {
                            showRadioSheet = true
                        } label: {
                            Label("随身磨耳朵", systemImage: "headphones")
                                .font(.footnote.weight(.medium))
                        }
                        .buttonStyle(.bordered)
                        .disabled(store.words.isEmpty)

                        Button {
                            showStatsSheet = true
                        } label: {
                            Label("记忆统计", systemImage: "chart.bar.xaxis")
                                .font(.footnote.weight(.medium))
                        }
                        .buttonStyle(.bordered)

                        Button {
                            redFilmMode.toggle()
                            revealedIds.removeAll()
                        } label: {
                            Label(redFilmMode ? "退出红膜" : "红膜遮罩", systemImage: redFilmMode ? "eye.fill" : "eye.slash.fill")
                                .font(.footnote.weight(.medium))
                        }
                        .buttonStyle(.bordered)
                        .tint(redFilmMode ? .red : .primary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                }

                // Filter Chips
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(VocabFilter.allCases) { f in
                            Button {
                                filter = f
                            } label: {
                                Text(f.rawValue)
                                    .font(.caption.weight(filter == f ? .bold : .regular))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(filter == f ? Color.accentColor : Color(.secondarySystemBackground))
                                    .foregroundStyle(filter == f ? .white : .primary)
                                    .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)
                }

                // Batch Action Toolbar
                if isBatchMode {
                    HStack(spacing: 12) {
                        Button(selectedIds.count == filteredWords.count ? "取消全选" : "全选") {
                            if selectedIds.count == filteredWords.count {
                                selectedIds.removeAll()
                            } else {
                                selectedIds = Set(filteredWords.map(\.id))
                            }
                        }
                        .font(.caption.weight(.semibold))

                        Spacer()

                        Button("标为掌握") {
                            for id in selectedIds { store.toggleMastered(wordId: id) }
                            selectedIds.removeAll()
                            isBatchMode = false
                        }
                        .font(.caption)
                        .disabled(selectedIds.isEmpty)

                        Button("删除 (\(selectedIds.count))", role: .destructive) {
                            store.deleteWords(ids: selectedIds)
                            selectedIds.removeAll()
                            isBatchMode = false
                        }
                        .font(.caption.weight(.bold))
                        .disabled(selectedIds.isEmpty)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(Color(.secondarySystemBackground))
                }

                if filteredWords.isEmpty {
                    VStack(spacing: 12) {
                        Spacer()
                        Image(systemName: "text.book.closed")
                            .font(.system(size: 48))
                            .foregroundStyle(.secondary)
                        Text(store.words.isEmpty ? "生词本还没有单词" : "当前筛选下没有单词")
                            .font(.headline)
                        Text("阅读时双击任意单词点「加入生词本」，或点击右上角菜单批量导入词表")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 36)
                        Spacer()
                    }
                } else {
                    List {
                        ForEach(filteredWords) { item in
                            if isBatchMode {
                                Button {
                                    if selectedIds.contains(item.id) {
                                        selectedIds.remove(item.id)
                                    } else {
                                        selectedIds.insert(item.id)
                                    }
                                } label: {
                                    HStack(spacing: 12) {
                                        Image(systemName: selectedIds.contains(item.id) ? "checkmark.circle.fill" : "circle")
                                            .foregroundStyle(selectedIds.contains(item.id) ? Color.accentColor : .secondary)
                                        VocabWordRow(
                                            item: item,
                                            isMasked: false,
                                            onToggleReveal: {},
                                            onSpeak: { SpeechManager.shared.speakWord(item.word, settings: store.settings) },
                                            onToggleStar: { store.toggleStar(wordId: item.id) }
                                        )
                                    }
                                }
                                .buttonStyle(.plain)
                            } else {
                                NavigationLink {
                                    WordDetailView(wordId: item.id)
                                } label: {
                                    VocabWordRow(
                                        item: item,
                                        isMasked: redFilmMode && !revealedIds.contains(item.id),
                                        onToggleReveal: {
                                            if revealedIds.contains(item.id) {
                                                revealedIds.remove(item.id)
                                            } else {
                                                revealedIds.insert(item.id)
                                            }
                                        },
                                        onSpeak: { SpeechManager.shared.speakWord(item.word, settings: store.settings) },
                                        onToggleStar: { store.toggleStar(wordId: item.id) }
                                    )
                                }
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        store.deleteWords(ids: [item.id])
                                    } label: {
                                        Label("删除", systemImage: "trash")
                                    }
                                    Button {
                                        store.toggleMastered(wordId: item.id)
                                    } label: {
                                        Label(item.isMastered ? "恢复学习" : "已掌握", systemImage: "checkmark.seal")
                                    }
                                    .tint(.green)
                                }
                            }
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("单词本 (\(store.words.count))")
            .searchable(text: $searchText, prompt: "搜索单词、释义或个人笔记")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Picker("排序方式", selection: $sort) {
                            ForEach(VocabSort.allCases) { s in
                                Text(s.rawValue).tag(s)
                            }
                        }
                        Divider()
                        Button {
                            isBatchMode.toggle()
                            selectedIds.removeAll()
                        } label: {
                            Label(isBatchMode ? "退出批量管理" : "批量选择管理", systemImage: "checklist")
                        }
                        Button {
                            showBatchImportSheet = true
                        } label: {
                            Label("批量导入英文词表", systemImage: "square.and.arrow.down")
                        }
                        if !store.words.isEmpty {
                            ShareLink(
                                item: store.exportVocabAsCSV(),
                                preview: SharePreview("奶读单词本.csv")
                            ) {
                                Label("导出 CSV 表格", systemImage: "tablecells")
                            }
                            ShareLink(
                                item: store.exportVocabAsMarkdown(),
                                preview: SharePreview("奶读单词本.md")
                            ) {
                                Label("导出 Markdown 笔记", systemImage: "doc.richtext")
                            }
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
            .sheet(isPresented: $showReviewSheet) {
                ReviewView()
                    .environmentObject(store)
            }
            .sheet(isPresented: $showRadioSheet) {
                AudioRadioView()
                    .environmentObject(store)
            }
            .sheet(isPresented: $showStatsSheet) {
                VocabStatsSheet()
                    .environmentObject(store)
            }
            .sheet(isPresented: $showBatchImportSheet) {
                BatchWordImportSheet()
                    .environmentObject(store)
            }
        }
    }
}

private struct VocabWordRow: View {
    let item: VocabWord
    let isMasked: Bool
    let onToggleReveal: () -> Void
    let onSpeak: () -> Void
    let onToggleStar: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Text(item.word)
                    .font(.headline)
                if !item.phoneticUs.isEmpty {
                    Text("/\(item.phoneticUs)/")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Button(action: onSpeak) {
                    Image(systemName: "speaker.wave.2.fill")
                        .font(.caption)
                        .foregroundStyle(Color.accentColor)
                }
                .buttonStyle(.plain)

                Spacer()

                Text(item.masteryLabel)
                    .font(.system(size: 10, weight: .semibold))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(item.isMastered ? Color.green.opacity(0.15) : Color.orange.opacity(0.15))
                    .foregroundStyle(item.isMastered ? .green : .orange)
                    .clipShape(Capsule())

                Button(action: onToggleStar) {
                    Image(systemName: item.isStarred ? "star.fill" : "star")
                        .font(.footnote)
                        .foregroundStyle(item.isStarred ? .yellow : .secondary)
                }
                .buttonStyle(.plain)
            }

            if isMasked {
                Button(action: onToggleReveal) {
                    Text("点击揭开红膜自测释义")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Color.red.opacity(0.78))
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                }
                .buttonStyle(.plain)
            } else {
                Text(item.meaning)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }

            if item.encounterCount > 1 || !item.contexts.isEmpty {
                HStack(spacing: 8) {
                    if item.encounterCount > 1 {
                        Text("偶遇 \(item.encounterCount) 次")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    if let ctx = item.contexts.first, !ctx.bookTitle.isEmpty {
                        Text("出自《\(ctx.bookTitle)》")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }
}

private struct BatchWordImportSheet: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var rawInput: String = ""
    @State private var isImporting: Bool = false
    @State private var importedSummary: String?

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text("粘贴英文单词列表（支持换行、空格或逗号分隔，自动匹配离线词库补全音标与中文释义）：")
                    .font(.footnote)
                    .foregroundStyle(.secondary)

                TextEditor(text: $rawInput)
                    .frame(minHeight: 180)
                    .padding(6)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.gray.opacity(0.3)))

                if let msg = importedSummary {
                    Text(msg)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.green)
                }

                Button {
                    isImporting = true
                    Task {
                        let count = await store.batchImportWords(rawText: rawInput)
                        importedSummary = "成功匹配并导入 \(count) 个新单词！"
                        isImporting = false
                    }
                } label: {
                    HStack {
                        if isImporting { ProgressView().tint(.white) }
                        Text(isImporting ? "正在匹配词库..." : "一键导入到生词本")
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                }
                .buttonStyle(.borderedProminent)
                .disabled(rawInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isImporting)

                Spacer()
            }
            .padding(16)
            .navigationTitle("批量导入词表")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { dismiss() }
                }
            }
        }
    }
}
