package com.robbyolivam.aac.device

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.robbyolivam.aac.core.platform.DeviceLock

/** Amazon Fire tablets (Fire OS): settings live in different places than on other Android devices. */
val isFireTablet: Boolean get() = Build.MANUFACTURER.equals("Amazon", ignoreCase = true)

/** Android's "Lock to this app": screen pinning (lock task). */
class AndroidDeviceLock(private val activity: Activity) : DeviceLock {

    override val isLocked: Boolean
        get() {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
            return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        }

    override val setupHint: String
        get() = if (isFireTablet) {
            "Couldn't lock. Turn on Fire Settings → Security & Privacy → Screen Pinning (or App Pinning), then try again."
        } else {
            "Couldn't lock. Turn on Settings → Security → App pinning, then try again."
        }

    override fun lock() {
        runCatching { activity.startLockTask() }
        // Some devices (Fire tablets among them) only pin once pinning is switched on in system
        // settings. If it didn't take, say how.
        activity.window.decorView.postDelayed({
            if (!isLocked) Toast.makeText(activity, setupHint, Toast.LENGTH_LONG).show()
        }, 2_000)
    }

    override fun unlock() {
        runCatching { activity.stopLockTask() }
    }
}
