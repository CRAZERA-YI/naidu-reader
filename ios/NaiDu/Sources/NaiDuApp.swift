import SwiftUI

@main
struct NaiDuApp: App {
    @StateObject private var store = AppStore()
    @StateObject private var renewalManager = AutoSignRenewalManager.shared
    @Environment(\.scenePhase) private var scenePhase

    @State private var selectedTab: Int = 0
    @State private var activeReaderBook: Book?
    @State private var importErrorMessage: String?

    var body: some Scene {
        WindowGroup {
            TabView(selection: $selectedTab) {
                BookshelfView(
                    onOpenBook: { book in
                        activeReaderBook = book
                    }
                )
                .tabItem {
                    Label("书架", systemImage: "books.vertical.fill")
                }
                .tag(0)

                VocabView()
                    .tabItem {
                        Label("单词本", systemImage: "character.book.closed.fill")
                    }
                    .badge(store.dueCount)
                    .tag(1)

                SettingsView()
                    .tabItem {
                        Label("设置与续签", systemImage: "checkmark.shield.fill")
                    }
                    .tag(2)
            }
            .environmentObject(store)
            .environmentObject(renewalManager)
            .fullScreenCover(item: $activeReaderBook) { book in
                ReaderView(book: book) {
                    activeReaderBook = nil
                }
                .environmentObject(store)
            }
            .onChange(of: scenePhase) { phase in
                if phase == .active {
                    renewalManager.refreshStatusAndSchedule(settings: store.settings)
                }
            }
            .onChange(of: store.jumpTargetBook?.bookId) { targetId in
                guard let jump = store.jumpTargetBook, targetId != nil else { return }
                if let idx = store.books.firstIndex(where: { $0.id == jump.bookId }) {
                    store.updateBookProgress(bookId: jump.bookId, chapterIndex: jump.chapterIndex, progress: 0.0)
                    activeReaderBook = store.books[idx]
                }
                store.jumpTargetBook = nil
            }
            .onOpenURL { url in
                do {
                    let imported = try store.importBookFromURL(url, securityScoped: true)
                    selectedTab = 0
                    activeReaderBook = imported
                } catch {
                    importErrorMessage = error.localizedDescription
                }
            }
            .alert("签名即将到期提醒", isPresented: $renewalManager.showExpiringPrompt) {
                Button("一键跳转 SideStore 续签") {
                    renewalManager.openSideStoreForRefresh()
                }
                Button("稍后处理", role: .cancel) {}
            } message: {
                Text("当前 Apple ID 免费签名\(renewalManager.signatureInfo.remainingSummaryText)。建议连接 Wi-Fi 打开 StosVPN 后立即在 SideStore 刷新续期。")
            }
            .alert("导入失败", isPresented: Binding(
                get: { importErrorMessage != nil },
                set: { if !$0 { importErrorMessage = nil } }
            )) {
                Button("知道了", role: .cancel) { importErrorMessage = nil }
            } message: {
                Text(importErrorMessage ?? "")
            }
            .onAppear {
                renewalManager.refreshStatusAndSchedule(settings: store.settings)
            }
        }
    }
}
