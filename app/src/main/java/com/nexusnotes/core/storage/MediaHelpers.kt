package com.nexusnotes.core.storage

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.LruCache
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.nexusnotes.domain.attachment.AttachmentPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decodes images at a bounded size (never the full-resolution original), EXIF-rotated, with a small memory cache. */
object BitmapLoader {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    suspend fun load(file: File, maxEdge: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "${file.name}@$maxEdge"
        cache.get(key)?.let { return@withContext it }
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = AttachmentPolicy.inSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            }
            val decoded = BitmapFactory.decodeFile(file.path, options) ?: return@runCatching null
            rotate(decoded, file).asImageBitmap()
        }.getOrNull()?.also { cache.put(key, it) }
    }

    private fun rotate(bitmap: Bitmap, file: File): Bitmap {
        val degrees = runCatching {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

/** Records microphone audio to an AAC (.m4a) file. Call only after RECORD_AUDIO was granted. */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var startedAt = 0L

    fun start(output: File) {
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(96_000)
        r.setAudioSamplingRate(44_100)
        r.setMaxDuration(AttachmentPolicy.MAX_RECORDING_MS.toInt())
        r.setOutputFile(output.path)
        r.prepare()
        r.start()
        recorder = r
        startedAt = System.currentTimeMillis()
    }

    /** Stops and returns the duration in ms, or null if the recording was too short / failed. */
    fun stop(): Long? {
        val r = recorder ?: return null
        recorder = null
        val duration = System.currentTimeMillis() - startedAt
        return try {
            r.stop()
            duration
        } catch (e: RuntimeException) {
            null // stop() throws if nothing was recorded
        } finally {
            r.release()
        }
    }

    fun cancel() {
        stop()
    }
}

/** Plays one audio file at a time. */
class AudioPlayer {
    private var player: MediaPlayer? = null
    var playingFile: File? = null
        private set

    fun play(file: File, onDone: () -> Unit) {
        stop()
        val p = MediaPlayer()
        p.setDataSource(file.path)
        p.setOnCompletionListener {
            stop()
            onDone()
        }
        p.prepare()
        p.start()
        player = p
        playingFile = file
    }

    fun stop() {
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null
        playingFile = null
    }
}

/** Opens or shares attachment files through a FileProvider (only on the user's request). */
object AttachmentIntents {
    private fun uriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, context.packageName + ".files", file)

    fun open(context: Context, file: File, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(context, file), mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app on this phone can open this file.", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, file: File, mimeType: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(intent, title))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app on this phone can receive this file.", Toast.LENGTH_SHORT).show()
        }
    }
}
