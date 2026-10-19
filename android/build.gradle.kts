plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

// Robolectric normally downloads its Android runtime jar at test time, which is slow and
// fails behind rate-limited networks. Resolve it through Gradle instead and run offline.
val robolectricRuntime: Configuration by configurations.creating
dependencies { robolectricRuntime(libs.robolectric.android.all) }
val robolectricDir = layout.buildDirectory.dir("robolectric-deps")
val syncRobolectricRuntime = tasks.register<Sync>("syncRobolectricRuntime") {
    from(robolectricRuntime)
    into(robolectricDir)
}

subprojects {
    tasks.withType<Test>().configureEach {
        dependsOn(syncRobolectricRuntime)
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", robolectricDir.get().asFile.absolutePath)
    }
}
