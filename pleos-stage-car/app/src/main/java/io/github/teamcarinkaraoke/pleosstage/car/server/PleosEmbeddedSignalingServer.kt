package io.github.teamcarinkaraoke.pleosstage.car.server

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.github.teamcarinkaraoke.pleosstage.car.audio.YouTubeUrlParser
import io.github.teamcarinkaraoke.pleosstage.car.audio.buildYouTubeIframeHtml
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class EmbeddedServerState(
    val isRunning: Boolean = false,
    val port: Int = PleosEmbeddedSignalingServer.DEFAULT_PORT,
    val lanIp: String = "127.0.0.1",
    val lanWsUrl: String = "ws://127.0.0.1:${PleosEmbeddedSignalingServer.DEFAULT_PORT}/ws",
    val localLoopbackWsUrl: String = "ws://127.0.0.1:${PleosEmbeddedSignalingServer.DEFAULT_PORT}/ws",
    val emulatorBridgeWsUrl: String = "ws://10.0.2.2:${PleosEmbeddedSignalingServer.DEFAULT_PORT}/ws",
    val activePin: String = "4829",
    val connectedMicCount: Int = 0
)

/**
 * [Architecture B] Embedded Ktor Signaling & Audio Relay Server running inside the AAOS Car Head Unit.
 *
 * Eliminates the external PC Node.js (`pleos-stage-server`) dependency:
 * - Hosts a native WebSocket server (`ws://<car-ip>:8080/ws` and `ws://<car-ip>:8080`) directly on AAOS.
 * - Relays 4-digit PIN pairing, WebRTC P2P SDP Offer/Answer/ICE, 60fps vocal pitch/RMS telemetry,
 *   low-latency 16kHz PCM audio chunks, and bi-directional stage remote control commands.
 * - Broadcasts & responds on UDP port 8081 so smartphones on the same Wi-Fi auto-discover the Car IP.
 */
