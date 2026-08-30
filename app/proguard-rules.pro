# ── R8 / Kotlin compatibility ─────────────────────────────────────────────────
# Disable R8 code optimizations — they break Kotlin reified type parameters
# inside Firebase Firestore SDK (causes "There is no way to get here" runtime crash).
# Shrinking (dead code removal) and obfuscation (renaming) are still active.
-dontoptimize

# ── Kotlin ───────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keep class kotlin.jvm.internal.** { *; }
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# ── Coroutines ────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ── Room ──────────────────────────────────────────────────────────────────────
# Keep all Room entity + DAO classes so R8 doesn't remove generated implementations
-keep class com.example.vespatacho.data.** { *; }

# ── Firebase Firestore ────────────────────────────────────────────────────────
# Keep data model classes mapped to/from Firestore documents
-keepclassmembers class com.example.vespatacho.data.GasReading { *; }
-keepclassmembers class com.example.vespatacho.data.Vehicle { *; }
-keepclassmembers class com.example.vespatacho.data.DetectionSample { *; }

# Firestore uses reflection for serialisation
-keep class com.google.firebase.firestore.** { *; }
-dontwarn com.google.firebase.firestore.**

# ── Firebase Auth ─────────────────────────────────────────────────────────────
-keep class com.google.firebase.auth.** { *; }
-dontwarn com.google.firebase.auth.**

# ── Firebase Storage ──────────────────────────────────────────────────────────
-keep class com.google.firebase.storage.** { *; }
-dontwarn com.google.firebase.storage.**

# ── Firebase common / Google Play Services ────────────────────────────────────
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-keep class com.google.firebase.** { *; }

# ── ML Kit Text Recognition ───────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.odml.** { *; }
-dontwarn com.google.android.odml.**

# ── OkHttp / Retrofit (used by githubAppUpdate) ───────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# ── githubAppUpdate models ─────────────────────────────────────────────────────
-keep class info.hannes.github.** { *; }
-keepclassmembers class info.hannes.github.model.** { *; }

# ── Gson (used by githubAppUpdate for JSON parsing) ───────────────────────────
-keepattributes *Annotation*
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }

# ── Timber ────────────────────────────────────────────────────────────────────
-dontwarn org.jetbrains.annotations.**

# ── Compose ───────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── BuildConfig ───────────────────────────────────────────────────────────────
-keep class com.example.vespatacho.BuildConfig { *; }

# ── General Android ───────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends androidx.appcompat.app.AppCompatActivity
