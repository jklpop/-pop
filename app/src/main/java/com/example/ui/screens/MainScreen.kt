package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.QuranData
import com.example.data.Reciter
import com.example.data.Surah
import com.example.ui.viewmodel.GenerationState
import com.example.ui.viewmodel.QuranReelsViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: QuranReelsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // ViewModel State variables
    val selectedSurah by viewModel.selectedSurah.collectAsState()
    val startAyah by viewModel.startAyah.collectAsState()
    val endAyah by viewModel.endAyah.collectAsState()
    val selectedReciter by viewModel.selectedReciter.collectAsState()
    val backgroundType by viewModel.backgroundType.collectAsState()
    val customImageUri by viewModel.customImageUri.collectAsState()
    val showTranslation by viewModel.showTranslation.collectAsState()
    val generationState by viewModel.generationState.collectAsState()

    // UI state
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSurahDropdown by remember { mutableStateOf(false) }
    var showReciterDropdown by remember { mutableStateOf(false) }

    // Intermediary text fields to support free text entry before setting
    var startInputText by remember(selectedSurah) { mutableStateOf(startAyah.toString()) }
    var endInputText by remember(selectedSurah) { mutableStateOf(endAyah.toString()) }

    // Image Picker launcher
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.setCustomImageUri(context, uri)
            Toast.makeText(context, "تم تحديد صورة الخلفية بنجاح!", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✵ صانع ريلز القرآن الكريم",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Decorative Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "صمّم مقاطع فيديو قصيرة (ريلز) مميزة لآيات القرآن الكريم بلمسة واحدة لمشاركتها على وسائل التواصل الاجتماعي.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Surah Selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "السورة الكريمة",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { showSurahDropdown = true }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedSurah.number}. سورة ${selectedSurah.nameArabic} (${selectedSurah.nameEnglish})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "فتح")
                        }

                        DropdownMenu(
                            expanded = showSurahDropdown,
                            onDismissRequest = { showSurahDropdown = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .heightIn(max = 300.dp)
                        ) {
                            QuranData.SURAHS.forEach { surah ->
                                DropdownMenuItem(
                                    text = {
                                        Text(text = "${surah.number}. سورة ${surah.nameArabic} (${surah.nameEnglish}) - ${surah.ayahCount} آية")
                                    },
                                    onClick = {
                                        viewModel.setSurah(surah)
                                        showSurahDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Ayah range inputs (Row layout)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "من الآية الـ",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = startInputText,
                            onValueChange = {
                                startInputText = it
                                it.toIntOrNull()?.let { num ->
                                    viewModel.setStartAyah(num)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("start_ayah_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "إلى الآية الـ",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = endInputText,
                            onValueChange = {
                                endInputText = it
                                it.toIntOrNull()?.let { num ->
                                    viewModel.setEndAyah(num)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("end_ayah_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }

                // Reciter Selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "القارئ الشيخ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { showReciterDropdown = true }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedReciter.displayNameArabic,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "فتح القراء")
                        }

                        DropdownMenu(
                            expanded = showReciterDropdown,
                            onDismissRequest = { showReciterDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            QuranData.RECITERS.forEach { reciter ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = reciter.displayNameArabic,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = reciter.displayNameEnglish,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.setReciter(reciter)
                                        showReciterDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Generate trigger button
                Button(
                    onClick = { viewModel.generateReel(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("generate_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "جلب البيانات وإنشاء الفيديو ✵",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Status updates and generation visuals
                AnimatedVisibility(
                    visible = generationState is GenerationState.Generating,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val state = generationState as? GenerationState.Generating
                    val progressVal = state?.progress ?: 0.0f
                    val statusMsg = state?.statusMessage ?: ""

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progressVal },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            text = "$statusMsg (${(progressVal * 100).toInt()}%)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Error message
                AnimatedVisibility(
                    visible = generationState is GenerationState.Error,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val state = generationState as? GenerationState.Error
                    val errorMsg = state?.errorMessage ?: ""

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "تنبيه خطأ:",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = errorMsg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Success Player card!
                AnimatedVisibility(
                    visible = generationState is GenerationState.Success,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val state = generationState as? GenerationState.Success
                    if (state != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("video_success_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "تم جلب البيانات وتصدير الريلز بنجاح! 🎉",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "تم حفظ الفيديو النهائي في المعرض الخاص بك. يمكنك تشغيله هنا أو مشاركته مع الأهل والأصدقاء.",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp
                                )

                                // Video player container
                                VideoPlayerView(
                                    videoPath = state.filePath,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(9f / 16f)
                                        .clip(RoundedCornerShape(8.dp))
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            try {
                                                val uri = Uri.parse(state.savedUri)
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "video/mp4"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "مشاركة ريلز القرآن"))
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "فشل بدء المشاركة: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "مشاركة")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("مشاركة الفيديو")
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.resetStateToIdle() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("إنشاء مقطع جديد")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom sheet for configurations
            if (showSettingsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSettingsSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = "✵ إعدادات مظهر الريلز",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Divider()

                        // Video Background selection
                        Column {
                            Text(
                                text = "نوع خلفية الفيديو",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                FilterChip(
                                    selected = backgroundType == "Solid Black",
                                    onClick = { viewModel.setBackgroundType("Solid Black") },
                                    label = { Text("خلفية سوداء") }
                                )

                                FilterChip(
                                    selected = backgroundType == "Custom Image",
                                    onClick = { viewModel.setBackgroundType("Custom Image") },
                                    label = { Text("صورة مخصصة") }
                                )
                            }
                        }

                        // Custom image picker button if custom background image is selected
                        if (backgroundType == "Custom Image") {
                            Button(
                                onClick = { pickerLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = if (customImageUri != null) "تغيير صورة الخلفية" else "اختر صورة من المعرض"
                                )
                            }

                            if (customImageUri != null) {
                                Text(
                                    text = "تم اختيار الصورة بنجاح وتجهيزها.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // English Translation toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "عرض الترجمة الإنجليزية",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "عرض تفسير وترجمة معاني الآيات أسفل النص العربي",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = showTranslation,
                                onCheckedChange = { viewModel.setShowTranslation(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { showSettingsSheet = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("حفظ وإغلاق")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerView(videoPath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(videoPath) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(File(videoPath)))
            setMediaItem(mediaItem)
            prepare()
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { playerView ->
            if (playerView.player != exoPlayer) {
                playerView.player = exoPlayer
            }
        },
        modifier = modifier
    )
}
