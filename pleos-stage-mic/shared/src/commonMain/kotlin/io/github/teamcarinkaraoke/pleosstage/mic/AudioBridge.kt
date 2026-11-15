package io.github.teamcarinkaraoke.pleosstage.mic

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.pow

data class VocalMetrics(
    val rms: Float = 0f,        // 0..100
    val pitchHz: Float = 0f,    // Hz
    val note: String = "-",     // e.g. C4, G4, A4
    val isStreaming: Boolean = false
)

data class MicConnectionStatus(
    val state: String = "IDLE", // IDLE, CONNECTING, CONNECTED, STREAMING, ERROR
    val message: String = "○ 연결 안 됨 (대기 중)",
    val pin: String = "4829",
    val serverUrl: String = "ws://10.0.2.2:8080/ws",
    val discoveredCarUrl: String = "",
    val phoneHotspotIps: List<String> = emptyList(),
    val isDirectLocalConnected: Boolean = false,
    val isCarVerified: Boolean = false,
    val netLatencyMs: Int = 3,
    val carBufferMs: Int = 12,
    val totalVocalLatencyMs: Int = 26,
    val transportMode: String = "DIRECT"
)

/**
 * Single Source of Truth Unified Playlist Track shared 1:1 between `pleos-stage-mic` and `pleos-stage-car`.
 */
data class UnifiedMicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val bpm: Int,
    val durationSec: Int,
    val durationLabel: String,
    val videoId: String = "",
    val songId: String = "",
    val isDevSample: Boolean = false
) {
    val isYouTube: Boolean get() = videoId.isNotBlank()
    val isBuiltIn: Boolean get() = songId.isNotBlank() && videoId.isBlank()
}

fun formatTrackMmSs(totalSec: Int): String {
    val safe = totalSec.coerceAtLeast(0)
    val m = safe / 60
    val s = safe % 60
    val mStr = if (m < 10) "0$m" else "$m"
    val sStr = if (s < 10) "0$s" else "$s"
    return "$mStr:$sStr"
}

val DEFAULT_UNIFIED_MIC_PLAYLIST: List<UnifiedMicTrack> = listOf(
    UnifiedMicTrack(
        id = "ncs_short_test_30s",
        title = "테스트 음원",
        artist = "Pleos Stage · 테스트 음원",
        genre = "",
        bpm = 128,
        durationSec = 30,
        durationLabel = "00:30",
        videoId = "K4DyBUG242c",
        songId = "",
        isDevSample = true
    ),
    UnifiedMicTrack(
        id = "ncs_on_and_on",
        title = "Cartoon - On & On (feat. Daniel Levi)",
        artist = "NoCopyrightSounds • Cartoon",
        genre = "",
        bpm = 128,
        durationSec = 208,
        durationLabel = "03:28",
        videoId = "K4DyBUG242c",
        songId = "",
        isDevSample = true
    ),
    UnifiedMicTrack(
        id = "ncs_alan_walker_fade",
        title = "Alan Walker - Fade [NCS Release]",
        artist = "NoCopyrightSounds • Alan Walker",
        genre = "",
        bpm = 90,
        durationSec = 264,
        durationLabel = "04:24",
        videoId = "bM7SZ5SBzyY",
        songId = "",
        isDevSample = true
    ),
    UnifiedMicTrack(
        id = "ncs_heroes_tonight",
        title = "Janji - Heroes Tonight (feat. Johnning)",
        artist = "NoCopyrightSounds • Janji",
        genre = "",
        bpm = 128,
        durationSec = 208,
        durationLabel = "03:28",
        videoId = "3nQNiWdeH2Q",
        songId = "",
        isDevSample = true
    ),
    UnifiedMicTrack(
        id = "ncs_deaf_kev_invincible",
        title = "DEAF KEV - Invincible [NCS Release]",
        artist = "NoCopyrightSounds • DEAF KEV",
        genre = "",
        bpm = 100,
        durationSec = 273,
        durationLabel = "04:33",
        videoId = "J2X5mJ3HDYE",
        songId = "",
        isDevSample = true
    ),
    UnifiedMicTrack(
        id = "ncs_warriyo_mortals",
        title = "Warriyo - Mortals (feat. Laura Brehm)",
        artist = "NoCopyrightSounds • Warriyo",
        genre = "",
        bpm = 110,
        durationSec = 230,
        durationLabel = "03:50",
        videoId = "yJg-Y5byMMw",
        songId = "",
        isDevSample = true
    )
)

data class SyncedCarStageState(
    val currentSongId: String = if (isDebugBuildAllowed()) "ncs_short_test_30s" else "",
    val currentTrackId: String = if (isDebugBuildAllowed()) "ncs_short_test_30s" else "",
    val isPlaying: Boolean = false,
    val progressSec: Int = 0,
    val durationSec: Int = if (isDebugBuildAllowed()) 30 else 0,
    val backingVolume: Int = 85,
    val micVolume: Int = 85,
    val guideVolume: Int = 75,
    val keyShift: Int = 0,
    val echoLevel: Int = 80,
    val mrPlaybackRate: Float = 1.00f,
    val syncOffsetMs: Int = 0,
    val isParked: Boolean = true,
    val score: Int = 78,
    val isScoreModeEnabled: Boolean = true,
    val isDevMode: Boolean = isDebugBuildAllowed(),
    val guideVocalEnabled: Boolean = false,
    val guidePitchHz: Float = 0f,
    val guideNote: String = "-",
    val guideVoiceLevelPercent: Float = 0f,
    val carHostId: String = "",
    val stateRevision: Long = 1L,
    val playlist: List<UnifiedMicTrack> = if (isDebugBuildAllowed()) DEFAULT_UNIFIED_MIC_PLAYLIST else emptyList()
)

/**
 * 차량-스마트폰 동시 동기화되는 노래방 점수 발표 결과 모델 (`karaoke-score-result`)
 */
data class KaraokeScoreResultState(
    val resultId: String,
    val score: Int,
    val badge: String,
    val title: String,
    val comment: String,
    val pitchAccuracy: Int = 92,
    val vocalPower: Int = 90,
    val stageManner: Int = 94,
    val trackTitle: String = "",
    val willAdvanceNext: Boolean = false
)

/**
 * 스마트폰 단독 재생/프리뷰 시에도 동일한 100점 편향형(75~100점) 알고리즘을 제공하는 KMP 누적기 & 계산기
 */
class MicVocalScoreAccumulator {
    var activeVoiceTicks: Int = 0
        private set
    var activeVoiceSeconds: Double = 0.0
        private set
    var activeEnergySum: Double = 0.0
        private set
    var peakEnergy: Float = 0f
        private set
    var pitchDetectedTicks: Int = 0
        private set

    fun reset() {
        activeVoiceTicks = 0
        activeVoiceSeconds = 0.0
        activeEnergySum = 0.0
        peakEnergy = 0f
        pitchDetectedTicks = 0
    }

    fun recordTick(micLevelPercent: Float, pitchHz: Float, dtSeconds: Double) {
        val safeLevel = micLevelPercent.coerceIn(0f, 100f)
        val safeDt = dtSeconds.coerceIn(0.01, 0.25)
        if (safeLevel >= 8f) {
            activeVoiceTicks += 1
            activeVoiceSeconds += safeDt
            activeEnergySum += safeLevel.toDouble()
            if (safeLevel > peakEnergy) {
                peakEnergy = safeLevel
            }
            if (pitchHz > 60f) {
                pitchDetectedTicks += 1
            }
        }
    }

    val avgActiveEnergy: Double
        get() = if (activeVoiceTicks > 0) activeEnergySum / activeVoiceTicks.toDouble() else 0.0

