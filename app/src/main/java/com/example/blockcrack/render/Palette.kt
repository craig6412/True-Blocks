package com.example.blockcrack.render

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Central place for colors and paint instances used by the demo. Keeping them here makes it easy to
 * tweak the look without touching the simulation code.
 */
object Palette {
    const val EMPTY_INDEX: Int = 0
    const val BAND_COUNT: Int = 7

    data class CellStyle(
        val basePaint: Paint,
        val highlightPaint: Paint,
        val depthPaint: Paint
    )

    private val bandColors = intArrayOf(
        Color.TRANSPARENT, // 0 -> empty
        Color.parseColor("#FF5A6D"), // 1 -> red
        Color.parseColor("#FF8F3F"), // 2 -> orange
        Color.parseColor("#FFD740"), // 3 -> yellow
        Color.parseColor("#62D26F"), // 4 -> green
        Color.parseColor("#4FC3F7"), // 5 -> blue
        Color.parseColor("#7C6CFF"), // 6 -> indigo
        Color.parseColor("#B388FF")  // 7 -> violet
    )

    /** Paint cache for the metallic looking cell colors. Index 0 is not used (empty cell). */
    val cellStyles: Array<CellStyle?> = Array(bandColors.size) { index ->
        if (index == EMPTY_INDEX) {
            null
        } else {
            val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = mixColor(bandColors[index], Color.rgb(28, 28, 40), 0.38f)
            }
            val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            val depthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            CellStyle(basePaint, highlightPaint, depthPaint)
        }
    }

    private var lastCellSize: Float = -1f
    private var lastOffsetX: Float = Float.NaN
    private var lastOffsetY: Float = Float.NaN
    private val tmpMatrixX = Matrix()
    private val tmpMatrixY = Matrix()

    /**
     * Update the gradient shaders used to give each block a subtle metallic sheen. The gradients are
     * recreated whenever the cell size or grid offsets change so the highlights stay aligned to the
     * grid.
     */
    fun ensureCellShaders(cellSize: Float, gridOffsetX: Float, gridOffsetY: Float) {
        if (cellSize <= 0f) return
        val sizeChanged = abs(cellSize - lastCellSize) >= 0.01f
        val offsetChanged =
            abs(gridOffsetX - lastOffsetX) >= 0.01f || abs(gridOffsetY - lastOffsetY) >= 0.01f
        if (!sizeChanged && !offsetChanged) {
            return
        }

        val highlightShift = wrapOffset(-gridOffsetX, cellSize)
        val depthShift = wrapOffset(-gridOffsetY, cellSize)

        for (index in 1 until bandColors.size) {
            val style = cellStyles[index] ?: continue
            val baseColor = bandColors[index]

            if (sizeChanged || style.highlightPaint.shader !is LinearGradient) {
                style.highlightPaint.shader = createHighlightGradient(cellSize, baseColor)
            }
            (style.highlightPaint.shader as? LinearGradient)?.let { shader ->
                tmpMatrixX.reset()
                tmpMatrixX.setTranslate(highlightShift, 0f)
                shader.setLocalMatrix(tmpMatrixX)
            }

            if (sizeChanged || style.depthPaint.shader !is LinearGradient) {
                style.depthPaint.shader = createDepthGradient(cellSize, baseColor)
            }
            (style.depthPaint.shader as? LinearGradient)?.let { shader ->
                tmpMatrixY.reset()
                tmpMatrixY.setTranslate(0f, depthShift)
                shader.setLocalMatrix(tmpMatrixY)
            }
        }

        lastCellSize = cellSize
        lastOffsetX = gridOffsetX
        lastOffsetY = gridOffsetY
    }

    val binPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(12, 12, 18)
    }

    val slatPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(46, 46, 56)
        strokeWidth = 4f
    }

    val dustPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(180, 220, 220, 220)
    }

    val debugPanelPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(200, 18, 18, 18)
    }

    val debugButtonPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(220, 30, 144, 255)
    }

    val debugButtonTextPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }

    private fun createHighlightGradient(cellSize: Float, baseColor: Int): LinearGradient {
        return LinearGradient(
            0f,
            0f,
            cellSize,
            0f,
            intArrayOf(
                withAlpha(mixColor(baseColor, Color.BLACK, 0.75f), 200),
                withAlpha(mixColor(baseColor, Color.WHITE, 0.3f), 230),
                withAlpha(mixColor(baseColor, Color.WHITE, 0.9f), 255),
                withAlpha(mixColor(baseColor, Color.WHITE, 0.3f), 230),
                withAlpha(mixColor(baseColor, Color.BLACK, 0.75f), 200)
            ),
            floatArrayOf(0f, 0.28f, 0.5f, 0.72f, 1f),
            Shader.TileMode.REPEAT
        )
    }

    private fun createDepthGradient(cellSize: Float, baseColor: Int): LinearGradient {
        return LinearGradient(
            0f,
            0f,
            0f,
            cellSize,
            intArrayOf(
                withAlpha(mixColor(baseColor, Color.WHITE, 0.6f), 120),
                Color.TRANSPARENT,
                withAlpha(mixColor(baseColor, Color.BLACK, 0.7f), 190)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.REPEAT
        )
    }

    private fun wrapOffset(offset: Float, period: Float): Float {
        if (period <= 0f) return 0f
        var value = offset % period
        if (value < 0f) {
            value += period
        }
        return value
    }

    private fun mixColor(colorA: Int, colorB: Int, amount: Float): Int {
        val clamped = amount.coerceIn(0f, 1f)
        val inverse = 1f - clamped
        val r = (Color.red(colorA) * inverse + Color.red(colorB) * clamped).roundToInt()
        val g = (Color.green(colorA) * inverse + Color.green(colorB) * clamped).roundToInt()
        val b = (Color.blue(colorA) * inverse + Color.blue(colorB) * clamped).roundToInt()
        return Color.rgb(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }
}
