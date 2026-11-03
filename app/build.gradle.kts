import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

/** Path of the release keystore, when this build has one (see signingConfigs). */
val releaseKey: String? = System.getenv("AAC_KEYSTORE_FILE")?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "com.robbyolivam.aac"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.robbyolivam.aac"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "0.12.0"
    }

    signingConfigs {
        // Shared debug key (checked in on purpose) so test APKs built by CI and on any computer
        // can update each other without uninstalling.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The real app's key. It is never in the repository: CI gets it from GitHub secrets, and
        // a computer from environment variables (AAC_KEYSTORE_FILE, AAC_KEYSTORE_PASSWORD,
        // AAC_KEY_ALIAS, AAC_KEY_PASSWORD). Updates only install over an app signed with the
        // same key, so this key must never change or be lost.
        create("release") {
            releaseKey?.let { key ->
                storeFile = file(key)
                storePassword = System.getenv("AAC_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AAC_KEY_ALIAS") ?: "aac"
                keyPassword = System.getenv("AAC_KEY_PASSWORD") ?: System.getenv("AAC_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseKey != null) signingConfigs.getByName("release") else null
        }
        debug {
            // Test builds install next to the real app ("AAC Talk (dev)") with their own boards,
            // so trying a pull request never touches the boards someone relies on.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
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

dependencies {
    implementation(project(":core"))
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
