package com.nexusnotes.domain.attachment

import java.util.Locale

enum class AttachmentKind { IMAGE, AUDIO, FILE }

/** Pure rules for attachments: limits, naming, sizing. Unit-tested on the JVM. */
object AttachmentPolicy {

    /** Largest single attachment accepted (images, files, shared items). */
    const val MAX_FILE_BYTES: Long = 50L * 1024 * 1024

    /** Longest audio recording. */
    const val MAX_RECORDING_MS: Long = 30L * 60 * 1000

    /** Most attachments one note can hold. */
    const val MAX_PER_NOTE = 50

    /** Largest edge for in-list thumbnails (pixels). */
    const val THUMBNAIL_EDGE = 360

    /** Largest edge for the full-screen image viewer (pixels). */
    const val VIEWER_EDGE = 2048

    fun kindFor(mimeType: String?): AttachmentKind = when {
        mimeType == null -> AttachmentKind.FILE
        mimeType.startsWith("image/", ignoreCase = true) -> AttachmentKind.IMAGE
        mimeType.startsWith("audio/", ignoreCase = true) -> AttachmentKind.AUDIO
        else -> AttachmentKind.FILE
    }

    /**
     * A display name that is safe to show and to use as a suggested file name:
     * no path parts, no control characters, sensible length, never empty.
     */
    fun safeDisplayName(raw: String?, fallback: String = "attachment"): String {
        val base = raw.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filter { !it.isISOControl() }
            .replace(Regex("[:*?\"<>|]"), "_")
            .trim()
            .trimStart('.')
        val cleaned = if (base.length > 120) {
            val ext = base.substringAfterLast('.', "").take(10)
            val stem = base.substringBeforeLast('.').take(120 - ext.length - 1)
            if (ext.isEmpty()) stem else "$stem.$ext"
        } else {
            base
        }
        return cleaned.ifEmpty { fallback }
    }

    /** Power-of-two sample size so the decoded image's longer edge is close to [targetEdge]. */
    fun inSampleSize(width: Int, height: Int, targetEdge: Int): Int {
        if (width <= 0 || height <= 0 || targetEdge <= 0) return 1
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / (sample * 2) >= targetEdge) sample *= 2
        return sample
    }

    /** "512 B", "1.5 KB", "12.3 MB" */
    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB")
        var value = bytes / 1024.0
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (value >= 100) String.format(Locale.US, "%.0f %s", value, units[unit])
        else String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    /** "0:07", "12:34", "1:02:03" */
    fun formatDuration(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    /** Title for a note created from shared content. */
    fun sharedNoteTitle(subject: String?, text: String?, firstFileName: String?): String {
        subject?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(120) }
        text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }?.let { return it.take(80) }
        return firstFileName?.take(80) ?: "Shared note"
    }

    /** Body for a note created from shared content (never longer than [maxChars]). */
    fun sharedNoteBody(text: String?, maxChars: Int = 200_000): String = text?.take(maxChars).orEmpty()
}
