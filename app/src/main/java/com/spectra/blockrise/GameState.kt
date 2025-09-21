package com.spectra.blockrise

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import java.util.ArrayDeque

/**
 * Holds all mutable gameplay state, including timers, scoring, and queue management.
 */
class GameState(private val random: Random = Random(System.currentTimeMillis())) {
    val grid: Grid = Grid()
    private val pieceBag: PieceBag = PieceBag(random)
    private val nextQueue: ArrayDeque<Tetromino> = ArrayDeque()

    var activePiece: Piece? = null
        private set
    var holdPiece: Tetromino? = null
        private set
    private var holdUsed: Boolean = false

    var score: Long = 0
        private set
    var totalLinesCleared: Int = 0
        private set
    var chainDisplay: Int = 1
        private set

    var settings: GameSettings = GameSettings()
        private set
    var gameMode: GameMode = GameMode.ZEN
        private set

    var isPaused: Boolean = false
        private set
    var gameOver: Boolean = false
        private set

    private var gravityTimerMs: Float = 0f
    private var gravityIntervalMs: Float = GRAVITY_MS
    private var lockTimerMs: Float = LOCK_DELAY_MS
    private var lockPending: Boolean = false
    private var riseTimer: Float = RISE_INTERVAL_SEC
    private var riseInterval: Float = RISE_INTERVAL_SEC
    private var timeSinceStart: Float = 0f
    private var difficultyTimer: Float = 0f
    private var difficultySteps: Int = 0
    private var riseSuspended: Boolean = false

    var bestZenScore: Long = 0
        private set
    var bestClassicScore: Long = 0
        private set

    val lineClearAnimations: ArrayDeque<LineClearAnimation> = ArrayDeque()
    val avalancheAnimation: AvalancheAnimation = AvalancheAnimation()
    val risingAnimation: RisingAnimation = RisingAnimation()
    var lockPulse: Float = 0f
        private set

    init {
        grid.unitTestCollision()
        reset(GameSettings())
    }

    fun reset(newSettings: GameSettings) {
        settings = newSettings.copy()
        gameMode = settings.mode()
        score = 0
        totalLinesCleared = 0
        chainDisplay = 1
        isPaused = false
        gameOver = false
        gravityTimerMs = 0f
        lockTimerMs = LOCK_DELAY_MS
        lockPending = false
        riseTimer = RISE_INTERVAL_SEC
        riseInterval = RISE_INTERVAL_SEC
        difficultyTimer = 0f
        difficultySteps = 0
        riseSuspended = false
        timeSinceStart = 0f
        holdPiece = null
        holdUsed = false
        lineClearAnimations.clear()
        avalancheAnimation.reset()
        risingAnimation.reset()
        lockPulse = 0f
        grid.clear()
        nextQueue.clear()
        pieceBag.restore(null)
        refillPreview()
        spawnNextPiece()
        applySettingsInternal()
    }

    fun togglePause() {
        if (gameOver) return
        isPaused = !isPaused
        if (isPaused) {
            SoundManager.playPause()
        }
    }

    fun applySettings(updated: GameSettings) {
        settings = updated
        gameMode = settings.mode()
        applySettingsInternal()
    }

    private fun applySettingsInternal() {
        gravityIntervalMs = settings.gravityProfile.intervalMs
        riseInterval = computeRiseIntervalInternal()
        SoundManager.setVolumes(settings)
    }

    fun update(deltaSec: Float) {
        updateAnimations(deltaSec)
        if (gameOver || isPaused) {
            return
        }
        timeSinceStart += deltaSec
        if (gameMode == GameMode.CLASSIC_RELAX) {
            difficultyTimer += deltaSec
            if (difficultyTimer >= CLASSIC_DIFFICULTY_STEP_SEC) {
                difficultyTimer -= CLASSIC_DIFFICULTY_STEP_SEC
                difficultySteps = min(difficultySteps + 1, ((RISE_INTERVAL_SEC - MIN_RISE_INTERVAL_SEC) / CLASSIC_RISE_DECREMENT).toInt())
                riseInterval = computeRiseIntervalInternal()
            }
        }

        ensureActivePiece()
        SoundManager.setVolumes(settings)
        updateGravity(deltaSec)
        updateLockTimer(deltaSec)
        updateRise(deltaSec)
    }

