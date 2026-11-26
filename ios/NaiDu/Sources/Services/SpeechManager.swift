import AVFoundation
import Foundation

@MainActor
final class SpeechManager: NSObject, ObservableObject {
    static let shared = SpeechManager()

    private let synthesizer = AVSpeechSynthesizer()
    private var audioPlayer: AVAudioPlayer?
    private let cacheDir: URL

    override private init() {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        cacheDir = base.appendingPathComponent("pronunciation", isDirectory: true)
        try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)
        super.init()
    }

    func speakWord(_ rawWord: String, settings: AppSettings) {
        let word = OfflineDictionary.cleanQuery(rawWord)
        guard !word.isEmpty else { return }

        configureAudioSession()

        if settings.preferHumanVoice && !word.contains(" ") {
            Task {
                if let fileURL = await fetchOrCacheHumanMp3(word: word, accent: settings.ttsAccent) {
                    if playMp3(at: fileURL, speed: settings.ttsSpeed) {
                        return
                    }
                }
                speakSystemTts(text: word, language: settings.ttsAccent == .uk ? "en-GB" : "en-US", speed: settings.ttsSpeed)
            }
        } else {
            speakSystemTts(text: word, language: settings.ttsAccent == .uk ? "en-GB" : "en-US", speed: settings.ttsSpeed)
        }
    }

    func speakChinese(_ text: String, speed: Double = 1.0) {
        let cleaned = text
            .replacingOccurrences(of: "\n", with: "；")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { return }
        configureAudioSession()
        speakSystemTts(text: String(cleaned.prefix(90)), language: "zh-CN", speed: speed)
    }

    func stop() {
        audioPlayer?.stop()
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
    }

    private func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
        try? session.setActive(true)
    }

    private func playMp3(at url: URL, speed: Double) -> Bool {
        do {
            audioPlayer?.stop()
            let player = try AVAudioPlayer(contentsOf: url)
            player.enableRate = true
            player.rate = Float(max(0.6, min(1.5, speed)))
            player.prepareToPlay()
            audioPlayer = player
            return player.play()
        } catch {
            return false
        }
    }

    private func speakSystemTts(text: String, language: String, speed: Double) {
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: language)
        let mappedRate = AVSpeechUtteranceDefaultSpeechRate * Float(max(0.7, min(1.35, speed)))
        utterance.rate = min(AVSpeechUtteranceMaximumSpeechRate, max(AVSpeechUtteranceMinimumSpeechRate, mappedRate))
        synthesizer.speak(utterance)
    }

    private func fetchOrCacheHumanMp3(word: String, accent: TtsAccent) async -> URL? {
        let safeKey = word.lowercased().replacingOccurrences(of: "[^a-z0-9_-]", with: "_", options: .regularExpression)
        let typeCode = (accent == .uk) ? 1 : 2
        let target = cacheDir.appendingPathComponent("\(safeKey)_\(typeCode).mp3")

        if let attrs = try? FileManager.default.attributesOfItem(atPath: target.path),
           let size = (attrs[.size] as? NSNumber)?.intValue, size > 512 {
            return target
        }

        guard let encoded = word.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "https://dict.youdao.com/dictvoice?audio=\(encoded)&type=\(typeCode)") else {
            return nil
        }

        do {
            let (data, resp) = try await URLSession.shared.data(from: url)
            guard (resp as? HTTPURLResponse)?.statusCode == 200, data.count > 512 else { return nil }
            try data.write(to: target, options: .atomic)
            return target
        } catch {
            return nil
        }
    }
}
