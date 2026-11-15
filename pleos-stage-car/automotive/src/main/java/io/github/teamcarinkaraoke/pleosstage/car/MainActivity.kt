package io.github.teamcarinkaraoke.pleosstage.car

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.media.browse.MediaBrowser
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
import io.github.teamcarinkaraoke.pleosstage.car.media.PleosMediaSessionBridge
import io.github.teamcarinkaraoke.pleosstage.car.media.PleosStageMediaBrowserService
import io.github.teamcarinkaraoke.pleosstage.car.model.KaraokeScoreCalculator
import io.github.teamcarinkaraoke.pleosstage.car.model.KaraokeScoreResult
import io.github.teamcarinkaraoke.pleosstage.car.model.QrCodeGenerator
import io.github.teamcarinkaraoke.pleosstage.car.model.VocalScoreAccumulator
import io.github.teamcarinkaraoke.pleosstage.car.sdk.GearPosition
import io.github.teamcarinkaraoke.pleosstage.car.sdk.LightingControlMode
import io.github.teamcarinkaraoke.pleosstage.car.sdk.PleosVehicleManager
import io.github.teamcarinkaraoke.pleosstage.car.sdk.VehicleDigitalTwinState
import io.github.teamcarinkaraoke.pleosstage.car.server.PleosEmbeddedSignalingServer
import io.github.teamcarinkaraoke.pleosstage.car.ui.PleosSdkUnitTestPane
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
val PleosBlue = Color(0xFF2563EB)             // Pleos Active Sidebar & Primary Accent Blue (#2563EB)
val PleosGreen = Color(0xFF1BB55C)            // Pleos Active Indicator Bar & READY Green
val PleosAmber = Color(0xFFF59E0B)            // Safety Interlock Warning Amber
val PleosDevRed = Color(0xFFDC2626)           // DEV Mode Highlight Badge Red
val PleosDevRedSoft = Color(0xFFFEF2F2)       // DEV Mode Soft Surface Tint
val PleosStagePurple = Color(0xFF7C4DFF)      // Ambient / Stage Accent Purple
val PleosStageRose = Color(0xFFFF2E93)        // Climax High-Note Rose
val PleosStageNavyStart = Color(0xFF0F172A)   // Dark Concert Hall Navy Top (#0F172A)
val PleosStageCharcoalEnd = Color(0xFF1E293B) // Dark Concert Hall Charcoal Bottom (#1E293B)
val PleosCyanBlue = Color(0xFF38BDF8)         // Sub Guide Text Cyan Blue (#38BDF8)
val PleosPitchDeepBlue = Color(0xFF1D4ED8)    // Deep Royal Blue for Active Vocal Pitch Text (#1D4ED8)
val PleosVocalCyanBlue = Color(0xFF0284C7)    // High-Visibility Cyan Blue for Vocal Status (#0284C7)
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

    val SdkLab: ImageVector by lazy {
        ImageVector.Builder(
            name = "SdkLab",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 3f)
                horizontalLineTo(15f)
                verticalLineTo(5f)
                horizontalLineTo(14f)
                verticalLineTo(9.5f)
                lineTo(19.6f, 18.2f)
                curveTo(20.2f, 19.2f, 19.5f, 20.5f, 18.3f, 20.5f)
                horizontalLineTo(5.7f)
                curveTo(4.5f, 20.5f, 3.8f, 19.2f, 4.4f, 18.2f)
                lineTo(10f, 9.5f)
                verticalLineTo(5f)
                horizontalLineTo(9f)
                verticalLineTo(3f)
                close()
                moveTo(11.3f, 11f)
                lineTo(8.1f, 16f)
                horizontalLineTo(15.9f)
                lineTo(12.7f, 11f)
                horizontalLineTo(11.3f)
                close()
            }
        }.build()
    }
}

private fun formatEnglishPitchNote(rawNote: String): String {
    val clean = rawNote.trim().replace('♯', '#').uppercase(Locale.US)
    if (clean.isEmpty() || clean == "-") return ""
    return clean
}

private fun formatConsumerKeyLabel(keyShift: Int): String {
    return when {
        keyShift == 0 -> "기본 키"
        keyShift > 0 -> "+${keyShift} 키"
        else -> "${keyShift} 키"
    }
}

private fun formatConsumerPlaybackRate(rate: Float): String {
    val hundredths = (rate * 100f).roundToInt()
    return if (hundredths % 10 == 0) {
        String.format(Locale.US, "%.1f배", rate)
    } else {
        String.format(Locale.US, "%.2f배", rate)
    }
}

/**
 * Unified Sidebar Menu:
 * `유튜브 반주` 및 `추천 선곡` 메뉴를 `라이브 스테이지` 단일 통합 화면으로 합쳐 파편화를 제거.
 * `SDK_UNIT_TEST`는 `DEV` 모드일 때만 5번째 탭으로 노출됨.
 */
enum class PleosSidebarMenu(val title: String) {
    STAGE_LIVE("라이브 스테이지"),
    VEHICLE_4D("차량 무대 모드"),
    MIC_PAIRING("스마트 링크"),
    SOUND_REVERB("사운드 설정"),
    SDK_UNIT_TEST("🧪 SDK 단위 테스트");

    val icon: ImageVector
        get() = when (this) {
            STAGE_LIVE -> PleosMaterialIcons.Mic
            VEHICLE_4D -> PleosMaterialIcons.Car
            MIC_PAIRING -> PleosMaterialIcons.QrLink
            SOUND_REVERB -> PleosMaterialIcons.Tune
            SDK_UNIT_TEST -> PleosMaterialIcons.SdkLab
        }
}

