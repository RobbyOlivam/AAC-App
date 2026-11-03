package com.robbyolivam.aac.web

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.robbyolivam.aac.shared.AacApp
import com.robbyolivam.aac.shared.Platform
import com.robbyolivam.aac.shared.Talker
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

const val WEB_VERSION = "0.12.0"

private const val INSTALL_HELP =
    "Install on iPad: open this page in Safari, tap Share, then \"Add to Home Screen\". " +
        "It then opens full screen and works offline. Updates arrive by themselves the next time it opens with internet. " +
        "To keep someone in the talker, use Guided Access (iPad Settings → Accessibility → Guided Access, then triple-click the top button)."

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val platform = Platform(
        storage = WebStorage(),
        assets = WebAssets(),
        files = WebFiles(),
        speech = WebSpeech(),
        installHelp = INSTALL_HELP,
        appVersion = WEB_VERSION,
    )
    val scope = MainScope()
    val talker = Talker(platform, scope)
    scope.launch { talker.load() }
    document.getElementById("loading")?.remove()
    ComposeViewport(document.body!!) {
        AacApp(talker, platform, systemDark = isSystemInDarkTheme())
    }
}
