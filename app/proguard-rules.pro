# Wearsic — R8 / ProGuard rules (release builds)

# DownloadState enum constants are persisted as strings in the Room database
# (DownloadState.QUEUED.name, COMPLETED.name, ...). Their names must never be
# obfuscated, otherwise downloads recorded by a previous build would no longer
# match and the Downloads screen would misrender offline tracks.
-keepclassmembers enum com.example.data.db.DownloadState { *; }

# Keep readable file/line info in release stack traces for crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Library rules (should already be handled by consumer rules) ---
# Room: KSP-generated *_Impl classes are instantiated reflectively.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Media3 MediaSessionService is referenced via ComponentName in
# WearsicPlaybackController and started from the manifest.
-keep class com.example.media.WearsicMediaService { <init>(); }

# kotlinx.serialization (com.example.network.model, health, playlist DTOs):
# reified decodeFromString<T>() passes generated serializers directly, but R8
# full mode can still strip the generated serializer/companion when a code path
# is only reached at runtime (e.g. restored sessions on relaunch). Keep the
# generated serializer classes + annotation metadata so release builds never
# lose a serializer on any path.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.** {
    kotlinx.serialization.KSerializer serializer(...);
}