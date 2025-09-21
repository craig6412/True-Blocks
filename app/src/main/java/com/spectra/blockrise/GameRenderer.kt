package com.spectra.blockrise

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.content.ContextCompat
import java.util.Arrays
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Responsible for drawing the playfield, HUD, and overlays onto the SurfaceView canvas.
 */
class GameRenderer(private val context: Context) {
    private val backgroundPaint = Paint()
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPath = Path()
    private val gearPath = Path()

    private val boardRect = RectF()
    private val nextRect = RectF()
    private val holdRect = RectF()
    private val pauseRect = RectF()
    private val settingsRect = RectF()
    private val leftButtonRect = RectF()
    private val rightButtonRect = RectF()
    private val settingsPanelRect = RectF()
    private val tempRect = RectF()

    private val hudLayout = InputController.HudLayout(pauseRect, settingsRect, leftButtonRect, rightButtonRect)

    private var cellSize = 0f
    private var cellPadding = context.dpToPx(1.5f)
    private val cornerRadius = context.dpToPx(6f)
    private val buttonCorner = context.dpToPx(18f)
    private val lineFade = FloatArray(BOARD_ROWS)

    private val cellColors = intArrayOf(
        0,
        Color.parseColor("#7FC8F8"),
        Color.parseColor("#F79D65"),
        Color.parseColor("#F8E16C"),
        Color.parseColor("#F6E4CB"),
        Color.parseColor("#81E6D9"),
        Color.parseColor("#C4B5FD"),
        Color.parseColor("#F472B6"),
        Color.parseColor("#A6774E")
    )

    init {
        backgroundPaint.color = ContextCompat.getColor(context, R.color.wood_background)
        boardPaint.color = ContextCompat.getColor(context, R.color.wood_panel)
        gridPaint.color = ContextCompat.getColor(context, R.color.grid_line)
        gridPaint.style = Paint.Style.STROKE
        gridPaint.strokeWidth = context.dpToPx(0.5f)
        cellPaint.style = Paint.Style.FILL
        textPaint.color = ContextCompat.getColor(context, R.color.hud_text)
        textPaint.textSize = context.dpToPx(18f)
        textPaint.isAntiAlias = true
        subTextPaint.color = ContextCompat.getColor(context, R.color.hud_text)
        subTextPaint.textSize = context.dpToPx(14f)
        buttonPaint.color = Color.argb(110, 0, 0, 0)
        panelPaint.color = Color.argb(200, 255, 255, 255)
        panelStroke.color = Color.argb(40, 0, 0, 0)
        panelStroke.style = Paint.Style.STROKE
        panelStroke.strokeWidth = context.dpToPx(1.5f)
        overlayTextPaint.color = Color.DKGRAY
        overlayTextPaint.textSize = context.dpToPx(15f)
        iconPaint.color = Color.WHITE
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = context.dpToPx(2f)
        iconPaint.isAntiAlias = true
        fadePaint.style = Paint.Style.FILL
    }