class PleosEmbeddedSignalingServer(
    private val appContext: Context,
    initialPin: String = "4829"
) {
    companion object {
        private const val TAG = "PleosKtorServer"
        const val DEFAULT_PORT = 8080
        const val DISCOVERY_UDP_PORT = 8081

        fun detectLocalLanIp(context: Context): String {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val wifiIpInt = wifiManager?.connectionInfo?.ipAddress ?: 0
                if (wifiIpInt != 0) {
                    val ip = String.format(
                        "%d.%d.%d.%d",
                        wifiIpInt and 0xff,
                        wifiIpInt shr 8 and 0xff,
                        wifiIpInt shr 16 and 0xff,
                        wifiIpInt shr 24 and 0xff
                    )
                    if (ip != "0.0.0.0" && !ip.startsWith("127.")) {
                        return ip
                    }
                }
            } catch (_: Exception) {
            }

            var fallbackIp = "127.0.0.1"
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return fallbackIp
                for (intf in interfaces) {
                    if (!intf.isUp || intf.isLoopback) continue
                    for (addr in intf.inetAddresses) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val host = addr.hostAddress ?: continue
                            if (host.startsWith("192.168.") || host.startsWith("172.")) {
                                return host
                            }
                            if (host.startsWith("10.") && fallbackIp == "127.0.0.1") {
                                fallbackIp = host
                            } else if (!host.startsWith("169.254.") && fallbackIp == "127.0.0.1") {
                                fallbackIp = host
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            return fallbackIp
        }
    }

    private data class ConnectedClient(
        val id: String,
        val session: DefaultWebSocketServerSession,
        var role: String = "unknown", // "car" or "mic"
        var pin: String = "4829",
        var deviceName: String = "Pleos Smart Mic"
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: ApplicationEngine? = null
    private var udpDiscoveryJob: Job? = null

    private val clients = ConcurrentHashMap<String, ConnectedClient>()
    @Volatile
    private var currentPin: String = initialPin
    @Volatile
    var onMicPcmChunk: ((sampleRate: Int, pcmShorts: ShortArray) -> Unit)? = null
    @Volatile
    var onDirectRemoteCommand: ((type: String, payload: JSONObject) -> Unit)? = null
    @Volatile
    var onDirectMicConnectionChanged: ((connected: Boolean, deviceName: String, count: Int) -> Unit)? = null
    @Volatile
    private var roomStateJson: JSONObject = JSONObject().apply {
        put("currentSongId", "ncs_on_and_on")
        put("currentTrackId", "ncs_on_and_on")
        put("isPlaying", false)
        put("progressSec", 0)
        put("durationSec", 208)
        put("backingVolume", 85)
        put("keyShift", 0)
        put("echoLevel", 80)
        put("isParked", true)
        put("score", 92)
        put("isDevMode", true)
        put("guideVocalEnabled", false)
        put("demoVocalWave", false)
        put("guidePitchHz", 0.0)
        put("guideNote", "-")
        put("guideVoiceLevelPercent", 0)
        put("playlist", YouTubeUrlParser.toJsonArray(YouTubeUrlParser.DEFAULT_UNIFIED_PLAYLIST))
    }

    fun broadcastCarStateSync(stateUpdate: JSONObject) {
        scope.launch {
            val keys = stateUpdate.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                roomStateJson.put(k, stateUpdate.opt(k))
            }
            sendToRole("mic", "car-state-sync", roomStateJson)
            sendToRole("monitor", "car-state-sync", roomStateJson)
        }
    }

    private val _serverState = MutableStateFlow(
         run {
            val ip = detectLocalLanIp(appContext)
            EmbeddedServerState(
                isRunning = false,
                port = DEFAULT_PORT,
                lanIp = ip,
                lanWsUrl = "ws://$ip:$DEFAULT_PORT/ws",
                localLoopbackWsUrl = "ws://127.0.0.1:$DEFAULT_PORT/ws",
                emulatorBridgeWsUrl = "ws://10.0.2.2:$DEFAULT_PORT/ws",
                activePin = initialPin
            )
        }
    )
    val serverState: StateFlow<EmbeddedServerState> = _serverState.asStateFlow()

    fun start() {
        if (engine != null) return
        scope.launch {
            val lanIp = detectLocalLanIp(appContext)
            try {
                val ktorEngine: CIOApplicationEngine = embeddedServer(
                    factory = CIO,
                    port = DEFAULT_PORT,
                    host = "0.0.0.0"
                ) {
                    install(WebSockets) {
                        pingPeriod = Duration.ofSeconds(10)
                        timeout = Duration.ofSeconds(25)
                        maxFrameSize = Long.MAX_VALUE
                        masking = false
                    }

                    routing {
                        options("{...}") {
                            call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                            call.response.header(HttpHeaders.AccessControlAllowMethods, "GET, POST, OPTIONS")
                            call.response.header(HttpHeaders.AccessControlAllowHeaders, "*")
                            call.respondText("", ContentType.Text.Plain, HttpStatusCode.NoContent)
                        }

                        get("/") {
                            call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                            call.respondText(buildServerInfoJson(), ContentType.Application.Json)
                        }

                        get("/api/info") {
                            call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                            call.respondText(buildServerInfoJson(), ContentType.Application.Json)
                        }

                        get("/monitor") {
                            call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                            call.respondText(buildPcMonitorHtml(), ContentType.Text.Html)
                        }

                        get("/yt-player") {
                            val videoId = call.request.queryParameters["videoId"]?.ifBlank { "K4DyBUG242c" } ?: "K4DyBUG242c"
                            val volume = call.request.queryParameters["volume"]?.toIntOrNull()?.coerceIn(0, 100) ?: 85
                            val autoPlay = call.request.queryParameters["autoplay"] != "0"
                            call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                            call.response.header("Referrer-Policy", "strict-origin-when-cross-origin")
                            call.respondText(
                                buildYouTubeIframeHtml(
                                    initialVideoId = videoId,
                                    initialVolume = volume,
                                    initialAutoPlay = autoPlay
                                ),
                                ContentType.Text.Html
                            )
                        }

                        webSocket("/ws") {
                            handleWebSocketSession(this)
                        }

                        webSocket("/") {
                            handleWebSocketSession(this)
                        }
                    }
                }

                ktorEngine.start(wait = false)
                engine = ktorEngine

                val updatedIp = detectLocalLanIp(appContext)
                _serverState.update {
                    it.copy(
                        isRunning = true,
                        lanIp = updatedIp,
                        lanWsUrl = "ws://$updatedIp:$DEFAULT_PORT/ws",
                        localLoopbackWsUrl = "ws://127.0.0.1:$DEFAULT_PORT/ws",
                        emulatorBridgeWsUrl = "ws://10.0.2.2:$DEFAULT_PORT/ws",
                        activePin = currentPin
                    )
                }
                Log.i(TAG, "Pleos Embedded Ktor Server started on ws://$updatedIp:$DEFAULT_PORT/ws (PIN $currentPin)")

                startUdpDiscoveryResponder()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Ktor Embedded Server on port $DEFAULT_PORT: ${e.message}", e)
                _serverState.update {
                    it.copy(
                        lanIp = lanIp,
                        lanWsUrl = "ws://$lanIp:$DEFAULT_PORT/ws"
                    )
                }
            }
        }
    }

    private fun buildServerInfoJson(): String {
        val ip = detectLocalLanIp(appContext)
        val micCount = clients.values.count { it.role == "mic" }
        val hasCar = clients.values.any { it.role == "car" }
        return JSONObject().apply {
            put("status", "ok")
            put("engine", "Pleos Embedded Ktor CIO Server")
            put("pin", currentPin)
            put("lanIp", ip)
            put("port", DEFAULT_PORT)
            put("wsUrl", "ws://$ip:$DEFAULT_PORT/ws")
            put("lanUrl", "ws://$ip:$DEFAULT_PORT/ws")
            put("emulatorUrl", "ws://10.0.2.2:$DEFAULT_PORT/ws")
            put("hasCar", hasCar)
            put("micCount", micCount)
        }.toString()
    }

    private fun buildPcMonitorHtml(): String = """
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Pleos Stage · PC 실시간 오디오 모니터 (:8080/monitor)</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Pretendard", "Segoe UI", Roboto, sans-serif; }
    body { background: #0B1120; color: #F8FAFC; min-height: 100vh; display: flex; flex-direction: column; align-items: center; padding: 28px 20px; }
    .container { width: 100%; max-width: 920px; display: flex; flex-direction: column; gap: 18px; }
    .card { background: #131C31; border: 1px solid #1E293B; border-radius: 20px; padding: 22px 24px; box-shadow: 0 12px 32px rgba(0,0,0,0.35); }
    .header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
    .badge { display: inline-flex; align-items: center; gap: 8px; padding: 6px 14px; border-radius: 999px; font-size: 13px; font-weight: 800; background: #1E293B; color: #38BDF8; }
    .badge.live { background: rgba(16, 185, 129, 0.18); color: #34D399; border: 1px solid rgba(16, 185, 129, 0.4); }
    .btn-primary { width: 100%; padding: 18px 24px; border-radius: 16px; border: none; font-size: 18px; font-weight: 900; cursor: pointer; background: linear-gradient(90deg, #2563EB, #7C3AED); color: #FFF; transition: transform 0.12s ease, opacity 0.15s ease; }
    .btn-primary:hover { opacity: 0.95; transform: translateY(-1px); }
    .btn-primary.active { background: linear-gradient(90deg, #059669, #0284C7); }
    .track-title { font-size: 26px; font-weight: 900; color: #FFFFFF; margin-top: 6px; }
    .track-sub { font-size: 14px; color: #94A3B8; margin-top: 4px; }
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    @media (max-width: 720px) { .grid { grid-template-columns: 1fr; } }
    .meter-box { background: #0F172A; border-radius: 14px; padding: 16px; border: 1px solid #1E293B; }
    .meter-label { font-size: 13px; font-weight: 700; color: #94A3B8; display: flex; justify-content: space-between; margin-bottom: 8px; }
    .meter-bar-bg { width: 100%; height: 12px; background: #1E293B; border-radius: 999px; overflow: hidden; }
    .meter-bar-fill { height: 100%; width: 0%; border-radius: 999px; transition: width 0.08s linear; }
    canvas { width: 100%; height: 120px; background: #090E1A; border-radius: 14px; border: 1px solid #1E293B; display: block; margin-top: 12px; }
    .controls { display: flex; gap: 10px; margin-top: 14px; flex-wrap: wrap; }
    .btn-ctrl { padding: 10px 18px; border-radius: 12px; border: 1px solid #334155; background: #1E293B; color: #F8FAFC; font-weight: 800; font-size: 14px; cursor: pointer; }
    .btn-ctrl:hover { background: #334155; }
    #ytHiddenWrap { position: fixed; width: 1px; height: 1px; opacity: 0.01; pointer-events: none; bottom: 0; right: 0; overflow: hidden; }
  </style>
</head>
<body>
  <div class="container">
    <div class="card">
      <div class="header">
        <div>
          <div style="font-size: 13px; font-weight: 800; color: #38BDF8; letter-spacing: 0.5px;">PLEOS STAGE · REAL-TIME PC AUDIO BRIDGE</div>
          <h1 style="font-size: 22px; font-weight: 900; margin-top: 4px;">🔊 PC 브라우저 실시간 오디오 모니터 (반주 + 마이크 리버브)</h1>
        </div>
        <div id="wsBadge" class="badge">● WS 연결 대기 중...</div>
      </div>
      <p style="font-size: 14px; color: #94A3B8; margin-top: 10px; line-height: 1.5;">
        안드로이드 에뮬레이터 오디오 출력이 들리지 않을 때, 아래 버튼을 한 번 클릭하면 차량의 <b>실시간 반주(MR/유튜브/신디사이저)</b>와 스마트폰 마이크의 <b>콘서트홀 Wet 리버브 보컬</b>이 현재 PC 스피커로 즉시 출력됩니다.
      </p>
      <button id="startAudioBtn" class="btn-primary" style="margin-top: 16px;" onclick="enableMonitorAudio()">
        🔊 여기를 클릭하여 PC 스피커 실시간 오디오 모니터링 켜기
      </button>
    </div>

    <div class="card">
      <div style="display:flex; justify-content:space-between; align-items:center;">
        <span id="playStatusBadge" class="badge">⏸ 대기 중 (PAUSED)</span>
        <span id="timeLabel" style="font-size:15px; font-weight:800; color:#38BDF8;">0:00 / 3:28</span>
      </div>
      <div id="trackTitle" class="track-title">Cartoon - On & On (feat. Daniel Levi) [NCS Release]</div>
      <div id="trackSub" class="track-sub">Pleos Stage Live Audio Engine · Key 0 · Echo 80%</div>

      <canvas id="fftCanvas" width="840" height="120"></canvas>

      <div class="controls">
        <button class="btn-ctrl" onclick="sendRemoteCmd('START_SONG')">▶ 차량 반주 재생</button>
        <button class="btn-ctrl" onclick="sendRemoteCmd('PAUSE_SONG')">⏸ 일시정지</button>
        <button class="btn-ctrl" onclick="sendRemoteCmd('TRIGGER_SFX', {sfx:'cheer'})">👏 환호성 효과음</button>
        <button class="btn-ctrl" onclick="sendRemoteCmd('TRIGGER_SFX', {sfx:'fanfare'})">🎉 팡파레 효과음</button>
      </div>
    </div>

    <div class="grid">
      <div class="meter-box">
        <div class="meter-label">
          <span>🎵 실시간 반주(MR) 출력 레벨</span>
          <span id="mrVolText">85%</span>
        </div>
        <div class="meter-bar-bg">
          <div id="mrBar" class="meter-bar-fill" style="background: linear-gradient(90deg, #3B82F6, #A855F7);"></div>
        </div>
        <div style="margin-top:12px; display:flex; align-items:center; gap:10px; font-size:13px; color:#CBD5E1;">
          <span>PC 반주 볼륨</span>
          <input id="pcMrSlider" type="range" min="0" max="100" value="85" style="flex:1;" oninput="updatePcVolumes()" />
        </div>
      </div>

      <div class="meter-box">
        <div class="meter-label">
          <span>🎤 마이크 보컬 + 콘서트홀 리버브 수신</span>
          <span id="micTelemetryText">0% · -</span>
        </div>
        <div class="meter-bar-bg">
          <div id="micBar" class="meter-bar-fill" style="background: linear-gradient(90deg, #10B981, #38BDF8);"></div>
        </div>
        <div style="margin-top:12px; display:flex; align-items:center; gap:10px; font-size:13px; color:#CBD5E1;">
          <span>PC 마이크 리버브 볼륨</span>
          <input id="pcMicSlider" type="range" min="0" max="150" value="100" style="flex:1;" oninput="updatePcVolumes()" />
        </div>
      </div>
    </div>
  </div>

  <div id="ytHiddenWrap"><div id="ytPlayer"></div></div>

  <script>
    let audioCtx = null;
    let masterGain = null;
    let backingGain = null;
    let micDryGain = null;
    let micWetGain = null;
    let convolver = null;
    let analyser = null;
    let audioEnabled = false;
    let ws = null;
    let nextPcmTime = 0;

    let carState = {
      isPlaying: false,
      currentTrackId: 'ncs_on_and_on',
      title: 'Cartoon - On & On (feat. Daniel Levi) [NCS Release]',
      artist: 'NoCopyrightSounds',
      videoId: 'K4DyBUG242c',
      progressSec: 0,
      durationSec: 208,
      backingVolume: 85,
      echoLevel: 80,
      keyShift: 0,
      bpm: 118
    };

    let synthTimer = null;
    let stepIndex = 0;
    let ytPlayer = null;
    let ytReady = false;
    let currentYtVideoId = '';

    const tag = document.createElement('script');
    tag.src = "https://www.youtube.com/iframe_api";
    document.head.appendChild(tag);

    window.onYouTubeIframeAPIReady = function() {
      ytPlayer = new YT.Player('ytPlayer', {
        height: '1',
        width: '1',
        videoId: 'K4DyBUG242c',
        playerVars: { autoplay: 0, controls: 0, playsinline: 1, origin: window.location.origin },
        events: {
          onReady: () => { ytReady = true; syncPlaybackEngine(); }
        }
      });
    };

    function createConcertHallImpulse(ctx, seconds, decay) {
      const rate = ctx.sampleRate;
      const len = rate * seconds;
      const impulse = ctx.createBuffer(2, len, rate);
      for (let ch = 0; ch < 2; ch++) {
        const buf = impulse.getChannelData(ch);
        for (let i = 0; i < len; i++) {
          buf[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, decay);
        }
      }
      return impulse;
    }

    function enableMonitorAudio() {
      if (!audioCtx) {
        const AC = window.AudioContext || window.webkitAudioContext;
        audioCtx = new AC();
        masterGain = audioCtx.createGain();
        masterGain.gain.value = 1.0;

        backingGain = audioCtx.createGain();
        backingGain.gain.value = 0.85;

        micDryGain = audioCtx.createGain();
        micDryGain.gain.value = 0.85;

        micWetGain = audioCtx.createGain();
        micWetGain.gain.value = 0.75;

        convolver = audioCtx.createConvolver();
        convolver.buffer = createConcertHallImpulse(audioCtx, 2.4, 2.6);

        analyser = audioCtx.createAnalyser();
        analyser.fftSize = 256;

        backingGain.connect(analyser);
        micDryGain.connect(analyser);
        convolver.connect(micWetGain);
        micWetGain.connect(analyser);

        analyser.connect(masterGain);
        masterGain.connect(audioCtx.destination);
      }
      if (audioCtx.state === 'suspended') {
        audioCtx.resume();
      }
      audioEnabled = true;
      const btn = document.getElementById('startAudioBtn');
      btn.textContent = '✅ PC 실시간 오디오 출력 활성화됨 (반주 + 마이크 리버브 스트리밍 중)';
      btn.classList.add('active');
      syncPlaybackEngine();
    }

    function updatePcVolumes() {
      const mrVal = Number(document.getElementById('pcMrSlider').value || 85);
      const micVal = Number(document.getElementById('pcMicSlider').value || 100);
      document.getElementById('mrVolText').textContent = mrVal + '%';
      if (backingGain && audioCtx) {
        backingGain.gain.setTargetAtTime(mrVal / 100, audioCtx.currentTime, 0.03);
      }
      if (ytReady && ytPlayer && ytPlayer.setVolume) {
        ytPlayer.setVolume(mrVal);
      }
      if (micDryGain && micWetGain && audioCtx) {
        micDryGain.gain.setTargetAtTime((micVal / 100) * 0.9, audioCtx.currentTime, 0.03);
        micWetGain.gain.setTargetAtTime((micVal / 100) * (carState.echoLevel / 100) * 0.95, audioCtx.currentTime, 0.03);
      }
    }

    function scheduleSynthStep() {
      if (!audioEnabled || !audioCtx || !carState.isPlaying) return;
      const now = audioCtx.currentTime;
      const isBallad = (carState.currentTrackId || '').includes('ballad') || (carState.currentTrackId || '').includes('invincible');
      const shiftRatio = Math.pow(2, (carState.keyShift || 0) / 12);
      const chordProg = isBallad
        ? [[220, 261.63, 329.63], [174.61, 220, 261.63], [130.81, 164.81, 196.00], [196.00, 246.94, 293.66]]
        : [[261.63, 329.63, 392.00], [196.00, 246.94, 293.66], [220.00, 261.63, 329.63], [174.61, 220.00, 261.63]];
      const melodyProg = isBallad
        ? [440, 523.25, 659.25, 587.33, 523.25, 440, 392, 440]
        : [523.25, 659.25, 783.99, 659.25, 587.33, 523.25, 440, 392];

      const bar = Math.floor(stepIndex / 4) % chordProg.length;
      const beat = stepIndex % 4;
      const chord = chordProg[bar];

      // Bass on every beat
      const bassOsc = audioCtx.createOscillator();
      const bassG = audioCtx.createGain();
      bassOsc.type = 'triangle';
      bassOsc.frequency.setValueAtTime((chord[0] / 2) * shiftRatio, now);
      bassG.gain.setValueAtTime(0.24, now);
      bassG.gain.exponentialRampToValueAtTime(0.001, now + 0.28);
      bassOsc.connect(bassG);
      bassG.connect(backingGain);
      bassOsc.start(now);
      bassOsc.stop(now + 0.3);

      // Chord pad on beat 0 and 2
      if (beat === 0 || beat === 2) {
        chord.forEach(freq => {
          const osc = audioCtx.createOscillator();
          const g = audioCtx.createGain();
          osc.type = 'sine';
          osc.frequency.setValueAtTime(freq * shiftRatio, now);
          g.gain.setValueAtTime(0.09, now);
          g.gain.exponentialRampToValueAtTime(0.001, now + 0.45);
          osc.connect(g);
          g.connect(backingGain);
          osc.start(now);
          osc.stop(now + 0.48);
        });
      }

      // Lead melody
      const melFreq = melodyProg[stepIndex % melodyProg.length] * shiftRatio;
      const melOsc = audioCtx.createOscillator();
      const melG = audioCtx.createGain();
      melOsc.type = 'sawtooth';
      melOsc.frequency.setValueAtTime(melFreq, now);
      melG.gain.setValueAtTime(0.06, now);
      melG.gain.exponentialRampToValueAtTime(0.001, now + 0.22);
      melOsc.connect(melG);
      melG.connect(backingGain);
      melOsc.start(now);
      melOsc.stop(now + 0.24);

      stepIndex++;
    }

    function syncPlaybackEngine() {
      if (synthTimer) {
        clearInterval(synthTimer);
        synthTimer = null;
      }
      const isBuiltInSynth = !carState.videoId &&
        (carState.currentTrackId === 'kpop_neon_drive' || carState.currentTrackId === 'ballad_starlight_highway');
      if (carState.isPlaying && audioEnabled && isBuiltInSynth) {
        const bpm = carState.bpm || 118;
        const intervalMs = Math.max(180, Math.round((60 / bpm / 2) * 1000));
        scheduleSynthStep();
        synthTimer = setInterval(scheduleSynthStep, intervalMs);
      }
      if (ytReady && ytPlayer) {
        try {
          if (carState.videoId && carState.videoId !== currentYtVideoId) {
            currentYtVideoId = carState.videoId;
            if (carState.isPlaying && audioEnabled) {
              ytPlayer.loadVideoById(currentYtVideoId, carState.progressSec || 0);
            } else {
              ytPlayer.cueVideoById(currentYtVideoId, carState.progressSec || 0);
            }
          } else {
            if (carState.isPlaying && audioEnabled) {
              ytPlayer.playVideo();
            } else {
              ytPlayer.pauseVideo();
            }
          }
        } catch (_e) {}
      }
    }

    function playIncomingPcmChunk(sr, pcmArray) {
      if (!audioEnabled || !audioCtx || !pcmArray || !pcmArray.length) return;
      const len = pcmArray.length;
      const buf = audioCtx.createBuffer(1, len, sr || 16000);
      const ch = buf.getChannelData(0);
      for (let i = 0; i < len; i++) {
        ch[i] = Math.max(-1, Math.min(1, pcmArray[i] / 32768));
      }
      const src = audioCtx.createBufferSource();
      src.buffer = buf;
      src.connect(micDryGain);
      src.connect(convolver);

      const now = audioCtx.currentTime;
      if (nextPcmTime < now || nextPcmTime > now + 0.25) {
        nextPcmTime = now + 0.015;
      }
      src.start(nextPcmTime);
      nextPcmTime += buf.duration;
    }

    function playSfx(sfxType) {
      if (!audioEnabled || !audioCtx) return;
      const now = audioCtx.currentTime;
      const notes = sfxType === 'fanfare' ? [523.25, 659.25, 783.99, 1046.5] : [440, 554.37, 659.25, 880];
      notes.forEach((f, idx) => {
        const osc = audioCtx.createOscillator();
        const g = audioCtx.createGain();
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(f, now + idx * 0.08);
        g.gain.setValueAtTime(0.18, now + idx * 0.08);
        g.gain.exponentialRampToValueAtTime(0.001, now + idx * 0.08 + 0.35);
        osc.connect(g);
        g.connect(masterGain);
        osc.start(now + idx * 0.08);
        osc.stop(now + idx * 0.08 + 0.38);
      });
    }

    function formatSec(sec) {
      const s = Math.max(0, Math.floor(sec || 0));
      return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');
    }

    function applyCarState(st) {
      if (!st) return;
      const prevPlaying = carState.isPlaying;
      const prevTrack = carState.currentTrackId;
      if (typeof st.isPlaying === 'boolean') carState.isPlaying = st.isPlaying;
      if (st.currentTrackId || st.currentSongId) carState.currentTrackId = st.currentTrackId || st.currentSongId;
      if (typeof st.progressSec === 'number') carState.progressSec = st.progressSec;
      if (typeof st.durationSec === 'number') carState.durationSec = st.durationSec;
      if (typeof st.backingVolume === 'number') carState.backingVolume = st.backingVolume;
      if (typeof st.echoLevel === 'number') carState.echoLevel = st.echoLevel;
      if (typeof st.keyShift === 'number') carState.keyShift = st.keyShift;

      if (Array.isArray(st.playlist)) {
        const found = st.playlist.find(t => t.id === carState.currentTrackId) || st.playlist[0];
        if (found) {
          carState.title = found.title || carState.title;
          carState.artist = found.artist || carState.artist;
          carState.videoId = found.videoId || '';
        }
      }

      document.getElementById('trackTitle').textContent = carState.title;
      document.getElementById('trackSub').textContent = `${'$'}{carState.artist} · Key ${'$'}{carState.keyShift >= 0 ? '+' + carState.keyShift : carState.keyShift} · Echo ${'$'}{carState.echoLevel}%`;
      document.getElementById('timeLabel').textContent = `${'$'}{formatSec(carState.progressSec)} / ${'$'}{formatSec(carState.durationSec)}`;

      const pBadge = document.getElementById('playStatusBadge');
      if (carState.isPlaying) {
        pBadge.textContent = '▶ 실시간 반주 재생 중 (LIVE PLAYING)';
        pBadge.className = 'badge live';
      } else {
        pBadge.textContent = '⏸ 일시정지 / 대기 중 (PAUSED)';
        pBadge.className = 'badge';
      }

      if (prevPlaying !== carState.isPlaying || prevTrack !== carState.currentTrackId) {
        syncPlaybackEngine();
      }
    }

    function connectWs() {
      const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
      ws = new WebSocket(`${'$'}{proto}//${'$'}{location.host}/ws`);
      ws.onopen = () => {
        const b = document.getElementById('wsBadge');
        b.textContent = '● 차량 서버(:8080) 실시간 연결됨';
        b.className = 'badge live';
        ws.send(JSON.stringify({ event: 'register-monitor', data: {} }));
      };
      ws.onmessage = (ev) => {
        try {
          const msg = JSON.parse(ev.data);
          const event = msg.event || msg.type;
          const data = msg.data || msg;
          if (event === 'car-state-sync') {
            applyCarState(data);
          } else if (event === 'vocal-telemetry') {
            const rms = Math.min(100, Math.round(data.rms || 0));
            const pitch = Math.round(data.pitch || 0);
            const note = data.note || '-';
            document.getElementById('micTelemetryText').textContent = `${'$'}{rms}% · ${'$'}{pitch > 0 ? pitch + 'Hz (' + note + ')' : note}`;
            document.getElementById('micBar').style.width = rms + '%';
          } else if (event === 'audio-pcm-chunk') {
            playIncomingPcmChunk(data.sr || 16000, data.pcm);
          } else if (event === 'remote-command' && data.type === 'TRIGGER_SFX') {
            playSfx(data.sfx || 'cheer');
          }
        } catch (_e) {}
      };
      ws.onclose = () => {
        const b = document.getElementById('wsBadge');
        b.textContent = '● 재연결 중...';
        b.className = 'badge';
        setTimeout(connectWs, 1500);
      };
    }

    function sendRemoteCmd(type, extra) {
      if (!ws || ws.readyState !== 1) return;
      const payload = Object.assign({ type }, extra || {});
      ws.send(JSON.stringify({ event: 'remote-command', data: payload }));
    }

    const canvas = document.getElementById('fftCanvas');
    const ctx2d = canvas.getContext('2d');
    const freqData = new Uint8Array(64);

    function renderLoop() {
      ctx2d.clearRect(0, 0, canvas.width, canvas.height);
      const bars = 48;
      const gap = 5;
      const barW = (canvas.width - gap * (bars + 1)) / bars;

      let avg = 0;
      if (analyser && audioEnabled && carState.isPlaying) {
        analyser.getByteFrequencyData(freqData);
      } else {
        freqData.fill(0);
      }

      for (let i = 0; i < bars; i++) {
        const v = carState.isPlaying ? (freqData[i] / 255) : 0;
        avg += v;
        const h = carState.isPlaying ? Math.max(6, v * (canvas.height - 20)) : 5;
        const x = gap + i * (barW + gap);
        const y = (canvas.height - h) / 2;
        ctx2d.fillStyle = carState.isPlaying ? (i % 2 === 0 ? '#3B82F6' : '#8B5CF6') : '#1E293B';
        ctx2d.beginPath();
        ctx2d.roundRect(x, y, barW, h, 4);
        ctx2d.fill();
      }
      document.getElementById('mrBar').style.width = carState.isPlaying ? Math.min(100, Math.round((avg / bars) * 140)) + '%' : '0%';
      requestAnimationFrame(renderLoop);
    }

    connectWs();
    renderLoop();
  </script>
</body>
</html>
    """.trimIndent()

    private suspend fun handleWebSocketSession(session: DefaultWebSocketServerSession) {
        val clientId = "client_${UUID.randomUUID().toString().take(8)}"
        val client = ConnectedClient(id = clientId, session = session, pin = currentPin)
        clients[clientId] = client

        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    handleIncomingJsonMessage(client, text)
                }
            }
        } catch (_: Exception) {
        } finally {
            clients.remove(clientId)
            if (client.role == "mic") {
                val remainingMics = clients.values.count { it.role == "mic" }
                _serverState.update { it.copy(connectedMicCount = remainingMics) }
                onDirectMicConnectionChanged?.invoke(remainingMics > 0, "Pleos Smart Mic", remainingMics)
                val payload = JSONObject().apply {
                    put("micId", client.id)
                    put("micCount", remainingMics)
                }
                sendToRole("car", "mic-disconnected", payload)
            } else if (client.role == "car") {
                val payload = JSONObject().apply {
                    put("pin", client.pin)
                }
                sendToRole("mic", "car-disconnected", payload)
            }
        }
    }

    private suspend fun handleIncomingJsonMessage(client: ConnectedClient, rawText: String) {
        val root = try {
            JSONObject(rawText)
        } catch (_: Exception) {
            return
        }

        val event = root.optString("event", root.optString("type", ""))
        val data = root.optJSONObject("data") ?: root

        when (event) {
            "register-car" -> {
                val requestedPin = data.optString("pin", "").trim()
                if (requestedPin.matches(Regex("^\\d{4}$"))) {
                    currentPin = requestedPin
                }
                client.role = "car"
                client.pin = currentPin

                val ip = detectLocalLanIp(appContext)
                val wsLanUrl = "ws://$ip:$DEFAULT_PORT/ws"
                val micCount = clients.values.count { it.role == "mic" }

                _serverState.update {
                    it.copy(
                        isRunning = true,
                        lanIp = ip,
                        lanWsUrl = wsLanUrl,
                        activePin = currentPin,
                        connectedMicCount = micCount
                    )
                }

                val resp = JSONObject().apply {
                    put("ok", true)
                    put("pin", currentPin)
                    put("roomId", "pleos-room-$currentPin")
                    put("lanIp", ip)
                    put("port", DEFAULT_PORT)
                    put("lanUrl", wsLanUrl)
                    put("wsUrl", wsLanUrl)
                    put("emulatorUrl", "ws://10.0.2.2:$DEFAULT_PORT/ws")
                    put("micCount", micCount)
                }
                sendEvent(client, "car-registered", resp)

                val readyPayload = JSONObject().apply {
                    put("pin", currentPin)
                    put("roomId", "pleos-room-$currentPin")
                    put("state", roomStateJson)
                }
                sendToRole("mic", "car-ready", readyPayload)
            }

            "join-pin" -> {
                val inputPin = data.optString("pin", "").trim()
                val deviceName = data.optString("deviceName", "Pleos Smart Mic").ifBlank { "Pleos Smart Mic" }

                // Match exact 4-digit PIN, or allow 'AUTO' / single active Car room on same Wi-Fi
                val isPinValid = inputPin == currentPin || inputPin == "AUTO" || inputPin.matches(Regex("^\\d{4}$"))
                if (!isPinValid) {
                    val err = JSONObject().apply {
                        put("ok", false)
                        put("error", "유효하지 않은 4자리 PIN 번호입니다 (차량 PIN: $currentPin)")
                    }
                    sendEvent(client, "join-error", err)
                    return
                }

                client.role = "mic"
                client.pin = currentPin
                client.deviceName = deviceName

                val micCount = clients.values.count { it.role == "mic" }
                val hasCar = clients.values.any { it.role == "car" }
                _serverState.update { it.copy(connectedMicCount = micCount) }

                val okResp = JSONObject().apply {
                    put("ok", true)
                    put("pin", currentPin)
                    put("roomId", "pleos-room-$currentPin")
                    put("carConnected", hasCar)
                    put("state", roomStateJson)
                }
                sendEvent(client, "paired-with-car", okResp)

                val micConnectedPayload = JSONObject().apply {
                    put("micId", client.id)
                    put("deviceName", deviceName)
                    put("pin", currentPin)
                    put("micCount", micCount)
                }
                onDirectMicConnectionChanged?.invoke(true, deviceName, micCount)
                sendToRole("car", "mic-connected", micConnectedPayload)
            }

            "webrtc-offer" -> {
                val payload = JSONObject().apply {
                    put("sdp", data.opt("sdp"))
                    put("from", client.id)
                    put("pin", currentPin)
                }
                sendToRole("car", "webrtc-offer", payload)
            }

            "webrtc-answer" -> {
                val payload = JSONObject().apply {
                    put("sdp", data.opt("sdp"))
                    put("from", client.id)
                    put("pin", currentPin)
                }
                sendToRole("mic", "webrtc-answer", payload)
            }

            "webrtc-ice-candidate" -> {
                val payload = JSONObject().apply {
                    put("candidate", data.opt("candidate"))
                    put("from", client.id)
                    put("pin", currentPin)
                }
                val targetRole = if (client.role == "mic") "car" else "mic"
                sendToRole(targetRole, "webrtc-ice-candidate", payload)
            }

            "vocal-telemetry" -> {
                val note = data.optString("note", "-")
                val payload = JSONObject().apply {
                    put("rms", data.optDouble("rms", 0.0))
                    put("pitch", data.optDouble("pitch", 0.0))
                    put("note", note)
                    put("from", client.id)
                    put("ts", data.optLong("ts", System.currentTimeMillis()))
                }
                sendToRole("car", "vocal-telemetry", payload)
                sendToRole("monitor", "vocal-telemetry", payload)
            }

            "audio-pcm-chunk" -> {
                val sr = data.optInt("sr", 16000)
                val pcmArr = data.optJSONArray("pcm")
                if (pcmArr != null && pcmArr.length() > 0) {
                    val len = pcmArr.length()
                    val shorts = ShortArray(len)
                    for (i in 0 until len) {
                        shorts[i] = pcmArr.optInt(i, 0).coerceIn(-32768, 32767).toShort()
                    }
                    onMicPcmChunk?.invoke(sr, shorts)
                }
                val payload = JSONObject().apply {
                    put("sr", sr)
                    put("pcm", pcmArr)
                    put("pin", currentPin)
                }
                sendToRole("car", "audio-pcm-chunk", payload)
                sendToRole("monitor", "audio-pcm-chunk", payload)
            }

            "register-monitor" -> {
                client.role = "monitor"
                client.pin = currentPin
                val readyPayload = JSONObject().apply {
                    put("ok", true)
                    put("pin", currentPin)
                    put("state", roomStateJson)
                }
                sendEvent(client, "car-state-sync", roomStateJson)
                sendEvent(client, "monitor-registered", readyPayload)
            }

            "remote-command" -> {
                val cmdType = data.optString("type", "")
                if (cmdType == "SELECT_SONG" && data.has("songId")) {
                    roomStateJson.put("currentSongId", data.optString("songId"))
                } else if (cmdType == "START_SONG") {
                    roomStateJson.put("isPlaying", true)
                } else if (cmdType == "PAUSE_SONG" || cmdType == "STOP_SONG") {
                    roomStateJson.put("isPlaying", false)
                } else if (cmdType == "CHANGE_KEY" && data.has("keyShift")) {
                    roomStateJson.put("keyShift", data.optInt("keyShift"))
                } else if (cmdType == "CHANGE_ECHO" && data.has("echoLevel")) {
                    roomStateJson.put("echoLevel", data.optInt("echoLevel"))
                } else if (cmdType == "SET_GUIDE_VOCAL" || cmdType == "TOGGLE_GUIDE_VOCAL" || cmdType == "SET_DEMO_VOCAL") {
                    val nextGuide = when {
                        data.has("guideVocalEnabled") -> data.optBoolean("guideVocalEnabled", false)
                        data.has("demoVocalWave") -> data.optBoolean("demoVocalWave", false)
                        data.has("enabled") -> data.optBoolean("enabled", false)
                        else -> !roomStateJson.optBoolean("guideVocalEnabled", false)
                    }
                    roomStateJson.put("guideVocalEnabled", nextGuide)
                    roomStateJson.put("demoVocalWave", nextGuide)
                    if (!nextGuide) {
                        roomStateJson.put("guidePitchHz", 0.0)
                        roomStateJson.put("guideNote", "-")
                        roomStateJson.put("guideVoiceLevelPercent", 0)
                    }
                    sendToRole("mic", "car-state-sync", roomStateJson)
                    sendToRole("monitor", "car-state-sync", roomStateJson)
                }

                val forwarded = JSONObject(data.toString()).apply {
                    put("from", client.id)
                    put("ts", System.currentTimeMillis())
                }
                if (cmdType.isNotBlank()) {
                    onDirectRemoteCommand?.invoke(cmdType, forwarded)
                }
                sendToRole("car", "remote-command", forwarded)
                sendToRole("monitor", "remote-command", forwarded)
            }

            "car-state-sync" -> {
                val keys = data.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    roomStateJson.put(k, data.opt(k))
                }
                sendToRole("mic", "car-state-sync", roomStateJson)
                sendToRole("monitor", "car-state-sync", roomStateJson)
            }
        }
    }

    private suspend fun sendEvent(client: ConnectedClient, event: String, data: JSONObject) {
        try {
            val envelope = JSONObject().apply {
                put("event", event)
                put("data", data)
            }.toString()
            client.session.send(envelope)
        } catch (_: Exception) {
        }
    }

    private suspend fun sendToRole(role: String, event: String, data: JSONObject) {
        val envelope = JSONObject().apply {
            put("event", event)
            put("data", data)
        }.toString()
        for (target in clients.values) {
            if (target.role == role) {
                try {
                    target.session.send(envelope)
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Lightweight UDP Responder & Beacon on port 8081 so a Smartphone on the same Wi-Fi
     * can auto-discover `ws://<car-ip>:8080/ws` without manual IP entry.
     */
    private fun startUdpDiscoveryResponder() {
        udpDiscoveryJob?.cancel()
        udpDiscoveryJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(DISCOVERY_UDP_PORT))
                    soTimeout = 2500
                }
                val buf = ByteArray(512)
                while (isActive) {
                    val ip = detectLocalLanIp(appContext)
                    val wsUrl = "ws://$ip:$DEFAULT_PORT/ws"
                    val beaconJson = JSONObject().apply {
                        put("type", "PLEOS_CAR_BEACON")
                        put("wsUrl", wsUrl)
                        put("lanIp", ip)
                        put("port", DEFAULT_PORT)
                        put("pin", currentPin)
                    }.toString()

                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        if (msg.startsWith("PLEOS_DISCOVER")) {
                            val replyBytes = beaconJson.toByteArray(Charsets.UTF_8)
                            val replyPacket = DatagramPacket(
                                replyBytes,
                                replyBytes.size,
                                packet.address,
                                packet.port
                            )
                            socket.send(replyPacket)
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Broadcast periodic UDP beacon on local Wi-Fi subnet
                        try {
                            val beaconBytes = beaconJson.toByteArray(Charsets.UTF_8)
                            val bcastPacket = DatagramPacket(
                                beaconBytes,
                                beaconBytes.size,
                                InetAddress.getByName("255.255.255.255"),
                                DISCOVERY_UDP_PORT
                            )
                            socket.send(bcastPacket)
                        } catch (_: Exception) {
                        }
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun stop() {
        udpDiscoveryJob?.cancel()
        udpDiscoveryJob = null
        try {
            engine?.stop(300, 500)
        } catch (_: Exception) {
        }
        engine = null
        scope.cancel()
    }
}
