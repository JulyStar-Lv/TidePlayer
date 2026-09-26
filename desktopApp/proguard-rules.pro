# Keep the desktop launcher and runtime boundaries that are accessed by name or
# from native/plugin code. The Compose Desktop plugin also keeps MainKt, but keep
# the configured entry point explicitly so custom ProGuard configuration cannot
# accidentally rename or remove it.
-keep class io.github.julystar.musicapp.MainKt { *; }
-keep class uniffi.** { *; }
-keep class com.sun.jna.** { *; }
-keep class org.freedesktop.dbus.** { *; }
-keep class io.github.julystar.musicapp.di.** { *; }
-keep class io.github.julystar.musicapp.database.** { *; }
-keep class io.github.julystar.musicapp.plugin.runtime.** { *; }
-keep class io.github.julystar.musicapp.plugin.management.** { *; }

# The macOS title-bar bridge passes these structures and callback to JNA by
# reflection. Keep the annotated field names and callback method intact.
-keep class io.github.julystar.musicapp.NSPoint { *; }
-keep class io.github.julystar.musicapp.NSSize { *; }
-keep interface io.github.julystar.musicapp.DispatchCallback { *; }
-keep class * implements io.github.julystar.musicapp.DispatchCallback { *; }
-keep interface io.github.julystar.musicapp.core.presentation.platform.MacThemeNotificationCallback { *; }
-keep class * implements io.github.julystar.musicapp.core.presentation.platform.MacThemeNotificationCallback { *; }

# Room can resolve generated database implementations from the RoomDatabase
# class name. Keep Room database subclasses as a defensive JVM/Desktop boundary;
# the application database package above also protects TidePlayer's generated
# AppDatabase implementation and DAO wiring.
-keep class * extends androidx.room.RoomDatabase { *; }

# DataStore Preferences uses generated protobuf-lite message metadata that
# resolves fields such as preferences_ by their generated JVM names. Keep the
# generated PreferencesProto model and its relocated protobuf runtime intact.
-keep class androidx.datastore.preferences.PreferencesProto** { *; }
-keep class androidx.datastore.preferences.protobuf.** { *; }

# AndroidX DataStore's JVM consumer rule: GeneratedMessageLite metadata resolves
# generated message fields by name, so those fields must not be renamed/removed.
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# sqlite-bundled registers JNI methods against fixed JVM classes such as
# BundledSQLiteDriverKt. Keep the package boundary and, importantly, do not allow
# native methods to be shrunk before JNI registration runs.
-keep class androidx.sqlite.driver.bundled.** { *; }
-keepclasseswithmembers class androidx.sqlite.driver.bundled.** {
    native <methods>;
}

# Preserve metadata used by Kotlin serialization, generated code and libraries
# that inspect runtime annotations/signatures.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,
    RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations,
    AnnotationDefault,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Keep generated Kotlin serialization serializers and their serializer accessors.
# The repository does not currently use kotlin.reflect, ServiceLoader, dynamic
# ClassLoader loading, or named-companion serializer lookup on the Desktop path,
# so no broader application-wide reflection keep is required.
-keep class **$$serializer { *; }
-keepclassmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# The Release mapping is uploaded as a separate GitHub Release asset when
# Desktop ProGuard is enabled.
-printmapping 'build/compose/proguard/mapping.txt'