    fun updateLayout(width: Int, height: Int, settings: GameSettings, overlay: SettingsOverlay) {
        val padding = context.dpToPx(18f)
        val maxBoardWidth = width * 0.68f
        val maxBoardHeight = height - padding * 2f
        val rawCell = min(maxBoardWidth / BOARD_COLS, maxBoardHeight / BOARD_ROWS)
        cellSize = rawCell
        cellPadding = min(cellSize * 0.12f, context.dpToPx(1.8f))
        val boardActualWidth = cellSize * BOARD_COLS
        val boardActualHeight = cellSize * BOARD_ROWS
        val boardLeft = if (settings.leftHanded) width - padding - boardActualWidth else padding
        val boardTop = (height - boardActualHeight) / 2f
        boardRect.set(boardLeft, boardTop, boardLeft + boardActualWidth, boardTop + boardActualHeight)

        val panelWidth = max(context.dpToPx(130f), width - boardRect.right - padding * 1.2f)
        val panelLeft = if (settings.leftHanded) padding else boardRect.right + padding / 2f
        val panelRight = min(width - padding, panelLeft + panelWidth)
        val panelTop = boardRect.top
        val previewHeight = cellSize * 4.5f
        holdRect.set(panelLeft, panelTop, panelRight, panelTop + previewHeight)
        nextRect.set(panelLeft, holdRect.bottom + padding, panelRight, holdRect.bottom + padding + previewHeight * 1.2f)

        val buttonSize = context.dpToPx(44f)
        val buttonOffset = context.dpToPx(12f)
        if (settings.leftHanded) {
            pauseRect.set(boardRect.left - buttonSize - buttonOffset, boardRect.top, boardRect.left - buttonOffset, boardRect.top + buttonSize)
            settingsRect.set(pauseRect.right + buttonOffset, boardRect.top, pauseRect.right + buttonOffset + buttonSize, boardRect.top + buttonSize)
        } else {
            pauseRect.set(boardRect.right + buttonOffset, boardRect.top, boardRect.right + buttonOffset + buttonSize, boardRect.top + buttonSize)
            settingsRect.set(pauseRect.right + buttonOffset, boardRect.top, pauseRect.right + buttonOffset + buttonSize, boardRect.top + buttonSize)
        }

        val bottomMargin = context.dpToPx(28f)
        val buttonY = height - buttonSize - bottomMargin
        if (settings.showTouchNudges) {
            leftButtonRect.set(padding, buttonY, padding + buttonSize, buttonY + buttonSize)
            rightButtonRect.set(width - padding - buttonSize, buttonY, width - padding, buttonY + buttonSize)
        } else {
            leftButtonRect.setEmpty()
            rightButtonRect.setEmpty()
        }
        hudLayout.pauseButton.set(pauseRect)
        hudLayout.settingsButton.set(settingsRect)
        hudLayout.leftButton.set(leftButtonRect)
        hudLayout.rightButton.set(rightButtonRect)

        settingsPanelRect.set(panelLeft, nextRect.bottom + padding, panelRight, boardRect.bottom)
        overlay.updateLayout(settingsPanelRect)
    }

    fun hudLayout(): InputController.HudLayout = hudLayout

