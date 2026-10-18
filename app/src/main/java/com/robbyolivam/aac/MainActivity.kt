package com.robbyolivam.aac

import android.os.Build
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
import com.robbyolivam.aac.ui.AacViewModel
import com.robbyolivam.aac.ui.CommunicationScreen
import com.robbyolivam.aac.ui.Panel
import com.robbyolivam.aac.ui.Screen
import com.robbyolivam.aac.ui.SettingsScreen
import com.robbyolivam.aac.ui.theme.AacTheme

class MainActivity : ComponentActivity() {
    private val vm: AacViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            LaunchedEffect(settings.keepScreenOn, settings.showOnLockScreen) {
                if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(settings.showOnLockScreen)
            }
            AacTheme(settings.themeMode) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    when (vm.screen) {
                        Screen.MAIN -> {
                            // Back never leaves the app by accident: it walks back through boards first.
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
