package com.spectra.blockrise

import kotlin.random.Random

/**
 * Implements a 7-bag tetromino randomizer. Ensures each bag contains exactly one of every tetromino.
 */
class PieceBag(private val random: Random = Random(System.currentTimeMillis())) {
    private val bag = ArrayDeque<Tetromino>()

    init {
        refill()
    }

    fun next(): Tetromino {
        if (bag.isEmpty()) {
            refill()
        }
        return bag.removeFirst()
    }

    fun preview(count: Int): List<Tetromino> {
        val result = ArrayList<Tetromino>(count)
        var i = 0
        for (tetromino in bag) {
            if (i >= count) break
            result += tetromino
            i++
        }
        if (i < count) {
            // If the preview spans beyond the current bag, create a hypothetical refill for visibility.
            val remainder = mutableListOf<Tetromino>()
            Tetromino.values().toCollection(remainder)
            remainder.shuffle(random)
            var idx = 0
            while (i < count && idx < remainder.size) {
                result += remainder[idx]
                i++
                idx++
            }
        }
        return result
    }

    fun serialize(): String = bag.joinToString(",") { it.name }

    fun restore(serialized: String?) {
        bag.clear()
        if (!serialized.isNullOrEmpty()) {
            val tokens = serialized.split(',')
            for (token in tokens) {
                val tetro = Tetromino.fromName(token)
                if (tetro != null) {
                    bag += tetro
                }
            }
        }
        if (bag.isEmpty()) {
            refill()
        }
    }

    private fun refill() {
        val values = Tetromino.values().toMutableList()
        values.shuffle(random)
        bag.addAll(values)
    }
}
