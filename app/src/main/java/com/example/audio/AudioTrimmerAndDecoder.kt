package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class TrimmedAudio(
    val pcmShorts: ShortArray,
    val sampleRate: Int,
    val channelCount: Int
) {
    val durationMs: Long
        get() = if (sampleRate > 0 && channelCount > 0) {
            (pcmShorts.size.toLong() * 1000) / (sampleRate * channelCount)
        } else {
            0
        }
}

object AudioTrimmerAndDecoder {
    private const val TAG = "AudioTrimmerAndDecoder"
    private const val SILENCE_THRESHOLD = 800 // Out of 32767 for 16-bit PCM

    private val okHttpClient = OkHttpClient.Builder().build()

    /**
     * Downloads (or loads from cache) the MP3 recitation for the specified verse,
     * decodes it to raw 16-bit PCM shorts, trims silence, and returns the TrimmedAudio.
     */
    fun fetchAndTrimAudio(
        context: Context,
        reciterId: String,
        surah: Int,
        ayah: Int,
        onProgress: (String) -> Unit
    ): TrimmedAudio {
        val mappedReciterId = if (reciterId == "AbdulRahman_Mossad") "Abdul_Rahman_Al3ossy_64kbps" else reciterId
        val fileName = String.format(Locale.US, "%s_%03d%03d.mp3", mappedReciterId, surah, ayah)
        val cacheFile = File(context.cacheDir, fileName)

        if (!cacheFile.exists()) {
            onProgress("جاري تحميل صوت الآية $ayah...")
            val url = String.format(Locale.US, "https://everyayah.com/data/%s/%03d%03d.mp3", mappedReciterId, surah, ayah)
            Log.d(TAG, "Downloading audio: $url")
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("فشل تحميل الصوت للآية $ayah: ${response.code}")
                }
                val body = response.body ?: throw Exception("ملف الصوت فارغ للآية $ayah")
                FileOutputStream(cacheFile).use { fos ->
                    body.byteStream().copyTo(fos)
                }
            }
        } else {
            onProgress("تم تحميل صوت الآية $ayah من الذاكرة المؤقتة.")
            Log.d(TAG, "Loaded audio from cache: ${cacheFile.absolutePath}")
        }

        onProgress("جاري معالجة صوت الآية $ayah...")
        return decodeAndTrim(cacheFile.absolutePath)
    }

    private fun decodeAndTrim(filePath: String): TrimmedAudio {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(filePath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) {
                throw Exception("لا يوجد مسار صوتي في الملف")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            Log.d(TAG, "Decoding audio: mime=$mime, sampleRate=$sampleRate, channels=$channelCount")

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmDataBuilder = ArrayList<Short>()
            val info = MediaCodec.BufferInfo()
            var isExtractorDone = false
            var isDecoderDone = false

            while (!isDecoderDone) {
                if (!isExtractorDone) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: continue
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isExtractorDone = true
                        } else {
                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex) ?: continue
                    outputBuffer.order(ByteOrder.nativeOrder())
                    val shortBuffer = outputBuffer.asShortBuffer()
                    val count = shortBuffer.remaining()
                    for (i in 0 until count) {
                        pcmDataBuilder.add(shortBuffer.get())
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)

                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isDecoderDone = true
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Output format changed
                }
            }

            val rawShorts = ShortArray(pcmDataBuilder.size)
            for (i in pcmDataBuilder.indices) {
                rawShorts[i] = pcmDataBuilder[i]
            }

            // Perform silence trimming
            var startIndex = 0
            while (startIndex < rawShorts.size && Math.abs(rawShorts[startIndex].toInt()) < SILENCE_THRESHOLD) {
                startIndex++
            }

            var endIndex = rawShorts.size - 1
            while (endIndex > startIndex && Math.abs(rawShorts[endIndex].toInt()) < SILENCE_THRESHOLD) {
                endIndex--
            }

            val trimmedShorts = if (startIndex <= endIndex) {
                rawShorts.copyOfRange(startIndex, endIndex + 1)
            } else {
                rawShorts // fallback if somehow silence was absolute
            }

            Log.d(TAG, "Audio trimmed from ${rawShorts.size} to ${trimmedShorts.size} samples.")
            return TrimmedAudio(trimmedShorts, sampleRate, channelCount)

        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing codec", e)
            }
            extractor.release()
        }
    }
}
