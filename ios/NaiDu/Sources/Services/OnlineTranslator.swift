import Foundation

enum OnlineTranslator {
    private static let session: URLSession = {
        let cfg = URLSessionConfiguration.default
        cfg.timeoutIntervalForRequest = 8
        cfg.timeoutIntervalForResource = 12
        return URLSession(configuration: cfg)
    }()

    static func lookupRich(
        query: String,
        sentenceContext: String,
        offlineBase: TranslationResult?,
        settings: AppSettings
    ) async -> TranslationResult {
        let trimmed = OfflineDictionary.cleanQuery(query)
        let isMultiWord = trimmed.contains(" ") || trimmed.count > 28
        var merged = offlineBase ?? TranslationResult(query: trimmed, displayWord: trimmed, lemma: trimmed.lowercased())

        if settings.enableOnlineDict, !trimmed.isEmpty {
            if let yd = await lookupYoudao(query: trimmed) {
                merged = mergeResults(offline: offlineBase, online: yd, originalQuery: trimmed)
            }
        }

        let apiKey = settings.aiApiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        if !apiKey.isEmpty {
            if let aiText = await queryOpenAI(
                target: trimmed,
                sentence: sentenceContext,
                isSentence: isMultiWord,
                settings: settings
            ) {
                merged.aiContextualTranslation = aiText
                if merged.chineseMeaning.isEmpty {
                    merged.chineseMeaning = aiText
                }
                if !merged.sourceLabel.contains("AI") {
                    merged.sourceLabel += " + AI语境"
                }
            }
        }

        if merged.chineseMeaning.isEmpty {
            merged.chineseMeaning = isMultiWord ? "联网后可自动翻译长句，或在设置中配置 AI 翻译接口" : "未查找到释义"
        }
        return merged
    }

    static func lookupYoudao(query: String) async -> TranslationResult? {
        guard let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "https://dict.youdao.com/jsonapi?q=\(encoded)") else {
            return nil
        }
        var req = URLRequest(url: url)
        req.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile", forHTTPHeaderField: "User-Agent")

