package io.github.teamcarinkaraoke.pleosstage.car.audio

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

class CarAudioEngineController {
    internal var webView: WebView? = null
    val nativeVocalEngine = NativeVocalAudioEngine().apply {
        start()
    }
    val guideTelemetry: StateFlow<GuideVocalTelemetryState>
        get() = nativeVocalEngine.guideTelemetry

    fun connectToSignalingServer(localWsUrl: String = "ws://127.0.0.1:8080/ws", pin: String) {
        val safeUrl = localWsUrl.replace("'", "")
        val safePin = pin.replace("'", "")
        evalJs("window.PleosCarEngine && window.PleosCarEngine.connectServer('$safeUrl', '$safePin');")
    }

    fun startSong(songId: String, bpm: Int = 124, forceSynth: Boolean = false) {
        val safeSongId = songId.replace("'", "")
        nativeVocalEngine.setTrackConfig(safeSongId, bpm)
        nativeVocalEngine.setStagePlaying(true)
        evalJs("window.PleosCarEngine && window.PleosCarEngine.startSong('$safeSongId', $bpm, $forceSynth);")
    }

    fun stopSong() {
        evalJs("window.PleosCarEngine && window.PleosCarEngine.stopSong();")
    }

    fun setStagePlaying(playing: Boolean) {
        nativeVocalEngine.setStagePlaying(playing)
    }

    fun setGuideVocalEnabled(enabled: Boolean) {
        nativeVocalEngine.setGuideVocalEnabled(enabled)
    }

    fun setTrackConfig(trackId: String, bpm: Int) {
        nativeVocalEngine.setTrackConfig(trackId, bpm)
    }

    fun setGuidePitch(pitchHz: Float, note: String, isHighlight: Boolean = false) {
        nativeVocalEngine.setGuidePitch(pitchHz, note, isHighlight)
    }

    fun setMasterVolumeScale(scale: Float) {
        nativeVocalEngine.setMasterVolumeScale(scale)
    }

    fun setKeyShift(semitones: Int) {
        nativeVocalEngine.setKeyShift(semitones)
        evalJs("window.PleosCarEngine && window.PleosCarEngine.setKeyShift($semitones);")
    }

    fun setEchoLevel(level: Int) {
        nativeVocalEngine.setEchoLevel(level)
        evalJs("window.PleosCarEngine && window.PleosCarEngine.setEchoLevel($level);")
    }

