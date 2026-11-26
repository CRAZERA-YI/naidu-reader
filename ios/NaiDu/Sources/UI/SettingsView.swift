import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var renewalManager: AutoSignRenewalManager
    @State private var copiedSourceToast: Bool = false
    @State private var showShortcutWizard: Bool = false

    var body: some View {
        NavigationStack {
            Form {
                // MARK: 1. iOS Free Apple ID Auto-Renewal Center (SideStore Guardian)
                Section {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Image(systemName: "checkmark.shield.fill")
                                .font(.title2)
                                .foregroundStyle(.green)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(renewalManager.signatureInfo.channel.rawValue)
                                    .font(.subheadline.weight(.bold))
                                Text(renewalManager.signatureInfo.remainingSummaryText)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            if renewalManager.signatureInfo.sideStoreInstalled {
                                Text("已装 SideStore")
                                    .font(.caption2.weight(.bold))
                                    .padding(.horizontal, 7)
                                    .padding(.vertical, 3)
                                    .background(Color.green.opacity(0.15))
                                    .foregroundStyle(.green)
                                    .clipShape(Capsule())
                            }
                        }

                        if renewalManager.signatureInfo.expirationDate != nil {
                            ProgressView(value: renewalManager.signatureInfo.progressRatio)
                                .tint((renewalManager.signatureInfo.remainingHours ?? 168) <= 48 ? .orange : .green)
                        }
                    }
                    .padding(.vertical, 4)

                    Toggle("签名到期前 48h / 24h / 6h 推送通知守护", isOn: Binding(
                        get: { store.settings.autoRenewReminderEnabled },
                        set: { val in store.updateSettings { $0.autoRenewReminderEnabled = val } }
                    ))

                    Toggle("剩余不足 2 天打开应用自动弹窗续签", isOn: Binding(
                        get: { store.settings.autoOpenSideStoreWhenExpiring },
                        set: { val in store.updateSettings { $0.autoOpenSideStoreWhenExpiring = val } }
                    ))

                    Button {
                        renewalManager.addRepoSourceToSideStore()
                        copiedSourceToast = true
                    } label: {
                        Label("一键将「奶读自动续签/更新源」加入 SideStore", systemImage: "link.badge.plus")
                    }

                    Button {
                        renewalManager.openSideStoreForRefresh()
                    } label: {
                        Label("立即唤起 SideStore 刷新 7 天有效期", systemImage: "arrow.clockwise.circle.fill")
                    }

                    Button {
                        showShortcutWizard = true
                    } label: {
                        Label("开启「每日连 Wi-Fi 全自动静默续签」配置向导", systemImage: "wand.and.stars")
                            .foregroundStyle(Color.accentColor)
                    }
                } header: {
                    Text("iOS 免费 Apple ID 自签与自动续签中心")
                } footer: {
                    Text(copiedSourceToast
                         ? "已唤起 SideStore 或将源地址复制到剪贴板：\(AutoSignRenewalManager.sideStoreSourceURL)"
                         : "原理说明：将奶读通过 SideStore 安装并添加上方官方源后，配合 iOS「快捷指令自动化」即可每天连上 Wi-Fi 时在后台静默重置回满血 7 天，无需电脑。")
                }

                // MARK: 2. Dictionary & Optional OpenAI AI Translation
                Section("查词与 AI 语境翻译") {
                    Toggle("联网增强（有道详尽义项 + 柯林斯双解）", isOn: Binding(
                        get: { store.settings.enableOnlineDict },
                        set: { v in store.updateSettings { $0.enableOnlineDict = v } }
                    ))

                    TextField("AI 接口地址 (OpenAI 兼容)", text: Binding(
                        get: { store.settings.aiApiBaseUrl },
                        set: { v in store.updateSettings { $0.aiApiBaseUrl = v } }
                    ))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()

                    SecureField("AI API Key（选填，留空仅用词典）", text: Binding(
                        get: { store.settings.aiApiKey },
                        set: { v in store.updateSettings { $0.aiApiKey = v } }
                    ))

                    TextField("AI 模型名称 (如 gpt-4o-mini / deepseek-chat)", text: Binding(
                        get: { store.settings.aiModel },
                        set: { v in store.updateSettings { $0.aiModel = v } }
                    ))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                }

                // MARK: 3. Human Pronunciation Settings
                Section("真人单词发音设置") {
                    Toggle("查词时自动播放发音", isOn: Binding(
                        get: { store.settings.autoPronounce },
                        set: { v in store.updateSettings { $0.autoPronounce = v } }
                    ))
                    Toggle("优先使用真人原声录音 (离线自动回退)", isOn: Binding(
                        get: { store.settings.preferHumanVoice },
                        set: { v in store.updateSettings { $0.preferHumanVoice = v } }
                    ))
                    Picker("默认口音", selection: Binding(
                        get: { store.settings.ttsAccent },
                        set: { v in store.updateSettings { $0.ttsAccent = v } }
                    )) {
                        ForEach(TtsAccent.allCases) { acc in
                            Text(acc.label).tag(acc)
                        }
                    }
                    HStack {
                        Text("朗读语速 \(String(format: "%.2fx", store.settings.ttsSpeed))")
                        Slider(value: Binding(
                            get: { store.settings.ttsSpeed },
                            set: { v in store.updateSettings { $0.ttsSpeed = v } }
                        ), in: 0.75...1.35, step: 0.05)
                    }
                }

                // MARK: 4. Default Reading Preferences
                Section("默认阅读与翻页偏好") {
                    Picker("默认主题", selection: Binding(
                        get: { store.settings.readerTheme },
                        set: { v in store.updateSettings { $0.readerTheme = v } }
                    )) {
                        ForEach(ReaderTheme.allCases) { t in
                            Text(t.label).tag(t)
                        }
                    }
                    Picker("默认翻页模式", selection: Binding(
                        get: { store.settings.pageTurnMode },
                        set: { v in store.updateSettings { $0.pageTurnMode = v } }
                    )) {
                        ForEach(PageTurnMode.allCases) { m in
                            Text(m.label).tag(m)
                        }
                    }
                    Toggle("启用音量键翻页", isOn: Binding(
                        get: { store.settings.volumeKeyPaging },
                        set: { v in store.updateSettings { $0.volumeKeyPaging = v } }
                    ))
                }
            }
            .navigationTitle("设置与续签")
            .sheet(isPresented: $showShortcutWizard) {
                AutoRenewShortcutGuideSheet()
                    .environmentObject(renewalManager)
            }
        }
    }
}

