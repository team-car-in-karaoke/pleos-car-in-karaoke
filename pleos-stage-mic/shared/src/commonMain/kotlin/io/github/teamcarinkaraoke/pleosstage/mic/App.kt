package io.github.teamcarinkaraoke.pleosstage.mic

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font
import pleos_stage_mic.shared.generated.resources.Res
import pleos_stage_mic.shared.generated.resources.pretendard_bold
import pleos_stage_mic.shared.generated.resources.pretendard_extrabold
import pleos_stage_mic.shared.generated.resources.pretendard_medium
import pleos_stage_mic.shared.generated.resources.pretendard_regular
import pleos_stage_mic.shared.generated.resources.pretendard_semibold
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.TimeSource

// ============================================================================
// Genuine Hyundai Pleos Connect Design System Palette (Mobile Companion)
// ============================================================================
private val PleosCanvasBg = Color(0xFFF2F4F8)         // Soft Alabaster Background
private val PleosPanelWhite = Color(0xFFFFFFFF)       // Pure White Card Surface
private val PleosTileGray = Color(0xFFEDEDEF)         // Inactive Quick-Setting Tile Gray
private val PleosTrackBg = Color(0xFFE9EAEE)          // Segmented Track Background
private val PleosBorderSubtle = Color(0xFFE3E6EC)     // Subtle Hairline Border
private val PleosBlue = Color(0xFF2B95F6)             // Pleos Active Blue
private val PleosActiveCardBg = Color(0xFFEFF6FF)     // Clean Active Card Surface Tint
private val PleosActiveCardBorder = Color(0xFF0284C7) // Clean Active Card Border Blue
private val PleosCyan = Color(0xFF00C2FF)             // Signature Cyan Accent
private val PleosPurple = Color(0xFF7C4DFF)           // Studio Arc Purple Accent
private val PleosGreen = Color(0xFF1BB55C)            // Pleos Active Indicator Green
private val PleosAmber = Color(0xFFF59E0B)            // Safety Amber
private val PleosDevRed = Color(0xFFDC2626)           // Explicit DEV Badge Red
private val PleosDevRedTint = Color(0xFFFEF2F2)       // Soft Red Background for DEV Panel
private val PleosStageNavy = Color(0xFF0F172A)        // Concert Hall Stage Navy
private val PleosPitchDeepBlue = Color(0xFF1E40AF)    // Deep Royal/Navy Blue for Pitch Code
private val PleosVocalStatusBlue = Color(0xFF2563EB)  // Vivid Royal Blue for Vocal Input Status
private val PleosTextDark = Color(0xFF14161A)         // Crisp Charcoal Primary Text
private val PleosTextMuted = Color(0xFF727885)        // Muted Secondary Label

// ============================================================================
// Material Vector Icons for Mobile Mic Companion App
// ============================================================================
private object MicMaterialIcons {
    val Mic: ImageVector by lazy {
        ImageVector.Builder(
            name = "Mic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 14f)
                curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
                verticalLineTo(5f)
                curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
                curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
                verticalLineTo(11f)
                curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f)
                close()
                moveTo(17f, 11f)
                curveTo(17f, 13.76f, 14.76f, 16f, 12f, 16f)
                curveTo(9.24f, 16f, 7f, 13.76f, 7f, 11f)
                horizontalLineTo(5f)
                curveTo(5f, 14.53f, 7.61f, 17.43f, 11f, 17.92f)
                verticalLineTo(21f)
                horizontalLineTo(13f)
                verticalLineTo(17.92f)
                curveTo(16.39f, 17.43f, 19f, 14.53f, 19f, 11f)
                horizontalLineTo(17f)
                close()
            }
        }.build()
    }

    val Headphones: ImageVector by lazy {
        ImageVector.Builder(
            name = "Headphones",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
                verticalLineTo(19f)
                curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
                horizontalLineTo(8f)
                verticalLineTo(13f)
                horizontalLineTo(5f)
                verticalLineTo(12f)
                curveTo(5f, 8.13f, 8.13f, 5f, 12f, 5f)
                curveTo(15.87f, 5f, 19f, 8.13f, 19f, 12f)
                verticalLineTo(13f)
                horizontalLineTo(16f)
                verticalLineTo(21f)
                horizontalLineTo(19f)
                curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
                verticalLineTo(12f)
                curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
                close()
            }
        }.build()
    }

    val PlayStage: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayStage",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 5f)
                verticalLineTo(19f)
                lineTo(19f, 12f)
                lineTo(8f, 5f)
                close()
            }
        }.build()
    }

    val SmartLink: ImageVector by lazy {
        ImageVector.Builder(
            name = "SmartLink",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3.9f, 12f)
                curveTo(3.9f, 10.29f, 5.29f, 8.9f, 7f, 8.9f)
                horizontalLineTo(11f)
                verticalLineTo(7f)
                horizontalLineTo(7f)
                curveTo(4.24f, 7f, 2f, 9.24f, 2f, 12f)
                curveTo(2f, 14.76f, 4.24f, 17f, 7f, 17f)
                horizontalLineTo(11f)
                verticalLineTo(15.1f)
                horizontalLineTo(7f)
                curveTo(5.29f, 15.1f, 3.9f, 13.71f, 3.9f, 12f)
                close()
                moveTo(8f, 13f)
                horizontalLineTo(16f)
                verticalLineTo(11f)
                horizontalLineTo(8f)
                verticalLineTo(13f)
                close()
                moveTo(17f, 7f)
                horizontalLineTo(13f)
                verticalLineTo(8.9f)
                horizontalLineTo(17f)
                curveTo(18.71f, 8.9f, 20.1f, 10.29f, 20.1f, 12f)
                curveTo(20.1f, 13.71f, 18.71f, 15.1f, 17f, 15.1f)
                horizontalLineTo(13f)
                verticalLineTo(17f)
                horizontalLineTo(17f)
                curveTo(19.76f, 17f, 22f, 14.76f, 22f, 12f)
                curveTo(22f, 9.24f, 19.76f, 7f, 17f, 7f)
                close()
            }
        }.build()
    }

    val Sparkle: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sparkle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                lineTo(14.5f, 9.5f)
                lineTo(22f, 12f)
                lineTo(14.5f, 14.5f)
                lineTo(12f, 22f)
                lineTo(9.5f, 14.5f)
                lineTo(2f, 12f)
                lineTo(9.5f, 9.5f)
                lineTo(12f, 2f)
                close()
            }
        }.build()
    }

    val MusicNote: ImageVector by lazy {
        ImageVector.Builder(
            name = "MusicNote",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                verticalLineTo(13.55f)
                curveTo(11.41f, 13.21f, 10.73f, 13f, 10f, 13f)
                curveTo(7.79f, 13f, 6f, 14.79f, 6f, 17f)
                curveTo(6f, 19.21f, 7.79f, 21f, 10f, 21f)
                curveTo(12.21f, 21f, 14f, 19.21f, 14f, 17f)
                verticalLineTo(7f)
                horizontalLineTo(18f)
                verticalLineTo(3f)
                horizontalLineTo(12f)
                close()
            }
        }.build()
    }
}

private enum class MicTab(val label: String) {
    MIC_STUDIO("라이브 마이크"),
    QUICK_REMOTE("선곡·유튜브"),
    CAR_CONNECT("스마트 링크")
}

private fun formatMicTransportModeLabel(transportMode: String, isDirectLocal: Boolean): String {
    return when {
        isDirectLocal || transportMode.equals("DIRECT", ignoreCase = true) -> "초저지연 직결"
        transportMode.equals("WEBRTC_P2P", ignoreCase = true) -> "P2P 무선 연결"
        else -> "클라우드 연결"
    }
}

@Composable
private fun rememberPretendardFontFamily(): FontFamily {
    val regular = Font(Res.font.pretendard_regular, FontWeight.Normal)
    val medium = Font(Res.font.pretendard_medium, FontWeight.Medium)
    val semiBold = Font(Res.font.pretendard_semibold, FontWeight.SemiBold)
    val bold = Font(Res.font.pretendard_bold, FontWeight.Bold)
    val extraBold = Font(Res.font.pretendard_extrabold, FontWeight.ExtraBold)
    val black = Font(Res.font.pretendard_extrabold, FontWeight.Black)
    return remember(regular, medium, semiBold, bold, extraBold, black) {
        FontFamily(regular, medium, semiBold, bold, extraBold, black)
    }
}

@Composable
private fun rememberPretendardTypography(fontFamily: FontFamily): Typography {
    return remember(fontFamily) {
        val base = Typography()
        Typography(
            displayLarge = base.displayLarge.copy(fontFamily = fontFamily, letterSpacing = (-0.02).em),
            displayMedium = base.displayMedium.copy(fontFamily = fontFamily, letterSpacing = (-0.02).em),
            displaySmall = base.displaySmall.copy(fontFamily = fontFamily, letterSpacing = (-0.02).em),
            headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily, letterSpacing = (-0.015).em),
            headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily, letterSpacing = (-0.015).em),
            headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily, letterSpacing = (-0.015).em),
            titleLarge = base.titleLarge.copy(fontFamily = fontFamily, letterSpacing = (-0.015).em),
            titleMedium = base.titleMedium.copy(fontFamily = fontFamily, letterSpacing = (-0.015).em),
            titleSmall = base.titleSmall.copy(fontFamily = fontFamily, letterSpacing = (-0.01).em),
            bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily, letterSpacing = (-0.01).em),
            bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily, letterSpacing = (-0.01).em),
            bodySmall = base.bodySmall.copy(fontFamily = fontFamily, letterSpacing = (-0.01).em),
            labelLarge = base.labelLarge.copy(fontFamily = fontFamily, letterSpacing = (-0.01).em),
            labelMedium = base.labelMedium.copy(fontFamily = fontFamily, letterSpacing = (-0.01).em),
            labelSmall = base.labelSmall.copy(fontFamily = fontFamily, letterSpacing = (-0.01).em)
        )
    }
}