data class GleoActionRequest(
    val action: String,
    val paramJson: JSONObject
)

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "PleosMainActivity"
        private const val GLEO_RESPONSE_ACTION = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
        private const val SUSPEND_PREFS_NAME = "pleos_stage_suspend_prefs"
    }

    private lateinit var vehicleManager: PleosVehicleManager
    private lateinit var embeddedServer: PleosEmbeddedSignalingServer
    private var initialAudioFocusRequest: AudioFocusRequest? = null
    private var mediaBrowser: MediaBrowser? = null

    private val _isAudioDucked = MutableStateFlow(false)
    val isAudioDucked: StateFlow<Boolean> = _isAudioDucked.asStateFlow()

    private val _gleoActions = MutableSharedFlow<GleoActionRequest>(replay = 1, extraBufferCapacity = 16)
    val gleoActions: SharedFlow<GleoActionRequest> = _gleoActions.asSharedFlow()

    @Volatile
    private var lastInternalAudioStartMs: Long = 0L

    var onExternalAudioFocusLossCallback: (() -> Unit)? = null
    var onSuspendOrExitStopCallback: (() -> Unit)? = null
    var onBackToDefaultStageCallback: (() -> Boolean)? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Navigation voice guidance etc.: do NOT stop playback; duck YouTube & Vocal volume to 30%
                _isAudioDucked.value = true
                Log.i("PleosAudioFocus", "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Ducking YouTube & Vocal volume to 30%")
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                // Restore original 100% volume (never auto-resume if stopped by Suspend)
                _isAudioDucked.value = false
                Log.i("PleosAudioFocus", "AUDIOFOCUS_GAIN -> Restored YouTube & Vocal volume to 100%")
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS -> {
                val elapsedSinceInternalStart = System.currentTimeMillis() - lastInternalAudioStartMs
                if (elapsedSinceInternalStart <= 1500L || hasWindowFocus()) {
                    // Ignore internal WebView self-focus transition when YouTube <video> starts playing
                    Log.i(
                        "PleosAudioFocus",
                        "Ignored internal WebView self-focus transition (focusChange=$focusChange, elapsed=${elapsedSinceInternalStart}ms)"
                    )
                } else {
                    _isAudioDucked.value = false
                    Log.i("PleosAudioFocus", "External AudioFocus loss ($focusChange) -> Pausing stage playback")
                    runOnUiThread {
                        onExternalAudioFocusLossCallback?.invoke()
                    }
                }
            }
        }
    }

    private val suspendPowerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == Intent.ACTION_SCREEN_OFF ||
                action == Intent.ACTION_SHUTDOWN ||
                action.contains("SUSPEND", ignoreCase = true)
            ) {
                Log.i(TAG, "Vehicle Suspend / Power off detected ($action) -> stopping audio & abandoning focus")
                handleSuspendOrAppExit(reason = action)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Request initial USAGE_MEDIA AudioFocus first so CarMediaService recognizes active MediaSession
        requestInitialMediaAudioFocus(markInternalStart = true)

        // 2. Start & bind PleosStageMediaBrowserService so AAOS Driving View Media Widget & Steering Wheel keys stay active
        startAndConnectMediaBrowserService()

        // 3. Register Vehicle Suspend / Screen-Off BroadcastReceiver
        registerSuspendReceiver()

        // 4. Register CCP Hardware Back Key Policy: return to STAGE_LIVE if in sub-tab, or moveTaskToBack(true) at top-level
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val consumedBySubTab = onBackToDefaultStageCallback?.invoke() ?: false
                    if (!consumedBySubTab) {
                        Log.i(TAG, "CCP Back hardware key at top-level -> moveTaskToBack(true)")
                        moveTaskToBack(true)
                    }
                }
            }
        )

        vehicleManager = PleosVehicleManager(applicationContext)
        embeddedServer = PleosEmbeddedSignalingServer(applicationContext, initialPin = "4829").apply {
            start()
        }

        // 5. Inspect initial Intent for Gleo AI voice action calls
        handleGleoIntentIfPresent(intent)

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
                    val ducked by isAudioDucked.collectAsState()
                    PleosConnectInfotainmentScreen(
                        vehicleManager = vehicleManager,
                        embeddedServer = embeddedServer,
                        isAudioDucked = ducked,
                        gleoActions = gleoActions,
                        onRequestAudioFocus = { requestInitialMediaAudioFocus(markInternalStart = true) },
                        onMarkInternalAudioStart = { markInternalAudioStart() },
                        onAbandonAudioFocusAndSaveState = { trackId, elapsedSec, backingVol, echo, key ->
                            savePlaybackStateToPrefs(trackId, elapsedSec, backingVol, echo, key)
                            abandonMediaAudioFocus()
                        },
                        onSendGleoResponse = { fnName, status, msg, data ->
                            sendGleoActionResponseBroadcast(fnName, status, msg, data)
                        },
                        onRegisterLifecycleCallbacks = { onExtLoss, onSuspendStop, onBackToStage ->
                            onExternalAudioFocusLossCallback = onExtLoss
                            onSuspendOrExitStopCallback = onSuspendStop
                            onBackToDefaultStageCallback = onBackToStage
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGleoIntentIfPresent(intent)
    }

    private fun startAndConnectMediaBrowserService() {
        try {
            PleosMediaSessionBridge.getOrCreateSession(applicationContext)
            val svcIntent = Intent(applicationContext, PleosStageMediaBrowserService::class.java)
            startService(svcIntent)

            val browser = MediaBrowser(
                applicationContext,
                ComponentName(applicationContext, PleosStageMediaBrowserService::class.java),
                object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() {
                        Log.i(TAG, "MediaBrowser connected to PleosStageMediaBrowserService")
                    }
                },
                null
            )
            browser.connect()
            mediaBrowser = browser
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start/connect PleosStageMediaBrowserService: ${e.message}")
        }
    }

    private fun registerSuspendReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SHUTDOWN)
                addAction("android.car.intent.action.SUSPEND")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(suspendPowerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(suspendPowerReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "registerSuspendReceiver fallback: ${e.message}")
        }
    }

    fun markInternalAudioStart() {
        lastInternalAudioStartMs = System.currentTimeMillis()
    }

    /**
     * Parses Gleo AI Action Intent (`PLAY_KARAOKE_SONG`, `SET_STAGE_SOUND_MODE`, `CONTROL_STAGE_PLAYBACK`)
     * and dispatches it to the stage controller.
     */
    private fun handleGleoIntentIfPresent(intent: Intent?) {
        val rawAction = intent?.action ?: return
        val normalizedAction = rawAction.substringAfterLast('.')
        val supportedActions = setOf(
            "PLAY_KARAOKE_SONG",
            "SET_STAGE_SOUND_MODE",
            "CONTROL_STAGE_PLAYBACK"
        )
        val matchedAction = when {
            rawAction in supportedActions -> rawAction
            normalizedAction in supportedActions -> normalizedAction
            else -> return
        }

        val rawParamStr = (intent.getStringExtra("ACTION_PARAM")
            ?: intent.getStringExtra("action_param")
            ?: "{}")
            .replace("\\\"", "\"")
            .trim()
        val paramJson = try {
            JSONObject(rawParamStr)
        } catch (_: Exception) {
            // Fallback parser if shell stripped quotes (e.g. {command:PLAY} or {searchTerm:IU})
            val fallback = JSONObject()
            val inner = rawParamStr.removePrefix("{").removeSuffix("}")
            inner.split(",").forEach { pair ->
                val idx = pair.indexOf(':')
                if (idx > 0) {
                    val k = pair.substring(0, idx).trim().trim('"', '\'')
                    val v = pair.substring(idx + 1).trim().trim('"', '\'')
                    if (k.isNotEmpty() && v.isNotEmpty()) {
                        if (v.equals("true", ignoreCase = true) || v.equals("false", ignoreCase = true)) {
                            fallback.put(k, v.toBoolean())
                        } else {
                            fallback.put(k, v)
                        }
                    }
                }
            }
            fallback
        }

        // Also copy any direct extras if passed outside ACTION_PARAM
        listOf("searchTerm", "soundPreset", "command").forEach { key ->
            if (!paramJson.has(key)) {
                val extraVal = intent.getStringExtra(key)
                if (!extraVal.isNullOrBlank()) {
                    paramJson.put(key, extraVal)
                }
            }
        }
        if (!paramJson.has("vocalGuide") && intent.hasExtra("vocalGuide")) {
            paramJson.put("vocalGuide", intent.getBooleanExtra("vocalGuide", false))
        }

        Log.i(TAG, "Received Gleo AI Intent: action=$matchedAction, ACTION_PARAM=$paramJson")
        if (::vehicleManager.isInitialized) {
            vehicleManager.recordReceivedGleoIntent(matchedAction, paramJson.toString())
        }
        _gleoActions.tryEmit(GleoActionRequest(matchedAction, paramJson))
    }

    /**
     * Broadcasts `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE` back to Pleos Gleo AI.
     */
    private fun sendGleoActionResponseBroadcast(
        functionName: String,
        status: String,
        message: String,
        data: JSONObject
    ) {
        try {
            val responseJson = JSONObject().apply {
                put("functionName", functionName)
                put("status", status)
                put("message", message)
                put("mode", "beep")
                put("data", data)
            }.toString()

            val responseIntent = Intent(GLEO_RESPONSE_ACTION).apply {
                putExtra("packageName", packageName)
                putExtra("response", responseJson)
            }
            sendBroadcast(responseIntent)
            Log.i(TAG, "Sent Gleo APP_ACTION_RESPONSE broadcast: packageName=$packageName, response=$responseJson")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Gleo APP_ACTION_RESPONSE broadcast: ${e.message}")
        }
    }

    /**
     * Requests USAGE_MEDIA AudioFocus while preserving internal 1.5s WebView self-focus ignore
     * and supporting 30% Ducking on `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`.
     */
    private fun requestInitialMediaAudioFocus(markInternalStart: Boolean = true) {
        try {
            if (markInternalStart) {
                lastInternalAudioStartMs = System.currentTimeMillis()
            }
            _isAudioDucked.value = false
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = initialAudioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(focusListener)
                    .build()
                initialAudioFocusRequest = req
                val res = audioManager.requestAudioFocus(req)
                Log.i("PleosAudioFocus", "USAGE_MEDIA AudioFocus requested result=$res")
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (e: Exception) {
            Log.w("PleosAudioFocus", "Failed to request AudioFocus: ${e.message}")
        }
    }

    private fun abandonMediaAudioFocus() {
        try {
            _isAudioDucked.value = false
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                initialAudioFocusRequest?.let { req ->
                    val res = audioManager.abandonAudioFocusRequest(req)
                    Log.i("PleosAudioFocus", "abandonAudioFocusRequest result=$res")
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusListener)
            }
        } catch (e: Exception) {
            Log.w("PleosAudioFocus", "Failed to abandon AudioFocus: ${e.message}")
        }
    }

    private fun savePlaybackStateToPrefs(
        trackId: String,
        elapsedSec: Float,
        backingVol: Int,
        echo: Int,
        key: Int
    ) {
        try {
            getSharedPreferences(SUSPEND_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("savedTrackId", trackId)
                .putFloat("savedElapsedSec", elapsedSec)
                .putInt("savedBackingVolume", backingVol)
                .putInt("savedEchoLevel", echo)
                .putInt("savedKeyShift", key)
                .putBoolean("autoResumeOnWake", false) // Never auto-resume after Suspend
                .putLong("savedAtMs", System.currentTimeMillis())
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun handleSuspendOrAppExit(reason: String) {
        Log.i(TAG, "handleSuspendOrAppExit(reason=$reason) -> stopping audio, saving state & abandoning AudioFocus")
        onSuspendOrExitStopCallback?.invoke()
        abandonMediaAudioFocus()
    }

    override fun onDestroy() {
        handleSuspendOrAppExit(reason = "onDestroy")
        try {
            unregisterReceiver(suspendPowerReceiver)
        } catch (_: Exception) {
        }
        try {
            if (mediaBrowser?.isConnected == true) {
                mediaBrowser?.disconnect()
            }
        } catch (_: Exception) {
        }
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
    isAudioDucked: Boolean = false,
    gleoActions: SharedFlow<GleoActionRequest>? = null,
    onRequestAudioFocus: () -> Unit = {},
    onMarkInternalAudioStart: () -> Unit = {},
    onAbandonAudioFocusAndSaveState: (trackId: String, elapsedSec: Float, backingVol: Int, echo: Int, key: Int) -> Unit = { _, _, _, _, _ -> },
    onSendGleoResponse: (functionName: String, status: String, message: String, data: JSONObject) -> Unit = { _, _, _, _ -> },
    onRegisterLifecycleCallbacks: (
        onExternalAudioFocusLoss: () -> Unit,
        onSuspendStop: () -> Unit,
        onBackToDefaultStage: () -> Boolean
    ) -> Unit = { _, _, _ -> }
) {
    val twinState by vehicleManager.twinState.collectAsState()
    val serverState by embeddedServer.serverState.collectAsState()
    val audioController = remember { CarAudioEngineController() }
    val youtubeController = remember { YouTubeMrController() }
    val coroutineScope = rememberCoroutineScope()

    // DEV / PROD Mode State: strictly locked to false (PROD) in Release (!BuildConfig.DEBUG), toggleable only in Debug
    var isDevMode by remember { mutableStateOf(BuildConfig.DEBUG) }
    var showDevModeSwitch by remember { mutableStateOf(BuildConfig.DEBUG) }

    // Sidebar Navigation State & Portrait Split-Window Sub-Tab State
    var activeMenu by remember { mutableStateOf(PleosSidebarMenu.STAGE_LIVE) }
    var portraitLiveSubTab by remember { mutableIntStateOf(0) } // 0 = 라이브 무대, 1 = 플레이리스트 & 선곡

    // Pairing & Embedded Ktor Signaling State
    var localLoopbackWsUrl by remember { mutableStateOf("ws://127.0.0.1:8080/ws") }
    var emulatorSignalingUrl by remember { mutableStateOf("ws://10.0.2.2:8080/ws") }
    var roomPin by remember { mutableStateOf("4829") }
    var phoneLanUrl by remember(serverState.lanWsUrl) { mutableStateOf(serverState.lanWsUrl) }
    var tunnelUrl by remember { mutableStateOf("") }
    var micConnected by remember { mutableStateOf(false) }
    var micDeviceName by remember { mutableStateOf("○ 연결 대기 중") }
    var connectedMicCount by remember { mutableIntStateOf(0) }
    var micTotalLatencyMs by remember { mutableIntStateOf(0) }
    var micNetLatencyMs by remember { mutableIntStateOf(0) }
    var micBufferLatencyMs by remember { mutableIntStateOf(12) }
    var micTransportMode by remember { mutableStateOf("DIRECT") }
    var lastAnyMicPacketMs by remember { mutableStateOf(0L) }

    // Single Source of Truth Unified Playlist (YouTube MR only; test playlist populated ONLY in Debug + DEV mode)
    val unifiedPlaylist = remember {
        mutableStateListOf<UnifiedStageTrack>().apply {
            if (BuildConfig.DEBUG) {
                addAll(YouTubeUrlParser.DEFAULT_UNIFIED_PLAYLIST)
            }
        }
    }
    var currentTrackId by remember {
        mutableStateOf(
            if (BuildConfig.DEBUG) YouTubeUrlParser.DEFAULT_UNIFIED_PLAYLIST.first().id else ""
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

    // Unified Stage Playback & Audio State
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var ytActuallyPlaying by remember { mutableStateOf(false) }
    var ytErrorCode by remember { mutableStateOf("") }
    var songElapsedSec by remember { mutableFloatStateOf(0f) }
    var backingVolume by remember { mutableIntStateOf(85) } // 0..100 Unified Backing/YouTube Volume (mrVolume)
    var micVolume by remember { mutableIntStateOf(85) }     // 0..100 Real-time Mic PCM Gain Volume
    var guideVolume by remember { mutableIntStateOf(75) }   // 0..100 Vocal Guide EP Bell Volume
    var echoLevel by remember { mutableIntStateOf(80) }     // 0..100 Reverb Echo Level (reverbLevel)
    var mrPlaybackRate by remember { mutableFloatStateOf(1.00f) } // 0.80f..1.20f Tempo Playback Rate (step 0.05f)
    var syncOffsetMs by remember { mutableIntStateOf(0) }   // -300..+300ms Vocal Sync Offset (step 20ms)
    var keyShift by remember { mutableIntStateOf(0) }       // -6..+6 Semitones
    var mediaVolume by remember { mutableIntStateOf(12) }   // 0..15 Pleos media bar scale
    val carHostId = remember { embeddedServer.carHostId }
    var stateRevision by remember { mutableStateOf(1L) }
    val duckScale = if (isAudioDucked) 0.30f else 1.0f
    val effectiveBackingVolume = (backingVolume * duckScale).roundToInt().coerceIn(0, 100)
    val effectiveVocalMasterScale = ((mediaVolume / 15f) * duckScale).coerceIn(0f, 1f)
    var currentScore by remember { mutableIntStateOf(78) }
    var isScoreModeEnabled by remember { mutableStateOf(true) }
    val vocalScoreAccumulator = remember { VocalScoreAccumulator() }
    var activeScoreResult by remember { mutableStateOf<KaraokeScoreResult?>(null) }
    var pendingAdvanceAfterScore by remember { mutableStateOf(false) }
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

    // Anti-flicker Singing VAD (micLevelPercent >= 8%) + 1000ms Hold Hysteresis + 300ms Pitch Note Debounce
    var isSingingActive by remember { mutableStateOf(false) }
    var lastSingingActiveElapsedMs by remember { mutableStateOf(0L) }
    var heldMicLevelPercent by remember { mutableIntStateOf(0) }
    var debouncedMicNote by remember { mutableStateOf("") }
    var lastMicNoteDebounceElapsedMs by remember { mutableStateOf(0L) }
    var lastValidMicPitchElapsedMs by remember { mutableStateOf(0L) }
    var debouncedGuideNote by remember { mutableStateOf("") }
    var lastGuideNoteDebounceElapsedMs by remember { mutableStateOf(0L) }

    val pitchHistory = remember { mutableStateListOf<Float>().apply { repeat(36) { add(0f) } } }
    val rmsHistory = remember { mutableStateListOf<Float>().apply { repeat(36) { add(0f) } } }
    val musicSpectrumBands = remember { mutableStateListOf<Float>().apply { repeat(48) { add(0f) } } }

    LaunchedEffect(demoVocalSimEnabled) {
        if (demoVocalSimEnabled && !isPlaying) {
            onRequestAudioFocus()
        } else if (!demoVocalSimEnabled && !isSingingActive) {
            vocalRms = 0f
            vocalPitchHz = 0f
            vocalNote = "-"
            debouncedGuideNote = ""
        }
    }

    LaunchedEffect(guideTelemetry, demoVocalSimEnabled) {
        val nowMs = System.currentTimeMillis()
        val hasRealMic = micConnected && (nowMs - lastRealMicTelemetryMs <= 450L) && (realMicRms.roundToInt() >= 8)
        if (!hasRealMic) {
            if (demoVocalSimEnabled) {
                val gt = audioController.nativeVocalEngine.guideTelemetry.value
                vocalRms = if (gt.guideVoiceLevelPercent > 10f) gt.guideVoiceLevelPercent else 64f
                vocalPitchHz = if (gt.guidePitchHz > 65f) gt.guidePitchHz else 293.66f
                vocalNote = if (gt.guideNote != "-" && gt.guideNote.isNotBlank()) gt.guideNote else "D4"
                val nowElapsed = SystemClock.elapsedRealtime()
                if (vocalNote != "-" && (debouncedGuideNote.isEmpty() || nowElapsed - lastGuideNoteDebounceElapsedMs >= 300L)) {
                    debouncedGuideNote = vocalNote
                    lastGuideNoteDebounceElapsedMs = nowElapsed
                }
            } else if (!isSingingActive) {
                vocalRms = 0f
                vocalPitchHz = 0f
                vocalNote = "-"
                debouncedGuideNote = ""
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

    val syncedSongElapsedSec = (songElapsedSec + (syncOffsetMs / 1000f)).coerceAtLeast(0f)
    val (guidePitchHz, guideNote) = remember(
        demoVocalSimEnabled,
        currentTrack.id,
        currentTrack.bpm,
        syncedSongElapsedSec,
        keyShift,
        mrPlaybackRate,
        syncOffsetMs
    ) {
        if (demoVocalSimEnabled) {
            NativeVocalAudioEngine.resolveRealtimeGuidePitch(
                trackId = currentTrack.id,
                bpm = currentTrack.bpm,
                elapsedSec = syncedSongElapsedSec,
                lyricBaseHz = 0f,
                keyShiftSemitones = keyShift,
                playbackRate = mrPlaybackRate,
                syncOffsetMs = 0
            )
        } else {
            0f to "-"
        }
    }

    // Broadcast 1:1 Single Source of Truth State to all connected Smartphones
    fun broadcastSyncState(
        overrideTrack: UnifiedStageTrack = currentTrack,
        overridePlaying: Boolean = isPlaying,
        overrideElapsed: Int = songElapsedSec.roundToInt(),
        bumpRevision: Boolean = true
    ) {
        if (bumpRevision) {
            stateRevision = embeddedServer.nextStateRevision()
        }
        val playlistJsonArr = YouTubeUrlParser.toJsonArray(unifiedPlaylist.toList())
        val safeElapsed = if (unifiedPlaylist.isEmpty()) 0 else overrideElapsed.coerceAtLeast(0)
        val safeDuration = if (unifiedPlaylist.isEmpty()) 0 else overrideTrack.durationSec.coerceAtLeast(15)
        val activeGuideHz = if (demoVocalSimEnabled && guideTelemetry.guidePitchHz > 60f) guideTelemetry.guidePitchHz else 0f
        val activeGuideNote = if (demoVocalSimEnabled && guideTelemetry.guidePitchHz > 60f) guideTelemetry.guideNote else "-"
        val activeGuideLevel = if (demoVocalSimEnabled) guideTelemetry.guideVoiceLevelPercent.roundToInt() else 0
        val stateJson = JSONObject().apply {
            put("carHostId", carHostId)
            put("stateRevision", stateRevision)
            put("currentSongId", overrideTrack.id)
            put("currentTrackId", overrideTrack.id)
            put("currentTrackTitle", overrideTrack.title)
            put("currentTrackArtist", overrideTrack.artist)
            put("isPlaying", overridePlaying && unifiedPlaylist.isNotEmpty())
            put("progressSec", safeElapsed)
            put("currentPositionSec", safeElapsed)
            put("durationSec", safeDuration)
            put("backingVolume", backingVolume)
            put("mrVolume", backingVolume)
            put("micVolume", micVolume)
            put("guideVolume", guideVolume)
            put("keyShift", keyShift)
            put("echoLevel", echoLevel)
            put("reverbLevel", echoLevel)
            put("mrPlaybackRate", mrPlaybackRate.toDouble())
            put("syncOffsetMs", syncOffsetMs)
            put("isParked", twinState.isParked)
            put("score", currentScore)
            put("scoreEnabled", isScoreModeEnabled)
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
            micVolume = micVolume,
            guideVolume = guideVolume,
            mrPlaybackRate = mrPlaybackRate,
            syncOffsetMs = syncOffsetMs,
            carHostId = carHostId,
            stateRevision = stateRevision,
            isDevMode = isDevMode,
            scoreEnabled = isScoreModeEnabled,
            guideVocalEnabled = demoVocalSimEnabled,
            guidePitchHz = activeGuideHz,
            guideNote = activeGuideNote,
            guideVoiceLevelPercent = activeGuideLevel,
            playlistJson = playlistJsonArr
        )
        PleosMediaSessionBridge.updatePlaybackAndMetadata(
            context = context,
            track = overrideTrack,
            isPlaying = overridePlaying && unifiedPlaylist.isNotEmpty(),
            positionMs = safeElapsed * 1000L,
            playlist = unifiedPlaylist.toList()
        )
    }

    // Start playing any track (YouTube or Built-in Synth) in the Unified Stage
    fun playUnifiedTrack(track: UnifiedStageTrack, resetProgress: Boolean = true) {
        if (track.id.isBlank()) return
        onMarkInternalAudioStart()
        onRequestAudioFocus()
        val trackChanged = currentTrackId != track.id
        currentTrackId = track.id
        if (resetProgress || trackChanged) {
            songElapsedSec = 0f
            currentScore = 78
            vocalScoreAccumulator.reset()
        }
        activeScoreResult = null
        pendingAdvanceAfterScore = false
        isPlaying = true
        ytActuallyPlaying = false
        ytErrorCode = ""
        activeMenu = PleosSidebarMenu.STAGE_LIVE
        portraitLiveSubTab = 0

        vehicleManager.onSongStarted(
            songTitle = track.title,
            genre = track.genre,
            bpm = track.bpm
        )

        audioController.setTrackConfig(track.id, track.bpm)
        audioController.setStagePlaying(true)

        audioController.stopSong()
        youtubeController.setVolume(effectiveBackingVolume)
        youtubeController.loadTarget(
            videoId = track.videoId,
            playlistId = "",
            title = track.title,
            bpm = track.bpm,
            autoPlay = true
        )
        youtubeController.play()

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
        vehicleManager.onSongFinished(78)
        broadcastSyncState(
            overrideTrack = currentTrack,
            overridePlaying = false,
            overrideElapsed = songElapsedSec.roundToInt()
        )
    }

    // Switch between DEV Mode (populates YouTube test playlist, DEV badges & 5th Tab) and PROD Mode (removes test playlist, hides DEV UI)
    fun applyDevProdMode(targetDevMode: Boolean) {
        val effectiveDevMode = BuildConfig.DEBUG && targetDevMode
        isDevMode = effectiveDevMode
        if (!effectiveDevMode && activeMenu == PleosSidebarMenu.SDK_UNIT_TEST) {
            activeMenu = PleosSidebarMenu.STAGE_LIVE
        }
        if (effectiveDevMode) {
            if (unifiedPlaylist.none { it.isDevSample }) {
                unifiedPlaylist.addAll(YouTubeUrlParser.DEFAULT_UNIFIED_PLAYLIST)
            }
            if (currentTrackId.isBlank() || unifiedPlaylist.none { it.id == currentTrackId }) {
                currentTrackId = unifiedPlaylist.first().id
            }
        } else {
            val wasPlayingDevTrack = isPlaying && currentTrack.isDevSample
            unifiedPlaylist.removeAll { it.isDevSample }
            if (wasPlayingDevTrack || unifiedPlaylist.isEmpty()) {
                isPlaying = false
                ytActuallyPlaying = false
                clearMusicSpectrum()
                songElapsedSec = 0f
                audioController.setStagePlaying(false)
                audioController.stopSong()
                youtubeController.pause()
            }
            currentTrackId = if (unifiedPlaylist.isEmpty()) {
                ""
            } else if (unifiedPlaylist.none { it.id == currentTrackId }) {
                unifiedPlaylist.first().id
            } else {
                currentTrackId
            }
        }
        val resolvedTrack = if (unifiedPlaylist.isEmpty()) {
            emptyStagePlaceholderTrack
        } else {
            unifiedPlaylist.firstOrNull { it.id == currentTrackId } ?: unifiedPlaylist.first()
        }
        broadcastSyncState(
            overrideTrack = resolvedTrack,
            overridePlaying = isPlaying && unifiedPlaylist.isNotEmpty(),
            overrideElapsed = if (unifiedPlaylist.isEmpty()) 0 else songElapsedSec.roundToInt()
        )
    }

    fun toggleUnifiedPlayPause() {
        if (unifiedPlaylist.isEmpty()) return
        if (isPlaying) {
            pauseOrStopUnifiedStage(resetProgress = false)
        } else {
            if (currentTrack.isYouTube && songElapsedSec > 1f) {
                isPlaying = true
                audioController.setStagePlaying(true)
                youtubeController.play()
                broadcastSyncState(overrideTrack = currentTrack, overridePlaying = true)
            } else {
                playUnifiedTrack(currentTrack, resetProgress = songElapsedSec <= 1f)
            }
        }
    }

    fun playAdjacentTrackDirect(step: Int) {
        val pool = unifiedPlaylist.toList()
        if (pool.isEmpty()) return
        val curIdx = pool.indexOfFirst { it.id == currentTrackId }.let { if (it < 0) 0 else it }
        val nextIdx = (curIdx + step + pool.size) % pool.size
        playUnifiedTrack(pool[nextIdx], resetProgress = true)
    }

    fun dismissScoreOverlayAndContinue(fromRemote: Boolean = false) {
        val prevResult = activeScoreResult
        val shouldAdvance = pendingAdvanceAfterScore
        activeScoreResult = null
        pendingAdvanceAfterScore = false
        if (prevResult != null && !fromRemote) {
            embeddedServer.broadcastKaraokeScoreDismiss(prevResult.resultId)
            audioController.broadcastScoreDismissToMic(prevResult.resultId)
        }
        if (shouldAdvance && unifiedPlaylist.isNotEmpty()) {
            playAdjacentTrackDirect(1)
        }
    }

    fun setScoreModeState(enabled: Boolean) {
        isScoreModeEnabled = enabled
        if (!enabled && activeScoreResult != null) {
            dismissScoreOverlayAndContinue(fromRemote = false)
        } else {
            broadcastSyncState()
        }
    }

    fun triggerKaraokeScoreReveal(advanceToNext: Boolean) {
        if (!isScoreModeEnabled) {
            vocalScoreAccumulator.reset()
            currentScore = 78
            if (advanceToNext) {
                playAdjacentTrackDirect(1)
            }
            return
        }
        if (activeScoreResult != null && !isPlaying) {
            return
        }

        isPlaying = false
        ytActuallyPlaying = false
        clearMusicSpectrum()
        songElapsedSec = 0f
        audioController.setStagePlaying(false)
        audioController.stopSong()
        youtubeController.pause()

        val result = KaraokeScoreCalculator.calculateFromAccumulator(
            accumulator = vocalScoreAccumulator,
            trackTitle = if (unifiedPlaylist.isEmpty()) "Pleos Live Stage" else currentTrack.title,
            willAdvanceNext = advanceToNext
        )
        vocalScoreAccumulator.reset()

        currentScore = result.score
        pendingAdvanceAfterScore = advanceToNext
        activeScoreResult = result

        // Play automatic CHEER + APPLAUSE SFX & 90+ Finale cabin lighting
        audioController.triggerSfx("CHEER")
        coroutineScope.launch {
            delay(180L)
            audioController.triggerSfx("APPLAUSE")
        }
        vehicleManager.onSongFinished(result.score)

        val scoreJson = result.toJson()
        embeddedServer.broadcastKaraokeScoreResult(scoreJson)
        audioController.broadcastScoreResultToMic(scoreJson)
        broadcastSyncState(
            overrideTrack = currentTrack,
            overridePlaying = isPlaying,
            overrideElapsed = songElapsedSec.roundToInt()
        )
    }

    fun stopUnifiedStageWithScoreCheck() {
        val wasPlaying = isPlaying && unifiedPlaylist.isNotEmpty()
        val hasSungAtLeast3Sec = micConnected && vocalScoreAccumulator.activeVoiceSeconds >= 3.0
        if (wasPlaying && isScoreModeEnabled && hasSungAtLeast3Sec) {
            triggerKaraokeScoreReveal(advanceToNext = false)
        } else {
            vocalScoreAccumulator.reset()
            currentScore = 78
            pauseOrStopUnifiedStage(resetProgress = true)
        }
    }

    fun playAdjacentTrack(step: Int) {
        if (unifiedPlaylist.isEmpty()) return
        if (activeScoreResult != null) {
            val prevId = activeScoreResult?.resultId.orEmpty()
            activeScoreResult = null
            pendingAdvanceAfterScore = false
            if (prevId.isNotBlank()) {
                embeddedServer.broadcastKaraokeScoreDismiss(prevId)
                audioController.broadcastScoreDismissToMic(prevId)
            }
            playAdjacentTrackDirect(step)
            return
        }
        val hasSungAtLeast3Sec = micConnected && vocalScoreAccumulator.activeVoiceSeconds >= 3.0
        if (step > 0 && isPlaying && isScoreModeEnabled && hasSungAtLeast3Sec) {
            triggerKaraokeScoreReveal(advanceToNext = true)
            return
        }
        vocalScoreAccumulator.reset()
        currentScore = 78
        playAdjacentTrackDirect(step)
    }

    fun seekUnifiedTrackTo(targetSec: Int) {
        if (unifiedPlaylist.isEmpty()) return
        val trackDur = currentTrack.durationSec.coerceAtLeast(1)
        val clampedSec = targetSec.coerceIn(0, trackDur)
        songElapsedSec = clampedSec.toFloat()
        if (currentTrack.isYouTube) {
            youtubeController.seekTo(clampedSec.toFloat())
        }
        audioController.seekTo(clampedSec.toFloat())
        broadcastSyncState(
            overrideTrack = currentTrack,
            overridePlaying = isPlaying,
            overrideElapsed = clampedSec
        )
        if (isPlaying && clampedSec >= trackDur) {
            if (isScoreModeEnabled) {
                triggerKaraokeScoreReveal(advanceToNext = true)
            } else {
                playAdjacentTrackDirect(1)
            }
        }
    }

    // Exact 5.0-second auto-dismiss timer for active Karaoke Score Reveal overlay
    LaunchedEffect(activeScoreResult?.resultId) {
        val targetId = activeScoreResult?.resultId ?: return@LaunchedEffect
        delay(5000L)
        if (activeScoreResult?.resultId == targetId) {
            dismissScoreOverlayAndContinue(fromRemote = false)
        }
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

            "PAUSE_SONG" -> {
                pauseOrStopUnifiedStage(resetProgress = false)
            }

            "STOP_SONG" -> {
                stopUnifiedStageWithScoreCheck()
            }

            "NEXT_TRACK", "YOUTUBE_NEXT" -> {
                playAdjacentTrack(1)
            }

            "PREV_TRACK", "YOUTUBE_PREV" -> {
                playAdjacentTrack(-1)
            }

            "CHANGE_BACKING_VOLUME", "YOUTUBE_VOLUME" -> {
                val vol = cmd.optInt(
                    "backingVolume",
                    cmd.optInt("mrVolume", cmd.optInt("youtubeVolume", cmd.optInt("volume", backingVolume)))
                ).coerceIn(0, 100)
                backingVolume = vol
                val effVol = (vol * duckScale).roundToInt().coerceIn(0, 100)
                youtubeController.setVolume(effVol)
                audioController.setBackingVolume(effVol)
                broadcastSyncState()
            }

            "CHANGE_MIC_VOLUME" -> {
                val vol = cmd.optInt("micVolume", cmd.optInt("volume", micVolume)).coerceIn(0, 100)
                micVolume = vol
                audioController.setMicVolume(vol)
                broadcastSyncState()
            }

            "CHANGE_GUIDE_VOLUME" -> {
                val vol = cmd.optInt("guideVolume", cmd.optInt("volume", guideVolume)).coerceIn(0, 100)
                guideVolume = vol
                audioController.setGuideVolume(vol)
                broadcastSyncState()
            }

            "CHANGE_KEY" -> {
                keyShift = cmd.optInt("keyShift", keyShift).coerceIn(-6, 6)
                audioController.setKeyShift(keyShift)
                broadcastSyncState()
            }

            "CHANGE_ECHO", "CHANGE_REVERB" -> {
                echoLevel = cmd.optInt("echoLevel", cmd.optInt("reverbLevel", echoLevel)).coerceIn(0, 100)
                audioController.setEchoLevel(echoLevel)
                broadcastSyncState()
            }

            "CHANGE_PLAYBACK_RATE" -> {
                val rawRate = cmd.optDouble(
                    "mrPlaybackRate",
                    cmd.optDouble("playbackRate", cmd.optDouble("rate", mrPlaybackRate.toDouble()))
                ).toFloat()
                val steppedRate = ((rawRate.coerceIn(0.75f, 1.25f) * 20f).roundToInt() / 20f)
                mrPlaybackRate = steppedRate
                youtubeController.setPlaybackRate(steppedRate)
                audioController.setPlaybackRate(steppedRate)
                broadcastSyncState()
            }

            "CHANGE_SYNC_OFFSET" -> {
                val prevOff = syncOffsetMs
                val rawOff = cmd.optInt("syncOffsetMs", cmd.optInt("offsetMs", syncOffsetMs))
                val steppedOff = ((rawOff.coerceIn(-500, 500) / 50.0).roundToInt() * 50).coerceIn(-500, 500)
                val explicitDelta = if (cmd.has("syncDeltaMs")) cmd.optInt("syncDeltaMs", 0) else (steppedOff - prevOff)
                syncOffsetMs = steppedOff
                youtubeController.adjustSyncOffset(steppedOff, explicitDelta)
                audioController.adjustSyncOffsetMs(steppedOff, explicitDelta)
                broadcastSyncState()
            }

            "RESET_AUDIO_MIXER" -> {
                val resetSyncDelta = -syncOffsetMs
                backingVolume = 85
                micVolume = 85
                guideVolume = 75
                echoLevel = 80
                mrPlaybackRate = 1.00f
                syncOffsetMs = 0
                keyShift = 0
                val effVol = (85 * duckScale).roundToInt().coerceIn(0, 100)
                youtubeController.setVolume(effVol)
                youtubeController.setPlaybackRate(1.00f)
                youtubeController.adjustSyncOffset(0, resetSyncDelta)
                audioController.setBackingVolume(effVol)
                audioController.setMicVolume(85)
                audioController.setGuideVolume(75)
                audioController.setEchoLevel(80)
                audioController.setPlaybackRate(1.00f)
                audioController.adjustSyncOffsetMs(0, resetSyncDelta)
                audioController.setKeyShift(0)
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
                } else if (realMicRms <= 1.5f) {
                    vocalRms = 0f
                    vocalPitchHz = 0f
                    vocalNote = "-"
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

            "toggle-score-mode", "TOGGLE_SCORE_MODE", "SET_SCORE_MODE" -> {
                val nextEnabled = when {
                    cmd.has("enabled") -> cmd.optBoolean("enabled", !isScoreModeEnabled)
                    cmd.has("scoreEnabled") -> cmd.optBoolean("scoreEnabled", !isScoreModeEnabled)
                    else -> !isScoreModeEnabled
                }
                setScoreModeState(nextEnabled)
            }

            "seek-to", "SEEK_TO" -> {
                val targetPos = cmd.optInt(
                    "positionSec",
                    cmd.optInt("seekSec", cmd.optInt("progressSec", songElapsedSec.roundToInt()))
                )
                seekUnifiedTrackTo(targetPos)
            }

            "dismiss-score-overlay", "DISMISS_SCORE_OVERLAY" -> {
                dismissScoreOverlayAndContinue(fromRemote = true)
            }
        }
    }

    var lastHandledRemoteCmdTs by remember { mutableStateOf(0L) }

    fun shouldProcessRemoteCommand(type: String, cmdTs: Long): Boolean {
        val isContinuousAudioMixerCmd = type == "CHANGE_BACKING_VOLUME" ||
            type == "YOUTUBE_VOLUME" ||
            type == "CHANGE_MIC_VOLUME" ||
            type == "CHANGE_GUIDE_VOLUME" ||
            type == "CHANGE_ECHO" ||
            type == "CHANGE_REVERB" ||
            type == "CHANGE_PLAYBACK_RATE" ||
            type == "CHANGE_SYNC_OFFSET" ||
            type == "RESET_AUDIO_MIXER" ||
            type == "CHANGE_KEY" ||
            type.equals("seek-to", ignoreCase = true) ||
            type == "SEEK_TO" ||
            type.equals("toggle-score-mode", ignoreCase = true) ||
            type == "TOGGLE_SCORE_MODE" ||
            type == "SET_SCORE_MODE" ||
            type.equals("dismiss-score-overlay", ignoreCase = true) ||
            type == "DISMISS_SCORE_OVERLAY"
        if (isContinuousAudioMixerCmd) {
            lastHandledRemoteCmdTs = cmdTs
            return true
        }
        if (cmdTs == 0L || cmdTs != lastHandledRemoteCmdTs) {
            lastHandledRemoteCmdTs = cmdTs
            return true
        }
        return false
    }

    fun markUnifiedMicPacketReceived(
        deviceName: String = "Pleos Smart Mic",
        transport: String = micTransportMode,
        countHint: Int = 1
    ) {
        lastAnyMicPacketMs = System.currentTimeMillis()
        val wasConnected = micConnected
        micConnected = true
        micDeviceName = deviceName.ifBlank { "Pleos Smart Mic" }
        connectedMicCount = countHint.coerceAtLeast(1)
        if (transport.isNotBlank()) {
            micTransportMode = transport
        }
        if (micTotalLatencyMs <= 0) {
            micTotalLatencyMs = if (micTransportMode == "DIRECT" || micTransportMode == "WEBRTC_P2P") 26 else 38
        }
        if (!wasConnected) {
            broadcastSyncState()
        }
    }

    fun applyIncomingMicVocalTelemetry(rms: Float, pitchHz: Float, note: String) {
        lastRealMicTelemetryMs = System.currentTimeMillis()
        markUnifiedMicPacketReceived(micDeviceName.ifBlank { "Pleos Smart Mic" }, micTransportMode)
        val cleanRms = rms.coerceIn(0f, 100f)
        val micLevelPercent = cleanRms.roundToInt().coerceIn(0, 100)
        val isAboveVad = micLevelPercent >= 8
        val cleanPitch = if (isAboveVad) pitchHz.coerceAtLeast(0f) else 0f
        val cleanNote = if (isAboveVad && cleanPitch > 60f && note.isNotBlank() && note != "-") note else "-"
        realMicRms = cleanRms
        realMicPitchHz = cleanPitch
        realMicNote = cleanNote
        if (isAboveVad) {
            val nowElapsed = SystemClock.elapsedRealtime()
            isSingingActive = true
            lastSingingActiveElapsedMs = nowElapsed
            heldMicLevelPercent = micLevelPercent
            if (cleanNote != "-") {
                lastValidMicPitchElapsedMs = nowElapsed
                if (debouncedMicNote.isEmpty() || cleanNote == debouncedMicNote || nowElapsed - lastMicNoteDebounceElapsedMs >= 300L) {
                    if (cleanNote != debouncedMicNote) {
                        debouncedMicNote = cleanNote
                        lastMicNoteDebounceElapsedMs = nowElapsed
                    }
                }
            } else if (debouncedMicNote.isNotEmpty() && nowElapsed - lastValidMicPitchElapsedMs >= 1000L) {
                debouncedMicNote = ""
            }
            vocalRms = cleanRms
            if (cleanPitch > 65f) {
                vocalPitchHz = cleanPitch
                vocalNote = cleanNote
                if (pitchHistory.isNotEmpty()) {
                    pitchHistory[pitchHistory.lastIndex] = cleanPitch
                }
                if (rmsHistory.isNotEmpty()) {
                    rmsHistory[rmsHistory.lastIndex] = cleanRms
                }
            }
        }
        if (isPlaying) {
            vehicleManager.onMusicAndVocalWaveformTick(
                genre = currentTrack.genre,
                bpm = currentTrack.bpm,
                elapsedSec = songElapsedSec,
                vocalRms = if (isAboveVad) cleanRms else 0f,
                vocalPitchHz = cleanPitch,
                isHighlightOrInterlude = cleanRms >= 70f
            )
        }
    }

    // Unified 6.0-second Smartphone Mic Connection Watchdog (Inbound :8080 + Outbound :8082 + Native/WebRTC Relay)
    LaunchedEffect(Unit) {
        while (true) {
            delay(400L)
            val nowMs = System.currentTimeMillis()
            if (micConnected && (lastAnyMicPacketMs == 0L || nowMs - lastAnyMicPacketMs > 6000L)) {
                micConnected = false
                connectedMicCount = 0
                micDeviceName = "○ 연결 대기 중"
            }
        }
    }

    // Wire Embedded Ktor Server real-time Smartphone Mic PCM chunks & Direct Remote Commands directly to Native AudioTrack & UI
    DisposableEffect(embeddedServer, audioController, youtubeController) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        audioController.youTubeMrController = youtubeController
        embeddedServer.audioEngineController = audioController
        embeddedServer.onMicPcmChunk = { sr, pcmShorts, seq ->
            audioController.enqueueMicPcmChunk(sr, pcmShorts, seq)
            mainHandler.post {
                markUnifiedMicPacketReceived(micDeviceName, micTransportMode)
            }
        }
        embeddedServer.onMicActivityVerified = { deviceName, transport ->
            mainHandler.post {
                markUnifiedMicPacketReceived(deviceName, transport)
            }
        }
        embeddedServer.onMicVocalTelemetry = { rms, pitchHz, note ->
            mainHandler.post {
                applyIncomingMicVocalTelemetry(rms, pitchHz, note)
            }
        }
        embeddedServer.onMicLatencyUpdated = { totalMs, netMs, carBufMs, transport ->
            mainHandler.post {
                if (transport == "RELAY" &&
                    (embeddedServer.serverState.value.connectedMicCount > 0 ||
                        micTransportMode == "DIRECT" ||
                        micTransportMode == "WEBRTC_P2P")
                ) {
                    return@post
                }
                micTotalLatencyMs = totalMs.coerceIn(5, 999)
                micNetLatencyMs = netMs.coerceIn(1, 500)
                micBufferLatencyMs = carBufMs.coerceIn(0, 300)
                micTransportMode = transport
                markUnifiedMicPacketReceived(micDeviceName, transport)
            }
        }
        embeddedServer.onDirectMicConnectionChanged = { connected, deviceName, count ->
            mainHandler.post {
                if (connected) {
                    markUnifiedMicPacketReceived(deviceName, micTransportMode.ifBlank { "DIRECT" }, count)
                } else if (System.currentTimeMillis() - lastAnyMicPacketMs > 6000L) {
                    micConnected = false
                    micDeviceName = "○ 연결 대기 중"
                    connectedMicCount = 0
                }
            }
        }
        embeddedServer.onDirectRemoteCommand = { type, cmd ->
            mainHandler.post {
                markUnifiedMicPacketReceived(micDeviceName, micTransportMode)
                val cmdTs = cmd.optLong("ts", 0L)
                if (shouldProcessRemoteCommand(type, cmdTs)) {
                    dispatchIncomingRemoteCommand(type, cmd)
                }
            }
        }
        onDispose {
            embeddedServer.onMicPcmChunk = null
            embeddedServer.onMicActivityVerified = null
            embeddedServer.onMicVocalTelemetry = null
            embeddedServer.onMicLatencyUpdated = null
            embeddedServer.onDirectMicConnectionChanged = null
            embeddedServer.onDirectRemoteCommand = null
        }
    }

    // Wire AAOS PleosStageMediaBrowserService & MediaSessionCompat bidirectional transport controls + Lifecycle callbacks
    DisposableEffect(Unit) {
        PleosMediaSessionBridge.onPlayCommand = {
            if (!isPlaying) {
                if (unifiedPlaylist.isEmpty() && BuildConfig.DEBUG && isDevMode) {
                    unifiedPlaylist.addAll(YouTubeUrlParser.DEFAULT_UNIFIED_PLAYLIST)
                    currentTrackId = unifiedPlaylist.first().id
                }
                toggleUnifiedPlayPause()
            }
        }
        PleosMediaSessionBridge.onPauseCommand = {
            if (isPlaying) {
                pauseOrStopUnifiedStage(resetProgress = false)
            }
        }
        PleosMediaSessionBridge.onTogglePlayPauseCommand = {
            toggleUnifiedPlayPause()
        }
        PleosMediaSessionBridge.onSkipToNextCommand = {
            playAdjacentTrack(1)
        }
        PleosMediaSessionBridge.onSkipToPreviousCommand = {
            playAdjacentTrack(-1)
        }
        PleosMediaSessionBridge.onStopCommand = {
            pauseOrStopUnifiedStage(resetProgress = true)
            onAbandonAudioFocusAndSaveState(currentTrack.id, 0f, backingVolume, echoLevel, keyShift)
            PleosMediaSessionBridge.stopAndDeactivateSession()
        }
        PleosMediaSessionBridge.onSeekToCommand = { posMs ->
            seekUnifiedTrackTo((posMs / 1000L).toInt())
        }
        PleosMediaSessionBridge.onPlayFromMediaIdCommand = { mediaId ->
            val target = unifiedPlaylist.firstOrNull { it.id == mediaId }
                ?: if (BuildConfig.DEBUG && isDevMode) {
                    YouTubeUrlParser.DEFAULT_UNIFIED_PLAYLIST.firstOrNull { it.id == mediaId }
                } else {
                    null
                }
            if (target != null) {
                if (unifiedPlaylist.none { it.id == target.id }) {
                    unifiedPlaylist.add(0, target)
                }
                playUnifiedTrack(target, resetProgress = true)
            }
        }
        PleosMediaSessionBridge.onPlayFromSearchCommand = { query ->
            val matches: (UnifiedStageTrack) -> Boolean = {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.id.equals(query, ignoreCase = true)
            }
            val target = unifiedPlaylist.firstOrNull(matches)
                ?: if (BuildConfig.DEBUG && isDevMode) {
                    YouTubeUrlParser.DEFAULT_UNIFIED_PLAYLIST.firstOrNull(matches)
                } else {
                    null
                }
            if (target != null) {
                if (unifiedPlaylist.none { it.id == target.id }) {
                    unifiedPlaylist.add(0, target)
                }
                playUnifiedTrack(target, resetProgress = true)
            } else {
                addTrackToUnifiedPlaylist(query, playImmediately = true)
            }
        }
        PleosMediaSessionBridge.onTaskRemovedCleanup = {
            pauseOrStopUnifiedStage(resetProgress = false)
            onAbandonAudioFocusAndSaveState(currentTrack.id, songElapsedSec, backingVolume, echoLevel, keyShift)
        }

        onRegisterLifecycleCallbacks(
            {
                // External AudioFocus loss (after 1.5s self-focus window)
                if (isPlaying) {
                    pauseOrStopUnifiedStage(resetProgress = false)
                }
            },
            {
                // Vehicle Suspend Mode / Activity onDestroy / Three-finger swipe exit
                pauseOrStopUnifiedStage(resetProgress = false)
                onAbandonAudioFocusAndSaveState(currentTrack.id, songElapsedSec, backingVolume, echoLevel, keyShift)
            },
            {
                // CCP Hardware Back Key: return to STAGE_LIVE if in another menu or Portrait Playlist sub-tab
                when {
                    activeMenu != PleosSidebarMenu.STAGE_LIVE -> {
                        activeMenu = PleosSidebarMenu.STAGE_LIVE
                        portraitLiveSubTab = 0
                        true
                    }
                    portraitLiveSubTab != 0 -> {
                        portraitLiveSubTab = 0
                        true
                    }
                    else -> false
                }
            }
        )

        onDispose {
            PleosMediaSessionBridge.onPlayCommand = null
            PleosMediaSessionBridge.onPauseCommand = null
            PleosMediaSessionBridge.onTogglePlayPauseCommand = null
            PleosMediaSessionBridge.onSkipToNextCommand = null
            PleosMediaSessionBridge.onSkipToPreviousCommand = null
            PleosMediaSessionBridge.onStopCommand = null
            PleosMediaSessionBridge.onSeekToCommand = null
            PleosMediaSessionBridge.onPlayFromMediaIdCommand = null
            PleosMediaSessionBridge.onPlayFromSearchCommand = null
            PleosMediaSessionBridge.onTaskRemovedCleanup = null
        }
    }

    // Handle Pleos Connect Gleo AI Voice Commands (PLAY_KARAOKE_SONG, SET_STAGE_SOUND_MODE, CONTROL_STAGE_PLAYBACK)
    LaunchedEffect(gleoActions) {
        gleoActions?.collect { req ->
            when (req.action) {
                "PLAY_KARAOKE_SONG" -> {
                    val searchTerm = req.paramJson.optString("searchTerm", "").trim()
                    if (searchTerm.isBlank()) {
                        onSendGleoResponse(
                            "PLAY_KARAOKE_SONG",
                            "error",
                            "Missing required parameter: searchTerm",
                            JSONObject()
                        )
                    } else {
                        val existing = unifiedPlaylist.firstOrNull {
                            it.title.contains(searchTerm, ignoreCase = true) ||
                                it.artist.contains(searchTerm, ignoreCase = true) ||
                                it.id.equals(searchTerm, ignoreCase = true)
                        } ?: if (BuildConfig.DEBUG && isDevMode) {
                            YouTubeUrlParser.DEFAULT_UNIFIED_PLAYLIST.firstOrNull {
                                it.title.contains(searchTerm, ignoreCase = true) ||
                                    it.artist.contains(searchTerm, ignoreCase = true) ||
                                    it.id.equals(searchTerm, ignoreCase = true)
                            }
                        } else {
                            null
                        }

                        val playedTitle: String
                        if (existing != null) {
                            if (unifiedPlaylist.none { it.id == existing.id }) {
                                unifiedPlaylist.add(0, existing)
                            }
                            playUnifiedTrack(existing, resetProgress = true)
                            playedTitle = existing.title
                        } else {
                            addTrackToUnifiedPlaylist(searchTerm, playImmediately = true)
                            playedTitle = searchTerm
                        }

                        Toast.makeText(
                            context,
                            "Gleo 음성 선곡 재생: $playedTitle",
                            Toast.LENGTH_SHORT
                        ).show()

                        val dataObj = JSONObject().apply {
                            put("searchTerm", searchTerm)
                            put("trackTitle", playedTitle)
                            put("isPlaying", true)
                        }
                        onSendGleoResponse(
                            "PLAY_KARAOKE_SONG",
                            "success",
                            "Added and started playing karaoke song: $playedTitle",
                            dataObj
                        )
                    }
                }

                "SET_STAGE_SOUND_MODE" -> {
                    val preset = req.paramJson.optString("soundPreset", "CONCERT_HALL").uppercase()
                    when (preset) {
                        "CONCERT_HALL" -> {
                            echoLevel = 85
                            backingVolume = 85
                        }
                        "STUDIO" -> {
                            echoLevel = 45
                            backingVolume = 80
                        }
                        "PASSENGER_CLUB" -> {
                            echoLevel = 95
                            backingVolume = 95
                        }
                        "DRIVER_SAFE" -> {
                            echoLevel = 30
                            backingVolume = 65
                            demoVocalSimEnabled = false
                        }
                    }
                    if (req.paramJson.has("vocalGuide")) {
                        demoVocalSimEnabled = req.paramJson.optBoolean("vocalGuide", demoVocalSimEnabled)
                    } else if (req.paramJson.has("vocalGuideEnabled")) {
                        demoVocalSimEnabled = req.paramJson.optBoolean("vocalGuideEnabled", demoVocalSimEnabled)
                    }
                    audioController.setEchoLevel(echoLevel)
                    audioController.setBackingVolume((backingVolume * duckScale).roundToInt())
                    youtubeController.setVolume((backingVolume * duckScale).roundToInt())
                    audioController.setGuideVocalEnabled(demoVocalSimEnabled)
                    broadcastSyncState()

                    Toast.makeText(
                        context,
                        "Gleo 사운드 프리셋 적용: $preset (에코 ${echoLevel}%)",
                        Toast.LENGTH_SHORT
                    ).show()

                    val dataObj = JSONObject().apply {
                        put("soundPreset", preset)
                        put("echoLevel", echoLevel)
                        put("backingVolume", backingVolume)
                        put("vocalGuide", demoVocalSimEnabled)
                    }
                    onSendGleoResponse(
                        "SET_STAGE_SOUND_MODE",
                        "success",
                        "Stage sound mode set to $preset with echo ${echoLevel}%",
                        dataObj
                    )
                }

                "CONTROL_STAGE_PLAYBACK" -> {
                    val command = req.paramJson.optString("command", "PLAY").uppercase()
                    when (command) {
                        "PLAY" -> {
                            if (!isPlaying) {
                                toggleUnifiedPlayPause()
                            }
                        }
                        "PAUSE" -> {
                            if (isPlaying) {
                                pauseOrStopUnifiedStage(resetProgress = false)
                            }
                        }
                        "NEXT_SONG" -> {
                            playAdjacentTrack(1)
                        }
                        "PREV_SONG" -> {
                            playAdjacentTrack(-1)
                        }
                        "KEY_UP" -> {
                            keyShift = (keyShift + 1).coerceIn(-6, 6)
                            audioController.setKeyShift(keyShift)
                            broadcastSyncState()
                        }
                        "KEY_DOWN" -> {
                            keyShift = (keyShift - 1).coerceIn(-6, 6)
                            audioController.setKeyShift(keyShift)
                            broadcastSyncState()
                        }
                    }

                    Toast.makeText(
                        context,
                        "Gleo 무대 제어: $command",
                        Toast.LENGTH_SHORT
                    ).show()

                    val dataObj = JSONObject().apply {
                        put("command", command)
                        put("isPlaying", isPlaying)
                        put("keyShift", keyShift)
                        put("currentTrackTitle", currentTrack.title)
                    }
                    onSendGleoResponse(
                        "CONTROL_STAGE_PLAYBACK",
                        "success",
                        "Executed stage playback command: $command",
                        dataObj
                    )
                }
            }
        }
    }

    // Sync Stage Playback, Guide Vocal Melody, Mic/Guide/Echo Volumes, Tempo, Sync Offset & 30% AudioFocus Ducking immediately to Native AudioTrack & YouTube
    LaunchedEffect(
        isPlaying,
        unifiedPlaylist.size,
        demoVocalSimEnabled,
        micVolume,
        guideVolume,
        echoLevel,
        mrPlaybackRate,
        syncOffsetMs,
        keyShift,
        mediaVolume,
        backingVolume,
        isAudioDucked,
        currentTrack.id,
        currentTrack.bpm,
        guidePitchHz,
        guideNote
    ) {
        audioController.setTrackConfig(currentTrack.id, currentTrack.bpm)
        audioController.setGuideVocalEnabled(demoVocalSimEnabled)
        audioController.setMicVolume(micVolume)
        audioController.setGuideVolume(guideVolume)
        audioController.setEchoLevel(echoLevel)
        audioController.setPlaybackRate(mrPlaybackRate)
        audioController.setSyncOffsetMs(syncOffsetMs)
        audioController.setKeyShift(keyShift)
        audioController.setMasterVolumeScale(effectiveVocalMasterScale)
        audioController.setBackingVolume(effectiveBackingVolume)
        youtubeController.setVolume(effectiveBackingVolume)
        youtubeController.setPlaybackRate(mrPlaybackRate)
        youtubeController.setSyncOffsetMs(syncOffsetMs)
        if (demoVocalSimEnabled) {
            audioController.setGuidePitch(guidePitchHz, guideNote, false)
        }
        audioController.setStagePlaying(isPlaying && unifiedPlaylist.isNotEmpty())
        PleosMediaSessionBridge.updatePlaybackAndMetadata(
            context = context,
            track = currentTrack,
            isPlaying = isPlaying && unifiedPlaylist.isNotEmpty(),
            positionMs = (songElapsedSec * 1000f).toLong(),
            playlist = unifiedPlaylist.toList()
        )
    }

    // Real-Time Mic & Guide Vocal Telemetry Loop (Always shifts 36-slot FIFO queue from right to left every 85ms)
    LaunchedEffect(Unit) {
        var vocalTick = 0
        while (true) {
            delay(85L)
            vocalTick++

            val nowMs = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()
            if (!micConnected || (nowMs - lastRealMicTelemetryMs > 450L)) {
                realMicRms = 0f
                realMicPitchHz = 0f
                realMicNote = "-"
            }
            val rawMicPercent = if (micConnected) realMicRms.roundToInt().coerceIn(0, 100) else 0
            if (!micConnected) {
                isSingingActive = false
                heldMicLevelPercent = 0
                debouncedMicNote = ""
            } else if (rawMicPercent >= 8) {
                isSingingActive = true
                lastSingingActiveElapsedMs = nowElapsed
                heldMicLevelPercent = rawMicPercent
            } else if (isSingingActive) {
                if (nowElapsed - lastSingingActiveElapsedMs >= 1000L) {
                    isSingingActive = false
                    heldMicLevelPercent = 0
                    debouncedMicNote = ""
                } else {
                    heldMicLevelPercent = maxOf(8, (heldMicLevelPercent * 0.90f).roundToInt())
                    if (debouncedMicNote.isNotEmpty() && nowElapsed - lastValidMicPitchElapsedMs >= 1000L) {
                        debouncedMicNote = ""
                    }
                }
            }

            val hasRealMic = micConnected && (nowMs - lastRealMicTelemetryMs <= 450L) && (rawMicPercent >= 8)
            val gt = audioController.nativeVocalEngine.guideTelemetry.value

            val newPitchSample: Float
            val newRmsSample: Float

            if (hasRealMic) {
                vocalRms = realMicRms
                vocalPitchHz = realMicPitchHz
                vocalNote = realMicNote
                if (realMicNote != "-" && realMicNote.isNotBlank()) {
                    lastValidMicPitchElapsedMs = nowElapsed
                    if (debouncedMicNote.isEmpty() ||
                        realMicNote == debouncedMicNote ||
                        nowElapsed - lastMicNoteDebounceElapsedMs >= 300L
                    ) {
                        if (realMicNote != debouncedMicNote) {
                            debouncedMicNote = realMicNote
                            lastMicNoteDebounceElapsedMs = nowElapsed
                        }
                    }
                } else if (debouncedMicNote.isNotEmpty() && nowElapsed - lastValidMicPitchElapsedMs >= 1000L) {
                    debouncedMicNote = ""
                }
                newPitchSample = (if (realMicPitchHz > 65f) realMicPitchHz else 262f).coerceIn(220f, 520f)
                newRmsSample = realMicRms
            } else if (demoVocalSimEnabled) {
                val simElapsedSec = if (isPlaying) syncedSongElapsedSec else (vocalTick * 0.085f * mrPlaybackRate)
                val (simHz, simNote) = NativeVocalAudioEngine.resolveRealtimeGuidePitch(
                    trackId = currentTrack.id,
                    bpm = currentTrack.bpm,
                    elapsedSec = simElapsedSec,
                    lyricBaseHz = 0f,
                    keyShiftSemitones = keyShift,
                    playbackRate = mrPlaybackRate,
                    syncOffsetMs = syncOffsetMs
                )
                val activeHz = if (gt.active && gt.guidePitchHz > 65f) gt.guidePitchHz else simHz
                val activeNote = if (gt.active && gt.guideNote != "-" && gt.guideNote.isNotBlank()) gt.guideNote else simNote
                val baseLevel = if (gt.active && gt.guideVoiceLevelPercent > 15f) gt.guideVoiceLevelPercent else 58f
                val waveModHz = (activeHz + sin(vocalTick * 0.62f) * 18f).coerceIn(220f, 520f)
                val waveModRms = (baseLevel + sin(vocalTick * 0.78f) * 18f).coerceIn(25f, 85f)

                vocalPitchHz = activeHz
                vocalNote = activeNote
                vocalRms = waveModRms
                if (activeNote != "-" && activeNote.isNotBlank() &&
                    (debouncedGuideNote.isEmpty() || nowElapsed - lastGuideNoteDebounceElapsedMs >= 300L)
                ) {
                    debouncedGuideNote = activeNote
                    lastGuideNoteDebounceElapsedMs = nowElapsed
                }
                newPitchSample = waveModHz
                newRmsSample = waveModRms
            } else {
                realMicPitchHz = 0f
                realMicNote = "-"
                if (!isSingingActive) {
                    vocalRms = 0f
                    vocalPitchHz = 0f
                    vocalNote = "-"
                }
                debouncedGuideNote = ""
                newPitchSample = 0f
                newRmsSample = 0f
            }

            // Shift elements left (index 1..35 -> 0..34) and push 1 new sample at index 35
            while (pitchHistory.size < 36) pitchHistory.add(0f)
            while (rmsHistory.size < 36) rmsHistory.add(0f)
            for (i in 0 until 35) {
                pitchHistory[i] = pitchHistory[i + 1]
                rmsHistory[i] = rmsHistory[i + 1]
            }
            pitchHistory[35] = newPitchSample
            rmsHistory[35] = newRmsSample

            // Strictly accumulate ONLY real connected smartphone microphone vocal input during playback (~11.8 ticks/sec)
            if (isPlaying && unifiedPlaylist.isNotEmpty()) {
                val realMicActive = micConnected && micVolume > 0 && (nowMs - lastRealMicTelemetryMs <= 350L)
                val activeMicLevel = if (realMicActive) rawMicPercent else 0
                val activeMicPitch = if (activeMicLevel >= 8) realMicPitchHz else 0f
                if (activeMicLevel >= 8) {
                    vocalScoreAccumulator.recordTick(
                        micLevelPercent = activeMicLevel,
                        pitchHz = activeMicPitch,
                        dtSeconds = 0.085
                    )
                    if (vocalTick % 4 == 0 && vocalScoreAccumulator.activeVoiceSeconds >= 1.5) {
                        val liveEst = KaraokeScoreCalculator.calculateFromAccumulator(
                            accumulator = vocalScoreAccumulator
                        ).score
                        currentScore = liveEst.coerceIn(82, 100)
                    }
                }
            }

            // Broadcast guide vocal telemetry to connected smartphones even when YouTube MR is paused/stopped!
            if (demoVocalSimEnabled && !isPlaying && vocalTick % 2 == 0) {
                broadcastSyncState(
                    overrideTrack = currentTrack,
                    overridePlaying = false,
                    overrideElapsed = songElapsedSec.roundToInt(),
                    bumpRevision = false
                )
            }
        }
    }

    // 100ms Backing Music Playback Timer & 500ms Bi-Directional Progress Sync Loop (Only when isPlaying == true)
    LaunchedEffect(isPlaying, currentTrack.id, currentTrack.genre, currentTrack.bpm) {
        var tick = 0
        while (isPlaying) {
            delay(100L)
            songElapsedSec += 0.1f * mrPlaybackRate.coerceIn(0.80f, 1.20f)
            tick++

            vehicleManager.onMusicAndVocalWaveformTick(
                genre = currentTrack.genre,
                bpm = (currentTrack.bpm * mrPlaybackRate).roundToInt().coerceIn(60, 200),
                elapsedSec = (songElapsedSec + (syncOffsetMs / 1000f)).coerceAtLeast(0f),
                vocalRms = vocalRms,
                vocalPitchHz = vocalPitchHz,
                isHighlightOrInterlude = vocalRms >= 70f
            )

            if (tick % 5 == 0) {
                broadcastSyncState(
                    overrideTrack = currentTrack,
                    overridePlaying = isPlaying,
                    overrideElapsed = songElapsedSec.roundToInt(),
                    bumpRevision = false
                )
            }

            if (tick % 10 == 0) {
                vehicleManager.accumulateHighOutputSingingSecond(vocalRms >= 65f)
            }

            if (songElapsedSec >= currentTrack.durationSec) {
                if (isScoreModeEnabled) {
                    triggerKaraokeScoreReveal(advanceToNext = true)
                } else {
                    playAdjacentTrackDirect(1)
                }
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

    // Defer heavy Chromium WebView cold-boot until after the first UI frame is rendered so there is no white screen
    var audioWebViewsReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        delay(600L)
        audioWebViewsReady = true
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PleosCanvasBg
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (audioWebViewsReady) {
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
                    if (connected) {
                        markUnifiedMicPacketReceived(deviceName, micTransportMode, count)
                    } else if (System.currentTimeMillis() - lastAnyMicPacketMs > 6000L) {
                        micConnected = false
                        micDeviceName = "○ 연결 대기 중"
                        connectedMicCount = 0
                    }
                },
                onMicLatencyUpdated = { totalMs, netMs, carBufMs, transport ->
                    val ignoreRelayLatency = transport == "RELAY" &&
                        (embeddedServer.serverState.value.connectedMicCount > 0 ||
                            micTransportMode == "DIRECT" ||
                            micTransportMode == "WEBRTC_P2P")
                    if (!ignoreRelayLatency) {
                        micTotalLatencyMs = totalMs.coerceIn(5, 999)
                        micNetLatencyMs = netMs.coerceIn(1, 500)
                        micBufferLatencyMs = carBufMs.coerceIn(0, 300)
                        micTransportMode = transport
                        markUnifiedMicPacketReceived(micDeviceName, transport)
                    }
                },
                onPhoneCandidateIps = { ips, port, devName ->
                    embeddedServer.registerCandidatePhoneIps(ips, port, devName)
                },
                onVocalTelemetry = { rms, pitchHz, note ->
                    applyIncomingMicVocalTelemetry(rms, pitchHz, note)
                },
                onRemoteCommand = { type, cmd ->
                    markUnifiedMicPacketReceived(micDeviceName, micTransportMode)
                    val cmdTs = cmd.optLong("ts", 0L)
                    if (shouldProcessRemoteCommand(type, cmdTs)) {
                        dispatchIncomingRemoteCommand(type, cmd)
                    }
                },
                onEngineLog = {},
                onMusicSpectrum = { bands ->
                    if (!currentTrack.isYouTube) {
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
                initialVideoId = currentTrack.videoId,
                initialVolume = effectiveBackingVolume,
                playbackRate = mrPlaybackRate,
                syncOffsetMs = syncOffsetMs,
                isPlaying = isPlaying && currentTrack.isYouTube,
                bpm = currentTrack.bpm,
                onPlayerStateChanged = { actualPlaying, ytTitle, _, _ ->
                    if (isPlaying && (!ytActuallyPlaying || actualPlaying)) {
                        onMarkInternalAudioStart()
                    }
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
                    if (isPlaying && activeScoreResult == null && currentTrack.isYouTube) {
                        if (isScoreModeEnabled) {
                            triggerKaraokeScoreReveal(advanceToNext = true)
                        } else {
                            playAdjacentTrackDirect(1)
                        }
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
                    if (currentTrack.isYouTube) {
                        updateMusicSpectrum(bands)
                    }
                }
            )
            }

            // Responsive Multi-Window Container (maxWidth >= 1000.dp: 3-column Landscape | maxWidth < 1000.dp: Compact Top Nav + Tabbed Portrait)
            BoxWithConstraints(
                modifier = Modifier
                    .zIndex(1f)
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .shadow(2.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(PleosPanelWhite)
                    .border(1.dp, PleosBorderSubtle, RoundedCornerShape(22.dp))
            ) {
                val isPortraitSplitWindow = maxWidth < 1000.dp
                val toggleDevSwitchVisibility: () -> Unit = {
                    if (BuildConfig.DEBUG) {
                        showDevModeSwitch = !showDevModeSwitch
                        Toast.makeText(
                            context,
                            if (showDevModeSwitch) "DEV / PROD 스위치가 표시되었습니다." else "심사 모드: DEV / PROD 스위치가 숨겨졌습니다 (로고 클릭 시 표시).",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                @Composable
                fun RenderActiveMenuContent(isCompactPortrait: Boolean) {
                    val baseDensity = LocalDensity.current
                    val contentDensity = if (activeMenu != PleosSidebarMenu.SDK_UNIT_TEST) {
                        Density(
                            density = baseDensity.density * 1.10f,
                            fontScale = baseDensity.fontScale * 1.12f
                        )
                    } else {
                        baseDensity
                    }
                    CompositionLocalProvider(LocalDensity provides contentDensity) {
                    when (activeMenu) {
                        PleosSidebarMenu.STAGE_LIVE -> {
                            PleosUnifiedLiveStagePane(
                                twinState = twinState,
                                currentTrack = currentTrack,
                                playlist = unifiedPlaylist,
                                isPlaying = isPlaying,
                                ytActuallyPlaying = ytActuallyPlaying,
                                ytErrorCode = ytErrorCode,
                                songElapsedSec = syncedSongElapsedSec,
                                backingVolume = backingVolume,
                                micVolume = micVolume,
                                guideVolume = guideVolume,
                                mrPlaybackRate = mrPlaybackRate,
                                syncOffsetMs = syncOffsetMs,
                                keyShift = keyShift,
                                echoLevel = echoLevel,
                                currentScore = currentScore,
                                vocalRms = vocalRms,
                                vocalPitchHz = vocalPitchHz,
                                vocalNote = vocalNote,
                                realMicRms = realMicRms,
                                realMicPitchHz = realMicPitchHz,
                                realMicNote = realMicNote,
                                isSingingActive = isSingingActive,
                                heldMicLevelPercent = heldMicLevelPercent,
                                debouncedMicNote = debouncedMicNote,
                                debouncedGuideNote = debouncedGuideNote,
                                pitchHistory = pitchHistory,
                                rmsHistory = rmsHistory,
                                musicSpectrumBands = musicSpectrumBands,
                                ambientColor = animatedAmbientColor,
                                demoVocalSimEnabled = demoVocalSimEnabled,
                                micConnected = micConnected,
                                micTotalLatencyMs = micTotalLatencyMs,
                                micTransportMode = micTransportMode,
                                isDevMode = isDevMode,
                                showDevModeSwitch = showDevModeSwitch,
                                isCompactPortrait = isCompactPortrait,
                                portraitLiveSubTab = portraitLiveSubTab,
                                onSelectPortraitSubTab = { portraitLiveSubTab = it },
                                addTrackInput = addTrackInput,
                                onToggleDevMode = { applyDevProdMode(!isDevMode) },
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
                                    val effVol = (backingVolume * duckScale).roundToInt().coerceIn(0, 100)
                                    youtubeController.setVolume(effVol)
                                    audioController.setBackingVolume(effVol)
                                    broadcastSyncState()
                                },
                                onMicVolumeChange = { newVol ->
                                    micVolume = newVol.coerceIn(0, 100)
                                    audioController.setMicVolume(micVolume)
                                    broadcastSyncState()
                                },
                                onGuideVolumeChange = { newVol ->
                                    guideVolume = newVol.coerceIn(0, 100)
                                    audioController.setGuideVolume(guideVolume)
                                    broadcastSyncState()
                                },
                                onEchoChange = { newEcho ->
                                    echoLevel = newEcho.coerceIn(0, 100)
                                    audioController.setEchoLevel(echoLevel)
                                    broadcastSyncState()
                                },
                                onPlaybackRateChange = { newRate ->
                                    val stepped = ((newRate.coerceIn(0.75f, 1.25f) * 20f).roundToInt() / 20f)
                                    mrPlaybackRate = stepped
                                    youtubeController.setPlaybackRate(stepped)
                                    audioController.setPlaybackRate(stepped)
                                    broadcastSyncState()
                                },
                                onSyncOffsetChange = { newOffset ->
                                    val prevOff = syncOffsetMs
                                    val stepped = ((newOffset.coerceIn(-500, 500) / 50.0).roundToInt() * 50).coerceIn(-500, 500)
                                    val deltaMs = stepped - prevOff
                                    syncOffsetMs = stepped
                                    youtubeController.adjustSyncOffset(stepped, deltaMs)
                                    audioController.adjustSyncOffsetMs(stepped, deltaMs)
                                    broadcastSyncState()
                                },
                                onResetAudioMixer = {
                                    val resetSyncDelta = -syncOffsetMs
                                    backingVolume = 85
                                    micVolume = 85
                                    guideVolume = 75
                                    echoLevel = 80
                                    mrPlaybackRate = 1.00f
                                    syncOffsetMs = 0
                                    val effVol = (85 * duckScale).roundToInt().coerceIn(0, 100)
                                    youtubeController.setVolume(effVol)
                                    youtubeController.setPlaybackRate(1.00f)
                                    youtubeController.adjustSyncOffset(0, resetSyncDelta)
                                    audioController.setBackingVolume(effVol)
                                    audioController.setMicVolume(85)
                                    audioController.setGuideVolume(75)
                                    audioController.setEchoLevel(80)
                                    audioController.setPlaybackRate(1.00f)
                                    audioController.adjustSyncOffsetMs(0, resetSyncDelta)
                                    broadcastSyncState()
                                },
                                onToggleDemoVocal = {
                                    demoVocalSimEnabled = !demoVocalSimEnabled
                                    if (demoVocalSimEnabled) {
                                        onRequestAudioFocus()
                                    } else if (!isSingingActive) {
                                        vocalRms = 0f
                                        vocalPitchHz = 0f
                                        vocalNote = "-"
                                        debouncedGuideNote = ""
                                    }
                                    audioController.setGuideVocalEnabled(demoVocalSimEnabled)
                                    broadcastSyncState()
                                },
                                onTriggerSfx = { audioController.triggerSfx(it) },
                                onOpenPairing = { activeMenu = PleosSidebarMenu.MIC_PAIRING },
                                onToggleWindows = { vehicleManager.toggleSoundproofWindowsManually() },
                                onToggleSmartGlass = { vehicleManager.toggleBlackoutSmartGlassManually() },
                                onToggleAmbientLight = { vehicleManager.toggleAmbientLightManually() },
                                onTriggerGreenroom = {
                                    vehicleManager.triggerGreenroomCooldownCare("라이브 스테이지 칩 그린룸 케어 실행")
                                },
                                onOpenVehicleStageMenu = { activeMenu = PleosSidebarMenu.VEHICLE_4D },
                                isScoreModeEnabled = isScoreModeEnabled,
                                activeScoreResult = activeScoreResult,
                                onToggleScoreMode = { setScoreModeState(!isScoreModeEnabled) },
                                onStopStage = { stopUnifiedStageWithScoreCheck() },
                                onSeekTo = { targetSec -> seekUnifiedTrackTo(targetSec) },
                                onDismissScoreOverlay = { dismissScoreOverlayAndContinue(fromRemote = false) }
                            )
                        }

                        PleosSidebarMenu.VEHICLE_4D -> {
                            LaunchedEffect(Unit) {
                                vehicleManager.refreshGearSelectionState()
                            }
                            PleosQuickSettings4DPane(
                                twinState = twinState,
                                echoLevel = echoLevel,
                                mediaVolume = mediaVolume,
                                isDevMode = isDevMode,
                                showDevModeSwitch = showDevModeSwitch,
                                isCompactPortrait = isCompactPortrait,
                                onToggleDevMode = { applyDevProdMode(!isDevMode) },
                                onToggleWindows = { vehicleManager.toggleSoundproofWindowsManually() },
                                onToggleSmartGlass = { vehicleManager.toggleBlackoutSmartGlassManually() },
                                onToggleAirCleaner = { vehicleManager.toggleAirCleanerManually() },
                                onToggleAmbientLight = { vehicleManager.cycleLightingControlMode() },
                                onSelectLightingMode = { mode -> vehicleManager.setLightingControlMode(mode) },
                                onToggleEcoStage = { vehicleManager.toggleEcoStageModeManually() },
                                onTriggerGreenroom = {
                                    vehicleManager.triggerGreenroomCooldownCare("쾌적 냉방 모드 가동")
                                },
                                onCycleSeatVentilation = { vehicleManager.cycleOccupiedSeatVentilationLevel() },
                                onToggleSeatOccupancy = { seatIdx -> vehicleManager.toggleSeatOccupancyForStage(seatIdx) },
                                onAdjustHvacTemp = { delta -> vehicleManager.adjustHvacTemperature(delta) },
                                onAdjustHvacFan = { delta -> vehicleManager.adjustHvacFanSpeed(delta) },
                                onAmbientBrightnessChange = { b -> vehicleManager.setAmbientBrightnessManually(b) },
                                onSmartGlassLevelChange = { lvl -> vehicleManager.setSmartGlassLevelManually(lvl) },
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
                                micTotalLatencyMs = micTotalLatencyMs,
                                micTransportMode = micTransportMode,
                                micDeviceName = micDeviceName,
                                connectedMicCount = connectedMicCount,
                                demoVocalSimEnabled = demoVocalSimEnabled,
                                isDevMode = isDevMode,
                                showDevModeSwitch = showDevModeSwitch,
                                isSetupRestricted = twinState.isSetupRestricted,
                                isCompactPortrait = isCompactPortrait,
                                onToggleDevMode = { applyDevProdMode(!isDevMode) },
                                onToggleDemoSim = {
                                    demoVocalSimEnabled = !demoVocalSimEnabled
                                    if (demoVocalSimEnabled) {
                                        onRequestAudioFocus()
                                    } else if (!isSingingActive) {
                                        vocalRms = 0f
                                        vocalPitchHz = 0f
                                        vocalNote = "-"
                                        debouncedGuideNote = ""
                                    }
                                    audioController.setGuideVocalEnabled(demoVocalSimEnabled)
                                    broadcastSyncState()
                                },
                                onDirectConnectPhoneIp = { phoneTarget ->
                                    embeddedServer.connectOrScanHotspotTarget(phoneTarget, 8082, "Pleos Smart Mic")
                                    val label = if (phoneTarget.isBlank()) "주변 마이크 자동 탐색" else phoneTarget
                                    Toast.makeText(
                                        context,
                                        "스마트폰 마이크 연결 탐색 중: $label",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }

                        PleosSidebarMenu.SDK_UNIT_TEST -> {
                            if (BuildConfig.DEBUG && isDevMode) {
                                PleosSdkUnitTestPane(
                                    vehicleManager = vehicleManager,
                                    modifier = Modifier.fillMaxSize(),
                                    headerTrailingContent = if (showDevModeSwitch && !isCompactPortrait) {
                                        {
                                            PleosDevProdHeaderBadge(
                                                isDevMode = isDevMode,
                                                onToggleDevMode = { applyDevProdMode(!isDevMode) }
                                            )
                                        }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }

                        PleosSidebarMenu.SOUND_REVERB -> {
                            PleosSoundReverbPane(
                                backingVolume = backingVolume,
                                micVolume = micVolume,
                                guideVolume = guideVolume,
                                echoLevel = echoLevel,
                                mrPlaybackRate = mrPlaybackRate,
                                syncOffsetMs = syncOffsetMs,
                                keyShift = keyShift,
                                mediaVolume = mediaVolume,
                                isDevMode = isDevMode,
                                showDevModeSwitch = showDevModeSwitch,
                                onToggleDevMode = { applyDevProdMode(!isDevMode) },
                                onBackingVolumeChange = {
                                    backingVolume = it.coerceIn(0, 100)
                                    val effVol = (backingVolume * duckScale).roundToInt().coerceIn(0, 100)
                                    youtubeController.setVolume(effVol)
                                    audioController.setBackingVolume(effVol)
                                    broadcastSyncState()
                                },
                                onMicVolumeChange = {
                                    micVolume = it.coerceIn(0, 100)
                                    audioController.setMicVolume(micVolume)
                                    broadcastSyncState()
                                },
                                onGuideVolumeChange = {
                                    guideVolume = it.coerceIn(0, 100)
                                    audioController.setGuideVolume(guideVolume)
                                    broadcastSyncState()
                                },
                                onEchoChange = {
                                    echoLevel = it.coerceIn(0, 100)
                                    audioController.setEchoLevel(echoLevel)
                                    broadcastSyncState()
                                },
                                onPlaybackRateChange = {
                                    val stepped = ((it.coerceIn(0.75f, 1.25f) * 20f).roundToInt() / 20f)
                                    mrPlaybackRate = stepped
                                    youtubeController.setPlaybackRate(stepped)
                                    audioController.setPlaybackRate(stepped)
                                    broadcastSyncState()
                                },
                                onSyncOffsetChange = {
                                    val prevOff = syncOffsetMs
                                    val stepped = ((it.coerceIn(-500, 500) / 50.0).roundToInt() * 50).coerceIn(-500, 500)
                                    val deltaMs = stepped - prevOff
                                    syncOffsetMs = stepped
                                    youtubeController.adjustSyncOffset(stepped, deltaMs)
                                    audioController.adjustSyncOffsetMs(stepped, deltaMs)
                                    broadcastSyncState()
                                },
                                onKeyShiftChange = {
                                    keyShift = it.coerceIn(-6, 6)
                                    audioController.setKeyShift(keyShift)
                                    broadcastSyncState()
                                },
                                onVolumeChange = { mediaVolume = it.coerceIn(0, 15) },
                                onResetAll = {
                                    val resetSyncDelta = -syncOffsetMs
                                    backingVolume = 85
                                    micVolume = 85
                                    guideVolume = 75
                                    echoLevel = 80
                                    mrPlaybackRate = 1.00f
                                    syncOffsetMs = 0
                                    keyShift = 0
                                    val effVol = (85 * duckScale).roundToInt().coerceIn(0, 100)
                                    youtubeController.setVolume(effVol)
                                    youtubeController.setPlaybackRate(1.00f)
                                    youtubeController.adjustSyncOffset(0, resetSyncDelta)
                                    audioController.setBackingVolume(effVol)
                                    audioController.setMicVolume(85)
                                    audioController.setGuideVolume(75)
                                    audioController.setEchoLevel(80)
                                    audioController.setPlaybackRate(1.00f)
                                    audioController.adjustSyncOffsetMs(0, resetSyncDelta)
                                    audioController.setKeyShift(0)
                                    broadcastSyncState()
                                }
                            )
                        }
                    }
                    }
                }

                if (!isPortraitSplitWindow) {
                    // maxWidth >= 1000.dp (Landscape 확장 창): 기존 3단 레이아웃(좌측 사이드바 + 중앙 스테이지 + 우측 플레이리스트) 그대로 유지
                    Row(modifier = Modifier.fillMaxSize()) {
                        PleosAppSidebar(
                            activeMenu = activeMenu,
                            micConnected = micConnected,
                            micTotalLatencyMs = micTotalLatencyMs,
                            micTransportMode = micTransportMode,
                            roomPin = roomPin,
                            twinState = twinState,
                            isDevMode = isDevMode,
                            showDevModeSwitch = showDevModeSwitch,
                            onLogoClick = toggleDevSwitchVisibility,
                            onSelectMenu = { activeMenu = it }
                        )

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(PleosBorderSubtle)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(20.dp)
                        ) {
                            RenderActiveMenuContent(isCompactPortrait = false)
                        }
                    }
                } else {
                    // maxWidth < 1000.dp (Portrait 1:1 분할 창): 상단 컴팩트 네비게이션 바 + 탭/스크롤 반응형 구조
                    Column(modifier = Modifier.fillMaxSize()) {
                        PleosCompactTopNavBar(
                            activeMenu = activeMenu,
                            micConnected = micConnected,
                            micTotalLatencyMs = micTotalLatencyMs,
                            micTransportMode = micTransportMode,
                            roomPin = roomPin,
                            twinState = twinState,
                            isDevMode = isDevMode,
                            showDevModeSwitch = showDevModeSwitch,
                            onLogoClick = toggleDevSwitchVisibility,
                            onToggleDevMode = { applyDevProdMode(!isDevMode) },
                            onSelectMenu = { activeMenu = it }
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(PleosBorderSubtle)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            RenderActiveMenuContent(isCompactPortrait = true)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 1. APP WINDOW LEFT SIDEBAR & PORTRAIT COMPACT TOP NAV BAR
// ============================================================================
@Composable
private fun PleosAppSidebar(
    activeMenu: PleosSidebarMenu,
    micConnected: Boolean,
    micTotalLatencyMs: Int = 32,
    micTransportMode: String = "DIRECT",
    roomPin: String,
    twinState: VehicleDigitalTwinState,
    isDevMode: Boolean,
    showDevModeSwitch: Boolean,
    onLogoClick: () -> Unit,
    onSelectMenu: (PleosSidebarMenu) -> Unit
) {
    Column(
        modifier = Modifier
            .width(236.dp)
            .fillMaxHeight()
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val brandTitle = stringResource(R.string.brand_title)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (BuildConfig.DEBUG) Modifier.clickable { onLogoClick() } else Modifier)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = brandTitle,
                        color = PleosTextDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (brandTitle != "CAR IN KARAOKE") {
                        Text(
                            text = "CAR IN KARAOKE",
                            color = PleosTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.4.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                if (BuildConfig.DEBUG && showDevModeSwitch && isDevMode) {
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

            val visibleMenus = remember(isDevMode) {
                PleosSidebarMenu.entries.filter { it != PleosSidebarMenu.SDK_UNIT_TEST || (BuildConfig.DEBUG && isDevMode) }
            }
            visibleMenus.forEach { menu ->
                val selected = menu == activeMenu
                val isSdkDevTab = menu == PleosSidebarMenu.SDK_UNIT_TEST
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when {
                                selected && isSdkDevTab -> PleosDevRed
                                selected -> PleosBlue
                                isSdkDevTab -> PleosDevRedSoft
                                else -> Color.Transparent
                            }
                        )
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
                            tint = when {
                                selected -> Color.White
                                isSdkDevTab -> PleosDevRed
                                else -> PleosTextDark
                            },
                            modifier = Modifier.size(21.dp)
                        )

                        Text(
                            text = menu.title,
                            color = when {
                                selected -> Color.White
                                isSdkDevTab -> PleosDevRed
                                else -> PleosTextDark
                            },
                            fontSize = if (isSdkDevTab) 14.5.sp else 16.sp,
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

        // Bottom Status Pill at Bottom of Sidebar (PIN & Concise Vehicle Summary — Zero Truncation)
        val gearSummary = when (twinState.gearPosition) {
            GearPosition.P -> "P 주차"
            GearPosition.D -> "D 주행"
            GearPosition.N -> "N 중립"
            GearPosition.R -> "R 후진"
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PleosTileGray)
                .border(1.dp, PleosBorderSubtle, RoundedCornerShape(16.dp))
                .clickable { onSelectMenu(PleosSidebarMenu.MIC_PAIRING) }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = if (micConnected) {
                    "● 마이크 연결됨 · ⚡ ${micTotalLatencyMs}ms"
                } else {
                    "○ 마이크 대기 중 (PIN $roomPin)"
                },
                color = if (micConnected) PleosGreen else PleosTextMuted,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$gearSummary · ${twinState.hvacTempCelsius}°C · 풍량 ${twinState.hvacFanLevel}단",
                color = PleosTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PleosCompactTopNavBar(
    activeMenu: PleosSidebarMenu,
    micConnected: Boolean,
    micTotalLatencyMs: Int = 32,
    micTransportMode: String = "DIRECT",
    roomPin: String,
    twinState: VehicleDigitalTwinState,
    isDevMode: Boolean,
    showDevModeSwitch: Boolean,
    onLogoClick: () -> Unit,
    onToggleDevMode: () -> Unit,
    onSelectMenu: (PleosSidebarMenu) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PleosPanelWhite)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val brandTitle = stringResource(R.string.brand_title)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (BuildConfig.DEBUG) Modifier.clickable { onLogoClick() } else Modifier)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = brandTitle,
                        color = PleosTextDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (brandTitle != "CAR IN KARAOKE") {
                        Text(
                            text = "CAR IN KARAOKE",
                            color = PleosTextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.4.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                if (BuildConfig.DEBUG && showDevModeSwitch && isDevMode) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = PleosDevRed
                    ) {
                        Text(
                            text = "DEV",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (micConnected) PleosGreen.copy(alpha = 0.14f) else PleosTileGray,
                    border = BorderStroke(
                        1.dp,
                        if (micConnected) PleosGreen.copy(alpha = 0.55f) else PleosBorderSubtle
                    ),
                    modifier = Modifier.clickable { onSelectMenu(PleosSidebarMenu.MIC_PAIRING) }
                ) {
                    Text(
                        text = if (micConnected) {
                            "● 스마트폰 마이크 연결됨 · ⚡ ${micTotalLatencyMs}ms"
                        } else {
                            "○ 연결 대기 중"
                        },
                        color = if (micConnected) PleosGreen else PleosTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = PleosTileGray
                ) {
                    Text(
                        text = "PIN $roomPin · 기어 ${twinState.gearPosition.label}",
                        color = if (twinState.isParked) PleosTextDark else PleosAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                if (BuildConfig.DEBUG && showDevModeSwitch) {
                    PleosDevProdHeaderBadge(
                        isDevMode = isDevMode,
                        onToggleDevMode = onToggleDevMode
                    )
                }
            }
        }

        val visibleMenus = remember(isDevMode) {
            PleosSidebarMenu.entries.filter { it != PleosSidebarMenu.SDK_UNIT_TEST || (BuildConfig.DEBUG && isDevMode) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            visibleMenus.forEach { menu ->
                val selected = menu == activeMenu
                val isSdkDevTab = menu == PleosSidebarMenu.SDK_UNIT_TEST
                Row(
                    modifier = Modifier
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                selected && isSdkDevTab -> PleosDevRed
                                selected -> PleosBlue
                                isSdkDevTab -> PleosDevRedSoft
                                else -> PleosTileGray
                            }
                        )
                        .clickable { onSelectMenu(menu) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = menu.icon,
                        contentDescription = menu.title,
                        tint = when {
                            selected -> Color.White
                            isSdkDevTab -> PleosDevRed
                            else -> PleosTextDark
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = menu.title,
                        color = when {
                            selected -> Color.White
                            isSdkDevTab -> PleosDevRed
                            else -> PleosTextDark
                        },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
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
    playlist: List<UnifiedStageTrack>,
    isPlaying: Boolean,
    ytActuallyPlaying: Boolean,
    ytErrorCode: String,
    songElapsedSec: Float,
    backingVolume: Int,
    micVolume: Int = 85,
    guideVolume: Int = 75,
    mrPlaybackRate: Float = 1.00f,
    syncOffsetMs: Int = 0,
    keyShift: Int,
    echoLevel: Int,
    currentScore: Int,
    vocalRms: Float,
    vocalPitchHz: Float,
    vocalNote: String,
    realMicRms: Float,
    realMicPitchHz: Float,
    realMicNote: String,
    isSingingActive: Boolean = false,
    heldMicLevelPercent: Int = 0,
    debouncedMicNote: String = "",
    debouncedGuideNote: String = "",
    pitchHistory: List<Float>,
    rmsHistory: List<Float>,
    musicSpectrumBands: List<Float>,
    ambientColor: Color,
    demoVocalSimEnabled: Boolean,
    micConnected: Boolean = false,
    micTotalLatencyMs: Int = 32,
    micTransportMode: String = "DIRECT",
    isDevMode: Boolean,
    showDevModeSwitch: Boolean = true,
    isCompactPortrait: Boolean = false,
    portraitLiveSubTab: Int = 0,
    onSelectPortraitSubTab: (Int) -> Unit = {},
    addTrackInput: String,
    onToggleDevMode: () -> Unit,
    onAddTrackInputChange: (String) -> Unit,
    onAddTrack: (String) -> Unit,
    onSelectAndPlayTrack: (UnifiedStageTrack) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onTogglePlay: () -> Unit,
    onPrevTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onBackingVolumeChange: (Int) -> Unit,
    onMicVolumeChange: (Int) -> Unit = {},
    onGuideVolumeChange: (Int) -> Unit = {},
    onEchoChange: (Int) -> Unit,
    onPlaybackRateChange: (Float) -> Unit = {},
    onSyncOffsetChange: (Int) -> Unit = {},
    onResetAudioMixer: () -> Unit = {},
    onToggleDemoVocal: () -> Unit,
    onTriggerSfx: (String) -> Unit,
    onOpenPairing: () -> Unit,
    onToggleWindows: () -> Unit,
    onToggleSmartGlass: () -> Unit = {},
    onToggleAmbientLight: () -> Unit = {},
    onTriggerGreenroom: () -> Unit,
    onOpenVehicleStageMenu: () -> Unit = {},
    isScoreModeEnabled: Boolean = true,
    activeScoreResult: KaraokeScoreResult? = null,
    onToggleScoreMode: () -> Unit = {},
    onStopStage: () -> Unit = {},
    onSeekTo: (Int) -> Unit = {},
    onDismissScoreOverlay: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isKeyboardRestricted = twinState.isKeyboardRestricted
    var showDrivingKeyboardBanner by remember { mutableStateOf(false) }

    LaunchedEffect(isKeyboardRestricted) {
        if (isKeyboardRestricted) {
            focusManager.clearFocus()
        } else {
            showDrivingKeyboardBanner = false
        }
    }

    val animatedRms by animateFloatAsState(
        targetValue = vocalRms,
        animationSpec = tween(90),
        label = "stageVocalRms"
    )

    var draggedTrackId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val stageSection: @Composable (Modifier) -> Unit = { stageModifier ->
        // ====================================================================
        // 좌측/중앙 메인 무대
        // ====================================================================
        Column(
            modifier = stageModifier,
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
                        if (BuildConfig.DEBUG && showDevModeSwitch && isDevMode && playlist.isNotEmpty() && currentTrack.isDevSample) {
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

                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (micConnected) PleosGreen.copy(alpha = 0.14f) else PleosTileGray,
                    border = BorderStroke(
                        1.dp,
                        if (micConnected) PleosGreen.copy(alpha = 0.55f) else PleosBorderSubtle
                    ),
                    modifier = Modifier.clickable { onOpenPairing() }
                ) {
                    Text(
                        text = if (micConnected) {
                            "● 스마트폰 마이크 연결됨 · ⚡ ${micTotalLatencyMs}ms"
                        } else {
                            "○ 연결 대기 중"
                        },
                        color = if (micConnected) PleosGreen else PleosTextMuted,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                if (showDevModeSwitch && !isCompactPortrait) {
                    Spacer(modifier = Modifier.width(12.dp))

                    PleosDevProdHeaderBadge(
                        isDevMode = isDevMode,
                        onToggleDevMode = onToggleDevMode
                    )
                }
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
                        // 상단: 차량 하드웨어 실시간 상태 요약 칩 (SeatOccupancy 배지 · Ambient · Window · SmartGlass · HVAC/Seat · EvBattery · Gear)
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
                                    text = if (twinState.occupiedSeatCount <= 1) "운전석 사운드" else "전 좌석 사운드",
                                    dotColor = PleosCyanBlue,
                                    onClick = onOpenVehicleStageMenu
                                )
                                val lightingChipLabel = when (twinState.lightingControlMode) {
                                    LightingControlMode.OFF -> "무드조명 꺼짐(0%)"
                                    LightingControlMode.FIXED -> "무드조명 고정 ${twinState.ambientBrightness}%"
                                    LightingControlMode.AUTO_MUSIC -> "무드조명 자동 ${twinState.ambientBrightness}%"
                                }
                                PleosGlassHardwareChip(
                                    text = lightingChipLabel,
                                    dotColor = if (twinState.lightingControlMode == LightingControlMode.OFF) PleosSlatePreview else ambientColor,
                                    onClick = onToggleAmbientLight
                                )
                                PleosGlassHardwareChip(
                                    text = if (twinState.allWindowsClosed) "방음 모드: 창문 닫힘" else "방음 모드: 창문 열림",
                                    dotColor = if (twinState.allWindowsClosed) PleosGreen else PleosAmber,
                                    onClick = onToggleWindows
                                )
                                PleosGlassHardwareChip(
                                    text = if (twinState.smartGlassBlackoutEnabled) "암막 글래스 켜짐" else "암막 글래스 꺼짐",
                                    dotColor = if (twinState.smartGlassBlackoutEnabled) PleosStagePurple else PleosSlatePreview,
                                    onClick = onToggleSmartGlass
                                )
                                PleosGlassHardwareChip(
                                    text = "${twinState.hvacTempCelsius}°C · 통풍 ${twinState.seatVentilationLevel}단",
                                    dotColor = if (twinState.seatVentilationLevel > 0) PleosCyanBlue else PleosSlatePreview,
                                    onClick = onTriggerGreenroom
                                )
                                PleosGlassHardwareChip(
                                    text = if (twinState.ecoStageModeActive) {
                                        "배터리 절전 (${twinState.evBatterySocPercent}%)"
                                    } else {
                                        "배터리 ${twinState.evBatterySocPercent}%"
                                    },
                                    dotColor = if (twinState.evBatterySocPercent <= 20 || twinState.ecoStageModeActive) PleosAmber else PleosGreen,
                                    onClick = onOpenVehicleStageMenu
                                )
                                PleosGlassHardwareChip(
                                    text = "주행 안전: ${twinState.gearPosition.label}단",
                                    dotColor = if (twinState.isParked) PleosGreen else PleosAmber,
                                    onClick = onOpenVehicleStageMenu
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.clickable { onToggleScoreMode() },
                                    shape = RoundedCornerShape(50),
                                    color = if (isScoreModeEnabled) Color(0xFFF59E0B).copy(alpha = 0.20f) else Color(0xFF475569).copy(alpha = 0.35f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isScoreModeEnabled) Color(0xFFFBBF24).copy(alpha = 0.72f) else Color(0xFF64748B).copy(alpha = 0.55f)
                                    )
                                ) {
                                    Text(
                                        text = if (isScoreModeEnabled) {
                                            "🏆 점수 ${currentScore}점 · 공간감 ${echoLevel}%"
                                        } else {
                                            "점수 측정 OFF · 공간감 ${echoLevel}%"
                                        },
                                        color = if (isScoreModeEnabled) Color(0xFFFDE68A) else Color(0xFFCBD5E1),
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
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
                                            playlist.isEmpty() -> "무대 준비 완료 · 부르고 싶은 곡을 추가해 보세요"
                                            !twinState.isParked -> "🚗 주행 안전 음악 감상 모드 (기어 ${twinState.gearPosition.label})"
                                            currentTrack.isYouTube && ytErrorCode.isNotBlank() ->
                                                "⚠️ 유튜브 영상 재생이 제한된 곡입니다 · 다른 유튜브 곡을 선택해 보세요"
                                            isPlaying -> "♪ 신나는 라이브 무대 진행 중"
                                            else -> "무대 준비 완료 · 재생을 누르면 시작됩니다"
                                        },
                                        color = if (!twinState.isParked || (currentTrack.isYouTube && ytErrorCode.isNotBlank())) PleosAmber else PleosCyanBlue,
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
                                } else {
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
                                            text = "♪ 반주 파형에 맞춰 자유롭게 노래해보세요 · 공간 음향과 실내 무드 조명이 음악에 반응합니다",
                                            color = PleosSlatePreview,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                // 3. 실제 재생 중인 반주 음원(MR)의 실시간 48-Band FFT 오디오 스펙트럼 파형 비주얼라이저 (최소 높이 120.dp 이상 -> 132.dp, 가로폭 100%)
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
                                            val bandEnergy = if (isPlaying && playlist.isNotEmpty()) {
                                                musicSpectrumBands.getOrElse(i) { 0f }.coerceIn(0f, 1.0f)
                                            } else {
                                                0f
                                            }

                                            if (bandEnergy > 0.005f) {
                                                val topH = (maxTopH * bandEnergy).coerceAtLeast(2.dp.toPx())
                                                val bottomReflectH = topH * 0.42f
                                                val x = i * slotW + (slotW - barW) * 0.5f

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

                                                if (bandEnergy > 0.18f) {
                                                    drawRoundRect(
                                                        color = Color.White.copy(alpha = 0.90f),
                                                        topLeft = Offset(x, (centerY - topH - 5.dp.toPx()).coerceAtLeast(0f)),
                                                        size = Size(barW, 3.dp.toPx()),
                                                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                                    )
                                                }

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

                        // 4. 스테이지 하단 진행 시간 바 (00:06 ━━━●────── 03:28) + 터치/드래그 탐색(Seek) + 웨이브폼 실루엣 통합
                        val totalDurationSec = currentTrack.durationSec.coerceAtLeast(1)
                        var progressBarWidthPx by remember { mutableFloatStateOf(1f) }
                        var isScrubbingProgress by remember(currentTrack.id) { mutableStateOf(false) }
                        var scrubFraction by remember(currentTrack.id) { mutableFloatStateOf(0f) }
                        val liveFraction = (songElapsedSec / totalDurationSec.toFloat()).coerceIn(0f, 1f)
                        val progressFraction = if (isScrubbingProgress) scrubFraction else liveFraction
                        val displayedElapsedSec = if (isScrubbingProgress) {
                            (scrubFraction * totalDurationSec).roundToInt().coerceIn(0, totalDurationSec)
                        } else {
                            songElapsedSec.roundToInt().coerceIn(0, totalDurationSec)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = UnifiedStageTrack.formatDurationMmSs(displayedElapsedSec),
                                color = PleosCyanBlue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .onSizeChanged { sz ->
                                        if (sz.width > 0) {
                                            progressBarWidthPx = sz.width.toFloat()
                                        }
                                    }
                                    .pointerInput(currentTrack.id, totalDurationSec, playlist.isNotEmpty()) {
                                        if (playlist.isEmpty()) return@pointerInput
                                        detectTapGestures { offset ->
                                            val w = progressBarWidthPx.coerceAtLeast(1f)
                                            val frac = (offset.x / w).coerceIn(0f, 1f)
                                            val targetSec = (frac * totalDurationSec).roundToInt().coerceIn(0, totalDurationSec)
                                            isScrubbingProgress = false
                                            onSeekTo(targetSec)
                                        }
                                    }
                                    .pointerInput(currentTrack.id, totalDurationSec, playlist.isNotEmpty()) {
                                        if (playlist.isEmpty()) return@pointerInput
                                        detectHorizontalDragGestures(
                                            onDragStart = { startOffset ->
                                                val w = progressBarWidthPx.coerceAtLeast(1f)
                                                scrubFraction = (startOffset.x / w).coerceIn(0f, 1f)
                                                isScrubbingProgress = true
                                            },
                                            onHorizontalDrag = { change, _ ->
                                                change.consume()
                                                val w = progressBarWidthPx.coerceAtLeast(1f)
                                                scrubFraction = (change.position.x / w).coerceIn(0f, 1f)
                                            },
                                            onDragEnd = {
                                                val targetSec = (scrubFraction * totalDurationSec).roundToInt().coerceIn(0, totalDurationSec)
                                                isScrubbingProgress = false
                                                onSeekTo(targetSec)
                                            },
                                            onDragCancel = {
                                                isScrubbingProgress = false
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(26.dp)
                                ) {
                                    val w = size.width
                                    val h = size.height
                                    val midY = h * 0.5f
                                    val silhouetteBars = 72
                                    val slotW = w / silhouetteBars
                                    val barW = (slotW * 0.56f).coerceAtLeast(2.5f)
                                    val seed = currentTrack.id.hashCode()

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
                                    drawCircle(
                                        color = PleosCyanBlue.copy(alpha = if (isScrubbingProgress) 0.55f else 0.38f),
                                        radius = if (isScrubbingProgress) 11.dp.toPx() else 8.dp.toPx(),
                                        center = Offset(playheadX, midY)
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = if (isScrubbingProgress) 6.dp.toPx() else 4.5.dp.toPx(),
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

                    // 5초간 다이내믹 노래방 점수 발표 오버레이 (차량 중앙 무대 화면)
                    if (activeScoreResult != null && isScoreModeEnabled) {
                        PleosKaraokeScoreOverlay(
                            result = activeScoreResult,
                            onDismiss = onDismissScoreOverlay
                        )
                    }
                }

                // 하단 가이드 음정 & 마이크/가이드 보컬 파형 바 (VAD >= 8% + 1.0초 Hold Hysteresis + 300ms 음정 디바운스 적용으로 점멸 완전 차단)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PleosTileGray)
                        .border(1.dp, PleosBorderSubtle, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isVocalStageActive = demoVocalSimEnabled || isSingingActive
                    val latestHistoryPitch = pitchHistory.lastOrNull { it > 65f } ?: 0f
                    val (fallbackTargetHz, fallbackTargetNote) = if (isVocalStageActive) {
                        NativeVocalAudioEngine.resolveRealtimeGuidePitch(
                            trackId = currentTrack.id,
                            bpm = currentTrack.bpm,
                            elapsedSec = songElapsedSec,
                            lyricBaseHz = 0f,
                            keyShiftSemitones = keyShift
                        )
                    } else {
                        0f to "-"
                    }
                    val activeTargetHz = when {
                        demoVocalSimEnabled && vocalPitchHz > 65f -> vocalPitchHz
                        demoVocalSimEnabled -> fallbackTargetHz
                        isSingingActive && realMicPitchHz > 65f -> realMicPitchHz
                        isSingingActive && vocalPitchHz > 65f -> vocalPitchHz
                        isSingingActive && latestHistoryPitch > 65f -> latestHistoryPitch
                        isSingingActive -> fallbackTargetHz
                        else -> 0f
                    }
                    val activeTargetNote = when {
                        demoVocalSimEnabled && debouncedGuideNote.isNotBlank() -> debouncedGuideNote
                        demoVocalSimEnabled && vocalPitchHz > 65f && vocalNote != "-" -> vocalNote
                        demoVocalSimEnabled -> fallbackTargetNote
                        else -> "-"
                    }
                    val displayVocalRms = if (demoVocalSimEnabled) {
                        if (vocalRms > 10f) vocalRms else 64f
                    } else {
                        0f
                    }
                    val keyShiftSuffix = when {
                        keyShift > 0 -> " (+${keyShift} Key)"
                        keyShift < 0 -> " (${keyShift} Key)"
                        else -> ""
                    }
                    val stableMicNote = debouncedMicNote.ifBlank {
                        if (isSingingActive && realMicNote.isNotBlank() && realMicNote != "-") realMicNote else ""
                    }
                    val formattedMicNote = formatEnglishPitchNote(stableMicNote)
                    val formattedGuideNote = formatEnglishPitchNote(activeTargetNote)
                    val displaySingingPercent = maxOf(8, heldMicLevelPercent, realMicRms.roundToInt()).coerceAtMost(100)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                isSingingActive && formattedMicNote.isNotEmpty() ->
                                    "내 목소리 음정: $formattedMicNote$keyShiftSuffix"
                                isSingingActive ->
                                    "내 목소리 음정 인식 중$keyShiftSuffix"
                                demoVocalSimEnabled && formattedGuideNote.isNotEmpty() ->
                                    "가이드 음정: $formattedGuideNote$keyShiftSuffix"
                                else ->
                                    "음정 대기 중$keyShiftSuffix"
                            },
                            color = when {
                                isSingingActive -> PleosPitchDeepBlue
                                demoVocalSimEnabled -> PleosBlue
                                else -> PleosTextDark
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    isSingingActive ->
                                        "🎤 보컬 입력 중 (성량 ${displaySingingPercent}%)"
                                    demoVocalSimEnabled ->
                                        "🎧 보컬 가이드 재생 중 (성량 ${displayVocalRms.roundToInt().coerceIn(10, 100)}%)"
                                    else ->
                                        "🎤 마이크 준비 완료 · 노래를 불러보세요"
                                },
                                color = when {
                                    isSingingActive -> PleosVocalCyanBlue
                                    demoVocalSimEnabled -> PleosBlue
                                    else -> PleosTextMuted
                                },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (micConnected) {
                                val modeTag = formatConsumerTransportLabel(micTransportMode)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = PleosVocalCyanBlue.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, PleosVocalCyanBlue.copy(alpha = 0.55f))
                                ) {
                                    Text(
                                        text = "🎤 $modeTag · ⚡ ${micTotalLatencyMs}ms",
                                        color = PleosVocalCyanBlue,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
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
                        val idleCenterY = h * 0.5f
                        val minHz = 220f
                        val maxHz = 520f
                        val count = 36
                        val slotW = w / count
                        val barW = (slotW - 6f).coerceAtLeast(6.5f)
                        val targetNorm = ((activeTargetHz.coerceIn(minHz, maxHz) - minHz) / (maxHz - minHz)).coerceIn(0.16f, 0.84f)
                        val targetY = h * (1f - targetNorm)

                        for (i in 0 until count) {
                            val hz = pitchHistory.getOrElse(i) { 0f }
                            val slotRms = rmsHistory.getOrElse(i) { 0f }
                            val slotStartX = i * slotW
                            val slotEndX = (i + 1) * slotW
                            val barX = slotStartX + (slotW - barW) * 0.5f

                            if (slotRms >= 8f && hz > 0f) {
                                val slotTargetNorm = (((if (activeTargetHz > 65f) activeTargetHz else hz).coerceIn(minHz, maxHz) - minHz) / (maxHz - minHz)).coerceIn(0.16f, 0.84f)
                                val slotTargetY = if (activeTargetHz > 65f) targetY else h * (1f - slotTargetNorm)
                                drawRect(
                                    color = PleosBlue.copy(alpha = 0.08f),
                                    topLeft = Offset(slotStartX, (slotTargetY - 12.dp.toPx()).coerceAtLeast(0f)),
                                    size = Size(slotW, 24.dp.toPx())
                                )
                                drawLine(
                                    color = PleosBlue.copy(alpha = 0.32f),
                                    start = Offset(slotStartX, slotTargetY),
                                    end = Offset(slotEndX, slotTargetY),
                                    strokeWidth = 2.dp.toPx()
                                )

                                val effectiveHz = hz.coerceIn(minHz, maxHz)
                                val norm = ((effectiveHz - minHz) / (maxHz - minHz)).coerceIn(0.14f, 0.86f)
                                val y = h * (1f - norm)
                                val rmsRatio = ((slotRms.coerceIn(15f, 95f) - 15f) / 80f).coerceIn(0f, 1f)
                                val barH = (10.dp.toPx() + rmsRatio * 34.dp.toPx()).coerceIn(10.dp.toPx(), 44.dp.toPx())
                                val refTargetHz = if (activeTargetHz > 65f) activeTargetHz else effectiveHz
                                val matched = abs(effectiveHz - refTargetHz) < 48f
                                val barColor = if (i % 2 == 0 || matched) PleosBlue else Color(0xFF0EA5E9)

                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(
                                        barX,
                                        (y - barH * 0.5f).coerceIn(2f, (h - barH - 2f).coerceAtLeast(2f))
                                    ),
                                    size = Size(barW, barH),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                )
                            } else {
                                drawLine(
                                    color = PleosBlue.copy(alpha = 0.18f),
                                    start = Offset(slotStartX + 2.dp.toPx(), idleCenterY),
                                    end = Offset(slotEndX - 2.dp.toPx(), idleCenterY),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }
                    }
                }
            }

            // 3. 하단 컨트롤 버튼 바: 좌측 6개 + 우측 효과음 3개 총 9개 버튼 한 줄 무손실 반응형 배치
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val btnHorizPad = when {
                    maxWidth >= 900.dp -> 12.dp
                    maxWidth >= 780.dp -> 10.dp
                    else -> 8.dp
                }
                val btnGap = if (maxWidth >= 820.dp) 6.dp else 5.dp
                val btnFontSize = when {
                    maxWidth >= 900.dp -> 13.5.sp
                    maxWidth >= 780.dp -> 12.5.sp
                    else -> 11.5.sp
                }
                val btnIconSize = when {
                    maxWidth >= 900.dp -> 17.dp
                    maxWidth >= 780.dp -> 15.dp
                    else -> 14.dp
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(btnGap)) {
                        PleosStageControlButton(
                            text = if (isPlaying) "일시정지" else "무대 시작",
                            icon = if (isPlaying) PleosMaterialIcons.Pause else PleosMaterialIcons.Play,
                            primary = true,
                            horizontalPadding = btnHorizPad,
                            fontSize = btnFontSize,
                            iconSize = btnIconSize,
                            onClick = onTogglePlay
                        )
                        PleosStageControlButton(
                            text = "이전 곡",
                            icon = PleosMaterialIcons.Play,
                            primary = false,
                            horizontalPadding = btnHorizPad,
                            fontSize = btnFontSize,
                            iconSize = btnIconSize,
                            onClick = onPrevTrack
                        )
                        PleosStageControlButton(
                            text = "다음 곡",
                            icon = PleosMaterialIcons.Play,
                            primary = false,
                            horizontalPadding = btnHorizPad,
                            fontSize = btnFontSize,
                            iconSize = btnIconSize,
                            onClick = onNextTrack
                        )
                        PleosStageControlButton(
                            text = "보컬 가이드",
                            icon = PleosMaterialIcons.Headphones,
                            primary = demoVocalSimEnabled,
                            horizontalPadding = btnHorizPad,
                            fontSize = btnFontSize,
                            iconSize = btnIconSize,
                            onClick = onToggleDemoVocal
                        )
                        PleosScoreModeControlButton(
                            enabled = isScoreModeEnabled,
                            horizontalPadding = btnHorizPad,
                            fontSize = btnFontSize,
                            onClick = onToggleScoreMode
                        )
                        PleosStageControlButton(
                            text = "마이크 연결",
                            icon = PleosMaterialIcons.Mic,
                            primary = false,
                            horizontalPadding = btnHorizPad,
                            fontSize = btnFontSize,
                            iconSize = btnIconSize,
                            onClick = onOpenPairing
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(btnGap)) {
                        PleosStageControlButton(
                            text = "박수",
                            icon = PleosMaterialIcons.Celebration,
                            primary = false,
                            horizontalPadding = btnHorizPad,
                            fontSize = btnFontSize,
                            iconSize = btnIconSize,
                            onClick = { onTriggerSfx("APPLAUSE") }
                        )
                        PleosStageControlButton(
                            text = "환호",
                            icon = PleosMaterialIcons.CheerSparkle,
                            primary = false,
                            horizontalPadding = btnHorizPad,
                            fontSize = btnFontSize,
                            iconSize = btnIconSize,
                            onClick = { onTriggerSfx("CHEER") }
                        )
                        PleosStageControlButton(
                            text = "탬버린",
                            icon = PleosMaterialIcons.Tambourine,
                            primary = false,
                            horizontalPadding = btnHorizPad,
                            fontSize = btnFontSize,
                            iconSize = btnIconSize,
                            onClick = { onTriggerSfx("TAMBOURINE") }
                        )
                    }
                }
            }
        }
    }

    val playlistSection: @Composable (Modifier) -> Unit = { playlistModifier ->
        // ====================================================================
        // '통합 플레이리스트 & 선곡' 패널
        // ====================================================================
        Surface(
            modifier = playlistModifier,
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
                val displayedList = remember(playlist.toList(), isDevMode, currentTrack.id) {
                    if (BuildConfig.DEBUG && isDevMode) {
                        playlist.toList()
                    } else {
                        playlist.filterNot { it.isDevSample }
                    }
                }

                // 1. 패널 상단 헤더 ("플레이리스트 & 선곡") + 총 곡수 배지
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "플레이리스트 & 선곡",
                        color = PleosTextDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = PleosBlue.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "총 ${displayedList.size}곡",
                            color = PleosBlue,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // 2. 모던 캡슐형 검색/링크 추가 바 (주행 중 UX_RESTRICTIONS_NO_KEYBOARD 안전 인터락 연동)
                val notifyKeyboardRestricted = {
                    focusManager.clearFocus()
                    showDrivingKeyboardBanner = true
                    Toast.makeText(
                        context,
                        "주행 중에는 안전을 위해 키보드 입력이 제한됩니다. Gleo 음성 명령이나 동승자 스마트폰 앱으로 선곡해 주세요.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PleosCapsuleSearchField(
                        value = addTrackInput,
                        onValueChange = onAddTrackInputChange,
                        placeholder = if (isKeyboardRestricted) {
                            "🔒 주행 중 키보드 입력 제한 (Gleo 음성 / 스마트폰 선곡)"
                        } else {
                            "유튜브 링크(URL) 또는 곡명 검색어 입력"
                        },
                        isRestricted = isKeyboardRestricted,
                        onRestrictedClick = notifyKeyboardRestricted,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )

                    Surface(
                        modifier = Modifier
                            .height(48.dp)
                            .clickable {
                                if (isKeyboardRestricted) {
                                    notifyKeyboardRestricted()
                                } else if (addTrackInput.isNotBlank()) {
                                    onAddTrack(addTrackInput)
                                }
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isKeyboardRestricted) PleosAmber else PleosBlue,
                        shadowElevation = 0.dp
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isKeyboardRestricted) "🔒 제한" else "+ 추가",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                if (isKeyboardRestricted || showDrivingKeyboardBanner) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = PleosAmber.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, PleosAmber.copy(alpha = 0.65f)),
                        shadowElevation = 0.dp
                    ) {
                        Text(
                            text = "주행 중에는 안전을 위해 키보드 입력이 제한됩니다. Gleo 음성 명령이나 동승자 스마트폰 앱으로 선곡해 주세요.",
                            color = PleosTextDark,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                // 3. 우측 상단 실시간 미니 믹서 (반주 / 마이크 / 가이드 / 에코 볼륨 + 템포 배속 / 싱크 조절 + 초기화)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "음량 및 빠르기 조절",
                            color = PleosTextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Surface(
                            modifier = Modifier.clickable { onResetAudioMixer() },
                            shape = RoundedCornerShape(8.dp),
                            color = PleosPanelWhite,
                            border = BorderStroke(1.dp, PleosBorderSubtle),
                            shadowElevation = 0.dp
                        ) {
                            Text(
                                text = "초기화",
                                color = PleosBlue,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PleosCompactMiniStepper(
                            modifier = Modifier.weight(1f),
                            label = "반주",
                            valueText = "${backingVolume}%",
                            onMinus = { onBackingVolumeChange(backingVolume - 5) },
                            onPlus = { onBackingVolumeChange(backingVolume + 5) },
                            onReset = { onBackingVolumeChange(85) }
                        )
                        PleosCompactMiniStepper(
                            modifier = Modifier.weight(1f),
                            label = "마이크",
                            valueText = "${micVolume}%",
                            onMinus = { onMicVolumeChange(micVolume - 5) },
                            onPlus = { onMicVolumeChange(micVolume + 5) },
                            onReset = { onMicVolumeChange(85) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PleosCompactMiniStepper(
                            modifier = Modifier.weight(1f),
                            label = "가이드",
                            valueText = "${guideVolume}%",
                            onMinus = { onGuideVolumeChange(guideVolume - 5) },
                            onPlus = { onGuideVolumeChange(guideVolume + 5) },
                            onReset = { onGuideVolumeChange(75) }
                        )
                        PleosCompactMiniStepper(
                            modifier = Modifier.weight(1f),
                            label = "에코",
                            valueText = "${echoLevel}%",
                            onMinus = { onEchoChange(echoLevel - 5) },
                            onPlus = { onEchoChange(echoLevel + 5) },
                            onReset = { onEchoChange(80) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PleosCompactMiniStepper(
                            modifier = Modifier.weight(1f),
                            label = "빠르기",
                            valueText = formatConsumerPlaybackRate(mrPlaybackRate),
                            onMinus = { onPlaybackRateChange(mrPlaybackRate - 0.10f) },
                            onPlus = { onPlaybackRateChange(mrPlaybackRate + 0.10f) },
                            onReset = { onPlaybackRateChange(1.00f) }
                        )
                        PleosCompactMiniStepper(
                            modifier = Modifier.weight(1f),
                            label = "싱크",
                            valueText = if (syncOffsetMs > 0) "+${syncOffsetMs}ms" else "${syncOffsetMs}ms",
                            onMinus = { onSyncOffsetChange(syncOffsetMs - 100) },
                            onPlus = { onSyncOffsetChange(syncOffsetMs + 100) },
                            onReset = { onSyncOffsetChange(0) }
                        )
                    }
                }

                // 4. 통합 플레이리스트 스크롤 목록 (유튜브 곡 전용)

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
                                                if (BuildConfig.DEBUG && showDevModeSwitch && isDevMode && track.isDevSample) {
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

    if (isCompactPortrait) {
        // Portrait 분할 창 (maxWidth < 1000.dp): 상단 무대/플레이리스트 탭 전환으로 UI 잘림 원천 차단
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PleosTrackBg)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = listOf(
                    0 to "🎤 라이브 스테이지 무대",
                    1 to "🎵 플레이리스트 & 선곡 (${playlist.size}곡)"
                )
                tabs.forEach { (tabIdx, tabLabel) ->
                    val selected = portraitLiveSubTab == tabIdx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) PleosBlue else Color.Transparent)
                            .clickable { onSelectPortraitSubTab(tabIdx) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabLabel,
                            color = if (selected) Color.White else PleosTextDark,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (portraitLiveSubTab == 0) {
                stageSection(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                playlistSection(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    } else {
        // Landscape 확장 창 (maxWidth >= 1000.dp): 2단 분할 레이아웃 (좌측/중앙 무대 weight(1f) + 우측 플레이리스트 360.dp 최적화)
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            stageSection(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            playlistSection(
                Modifier
                    .width(360.dp)
                    .fillMaxHeight()
            )
        }
    }
}

/**
 * 모던 캡슐형 검색/링크 입력 바 (투박한 OutlinedTextField 테두리 제거, #F1F5F9 배경, RoundedCornerShape(16.dp), 좌측 돋보기 아이콘 내장)
 * 주행 안전 규제(isRestricted = true) 시 소프트 키보드를 띄우지 않고 안내 토스트/배너를 호출합니다.
 */
@Composable
private fun PleosCapsuleSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isRestricted: Boolean = false,
    onRestrictedClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = if (isRestricted) {
            modifier.clickable { onRestrictedClick() }
        } else {
            modifier
        },
        shape = RoundedCornerShape(16.dp),
        color = if (isRestricted) PleosAmber.copy(alpha = 0.12f) else Color(0xFFF1F5F9),
        border = if (isRestricted) BorderStroke(1.dp, PleosAmber.copy(alpha = 0.55f)) else null,
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
                text = if (isRestricted) "🔒" else "🔍",
                fontSize = 14.sp,
                maxLines = 1,
                softWrap = false
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isRestricted) {
                    Text(
                        text = placeholder,
                        color = PleosTextDark,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
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
}

/**
 * 우측 상단 DEV / PROD 모드 전환 스위치 (중복 [DEV] 배지 제거, 단일 토글 유지)
 */
@Composable
private fun PleosDevProdHeaderBadge(
    isDevMode: Boolean,
    onToggleDevMode: () -> Unit
) {
    if (!BuildConfig.DEBUG) return
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
    onPlus: () -> Unit,
    onReset: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(10.dp),
        color = PleosPanelWhite,
        border = BorderStroke(1.dp, PleosBorderSubtle),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = if (onReset != null) {
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onReset() }
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                } else {
                    Modifier
                },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = PleosTextDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = valueText,
                    color = PleosBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Surface(
                    modifier = Modifier
                        .size(25.dp)
                        .clickable { onMinus() },
                    shape = CircleShape,
                    color = PleosTileGray,
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "−",
                            color = PleosTextDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Surface(
                    modifier = Modifier
                        .size(25.dp)
                        .clickable { onPlus() },
                    shape = CircleShape,
                    color = PleosTileGray,
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "+",
                            color = PleosTextDark,
                            fontSize = 14.sp,
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
 * 하단 컨트롤 버튼 바: 가로 패딩 10~12.dp, maxLines=1, softWrap=false 최적화
 */
@Composable
private fun PleosStageControlButton(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    horizontalPadding: Dp = 11.dp,
    fontSize: TextUnit = 13.5.sp,
    iconSize: Dp = 16.dp,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(50.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(13.dp),
        color = if (primary) Color(0xFF2563EB) else PleosTileGray,
        border = BorderStroke(1.dp, if (primary) Color(0xFF2563EB) else PleosBorderSubtle),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = if (primary) Color.White else PleosTextDark,
                modifier = Modifier.size(iconSize)
            )
            Text(
                text = text,
                color = if (primary) Color.White else PleosTextDark,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * 하단 컨트롤 바용 [🏆 점수 측정 ON / OFF] 토글 버튼
 * ON: 골드/블루 강조 배지 | OFF: 슬레이트 그레이 배지
 */
@Composable
private fun PleosScoreModeControlButton(
    enabled: Boolean,
    horizontalPadding: Dp = 11.dp,
    fontSize: TextUnit = 13.5.sp,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(50.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(13.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.5.dp,
            color = if (enabled) Color(0xFFFBBF24) else Color(0xFF94A3B8)
        ),
        shadowElevation = if (enabled) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .background(
                    if (enabled) {
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF1D4ED8), Color(0xFF2563EB), Color(0xFFD97706))
                        )
                    } else {
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF475569), Color(0xFF64748B))
                        )
                    }
                )
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "🏆",
                fontSize = fontSize,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = if (enabled) "점수 측정 ON" else "점수 측정 OFF",
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * 우측 플레이리스트 상단 바용 컴팩트 [🏆 점수 측정 ON / OFF] 토글 배지
 */
@Composable
private fun PleosPlaylistScoreToggleBadge(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) Color(0xFFFBBF24) else Color(0xFF94A3B8)
        ),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (enabled) {
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF1D4ED8), Color(0xFFD97706))
                        )
                    } else {
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF64748B), Color(0xFF475569))
                        )
                    }
                )
                .padding(horizontal = 9.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (enabled) "🏆 점수 측정 ON" else "🏆 점수 측정 OFF",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * 곡 종료 시 5초간 노출되는 다이내믹 노래방 점수 발표 오버레이 (차량 중앙 무대 화면)
 * - 0.0s ~ 1.2s: 75점 -> 최종 점수 롤링 카운트업 + 스프링 스케일 바운스
 * - 1.2s ~ 5.0s: 점수 확정 + 등급 배지 + 축하 코멘트 + 화려한 파티클 + 세부 게이지 3종
 * - 5.0s 도달 시 자동 페이드아웃 (터치 또는 [닫기] 클릭 시 즉시 닫힘)
 */
@Composable
private fun PleosKaraokeScoreOverlay(
    result: KaraokeScoreResult,
    onDismiss: () -> Unit
) {
    var targetScore by remember(result.resultId) { mutableIntStateOf(75) }
    var isLockedIn by remember(result.resultId) { mutableStateOf(false) }
    var elapsedMs by remember(result.resultId) { mutableIntStateOf(0) }

    LaunchedEffect(result.resultId) {
        targetScore = 75
        isLockedIn = false
        elapsedMs = 0
        delay(40L)
        if (result.score <= 80) {
            // 격려 점수(75~79점)일 때도 두구두구 롤링감을 주기 위해 잠시 상승 후 안착
            targetScore = 88
            delay(520L)
        }
        targetScore = result.score

        val startTime = SystemClock.elapsedRealtime()
        while (true) {
            val nowElapsed = (SystemClock.elapsedRealtime() - startTime).toInt().coerceAtMost(5000)
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
        label = "carKaraokeRollingScore"
    )

    val scoreScale by animateFloatAsState(
        targetValue = when {
            elapsedMs > 4680 -> 0.94f
            isLockedIn -> 1.08f
            else -> 0.92f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "carKaraokeScoreBounce"
    )

    val overlayAlpha by animateFloatAsState(
        targetValue = if (elapsedMs >= 4680) 0f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "carKaraokeOverlayFadeOut"
    )

    val detailGaugeReveal by animateFloatAsState(
        targetValue = if (isLockedIn) 1f else 0.18f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "carKaraokeGaugeReveal"
    )

    val particleTransition = rememberInfiniteTransition(label = "carScoreParticles")
    val particlePhase by particleTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "carParticlePhase"
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
    val remainingSecLabel = "%.1f".format(((5000 - elapsedMs).coerceAtLeast(0)) / 1000f)

    Box(
        modifier = Modifier
            .zIndex(25f)
            .fillMaxSize()
            .alpha(overlayAlpha)
            .background(Color(0xEB090E1A))
            .clickable { onDismiss() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // 축하 스파클 & 파티클 이펙트 캔버스
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.5f
            val cy = h * 0.44f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentPrimary.copy(alpha = if (isLockedIn) 0.30f else 0.14f),
                        accentSecondary.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = w * 0.42f
                ),
                radius = w * 0.42f,
                center = Offset(cx, cy)
            )

            val particleCount = if (isLockedIn) 26 else 10
            for (i in 0 until particleCount) {
                val angle = (i * (2.0 * PI / particleCount) + particlePhase * 0.65).toFloat()
                val distRatio = if (isLockedIn) {
                    0.18f + 0.28f * ((i % 5) / 4f) + 0.04f * sin(particlePhase + i)
                } else {
                    0.14f + 0.10f * ((i % 3) / 2f)
                }
                val px = cx + cos(angle) * (w * distRatio)
                val py = cy + sin(angle) * (h * (distRatio * 0.85f))
                val pRadius = (if (i % 3 == 0) 5.5.dp else 3.2.dp).toPx()
                val pColor = if (i % 2 == 0) accentPrimary else accentSecondary
                drawCircle(
                    color = pColor.copy(alpha = if (isLockedIn) 0.78f else 0.38f),
                    radius = pRadius,
                    center = Offset(px, py)
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 상단 바: 등급 배지 + [닫기] 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accentPrimary.copy(alpha = 0.20f),
                    border = BorderStroke(1.2.dp, accentPrimary.copy(alpha = 0.75f))
                ) {
                    Text(
                        text = if (isLockedIn) "🏆 ${result.badge}" else "🥁 두구두구 점수 집계 중...",
                        color = accentSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (result.trackTitle.isNotBlank()) {
                        Text(
                            text = result.trackTitle,
                            color = Color(0xFF94A3B8),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        modifier = Modifier.clickable { onDismiss() },
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "✕ 닫기 (${remainingSecLabel}s)",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // 중앙: 대형 롤링 숫자 + 축하 멘트 + 세부 게이지 3종
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
                            fontFamily = PretendardFontFamily,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFFFFFBEB), accentSecondary, accentPrimary)
                            ),
                            fontSize = 78.sp,
                            fontWeight = FontWeight.Black,
                            shadow = Shadow(
                                color = accentPrimary.copy(alpha = 0.65f),
                                offset = Offset(0f, 4f),
                                blurRadius = 22f
                            )
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "점",
                        color = accentSecondary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Text(
                    text = if (isLockedIn) result.title else "보컬 성량 · 음정 에너지 분석 완료!",
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = result.comment,
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 세부 바 3종: 음정 정확도 / 성량 파워 / 무대 매너
                Row(
                    modifier = Modifier.fillMaxWidth(0.88f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PleosScoreDetailGaugeCard(
                        modifier = Modifier.weight(1f),
                        label = "음정 정확도",
                        value = result.pitchAccuracy,
                        revealFraction = detailGaugeReveal,
                        barColor = Color(0xFF38BDF8)
                    )
                    PleosScoreDetailGaugeCard(
                        modifier = Modifier.weight(1f),
                        label = "성량 파워",
                        value = result.vocalPower,
                        revealFraction = detailGaugeReveal,
                        barColor = Color(0xFFFBBF24)
                    )
                    PleosScoreDetailGaugeCard(
                        modifier = Modifier.weight(1f),
                        label = "무대 매너",
                        value = result.stageManner,
                        revealFraction = detailGaugeReveal,
                        barColor = Color(0xFFA855F7)
                    )
                }
            }

            // 하단: 5초 카운트다운 진행 바 & 다음 곡 안내
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (result.willAdvanceNext) {
                        "화면을 터치하면 즉시 닫히며, 잠시 후 다음 곡이 자동으로 시작됩니다."
                    } else {
                        "화면을 터치하거나 [닫기]를 누르면 무대 화면으로 돌아갑니다."
                    },
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.14f))
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

@Composable
private fun PleosScoreDetailGaugeCard(
    modifier: Modifier = Modifier,
    label: String,
    value: Int,
    revealFraction: Float,
    barColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = Color(0xFFE2E8F0),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "${value}%",
                    color = barColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
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
}

// ============================================================================
// 3B. PANE 2: PLEOS QUICK SETTINGS 4D HARDWARE TILE GRID (Official Pleos SDK v2.3.2)
// ============================================================================
@Composable
private fun PleosQuickSettings4DPane(
    twinState: VehicleDigitalTwinState,
    echoLevel: Int,
    mediaVolume: Int,
    isDevMode: Boolean,
    showDevModeSwitch: Boolean = true,
    isCompactPortrait: Boolean = false,
    onToggleDevMode: () -> Unit,
    onToggleWindows: () -> Unit,
    onToggleSmartGlass: () -> Unit = {},
    onToggleAirCleaner: () -> Unit = {},
    onToggleAmbientLight: () -> Unit = {},
    onSelectLightingMode: (LightingControlMode) -> Unit = {},
    onToggleEcoStage: () -> Unit = {},
    onTriggerGreenroom: () -> Unit,
    onCycleSeatVentilation: () -> Unit = {},
    onToggleSeatOccupancy: (Int) -> Unit = {},
    onAdjustHvacTemp: (Float) -> Unit = {},
    onAdjustHvacFan: (Int) -> Unit = {},
    onAmbientBrightnessChange: (Int) -> Unit = {},
    onSmartGlassLevelChange: (Int) -> Unit = {},
    onEchoChange: (Int) -> Unit,
    onVolumeChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "차량 노래방 공간 연출",
                    color = PleosTextDark,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "창문, 조명, 에어컨을 노래방 분위기에 맞춰 원터치로 조절합니다",
                    color = PleosTextMuted,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (showDevModeSwitch) {
                Spacer(modifier = Modifier.width(12.dp))
                PleosDevProdHeaderBadge(
                    isDevMode = isDevMode,
                    onToggleDevMode = onToggleDevMode
                )
            }
        }

        // 1. [배터리 절전 모드] 알림 배너 (배터리 20% 이하 자동 발동 또는 절전 모드 활성 시 강조)
        if (twinState.evBatterySocPercent <= 20 || twinState.ecoStageModeActive) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleEcoStage() },
                shape = RoundedCornerShape(16.dp),
                color = PleosAmber.copy(alpha = 0.14f),
                border = BorderStroke(1.5.dp, PleosAmber)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "⚡ 배터리 절전 모드 작동 중 (현재 배터리 잔량 ${twinState.evBatterySocPercent}%)",
                            color = PleosTextDark,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "배터리 보호를 위해 실내 무드 조명 밝기(30%)와 에어컨 바람 세기(1단)를 절전 모드로 자동 조절합니다",
                            color = PleosTextMuted,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PleosAmber
                    ) {
                        Text(
                            text = "절전 해제",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 2. 좌석 탑승객 자동 감지 기반 무대 모드 전환 & 승객 좌석 통풍 시트 카드
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = PleosTileGray,
            border = BorderStroke(1.dp, PleosBorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = PleosBlue
                        ) {
                            Text(
                                text = "좌석 맞춤 공조",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "탑승객 자동 감지 (현재 ${twinState.occupiedSeatCount}명 탑승 중)",
                                color = PleosTextDark,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "승객이 앉아 있는 좌석에만 시원한 통풍 시트를 자동으로 켜줍니다",
                                color = PleosTextMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.clickable { onCycleSeatVentilation() },
                        shape = RoundedCornerShape(10.dp),
                        color = PleosPanelWhite,
                        border = BorderStroke(1.dp, PleosBlue)
                    ) {
                        Text(
                            text = "탑승석 통풍: ${twinState.seatVentilationLevel}단 변경",
                            color = PleosBlue,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val seatInfos = listOf(
                        Triple(0, "운전석 (1열 좌)", twinState.seatOccupiedFL to twinState.seatVentilationFL),
                        Triple(1, "동승석 (1열 우)", twinState.seatOccupiedFR to twinState.seatVentilationFR),
                        Triple(2, "2열 좌측석", twinState.seatOccupiedRL to twinState.seatVentilationRL),
                        Triple(3, "2열 우측석", twinState.seatOccupiedRR to twinState.seatVentilationRR)
                    )
                    seatInfos.forEach { (idx, seatTitle, occAndVent) ->
                        val (occupied, ventLvl) = occAndVent
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onToggleSeatOccupancy(idx) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (occupied) PleosPanelWhite else Color(0xFFE4E6EC),
                            border = BorderStroke(1.2.dp, if (occupied) PleosBlue else PleosBorderSubtle)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = seatTitle,
                                    color = PleosTextDark,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = if (occupied) "● 승객 착석 · 통풍 ${ventLvl}단" else "○ 빈 좌석 (통풍 꺼짐)",
                                    color = if (occupied) PleosBlue else PleosTextMuted,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 6대 차량 노래방 공간 연출 Quick Tile 그리드 (3열 x 2행 균형 배치)
        val windowSubtitle = if (twinState.allWindowsClosed) {
            "4좌석 창문 모두 닫힘"
        } else {
            "창문 열림 (앞좌석 ${twinState.windowOpenPercentFL}% · 뒷좌석 ${twinState.windowOpenPercentRL}%)"
        }
        val smartGlassSubtitle = if (twinState.smartGlassBlackoutEnabled) {
            "암막 켜짐 (${twinState.smartGlassLevel}단계) · 가리개 닫힘"
        } else {
            "암막 꺼짐 (투명) · 가리개 열림"
        }
        val airCleanerSubtitle = if (twinState.hvacAirCleanerEnabled) {
            "실내 공기청정 작동 중"
        } else {
            "창문 닫힘 시 쾌적 공기 관리"
        }
        val hvacSubtitle = "희망 ${twinState.hvacTempCelsius}°C (실내 ${twinState.hvacCurrentIndoorTempCelsius}°C) · 바람 ${twinState.hvacFanLevel}단"
        val lightSubtitle = when (twinState.lightingControlMode) {
            LightingControlMode.AUTO_MUSIC -> "자동조정(음악 연동) · ${twinState.ambientBrightness}%"
            LightingControlMode.FIXED -> "밝기 고정 · ${twinState.ambientBrightness}%"
            LightingControlMode.OFF -> "실내 무드 조명 꺼짐 (0%)"
        }
        val batterySubtitle = if (twinState.ecoStageModeActive) {
            "조명 30% · 바람 1단 절전 작동 중"
        } else {
            "배터리 ${twinState.evBatterySocPercent}% · 일반 모드"
        }

        val tileHorizontalSpacing = if (isCompactPortrait) 12.dp else 14.dp
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 1행 (3열): 전 좌석 창문 닫기 | 암막 글래스 모드 | 실내 공기청정
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tileHorizontalSpacing)
            ) {
                PleosQuickTile(
                    modifier = Modifier.weight(1f),
                    title = "전 좌석 창문 닫기",
                    subtitle = windowSubtitle,
                    active = twinState.allWindowsClosed,
                    icon = PleosMaterialIcons.Car,
                    onClick = onToggleWindows
                )
                PleosQuickTile(
                    modifier = Modifier.weight(1f),
                    title = "암막 글래스 모드",
                    subtitle = smartGlassSubtitle,
                    active = twinState.smartGlassBlackoutEnabled,
                    icon = PleosMaterialIcons.Car,
                    onClick = onToggleSmartGlass
                )
                PleosQuickTile(
                    modifier = Modifier.weight(1f),
                    title = "실내 공기청정",
                    subtitle = airCleanerSubtitle,
                    active = twinState.hvacAirCleanerEnabled,
                    icon = PleosMaterialIcons.CheerSparkle,
                    onClick = onToggleAirCleaner
                )
            }
            // 2행 (3열): 쾌적 냉방 모드 | 공연 조명 (무드등) | 배터리 절전 모드
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tileHorizontalSpacing)
            ) {
                PleosQuickTile(
                    modifier = Modifier.weight(1f),
                    title = "쾌적 냉방 모드",
                    subtitle = hvacSubtitle,
                    active = twinState.greenroomCareActive,
                    icon = PleosMaterialIcons.CheerSparkle,
                    onClick = onTriggerGreenroom
                )
                PleosLightingModeTile(
                    modifier = Modifier.weight(1f),
                    subtitle = lightSubtitle,
                    currentMode = twinState.lightingControlMode,
                    onCycleMode = onToggleAmbientLight,
                    onSelectMode = onSelectLightingMode
                )
                PleosQuickTile(
                    modifier = Modifier.weight(1f),
                    title = "배터리 절전 모드",
                    subtitle = batterySubtitle,
                    active = twinState.ecoStageModeActive,
                    icon = PleosMaterialIcons.Car,
                    onClick = onToggleEcoStage
                )
            }
        }

        // 4. 세부 공간 제어 트랙바 (공조 온도 / 공조 풍량 / 조명 밝기 / 암막 농도 / 공간감 / 미디어)
        PleosHorizontalTrackBar(
            label = "공조 온도",
            valueText = "${twinState.hvacTempCelsius}°C",
            progress = ((twinState.hvacTempCelsius - 17f) / 10f).coerceIn(0f, 1f),
            actionLabel = "쾌적 냉방",
            onMinus = { onAdjustHvacTemp(-0.5f) },
            onPlus = { onAdjustHvacTemp(0.5f) },
            onAction = onTriggerGreenroom
        )

        PleosHorizontalTrackBar(
            label = "공조 풍량",
            valueText = "${twinState.hvacFanLevel}단",
            progress = (twinState.hvacFanLevel / 7f).coerceIn(0f, 1f),
            actionLabel = "공기청정",
            onMinus = { onAdjustHvacFan(-1) },
            onPlus = { onAdjustHvacFan(1) },
            onAction = onToggleAirCleaner
        )

        PleosLightingBrightnessControlRow(
            lightingMode = twinState.lightingControlMode,
            brightness = twinState.ambientBrightness,
            onBrightnessChangeToFixed = { targetPct ->
                onAmbientBrightnessChange(targetPct.coerceIn(10, 100))
            },
            onSelectLightingMode = onSelectLightingMode
        )

        PleosHorizontalTrackBar(
            label = "암막 농도",
            valueText = "${twinState.smartGlassLevel}단계 / ${twinState.smartGlassMaxLevel}단계",
            progress = (twinState.smartGlassLevel.toFloat() / twinState.smartGlassMaxLevel.coerceAtLeast(1)).coerceIn(0f, 1f),
            actionLabel = if (twinState.smartGlassBlackoutEnabled) "암막 켜짐" else "투명",
            onMinus = { onSmartGlassLevelChange(twinState.smartGlassLevel - 1) },
            onPlus = { onSmartGlassLevelChange(twinState.smartGlassLevel + 1) },
            onAction = onToggleSmartGlass
        )

        PleosHorizontalTrackBar(
            label = "에코 (공간감)",
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

        // 5. 하단 소비자 친화적 차량 공간 상태 요약 바 (기어 상태는 Pleos Vehicle SDK Driving 도메인에서 Read-Only로 반영)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFF1F5F9),
            border = BorderStroke(1.dp, PleosBorderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val summaryLightText = when (twinState.lightingControlMode) {
                    LightingControlMode.OFF -> "꺼짐(0%)"
                    LightingControlMode.FIXED -> "고정 ${twinState.ambientBrightness}%"
                    LightingControlMode.AUTO_MUSIC -> "자동 ${twinState.ambientBrightness}%"
                }
                Text(
                    text = "현재 상태: 실내 무드 조명($summaryLightText) 및 노래방 공조(${if (twinState.hvacPowerOn) "${twinState.hvacTempCelsius}°C · 바람 ${twinState.hvacFanLevel}단" else "희망 ${twinState.hvacTempCelsius}°C"}) 연동 중",
                    color = PleosTextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (twinState.hvacPowerOn) {
                        "기어 ${twinState.gearPosition.label} · 공조 켜짐 · 실시간 차량 연동 중"
                    } else {
                        "기어 ${twinState.gearPosition.label} · 실시간 차량 연동 중"
                    },
                    color = PleosBlue,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
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
    labelWidth: Dp = 128.dp,
    isInactiveZero: Boolean = progress <= 0.001f,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onAction: () -> Unit
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val displayFillFraction = if (isInactiveZero) 0f else clampedProgress.coerceIn(0.08f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PleosTileGray)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = label,
                color = PleosTextDark,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(labelWidth)
            )

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PleosPanelWhite)
                    .border(1.dp, PleosBorderSubtle, CircleShape)
                    .clickable { onMinus() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "−",
                    color = PleosTextDark,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFCBD5E1))
            ) {
                if (displayFillFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(displayFillFraction)
                            .shadow(1.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xF5FFFFFF),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 0.dp
                ) {
                    Text(
                        text = valueText,
                        color = if (isInactiveZero) Color(0xFF64748B) else PleosTextDark,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PleosPanelWhite)
                    .border(1.dp, PleosBorderSubtle, CircleShape)
                    .clickable { onPlus() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = PleosTextDark,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Box(
            modifier = Modifier
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PleosTileGray)
                .border(1.dp, PleosBorderSubtle, RoundedCornerShape(16.dp))
                .clickable { onAction() }
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = actionLabel,
                color = PleosTextDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun PleosLightingModeTile(
    modifier: Modifier = Modifier,
    subtitle: String,
    currentMode: LightingControlMode,
    onCycleMode: () -> Unit,
    onSelectMode: (LightingControlMode) -> Unit
) {
    val active = currentMode != LightingControlMode.OFF
    Surface(
        modifier = modifier
            .height(126.dp)
            .clickable { onCycleMode() },
        shape = RoundedCornerShape(18.dp),
        color = if (active) PleosTileActiveWhite else PleosTileGray,
        border = BorderStroke(1.5.dp, if (active) PleosBlue else PleosBorderSubtle),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "공연 조명 (무드등)",
                        color = PleosTextDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        color = if (active) PleosBlue else PleosTextMuted,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (active) PleosGreen else Color(0xFFC5C9D2))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val modes = listOf(
                    LightingControlMode.AUTO_MUSIC to "자동조정",
                    LightingControlMode.FIXED to "고정",
                    LightingControlMode.OFF to "꺼짐"
                )
                modes.forEach { (mode, modeText) ->
                    val selected = currentMode == mode
                    val chipBg = when {
                        selected && (mode == LightingControlMode.AUTO_MUSIC || mode == LightingControlMode.FIXED) -> Color(0xFF2563EB)
                        selected && mode == LightingControlMode.OFF -> Color(0xFF475569)
                        else -> Color(0xFFE5E7EB)
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clickable { onSelectMode(mode) },
                        shape = RoundedCornerShape(8.dp),
                        color = chipBg
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = modeText,
                                color = if (selected) Color.White else PleosTextDark,
                                fontSize = 12.sp,
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

@Composable
private fun PleosLightingBrightnessControlRow(
    lightingMode: LightingControlMode,
    brightness: Int,
    onBrightnessChangeToFixed: (Int) -> Unit,
    onSelectLightingMode: (LightingControlMode) -> Unit
) {
    val isOff = lightingMode == LightingControlMode.OFF
    val effectiveBrightness = if (isOff) 0 else brightness.coerceIn(10, 100)
    val fillFraction = if (isOff) 0f else (effectiveBrightness / 100f).coerceIn(0.10f, 1f)
    val valueText = when (lightingMode) {
        LightingControlMode.OFF -> "0% (꺼짐)"
        LightingControlMode.FIXED -> "${effectiveBrightness}% (고정)"
        LightingControlMode.AUTO_MUSIC -> "${effectiveBrightness}% (자동)"
    }
    var sliderWidthPx by remember { mutableFloatStateOf(1f) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PleosTileGray)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "조명 밝기",
                color = PleosTextDark,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(128.dp)
            )

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PleosPanelWhite)
                    .border(1.dp, PleosBorderSubtle, CircleShape)
                    .clickable {
                        val nextPct = if (isOff) 10 else (effectiveBrightness - 10).coerceIn(10, 100)
                        onBrightnessChangeToFixed(nextPct)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "−",
                    color = PleosTextDark,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFCBD5E1))
                    .onSizeChanged { sz ->
                        if (sz.width > 0) sliderWidthPx = sz.width.toFloat()
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val w = sliderWidthPx.coerceAtLeast(1f)
                            val frac = (offset.x / w).coerceIn(0f, 1f)
                            val pct = (10f + frac * 90f).roundToInt().coerceIn(10, 100)
                            onBrightnessChangeToFixed(pct)
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { startOffset ->
                                val w = sliderWidthPx.coerceAtLeast(1f)
                                val frac = (startOffset.x / w).coerceIn(0f, 1f)
                                val pct = (10f + frac * 90f).roundToInt().coerceIn(10, 100)
                                onBrightnessChangeToFixed(pct)
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                val w = sliderWidthPx.coerceAtLeast(1f)
                                val frac = (change.position.x / w).coerceIn(0f, 1f)
                                val pct = (10f + frac * 90f).roundToInt().coerceIn(10, 100)
                                onBrightnessChangeToFixed(pct)
                            }
                        )
                    }
            ) {
                if (fillFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fillFraction)
                            .shadow(1.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xF5FFFFFF),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 0.dp
                ) {
                    Text(
                        text = valueText,
                        color = if (isOff) Color(0xFF64748B) else PleosTextDark,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(PleosPanelWhite)
                    .border(1.dp, PleosBorderSubtle, CircleShape)
                    .clickable {
                        val nextPct = if (isOff) 20 else (effectiveBrightness + 10).coerceIn(10, 100)
                        onBrightnessChangeToFixed(nextPct)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = PleosTextDark,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Row(
            modifier = Modifier
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PleosTileGray)
                .border(1.dp, PleosBorderSubtle, RoundedCornerShape(16.dp))
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val modeButtons = listOf(
                LightingControlMode.AUTO_MUSIC to "자동조정",
                LightingControlMode.FIXED to "고정",
                LightingControlMode.OFF to "꺼짐"
            )
            modeButtons.forEach { (mode, label) ->
                val selected = lightingMode == mode
                val btnBg = when {
                    selected && (mode == LightingControlMode.AUTO_MUSIC || mode == LightingControlMode.FIXED) -> Color(0xFF2563EB)
                    selected && mode == LightingControlMode.OFF -> Color(0xFF475569)
                    else -> Color.Transparent
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(btnBg)
                        .clickable { onSelectLightingMode(mode) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) Color.White else PleosTextDark,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

private fun formatConsumerTransportLabel(transportMode: String): String {
    return when {
        transportMode.equals("DIRECT", ignoreCase = true) -> "초저지연 직결"
        transportMode.equals("WEBRTC_P2P", ignoreCase = true) -> "P2P 무선 연결"
        else -> "클라우드 연결"
    }
}

// ============================================================================
// 3C. PANE 3: SMARTPHONE MIC PAIRING (2-COLUMN BALANCED CARDS + PROD/DEV SEPARATION)
// ============================================================================
@Composable
private fun PleosPairingPane(
    qrBitmap: androidx.compose.ui.graphics.ImageBitmap,
    roomPin: String,
    lanUrl: String,
    emulatorUrl: String,
    micConnected: Boolean,
    micTotalLatencyMs: Int = 0,
    micTransportMode: String = "DIRECT",
    micDeviceName: String,
    connectedMicCount: Int,
    demoVocalSimEnabled: Boolean,
    isDevMode: Boolean,
    showDevModeSwitch: Boolean = true,
    isSetupRestricted: Boolean = false,
    isCompactPortrait: Boolean = false,
    onToggleDevMode: () -> Unit,
    onToggleDemoSim: () -> Unit,
    onDirectConnectPhoneIp: (String) -> Unit = {}
) {
    val pairingContext = LocalContext.current
    var rememberedHotspotIp by remember {
        mutableStateOf(PleosEmbeddedSignalingServer.loadLastSavedHotspotIp(pairingContext))
    }
    var offlineHotspotCode by remember { mutableStateOf(rememberedHotspotIp) }
    LaunchedEffect(micConnected) {
        val latestSaved = PleosEmbeddedSignalingServer.loadLastSavedHotspotIp(pairingContext)
        if (latestSaved.isNotBlank()) {
            rememberedHotspotIp = latestSaved
            if (offlineHotspotCode.isBlank()) {
                offlineHotspotCode = latestSaved
            }
        }
    }
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

    // 좌측 카드 (폭 42%): [📷 QR 코드로 1초 연결]
    val qrCardSection: @Composable (Modifier) -> Unit = { cardModifier ->
        Surface(
            modifier = cardModifier,
            shape = RoundedCornerShape(22.dp),
            color = PleosTileGray,
            border = BorderStroke(1.2.dp, PleosBorderSubtle),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF2563EB).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "FAST SCAN PAIRING",
                            color = Color(0xFF2563EB),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = "📷 QR 코드로 1초 연결",
                        color = PleosTextDark,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .shadow(4.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .background(PleosPanelWhite)
                        .border(1.5.dp, Color(0xFFDBEAFE), RoundedCornerShape(18.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap,
                        contentDescription = "Pairing QR Code",
                        modifier = Modifier.fillMaxSize()
                    )

                    // 주행 안전 규제(UX_RESTRICTIONS_NO_SETUP / 기어 D) 활성 시 QR 스캔 안전 잠금 오버레이 표시 (P 기어 전환 시 즉시 해제)
                    if (isSetupRestricted) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xEB0F172A))
                                .border(1.5.dp, PleosAmber, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "🔒 주행 안전 잠금",
                                    color = PleosAmber,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "주행 중에는 QR 기기 페어링 설정이 제한됩니다.\n정차(P 기어) 시 즉시 해제됩니다.",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isSetupRestricted) {
                            "주행 중 QR 스캔 안전 제한 (P 기어 전환 시 해제)"
                        } else {
                            "스마트폰 기본 카메라 또는 마이크 앱으로 스캔하세요"
                        },
                        color = if (isSetupRestricted) PleosAmber else PleosTextDark,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "동일 Wi-Fi · 스마트폰 핫스팟 · 무선 중계 자동 연결",
                        color = Color(0xFF2563EB),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }

    // 우측 카드 (폭 58%): [🔢 4자리 PIN 번호로 연결]
    val pinCardSection: @Composable (Modifier) -> Unit = { cardModifier ->
        Surface(
            modifier = cardModifier,
            shape = RoundedCornerShape(22.dp),
            color = PleosTileGray,
            border = BorderStroke(1.2.dp, PleosBorderSubtle),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 상단: 카드 헤더 + 대형 4자리 PIN 번호 박스
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔢 4자리 PIN 번호로 연결",
                            color = PleosTextDark,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF2563EB).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "차량 고유 PIN",
                                color = Color(0xFF2563EB),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        roomPin.padEnd(4, '0').take(4).forEach { digit ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(78.dp)
                                    .shadow(3.dp, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(PleosPanelWhite)
                                    .border(2.5.dp, Color(0xFF2563EB), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = digit.toString(),
                                    color = Color(0xFF2563EB),
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 중단: 스마트폰 연결 3단계 비주얼 가이드 카드
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = PleosPanelWhite,
                    border = BorderStroke(1.dp, PleosBorderSubtle),
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "스마트폰 3단계 간편 연결 가이드",
                            color = PleosTextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val steps = listOf(
                                "STEP 1" to "1. 스마트폰 앱 실행",
                                "STEP 2" to "2. [스마트 링크] 탭 이동",
                                "STEP 3" to "3. [원터치 차량 자동 연결] 터치"
                            )
                            steps.forEachIndexed { idx, (stepTag, stepTitle) ->
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, Color(0xFFDBEAFE)),
                                    shadowElevation = 0.dp
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = stepTag,
                                            color = Color(0xFF2563EB),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = stepTitle,
                                            color = PleosTextDark,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (idx < steps.lastIndex) {
                                    Text(
                                        text = "➔",
                                        color = Color(0xFF2563EB),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 하단: 현재 연결 상태 요약 배지
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (micConnected) Color(0xFFECFDF5) else Color(0xFFEFF6FF),
                    border = BorderStroke(
                        1.5.dp,
                        if (micConnected) PleosGreen else Color(0xFF2563EB).copy(alpha = 0.45f)
                    ),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val c = Offset(size.width / 2f, size.height / 2f)
                                val maxR = size.minDimension / 2f
                                drawCircle(
                                    color = (if (micConnected) PleosGreen else Color(0xFF2563EB))
                                        .copy(alpha = (1f - radarPhase) * 0.45f),
                                    radius = maxR * (0.4f + 0.6f * radarPhase),
                                    center = c,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawCircle(
                                    color = if (micConnected) PleosGreen else Color(0xFF2563EB),
                                    radius = 6.5.dp.toPx(),
                                    center = c
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (micConnected) {
                                    "● 스마트폰 마이크 연결됨 · ⚡ ${micTotalLatencyMs}ms"
                                } else {
                                    "○ 마이크 대기 중 (PIN $roomPin)"
                                },
                                color = if (micConnected) PleosGreen else PleosTextDark,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (micConnected) {
                                    "${formatConsumerTransportLabel(micTransportMode)} · $micDeviceName (${connectedMicCount.coerceAtLeast(1)}대) 실시간 연결됨"
                                } else {
                                    "동일 네트워크 자동 연결 또는 PIN $roomPin · QR 스캔으로 즉시 페어링됩니다"
                                },
                                color = PleosTextMuted,
                                fontSize = 12.5.sp,
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
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 상단 타이틀 헤더 + PROD/DEV 토글
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "차량 스마트 링크 (마이크 연결)",
                    color = PleosTextDark,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "카메라 QR 코드 스캔 또는 4자리 PIN 번호로 스마트폰 마이크와 리모컨을 즉시 연결합니다.",
                    color = PleosTextMuted,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (showDevModeSwitch) {
                Spacer(modifier = Modifier.width(12.dp))
                PleosDevProdHeaderBadge(
                    isDevMode = isDevMode,
                    onToggleDevMode = onToggleDevMode
                )
            }
        }

        // [상단 최우선 배치] 스마트폰 IP 직접 연결 & 영구 기억 카드 (PROD / Release 빌드에서도 100% 상시 노출)
        val cleanCarHostIp = remember(lanUrl) {
            lanUrl.removePrefix("ws://")
                .removePrefix("wss://")
                .removePrefix("http://")
                .removePrefix("https://")
                .substringBefore("/")
                .substringBefore(":")
                .takeIf { it.isNotBlank() && it != "127.0.0.1" && it != "localhost" }
                .orEmpty()
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = if (micConnected) Color(0xFFECFDF5) else Color(0xFFEFF6FF),
            border = BorderStroke(
                1.5.dp,
                if (micConnected) PleosGreen else Color(0xFF2563EB).copy(alpha = 0.55f)
            ),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        Text(
                            text = "스마트폰 IP 직접 연결",
                            color = PleosTextDark,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = (if (micConnected) PleosGreen else Color(0xFF2563EB)).copy(alpha = 0.14f),
                            shadowElevation = 0.dp
                        ) {
                            Text(
                                text = when {
                                    micConnected -> "● ${formatConsumerTransportLabel(micTransportMode)} (${micTotalLatencyMs}ms)"
                                    rememberedHotspotIp.isNotBlank() -> "기억된 IP: $rememberedHotspotIp (자동 직결 활성)"
                                    else -> "1회 입력 시 영구 기억 · 다음 실행 0.5초 자동 직결"
                                },
                                color = if (micConnected) PleosGreen else Color(0xFF2563EB),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (BuildConfig.DEBUG && showDevModeSwitch && isDevMode) {
                        Surface(
                            modifier = Modifier.clickable { onToggleDemoSim() },
                            shape = RoundedCornerShape(8.dp),
                            color = if (demoVocalSimEnabled) PleosDevRed else PleosPanelWhite,
                            border = BorderStroke(1.dp, PleosDevRed)
                        ) {
                            Text(
                                text = if (demoVocalSimEnabled) "DEV 보컬 시뮬레이터 ON" else "DEV 보컬 시뮬레이터 켜기",
                                color = if (demoVocalSimEnabled) Color.White else PleosDevRed,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = PleosPanelWhite,
                        border = BorderStroke(1.2.dp, Color(0xFF2563EB).copy(alpha = 0.45f)),
                        shadowElevation = 0.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (offlineHotspotCode.isEmpty()) {
                                Text(
                                    text = "스마트폰 앱 [스마트 링크] 상단의 IP 또는 보조 번호 입력 (예: 192.168.43.1 또는 43.1)",
                                    color = PleosTextMuted,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = offlineHotspotCode,
                                onValueChange = { offlineHotspotCode = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = PleosTextDark,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                cursorBrush = SolidColor(Color(0xFF2563EB)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .height(46.dp)
                            .clickable {
                                val target = offlineHotspotCode.trim()
                                onDirectConnectPhoneIp(target)
                                val savedNow = PleosEmbeddedSignalingServer.loadLastSavedHotspotIp(pairingContext)
                                if (savedNow.isNotBlank()) {
                                    rememberedHotspotIp = savedNow
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2563EB),
                        shadowElevation = 0.dp
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "즉시 연결",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .height(46.dp)
                            .clickable { onDirectConnectPhoneIp("") },
                        shape = RoundedCornerShape(12.dp),
                        color = PleosPanelWhite,
                        border = BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.55f)),
                        shadowElevation = 0.dp
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🔄 자동 탐색",
                                color = Color(0xFF2563EB),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Text(
                    text = if (cleanCarHostIp.isNotBlank()) {
                        "• 스마트폰 핫스팟 게이트웨이 자동 탐색 활성   |   차량 연결 IP: $cleanCarHostIp"
                    } else {
                        "• 스마트폰 핫스팟 연결 시 게이트웨이 IP를 자동 탐색하며, 1회 직접 연결 시 다음 실행부터 0.5초 만에 자동 재접속됩니다."
                    },
                    color = PleosTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 메인 좌우 대칭형 2단 대형 카드 레이아웃 (좌 42% : 우 58%)
        if (isCompactPortrait) {
            qrCardSection(Modifier.fillMaxWidth())
            pinCardSection(Modifier.fillMaxWidth())
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.Top
            ) {
                qrCardSection(
                    Modifier
                        .weight(0.42f)
                        .height(378.dp)
                )
                pinCardSection(
                    Modifier
                        .weight(0.58f)
                        .height(378.dp)
                )
            }
        }
    }
}

private data class SoundPresetSpec(
    val title: String,
    val subtitle: String,
    val backing: Int,
    val mic: Int,
    val guide: Int,
    val echo: Int,
    val rate: Float
)

// ============================================================================
// 3D. PANE 4: SOUND & REVERB SETTINGS (4 PRESET CHIPS + 2 GROUPED CARDS)
// ============================================================================
@Composable
private fun PleosSoundReverbPane(
    backingVolume: Int,
    micVolume: Int = 85,
    guideVolume: Int = 75,
    echoLevel: Int,
    mrPlaybackRate: Float = 1.00f,
    syncOffsetMs: Int = 0,
    keyShift: Int,
    mediaVolume: Int,
    isDevMode: Boolean,
    showDevModeSwitch: Boolean = true,
    onToggleDevMode: () -> Unit,
    onBackingVolumeChange: (Int) -> Unit,
    onMicVolumeChange: (Int) -> Unit = {},
    onGuideVolumeChange: (Int) -> Unit = {},
    onEchoChange: (Int) -> Unit,
    onPlaybackRateChange: (Float) -> Unit = {},
    onSyncOffsetChange: (Int) -> Unit = {},
    onKeyShiftChange: (Int) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onResetAll: () -> Unit = {}
) {
    val presets = remember {
        listOf(
            SoundPresetSpec("🎤 라이브 표준", "균형 잡힌 기본 무대", backing = 85, mic = 85, guide = 75, echo = 80, rate = 1.00f),
            SoundPresetSpec("🏛 콘서트홀", "풍성한 공간 울림", backing = 85, mic = 90, guide = 70, echo = 95, rate = 1.00f),
            SoundPresetSpec("🗣 보컬 선명", "또렷한 목소리 전달", backing = 75, mic = 95, guide = 65, echo = 45, rate = 1.00f),
            SoundPresetSpec("🌙 야간 드라이브", "차분한 볼륨 밸런스", backing = 65, mic = 80, guide = 60, echo = 60, rate = 1.00f)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 상단 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "사운드 설정",
                    color = PleosTextDark,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "원터치 사운드 프리셋과 음량 · 에코 공간감 · 곡 빠르기 · 보컬 싱크를 직관적으로 조절합니다.",
                    color = PleosTextMuted,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .height(40.dp)
                        .clickable { onResetAll() },
                    shape = RoundedCornerShape(12.dp),
                    color = PleosTileGray,
                    border = BorderStroke(1.dp, PleosBorderSubtle),
                    shadowElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "전체 초기화",
                            color = Color(0xFF2563EB),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                if (showDevModeSwitch) {
                    PleosDevProdHeaderBadge(
                        isDevMode = isDevMode,
                        onToggleDevMode = onToggleDevMode
                    )
                }
            }
        }

        // 원터치 사운드 프리셋 칩 4종
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            presets.forEach { preset ->
                val isSelected = backingVolume == preset.backing &&
                    micVolume == preset.mic &&
                    guideVolume == preset.guide &&
                    echoLevel == preset.echo
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            onBackingVolumeChange(preset.backing)
                            onMicVolumeChange(preset.mic)
                            onGuideVolumeChange(preset.guide)
                            onEchoChange(preset.echo)
                            onPlaybackRateChange(preset.rate)
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color(0xFF2563EB) else PleosTileGray,
                    border = BorderStroke(
                        1.5.dp,
                        if (isSelected) Color(0xFF2563EB) else PleosBorderSubtle
                    ),
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = preset.title,
                            color = if (isSelected) Color.White else PleosTextDark,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = preset.subtitle,
                            color = if (isSelected) Color(0xFFDBEAFE) else PleosTextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 그룹 카드 1: [음량 및 공간감 밸런스]
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = PleosPanelWhite,
            border = BorderStroke(1.2.dp, PleosBorderSubtle),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "음량 및 공간감 밸런스",
                        color = PleosTextDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "반주 · 마이크 · 보컬 가이드 · 에코 · 시스템 미디어",
                        color = PleosTextMuted,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                PleosHorizontalTrackBar(
                    label = "반주 음량",
                    valueText = "$backingVolume%",
                    progress = backingVolume / 100f,
                    actionLabel = "초기화",
                    onMinus = { onBackingVolumeChange(backingVolume - 10) },
                    onPlus = { onBackingVolumeChange(backingVolume + 10) },
                    onAction = { onBackingVolumeChange(85) }
                )

                PleosHorizontalTrackBar(
                    label = "마이크 음량",
                    valueText = "$micVolume%",
                    progress = micVolume / 100f,
                    actionLabel = "초기화",
                    onMinus = { onMicVolumeChange(micVolume - 5) },
                    onPlus = { onMicVolumeChange(micVolume + 5) },
                    onAction = { onMicVolumeChange(85) }
                )

                PleosHorizontalTrackBar(
                    label = "보컬 가이드",
                    valueText = "$guideVolume%",
                    progress = guideVolume / 100f,
                    actionLabel = "초기화",
                    onMinus = { onGuideVolumeChange(guideVolume - 5) },
                    onPlus = { onGuideVolumeChange(guideVolume + 5) },
                    onAction = { onGuideVolumeChange(75) }
                )

                PleosHorizontalTrackBar(
                    label = "에코 (공간감)",
                    valueText = "$echoLevel%",
                    progress = echoLevel / 100f,
                    actionLabel = "초기화",
                    onMinus = { onEchoChange(echoLevel - 5) },
                    onPlus = { onEchoChange(echoLevel + 5) },
                    onAction = { onEchoChange(80) }
                )

                PleosHorizontalTrackBar(
                    label = "미디어",
                    valueText = "$mediaVolume / 15",
                    progress = mediaVolume / 15f,
                    actionLabel = "음소거",
                    onMinus = { onVolumeChange(mediaVolume - 1) },
                    onPlus = { onVolumeChange(mediaVolume + 1) },
                    onAction = { onVolumeChange(0) }
                )
            }
        }

        // 그룹 카드 2: [곡 빠르기 · 키 · 싱크 조절]
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = PleosPanelWhite,
            border = BorderStroke(1.2.dp, PleosBorderSubtle),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "곡 빠르기 · 키 · 싱크 조절",
                        color = PleosTextDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "템포 배속 · 반음 키 변경 · 마이크 싱크 미세 보정",
                        color = PleosTextMuted,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                PleosHorizontalTrackBar(
                    label = "곡 빠르기",
                    valueText = formatConsumerPlaybackRate(mrPlaybackRate),
                    progress = ((mrPlaybackRate - 0.75f) / 0.50f).coerceIn(0f, 1f),
                    isInactiveZero = false,
                    actionLabel = "기본 빠르기",
                    onMinus = { onPlaybackRateChange(mrPlaybackRate - 0.10f) },
                    onPlus = { onPlaybackRateChange(mrPlaybackRate + 0.10f) },
                    onAction = { onPlaybackRateChange(1.00f) }
                )

                PleosHorizontalTrackBar(
                    label = "키 조절",
                    valueText = formatConsumerKeyLabel(keyShift),
                    progress = (keyShift + 6) / 12f,
                    isInactiveZero = false,
                    actionLabel = "기본 키",
                    onMinus = { onKeyShiftChange(keyShift - 1) },
                    onPlus = { onKeyShiftChange(keyShift + 1) },
                    onAction = { onKeyShiftChange(0) }
                )

                PleosHorizontalTrackBar(
                    label = "보컬 싱크",
                    valueText = if (syncOffsetMs > 0) "+${syncOffsetMs}ms" else "${syncOffsetMs}ms",
                    progress = ((syncOffsetMs + 500f) / 1000f).coerceIn(0f, 1f),
                    isInactiveZero = false,
                    actionLabel = "초기화 0ms",
                    onMinus = { onSyncOffsetChange(syncOffsetMs - 100) },
                    onPlus = { onSyncOffsetChange(syncOffsetMs + 100) },
                    onAction = { onSyncOffsetChange(0) }
                )
            }
        }
    }
}
