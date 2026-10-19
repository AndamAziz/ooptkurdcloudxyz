# Retrofit, OkHttp, Room, WorkManager and kotlinx.serialization ship their own consumer R8 rules.
# Keep the generated serializers of our DTOs (accessed reflectively by the converter factory).
-keepclassmembers class xyz.kurdcloud.englishmaster.core.network.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class xyz.kurdcloud.englishmaster.core.network.**$$serializer { *; }
