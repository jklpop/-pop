package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioTrimmerAndDecoder
import com.example.audio.TrimmedAudio
import com.example.data.QuranData
import com.example.data.Reciter
import com.example.data.Surah
import com.example.video.QuranReelsGenerator
import com.example.video.VerseClipData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

sealed class GenerationState {
    object Idle : GenerationState()
    data class Generating(val progress: Float, val statusMessage: String) : GenerationState()
    data class Success(val savedUri: String, val filePath: String) : GenerationState()
    data class Error(val errorMessage: String) : GenerationState()
}

class QuranReelsViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "QuranReelsViewModel"
    private val okHttpClient = OkHttpClient.Builder().build()

    // Configuration states
    private val _selectedSurah = MutableStateFlow(QuranData.SURAHS[0]) // Defaults to Al-Fatihah
    val selectedSurah: StateFlow<Surah> = _selectedSurah.asStateFlow()

    private val _startAyah = MutableStateFlow(1)
    val startAyah: StateFlow<Int> = _startAyah.asStateFlow()

    private val _endAyah = MutableStateFlow(1)
    val endAyah: StateFlow<Int> = _endAyah.asStateFlow()

    private val _selectedReciter = MutableStateFlow(QuranData.RECITERS[4]) // Defaults to Alafasy
    val selectedReciter: StateFlow<Reciter> = _selectedReciter.asStateFlow()

    private val _backgroundType = MutableStateFlow("Solid Black") // "Solid Black" or "Custom Image"
    val backgroundType: StateFlow<String> = _backgroundType.asStateFlow()

    private val _customImageUri = MutableStateFlow<Uri?>(null)
    val customImageUri: StateFlow<Uri?> = _customImageUri.asStateFlow()

    private val _showTranslation = MutableStateFlow(true)
    val showTranslation: StateFlow<Boolean> = _showTranslation.asStateFlow()

    // Operational state
    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    fun setSurah(surah: Surah) {
        _selectedSurah.value = surah
        // Adjust ayah numbers to fit the new Surah's bounds
        if (_startAyah.value > surah.ayahCount) {
            _startAyah.value = 1
        }
        val defaultEnd = _startAyah.value + 4
        _endAyah.value = Math.min(defaultEnd, surah.ayahCount)
    }

    fun setStartAyah(ayah: Int) {
        val maxAyah = _selectedSurah.value.ayahCount
        val parsed = Math.max(1, Math.min(ayah, maxAyah))
        _startAyah.value = parsed
        // Auto-adjust endAyah if it becomes less than startAyah
        if (_endAyah.value < parsed) {
            _endAyah.value = parsed
        } else if (_endAyah.value > parsed + 9) {
            // Suggest default of start+9 max to preserve responsive experience
            _endAyah.value = parsed + 9
        }
    }

    fun setEndAyah(ayah: Int) {
        val maxAyah = _selectedSurah.value.ayahCount
        _endAyah.value = Math.max(_startAyah.value, Math.min(ayah, maxAyah))
    }

    fun setReciter(reciter: Reciter) {
        _selectedReciter.value = reciter
    }

    fun setBackgroundType(type: String) {
        _backgroundType.value = type
    }

    fun setCustomImageUri(context: Context, uri: Uri?) {
        if (uri == null) {
            _customImageUri.value = null
            return
        }
        try {
            val cacheFile = File(context.cacheDir, "custom_bg_cache.jpg")
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            context.contentResolver.openInputStream(uri).use { input ->
                if (input != null) {
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            _customImageUri.value = Uri.fromFile(cacheFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error caching custom image", e)
            _customImageUri.value = uri // fallback
        }
    }

    fun setShowTranslation(show: Boolean) {
        _showTranslation.value = show
    }

    fun resetStateToIdle() {
        _generationState.value = GenerationState.Idle
    }

    fun generateReel(context: Context) {
        val surah = _selectedSurah.value
        val start = _startAyah.value
        val end = _endAyah.value
        val reciter = _selectedReciter.value
        val bgType = _backgroundType.value
        val sampleUri = _customImageUri.value?.toString()
        val drawTrans = _showTranslation.value

        // Valication Check
        if (start > end) {
            _generationState.value = GenerationState.Error("يجب أن يكون رقم آية البداية أصغر من أو يساوي آية النهاية.")
            return
        }

        if (end - start >= 10) {
            _generationState.value = GenerationState.Error("الحد الأقصى لإنشاء مقطع واحد هو 10 آيات لتجنب البطء.")
            return
        }

        _generationState.value = GenerationState.Generating(0.0f, "جاري تحضير موارد الآيات...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val clipDataList = ArrayList<VerseClipData>()
                val rawPcmList = ArrayList<ShortArray>()
                var firstSampleRate = 44100
                var firstChannelCount = 1

                for (ayah in start..end) {
                    val progressRatio = (ayah - start).toFloat() / (end - start + 1) * 0.35f
                    _generationState.value = GenerationState.Generating(progressRatio, "جاري جلب نص الآية $ayah من السورة...")

                    // 1. Fetch Arabic text & translation
                    val arabicText = fetchAyahTextUthmani(surah.number, ayah)
                    val englishText = if (drawTrans) fetchEnglishTranslation(surah.number, ayah) else null

                    // 2. Fetch Audio and decode with progress details
                    val audio = AudioTrimmerAndDecoder.fetchAndTrimAudio(
                        context,
                        reciter.id,
                        surah.number,
                        ayah
                    ) { message ->
                        _generationState.value = GenerationState.Generating(progressRatio + 0.05f, message)
                    }

                    if (ayah == start) {
                        firstSampleRate = audio.sampleRate
                        firstChannelCount = audio.channelCount
                    }

                    // Store clip elements
                    clipDataList.add(VerseClipData(
                        arabicText = arabicText,
                        translationText = englishText,
                        durationUs = audio.durationMs * 1000L
                    ))
                    rawPcmList.add(audio.pcmShorts)
                }

                // Concatenate Audios
                _generationState.value = GenerationState.Generating(0.38f, "جاري دمج المقاطع الصوتية المحددة وبدء التجميع...")
                val totalPcm = concatenateAudioShorts(rawPcmList)

                // Render & Mux Video
                val tempFile = File(context.cacheDir, "temp_quran_reel.mp4")
                QuranReelsGenerator.compileReel(
                    context = context,
                    verses = clipDataList,
                    totalPcm = totalPcm,
                    sampleRate = firstSampleRate,
                    channelCount = firstChannelCount,
                    backgroundType = bgType,
                    customImageUriString = sampleUri,
                    showTranslation = drawTrans,
                    outputFile = tempFile,
                    onProgress = { progress, message ->
                        _generationState.value = GenerationState.Generating(progress, message)
                    }
                )

                // Save to gallery
                _generationState.value = GenerationState.Generating(0.95f, "جاري تسجيل مقطع الفيديو في معرض الصور الخاص بك...")
                val galleryTitle = String.format(Locale.US, "QuranReel_Surah%d_%d-%d", surah.number, start, end)
                val savedUriString = saveVideoToGallery(context, tempFile, galleryTitle)

                if (savedUriString != null) {
                    _generationState.value = GenerationState.Success(savedUriString, tempFile.absolutePath)
                } else {
                    _generationState.value = GenerationState.Error("فشل حفظ مقطع الفيديو في معرض الصور المعرّف بالهاتف.")
                }

            } catch (e: Throwable) {
                Log.e(TAG, "Reel compilation error", e)
                val displayMsg = e.message ?: "حدث خطأ غير متوقع أثناء معالجة وإعداد مقطع الفيديو"
                _generationState.value = GenerationState.Error(displayMsg)
            }
        }
    }

    private fun fetchAyahTextUthmani(surahNum: Int, ayahNum: Int): String {
        val url = String.format(Locale.US, "https://api.alquran.cloud/v1/ayah/%d:%d/quran-uthmani", surahNum, ayahNum)
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("فشل الاتصال بخادم الآيات للآية $ayahNum ($surahNum): ${response.code}")
            }
            val body = response.body?.string() ?: throw Exception("استجابة نص السورة فارغة")
            val json = JSONObject(body)
            val dataObj = json.getJSONObject("data")
            return dataObj.getString("text")
        }
    }

    private fun fetchEnglishTranslation(surahNum: Int, ayahNum: Int): String {
        val url = String.format(Locale.US, "https://api.alquran.cloud/v1/ayah/%d:%d/en.sahih", surahNum, ayahNum)
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ""
            val body = response.body?.string() ?: return ""
            val json = JSONObject(body)
            val dataObj = json.getJSONObject("data")
            return dataObj.getString("text")
        }
    }

    private fun concatenateAudioShorts(list: List<ShortArray>): ShortArray {
        var totalSize = 0
        for (arr in list) {
            totalSize += arr.size
        }
        val target = ShortArray(totalSize)
        var offset = 0
        for (arr in list) {
            System.arraycopy(arr, 0, target, offset, arr.size)
            offset += arr.size
        }
        return target
    }

    private fun saveVideoToGallery(context: Context, sourceFile: File, title: String): String? {
        val resolver = context.contentResolver
        val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val finalFileName = "$title.mp4"
        val newVideoDetails = android.content.ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, finalFileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/QuranReels")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        return try {
            val myVideoUri = resolver.insert(videoCollection, newVideoDetails) ?: return null
            resolver.openOutputStream(myVideoUri).use { outputStream ->
                if (outputStream == null) return null
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                newVideoDetails.clear()
                newVideoDetails.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(myVideoUri, newVideoDetails, null, null)
            }
            myVideoUri.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to MediaStore", e)
            null
        }
    }
}
