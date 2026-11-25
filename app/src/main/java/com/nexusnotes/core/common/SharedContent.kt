package com.nexusnotes.core.common

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** Content another app shared with us (ACTION_SEND / ACTION_SEND_MULTIPLE). Inputs are untrusted. */
data class SharedContent(val subject: String?, val text: String?, val uris: List<Uri>) {
    companion object {
        private const val MAX_TEXT = 200_000

        fun from(intent: Intent?): SharedContent? {
            intent ?: return null
            val uris = when (intent.action) {
                Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
                Intent.ACTION_SEND_MULTIPLE ->
                    IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                else -> return null
            }.filter { it.scheme == "content" } // never read file:// paths handed to us
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(MAX_TEXT)
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.take(200)
            if (uris.isEmpty() && text.isNullOrBlank()) return null
            return SharedContent(subject, text, uris)
        }
    }
}
