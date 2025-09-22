package com.spectra.blockcrush.game

data class GameState(
    val width: Int = Config.BOARD_WIDTH,
    val height: Int = Config.BOARD_HEIGHT,
    val grid: IntArray = IntArray(Config.BOARD_WIDTH * (Config.BOARD_HEIGHT + Config.HIDDEN_ROWS)),
    val active: Piece? = null,
    val next: Tetromino = Tetromino.T,
    val score: Long = 0,
    val cascadeDepth: Int = 0,
    val riseTimerMs: Long = Config.RISING_START_PERIOD_MS,
    val risePeriodMs: Long = Config.RISING_START_PERIOD_MS,
    val rngSeed: Long = System.currentTimeMillis(),
    val gameOver: Boolean = false
) {
    fun idx(x: Int, y: Int) = (y + Config.HIDDEN_ROWS) * width + x
    fun get(x: Int, y: Int) = grid[idx(x,y)]
    fun set(x: Int, y: Int, v: Int) { grid[idx(x,y)] = v }
    fun cloneWithGrid() = copy(grid = grid.copyOf())
}