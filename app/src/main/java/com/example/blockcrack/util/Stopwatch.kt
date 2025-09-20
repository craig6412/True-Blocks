package com.example.blockcrack.util

/**
 * Very small helper used for timing loops. It returns the elapsed seconds between the current call
 * and the previous mark.
 */
class Stopwatch {
    private var lastNanos: Long = System.nanoTime()

    /** Resets the internal clock without reporting a delta. */
    fun reset() {
        lastNanos = System.nanoTime()
    }

    /**
     * Returns the elapsed seconds since the last call to [tickSeconds] (or [reset]) and restarts the
     * clock.
     */
    fun tickSeconds(): Float {
        val now = System.nanoTime()
        val delta = now - lastNanos
        lastNanos = now
        return delta / 1_000_000_000f
    }

    /** Returns the elapsed milliseconds since the previous mark but does not reset the timer. */
    fun peekMillis(): Float {
        val now = System.nanoTime()
        return (now - lastNanos) / 1_000_000f
    }
}
