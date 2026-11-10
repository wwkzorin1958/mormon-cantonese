# Minification is disabled for this app (see app/build.gradle.kts), so this file is
# intentionally minimal. Keep it if minification is ever enabled.

-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class org.kcmcelia.bookofmormon.**$$serializer { *; }
-keepclassmembers class org.kcmcelia.bookofmormon.** {
    *** Companion;
}
-keepclasseswithmembers class org.kcmcelia.bookofmormon.** {
    kotlinx.serialization.KSerializer serializer(...);
}
