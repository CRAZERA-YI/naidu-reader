package com.lingoreader.app.parser

import com.lingoreader.app.data.model.Book
import com.lingoreader.app.data.model.BookFormat
import com.lingoreader.app.data.model.ParsedBook
import java.io.File

object BookParserFactory {

    fun parseBook(book: Book): ParsedBook {
        val file = File(book.filePath)
        return parseFile(file, book.format, book.title)
    }

    fun parseFile(file: File, format: BookFormat, fallbackTitle: String): ParsedBook {
        return when (format) {
            BookFormat.TXT -> TxtParser.parse(file, fallbackTitle)
            BookFormat.MD -> MarkdownParser.parse(file, fallbackTitle)
            BookFormat.EPUB -> {
                val extractDir = File(file.parentFile, "extracted")
                EpubParser.parse(file, extractDir, fallbackTitle)
            }
        }
    }
}
