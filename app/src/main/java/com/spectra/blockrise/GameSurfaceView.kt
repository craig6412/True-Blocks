package com.spectra.blockrise

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * SurfaceView hosting the fixed-timestep game loop and delegating rendering/input.
 */
class GameSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    private val gameState = GameState()
    private val renderer = GameRenderer(context)
    private val settingsOverlay = SettingsOverlay(context)
    private val inputController = InputController(context)

    @Volatile
    private var running = false
    private var renderThread: Thread? = null
    private var lastTimeNs: Long = 0
    private var accumulator = 0f
    private val frameDurationNs = 1_000_000_000L / TARGET_FPS

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var layoutDirty = true
    private var overlayPausedGame = false

    init {
        holder.addCallback(this)
        isFocusable = true
        keepScreenOn = true
        SoundManager.initialize(context)
        SoundManager.setVolumes(gameState.settings)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        renderThread = Thread(this, "BlockRiseLoop").also { thread ->
            thread.start()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        layoutDirty = true
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        renderThread?.join()
        renderThread = null
    }

    override fun run() {
        lastTimeNs = System.nanoTime()
        // Fixed-timestep accumulator loop: advance simulation in 60 Hz slices regardless of
        // render cadence so logic remains deterministic while the canvas aims for 60 FPS.
        while (running) {
            val now = System.nanoTime()
            val delta = (now - lastTimeNs) / 1_000_000_000f
            lastTimeNs = now
            accumulator += delta
            while (accumulator >= LOGIC_STEP_SEC) {
                stepGame(LOGIC_STEP_SEC)
                accumulator -= LOGIC_STEP_SEC
            }
            drawFrame()
            val frameTime = System.nanoTime() - now
            val sleepNs = frameDurationNs - frameTime
            if (sleepNs > 0) {
                try {
                    Thread.sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                } catch (_: InterruptedException) {
                }
            }
        }
    }

    private fun stepGame(deltaSec: Float) {
        inputController.update(deltaSec)
        inputController.pollCommands { command ->
            handleCommand(command)
        }
        gameState.update(deltaSec)
    }

    private fun drawFrame() {
        ensureLayout()
        val canvas = holder.lockCanvas() ?: return
        try {
            renderer.draw(canvas, gameState, settingsOverlay)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun ensureLayout() {
        if (!layoutDirty || surfaceWidth == 0 || surfaceHeight == 0) return
        renderer.updateLayout(surfaceWidth, surfaceHeight, gameState.settings, settingsOverlay)
        inputController.setHudLayout(renderer.hudLayout())
        layoutDirty = false
    }

    private fun handleCommand(command: InputCommand) {
        if (settingsOverlay.isVisible && command != InputCommand.ToggleSettings) {
            return
        }
        when (command) {
            InputCommand.MoveLeft -> gameState.moveHorizontal(-1)
            InputCommand.MoveRight -> gameState.moveHorizontal(1)
            InputCommand.RotateCW -> gameState.rotateClockwise()
            InputCommand.Hold -> gameState.holdCurrent()
            InputCommand.TogglePause -> gameState.togglePause()
            InputCommand.ToggleSettings -> toggleSettingsOverlay()
        }
    }

    private fun toggleSettingsOverlay() {
        if (settingsOverlay.isVisible) {
            settingsOverlay.hide()
            gameState.applySettings(gameState.settings)
            if (overlayPausedGame && gameState.isPaused) {
                gameState.togglePause()
            }
            overlayPausedGame = false
            layoutDirty = true
        } else {
            overlayPausedGame = !gameState.isPaused
            if (!gameState.isPaused) {
                gameState.togglePause()
            }
            settingsOverlay.toggle()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (settingsOverlay.isVisible) {
            if (event.action == MotionEvent.ACTION_UP) {
                val consumed = settingsOverlay.handleTap(event.x, event.y, gameState.settings)
                if (consumed) {
                    gameState.applySettings(gameState.settings)
                    layoutDirty = true
                    return true
                }
            }
        }
        inputController.onTouchEvent(event)
        return true
    }

    fun captureSnapshot(): GameState.GameSnapshot = gameState.snapshot()

    fun restoreFromSnapshot(snapshot: GameState.GameSnapshot) {
        gameState.restore(snapshot)
        layoutDirty = true
    }
    fun resetGame(newSettings: GameSettings) {
        gameState.reset(newSettings)
        layoutDirty = true
    }
}
