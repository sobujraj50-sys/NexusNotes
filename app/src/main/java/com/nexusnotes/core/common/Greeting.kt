package com.nexusnotes.core.common

/** Pure function so it can be unit-tested without Android. */
fun greetingForHour(hour: Int): String {
    require(hour in 0..23) { "hour must be 0..23, was $hour" }
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Working late"
    }
}