        do {
            let (data, resp) = try await session.data(for: req)
            guard (resp as? HTTPURLResponse)?.statusCode == 200 else { return nil }
            return parseYoudaoJSON(query: query, data: data)
        } catch {
            return nil
        }
    }

    private static func parseYoudaoJSON(query: String, data: Data) -> TranslationResult? {
        guard let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            return nil
        }

        var ukPhone = ""
        var usPhone = ""
        var returnPhrase = ""
        var meanings: [String] = []
        var wordForms: [WordForm] = []
        var senseGroups: [SenseGroup] = []
        var collinsSenses: [CollinsSense] = []
        var phrases: [PhraseItem] = []
        var relatedWords: [RelatedWordGroup] = []
        var examples: [ExampleSentence] = []

        // 1. Primary EC dictionary
        if let ec = root["ec"] as? [String: Any],
           let words = ec["word"] as? [[String: Any]],
           let wordObj = words.first {
            returnPhrase = str(wordObj["return-phrase"])
            ukPhone = str(wordObj["ukphone"])
            usPhone = str(wordObj["usphone"])

            if let trs = wordObj["trs"] as? [[String: Any]] {
                for trObj in trs {
                    if let innerArr = trObj["tr"] as? [[String: Any]] {
                        for inner in innerArr {
                            if let lObj = inner["l"] as? [String: Any],
                               let iArr = lObj["i"] as? [Any] {
                                for item in iArr {
                                    let text = cleanHtml(str(item))
                                    if !text.isEmpty, !meanings.contains(text) {
                                        meanings.append(text)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if let wfs = wordObj["wfs"] as? [[String: Any]] {
                for wfElem in wfs {
                    if let wf = wfElem["wf"] as? [String: Any] {
                        let n = str(wf["name"])
                        let v = str(wf["value"])
                        if !n.isEmpty, !v.isEmpty {
                            wordForms.append(WordForm(name: n, value: v))
                        }
                    }
                }
            }
        }

        // 2. Expanded EC
        if let expandEc = root["expand_ec"] as? [String: Any],
           let wordArr = expandEc["word"] as? [[String: Any]] {
            for wObj in wordArr {
                let pos = str(wObj["pos"]).isEmpty ? "释义" : str(wObj["pos"])
                var detailedList: [DetailedSense] = []
                if let transList = wObj["transList"] as? [[String: Any]] {
                    for tObj in transList {
                        let trans = cleanHtml(str(tObj["trans"]))
                        if trans.isEmpty { continue }
                        let contentObj = tObj["content"] as? [String: Any]
                        let detailPos = str(contentObj?["detailPos"])
                        var senseExamples: [ExampleSentence] = []
                        if let sents = contentObj?["sents"] as? [[String: Any]] {
                            for sObj in sents.prefix(2) {
                                let eng = cleanHtml(str(sObj["sentOrig"]))
                                let chn = cleanHtml(str(sObj["sentTrans"]))
                                if !eng.isEmpty, !chn.isEmpty {
                                    senseExamples.append(ExampleSentence(english: eng, chinese: chn, source: str(sObj["source"])))
                                }
                            }
                        }
                        detailedList.append(DetailedSense(translation: trans, detailPos: detailPos, examples: senseExamples))
                    }
                }
                if !detailedList.isEmpty {
                    senseGroups.append(SenseGroup(pos: pos, senses: detailedList))
                }
            }
        }

        // 3. Collins Bilingual Senses
        var collinsIdx = 1
        if let collins = root["collins"] as? [String: Any],
           let entriesArr = collins["collins_entries"] as? [[String: Any]] {
            for ceObj in entriesArr {
                if let entries = (ceObj["entries"] as? [String: Any])?["entry"] as? [[String: Any]] {
                    for entryElem in entries {
                        guard let tranEntries = entryElem["tran_entry"] as? [[String: Any]] else { continue }
                        for teObj in tranEntries {
                            let explanation = cleanHtml(str(teObj["tran"]))
                            if explanation.isEmpty { continue }
                            let posObj = teObj["pos_entry"] as? [String: Any]
                            let pos = str(posObj?["pos"])
                            let posTips = str(posObj?["pos_tips"])
                            var cExamples: [ExampleSentence] = []
                            if let sents = (teObj["exam_sents"] as? [String: Any])?["sent"] as? [[String: Any]] {
                                for sObj in sents.prefix(2) {
                                    let eng = cleanHtml(str(sObj["eng_sent"]))
                                    let chn = cleanHtml(str(sObj["chn_sent"]))
                                    if !eng.isEmpty, !chn.isEmpty {
                                        cExamples.append(ExampleSentence(english: eng, chinese: chn, source: "柯林斯"))
                                    }
                                }
                            }
                            collinsSenses.append(
                                CollinsSense(index: collinsIdx, pos: pos, posTips: posTips, explanation: explanation, examples: cExamples)
                            )
                            collinsIdx += 1
                        }
                    }
                }
            }
        }

        // 4. Phrases
        if let phrsRoot = root["phrs"] as? [String: Any],
           let phrsArr = phrsRoot["phrs"] as? [[String: Any]] {
            for pElem in phrsArr.prefix(12) {
                guard let phrObj = pElem["phr"] as? [String: Any] else { continue }
                let headword = cleanHtml(str(((phrObj["headword"] as? [String: Any])?["l"] as? [String: Any])?["i"]))
                var tran = ""
                if let trs = phrObj["trs"] as? [[String: Any]],
                   let firstTr = trs.first?["tr"] as? [String: Any],
                   let lObj = firstTr["l"] as? [String: Any] {
                    tran = cleanHtml(str(lObj["i"]))
                }
                if !headword.isEmpty, !tran.isEmpty {
                    phrases.append(PhraseItem(phrase: headword, meaning: tran))
                }
            }
        }

        // 5. Related Words / Derivatives
        if let relRoot = root["rel_word"] as? [String: Any],
           let rels = relRoot["rels"] as? [[String: Any]] {
            for rElem in rels {
                guard let relObj = rElem["rel"] as? [String: Any] else { continue }
                let pos = str(relObj["pos"])
                var entries: [RelatedWordEntry] = []
                if let words = relObj["words"] as? [[String: Any]] {
                    for wObj in words.prefix(6) {
                        let w = cleanHtml(str(wObj["word"]))
                        let t = cleanHtml(str(wObj["tran"]))
                        if !w.isEmpty, !t.isEmpty {
                            entries.append(RelatedWordEntry(word: w, meaning: t))
                        }
                    }
                }
                if !entries.isEmpty {
                    relatedWords.append(RelatedWordGroup(pos: pos, entries: entries))
                }
            }
        }

        // 6. Bilingual Sentences & Full-Sentence Fanyi Fallback
        if let blng = root["blng_sents_part"] as? [String: Any],
           let pairs = blng["sentence-pair"] as? [[String: Any]] {
            for pair in pairs.prefix(4) {
                let eng = cleanHtml(str(pair["sentence"]))
                let chn = cleanHtml(str(pair["sentence-translation"]))
                let src = str(pair["source"])
                if !eng.isEmpty, !chn.isEmpty {
                    examples.append(ExampleSentence(english: eng, chinese: chn, source: src))
                }
            }
        }

        if meanings.isEmpty, let fanyi = root["fanyi"] as? [String: Any] {
            let tran = cleanHtml(str(fanyi["tran"]))
            if !tran.isEmpty {
                meanings.append(tran)
            }
        }

        if meanings.isEmpty, senseGroups.isEmpty, collinsSenses.isEmpty {
            return nil
        }

        let display = returnPhrase.isEmpty ? query : returnPhrase
        let joinedMeaning = meanings.isEmpty
            ? senseGroups.flatMap { g in g.senses.map { "\(g.pos) \($0.translation)" } }.prefix(6).joined(separator: "\n")
            : meanings.joined(separator: "\n")

        return TranslationResult(
            query: query,
            displayWord: display,
            lemma: display.lowercased(),
            phoneticUk: ukPhone,
            phoneticUs: usPhone,
            chineseMeaning: joinedMeaning,
            wordForms: wordForms,
            senseGroups: senseGroups,
            collinsSenses: collinsSenses,
            phrases: phrases,
            relatedWords: relatedWords,
            examples: examples,
            isPhrase: query.contains(" "),
            sourceLabel: collinsSenses.isEmpty ? "有道详尽词典" : "有道 + 柯林斯双解"
        )
    }

    private static func mergeResults(
        offline: TranslationResult?,
        online: TranslationResult,
        originalQuery: String
    ) -> TranslationResult {
        guard let off = offline else { return online }
        var out = online
        out.query = originalQuery
        if out.phoneticUs.isEmpty { out.phoneticUs = off.phoneticUs }
        if out.lemma.isEmpty { out.lemma = off.lemma }
        if out.formNote.isEmpty { out.formNote = off.formNote }
        if out.englishDef.isEmpty { out.englishDef = off.englishDef }
        if out.tags.isEmpty { out.tags = off.tags }
        if out.collins == 0 { out.collins = off.collins }
        if !out.oxford { out.oxford = off.oxford }
        if out.chineseMeaning.isEmpty { out.chineseMeaning = off.chineseMeaning }
        return out
    }

    private static func queryOpenAI(
        target: String,
        sentence: String,
        isSentence: Bool,
        settings: AppSettings
    ) async -> String? {
        let base = settings.aiApiBaseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/ "))
        guard let url = URL(string: "\(base)/chat/completions") else { return nil }

        let prompt: String
        if isSentence {
            prompt = "请将以下英文段落准确、流畅地翻译成中文，只输出译文：\n\(target)"
        } else {
            prompt = "英文单词/词组：\"\(target)\"\n所在原句：\"\(sentence)\"\n请用一句话给出它在当前原句语境中的精准中文含义与简要语法点拨（不超过60字）。"
        }

        let payload: [String: Any] = [
            "model": settings.aiModel,
            "temperature": 0.2,
            "messages": [
                ["role": "system", "content": "你是一位精炼准确的英语阅读助教。"],
                ["role": "user", "content": prompt]
            ]
        ]

        guard let body = try? JSONSerialization.data(withJSONObject: payload) else { return nil }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(settings.aiApiKey.trimmingCharacters(in: .whitespacesAndNewlines))", forHTTPHeaderField: "Authorization")
        req.httpBody = body

        do {
            let (data, resp) = try await session.data(for: req)
            guard (resp as? HTTPURLResponse)?.statusCode == 200,
                  let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
                  let choices = root["choices"] as? [[String: Any]],
                  let msg = choices.first?["message"] as? [String: Any],
                  let content = msg["content"] as? String else {
                return nil
            }
            return content.trimmingCharacters(in: .whitespacesAndNewlines)
        } catch {
            return nil
        }
    }

    private static func str(_ any: Any?) -> String {
        if let s = any as? String { return s }
        if let n = any as? NSNumber { return n.stringValue }
        return ""
    }

    private static func cleanHtml(_ raw: String) -> String {
        guard !raw.isEmpty else { return "" }
        return raw
            .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
