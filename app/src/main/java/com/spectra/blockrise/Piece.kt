package com.spectra.blockrise

/**
 * Represents the active tetromino on the grid. Responsible for movement, rotation, and wall kicks.
 */
class Piece(var tetromino: Tetromino, startX: Int, startY: Int) {
    var x: Int = startX
        private set
    var y: Int = startY
        private set
    var rotation: Int = 0
        private set

    fun clone(): Piece = Piece(tetromino, x, y).also { it.rotation = rotation }

    fun forEachCell(rotationIndex: Int = rotation, offsetX: Int = 0, offsetY: Int = 0, action: (Int, Int) -> Unit) {
        tetromino.forEachCell(rotationIndex, x + offsetX, y + offsetY, action)
    }

    fun move(dx: Int, dy: Int, grid: Grid): Boolean {
        if (grid.canPlace(tetromino, x + dx, y + dy, rotation)) {
            x += dx
            y += dy
            return true
        }
        return false
    }

    fun moveDown(grid: Grid): Boolean = move(0, 1, grid)

    fun rotateClockwise(grid: Grid): Boolean {
        val nextRotation = (rotation + 1) % tetromino.rotationCount()
        val kicks = WALL_KICK_TESTS
        for (offset in kicks) {
            val newX = x + offset.first
            val newY = y + offset.second
            if (grid.canPlace(tetromino, newX, newY, nextRotation)) {
                x = newX
                y = newY
                rotation = nextRotation
                return true
            }
        }
        return false
    }

    fun resetPosition(newX: Int, newY: Int) {
        x = newX
        y = newY
        rotation = 0
    }

    fun applyRotation(rotationIndex: Int) {
        rotation = rotationIndex % tetromino.rotationCount()
    }

    companion object {
        // Minimal SRS-style kick tests. Try the in-place rotation first, then probe horizontal
        // offsets before finally nudging the piece upward to mimic classic wall kick behaviour.
        private val WALL_KICK_TESTS = arrayOf(
            0 to 0,
            1 to 0,
            -1 to 0,
            2 to 0,
            -2 to 0,
            0 to 1
        )
    }
}
