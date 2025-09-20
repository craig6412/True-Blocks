package com.example.blockcrack.sim

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// TODO: Tweak gridW, gridH, noiseStrength, crackRadiusCells to match your device performance.
object WorldConfig {
    var gridW: Int = 48
    var gridH: Int = 96
    var noiseStrength: Float = 0.35f
    var crackRadiusCells: Int = 1
    var gravityStepsPerFrame: Int = 1
}

/**
 * Cellular grid simulation that drives the falling rainbow blocks. The world keeps two buffers and
 * swaps them every step, so there are no per-frame allocations or object churn.
 */
class World(initialW: Int = WorldConfig.gridW, initialH: Int = WorldConfig.gridH) {

    data class Dimensions(var width: Int = 0, var height: Int = 0)

    private class MovementStats {
        var moved: Int = 0
        var sumX: Float = 0f
        var sumY: Float = 0f
        fun reset() {
            moved = 0
            sumX = 0f
            sumY = 0f
        }
    }

    data class GridPoint(val x: Int, val y: Int)

    private val random = Random(System.nanoTime())
    private val movementStats = MovementStats()

    @Volatile
    private var width: Int = initialW
    @Volatile
    private var height: Int = initialH

    private var cur: ByteArray = ByteArray(width * height)
    private var next: ByteArray = ByteArray(width * height)
    private var rowNoiseMask: BooleanArray = BooleanArray(height)
    private var rowNoiseDir: BooleanArray = BooleanArray(height)

    private var frame: Long = 0

    @Volatile
    var lastMovedCells: Int = 0
        private set

    @Volatile
    var lastMovementCenterX: Float = width / 2f
        private set

    @Volatile
    var lastMovementCenterY: Float = height / 2f
        private set

    init {
        next.fill(EMPTY)
        fillRainbowUnlocked()
    }

    @Synchronized
    fun ensureSize(newWidth: Int, newHeight: Int) {
        if (newWidth == width && newHeight == height) return
        width = newWidth
        height = newHeight
        cur = ByteArray(width * height)
        next = ByteArray(width * height)
        rowNoiseMask = BooleanArray(height)
        rowNoiseDir = BooleanArray(height)
        lastMovementCenterX = width / 2f
        lastMovementCenterY = height / 2f
        fillRainbowUnlocked()
    }

    @Synchronized
    fun capacity(): Int = width * height

    @Synchronized
    fun snapshot(into: ByteArray, out: Dimensions) {
        val size = width * height
        require(into.size >= size) { "Snapshot buffer too small" }
        cur.copyInto(into, 0, 0, size)
        out.width = width
        out.height = height
    }

    @Synchronized
    fun getDimensions(out: Dimensions) {
        out.width = width
        out.height = height
    }

    @Synchronized
    fun fillRainbow() {
        fillRainbowUnlocked()
    }

    @Synchronized
    fun carve(points: List<GridPoint>): Int {
        if (points.isEmpty()) return 0
        val radius = max(1, WorldConfig.crackRadiusCells)
        val radiusSq = radius * radius
        var removed = 0
        for (pt in points) {
            val minX = max(0, pt.x - radius)
            val maxX = min(width - 1, pt.x + radius)
            val minY = max(0, pt.y - radius)
            val maxY = min(height - 1, pt.y + radius)
            for (y in minY..maxY) {
                val dy = y - pt.y
                val row = y * width
                for (x in minX..maxX) {
                    val dx = x - pt.x
                    if (dx * dx + dy * dy <= radiusSq) {
                        val idx = row + x
                        if (cur[idx] != EMPTY) {
                            cur[idx] = EMPTY
                            next[idx] = EMPTY
                            removed++
                        }
                    }
                }
            }
        }
        return removed
    }

    @Synchronized
    fun step(): Int {
        var totalMoved = 0
        var sumX = 0f
        var sumY = 0f
        repeat(max(1, WorldConfig.gravityStepsPerFrame)) {
            val stats = stepOnce()
            totalMoved += stats.moved
            sumX += stats.sumX
            sumY += stats.sumY
        }
        if (totalMoved > 0) {
            lastMovementCenterX = sumX / totalMoved
            lastMovementCenterY = sumY / totalMoved
        }
        lastMovedCells = totalMoved
        frame++
        return totalMoved
    }

