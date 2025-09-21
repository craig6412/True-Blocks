package com.spectra.blockrise

import kotlin.math.min

/**
 * Handles line clears, avalanche gravity, and chain scoring. Implements the BFS cluster search described in the spec.
 */
object AvalancheEngine {
    private val neighborX = intArrayOf(-1, 1, 0, 0)
    private val neighborY = intArrayOf(0, 0, -1, 1)

    data class Result(
        val totalLines: Int,
        val scoreGained: Long,
        val lastChainMultiplier: Int,
        val chainStages: List<List<Int>>,
        val anyDrop: Boolean,
        val maxDropDistance: Int
    )

    fun resolve(grid: Grid, startChain: Int = 1): Result {
        val chainRows = mutableListOf<List<Int>>()
        var chainMultiplier = startChain
        var lastChainUsed = startChain
        var totalLines = 0
        var totalScore = 0L
        var anyDrop = false
        var maxDrop = 0
        grid.clearDropDistances()

        while (true) {
            val fullRows = grid.gatherFullRows()
            if (fullRows.isEmpty()) {
                break
            }
            val rowsList = fullRows.toList()
            chainRows += rowsList
            totalLines += fullRows.size
            lastChainUsed = chainMultiplier
            totalScore += scoreForLines(fullRows.size, chainMultiplier)
            grid.clearRows(fullRows)

            val dropInfo = applyGravity(grid)
            if (dropInfo != null) {
                anyDrop = anyDrop || dropInfo.second
                maxDrop = maxOf(maxDrop, dropInfo.first)
            }
            chainMultiplier += 1
        }

        return Result(
            totalLines,
            totalScore,
            if (totalLines == 0) startChain else lastChainUsed,
            chainRows,
            anyDrop,
            maxDrop
        )
    }

    private fun scoreForLines(lines: Int, chain: Int): Long {
        val base = when (lines) {
            1 -> 100
            2 -> 300
            3 -> 500
            4 -> 800
            else -> 100 * lines
        }
        return (base * chain).toLong()
    }

    private fun applyGravity(grid: Grid): Pair<Int, Boolean>? {
        val cols = grid.cols
        val rows = grid.rows
        val size = cols * rows
        val grounded = BooleanArray(size)
        val queue = IntArray(size)
        var head = 0
        var tail = 0

        fun enqueue(idx: Int) {
            if (!grounded[idx]) {
                grounded[idx] = true
                queue[tail++] = idx
            }
        }

        // Seed BFS with all tiles touching the bottom row to build the support/"grounded" set.
        val bottomRow = rows - 1
        for (c in 0 until cols) {
            val idx = bottomRow * cols + c
            if (grid.cells[idx] != 0) {
                enqueue(idx)
            }
        }
        while (head < tail) {
            val current = queue[head++]
            val cx = current % cols
            val cy = current / cols
            var dir = 0
            while (dir < neighborX.size) {
                val nx = cx + neighborX[dir]
                val ny = cy + neighborY[dir]
                dir++
                if (nx in 0 until cols && ny in 0 until rows) {
                    val nIdx = ny * cols + nx
                    if (grid.cells[nIdx] != 0 && !grounded[nIdx]) {
                        enqueue(nIdx)
                    }
                }
            }
        }

        // Collect floating clusters using BFS. Anything not connected to the grounded set is a
        // disconnected island that should fall together as a uniform cluster.
        val visitedFloating = BooleanArray(size)
        val clusters = ArrayList<IntArray>()
        val stack = IntArray(size)
        for (idx in 0 until size) {
            if (grid.cells[idx] != 0 && !grounded[idx] && !visitedFloating[idx]) {
                var stackSize = 0
                stack[stackSize++] = idx
                visitedFloating[idx] = true
                var read = 0
                while (read < stackSize) {
                    val current = stack[read++]
                    val cx = current % cols
                    val cy = current / cols
                    var dir = 0
                    while (dir < neighborX.size) {
                        val nx = cx + neighborX[dir]
                        val ny = cy + neighborY[dir]
                        dir++
                        if (nx in 0 until cols && ny in 0 until rows) {
                            val neighborIdx = ny * cols + nx
                            if (grid.cells[neighborIdx] != 0 && !grounded[neighborIdx] && !visitedFloating[neighborIdx]) {
                                visitedFloating[neighborIdx] = true
                                stack[stackSize++] = neighborIdx
                            }
                        }
                    }
                }
                clusters += stack.copyOf(stackSize)
            }
        }
        if (clusters.isEmpty()) {
            return null
        }

        val occupied = BooleanArray(size)
        for (i in 0 until size) {
            occupied[i] = grid.cells[i] != 0
        }

        val dropDistances = IntArray(clusters.size)
        for (i in clusters.indices) {
            dropDistances[i] = computeDropDistance(clusters[i], occupied, cols, rows)
        }

        var droppedAny = false
        var maxDropDistance = 0
        for (i in clusters.indices) {
            val cluster = clusters[i]
            val distance = dropDistances[i]
            if (distance <= 0) continue
            droppedAny = true
            if (distance > maxDropDistance) {
                maxDropDistance = distance
            }
            val values = IntArray(cluster.size)
            for (j in cluster.indices) {
                val index = cluster[j]
                values[j] = grid.cells[index]
                occupied[index] = false
                grid.cells[index] = 0
                grid.lastDropDistances[index] = 0
            }
            for (j in cluster.indices) {
                val index = cluster[j]
                val col = index % cols
                val row = index / cols
                val targetRow = row + distance
                val targetIdx = targetRow * cols + col
                grid.cells[targetIdx] = values[j]
                grid.lastDropDistances[targetIdx] = distance
                occupied[targetIdx] = true
            }
        }
        return maxDropDistance to droppedAny
    }

    private fun computeDropDistance(cluster: IntArray, occupied: BooleanArray, cols: Int, rows: Int): Int {
        for (index in cluster) {
            occupied[index] = false
        }
        var minDistance = Int.MAX_VALUE
        for (index in cluster) {
            val col = index % cols
            var drop = 0
            var nextRow = index / cols + 1
            while (nextRow < rows) {
                val nextIdx = nextRow * cols + col
                if (occupied[nextIdx]) {
                    break
                }
                drop++
                nextRow++
            }
            minDistance = min(minDistance, drop)
        }
        if (minDistance == Int.MAX_VALUE) {
            minDistance = 0
        }
        for (index in cluster) {
            occupied[index] = true
        }
        return minDistance
    }
}
