package com.robbyolivam.aac.ui

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.robbyolivam.aac.data.ScanMode
import com.robbyolivam.aac.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Something a switch user can select. */
class ScanItem(val id: String, val label: String, val onSelect: () -> Unit)

/** A row of items: scanning highlights the group first, then its items. */
class ScanGroup(val id: String, val label: String, val items: List<ScanItem>)

/**
 * Row–column switch scanning.
 *
 * - [ScanMode.AUTO] (one switch): the highlight moves on a timer; any switch press selects.
 * - [ScanMode.STEP] (two switches): one switch moves the highlight, the other selects.
 *
 * The screen publishes its current [groups] on every composition; selection always restarts
 * from the first group so the user's scanning path stays predictable.
 */
class Scanner(private val scope: CoroutineScope, private val speak: (String) -> Unit) {
    var groups: List<ScanGroup> = emptyList()

    var activeGroup by mutableStateOf<String?>(null)
        private set
    var activeItem by mutableStateOf<String?>(null)
        private set

    private var settings = Settings()
    private var groupIndex = -1
    private var itemIndex = -1
    private var idleLoops = 0
    private var timer: Job? = null

    val enabled: Boolean get() = settings.scanMode != ScanMode.OFF

    fun configure(settings: Settings) {
        val modeChanged = settings.scanMode != this.settings.scanMode || settings.scanIntervalMs != this.settings.scanIntervalMs
        this.settings = settings
        if (modeChanged) {
            reset()
            restartTimer()
        }
    }

    fun isHighlighted(groupId: String, itemId: String): Boolean =
        activeGroup == groupId && (activeItem == null || activeItem == itemId)

    /** The "select" switch. */
    fun select() {
        if (!enabled) return
        val group = groups.getOrNull(groupIndex)
        when {
            group == null -> {
                // Paused or not started: the first press starts scanning.
                idleLoops = 0
                moveGroup()
            }
            itemIndex < 0 && group.items.size == 1 -> activate(group.items[0])
            itemIndex < 0 -> {
                itemIndex = 0
                publish()
            }
            else -> group.items.getOrNull(itemIndex)?.let(::activate)
        }
        restartTimer()
    }

    /** The "move" switch (two-switch step scanning). */
    fun move() {
        if (!enabled) return
        idleLoops = 0
        advance()
        restartTimer()
    }

    /** Routes hardware switch keys (most switch interfaces send Space/Enter or 1/2). */
    fun onKey(keyCode: Int): Boolean {
        if (!enabled) return false
        val isSelect = keyCode in SELECT_KEYS
        val isMove = keyCode in MOVE_KEYS
        if (!isSelect && !isMove) return false
        if (settings.scanMode == ScanMode.AUTO || isSelect) select() else move()
        return true
    }

    fun handles(keyCode: Int): Boolean = enabled && (keyCode in SELECT_KEYS || keyCode in MOVE_KEYS)

    private fun activate(item: ScanItem) {
        item.onSelect()
        reset()
        // Begin the next pass right away so the user doesn't have to restart it.
        moveGroup()
    }

    private fun advance() {
        val group = groups.getOrNull(groupIndex)
        if (group == null || itemIndex < 0) {
            moveGroup()
            return
        }
        itemIndex++
        if (itemIndex >= group.items.size) {
            // One full pass through a row without a selection: back out to rows.
            itemIndex = -1
        }
        publish()
    }

    private fun moveGroup() {
        if (groups.isEmpty()) return
        groupIndex++
        if (groupIndex >= groups.size) {
            groupIndex = 0
            idleLoops++
            if (settings.scanMode == ScanMode.AUTO && idleLoops >= settings.scanLoops) {
                // Stop cycling forever; the next press starts again.
                reset()
                return
            }
        }
        itemIndex = -1
        publish()
    }

    private fun publish() {
        val group = groups.getOrNull(groupIndex)
        activeGroup = group?.id
        val item = group?.items?.getOrNull(itemIndex)
        activeItem = item?.id
        if (settings.scanSpeakItems) {
            val cue = item?.label ?: group?.label
            if (!cue.isNullOrBlank()) speak(cue)
        }
    }

    private fun reset() {
        groupIndex = -1
        itemIndex = -1
        idleLoops = 0
        activeGroup = null
        activeItem = null
    }

    private fun restartTimer() {
        timer?.cancel()
        if (settings.scanMode != ScanMode.AUTO) return
        timer = scope.launch {
            while (isActive) {
                delay(settings.scanIntervalMs.toLong().coerceAtLeast(300))
                if (groupIndex >= 0) advance()
            }
        }
    }

    fun stop() {
        timer?.cancel()
        reset()
    }

    private companion object {
        val SELECT_KEYS = setOf(
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        )
        val MOVE_KEYS = setOf(
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_TAB,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_BUTTON_B,
        )
    }
}
