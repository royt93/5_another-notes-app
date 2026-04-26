
# kotlinx.serialization rules
# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Strip Log.d / Log.v in release builds (no-op the calls so strings are removed)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# Room — keep entity classes and DAO impls unobfuscated for the generated code paths
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# Google Mobile Ads SDK — keeps the runtime adapter loader happy
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.internal.ads.** { *; }
-keep public class * extends com.google.android.gms.ads.mediation.MediationAdapter
-keep public class * extends com.google.android.gms.ads.mediation.Adapter
-dontwarn com.google.android.gms.ads.**

# AppLovin mediation adapter
-keep class com.applovin.** { *; }
-keep class com.google.ads.mediation.applovin.** { *; }
-dontwarn com.applovin.**

# Play services common — service loader picks classes up reflectively
-keep class com.google.android.gms.common.** { *; }
