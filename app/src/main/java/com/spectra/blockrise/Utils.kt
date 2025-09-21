package com.spectra.blockrise

import android.content.Context
import android.util.TypedValue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val BOARD_COLS = 24
const val BOARD_ROWS = 80
const val HIDDEN_ROWS = 2
const val BOARD_TOTAL_ROWS = BOARD_ROWS + HIDDEN_ROWS

const val RISE_INTERVAL_SEC = 6.0f
const val MIN_RISE_INTERVAL_SEC = 2.5f
const val RISE_DELAY_BONUS_SEC = 3.0f
const val CLASSIC_DIFFICULTY_STEP_SEC = 90f
const val CLASSIC_RISE_DECREMENT = 0.5f

const val GRAVITY_MS = 1200f
const val MEDIUM_GRAVITY_MS = 800f
const val FAST_GRAVITY_MS = 500f
const val LOCK_DELAY_MS = 500f

const val TARGET_FPS = 60
const val LOGIC_STEP_SEC = 1f / TARGET_FPS

fun clamp(value: Float, minValue: Float, maxValue: Float): Float = max(minValue, min(value, maxValue))

fun clamp(value: Int, minValue: Int, maxValue: Int): Int = max(minValue, min(value, maxValue))

fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t

fun easeInCubic(t: Float): Float = t * t * t

fun easeOutQuad(t: Float): Float = 1f - (1f - t) * (1f - t)

fun Context.dpToPx(dp: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

fun approximatelyEqual(a: Float, b: Float, tolerance: Float = 0.0001f): Boolean = abs(a - b) <= tolerance

inline fun <T> MutableList<T>.popEach(action: (T) -> Unit) {
    val iterator = iterator()
    while (iterator.hasNext()) {
        val value = iterator.next()
        iterator.remove()
        action(value)
    }
}
