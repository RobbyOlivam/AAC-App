import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.robbyolivam.aac"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.robbyolivam.aac"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
        // Neural voices run on ARM phones and tablets; this keeps x86 engine copies out of the APK.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    packaging {
        // Compress the voice engine's native libraries in the APK (smaller download).
        jniLibs.useLegacyPackaging = true
    }

    signingConfigs {
        // Shared debug key (checked in on purpose) so APKs built by CI and on any computer
        // can update each other without uninstalling — uninstalling would wipe the user's words.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
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
    }

    testOptions {
        // Robolectric screenshot tests render the real Compose UI on the JVM.
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

/**
 * The sherpa-onnx speech engine (Apache-2.0, https://github.com/k2-fsa/sherpa-onnx) isn't on
 * Maven Central, so the official Android AAR is fetched from the project's Hugging Face mirror
 * on first build and checked against a pinned SHA-256.
 */
val sherpaVersion = "1.12.21"
val sherpaSha256 = "4ce353cda9ab9e4c2e398dfd45aeb43cca1b5f97279051d354ebcdbd043e74ef"
val sherpaAar = layout.buildDirectory.file("libs/sherpa-onnx-$sherpaVersion.aar").get().asFile
fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
if (!sherpaAar.exists() || sha256(sherpaAar) != sherpaSha256) {
    sherpaAar.parentFile.mkdirs()
    val tmp = File(sherpaAar.path + ".part")
    URI("https://huggingface.co/csukuangfj/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-$sherpaVersion.aar")
        .toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
    check(sha256(tmp) == sherpaSha256) { "sherpa-onnx AAR checksum mismatch" }
    tmp.renameTo(sherpaAar)
}

dependencies {
    implementation(project(":core"))
    implementation(files(sherpaAar))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidsvg)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
