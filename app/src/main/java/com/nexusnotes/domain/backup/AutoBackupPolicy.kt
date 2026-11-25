package com.nexusnotes.domain.backup

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Automatic backups: naming and which old automatic copies to remove. Manual backups are never touched. */
object AutoBackupPolicy {
    const val PREFIX = "NexusNotes-auto-"
    const val DEFAULT_KEEP = 5
    val KEEP_OPTIONS = listOf(3, 5, 10)
    val INTERVAL_DAYS_OPTIONS = listOf(1, 7)
    private val NAME = Regex("""^NexusNotes-auto-(\d{8}-\d{4})(?: \(\d+\))?\.zip$""")
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    fun fileName(now: Long, zone: ZoneId): String = PREFIX + STAMP.format(Instant.ofEpochMilli(now).atZone(zone)) + ".zip"

    fun isAutoBackup(name: String): Boolean = NAME.matches(name)

    /** Names of automatic backups to delete so that only the newest [keep] remain. */
    fun toDelete(names: List<String>, keep: Int): List<String> =
        names.filter(::isAutoBackup)
            .sortedByDescending { NAME.find(it)!!.groupValues[1] + it }
            .drop(keep.coerceAtLeast(1))
}
