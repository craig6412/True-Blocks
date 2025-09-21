package com.spectra.blockrise

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import java.util.ArrayDeque

/**
 * Translates touch gestures and on-screen buttons into discrete game commands.
 */
class InputController(context: Context) : GestureDetector.SimpleOnGestureListener() {
    private val lock = Any()
    private val commandQueue: ArrayDeque<InputCommand> = ArrayDeque()
    private val gestureDetector = GestureDetector(context, this)
    private val swipeThresholdPx = context.dpToPx(32f)
    private val tapSlopPx = context.dpToPx(16f)
    private val buttonRepeatInterval = 0.18f
    private val tapMaxDurationMs = 250L
    private val longPressThresholdSec = 0.6f

    private var hudLayout: HudLayout? = null
    private var accumulatedScroll = 0f
    private var buttonConsumedTap = false
    private var activeButton: ActiveButton? = null
    private var buttonRepeatTimer = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime: Long = 0
    private var pointerDown = false
    private var pointerDownDuration = 0f
    private var longPressTriggered = false

    fun setHudLayout(layout: HudLayout) {
        hudLayout = layout
    }

    fun update(deltaSec: Float) {
        val button = activeButton
        if (button != null) {
            buttonRepeatTimer += deltaSec
            if (buttonRepeatTimer >= buttonRepeatInterval) {
                buttonRepeatTimer -= buttonRepeatInterval
                when (button) {
                    ActiveButton.LEFT -> queueCommand(InputCommand.MoveLeft)
                    ActiveButton.RIGHT -> queueCommand(InputCommand.MoveRight)
                }
            }
        }
        if (pointerDown && !longPressTriggered) {
            pointerDownDuration += deltaSec
            if (pointerDownDuration >= longPressThresholdSec) {
                longPressTriggered = true
                queueCommand(InputCommand.Hold)
            }
        }
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.downTime
                accumulatedScroll = 0f
                pointerDownDuration = 0f
                longPressTriggered = false
                buttonConsumedTap = handleButtons(event)
                pointerDown = !buttonConsumedTap
                if (buttonConsumedTap) {
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeButton = null
                buttonRepeatTimer = 0f
                pointerDown = false
                pointerDownDuration = 0f
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    buttonConsumedTap = false
                }
                if (buttonConsumedTap) {
                    buttonConsumedTap = false
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeButton != null) {
                    handleButtons(event)
                    return true
                }
                if (pointerDown && !longPressTriggered) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    val distanceSq = dx * dx + dy * dy
                    if (distanceSq > tapSlopPx * tapSlopPx) {
                        pointerDown = false
                    }
                }
            }
        }
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && !buttonConsumedTap) {
            val dx = event.x - downX
            val dy = event.y - downY
            if (dx * dx + dy * dy <= tapSlopPx * tapSlopPx &&
                event.eventTime - downTime <= tapMaxDurationMs
            ) {
                queueCommand(InputCommand.RotateCW)
            }
        }
        return true
    }

    override fun onDown(e: MotionEvent): Boolean {
        return true
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        // Single tap already handled in ACTION_UP guard to respect tap slop and duration.
        return true
    }

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        accumulatedScroll += -distanceX
        while (accumulatedScroll >= swipeThresholdPx) {
            queueCommand(InputCommand.MoveRight)
            accumulatedScroll -= swipeThresholdPx
        }
        while (accumulatedScroll <= -swipeThresholdPx) {
            queueCommand(InputCommand.MoveLeft)
            accumulatedScroll += swipeThresholdPx
        }
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        // Long press is handled manually to honour the 600 ms threshold.
    }

    private fun handleButtons(event: MotionEvent): Boolean {
        val layout = hudLayout ?: return false
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (layout.pauseButton.contains(x, y)) {
                    queueCommand(InputCommand.TogglePause)
                    return true
                }
                if (layout.settingsButton.contains(x, y)) {
                    queueCommand(InputCommand.ToggleSettings)
                    return true
                }
                if (!layout.leftButton.isEmpty && layout.leftButton.contains(x, y)) {
                    activeButton = ActiveButton.LEFT
                    buttonRepeatTimer = 0f
                    queueCommand(InputCommand.MoveLeft)
                    return true
                }
                if (!layout.rightButton.isEmpty && layout.rightButton.contains(x, y)) {
                    activeButton = ActiveButton.RIGHT
                    buttonRepeatTimer = 0f
                    queueCommand(InputCommand.MoveRight)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val button = activeButton
                if (button != null) {
                    val targetRect = if (button == ActiveButton.LEFT) layout.leftButton else layout.rightButton
                    if (!targetRect.contains(x, y)) {
                        activeButton = null
                        buttonConsumedTap = false
                    } else {
                        buttonConsumedTap = true
                    }
                    return true
                }
            }
        }
        return false
    }

    fun pollCommands(consumer: (InputCommand) -> Unit) {
        synchronized(lock) {
            while (commandQueue.isNotEmpty()) {
                consumer(commandQueue.removeFirst())
            }
        }
    }

    private fun queueCommand(command: InputCommand) {
        synchronized(lock) {
            commandQueue.addLast(command)
        }
    }

    data class HudLayout(
        val pauseButton: android.graphics.RectF,
        val settingsButton: android.graphics.RectF,
        val leftButton: android.graphics.RectF,
        val rightButton: android.graphics.RectF
    )

    private enum class ActiveButton { LEFT, RIGHT }
}

sealed class InputCommand {
    object MoveLeft : InputCommand()
    object MoveRight : InputCommand()
    object RotateCW : InputCommand()
    object Hold : InputCommand()
    object TogglePause : InputCommand()
    object ToggleSettings : InputCommand()
}
