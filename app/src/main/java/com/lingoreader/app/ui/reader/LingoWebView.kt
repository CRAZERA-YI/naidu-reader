package com.lingoreader.app.ui.reader

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.gson.JsonParser

/**
 * Custom WebView that injects "翻译" and "记生词" directly into Android's native text-selection ActionMode menu.
 */
class LingoWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    var onActionTranslateSelected: ((selectedText: String, contextSentence: String) -> Unit)? = null
    var onActionSaveSelected: ((selectedText: String, contextSentence: String) -> Unit)? = null

    companion object {
        private const val MENU_ID_TRANSLATE = 99101
        private const val MENU_ID_SAVE = 99102
    }

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? {
        if (callback == null) return super.startActionMode(null)
        return super.startActionMode(wrapCallback(callback))
    }

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        if (callback == null) return super.startActionMode(null, type)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback is ActionMode.Callback2) {
            super.startActionMode(wrapCallback2(callback), type)
        } else {
            super.startActionMode(wrapCallback(callback), type)
        }
    }

    private fun wrapCallback(delegate: ActionMode.Callback): ActionMode.Callback {
        return object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val created = delegate.onCreateActionMode(mode, menu)
                addLingoMenuItems(menu)
                return created
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                val prepared = delegate.onPrepareActionMode(mode, menu)
                addLingoMenuItems(menu)
                return prepared
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return handleLingoMenuClick(mode, item) || delegate.onActionItemClicked(mode, item)
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                delegate.onDestroyActionMode(mode)
            }
        }
    }

    private fun wrapCallback2(delegate: ActionMode.Callback2): ActionMode.Callback2 {
        return object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val created = delegate.onCreateActionMode(mode, menu)
                addLingoMenuItems(menu)
                return created
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                val prepared = delegate.onPrepareActionMode(mode, menu)
                addLingoMenuItems(menu)
                return prepared
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return handleLingoMenuClick(mode, item) || delegate.onActionItemClicked(mode, item)
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                delegate.onDestroyActionMode(mode)
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                delegate.onGetContentRect(mode, view, outRect)
            }
        }
    }

    private fun addLingoMenuItems(menu: Menu) {
        if (menu.findItem(MENU_ID_TRANSLATE) == null) {
            menu.add(Menu.NONE, MENU_ID_TRANSLATE, 0, "译 翻译")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        if (menu.findItem(MENU_ID_SAVE) == null) {
            menu.add(Menu.NONE, MENU_ID_SAVE, 1, "★ 记生词")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
    }

    private fun handleLingoMenuClick(mode: ActionMode, item: MenuItem): Boolean {
        val isTranslate = item.itemId == MENU_ID_TRANSLATE
        val isSave = item.itemId == MENU_ID_SAVE
        if (!isTranslate && !isSave) return false

        evaluateJavascript("window.getSelectedWithContext()") { rawResult ->
            val parsed = parseJsJsonString(rawResult)
            if (parsed != null && parsed.first.isNotBlank()) {
                if (isTranslate) {
                    onActionTranslateSelected?.invoke(parsed.first, parsed.second)
                } else {
                    onActionSaveSelected?.invoke(parsed.first, parsed.second)
                }
            }
            evaluateJavascript("window.clearSelection()", null)
        }
        mode.finish()
        return true
    }

    private fun parseJsJsonString(raw: String?): Pair<String, String>? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            val unquoted = JsonParser.parseString(raw).asString
            val obj = JsonParser.parseString(unquoted).asJsonObject
            val text = obj.get("text")?.asString.orEmpty().trim()
            val sentence = obj.get("sentence")?.asString.orEmpty().trim()
            text to sentence
        }.getOrNull()
    }
}

class ReaderJsBridge(
    private val onWordTappedCb: (word: String, sentence: String) -> Unit,
    private val onBlankTappedCb: () -> Unit,
    private val onSelectionChangedCb: (text: String, sentence: String) -> Unit,
    private val onSelectionClearedCb: () -> Unit,
    private val onPageChangedCb: (currentPage: Int, totalPages: Int) -> Unit,
    private val onRequestNextChapterCb: () -> Unit,
    private val onRequestPrevChapterCb: () -> Unit
) {
    @JavascriptInterface
    fun onWordTapped(word: String, sentence: String) {
        onWordTappedCb(word, sentence)
    }

    @JavascriptInterface
    fun onBlankTapped() {
        onBlankTappedCb()
    }

    @JavascriptInterface
    fun onSelectionChanged(text: String, sentence: String) {
        onSelectionChangedCb(text, sentence)
    }

    @JavascriptInterface
    fun onSelectionCleared() {
        onSelectionClearedCb()
    }

    @JavascriptInterface
    fun onPageChanged(currentPage: Int, totalPages: Int) {
        onPageChangedCb(currentPage, totalPages)
    }

    @JavascriptInterface
    fun onRequestNextChapter() {
        onRequestNextChapterCb()
    }

    @JavascriptInterface
    fun onRequestPrevChapter() {
        onRequestPrevChapterCb()
    }
}
