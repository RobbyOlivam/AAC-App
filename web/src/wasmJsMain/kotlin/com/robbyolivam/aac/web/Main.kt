package com.robbyolivam.aac.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.robbyolivam.aac.shared.Platform
import com.robbyolivam.aac.ui.AacApp
import com.robbyolivam.aac.ui.AacViewModel
import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

const val WEB_VERSION = "0.13.0"

private const val INSTALL_HELP =
    "Install on iPad: open this page in Safari, tap Share, then \"Add to Home Screen\". " +
        "It then opens full screen and works offline. Updates arrive by themselves the next time it opens with internet."

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val platform = Platform(
        storage = WebStorage(),
        assets = WebAssets(),
        files = WebFiles(),
        speech = WebSpeech(),
        images = WebImages(),
        recorder = WebRecorder(),
        device = WebDevice(),
        installHelp = INSTALL_HELP,
        appVersion = WEB_VERSION,
        canVibrate = aac.device.canVibrate(),
    )
    val scope = MainScope()
    val vm = AacViewModel(platform, scope)
    scope.launch { vm.load() }
    document.getElementById("loading")?.remove()
    ComposeViewport(document.body!!) { AacApp(vm) }
}
