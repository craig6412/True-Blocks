package com.spectra.blockrise

import android.content.Context
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

enum class GameMode {
    ZEN,
    CLASSIC_RELAX
}

enum class GravityProfile(val intervalMs: Float) {
    SLOW(GRAVITY_MS),
    MEDIUM(MEDIUM_GRAVITY_MS),
    FAST(FAST_GRAVITY_MS);

    fun next(): GravityProfile = when (this) {
        SLOW -> MEDIUM
        MEDIUM -> FAST
        FAST -> SLOW
    }
}

data class GameSettings(
    var zenMode: Boolean = true,
    var holdEnabled: Boolean = false,
    var showGrid: Boolean = true,
    var leftHanded: Boolean = false,
    var showTouchNudges: Boolean = true,
    var gravityProfile: GravityProfile = GravityProfile.SLOW,
    var musicVolume: Float = 0.25f,
    var sfxVolume: Float = 0.5f
)

/**
 * Simple overlay model. Stores toggle/slider hit areas so the renderer can draw a menu using the canvas.
 */
class SettingsOverlay(context: Context) {
    private val padding = context.dpToPx(12f)
    private val sliderHeight = context.dpToPx(8f)

    var isVisible: Boolean = false
        private set

    private val panelRect = RectF()
    private val items = mutableListOf<SettingItem>()

    fun toggle() {
        isVisible = !isVisible
    }

    fun hide() {
        isVisible = false
    }

    fun updateLayout(container: RectF) {
        panelRect.set(container)
        items.clear()
        val entries = listOf(
            SettingId.MODE,
            SettingId.GRAVITY,
            SettingId.SHOW_GRID,
            SettingId.HOLD,
            SettingId.LEFT_HANDED,
            SettingId.TOUCH_NUDGES,
            SettingId.MUSIC,
            SettingId.SFX
        )
        val rowHeight = panelRect.height() / entries.size
        var top = panelRect.top
        for (id in entries) {
            val rowRect = RectF(panelRect.left + padding, top + padding / 2f, panelRect.right - padding, top + rowHeight - padding / 2f)
            val sliderRect = if (id == SettingId.MUSIC || id == SettingId.SFX) {
                val sliderTop = rowRect.centerY() - sliderHeight / 2f
                RectF(rowRect.right - context.dpToPx(120f), sliderTop, rowRect.right, sliderTop + sliderHeight)
            } else {
                null
            }
            items += SettingItem(id, rowRect, sliderRect)
            top += rowHeight
        }
    }

    fun getPanelRect(): RectF = panelRect

    fun getItems(): List<SettingItem> = items

    fun handleTap(x: Float, y: Float, settings: GameSettings): Boolean {
        if (!panelRect.contains(x, y)) return false
        for (item in items) {
            if (!item.hitRect.contains(x, y)) continue
            when (item.id) {
                SettingId.MODE -> settings.zenMode = !settings.zenMode
                SettingId.SHOW_GRID -> settings.showGrid = !settings.showGrid
                SettingId.HOLD -> settings.holdEnabled = !settings.holdEnabled
                SettingId.LEFT_HANDED -> settings.leftHanded = !settings.leftHanded
                SettingId.TOUCH_NUDGES -> settings.showTouchNudges = !settings.showTouchNudges
                SettingId.GRAVITY -> settings.gravityProfile = settings.gravityProfile.next()
                SettingId.MUSIC -> {
                    val slider = item.sliderRect ?: continue
                    val ratio = ((x - slider.left) / slider.width()).coerceIn(0f, 1f)
                    settings.musicVolume = clampVolume(ratio)
                }
                SettingId.SFX -> {
                    val slider = item.sliderRect ?: continue
                    val ratio = ((x - slider.left) / slider.width()).coerceIn(0f, 1f)
                    settings.sfxVolume = clampVolume(ratio)
                }
            }
            return true
        }
        return true
    }

    data class SettingItem(
        val id: SettingId,
        val hitRect: RectF,
        val sliderRect: RectF?
    )

    enum class SettingId {
        MODE,
        HOLD,
        SHOW_GRID,
        LEFT_HANDED,
        TOUCH_NUDGES,
        MUSIC,
        SFX,
        GRAVITY
    }
}

fun GameSettings.mode(): GameMode = if (zenMode) GameMode.ZEN else GameMode.CLASSIC_RELAX

fun clampVolume(value: Float): Float = min(1f, max(0f, value))
