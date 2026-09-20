package ir.metra.app.core.common

/**
 * Time source abstraction.
 *
 * Injected rather than calling `System.currentTimeMillis()` inline so that
 * tests can freeze time and so timestamped rows stay deterministic.
 */
fun interface Clock {
    fun nowEpochMilli(): Long
}

/** Production clock: wall time. */
class SystemClock : Clock {
    override fun nowEpochMilli(): Long = System.currentTimeMillis()
}
