import SwiftUI
import UniformTypeIdentifiers
import UIKit

struct BookshelfView: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var renewalManager: AutoSignRenewalManager
    let onOpenBook: (Book) -> Void

    @State private var searchText: String = ""
    @State private var showFileImporter: Bool = false
    @State private var importError: String?
    @State private var bookPendingDelete: Book?

    private var filteredBooks: [Book] {
        let q = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return store.books }
        return store.books.filter {
            $0.title.lowercased().contains(q) || $0.author.lowercased().contains(q)
        }
    }

    private let columns = [
        GridItem(.adaptive(minimum: 108, maximum: 150), spacing: 16)
    ]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // SideStore Auto-Renewal Live Banner (visible whenever sideloaded with 7-day profile)
                    if renewalManager.signatureInfo.expirationDate != nil {
                        SignatureQuickBanner(info: renewalManager.signatureInfo) {
                            renewalManager.openSideStoreForRefresh()
                        }
                        .padding(.horizontal, 16)
                    }

                    if filteredBooks.isEmpty {
                        VStack(spacing: 14) {
                            Image(systemName: "book.closed.circle.fill")
                                .font(.system(size: 54))
                                .foregroundStyle(.orange.opacity(0.85))
                            Text(searchText.isEmpty ? "书架还是空的" : "没有找到匹配的书籍")
                                .font(.headline)
                            Text("支持导入 TXT、Markdown、EPUB 英文原著，双击任意单词即可查词发音")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 32)
                            if searchText.isEmpty {
                                Button {
                                    showFileImporter = true
                                } label: {
                                    Label("导入本地书籍", systemImage: "plus.circle.fill")
                                        .padding(.horizontal, 18)
                                        .padding(.vertical, 10)
                                }
                                .buttonStyle(.borderedProminent)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 64)
                    } else {
                        LazyVGrid(columns: columns, spacing: 20) {
                            ForEach(filteredBooks) { book in
                                BookGridCard(
                                    book: book,
                                    coverURL: store.coverURL(for: book),
                                    onTap: { onOpenBook(book) },
                                    onDelete: { bookPendingDelete = book }
                                )
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 24)
                    }
                }
                .padding(.top, 8)
            }
            .navigationTitle("奶读")
            .searchable(text: $searchText, prompt: "搜索书名或作者")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showFileImporter = true
                    } label: {
                        Label("导入书籍", systemImage: "plus")
                    }
                }
            }
            .fileImporter(
                isPresented: $showFileImporter,
                allowedContentTypes: [
                    UTType(filenameExtension: "epub") ?? .data,
                    .plainText,
                    UTType(filenameExtension: "md") ?? .plainText,
                    UTType(filenameExtension: "markdown") ?? .plainText
                ],
                allowsMultipleSelection: true
            ) { result in
                switch result {
                case .success(let urls):
                    for u in urls {
                        do {
                            try store.importBookFromURL(u, securityScoped: true)
                        } catch {
                            importError = error.localizedDescription
                        }
                    }
                case .failure(let err):
                    importError = err.localizedDescription
                }
            }
            .confirmationDialog(
                "确认从书架移除这本书吗？",
                isPresented: Binding(
                    get: { bookPendingDelete != nil },
                    set: { if !$0 { bookPendingDelete = nil } }
                ),
                titleVisibility: .visible
            ) {
                Button("删除书籍", role: .destructive) {
                    if let target = bookPendingDelete {
                        store.deleteBook(target)
                    }
                    bookPendingDelete = nil
                }
                Button("取消", role: .cancel) { bookPendingDelete = nil }
            } message: {
                Text(bookPendingDelete?.title ?? "")
            }
            .alert("导入提示", isPresented: Binding(
                get: { importError != nil },
                set: { if !$0 { importError = nil } }
            )) {
                Button("确定", role: .cancel) { importError = nil }
            } message: {
                Text(importError ?? "")
            }
        }
    }
}

private struct SignatureQuickBanner: View {
    let info: ProvisioningSignatureInfo
    let onRefreshTap: () -> Void

    private var isUrgent: Bool {
        (info.remainingHours ?? 168) <= 48
    }

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: isUrgent ? "exclamationmark.shield.fill" : "checkmark.shield.fill")
                .font(.title3)
                .foregroundStyle(isUrgent ? .orange : .green)

            VStack(alignment: .leading, spacing: 2) {
                Text("Apple ID 自签守护 · \(info.remainingSummaryText)")
                    .font(.footnote.weight(.semibold))
                Text(isUrgent ? "建议连 Wi-Fi 开 StosVPN 后立即刷新续期" : "SideStore 自动续签守护已开启")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Button(action: onRefreshTap) {
                Text("续签")
                    .font(.caption.weight(.bold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(isUrgent ? Color.orange : Color.accentColor.opacity(0.15))
                    .foregroundStyle(isUrgent ? .white : .accentColor)
                    .clipShape(Capsule())
            }
        }
        .padding(12)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private struct BookGridCard: View {
    let book: Book
    let coverURL: URL?
    let onTap: () -> Void
    let onDelete: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 8) {
                ZStack(alignment: .bottomTrailing) {
                    BookCoverThumbnail(book: book, coverURL: coverURL)
                        .frame(height: 158)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .shadow(color: .black.opacity(0.12), radius: 4, x: 0, y: 2)

                    Text("\(book.overallProgressPercent)%")
                        .font(.system(size: 10, weight: .bold))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(.ultraThinMaterial)
                        .clipShape(Capsule())
                        .padding(6)
                }

                Text(book.title)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                Text("\(book.format.rawValue) · \(book.totalChapters) 章")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button {
                onTap()
            } label: {
                Label("继续阅读", systemImage: "book.fill")
            }
            Button(role: .destructive) {
                onDelete()
            } label: {
                Label("删除书籍", systemImage: "trash")
            }
        }
    }
}

private struct BookCoverThumbnail: View {
    let book: Book
    let coverURL: URL?
    @State private var uiImage: UIImage?

    var body: some View {
        ZStack {
            if let img = uiImage {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFill()
            } else {
                LinearGradient(
                    colors: gradientColors(for: book.format),
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                VStack(alignment: .leading, spacing: 8) {
                    Text(book.format.rawValue)
                        .font(.system(size: 10, weight: .bold))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(.white.opacity(0.22))
                        .clipShape(Capsule())
                        .foregroundStyle(.white)
                    Spacer()
                    Text(book.title)
                        .font(.system(size: 13, weight: .bold, design: .serif))
                        .foregroundStyle(.white)
                        .lineLimit(4)
                    Text(book.author)
                        .font(.system(size: 10))
                        .foregroundStyle(.white.opacity(0.82))
                        .lineLimit(1)
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .task(id: coverURL) {
            guard let url = coverURL else { return }
            let loaded = await Task.detached(priority: .utility) { () -> UIImage? in
                guard let data = try? Data(contentsOf: url) else { return nil }
                return UIImage(data: data)
            }.value
            uiImage = loaded
        }
    }

    private func gradientColors(for fmt: BookFormat) -> [Color] {
        switch fmt {
        case .epub: return [Color(hex: "#8A4F2B"), Color(hex: "#4E2A14")]
        case .markdown: return [Color(hex: "#2B5B84"), Color(hex: "#193652")]
        case .txt: return [Color(hex: "#3E6B52"), Color(hex: "#213E2E")]
        }
    }
}
