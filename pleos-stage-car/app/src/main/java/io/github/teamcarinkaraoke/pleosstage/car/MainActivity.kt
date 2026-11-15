package io.github.teamcarinkaraoke.pleosstage.car

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.teamcarinkaraoke.pleosstage.car.audio.CarAudioEngineController
import io.github.teamcarinkaraoke.pleosstage.car.audio.CarWetReverbEngineHost
import io.github.teamcarinkaraoke.pleosstage.car.audio.NativeVocalAudioEngine
import io.github.teamcarinkaraoke.pleosstage.car.audio.UnifiedStageTrack
import io.github.teamcarinkaraoke.pleosstage.car.audio.YouTubeMrController
import io.github.teamcarinkaraoke.pleosstage.car.audio.YouTubeMrPlayerHost
import io.github.teamcarinkaraoke.pleosstage.car.audio.YouTubeThumbnailImage
import io.github.teamcarinkaraoke.pleosstage.car.audio.YouTubeUrlParser
import io.github.teamcarinkaraoke.pleosstage.car.model.LyricLine
import io.github.teamcarinkaraoke.pleosstage.car.model.QrCodeGenerator
import io.github.teamcarinkaraoke.pleosstage.car.model.StageSong
import io.github.teamcarinkaraoke.pleosstage.car.model.StageSongCatalog
import io.github.teamcarinkaraoke.pleosstage.car.sdk.GearPosition
import io.github.teamcarinkaraoke.pleosstage.car.sdk.PleosVehicleManager
import io.github.teamcarinkaraoke.pleosstage.car.sdk.VehicleDigitalTwinState
import io.github.teamcarinkaraoke.pleosstage.car.server.PleosEmbeddedSignalingServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================================
// Genuine Hyundai Pleos Connect Infotainment OS Design System Palette
// ============================================================================
val PleosCanvasBg = Color(0xFFF2F4F8)         // Soft Alabaster Outer OS Canvas
val PleosPanelWhite = Color(0xFFFFFFFF)       // Pure White Panel Surface
val PleosTileGray = Color(0xFFEDEDEF)         // Inactive Quick-Setting Tile Gray
val PleosTileActiveWhite = Color(0xFFFFFFFF)  // Active Quick-Setting Tile White
val PleosTrackBg = Color(0xFFE9EAEE)          // Segmented & Slider Track Background
val PleosSliderFillGray = Color(0xFFC8CBD2)   // Inactive Slider Track Fill
val PleosBorderSubtle = Color(0xFFE3E6EC)     // Hairline Divider / Card Border
val PleosBlue = Color(0xFF2B95F6)             // Pleos Active Sidebar Selection Blue
val PleosGreen = Color(0xFF1BB55C)            // Pleos Active Indicator Bar & READY Green
val PleosAmber = Color(0xFFF59E0B)            // Safety Interlock Warning Amber
val PleosDevRed = Color(0xFFDC2626)           // DEV Mode Highlight Badge Red
val PleosDevRedSoft = Color(0xFFFEF2F2)       // DEV Mode Soft Surface Tint
val PleosStagePurple = Color(0xFF7C4DFF)      // Ambient / Stage Accent Purple
val PleosStageRose = Color(0xFFFF2E93)        // Climax High-Note Rose
val PleosStageNavyStart = Color(0xFF0F172A)   // Dark Concert Hall Navy Top (#0F172A)
val PleosStageCharcoalEnd = Color(0xFF1E293B) // Dark Concert Hall Charcoal Bottom (#1E293B)
val PleosCyanBlue = Color(0xFF38BDF8)         // Sub Guide Text Cyan Blue (#38BDF8)
val PleosSlatePreview = Color(0xFF94A3B8)     // Next Lyric Preview Slate Gray (#94A3B8)
val PleosTextDark = Color(0xFF14161A)         // Primary Crisp Charcoal Text
val PleosTextMuted = Color(0xFF727885)        // Secondary Muted Label Text

// ============================================================================
// Pretendard Typography System (v1.3.9)
// ============================================================================
val PretendardFontFamily = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
    Font(R.font.pretendard_extrabold, FontWeight.Black)
)

private val BaseTypography = Typography()
val PretendardTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = PretendardFontFamily, letterSpacing = (-0.02).em),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = PretendardFontFamily, letterSpacing = (-0.02).em),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = PretendardFontFamily, letterSpacing = (-0.02).em),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = PretendardFontFamily, letterSpacing = (-0.015).em),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = PretendardFontFamily, letterSpacing = (-0.015).em),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = PretendardFontFamily, letterSpacing = (-0.015).em),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = PretendardFontFamily, letterSpacing = (-0.015).em),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = PretendardFontFamily, letterSpacing = (-0.015).em),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = PretendardFontFamily, letterSpacing = (-0.01).em),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = PretendardFontFamily, letterSpacing = (-0.01).em),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = PretendardFontFamily, letterSpacing = (-0.01).em),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = PretendardFontFamily, letterSpacing = (-0.01).em),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = PretendardFontFamily, letterSpacing = (-0.01).em),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = PretendardFontFamily, letterSpacing = (-0.01).em),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = PretendardFontFamily, letterSpacing = (-0.01).em)
)

// ============================================================================
// Distinct Material Vector Icons for Sidebar Menus & Stage Buttons
// ============================================================================
object PleosMaterialIcons {
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

    val Car: ImageVector by lazy {
        ImageVector.Builder(
            name = "Car",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(18.92f, 6.01f)
                curveTo(18.72f, 5.42f, 18.16f, 5f, 17.5f, 5f)
                horizontalLineTo(6.5f)
                curveTo(5.84f, 5f, 5.29f, 5.42f, 5.08f, 6.01f)
                lineTo(3f, 12f)
                verticalLineTo(20f)
                curveTo(3f, 20.55f, 3.45f, 21f, 4f, 21f)
                horizontalLineTo(5f)
                curveTo(5.55f, 21f, 6f, 20.55f, 6f, 20f)
                verticalLineTo(19f)
                horizontalLineTo(18f)
                verticalLineTo(20f)
                curveTo(18f, 20.55f, 18.45f, 21f, 19f, 21f)
                horizontalLineTo(20f)
                curveTo(20.55f, 21f, 21f, 20.55f, 21f, 20f)
                verticalLineTo(12f)
                lineTo(18.92f, 6.01f)
                close()
                moveTo(6.5f, 16f)
                curveTo(5.67f, 16f, 5f, 15.33f, 5f, 14.5f)
                curveTo(5f, 13.67f, 5.67f, 13f, 6.5f, 13f)
                curveTo(7.33f, 13f, 8f, 13.67f, 8f, 14.5f)
                curveTo(8f, 15.33f, 7.33f, 16f, 6.5f, 16f)
                close()
                moveTo(17.5f, 16f)
                curveTo(16.67f, 16f, 16f, 15.33f, 16f, 14.5f)
                curveTo(16f, 13.67f, 16.67f, 13f, 17.5f, 13f)
                curveTo(18.33f, 13f, 19f, 13.67f, 19f, 14.5f)
                curveTo(19f, 15.33f, 18.33f, 16f, 17.5f, 16f)
                close()
                moveTo(5f, 11f)
                lineTo(6.5f, 6.5f)
                horizontalLineTo(17.5f)
                lineTo(19f, 11f)
                horizontalLineTo(5f)
                close()
            }
        }.build()
    }

    val QrLink: ImageVector by lazy {
        ImageVector.Builder(
            name = "QrLink",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 11f)
                horizontalLineTo(11f)
                verticalLineTo(3f)
                horizontalLineTo(3f)
                verticalLineTo(11f)
                close()
                moveTo(5f, 5f)
                horizontalLineTo(9f)
                verticalLineTo(9f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                close()
                moveTo(3f, 21f)
                horizontalLineTo(11f)
                verticalLineTo(13f)
                horizontalLineTo(3f)
                verticalLineTo(21f)
                close()
                moveTo(5f, 15f)
                horizontalLineTo(9f)
                verticalLineTo(19f)
                horizontalLineTo(5f)
                verticalLineTo(15f)
                close()
                moveTo(13f, 3f)
                verticalLineTo(11f)
                horizontalLineTo(21f)
                verticalLineTo(3f)
                horizontalLineTo(13f)
                close()
                moveTo(19f, 9f)
                horizontalLineTo(15f)
                verticalLineTo(5f)
                horizontalLineTo(19f)
                verticalLineTo(9f)
                close()
                moveTo(13f, 13f)
                horizontalLineTo(16f)
                verticalLineTo(16f)
                horizontalLineTo(13f)
                verticalLineTo(13f)
                close()
                moveTo(17f, 17f)
                horizontalLineTo(21f)
                verticalLineTo(21f)
                horizontalLineTo(17f)
                verticalLineTo(17f)
                close()
                moveTo(13f, 18f)
                horizontalLineTo(16f)
                verticalLineTo(21f)
                horizontalLineTo(13f)
                verticalLineTo(18f)
                close()
                moveTo(18f, 13f)
                horizontalLineTo(21f)
                verticalLineTo(16f)
                horizontalLineTo(18f)
                verticalLineTo(13f)
                close()
            }
        }.build()
    }

    val Tune: ImageVector by lazy {
        ImageVector.Builder(
            name = "Tune",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 17f)
                verticalLineTo(19f)
                horizontalLineTo(9f)
                verticalLineTo(17f)
                horizontalLineTo(3f)
                close()
                moveTo(3f, 5f)
                verticalLineTo(7f)
                horizontalLineTo(13f)
                verticalLineTo(5f)
                horizontalLineTo(3f)
                close()
                moveTo(13f, 21f)
                verticalLineTo(19f)
                horizontalLineTo(21f)
                verticalLineTo(17f)
                horizontalLineTo(13f)
                verticalLineTo(15f)
                horizontalLineTo(11f)
                verticalLineTo(21f)
                horizontalLineTo(13f)
                close()
                moveTo(7f, 9f)
                verticalLineTo(11f)
                horizontalLineTo(3f)
                verticalLineTo(13f)
                horizontalLineTo(7f)
                verticalLineTo(15f)
                horizontalLineTo(9f)
                verticalLineTo(9f)
                horizontalLineTo(7f)
                close()
                moveTo(21f, 13f)
                verticalLineTo(11f)
                horizontalLineTo(11f)
                verticalLineTo(13f)
                horizontalLineTo(21f)
                close()
                moveTo(15f, 9f)
                horizontalLineTo(17f)
                verticalLineTo(7f)
                horizontalLineTo(21f)
                verticalLineTo(5f)
                horizontalLineTo(17f)
                verticalLineTo(3f)
                horizontalLineTo(15f)
                verticalLineTo(9f)
                close()
            }
        }.build()
    }

    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "Play",
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

    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 19f)
                horizontalLineTo(10f)
                verticalLineTo(5f)
                horizontalLineTo(6f)
                verticalLineTo(19f)
                close()
                moveTo(14f, 5f)
                verticalLineTo(19f)
                horizontalLineTo(18f)
                verticalLineTo(5f)
                horizontalLineTo(14f)
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

    val Celebration: ImageVector by lazy {
        ImageVector.Builder(
            name = "Celebration",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(2f, 22f)
                lineTo(16f, 16f)
                lineTo(8f, 8f)
                lineTo(2f, 22f)
                close()
                moveTo(14.5f, 5.5f)
                lineTo(17f, 3f)
                lineTo(19f, 5f)
                lineTo(16.5f, 7.5f)
                lineTo(14.5f, 5.5f)
                close()
                moveTo(18.5f, 9.5f)
                lineTo(21f, 7f)
                lineTo(22.5f, 8.5f)
                lineTo(20f, 11f)
                lineTo(18.5f, 9.5f)
                close()
                moveTo(11f, 3f)
                horizontalLineTo(13f)
                verticalLineTo(6f)
                horizontalLineTo(11f)
                verticalLineTo(3f)
                close()
            }
        }.build()
    }

    val CheerSparkle: ImageVector by lazy {
        ImageVector.Builder(
            name = "CheerSparkle",
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

    val Tambourine: ImageVector by lazy {
        ImageVector.Builder(
            name = "Tambourine",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
                close()
                moveTo(12f, 18f)
                curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
                curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
                curveTo(15.31f, 6f, 18f, 8.69f, 18f, 12f)
                curveTo(18f, 15.31f, 15.31f, 18f, 12f, 18f)
                close()
                moveTo(12f, 10f)
                curveTo(10.9f, 10f, 10f, 10.9f, 10f, 12f)
                curveTo(10f, 13.1f, 10.9f, 14f, 12f, 14f)
                curveTo(13.1f, 14f, 14f, 13.1f, 14f, 12f)
                curveTo(14f, 10.9f, 13.1f, 10f, 12f, 10f)
                close()
            }
        }.build()
    }
}

/**
 * Unified Sidebar Menu:
 * `유튜브 반주` 및 `추천 선곡` 메뉴를 `라이브 스테이지` 단일 통합 화면으로 합쳐 파편화를 제거.
 */
enum class PleosSidebarMenu(val title: String) {
    STAGE_LIVE("라이브 스테이지"),
    VEHICLE_4D("차량 무대 모드"),
    MIC_PAIRING("스마트 링크"),
    SOUND_REVERB("사운드 설정");

    val icon: ImageVector
        get() = when (this) {
            STAGE_LIVE -> PleosMaterialIcons.Mic
            VEHICLE_4D -> PleosMaterialIcons.Car
            MIC_PAIRING -> PleosMaterialIcons.QrLink
            SOUND_REVERB -> PleosMaterialIcons.Tune
        }
}

