// SPDX-License-Identifier: GPL-3.0-only
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "pl.ergenos.glosfacet"
    compileSdk = 35
    defaultConfig {
        applicationId = "pl.ergenos.glosfacet"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "OpenSpeech"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    signingConfigs { getByName("debug") { storeFile = rootProject.file(".tools/debug.keystore") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += listOf("onnx") }
    packaging { jniLibs { useLegacyPackaging = false } }
}
dependencies {
    implementation(files("libs/sherpa-onnx-1.12.20.aar"))
    implementation("androidx.core:core:1.15.0")
}
