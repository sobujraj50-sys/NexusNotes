package com.nexusnotes.core.common

/** Source of the current time in epoch milliseconds. Replaced by a fake in tests. */
fun interface Clock {
    fun now(): Long
}

object WallClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}
