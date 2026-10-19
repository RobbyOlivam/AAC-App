package com.robbyolivam.aac

import android.content.pm.ActivityInfo
import android.os.Build
import android.view.KeyEvent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robbyolivam.aac.data.OrientationMode
import com.robbyolivam.aac.ui.AacViewModel
import com.robbyolivam.aac.ui.CommunicationScreen
import com.robbyolivam.aac.ui.Panel
import com.robbyolivam.aac.ui.Screen
import com.robbyolivam.aac.ui.SettingsScreen
import com.robbyolivam.aac.ui.theme.AacTheme

class MainActivity : ComponentActivity() {
    private val vm: AacViewModel by viewModels()

    /** Bluetooth/USB switches usually type Space/Enter (or 1/2); route them to scanning. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val scanningScreen = vm.scanner.enabled && vm.screen == Screen.MAIN && !vm.editMode &&
            vm.editing == null && !vm.searchOpen && vm.formsFor == null && !vm.showPartner
        if (scanningScreen) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && vm.scanner.onKey(event.keyCode)) return true
            if (event.action == KeyEvent.ACTION_UP && vm.scanner.handles(event.keyCode)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            LaunchedEffect(settings.orientation) {
                requestedOrientation = when (settings.orientation) {
                    OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
            LaunchedEffect(settings.keepScreenOn, settings.showOnLockScreen) {
                if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(settings.showOnLockScreen)
            }
            AacTheme(settings.themeMode) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    when (vm.screen) {
                        Screen.MAIN -> {
                            // The system back gesture mirrors the in-app Back button, but nobody needs it.
                            BackHandler(enabled = vm.panel == Panel.KEYBOARD || vm.currentBoardId != vm.vocabulary.value.homeBoardId) {
                                vm.goBack()
                            }
                            CommunicationScreen(vm)
                        }
                        Screen.SETTINGS -> SettingsScreen(vm)
                    }
                }
            }
        }
    }
}