    fun draw(canvas: Canvas, state: GameState, overlay: SettingsOverlay) {
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), backgroundPaint)
        drawBoard(canvas, state)
        drawHeldAndNext(canvas, state)
        drawHud(canvas, state)
        drawButtons(canvas)
        if (overlay.isVisible) {
            drawSettingsOverlay(canvas, overlay, state.settings)
        }
    }

    private fun drawBoard(canvas: Canvas, state: GameState) {
        canvas.save()
        if (state.lockPulse > 0f) {
            val centerX = boardRect.centerX()
            val centerY = boardRect.centerY()
            val scale = 1f + state.lockPulse * 0.1f
            canvas.translate(centerX, centerY)
            canvas.scale(scale, scale)
            canvas.translate(-centerX, -centerY)
        }
        canvas.drawRoundRect(boardRect, cornerRadius, cornerRadius, boardPaint)

        val riseOffset = if (state.risingAnimation.active) {
            val progress = state.risingAnimation.progress()
            (1f - easeOutQuad(progress)) * cellSize
        } else 0f

        val dropProgress = if (state.avalancheAnimation.active) state.avalancheAnimation.progress() else 1f
        val dropEase = easeInCubic(dropProgress)
        java.util.Arrays.fill(lineFade, 0f)
        for (anim in state.lineClearAnimations) {
            val visibleRow = anim.absoluteRow - HIDDEN_ROWS
            if (visibleRow in 0 until BOARD_ROWS) {
                val t = clamp(anim.elapsed / anim.duration, 0f, 1f)
                lineFade[visibleRow] = max(lineFade[visibleRow], 1f - t)
            }
        }

        if (state.settings.showGrid) {
            val step = cellSize
            var x = boardRect.left
            while (x <= boardRect.right + 1f) {
                canvas.drawLine(x, boardRect.top, x, boardRect.bottom, gridPaint)
                x += step
            }
            var y = boardRect.top
            while (y <= boardRect.bottom + 1f) {
                canvas.drawLine(boardRect.left, y, boardRect.right, y, gridPaint)
                y += step
            }
        }

        val startY = boardRect.top + riseOffset
        for (row in 0 until BOARD_ROWS) {
            val actualRow = row + HIDDEN_ROWS
            val baseY = startY + row * cellSize
            val fade = lineFade[row]
            for (col in 0 until BOARD_COLS) {
                val value = state.grid.visibleCell(col, row)
                if (value == 0) continue
                val dropDistance = if (state.avalancheAnimation.active) state.grid.consumeDropDistance(col, actualRow) else 0
                val dropOffset = if (dropDistance > 0) dropDistance * cellSize * (1f - dropEase) else 0f
                val dustOffset = fade * cellSize * 0.25f
                val left = boardRect.left + col * cellSize + cellPadding
                val top = baseY + cellPadding - dropOffset - dustOffset
                val right = boardRect.left + (col + 1) * cellSize - cellPadding
                val bottom = baseY + cellSize - cellPadding - dropOffset - dustOffset
                cellPaint.color = resolveColor(value)
                canvas.drawRoundRect(left, top, right, bottom, cellSize * 0.2f, cellSize * 0.2f, cellPaint)
                if (fade > 0f) {
                    fadePaint.color = Color.argb((fade * 180).toInt(), 255, 255, 255)
                    canvas.drawRoundRect(left, top, right, bottom, cellSize * 0.2f, cellSize * 0.2f, fadePaint)
                }
            }
        }

        val active = state.activePiece
        if (active != null) {
            cellPaint.color = resolveColor(active.tetromino.colorId)
            active.forEachCell { col, row ->
                if (row < HIDDEN_ROWS) return@forEachCell
                val left = boardRect.left + col * cellSize + cellPadding
                val top = startY + (row - HIDDEN_ROWS) * cellSize + cellPadding
                val right = left + cellSize - cellPadding * 2
                val bottom = top + cellSize - cellPadding * 2
                canvas.drawRoundRect(left, top, right, bottom, cellSize * 0.2f, cellSize * 0.2f, cellPaint)
            }
        }
        canvas.restore()
    }

    private fun drawHeldAndNext(canvas: Canvas, state: GameState) {
        panelPaint.color = Color.argb(180, 255, 255, 255)
        canvas.drawRoundRect(holdRect, cornerRadius, cornerRadius, panelPaint)
        canvas.drawRoundRect(nextRect, cornerRadius, cornerRadius, panelPaint)
        canvas.drawRoundRect(holdRect, cornerRadius, cornerRadius, panelStroke)
        canvas.drawRoundRect(nextRect, cornerRadius, cornerRadius, panelStroke)

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(context.getString(R.string.hud_hold), holdRect.left + cellPadding, holdRect.top + textPaint.textSize + cellPadding, textPaint)
        canvas.drawText(context.getString(R.string.hud_next), nextRect.left + cellPadding, nextRect.top + textPaint.textSize + cellPadding, textPaint)

        val holdPiece = state.holdPiece
        if (holdPiece != null) {
            drawPreview(canvas, holdRect, holdPiece)
        } else {
            subTextPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("--", holdRect.centerX(), holdRect.centerY(), subTextPaint)
        }
        state.forEachPreview { index, tetromino ->
            val rectTop = nextRect.top + cellPadding + index * (cellSize * 3.1f)
            tempRect.set(nextRect.left + cellPadding, rectTop, nextRect.right - cellPadding, rectTop + cellSize * 2.5f)
            drawPreview(canvas, tempRect, tetromino)
        }
    }

    private fun drawPreview(canvas: Canvas, rect: RectF, tetromino: Tetromino) {
        val miniCell = min(rect.width(), rect.height()) / 4f
        val originX = rect.left + (rect.width() - miniCell * 4f) / 2f
        val originY = rect.top + (rect.height() - miniCell * 4f) / 2f
        cellPaint.color = resolveColor(tetromino.colorId)
        tetromino.forEachCell(0, 0, 0) { x, y ->
            val left = originX + x * miniCell + miniCell * 0.2f
            val top = originY + y * miniCell + miniCell * 0.2f
            val right = left + miniCell - miniCell * 0.4f
            val bottom = top + miniCell - miniCell * 0.4f
            canvas.drawRoundRect(left, top, right, bottom, miniCell * 0.25f, miniCell * 0.25f, cellPaint)
        }
    }

    private fun drawHud(canvas: Canvas, state: GameState) {
        textPaint.textAlign = Paint.Align.LEFT
        val scoreText = "${context.getString(R.string.hud_score)} ${state.score}"
        val linesText = "${context.getString(R.string.hud_lines)} ${state.totalLinesCleared}"
        val chainText = "${context.getString(R.string.hud_chain)} ${state.chainDisplay}"
        val baseX = boardRect.left
        val baseY = boardRect.top - context.dpToPx(24f)
        canvas.drawText(scoreText, baseX, baseY, textPaint)
        canvas.drawText(linesText, baseX + boardRect.width() * 0.35f, baseY, textPaint)
        canvas.drawText(chainText, baseX + boardRect.width() * 0.65f, baseY, textPaint)

        val modeText = when (state.gameMode) {
            GameMode.ZEN -> context.getString(R.string.mode_zen)
            GameMode.CLASSIC_RELAX -> context.getString(R.string.mode_classic)
        }
        subTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(modeText, boardRect.right, boardRect.bottom + context.dpToPx(20f), subTextPaint)

        val riseInterval = state.currentRiseInterval()
        val riseRemaining = if (state.isRiseSuspended()) 0f else state.riseCountdown()
        val riseProgress = if (riseInterval <= 0f) 0f else clamp(1f - riseRemaining / riseInterval, 0f, 1f)
        tempRect.set(nextRect.left, nextRect.bottom + context.dpToPx(8f), nextRect.right, nextRect.bottom + context.dpToPx(28f))
        panelPaint.color = Color.argb(40, 0, 0, 0)
        canvas.drawRoundRect(tempRect, cornerRadius, cornerRadius, panelPaint)
        panelPaint.color = Color.argb(140, 255, 200, 90)
        val progressWidth = tempRect.width() * riseProgress
        canvas.drawRoundRect(tempRect.left, tempRect.top, tempRect.left + progressWidth, tempRect.bottom, cornerRadius, cornerRadius, panelPaint)
        subTextPaint.textAlign = Paint.Align.CENTER
        val label = if (state.isRiseSuspended()) "Rise paused" else String.format(Locale.US, "Rise %.1fs", max(0f, riseRemaining))
        canvas.drawText(label, tempRect.centerX(), tempRect.centerY() + subTextPaint.textSize / 3f, subTextPaint)
    }

    private fun drawButtons(canvas: Canvas) {
        if (!leftButtonRect.isEmpty) {
            canvas.drawRoundRect(leftButtonRect, buttonCorner, buttonCorner, buttonPaint)
            drawArrow(canvas, leftButtonRect, true)
        }
        if (!rightButtonRect.isEmpty) {
            canvas.drawRoundRect(rightButtonRect, buttonCorner, buttonCorner, buttonPaint)
            drawArrow(canvas, rightButtonRect, false)
        }
        drawPauseIcon(canvas, pauseRect)
        drawSettingsIcon(canvas, settingsRect)
    }

    private fun drawPauseIcon(canvas: Canvas, rect: RectF) {
        val barWidth = rect.width() / 4f
        val gap = barWidth
        val left = rect.left + barWidth
        val top = rect.top + barWidth
        val bottom = rect.bottom - barWidth
        canvas.drawRoundRect(left, top, left + barWidth, bottom, barWidth / 2f, barWidth / 2f, buttonPaint)
        canvas.drawRoundRect(left + barWidth + gap, top, left + barWidth * 2 + gap, bottom, barWidth / 2f, barWidth / 2f, buttonPaint)
    }

    private fun drawSettingsIcon(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val radius = rect.width() / 3f
        gearPath.reset()
        val teeth = 6
        for (i in 0 until teeth) {
            val angle = (Math.PI * 2 * i) / teeth
            val outerX = (cx + radius * 1.2 * kotlin.math.cos(angle)).toFloat()
            val outerY = (cy + radius * 1.2 * kotlin.math.sin(angle)).toFloat()
            val innerX = (cx + radius * kotlin.math.cos(angle)).toFloat()
            val innerY = (cy + radius * kotlin.math.sin(angle)).toFloat()
            if (i == 0) {
                gearPath.moveTo(outerX, outerY)
            } else {
                gearPath.lineTo(outerX, outerY)
            }
            gearPath.lineTo(innerX, innerY)
        }
        gearPath.close()
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = context.dpToPx(2f)
        canvas.drawPath(gearPath, iconPaint)
        iconPaint.style = Paint.Style.FILL
        iconPaint.alpha = 160
        canvas.drawCircle(cx, cy, radius * 0.6f, iconPaint)
        iconPaint.alpha = 255
    }

    private fun drawArrow(canvas: Canvas, rect: RectF, left: Boolean) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val size = rect.width() * 0.3f
        arrowPath.reset()
        if (left) {
            arrowPath.moveTo(cx + size, cy - size)
            arrowPath.lineTo(cx - size, cy)
            arrowPath.lineTo(cx + size, cy + size)
        } else {
            arrowPath.moveTo(cx - size, cy - size)
            arrowPath.lineTo(cx + size, cy)
            arrowPath.lineTo(cx - size, cy + size)
        }
        arrowPath.close()
        iconPaint.style = Paint.Style.FILL
        canvas.drawPath(arrowPath, iconPaint)
    }

    private fun drawSettingsOverlay(canvas: Canvas, overlay: SettingsOverlay, settings: GameSettings) {
        val rect = overlay.getPanelRect()
        panelPaint.color = Color.argb(240, 255, 255, 255)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, panelPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, panelStroke)
        val items = overlay.getItems()
        for (item in items) {
            when (item.id) {
                SettingsOverlay.SettingId.MODE -> {
                    val modeLabel = if (settings.zenMode) context.getString(R.string.mode_zen) else context.getString(R.string.mode_classic)
                    drawValueItem(canvas, item, "Mode", modeLabel)
                }
                SettingsOverlay.SettingId.HOLD -> drawToggleItem(canvas, item, context.getString(R.string.setting_hold), settings.holdEnabled)
                SettingsOverlay.SettingId.SHOW_GRID -> drawToggleItem(canvas, item, context.getString(R.string.setting_show_grid), settings.showGrid)
                SettingsOverlay.SettingId.LEFT_HANDED -> drawToggleItem(canvas, item, context.getString(R.string.setting_left_handed), settings.leftHanded)
                SettingsOverlay.SettingId.TOUCH_NUDGES -> drawToggleItem(canvas, item, context.getString(R.string.setting_nudges), settings.showTouchNudges)
                SettingsOverlay.SettingId.GRAVITY -> {
                    val readable = settings.gravityProfile.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
                    drawValueItem(canvas, item, context.getString(R.string.setting_speed), readable)
                }
                SettingsOverlay.SettingId.MUSIC -> drawSliderItem(canvas, item, context.getString(R.string.setting_music), settings.musicVolume)
                SettingsOverlay.SettingId.SFX -> drawSliderItem(canvas, item, context.getString(R.string.setting_sfx), settings.sfxVolume)
            }
        }
    }

    private fun drawToggleItem(canvas: Canvas, item: SettingsOverlay.SettingItem, label: String, checked: Boolean) {
        overlayTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, item.hitRect.left, item.hitRect.centerY() + overlayTextPaint.textSize / 3f, overlayTextPaint)
        val radius = context.dpToPx(10f)
        val cx = item.hitRect.right - radius * 1.5f
        val cy = item.hitRect.centerY()
        canvas.drawCircle(cx, cy, radius, panelStroke)
        if (checked) {
            iconPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, radius * 0.65f, iconPaint)
        }
    }

    private fun drawValueItem(canvas: Canvas, item: SettingsOverlay.SettingItem, label: String, value: String) {
        overlayTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, item.hitRect.left, item.hitRect.centerY() + overlayTextPaint.textSize / 3f, overlayTextPaint)
        overlayTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(value, item.hitRect.right, item.hitRect.centerY() + overlayTextPaint.textSize / 3f, overlayTextPaint)
    }

    private fun drawSliderItem(canvas: Canvas, item: SettingsOverlay.SettingItem, label: String, value: Float) {
        overlayTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, item.hitRect.left, item.hitRect.centerY() - overlayTextPaint.textSize, overlayTextPaint)
        val slider = item.sliderRect ?: return
        panelPaint.color = Color.argb(60, 0, 0, 0)
        canvas.drawRoundRect(slider, slider.height() / 2f, slider.height() / 2f, panelPaint)
        panelPaint.color = Color.argb(160, 120, 120, 120)
        val knobX = slider.left + slider.width() * clamp(value, 0f, 1f)
        canvas.drawCircle(knobX, slider.centerY(), slider.height() / 2f, panelPaint)
    }

    private fun resolveColor(id: Int): Int {
        if (id in cellColors.indices) return cellColors[id]
        return Color.GRAY
    }
}