@Composable
@Preview
fun App(isDebugBuild: Boolean = isDebugBuildAllowed()) {
    val pretendardFontFamily = rememberPretendardFontFamily()
    val pretendardTypography = rememberPretendardTypography(pretendardFontFamily)

    MaterialTheme(
        colorScheme = lightColorScheme(
            background = PleosCanvasBg,
            surface = PleosPanelWhite,
            primary = PleosBlue,
            secondary = PleosGreen
        ),
        typography = pretendardTypography
    ) {
        ProvideTextStyle(
            value = TextStyle(
                fontFamily = pretendardFontFamily,
                letterSpacing = (-0.015).em
            )
        ) {
            val bridgeController = remember { AudioBridgeController() }
            val metrics by bridgeController.metrics.collectAsState()
            val connection by bridgeController.connection.collectAsState()
            val carState by bridgeController.carState.collectAsState()
            val localBackingAudioEnabled by bridgeController.localBackingAudioEnabled.collectAsState()
            val activeScoreResult by bridgeController.activeScoreResult.collectAsState()
            val incomingSharedUrl by SharedYouTubeIntentBus.incomingSharedUrl.collectAsState()

            var activeTab by remember { mutableStateOf(MicTab.MIC_STUDIO) }
            var serverUrlInput by remember(connection.discoveredCarUrl) {
                mutableStateOf(
                    connection.discoveredCarUrl.ifBlank { "ws://10.0.2.2:8080/ws" }
                )
            }
            var pinInput by remember { mutableStateOf("4829") }
            var isMuted by remember { mutableStateOf(false) }
            var demoVocalWave by remember { mutableStateOf(false) }
            var isKeepScreenOn: Boolean by remember { mutableStateOf(true) }

            LaunchedEffect(isKeepScreenOn) {
                setKeepScreenOnEnabled(isKeepScreenOn)
            }

            // 앱 실행 즉시 어쿠스틱 무대 효과음(48kHz 스테레오 SoundPool) 예열 + 기본 PIN(4829) 자동 연결 시작
            LaunchedEffect(Unit) {
                warmUpLocalStageSfx()
                bridgeController.connectToCarStage(serverUrlInput, pinInput)
            }

            LaunchedEffect(carState.guideVocalEnabled) {
                demoVocalWave = carState.guideVocalEnabled
            }

            // 5초간 다이내믹 점수 발표 시 박수 효과음 레이어링 + 정확히 5초 뒤 자동 종료
            LaunchedEffect(activeScoreResult?.resultId) {
                val showing = activeScoreResult ?: return@LaunchedEffect
                delay(180L)
                val pairedNow = connection.isCarVerified &&
                    (connection.state == "CONNECTED" || connection.state == "STREAMING")
                playLocalStageSfx(
                    sfxType = "APPLAUSE",
                    volumeScale = if (pairedNow && !isMuted) 0.48f else 0.88f
                )
                delay(4820L)
                if (activeScoreResult?.resultId == showing.resultId) {
                    bridgeController.dismissActiveScoreOverlay(notifyCar = false)
                }
            }

            // 유튜브 앱에서 [공유] -> [Pleos Stage Mic] 선택 시 전달된 링크를 즉시 플레이리스트에 추가하고 차량과 동기화
            LaunchedEffect(incomingSharedUrl) {
                val shared = incomingSharedUrl
                if (!shared.isNullOrBlank()) {
                    bridgeController.addTrackToStagePlaylist(shared, playImmediately = true)
                    activeTab = MicTab.QUICK_REMOTE
                    SharedYouTubeIntentBus.consume()
                }
            }

            val isPaired = connection.isCarVerified &&
                (connection.state == "CONNECTED" || connection.state == "STREAMING")
            val isConnecting = !isPaired && connection.state == "CONNECTING"
            val effectiveIsDevMode = isDebugBuild && carState.isDevMode
            val visiblePlaylist = remember(carState.playlist, effectiveIsDevMode) {
                if (effectiveIsDevMode) {
                    carState.playlist
                } else {
                    carState.playlist.filterNot { it.isDevSample }
                }
            }
            val emptyMicPlaceholderTrack = remember {
                UnifiedMicTrack(
                    id = "",
                    title = "예약된 곡이 없습니다",
                    artist = "상단 검색창이나 스마트폰 앱에서 유튜브 곡을 추가해 보세요.",
                    genre = "",
                    bpm = 120,
                    durationSec = 0,
                    durationLabel = "00:00",
                    videoId = "",
                    songId = "",
                    isDevSample = false
                )
            }
            val currentTrack = remember(visiblePlaylist, carState.currentTrackId) {
                if (visiblePlaylist.isEmpty()) {
                    emptyMicPlaceholderTrack
                } else {
                    visiblePlaylist.firstOrNull { it.id == carState.currentTrackId }
                        ?: visiblePlaylist.first()
                }
            }

            // 로컬 단독 모드일 때도 재생 진행바가 실시간으로 움직이도록 1초 주기 타이머 실행 (연결 시에는 차량과 1:1 동기화)
            LaunchedEffect(carState.isPlaying) {
                while (carState.isPlaying) {
                    delay(1000L)
                    bridgeController.tickLocalStandaloneProgress()
                }
            }

            // 보컬 가이드 ON 시 (반주 정지 상태 포함) 실시간 가이드 음정(Pitch/Note) 및 타건 성량(25%~85%) 동기화 루프
            var localGuidePitchHz by remember { mutableStateOf(0f) }
            var localGuideNote by remember { mutableStateOf("-") }
            var localGuideLevel by remember { mutableStateOf(0f) }

            LaunchedEffect(demoVocalWave, currentTrack.id, currentTrack.bpm, carState.keyShift, carState.mrPlaybackRate, carState.guideVolume, carState.syncOffsetMs) {
                if (!demoVocalWave) {
                    localGuidePitchHz = 0f
                    localGuideNote = "-"
                    localGuideLevel = 0f
                    return@LaunchedEffect
                }
                val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                val pattern = floatArrayOf(
                    261.63f, 293.66f, 329.63f, 392.00f, 440.00f, 392.00f, 329.63f, 293.66f,
                    329.63f, 392.00f, 440.00f, 523.25f, 493.88f, 440.00f, 392.00f, 329.63f
                )
                var elapsedSec = (carState.syncOffsetMs / 1000f).coerceAtLeast(0f)
                var prevStep = -1
                var noteAgeSec = 0f
                while (demoVocalWave) {
                    val safeRate = carState.mrPlaybackRate.coerceIn(0.80f, 1.20f)
                    val safeBpm = currentTrack.bpm.coerceIn(72, 160) * safeRate
                    val beatsPerSec = safeBpm / 60f
                    val stepIdx = ((elapsedSec * beatsPerSec) / 1.25f).toInt().coerceAtLeast(0)
                    if (stepIdx != prevStep) {
                        prevStep = stepIdx
                        noteAgeSec = 0f
                    }
                    val baseHz = pattern[stepIdx % pattern.size]
                    val shiftRatio = 2.0.pow(carState.keyShift / 12.0).toFloat()
                    val calcHz = (baseHz * shiftRatio).coerceIn(180f, 660f)
                    val midi = (69.0 + 12.0 * (ln(calcHz.toDouble() / 440.0) / ln(2.0))).roundToInt()
                    val calcNote = "${noteNames[((midi % 12) + 12) % 12]}${(midi / 12) - 1}"

                    val attackEnv = if (noteAgeSec < 0.04f) {
                        0.38f + 0.62f * (noteAgeSec / 0.04f)
                    } else {
                        exp(-(noteAgeSec - 0.04f) / 0.34f).coerceIn(0f, 1f)
                    }
                    val guideScale = (carState.guideVolume.coerceIn(0, 100) / 75f).coerceIn(0f, 1.35f)
                    val envLevel = ((26f + 58f * attackEnv) * guideScale).coerceIn(0f, 100f)

                    if (carState.guidePitchHz > 60f && carState.guideNote != "-") {
                        localGuidePitchHz = carState.guidePitchHz
                        localGuideNote = carState.guideNote
                        localGuideLevel = envLevel
                    } else {
                        localGuidePitchHz = calcHz
                        localGuideNote = calcNote
                        localGuideLevel = envLevel
                    }

                    delay(60L)
                    elapsedSec += 0.06f * safeRate
                    noteAgeSec += 0.06f * safeRate
                }
            }

            // [점멸 방지] 8% 미만 미세 배경 소음 차단 + 1.0초(1000ms) Hold Hysteresis + 300ms 음정 노트 디바운스
            val monotonicOrigin = remember { TimeSource.Monotonic.markNow() }
            val micScale = (carState.micVolume.coerceIn(0, 100) / 85f).coerceIn(0f, 1.35f)
            val rawMicLevelPercent = if (!isMuted && carState.micVolume > 0) {
                (metrics.rms * micScale).roundToInt().coerceIn(0, 100)
            } else {
                0
            }

            var isSingingActive by remember { mutableStateOf(false) }
            var lastSingingAboveThresholdMs by remember { mutableLongStateOf(-10_000L) }
            var heldSingingPercent by remember { mutableIntStateOf(0) }
            var debouncedMicNote by remember { mutableStateOf("") }
            var lastNoteUpdateMs by remember { mutableLongStateOf(0L) }
            var lastValidPitchDetectedMs by remember { mutableLongStateOf(-10_000L) }

            LaunchedEffect(rawMicLevelPercent, metrics.note, metrics.pitchHz, isMuted, carState.micVolume) {
                val nowMs = monotonicOrigin.elapsedNow().inWholeMilliseconds
                if (isMuted || carState.micVolume <= 0) {
                    isSingingActive = false
                    heldSingingPercent = 0
                    debouncedMicNote = ""
                    return@LaunchedEffect
                }
                if (rawMicLevelPercent >= 8) {
                    isSingingActive = true
                    lastSingingAboveThresholdMs = nowMs
                    heldSingingPercent = rawMicLevelPercent
                    val candidateNote = formatEnglishPitchNote(metrics.note)
                    if (metrics.pitchHz > 65f && candidateNote.isNotEmpty()) {
                        lastValidPitchDetectedMs = nowMs
                        if (debouncedMicNote.isEmpty() ||
                            candidateNote == debouncedMicNote ||
                            nowMs - lastNoteUpdateMs >= 300L
                        ) {
                            if (candidateNote != debouncedMicNote) {
                                debouncedMicNote = candidateNote
                                lastNoteUpdateMs = nowMs
                            }
                        }
                    } else if (debouncedMicNote.isNotEmpty() && nowMs - lastValidPitchDetectedMs >= 1000L) {
                        debouncedMicNote = ""
                    }
                } else if (isSingingActive) {
                    if (nowMs - lastSingingAboveThresholdMs >= 1000L) {
                        isSingingActive = false
                        heldSingingPercent = 0
                        debouncedMicNote = ""
                    } else {
                        if (rawMicLevelPercent > 0) {
                            heldSingingPercent = maxOf(rawMicLevelPercent, 8)
                        }
                        if (debouncedMicNote.isNotEmpty() && nowMs - lastValidPitchDetectedMs >= 1000L) {
                            debouncedMicNote = ""
                        }
                    }
                }
            }

            // 소리 입력 중 피치 노트가 일시적으로 끊겨도 직전 영문 음정 코드를 1.0초간 유지(Hold)한 뒤 만료 처리
            LaunchedEffect(isSingingActive, debouncedMicNote, lastValidPitchDetectedMs, isMuted, carState.micVolume) {
                if (!isSingingActive || debouncedMicNote.isEmpty() || isMuted || carState.micVolume <= 0) return@LaunchedEffect
                val remainingNoteHoldMs = (1000L - (monotonicOrigin.elapsedNow().inWholeMilliseconds - lastValidPitchDetectedMs))
                    .coerceAtLeast(50L)
                delay(remainingNoteHoldMs + 25L)
                val nowMs = monotonicOrigin.elapsedNow().inWholeMilliseconds
                if (nowMs - lastValidPitchDetectedMs >= 1000L) {
                    debouncedMicNote = ""
                }
            }

            // 메트릭 업데이트가 멈춰도 정확히 1.0초 Hold 후 대기 상태로 복귀 보장
            LaunchedEffect(isSingingActive, lastSingingAboveThresholdMs, isMuted, carState.micVolume) {
                if (!isSingingActive || isMuted || carState.micVolume <= 0) return@LaunchedEffect
                val remainingHoldMs = (1000L - (monotonicOrigin.elapsedNow().inWholeMilliseconds - lastSingingAboveThresholdMs))
                    .coerceAtLeast(60L)
                delay(remainingHoldMs + 25L)
                val nowMs = monotonicOrigin.elapsedNow().inWholeMilliseconds
                if (rawMicLevelPercent < 8 && nowMs - lastSingingAboveThresholdMs >= 1000L) {
                    isSingingActive = false
                    heldSingingPercent = 0
                    debouncedMicNote = ""
                }
            }

            val effectiveRms = when {
                isMuted -> 0f
                isSingingActive -> (metrics.rms * micScale).coerceIn(8f, 100f)
                demoVocalWave -> localGuideLevel
                else -> 0f
            }
            val effectivePitchHz = when {
                isMuted -> 0f
                isSingingActive -> metrics.pitchHz
                demoVocalWave -> localGuidePitchHz
                else -> 0f
            }
            val effectiveNote = when {
                isMuted -> "음소거"
                isSingingActive -> debouncedMicNote.ifBlank { "음정 인식 중" }
                demoVocalWave && localGuideNote != "-" -> formatEnglishPitchNote(localGuideNote).ifBlank { "음정 대기 중" }
                else -> "음정 대기 중"
            }

            val triggerStageSfx: (String) -> Unit = { sfx ->
                playLocalStageSfx(
                    sfxType = sfx,
                    volumeScale = if (isPaired && !isMuted) 0.42f else 0.88f
                )
                bridgeController.sendRemoteCommand(type = "TRIGGER_SFX", sfx = sfx)
            }

            val animatedRms by animateFloatAsState(
                targetValue = effectiveRms,
                animationSpec = tween(70),
                label = "rms"
            )

            val vocalAccentColor by animateColorAsState(
                targetValue = when {
                    isSingingActive && (effectivePitchHz > 380f || effectiveRms > 78f) -> PleosPurple
                    isSingingActive -> Color(0xFF1D4ED8)
                    demoVocalWave -> PleosBlue
                    else -> PleosBlue
                },
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "vocalAccent"
            )

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = PleosCanvasBg
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeContentPadding()
                ) {
                    // 240x200dp YouTube & Audio WebView at zIndex(-1f) hidden behind the opaque stage UI at zIndex(1f)
                    HeadlessAudioWebViewBridge(
                        controller = bridgeController,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .size(240.dp, 200.dp)
                            .alpha(1f)
                            .zIndex(-1f)
                    )

                    // 전체 컨테이너 Column: verticalScroll(rememberScrollState()) 필수 적용 + 한 화면 컴팩트 간격
                    Column(
                        modifier = Modifier
                            .zIndex(1f)
                            .fillMaxSize()
                            .background(PleosCanvasBg)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. 미니멀 상단 상태 바 (기어 상태 + Debug 전용 PROD/DEV 작은 칩 + 컴팩트 연결·지연 배지 & 터치 시 상세 카드)
                        var showLatencyDetailPopup by remember { mutableStateOf(false) }
                        LaunchedEffect(isPaired) {
                            if (!isPaired) {
                                showLatencyDetailPopup = false
                            }
                        }
                        PleosMobileTopBar(
                            isPaired = isPaired,
                            isConnecting = isConnecting,
                            totalLatencyMs = connection.totalVocalLatencyMs,
                            isParked = carState.isParked,
                            isDebugBuild = isDebugBuild,
                            isDevMode = effectiveIsDevMode,
                            onToggleDevMode = {
                                if (isDebugBuild) {
                                    bridgeController.updateDevMode(!effectiveIsDevMode)
                                }
                            },
                            onConnectBadgeClick = {
                                if (isPaired) {
                                    showLatencyDetailPopup = !showLatencyDetailPopup
                                } else {
                                    activeTab = MicTab.CAR_CONNECT
                                }
                            }
                        )

                        PleosKeepScreenOnToggleBar(
                            isKeepScreenOn = isKeepScreenOn,
                            onToggle = { isKeepScreenOn = !isKeepScreenOn }
                        )

                        if (isPaired && showLatencyDetailPopup) {
                            val isDirectQuality = connection.transportMode.equals("WEBRTC_P2P", ignoreCase = true) ||
                                connection.isDirectLocalConnected ||
                                connection.transportMode.equals("DIRECT", ignoreCase = true)
                            val qualityLabel = formatMicTransportModeLabel(
                                transportMode = connection.transportMode,
                                isDirectLocal = connection.isDirectLocalConnected
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = PleosPanelWhite,
                                border = BorderStroke(1.2.dp, PleosActiveCardBorder.copy(alpha = 0.45f)),
                                shadowElevation = 2.dp
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "차량 사운드 연결 및 지연 상태",
                                            color = PleosTextDark,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                modifier = Modifier.clickable {
                                                    showLatencyDetailPopup = false
                                                    activeTab = MicTab.CAR_CONNECT
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = PleosActiveCardBg
                                            ) {
                                                Text(
                                                    text = "스마트 링크",
                                                    color = PleosBlue,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                            Surface(
                                                modifier = Modifier.clickable { showLatencyDetailPopup = false },
                                                shape = RoundedCornerShape(8.dp),
                                                color = PleosTrackBg
                                            ) {
                                                Text(
                                                    text = "닫기",
                                                    color = PleosTextMuted,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "연결 상태",
                                                color = PleosTextMuted,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Text(
                                                text = "● 연결됨 (PIN ${connection.pin})",
                                                color = PleosGreen,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "연결 경로",
                                                color = PleosTextMuted,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Text(
                                                text = qualityLabel,
                                                color = if (isDirectQuality) PleosGreen else PleosBlue,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = PleosTrackBg,
                                        shadowElevation = 0.dp
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                Triple("총 보컬 지연:", "${connection.totalVocalLatencyMs} ms", PleosBlue),
                                                Triple("네트워크 지연:", "${connection.netLatencyMs} ms", PleosTextDark),
                                                Triple("오디오 버퍼 지연:", "${connection.carBufferMs} ms", PleosTextDark)
                                            ).forEach { (metricLabel, metricVal, metricColor) ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = metricLabel,
                                                        color = PleosTextMuted,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                    Text(
                                                        text = metricVal,
                                                        color = metricColor,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. 상단 탭 바: 높이 50.dp, 텍스트 13.5.sp ExtraBold, 말줄임표(...) 잘림 완벽 방지
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = PleosTrackBg,
                            shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                MicTab.entries.forEach { tab ->
                                    val selected = tab == activeTab
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable { activeTab = tab },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) PleosPanelWhite else Color.Transparent,
                                        shadowElevation = 0.dp
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        ) {
                                            Text(
                                                text = tab.label,
                                                color = if (selected) PleosBlue else PleosTextMuted,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. 상단 '현재 재생 중(Now Playing)' 미니 플레이어 배너 (차량과 1:1 실시간 동기화 + 터치/드래그 Seek 지원)
                        if (carState.isPlaying || activeTab == MicTab.QUICK_REMOTE) {
                            PleosMobileNowPlayingBanner(
                                currentTrack = currentTrack,
                                isPlaying = carState.isPlaying,
                                progressSec = carState.progressSec,
                                durationSec = carState.durationSec.coerceAtLeast(currentTrack.durationSec),
                                isDevMode = effectiveIsDevMode,
                                onPrevClick = { bridgeController.skipAdjacentTrack(-1) },
                                onPlayPauseClick = { bridgeController.togglePlayPauseOnStage() },
                                onNextClick = { bridgeController.skipAdjacentTrack(1) },
                                onSeekTo = { targetSec -> bridgeController.seekToPositionSec(targetSec) },
                                onBannerClick = { activeTab = MicTab.QUICK_REMOTE }
                            )
                        }

                        // 4. 탭별 컨텐츠 영역
                        when (activeTab) {
                            MicTab.MIC_STUDIO -> {
                                PleosMicStudioTab(
                                    rms = animatedRms,
                                    note = effectiveNote,
                                    isSingingActive = isSingingActive,
                                    singingPercent = if (rawMicLevelPercent >= 8) rawMicLevelPercent else heldSingingPercent,
                                    accentColor = vocalAccentColor,
                                    isPaired = isPaired,
                                    totalLatencyMs = connection.totalVocalLatencyMs,
                                    netLatencyMs = connection.netLatencyMs,
                                    carBufferMs = connection.carBufferMs,
                                    isDirectPath = connection.isDirectLocalConnected || connection.transportMode == "DIRECT",
                                    isMuted = isMuted,
                                    demoVocalWave = demoVocalWave,
                                    isPlaying = carState.isPlaying,
                                    currentTrackTitle = currentTrack.title,
                                    backingVolume = carState.backingVolume,
                                    micVolume = carState.micVolume,
                                    guideVolume = carState.guideVolume,
                                    echoLevel = carState.echoLevel,
                                    mrPlaybackRate = carState.mrPlaybackRate,
                                    syncOffsetMs = carState.syncOffsetMs,
                                    keyShift = carState.keyShift,
                                    onToggleMute = {
                                        isMuted = !isMuted
                                        bridgeController.setMicMuted(isMuted)
                                    },
                                    onToggleDemoVocal = {
                                        demoVocalWave = !demoVocalWave
                                        bridgeController.setDemoVocalWave(demoVocalWave)
                                    },
                                    onTogglePlay = {
                                        bridgeController.togglePlayPauseOnStage()
                                    },
                                    onOpenConnect = { activeTab = MicTab.CAR_CONNECT },
                                    onBackingVolumeChange = { bridgeController.updateBackingVolume(it) },
                                    onMicVolumeChange = { bridgeController.updateMicVolume(it) },
                                    onGuideVolumeChange = { bridgeController.updateGuideVolume(it) },
                                    onEchoChange = { newEcho ->
                                        bridgeController.updateEchoLevel(newEcho)
                                    },
                                    onPlaybackRateChange = { bridgeController.updatePlaybackRate(it) },
                                    onSyncOffsetChange = { bridgeController.updateSyncOffsetMs(it) },
                                    onKeyShiftChange = { newKey ->
                                        bridgeController.updateKeyShift(newKey)
                                    },
                                    onResetAllAudio = {
                                        bridgeController.resetAudioMixerDefaults()
                                    },
                                    onTriggerSfx = triggerStageSfx,
                                    isScoreModeEnabled = carState.isScoreModeEnabled,
                                    currentScore = carState.score,
                                    onToggleScoreMode = { bridgeController.toggleScoreMode() }
                                )
                            }

                            MicTab.QUICK_REMOTE -> {
                                PleosQuickRemoteTab(
                                    playlist = visiblePlaylist,
                                    currentTrackId = carState.currentTrackId,
                                    isPlaying = carState.isPlaying,
                                    progressSec = carState.progressSec,
                                    durationSec = carState.durationSec,
                                    backingVolume = carState.backingVolume,
                                    micVolume = carState.micVolume,
                                    guideVolume = carState.guideVolume,
                                    echoLevel = carState.echoLevel,
                                    mrPlaybackRate = carState.mrPlaybackRate,
                                    syncOffsetMs = carState.syncOffsetMs,
                                    isDevMode = effectiveIsDevMode,
                                    localBackingAudioEnabled = localBackingAudioEnabled,
                                    onToggleLocalBackingAudio = {
                                        bridgeController.setLocalBackingAudioEnabled(!localBackingAudioEnabled)
                                    },
                                    onPlayOrToggleTrack = { track ->
                                        if (track.id == carState.currentTrackId) {
                                            bridgeController.togglePlayPauseOnStage()
                                        } else {
                                            bridgeController.playTrackOnStage(track)
                                        }
                                    },
                                    onAddTrackInput = { rawInput ->
                                        bridgeController.addTrackToStagePlaylist(rawInput)
                                    },
                                    onMoveTrack = { fromIdx, toIdx ->
                                        bridgeController.moveTrackInStagePlaylist(fromIdx, toIdx)
                                    },
                                    onDeleteTrack = { trackId ->
                                        bridgeController.removeTrackFromStagePlaylist(trackId)
                                    },
                                    onPrevTrack = { bridgeController.skipAdjacentTrack(-1) },
                                    onTogglePlayPause = { bridgeController.togglePlayPauseOnStage() },
                                    onNextTrack = { bridgeController.skipAdjacentTrack(1) },
                                    onStopSong = { bridgeController.stopStagePlayback() },
                                    onSeekTo = { targetSec -> bridgeController.seekToPositionSec(targetSec) },
                                    onBackingVolumeChange = { bridgeController.updateBackingVolume(it) },
                                    onMicVolumeChange = { bridgeController.updateMicVolume(it) },
                                    onGuideVolumeChange = { bridgeController.updateGuideVolume(it) },
                                    onEchoLevelChange = { bridgeController.updateEchoLevel(it) },
                                    onPlaybackRateChange = { bridgeController.updatePlaybackRate(it) },
                                    onSyncOffsetChange = { bridgeController.updateSyncOffsetMs(it) },
                                    onResetAllAudio = { bridgeController.resetAudioMixerDefaults() },
                                    onTriggerSfx = triggerStageSfx,
                                    isScoreModeEnabled = carState.isScoreModeEnabled,
                                    onToggleScoreMode = { bridgeController.toggleScoreMode() }
                                )
                            }

                            MicTab.CAR_CONNECT -> {
                                val transportLabel = formatMicTransportModeLabel(
                                    transportMode = connection.transportMode,
                                    isDirectLocal = connection.isDirectLocalConnected
                                )
                                PleosCarConnectTab(
                                    pin = pinInput,
                                    connectionMessage = connection.message,
                                    isPaired = isPaired,
                                    isConnecting = isConnecting,
                                    totalLatencyMs = connection.totalVocalLatencyMs,
                                    transportModeLabel = transportLabel,
                                    hotspotIp = connection.phoneHotspotIps.firstOrNull().orEmpty(),
                                    discoveredCarIp = connection.discoveredCarUrl
                                        .removePrefix("ws://")
                                        .removePrefix("wss://")
                                        .substringBefore("/")
                                        .substringBefore(":")
                                        .takeIf { it.isNotBlank() && it != "10.0.2.2" && it != "127.0.0.1" }
                                        .orEmpty(),
                                    isDevMode = effectiveIsDevMode,
                                    onPinDigitClick = { digit ->
                                        if (pinInput.length < 4) pinInput += digit
                                    },
                                    onPinBackspace = {
                                        if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                    },
                                    onPinReset = { pinInput = "4829" },
                                    onConnectClick = {
                                        val targetPin = if (pinInput.length == 4) pinInput else "4829"
                                        pinInput = targetPin
                                        val targetUrl = connection.discoveredCarUrl.ifBlank { serverUrlInput }
                                        bridgeController.connectToCarStage(targetUrl, targetPin)
                                    },
                                    onManualIpConnect = { manualIp ->
                                        val targetPin = if (pinInput.length == 4) pinInput else "4829"
                                        pinInput = targetPin
                                        if (manualIp.isNotBlank()) {
                                            serverUrlInput = manualIp
                                            bridgeController.connectToCarStage(manualIp, targetPin)
                                        } else {
                                            val targetUrl = connection.discoveredCarUrl.ifBlank { serverUrlInput }
                                            bridgeController.connectToCarStage(targetUrl, targetPin)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // 5초간 다이내믹 노래방 점수 발표 모달 (스마트폰 화면 상단/중앙 오버레이)
                    val showingScoreModal = activeScoreResult
                    if (showingScoreModal != null && carState.isScoreModeEnabled) {
                        MicKaraokeScoreModal(
                            result = showingScoreModal,
                            fontFamily = pretendardFontFamily,
                            onDismiss = {
                                bridgeController.dismissActiveScoreOverlay(notifyCar = true)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// TOP HEADER BAR (MINIMAL GEAR STATUS + PROD/DEV CHIP + COMPACT LATENCY BADGE)
// ============================================================================
@Composable
private fun PleosMobileTopBar(
    isPaired: Boolean,
    isConnecting: Boolean = false,
    totalLatencyMs: Int = 0,
    isParked: Boolean,
    isDebugBuild: Boolean = false,
    isDevMode: Boolean,
    onToggleDevMode: () -> Unit,
    onConnectBadgeClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isParked) "주차 모드 (P)" else "주행 모드 (D)",
                color = if (isParked) PleosTextDark else PleosAmber,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
            )

            // 개발자 모드(DEV) vs 사용자 모드(PROD) 소형 칩 (Debug 빌드 전용 — Release 빌드에서는 렌더링 완전 제외)
            if (isDebugBuild) {
                Surface(
                    modifier = Modifier.clickable { onToggleDevMode() },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isDevMode) PleosDevRed else PleosTrackBg,
                    border = BorderStroke(1.dp, if (isDevMode) PleosDevRed else PleosBorderSubtle),
                    shadowElevation = 0.dp
                ) {
                    Text(
                        text = if (isDevMode) "DEV" else "PROD",
                        color = if (isDevMode) Color.White else PleosTextMuted,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        val badgeLabel = when {
            isPaired -> "● 연결됨 · ⚡ ${totalLatencyMs}ms"
            isConnecting -> "◐ 차량 연결 중..."
            else -> "○ 연결 안 됨"
        }
        val badgeBorderColor = when {
            isPaired -> PleosGreen
            isConnecting -> PleosBlue
            else -> PleosBorderSubtle
        }
        val badgeTextColor = when {
            isPaired -> PleosGreen
            isConnecting -> PleosBlue
            else -> PleosTextMuted
        }
        Surface(
            modifier = Modifier.clickable { onConnectBadgeClick() },
            shape = RoundedCornerShape(50),
            color = PleosPanelWhite,
            border = BorderStroke(1.2.dp, badgeBorderColor),
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = badgeLabel,
                    color = badgeTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun PleosKeepScreenOnToggleBar(
    isKeepScreenOn: Boolean,
    onToggle: () -> Unit
) {
    val cardBg = if (isKeepScreenOn) Color(0xFFEFF6FF) else Color(0xFFF1F5F9)
    val cardBorder = if (isKeepScreenOn) Color(0xFF3B82F6).copy(alpha = 0.55f) else Color(0xFFCBD5E1)
    val badgeBg = if (isKeepScreenOn) Color(0xFF2563EB) else Color(0xFF64748B)
    val titleColor = if (isKeepScreenOn) Color(0xFF1E3A8A) else Color(0xFF475569)
    val subtitleColor = if (isKeepScreenOn) Color(0xFFB45309) else Color(0xFF64748B)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(1.2.dp, cardBorder),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isKeepScreenOn) Color(0xFFFEF3C7) else Color(0xFFE2E8F0),
                    border = BorderStroke(
                        1.dp,
                        if (isKeepScreenOn) Color(0xFFF59E0B).copy(alpha = 0.55f) else Color(0xFFCBD5E1)
                    )
                ) {
                    Text(
                        text = if (isKeepScreenOn) "☀️ 화면 켜짐 유지 ON" else "🌙 화면 자동 꺼짐",
                        color = titleColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = if (isKeepScreenOn) "노래 중 화면이 꺼지지 않습니다" else "일정 시간 후 화면이 꺼집니다",
                    color = subtitleColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = badgeBg
            ) {
                Text(
                    text = if (isKeepScreenOn) "ON" else "OFF",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

// ============================================================================
// NOW PLAYING MINI PLAYER BANNER (1:1 SYNC WITH CAR STAGE)
// ============================================================================
@Composable
private fun PleosMobileNowPlayingBanner(
    currentTrack: UnifiedMicTrack,
    isPlaying: Boolean,
    progressSec: Int,
    durationSec: Int,
    isDevMode: Boolean,
    onPrevClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekTo: (Int) -> Unit = {},
    onBannerClick: () -> Unit
) {
    val safeDuration = durationSec.coerceAtLeast(1)
    var previewSeekSec by remember(currentTrack.id) { mutableStateOf<Int?>(null) }
    val shownProgressSec = previewSeekSec ?: progressSec

    Surface(
        modifier = Modifier
            .zIndex(1f)
            .fillMaxWidth()
            .clickable { onBannerClick() },
        shape = RoundedCornerShape(18.dp),
        color = PleosStageNavy,
        border = BorderStroke(1.5.dp, if (isPlaying) PleosCyan else Color(0xFF334155)),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MicAnimatedEqualizerBars(
                            isPlaying = isPlaying,
                            barColor = if (isPlaying) PleosCyan else Color(0xFF94A3B8)
                        )

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isPlaying) PleosCyan.copy(alpha = 0.2f) else Color(0xFF334155),
                            shadowElevation = 0.dp
                        ) {
                            Text(
                                text = if (isPlaying) "현재 재생 중" else "무대 대기 중",
                                color = if (isPlaying) PleosCyan else Color(0xFFCBD5E1),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }

                        if (isDevMode && currentTrack.isDevSample) {
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = PleosDevRed,
                                shadowElevation = 0.dp
                            ) {
                                Text(
                                    text = "DEV",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = if (currentTrack.id.isBlank()) {
                                "00:00 / 00:00"
                            } else {
                                "${formatTrackMmSs(shownProgressSec)} / ${formatTrackMmSs(safeDuration)}"
                            },
                            color = PleosCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Text(
                        text = currentTrack.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = if (currentTrack.id.isBlank()) {
                            currentTrack.artist
                        } else {
                            "${currentTrack.artist} · ${formatTrackMmSs(shownProgressSec)} / ${formatTrackMmSs(safeDuration)}"
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 미니 플레이어 원형 컨트롤 버튼 ([⏮], [⏸ / ▶], [⏭])
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .size(30.dp)
                            .clickable { onPrevClick() },
                        shape = CircleShape,
                        color = Color(0xFF1E293B),
                        shadowElevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "⏮",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onPlayPauseClick() },
                        shape = CircleShape,
                        color = if (isPlaying) PleosCyan else PleosBlue,
                        shadowElevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isPlaying) "⏸" else "▶",
                                color = if (isPlaying) PleosStageNavy else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .size(30.dp)
                            .clickable { onNextClick() },
                        shape = CircleShape,
                        color = Color(0xFF1E293B),
                        shadowElevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "⏭",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // 실시간 재생 진행바 (터치 & 좌우 드래그 탐색 Seek 지원)
            PleosSeekableProgressBar(
                progressSec = progressSec,
                durationSec = safeDuration,
                isPlaying = isPlaying,
                enabled = currentTrack.id.isNotBlank(),
                trackColor = Color(0xFF334155),
                activeGradient = listOf(PleosCyan, PleosBlue, PleosPurple),
                thumbColor = PleosCyan,
                onPreviewSecChange = { previewSeekSec = it },
                onSeekTo = onSeekTo
            )
        }
    }
}

/**
 * 터치(Tap) 및 좌우 드래그(Scrub)로 재생 위치를 즉시 이동할 수 있는 인터랙티브 프로그레스바
 */
@Composable
private fun PleosSeekableProgressBar(
    progressSec: Int,
    durationSec: Int,
    isPlaying: Boolean,
    enabled: Boolean = true,
    trackColor: Color = Color(0xFFDBEAFE),
    activeGradient: List<Color> = listOf(PleosActiveCardBorder, PleosBlue, PleosPurple),
    thumbColor: Color = PleosBlue,
    onPreviewSecChange: ((Int?) -> Unit)? = null,
    onSeekTo: (Int) -> Unit
) {
    val safeDuration = durationSec.coerceAtLeast(1)
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubRatio by remember { mutableFloatStateOf(0f) }

    val liveRatio = (progressSec.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val shownRatio = if (isScrubbing) scrubRatio else liveRatio

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .onSizeChanged { sz ->
                barWidthPx = sz.width.toFloat().coerceAtLeast(1f)
            }
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(safeDuration) {
                            detectTapGestures { offset ->
                                val ratio = (offset.x / barWidthPx).coerceIn(0f, 1f)
                                val targetSec = (ratio * safeDuration).roundToInt().coerceIn(0, safeDuration)
                                onPreviewSecChange?.invoke(null)
                                onSeekTo(targetSec)
                            }
                        }
                        .pointerInput(safeDuration) {
                            detectHorizontalDragGestures(
                                onDragStart = { startOffset ->
                                    isScrubbing = true
                                    scrubRatio = (startOffset.x / barWidthPx).coerceIn(0f, 1f)
                                    onPreviewSecChange?.invoke(
                                        (scrubRatio * safeDuration).roundToInt().coerceIn(0, safeDuration)
                                    )
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    scrubRatio = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                                    onPreviewSecChange?.invoke(
                                        (scrubRatio * safeDuration).roundToInt().coerceIn(0, safeDuration)
                                    )
                                },
                                onDragEnd = {
                                    val targetSec = (scrubRatio * safeDuration).roundToInt().coerceIn(0, safeDuration)
                                    isScrubbing = false
                                    onPreviewSecChange?.invoke(null)
                                    onSeekTo(targetSec)
                                },
                                onDragCancel = {
                                    isScrubbing = false
                                    onPreviewSecChange?.invoke(null)
                                }
                            )
                        }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            val trackHeightPx = if (isScrubbing) 7.dp.toPx() else 5.5.dp.toPx()
            val centerY = size.height / 2f
            val cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                size = Size(size.width, trackHeightPx),
                cornerRadius = cornerRadius
            )

            val fillRatio = shownRatio.coerceAtLeast(if (isPlaying && !isScrubbing) 0.025f else 0f)
            val fillWidth = (size.width * fillRatio).coerceIn(0f, size.width)
            if (fillWidth > 1f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(activeGradient),
                    topLeft = Offset(0f, centerY - trackHeightPx / 2f),
                    size = Size(fillWidth, trackHeightPx),
                    cornerRadius = cornerRadius
                )
            }

            if (enabled) {
                val thumbRadius = if (isScrubbing) 6.5.dp.toPx() else 5.dp.toPx()
                val thumbX = (size.width * shownRatio).coerceIn(thumbRadius, (size.width - thumbRadius).coerceAtLeast(thumbRadius))
                drawCircle(
                    color = thumbColor.copy(alpha = if (isScrubbing) 0.34f else 0.20f),
                    radius = thumbRadius * 1.45f,
                    center = Offset(thumbX, centerY)
                )
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(thumbX, centerY)
                )
                drawCircle(
                    color = thumbColor,
                    radius = thumbRadius * 0.62f,
                    center = Offset(thumbX, centerY)
                )
            }
        }
    }
}

/**
 * 현재 재생 중인 곡에 표시되는 움직이는 오디오 이퀄라이저 아이콘 (4-Bar Equalizer)
 */
@Composable
private fun MicAnimatedEqualizerBars(
    isPlaying: Boolean,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "eqBars")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "eqPhase"
    )

    Row(
        modifier = modifier
            .width(18.dp)
            .height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0 until 4) {
            val fraction = if (isPlaying) {
                (0.3f + 0.7f * abs(sin(phase + i * 0.9f))).coerceIn(0.25f, 1f)
            } else {
                0.28f
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction)
                    .background(barColor, RoundedCornerShape(2.dp))
            )
        }
    }
}

// ============================================================================
// TAB 1: PLEOS MIC STUDIO (ONE-SCREEN COMPACT HEIGHTS & ZERO ARTIFACTS)
// - 원형 비주얼라이저 카드 전체 높이: 204.dp (210.dp 이하), 내부 원형 게이지 150.dp
// - 2x2 컨트롤 카드 각각의 높이: 92.dp (내부 패딩 14.dp), 단일 Surface
// - 하단 '공간감' 및 '키 조절' 컨트롤 바 높이: 각각 56.dp, 두 바 사이 간격 8.dp
// ============================================================================
private fun formatPlaybackRateLabel(rate: Float): String {
    val pct = (rate.coerceIn(0.75f, 1.25f) * 100f).roundToInt()
    val whole = pct / 100
    val frac = pct % 100
    return if (frac % 10 == 0) {
        "${whole}.${frac / 10}배"
    } else {
        val fracStr = if (frac < 10) "0$frac" else "$frac"
        "${whole}.${fracStr}배"
    }
}

private fun formatSyncOffsetLabel(offsetMs: Int): String {
    return when {
        offsetMs == 0 -> "0ms"
        offsetMs > 0 -> "+${offsetMs}ms"
        else -> "${offsetMs}ms"
    }
}

private fun formatKeyShiftLabel(keyShift: Int): String {
    return when {
        keyShift == 0 -> "기본 키"
        keyShift > 0 -> "+${keyShift} 키"
        else -> "${keyShift} 키"
    }
}

private fun formatEnglishPitchNote(rawNote: String): String {
    val trimmed = rawNote.trim().replace('♯', '#').uppercase()
    if (trimmed.isEmpty() || trimmed == "-") return ""
    return trimmed
}

@Composable
private fun PleosMicStudioTab(
    rms: Float,
    note: String,
    isSingingActive: Boolean,
    singingPercent: Int,
    accentColor: Color,
    isPaired: Boolean,
    totalLatencyMs: Int = 32,
    netLatencyMs: Int = 3,
    carBufferMs: Int = 24,
    isDirectPath: Boolean = true,
    isMuted: Boolean,
    demoVocalWave: Boolean,
    isPlaying: Boolean,
    currentTrackTitle: String,
    backingVolume: Int,
    micVolume: Int,
    guideVolume: Int,
    echoLevel: Int,
    mrPlaybackRate: Float,
    syncOffsetMs: Int,
    keyShift: Int,
    onToggleMute: () -> Unit,
    onToggleDemoVocal: () -> Unit,
    onTogglePlay: () -> Unit,
    onOpenConnect: () -> Unit,
    onBackingVolumeChange: (Int) -> Unit,
    onMicVolumeChange: (Int) -> Unit,
    onGuideVolumeChange: (Int) -> Unit,
    onEchoChange: (Int) -> Unit,
    onPlaybackRateChange: (Float) -> Unit,
    onSyncOffsetChange: (Int) -> Unit,
    onKeyShiftChange: (Int) -> Unit,
    onResetAllAudio: () -> Unit,
    onTriggerSfx: (String) -> Unit,
    isScoreModeEnabled: Boolean = true,
    currentScore: Int = 78,
    onToggleScoreMode: () -> Unit = {}
) {
    val rippleTransition = rememberInfiniteTransition(label = "micRipple")
    val ripplePhase by rippleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripplePhase"
    )

    // 1. 원형 비주얼라이저 카드 (콘서트홀 음향 배지 및 하단 총 지연 바 완전 제거, 깔끔한 196.dp 높이)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp),
        shape = RoundedCornerShape(20.dp),
        color = PleosPanelWhite,
        border = BorderStroke(1.dp, PleosBorderSubtle),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "스튜디오 라이브 마이크",
                    color = PleosTextDark,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // 내부 원형 게이지 크기: 146.dp
            Box(
                modifier = Modifier.size(146.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val rmsNorm = (rms / 100f).coerceIn(0f, 1f)
                    val arcStrokePx = 12.dp.toPx()
                    val arcRadius = (size.minDimension / 2f) - arcStrokePx * 1.25f

                    // 성량에 따라 퍼져나가는 동심원 파동(Ripple) 효과
                    val activeIntensity = if (isMuted) 0f else rmsNorm.coerceAtLeast(0.12f)
                    for (waveIdx in 0..2) {
                        val phase = (ripplePhase + waveIdx * 0.33f) % 1f
                        val rippleRadius = arcRadius * (0.72f + phase * (0.34f + rmsNorm * 0.16f))
                        val rippleAlpha = (1f - phase) * (0.26f * activeIntensity)
                        drawCircle(
                            color = accentColor.copy(alpha = rippleAlpha.coerceIn(0f, 0.32f)),
                            radius = rippleRadius,
                            center = center,
                            style = Stroke(width = (3f - phase * 1.6f).dp.toPx())
                        )
                    }

                    // Soft inner radial glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.14f * activeIntensity),
                                Color.Transparent
                            ),
                            center = center,
                            radius = arcRadius * 1.05f
                        ),
                        radius = arcRadius * 1.05f,
                        center = center
                    )

                    // Background Track Arc (270 degrees)
                    val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                    val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
                    drawArc(
                        color = PleosTrackBg,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = arcStrokePx, cap = StrokeCap.Round)
                    )

                    // Gradient Active Arc Gauge
                    val sweep = (270f * (if (isMuted) 0.04f else rmsNorm.coerceAtLeast(0.08f))).coerceIn(10f, 270f)
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                PleosCyan,
                                PleosBlue,
                                PleosPurple,
                                accentColor,
                                PleosCyan
                            ),
                            center = center
                        ),
                        startAngle = 135f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = arcStrokePx, cap = StrokeCap.Round)
                    )
                }

                val isEnglishPitchCode = note.isNotEmpty() && note[0] in 'A'..'G'
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = note,
                        color = when {
                            isMuted -> PleosAmber
                            isSingingActive -> PleosPitchDeepBlue
                            demoVocalWave && isEnglishPitchCode -> PleosPitchDeepBlue
                            else -> PleosTextDark
                        },
                        fontSize = when {
                            isEnglishPitchCode -> 26.sp
                            note.length >= 5 -> 17.5.sp
                            else -> 22.sp
                        },
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isMuted -> "마이크 잠시 꺼짐"
                            isSingingActive -> "🎤 보컬 입력 중"
                            demoVocalWave -> "🎧 보컬 가이드"
                            else -> "마이크 준비 완료"
                        },
                        color = when {
                            isMuted -> PleosAmber
                            isSingingActive -> PleosVocalStatusBlue
                            demoVocalWave -> PleosVocalStatusBlue
                            else -> PleosVocalStatusBlue
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isMuted -> "버튼을 눌러 켜기"
                            isSingingActive -> "성량 ${singingPercent}%"
                            demoVocalWave -> "가이드 성량 ${rms.roundToInt()}%"
                            else -> "노래를 불러보세요"
                        },
                        color = PleosTextMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // 1B. 스튜디오 탭 원터치 무대 효과음 3열 버튼 (박수 · 환호 · 탬버린)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            "APPLAUSE" to "👏 박수",
            "CHEER" to "🎉 환호",
            "TAMBOURINE" to "🥁 탬버린"
        ).forEach { (sfxKey, sfxLabel) ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clickable { onTriggerSfx(sfxKey) },
                shape = RoundedCornerShape(14.dp),
                color = PleosActiveCardBg,
                border = BorderStroke(1.2.dp, PleosActiveCardBorder),
                shadowElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = sfxLabel,
                        color = PleosTextDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }

    // 2. 2x2 컨트롤 카드 (말줄임표 잘림 제로, 단일 Surface + [🏆 점수 표시 켜짐 / 꺼짐] 원터치 토글)
    Text(
        text = "마이크 및 무대 컨트롤",
        color = PleosTextDark,
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        softWrap = false
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PleosMobileQuickTile(
                modifier = Modifier.weight(1f),
                title = "라이브 마이크",
                subtitle = if (!isMuted) "켜짐 • 실시간 보컬" else "음소거됨",
                active = !isMuted,
                icon = MicMaterialIcons.Mic,
                onClick = onToggleMute
            )
            PleosMobileQuickTile(
                modifier = Modifier.weight(1f),
                title = "보컬 가이드",
                subtitle = if (demoVocalWave) "가이드 보컬 켜짐" else "꺼짐",
                active = demoVocalWave,
                icon = MicMaterialIcons.Headphones,
                onClick = onToggleDemoVocal
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PleosMobileQuickTile(
                modifier = Modifier.weight(1f),
                title = "무대 반주",
                subtitle = if (isPlaying) "재생 중 • $currentTrackTitle" else "터치하여 시작",
                active = isPlaying,
                icon = MicMaterialIcons.PlayStage,
                onClick = onTogglePlay
            )
            PleosMobileScoreQuickTile(
                modifier = Modifier.weight(1f),
                enabled = isScoreModeEnabled,
                currentScore = currentScore,
                onClick = onToggleScoreMode
            )
        }
    }

    // 3. 핵심 4대 볼륨 (2x2 그리드 카드) + 미세 조절 3종 (속도/싱크/키) + 우측 상단 [전체 초기화] 단일 통합
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "음량 및 빠르기 조절",
            color = PleosTextDark,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
        )
        Surface(
            modifier = Modifier
                .height(30.dp)
                .clickable { onResetAllAudio() },
            shape = RoundedCornerShape(10.dp),
            color = PleosActiveCardBg,
            border = BorderStroke(1.dp, PleosActiveCardBorder.copy(alpha = 0.45f)),
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "전체 초기화",
                    color = PleosActiveCardBorder,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }

    // 상단 핵심 4대 볼륨: 한눈에 들어오는 2x2 그리드 카드 ([-]  [ 85% ]  [+])
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PleosVolumeGridCard(
                modifier = Modifier.weight(1f),
                label = "반주 음량",
                valueText = "${backingVolume}%",
                onMinus = { onBackingVolumeChange(backingVolume - 10) },
                onPlus = { onBackingVolumeChange(backingVolume + 10) }
            )
            PleosVolumeGridCard(
                modifier = Modifier.weight(1f),
                label = "마이크 음량",
                valueText = "${micVolume}%",
                onMinus = { onMicVolumeChange(micVolume - 5) },
                onPlus = { onMicVolumeChange(micVolume + 5) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PleosVolumeGridCard(
                modifier = Modifier.weight(1f),
                label = "가이드 음량",
                valueText = "${guideVolume}%",
                onMinus = { onGuideVolumeChange(guideVolume - 5) },
                onPlus = { onGuideVolumeChange(guideVolume + 5) }
            )
            PleosVolumeGridCard(
                modifier = Modifier.weight(1f),
                label = "에코 공간감",
                valueText = "${echoLevel}%",
                onMinus = { onEchoChange(echoLevel - 5) },
                onPlus = { onEchoChange(echoLevel + 5) }
            )
        }

        // 하단 미세 조절 3종 (빠르기, 싱크 조절, 키 조절): [항목명] + [-]  [ 수치 ]  [+]
        PleosCompactValueStepperRow(
            label = "빠르기",
            valueText = formatPlaybackRateLabel(mrPlaybackRate),
            onMinus = { onPlaybackRateChange(mrPlaybackRate - 0.10f) },
            onPlus = { onPlaybackRateChange(mrPlaybackRate + 0.10f) }
        )

        PleosCompactValueStepperRow(
            label = "싱크 조절",
            valueText = formatSyncOffsetLabel(syncOffsetMs),
            onMinus = { onSyncOffsetChange(syncOffsetMs - 100) },
            onPlus = { onSyncOffsetChange(syncOffsetMs + 100) }
        )

        PleosCompactValueStepperRow(
            label = "키 조절",
            valueText = formatKeyShiftLabel(keyShift),
            onMinus = { onKeyShiftChange(keyShift - 1) },
            onPlus = { onKeyShiftChange(keyShift + 1) }
        )
    }
}

// ============================================================================
// TAB 2: UNIFIED PLAYLIST & YOUTUBE MR REMOTE (1:1 BI-DIRECTIONAL SYNC)
// - 차량('pleos-stage-car')의 우측 통합 플레이리스트 패널과 1:1 동일한 곡 목록/재생 상태 공유
// - 현재 재생 중인 곡 카드에는 정적인 [▶ 재생] 대신 움직이는 이퀄라이저 + [⏸ 일시정지] + 실시간 진행바 표시
// - DEV 모드 활성화 시 내장 데모 곡 옆에 붉은색 [DEV 샘플] 뱃지 표시
// ============================================================================
@Composable
private fun PleosQuickRemoteTab(
    playlist: List<UnifiedMicTrack>,
    currentTrackId: String,
    isPlaying: Boolean,
    progressSec: Int,
    durationSec: Int,
    backingVolume: Int,
    micVolume: Int,
    guideVolume: Int,
    echoLevel: Int,
    mrPlaybackRate: Float,
    syncOffsetMs: Int,
    isDevMode: Boolean,
    localBackingAudioEnabled: Boolean,
    onToggleLocalBackingAudio: () -> Unit,
    onPlayOrToggleTrack: (UnifiedMicTrack) -> Unit,
    onAddTrackInput: (String) -> Unit,
    onMoveTrack: (fromIndex: Int, toIndex: Int) -> Unit,
    onDeleteTrack: (trackId: String) -> Unit,
    onPrevTrack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onStopSong: () -> Unit,
    onSeekTo: (Int) -> Unit = {},
    onBackingVolumeChange: (Int) -> Unit,
    onMicVolumeChange: (Int) -> Unit,
    onGuideVolumeChange: (Int) -> Unit,
    onEchoLevelChange: (Int) -> Unit,
    onPlaybackRateChange: (Float) -> Unit,
    onSyncOffsetChange: (Int) -> Unit,
    onResetAllAudio: () -> Unit,
    onTriggerSfx: (String) -> Unit,
    isScoreModeEnabled: Boolean = true,
    onToggleScoreMode: () -> Unit = {}
) {
    var addTrackInput by remember { mutableStateOf("") }
    var draggedTrackId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "통합 플레이리스트 & 선곡",
            color = PleosTextDark,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Surface(
            shape = RoundedCornerShape(50),
            color = PleosActiveCardBg,
            border = BorderStroke(1.dp, PleosActiveCardBorder.copy(alpha = 0.35f)),
            shadowElevation = 0.dp
        ) {
            Text(
                text = "총 ${playlist.size}곡",
                color = PleosActiveCardBorder,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }

    // 1. 모던 캡슐형 검색/링크 추가 바 (투박한 OutlinedTextField 제거, #F1F5F9 배경, RoundedCornerShape(16.dp), 좌측 돋보기 아이콘 내장)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PleosMobileCapsuleSearchField(
            value = addTrackInput,
            onValueChange = { addTrackInput = it },
            placeholder = "유튜브 곡 링크 또는 검색어 입력",
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        )
        Surface(
            modifier = Modifier
                .height(48.dp)
                .clickable {
                    if (addTrackInput.isNotBlank()) {
                        onAddTrackInput(addTrackInput)
                        addTrackInput = ""
                    }
                },
            shape = RoundedCornerShape(16.dp),
            color = PleosBlue,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ 곡 추가",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // 1B. 검색창 바로 아래 유튜브 간편 연동 퀵 액션 버튼 2개 ([▶ YouTube 앱 열기], [📋 복사한 링크 추가])
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .clickable {
                    openYouTubeKaraokeAppSearch(addTrackInput.ifBlank { "노래방 MR KY TJ" })
                },
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFFEF2F2),
            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                Text(
                    text = "▶ YouTube 앱 열기",
                    color = Color(0xFFDC2626),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .clickable {
                    val clip = readClipboardTextForPlaylist()
                    val candidate = clip?.takeIf { it.isNotBlank() } ?: addTrackInput.takeIf { it.isNotBlank() }
                    if (!candidate.isNullOrBlank()) {
                        onAddTrackInput(candidate)
                        addTrackInput = ""
                    }
                },
            shape = RoundedCornerShape(14.dp),
            color = PleosActiveCardBg,
            border = BorderStroke(1.dp, PleosActiveCardBorder.copy(alpha = 0.45f)),
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                Text(
                    text = "📋 복사한 링크 추가",
                    color = PleosActiveCardBorder,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }

    // 1C. 에뮬레이터 무음 우회: 스마트폰에서 반주 소리 직접 듣기 ON/OFF 토글 (기본 ON)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clickable { onToggleLocalBackingAudio() },
        shape = RoundedCornerShape(14.dp),
        color = if (localBackingAudioEnabled) Color(0xFFECFDF5) else PleosPanelWhite,
        border = BorderStroke(
            width = 1.dp,
            color = if (localBackingAudioEnabled) PleosGreen else PleosBorderSubtle
        ),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (localBackingAudioEnabled) "🔊 스마트폰 반주 소리 듣기 ON" else "🔇 스마트폰 반주 소리 듣기 OFF",
                color = if (localBackingAudioEnabled) PleosGreen else PleosTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (localBackingAudioEnabled) "스피커 출력 중" else "터치하여 켜기",
                color = if (localBackingAudioEnabled) PleosGreen else PleosTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }

    // 2. 무대 재생 트랜스포트 컨트롤 바
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(
            "⏮ 이전 곡" to onPrevTrack,
            (if (isPlaying) "⏸ 일시정지" else "▶ 무대 재생") to onTogglePlayPause,
            "⏭ 다음 곡" to onNextTrack,
            "⏹ 정지" to onStopSong
        ).forEachIndexed { idx, (label, action) ->
            val highlight = idx == 1 && isPlaying
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clickable { action() },
                shape = RoundedCornerShape(12.dp),
                color = if (highlight) PleosActiveCardBg else PleosPanelWhite,
                border = BorderStroke(
                    width = if (highlight) 1.5.dp else 1.dp,
                    color = if (highlight) PleosActiveCardBorder else PleosBorderSubtle
                ),
                shadowElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        text = label,
                        color = if (highlight) PleosActiveCardBorder else PleosTextDark,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }

    // 2A. [선곡·유튜브] 전용 재생 구간 탐색(Seek) 컨트롤 바 (터치 & 좌우 드래그 시 차량/스마트폰 동시 이동)
    val activeRemoteTrack = playlist.firstOrNull { it.id == currentTrackId } ?: playlist.firstOrNull()
    val remoteSafeDuration = if (durationSec > 0) {
        durationSec
    } else {
        (activeRemoteTrack?.durationSec ?: 30).coerceAtLeast(1)
    }
    var remotePreviewSec by remember(currentTrackId) { mutableStateOf<Int?>(null) }
    val shownRemoteSec = remotePreviewSec ?: progressSec

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = PleosPanelWhite,
        border = BorderStroke(1.dp, PleosBorderSubtle),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (remotePreviewSec != null) "⏩ 구간 이동 탐색 중" else "구간 탐색 (터치 · 드래그)",
                    color = if (remotePreviewSec != null) PleosBlue else PleosTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = if (activeRemoteTrack == null) {
                        "00:00 / 00:00"
                    } else {
                        "${formatTrackMmSs(shownRemoteSec)} / ${formatTrackMmSs(remoteSafeDuration)}"
                    },
                    color = PleosActiveCardBorder,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }

            PleosSeekableProgressBar(
                progressSec = progressSec,
                durationSec = remoteSafeDuration,
                isPlaying = isPlaying,
                enabled = activeRemoteTrack != null,
                trackColor = Color(0xFFDBEAFE),
                activeGradient = listOf(PleosActiveCardBorder, PleosBlue, PleosPurple),
                thumbColor = PleosBlue,
                onPreviewSecChange = { remotePreviewSec = it },
                onSeekTo = onSeekTo
            )
        }
    }

    // 2B. 재생 컨트롤 줄 바로 아래 [🏆 점수 표시 켜짐 / 꺼짐] 원터치 토글
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clickable { onToggleScoreMode() },
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.2.dp,
            color = if (isScoreModeEnabled) Color(0xFFFBBF24) else Color(0xFF94A3B8)
        ),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isScoreModeEnabled) {
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF1D4ED8), Color(0xFF2563EB), Color(0xFFD97706))
                        )
                    } else {
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF64748B), Color(0xFF475569))
                        )
                    }
                )
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isScoreModeEnabled) "🏆 점수 표시 켜짐" else "🏆 점수 표시 꺼짐",
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = if (isScoreModeEnabled) "차량 동기화 ON" else "터치하여 켜기",
                color = Color.White.copy(alpha = 0.90f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }

    // 3. [선곡·유튜브] 탭 다이어트: 평소에는 플레이리스트가 한눈에 보이도록 접이식(Collapsible) 토글 버튼 1개만 배치!
    var showQuickMixer by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clickable { showQuickMixer = !showQuickMixer },
        shape = RoundedCornerShape(14.dp),
        color = if (showQuickMixer) PleosActiveCardBg else PleosPanelWhite,
        border = BorderStroke(
            width = 1.dp,
            color = if (showQuickMixer) PleosActiveCardBorder else PleosBorderSubtle
        ),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showQuickMixer) "🎛️ 음량 및 빠르기 조절 닫기" else "🎛️ 음량 및 빠르기 조절 열기",
                color = if (showQuickMixer) PleosActiveCardBorder else PleosTextDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = "반주 ${backingVolume}% · 마이크 ${micVolume}% · 빠르기 ${formatPlaybackRateLabel(mrPlaybackRate)} ${if (showQuickMixer) "▴" else "▾"}",
                color = PleosBlue,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }

    if (showQuickMixer) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "음량 및 빠르기 조절",
                    color = PleosTextDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
                Surface(
                    modifier = Modifier
                        .height(28.dp)
                        .clickable { onResetAllAudio() },
                    shape = RoundedCornerShape(8.dp),
                    color = PleosActiveCardBg,
                    border = BorderStroke(1.dp, PleosActiveCardBorder.copy(alpha = 0.45f)),
                    shadowElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "전체 초기화",
                            color = PleosActiveCardBorder,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PleosVolumeGridCard(
                    modifier = Modifier.weight(1f),
                    label = "반주 음량",
                    valueText = "${backingVolume}%",
                    onMinus = { onBackingVolumeChange(backingVolume - 10) },
                    onPlus = { onBackingVolumeChange(backingVolume + 10) }
                )
                PleosVolumeGridCard(
                    modifier = Modifier.weight(1f),
                    label = "마이크 음량",
                    valueText = "${micVolume}%",
                    onMinus = { onMicVolumeChange(micVolume - 5) },
                    onPlus = { onMicVolumeChange(micVolume + 5) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PleosVolumeGridCard(
                    modifier = Modifier.weight(1f),
                    label = "가이드 음량",
                    valueText = "${guideVolume}%",
                    onMinus = { onGuideVolumeChange(guideVolume - 5) },
                    onPlus = { onGuideVolumeChange(guideVolume + 5) }
                )
                PleosVolumeGridCard(
                    modifier = Modifier.weight(1f),
                    label = "에코 공간감",
                    valueText = "${echoLevel}%",
                    onMinus = { onEchoLevelChange(echoLevel - 5) },
                    onPlus = { onEchoLevelChange(echoLevel + 5) }
                )
            }

            PleosCompactValueStepperRow(
                label = "빠르기",
                valueText = formatPlaybackRateLabel(mrPlaybackRate),
                onMinus = { onPlaybackRateChange(mrPlaybackRate - 0.10f) },
                onPlus = { onPlaybackRateChange(mrPlaybackRate + 0.10f) }
            )

            PleosCompactValueStepperRow(
                label = "싱크 조절",
                valueText = formatSyncOffsetLabel(syncOffsetMs),
                onMinus = { onSyncOffsetChange(syncOffsetMs - 100) },
                onPlus = { onSyncOffsetChange(syncOffsetMs + 100) }
            )
        }
    }

    Text(
        text = "곡을 터치하면 차량과 스마트폰에서 즉시 동기화 재생됩니다",
        color = PleosBlue,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        softWrap = true
    )

    // 4. 통합 예약 곡 리스트 (유튜브 곡 + 내장 수록곡 통합, 순서 변경 ▲/▼ & 드래그 & 삭제 ✕ 지원)
    if (playlist.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = PleosPanelWhite,
            border = BorderStroke(1.dp, PleosBorderSubtle),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "예약된 곡이 없습니다.",
                    color = PleosTextDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "예약된 곡이 없습니다. 상단 검색창이나 스마트폰 앱에서 유튜브 곡을 추가해 보세요.",
                    color = PleosTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    val rowStepPx = 115f
    playlist.forEachIndexed { index, track ->
        val isCurrent = track.id == currentTrackId
        val isCurrentPlaying = isCurrent && isPlaying
        val isDragged = draggedTrackId == track.id
        val trackDuration = if (isCurrent && durationSec > 0) durationSec else track.durationSec.coerceAtLeast(1)
        val trackProgressRatio = if (isCurrent) {
            (progressSec.toFloat() / trackDuration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (isDragged) 10f else 0f)
                .graphicsLayer {
                    translationY = if (isDragged) dragOffsetY else 0f
                    scaleX = if (isDragged) 1.02f else 1f
                    scaleY = if (isDragged) 1.02f else 1f
                }
                .pointerInput(track.id, playlist.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            draggedTrackId = track.id
                            dragOffsetY = 0f
                        },
                        onDragEnd = {
                            draggedTrackId = null
                            dragOffsetY = 0f
                        },
                        onDragCancel = {
                            draggedTrackId = null
                            dragOffsetY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount.y
                            val curIndex = playlist.indexOfFirst { it.id == track.id }
                            if (curIndex != -1) {
                                if (dragOffsetY > rowStepPx && curIndex < playlist.lastIndex) {
                                    onMoveTrack(curIndex, curIndex + 1)
                                    dragOffsetY -= rowStepPx
                                } else if (dragOffsetY < -rowStepPx && curIndex > 0) {
                                    onMoveTrack(curIndex, curIndex - 1)
                                    dragOffsetY += rowStepPx
                                }
                            }
                        }
                    )
                }
                .clickable { onPlayOrToggleTrack(track) },
            shape = RoundedCornerShape(18.dp),
            color = if (isCurrent) PleosActiveCardBg else PleosPanelWhite,
            border = BorderStroke(
                width = if (isDragged) 2.dp else if (isCurrent) 1.5.dp else 1.dp,
                color = if (isDragged) PleosGreen else if (isCurrent) PleosActiveCardBorder else PleosBorderSubtle
            ),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 순서 변경 버튼 (▲ / ▼) 및 트랙 번호 (슬림 18.dp)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            modifier = Modifier.width(18.dp)
                        ) {
                            Text(
                                text = "▲",
                                color = if (index > 0) PleosTextMuted else PleosBorderSubtle,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.clickable(enabled = index > 0) {
                                    onMoveTrack(index, index - 1)
                                }
                            )
                            Text(
                                text = "${index + 1}",
                                color = if (isCurrent) PleosActiveCardBorder else PleosTextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "▼",
                                color = if (index < playlist.lastIndex) PleosTextMuted else PleosBorderSubtle,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.clickable(enabled = index < playlist.lastIndex) {
                                    onMoveTrack(index, index + 1)
                                }
                            )
                        }

                        // 컴팩트 썸네일 (52.dp x 38.dp)
                        if (track.videoId.isNotBlank()) {
                            YouTubeThumbnailBox(
                                videoId = track.videoId,
                                durationLabel = "",
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(38.dp)
                            )
                        } else {
                            Surface(
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(38.dp),
                                shape = RoundedCornerShape(9.dp),
                                color = PleosStageNavy,
                                shadowElevation = 0.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "YouTube",
                                        color = PleosCyan,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // 곡 제목과 아티스트 영역 (충분한 가로 폭 확보로 15~25자 이상 시원하게 노출)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = track.title,
                                color = PleosTextDark,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (isCurrentPlaying) {
                                    MicAnimatedEqualizerBars(
                                        isPlaying = true,
                                        barColor = PleosActiveCardBorder,
                                        modifier = Modifier
                                            .width(14.dp)
                                            .height(12.dp)
                                    )
                                }

                                // DEV 모드일 때 내장 데모 곡에 간결한 DEV 뱃지 부착 (10.sp, 가로 6.dp, 세로 2.dp)
                                if (isDevMode && track.isDevSample) {
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = PleosDevRed,
                                        shadowElevation = 0.dp
                                    ) {
                                        Text(
                                            text = "DEV",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "${track.artist} • ${track.durationLabel.ifBlank { formatTrackMmSs(trackDuration) }}",
                                    color = if (isCurrent) PleosActiveCardBorder else PleosTextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 우측 액션 영역: 깔끔한 40.dp 원형 재생/일시정지 아이콘 버튼 + 26.dp 삭제 버튼
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { onPlayOrToggleTrack(track) },
                            shape = CircleShape,
                            color = when {
                                isCurrentPlaying -> PleosActiveCardBorder
                                isCurrent -> PleosBlue
                                else -> PleosTrackBg
                            },
                            shadowElevation = 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = when {
                                        isDragged -> "↕"
                                        isCurrentPlaying -> "⏸"
                                        else -> "▶"
                                    },
                                    color = if (isCurrent) Color.White else PleosTextDark,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (playlist.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clickable { onDeleteTrack(track.id) },
                                shape = CircleShape,
                                color = PleosTileGray,
                                shadowElevation = 0.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "✕",
                                        color = PleosTextMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                // 현재 선택/재생 중인 곡 카드 하단에 터치/드래그 탐색(Seek) 진행바 및 진행 시간 표시
                if (isCurrent) {
                    var cardPreviewSec by remember(track.id) { mutableStateOf<Int?>(null) }
                    val shownCardSec = cardPreviewSec ?: progressSec
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        PleosSeekableProgressBar(
                            progressSec = progressSec,
                            durationSec = trackDuration,
                            isPlaying = isCurrentPlaying,
                            enabled = true,
                            trackColor = Color(0xFFDBEAFE),
                            activeGradient = listOf(PleosActiveCardBorder, PleosBlue, PleosPurple),
                            thumbColor = PleosActiveCardBorder,
                            onPreviewSecChange = { cardPreviewSec = it },
                            onSeekTo = onSeekTo
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    cardPreviewSec != null -> "⏩ 원하는 지점으로 이동 중..."
                                    isCurrentPlaying -> "♪ 차량 메인 스테이지에서 실시간 재생 중"
                                    else -> "일시정지됨 • 바를 터치해 위치 이동 가능"
                                },
                                color = PleosActiveCardBorder,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${formatTrackMmSs(shownCardSec)} / ${formatTrackMmSs(trackDuration)}",
                                color = PleosActiveCardBorder,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // 5. 라이브 무대 효과음 (👏 박수, 🎉 환호, 🥁 탬버린 — 말줄임표 잘림 제로 3열 전용 버튼)
    Text(
        text = "라이브 무대 효과음",
        color = PleosTextDark,
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        softWrap = false
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            Triple("APPLAUSE", "👏 박수", "터치 재생"),
            Triple("CHEER", "🎉 환호", "터치 재생"),
            Triple("TAMBOURINE", "🥁 탬버린", "터치 재생")
        ).forEach { (sfxKey, label, subLabel) ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clickable { onTriggerSfx(sfxKey) },
                shape = RoundedCornerShape(16.dp),
                color = PleosActiveCardBg,
                border = BorderStroke(1.5.dp, PleosActiveCardBorder),
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = label,
                        color = PleosTextDark,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subLabel,
                        color = PleosActiveCardBorder,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ============================================================================
// TAB 3: CONSUMER-FRIENDLY SMART LINK PAIRING (4-DIGIT PIN AUTO-CONNECT)
// ============================================================================
@Composable
private fun PleosCarConnectTab(
    pin: String,
    connectionMessage: String,
    isPaired: Boolean,
    isConnecting: Boolean = false,
    totalLatencyMs: Int = 0,
    transportModeLabel: String = "초저지연 직결",
    hotspotIp: String = "",
    discoveredCarIp: String = "",
    isDevMode: Boolean = false,
    onPinDigitClick: (String) -> Unit,
    onPinBackspace: () -> Unit,
    onPinReset: () -> Unit,
    onConnectClick: () -> Unit,
    onManualIpConnect: (String) -> Unit = {}
) {
    var manualCarIpInput by remember(discoveredCarIp) { mutableStateOf(discoveredCarIp) }
    val radarTransition = rememberInfiniteTransition(label = "radarScan")
    val radarPulse by radarTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarPulse"
    )

    Text(
        text = "차량 스마트 링크 연결",
        color = PleosTextDark,
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        softWrap = false
    )

    // 1. 차량 연결 상태 카드 (실제 차량 패킷 수신 시에만 ● 연결됨 표시)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isPaired) PleosActiveCardBg else PleosPanelWhite,
        border = BorderStroke(
            width = 1.5.dp,
            color = when {
                isPaired -> PleosGreen
                isConnecting -> PleosBlue
                else -> PleosBorderSubtle
            }
        ),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(50.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val maxR = size.minDimension / 2f
                    val baseColor = when {
                        isPaired -> PleosGreen
                        isConnecting -> PleosBlue
                        else -> Color(0xFF94A3B8)
                    }
                    if (isPaired || isConnecting) {
                        for (idx in 0..1) {
                            val p = (radarPulse + idx * 0.5f) % 1f
                            drawCircle(
                                color = baseColor.copy(alpha = (1f - p) * 0.35f),
                                radius = maxR * (0.35f + p * 0.65f),
                                center = c,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                    drawCircle(
                        color = baseColor.copy(alpha = 0.16f),
                        radius = maxR * 0.52f,
                        center = c
                    )
                }
                Icon(
                    imageVector = MicMaterialIcons.SmartLink,
                    contentDescription = "차량 탐색",
                    tint = when {
                        isPaired -> PleosGreen
                        isConnecting -> PleosBlue
                        else -> PleosTextMuted
                    },
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = when {
                        isPaired -> "● 연결됨 · ⚡ ${totalLatencyMs}ms"
                        isConnecting -> "◐ 차량과 연결하는 중..."
                        else -> "○ 차량 연결 대기 중"
                    },
                    color = when {
                        isPaired -> PleosGreen
                        isConnecting -> PleosBlue
                        else -> PleosTextDark
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    softWrap = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = when {
                        isPaired -> "$transportModeLabel 활성 · 마이크와 선곡 리모컨이 연결되었습니다 (PIN $pin)"
                        isConnecting -> "차량 화면의 PIN($pin) 또는 동일 네트워크로 자동 연결 중입니다..."
                        else -> "차량 화면 QR 스캔, 4자리 PIN 번호, 또는 동일 네트워크로 자동 연결됩니다."
                    },
                    color = when {
                        isPaired -> PleosGreen
                        isConnecting -> PleosBlue
                        else -> PleosTextMuted
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    softWrap = true
                )
            }
        }
    }

    // [상단 배치] 스마트폰 핫스팟 주소 안내 및 차량 IP 직접 연결 카드 (PROD Release에서도 상시 노출)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = PleosPanelWhite,
        border = BorderStroke(1.2.dp, PleosBlue.copy(alpha = 0.45f)),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IP 직접 연결 · 핫스팟 직결",
                    color = PleosTextDark,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = if (isPaired) transportModeLabel else "영구 기억 · 0.5초 직결",
                    color = PleosBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (hotspotIp.isNotBlank()) {
                val octets = hotspotIp.split(".")
                val shortCode = if (octets.size == 4) "${octets[2]}.${octets[3]}" else hotspotIp
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, PleosBlue.copy(alpha = 0.25f)),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "내 스마트폰 IP: $hotspotIp",
                            color = PleosBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "보조 번호: $shortCode",
                            color = PleosTextDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, PleosBorderSubtle),
                    shadowElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (manualCarIpInput.isEmpty()) {
                            Text(
                                text = "차량 연결 IP 입력 (예: 192.168.0.15)",
                                color = PleosTextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = manualCarIpInput,
                            onValueChange = { manualCarIpInput = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = PleosTextDark,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(PleosBlue),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .height(42.dp)
                        .clickable { onManualIpConnect(manualCarIpInput.trim()) },
                    shape = RoundedCornerShape(12.dp),
                    color = PleosBlue,
                    shadowElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "즉시 연결",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }

    // 2. 차량 화면의 4자리 PIN 번호 입력 패드
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "차량 화면의 4자리 PIN 번호",
            color = PleosTextDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = "PIN 입력",
            color = PleosActiveCardBorder,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }

    // 4-Digit PIN Display Boxes
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (i in 0 until 4) {
            val ch = pin.getOrNull(i)?.toString() ?: ""
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                color = PleosPanelWhite,
                border = BorderStroke(
                    width = 2.dp,
                    color = if (ch.isNotEmpty()) PleosBlue else PleosBorderSubtle
                ),
                shadowElevation = 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = ch,
                        color = PleosBlue,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }

    // Numeric Keypad Grid
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("초기화", "0", "지움")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable {
                                when (key) {
                                    "지움" -> onPinBackspace()
                                    "초기화" -> onPinReset()
                                    else -> onPinDigitClick(key)
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = PleosPanelWhite,
                        border = BorderStroke(1.dp, PleosBorderSubtle),
                        shadowElevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = key,
                                color = PleosTextDark,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    // Primary One-Touch Auto-Connect / Reconnect Button
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable { onConnectClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isPaired) PleosGreen else PleosBlue,
        shadowElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = when {
                    isPaired -> "🔄 차량 자동 재연결"
                    isConnecting -> "◐ 차량 연결 중..."
                    else -> "⚡ 원터치 차량 자동 연결"
                },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    val cleanStatusMessage = when {
        isPaired -> "● 차량 노래방 연결됨 ($transportModeLabel · PIN $pin · ⚡ ${totalLatencyMs}ms)"
        isConnecting -> "◐ 차량과 연결하는 중..."
        else -> "○ 연결 대기 중 · 차량 카메라 QR 스캔 또는 자동 연결 지원"
    }

    Text(
        text = cleanStatusMessage,
        color = when {
            isPaired -> PleosGreen
            isConnecting -> PleosBlue
            else -> PleosTextMuted
        },
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        softWrap = true,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 모던 캡슐형 검색/링크 입력 바 (투박한 OutlinedTextField 테두리 제거, #F1F5F9 배경, RoundedCornerShape(16.dp), 좌측 돋보기 아이콘 내장)
 */
@Composable
private fun PleosMobileCapsuleSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF1F5F9),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "🔍",
                fontSize = 14.sp,
                maxLines = 1,
                softWrap = false
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = PleosTextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = PleosTextDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    cursorBrush = SolidColor(PleosBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ============================================================================
// SHARED MOBILE DESIGN SYSTEM COMPONENTS (ZERO '...' TRUNCATION)
// ============================================================================
@Composable
private fun PleosMobileQuickTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    active: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(94.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = if (active) PleosActiveCardBg else PleosPanelWhite,
        border = if (active) {
            BorderStroke(1.5.dp, PleosActiveCardBorder)
        } else {
            BorderStroke(1.dp, PleosBorderSubtle)
        },
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Icon on Left + Status Dot on Right (frees 100% width below for Title & Subtitle!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = if (active) PleosActiveCardBorder else PleosTileGray,
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = if (active) Color.White else PleosTextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (active) PleosActiveCardBorder else Color(0xFFC5C9D2),
                            shape = CircleShape
                        )
                )
            }

            // Bottom Column: Full-width Title & Subtitle (never blocked by right-side icon!)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = PleosTextDark,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = subtitle,
                    color = if (active) PleosActiveCardBorder else PleosTextMuted,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    softWrap = true
                )
            }
        }
    }
}

/**
 * 상단 핵심 4대 볼륨 2x2 그리드 카드:
 * - 상단: 항목명 (`반주 음량`, `마이크 음량`, `가이드 음량`, `에코 공간감`)
 * - 하단: `[-]`  `[ 85% (굵고 큰 파란 글씨) ]`  `[+]` 정중앙 배치 (절대 `...`으로 잘리지 않음)
 */
@Composable
private fun PleosVolumeGridCard(
    modifier: Modifier = Modifier,
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Surface(
        modifier = modifier.height(82.dp),
        shape = RoundedCornerShape(16.dp),
        color = PleosPanelWhite,
        border = BorderStroke(1.dp, PleosBorderSubtle),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = PleosTextDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .size(width = 36.dp, height = 32.dp)
                        .clickable { onMinus() },
                    shape = RoundedCornerShape(10.dp),
                    color = PleosTileGray,
                    border = BorderStroke(1.dp, PleosBorderSubtle),
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "−",
                            color = PleosTextDark,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Text(
                    text = valueText,
                    color = PleosBlue,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )

                Surface(
                    modifier = Modifier
                        .size(width = 36.dp, height = 32.dp)
                        .clickable { onPlus() },
                    shape = RoundedCornerShape(10.dp),
                    color = PleosTileGray,
                    border = BorderStroke(1.dp, PleosBorderSubtle),
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "+",
                            color = PleosTextDark,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 하단 미세 조절 3종 (`반주 속도`, `싱크 조절`, `키 조절`) 컴팩트 스테퍼:
 * - `[항목명(좌측)]` + `[-]  [ 1.00x / 0ms / 0 키 (굵고 큰 파란 글씨) ]  [+]`
 */
@Composable
private fun PleosCompactValueStepperRow(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(16.dp),
        color = PleosPanelWhite,
        border = BorderStroke(1.dp, PleosBorderSubtle),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = PleosTextDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(width = 38.dp, height = 34.dp)
                        .clickable { onMinus() },
                    shape = RoundedCornerShape(10.dp),
                    color = PleosTileGray,
                    border = BorderStroke(1.dp, PleosBorderSubtle),
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "−",
                            color = PleosTextDark,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Box(
                    modifier = Modifier.width(74.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = valueText,
                        color = PleosBlue,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(width = 38.dp, height = 34.dp)
                        .clickable { onPlus() },
                    shape = RoundedCornerShape(10.dp),
                    color = PleosTileGray,
                    border = BorderStroke(1.dp, PleosBorderSubtle),
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "+",
                            color = PleosTextDark,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * [라이브 마이크] 탭 2x2 컨트롤 영역용 [🏆 점수 표시 켜짐 / 꺼짐] 골드/블루 · 슬레이트 그레이 토글 타일
 */
@Composable
private fun PleosMobileScoreQuickTile(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    currentScore: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(94.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) Color(0xFFFFFBEB) else Color(0xFFF1F5F9),
        border = BorderStroke(
            width = if (enabled) 1.5.dp else 1.dp,
            color = if (enabled) Color(0xFFF59E0B) else Color(0xFF94A3B8)
        ),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = Color.Transparent,
                    shadowElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (enabled) {
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF2563EB), Color(0xFFD97706))
                                    )
                                } else {
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF64748B), Color(0xFF475569))
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🏆",
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (enabled) Color(0xFF2563EB) else Color(0xFF64748B)
                ) {
                    Text(
                        text = if (enabled) "ON" else "OFF",
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (enabled) "🏆 점수 표시 켜짐" else "🏆 점수 표시 꺼짐",
                    color = if (enabled) Color(0xFF92400E) else Color(0xFF475569),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (enabled) "최근 ${currentScore}점 • 차량 동기화" else "터치하여 점수 켜기",
                    color = if (enabled) Color(0xFF1D4ED8) else Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 곡 종료 시 스마트폰 화면 중앙에 5초간 표시되는 다이내믹 점수 발표 모달
 * - 0.0초 ~ 1.2초: 75점에서 최종 점수까지 롤링 카운트업 + 스프링 바운스
 * - 1.2초 ~ 5.0초: 골드/네온 그라데이션 점수 + 등급 배지 + 칭찬 멘트 + 파티클 + 3대 게이지
 * - 5.0초 뒤 자동 페이드아웃 종료 (터치 또는 [닫기]로 즉시 닫기 가능)
 */
@Composable
private fun MicKaraokeScoreModal(
    result: KaraokeScoreResultState,
    fontFamily: FontFamily,
    onDismiss: () -> Unit
) {
    var targetScore by remember(result.resultId) { mutableIntStateOf(75) }
    var isLockedIn by remember(result.resultId) { mutableStateOf(false) }
    var elapsedMs by remember(result.resultId) { mutableIntStateOf(0) }

    LaunchedEffect(result.resultId) {
        targetScore = 75
        isLockedIn = false
        elapsedMs = 0
        val startMark = TimeSource.Monotonic.markNow()
        delay(40L)
        if (result.score <= 80) {
            targetScore = 88
            delay(500L)
        }
        targetScore = result.score
        while (true) {
            val nowElapsed = startMark.elapsedNow().inWholeMilliseconds.toInt().coerceAtMost(5000)
            elapsedMs = nowElapsed
            if (nowElapsed >= 1150 && !isLockedIn) {
                isLockedIn = true
            }
            if (nowElapsed >= 5000) break
            delay(32L)
        }
    }

    val animatedScore by animateIntAsState(
        targetValue = targetScore,
        animationSpec = tween(durationMillis = 1120, easing = FastOutSlowInEasing),
        label = "micKaraokeRollingScore"
    )

    val scoreScale by animateFloatAsState(
        targetValue = when {
            elapsedMs > 4680 -> 0.92f
            isLockedIn -> 1.07f
            else -> 0.90f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "micKaraokeScoreScale"
    )

    val modalAlpha by animateFloatAsState(
        targetValue = if (elapsedMs >= 4680) 0f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "micKaraokeModalAlpha"
    )

    val gaugeReveal by animateFloatAsState(
        targetValue = if (isLockedIn) 1f else 0.16f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "micKaraokeGaugeReveal"
    )

    val particleTransition = rememberInfiniteTransition(label = "micScoreParticles")
    val particlePhase by particleTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "micParticlePhase"
    )

    val isGoldCrown = result.score >= 98
    val isSuperStar = result.score in 90..97
    val accentPrimary = when {
        isGoldCrown -> Color(0xFFFBBF24)
        isSuperStar -> Color(0xFF38BDF8)
        result.score >= 82 -> Color(0xFFA855F7)
        else -> Color(0xFF34D399)
    }
    val accentSecondary = when {
        isGoldCrown -> Color(0xFFFDE047)
        isSuperStar -> Color(0xFFFBBF24)
        result.score >= 82 -> Color(0xFF38BDF8)
        else -> Color(0xFF60A5FA)
    }

    val remainingRatio = (1f - (elapsedMs / 5000f)).coerceIn(0f, 1f)
    val remainingTenths = ((5000 - elapsedMs).coerceAtLeast(0)) / 100
    val remainingSecStr = "${remainingTenths / 10}.${remainingTenths % 10}s"

    Box(
        modifier = Modifier
            .zIndex(30f)
            .fillMaxSize()
            .alpha(modalAlpha)
            .background(Color(0xD9080D1A))
            .clickable { onDismiss() }
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.8.dp, accentPrimary.copy(alpha = 0.78f)),
            shadowElevation = 10.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val cx = w * 0.5f
                    val cy = h * 0.42f

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentPrimary.copy(alpha = if (isLockedIn) 0.28f else 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = w * 0.48f
                        ),
                        radius = w * 0.48f,
                        center = Offset(cx, cy)
                    )

                    val pCount = if (isLockedIn) 18 else 8
                    for (i in 0 until pCount) {
                        val angle = (i * (2.0 * PI / pCount) + particlePhase * 0.7).toFloat()
                        val dist = if (isLockedIn) {
                            0.22f + 0.24f * ((i % 4) / 3f) + 0.03f * sin(particlePhase + i)
                        } else {
                            0.16f
                        }
                        val px = cx + cos(angle) * (w * dist)
                        val py = cy + sin(angle) * (h * (dist * 0.75f))
                        drawCircle(
                            color = (if (i % 2 == 0) accentPrimary else accentSecondary)
                                .copy(alpha = if (isLockedIn) 0.80f else 0.35f),
                            radius = (if (i % 3 == 0) 4.5.dp else 2.8.dp).toPx(),
                            center = Offset(px, py)
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = accentPrimary.copy(alpha = 0.20f),
                            border = BorderStroke(1.dp, accentPrimary.copy(alpha = 0.75f))
                        ) {
                            Text(
                                text = if (isLockedIn) "🏆 ${result.badge}" else "🥁 점수 집계 중...",
                                color = accentSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                            )
                        }

                        Surface(
                            modifier = Modifier.clickable { onDismiss() },
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f))
                        ) {
                            Text(
                                text = "✕ 닫기 ($remainingSecStr)",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    if (result.trackTitle.isNotBlank()) {
                        Text(
                            text = result.trackTitle,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.graphicsLayer {
                            scaleX = scoreScale
                            scaleY = scoreScale
                        },
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "$animatedScore",
                            style = TextStyle(
                                fontFamily = fontFamily,
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFFFFFBEB), accentSecondary, accentPrimary)
                                ),
                                fontSize = 68.sp,
                                fontWeight = FontWeight.Black,
                                shadow = Shadow(
                                    color = accentPrimary.copy(alpha = 0.65f),
                                    offset = Offset(0f, 4f),
                                    blurRadius = 20f
                                )
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "점",
                            color = accentSecondary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    Text(
                        text = if (isLockedIn) result.title else "보컬 성량 · 음정 에너지 분석 완료!",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = result.comment,
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MicScoreDetailGaugeRow(
                            label = "음정 정확도",
                            value = result.pitchAccuracy,
                            revealFraction = gaugeReveal,
                            barColor = Color(0xFF38BDF8)
                        )
                        MicScoreDetailGaugeRow(
                            label = "성량 파워",
                            value = result.vocalPower,
                            revealFraction = gaugeReveal,
                            barColor = Color(0xFFFBBF24)
                        )
                        MicScoreDetailGaugeRow(
                            label = "무대 매너",
                            value = result.stageManner,
                            revealFraction = gaugeReveal,
                            barColor = Color(0xFFA855F7)
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (result.willAdvanceNext) {
                                "5초 후 자동으로 닫히며 다음 곡으로 이어집니다"
                            } else {
                                "화면을 터치하면 즉시 닫힙니다"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(remainingRatio)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(accentPrimary, accentSecondary)
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MicScoreDetailGaugeRow(
    label: String,
    value: Int,
    revealFraction: Float,
    barColor: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color(0xFFE2E8F0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${value}%",
                color = barColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.14f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(((value / 100f) * revealFraction).coerceIn(0.05f, 1f))
                    .clip(RoundedCornerShape(50))
                    .background(barColor)
            )
        }
    }
}