    fun setBackingVolume(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 100)
        evalJs("window.PleosCarEngine && window.PleosCarEngine.setBackingVolume && window.PleosCarEngine.setBackingVolume($clamped);")
    }

    fun triggerSfx(sfxType: String) {
        nativeVocalEngine.triggerSfx(sfxType)
    }

    fun enqueueMicPcmChunk(sampleRate: Int, pcmShorts: ShortArray) {
        nativeVocalEngine.enqueueMicPcmChunk(sampleRate, pcmShorts)
    }

    fun syncStateToMic(
        songId: String,
        isPlaying: Boolean,
        keyShift: Int,
        echoLevel: Int,
        isParked: Boolean,
        score: Int,
        currentTrackId: String = songId,
        progressSec: Int = 0,
        durationSec: Int = 48,
        backingVolume: Int = 85,
        isDevMode: Boolean = true,
        guideVocalEnabled: Boolean = false,
        guidePitchHz: Float = 0f,
        guideNote: String = "-",
        guideVoiceLevelPercent: Int = 0,
        playlistJson: JSONArray? = null
    ) {
        val json = JSONObject().apply {
            put("currentSongId", songId)
            put("currentTrackId", currentTrackId)
            put("isPlaying", isPlaying)
            put("progressSec", progressSec)
            put("currentPositionSec", progressSec)
            put("durationSec", durationSec)
            put("backingVolume", backingVolume)
            put("keyShift", keyShift)
            put("echoLevel", echoLevel)
            put("isParked", isParked)
            put("score", score)
            put("isDevMode", isDevMode)
            put("guideVocalEnabled", guideVocalEnabled)
            put("demoVocalWave", guideVocalEnabled)
            put("guidePitchHz", guidePitchHz.toDouble())
            put("guideNote", guideNote)
            put("guideVoiceLevelPercent", guideVoiceLevelPercent)
            if (playlistJson != null) {
                put("playlist", playlistJson)
            }
        }.toString().replace("\\", "\\\\").replace("'", "\\'")
        evalJs("window.PleosCarEngine && window.PleosCarEngine.syncStateToMic('$json');")
    }

    fun release() {
        nativeVocalEngine.release()
    }

    private fun evalJs(script: String) {
        webView?.post {
            webView?.evaluateJavascript(script, null)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CarWetReverbEngineHost(
    controller: CarAudioEngineController,
    signalingUrl: String,
    initialPin: String,
    onRoomRegistered: (pin: String, lanUrl: String, tunnelUrl: String) -> Unit,
    onTunnelUpdated: (tunnelUrl: String) -> Unit,
    onMicConnectionChanged: (connected: Boolean, deviceName: String, count: Int) -> Unit,
    onVocalTelemetry: (rms: Float, pitchHz: Float, note: String) -> Unit,
    onMusicSpectrum: (bands: List<Float>) -> Unit = {},
    onRemoteCommand: (type: String, payload: JSONObject) -> Unit,
    onEngineLog: (String) -> Unit
) {
    // 1.dp size & alpha = 0.01f prevents Chromium WebView background throttling
    AndroidView(
        modifier = Modifier
            .size(1.dp)
            .alpha(0.01f),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = true

                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.grant(request.resources)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        controller.connectToSignalingServer(signalingUrl, initialPin)
                    }
                }

                val hostWebView = this
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onRoomRegistered(pin: String, lanUrl: String, tunnelUrl: String) {
                            hostWebView.post {
                                onRoomRegistered(pin, lanUrl, tunnelUrl)
                            }
                        }

                        @JavascriptInterface
                        fun onTunnelUpdated(tunnelUrl: String) {
                            hostWebView.post {
                                onTunnelUpdated(tunnelUrl)
                            }
                        }

                        @JavascriptInterface
                        fun onMicConnectionChanged(connected: Boolean, deviceName: String, count: Int) {
                            hostWebView.post {
                                onMicConnectionChanged(connected, deviceName, count)
                            }
                        }

                        @JavascriptInterface
                        fun onWebRtcConnected(micId: String) {
                            hostWebView.post {
                                onEngineLog("WebRTC P2P Connected ($micId) • Native AudioTrack Reverb Active")
                            }
                        }

                        @JavascriptInterface
                        fun onVocalTelemetry(rms: Float, pitch: Float, note: String) {
                            hostWebView.post {
                                onVocalTelemetry(rms, pitch, note)
                            }
                        }

                        @JavascriptInterface
                        fun onAudioPcmChunk(sampleRate: Int, csvPcm: String) {
                            if (csvPcm.isBlank()) return
                            try {
                                val parts = csvPcm.split(",")
                                val shorts = ShortArray(parts.size)
                                var count = 0
                                for (p in parts) {
                                    val v = p.trim().toIntOrNull()
                                    if (v != null) {
                                        shorts[count++] = v.coerceIn(-32768, 32767).toShort()
                                    }
                                }
                                if (count > 0) {
                                    val finalShorts = if (count == shorts.size) shorts else shorts.copyOf(count)
                                    controller.enqueueMicPcmChunk(sampleRate, finalShorts)
                                }
                            } catch (_: Exception) {
                            }
                        }

                        @JavascriptInterface
                        fun onMusicSpectrum(csvBands: String) {
                            hostWebView.post {
                                if (csvBands.isBlank()) {
                                    onMusicSpectrum(List(48) { 0f })
                                } else {
                                    val parsed = csvBands.split(",").mapNotNull {
                                        it.trim().toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f)
                                    }
                                    if (parsed.isNotEmpty()) {
                                        onMusicSpectrum(parsed)
                                    }
                                }
                            }
                        }

                        @JavascriptInterface
                        fun onRemoteCommand(jsonStr: String) {
                            hostWebView.post {
                                try {
                                    val obj = JSONObject(jsonStr)
                                    val type = obj.optString("type", "")
                                    if (type.isNotEmpty()) {
                                        onRemoteCommand(type, obj)
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }

                        @JavascriptInterface
                        fun onEngineLog(msg: String) {
                            Log.i("PleosCarEngine", msg)
                            hostWebView.post {
                                onEngineLog(msg)
                            }
                        }
                    },
                    "PleosCarBridge"
                )

                onResume()
                resumeTimers()
                controller.webView = this
                loadUrl("file:///android_asset/car-engine.html")
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            controller.release()
            controller.webView?.destroy()
            controller.webView = null
        }
    }
}
