package io.github.teamcarinkaraoke.pleosstage.mic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDictionary
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

@Composable
actual fun YouTubeThumbnailBox(
    videoId: String,
    durationLabel: String,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF141B2D), Color(0xFF2B3556))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "▶ YT",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold
        )
        if (durationLabel.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC0B0E14))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = durationLabel,
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun HeadlessAudioWebViewBridge(
    controller: AudioBridgeController,
    modifier: Modifier
) {
    val messageHandler = remember(controller) {
        object : NSObject(), WKScriptMessageHandlerProtocol {
            override fun userContentController(
                userContentController: WKUserContentController,
                didReceiveScriptMessage: WKScriptMessage
            ) {
                val body = didReceiveScriptMessage.body as? NSDictionary ?: return
                val type = body.objectForKey("type") as? String ?: return
                when (type) {
                    "metrics" -> {
                        val rms = (body.objectForKey("rms") as? NSNumber)?.floatValue ?: 0f
                        val pitch = (body.objectForKey("pitch") as? NSNumber)?.floatValue ?: 0f
                        val note = body.objectForKey("note") as? String ?: "-"
                        controller.onMetricsFromWebView(rms, pitch, note)
                    }
                    "connection" -> {
                        val state = body.objectForKey("state") as? String ?: "IDLE"
                        val message = body.objectForKey("message") as? String ?: ""
                        val pin = body.objectForKey("pin") as? String ?: ""
                        controller.onConnectionStateFromWebView(state, message, pin)
                    }
                    "carState" -> {
                        val songId = body.objectForKey("songId") as? String ?: "ncs_short_test_30s"
                        val currentTrackId = body.objectForKey("currentTrackId") as? String ?: songId
                        val isPlaying = (body.objectForKey("isPlaying") as? NSNumber)?.boolValue ?: false
                        val progressSec = (body.objectForKey("progressSec") as? NSNumber)?.intValue ?: 0
                        val durationSec = (body.objectForKey("durationSec") as? NSNumber)?.intValue ?: 30
                        val backingVolume = (body.objectForKey("backingVolume") as? NSNumber)?.intValue ?: 85
                        val keyShift = (body.objectForKey("keyShift") as? NSNumber)?.intValue ?: 0
                        val echoLevel = (body.objectForKey("echoLevel") as? NSNumber)?.intValue ?: 80
                        val isParked = (body.objectForKey("isParked") as? NSNumber)?.boolValue ?: true
                        val score = (body.objectForKey("score") as? NSNumber)?.intValue ?: 78
                        val isDevMode = if (isDebugBuildAllowed()) {
                            (body.objectForKey("isDevMode") as? NSNumber)?.boolValue ?: true
                        } else {
                            false
                        }
                        val playlistEncoded = body.objectForKey("playlistEncoded") as? String ?: ""
                        (body.objectForKey("scoreEnabled") as? NSNumber)?.boolValue?.let {
                            controller.onScoreModeSyncedFromCar(it)
                        }
                        controller.onCarStateSyncFromWebView(
                            songId = songId,
                            isPlaying = isPlaying,
                            keyShift = keyShift,
                            echoLevel = echoLevel,
                            isParked = isParked,
                            score = score,
                            currentTrackId = currentTrackId,
                            progressSec = progressSec,
                            durationSec = durationSec,
                            backingVolume = backingVolume,
                            isDevMode = isDevMode,
                            playlistEncoded = playlistEncoded
                        )
                    }
                    "scoreMode" -> {
                        val enabled = (body.objectForKey("enabled") as? NSNumber)?.boolValue ?: true
                        controller.onScoreModeSyncedFromCar(enabled)
                    }
                    "karaokeScoreResult" -> {
                        val resultId = body.objectForKey("resultId") as? String ?: ""
                        val score = (body.objectForKey("score") as? NSNumber)?.intValue ?: 95
                        val badge = body.objectForKey("badge") as? String ?: "SUPER STAR"
                        val title = body.objectForKey("title") as? String ?: "🎉 환상적인 가창력! 앙코르 요청 쇄도!"
                        val comment = body.objectForKey("comment") as? String ?: ""
                        val pitchAccuracy = (body.objectForKey("pitchAccuracy") as? NSNumber)?.intValue ?: 92
                        val vocalPower = (body.objectForKey("vocalPower") as? NSNumber)?.intValue ?: 90
                        val stageManner = (body.objectForKey("stageManner") as? NSNumber)?.intValue ?: 94
                        val trackTitle = body.objectForKey("trackTitle") as? String ?: ""
                        val willAdvanceNext = (body.objectForKey("willAdvanceNext") as? NSNumber)?.boolValue ?: false
                        controller.onKaraokeScoreResultReceived(
                            KaraokeScoreResultState(
                                resultId = resultId,
                                score = score,
                                badge = badge,
                                title = title,
                                comment = comment,
                                pitchAccuracy = pitchAccuracy,
                                vocalPower = vocalPower,
                                stageManner = stageManner,
                                trackTitle = trackTitle,
                                willAdvanceNext = willAdvanceNext
                            )
                        )
                    }
                    "karaokeScoreDismiss" -> {
                        val resultId = body.objectForKey("resultId") as? String ?: ""
                        controller.onKaraokeScoreDismissedFromCar(resultId)
                    }
                }
            }
        }
    }

    UIKitView(
        modifier = modifier
            .size(1.dp)
            .alpha(0.01f),
        factory = {
            val contentController = WKUserContentController().apply {
                addScriptMessageHandler(messageHandler, name = "PleosMicBridge")
            }
            val config = WKWebViewConfiguration().apply {
                userContentController = contentController
                allowsInlineMediaPlayback = true
                mediaTypesRequiringUserActionForPlayback = 0u
            }
            val webView = WKWebView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0), configuration = config)
            controller.jsEvaluator = { script ->
                webView.evaluateJavaScript(script, completionHandler = null)
            }
            webView.loadHTMLString(
                string = buildHeadlessMicEngineHtml(),
                baseURL = NSURL.URLWithString("http://localhost/")
            )
            webView
        }
    )

    DisposableEffect(controller) {
        onDispose {
            controller.jsEvaluator = null
        }
    }
}

actual fun openYouTubeKaraokeAppSearch(query: String) {
}

actual fun readClipboardTextForPlaylist(): String? = null

actual fun warmUpLocalStageSfx() {
}

actual fun playLocalStageSfx(sfxType: String, volumeScale: Float) {
}

actual fun setKeepScreenOnEnabled(enabled: Boolean) {
}
