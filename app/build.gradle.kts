// SPDX-License-Identifier: GPL-3.0-only
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "pl.ergenos.glosfacet"
    compileSdk = 35
    ndkVersion = "28.2.13676358"
    defaultConfig {
        applicationId = "pl.ergenos.glosfacet"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "OpenSpeech"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    signingConfigs {
        getByName("debug") { storeFile = rootProject.file(".tools/debug.keystore") }
        create("release") {
            System.getenv("OPENSPEECH_STORE_FILE")?.let { storeFile = rootProject.file(it) }
            storePassword = System.getenv("OPENSPEECH_STORE_PASSWORD")
            keyAlias = System.getenv("OPENSPEECH_KEY_ALIAS")
            keyPassword = System.getenv("OPENSPEECH_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    androidResources { noCompress += listOf("onnx") }
    packaging { jniLibs { useLegacyPackaging = false } }
}
dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.23.2")
    implementation("androidx.core:core:1.15.0")
}

// Pin transitive dependencies; generate and review the lock before release.
dependencyLocking { lockAllConfigurations() }
val checkReleaseReadiness by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python3", "scripts/check-release.py")
    doFirst {
        listOf("OPENSPEECH_STORE_FILE", "OPENSPEECH_STORE_PASSWORD", "OPENSPEECH_KEY_ALIAS", "OPENSPEECH_KEY_PASSWORD").forEach {
            require(!System.getenv(it).isNullOrBlank()) { "Missing release signing setting: $it" }
        }
        require(rootProject.file(System.getenv("OPENSPEECH_STORE_FILE")).isFile) { "Release keystore does not exist" }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseReadiness) }

val verifySpeechAssets by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("python3", "scripts/prepare-assets.py", "--verify-only")
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(verifySpeechAssets) }
