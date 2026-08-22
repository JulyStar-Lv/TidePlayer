# Keep the desktop launcher and runtime boundaries that are accessed by name or
# from native/plugin code. The Compose Desktop plugin keeps MainKt separately.
-keep class uniffi.** { *; }
-keep class com.sun.jna.** { *; }
-keep class org.freedesktop.dbus.** { *; }
-keep class io.github.julystar.musicapp.di.** { *; }
-keep class io.github.julystar.musicapp.database.** { *; }
-keep class io.github.julystar.musicapp.plugin.runtime.** { *; }
-keep class io.github.julystar.musicapp.plugin.management.** { *; }

-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,
    RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations,
    AnnotationDefault,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Keep generated Kotlin serialization serializers and their metadata.
-keep class **$$serializer { *; }
-keepclassmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# The Release mapping is uploaded as a separate GitHub Release asset.
-printmapping 'build/compose/proguard/mapping.txt'
