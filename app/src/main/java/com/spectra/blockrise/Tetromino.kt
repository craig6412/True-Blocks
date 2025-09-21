package com.spectra.blockrise

enum class Tetromino(val colorId: Int, private val rotations: Array<IntArray>) {
    I(1, arrayOf(
        intArrayOf(0, 1, 1, 1, 2, 1, 3, 1),
        intArrayOf(2, 0, 2, 1, 2, 2, 2, 3),
        intArrayOf(0, 2, 1, 2, 2, 2, 3, 2),
        intArrayOf(1, 0, 1, 1, 1, 2, 1, 3)
    )),
    J(2, arrayOf(
        intArrayOf(0, 0, 0, 1, 1, 1, 2, 1),
        intArrayOf(1, 0, 2, 0, 1, 1, 1, 2),
        intArrayOf(0, 1, 1, 1, 2, 1, 2, 2),
        intArrayOf(1, 0, 1, 1, 0, 2, 1, 2)
    )),
    L(3, arrayOf(
        intArrayOf(2, 0, 0, 1, 1, 1, 2, 1),
        intArrayOf(1, 0, 1, 1, 1, 2, 2, 2),
        intArrayOf(0, 1, 1, 1, 2, 1, 0, 2),
        intArrayOf(0, 0, 1, 0, 1, 1, 1, 2)
    )),
    O(4, arrayOf(
        intArrayOf(1, 0, 2, 0, 1, 1, 2, 1),
        intArrayOf(1, 0, 2, 0, 1, 1, 2, 1),
        intArrayOf(1, 0, 2, 0, 1, 1, 2, 1),
        intArrayOf(1, 0, 2, 0, 1, 1, 2, 1)
    )),
    S(5, arrayOf(
        intArrayOf(1, 0, 2, 0, 0, 1, 1, 1),
        intArrayOf(1, 0, 1, 1, 2, 1, 2, 2),
        intArrayOf(1, 1, 2, 1, 0, 2, 1, 2),
        intArrayOf(0, 0, 0, 1, 1, 1, 1, 2)
    )),
    T(6, arrayOf(
        intArrayOf(1, 0, 0, 1, 1, 1, 2, 1),
        intArrayOf(1, 0, 1, 1, 2, 1, 1, 2),
        intArrayOf(0, 1, 1, 1, 2, 1, 1, 2),
        intArrayOf(1, 0, 0, 1, 1, 1, 1, 2)
    )),
    Z(7, arrayOf(
        intArrayOf(0, 0, 1, 0, 1, 1, 2, 1),
        intArrayOf(2, 0, 1, 1, 2, 1, 1, 2),
        intArrayOf(0, 1, 1, 1, 1, 2, 2, 2),
        intArrayOf(1, 0, 0, 1, 1, 1, 0, 2)
    ));

    fun forEachCell(rotationIndex: Int, originX: Int, originY: Int, consumer: (Int, Int) -> Unit) {
        val data = rotations[rotationIndex % rotations.size]
        var idx = 0
        while (idx < data.size) {
            consumer(originX + data[idx], originY + data[idx + 1])
            idx += 2
        }
    }

    fun rotationCount(): Int = rotations.size

    companion object {
        fun fromName(name: String?): Tetromino? = values().firstOrNull { it.name == name }
    }
}
