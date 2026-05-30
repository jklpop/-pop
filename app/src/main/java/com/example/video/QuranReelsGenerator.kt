package com.example.video

import android.content.Context
import android.graphics.*
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VerseClipData(
    val arabicText: String,
    val translationText: String?,
    val durationUs: Long
)

class SavedBuffer(
    val data: ByteArray,
    val size: Int,
    val offset: Int,
    val presentationTimeUs: Long,
    val flags: Int
)

object QuranReelsGenerator {
    private const val TAG = "QuranReelsGenerator"
    private const val WIDTH = 1080
    private const val HEIGHT = 1920
    private const val VIDEO_MIME = "video/avc"
    private const val AUDIO_MIME = "audio/mp4a-latm"

    private val okHttpClient = OkHttpClient.Builder().build()

    fun compileReel(
        context: Context,
        verses: List<VerseClipData>,
        totalPcm: ShortArray,
        sampleRate: Int,
        channelCount: Int,
        backgroundType: String, // "Solid Black" or "Custom Image"
        customImageUriString: String?,
        showTranslation: Boolean,
        outputFile: File,
        onProgress: (Float, String) -> Unit
    ) {
        onProgress(0.01f, "جاري تهيئة محرك ترميز الفيديو...")
        Log.d(TAG, "compileReel started: versesSize=${verses.size}, pcmSize=${totalPcm.size}")

        // 1. Prepare properties: Font & Background Image
        val fontsDir = File(context.filesDir, "fonts")
        if (!fontsDir.exists()) fontsDir.mkdirs()
        val amiriFontFile = File(fontsDir, "Amiri-Regular.ttf")
        var typefaceAmiri: Typeface = Typeface.DEFAULT

        if (!amiriFontFile.exists()) {
            onProgress(0.05f, "جاري تحميل خط أميري العربي الجميل...")
            try {
                val request = Request.Builder()
                    .url("https://raw.githubusercontent.com/google/fonts/main/ofl/amiri/Amiri-Regular.ttf")
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(amiriFontFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        typefaceAmiri = Typeface.createFromFile(amiriFontFile)
                        Log.d(TAG, "Amiri Font downloaded successfully!")
                    } else {
                        Log.e(TAG, "Amiri Font download returned code: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not download Amiri Font, falling back to system serif", e)
                typefaceAmiri = Typeface.create("serif", Typeface.BOLD)
            }
        } else {
            try {
                typefaceAmiri = Typeface.createFromFile(amiriFontFile)
            } catch (e: Exception) {
                typefaceAmiri = Typeface.create("serif", Typeface.BOLD)
            }
        }

        var backgroundBitmap: Bitmap? = null
        if (backgroundType == "Custom Image" && !customImageUriString.isNullOrEmpty()) {
            onProgress(0.10f, "جاري تحميل وتعديل صورة الخلفية...")
            backgroundBitmap = loadScaledBitmap(context, customImageUriString, WIDTH, HEIGHT)
        }

        // 2. Transcode and encode AUDIO first
        onProgress(0.15f, "جاري تحويل وترميز الصوت...")
        val audioCodecInfos = collectCodecBuffers(AUDIO_MIME, totalPcm, sampleRate, channelCount)
        val audioBuffers = audioCodecInfos.first
        val audioFormat = audioCodecInfos.second

        // 3. Render and encode VIDEO frames next
        onProgress(0.40f, "جاري إنشاء إطارات الفيديو ورسم الآيات...")
        val totalDurationUs = ((totalPcm.size.toDouble() / (sampleRate * channelCount)) * 1_000_000L).toLong()
        val videoCodecFormat = chooseVideoColorFormat()
        val videoCodecInfos = encodeVideoFrames(
            verses,
            totalDurationUs,
            videoCodecFormat,
            backgroundBitmap,
            showTranslation,
            typefaceAmiri,
            onProgress
        )
        val videoBuffers = videoCodecInfos.first
        val videoFormat = videoCodecInfos.second

        // 4. Multiplex them via MediaMuxer
        onProgress(0.90f, "جاري دمج الصوت والفيديو وحفظ الملف النهائي...")
        var muxer: MediaMuxer? = null
        try {
            if (outputFile.exists()) outputFile.delete()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val videoTrackIndex = muxer.addTrack(videoFormat)
            val audioTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            // Sort and write video buffers
            Log.d(TAG, "Muxing video buffers: ${videoBuffers.size}")
            for (buf in videoBuffers) {
                val bufferInfo = MediaCodec.BufferInfo().apply {
                    set(buf.offset, buf.size, buf.presentationTimeUs, buf.flags)
                }
                val byteBuffer = ByteBuffer.wrap(buf.data)
                muxer.writeSampleData(videoTrackIndex, byteBuffer, bufferInfo)
            }

            // Write audio buffers
            Log.d(TAG, "Muxing audio buffers: ${audioBuffers.size}")
            for (buf in audioBuffers) {
                val bufferInfo = MediaCodec.BufferInfo().apply {
                    set(buf.offset, buf.size, buf.presentationTimeUs, buf.flags)
                }
                val byteBuffer = ByteBuffer.wrap(buf.data)
                muxer.writeSampleData(audioTrackIndex, byteBuffer, bufferInfo)
            }

            muxer.stop()
            Log.d(TAG, "Muxing completed, saved file of size ${outputFile.length()} bytes.")
            onProgress(1.0f, "تم بنجاح!")
        } catch (e: Exception) {
            Log.e(TAG, "Muxing crashed", e)
            throw Exception("فشل دمج الصوت والفيديو: ${e.message}")
        } finally {
            muxer?.release()
            backgroundBitmap?.recycle()
        }
    }

    private fun loadScaledBitmap(context: Context, uriString: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            val uri = android.net.Uri.parse(uriString)
            
            // Step 1: Decode dimensions only
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri).use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            
            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return null
            
            // Step 2: Calculate inSampleSize
            var inSampleSize = 1
            if (srcWidth > targetWidth || srcHeight > targetHeight) {
                val halfWidth = srcWidth / 2
                val halfHeight = srcHeight / 2
                while ((halfWidth / inSampleSize) >= targetWidth && (halfHeight / inSampleSize) >= targetHeight) {
                    inSampleSize *= 2
                }
            }
            
            // Step 3: Decode with inSampleSize
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = inSampleSize
            }
            val original = context.contentResolver.openInputStream(uri).use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            } ?: return null
            
            // Step 4: Precise scaling & cropping
            val scale = Math.max(targetWidth.toFloat() / original.width, targetHeight.toFloat() / original.height)
            val scaledWidth = (original.width * scale).toInt()
            val scaledHeight = (original.height * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(original, scaledWidth, scaledHeight, true)
            val xOffset = (scaledWidth - targetWidth) / 2
            val yOffset = (scaledHeight - targetHeight) / 2
            val cropped = Bitmap.createBitmap(scaled, xOffset, yOffset, targetWidth, targetHeight)
            
            if (scaled != cropped && !scaled.isRecycled) {
                scaled.recycle()
            }
            if (original != scaled && !original.isRecycled) {
                original.recycle()
            }
            cropped
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping custom bitmap", e)
            null
        }
    }

    private fun chooseVideoColorFormat(): Int {
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (info in list.codecInfos) {
            if (!info.isEncoder) continue
            val types = info.supportedTypes
            for (type in types) {
                if (type.equals(VIDEO_MIME, ignoreCase = true)) {
                    val caps = info.getCapabilitiesForType(VIDEO_MIME)
                    for (format in caps.colorFormats) {
                        if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                            return format
                        }
                    }
                    for (format in caps.colorFormats) {
                        if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                            return format
                        }
                    }
                }
            }
        }
        return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
    }

    private fun collectCodecBuffers(
        mime: String,
        pcmShorts: ShortArray,
        sampleRate: Int,
        channelCount: Int
    ): Pair<List<SavedBuffer>, MediaFormat> {
        val pcmBytes = ByteArray(pcmShorts.size * 2)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.nativeOrder()).asShortBuffer().put(pcmShorts)

        val format = MediaFormat.createAudioFormat(mime, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }

        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val savedList = ArrayList<SavedBuffer>()
        var actualOutputFormat = format

        val info = MediaCodec.BufferInfo()
        var bytesWritten = 0
        var isInputDone = false
        var isOutputDone = false

        while (!isOutputDone) {
            if (!isInputDone) {
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: continue
                    inputBuffer.clear()
                    val remainingBytes = pcmBytes.size - bytesWritten
                    if (remainingBytes <= 0) {
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        isInputDone = true
                    } else {
                        val limit = Math.min(inputBuffer.remaining(), remainingBytes)
                        inputBuffer.put(pcmBytes, bytesWritten, limit)
                        bytesWritten += limit

                        // presentation time based on samples
                        val ptsUs = (bytesWritten / 2L * 1_000_000L) / (sampleRate * channelCount)
                        codec.queueInputBuffer(inputBufferIndex, 0, limit, ptsUs, 0)
                    }
                }
            }

            val outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
            if (outputBufferIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex) ?: continue
                val outData = ByteArray(info.size)
                outputBuffer.position(info.offset)
                outputBuffer.get(outData)

                savedList.add(SavedBuffer(outData, info.size, 0, info.presentationTimeUs, info.flags))
                codec.releaseOutputBuffer(outputBufferIndex, false)

                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isOutputDone = true
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                actualOutputFormat = codec.outputFormat
                Log.d(TAG, "Audio output format verified: $actualOutputFormat")
            }
        }

        codec.stop()
        codec.release()
        return Pair(savedList, actualOutputFormat)
    }

    private fun encodeVideoFrames(
        verses: List<VerseClipData>,
        totalDurationUs: Long,
        colorFormat: Int,
        backgroundBitmap: Bitmap?,
        showTranslation: Boolean,
        typefaceAmiri: Typeface,
        onProgress: (Float, String) -> Unit
    ): Pair<List<SavedBuffer>, MediaFormat> {
        val format = MediaFormat.createVideoFormat(VIDEO_MIME, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, 2000000) // 2 Mbps
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second
        }

        val codec = MediaCodec.createEncoderByType(VIDEO_MIME)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val savedList = ArrayList<SavedBuffer>()
        var actualOutputFormat = format

        val info = MediaCodec.BufferInfo()
        val frameDurationUs = 33333L // 30 FPS
        val totalFrames = (totalDurationUs / frameDurationUs).toInt()
        var currentFrame = 0
        var isInputDone = false
        var isOutputDone = false

        val frameBitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frameBitmap)

        val textPaintArabic = TextPaint().apply {
            color = Color.WHITE
            isAntiAlias = true
            typeface = typefaceAmiri
        }

        val textPaintTranslation = TextPaint().apply {
            color = Color.parseColor("#E0E0E0") // Elegant soft grey
            isAntiAlias = true
            typeface = Typeface.create("serif", Typeface.ITALIC)
        }

        val overlayPaint = Paint().apply {
            color = Color.parseColor("#90000000") // 56% opacity black mask
        }

        val yuvSize = WIDTH * HEIGHT * 3 / 2
        val yuvBuffer = ByteArray(yuvSize)
        val pixelBuffer = IntArray(WIDTH * HEIGHT)

        // Maps timestamps to specific verse indexes
        val verseIntervals = ArrayList<Pair<LongRange, VerseClipData>>()
        var accumulatedUs = 0L
        for (v in verses) {
            val endUs = accumulatedUs + v.durationUs
            verseIntervals.add(Pair(accumulatedUs until endUs, v))
            accumulatedUs = endUs
        }

        while (!isOutputDone) {
            // 1. Feed frame as YUV to Video Encoder
            if (!isInputDone) {
                if (currentFrame < totalFrames) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: continue
                        inputBuffer.clear()

                        val currentPtsUs = currentFrame * frameDurationUs

                        // Find matching verse
                        val matchingVerse = verseIntervals.firstOrNull { currentPtsUs in it.first }?.second
                            ?: verses.last()

                        // Render Bitmap
                        if (backgroundBitmap != null) {
                            canvas.drawBitmap(backgroundBitmap, 0f, 0f, null)
                            canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), overlayPaint)
                        } else {
                            canvas.drawColor(Color.BLACK)
                        }

                        // Write verse number & decorative header
                        val headerPaint = Paint().apply {
                            color = Color.parseColor("#D4AF37") // Premium Metallic Gold
                            textSize = 36f
                            isAntiAlias = true
                            typeface = typefaceAmiri
                            textAlign = Paint.Align.CENTER
                        }
                        canvas.drawText("✵ سُورَةُ وقُرآنٌ كَرِيمٌ ✵", WIDTH / 2f, 180f, headerPaint)

                        // Draw Arabic wrapped text with dynamic size
                        var arabicTextSize = 72f
                        var staticLayoutArabic = createWrappedLayout(matchingVerse.arabicText, textPaintArabic, WIDTH - 160, arabicTextSize)
                        while (staticLayoutArabic.height > HEIGHT / 2 && arabicTextSize > 32f) {
                            arabicTextSize -= 4f
                            staticLayoutArabic = createWrappedLayout(matchingVerse.arabicText, textPaintArabic, WIDTH - 160, arabicTextSize)
                        }

                        // Draw translation wrapped text, if toggled
                        val hasTranslation = showTranslation && !matchingVerse.translationText.isNullOrEmpty()
                        var staticLayoutTrans: StaticLayout? = null
                        if (hasTranslation) {
                            var transTextSize = 38f
                            var layoutTrans = createWrappedLayout(matchingVerse.translationText!!, textPaintTranslation, WIDTH - 200, transTextSize)
                            while (layoutTrans.height > HEIGHT / 3 && transTextSize > 24f) {
                                transTextSize -= 2f
                                layoutTrans = createWrappedLayout(matchingVerse.translationText, textPaintTranslation, WIDTH - 200, transTextSize)
                            }
                            staticLayoutTrans = layoutTrans
                        }

                        // Calculate total combined height of the block
                        val totalBlockHeight = staticLayoutArabic.height + if (hasTranslation && staticLayoutTrans != null) {
                            40f + 3f + 30f + staticLayoutTrans.height
                        } else {
                            0f
                        }

                        // Center the entire block vertically in the middle of the frame
                        val blockStartY = (HEIGHT - totalBlockHeight) / 2f

                        // Draw Arabic text
                        canvas.save()
                        canvas.translate(80f, blockStartY)
                        staticLayoutArabic.draw(canvas)
                        canvas.restore()

                        // Draw translation wrapped text below with a divider, if toggled
                        if (hasTranslation && staticLayoutTrans != null) {
                            // Divider highlight (thin gold line separator)
                            val linePaint = Paint().apply {
                                color = Color.parseColor("#50D4AF37") // soft semi-transparent gold
                                strokeWidth = 3f
                            }
                            val dividerY = blockStartY + staticLayoutArabic.height + 40f
                            canvas.drawLine(WIDTH / 4f, dividerY, WIDTH * 3f / 4f, dividerY, linePaint)

                            canvas.save()
                            canvas.translate(100f, dividerY + 30f)
                            staticLayoutTrans.draw(canvas)
                            canvas.restore()
                        }

                        // Convert Bitmap ARGB to YUV420 Planar or Semi-Planar
                        if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                            convertBitmapToYUV420SemiPlanar(frameBitmap, yuvBuffer, WIDTH, HEIGHT, pixelBuffer)
                        } else {
                            convertBitmapToYUV420Planar(frameBitmap, yuvBuffer, WIDTH, HEIGHT, pixelBuffer)
                        }

                        inputBuffer.put(yuvBuffer)
                        codec.queueInputBuffer(inputBufferIndex, 0, yuvSize, currentPtsUs, 0)

                        if (currentFrame % 15 == 0) {
                            val progressRatio = 0.40f + (currentFrame.toFloat() / totalFrames) * 0.45f
                            onProgress(progressRatio, "جاري إعداد إطار الفيديو: $currentFrame / $totalFrames")
                        }
                        currentFrame++
                    }
                } else {
                    // Done inputting frames. Flush encoder
                    val inputBufferIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        isInputDone = true
                    }
                }
            }

            // 2. Read output encoded chunks
            val outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
            if (outputBufferIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex) ?: continue
                val outData = ByteArray(info.size)
                outputBuffer.position(info.offset)
                outputBuffer.get(outData)

                savedList.add(SavedBuffer(outData, info.size, 0, info.presentationTimeUs, info.flags))
                codec.releaseOutputBuffer(outputBufferIndex, false)

                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isOutputDone = true
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                actualOutputFormat = codec.outputFormat
                Log.d(TAG, "Video output format verified: $actualOutputFormat")
            }
        }

        codec.stop()
        codec.release()
        frameBitmap.recycle()

        return Pair(savedList, actualOutputFormat)
    }

    private fun createWrappedLayout(text: String, paint: TextPaint, width: Int, size: Float): StaticLayout {
        paint.textSize = size
        val alignment = Layout.Alignment.ALIGN_CENTER
        val spacingMultiplier = 1.15f
        val spacingAddition = 0.0f
        val includePadding = false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(alignment)
                .setLineSpacing(spacingAddition, spacingMultiplier)
                .setIncludePad(includePadding)
                .build()
        } else {
            StaticLayout(
                text, paint, width,
                alignment, spacingMultiplier, spacingAddition, includePadding
            )
        }
    }

    private fun convertBitmapToYUV420SemiPlanar(bitmap: Bitmap, yuv: ByteArray, width: Int, height: Int, pixels: IntArray) {
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val numPixels = width * height
        var yIndex = 0
        var uvIndex = numPixels

        for (y in 0 until height) {
            val isEvenRow = (y % 2 == 0)
            var pixelOffset = y * width
            for (x in 0 until width) {
                val color = pixels[pixelOffset++]
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff

                // Y component
                val yValue = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = (if (yValue < 0) 0 else if (yValue > 255) 255 else yValue).toByte()

                if (isEvenRow && (x % 2 == 0)) {
                    val uValue = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val vValue = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                    yuv[uvIndex++] = (if (uValue < 0) 0 else if (uValue > 255) 255 else uValue).toByte() // U
                    yuv[uvIndex++] = (if (vValue < 0) 0 else if (vValue > 255) 255 else vValue).toByte() // V
                }
            }
        }
    }

    private fun convertBitmapToYUV420Planar(bitmap: Bitmap, yuv: ByteArray, width: Int, height: Int, pixels: IntArray) {
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val numPixels = width * height
        var yIndex = 0
        var uIndex = numPixels
        var vIndex = numPixels + (numPixels / 4)

        for (y in 0 until height) {
            val isEvenRow = (y % 2 == 0)
            var pixelOffset = y * width
            for (x in 0 until width) {
                val color = pixels[pixelOffset++]
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff

                val yValue = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = (if (yValue < 0) 0 else if (yValue > 255) 255 else yValue).toByte()

                if (isEvenRow && (x % 2 == 0)) {
                    val uValue = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val vValue = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                    yuv[uIndex++] = (if (uValue < 0) 0 else if (uValue > 255) 255 else uValue).toByte()
                    yuv[vIndex++] = (if (vValue < 0) 0 else if (vValue > 255) 255 else vValue).toByte()
                }
            }
        }
    }
}
