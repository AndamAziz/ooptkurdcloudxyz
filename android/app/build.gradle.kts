import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Public Supabase client configuration, in order of precedence: local.properties (git-ignored),
// environment variables, then the defaults in gradle.properties. Only the URL and the public
// anon/publishable key belong here — never a service_role or secret key.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun config(property: String, env: String, default: String): String =
    (localProperties.getProperty(property) ?: System.getenv(env) ?: providers.gradleProperty(default).orNull ?: "").trim()

val supabaseUrl = config("supabase.url", "SUPABASE_URL", "englishmaster.supabase.url")

// Release signing. The same key must sign every release, otherwise Android refuses the update.
// Supplied by keystore.properties (git-ignored) or environment variables in CI; never committed.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signing(property: String, env: String): String? = (keystoreProperties.getProperty(property) ?: System.getenv(env))?.trim()?.takeIf { it.isNotEmpty() }
val releaseStoreFile = signing("storeFile", "ANDROID_KEYSTORE_PATH")
val selfUpdate = providers.gradleProperty("englishmaster.selfUpdate").orNull?.toBoolean() ?: true
val supabaseAnonKey = config("supabase.anonKey", "SUPABASE_ANON_KEY", "englishmaster.supabase.publishableKey")

android {
    namespace = "xyz.kurdcloud.englishmaster"
    compileSdk { version = release(37) { minorApiLevel = 2 } }

    defaultConfig {
        applicationId = "app.englishmaster"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.7.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        // In-app updates are for direct distribution only; set englishmaster.selfUpdate=false for Play builds.
        buildConfigField("boolean", "SELF_UPDATE", selfUpdate.toString())
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signing("storePassword", "ANDROID_KEYSTORE_PASSWORD")
                keyAlias = signing("keyAlias", "ANDROID_KEY_ALIAS")
                keyPassword = signing("keyPassword", "ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // R8 stays off until the minified build has been verified on a device: a crash in a
            // release build would also break the in-app update path.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
        // Ship only the languages the app is translated into (drops library translations).
        localeFilters += listOf("en", "b+ckb")
    }

    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:practice"))
    implementation(project(":feature:admin"))
    implementation(project(":feature:exam"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
