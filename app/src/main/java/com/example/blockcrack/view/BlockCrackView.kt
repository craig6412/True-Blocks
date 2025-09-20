package com.example.blockcrack.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewConfiguration
import androidx.core.view.GestureDetectorCompat
import com.example.blockcrack.render.Palette
import com.example.blockcrack.sim.World
import com.example.blockcrack.sim.WorldConfig
import com.example.blockcrack.util.Stopwatch
import java.util.Locale
import java.util.HashSet
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * SurfaceView that owns the render thread, user input, and simulation loop. The view forwards touch
 * paths to [World.carve] and renders the falling rainbow blocks on a dedicated thread.
 */
class BlockCrackView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val world = World(WorldConfig.gridW, WorldConfig.gridH)
    private var renderThread: RenderThread? = null

    private val density = resources.displayMetrics.density
    private val minCrackSpacing = 6f * density
    private val minSampleSpacing = 4f * density
    private val gestureDetector: GestureDetectorCompat

    private val statsTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * density
    }
    private val overlayTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f * density
    }
    private val overlayLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f * density
    }
    private val overlayValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f * density
        textAlign = Paint.Align.RIGHT
    }
    private val overlayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textSize = 12f * density
    }
    private val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f * density
        style = Paint.Style.STROKE
    }

    private val debugEntries = listOf(
        DebugEntry(
            label = "gridW",
            valueProvider = { WorldConfig.gridW.toString() },
            onDecrease = { adjustGridWidth(-1) },
            onIncrease = { adjustGridWidth(1) }
        ),
        DebugEntry(
            label = "gridH",
            valueProvider = { WorldConfig.gridH.toString() },
            onDecrease = { adjustGridHeight(-2) },
            onIncrease = { adjustGridHeight(2) }
        ),
        DebugEntry(
            label = "gravity",
            valueProvider = { WorldConfig.gravityStepsPerFrame.toString() },
            onDecrease = { adjustGravity(-1) },
            onIncrease = { adjustGravity(1) }
        ),
        DebugEntry(
            label = "crackRadius",
            valueProvider = { WorldConfig.crackRadiusCells.toString() },
            onDecrease = { adjustCrackRadius(-1) },
            onIncrease = { adjustCrackRadius(1) }
        ),
        DebugEntry(
            label = "noise",
            valueProvider = { String.format(Locale.US, "%.2f", WorldConfig.noiseStrength) },
            onDecrease = { adjustNoise(-0.05f) },
            onIncrease = { adjustNoise(0.05f) }
        )
    )

    private val pathPoints = mutableListOf<PointF>()
    private val snapshotDimensions = World.Dimensions()
    private var cellSnapshot = ByteArray(world.capacity())

    @Volatile
    private var debugButtons: List<DebugButton> = emptyList()

    @Volatile
    private var debugOverlayEnabled = false

    @Volatile
    private var statsEnabled = false

    @Volatile
    private var resizeRequested = false

    private var jitterFrame = 0

    private val dustParticles = mutableListOf<DustParticle>()
    private val dustRandom = Random(System.nanoTime())

    private val statsSnapshot = StatsSnapshot()

    private var gridWidth: Int = WorldConfig.gridW
    private var gridHeight: Int = WorldConfig.gridH

    private var twoFingerTapCandidate = false
    private var twoFingerStartTime = 0L
    private val twoFingerStart = arrayOf(PointF(), PointF())
    private val tapTimeout = ViewConfiguration.getTapTimeout().toLong()
    private val moveSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private var isSurfaceReady = false

    init {
        holder.addCallback(this)
        isFocusable = true
        keepScreenOn = true
        Palette.slatPaint.strokeWidth = max(2f, 2f * density)
        Palette.debugButtonTextPaint.textSize = 16f * density
        gestureDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                debugOverlayEnabled = !debugOverlayEnabled
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                statsEnabled = !statsEnabled
            }
        })
        gestureDetector.setOnDoubleTapListener(gestureDetector.onDoubleTapListener)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isSurfaceReady = true
        startRenderThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // No-op
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isSurfaceReady = false
        stopRenderThread()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        handleTwoFingerTap(event)
        if (debugOverlayEnabled) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val buttons = debugButtons
                val x = event.x
                val y = event.y
                for (button in buttons) {
                    if (button.hitRect.contains(x, y)) {
                        button.onTap.invoke()
                        break
                    }
                }
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount == 1) {
                    beginCrack(event.x, event.y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    extendCrack(event.x, event.y)
                }
            }
            MotionEvent.ACTION_UP -> {
                finishCrack()
            }
            MotionEvent.ACTION_CANCEL -> {
                pathPoints.clear()
            }
        }
        return true
    }

    fun onHostResume() {
        if (isSurfaceReady) {
            startRenderThread()
        }
    }

    fun onHostPause() {
        stopRenderThread()
    }

    private fun startRenderThread() {
        if (renderThread?.isRunning == true) return
        val thread = RenderThread()
        renderThread = thread
        thread.start()
    }

    private fun stopRenderThread() {
        val thread = renderThread ?: return
        thread.requestStop()
        try {
            thread.join()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        renderThread = null
    }

    private inner class RenderThread : Thread("BlockCrackThread") {
        @Volatile
        var isRunning = true
        private val stopwatch = Stopwatch()
        private var accumulator = 0f

        init {
            stopwatch.reset()
        }

        override fun run() {
            while (isRunning) {
                if (!holder.surface.isValid) {
                    sleepQuiet(16L)
                    continue
                }
                applyPendingResize()
                var delta = stopwatch.tickSeconds()
                if (delta > MAX_FRAME_DELTA) delta = MAX_FRAME_DELTA
                accumulator += delta

                var movedThisFrame = 0
                var stepsTaken = 0
                while (accumulator >= FIXED_TIMESTEP) {
                    movedThisFrame += world.step()
                    stepsTaken++
                    accumulator -= FIXED_TIMESTEP
                }

                val canvas = holder.lockCanvas()
                if (canvas != null) {
                    try {
                        drawFrame(canvas, movedThisFrame, delta, stepsTaken)
                    } finally {
                        holder.unlockCanvasAndPost(canvas)
                    }
                }

                val frameMillis = stopwatch.peekMillis()
                val targetMillis = 1000f / 60f
                if (frameMillis < targetMillis) {
                    sleepQuiet((targetMillis - frameMillis).toLong())
                }

                statsSnapshot.update(
                    fps = if (delta > 0f) 1f / delta else 0f,
                    movedCells = movedThisFrame,
                    steps = stepsTaken
                )
            }
        }

        fun requestStop() {
            isRunning = false
            interrupt()
        }

        private fun sleepQuiet(millis: Long) {
            if (millis <= 0) return
            try {
                sleep(millis)
            } catch (_: InterruptedException) {
                // Thread stop requested.
            }
        }
    }

    private fun applyPendingResize() {
        if (!resizeRequested) return
        val newWidth = max(8, WorldConfig.gridW)
        val newHeight = max(8, WorldConfig.gridH)
        world.ensureSize(newWidth, newHeight)
        gridWidth = newWidth
        gridHeight = newHeight
        val needed = newWidth * newHeight
        if (cellSnapshot.size < needed) {
            cellSnapshot = ByteArray(needed)
        }
        resizeRequested = false
    }

    private fun drawFrame(canvas: Canvas, movedCells: Int, deltaSeconds: Float, stepsTaken: Int) {
        world.getDimensions(snapshotDimensions)
        val needed = snapshotDimensions.width * snapshotDimensions.height
        if (cellSnapshot.size < needed) {
            cellSnapshot = ByteArray(needed)
        }
        world.snapshot(cellSnapshot, snapshotDimensions)
        gridWidth = snapshotDimensions.width
        gridHeight = snapshotDimensions.height

        val widthCells = gridWidth
        val heightCells = gridHeight
        val canvasWidth = canvas.width.toFloat()
        val canvasHeight = canvas.height.toFloat()
        if (widthCells <= 0 || heightCells <= 0) {
            canvas.drawColor(Color.rgb(8, 8, 12))
            jitterFrame = (jitterFrame + 1) % 8
            return
        }
        val cellSize = min(canvasWidth / widthCells, canvasHeight / heightCells)
        if (cellSize <= 0f) {
            canvas.drawColor(Color.rgb(8, 8, 12))
            jitterFrame = (jitterFrame + 1) % 8
            return
        }
        val gridPixelWidth = cellSize * widthCells
        val gridPixelHeight = cellSize * heightCells
        val offsetX = (canvasWidth - gridPixelWidth) * 0.5f
        val offsetY = (canvasHeight - gridPixelHeight) * 0.5f

        canvas.drawColor(Color.rgb(8, 8, 12))
        drawBin(canvas, offsetX, offsetY, gridPixelWidth, gridPixelHeight, cellSize)
        drawCells(canvas, widthCells, heightCells, offsetX, offsetY, cellSize)
        drawCrackPath(canvas)
        updateDust(deltaSeconds, movedCells, offsetX, offsetY, cellSize)
        drawDust(canvas)
        if (debugOverlayEnabled) {
            drawDebugOverlay(canvas)
        }
        if (statsEnabled) {
            drawStats(canvas)
        }

        jitterFrame = (jitterFrame + 1) % 8
    }

    private fun drawBin(
        canvas: Canvas,
        offsetX: Float,
        offsetY: Float,
        gridPixelWidth: Float,
        gridPixelHeight: Float,
        cellSize: Float
    ) {
        val padding = cellSize * 1.5f
        val rect = RectF(
            offsetX - padding,
            offsetY - padding,
            offsetX + gridPixelWidth + padding,
            offsetY + gridPixelHeight + padding
        )
        canvas.drawRoundRect(rect, cellSize, cellSize, Palette.binPaint)

        val slatPaint = Palette.slatPaint
        slatPaint.strokeWidth = max(2f, cellSize * 0.1f)
        var y = rect.top + padding
        while (y < rect.bottom - padding) {
            canvas.drawLine(rect.left + padding * 0.3f, y, rect.right - padding * 0.3f, y, slatPaint)
            y += cellSize * 4f
        }
        var x = rect.left + padding
        while (x < rect.right - padding) {
            canvas.drawLine(x, rect.top + padding * 0.2f, x, rect.bottom - padding * 0.2f, slatPaint)
            x += cellSize * 6f
        }
    }

    private fun drawCells(
        canvas: Canvas,
        width: Int,
        height: Int,
        offsetX: Float,
        offsetY: Float,
        cellSize: Float
    ) {
        Palette.ensureCellShaders(cellSize, offsetX, offsetY)
        val styles = Palette.cellStyles
        val rowJitterScale = cellSize * 0.04f
        for (y in 0 until height) {
            val rowStart = y * width
            val rowJitter = ((y + jitterFrame) % 4 - 1.5f) * rowJitterScale
            val top = offsetY + y * cellSize
            val bottom = top + cellSize
            var x = 0
            while (x < width) {
                val index = cellSnapshot[rowStart + x].toInt() and 0xFF
                val style = if (index < styles.size) styles[index] else null
                if (index == Palette.EMPTY_INDEX || style == null) {
                    x++
                    continue
                }
                var runEnd = x + 1
                while (runEnd < width) {
                    val nextIndex = cellSnapshot[rowStart + runEnd].toInt() and 0xFF
                    if (nextIndex != index) break
                    runEnd++
                }
                val left = offsetX + x * cellSize + rowJitter
                val right = offsetX + runEnd * cellSize + rowJitter
                canvas.drawRect(left, top, right, bottom, style.basePaint)
                canvas.drawRect(left, top, right, bottom, style.highlightPaint)
                canvas.drawRect(left, top, right, bottom, style.depthPaint)
                x = runEnd
            }
        }
    }

    private fun drawCrackPath(canvas: Canvas) {
        if (pathPoints.size < 2) return
        for (i in 0 until pathPoints.size - 1) {
            val a = pathPoints[i]
            val b = pathPoints[i + 1]
            canvas.drawLine(a.x, a.y, b.x, b.y, crackPaint)
        }
    }

    private fun drawDebugOverlay(canvas: Canvas) {
        val margin = 16f * density
        val rowHeight = 48f * density
        val panelWidth = width * 0.6f
        val left = margin
        val top = margin
        val right = left + panelWidth
        val bottom = top + rowHeight * (debugEntries.size + 2)
        val panelRect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(panelRect, 12f * density, 12f * density, Palette.debugPanelPaint)

        val titleBaseline = top + rowHeight * 0.8f
        canvas.drawText("Debug controls", left + margin, titleBaseline, overlayTitlePaint)
        canvas.drawText(
            "Double-tap to toggle. Tap +/- to adjust values.",
            left + margin,
            titleBaseline + rowHeight * 0.6f,
            overlayTextPaint
        )

        val buttons = ArrayList<DebugButton>(debugEntries.size * 2)
        val buttonSize = rowHeight * 0.7f
        val buttonRadius = 10f * density
        val gap = margin * 0.4f
        var rowTop = top + rowHeight * 1.6f
        for (entry in debugEntries) {
            val baseline = rowTop + rowHeight * 0.7f
            canvas.drawText(entry.label, left + margin, baseline, overlayLabelPaint)
            canvas.drawText(entry.valueProvider(), right - margin - buttonSize * 2 - gap, baseline, overlayValuePaint)

            val buttonTop = rowTop + (rowHeight - buttonSize) * 0.5f
            val plusRect = RectF(
                right - margin - buttonSize,
                buttonTop,
                right - margin,
                buttonTop + buttonSize
            )
            val minusRect = RectF(
                plusRect.left - gap - buttonSize,
                buttonTop,
                plusRect.left - gap,
                buttonTop + buttonSize
            )
            canvas.drawRoundRect(minusRect, buttonRadius, buttonRadius, Palette.debugButtonPaint)
            canvas.drawRoundRect(plusRect, buttonRadius, buttonRadius, Palette.debugButtonPaint)

            val textBaseline = minusRect.centerY() + Palette.debugButtonTextPaint.textSize * 0.35f
            canvas.drawText("-", minusRect.centerX(), textBaseline, Palette.debugButtonTextPaint)
            canvas.drawText("+", plusRect.centerX(), textBaseline, Palette.debugButtonTextPaint)

            buttons += DebugButton(RectF(minusRect), entry.onDecrease)
            buttons += DebugButton(RectF(plusRect), entry.onIncrease)

            rowTop += rowHeight
        }
        debugButtons = buttons
    }

    private fun drawStats(canvas: Canvas) {
        val margin = 16f * density
        val rowHeight = 24f * density
        val snapshot = statsSnapshot.copy()
        val lines = listOf(
            String.format(Locale.US, "FPS: %.1f", snapshot.fps),
            "Cells moved: ${snapshot.movedCells}",
            "Steps: ${snapshot.steps}",
            "Grid: ${gridWidth}x${gridHeight}",
            String.format(Locale.US, "Noise: %.2f", WorldConfig.noiseStrength)
        )
        val textWidth = lines.maxOf { statsTextPaint.measureText(it) }
        val panelWidth = textWidth + margin * 2
        val panelHeight = rowHeight * lines.size + margin * 1.5f
        val rect = RectF(
            margin,
            height - panelHeight - margin,
            margin + panelWidth,
            height - margin
        )
        canvas.drawRoundRect(rect, 12f * density, 12f * density, Palette.debugPanelPaint)
        var baseline = rect.top + rowHeight
        for (line in lines) {
            canvas.drawText(line, rect.left + margin, baseline, statsTextPaint)
            baseline += rowHeight
        }
    }

    private fun updateDust(deltaSeconds: Float, movedCells: Int, offsetX: Float, offsetY: Float, cellSize: Float) {
        val iterator = dustParticles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.life += deltaSeconds
            if (particle.life >= particle.maxLife) {
                iterator.remove()
                continue
            }
            particle.x += particle.vx * deltaSeconds
            particle.y += particle.vy * deltaSeconds
        }

        if (movedCells > 150) {
            val baseX = offsetX + (world.lastMovementCenterX + 0.5f) * cellSize
            val baseY = offsetY + (world.lastMovementCenterY + 0.5f) * cellSize
            val count = min(6, movedCells / 120)
            repeat(count) {
                val angle = dustRandom.nextFloat() * (Math.PI * 2).toFloat()
                val speed = (10f + dustRandom.nextFloat() * 30f) * density
                val vx = cos(angle) * speed
                val vy = sin(angle) * speed - 12f * density
                val radius = cellSize * (0.3f + dustRandom.nextFloat() * 0.4f)
                val maxLife = 0.4f + dustRandom.nextFloat() * 0.4f
                dustParticles += DustParticle(
                    x = baseX + (dustRandom.nextFloat() - 0.5f) * cellSize,
                    y = baseY + (dustRandom.nextFloat() - 0.5f) * cellSize,
                    vx = vx,
                    vy = vy,
                    life = 0f,
                    maxLife = maxLife,
                    radius = radius
                )
            }
        }
    }

    private fun drawDust(canvas: Canvas) {
        if (dustParticles.isEmpty()) return
        val paint = Palette.dustPaint
        for (particle in dustParticles) {
            val alpha = ((1f - particle.life / particle.maxLife) * 180f).toInt().coerceIn(0, 180)
            paint.alpha = alpha
            canvas.drawCircle(particle.x, particle.y, particle.radius, paint)
        }
        paint.alpha = 180
    }

    private fun beginCrack(x: Float, y: Float) {
        pathPoints.clear()
        pathPoints.add(PointF(x, y))
    }

    private fun extendCrack(x: Float, y: Float) {
        if (pathPoints.isEmpty()) {
            pathPoints.add(PointF(x, y))
            return
        }
        val last = pathPoints.last()
        val dx = x - last.x
        val dy = y - last.y
        val minSpacing = minCrackSpacing
        if (dx * dx + dy * dy > minSpacing * minSpacing) {
            pathPoints.add(PointF(x, y))
            while (pathPoints.size > MAX_CRACK_POINTS) {
                pathPoints.removeAt(0)
            }
        }
    }

    private fun finishCrack() {
        if (pathPoints.size < 2) {
            pathPoints.clear()
            return
        }
        world.getDimensions(snapshotDimensions)
        val dims = snapshotDimensions
        if (dims.width <= 0 || dims.height <= 0) {
            pathPoints.clear()
            return
        }
        val cellSize = min(width.toFloat() / dims.width, height.toFloat() / dims.height)
        if (cellSize <= 0f) {
            pathPoints.clear()
            return
        }
        val gridPixelWidth = cellSize * dims.width
        val gridPixelHeight = cellSize * dims.height
        val offsetX = (width - gridPixelWidth) * 0.5f
        val offsetY = (height - gridPixelHeight) * 0.5f
        val spacing = max(cellSize * 0.5f, minSampleSpacing)
        val visited = HashSet<Int>()
        val samples = ArrayList<World.GridPoint>()

        fun addSample(px: Float, py: Float) {
            val gx = ((px - offsetX) / cellSize).toInt()
            val gy = ((py - offsetY) / cellSize).toInt()
            if (gx !in 0 until dims.width || gy !in 0 until dims.height) return
            val key = gy * dims.width + gx
            if (visited.add(key)) {
                samples += World.GridPoint(gx, gy)
            }
        }

        pathPoints.firstOrNull()?.let { addSample(it.x, it.y) }
        var accumulated = 0f
        for (i in 0 until pathPoints.size - 1) {
            val start = pathPoints[i]
            val end = pathPoints[i + 1]
            val dx = end.x - start.x
            val dy = end.y - start.y
            val distance = hypot(dx, dy)
            if (distance <= 0f) continue
            var travel = spacing - accumulated
            while (travel <= distance) {
                val ratio = travel / distance
                val sampleX = start.x + dx * ratio
                val sampleY = start.y + dy * ratio
                addSample(sampleX, sampleY)
                travel += spacing
            }
            accumulated = (accumulated + distance) % spacing
        }

        if (samples.isNotEmpty()) {
            world.carve(samples)
        }
        pathPoints.clear()
    }

    private fun handleTwoFingerTap(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    twoFingerTapCandidate = true
                    twoFingerStartTime = SystemClock.uptimeMillis()
                    twoFingerStart[0].set(event.getX(0), event.getY(0))
                    twoFingerStart[1].set(event.getX(1), event.getY(1))
                    pathPoints.clear()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (twoFingerTapCandidate && event.pointerCount >= 2) {
                    for (i in 0 until 2) {
                        val dx = event.getX(i) - twoFingerStart[i].x
                        val dy = event.getY(i) - twoFingerStart[i].y
                        if (dx * dx + dy * dy > moveSlop * moveSlop) {
                            twoFingerTapCandidate = false
                            break
                        }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (twoFingerTapCandidate && event.pointerCount == 2) {
                    val elapsed = SystemClock.uptimeMillis() - twoFingerStartTime
                    if (elapsed <= tapTimeout * 2) {
                        world.fillRainbow()
                        dustParticles.clear()
                    }
                }
                twoFingerTapCandidate = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                twoFingerTapCandidate = false
            }
        }
    }

    private fun adjustGridWidth(delta: Int) {
        val newWidth = (WorldConfig.gridW + delta).coerceIn(24, 128)
        if (newWidth != WorldConfig.gridW) {
            WorldConfig.gridW = newWidth
            requestResize()
        }
    }

    private fun adjustGridHeight(delta: Int) {
        val newHeight = (WorldConfig.gridH + delta).coerceIn(48, 160)
        if (newHeight != WorldConfig.gridH) {
            WorldConfig.gridH = newHeight
            requestResize()
        }
    }

    private fun adjustNoise(delta: Float) {
        WorldConfig.noiseStrength = (WorldConfig.noiseStrength + delta).coerceIn(0f, 1f)
    }

    private fun adjustCrackRadius(delta: Int) {
        WorldConfig.crackRadiusCells = (WorldConfig.crackRadiusCells + delta).coerceIn(1, 6)
    }

    private fun adjustGravity(delta: Int) {
        WorldConfig.gravityStepsPerFrame = (WorldConfig.gravityStepsPerFrame + delta).coerceIn(1, 4)
    }

    private fun requestResize() {
        resizeRequested = true
    }

    private data class DebugEntry(
        val label: String,
        val valueProvider: () -> String,
        val onDecrease: () -> Unit,
        val onIncrease: () -> Unit
    )

    private data class DebugButton(
        val hitRect: RectF,
        val onTap: () -> Unit
    )

    private data class DustParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        val maxLife: Float,
        val radius: Float
    )

    private class StatsSnapshot(
        @Volatile var fps: Float = 0f,
        @Volatile var movedCells: Int = 0,
        @Volatile var steps: Int = 0
    ) {
        fun update(fps: Float, movedCells: Int, steps: Int) {
            this.fps = fps
            this.movedCells = movedCells
            this.steps = steps
        }

        fun copy(): SnapshotCopy = SnapshotCopy(fps, movedCells, steps)
    }

    private data class SnapshotCopy(val fps: Float, val movedCells: Int, val steps: Int)

    companion object {
        private const val MAX_CRACK_POINTS = 2048
        private const val FIXED_TIMESTEP = 1f / 60f
        private const val MAX_FRAME_DELTA = 0.25f
    }
}