    private fun updateGravity(deltaSec: Float) {
        gravityIntervalMs = settings.gravityProfile.intervalMs
        gravityTimerMs += deltaSec * 1000f
        while (gravityTimerMs >= gravityIntervalMs) {
            gravityTimerMs -= gravityIntervalMs
            val moved = stepGravity()
            if (!moved) {
                break
            }
        }
    }

    private fun stepGravity(): Boolean {
        val piece = activePiece ?: return false
        if (piece.moveDown(grid)) {
            lockPending = false
            lockTimerMs = LOCK_DELAY_MS
            return true
        }
        if (!lockPending) {
            lockPending = true
            lockTimerMs = LOCK_DELAY_MS
        }
        return false
    }

    private fun updateLockTimer(deltaSec: Float) {
        val piece = activePiece ?: return
        val canDescend = grid.canPlace(piece.tetromino, piece.x, piece.y + 1, piece.rotation)
        if (canDescend) {
            lockPending = false
            lockTimerMs = LOCK_DELAY_MS
            return
        }
        if (!lockPending) {
            lockPending = true
            lockTimerMs = LOCK_DELAY_MS
        }
        lockTimerMs -= deltaSec * 1000f
        if (lockTimerMs <= 0f) {
            lockActivePiece()
        }
    }

    private fun updateRise(deltaSec: Float) {
        // Rising-row scheduler: count down using the current interval, synthesize a garbage
        // row when the timer elapses, and suspend further rises in Zen mode until the hidden
        // buffer has been excavated. Classic Relax instead ends the run if the push causes an
        // overflow into the hidden rows.
        if (riseSuspended) {
            if (grid.hiddenRowsClear()) {
                riseSuspended = false
                riseTimer = max(riseTimer, 0.5f)
            }
            return
        }
        riseTimer -= deltaSec
        while (riseTimer <= 0f) {
            val result = grid.riseWithGarbage(random)
            risingAnimation.start()
            SoundManager.playRise()
            if (result.overflowed) {
                if (gameMode == GameMode.CLASSIC_RELAX) {
                    triggerGameOver()
                } else {
                    riseSuspended = true
                }
                riseTimer = riseInterval
                break
            }
            riseTimer += riseInterval
        }
    }

    private fun ensureActivePiece() {
        if (activePiece != null || gameOver) return
        spawnNextPiece()
    }

    private fun spawnNextPiece() {
        refillPreview()
        val next = nextQueue.removeFirstOrNull() ?: pieceBag.next()
        val spawnX = BOARD_COLS / 2 - 2
        val spawnY = -HIDDEN_ROWS
        val piece = Piece(next, spawnX, spawnY)
        if (!grid.canPlace(piece.tetromino, piece.x, piece.y, piece.rotation)) {
            // Spawn collision.
            if (gameMode == GameMode.CLASSIC_RELAX) {
                triggerGameOver()
            } else {
                riseSuspended = true
            }
            activePiece = null
            return
        }
        activePiece = piece
        lockPending = false
        lockTimerMs = LOCK_DELAY_MS
        gravityTimerMs = 0f
        nextQueue.addLast(pieceBag.next())
    }

    private fun refillPreview() {
        while (nextQueue.size < PREVIEW_COUNT + 1) {
            nextQueue.addLast(pieceBag.next())
        }
    }

    fun moveHorizontal(direction: Int): Boolean {
        val piece = activePiece ?: return false
        val moved = piece.move(direction, 0, grid)
        if (moved) {
            lockPending = false
            lockTimerMs = LOCK_DELAY_MS
        }
        return moved
    }

    fun rotateClockwise(): Boolean {
        val piece = activePiece ?: return false
        val rotated = piece.rotateClockwise(grid)
        if (rotated) {
            lockPending = false
            lockTimerMs = LOCK_DELAY_MS
        }
        return rotated
    }

    fun holdCurrent(): Boolean {
        if (!settings.holdEnabled) return false
        if (holdUsed) return false
        val piece = activePiece ?: return false
        val currentType = piece.tetromino
        val swapped = holdPiece
        holdPiece = currentType
        holdUsed = true
        activePiece = null
        gravityTimerMs = 0f
        lockPending = false
        lockTimerMs = LOCK_DELAY_MS
        if (swapped == null) {
            spawnNextPiece()
        } else {
            val spawnX = BOARD_COLS / 2 - 2
            val spawnY = -HIDDEN_ROWS
            val newPiece = Piece(swapped, spawnX, spawnY)
            if (!grid.canPlace(newPiece.tetromino, newPiece.x, newPiece.y, newPiece.rotation)) {
                if (gameMode == GameMode.CLASSIC_RELAX) {
                    triggerGameOver()
                } else {
                    riseSuspended = true
                }
                activePiece = null
            } else {
                activePiece = newPiece
            }
        }
        return true
    }

