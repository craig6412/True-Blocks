package com.spectra.blockrise

import kotlin.math.min

/**
 * Backing grid for the playfield. Stores tile color ids and exposes helpers for collision, line checks, and rises.
 */
class Grid(val cols: Int = BOARD_COLS, val rows: Int = BOARD_TOTAL_ROWS) {
    internal val cells = IntArray(cols * rows)
    internal val lastDropDistances = IntArray(cols * rows)

    fun clear() {
        cells.fill(0)
        lastDropDistances.fill(0)
    }

    fun get(col: Int, row: Int): Int {
        if (!isInside(col, row)) return -1
        return cells[index(col, row)]
    }

    fun set(col: Int, row: Int, value: Int) {
        if (!isInside(col, row)) return
        cells[index(col, row)] = value
    }

    fun addPiece(piece: Piece) {
        piece.forEachCell { col, row ->
            if (isInside(col, row)) {
                cells[index(col, row)] = piece.tetromino.colorId
            }
        }
    }

    fun canPlace(tetromino: Tetromino, baseX: Int, baseY: Int, rotation: Int): Boolean {
        var valid = true
        tetromino.forEachCell(rotation, baseX, baseY) { col, row ->
            if (col !in 0 until cols || row >= rows) {
                valid = false
                return@forEachCell
            }
            if (row >= 0 && cells[index(col, row)] != 0) {
                valid = false
                return@forEachCell
            }
        }
        return valid
    }

    fun isRowFull(row: Int): Boolean {
        if (row !in 0 until rows) return false
        val base = row * cols
        for (c in 0 until cols) {
            if (cells[base + c] == 0) return false
        }
        return true
    }

    fun gatherFullRows(): IntArray {
        val temp = IntArray(rows)
        var count = 0
        for (r in 0 until rows) {
            if (isRowFull(r)) {
                temp[count++] = r
            }
        }
        return temp.copyOf(count)
    }

    fun clearRows(rowsToClear: IntArray) {
        if (rowsToClear.isEmpty()) return
        rowsToClear.sort()
        var writeRow = rows - 1
        var readRow = rows - 1
        var clearIdx = rowsToClear.size - 1
        while (readRow >= 0) {
            if (clearIdx >= 0 && readRow == rowsToClear[clearIdx]) {
                readRow--
                clearIdx--
                continue
            }
            if (writeRow != readRow) {
                copyRow(readRow, writeRow)
            }
            readRow--
            writeRow--
        }
        while (writeRow >= 0) {
            clearRow(writeRow)
            writeRow--
        }
        lastDropDistances.fill(0)
    }

    fun clearRow(row: Int) {
        val base = row * cols
        for (c in 0 until cols) {
            cells[base + c] = 0
        }
    }

    fun copyRow(fromRow: Int, toRow: Int) {
        val srcBase = fromRow * cols
        val dstBase = toRow * cols
        for (c in 0 until cols) {
            cells[dstBase + c] = cells[srcBase + c]
            lastDropDistances[dstBase + c] = lastDropDistances[srcBase + c]
        }
    }

    fun riseWithGarbage(random: kotlin.random.Random): RiseResult {
        val newRow = IntArray(cols)
        var gap = random.nextInt(cols)
        for (c in 0 until cols) {
            val filled = if (c == gap) 0 else if (random.nextFloat() < 0.6f) GARBAGE_COLOR else 0
            newRow[c] = filled
        }
        if (newRow.all { it != 0 }) {
            val forcedGap = random.nextInt(cols)
            newRow[forcedGap] = 0
        }

        val overflow = hasOverflowBeforeRise()
        // Shift board upwards by one row.
        for (r in 0 until rows - 1) {
            val srcBase = (r + 1) * cols
            val dstBase = r * cols
            for (c in 0 until cols) {
                cells[dstBase + c] = cells[srcBase + c]
                lastDropDistances[dstBase + c] = 0
            }
        }
        val bottomBase = (rows - 1) * cols
        for (c in 0 until cols) {
            cells[bottomBase + c] = newRow[c]
            lastDropDistances[bottomBase + c] = 0
        }
        return RiseResult(overflow || hasOverflow(), newRow)
    }

    private fun hasOverflowBeforeRise(): Boolean {
        for (r in 0 until HIDDEN_ROWS) {
            for (c in 0 until cols) {
                if (cells[index(c, r)] != 0) {
                    return true
                }
            }
        }
        return false
    }

    fun hasOverflow(): Boolean {
        for (r in 0 until HIDDEN_ROWS) {
            for (c in 0 until cols) {
                if (cells[index(c, r)] != 0) {
                    return true
                }
            }
        }
        return false
    }

    fun hiddenRowsClear(): Boolean {
        for (r in 0 until HIDDEN_ROWS) {
            for (c in 0 until cols) {
                if (cells[index(c, r)] != 0) return false
            }
        }
        return true
    }

    fun visibleCell(col: Int, visibleRow: Int): Int {
        return get(col, visibleRow + HIDDEN_ROWS)
    }

    fun markDropDistance(col: Int, row: Int, distance: Int) {
        if (!isInside(col, row)) return
        lastDropDistances[index(col, row)] = distance
    }

    fun consumeDropDistance(col: Int, row: Int): Int {
        if (!isInside(col, row)) return 0
        val idx = index(col, row)
        val value = lastDropDistances[idx]
        return value
    }

    fun clearDropDistances() {
        lastDropDistances.fill(0)
    }

    fun serialize(): String = buildString(cols * rows * 2) {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                append(cells[index(c, r)])
                if (!(r == rows - 1 && c == cols - 1)) {
                    append(',')
                }
            }
        }
    }

    fun restore(serialized: String?) {
        clear()
        if (serialized.isNullOrEmpty()) return
        val tokens = serialized.split(',')
        val limit = min(tokens.size, cells.size)
        for (i in 0 until limit) {
            val value = tokens[i].toIntOrNull() ?: 0
            cells[i] = value
        }
    }

    fun unitTestCollision(): Boolean {
        val piece = Piece(Tetromino.O, 0, 0)
        piece.forEachCell { col, row ->
            require(col in 0 until cols)
            require(row in 0 until rows)
        }
        return true
    }

    private fun isInside(col: Int, row: Int): Boolean = col in 0 until cols && row in 0 until rows
    private fun index(col: Int, row: Int): Int = row * cols + col

    data class RiseResult(val overflowed: Boolean, val row: IntArray)

    companion object {
        const val GARBAGE_COLOR = 8
    }
}
