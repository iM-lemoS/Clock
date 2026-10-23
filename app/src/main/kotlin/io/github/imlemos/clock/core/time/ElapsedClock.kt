package io.github.imlemos.clock.core.time

/** Milliseconds since boot (`SystemClock.elapsedRealtime`); a seam for tests. */
fun interface ElapsedClock {
    fun millis(): Long
}
