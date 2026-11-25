# ProGuard rules for LingoReader
-keep class com.lingoreader.app.ui.reader.ReaderJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes *Annotation*