    private fun stepOnce(): MovementStats {
        movementStats.reset()
        ensureNoiseCapacity()
        refreshRowNoise()

        val width = width
        val height = height
        val cur = cur
        val next = next

        val bottomStart = (height - 1) * width
        // Process rows from second-to-last up to the top
        for (y in height - 2 downTo 0) {
            val rowStart = y * width
            val belowStart = rowStart + width
            val noiseOverride = rowNoiseMask[y]
            val noisePrefersLeft = rowNoiseDir[y]
            for (x in 0 until width) {
                val idx = rowStart + x
                val cell = cur[idx]
                if (cell == EMPTY) continue

                val downIndex = belowStart + x
                if (cur[downIndex] == EMPTY && next[downIndex] == EMPTY) {
                    next[downIndex] = cell
                    movementStats.moved++
                    movementStats.sumX += x.toFloat()
                    movementStats.sumY += (y + 1).toFloat()
                    continue
                }

                var leftFirst = ((frame + x + y) and 1) == 0
                if (noiseOverride) {
                    leftFirst = noisePrefersLeft
                }

                var placed = false
                if (leftFirst) {
                    placed = tryMoveDiagonal(x - 1, belowStart, cell)
                    if (!placed) {
                        placed = tryMoveDiagonal(x + 1, belowStart, cell)
                    }
                } else {
                    placed = tryMoveDiagonal(x + 1, belowStart, cell)
                    if (!placed) {
                        placed = tryMoveDiagonal(x - 1, belowStart, cell)
                    }
                }

                if (placed) {
                    // tryMoveDiagonal updates movementStats
                    continue
                }

                // Stay in place
                next[idx] = cell
            }
        }

        // Copy any remaining cells in the bottom row that were not written this step
        for (x in 0 until width) {
            val idx = bottomStart + x
            if (next[idx] == EMPTY) {
                next[idx] = cur[idx]
            }
        }

        swapBuffers()
        return movementStats
    }

    private fun tryMoveDiagonal(targetX: Int, belowStart: Int, cell: Byte): Boolean {
        if (targetX < 0 || targetX >= width) return false
        val destIndex = belowStart + targetX
        if (cur[destIndex] != EMPTY || next[destIndex] != EMPTY) return false
        next[destIndex] = cell
        movementStats.moved++
        movementStats.sumX += targetX.toFloat()
        movementStats.sumY += (destIndex / width).toFloat()
        return true
    }

    private fun swapBuffers() {
        val tmp = cur
        cur = next
        next = tmp
        next.fill(EMPTY)
    }

    private fun ensureNoiseCapacity() {
        if (rowNoiseMask.size != height) {
            rowNoiseMask = BooleanArray(height)
            rowNoiseDir = BooleanArray(height)
        }
    }

    private fun refreshRowNoise() {
        val noiseStrength = min(1f, max(0f, WorldConfig.noiseStrength))
        if (noiseStrength <= 0f) {
            rowNoiseMask.fill(false)
            return
        }
        for (y in 0 until height) {
            if (random.nextFloat() < noiseStrength) {
                rowNoiseMask[y] = true
                rowNoiseDir[y] = random.nextBoolean()
            } else {
                rowNoiseMask[y] = false
            }
        }
    }

    private fun fillRainbowUnlocked() {
        val width = width
        val height = height
        val colors = byteArrayOf(7, 6, 5, 4, 3, 2, 1)
        if (cur.size != width * height) {
            cur = ByteArray(width * height)
        }
        if (next.size != width * height) {
            next = ByteArray(width * height)
        }
        var idx = 0
        for (y in 0 until height) {
            val bandIndex = ((height - 1 - y) * colors.size) / max(1, height)
            val color = colors[min(colors.size - 1, max(0, bandIndex))]
            for (x in 0 until width) {
                cur[idx++] = color
            }
        }
        next.fill(EMPTY)
        lastMovementCenterX = width / 2f
        lastMovementCenterY = height / 2f
        lastMovedCells = 0
    }

    companion object {
        private const val EMPTY: Byte = 0
    }
}
