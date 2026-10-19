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
val supabaseAnonKey = config("supabase.anonKey", "SUPABASE_ANON_KEY", "englishmaster.supabase.publishableKey")

android {
    namespace = "xyz.kurdcloud.englishmaster"
    compileSdk { version = release(37) { minorApiLevel = 2 } }

    defaultConfig {
        applicationId = "app.englishmaster"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
