plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

// The web app's build tools: Node.js and Yarn come from the machine (CI runners have both);
// Binaryen downloads through the repository declared in settings.gradle.kts. Kotlin's own
// download sources would bypass the repository rules there.
allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().download.set(false)
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().download.set(false)
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec>().download.set(false)
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.binaryen.BinaryenRootPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.js.binaryen.BinaryenRootEnvSpec>().downloadBaseUrl.set(null as String?)
    }
}