class MainActivity : ComponentActivity() {
    private lateinit var vehicleManager: PleosVehicleManager
    private lateinit var embeddedServer: PleosEmbeddedSignalingServer
    private var initialAudioFocusRequest: AudioFocusRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestInitialMediaAudioFocus()
        vehicleManager = PleosVehicleManager(applicationContext)
        embeddedServer = PleosEmbeddedSignalingServer(applicationContext, initialPin = "4829").apply {
            start()
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    background = PleosCanvasBg,
                    surface = PleosPanelWhite,
                    primary = PleosBlue,
                    secondary = PleosGreen
                ),
                typography = PretendardTypography
            ) {
                ProvideTextStyle(
                    value = TextStyle(
                        fontFamily = PretendardFontFamily,
                        letterSpacing = (-0.015).em
                    )
                ) {
                    PleosConnectInfotainmentScreen(
                        vehicleManager = vehicleManager,
                        embeddedServer = embeddedServer,
                        onRequestAudioFocus = { requestInitialMediaAudioFocus() }
                    )
                }
            }
        }
    }

    /**
     * AAOS 시작 시 최초 1회 USAGE_MEDIA AudioFocus를 획득하여 스피커 채널을 열고,
     * 포커스 리스너에서 AUDIOFOCUS_LOSS가 와도 내부 오디오를 멈추지 않도록(무시하도록) 유지.
     */
    private fun requestInitialMediaAudioFocus() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (curVol < (maxVol * 0.7f).toInt()) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.85f).toInt(), 0)
            }
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
                // AUDIOFOCUS_LOSS 등이 발생해도 내부 오디오(마이크 리버브/신스/유튜브)를 절대 중지하지 않고 무시
                Log.i("PleosAudioFocus", "AudioFocus changed=$focusChange (ignored to keep stage audio alive)")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = initialAudioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(focusListener)
                    .build()
                initialAudioFocusRequest = req
                val res = audioManager.requestAudioFocus(req)
                Log.i("PleosAudioFocus", "Initial USAGE_MEDIA AudioFocus requestedonce result=$res")
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (e: Exception) {
            Log.w("PleosAudioFocus", "Failed to request initial AudioFocus: ${e.message}")
        }
    }

    override fun onDestroy() {
        if (::embeddedServer.isInitialized) {
            embeddedServer.stop()
        }
        if (::vehicleManager.isInitialized) {
            vehicleManager.release()
        }
        super.onDestroy()
    }
}

