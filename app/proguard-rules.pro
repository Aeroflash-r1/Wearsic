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

# Serialized/network models are mapped manually via org.json — no reflection,
# so no keep rules required for com.example.model / com.example.network.model.