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
    var youTubeMrController: YouTubeMrController? = null
    val nativeVocalEngine = NativeVocalAudioEngine().apply {
        start()
    }
    val guideTelemetry: StateFlow<GuideVocalTelemetryState>
        get() = nativeVocalEngine.guideTelemetry

    fun applyRemoteAudioCommandDirectly(type: String, cmd: JSONObject) {
        when (type) {
            "CHANGE_BACKING_VOLUME", "YOUTUBE_VOLUME" -> {
                val vol = cmd.optInt("backingVolume", cmd.optInt("mrVolume", cmd.optInt("volume", 85))).coerceIn(0, 100)
                setBackingVolume(vol)
            }
            "CHANGE_MIC_VOLUME" -> {
                val vol = cmd.optInt("micVolume", cmd.optInt("volume", 85)).coerceIn(0, 100)
                setMicVolume(vol)
            }
            "CHANGE_GUIDE_VOLUME" -> {
                val vol = cmd.optInt("guideVolume", cmd.optInt("volume", 75)).coerceIn(0, 100)
                setGuideVolume(vol)
            }
            "CHANGE_ECHO" -> {
                val echo = cmd.optInt("echoLevel", cmd.optInt("reverbLevel", 80)).coerceIn(0, 100)
                setEchoLevel(echo)
            }
            "CHANGE_PLAYBACK_RATE" -> {
                val rate = cmd.optDouble("mrPlaybackRate", cmd.optDouble("playbackRate", 1.0)).toFloat().coerceIn(0.75f, 1.25f)
                setPlaybackRate(rate)
            }
            "CHANGE_SYNC_OFFSET" -> {
                val offset = cmd.optInt("syncOffsetMs", 0).coerceIn(-500, 500)
                val explicitDelta = if (cmd.has("syncDeltaMs")) cmd.optInt("syncDeltaMs", 0) else null
                adjustSyncOffsetMs(offset, explicitDelta)
            }
            "RESET_AUDIO_MIXER" -> {
                setBackingVolume(85)
                setMicVolume(85)
                setGuideVolume(75)
                setEchoLevel(80)
                setPlaybackRate(1.00f)
                adjustSyncOffsetMs(0, null)
            }
            "CHANGE_KEY" -> {
                val ks = cmd.optInt("keyShift", 0).coerceIn(-6, 6)
                setKeyShift(ks)
            }
            "TOGGLE_GUIDE_VOCAL", "TOGGLE_DEMO_WAVE" -> {
                val enabled = cmd.optBoolean("guideVocalEnabled", cmd.optBoolean("enabled", false))
                setGuideVocalEnabled(enabled)
            }
            "SEEK_TO", "seek-to" -> {
                val posSec = cmd.optDouble("positionSec", cmd.optDouble("seekSec", 0.0)).toFloat().coerceAtLeast(0f)
                seekTo(posSec)
            }
        }
    }

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

    fun setMicVolume(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 100)
        nativeVocalEngine.setMicVolume(clamped)
        evalJs("window.PleosCarEngine && window.PleosCarEngine.setMicVolume && window.PleosCarEngine.setMicVolume($clamped);")
    }

    fun setGuideVolume(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 100)
        nativeVocalEngine.setGuideVolume(clamped)
        evalJs("window.PleosCarEngine && window.PleosCarEngine.setGuideVolume && window.PleosCarEngine.setGuideVolume($clamped);")
    }

    fun setBackingVolume(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 100)
        nativeVocalEngine.setBackingVolume(clamped)
        youTubeMrController?.setVolume(clamped)
        evalJs("window.PleosCarEngine && window.PleosCarEngine.setBackingVolume && window.PleosCarEngine.setBackingVolume($clamped);")
    }

    fun setPlaybackRate(rate: Float) {
        val clamped = ((rate.coerceIn(0.75f, 1.25f) * 20f).let { kotlin.math.round(it) } / 20f)
        nativeVocalEngine.setPlaybackRate(clamped)
        youTubeMrController?.setPlaybackRate(clamped)
        evalJs("window.PleosCarEngine && window.PleosCarEngine.setPlaybackRate && window.PleosCarEngine.setPlaybackRate($clamped);")
    }

    fun setSyncOffsetMs(offsetMs: Int) {
        adjustSyncOffsetMs(offsetMs, null)
    }

    fun adjustSyncOffsetMs(offsetMs: Int, explicitDeltaMs: Int? = null) {
        val clamped = offsetMs.coerceIn(-500, 500)
        nativeVocalEngine.setSyncOffsetMs(clamped)
        youTubeMrController?.adjustSyncOffset(clamped, explicitDeltaMs)
        evalJs("window.PleosCarEngine && window.PleosCarEngine.setSyncOffsetMs && window.PleosCarEngine.setSyncOffsetMs($clamped);")
    }

    fun seekTo(positionSec: Float) {
        val safeSec = positionSec.coerceAtLeast(0f)
        youTubeMrController?.seekTo(safeSec)
    }

    fun triggerSfx(sfxType: String) {
        nativeVocalEngine.triggerSfx(sfxType)
    }

    fun enqueueMicPcmChunk(sampleRate: Int, pcmShorts: ShortArray, seq: Long = -1L) {
        nativeVocalEngine.enqueueMicPcmChunk(sampleRate, pcmShorts, seq)
    }

    fun getBufferedMicLatencyMs(): Int {
        return nativeVocalEngine.getBufferedMicLatencyMs()
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
        micVolume: Int = 85,
        guideVolume: Int = 75,
        mrPlaybackRate: Float = 1.00f,
        syncOffsetMs: Int = 0,
        carHostId: String = "",
        stateRevision: Long = 1L,
        isDevMode: Boolean = io.github.teamcarinkaraoke.pleosstage.car.BuildConfig.DEBUG,
        scoreEnabled: Boolean = true,
        guideVocalEnabled: Boolean = false,
        guidePitchHz: Float = 0f,
        guideNote: String = "-",
        guideVoiceLevelPercent: Int = 0,
        playlistJson: JSONArray? = null
    ) {
        val json = JSONObject().apply {
            if (carHostId.isNotBlank()) {
                put("carHostId", carHostId)
            }
            put("stateRevision", stateRevision)
            put("currentSongId", songId)
            put("currentTrackId", currentTrackId)
            put("isPlaying", isPlaying)
            put("progressSec", progressSec)
            put("currentPositionSec", progressSec)
            put("durationSec", durationSec)
            put("backingVolume", backingVolume)
            put("mrVolume", backingVolume)
            put("micVolume", micVolume)
            put("guideVolume", guideVolume)
            put("echoLevel", echoLevel)
            put("reverbLevel", echoLevel)
            put("mrPlaybackRate", mrPlaybackRate.toDouble())
            put("syncOffsetMs", syncOffsetMs)
            put("keyShift", keyShift)
            put("isParked", isParked)
            put("score", score)
            put("scoreEnabled", scoreEnabled)
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

    fun broadcastScoreResultToMic(scoreJson: JSONObject) {
        val escaped = scoreJson.toString().replace("\\", "\\\\").replace("'", "\\'")
        evalJs("window.PleosCarEngine && window.PleosCarEngine.broadcastScoreResult && window.PleosCarEngine.broadcastScoreResult('$escaped');")
    }

    fun broadcastScoreDismissToMic(resultId: String) {
        val payload = JSONObject().apply {
            put("resultId", resultId)
            put("ts", System.currentTimeMillis())
        }.toString().replace("\\", "\\\\").replace("'", "\\'")
        evalJs("window.PleosCarEngine && window.PleosCarEngine.broadcastScoreDismiss && window.PleosCarEngine.broadcastScoreDismiss('$payload');")
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
    onMicLatencyUpdated: (totalLatencyMs: Int, netLatencyMs: Int, carBufferMs: Int, transport: String) -> Unit = { _, _, _, _ -> },
    onPhoneCandidateIps: (ips: List<String>, phonePort: Int, deviceName: String) -> Unit = { _, _, _ -> },
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
                        fun onPhoneCandidateIps(ipsCsv: String, phonePort: Int, deviceName: String) {
                            val list = ipsCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }
                            if (list.isNotEmpty()) {
                                onPhoneCandidateIps(
                                    list,
                                    if (phonePort > 0) phonePort else 8082,
                                    deviceName.ifBlank { "Pleos Smart Mic" }
                                )
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
                        fun getCarBufferLatencyMs(): Int {
                            return controller.getBufferedMicLatencyMs()
                        }

                        @JavascriptInterface
                        fun onMicLatencyUpdated(totalLatencyMs: Int, netLatencyMs: Int, carBufferMs: Int, transport: String) {
                            hostWebView.post {
                                onMicLatencyUpdated(totalLatencyMs, netLatencyMs, carBufferMs, transport)
                            }
                        }

                        @JavascriptInterface
                        fun onAudioPcmChunk(sampleRate: Int, csvPcm: String) {
                            onAudioPcmChunkSeq(sampleRate, -1L, csvPcm)
                        }

                        @JavascriptInterface
                        fun onAudioPcmChunkSeq(sampleRate: Int, seq: Long, csvPcm: String) {
                            val n = csvPcm.length
                            if (n == 0) return
                            try {
                                // Zero-allocation integer parser for 48kHz 1024-sample CSV chunks (eliminates JVM String.split GC pauses)
                                var commaCount = 0
                                for (i in 0 until n) {
                                    if (csvPcm[i] == ',') commaCount++
                                }
                                val shorts = ShortArray(commaCount + 1)
                                var count = 0
                                var idx = 0
                                while (idx < n) {
                                    while (idx < n && (csvPcm[idx] <= ' ' || csvPcm[idx] == ',')) {
                                        idx++
                                    }
                                    if (idx >= n) break
                                    var neg = false
                                    if (csvPcm[idx] == '-') {
                                        neg = true
                                        idx++
                                    } else if (csvPcm[idx] == '+') {
                                        idx++
                                    }
                                    var value = 0
                                    var hasDigit = false
                                    while (idx < n) {
                                        val ch = csvPcm[idx]
                                        if (ch in '0'..'9') {
                                            value = value * 10 + (ch - '0')
                                            hasDigit = true
                                            idx++
                                        } else {
                                            break
                                        }
                                    }
                                    while (idx < n && csvPcm[idx] != ',') {
                                        idx++
                                    }
                                    if (hasDigit && count < shorts.size) {
                                        val signed = if (neg) -value else value
                                        shorts[count++] = signed.coerceIn(-32768, 32767).toShort()
                                    }
                                    if (idx < n && csvPcm[idx] == ',') idx++
                                }
                                if (count > 0) {
                                    val finalShorts = if (count == shorts.size) shorts else shorts.copyOf(count)
                                    controller.enqueueMicPcmChunk(sampleRate, finalShorts, seq)
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
                                    val type = obj.optString("type", "").ifBlank { obj.optString("action", "") }
                                    if (type.isNotEmpty()) {
                                        controller.applyRemoteAudioCommandDirectly(type, obj)
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
