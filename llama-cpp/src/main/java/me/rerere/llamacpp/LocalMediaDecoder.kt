package me.rerere.llamacpp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.provider.Modality
import me.rerere.ai.ui.UIMessagePart
import java.io.File
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.roundToInt

data class PreparedMedia(val messages: List<UIMessage>, val unsupported: Boolean, val truncated: Boolean)

/** Android decodes media; only raw RGBA/mono PCM crosses JNI. No ffmpeg. */
class LocalMediaDecoder(private val context: Context) {
    suspend fun prepare(messages: List<UIMessage>, runtime: LlamaCppRuntime, allowed: List<Modality> = Modality.entries): PreparedMedia {
        runtime.beginMedia()
        var unsupported = false
        var truncated = false
        val converted = messages.map { message -> message.copy(parts = message.parts.mapNotNull { part ->
            currentCoroutineContext().ensureActive()
            if (part !is UIMessagePart.Image && part !is UIMessagePart.Audio && part !is UIMessagePart.Video) return@mapNotNull part
            try {
                when (part) {
                    is UIMessagePart.Image -> {
                        check(Modality.IMAGE in allowed && runtime.capabilities.vision)
                        image(part.url).let { original ->
                            val bitmap = resize(original, 1024)
                            runtime.image(rgba(bitmap), bitmap.width, bitmap.height)
                            if (bitmap !== original) original.recycle()
                            bitmap.recycle()
                        }
                    }
                    is UIMessagePart.Audio -> {
                        check(Modality.AUDIO in allowed && runtime.capabilities.audio && runtime.capabilities.sampleRate > 0)
                        val decoded = audio(part.url, runtime.capabilities.sampleRate)
                        runtime.audio(decoded.first); truncated = truncated || decoded.second
                    }
                    is UIMessagePart.Video -> {
                        check(Modality.VIDEO in allowed && runtime.capabilities.vision)
                        video(part.url, runtime)
                    }
                    else -> Unit
                }
                UIMessagePart.Text("<__media__>")
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { unsupported = true; null }
        }) }
        return PreparedMedia(converted, unsupported, truncated)
    }

    private fun image(url: String): Bitmap {
        if (url.startsWith("data:")) {
            val bytes = Base64.decode(url.substringAfter(','), Base64.DEFAULT)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            var sample = 1
            while (maxOf(options.outWidth, options.outHeight) / sample > 2048) sample *= 2
            options.inJustDecodeBounds = false; options.inSampleSize = sample
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
        }
        val uri = uri(url)
        // Read dimensions first so a large photo does not allocate its full bitmap.
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
        var sample = 1
        while (maxOf(options.outWidth, options.outHeight) / sample > 2048) sample *= 2
        options.inJustDecodeBounds = false; options.inSampleSize = sample
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        return context.contentResolver.openInputStream(uri).use { requireNotNull(BitmapFactory.decodeStream(it, null, options)) }
    }

    private suspend fun video(url: String, runtime: LlamaCppRuntime) {
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(context, uri(url))
            val duration = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val count = ceil(duration / 1000.0).toInt().coerceIn(1, 16)
            val frames = mutableListOf<ByteArray>()
            var width = 0; var height = 0
            repeat(count) { index ->
                currentCoroutineContext().ensureActive()
                val original = reader.getFrameAtTime(index * 1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST) ?: return@repeat
                val scaled = if (width == 0) resize(original, 768) else Bitmap.createScaledBitmap(original, width, height, true)
                width = scaled.width; height = scaled.height
                frames += rgba(scaled)
                if (scaled !== original) original.recycle()
                scaled.recycle()
            }
            check(frames.isNotEmpty())
            runtime.video(frames.toTypedArray(), width, height)
        } finally { reader.release() }
    }

    private suspend fun audio(url: String, sampleRate: Int): Pair<FloatArray, Boolean> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri(url), null)
            val track = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val decoder = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0); decoder.start()
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var inputDone = false; var outputDone = false
            val info = MediaCodec.BufferInfo()
            val samples = ArrayList<Float>()
            var clipped = (format.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }?.getLong(MediaFormat.KEY_DURATION) ?: 0) > 60_000_000
            while (!outputDone) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = requireNotNull(decoder.getInputBuffer(index))
                        val size = extractor.readSampleData(buffer, 0)
                        val end = size < 0 || extractor.sampleTime >= 60_000_000
                        if (extractor.sampleTime >= 60_000_000) clipped = true
                        decoder.queueInputBuffer(index, 0, if (end) 0 else size, if (end) 0 else extractor.sampleTime,
                            if (end) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        if (end) inputDone = true else extractor.advance()
                    }
                }
                when (val index = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val output = decoder.outputFormat
                        channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        rate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }
                    in 0..Int.MAX_VALUE -> {
                        try {
                            val buffer = requireNotNull(decoder.getOutputBuffer(index)).order(ByteOrder.LITTLE_ENDIAN)
                            buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                            while (buffer.remaining() >= channels * bytes && samples.size < rate * 60) {
                                var mono = 0f
                                repeat(channels) { mono += if (bytes == 4) buffer.float else buffer.short / 32768f }
                                samples += (mono / channels).coerceIn(-1f, 1f)
                            }
                        } finally { decoder.releaseOutputBuffer(index, false) }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || samples.size >= rate * 60
                    }
                }
            }
            check(samples.isNotEmpty() && rate > 0)
            val target = (samples.size.toLong() * sampleRate / rate).toInt()
            return FloatArray(target) { index ->
                val position = index.toDouble() * rate / sampleRate
                val start = position.toInt().coerceAtMost(samples.lastIndex)
                val end = (start + 1).coerceAtMost(samples.lastIndex)
                (samples[start] + (samples[end] - samples[start]) * (position - start)).toFloat()
            } to clipped
        } finally {
            runCatching { codec?.stop() }; codec?.release(); extractor.release()
        }
    }

    private fun uri(url: String) = if (url.startsWith('/')) Uri.fromFile(File(url)) else Uri.parse(url)
    private fun resize(bitmap: Bitmap, edge: Int): Bitmap {
        val scale = (edge.toDouble() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1.0)
        return if (scale == 1.0) bitmap else Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1), (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
    }
    private fun rgba(bitmap: Bitmap): ByteArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return ByteArray(pixels.size * 4).apply { pixels.forEachIndexed { i, p ->
            this[i * 4] = (p shr 16).toByte(); this[i * 4 + 1] = (p shr 8).toByte()
            this[i * 4 + 2] = p.toByte(); this[i * 4 + 3] = (p ushr 24).toByte()
        } }
    }
}
