# ProGuard / R8 rules for 奶读 (NaiDu)

# Keep WebView JS bridge methods
-keep class com.lingoreader.app.ui.reader.ReaderJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Gson data models and generic TypeToken metadata
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class com.lingoreader.app.data.model.** { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

# Keep juniversalchardet charset probers
-keep class org.mozilla.universalchardet.** { *; }
-dontwarn org.mozilla.universalchardet.**