    val pitchActiveRatio: Double
        get() = if (activeVoiceTicks > 0) {
            (pitchDetectedTicks.toDouble() / activeVoiceTicks.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
}

object MicKaraokeScoreCalculator {
    fun calculateKaraokeScore(
        acc: MicVocalScoreAccumulator,
        trackTitle: String = "",
        willAdvanceNext: Boolean = false
    ): KaraokeScoreResultState {
        val resultId = "mic_score_${kotlin.random.Random.nextInt(100_000, 999_999)}"
        if (acc.activeVoiceTicks <= 0 || acc.activeVoiceSeconds < 1.5) {
            val encouragementScore = kotlin.random.Random.nextInt(75, 80)
            val (badge, title) = resolveBadgeAndTitle(encouragementScore)
            return KaraokeScoreResultState(
                resultId = resultId,
                score = encouragementScore,
                badge = badge,
                title = title,
                comment = "반주와 함께 즐거운 드라이빙 스테이지였어요! 다음 곡에선 마이크에 목소리를 들려주세요!",
                pitchAccuracy = 78,
                vocalPower = 76,
                stageManner = 82,
                trackTitle = trackTitle,
                willAdvanceNext = willAdvanceNext
            )
        }

        val rawP = kotlin.math.min(1.0, acc.activeVoiceSeconds / 18.0)
        val participationP = if (acc.activeVoiceSeconds >= 1.0) kotlin.math.max(0.38, rawP) else kotlin.math.max(0.28, rawP)
        val energyE = kotlin.math.min(1.0, acc.avgActiveEnergy / 28.0)
        val vitalityV = kotlin.math.min(1.0, acc.pitchActiveRatio * 1.4)
        val x = (0.50 * participationP + 0.30 * energyE + 0.20 * vitalityV).coerceIn(0.0, 1.0)
        val y = x.pow(0.38)
        val passionBonus = when {
            acc.peakEnergy >= 55f && acc.activeVoiceSeconds >= 8.0 -> 2
            acc.peakEnergy >= 32f || acc.activeVoiceSeconds >= 4.0 -> 1
            else -> kotlin.random.Random.nextInt(0, 2)
        }
        val finalScore = (kotlin.math.round(80.0 + 20.0 * y).toInt() + passionBonus).coerceIn(82, 100)
        val (badge, title) = resolveBadgeAndTitle(finalScore)
        val comment = when {
            finalScore >= 98 -> "최고 성량 ${kotlin.math.round(acc.peakEnergy).toInt()}% · 흔들림 없는 음정 에너지까지 완벽했습니다!"
            finalScore >= 90 -> "풍부한 성량과 정확한 음정 표현력이 돋보인 멋진 라이브였어요!"
            else -> "감미로운 보이스 톤과 리듬감으로 드라이빙 무대를 가득 채웠어요!"
        }
        val pitchGauge = kotlin.math.round(80.0 + 20.0 * vitalityV.coerceIn(0.18, 1.0).pow(0.42)).toInt().coerceIn(82, 100)
        val powerGauge = kotlin.math.round(80.0 + 20.0 * energyE.coerceIn(0.18, 1.0).pow(0.40)).toInt().coerceIn(82, 100)
        val mannerGauge = kotlin.math.round(82.0 + 18.0 * participationP.coerceIn(0.25, 1.0).pow(0.38)).toInt().coerceIn(84, 100)
        return KaraokeScoreResultState(
            resultId = resultId,
            score = finalScore,
            badge = badge,
            title = title,
            comment = comment,
            pitchAccuracy = pitchGauge,
            vocalPower = powerGauge,
            stageManner = mannerGauge,
            trackTitle = trackTitle,
            willAdvanceNext = willAdvanceNext
        )
    }

    fun resolveBadgeAndTitle(score: Int): Pair<String, String> = when {
        score >= 98 -> "GOLDEN CROWN" to "👑 완벽한 레전드 무대! 가왕 탄생!"
        score >= 90 -> "SUPER STAR" to "🎉 환상적인 가창력! 앙코르 요청 쇄도!"
        score >= 82 -> "GREAT STAGE" to "✨ 분위기 최고! 감미로운 라이브 무대!"
        else -> "GOOD VIBE" to "🎵 즐거운 드라이빙 노래방 스테이지!"
    }
}

fun decodeSyncedPlaylist(encoded: String): List<UnifiedMicTrack> {
    if (encoded.isBlank() || encoded == "__EMPTY__") return emptyList()
    return encoded.split("\n").mapNotNull { line ->
        if (line.isBlank()) return@mapNotNull null
        val cols = line.split("\t")
        if (cols.size < 9) return@mapNotNull null
        val id = cols[0].trim()
        if (id.isEmpty()) return@mapNotNull null
        val title = cols[1].ifBlank { "Pleos Track" }
        val artist = cols[2].ifBlank { "Pleos Stage" }
        val genre = cols[3]
        val bpm = cols[4].toIntOrNull() ?: 124
        val durSec = (cols[5].toIntOrNull() ?: 180).coerceAtLeast(15)
        val durLabel = cols[6].ifBlank { formatTrackMmSs(durSec) }
        val videoId = cols[7].trim()
        val songId = cols[8].trim()
        val isDev = cols.getOrNull(9)?.trim()?.toBooleanStrictOrNull() ?: false
        UnifiedMicTrack(
            id = id,
            title = title,
            artist = artist,
            genre = genre,
            bpm = bpm,
            durationSec = durSec,
            durationLabel = durLabel,
            videoId = videoId,
            songId = songId,
            isDevSample = isDev
        )
    }
}

/**
 * Bus for receiving Android `Intent.ACTION_SEND` (`text/plain`) shared YouTube links from the YouTube app.
 */
object SharedYouTubeIntentBus {
    private val _incomingSharedUrl = MutableStateFlow<String?>(null)
    val incomingSharedUrl: StateFlow<String?> = _incomingSharedUrl.asStateFlow()

    fun emitSharedText(rawText: String?) {
        val cleaned = rawText?.trim().orEmpty()
        if (cleaned.isEmpty()) return
        // Extract HTTP/HTTPS URL if shared text includes title prefix (e.g. "노래 제목 https://youtu.be/xxxx")
        val urlMatch = Regex("(https?://\\S+)").find(cleaned)?.groupValues?.getOrNull(1) ?: cleaned
        _incomingSharedUrl.value = urlMatch
    }

    fun consume() {
        _incomingSharedUrl.value = null
    }
}

/**
 * Shared KMP Controller that bridges 60fps WebAudio `AnalyserNode` metrics (`StateFlow`)
 * and 1:1 bi-directional WebSocket playlist/stage synchronization to the Car's Embedded Ktor Server.
 */
class AudioBridgeController {
    private val _metrics = MutableStateFlow(VocalMetrics())
    val metrics: StateFlow<VocalMetrics> = _metrics.asStateFlow()

    private val _connection = MutableStateFlow(MicConnectionStatus())
    val connection: StateFlow<MicConnectionStatus> = _connection.asStateFlow()

    private val _carState = MutableStateFlow(SyncedCarStageState())
    val carState: StateFlow<SyncedCarStageState> = _carState.asStateFlow()

    private val _localBackingAudioEnabled = MutableStateFlow(true)
    val localBackingAudioEnabled: StateFlow<Boolean> = _localBackingAudioEnabled.asStateFlow()

    private val _activeScoreResult = MutableStateFlow<KaraokeScoreResultState?>(null)
    val activeScoreResult: StateFlow<KaraokeScoreResultState?> = _activeScoreResult.asStateFlow()

    private val standaloneScoreAccumulator = MicVocalScoreAccumulator()
    private var lastVocalTickMark: kotlin.time.TimeMark? = null
    private var lastScoreModeActionMark: kotlin.time.TimeMark? = null
    private val seenScoreResultIds = ArrayDeque<String>()
    private var pendingStandaloneNextTrack: Boolean = false

    internal var jsEvaluator: ((String) -> Unit)? = null
    internal var ytJsEvaluator: ((String) -> Unit)? = null
    internal var outboundSocketWriter: ((String) -> Unit)? = null
    internal var onVerifiedCarWs8080Callback: ((String, String) -> Unit)? = null
    internal var onCarWs8080ClosedCallback: (() -> Unit)? = null
    internal var phoneLocalIp: String = ""
    internal var phoneCandidateIps: List<String> = emptyList()

    @kotlin.concurrent.Volatile
    var isMicMutedState: Boolean = false
        private set

    // Cooldown Lock (1200ms) & 6.0s Car Watchdog (carHostId & stateRevision packet drops completely removed!)
    private var lastUserActionMark: kotlin.time.TimeMark? = null
    private var lastVerifiedCarPacketMark: kotlin.time.TimeMark? = null
    private var lastDirectVerifiedMark: kotlin.time.TimeMark? = null
    private var connectAttemptMark: kotlin.time.TimeMark? = null

    fun hasRecentVerifiedCarPacket(maxAgeMs: Long = 6000L): Boolean {
        val elapsedMs = lastVerifiedCarPacketMark?.elapsedNow()?.inWholeMilliseconds ?: return false
        return elapsedMs <= maxAgeMs
    }

    private fun hasRecentDirectTransport(maxAgeMs: Long = 6000L): Boolean {
        if (_connection.value.isDirectLocalConnected) return true
        val curMode = _connection.value.transportMode
        if (curMode != "DIRECT" && curMode != "WEBRTC_P2P") return false
        val elapsedMs = lastDirectVerifiedMark?.elapsedNow()?.inWholeMilliseconds ?: return false
        return elapsedMs <= maxAgeMs
    }

    fun onVerifiedCarPacketReceived(pin: String = "", transportHint: String = "") {
        val nowMark = kotlin.time.TimeSource.Monotonic.markNow()
        lastVerifiedCarPacketMark = nowMark
        if (transportHint.equals("DIRECT", ignoreCase = true) ||
            transportHint.equals("WEBRTC_P2P", ignoreCase = true) ||
            _connection.value.isDirectLocalConnected
        ) {
            lastDirectVerifiedMark = nowMark
        }
        val directActive = hasRecentDirectTransport()
        _connection.update { cur ->
            val effectivePin = pin.ifBlank { cur.pin.ifBlank { "4829" } }
            val resolvedTransport = when {
                cur.isDirectLocalConnected || transportHint.equals("DIRECT", ignoreCase = true) -> "DIRECT"
                transportHint.equals("WEBRTC_P2P", ignoreCase = true) -> "WEBRTC_P2P"
                directActive -> cur.transportMode.takeIf { it == "DIRECT" || it == "WEBRTC_P2P" } ?: "DIRECT"
                transportHint.isNotBlank() -> transportHint
                else -> cur.transportMode
            }
            cur.copy(
                state = if (cur.state == "STREAMING") "STREAMING" else "CONNECTED",
                isCarVerified = true,
                pin = effectivePin,
                transportMode = resolvedTransport,
                message = "● 차량과 연결됨 (PIN $effectivePin)"
            )
        }
    }

    fun tickConnectionWatchdog() {
        val silenceMs = lastVerifiedCarPacketMark?.elapsedNow()?.inWholeMilliseconds ?: 999_999L
        val cur = _connection.value
        if (silenceMs > 6000L) {
            if (cur.isCarVerified || cur.state == "CONNECTED" || cur.state == "STREAMING") {
                _connection.update {
                    it.copy(
                        state = "IDLE",
                        isCarVerified = false,
                        isDirectLocalConnected = false,
                        message = "○ 연결 안 됨 (대기 중)"
                    )
                }
            } else if (cur.state == "CONNECTING") {
                val attemptAgeMs = connectAttemptMark?.elapsedNow()?.inWholeMilliseconds ?: 999_999L
                if (attemptAgeMs > 6000L) {
                    _connection.update {
                        it.copy(
                            state = "IDLE",
                            isCarVerified = false,
                            isDirectLocalConnected = false,
                            message = "○ 연결 안 됨 (대기 중)"
                        )
                    }
                }
            }
        }
    }

    fun markUserActionCooldown() {
        lastUserActionMark = kotlin.time.TimeSource.Monotonic.markNow()
        evaluateJs("window.PleosMicEngine && window.PleosMicEngine.markUserCommandCooldown && window.PleosMicEngine.markUserCommandCooldown();")
    }

    fun isInsideUserActionCooldown(): Boolean {
        val elapsedMs = lastUserActionMark?.elapsedNow()?.inWholeMilliseconds ?: 999_999L
        return elapsedMs < 1200L
    }

    fun onPhoneLocalIpDetected(ip: String) {
        val cleanIp = ip.trim()
        if (cleanIp.isBlank() || cleanIp == "127.0.0.1") return
        phoneLocalIp = cleanIp
        if (phoneCandidateIps.isEmpty()) {
            phoneCandidateIps = listOf(cleanIp)
        }
        _connection.update { it.copy(phoneHotspotIps = phoneCandidateIps) }
        val safeIp = cleanIp.replace("'", "")
        evaluateJs("window.PleosMicEngine && window.PleosMicEngine.setPhoneLocalIp && window.PleosMicEngine.setPhoneLocalIp('$safeIp');")
    }

    fun onPhoneLocalIpsDetected(ips: List<String>) {
        val cleaned = ips.map { it.trim() }
            .filter { it.isNotBlank() && it != "127.0.0.1" && it != "0.0.0.0" }
            .distinct()
        if (cleaned.isEmpty()) return
        phoneCandidateIps = cleaned
        phoneLocalIp = cleaned.first()
        _connection.update { it.copy(phoneHotspotIps = cleaned) }
        val safePrimary = phoneLocalIp.replace("'", "")
        val joinedCsv = cleaned.joinToString(",") { it.replace("'", "") }
        evaluateJs(
            "window.PleosMicEngine && window.PleosMicEngine.setPhoneLocalIp && window.PleosMicEngine.setPhoneLocalIp('$safePrimary');" +
                "window.PleosMicEngine && window.PleosMicEngine.setPhoneCandidateIps && window.PleosMicEngine.setPhoneCandidateIps('$joinedCsv');" +
                "window.PleosMicEngine && window.PleosMicEngine.broadcastCandidateIpsNow && window.PleosMicEngine.broadcastCandidateIpsNow();"
        )
    }

    fun onDirectLocalSocketStateChanged(connected: Boolean) {
        if (connected) {
            lastDirectVerifiedMark = kotlin.time.TimeSource.Monotonic.markNow()
        } else {
            lastDirectVerifiedMark = null
        }
        _connection.update {
            it.copy(
                isDirectLocalConnected = connected,
                transportMode = if (connected) "DIRECT" else it.transportMode
            )
        }
        evaluateJs("window.PleosMicEngine && window.PleosMicEngine.setDirectLocalConnected && window.PleosMicEngine.setDirectLocalConnected($connected);")
    }

    fun onDirectCarHeartbeatReceived() {
        lastDirectVerifiedMark = kotlin.time.TimeSource.Monotonic.markNow()
        onVerifiedCarPacketReceived(_connection.value.pin, "DIRECT")
        evaluateJs("window.PleosMicEngine && window.PleosMicEngine.onDirectCarPacketReceived && window.PleosMicEngine.onDirectCarPacketReceived();")
    }

    fun sendDirectSocketEventFromWebView(event: String, dataJson: String) {
        if (!_connection.value.isDirectLocalConnected) return
        val safeEvent = event.trim().replace("\"", "")
        if (safeEvent.isBlank()) return
        val cleanData = dataJson.ifBlank { "{}" }
        outboundSocketWriter?.invoke("""{"event":"$safeEvent","type":"$safeEvent","data":$cleanData}""")
    }

    fun onDirectPcmChunkFromWebView(sampleRate: Int, pcmJsonArray: String, seq: Long = -1L) {
        if (!_connection.value.isDirectLocalConnected) return
        val safePin = _connection.value.pin.ifBlank { "4829" }
        outboundSocketWriter?.invoke(
            """{"event":"audio-pcm-chunk","data":{"pin":"$safePin","sr":$sampleRate,"seq":$seq,"pcm":$pcmJsonArray}}"""
        )
    }

    fun sendDirectLatencyPing(seq: Long, clientSendTs: Long, lastNetMs: Int) {
        if (!_connection.value.isDirectLocalConnected) return
        val safePin = _connection.value.pin.ifBlank { "4829" }
        val dataJson = """{"type":"latency-ping","pin":"$safePin","seq":$seq,"clientSendTs":$clientSendTs,"lastNetLatencyMs":$lastNetMs}"""
        outboundSocketWriter?.invoke("""{"event":"latency-ping","type":"latency-ping","data":$dataJson}""")
    }

    fun onLatencyMetricsFromWebView(totalMs: Int, netMs: Int, carBufferMs: Int, transport: String) {
        val isRelayPong = transport.equals("RELAY", ignoreCase = true)
        if (isRelayPong && hasRecentDirectTransport()) {
            // Completely ignore slow overseas WSS relay latency-pong (~270ms) while Priority 1 (DIRECT) or Priority 2 (WEBRTC_P2P) is active!
            onVerifiedCarPacketReceived(_connection.value.pin, _connection.value.transportMode)
            return
        }
        val cleanNet = netMs.coerceIn(1, 500)
        val cleanBuf = carBufferMs.coerceIn(6, 250)
        val cleanTotal = totalMs.coerceIn(10, 999)
        val cleanTransport = when {
            transport.equals("WEBRTC_P2P", ignoreCase = true) -> "WEBRTC_P2P"
            transport.equals("DIRECT", ignoreCase = true) -> "DIRECT"
            else -> "RELAY"
        }
        onVerifiedCarPacketReceived(_connection.value.pin, cleanTransport)
        _connection.update {
            it.copy(
                netLatencyMs = cleanNet,
                carBufferMs = cleanBuf,
                totalVocalLatencyMs = cleanTotal,
                transportMode = cleanTransport
            )
        }
    }

    fun onDirectLatencyPongReceived(netMs: Int, carBufferMs: Int, transport: String = "DIRECT") {
        if (transport.equals("RELAY", ignoreCase = true) && hasRecentDirectTransport()) {
            onVerifiedCarPacketReceived(_connection.value.pin, _connection.value.transportMode)
            return
        }
        val cleanNet = netMs.coerceIn(1, 250)
        val cleanBuf = carBufferMs.coerceIn(6, 200)
        val totalMs = cleanNet + 11 + cleanBuf
        onLatencyMetricsFromWebView(totalMs, cleanNet, cleanBuf, transport)
        if (transport == "DIRECT") {
            evaluateJs(
                "window.PleosMicEngine && window.PleosMicEngine.onDirectLatencyPong && window.PleosMicEngine.onDirectLatencyPong($cleanNet, $cleanBuf);"
            )
        }
    }

    fun onMetricsFromWebView(rms: Float, pitchHz: Float, note: String) {
        val safeRms = rms.coerceIn(0f, 100f)
        val safePitch = pitchHz.coerceAtLeast(0f)
        val safeNote = note.ifBlank { "-" }
        val conn = _connection.value
        val actuallyConnected = conn.isCarVerified && (conn.state == "CONNECTED" || conn.state == "STREAMING")
        _metrics.value = VocalMetrics(
            rms = safeRms,
            pitchHz = safePitch,
            note = safeNote,
            isStreaming = actuallyConnected
        )

        val nowMark = kotlin.time.TimeSource.Monotonic.markNow()
        val dtSec = ((lastVocalTickMark?.elapsedNow()?.inWholeMilliseconds ?: 50L) / 1000.0).coerceIn(0.02, 0.20)
        lastVocalTickMark = nowMark
        if (_carState.value.isPlaying &&
            _carState.value.playlist.isNotEmpty() &&
            !isMicMutedState &&
            _carState.value.micVolume > 0 &&
            safeRms >= 8f
        ) {
            standaloneScoreAccumulator.recordTick(
                micLevelPercent = safeRms,
                pitchHz = safePitch,
                dtSeconds = dtSec
            )
        }
    }

    fun onConnectionStateFromWebView(state: String, message: String, pin: String) {
        val upper = state.trim().uppercase()
        val effectivePin = pin.ifBlank { _connection.value.pin.ifBlank { "4829" } }
        when (upper) {
            "CONNECTED", "STREAMING" -> {
                // Strictly forbid optimistic connection state unless a real packet from pleos-stage-car arrived within 6.0s
                if (!hasRecentVerifiedCarPacket(6000L)) {
                    return
                }
                _connection.update {
                    it.copy(
                        state = upper,
                        isCarVerified = true,
                        message = "● 차량과 연결됨 (PIN $effectivePin)",
                        pin = effectivePin
                    )
                }
            }
            "CONNECTING" -> {
                if (!hasRecentVerifiedCarPacket(6000L)) {
                    val attemptAgeMs = connectAttemptMark?.elapsedNow()?.inWholeMilliseconds ?: 999_999L
                    if (attemptAgeMs <= 6000L) {
                        _connection.update {
                            it.copy(
                                state = "CONNECTING",
                                isCarVerified = false,
                                message = "◐ 차량 연결 중...",
                                pin = effectivePin
                            )
                        }
                    } else {
                        _connection.update {
                            it.copy(
                                state = "IDLE",
                                isCarVerified = false,
                                isDirectLocalConnected = false,
                                message = "○ 연결 안 됨 (대기 중)",
                                pin = effectivePin
                            )
                        }
                    }
                }
            }
            "DISCONNECTED", "IDLE" -> {
                if (!hasRecentVerifiedCarPacket(6000L)) {
                    _connection.update {
                        it.copy(
                            state = "IDLE",
                            isCarVerified = false,
                            isDirectLocalConnected = false,
                            message = "○ 연결 안 됨 (대기 중)",
                            pin = effectivePin
                        )
                    }
                }
            }
            else -> {
                if (!hasRecentVerifiedCarPacket(6000L)) {
                    _connection.update {
                        it.copy(
                            state = upper,
                            isCarVerified = false,
                            message = message.ifBlank { "○ 연결 안 됨 (대기 중)" },
                            pin = effectivePin
                        )
                    }
                }
            }
        }
    }

    fun onCarEndpointDiscovered(wsUrl: String, pin: String) {
        val cleanUrl = wsUrl.trim()
        if (cleanUrl.isBlank()) return
        _connection.update { current ->
            val updatedMsg = if (current.state == "IDLE") {
                "차량 스마트 링크가 자동으로 감지되었습니다 (PIN $pin)"
            } else {
                current.message
            }
            current.copy(
                discoveredCarUrl = cleanUrl,
                pin = pin.ifBlank { current.pin },
                message = updatedMsg
            )
        }
        val safeUrl = cleanUrl.replace("'", "")
        val effectivePin = pin.ifBlank { _connection.value.pin.ifBlank { "4829" } }
        onVerifiedCarWs8080Callback?.invoke(cleanUrl, effectivePin)
        evaluateJs("window.PleosMicEngine && window.PleosMicEngine.setDiscoveredCarUrl('$safeUrl');")
    }

    fun onVerifiedCarWs8080FromWebView(wsUrl: String, pin: String) {
        val cleanUrl = wsUrl.trim()
        if (cleanUrl.isBlank() || cleanUrl.contains("127.0.0.1") || cleanUrl.contains("localhost")) return
        val effectivePin = pin.ifBlank { _connection.value.pin.ifBlank { "4829" } }
        onVerifiedCarWs8080Callback?.invoke(cleanUrl, effectivePin)
    }

    fun onCarWs8080ClosedFromWebView() {
        onCarWs8080ClosedCallback?.invoke()
    }

    fun dispatchNativePcmToWebViewFallback(sampleRate: Int, seq: Long, pcmJsonArray: String) {
        evaluateJs(
            "window.PleosMicEngine && window.PleosMicEngine.sendNativePcmChunk && " +
                "window.PleosMicEngine.sendNativePcmChunk($sampleRate, $seq, $pcmJsonArray);"
        )
    }

    fun dispatchNativeTelemetryToWebViewFallback(rms: Float, pitchHz: Float, note: String) {
        val safeNote = note.replace("'", "")
        evaluateJs(
            "window.PleosMicEngine && window.PleosMicEngine.sendNativeTelemetry && " +
                "window.PleosMicEngine.sendNativeTelemetry($rms, $pitchHz, '$safeNote');"
        )
    }

    fun onCarStateSyncFromWebView(
        songId: String,
        isPlaying: Boolean,
        keyShift: Int,
        echoLevel: Int,
        isParked: Boolean,
        score: Int,
        currentTrackId: String = songId,
        progressSec: Int = 0,
        durationSec: Int = 30,
        backingVolume: Int = 85,
        isDevMode: Boolean = isDebugBuildAllowed(),
        playlistEncoded: String = "",
        micVolume: Int = _carState.value.micVolume,
        guideVolume: Int = _carState.value.guideVolume,
        mrPlaybackRate: Float = _carState.value.mrPlaybackRate,
        syncOffsetMs: Int = _carState.value.syncOffsetMs,
        carHostId: String = "",
        stateRevision: Long = 0L
    ) {
        val incomingHostId = carHostId.trim()
        // Immediately mark paired/verified upon any incoming car packet (no carHostId lock-on or stateRevision drop!)
        onVerifiedCarPacketReceived(_connection.value.pin)

        // 1200ms Cooldown Lock: prevent periodic car-state-sync from overwriting immediate user button presses
        if (isInsideUserActionCooldown()) {
            return
        }

        val decodedTracks = decodeSyncedPlaylist(playlistEncoded)
        var didChangeTrack = false
        var seekJumpTargetSec: Int? = null
        _carState.update { prev ->
            val effectiveDev = isDebugBuildAllowed() && isDevMode
            val rawList = when {
                playlistEncoded == "__EMPTY__" -> emptyList()
                decodedTracks.isNotEmpty() -> decodedTracks
                else -> prev.playlist
            }
            val updatedList = if (effectiveDev) rawList else rawList.filterNot { it.isDevSample }
            val activeId = if (updatedList.isEmpty()) {
                ""
            } else {
                val candidate = currentTrackId.ifBlank { songId.ifBlank { prev.currentTrackId } }
                if (updatedList.any { it.id == candidate }) candidate else updatedList.first().id
            }
            if (activeId != prev.currentTrackId) {
                didChangeTrack = true
            }
            val matchedTrack = updatedList.firstOrNull { it.id == activeId }
            val incomingSec = progressSec.coerceAtLeast(0)
            val resolvedProgressSec = when {
                updatedList.isEmpty() -> 0
                !isPlaying -> incomingSec
                activeId != prev.currentTrackId -> incomingSec
                kotlin.math.abs(incomingSec - prev.progressSec) >= 2 -> incomingSec
                incomingSec == 0 && prev.progressSec == 1 -> prev.progressSec
                else -> maxOf(prev.progressSec, incomingSec)
            }
            if (!didChangeTrack && kotlin.math.abs(resolvedProgressSec - prev.progressSec) >= 3) {
                seekJumpTargetSec = resolvedProgressSec
            }
            val safeRate = ((kotlin.math.round(mrPlaybackRate.coerceIn(0.75f, 1.25f) * 20f)) / 20f).coerceIn(0.75f, 1.25f)
            val safeSyncOffset = ((syncOffsetMs.coerceIn(-500, 500) / 50) * 50).coerceIn(-500, 500)
            prev.copy(
                currentSongId = activeId,
                currentTrackId = activeId,
                isPlaying = isPlaying && updatedList.isNotEmpty(),
                progressSec = resolvedProgressSec,
                durationSec = if (updatedList.isEmpty()) 0 else (matchedTrack?.durationSec ?: durationSec.coerceAtLeast(15)),
                backingVolume = backingVolume.coerceIn(0, 100),
                micVolume = micVolume.coerceIn(0, 100),
                guideVolume = guideVolume.coerceIn(0, 100),
                keyShift = keyShift.coerceIn(-6, 6),
                echoLevel = echoLevel.coerceIn(0, 100),
                mrPlaybackRate = safeRate,
                syncOffsetMs = safeSyncOffset,
                isParked = isParked,
                score = score,
                isDevMode = if (isDebugBuildAllowed()) isDevMode else false,
                carHostId = if (incomingHostId.isNotBlank()) incomingHostId else prev.carHostId,
                stateRevision = if (stateRevision > 0L) stateRevision else prev.stateRevision,
                playlist = updatedList
            )
        }
        if (didChangeTrack) {
            standaloneScoreAccumulator.reset()
            evaluateYtJs("window.PleosYtMr && window.PleosYtMr.seekTo && window.PleosYtMr.seekTo(0);")
        }
        syncLocalBackingPlayback()
        seekJumpTargetSec?.let { jumpSec ->
            evaluateYtJs("window.PleosYtMr && window.PleosYtMr.seekTo && window.PleosYtMr.seekTo($jumpSec);")
        }
    }

    fun setLocalBackingAudioEnabled(enabled: Boolean) {
        _localBackingAudioEnabled.value = enabled
        syncLocalBackingPlayback()
    }

    fun syncLocalBackingPlayback() {
        val st = _carState.value
        val activeTrack = st.playlist.firstOrNull { it.id == st.currentTrackId } ?: st.playlist.firstOrNull()
        val safeTrackId = (activeTrack?.id ?: st.currentTrackId).replace("'", "").trim()
        val safeSongId = (activeTrack?.songId ?: "").replace("'", "").trim()
        val safeVideoId = (activeTrack?.videoId ?: "").replace("'", "").trim()
        val bpm = activeTrack?.bpm ?: 120
        val shouldPlay = st.isPlaying && st.playlist.isNotEmpty() && _localBackingAudioEnabled.value
        val isBuiltInSynth = false
        val synthTrackKey = if (safeSongId.isNotBlank()) safeSongId else safeTrackId
        evaluateJs(
            "window.PleosMicEngine && window.PleosMicEngine.syncLocalBackingAudio && " +
                "window.PleosMicEngine.syncLocalBackingAudio(${shouldPlay && isBuiltInSynth}, '$synthTrackKey', '$safeVideoId', $bpm, ${st.backingVolume}, ${st.keyShift}, ${st.echoLevel}, ${st.micVolume}, ${st.guideVolume}, ${st.mrPlaybackRate}, ${st.syncOffsetMs});"
        )
        val shouldPlayYt = shouldPlay && safeVideoId.isNotBlank()
        evaluateYtJs(
            "window.PleosYtMr && window.PleosYtMr.syncState($shouldPlayYt, '$safeVideoId', ${st.backingVolume}, $bpm, ${st.mrPlaybackRate}, ${st.syncOffsetMs});"
        )
    }

    // Optimistic local state updates + instant WebSocket command dispatch for 1:1 responsiveness
    fun playTrackOnStage(track: UnifiedMicTrack) {
        markUserActionCooldown()
        standaloneScoreAccumulator.reset()
        pendingStandaloneNextTrack = false
        _activeScoreResult.value = null
        _carState.update { prev ->
            val exists = prev.playlist.any { it.id == track.id }
            val newList = if (exists) prev.playlist else listOf(track) + prev.playlist
            prev.copy(
                currentSongId = track.id,
                currentTrackId = track.id,
                isPlaying = true,
                progressSec = 0,
                durationSec = track.durationSec,
                playlist = newList
            )
        }
        syncLocalBackingPlayback()
        evaluateYtJs("window.PleosYtMr && window.PleosYtMr.seekTo && window.PleosYtMr.seekTo(0);")
        sendRemoteCommand(
            type = "PLAY_TRACK",
            trackId = track.id,
            songId = track.songId.ifBlank { track.id },
            videoId = track.videoId,
            title = track.title,
            artist = track.artist,
            genre = track.genre,
            bpm = track.bpm,
            durationSec = track.durationSec,
            durationLabel = track.durationLabel
        )
    }

    fun togglePlayPauseOnStage() {
        if (_carState.value.playlist.isEmpty()) return
        markUserActionCooldown()
        val willPlay = !_carState.value.isPlaying
        _carState.update { it.copy(isPlaying = willPlay) }
        syncLocalBackingPlayback()
        sendRemoteCommand(type = "TOGGLE_PLAY_PAUSE")
    }

    fun stopStagePlayback() {
        markUserActionCooldown()
        val st = _carState.value
        val wasPlaying = st.isPlaying && st.playlist.isNotEmpty()
        val hasSungAtLeast3Sec = standaloneScoreAccumulator.activeVoiceSeconds >= 3.0
        _carState.update { it.copy(isPlaying = false, progressSec = 0) }
        syncLocalBackingPlayback()
        if (hasRecentVerifiedCarPacket(6000L)) {
            standaloneScoreAccumulator.reset()
            sendRemoteCommand(type = "STOP_SONG")
        } else {
            sendRemoteCommand(type = "STOP_SONG")
            if (wasPlaying && st.isScoreModeEnabled && hasSungAtLeast3Sec) {
                triggerStandaloneScoreReveal(willAdvanceNext = false)
            } else {
                standaloneScoreAccumulator.reset()
            }
        }
    }

    private fun advanceStandaloneNextTrackImmediate() {
        val st = _carState.value
        val pool = st.playlist
        if (pool.isEmpty()) return
        val curIdx = pool.indexOfFirst { it.id == st.currentTrackId }.let { if (it < 0) 0 else it }
        val nextTrack = pool[(curIdx + 1) % pool.size]
        standaloneScoreAccumulator.reset()
        _carState.update {
            it.copy(
                currentSongId = nextTrack.id,
                currentTrackId = nextTrack.id,
                isPlaying = true,
                progressSec = 0,
                durationSec = nextTrack.durationSec
            )
        }
        syncLocalBackingPlayback()
        evaluateYtJs("window.PleosYtMr && window.PleosYtMr.seekTo && window.PleosYtMr.seekTo(0);")
    }

    private fun triggerStandaloneScoreReveal(willAdvanceNext: Boolean) {
        val st = _carState.value
        if (!st.isScoreModeEnabled) return
        val activeTrack = st.playlist.firstOrNull { it.id == st.currentTrackId } ?: st.playlist.firstOrNull()
        val computed = MicKaraokeScoreCalculator.calculateKaraokeScore(
            acc = standaloneScoreAccumulator,
            trackTitle = activeTrack?.title.orEmpty(),
            willAdvanceNext = willAdvanceNext
        )
        standaloneScoreAccumulator.reset()
        pendingStandaloneNextTrack = willAdvanceNext
        onKaraokeScoreResultReceived(computed)
    }

    fun skipAdjacentTrack(step: Int) {
        markUserActionCooldown()
        val st = _carState.value
        val pool = st.playlist
        val hasSungAtLeast3Sec = standaloneScoreAccumulator.activeVoiceSeconds >= 3.0
        if (step > 0 && st.isPlaying && st.isScoreModeEnabled && pool.isNotEmpty() && hasSungAtLeast3Sec) {
            // 3초 이상 실제 가창 후 [다음 곡]으로 마무리할 때만 5초 점수 발표 후 다음 곡으로 전환
            _carState.update { it.copy(isPlaying = false) }
            syncLocalBackingPlayback()
            if (hasRecentVerifiedCarPacket(6000L)) {
                standaloneScoreAccumulator.reset()
                sendRemoteCommand(type = "NEXT_TRACK")
            } else {
                sendRemoteCommand(type = "NEXT_TRACK")
                triggerStandaloneScoreReveal(willAdvanceNext = true)
            }
            return
        }
        if (pool.isNotEmpty()) {
            val curIdx = pool.indexOfFirst { it.id == st.currentTrackId }.let { if (it < 0) 0 else it }
            val nextTrack = pool[(curIdx + step + pool.size) % pool.size]
            standaloneScoreAccumulator.reset()
            _carState.update {
                it.copy(
                    currentSongId = nextTrack.id,
                    currentTrackId = nextTrack.id,
                    isPlaying = true,
                    progressSec = 0,
                    durationSec = nextTrack.durationSec
                )
            }
        }
        syncLocalBackingPlayback()
        sendRemoteCommand(type = if (step >= 0) "NEXT_TRACK" else "PREV_TRACK")
    }

    fun seekToPositionSec(targetSec: Int) {
        val st = _carState.value
        if (st.playlist.isEmpty()) return
        val maxDur = st.durationSec.coerceAtLeast(1)
        val clamped = targetSec.coerceIn(0, maxDur)
        markUserActionCooldown()
        _carState.update { it.copy(progressSec = clamped) }
        evaluateYtJs("window.PleosYtMr && window.PleosYtMr.seekTo && window.PleosYtMr.seekTo($clamped);")
        sendRemoteCommand(
            type = "SEEK_TO",
            action = "seek-to",
            positionSec = clamped
        )
        if (!hasRecentVerifiedCarPacket(6000L) && st.isPlaying && clamped >= maxDur) {
            if (st.isScoreModeEnabled) {
                _carState.update { it.copy(isPlaying = false, progressSec = maxDur) }
                syncLocalBackingPlayback()
                triggerStandaloneScoreReveal(willAdvanceNext = true)
            } else {
                advanceStandaloneNextTrackImmediate()
            }
        }
    }

    fun addTrackToStagePlaylist(rawInput: String, playImmediately: Boolean = false) {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return
        markUserActionCooldown()

        // Extract YouTube ID if URL/ID or build search keyword track locally for 0ms UI update
        val vMatch = Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(trimmed)?.groupValues?.getOrNull(1)
            ?: Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(trimmed)?.groupValues?.getOrNull(1)
            ?: if (Regex("^[a-zA-Z0-9_-]{11}$").matches(trimmed)) trimmed else ""

        val vid = vMatch.ifBlank { "K4DyBUG242c" }
        val newTrack = UnifiedMicTrack(
            id = "yt_${vid}_${(1000..9999).random()}",
            title = if (vMatch.isNotBlank()) "YouTube MR ($vMatch)" else trimmed,
            artist = if (vMatch.isNotBlank()) "YouTube Live Stage" else "YouTube 노래방 MR",
            genre = "",
            bpm = 124,
            durationSec = 210,
            durationLabel = "03:30",
            videoId = vid,
            songId = "",
            isDevSample = false
        )
        _carState.update { prev ->
            val wasEmpty = prev.playlist.isEmpty()
            prev.copy(
                playlist = listOf(newTrack) + prev.playlist,
                currentSongId = if (playImmediately || wasEmpty) newTrack.id else prev.currentSongId,
                currentTrackId = if (playImmediately || wasEmpty) newTrack.id else prev.currentTrackId,
                durationSec = if (playImmediately || wasEmpty) newTrack.durationSec else prev.durationSec,
                isPlaying = if (playImmediately) true else prev.isPlaying,
                progressSec = if (playImmediately) 0 else prev.progressSec
            )
        }
        syncLocalBackingPlayback()
        sendRemoteCommand(
            type = "ADD_TRACK",
            youtubeUrl = trimmed,
            title = newTrack.title,
            playImmediately = playImmediately
        )
    }

    fun removeTrackFromStagePlaylist(trackId: String) {
        markUserActionCooldown()
        _carState.update { prev ->
            if (prev.playlist.isEmpty()) prev
            else {
                val filtered = prev.playlist.filterNot { it.id == trackId }
                if (filtered.isEmpty()) {
                    prev.copy(
                        playlist = emptyList(),
                        currentSongId = "",
                        currentTrackId = "",
                        isPlaying = false,
                        progressSec = 0,
                        durationSec = 0
                    )
                } else {
                    val nextActive = if (prev.currentTrackId == trackId) filtered.first().id else prev.currentTrackId
                    prev.copy(playlist = filtered, currentTrackId = nextActive, currentSongId = nextActive)
                }
            }
        }
        syncLocalBackingPlayback()
        sendRemoteCommand(type = "REMOVE_TRACK", trackId = trackId)
    }

    fun moveTrackInStagePlaylist(fromIndex: Int, toIndex: Int) {
        markUserActionCooldown()
        _carState.update { prev ->
            val list = prev.playlist.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices && fromIndex != toIndex) {
                val moved = list.removeAt(fromIndex)
                list.add(toIndex, moved)
                prev.copy(playlist = list)
            } else {
                prev
            }
        }
        sendRemoteCommand(type = "MOVE_TRACK", fromIndex = fromIndex, toIndex = toIndex)
    }

    fun updateBackingVolume(newVol: Int) {
        markUserActionCooldown()
        val clamped = newVol.coerceIn(0, 100)
        _carState.update { it.copy(backingVolume = clamped) }
        evaluateYtJs("window.PleosYtMr && window.PleosYtMr.setVolume && window.PleosYtMr.setVolume($clamped);")
        syncLocalBackingPlayback()
        sendRemoteCommand(type = "CHANGE_BACKING_VOLUME", youtubeVolume = clamped)
    }

    fun updateMicVolume(newVol: Int) {
        markUserActionCooldown()
        val clamped = newVol.coerceIn(0, 100)
        _carState.update { it.copy(micVolume = clamped) }
        syncLocalBackingPlayback()
        sendRemoteCommand(type = "CHANGE_MIC_VOLUME", micVolume = clamped)
    }

    fun updateGuideVolume(newVol: Int) {
        markUserActionCooldown()
        val clamped = newVol.coerceIn(0, 100)
        _carState.update { it.copy(guideVolume = clamped) }
        syncLocalBackingPlayback()
        sendRemoteCommand(type = "CHANGE_GUIDE_VOLUME", guideVolume = clamped)
    }

    fun updateEchoLevel(newEcho: Int) {
        markUserActionCooldown()
        val clamped = newEcho.coerceIn(0, 100)
        _carState.update { it.copy(echoLevel = clamped) }
        syncLocalBackingPlayback()
        sendRemoteCommand(type = "CHANGE_ECHO", echoLevel = clamped)
    }

    fun updateKeyShift(newKey: Int) {
        markUserActionCooldown()
        val clamped = newKey.coerceIn(-6, 6)
        _carState.update { it.copy(keyShift = clamped) }
        syncLocalBackingPlayback()
        sendRemoteCommand(type = "CHANGE_KEY", keyShift = clamped)
    }

    fun updatePlaybackRate(newRate: Float) {
        markUserActionCooldown()
        val stepped = ((kotlin.math.round(newRate.coerceIn(0.75f, 1.25f) * 20f)) / 20f).coerceIn(0.75f, 1.25f)
        _carState.update { it.copy(mrPlaybackRate = stepped) }
        evaluateYtJs("window.PleosYtMr && window.PleosYtMr.setPlaybackRate && window.PleosYtMr.setPlaybackRate($stepped);")
        syncLocalBackingPlayback()
        sendRemoteCommand(type = "CHANGE_PLAYBACK_RATE", mrPlaybackRate = stepped)
    }

    fun updateSyncOffsetMs(newOffsetMs: Int) {
        markUserActionCooldown()
        val prevOffset = _carState.value.syncOffsetMs
        val stepped = ((newOffsetMs.coerceIn(-500, 500) / 50) * 50).coerceIn(-500, 500)
        val deltaMs = stepped - prevOffset
        _carState.update { it.copy(syncOffsetMs = stepped) }
        if (deltaMs != 0) {
            evaluateYtJs("window.PleosYtMr && window.PleosYtMr.nudgeSync && window.PleosYtMr.nudgeSync(${deltaMs / 1000.0});")
        }
        syncLocalBackingPlayback()
        sendRemoteCommand(type = "CHANGE_SYNC_OFFSET", syncOffsetMs = stepped, syncDeltaMs = deltaMs)
    }

    fun resetAudioMixerDefaults() {
        markUserActionCooldown()
        val resetSyncDelta = -_carState.value.syncOffsetMs
        _carState.update {
            it.copy(
                backingVolume = 85,
                micVolume = 85,
                guideVolume = 75,
                echoLevel = 80,
                mrPlaybackRate = 1.00f,
                syncOffsetMs = 0,
                keyShift = 0
            )
        }
        evaluateYtJs("window.PleosYtMr && window.PleosYtMr.setVolume && window.PleosYtMr.setVolume(85);")
        evaluateYtJs("window.PleosYtMr && window.PleosYtMr.setPlaybackRate && window.PleosYtMr.setPlaybackRate(1.0);")
        if (resetSyncDelta != 0) {
            evaluateYtJs("window.PleosYtMr && window.PleosYtMr.nudgeSync && window.PleosYtMr.nudgeSync(${resetSyncDelta / 1000.0});")
        }
        syncLocalBackingPlayback()
        sendRemoteCommand(type = "RESET_AUDIO_MIXER", syncDeltaMs = resetSyncDelta)
    }

    fun updateDevMode(enabled: Boolean) {
        if (!isDebugBuildAllowed()) return
        markUserActionCooldown()
        _carState.update { prev ->
            if (enabled) {
                val updatedList = if (prev.playlist.none { it.isDevSample }) {
                    prev.playlist + DEFAULT_UNIFIED_MIC_PLAYLIST
                } else {
                    prev.playlist
                }
                val activeTrack = updatedList.firstOrNull { it.id == prev.currentTrackId } ?: updatedList.first()
                prev.copy(
                    isDevMode = true,
                    durationSec = if (prev.durationSec > 0 && prev.currentTrackId.isNotBlank()) prev.durationSec else activeTrack.durationSec,
                    currentSongId = prev.currentSongId.ifBlank { activeTrack.id },
                    currentTrackId = prev.currentTrackId.ifBlank { activeTrack.id },
                    playlist = updatedList
                )
            } else {
                val filtered = prev.playlist.filterNot { it.isDevSample }
                val wasPlayingDevTrack = prev.isPlaying && prev.playlist.firstOrNull { it.id == prev.currentTrackId }?.isDevSample == true
                val nextActiveTrack = filtered.firstOrNull { it.id == prev.currentTrackId } ?: filtered.firstOrNull()
                prev.copy(
                    isDevMode = false,
                    isPlaying = if (wasPlayingDevTrack || filtered.isEmpty()) false else prev.isPlaying,
                    progressSec = if (wasPlayingDevTrack || filtered.isEmpty()) 0 else prev.progressSec,
                    durationSec = nextActiveTrack?.durationSec ?: 0,
                    currentSongId = nextActiveTrack?.id ?: "",
                    currentTrackId = nextActiveTrack?.id ?: "",
                    playlist = filtered
                )
            }
        }
        syncLocalBackingPlayback()
        sendRemoteCommand(type = "SET_DEV_MODE", isDevMode = enabled)
    }

    fun tickLocalStandaloneProgress() {
        if (_carState.value.isPlaying && _carState.value.playlist.isNotEmpty()) {
            val stNow = _carState.value
            val nextSec = stNow.progressSec + 1
            if (nextSec >= stNow.durationSec.coerceAtLeast(15)) {
                if (!hasRecentVerifiedCarPacket(6000L) && stNow.isScoreModeEnabled) {
                    _carState.update { it.copy(isPlaying = false, progressSec = it.durationSec.coerceAtLeast(15)) }
                    syncLocalBackingPlayback()
                    triggerStandaloneScoreReveal(willAdvanceNext = true)
                    return
                }
                if (hasRecentVerifiedCarPacket(6000L)) {
                    // Paired with Car: Car controls track completion & 5s score reveal
                    _carState.update { it.copy(progressSec = it.durationSec.coerceAtLeast(15)) }
                    return
                }
                advanceStandaloneNextTrackImmediate()
            } else {
                _carState.update { it.copy(progressSec = nextSec) }
            }
        }
    }

    private fun isInsideScoreModeCooldown(): Boolean {
        val elapsedMs = lastScoreModeActionMark?.elapsedNow()?.inWholeMilliseconds ?: 999_999L
        return elapsedMs < 1200L
    }

    fun onScoreModeSyncedFromCar(enabled: Boolean) {
        onVerifiedCarPacketReceived(_connection.value.pin)
        if (isInsideScoreModeCooldown()) return
        _carState.update { it.copy(isScoreModeEnabled = enabled) }
        if (!enabled) {
            pendingStandaloneNextTrack = false
            _activeScoreResult.value = null
        }
    }

    fun toggleScoreMode() {
        setScoreModeEnabled(!_carState.value.isScoreModeEnabled)
    }

    fun setScoreModeEnabled(enabled: Boolean) {
        lastScoreModeActionMark = kotlin.time.TimeSource.Monotonic.markNow()
        markUserActionCooldown()
        _carState.update { it.copy(isScoreModeEnabled = enabled) }
        if (!enabled) {
            pendingStandaloneNextTrack = false
            _activeScoreResult.value = null
        }
        sendRemoteCommand(
            type = "toggle-score-mode",
            action = "toggle-score-mode",
            scoreModeEnabled = enabled
        )
    }

    fun onKaraokeScoreResultReceived(result: KaraokeScoreResultState) {
        if (!_carState.value.isScoreModeEnabled) return
        val cleanId = result.resultId.trim()
        if (cleanId.isNotEmpty()) {
            if (seenScoreResultIds.contains(cleanId)) return
            seenScoreResultIds.addLast(cleanId)
            if (seenScoreResultIds.size > 32) {
                seenScoreResultIds.removeFirst()
            }
        }
        _carState.update { it.copy(score = result.score.coerceIn(75, 100), isPlaying = false) }
        syncLocalBackingPlayback()
        _activeScoreResult.value = result
        // 발표 즉시 환호 효과음 재생 (박수 효과음은 App.kt LaunchedEffect에서 180ms 뒤 레이어링 재생)
        playLocalStageSfx("CHEER", 0.88f)
    }

    fun onKaraokeScoreDismissedFromCar(resultId: String) {
        val current = _activeScoreResult.value ?: return
        if (resultId.isBlank() || current.resultId == resultId) {
            pendingStandaloneNextTrack = false
            _activeScoreResult.value = null
        }
    }

    fun dismissActiveScoreOverlay(notifyCar: Boolean = true) {
        val current = _activeScoreResult.value
        val shouldAdvanceLocal = pendingStandaloneNextTrack
        pendingStandaloneNextTrack = false
        _activeScoreResult.value = null
        if (notifyCar && current != null && hasRecentVerifiedCarPacket(6000L)) {
            sendRemoteCommand(
                type = "DISMISS_SCORE_OVERLAY",
                action = "dismiss-score-overlay"
            )
        }
        if (shouldAdvanceLocal && !hasRecentVerifiedCarPacket(6000L)) {
            advanceStandaloneNextTrackImmediate()
        }
    }

    private fun normalizeCarStageWsUrl(rawInput: String): String {
        var u = rawInput.trim()
        if (u.isBlank()) return "ws://10.0.2.2:8080/ws"
        if (u.startsWith("http://", ignoreCase = true)) {
            u = "ws://" + u.substring(7)
        } else if (u.startsWith("https://", ignoreCase = true)) {
            u = "wss://" + u.substring(8)
        } else if (!u.startsWith("ws://", ignoreCase = true) && !u.startsWith("wss://", ignoreCase = true)) {
            u = "ws://$u"
        }
        u = u.trimEnd('/')
        val scheme = if (u.startsWith("wss://", ignoreCase = true)) "wss://" else "ws://"
        val afterScheme = u.substring(scheme.length)
        val hostPort = afterScheme.substringBefore("/")
        val pathPart = afterScheme.substringAfter("/", "")
        val normalizedHostPort = if (!hostPort.contains(":") && scheme == "ws://") {
            "$hostPort:8080"
        } else {
            hostPort.replace(":3000", ":8080")
        }
        val normalizedPath = when {
            pathPart.isBlank() -> "ws"
            pathPart.endsWith("ws") -> pathPart
            else -> "$pathPart/ws"
        }
        return "$scheme$normalizedHostPort/$normalizedPath"
    }

    fun connectToCarStage(serverUrl: String, pin: String, deviceName: String = "Pleos Smart Mic") {
        val cleanUrl = normalizeCarStageWsUrl(serverUrl)
        val cleanPin = pin.trim().ifBlank { "4829" }
        connectAttemptMark = kotlin.time.TimeSource.Monotonic.markNow()
        val verifiedNow = hasRecentVerifiedCarPacket(6000L)
        _connection.update {
            if (verifiedNow) {
                it.copy(
                    pin = cleanPin,
                    serverUrl = cleanUrl
                )
            } else {
                it.copy(
                    state = "CONNECTING",
                    isCarVerified = false,
                    message = "◐ 차량 연결 중...",
                    pin = cleanPin,
                    serverUrl = cleanUrl
                )
            }
        }
        if (!cleanUrl.contains("10.0.2.2") && !cleanUrl.contains("127.0.0.1") && !cleanUrl.contains("localhost")) {
            onVerifiedCarWs8080Callback?.invoke(cleanUrl, cleanPin)
        }
        val safeUrl = cleanUrl.replace("'", "")
        val safePin = cleanPin.replace("'", "")
        val safeName = deviceName.replace("'", "")
        evaluateJs("window.PleosMicEngine && window.PleosMicEngine.connectToRoom('$safeUrl', '$safePin', '$safeName');")
    }

    fun onGuideVocalSyncedFromWebView(enabled: Boolean) {
        _carState.update {
            it.copy(
                guideVocalEnabled = enabled,
                guidePitchHz = if (enabled) it.guidePitchHz else 0f,
                guideNote = if (enabled) it.guideNote else "-",
                guideVoiceLevelPercent = if (enabled) it.guideVoiceLevelPercent else 0f
            )
        }
    }

    fun onGuideVocalTelemetrySyncedFromWebView(
        enabled: Boolean,
        pitchHz: Float,
        note: String,
        levelPercent: Float
    ) {
        _carState.update {
            it.copy(
                guideVocalEnabled = enabled,
                guidePitchHz = if (enabled) pitchHz.coerceAtLeast(0f) else 0f,
                guideNote = if (enabled && note.isNotBlank()) note else "-",
                guideVoiceLevelPercent = if (enabled) levelPercent.coerceIn(0f, 100f) else 0f
            )
        }
    }

    fun setMicMuted(muted: Boolean) {
        isMicMutedState = muted
        if (muted) {
            onMetricsFromWebView(0f, 0f, "-")
        }
        evaluateJs("window.PleosMicEngine && window.PleosMicEngine.setMuted($muted);")
    }

    fun setDemoVocalWave(enabled: Boolean) {
        markUserActionCooldown()
        _carState.update { it.copy(guideVocalEnabled = enabled) }
        evaluateJs("window.PleosMicEngine && window.PleosMicEngine.setDemoVocal($enabled);")
        sendRemoteCommand(type = "SET_GUIDE_VOCAL", guideVocalEnabled = enabled)
    }

    fun sendRemoteCommand(
        type: String,
        songId: String? = null,
        trackId: String? = null,
        videoId: String? = null,
        keyShift: Int? = null,
        echoLevel: Int? = null,
        sfx: String? = null,
        youtubeUrl: String? = null,
        title: String? = null,
        artist: String? = null,
        genre: String? = null,
        bpm: Int? = null,
        durationSec: Int? = null,
        durationLabel: String? = null,
        youtubeVolume: Int? = null,
        micVolume: Int? = null,
        guideVolume: Int? = null,
        mrPlaybackRate: Float? = null,
        syncOffsetMs: Int? = null,
        syncDeltaMs: Int? = null,
        fromIndex: Int? = null,
        toIndex: Int? = null,
        playImmediately: Boolean? = null,
        isDevMode: Boolean? = null,
        guideVocalEnabled: Boolean? = null,
        action: String? = null,
        scoreModeEnabled: Boolean? = null,
        positionSec: Int? = null
    ) {
        val parts = mutableListOf("\"type\":\"$type\"")
        if (action != null) parts.add("\"action\":\"${action.replace("\"", "")}\"")
        if (songId != null) parts.add("\"songId\":\"${songId.replace("\"", "")}\"")
        if (trackId != null) parts.add("\"trackId\":\"${trackId.replace("\"", "")}\"")
        if (videoId != null) parts.add("\"videoId\":\"${videoId.replace("\"", "")}\"")
        if (keyShift != null) parts.add("\"keyShift\":$keyShift")
        if (echoLevel != null) {
            parts.add("\"echoLevel\":$echoLevel")
            parts.add("\"reverbLevel\":$echoLevel")
        }
        if (sfx != null) parts.add("\"sfx\":\"$sfx\"")
        if (youtubeUrl != null) parts.add("\"youtubeUrl\":\"${youtubeUrl.replace("\"", "")}\"")
        if (title != null) parts.add("\"title\":\"${title.replace("\"", "")}\"")
        if (artist != null) parts.add("\"artist\":\"${artist.replace("\"", "")}\"")
        if (genre != null) parts.add("\"genre\":\"${genre.replace("\"", "")}\"")
        if (bpm != null) parts.add("\"bpm\":$bpm")
        if (durationSec != null) parts.add("\"durationSec\":$durationSec")
        if (durationLabel != null) parts.add("\"durationLabel\":\"${durationLabel.replace("\"", "")}\"")
        if (youtubeVolume != null) {
            parts.add("\"youtubeVolume\":$youtubeVolume")
            parts.add("\"backingVolume\":$youtubeVolume")
            parts.add("\"mrVolume\":$youtubeVolume")
            parts.add("\"volume\":$youtubeVolume")
        }
        if (micVolume != null) parts.add("\"micVolume\":$micVolume")
        if (guideVolume != null) parts.add("\"guideVolume\":$guideVolume")
        if (mrPlaybackRate != null) {
            parts.add("\"mrPlaybackRate\":$mrPlaybackRate")
            parts.add("\"playbackRate\":$mrPlaybackRate")
        }
        if (syncOffsetMs != null) parts.add("\"syncOffsetMs\":$syncOffsetMs")
        if (syncDeltaMs != null) parts.add("\"syncDeltaMs\":$syncDeltaMs")
        if (fromIndex != null) parts.add("\"fromIndex\":$fromIndex")
        if (toIndex != null) parts.add("\"toIndex\":$toIndex")
        if (playImmediately != null) parts.add("\"playImmediately\":$playImmediately")
        if (isDevMode != null) parts.add("\"isDevMode\":$isDevMode")
        if (guideVocalEnabled != null) {
            parts.add("\"guideVocalEnabled\":$guideVocalEnabled")
            parts.add("\"demoVocalWave\":$guideVocalEnabled")
        }
        if (scoreModeEnabled != null) {
            parts.add("\"enabled\":$scoreModeEnabled")
            parts.add("\"scoreEnabled\":$scoreModeEnabled")
        }
        if (positionSec != null) {
            parts.add("\"positionSec\":$positionSec")
            parts.add("\"seekSec\":$positionSec")
            parts.add("\"progressSec\":$positionSec")
        }
        val json = "{${parts.joinToString(",")}}"
        val escaped = json.replace("\\", "\\\\").replace("'", "\\'")
        evaluateJs("window.PleosMicEngine && window.PleosMicEngine.sendRemoteCommand('$escaped');")
    }

    private fun evaluateJs(script: String) {
        jsEvaluator?.invoke(script)
    }

    private fun evaluateYtJs(script: String) {
        ytJsEvaluator?.invoke(script)
    }
}

