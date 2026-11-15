package com.robbyolivam.aac.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robbyolivam.aac.core.SymbolStyle
import com.robbyolivam.aac.ui.theme.AacTheme

/**
 * The whole app (what Android's MainActivity shows): the talker, Settings, the report and the
 * guide, with notes and progress drawn over them. Shared by every platform.
 */
@Composable
fun AacApp(vm: AacViewModel) {
    val settings by vm.settings.collectAsState()
    LaunchedEffect(settings.keepScreenOn) { vm.platform.device.keepScreenOn(settings.keepScreenOn) }
    AacTheme(settings.themeMode) {
        val tutorialAnchors = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
        CompositionLocalProvider(
            LocalSymbolLibrary provides vm.symbols,
            // No emoji font on the web: always the bundled symbols.
            LocalSymbolStyle provides SymbolStyle.LIBRARY,
            LocalTutorialAnchors provides tutorialAnchors,
            LocalHasEmojiFont provides vm.platform.hasEmojiFont,
        ) {
            val focus = remember { FocusRequester() }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                BoxWithConstraints(
                    Modifier.fillMaxSize()
                        // Bluetooth/USB switches usually type Space/Enter (or 1/2); route them to scanning.
                        .onPreviewKeyEvent { event ->
                            val scanningScreen = vm.scanner.enabled && vm.screen == Screen.MAIN && !vm.editMode &&
                                vm.editing == null && !vm.searchOpen && vm.formsFor == null && !vm.showPartner
                            when {
                                !scanningScreen -> false
                                event.type == KeyEventType.KeyDown -> vm.scanner.onKey(event.key)
                                event.type == KeyEventType.KeyUp -> vm.scanner.handles(event.key)
                                else -> false
                            }
                        }
                        .focusRequester(focus)
                        .focusable(),
                ) {
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    CompositionLocalProvider(LocalSmallestWidthDp provides minOf(maxWidth.value, maxHeight.value).toInt()) {
                        if (vm.loaded) {
                            when (vm.screen) {
                                Screen.MAIN -> CommunicationScreen(vm)
                                Screen.SETTINGS -> SettingsScreen(vm)
                                Screen.REPORT -> ReportScreen(vm)
                                Screen.HELP -> HelpScreen(vm)
                            }
                        }
                        vm.busy?.let { BusyOverlay(it) }
                        vm.notice?.let { NoticeBar(it) { vm.notice = null } }
                    }
                }
            }
        }
    }
}

@Composable
private fun BusyOverlay(text: String) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp) {
            Text(text, Modifier.padding(28.dp), fontSize = 20.sp)
        }
    }
}

/** A note drawn over the screen (never pushing the board down); goes away by itself or with OK. */
@Composable
private fun NoticeBar(text: String, onDismiss: () -> Unit) {
    LaunchedEffect(text) {
        kotlinx.coroutines.delay(6000)
        onDismiss()
    }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.inverseSurface, tonalElevation = 8.dp) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text, color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 17.sp, modifier = Modifier.weight(1f, fill = false))
                TextButton(onClick = onDismiss) { Text("OK", color = MaterialTheme.colorScheme.inversePrimary, fontSize = 17.sp) }
            }
        }
    }
}
