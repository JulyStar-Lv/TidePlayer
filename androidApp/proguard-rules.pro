# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class uniffi.** { *; }
-keep class com.sun.jna.** { *; }

# Keep JNI native method classes used by Rust DSP and native bridges
-keep class io.github.julystar.musicapp.core.audio.RustDspNative { *; }
-keepclassmembers class io.github.julystar.musicapp.core.audio.RustDspNative {
    native <methods>;
}

# Preserve JNA and UniFFI native method signatures
-keepattributes Signature,InnerClasses,EnclosingMethod
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window
-keepattributes LineNumberTable,SourceFile

# Temporary size-analysis rule; removed after the release baseline is measured.
-dontwarn android.os.ServiceManager

# Koin resolves bindings through runtime Kotlin class metadata. Preserve class names while
# retaining the rest of R8 shrinking and optimization for the release build.
-keepnames class **