@Composable
fun PleosConnectInfotainmentScreen(
    vehicleManager: PleosVehicleManager,
    embeddedServer: PleosEmbeddedSignalingServer,
    onRequestAudioFocus: () -> Unit = {}
) {
    val twinState by vehicleManager.twinState.collectAsState()
    val serverState by embeddedServer.serverState.collectAsState()
    val audioController = remember { CarAudioEngineController() }
    val youtubeController = remember { YouTubeMrController() }
    val coroutineScope = rememberCoroutineScope()

    // DEV / PROD Mode State (Initialized from BuildConfig.IS_DEV_BUILD, toggleable at top-right)
    var isDevMode by remember { mutableStateOf(BuildConfig.IS_DEV_BUILD) }

    // Sidebar Navigation State
    var activeMenu by remember { mutableStateOf(PleosSidebarMenu.STAGE_LIVE) }

    // Pairing & Embedded Ktor Signaling State
    var localLoopbackWsUrl by remember { mutableStateOf("ws://127.0.0.1:8080/ws") }
    var emulatorSignalingUrl by remember { mutableStateOf("ws://10.0.2.2:8080/ws") }
    var roomPin by remember { mutableStateOf("4829") }
    var phoneLanUrl by remember(serverState.lanWsUrl) { mutableStateOf(serverState.lanWsUrl) }
    var tunnelUrl by remember { mutableStateOf("") }
    var micConnected by remember { mutableStateOf(false) }
    var micDeviceName by remember { mutableStateOf("미연결 (PIN 4829)") }
    var connectedMicCount by remember { mutableIntStateOf(0) }

    // Single Source of Truth Unified Playlist (YouTube MR + Built-in Synth Tracks)
    val unifiedPlaylist = remember {
        mutableStateListOf<UnifiedStageTrack>().apply {
            if (BuildConfig.IS_DEV_BUILD) {
                addAll(YouTubeUrlParser.DEFAULT_UNIFIED_PLAYLIST)
            }
        }
    }
    var currentTrackId by remember {
        mutableStateOf(
            if (BuildConfig.IS_DEV_BUILD) YouTubeUrlParser.DEFAULT_UNIFIED_PLAYLIST.first().id else ""
        )
    }
    val emptyStagePlaceholderTrack = remember {
        UnifiedStageTrack(
            id = "",
            title = "예약된 곡이 없습니다",
            artist = "상단 검색창이나 스마트폰 앱에서 유튜브 곡을 추가해 보세요",
            genre = "",
            bpm = 120,
            durationSec = 0,
            durationLabel = "00:00",
            videoId = "",
            songId = "",
            isDevSample = false
        )
    }
    val currentTrack = remember(unifiedPlaylist.toList(), currentTrackId) {
        if (unifiedPlaylist.isEmpty()) {
            emptyStagePlaceholderTrack
        } else {
            unifiedPlaylist.firstOrNull { it.id == currentTrackId }
                ?: unifiedPlaylist.first()
        }
    }
    val selectedBuiltInSong: StageSong = remember(currentTrack.songId, currentTrack.id) {
        if (currentTrack.isBuiltIn && currentTrack.id.isNotBlank()) {
            StageSongCatalog.findById(currentTrack.songId.ifBlank { currentTrack.id })
        } else {
            StageSongCatalog.DEMO_SONGS.first()
        }
    }

    // Unified Stage Playback & Audio State
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var ytActuallyPlaying by remember { mutableStateOf(false) }
    var ytErrorCode by remember { mutableStateOf("") }
    var useSynthMrFallback by remember { mutableStateOf(false) } // false = YouTube 원곡 (<video>), true = 내장 신스 MR
    var songElapsedSec by remember { mutableFloatStateOf(0f) }
    var backingVolume by remember { mutableIntStateOf(85) } // 0..100 Unified Backing/YouTube Volume
    var echoLevel by remember { mutableIntStateOf(80) }     // 0..100 Reverb Echo Level
    var keyShift by remember { mutableIntStateOf(0) }       // -6..+6 Semitones
    var mediaVolume by remember { mutableIntStateOf(12) }   // 0..15 Pleos media bar scale
    var currentScore by remember { mutableIntStateOf(92) }
    var addTrackInput by remember { mutableStateOf("") }

    // Real-time Vocal & Audio Spectrum FFT Metrics (48 Frequency Bands: Bass -> Mid -> High-Hat)
    val guideTelemetry by audioController.guideTelemetry.collectAsState()
    var realMicRms by remember { mutableFloatStateOf(0f) }
    var realMicPitchHz by remember { mutableFloatStateOf(0f) }
    var realMicNote by remember { mutableStateOf("-") }
    var vocalRms by remember { mutableFloatStateOf(0f) }
    var vocalPitchHz by remember { mutableFloatStateOf(0f) }
    var vocalNote by remember { mutableStateOf("-") }
    var lastRealMicTelemetryMs by remember { mutableStateOf(0L) }
    var demoVocalSimEnabled by remember { mutableStateOf(false) }
    val pitchHistory = remember { mutableStateListOf<Float>().apply { repeat(36) { add(0f) } } }
    val rmsHistory = remember { mutableStateListOf<Float>().apply { repeat(36) { add(0f) } } }
    val musicSpectrumBands = remember { mutableStateListOf<Float>().apply { repeat(48) { add(0f) } } }

    fun populateInitialGuideWaveBars(trackId: String = currentTrack.id, bpm: Int = currentTrack.bpm) {
        for (i in 0 until 36) {
            val stepSec = i * 0.12f
            val (hz, _) = NativeVocalAudioEngine.resolveRealtimeGuidePitch(
                trackId = trackId,
                bpm = bpm,
                elapsedSec = stepSec,
                lyricBaseHz = 0f,
                keyShiftSemitones = keyShift
            )
            val waveHz = (hz + sin(i * 0.55f) * 18f).coerceIn(220f, 520f)
            val waveRms = (56f + sin(i * 0.72f) * 24f).coerceIn(28f, 84f)
            if (i < pitchHistory.size) pitchHistory[i] = waveHz else pitchHistory.add(waveHz)
            if (i < rmsHistory.size) rmsHistory[i] = waveRms else rmsHistory.add(waveRms)
        }
    }

    fun clearVocalWaveBars() {
        vocalRms = 0f
        vocalPitchHz = 0f
        vocalNote = "-"
        for (i in 0 until pitchHistory.size) pitchHistory[i] = 0f
        for (i in 0 until rmsHistory.size) rmsHistory[i] = 0f
    }

    LaunchedEffect(demoVocalSimEnabled) {
        if (demoVocalSimEnabled && !isPlaying) {
            onRequestAudioFocus()
        } else if (!demoVocalSimEnabled && realMicRms <= 1.5f) {
            clearVocalWaveBars()
        }
    }

    LaunchedEffect(guideTelemetry, demoVocalSimEnabled) {
        val nowMs = System.currentTimeMillis()
        val hasRealMic = micConnected && (nowMs - lastRealMicTelemetryMs <= 450L) && (realMicRms > 1.5f)
        if (!hasRealMic) {
            if (demoVocalSimEnabled) {
                val gt = audioController.nativeVocalEngine.guideTelemetry.value
                vocalRms = if (gt.guideVoiceLevelPercent > 10f) gt.guideVoiceLevelPercent else 64f
                vocalPitchHz = if (gt.guidePitchHz > 65f) gt.guidePitchHz else 293.66f
                vocalNote = if (gt.guideNote != "-" && gt.guideNote.isNotBlank()) gt.guideNote else "D4"
                if (pitchHistory.all { it <= 65f }) {
                    populateInitialGuideWaveBars()
                }
            } else {
                clearVocalWaveBars()
            }
        }
    }

    fun clearMusicSpectrum() {
        for (i in 0 until 48) {
            if (i < musicSpectrumBands.size) {
                musicSpectrumBands[i] = 0f
            }
        }
    }

    fun updateMusicSpectrum(bands: List<Float>) {
        if (!isPlaying || unifiedPlaylist.isEmpty()) {
            clearMusicSpectrum()
            return
        }
        val volScale = (backingVolume / 100f).coerceIn(0.15f, 1.0f)
        for (i in 0 until 48) {
            val raw = bands.getOrElse(i) { 0f }.coerceIn(0f, 1f) * volScale
            if (i < musicSpectrumBands.size) {
                musicSpectrumBands[i] = raw
            }
        }
    }

    LaunchedEffect(isPlaying, unifiedPlaylist.size) {
        if (!isPlaying || unifiedPlaylist.isEmpty()) {
            clearMusicSpectrum()
        }
    }

    val songLyricCycleSec = selectedBuiltInSong.durationSec.coerceAtLeast(1).toFloat()
    val wrappedLyricSec = songElapsedSec % songLyricCycleSec
    val currentLyricIndex = remember(selectedBuiltInSong, wrappedLyricSec) {
        selectedBuiltInSong.lyrics.indexOfFirst {
            wrappedLyricSec >= it.startSec && wrappedLyricSec < it.endSec
        }.let { if (it == -1) 0 else it }
    }
    val currentLyric = selectedBuiltInSong.lyrics.getOrElse(currentLyricIndex) { selectedBuiltInSong.lyrics.first() }
    val nextLyric = selectedBuiltInSong.lyrics.getOrNull((currentLyricIndex + 1) % selectedBuiltInSong.lyrics.size)
    val (guidePitchHz, guideNote) = remember(
        demoVocalSimEnabled,
        currentTrack.id,
        currentTrack.bpm,
        songElapsedSec,
        currentLyric.targetPitchHz,
        keyShift
    ) {
        if (demoVocalSimEnabled) {
            NativeVocalAudioEngine.resolveRealtimeGuidePitch(
                trackId = currentTrack.id,
                bpm = currentTrack.bpm,
                elapsedSec = songElapsedSec,
                lyricBaseHz = currentLyric.targetPitchHz,
                keyShiftSemitones = keyShift
            )
        } else {
            0f to "-"
        }
    }

    // Broadcast 1:1 Single Source of Truth State to all connected Smartphones
    fun broadcastSyncState(
        overrideTrack: UnifiedStageTrack = currentTrack,
        overridePlaying: Boolean = isPlaying,
        overrideElapsed: Int = songElapsedSec.roundToInt()
    ) {
        val playlistJsonArr = YouTubeUrlParser.toJsonArray(unifiedPlaylist.toList())
        val safeElapsed = if (unifiedPlaylist.isEmpty()) 0 else overrideElapsed.coerceAtLeast(0)
        val safeDuration = if (unifiedPlaylist.isEmpty()) 0 else overrideTrack.durationSec.coerceAtLeast(15)
        val activeGuideHz = if (demoVocalSimEnabled && guideTelemetry.guidePitchHz > 60f) guideTelemetry.guidePitchHz else 0f
        val activeGuideNote = if (demoVocalSimEnabled && guideTelemetry.guidePitchHz > 60f) guideTelemetry.guideNote else "-"
        val activeGuideLevel = if (demoVocalSimEnabled) guideTelemetry.guideVoiceLevelPercent.roundToInt() else 0
        val stateJson = JSONObject().apply {
            put("currentSongId", overrideTrack.id)
            put("currentTrackId", overrideTrack.id)
            put("currentTrackTitle", overrideTrack.title)
            put("currentTrackArtist", overrideTrack.artist)
            put("isPlaying", overridePlaying && unifiedPlaylist.isNotEmpty())
            put("progressSec", safeElapsed)
            put("currentPositionSec", safeElapsed)
            put("durationSec", safeDuration)
            put("backingVolume", backingVolume)
            put("keyShift", keyShift)
            put("echoLevel", echoLevel)
            put("isParked", twinState.isParked)
            put("score", currentScore)
            put("isDevMode", isDevMode)
            put("guideVocalEnabled", demoVocalSimEnabled)
            put("demoVocalWave", demoVocalSimEnabled)
            put("guidePitchHz", activeGuideHz.toDouble())
            put("guideNote", activeGuideNote)
            put("guideVoiceLevelPercent", activeGuideLevel)
            put("playlist", playlistJsonArr)
        }
        embeddedServer.broadcastCarStateSync(stateJson)
        audioController.syncStateToMic(
            songId = overrideTrack.id,
            isPlaying = overridePlaying && unifiedPlaylist.isNotEmpty(),
            keyShift = keyShift,
            echoLevel = echoLevel,
            isParked = twinState.isParked,
            score = currentScore,
            currentTrackId = overrideTrack.id,
            progressSec = safeElapsed,
            durationSec = safeDuration,
            backingVolume = backingVolume,
            isDevMode = isDevMode,
            guideVocalEnabled = demoVocalSimEnabled,
            guidePitchHz = activeGuideHz,
            guideNote = activeGuideNote,
            guideVoiceLevelPercent = activeGuideLevel,
            playlistJson = playlistJsonArr
        )
    }

    // Start playing any track (YouTube or Built-in Synth) in the Unified Stage
    fun playUnifiedTrack(track: UnifiedStageTrack, resetProgress: Boolean = true) {
        if (track.id.isBlank()) return
        currentTrackId = track.id
        if (resetProgress) {
            songElapsedSec = 0f
            currentScore = 90
        }
        isPlaying = true
        ytActuallyPlaying = false
        ytErrorCode = ""
        activeMenu = PleosSidebarMenu.STAGE_LIVE

        vehicleManager.onSongStarted(
            songTitle = track.title,
            genre = track.genre,
            bpm = track.bpm
        )

        audioController.setTrackConfig(track.id, track.bpm)
        audioController.setStagePlaying(true)

        if (track.isYouTube && !useSynthMrFallback) {
            audioController.stopSong()
            youtubeController.setVolume(backingVolume)
            youtubeController.loadTarget(
                videoId = track.videoId,
                playlistId = "",
                title = track.title,
                bpm = track.bpm,
                autoPlay = true
            )
            youtubeController.play()
        } else {
            // 내장 수록곡이거나 [내장 신스 MR] 하이브리드 토글 활성화 시 100% 풍부한 멀티트랙 신스 반주 즉시 출력
            youtubeController.pause()
            val targetSongId = track.songId.ifBlank { track.id }
            audioController.setBackingVolume(backingVolume)
            audioController.startSong(targetSongId, track.bpm, forceSynth = true)
        }

        broadcastSyncState(
            overrideTrack = track,
            overridePlaying = true,
            overrideElapsed = songElapsedSec.roundToInt()
        )
    }

    fun pauseOrStopUnifiedStage(resetProgress: Boolean = false) {
        isPlaying = false
        ytActuallyPlaying = false
        clearMusicSpectrum()
        if (resetProgress) {
            songElapsedSec = 0f
        }
        audioController.setStagePlaying(false)
        audioController.stopSong()
        youtubeController.pause()
        vehicleManager.onSongFinished(currentScore)
        broadcastSyncState(
            overrideTrack = currentTrack,
            overridePlaying = false,
            overrideElapsed = songElapsedSec.roundToInt()
        )
    }

    // Switch between DEV Mode (7 sample tracks) and PROD Mode (completely empty 0-track playlist)
    fun applyDevProdMode(targetDevMode: Boolean) {
        isDevMode = targetDevMode
        if (!targetDevMode) {
            isPlaying = false
            clearMusicSpectrum()
            songElapsedSec = 0f
            audioController.setStagePlaying(false)
            audioController.stopSong()
            youtubeController.pause()
            unifiedPlaylist.clear()
            currentTrackId = ""
            broadcastSyncState(
                overrideTrack = emptyStagePlaceholderTrack,
                overridePlaying = false,
                overrideElapsed = 0
            )
        } else {
            isPlaying = false
            clearMusicSpectrum()
            songElapsedSec = 0f
            audioController.setStagePlaying(false)
            audioController.stopSong()
            youtubeController.pause()
            unifiedPlaylist.clear()
            unifiedPlaylist.addAll(YouTubeUrlParser.DEFAULT_UNIFIED_PLAYLIST)
            val firstTrack = YouTubeUrlParser.DEFAULT_UNIFIED_PLAYLIST.first()
            currentTrackId = firstTrack.id
            broadcastSyncState(
                overrideTrack = firstTrack,
                overridePlaying = false,
                overrideElapsed = 0
            )
        }
    }

    fun toggleUnifiedPlayPause() {
        if (unifiedPlaylist.isEmpty()) return
        if (isPlaying) {
            pauseOrStopUnifiedStage(resetProgress = false)
        } else {
            if (currentTrack.isYouTube && !useSynthMrFallback && songElapsedSec > 1f) {
                isPlaying = true
                audioController.setStagePlaying(true)
                youtubeController.play()
                broadcastSyncState(overrideTrack = currentTrack, overridePlaying = true)
            } else {
                playUnifiedTrack(currentTrack, resetProgress = songElapsedSec <= 1f)
            }
        }
    }

    fun toggleBackingSourceMode() {
        useSynthMrFallback = !useSynthMrFallback
        if (isPlaying && unifiedPlaylist.isNotEmpty()) {
            playUnifiedTrack(currentTrack, resetProgress = false)
        }
    }

    fun playAdjacentTrack(step: Int) {
        val pool = unifiedPlaylist.toList()
        if (pool.isEmpty()) return
        val curIdx = pool.indexOfFirst { it.id == currentTrackId }.let { if (it < 0) 0 else it }
        val nextIdx = (curIdx + step + pool.size) % pool.size
        playUnifiedTrack(pool[nextIdx], resetProgress = true)
    }

    fun addTrackToUnifiedPlaylist(rawInput: String, playImmediately: Boolean = false) {
        val created = YouTubeUrlParser.createTrackFromInput(rawInput) ?: return
        val wasEmpty = unifiedPlaylist.isEmpty()
        unifiedPlaylist.add(0, created)
        if (wasEmpty || currentTrackId.isBlank()) {
            currentTrackId = created.id
        }
        addTrackInput = ""
        if (playImmediately) {
            playUnifiedTrack(created, resetProgress = true)
        } else {
            broadcastSyncState(overrideTrack = if (wasEmpty) created else currentTrack)
        }

        // If rawInput was a YouTube URL or Video ID, asynchronously enrich title via oEmbed
        val parsed = YouTubeUrlParser.parse(rawInput)
        if (parsed.videoId.isNotBlank()) {
            coroutineScope.launch {
                val meta = YouTubeUrlParser.fetchOEmbedMetadata(parsed.videoId)
                if (meta != null) {
                    val idx = unifiedPlaylist.indexOfFirst { it.id == created.id }
                    if (idx >= 0) {
                        val updated = unifiedPlaylist[idx].copy(title = meta.first, artist = meta.second)
                        unifiedPlaylist[idx] = updated
                        broadcastSyncState(overrideTrack = if (currentTrackId == updated.id) updated else currentTrack)
                    }
                }
            }
        }
    }

    fun removeTrackFromUnifiedPlaylist(trackId: String) {
        if (unifiedPlaylist.isEmpty()) return
        val wasCurrent = trackId == currentTrackId
        val idx = unifiedPlaylist.indexOfFirst { it.id == trackId }
        if (idx >= 0) {
            unifiedPlaylist.removeAt(idx)
            if (unifiedPlaylist.isEmpty()) {
                isPlaying = false
                songElapsedSec = 0f
                currentTrackId = ""
                audioController.setStagePlaying(false)
                audioController.stopSong()
                youtubeController.pause()
                broadcastSyncState(
                    overrideTrack = emptyStagePlaceholderTrack,
                    overridePlaying = false,
                    overrideElapsed = 0
                )
            } else if (wasCurrent) {
                val nextTrack = unifiedPlaylist[idx.coerceAtMost(unifiedPlaylist.lastIndex)]
                if (isPlaying) {
                    playUnifiedTrack(nextTrack, resetProgress = true)
                } else {
                    currentTrackId = nextTrack.id
                    broadcastSyncState(overrideTrack = nextTrack, overridePlaying = false)
                }
            } else {
                broadcastSyncState()
            }
        }
    }

    fun moveTrackInUnifiedPlaylist(fromIndex: Int, toIndex: Int) {
        if (fromIndex in unifiedPlaylist.indices && toIndex in unifiedPlaylist.indices && fromIndex != toIndex) {
            val item = unifiedPlaylist.removeAt(fromIndex)
            unifiedPlaylist.add(toIndex, item)
            broadcastSyncState()
        }
    }

    LaunchedEffect(currentTrack.id) {
        vehicleManager.onSongGenreSelected(
            songId = currentTrack.id,
            genre = currentTrack.genre,
            bpm = currentTrack.bpm
        )
    }

    LaunchedEffect(isDevMode, twinState.isParked) {
        broadcastSyncState()
    }

    fun dispatchIncomingRemoteCommand(type: String, cmd: JSONObject) {
        when (type) {
            "PLAY_TRACK", "START_SONG", "SELECT_SONG" -> {
                val requestedId = cmd.optString("trackId", cmd.optString("songId", ""))
                val existing = unifiedPlaylist.firstOrNull {
                    it.id == requestedId || (it.songId.isNotBlank() && it.songId == requestedId) ||
                        (it.videoId.isNotBlank() && it.videoId == cmd.optString("videoId", "-"))
                }
                if (existing != null) {
                    if (type == "SELECT_SONG" && !isPlaying) {
                        currentTrackId = existing.id
                        broadcastSyncState(overrideTrack = existing, overridePlaying = false)
                    } else {
                        playUnifiedTrack(existing, resetProgress = true)
                    }
                } else if (cmd.optString("videoId", "").isNotBlank() || cmd.optString("title", "").isNotBlank()) {
                    val newTrack = UnifiedStageTrack.fromJson(cmd)
                    unifiedPlaylist.add(0, newTrack)
                    playUnifiedTrack(newTrack, resetProgress = true)
                }
            }

            "LOAD_YOUTUBE" -> {
                val rawUrl = cmd.optString("youtubeUrl", "")
                val existing = unifiedPlaylist.firstOrNull {
                    val parsedVid = YouTubeUrlParser.parse(rawUrl).videoId
                    parsedVid.isNotBlank() && it.videoId == parsedVid
                }
                if (existing != null) {
                    playUnifiedTrack(existing, resetProgress = true)
                } else if (rawUrl.isNotBlank()) {
                    addTrackToUnifiedPlaylist(rawUrl, playImmediately = true)
                }
            }

            "ADD_TRACK" -> {
                val rawInput = cmd.optString("youtubeUrl", cmd.optString("rawInput", cmd.optString("title", "")))
                val playImm = cmd.optBoolean("playImmediately", false)
                if (rawInput.isNotBlank()) {
                    addTrackToUnifiedPlaylist(rawInput, playImmediately = playImm)
                }
            }

            "REMOVE_TRACK" -> {
                val tid = cmd.optString("trackId", cmd.optString("songId", ""))
                if (tid.isNotBlank()) {
                    removeTrackFromUnifiedPlaylist(tid)
                }
            }

            "MOVE_TRACK" -> {
                val fromIdx = cmd.optInt("fromIndex", -1)
                val toIdx = cmd.optInt("toIndex", -1)
                moveTrackInUnifiedPlaylist(fromIdx, toIdx)
            }

            "TOGGLE_PLAY_PAUSE", "YOUTUBE_PLAY_PAUSE" -> {
                toggleUnifiedPlayPause()
            }

            "PAUSE_SONG", "STOP_SONG" -> {
                pauseOrStopUnifiedStage(resetProgress = type == "STOP_SONG")
            }

            "NEXT_TRACK", "YOUTUBE_NEXT" -> {
                playAdjacentTrack(1)
            }

            "PREV_TRACK", "YOUTUBE_PREV" -> {
                playAdjacentTrack(-1)
            }

            "CHANGE_BACKING_VOLUME", "YOUTUBE_VOLUME" -> {
                val vol = cmd.optInt("backingVolume", cmd.optInt("youtubeVolume", backingVolume)).coerceIn(0, 100)
                backingVolume = vol
                youtubeController.setVolume(vol)
                audioController.setBackingVolume(vol)
                broadcastSyncState()
            }

            "CHANGE_KEY" -> {
                keyShift = cmd.optInt("keyShift", keyShift).coerceIn(-6, 6)
                audioController.setKeyShift(keyShift)
                broadcastSyncState()
            }

            "CHANGE_ECHO" -> {
                echoLevel = cmd.optInt("echoLevel", echoLevel).coerceIn(0, 100)
                audioController.setEchoLevel(echoLevel)
                broadcastSyncState()
            }

            "SET_DEV_MODE" -> {
                if (cmd.has("isDevMode")) {
                    val nextDevMode = cmd.optBoolean("isDevMode", isDevMode)
                    applyDevProdMode(nextDevMode)
                }
            }

            "SET_GUIDE_VOCAL", "TOGGLE_GUIDE_VOCAL", "SET_DEMO_VOCAL" -> {
                val nextGuide = when {
                    cmd.has("guideVocalEnabled") -> cmd.optBoolean("guideVocalEnabled", false)
                    cmd.has("demoVocalWave") -> cmd.optBoolean("demoVocalWave", false)
                    cmd.has("enabled") -> cmd.optBoolean("enabled", false)
                    else -> !demoVocalSimEnabled
                }
                demoVocalSimEnabled = nextGuide
                if (nextGuide) {
                    onRequestAudioFocus()
                    populateInitialGuideWaveBars()
                } else if (realMicRms <= 1.5f) {
                    clearVocalWaveBars()
                }
                audioController.setGuideVocalEnabled(demoVocalSimEnabled)
                broadcastSyncState()
            }

            "RESERVE_SONG" -> {
                val sid = cmd.optString("songId", "")
                val idx = unifiedPlaylist.indexOfFirst { it.id == sid || it.songId == sid }
                if (idx > 0) {
                    moveTrackInUnifiedPlaylist(idx, 0)
                }
            }

            "TRIGGER_SFX" -> {
                val sfx = cmd.optString("sfx", "CHEER").uppercase()
                audioController.triggerSfx(sfx)
            }
        }
    }

    var lastHandledRemoteCmdTs by remember { mutableStateOf(0L) }

    // Wire Embedded Ktor Server real-time Smartphone Mic PCM chunks & Direct Remote Commands directly to Native AudioTrack & UI
    DisposableEffect(embeddedServer, audioController) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        embeddedServer.onMicPcmChunk = { sr, pcmShorts ->
            audioController.enqueueMicPcmChunk(sr, pcmShorts)
        }
        embeddedServer.onDirectMicConnectionChanged = { connected, deviceName, count ->
            mainHandler.post {
                micConnected = connected
                micDeviceName = if (connected) deviceName else "미연결 (PIN $roomPin)"
                connectedMicCount = count
                if (connected) {
                    broadcastSyncState()
                }
            }
        }
        embeddedServer.onDirectRemoteCommand = { type, cmd ->
            mainHandler.post {
                val cmdTs = cmd.optLong("ts", 0L)
                if (cmdTs == 0L || cmdTs != lastHandledRemoteCmdTs) {
                    lastHandledRemoteCmdTs = cmdTs
                    dispatchIncomingRemoteCommand(type, cmd)
                }
            }
        }
        onDispose {
            embeddedServer.onMicPcmChunk = null
            embeddedServer.onDirectMicConnectionChanged = null
            embeddedServer.onDirectRemoteCommand = null
        }
    }

    // Sync Stage Playback, Guide Vocal Melody, Echo Level & Key Shift immediately to Native AudioTrack
    LaunchedEffect(
        isPlaying,
        unifiedPlaylist.size,
        demoVocalSimEnabled,
        echoLevel,
        keyShift,
        mediaVolume,
        currentTrack.id,
        currentTrack.bpm,
        guidePitchHz,
        guideNote,
        currentLyric.isHighlight
    ) {
        audioController.setTrackConfig(currentTrack.id, currentTrack.bpm)
        audioController.setGuideVocalEnabled(demoVocalSimEnabled)
        audioController.setEchoLevel(echoLevel)
        audioController.setKeyShift(keyShift)
        audioController.setMasterVolumeScale((mediaVolume / 15f).coerceIn(0f, 1f))
        if (demoVocalSimEnabled) {
            audioController.setGuidePitch(guidePitchHz, guideNote, currentLyric.isHighlight)
        }
        audioController.setStagePlaying(isPlaying && unifiedPlaylist.isNotEmpty())
    }

    // Real-Time Mic & Guide Vocal Telemetry Loop (Active whenever real mic input > 0 OR guideVocalEnabled == true, even when MR is paused!)
    LaunchedEffect(Unit) {
        var vocalTick = 0
        while (true) {
            delay(85L)
            vocalTick++

            val nowMs = System.currentTimeMillis()
            if (!micConnected || (nowMs - lastRealMicTelemetryMs > 450L)) {
                realMicRms = 0f
                realMicPitchHz = 0f
                realMicNote = "-"
            }
            val hasRealMic = micConnected && (nowMs - lastRealMicTelemetryMs <= 450L) && (realMicRms > 1.5f)
            val gt = audioController.nativeVocalEngine.guideTelemetry.value

            if (hasRealMic) {
                vocalRms = realMicRms
                vocalPitchHz = realMicPitchHz
                vocalNote = realMicNote
                val slotPitch = if (realMicPitchHz > 65f) realMicPitchHz else 262f
                val slotRms = realMicRms
                if (pitchHistory.size >= 36) pitchHistory.removeAt(0)
                pitchHistory.add(slotPitch)
                if (rmsHistory.size >= 36) rmsHistory.removeAt(0)
                rmsHistory.add(slotRms)
            } else if (demoVocalSimEnabled) {
                val simElapsedSec = if (isPlaying) songElapsedSec else (vocalTick * 0.085f)
                val (simHz, simNote) = NativeVocalAudioEngine.resolveRealtimeGuidePitch(
                    trackId = currentTrack.id,
                    bpm = currentTrack.bpm,
                    elapsedSec = simElapsedSec,
                    lyricBaseHz = if (isPlaying) currentLyric.targetPitchHz else 0f,
                    keyShiftSemitones = keyShift
                )
                val activeHz = if (gt.active && gt.guidePitchHz > 65f) gt.guidePitchHz else simHz
                val activeNote = if (gt.active && gt.guideNote != "-" && gt.guideNote.isNotBlank()) gt.guideNote else simNote
                val baseLevel = if (gt.active && gt.guideVoiceLevelPercent > 15f) gt.guideVoiceLevelPercent else 58f
                val waveModHz = (activeHz + sin(vocalTick * 0.62f) * 18f).coerceIn(220f, 520f)
                val waveModRms = (baseLevel + sin(vocalTick * 0.78f) * 18f).coerceIn(25f, 85f)

                vocalPitchHz = activeHz
                vocalNote = activeNote
                vocalRms = waveModRms
                if (pitchHistory.size >= 36) pitchHistory.removeAt(0)
                pitchHistory.add(waveModHz)
                if (rmsHistory.size >= 36) rmsHistory.removeAt(0)
                rmsHistory.add(waveModRms)
            } else {
                realMicRms = 0f
                realMicPitchHz = 0f
                realMicNote = "-"
                vocalRms = 0f
                vocalPitchHz = 0f
                vocalNote = "-"
                if (pitchHistory.any { it != 0f }) {
                    for (i in 0 until pitchHistory.size) pitchHistory[i] = 0f
                }
                if (rmsHistory.any { it != 0f }) {
                    for (i in 0 until rmsHistory.size) rmsHistory[i] = 0f
                }
            }

            if (vocalTick % 2 == 0 && vocalRms > 10f && vocalPitchHz > 65f) {
                val refHz = if (demoVocalSimEnabled && gt.guidePitchHz > 60f) gt.guidePitchHz else guidePitchHz
                if (abs(vocalPitchHz - refHz) / refHz.coerceAtLeast(100f) < 0.14f) {
                    currentScore = (currentScore + 1).coerceAtMost(99)
                }
            }

            // Broadcast guide vocal telemetry to connected smartphones even when YouTube MR is paused/stopped!
            if (demoVocalSimEnabled && !isPlaying && vocalTick % 2 == 0) {
                broadcastSyncState(
                    overrideTrack = currentTrack,
                    overridePlaying = false,
                    overrideElapsed = songElapsedSec.roundToInt()
                )
            }
        }
    }

    // 100ms Backing Music Playback Timer & 500ms Bi-Directional Progress Sync Loop (Only when isPlaying == true)
    LaunchedEffect(isPlaying, currentTrack.id, currentTrack.genre, currentTrack.bpm) {
        var tick = 0
        while (isPlaying) {
            delay(100L)
            songElapsedSec += 0.1f
            tick++

            vehicleManager.onMusicAndVocalWaveformTick(
                genre = currentTrack.genre,
                bpm = currentTrack.bpm,
                elapsedSec = songElapsedSec,
                vocalRms = vocalRms,
                vocalPitchHz = vocalPitchHz,
                isHighlightOrInterlude = if (currentTrack.isYouTube) (vocalRms >= 70f) else currentLyric.isHighlight
            )

            if (tick % 5 == 0) {
                broadcastSyncState(
                    overrideTrack = currentTrack,
                    overridePlaying = isPlaying,
                    overrideElapsed = songElapsedSec.roundToInt()
                )
            }

            if (tick % 10 == 0) {
                vehicleManager.accumulateHighOutputSingingSecond(vocalRms >= 65f)
            }

            if (songElapsedSec >= currentTrack.durationSec) {
                playAdjacentTrack(1)
            }
        }
    }

    val animatedAmbientColor by animateColorAsState(
        targetValue = Color(twinState.ambientColorHex),
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "ambientColor"
    )

    val activePairServerUrl = if (tunnelUrl.isNotBlank()) tunnelUrl else phoneLanUrl
    val qrPayload = remember(activePairServerUrl, roomPin) {
        "$activePairServerUrl?pin=$roomPin"
    }
    val qrBitmap = remember(qrPayload) {
        QrCodeGenerator.generateQrBitmap(qrPayload, 320)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PleosCanvasBg
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1x1 Hidden WebView for Wet-Only Reverb Audio Engine & WebRTC Receiver
            CarWetReverbEngineHost(
                controller = audioController,
                signalingUrl = localLoopbackWsUrl,
                initialPin = roomPin,
                onRoomRegistered = { pin, lan, tunnel ->
                    roomPin = pin
                    if (lan.isNotBlank()) phoneLanUrl = lan
                    if (tunnel.isNotBlank()) tunnelUrl = tunnel
                    broadcastSyncState()
                },
                onTunnelUpdated = { tunnel -> tunnelUrl = tunnel },
                onMicConnectionChanged = { connected, deviceName, count ->
                    micConnected = connected
                    micDeviceName = if (connected) deviceName else "미연결 (PIN $roomPin)"
                    connectedMicCount = count
                    if (connected) {
                        broadcastSyncState()
                    }
                },
                onVocalTelemetry = { rms, pitchHz, note ->
                    lastRealMicTelemetryMs = System.currentTimeMillis()
                    val cleanRms = rms.coerceIn(0f, 100f)
                    val cleanPitch = if (cleanRms > 1f) pitchHz.coerceAtLeast(0f) else 0f
                    val cleanNote = if (cleanRms > 1f && cleanPitch > 60f) note else "-"
                    realMicRms = cleanRms
                    realMicPitchHz = cleanPitch
                    realMicNote = cleanNote
                    if (cleanRms > 1.5f && cleanPitch > 65f) {
                        vocalRms = cleanRms
                        vocalPitchHz = cleanPitch
                        vocalNote = cleanNote
                        if (pitchHistory.isNotEmpty()) {
                            pitchHistory[pitchHistory.lastIndex] = cleanPitch
                        }
                        if (rmsHistory.isNotEmpty()) {
                            rmsHistory[rmsHistory.lastIndex] = cleanRms
                        }
                    }
                    vehicleManager.onMusicAndVocalWaveformTick(
                        genre = currentTrack.genre,
                        bpm = currentTrack.bpm,
                        elapsedSec = songElapsedSec,
                        vocalRms = cleanRms,
                        vocalPitchHz = cleanPitch,
                        isHighlightOrInterlude = currentLyric.isHighlight
                    )
                },
                onRemoteCommand = { type, cmd ->
                    val cmdTs = cmd.optLong("ts", 0L)
                    if (cmdTs == 0L || cmdTs != lastHandledRemoteCmdTs) {
                        lastHandledRemoteCmdTs = cmdTs
                        dispatchIncomingRemoteCommand(type, cmd)
                    }
                },
                onEngineLog = {},
                onMusicSpectrum = { bands ->
                    if (!currentTrack.isYouTube || useSynthMrFallback) {
                        updateMusicSpectrum(bands)
                    }
                }
            )

            // Separate YouTube Watch (<video>) WebView at top layer zIndex(2f), bottom-right of stage Box, 200x120.dp, alpha(0.02f)
            YouTubeMrPlayerHost(
                controller = youtubeController,
                modifier = Modifier
                    .zIndex(2f)
                    .align(Alignment.BottomEnd)
                    .padding(end = 436.dp, bottom = 116.dp)
                    .size(200.dp, 120.dp)
                    .alpha(0.02f),
                initialVideoId = currentTrack.videoId.ifBlank { "K4DyBUG242c" },
                initialVolume = backingVolume,
                isPlaying = isPlaying && currentTrack.isYouTube && !useSynthMrFallback,
                bpm = currentTrack.bpm,
                onPlayerStateChanged = { actualPlaying, ytTitle, _, _ ->
                    ytActuallyPlaying = actualPlaying
                    if (actualPlaying) {
                        ytErrorCode = ""
                    }
                    if (currentTrack.isYouTube && ytTitle.isNotBlank() &&
                        ytTitle != "YouTube MR Stream" &&
                        currentTrack.title.startsWith("YouTube MR (")
                    ) {
                        val idx = unifiedPlaylist.indexOfFirst { it.id == currentTrack.id }
                        if (idx >= 0) {
                            unifiedPlaylist[idx] = unifiedPlaylist[idx].copy(title = ytTitle)
                            broadcastSyncState()
                        }
                    }
                },
                onVideoEnded = {
                    if (currentTrack.isYouTube && !useSynthMrFallback) {
                        playAdjacentTrack(1)
                    }
                },
                onPlayerError = { errCode ->
                    ytActuallyPlaying = false
                    ytErrorCode = errCode
                    Log.e("PleosYtPlayer", "YouTube Watch Error: $errCode")
                    Toast.makeText(
                        context,
                        "유튜브 재생 오류 발생: Error $errCode",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onMusicSpectrum = { bands ->
                    if (currentTrack.isYouTube && !useSynthMrFallback) {
                        updateMusicSpectrum(bands)
                    }
                }
            )

            // Canonical Pleos Connect App Window Layout (Left Sidebar Navigation + Right Content Area)
            Row(
                modifier = Modifier
                    .zIndex(1f)
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .shadow(2.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(PleosPanelWhite)
                    .border(1.dp, PleosBorderSubtle, RoundedCornerShape(22.dp))
            ) {
                // 1. Left Sidebar Navigation (Width 250.dp, Menu Height 60.dp, Text 18.sp Bold)
                PleosAppSidebar(
                    activeMenu = activeMenu,
                    micConnected = micConnected,
                    roomPin = roomPin,
                    twinState = twinState,
                    isDevMode = isDevMode,
                    onSelectMenu = { activeMenu = it }
                )

                // Vertical Hairline Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(PleosBorderSubtle)
                )

                // Right Content Area inside App Window
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(20.dp)
                ) {
                    when (activeMenu) {
                        PleosSidebarMenu.STAGE_LIVE -> {
                            PleosUnifiedLiveStagePane(
                                twinState = twinState,
                                currentTrack = currentTrack,
                                selectedBuiltInSong = selectedBuiltInSong,
                                playlist = unifiedPlaylist,
                                isPlaying = isPlaying,
                                ytActuallyPlaying = ytActuallyPlaying,
                                ytErrorCode = ytErrorCode,
                                useSynthMrFallback = useSynthMrFallback,
                                songElapsedSec = songElapsedSec,
                                currentLyric = currentLyric,
                                nextLyric = nextLyric,
                                backingVolume = backingVolume,
                                keyShift = keyShift,
                                echoLevel = echoLevel,
                                currentScore = currentScore,
                                vocalRms = vocalRms,
                                vocalPitchHz = vocalPitchHz,
                                vocalNote = vocalNote,
                                realMicRms = realMicRms,
                                realMicPitchHz = realMicPitchHz,
                                realMicNote = realMicNote,
                                pitchHistory = pitchHistory,
                                rmsHistory = rmsHistory,
                                musicSpectrumBands = musicSpectrumBands,
                                ambientColor = animatedAmbientColor,
                                demoVocalSimEnabled = demoVocalSimEnabled,
                                isDevMode = isDevMode,
                                addTrackInput = addTrackInput,
                                onToggleDevMode = { applyDevProdMode(!isDevMode) },
                                onToggleBackingSource = { toggleBackingSourceMode() },
                                onAddTrackInputChange = { addTrackInput = it },
                                onAddTrack = { input ->
                                    addTrackToUnifiedPlaylist(input, playImmediately = false)
                                },
                                onSelectAndPlayTrack = { track ->
                                    if (track.id == currentTrack.id) {
                                        toggleUnifiedPlayPause()
                                    } else {
                                        playUnifiedTrack(track, resetProgress = true)
                                    }
                                },
                                onMoveTrack = { fromIdx, toIdx ->
                                    moveTrackInUnifiedPlaylist(fromIdx, toIdx)
                                },
                                onRemoveTrack = { trackId ->
                                    removeTrackFromUnifiedPlaylist(trackId)
                                },
                                onTogglePlay = { toggleUnifiedPlayPause() },
                                onPrevTrack = { playAdjacentTrack(-1) },
                                onNextTrack = { playAdjacentTrack(1) },
                                onBackingVolumeChange = { newVol ->
                                    backingVolume = newVol.coerceIn(0, 100)
                                    youtubeController.setVolume(backingVolume)
                                    audioController.setBackingVolume(backingVolume)
                                    broadcastSyncState()
                                },
                                onEchoChange = { newEcho ->
                                    echoLevel = newEcho.coerceIn(0, 100)
                                    audioController.setEchoLevel(echoLevel)
                                    broadcastSyncState()
                                },
                                onToggleDemoVocal = {
                                    demoVocalSimEnabled = !demoVocalSimEnabled
                                    if (demoVocalSimEnabled) {
                                        onRequestAudioFocus()
                                        populateInitialGuideWaveBars()
                                    } else if (realMicRms <= 1.5f) {
                                        clearVocalWaveBars()
                                    }
                                    audioController.setGuideVocalEnabled(demoVocalSimEnabled)
                                    broadcastSyncState()
                                },
                                onTriggerSfx = { audioController.triggerSfx(it) },
                                onOpenPairing = { activeMenu = PleosSidebarMenu.MIC_PAIRING },
                                onToggleWindows = { vehicleManager.toggleSoundproofWindowsManually() },
                                onTriggerGreenroom = {
                                    vehicleManager.triggerGreenroomCooldownCare("라이브 스테이지 칩 그린룸 케어 실행")
                                }
                            )
                        }

                        PleosSidebarMenu.VEHICLE_4D -> {
                            PleosQuickSettings4DPane(
                                twinState = twinState,
                                echoLevel = echoLevel,
                                mediaVolume = mediaVolume,
                                isDevMode = isDevMode,
                                onToggleDevMode = { applyDevProdMode(!isDevMode) },
                                onToggleWindows = { vehicleManager.toggleSoundproofWindowsManually() },
                                onTriggerGreenroom = {
                                    vehicleManager.triggerGreenroomCooldownCare("빠른 설정 그린룸 케어 실행")
                                },
                                onTriggerEncore = {
                                    currentScore = 96
                                    vehicleManager.onSongFinished(96)
                                },
                                onSelectGear = { gear ->
                                    vehicleManager.setGearAndBrakeState(gear, gear == GearPosition.P)
                                },
                                onEchoChange = { newEcho ->
                                    echoLevel = newEcho.coerceIn(0, 100)
                                    audioController.setEchoLevel(echoLevel)
                                    broadcastSyncState()
                                },
                                onVolumeChange = { mediaVolume = it.coerceIn(0, 15) }
                            )
                        }

                        PleosSidebarMenu.MIC_PAIRING -> {
                            PleosPairingPane(
                                qrBitmap = qrBitmap.asImageBitmap(),
                                roomPin = roomPin,
                                lanUrl = phoneLanUrl,
                                emulatorUrl = emulatorSignalingUrl,
                                micConnected = micConnected,
                                micDeviceName = micDeviceName,
                                connectedMicCount = connectedMicCount,
                                demoVocalSimEnabled = demoVocalSimEnabled,
                                isDevMode = isDevMode,
                                onToggleDevMode = { applyDevProdMode(!isDevMode) },
                                onToggleDemoSim = {
                                    demoVocalSimEnabled = !demoVocalSimEnabled
                                    if (demoVocalSimEnabled) {
                                        onRequestAudioFocus()
                                        populateInitialGuideWaveBars()
                                    }
                                    audioController.setGuideVocalEnabled(demoVocalSimEnabled)
                                    broadcastSyncState()
                                }
                            )
                        }

                        PleosSidebarMenu.SOUND_REVERB -> {
                            PleosSoundReverbPane(
                                backingVolume = backingVolume,
                                echoLevel = echoLevel,
                                keyShift = keyShift,
                                mediaVolume = mediaVolume,
                                isDevMode = isDevMode,
                                onToggleDevMode = { applyDevProdMode(!isDevMode) },
                                onBackingVolumeChange = {
                                    backingVolume = it.coerceIn(0, 100)
                                    youtubeController.setVolume(backingVolume)
                                    audioController.setBackingVolume(backingVolume)
                                    broadcastSyncState()
                                },
                                onEchoChange = {
                                    echoLevel = it.coerceIn(0, 100)
                                    audioController.setEchoLevel(echoLevel)
                                    broadcastSyncState()
                                },
                                onKeyShiftChange = {
                                    keyShift = it.coerceIn(-6, 6)
                                    audioController.setKeyShift(keyShift)
                                    broadcastSyncState()
                                },
                                onVolumeChange = { mediaVolume = it.coerceIn(0, 15) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 1. APP WINDOW LEFT SIDEBAR (Width 220.dp, Menu Height 60.dp, Text 16.sp Bold)
// ============================================================================
@Composable
private fun PleosAppSidebar(
    activeMenu: PleosSidebarMenu,
    micConnected: Boolean,
    roomPin: String,
    twinState: VehicleDigitalTwinState,
    isDevMode: Boolean,
    onSelectMenu: (PleosSidebarMenu) -> Unit
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PLEOS STAGE",
                    color = PleosTextDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false
                )
                if (isDevMode) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PleosDevRed,
                        shadowElevation = 0.dp
                    ) {
                        Text(
                            text = "DEV",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            PleosSidebarMenu.entries.forEach { menu ->
                val selected = menu == activeMenu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) PleosBlue else Color.Transparent)
                        .clickable { onSelectMenu(menu) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = menu.icon,
                            contentDescription = menu.title,
                            tint = if (selected) Color.White else PleosTextDark,
                            modifier = Modifier.size(22.dp)
                        )

                        Text(
                            text = menu.title,
                            color = if (selected) Color.White else PleosTextDark,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (menu == PleosSidebarMenu.MIC_PAIRING) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (micConnected) PleosGreen else PleosAmber)
                        )
                    }
                }
            }
        }

        // Bottom Status Pill at Bottom of Sidebar (PIN & Vehicle SDK Summary)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PleosTileGray)
                .border(1.dp, PleosBorderSubtle, RoundedCornerShape(16.dp))
                .clickable { onSelectMenu(PleosSidebarMenu.MIC_PAIRING) }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = if (micConnected) "● 마이크 연결됨" else "마이크 연결 PIN: $roomPin",
                color = if (micConnected) PleosGreen else PleosTextDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "기어 ${twinState.gearPosition.label} · ${twinState.hvacTempCelsius}°C · 통풍 ${twinState.seatVentilationLevel}단",
                color = PleosTextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ============================================================================
// 2. UNIFIED LIVE STAGE PANE (LEFT weight(1f) MAIN STAGE + RIGHT 400.dp PLAYLIST)
// ============================================================================
@Composable
private fun PleosUnifiedLiveStagePane(
    twinState: VehicleDigitalTwinState,
    currentTrack: UnifiedStageTrack,
    selectedBuiltInSong: StageSong,
    playlist: List<UnifiedStageTrack>,
    isPlaying: Boolean,
    ytActuallyPlaying: Boolean,
    ytErrorCode: String,
    useSynthMrFallback: Boolean,
    songElapsedSec: Float,
    currentLyric: LyricLine,
    nextLyric: LyricLine?,
    backingVolume: Int,
    keyShift: Int,
    echoLevel: Int,
    currentScore: Int,
    vocalRms: Float,
    vocalPitchHz: Float,
    vocalNote: String,
    realMicRms: Float,
    realMicPitchHz: Float,
    realMicNote: String,
    pitchHistory: List<Float>,
    rmsHistory: List<Float>,
    musicSpectrumBands: List<Float>,
    ambientColor: Color,
    demoVocalSimEnabled: Boolean,
    isDevMode: Boolean,
    addTrackInput: String,
    onToggleDevMode: () -> Unit,
    onToggleBackingSource: () -> Unit,
    onAddTrackInputChange: (String) -> Unit,
    onAddTrack: (String) -> Unit,
    onSelectAndPlayTrack: (UnifiedStageTrack) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onTogglePlay: () -> Unit,
    onPrevTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onBackingVolumeChange: (Int) -> Unit,
    onEchoChange: (Int) -> Unit,
    onToggleDemoVocal: () -> Unit,
    onTriggerSfx: (String) -> Unit,
    onOpenPairing: () -> Unit,
    onToggleWindows: () -> Unit,
    onTriggerGreenroom: () -> Unit
) {
    val animatedRms by animateFloatAsState(
        targetValue = vocalRms,
        animationSpec = tween(90),
        label = "stageVocalRms"
    )

    var draggedTrackId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    // 2단 분할 레이아웃: 좌측/중앙 메인 무대 (weight(1f)) + 우측 통합 플레이리스트 패널 (400.dp 고정)
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ====================================================================
        // 좌측/중앙 메인 무대 (weight(1f))
        // ====================================================================
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. 상단 타이틀 바 (데모곡 고정 장르 문구 완전 제거, DEV/PROD 스위치 배치)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (playlist.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PleosBlue.copy(alpha = 0.14f),
                                shadowElevation = 0.dp
                            ) {
                                Text(
                                    text = "대기 중",
                                    color = PleosBlue,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        } else if (isDevMode && currentTrack.isDevSample) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = PleosDevRed,
                                shadowElevation = 0.dp
                            ) {
                                Text(
                                    text = "DEV",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (currentTrack.isYouTube) PleosBlue.copy(alpha = 0.14f) else PleosStagePurple.copy(alpha = 0.14f),
                                shadowElevation = 0.dp
                            ) {
                                Text(
                                    text = if (currentTrack.isYouTube) "YouTube MR" else "수록곡 싱크",
                                    color = if (currentTrack.isYouTube) PleosBlue else PleosStagePurple,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = if (playlist.isEmpty()) "예약된 곡이 없습니다" else currentTrack.title,
                            color = PleosTextDark,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    val elapsedLabel = UnifiedStageTrack.formatDurationMmSs(songElapsedSec.roundToInt())
                    Text(
                        text = if (playlist.isEmpty()) {
                            "상단 검색창이나 스마트폰 앱에서 유튜브 곡을 추가해 보세요."
                        } else {
                            "${currentTrack.artist} · $elapsedLabel / ${currentTrack.durationLabel}"
                        },
                        color = PleosTextMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                PleosDevProdHeaderBadge(
                    isDevMode = isDevMode,
                    onToggleDevMode = onToggleDevMode
                )
            }

            // 2. 중앙 다크 콘서트홀 스테이지 + 하단 가이드 음정 바
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 다크 네이비/차콜 콘서트홀 스테이지 카드 (#0F172A -> #1E293B)
                Box(
                    modifier = Modifier
                        .zIndex(1f)
                        .fillMaxWidth()
                        .weight(1f)
                        .shadow(4.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(PleosStageNavyStart)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(PleosStageNavyStart, PleosStageCharcoalEnd)
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (!twinState.isParked) PleosAmber else PleosCyanBlue.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(22.dp)
                        )
                ) {
                    // 배경: 성량(RMS)과 앰비언트 조명에 반응하는 소프트 콘서트홀 글로우
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val energyNorm = ((animatedRms / 100f) + if (isPlaying) 0.25f else 0.08f).coerceIn(0.08f, 1.0f)

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    PleosCyanBlue.copy(alpha = 0.20f * energyNorm),
                                    Color.Transparent
                                ),
                                center = Offset(w * 0.25f, h * 0.85f),
                                radius = w * 0.42f
                            ),
                            radius = w * 0.42f,
                            center = Offset(w * 0.25f, h * 0.85f)
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    ambientColor.copy(alpha = 0.26f * energyNorm),
                                    Color.Transparent
                                ),
                                center = Offset(w * 0.75f, h * 0.85f),
                                radius = w * 0.45f
                            ),
                            radius = w * 0.45f,
                            center = Offset(w * 0.75f, h * 0.85f)
                        )
                    }

                    // 중앙 무대 내부 콘텐츠 (상단 하드웨어 상태 요약 칩 4개 + 중앙 100% 전체 폭 곡 정보 및 실시간 FFT 스펙트럼 파형)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 상단: 차량 하드웨어 상태 요약 칩 4개 (15.sp 반투명 글래스 배지)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PleosGlassHardwareChip(
                                    text = if (twinState.ambientLightEnabled) "앰비언트 싱크 ON" else "앰비언트 싱크 OFF",
                                    dotColor = ambientColor,
                                    onClick = {}
                                )
                                PleosGlassHardwareChip(
                                    text = if (twinState.allWindowsClosed) "방음 모드: 창문 닫힘" else "방음 모드: 창문 열림",
                                    dotColor = if (twinState.allWindowsClosed) PleosGreen else PleosAmber,
                                    onClick = onToggleWindows
                                )
                                PleosGlassHardwareChip(
                                    text = "통풍 시트: ${twinState.seatVentilationLevel.coerceAtLeast(2)}단",
                                    dotColor = PleosCyanBlue,
                                    onClick = onTriggerGreenroom
                                )
                                PleosGlassHardwareChip(
                                    text = "주행 안전: ${twinState.gearPosition.label}단",
                                    dotColor = if (twinState.isParked) PleosGreen else PleosAmber,
                                    onClick = {}
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = PleosCyanBlue.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, PleosCyanBlue.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "점수 ${currentScore}점 · 공간감 ${echoLevel}%",
                                    color = PleosCyanBlue,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }

                        // 중앙 무대 본문: 검은 비디오 박스 완전 제거(오디오 전용 안전 무대) + 100% 전체 가로폭 중앙 정렬 + 실제 오디오 FFT 스펙트럼 파형 비주얼라이저(높이 132.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // 1. 상단 오디오 전용 안전 무대 상태 캡슐 배지
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (!twinState.isParked || (currentTrack.isYouTube && ytErrorCode.isNotBlank())) {
                                        PleosAmber.copy(alpha = 0.22f)
                                    } else if (isPlaying && playlist.isNotEmpty()) {
                                        PleosCyanBlue.copy(alpha = 0.18f)
                                    } else {
                                        Color.White.copy(alpha = 0.10f)
                                    },
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (!twinState.isParked || (currentTrack.isYouTube && ytErrorCode.isNotBlank())) {
                                            PleosAmber.copy(alpha = 0.65f)
                                        } else {
                                            PleosCyanBlue.copy(alpha = 0.45f)
                                        }
                                    )
                                ) {
                                    Text(
                                        text = when {
                                            playlist.isEmpty() -> "⏸ LIVE AUDIO STAGE · 예약된 곡이 없습니다 (선곡 대기 중)"
                                            !twinState.isParked -> "● 주행 안전 오디오 전용 무대 (기어 ${twinState.gearPosition.label}) · ${currentTrack.bpm} BPM"
                                            useSynthMrFallback && isPlaying -> "● LIVE AUDIO STAGE · 내장 신스 MR 반주 재생 중 (${currentTrack.bpm} BPM)"
                                            useSynthMrFallback -> "⏸ LIVE AUDIO STAGE · 내장 신스 MR 대기 중 (${currentTrack.bpm} BPM)"
                                            currentTrack.isYouTube && ytErrorCode.isNotBlank() -> "⚠️ LIVE AUDIO STAGE · 유튜브 재생 오류 (Error $ytErrorCode)"
                                            currentTrack.isYouTube && isPlaying && ytActuallyPlaying -> "● LIVE AUDIO STAGE · 실제 유튜브 오디오 재생 중 (${currentTrack.bpm} BPM)"
                                            currentTrack.isYouTube && isPlaying -> "⏳ LIVE AUDIO STAGE · 유튜브 오디오 스트림 연결 중... (${currentTrack.bpm} BPM)"
                                            currentTrack.isYouTube -> "⏸ LIVE AUDIO STAGE · 일시정지 대기 중 (${currentTrack.bpm} BPM)"
                                            isPlaying -> "● LIVE CONCERT STAGE · 수록곡 싱크 재생 중 (${currentTrack.bpm} BPM)"
                                            else -> "⏸ LIVE CONCERT STAGE · 무대 대기 중 (${currentTrack.bpm} BPM)"
                                        },
                                        color = if (!twinState.isParked || (currentTrack.isYouTube && !useSynthMrFallback && ytErrorCode.isNotBlank())) PleosAmber else PleosCyanBlue,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                                    )
                                }

                                // 2. 다크 스테이지 가로폭 100% 중앙 정렬 곡 제목 & 가사 안내
                                val stageElapsedLabel = UnifiedStageTrack.formatDurationMmSs(songElapsedSec.roundToInt())
                                if (playlist.isEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "예약된 곡이 없습니다",
                                            color = Color.White,
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = "우측 검색창이나 스마트폰 앱에서 유튜브 곡을 추가해 보세요.",
                                            color = PleosCyanBlue,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                } else if (!twinState.isParked) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = currentTrack.title,
                                            color = Color.White,
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = "안전 운전을 위해 오디오 반주와 실시간 음원 스펙트럼 파형만 표시됩니다",
                                            color = PleosSlatePreview,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                } else if (currentTrack.isYouTube) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = currentTrack.title,
                                            color = Color.White,
                                            fontSize = 34.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = TextStyle(
                                                fontFamily = PretendardFontFamily,
                                                shadow = Shadow(
                                                    color = PleosCyanBlue.copy(alpha = 0.50f),
                                                    offset = Offset(0f, 0f),
                                                    blurRadius = 16f
                                                )
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = "${currentTrack.artist} · $stageElapsedLabel / ${currentTrack.durationLabel}",
                                            color = PleosCyanBlue,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = "♪ 반주 파형에 맞춰 자유롭게 노래해보세요 · 보컬 리버브 & 차량 4D 조명이 비트에 반응합니다",
                                            color = PleosSlatePreview,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                } else {
                                    // 내장 수록곡 재생 시: 100% 전체 폭 대형 싱크 가사 3줄 렌더링
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = currentLyric.koreanText,
                                            color = Color(0xFFFFFFFF),
                                            fontSize = 36.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = TextStyle(
                                                fontFamily = PretendardFontFamily,
                                                shadow = Shadow(
                                                    color = PleosCyanBlue.copy(alpha = 0.55f),
                                                    offset = Offset(0f, 0f),
                                                    blurRadius = 18f
                                                )
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = currentLyric.subText,
                                            color = PleosCyanBlue,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = nextLyric?.let { "다음 가사  ${it.koreanText}" } ?: "마지막 소절입니다",
                                            color = PleosSlatePreview,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                // 3. 실제 재생 중인 반주 음원(MR)의 실시간 48-Band FFT 오디오 스펙트럼 파형 비주얼라이저 (최소 높이 120.dp 이상 -> 132.dp, 가로폭 100%)
                                // - 가짜 사인파 애니메이션 완전 제거: WebAudio AnalyserNode.getByteFrequencyData / Android Visualizer FFT 대역 에너지와 1:1 직결
                                // - 일시정지 시 모든 바가 즉시 0으로 가라앉음
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(132.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val w = size.width
                                        val h = size.height
                                        val centerY = h * 0.64f

                                        val spectrumBars = 48
                                        val slotW = w / spectrumBars
                                        val barW = (slotW * 0.64f).coerceAtLeast(4.5f)
                                        val maxTopH = h * 0.58f

                                        // 중앙 기준선 글로우 (0 레벨 베이스라인)
                                        drawLine(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    PleosCyanBlue.copy(alpha = 0.50f),
                                                    PleosStagePurple.copy(alpha = 0.50f),
                                                    Color.Transparent
                                                )
                                            ),
                                            start = Offset(0f, centerY),
                                            end = Offset(w, centerY),
                                            strokeWidth = 1.5.dp.toPx()
                                        )

                                        for (i in 0 until spectrumBars) {
                                            // 일시정지(!isPlaying) 상태에서는 즉시 0f로 가라앉음
                                            val bandEnergy = if (isPlaying && playlist.isNotEmpty()) {
                                                musicSpectrumBands.getOrElse(i) { 0f }.coerceIn(0f, 1.0f)
                                            } else {
                                                0f
                                            }

                                            if (bandEnergy > 0.005f) {
                                                val topH = (maxTopH * bandEnergy).coerceAtLeast(2.dp.toPx())
                                                val bottomReflectH = topH * 0.42f
                                                val x = i * slotW + (slotW - barW) * 0.5f

                                                // 상단 메인 네온 FFT 스펙트럼 바 (Cyan -> Blue -> Purple)
                                                drawRoundRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            PleosCyanBlue,
                                                            PleosBlue,
                                                            PleosStagePurple
                                                        ),
                                                        startY = centerY - topH,
                                                        endY = centerY
                                                    ),
                                                    topLeft = Offset(x, centerY - topH),
                                                    size = Size(barW, topH),
                                                    cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                                                )

                                                // 상단 피크 캡 포인트
                                                if (bandEnergy > 0.18f) {
                                                    drawRoundRect(
                                                        color = Color.White.copy(alpha = 0.90f),
                                                        topLeft = Offset(x, (centerY - topH - 5.dp.toPx()).coerceAtLeast(0f)),
                                                        size = Size(barW, 3.dp.toPx()),
                                                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                                    )
                                                }

                                                // 하단 미러 리플렉션 바
                                                drawRoundRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            PleosStagePurple.copy(alpha = 0.42f),
                                                            PleosCyanBlue.copy(alpha = 0.04f)
                                                        ),
                                                        startY = centerY + 2f,
                                                        endY = centerY + 2f + bottomReflectH
                                                    ),
                                                    topLeft = Offset(x, centerY + 2f),
                                                    size = Size(barW, bottomReflectH),
                                                    cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. 스테이지 하단 진행 시간 바 (00:06 ━━━●────── 03:28) + 전체 음원 웨이브폼 실루엣 통합
                        val progressFraction = (songElapsedSec / currentTrack.durationSec.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = UnifiedStageTrack.formatDurationMmSs(songElapsedSec.roundToInt()),
                                color = PleosCyanBlue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height
                                    val midY = h * 0.5f
                                    val silhouetteBars = 72
                                    val slotW = w / silhouetteBars
                                    val barW = (slotW * 0.56f).coerceAtLeast(2.5f)
                                    val seed = currentTrack.id.hashCode()

                                    // 전체 음원의 웨이브폼 실루엣 (재생된 구간은 네온 시안/블루, 미재생 구간은 슬레이트 실루엣)
                                    for (i in 0 until silhouetteBars) {
                                        val barPos = i.toFloat() / (silhouetteBars - 1).coerceAtLeast(1)
                                        val pseudo = abs(sin((i * 0.63f) + (seed % 17))) * 0.55f +
                                            abs(sin((i * 0.27f) + (seed % 7))) * 0.45f
                                        val env = (1f - abs(barPos - 0.5f) * 0.45f).coerceIn(0.45f, 1f)
                                        val barH = (h * (0.24f + 0.72f * pseudo * env)).coerceIn(5.dp.toPx(), h)
                                        val x = i * slotW + (slotW - barW) * 0.5f
                                        val isPlayed = barPos <= progressFraction

                                        drawRoundRect(
                                            color = if (isPlayed) {
                                                PleosCyanBlue.copy(alpha = 0.85f)
                                            } else {
                                                Color.White.copy(alpha = 0.18f)
                                            },
                                            topLeft = Offset(x, midY - barH * 0.5f),
                                            size = Size(barW, barH),
                                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                                        )
                                    }

                                    // 중앙 진행 라인 트랙 (━━━●──────)
                                    val trackStroke = 3.dp.toPx()
                                    val playheadX = (w * progressFraction).coerceIn(6.dp.toPx(), (w - 6.dp.toPx()).coerceAtLeast(6.dp.toPx()))
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.24f),
                                        start = Offset(0f, midY),
                                        end = Offset(w, midY),
                                        strokeWidth = trackStroke
                                    )
                                    drawLine(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(PleosCyanBlue, PleosBlue, PleosStagePurple)
                                        ),
                                        start = Offset(0f, midY),
                                        end = Offset(playheadX, midY),
                                        strokeWidth = trackStroke
                                    )
                                    // 플레이헤드 포인터 (●)
                                    drawCircle(
                                        color = PleosCyanBlue.copy(alpha = 0.38f),
                                        radius = 8.dp.toPx(),
                                        center = Offset(playheadX, midY)
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 4.5.dp.toPx(),
                                        center = Offset(playheadX, midY)
                                    )
                                }
                            }

                            Text(
                                text = currentTrack.durationLabel,
                                color = PleosSlatePreview,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // 하단 가이드 음정 & 마이크/가이드 보컬 파형 바 (배경음악 재생 여부 isPlaying과 무관하게 실제 마이크 입력 또는 보컬 가이드 ON 시 상시 실시간 작동!)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PleosTileGray)
                        .border(1.dp, PleosBorderSubtle, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isRealMicActive = realMicRms > 1.5f
                    val isVocalStageActive = demoVocalSimEnabled || isRealMicActive
                    val (fallbackTargetHz, fallbackTargetNote) = if (isVocalStageActive) {
                        NativeVocalAudioEngine.resolveRealtimeGuidePitch(
                            trackId = currentTrack.id,
                            bpm = currentTrack.bpm,
                            elapsedSec = songElapsedSec,
                            lyricBaseHz = currentLyric.targetPitchHz,
                            keyShiftSemitones = keyShift
                        )
                    } else {
                        0f to "-"
                    }
                    val activeTargetHz = when {
                        demoVocalSimEnabled && vocalPitchHz > 65f -> vocalPitchHz
                        demoVocalSimEnabled -> fallbackTargetHz
                        isRealMicActive && realMicPitchHz > 65f -> realMicPitchHz
                        isRealMicActive && vocalPitchHz > 65f -> vocalPitchHz
                        isRealMicActive -> fallbackTargetHz
                        else -> 0f
                    }
                    val activeTargetNote = when {
                        demoVocalSimEnabled && vocalPitchHz > 65f && vocalNote != "-" -> vocalNote
                        demoVocalSimEnabled -> fallbackTargetNote
                        isRealMicActive && realMicNote.isNotBlank() && realMicNote != "-" -> realMicNote
                        isRealMicActive && vocalNote.isNotBlank() && vocalNote != "-" -> vocalNote
                        isRealMicActive -> fallbackTargetNote
                        else -> "-"
                    }
                    val displayVocalHz = if (vocalPitchHz > 65f) vocalPitchHz else activeTargetHz
                    val displayVocalNote = if (vocalPitchHz > 65f && vocalNote != "-") vocalNote else activeTargetNote
                    val displayVocalRms = if (vocalRms > 1.5f) vocalRms else if (demoVocalSimEnabled) 64f else 0f
                    val keyShiftLabel = if (keyShift > 0) "+$keyShift" else "$keyShift"
                    val micDisplayHz = (if (realMicPitchHz > 65f) realMicPitchHz else if (vocalPitchHz > 65f) vocalPitchHz else activeTargetHz.coerceAtLeast(262f)).roundToInt()
                    val micDisplayNote = when {
                        realMicNote.isNotBlank() && realMicNote != "-" -> realMicNote
                        vocalNote.isNotBlank() && vocalNote != "-" -> vocalNote
                        activeTargetNote != "-" -> activeTargetNote
                        else -> "C4"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                isRealMicActive ->
                                    "실시간 마이크 음정 $micDisplayNote (${micDisplayHz}Hz) · 키 $keyShiftLabel"
                                demoVocalSimEnabled ->
                                    "가이드 음정 $activeTargetNote (${activeTargetHz.roundToInt()}Hz) · 키 $keyShiftLabel"
                                else ->
                                    "가이드 음정 OFF · 키 $keyShiftLabel"
                            },
                            color = PleosTextDark,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = when {
                                demoVocalSimEnabled ->
                                    "현재 음정 $displayVocalNote (${displayVocalHz.roundToInt()}Hz) · 가이드 성량 ${displayVocalRms.roundToInt()}%"
                                isRealMicActive && (realMicPitchHz > 65f || vocalPitchHz > 65f) ->
                                    "현재 음정 $micDisplayNote (${micDisplayHz}Hz) · 마이크 ${realMicRms.coerceAtLeast(vocalRms).roundToInt()}%"
                                isRealMicActive ->
                                    "실시간 마이크 보컬 입력 감지 중 · 마이크 ${realMicRms.coerceAtLeast(vocalRms).roundToInt()}%"
                                else ->
                                    "마이크 대기 중 (노래나 목소리를 입력하면 파형이 즉시 반응합니다)"
                            },
                            color = if (isVocalStageActive) PleosGreen else PleosBlue,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PleosPanelWhite)
                    ) {
                        val w = size.width
                        val h = size.height
                        if (!isVocalStageActive) {
                            // [보컬 가이드 OFF] & 실제 마이크 입력 없음(Idle): 캔버스 정중앙에 얇고 연한 고정 대기선 하나만 완전히 멈춰 있게 표시
                            val idleCenterY = h * 0.5f
                            drawLine(
                                color = PleosBlue.copy(alpha = 0.18f),
                                start = Offset(0f, idleCenterY),
                                end = Offset(w, idleCenterY),
                                strokeWidth = 1.dp.toPx()
                            )
                        } else {
                            val minHz = 220f
                            val maxHz = 520f
                            val targetNorm = ((activeTargetHz - minHz) / (maxHz - minHz)).coerceIn(0.16f, 0.84f)
                            val targetY = h * (1f - targetNorm)

                            drawRect(
                                color = PleosBlue.copy(alpha = 0.08f),
                                topLeft = Offset(0f, (targetY - 12.dp.toPx()).coerceAtLeast(0f)),
                                size = Size(w, 24.dp.toPx())
                            )

                            // 목표 음정 기준선
                            drawLine(
                                color = PleosBlue.copy(alpha = 0.32f),
                                start = Offset(0f, targetY),
                                end = Offset(w, targetY),
                                strokeWidth = 2.dp.toPx()
                            )

                            // 36개의 둥근 세로 캡슐 바(drawRoundRect) 렌더링: 음높이(220~520Hz)에 따라 물결처럼 위아래 배치 + 성량에 비례해 10.dp ~ 44.dp 길이 조절
                            val count = pitchHistory.size.coerceAtLeast(32)
                            val slotW = w / count
                            val barW = (slotW - 6f).coerceAtLeast(6.5f)
                            for (idx in 0 until count) {
                                val hz = pitchHistory.getOrElse(idx) { 0f }
                                val slotRms = rmsHistory.getOrElse(idx) { 0f }
                                val shouldRenderBar = demoVocalSimEnabled || hz > 65f || slotRms > 1.5f
                                if (shouldRenderBar) {
                                    val effectiveHz = when {
                                        hz > 65f -> hz
                                        else -> (activeTargetHz + sin(idx * 0.62f) * 28f).coerceIn(minHz, maxHz)
                                    }
                                    val effectiveRms = when {
                                        slotRms > 1.5f -> slotRms
                                        demoVocalSimEnabled -> (54f + sin(idx * 0.78f) * 24f).coerceIn(25f, 85f)
                                        else -> 0f
                                    }
                                    val norm = ((effectiveHz - minHz) / (maxHz - minHz)).coerceIn(0.14f, 0.86f)
                                    val y = h * (1f - norm)
                                    val rmsRatio = ((effectiveRms.coerceIn(15f, 95f) - 15f) / 80f).coerceIn(0f, 1f)
                                    val barH = (10.dp.toPx() + rmsRatio * 34.dp.toPx()).coerceIn(10.dp.toPx(), 44.dp.toPx())
                                    val matched = abs(effectiveHz - activeTargetHz) < 48f
                                    val barColor = if (demoVocalSimEnabled) {
                                        if (idx % 2 == 0 || matched) Color(0xFF10B981) else Color(0xFF0EA5E9)
                                    } else {
                                        if (matched) Color(0xFF10B981) else PleosBlue
                                    }

                                    drawRoundRect(
                                        color = barColor,
                                        topLeft = Offset(
                                            idx * slotW + (slotW - barW) * 0.5f,
                                            (y - barH * 0.5f).coerceIn(2f, (h - barH - 2f).coerceAtLeast(2f))
                                        ),
                                        size = Size(barW, barH),
                                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. 하단 컨트롤 버튼 바: 높이 56.dp, 가로 패딩 20.dp, 글씨 크기 17.sp Bold
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PleosStageControlButton(
                        text = if (isPlaying) "일시정지" else "무대 시작",
                        icon = if (isPlaying) PleosMaterialIcons.Pause else PleosMaterialIcons.Play,
                        primary = true,
                        onClick = onTogglePlay
                    )
                    PleosStageControlButton(
                        text = "이전 곡",
                        icon = PleosMaterialIcons.Play,
                        primary = false,
                        onClick = onPrevTrack
                    )
                    PleosStageControlButton(
                        text = "다음 곡",
                        icon = PleosMaterialIcons.Play,
                        primary = false,
                        onClick = onNextTrack
                    )
                    PleosStageControlButton(
                        text = if (demoVocalSimEnabled) "보컬 가이드 ON" else "보컬 가이드",
                        icon = PleosMaterialIcons.Headphones,
                        primary = demoVocalSimEnabled,
                        onClick = onToggleDemoVocal
                    )
                    PleosStageControlButton(
                        text = "마이크 연결",
                        icon = PleosMaterialIcons.Mic,
                        primary = false,
                        onClick = onOpenPairing
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PleosStageControlButton(
                        text = "박수",
                        icon = PleosMaterialIcons.Celebration,
                        primary = false,
                        onClick = { onTriggerSfx("APPLAUSE") }
                    )
                    PleosStageControlButton(
                        text = "환호",
                        icon = PleosMaterialIcons.CheerSparkle,
                        primary = false,
                        onClick = { onTriggerSfx("CHEER") }
                    )
                    PleosStageControlButton(
                        text = "탬버린",
                        icon = PleosMaterialIcons.Tambourine,
                        primary = false,
                        onClick = { onTriggerSfx("TAMBOURINE") }
                    )
                }
            }
        }

        // ====================================================================
        // 우측 상시 노출 '통합 플레이리스트 & 선곡' 패널 (400.dp 고정 너비)
        // ====================================================================
        Surface(
            modifier = Modifier
                .width(400.dp)
                .fillMaxHeight(),
            shape = RoundedCornerShape(20.dp),
            color = PleosTileGray,
            border = BorderStroke(1.dp, PleosBorderSubtle),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. 패널 상단 헤더 + [반주 소스: YouTube 원곡 / 내장 신스 MR] 하이브리드 토글
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "통합 플레이리스트 & 선곡",
                        color = PleosTextDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.clickable { onToggleBackingSource() },
                            shape = RoundedCornerShape(50),
                            color = if (useSynthMrFallback) PleosStagePurple else PleosBlue,
                            shadowElevation = 0.dp
                        ) {
                            Text(
                                text = if (useSynthMrFallback) "반주 소스: 내장 신스 MR" else "반주 소스: YouTube 원곡",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PleosBlue.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "총 ${playlist.size}곡",
                                color = PleosBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // 2. 모던 캡슐형 검색/링크 추가 바 (테두리 제거, #F1F5F9 배경, RoundedCornerShape(16.dp), 좌측 돋보기/링크 아이콘 내장)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PleosCapsuleSearchField(
                        value = addTrackInput,
                        onValueChange = onAddTrackInputChange,
                        placeholder = "유튜브 링크(URL) 또는 곡명 검색어 입력",
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )

                    Surface(
                        modifier = Modifier
                            .height(48.dp)
                            .clickable {
                                if (addTrackInput.isNotBlank()) {
                                    onAddTrack(addTrackInput)
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
                                text = "+ 추가",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // 3. 반주 음량 / 에코 볼륨 조절 컨트롤
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PleosCompactMiniStepper(
                        modifier = Modifier.weight(1f),
                        label = "반주",
                        valueText = "${backingVolume}%",
                        onMinus = { onBackingVolumeChange(backingVolume - 10) },
                        onPlus = { onBackingVolumeChange(backingVolume + 10) }
                    )
                    PleosCompactMiniStepper(
                        modifier = Modifier.weight(1f),
                        label = "에코",
                        valueText = "${echoLevel}%",
                        onMinus = { onEchoChange(echoLevel - 10) },
                        onPlus = { onEchoChange(echoLevel + 10) }
                    )
                }

                // 4. 통합 플레이리스트 스크롤 목록 (유튜브 곡 + 내장 수록곡 통합)
                val displayedList = remember(playlist.toList(), isDevMode, currentTrack.id) {
                    playlist.toList()
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (displayedList.isEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = PleosPanelWhite,
                            border = BorderStroke(1.dp, PleosBorderSubtle),
                            shadowElevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "예약된 곡이 없습니다.",
                                    color = PleosTextDark,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "예약된 곡이 없습니다. 상단 검색창이나 스마트폰 앱에서 유튜브 곡을 추가해 보세요.",
                                    color = PleosTextMuted,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    val rowStepPx = 95f
                    displayedList.forEachIndexed { index, track ->
                        val actualIdx = playlist.indexOfFirst { it.id == track.id }
                        val isCurrent = track.id == currentTrack.id
                        val isDragged = draggedTrackId == track.id

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragged) 10f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragged) dragOffsetY else 0f
                                    scaleX = if (isDragged) 1.02f else 1f
                                    scaleY = if (isDragged) 1.02f else 1f
                                }
                                .pointerInput(track.id) {
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
                                .clickable { onSelectAndPlayTrack(track) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isCurrent) Color(0xFFEFF6FF) else PleosPanelWhite,
                            border = BorderStroke(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = if (isCurrent) PleosBlue else PleosBorderSubtle
                            ),
                            shadowElevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
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
                                        // 순번 또는 현재 재생 중 이퀄라이저 애니메이션
                                        if (isCurrent && isPlaying) {
                                            PleosAnimatedEqualizerBars(
                                                barColor = PleosBlue,
                                                modifier = Modifier.size(width = 20.dp, height = 20.dp)
                                            )
                                        } else {
                                            Text(
                                                text = "#${index + 1}",
                                                color = if (isCurrent) PleosBlue else PleosTextMuted,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.width(20.dp)
                                            )
                                        }

                                        // 썸네일 44.dp x 32.dp로 축소하여 곡 제목 노출 영역 극대화
                                        YouTubeThumbnailImage(
                                            videoId = track.videoId,
                                            durationLabel = track.durationLabel,
                                            modifier = Modifier
                                                .width(44.dp)
                                                .height(32.dp)
                                        )

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
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
                                                            fontWeight = FontWeight.Black,
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = track.title,
                                                    color = PleosTextDark,
                                                    fontSize = 17.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }

                                            Text(
                                                text = "${track.artist} · ${track.durationLabel}",
                                                color = if (isCurrent) PleosBlue else PleosTextMuted,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // 우측 순서 변경(▲/▼) 및 삭제(✕) 컨트롤 (컴팩트 24.dp)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable {
                                                    if (actualIdx > 0) onMoveTrack(actualIdx, actualIdx - 1)
                                                },
                                            shape = RoundedCornerShape(6.dp),
                                            color = PleosTrackBg,
                                            shadowElevation = 0.dp
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "▲",
                                                    color = PleosTextDark,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Surface(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable {
                                                    if (actualIdx in 0 until playlist.lastIndex) {
                                                        onMoveTrack(actualIdx, actualIdx + 1)
                                                    }
                                                },
                                            shape = RoundedCornerShape(6.dp),
                                            color = PleosTrackBg,
                                            shadowElevation = 0.dp
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "▼",
                                                    color = PleosTextDark,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Surface(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clickable { onRemoveTrack(track.id) },
                                            shape = RoundedCornerShape(6.dp),
                                            color = PleosTrackBg,
                                            shadowElevation = 0.dp
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "✕",
                                                    color = PleosTextMuted,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                // 현재 재생 중인 카드 하단에 실시간 프로그레스 바 표시
                                if (isCurrent) {
                                    val prog = (songElapsedSec / track.durationSec.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(PleosTrackBg)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(prog)
                                                .background(PleosBlue)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 모던 캡슐형 검색/링크 입력 바 (투박한 OutlinedTextField 테두리 제거, #F1F5F9 배경, RoundedCornerShape(16.dp), 좌측 돋보기 아이콘 내장)
 */
@Composable
private fun PleosCapsuleSearchField(
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
                        fontSize = 14.sp,
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
                        fontFamily = PretendardFontFamily,
                        color = PleosTextDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    cursorBrush = SolidColor(PleosBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 우측 상단 DEV / PROD 모드 전환 스위치 (중복 [DEV] 배지 제거, 단일 토글 유지)
 */
@Composable
private fun PleosDevProdHeaderBadge(
    isDevMode: Boolean,
    onToggleDevMode: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(40.dp)
            .clickable { onToggleDevMode() },
        shape = RoundedCornerShape(12.dp),
        color = PleosTrackBg,
        border = BorderStroke(1.dp, if (isDevMode) PleosDevRed.copy(alpha = 0.5f) else PleosBorderSubtle),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (!isDevMode) PleosPanelWhite else Color.Transparent)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PROD",
                    color = if (!isDevMode) PleosBlue else PleosTextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isDevMode) PleosDevRed else Color.Transparent)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "DEV",
                    color = if (isDevMode) Color.White else PleosTextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * 실시간 움직이는 4바 오디오 이퀄라이저 아이콘
 */
@Composable
private fun PleosAnimatedEqualizerBars(
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

    Canvas(modifier = modifier) {
        val barCount = 4
        val spacing = size.width / barCount
        val barW = (spacing * 0.62f).coerceAtLeast(3f)
        for (i in 0 until barCount) {
            val ratio = (0.28f + 0.72f * abs(sin(phase + i * 1.1f))).coerceIn(0.2f, 1f)
            val bh = size.height * ratio
            drawRoundRect(
                color = barColor,
                topLeft = Offset(i * spacing + (spacing - barW) / 2f, size.height - bh),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
    }
}

@Composable
private fun PleosCompactMiniStepper(
    modifier: Modifier = Modifier,
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Surface(
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = PleosPanelWhite,
        border = BorderStroke(1.dp, PleosBorderSubtle),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = PleosTextDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = valueText,
                    color = PleosBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onMinus() },
                    shape = CircleShape,
                    color = PleosTileGray,
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "−",
                            color = PleosTextDark,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onPlus() },
                    shape = CircleShape,
                    color = PleosTileGray,
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "+",
                            color = PleosTextDark,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 중앙 무대 상단 차량 하드웨어 상태 요약 반투명 글래스 배지 (15.sp)
 */
@Composable
private fun PleosGlassHardwareChip(
    text: String,
    dotColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(dotColor, CircleShape)
            )
            Text(
                text = text,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * 하단 컨트롤 버튼 바: 높이 56.dp, 글씨 크기 17.sp Bold
 */
@Composable
private fun PleosStageControlButton(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(56.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = if (primary) PleosBlue else PleosTileGray,
        border = BorderStroke(1.dp, if (primary) PleosBlue else PleosBorderSubtle),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = if (primary) Color.White else PleosTextDark,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                color = if (primary) Color.White else PleosTextDark,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

// ============================================================================
// 3B. PANE 2: PLEOS QUICK SETTINGS 4D HARDWARE TILE GRID
// ============================================================================
@Composable
private fun PleosQuickSettings4DPane(
    twinState: VehicleDigitalTwinState,
    echoLevel: Int,
    mediaVolume: Int,
    isDevMode: Boolean,
    onToggleDevMode: () -> Unit,
    onToggleWindows: () -> Unit,
    onTriggerGreenroom: () -> Unit,
    onTriggerEncore: () -> Unit,
    onSelectGear: (GearPosition) -> Unit,
    onEchoChange: (Int) -> Unit,
    onVolumeChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "차량 무대 환경 설정 (Pleos 4D SDK)",
                    color = PleosTextDark,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "창문 방음 부스 · 앰비언트 라이트 · 그린룸 공조 및 주행 안전 인터락을 제어합니다",
                    color = PleosTextMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            PleosDevProdHeaderBadge(
                isDevMode = isDevMode,
                onToggleDevMode = onToggleDevMode
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PleosQuickTile(
                modifier = Modifier.weight(1f),
                title = "차음 윈도우",
                subtitle = if (twinState.allWindowsClosed) "창문 4개 전체 닫힘" else "일부 열림",
                active = twinState.allWindowsClosed,
                icon = PleosMaterialIcons.Car,
                onClick = onToggleWindows
            )
            PleosQuickTile(
                modifier = Modifier.weight(1f),
                title = "안내음 차단",
                subtitle = if (twinState.tollHiPassMuted) "하이패스 음소거" else "일반 안내",
                active = twinState.tollHiPassMuted,
                icon = PleosMaterialIcons.Tune,
                onClick = {}
            )
            PleosQuickTile(
                modifier = Modifier.weight(1f),
                title = "쾌적 공조",
                subtitle = if (twinState.greenroomCareActive) "통풍 ${twinState.seatVentilationLevel}단 · 외기" else "자동 대기",
                active = twinState.greenroomCareActive,
                icon = PleosMaterialIcons.CheerSparkle,
                onClick = onTriggerGreenroom
            )
            PleosQuickTile(
                modifier = Modifier.weight(1f),
                title = "앙코르 효과",
                subtitle = if (twinState.encoreFinaleActive) "피날레 조명 작동" else "90점 달성 시",
                active = twinState.encoreFinaleActive,
                icon = PleosMaterialIcons.Celebration,
                onClick = onTriggerEncore
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PleosTrackBg)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val gearItems = listOf(
                GearPosition.P to "P 주차 (전체 무대 활성)",
                GearPosition.D to "D 주행 (세이프 오디오 모드)",
                GearPosition.N to "N 중립",
                GearPosition.R to "R 후진"
            )
            gearItems.forEach { (gear, label) ->
                val selected = twinState.gearPosition == gear
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) PleosPanelWhite else Color.Transparent)
                        .clickable { onSelectGear(gear) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) PleosGreen else PleosTextDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        PleosHorizontalTrackBar(
            label = "공간감",
            valueText = "$echoLevel%",
            progress = echoLevel / 100f,
            actionLabel = "기본값",
            onMinus = { onEchoChange(echoLevel - 10) },
            onPlus = { onEchoChange(echoLevel + 10) },
            onAction = { onEchoChange(80) }
        )

        PleosHorizontalTrackBar(
            label = "미디어",
            valueText = "$mediaVolume",
            progress = mediaVolume / 15f,
            actionLabel = "음소거",
            onMinus = { onVolumeChange(mediaVolume - 1) },
            onPlus = { onVolumeChange(mediaVolume + 1) },
            onAction = { onVolumeChange(0) }
        )
    }
}

@Composable
private fun PleosQuickTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    active: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(126.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = if (active) PleosTileActiveWhite else PleosTileGray,
        border = BorderStroke(1.5.dp, if (active) PleosBlue else PleosBorderSubtle),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    color = PleosTextDark,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = subtitle,
                    color = if (active) PleosBlue else PleosTextMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(5.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (active) PleosGreen else Color(0xFFC5C9D2))
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (active) PleosBlue.copy(alpha = 0.14f) else Color(0xFFE0E3E9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (active) PleosBlue else PleosTextDark,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PleosHorizontalTrackBar(
    label: String,
    valueText: String,
    progress: Float,
    actionLabel: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PleosTileGray)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = label,
                color = PleosTextDark,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.width(80.dp)
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PleosPanelWhite)
                    .clickable { onMinus() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "−",
                    color = PleosTextDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PleosSliderFillGray)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0.12f, 1f))
                        .background(PleosPanelWhite)
                )
                Text(
                    text = valueText,
                    color = PleosTextDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PleosPanelWhite)
                    .clickable { onPlus() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = PleosTextDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Box(
            modifier = Modifier
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PleosTileGray)
                .border(1.dp, PleosBorderSubtle, RoundedCornerShape(16.dp))
                .clickable { onAction() }
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = actionLabel,
                color = PleosTextDark,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================================
// 3C. PANE 3: SMARTPHONE MIC PAIRING (PROD vs DEV MODE SEPARATION)
// ============================================================================
@Composable
private fun PleosPairingPane(
    qrBitmap: androidx.compose.ui.graphics.ImageBitmap,
    roomPin: String,
    lanUrl: String,
    emulatorUrl: String,
    micConnected: Boolean,
    micDeviceName: String,
    connectedMicCount: Int,
    demoVocalSimEnabled: Boolean,
    isDevMode: Boolean,
    onToggleDevMode: () -> Unit,
    onToggleDemoSim: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "carRadarPulse")
    val radarPhase by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarPhase"
    )

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .background(PleosTileGray)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PleosPanelWhite)
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "Pairing QR Code",
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "스마트폰 카메라 QR 스캔",
                color = PleosTextDark,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "차량 자동 탐색으로 즉시 연결",
                color = PleosBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "차량 스마트 링크 (마이크 연결)",
                        color = PleosTextDark,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    PleosDevProdHeaderBadge(
                        isDevMode = isDevMode,
                        onToggleDevMode = onToggleDevMode
                    )
                }

                Text(
                    text = "스마트폰 앱에서 QR 코드를 스캔하거나 아래 4자리 PIN 번호를 입력하면 자동 탐색으로 즉시 연결됩니다.",
                    color = PleosTextMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                // 4-Digit PIN Cards + Auto-Discovery Graphic Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        roomPin.padEnd(4, '0').take(4).forEach { digit ->
                            Box(
                                modifier = Modifier
                                    .size(width = 72.dp, height = 80.dp)
                                    .shadow(3.dp, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(PleosPanelWhite)
                                    .border(2.5.dp, PleosBlue, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = digit.toString(),
                                    color = PleosBlue,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    // 세련된 차량 자동 탐색 상태 그래픽 카드
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.5.dp, if (micConnected) PleosGreen else PleosBlue.copy(alpha = 0.45f)),
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(46.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val c = Offset(size.width / 2f, size.height / 2f)
                                    val maxR = size.minDimension / 2f
                                    drawCircle(
                                        color = (if (micConnected) PleosGreen else PleosBlue)
                                            .copy(alpha = (1f - radarPhase) * 0.45f),
                                        radius = maxR * (0.4f + 0.6f * radarPhase),
                                        center = c,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                    drawCircle(
                                        color = if (micConnected) PleosGreen else PleosBlue,
                                        radius = 7.dp.toPx(),
                                        center = c
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = if (micConnected) "● 스마트 마이크 연결 완료 ($micDeviceName)" else "차량 자동 탐색 신호 송출 중",
                                    color = if (micConnected) PleosGreen else PleosTextDark,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (micConnected) "연결된 마이크 ${connectedMicCount}대 · 스마트폰 마이크 실시간 연결됨" else "스마트폰에서 PIN $roomPin 입력 시 즉시 매칭됩니다",
                                    color = PleosTextMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // DEV 모드 전용: 🔴 [DEV] 네트워크 수동 설정 섹션 (PROD 모드에서는 완전히 숨김)
            if (isDevMode) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = PleosDevRedSoft,
                    border = BorderStroke(1.5.dp, PleosDevRed.copy(alpha = 0.55f)),
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = PleosDevRed,
                                    shadowElevation = 0.dp
                                ) {
                                    Text(
                                        text = "DEV",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "네트워크 수동 설정 및 에뮬레이터 브릿지",
                                    color = PleosDevRed,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Surface(
                                modifier = Modifier.clickable { onToggleDemoSim() },
                                shape = RoundedCornerShape(8.dp),
                                color = if (demoVocalSimEnabled) PleosDevRed else PleosPanelWhite,
                                border = BorderStroke(1.dp, PleosDevRed)
                            ) {
                                Text(
                                    text = if (demoVocalSimEnabled) "DEV 보컬 시뮬레이터 ON" else "DEV 보컬 시뮬레이터 켜기",
                                    color = if (demoVocalSimEnabled) Color.White else PleosDevRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                        Text(
                            text = "• 동일 Wi-Fi 다이렉트 WebSocket: $lanUrl",
                            color = PleosTextDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "• 로컬 에뮬레이터 브릿지 주소: $emulatorUrl (UDP 자동 탐색 포트: 8081)",
                            color = PleosTextDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3D. PANE 4: SOUND & REVERB SETTINGS
// ============================================================================
@Composable
private fun PleosSoundReverbPane(
    backingVolume: Int,
    echoLevel: Int,
    keyShift: Int,
    mediaVolume: Int,
    isDevMode: Boolean,
    onToggleDevMode: () -> Unit,
    onBackingVolumeChange: (Int) -> Unit,
    onEchoChange: (Int) -> Unit,
    onKeyShiftChange: (Int) -> Unit,
    onVolumeChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "사운드 및 공간 음향",
                    color = PleosTextDark,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "스마트폰과 1:1 실시간 동기화되는 반주 음량 · 콘서트홀 리버브(공간감) · 마이크 음정을 조절합니다.",
                    color = PleosTextMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            PleosDevProdHeaderBadge(
                isDevMode = isDevMode,
                onToggleDevMode = onToggleDevMode
            )
        }

        PleosHorizontalTrackBar(
            label = "반주 음량",
            valueText = "$backingVolume%",
            progress = backingVolume / 100f,
            actionLabel = "기본값",
            onMinus = { onBackingVolumeChange(backingVolume - 10) },
            onPlus = { onBackingVolumeChange(backingVolume + 10) },
            onAction = { onBackingVolumeChange(85) }
        )

        PleosHorizontalTrackBar(
            label = "공간감",
            valueText = "$echoLevel%",
            progress = echoLevel / 100f,
            actionLabel = "기본값",
            onMinus = { onEchoChange(echoLevel - 10) },
            onPlus = { onEchoChange(echoLevel + 10) },
            onAction = { onEchoChange(80) }
        )

        PleosHorizontalTrackBar(
            label = "키 조절",
            valueText = if (keyShift > 0) "+$keyShift" else "$keyShift",
            progress = (keyShift + 6) / 12f,
            actionLabel = "원키",
            onMinus = { onKeyShiftChange(keyShift - 1) },
            onPlus = { onKeyShiftChange(keyShift + 1) },
            onAction = { onKeyShiftChange(0) }
        )

        PleosHorizontalTrackBar(
            label = "미디어",
            valueText = "$mediaVolume",
            progress = mediaVolume / 15f,
            actionLabel = "음소거",
            onMinus = { onVolumeChange(mediaVolume - 1) },
            onPlus = { onVolumeChange(mediaVolume + 1) },
            onAction = { onVolumeChange(0) }
        )
    }
}
