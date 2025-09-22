package com.spectra.blockcrush.game

import kotlin.math.max

class GameLogic(seed: Long = System.currentTimeMillis()) {
    private val rng = BagRng(seed)
    var state = GameState(rngSeed = seed)
        private set
    private var fallTimer = 0L

    fun start() {
        state = GameState(rngSeed = state.rngSeed)
        spawn()
    }

    private fun spawn() {
        val k = state.next
        val piece = Piece(k, 0, Point((state.width - 4) / 2, -1))
        state = state.copy(active = piece, next = rng.next())
        if (collides(piece)) state = state.copy(gameOver = true)
    }

    private fun cells(p: Piece) = buildList {
        val m = Shapes.mask(p.kind, p.rotation)
        for (i in 0 until 16) if (m[i]) {
            val cx = i % 4;
            val cy = i / 4
            add(Point(p.position.x + cx, p.position.y + cy))
        }
    }

    private fun collides(p: Piece): Boolean {
        for (c in cells(p)) {
            if (c.x < 0 || c.x >= state.width) return true
            if (c.y >= state.height) return true
            if (c.y >= 0 && state.get(c.x, c.y) != 0) return true
        }
        return false
    }

    fun move(dx: Int) {
        val a = state.active ?: return
        val moved = a.copy(position = Point(a.position.x + dx, a.position.y))
        if (!collides(moved)) state = state.copy(active = moved)
    }

    fun rotate(clockwise: Boolean) {
        val a = state.active ?: return
        val r = if (clockwise) a.rotation + 1 else a.rotation - 1
        val rotated = a.copy(rotation = r)
        for (kick in listOf(0, -1, 1, -2, 2)) {
            val cand = rotated.copy(position = Point(a.position.x + kick, a.position.y))
            if (!collides(cand)) {
                state = state.copy(active = cand); return
            }
        }
    }

    fun softDrop() {
        val a = state.active ?: return
        val down = a.copy(position = Point(a.position.x, a.position.y + 1))
        if (!collides(down)) state = state.copy(active = down) else lockPiece()
    }

    fun tick(dtMs: Long) {
        if (state.gameOver) return
        fallTimer += dtMs
        val newRise = state.riseTimerMs - dtMs
        state = state.copy(riseTimerMs = newRise)
        if (fallTimer >= Config.FALL_INTERVAL_MS) {
            fallTimer = 0L
            softDrop()
        }
        if (state.riseTimerMs <= 0L) {
            injectGarbageRow()
            state =
                state.copy(riseTimerMs = max(Config.RISING_MIN_PERIOD_MS, state.risePeriodMs - 50))
        }
    }

    private fun lockPiece() {
        val a = state.active ?: return
        for (c in cells(a)) {
            if (c.y < 0) {
                state = state.copy(gameOver = true); return
            }
            if (c.y < state.height) state.set(c.x, c.y, a.kind.ordinal + 1)
        }
        state = state.copy(active = null)
        clearAndCascade()
        if (!state.gameOver) spawn()
    }

    private fun clearAndCascade() {
        var cascades = 0
        while (true) {
            val cleared = clearLines()
            if (cleared == 0) break
            applyGravity()
            cascades += 1
            state = state.copy(score = state.score + 100L * cleared * max(1, cascades))
        }
        state = state.copy(cascadeDepth = cascades)
    }

    private fun clearLines(): Int {
        var cleared = 0
        for (y in 0 until state.height) {
            var full = true
            for (x in 0 until state.width) if (state.get(x, y) == 0) {
                full = false; break
            }
            if (full) {
                cleared++
                for (yy in y downTo 1) for (x in 0 until state.width) state.set(
                    x,
                    yy,
                    state.get(x, yy - 1)
                )
                for (x in 0 until state.width) state.set(x, 0, 0)
            }
        }
        return cleared
    }

    private fun applyGravity() {
        var moved: Boolean
        do {
            moved = false
            for (y in (state.height - 2) downTo 0) {
                for (x in 0 until state.width) {
                    val v = state.get(x, y)
                    if (v != 0 && state.get(x, y + 1) == 0) {
                        state.set(x, y, 0); state.set(x, y + 1, v); moved = true
                    }
                }
            }
        } while (moved)
    }

    fun injectGarbageRow(gaps: Int = 3) {
        // push everything up by 1
        for (y in 0 until state.height - 1) {
            for (x in 0 until state.width) {
                state.set(x, y, state.get(x, y + 1))
            }
        }
        // bottom row with random gaps
        val holes = mutableSetOf<Int>()
        while (holes.size < gaps) holes += (0 until state.width).random()
        for (x in 0 until state.width) {
            state.set(x, state.height - 1, if (x in holes) 0 else 8) // <-- 8 = garbage sprite id
        }
        // overflow check (hidden rows)
        for (x in 0 until state.width) {
            if (state.get(x, -1) != 0) {
                state = state.copy(gameOver = true); return
            }
        }
    }
}