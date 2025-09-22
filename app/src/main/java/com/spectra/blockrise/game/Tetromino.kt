package com.spectra.blockcrush.game

import kotlin.random.Random

enum class Tetromino { I, O, T, S, Z, J, L }

data class Point(val x: Int, val y: Int)

object Shapes {
    private fun rot(vararg rows: String) = BooleanArray(16) { idx ->
        val r = idx / 4; val c = idx % 4; rows[r][c] == '1'
    }

    private val I = arrayOf(
        rot("0000","1111","0000","0000"),
        rot("0010","0010","0010","0010"),
        rot("0000","1111","0000","0000"),
        rot("0010","0010","0010","0010")
    )
    private val O = arrayOf(
        rot("0110","0110","0000","0000"),
        rot("0110","0110","0000","0000"),
        rot("0110","0110","0000","0000"),
        rot("0110","0110","0000","0000")
    )
    private val T = arrayOf(
        rot("0100","1110","0000","0000"),
        rot("0100","0110","0100","0000"),
        rot("0000","1110","0100","0000"),
        rot("0100","1100","0100","0000"),
    )
    private val S = arrayOf(
        rot("0110","1100","0000","0000"),
        rot("0100","0110","0010","0000"),
        rot("0110","1100","0000","0000"),
        rot("0100","0110","0010","0000"),
    )
    private val Z = arrayOf(
        rot("1100","0110","0000","0000"),
        rot("0010","0110","0100","0000"),
        rot("1100","0110","0000","0000"),
        rot("0010","0110","0100","0000"),
    )
    private val J = arrayOf(
        rot("1000","1110","0000","0000"),
        rot("0110","0100","0100","0000"),
        rot("0000","1110","0010","0000"),
        rot("0100","0100","1100","0000"),
    )
    private val L = arrayOf(
        rot("0010","1110","0000","0000"),
        rot("0100","0100","0110","0000"),
        rot("0000","1110","1000","0000"),
        rot("1100","0100","0100","0000"),
    )

    fun mask(kind: Tetromino, rotation: Int): BooleanArray {
        val r = ((rotation % 4) + 4) % 4
        return when(kind) {
            Tetromino.I -> I[r]
            Tetromino.O -> O[r]
            Tetromino.T -> T[r]
            Tetromino.S -> S[r]
            Tetromino.Z -> Z[r]
            Tetromino.J -> J[r]
            Tetromino.L -> L[r]
        }
    }
}

data class Piece(
    val kind: Tetromino,
    val rotation: Int,
    val position: Point
)

class BagRng(seed: Long = System.currentTimeMillis()) {
    private val rng = Random(seed)
    private var bag = Tetromino.values().toMutableList()

    fun next(): Tetromino {
        if (bag.isEmpty()) {
            bag = Tetromino.values().toMutableList()
        }
        bag.shuffle(rng)
        return bag.removeAt(0)
    }
}