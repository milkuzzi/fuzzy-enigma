# Add project specific ProGuard rules here.

# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.studydungeon.** {
    *** Companion;
}
-keepclasseswithmembers class com.studydungeon.** {
    kotlinx.serialization.KSerializer serializer(...);
}
