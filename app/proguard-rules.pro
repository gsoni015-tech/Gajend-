# Add project specific ProGuard / R8 rules here.
# Keep stacktrace metadata cleanly readable while shrinking and obfuscating release builds
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,Deprecated
-renamesourcefileattribute SourceFile

# Preserve BuildConfig so BuildConfig.DEBUG remains accurate in release & debug
-keep class com.example.BuildConfig { *; }

# Keep Kotlin reflection metadata & enums intact
-keep class kotlin.Metadata { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-dontwarn kotlinx.coroutines.**

# Kotlin Serialization (if used directly or transitively)
-dontwarn kotlinx.serialization.**
-keepattributes *Annotation*,InnerClasses
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.**$$serializer { *; }

# Start.io (StartApp In-App SDK 5.+)
-keep class com.startapp.** { *; }
-keep class com.truenet.** { *; }
-dontwarn com.startapp.**
-dontwarn com.truenet.**
-dontwarn android.webkit.JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class * extends android.webkit.WebViewClient { *; }
-keep class * extends android.webkit.WebChromeClient { *; }

# @Keep annotations (Firestore @IgnoreExtraProperties, CloudDocument, AppReleaseConfig, etc.)
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# App Data Models (Drive Sync, Room Entities, Update Config, PDF Editor/Render Models, Ads State)
-keep class com.example.data.cloud.** { *; }
-keep class com.example.data.local.** { *; }
-keep class com.example.update.AppReleaseConfig { *; }
-keep class com.example.update.UpdateCheckState { *; }
-keep class com.example.update.UpdateCheckState$* { *; }
-keep class com.example.pdf.DrawingPoint { *; }
-keep class com.example.pdf.DrawingStroke { *; }
-keep class com.example.pdf.TextAnnotation { *; }
-keep class com.example.pdf.SignatureAnnotation { *; }
-keep class com.example.pdf.StampType { *; }
-keep class com.example.pdf.StampAnnotation { *; }
-keep class com.example.pdf.WatermarkConfig { *; }
-keep class com.example.pdf.PageEditState { *; }
-keep class com.example.pdf.render.PdfPageRenderState { *; }
-keep class com.example.pdf.render.PdfRenderConfig { *; }
-keep class com.example.ads.BannerAdState { *; }

# Google Sign-In / Credential Manager / Play Services Auth
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# OkHttp / Okio / Retrofit / Moshi
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