    private fun lockActivePiece() {
        val piece = activePiece ?: return
        piece.forEachCell { col, row ->
            if (row >= 0 && row < grid.rows && col in 0 until grid.cols) {
                grid.set(col, row, piece.tetromino.colorId)
            }
        }
        SoundManager.playPlace()
        lockPulse = 0.3f
        activePiece = null
        holdUsed = false
        lockPending = false
        lockTimerMs = LOCK_DELAY_MS
        gravityTimerMs = 0f

        val result = AvalancheEngine.resolve(grid, 1)
        if (result.totalLines > 0) {
            SoundManager.playClear(result.chainStages.size > 1)
            if (result.chainStages.size > 1) {
                SoundManager.playChain()
            }
            totalLinesCleared += result.totalLines
            score += result.scoreGained
            chainDisplay = max(1, result.lastChainMultiplier)
            if (result.totalLines >= 2) {
                riseTimer += RISE_DELAY_BONUS_SEC
            }
            for (rows in result.chainStages) {
                for (row in rows) {
                    lineClearAnimations.add(LineClearAnimation(row))
                }
            }
            if (result.maxDropDistance > 0) {
                avalancheAnimation.start(result.maxDropDistance)
            }
        } else {
            chainDisplay = 1
            if (result.anyDrop) {
                SoundManager.playChain()
            }
        }

        updateBestScores()
        if (grid.hasOverflow()) {
            if (gameMode == GameMode.CLASSIC_RELAX) {
                triggerGameOver()
            } else {
                riseSuspended = true
            }
        }
    }

    private fun updateBestScores() {
        if (gameMode == GameMode.ZEN) {
            bestZenScore = max(bestZenScore, score)
        } else {
            bestClassicScore = max(bestClassicScore, score)
        }
    }

    private fun triggerGameOver() {
        gameOver = true
        isPaused = true
        updateBestScores()
    }

    private fun updateAnimations(deltaSec: Float) {
        if (lockPulse > 0f) {
            lockPulse = max(0f, lockPulse - deltaSec)
        }
        val iterator = lineClearAnimations.iterator()
        while (iterator.hasNext()) {
            val anim = iterator.next()
            anim.elapsed += deltaSec
            if (anim.elapsed >= anim.duration) {
                iterator.remove()
            }
        }
        avalancheAnimation.update(deltaSec, grid)
        risingAnimation.update(deltaSec)
    }

    fun currentGravityProgress(): Float {
        val interval = gravityIntervalMs
        if (interval <= 0f) return 0f
        return gravityTimerMs / interval
    }

    fun forEachPreview(consumer: (index: Int, Tetromino) -> Unit) {
        var index = 0
        for (tetromino in nextQueue) {
            if (index >= PREVIEW_COUNT) {
                break
            }
            consumer(index, tetromino)
            index++
        }
    }

    fun currentRiseInterval(): Float = riseInterval

    fun riseCountdown(): Float = riseTimer

    fun isRiseSuspended(): Boolean = riseSuspended

    fun snapshot(): GameSnapshot {
        return GameSnapshot(
            gridData = grid.serialize(),
            activeType = activePiece?.tetromino?.name,
            activeX = activePiece?.x ?: 0,
            activeY = activePiece?.y ?: 0,
            activeRotation = activePiece?.rotation ?: 0,
            holdType = holdPiece?.name,
            holdUsed = holdUsed,
            nextQueue = nextQueue.joinToString(",") { it.name },
            bagState = pieceBag.serialize(),
            score = score,
            totalLines = totalLinesCleared,
            chain = chainDisplay,
            riseInterval = riseInterval,
            riseTimer = riseTimer,
            gravityTimer = gravityTimerMs,
            lockTimer = lockTimerMs,
            lockPending = lockPending,
            timeSinceStart = timeSinceStart,
            difficultySteps = difficultySteps,
            difficultyTimer = difficultyTimer,
            riseSuspended = riseSuspended,
            gameMode = gameMode.name,
            isPaused = isPaused,
            gameOver = gameOver,
            bestZen = bestZenScore,
            bestClassic = bestClassicScore,
            settings = settings
        )
    }