@Composable
expect fun HeadlessAudioWebViewBridge(
    controller: AudioBridgeController,
    modifier: Modifier = Modifier
)

@Composable
expect fun YouTubeThumbnailBox(
    videoId: String,
    durationLabel: String = "",
    modifier: Modifier = Modifier
)

expect fun openYouTubeKaraokeAppSearch(query: String = "노래방 MR KY TJ")

expect fun readClipboardTextForPlaylist(): String?

expect fun warmUpLocalStageSfx()

expect fun playLocalStageSfx(sfxType: String, volumeScale: Float = 0.85f)

expect fun setKeepScreenOnEnabled(enabled: Boolean)

/**
 * Fallback/iOS inline HTML matching `assets/mic-engine.html` (Dual-Channel Local WS + Zero-Config WSS Relay).
 */
fun buildHeadlessMicEngineHtml(): String = """
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
</head>
<body style="margin:0;padding:0;background:transparent;overflow:hidden;">
<script>
(function() {
  const ICE_SERVERS = [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' }
  ];
  const RELAY_BROKERS = [
    'wss://broker.emqx.io:8084/mqtt',
    'wss://broker.hivemq.com:8884/mqtt'
  ];
  const NOTE_STRINGS = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];

  let ws = null;
  let isMuted = false;
  let demoVocalActive = false;
  let demoPhase = 0;
  let currentPin = '4829';
  let currentDeviceName = 'Pleos Smart Mic';
  let discoveredCarWsUrl = '';
  let phoneLocalIp = '';
  let phoneCandidateIps = [];
  let isDirectLocalSocketActive = false;
  let rafId = null;
  let lastSocketTelemetryTs = 0;
  let isCarPaired = false;
  let lastCarPacketTs = 0;
  let lastUserCommandTs = 0;

  const myClientId = 'mic_' + Math.random().toString(36).substring(2, 10);
  const relayClients = new Map();
  const seenMsgIds = new Set();
  const seenMsgQueue = [];

  function rememberMsgId(rid) {
    if (!rid) return false;
    if (seenMsgIds.has(rid)) return true;
    seenMsgIds.add(rid);
    seenMsgQueue.push(rid);
    if (seenMsgQueue.length > 300) {
      const oldest = seenMsgQueue.shift();
      seenMsgIds.delete(oldest);
    }
    return false;
  }

  function isRelayConnected() {
    let ok = false;
    relayClients.forEach((entry) => {
      if (entry && entry.ready && entry.socket && entry.socket.readyState === WebSocket.OPEN) {
        ok = true;
      }
    });
    return ok;
  }

  function isAnyTransportOpen() {
    return (ws && ws.readyState === WebSocket.OPEN) || isRelayConnected();
  }

  function strToUtf8Bytes(str) {
    if (typeof TextEncoder !== 'undefined') return new TextEncoder().encode(str);
    const out = [];
    for (let i = 0; i < str.length; i++) {
      let c = str.charCodeAt(i);
      if (c < 0x80) out.push(c);
      else if (c < 0x800) out.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f));
      else out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f));
    }
    return new Uint8Array(out);
  }

  function utf8BytesToStr(bytes) {
    if (typeof TextDecoder !== 'undefined') return new TextDecoder().decode(bytes);
    let out = '';
    for (let i = 0; i < bytes.length; i++) out += String.fromCharCode(bytes[i]);
    try { return decodeURIComponent(escape(out)); } catch (e) { return out; }
  }

  function encodeMqttRemainingLength(len) {
    const out = [];
    do {
      let digit = len % 128;
      len = Math.floor(len / 128);
      if (len > 0) digit = digit | 0x80;
      out.push(digit);
    } while (len > 0);
    return out;
  }

  function buildMqttConnectPacket(clientId) {
    const cidBytes = strToUtf8Bytes(clientId);
    const varHeader = [0x00, 0x04, 0x4D, 0x51, 0x54, 0x54, 0x04, 0x02, 0x00, 0x1E];
    const remLen = varHeader.length + 2 + cidBytes.length;
    const remBytes = encodeMqttRemainingLength(remLen);
    const pkt = new Uint8Array(1 + remBytes.length + remLen);
    let idx = 0;
    pkt[idx++] = 0x10;
    for (let i = 0; i < remBytes.length; i++) pkt[idx++] = remBytes[i];
    for (let i = 0; i < varHeader.length; i++) pkt[idx++] = varHeader[i];
    pkt[idx++] = (cidBytes.length >> 8) & 0xFF;
    pkt[idx++] = cidBytes.length & 0xFF;
    pkt.set(cidBytes, idx);
    return pkt;
  }

  function buildMqttSubscribePacket(topic) {
    const topBytes = strToUtf8Bytes(topic);
    const remLen = 2 + 2 + topBytes.length + 1;
    const remBytes = encodeMqttRemainingLength(remLen);
    const pkt = new Uint8Array(1 + remBytes.length + remLen);
    let idx = 0;
    pkt[idx++] = 0x82;
    for (let i = 0; i < remBytes.length; i++) pkt[idx++] = remBytes[i];
    pkt[idx++] = 0x00;
    pkt[idx++] = 0x01;
    pkt[idx++] = (topBytes.length >> 8) & 0xFF;
    pkt[idx++] = topBytes.length & 0xFF;
    pkt.set(topBytes, idx);
    idx += topBytes.length;
    pkt[idx] = 0x00;
    return pkt;
  }

  function buildMqttPublishPacket(topic, jsonStr) {
    const topBytes = strToUtf8Bytes(topic);
    const payBytes = strToUtf8Bytes(jsonStr);
    const remLen = 2 + topBytes.length + payBytes.length;
    const remBytes = encodeMqttRemainingLength(remLen);
    const pkt = new Uint8Array(1 + remBytes.length + remLen);
    let idx = 0;
    pkt[idx++] = 0x30;
    for (let i = 0; i < remBytes.length; i++) pkt[idx++] = remBytes[i];
    pkt[idx++] = (topBytes.length >> 8) & 0xFF;
    pkt[idx++] = topBytes.length & 0xFF;
    pkt.set(topBytes, idx);
    idx += topBytes.length;
    pkt.set(payBytes, idx);
    return pkt;
  }

  function getRelayTopic(pin) {
    return 'pleos-stage/karaoke/pin-' + String(pin || currentPin || '4829').trim();
  }

  function publishRelayEvent(event, dataObj) {
    const topic = getRelayTopic(currentPin);
    const rid = myClientId + '_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7);
    rememberMsgId(rid);
    const payload = JSON.stringify({
      senderId: myClientId,
      rid: rid,
      role: 'mic',
      event: event,
      data: dataObj || {},
      ts: Date.now()
    });
    const pkt = buildMqttPublishPacket(topic, payload);
    relayClients.forEach((entry) => {
      if (entry && entry.ready && entry.socket && entry.socket.readyState === WebSocket.OPEN) {
        try { entry.socket.send(pkt); } catch (e) {}
      }
    });
  }

  function buildJoinPinPayload() {
    return {
      pin: currentPin,
      deviceName: currentDeviceName || 'Pleos Smart Mic',
      phoneIp: phoneLocalIp || '',
      candidateIps: phoneCandidateIps,
      phonePort: 8082
    };
  }

  function markRealCarPacketReceived() {
    lastCarPacketTs = Date.now();
    if (window.PleosMicBridge && window.PleosMicBridge.onVerifiedCarPacket) {
      window.PleosMicBridge.onVerifiedCarPacket(String(currentPin));
    }
    if (!isCarPaired) {
      notifyNativeConnection('CONNECTED', '● 차량과 연결됨 (PIN ' + currentPin + ')', currentPin);
    }
  }

  function connectRelayBroker(brokerUrl) {
    const existing = relayClients.get(brokerUrl);
    if (existing) {
      if (existing.pingTimer) clearInterval(existing.pingTimer);
      if (existing.reconnectTimer) clearTimeout(existing.reconnectTimer);
      if (existing.socket) { try { existing.socket.close(); } catch (e) {} }
    }
    const entry = { url: brokerUrl, socket: null, ready: false, subscribedTopic: '', pingTimer: null, reconnectTimer: null };
    relayClients.set(brokerUrl, entry);

    let sock = null;
    try {
      sock = new WebSocket(brokerUrl, ['mqtt']);
      sock.binaryType = 'arraybuffer';
      entry.socket = sock;
    } catch (e) {
      entry.reconnectTimer = setTimeout(() => connectRelayBroker(brokerUrl), 1500);
      return;
    }

    sock.onopen = () => {
      try { sock.send(buildMqttConnectPacket(myClientId + '_' + Math.random().toString(36).substring(2, 5))); } catch (e) {}
      entry.pingTimer = setInterval(() => {
        if (sock.readyState === WebSocket.OPEN) {
          try { sock.send(new Uint8Array([0xC0, 0x00])); } catch (e) {}
        }
      }, 15000);
    };

    sock.onmessage = (evt) => {
      if (!(evt && evt.data instanceof ArrayBuffer)) return;
      const u8 = new Uint8Array(evt.data);
      let offset = 0;
      while (offset < u8.length) {
        const headerByte = u8[offset];
        const pktType = headerByte & 0xF0;
        let multiplier = 1, remLen = 0, lenIdx = offset + 1, valid = true;
        while (true) {
          if (lenIdx >= u8.length) { valid = false; break; }
          const b = u8[lenIdx++];
          remLen += (b & 127) * multiplier;
          multiplier *= 128;
          if ((b & 128) === 0) break;
          if (multiplier > 128 * 128 * 128) { valid = false; break; }
        }
        if (!valid || lenIdx + remLen > u8.length) break;
        const bodyStart = lenIdx, bodyEnd = bodyStart + remLen;
        if (pktType === 0x20) {
          entry.ready = true;
          const topic = getRelayTopic(currentPin);
          entry.subscribedTopic = topic;
          try { entry.socket.send(buildMqttSubscribePacket(topic)); } catch (e) {}
          publishRelayEvent('join-pin', buildJoinPinPayload());
        } else if (pktType === 0x90) {
          publishRelayEvent('join-pin', buildJoinPinPayload());
        } else if (pktType === 0x30 && bodyStart + 2 <= bodyEnd) {
          const topLen = (u8[bodyStart] << 8) | u8[bodyStart + 1];
          let payStart = bodyStart + 2 + topLen;
          if (((headerByte >> 1) & 0x03) > 0) payStart += 2;
          if (payStart <= bodyEnd) {
            try {
              const obj = JSON.parse(utf8BytesToStr(u8.subarray(payStart, bodyEnd)));
              if (obj && obj.senderId !== myClientId && obj.role !== 'mic' && !rememberMsgId(obj.rid)) {
                if (obj.event === 'paired-with-car' && obj.data && obj.data.ok) {
                  markRealCarPacketReceived();
                  if (obj.data.state) notifyNativeCarState(obj.data.state);
                } else if (obj.event === 'car-ready' || obj.event === 'car-pong') {
                  markRealCarPacketReceived();
                  publishRelayEvent('join-pin', buildJoinPinPayload());
                  if (obj.data && obj.data.state) notifyNativeCarState(obj.data.state);
                } else if (obj.event === 'car-state-sync') {
                  markRealCarPacketReceived();
                  notifyNativeCarState(obj.data);
                } else if (obj.event === 'karaoke-score-result') {
                  markRealCarPacketReceived();
                  notifyNativeScoreResult(obj.data);
                } else if (obj.event === 'karaoke-score-dismiss') {
                  notifyNativeScoreDismiss(obj.data);
                }
              }
            } catch (e) {}
          }
        }
        offset = bodyEnd;
      }
    };

    sock.onclose = () => {
      entry.ready = false;
      if (entry.pingTimer) { clearInterval(entry.pingTimer); entry.pingTimer = null; }
      entry.reconnectTimer = setTimeout(() => connectRelayBroker(brokerUrl), 1200);
    };
  }

  function encodePlaylistArray(arr) {
    if (!Array.isArray(arr)) return '';
    if (arr.length === 0) return '__EMPTY__';
    return arr.map(function(item) {
      if (!item) return '';
      const clean = function(v) { return String(v || '').replace(/[\t\n\r]/g, ' '); };
      return [
        clean(item.id),
        clean(item.title),
        clean(item.artist),
        clean(item.genre),
        Number(item.bpm || 124),
        Number(item.durationSec || 180),
        clean(item.durationLabel || '03:00'),
        clean(item.videoId || ''),
        clean(item.songId || ''),
        Boolean(item.isDevSample)
      ].join('\t');
    }).filter(Boolean).join('\n');
  }

  function notifyNativeConnection(state, message, pin) {
    if (state === 'CONNECTED' || state === 'STREAMING') {
      isCarPaired = true;
    } else if (state === 'CONNECTING' || state === 'IDLE' || state === 'ERROR') {
      isCarPaired = false;
    }
    if (window.PleosMicBridge && window.PleosMicBridge.onConnectionState) {
      window.PleosMicBridge.onConnectionState(String(state), String(message || ''), String(pin || currentPin));
    }
    if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.PleosMicBridge) {
      window.webkit.messageHandlers.PleosMicBridge.postMessage({
        type: 'connection',
        state: String(state),
        message: String(message || ''),
        pin: String(pin || currentPin)
      });
    }
  }

  function notifyNativeMetrics(rms, pitch, note) {
    if (window.PleosMicBridge && window.PleosMicBridge.onAudioMetrics) {
      window.PleosMicBridge.onAudioMetrics(Number(rms), Number(pitch), String(note));
    }
    if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.PleosMicBridge) {
      window.webkit.messageHandlers.PleosMicBridge.postMessage({
        type: 'metrics',
        rms: Number(rms),
        pitch: Number(pitch),
        note: String(note)
      });
    }
  }

  let lastScoreToggleTs = 0;

  function notifyNativeScoreResult(dataObj) {
    if (!dataObj) return;
    if (window.PleosMicBridge && window.PleosMicBridge.onKaraokeScoreResult) {
      window.PleosMicBridge.onKaraokeScoreResult(JSON.stringify(dataObj));
    }
    if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.PleosMicBridge) {
      window.webkit.messageHandlers.PleosMicBridge.postMessage({
        type: 'karaokeScoreResult',
        resultId: String(dataObj.resultId || ''),
        score: Number(dataObj.score || 95),
        badge: String(dataObj.badge || 'SUPER STAR'),
        title: String(dataObj.title || ''),
        comment: String(dataObj.comment || ''),
        pitchAccuracy: Number(dataObj.pitchAccuracy || 92),
        vocalPower: Number(dataObj.vocalPower || 90),
        stageManner: Number(dataObj.stageManner || 94),
        trackTitle: String(dataObj.trackTitle || ''),
        willAdvanceNext: !!dataObj.willAdvanceNext
      });
    }
  }

  function notifyNativeScoreDismiss(dataObj) {
    const rid = String((dataObj && dataObj.resultId) || '');
    if (window.PleosMicBridge && window.PleosMicBridge.onKaraokeScoreDismiss) {
      window.PleosMicBridge.onKaraokeScoreDismiss(rid);
    }
    if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.PleosMicBridge) {
      window.webkit.messageHandlers.PleosMicBridge.postMessage({
        type: 'karaokeScoreDismiss',
        resultId: rid
      });
    }
  }

  function notifyNativeCarState(stateObj) {
    if (!stateObj) return;
    const nowTs = Date.now();
    if (stateObj.scoreEnabled !== undefined && (nowTs - lastScoreToggleTs >= 1200)) {
      if (window.PleosMicBridge && window.PleosMicBridge.onScoreModeSync) {
        window.PleosMicBridge.onScoreModeSync(!!stateObj.scoreEnabled);
      }
      if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.PleosMicBridge) {
        window.webkit.messageHandlers.PleosMicBridge.postMessage({
          type: 'scoreMode',
          enabled: !!stateObj.scoreEnabled
        });
      }
    }
    const incomingHostId = String(stateObj.carHostId || '').trim();
    const stateRevision = Number(stateObj.stateRevision || 0);
    if (nowTs - lastUserCommandTs < 1200) {
      return;
    }

    const songId = String(stateObj.currentSongId || stateObj.currentTrackId || 'ncs_short_test_30s');
    const currentTrackId = String(stateObj.currentTrackId || songId);
    const isPlaying = !!stateObj.isPlaying;
    const rawPos = stateObj.currentPositionSec !== undefined ? stateObj.currentPositionSec : (stateObj.progressSec || 0);
    const progressSec = Math.max(0, Math.round(Number(rawPos || 0)));
    const durationSec = Math.max(15, Math.round(Number(stateObj.durationSec || 30)));
    const backingVolume = Number(stateObj.backingVolume !== undefined ? stateObj.backingVolume : (stateObj.mrVolume !== undefined ? stateObj.mrVolume : 85));
    const micVolume = Number(stateObj.micVolume !== undefined ? stateObj.micVolume : 85);
    const guideVolume = Number(stateObj.guideVolume !== undefined ? stateObj.guideVolume : 75);
    const keyShift = Number(stateObj.keyShift || 0);
    const echoLevel = Number(stateObj.echoLevel !== undefined ? stateObj.echoLevel : (stateObj.reverbLevel !== undefined ? stateObj.reverbLevel : 80));
    const mrPlaybackRate = Number(stateObj.mrPlaybackRate !== undefined ? stateObj.mrPlaybackRate : 1.0);
    const syncOffsetMs = Number(stateObj.syncOffsetMs !== undefined ? stateObj.syncOffsetMs : 0);
    const isParked = stateObj.isParked !== undefined ? !!stateObj.isParked : true;
    const score = Number(stateObj.score || 78);
    const isDevMode = stateObj.isDevMode !== undefined ? !!stateObj.isDevMode : true;
    const guideVocalEnabled = !!(stateObj.guideVocalEnabled || stateObj.demoVocalWave);
    const guidePitchHz = Number(stateObj.guidePitchHz || 0);
    const guideNote = String(stateObj.guideNote || '-');
    const guideVoiceLevelPercent = Number(stateObj.guideVoiceLevelPercent || 0);
    demoVocalActive = guideVocalEnabled;
    const playlistEncoded = encodePlaylistArray(stateObj.playlist);

    if (window.PleosMicBridge && window.PleosMicBridge.onGuideVocalTelemetrySync) {
      window.PleosMicBridge.onGuideVocalTelemetrySync(
        guideVocalEnabled,
        guidePitchHz,
        guideNote,
        guideVoiceLevelPercent
      );
    } else if (window.PleosMicBridge && window.PleosMicBridge.onGuideVocalSync) {
      window.PleosMicBridge.onGuideVocalSync(guideVocalEnabled);
    }

    if (window.PleosMicBridge && window.PleosMicBridge.onCarStateSyncExtended) {
      window.PleosMicBridge.onCarStateSyncExtended(
        songId,
        currentTrackId,
        isPlaying,
        progressSec,
        durationSec,
        backingVolume,
        micVolume,
        guideVolume,
        keyShift,
        echoLevel,
        mrPlaybackRate,
        syncOffsetMs,
        isParked,
        score,
        isDevMode,
        playlistEncoded,
        incomingHostId,
        stateRevision
      );
    } else if (window.PleosMicBridge && window.PleosMicBridge.onCarStateSyncFull) {
      window.PleosMicBridge.onCarStateSyncFull(
        songId,
        currentTrackId,
        isPlaying,
        progressSec,
        durationSec,
        backingVolume,
        keyShift,
        echoLevel,
        isParked,
        score,
        isDevMode,
        playlistEncoded
      );
    } else if (window.PleosMicBridge && window.PleosMicBridge.onCarStateSync) {
      window.PleosMicBridge.onCarStateSync(songId, isPlaying, keyShift, echoLevel, isParked, score);
    }
    if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.PleosMicBridge) {
      window.webkit.messageHandlers.PleosMicBridge.postMessage({
        type: 'carState',
        songId,
        currentTrackId,
        isPlaying,
        progressSec,
        durationSec,
        backingVolume,
        micVolume,
        guideVolume,
        keyShift,
        echoLevel,
        mrPlaybackRate,
        syncOffsetMs,
        isParked,
        score,
        isDevMode,
        playlistEncoded,
        carHostId: incomingHostId,
        stateRevision
      });
    }
  }

  function normalizeWsUrl(rawUrl) {
    let u = String(rawUrl || '').trim();
    if (!u) return 'ws://10.0.2.2:8080/ws';
    const qMark = u.indexOf('?');
    if (qMark >= 0) {
      try {
        const params = new URLSearchParams(u.substring(qMark + 1));
        if (params.get('pin')) currentPin = params.get('pin');
      } catch (e) {}
      u = u.substring(0, qMark);
    }
    if (u.startsWith('http://')) u = 'ws://' + u.substring(7);
    else if (u.startsWith('https://')) u = 'wss://' + u.substring(8);
    else if (!u.startsWith('ws://') && !u.startsWith('wss://')) u = 'ws://' + u;
    u = u.replace(':3000', ':8080');
    u = u.replace(/\/$/, '');
    if (!u.endsWith('/ws')) u = u + '/ws';
    return u;
  }

  function isDirectLocalConnected() {
    return (ws && ws.readyState === WebSocket.OPEN) || isDirectLocalSocketActive;
  }

  function sendWsEvent(event, dataObj) {
    const isRealtimeAudioOrTelemetry = (event === 'audio-pcm-chunk' || event === 'vocal-telemetry');
    if (ws && ws.readyState === WebSocket.OPEN) {
      try {
        ws.send(JSON.stringify({ event: event, data: dataObj || {} }));
      } catch (e) {}
    }
    if (isRealtimeAudioOrTelemetry) {
      if (isDirectLocalConnected()) {
        if (event === 'audio-pcm-chunk' && isDirectLocalSocketActive && window.PleosMicBridge && window.PleosMicBridge.onAudioPcmChunkForDirectSocket) {
          try {
            window.PleosMicBridge.onAudioPcmChunkForDirectSocket(
              Number((dataObj && dataObj.sr) || 16000),
              JSON.stringify((dataObj && dataObj.pcm) || [])
            );
          } catch (e) {}
        }
        return;
      }
      if (!isCarPaired) return;
    }
    publishRelayEvent(event, dataObj);
  }

  function freqToNote(freq) {
    if (!freq || freq < 65 || freq > 1400) return '-';
    const noteNum = 12 * (Math.log(freq / 440) / Math.log(2));
    const midi = Math.round(noteNum) + 69;
    const noteName = NOTE_STRINGS[midi % 12];
    const octave = Math.floor(midi / 12) - 1;
    return noteName + octave;
  }

  function start60FpsLoop() {
    if (rafId) {
      cancelAnimationFrame(rafId);
      rafId = null;
    }
    if (window.PleosMicBridge) {
      // Android Native AudioRecord handles 100% of real microphone metrics & telemetry
      return;
    }
  }

  window.PleosMicEngine = {
    setPhoneLocalIp: function(ip) {
      if (ip) phoneLocalIp = String(ip).trim();
    },
    setPhoneCandidateIps: function(csvStr) {
      if (csvStr) {
        phoneCandidateIps = String(csvStr).split(',').map(function(s) { return s.trim(); }).filter(Boolean);
        if (phoneCandidateIps.length > 0 && !phoneLocalIp) phoneLocalIp = phoneCandidateIps[0];
      }
    },
    setDirectLocalConnected: function(connected) {
      isDirectLocalSocketActive = !!connected;
    },
    onDirectCarPacketReceived: function() {
      markRealCarPacketReceived();
    },
    setDiscoveredCarUrl: function(wsUrl) {
      if (wsUrl) discoveredCarWsUrl = normalizeWsUrl(wsUrl);
    },
    connectToRoom: function(serverUrl, pin, deviceName) {
      currentPin = String(pin || '4829').trim();
      if (deviceName) currentDeviceName = String(deviceName);
      if (relayClients.size === 0) {
        RELAY_BROKERS.forEach((url) => connectRelayBroker(url));
      } else {
        publishRelayEvent('join-pin', buildJoinPinPayload());
      }
      const targetUrl = normalizeWsUrl(serverUrl);
      if (!isCarPaired) {
        notifyNativeConnection('CONNECTING', '◐ 차량 연결 중...', currentPin);
      }
      if (ws) { try { ws.close(); } catch (e) {} }
      try {
        ws = new WebSocket(targetUrl);
        ws.onopen = () => sendWsEvent('join-pin', buildJoinPinPayload());
        ws.onmessage = (evt) => {
          try {
            const msg = JSON.parse(evt.data);
            if (msg.event === 'paired-with-car' && msg.data && msg.data.ok) {
              markRealCarPacketReceived();
              if (msg.data.state) notifyNativeCarState(msg.data.state);
            } else if (msg.event === 'car-state-sync' || msg.event === 'car-ready' || msg.event === 'car-pong' || msg.event === 'car-ping') {
              markRealCarPacketReceived();
              if (msg.data && (msg.event === 'car-state-sync' || msg.data.state)) notifyNativeCarState(msg.data.state || msg.data);
            } else if (msg.event === 'karaoke-score-result') {
              markRealCarPacketReceived();
              notifyNativeScoreResult(msg.data);
            } else if (msg.event === 'karaoke-score-dismiss') {
              notifyNativeScoreDismiss(msg.data);
            }
          } catch (e) {}
        };
        ws.onerror = () => {};
      } catch (e) {}
    },
    markUserCommandCooldown: function() {
      lastUserCommandTs = Date.now();
    },
    sendRemoteCommand: function(jsonStr) {
      lastUserCommandTs = Date.now();
      try {
        const cmd = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr;
        if (cmd && (cmd.action === 'toggle-score-mode' || cmd.type === 'toggle-score-mode')) {
          lastScoreToggleTs = Date.now();
        }
        sendWsEvent('remote-command', { ...cmd, pin: currentPin });
      } catch (e) {}
    },
    setMuted: function(muted) { isMuted = !!muted; },
    setDemoVocal: function(active) { demoVocalActive = !!active; start60FpsLoop(); }
  };
  start60FpsLoop();
  window.PleosMicEngine.connectToRoom(discoveredCarWsUrl || 'ws://10.0.2.2:8080/ws', currentPin, 'Pleos Smart Mic');
  setInterval(function() {
    if (isRelayConnected()) publishRelayEvent('join-pin', buildJoinPinPayload());
    if (ws && ws.readyState === WebSocket.OPEN) {
      try { ws.send(JSON.stringify({ event: 'mic-ping', data: buildJoinPinPayload() })); } catch (e) {}
    }
    if (isCarPaired && (Date.now() - lastCarPacketTs > 6000)) {
      isCarPaired = false;
      isDirectLocalSocketActive = false;
      notifyNativeConnection('IDLE', '○ 연결 안 됨 (대기 중)', currentPin);
    }
  }, 1000);
})();
</script>
</body>
</html>
""".trimIndent()
