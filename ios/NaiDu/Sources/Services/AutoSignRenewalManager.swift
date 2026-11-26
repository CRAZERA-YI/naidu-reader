import Foundation
import UIKit
import UserNotifications

struct ProvisioningSignatureInfo: Equatable {
    enum InstallChannel: String, Equatable {
        case freeAppleIdSideload = "免费 Apple ID 自签 (SideStore / Sideloadly)"
        case developerOrAdHoc = "开发者证书签名"
        case permanentOrSimulator = "永久签名 / 巨魔 / 模拟器环境"
    }

    let channel: InstallChannel
    let teamName: String
    let appIdName: String
    let creationDate: Date?
    let expirationDate: Date?
    let sideStoreInstalled: Bool
    let altStoreInstalled: Bool

    var remainingHours: Double? {
        guard let exp = expirationDate else { return nil }
        return exp.timeIntervalSinceNow / 3600.0
    }

    var remainingSummaryText: String {
        guard let exp = expirationDate else {
            return "当前环境无 7 天过期限制"
        }
        let secs = exp.timeIntervalSinceNow
        if secs <= 0 {
            return "签名即将/已经到期，请立即跳转 SideStore 刷新"
        }
        let days = Int(secs) / 86400
        let hours = (Int(secs) % 86400) / 3600
        let mins = (Int(secs) % 3600) / 60
        if days > 0 {
            return "剩余 \(days) 天 \(hours) 小时"
        } else if hours > 0 {
            return "剩余 \(hours) 小时 \(mins) 分钟"
        } else {
            return "仅剩 \(mins) 分钟"
        }
    }

    var progressRatio: Double {
        guard let exp = expirationDate else { return 1.0 }
        let totalWindow = (creationDate != nil)
            ? max(3600.0, exp.timeIntervalSince(creationDate!))
            : 7.0 * 86400.0
        let left = max(0.0, exp.timeIntervalSinceNow)
        return min(1.0, left / totalWindow)
    }
}

@MainActor
final class AutoSignRenewalManager: ObservableObject {
    static let shared = AutoSignRenewalManager()

    static let sideStoreSourceURL = "https://raw.githubusercontent.com/CRAZERA-YI/naidu-reader/main/sidestore-source.json"
    static let githubReleaseURL = "https://github.com/CRAZERA-YI/naidu-reader/releases/latest"

    @Published private(set) var signatureInfo: ProvisioningSignatureInfo
    @Published var showExpiringPrompt: Bool = false

    private var hasPromptedThisSession = false

    private init() {
        signatureInfo = Self.inspectCurrentBundleSignature()
    }

    func refreshStatusAndSchedule(settings: AppSettings) {
        let info = Self.inspectCurrentBundleSignature()
        signatureInfo = info

        if settings.autoRenewReminderEnabled {
            scheduleExpiryNotifications(for: info)
        } else {
            cancelScheduledExpiryNotifications()
        }

        if settings.autoOpenSideStoreWhenExpiring,
           !hasPromptedThisSession,
           let hoursLeft = info.remainingHours,
           hoursLeft > 0,
           hoursLeft <= Double(settings.renewAlertThresholdHours) {
            hasPromptedThisSession = true
            showExpiringPrompt = true
        }
    }

    // MARK: - Real `embedded.mobileprovision` Parser

    static func inspectCurrentBundleSignature() -> ProvisioningSignatureInfo {
        let sideStoreInstalled = canOpen("sidestore://")
        let altStoreInstalled = canOpen("altstore://")

        guard let mobileProvisionURL = Bundle.main.url(forResource: "embedded", withExtension: "mobileprovision"),
              let rawData = try? Data(contentsOf: mobileProvisionURL) else {
            return ProvisioningSignatureInfo(
                channel: .permanentOrSimulator,
                teamName: "无内嵌描述文件",
                appIdName: "NaiDu",
                creationDate: nil,
                expirationDate: nil,
                sideStoreInstalled: sideStoreInstalled,
                altStoreInstalled: altStoreInstalled
            )
        }

        // Extract XML Plist slice inside PKCS#7 CMS envelope: <?xml ... </plist>
        guard let xmlSlice = extractPlistData(from: rawData),
              let plist = (try? PropertyListSerialization.propertyList(from: xmlSlice, options: [], format: nil)) as? [String: Any] else {
            return ProvisioningSignatureInfo(
                channel: .freeAppleIdSideload,
                teamName: "Apple ID 自签",
                appIdName: "NaiDu",
                creationDate: nil,
                expirationDate: nil,
                sideStoreInstalled: sideStoreInstalled,
                altStoreInstalled: altStoreInstalled
            )
        }

        let expDate = plist["ExpirationDate"] as? Date
        let createDate = plist["CreationDate"] as? Date
        let teamName = (plist["TeamName"] as? String) ?? "个人 Apple ID"
        let appIdName = (plist["AppIDName"] as? String) ?? "奶读"

        let totalDays: Double = {
            if let c = createDate, let e = expDate {
                return e.timeIntervalSince(c) / 86400.0
            }
            return 7.0
        }()

        let channel: ProvisioningSignatureInfo.InstallChannel = (totalDays <= 8.5)
            ? .freeAppleIdSideload
            : .developerOrAdHoc

        return ProvisioningSignatureInfo(
            channel: channel,
            teamName: teamName,
            appIdName: appIdName,
            creationDate: createDate,
            expirationDate: expDate,
            sideStoreInstalled: sideStoreInstalled,
            altStoreInstalled: altStoreInstalled
        )
    }