private struct AutoRenewShortcutGuideSheet: View {
    @EnvironmentObject private var renewalManager: AutoSignRenewalManager
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Text("只需配置一次 iOS 系统自带的「快捷指令自动化」，每天手机连着家里 Wi-Fi 时就会在后台自动打开 StosVPN 并调用 SideStore 刷新签名，彻底告别 7 天掉签烦恼：")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    WizardStepCard(
                        step: "第一步",
                        title: "确保使用 SideStore 安装奶读",
                        detail: "首次在电脑上用 SideServer / AltServer 安装好 SideStore 与 StosVPN（导入配对文件）后，在奶读设置页点「一键加入 SideStore 源」或直接在 SideStore 导入 NaiDu-iOS.ipa。"
                    )

                    WizardStepCard(
                        step: "第二步",
                        title: "新建每日定时自动化（立即运行）",
                        detail: "打开 iPhone 自带「快捷指令」App → 底部点「自动化」→ 右上角「+」→ 选择「特定时间」（例如每天凌晨 03:30 或晚上 22:00），勾选「立即运行」（不要勾选运行时询问）。"
                    )

                    WizardStepCard(
                        step: "第三步",
                        title: "添加「连接 StosVPN + 刷新全部应用」动作",
                        detail: "在新建空白自动化中依次添加 3 个动作：\n1. 搜索「设置 VPN」→ 设为「连接 StosVPN」\n2. 搜索「等待」→ 设为「等待 3 秒」\n3. 搜索「SideStore」→ 选择「Refresh All Apps（刷新所有应用）」\n保存后，只要该时段手机连着任意 Wi-Fi，签名每天都会自动续满 7 天！"
                    )

                    Button {
                        renewalManager.openSystemShortcutsApp()
                    } label: {
                        Label("一键打开系统「快捷指令」去配置", systemImage: "arrow.up.forward.app.fill")
                            .fontWeight(.semibold)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                    .buttonStyle(.borderedProminent)
                }
                .padding(18)
            }
            .navigationTitle("全自动静默续签向导")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { dismiss() }
                }
            }
        }
    }
}

private struct WizardStepCard: View {
    let step: String
    let title: String
    let detail: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(step)
                .font(.caption.weight(.bold))
                .foregroundStyle(Color.accentColor)
            Text(title)
                .font(.headline)
            Text(detail)
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
