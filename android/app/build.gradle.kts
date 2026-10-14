plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.nedal.cinejoyremote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nedal.cinejoyremote"
        minSdk = 26
        targetSdk = 36
        versionCode = 500147
        versionName = "5.0.147"
    }

    // CI signed builds: env vars set by .github/workflows/release.yml.
    // Absent locally → no signing config → assembleDebug as usual.
    val ksPath = System.getenv("CINEJOY_KEYSTORE_PATH")
    if (!ksPath.isNullOrBlank() && file(ksPath).exists()) {
        signingConfigs {
            create("ciRelease") {
                storeFile = file(ksPath)
                storePassword = System.getenv("CINEJOY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CINEJOY_KEY_ALIAS")
                keyPassword = System.getenv("CINEJOY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("ciRelease")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    // Ktor HTTP engine (Android): supabase-kt brings ktor-client-core but no
    // engine. Without this the app crashes on launch with
    // "Failed to find HTTP client engine implementation".
    implementation(libs.ktor.client.android)
    implementation(libs.coil.compose)
    implementation(libs.material.icons.extended)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
}
