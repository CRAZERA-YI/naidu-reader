package com.lingoreader.app

import com.lingoreader.app.parser.EpubParser
import com.lingoreader.app.parser.MarkdownParser
import com.lingoreader.app.parser.TxtParser
import com.lingoreader.app.translation.OfflineDictionary
import com.lingoreader.app.translation.OnlineTranslator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersAndMorphTest {

    @Test
    fun txtParserSplitsChaptersCleanly() {
        val sample = """
            Chapter 1: Dawn
            First paragraph line one.
            Still first paragraph.

            Second paragraph here.

            Chapter 2: Dusk
            Another chapter begins.
        """.trimIndent()

        val parsed = TxtParser.parseText(sample, "Test Story")
        assertEquals(2, parsed.chapters.size)
        assertEquals("Chapter 1: Dawn", parsed.chapters[0].title)
        assertEquals("Chapter 2: Dusk", parsed.chapters[1].title)
        assertTrue(parsed.chapters[0].htmlContent.contains("First paragraph line one. Still first paragraph."))
    }

    @Test
    fun markdownParserRendersRichFormattingAndEscapesHtml() {
        val md = """
            # Sample Title

            > A **bold** quote with *italics* and `inline_code`.

            - First item
            - Second <script>alert(1)</script> item

            | Word | Meaning |
            | --- | --- |
            | subtle | 微妙的 |
        """.trimIndent()

        val parsed = MarkdownParser.parseMarkdown(md, "Fallback")
        assertEquals("Sample Title", parsed.title)
        val html = parsed.chapters.first().htmlContent
        assertTrue(html.contains("<h1>Sample Title</h1>"))
        assertTrue(html.contains("<strong>bold</strong>"))
        assertTrue(html.contains("<em>italics</em>"))
        assertTrue(html.contains("<code>inline_code</code>"))
        assertTrue(html.contains("<table>"))
        assertFalse(html.contains("<script>alert(1)</script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun epubBodyExtractorStripsScripts() {
        val raw = "<html><head><title>Ch1</title></head><body><h1>Hello</h1><script>evil()</script><p>World</p></body></html>"
        val body = EpubParser.extractBodyHtml(raw)
        assertTrue(body.contains("<h1>Hello</h1>"))
        assertTrue(body.contains("<p>World</p>"))
        assertFalse(body.contains("evil()"))
    }

    @Test
    fun offlineDictionaryCleaningAndMorphology() {
        assertEquals("serendipity", OfflineDictionary.cleanQuery("“serendipity,”"))
        assertEquals("author", OfflineDictionary.cleanQuery("author’s"))
        val ingCandidates = OfflineDictionary.generateMorphCandidates("running").map { it.first }
        assertTrue("run" in ingCandidates)
        val iesCandidates = OfflineDictionary.generateMorphCandidates("studies").map { it.first }
        assertTrue("study" in iesCandidates)
    }

    @Test
    fun youdaoRichJsonParserExtractsAllSensesAndMetadata() {
        val sampleJson = """
            {
              "ec": {
                "word": [{
                  "return-phrase": "charge",
                  "ukphone": "tʃɑːdʒ",
                  "usphone": "tʃɑːrdʒ",
                  "trs": [
                    {"tr": [{"l": {"i": ["v. 充电；控告，指控；收费"]}}]},
                    {"tr": [{"l": {"i": ["n. 费用；电量；掌管"]}}]}
                  ],
                  "wfs": [
                    {"wf": {"name": "过去式", "value": "charged"}},
                    {"wf": {"name": "复数", "value": "charges"}}
                  ]
                }]
              },
              "expand_ec": {
                "word": [{
                  "pos": "v.",
                  "transList": [{
                    "trans": "充电",
                    "content": {
                      "detailPos": "vt.&vi.",
                      "sents": [{"sentOrig": "Forgot to <b>charge</b> the battery.", "sentTrans": "忘了给电池充电。", "source": "柯林斯"}]
                    }
                  }]
                }]
              },
              "collins": {
                "collins_entries": [{
                  "entries": {
                    "entry": [{
                      "tran_entry": [{
                        "pos_entry": {"pos": "V-T", "pos_tips": "及物动词"},
                        "tran": "If you <b>charge</b> someone an amount of money. 收费",
                        "exam_sents": {"sent": [{"eng_sent": "They charge $10.", "chn_sent": "他们收费10美元。"}]}
                      }]
                    }]
                  }
                }]
              },
              "phrs": {
                "phrs": [{"phr": {"headword": {"l": {"i": "in charge of"}}, "trs": [{"tr": {"l": {"i": "负责；主管"}}}]}}]
              },
              "syno": {
                "synos": [{"syno": {"pos": "n.", "tran": "费用", "ws": [{"w": "fee"}, {"w": "cost"}]}}]
              },
              "rel_word": {
                "rels": [{"rel": {"pos": "n.", "words": [{"word": "charger", "tran": "充电器"}]}}]
              },
              "blng_sents_part": {
                "sentence-pair": [{"sentence": "Free of charge.", "sentence-translation": "免费。"}]
              }
            }
        """.trimIndent()

        val parsed = OnlineTranslator.parseYoudaoJson("charge", sampleJson)
        assertNotNull(parsed)
        assertEquals("tʃɑːdʒ", parsed!!.phoneticUk)
        assertEquals("tʃɑːrdʒ", parsed.phoneticUs)
        assertTrue(parsed.chineseMeaning.contains("充电"))
        assertTrue(parsed.chineseMeaning.contains("费用"))
        assertEquals(2, parsed.wordForms.size)
        assertEquals(1, parsed.senseGroups.size)
        assertEquals("Forgot to charge the battery.", parsed.senseGroups[0].senses[0].examples[0].english)
        assertEquals(1, parsed.collinsSenses.size)
        assertEquals("If you charge someone an amount of money. 收费", parsed.collinsSenses[0].explanation)
        assertEquals("in charge of", parsed.phrases[0].phrase)
        assertEquals(listOf("fee", "cost"), parsed.synonyms[0].words)
        assertEquals("charger", parsed.relatedWords[0].words[0].word)
        assertEquals(1, parsed.examples.size)
        assertTrue(parsed.isRichLoaded)
    }
}
