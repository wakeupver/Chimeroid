## Options
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
-keepattributes SourceFile,LineNumberTable,Signature,JavascriptInterface,Exceptions
-verbose

## Arch Components
-keep class * implements android.arch.lifecycle.GeneratedAdapter {<init>(...);}

## Fabric
-dontnote com.google.android.gms.**
-dontnote com.google.firebase.crash.FirebaseCrash

## Kotlin
-dontwarn kotlin.**
-dontnote kotlin.**
-dontwarn org.jetbrains.annotations.**
-keep class kotlin.Metadata { *; }
-keep class android.arch.lifecycle.**
-dontwarn kotlinx.coroutines.flow.**
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

## Okio
-dontwarn okio.**

## OkHttp
-dontwarn okhttp3.**
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
-dontnote com.android.org.conscrypt.SSLParametersImpl
-dontnote dalvik.system.CloseGuard
-dontnote sun.security.ssl.SSLContextImpl
-dontnote org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
-dontnote org.conscrypt.ConscryptEngineSocket

## Retrofit
-dontwarn retrofit2.Platform$Java8
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

## Moshi
-dontnote sun.misc.Unsafe

## Google API
-dontwarn com.google.api.client.json.jackson2.JacksonFactory
-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}

## Retrograde
-keep class **.model.**
-keepclassmembers class **.model.** {
  <init>(...);
  <fields>;
}

## Misc
-dontwarn com.uber.javaxextras.**
-dontwarn java.lang.management.**
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn junit.**
-dontwarn com.google.errorprone.**
-dontnote android.net.http.*

## Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.swordfish.chimeroid.**$$serializer { *; }
-keepclassmembers class com.swordfish.chimeroid.** {
    *** Companion;
}
-keepclasseswithmembers class com.swordfish.chimeroid.** {
    kotlinx.serialization.KSerializer serializer(...);
}

## LibretroDroid
-keep class com.swordfish.libretrodroid.** { *; }

## Dagger Worker injection (custom dagger-android extension for WorkManager;
## not part of Dagger's officially supported injection targets, so Dagger's
## own bundled consumer rules don't cover it the way they do for
## @ContributesAndroidInjector Activities/Services/Fragments)
-keep class com.swordfish.chimeroid.lib.injection.** { *; }
-keep @dagger.MapKey class * { *; }
-keep interface dagger.android.AndroidInjector { *; }
-keep interface dagger.android.AndroidInjector$Factory { *; }
-keep class * implements dagger.android.AndroidInjector { *; }
-keep class * implements dagger.android.AndroidInjector$Factory { *; }
-keep class com.swordfish.chimeroid.app.shared.library.CoreUpdateWork { *; }
-keep class com.swordfish.chimeroid.app.shared.library.CoreUpdateWork$* { *; }
-keep class com.swordfish.chimeroid.app.shared.library.LibraryIndexWork { *; }
-keep class com.swordfish.chimeroid.app.shared.library.LibraryIndexWork$* { *; }
-keep class com.swordfish.chimeroid.app.shared.savesync.SaveSyncWork { *; }
-keep class com.swordfish.chimeroid.app.shared.savesync.SaveSyncWork$* { *; }
-keep class com.swordfish.chimeroid.app.shared.storage.cache.CacheCleanerWork { *; }
-keep class com.swordfish.chimeroid.app.shared.storage.cache.CacheCleanerWork$* { *; }
-keep class com.swordfish.chimeroid.app.shared.covers.CoverArtSyncWorker { *; }
-keep class com.swordfish.chimeroid.app.shared.covers.CoverArtSyncWorker$* { *; }