    private static func extractPlistData(from cmsData: Data) -> Data? {
        let startMarker = Data("<?xml".utf8)
        let endMarker = Data("</plist>".utf8)
        guard let startRange = cmsData.range(of: startMarker),
              let endRange = cmsData.range(of: endMarker, in: startRange.lowerBound..<cmsData.endIndex) else {
            return nil
        }
        return cmsData.subdata(in: startRange.lowerBound..<endRange.upperBound)
    }

    private static func canOpen(_ scheme: String) -> Bool {
        guard let url = URL(string: scheme) else { return false }
        return UIApplication.shared.canOpenURL(url)
    }

    // MARK: - Deep-Link Actions for SideStore / AltStore / Shortcuts

    func openSideStoreForRefresh() {
        let candidates = [
            "sidestore://",
            "altstore://",
            Self.githubReleaseURL
        ]
        for raw in candidates {
            if let url = URL(string: raw), UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url)
                return
            }
        }
    }

    func addRepoSourceToSideStore() {
        let encoded = Self.sideStoreSourceURL.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? Self.sideStoreSourceURL
        let deepLinks = [
            "sidestore://source?url=\(encoded)",
            "altstore://source?url=\(encoded)"
        ]
        for raw in deepLinks {
            if let url = URL(string: raw), UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url)
                return
            }
        }
        UIPasteboard.general.string = Self.sideStoreSourceURL
    }

    func copySideStoreSourceURL() {
        UIPasteboard.general.string = Self.sideStoreSourceURL
    }

    func openSystemShortcutsApp() {
        if let url = URL(string: "shortcuts://"), UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
        }
    }

    // MARK: - Local Expiry Warning Notifications (48h / 24h / 6h before deadline)

    private func scheduleExpiryNotifications(for info: ProvisioningSignatureInfo) {
        guard let expDate = info.expirationDate, expDate.timeIntervalSinceNow > 600 else { return }

        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            guard granted else { return }
            let ids = ["naidu_sign_48h", "naidu_sign_24h", "naidu_sign_6h"]
            center.removePendingNotificationRequests(withIdentifiers: ids)

            let checkpoints: [(String, TimeInterval, String)] = [
                ("naidu_sign_48h", 48 * 3600, "签名还剩 2 天到期，连接 Wi-Fi 打开 StosVPN 后可在 SideStore 一键自动续期。"),
                ("naidu_sign_24h", 24 * 3600, "签名还剩不到 24 小时！请确保今日已触发 SideStore 自动续签，避免过期无法打开。"),
                ("naidu_sign_6h", 6 * 3600, "紧急提醒：奶读 iOS 签名仅剩 6 小时到期，点击通知立即打开并跳转 SideStore 续签！")
            ]

            for (identifier, leadSeconds, message) in checkpoints {
                let fireDate = expDate.addingTimeInterval(-leadSeconds)
                let delay = fireDate.timeIntervalSinceNow
                guard delay > 120 else { continue }

                let content = UNMutableNotificationContent()
                content.title = "奶读 · 自动续签守护"
                content.body = message
                content.sound = .default

                let trigger = UNTimeIntervalNotificationTrigger(timeInterval: delay, repeats: false)
                let req = UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
                center.add(req)
            }
        }
    }

    func cancelScheduledExpiryNotifications() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(
            withIdentifiers: ["naidu_sign_48h", "naidu_sign_24h", "naidu_sign_6h"]
        )
    }
}
