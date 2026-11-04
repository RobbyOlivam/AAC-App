import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// Shared by every version of the app: vocabulary, grammar, predictions, Open Board Format,
// usage reports. Pure Kotlin (no Java or Android APIs) so it builds for Android and iOS.
kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // iPad / iPhone. These need Apple's toolchain, so they build on macOS (CI's ios job);
    // elsewhere Gradle skips them and the JVM build still checks the shared code.
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    // The web app (installable on iPads from Safari).
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
