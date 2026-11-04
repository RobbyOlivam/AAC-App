plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// The web app: installable from Safari on iPads (Share → Add to Home Screen), works offline.
kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "aac"
        browser {
            commonWebpackConfig { outputFileName = "aac.js" }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":shared-ui"))
        }
    }
}

// The symbol pictures and help ship with the web app, as with the Android app.
kotlin.sourceSets.named("wasmJsMain") {
    resources.srcDir(rootProject.layout.projectDirectory.dir("app/src/main/assets"))
}