    fun restore(snapshot: GameSnapshot) {
        settings = snapshot.settings.copy()
        gameMode = settings.mode()
        difficultySteps = snapshot.difficultySteps
        difficultyTimer = snapshot.difficultyTimer
        applySettingsInternal()
        grid.restore(snapshot.gridData)
        pieceBag.restore(snapshot.bagState)
        nextQueue.clear()
        if (snapshot.nextQueue.isNotEmpty()) {
            val tokens = snapshot.nextQueue.split(',')
            for (token in tokens) {
                val tetro = Tetromino.fromName(token)
                if (tetro != null) {
                    nextQueue.addLast(tetro)
                }
            }
        }
        holdPiece = Tetromino.fromName(snapshot.holdType)
        holdUsed = snapshot.holdUsed
        score = snapshot.score
        totalLinesCleared = snapshot.totalLines
        chainDisplay = snapshot.chain
        riseInterval = computeRiseIntervalInternal()
        riseTimer = snapshot.riseTimer
        gravityTimerMs = snapshot.gravityTimer
        lockTimerMs = snapshot.lockTimer
        lockPending = snapshot.lockPending
        timeSinceStart = snapshot.timeSinceStart
        difficultySteps = snapshot.difficultySteps
        difficultyTimer = snapshot.difficultyTimer
        riseSuspended = snapshot.riseSuspended
        isPaused = snapshot.isPaused
        gameOver = snapshot.gameOver
        bestZenScore = snapshot.bestZen
        bestClassicScore = snapshot.bestClassic
        lockPulse = 0f
        lineClearAnimations.clear()
        avalancheAnimation.reset()
        risingAnimation.reset()

        refillPreview()

        val activeName = Tetromino.fromName(snapshot.activeType)
        activePiece = if (activeName != null) {
            Piece(activeName, snapshot.activeX, snapshot.activeY).also {
                it.applyRotation(snapshot.activeRotation)
            }
        } else {
            null
        }
        ensureActivePiece()
        SoundManager.setVolumes(settings)
    }

    data class LineClearAnimation(val absoluteRow: Int, var elapsed: Float = 0f, val duration: Float = 0.35f)

    class AvalancheAnimation {
        private var timer = 0f
        private var duration = 0.3f
        var distance: Int = 0
            private set
        var active: Boolean = false
            private set

        fun start(maxDistance: Int) {
            distance = maxDistance
            timer = 0f
            duration = 0.3f
            active = true
        }

        fun update(deltaSec: Float, grid: Grid) {
            if (!active) return
            timer += deltaSec
            if (timer >= duration) {
                active = false
                distance = 0
                grid.clearDropDistances()
            }
        }

        fun progress(): Float {
            if (!active) return 1f
            return clamp(timer / duration, 0f, 1f)
        }

        fun reset() {
            active = false
            distance = 0
            timer = 0f
        }
    }

    class RisingAnimation {
        private var timer = 0f
        private var duration = 0.35f
        var active: Boolean = false
            private set

        fun start() {
            timer = 0f
            active = true
        }

        fun update(deltaSec: Float) {
            if (!active) return
            timer += deltaSec
            if (timer >= duration) {
                active = false
            }
        }

        fun progress(): Float {
            if (!active) return 1f
            return clamp(timer / duration, 0f, 1f)
        }

        fun reset() {
            active = false
            timer = 0f
        }
    }

    data class GameSnapshot(
        val gridData: String,
        val activeType: String?,
        val activeX: Int,
        val activeY: Int,
        val activeRotation: Int,
        val holdType: String?,
        val holdUsed: Boolean,
        val nextQueue: String,
        val bagState: String,
        val score: Long,
        val totalLines: Int,
        val chain: Int,
        val riseInterval: Float,
        val riseTimer: Float,
        val gravityTimer: Float,
        val lockTimer: Float,
        val lockPending: Boolean,
        val timeSinceStart: Float,
        val difficultySteps: Int,
        val difficultyTimer: Float,
        val riseSuspended: Boolean,
        val gameMode: String,
        val isPaused: Boolean,
        val gameOver: Boolean,
        val bestZen: Long,
        val bestClassic: Long,
        val settings: GameSettings
    )

    private fun computeRiseIntervalInternal(): Float {
        val target = RISE_INTERVAL_SEC - difficultySteps * CLASSIC_RISE_DECREMENT
        return max(MIN_RISE_INTERVAL_SEC, target)
    }

    companion object {
        private const val PREVIEW_COUNT = 3
    }
}

private fun <E> ArrayDeque<E>.removeFirstOrNull(): E? = if (isEmpty()) null else removeFirst()
