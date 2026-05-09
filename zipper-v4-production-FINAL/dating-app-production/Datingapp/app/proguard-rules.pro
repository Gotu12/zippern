# Zipper release / R8 rules — WebRTC, Firebase, Camera Kit, Firestore models.
# Keep file alongside `minifyEnabled true` on the release buildType.

# Readable stack traces in Firebase Crashlytics / Play Console (source file names obfuscated; lines kept).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- App Firestore / RTDB POJOs (Game Center, PK, live, etc.) ---
-keep class com.zipper.datingapp.data.** { *; }
-keep enum com.zipper.datingapp.data.GameType { *; }
-keep enum com.zipper.datingapp.data.BattleStatus { *; }
-keep enum com.zipper.datingapp.data.MysteryRewardType { *; }
-keepattributes *Annotation*, Signature, EnclosingMethod, InnerClasses
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- WebRTC ---
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

-keep class io.getstream.webrtc.** { *; }
-dontwarn io.getstream.webrtc.**

# --- Firebase (Auth token refresh, Firestore mappers) ---
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Crashlytics / uncaught exceptions
-keepattributes *Annotation*
-keep public class * extends java.lang.Throwable
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# --- Kotlin / coroutines (R8 default rules often suffice; keep for edge JNI) ---
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- Snap Camera Kit / lenses (heavy JNI + reflection) ---
-keep class com.snap.** { *; }
-dontwarn com.snap.**

# --- ML Kit (face) ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# --- Lottie (Compose) ---
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# --- Media3 / ExoPlayer ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- Play Services (Auth, Tasks) ---
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# --- Agora RTC SDK (JNI / reflection; matches SDK consumer proguard.txt) ---
-keep class io.agora.** { *; }
-dontwarn io.agora.**
