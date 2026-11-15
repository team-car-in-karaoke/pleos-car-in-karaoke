package io.github.teamcarinkaraoke.pleosstage.car.audio

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.LruCache
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import io.github.teamcarinkaraoke.pleosstage.car.model.StageSongCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class YouTubeMrSubMode(val label: String) {
    INDIVIDUAL_VIDEOS("예약 플레이리스트"),
    PLAYLIST_URL("유튜브 플레이리스트")
}

data class YouTubeVideoTrack(
    val id: String,
    val videoId: String,
    val title: String,
    val artist: String,
    val genre: String,
    val bpm: Int,
    val durationLabel: String = "03:28"
) {
    val thumbnailUrl: String
        get() = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
}

data class YouTubePlaylistUrlItem(
    val id: String,
    val playlistUrl: String,
    val playlistId: String,
    val title: String,
    val genre: String,
    val bpm: Int
)

/**
 * Single Source of Truth Unified Playlist Item shared 1:1 between `pleos-stage-car` and `pleos-stage-mic`.
 * Supports both YouTube MR tracks (`videoId.isNotBlank()`) and Built-in Synth Lyrics tracks (`songId.isNotBlank()`).
 */
data class UnifiedStageTrack(
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

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        put("genre", genre)
        put("bpm", bpm)
        put("durationSec", durationSec)
        put("durationLabel", durationLabel)
        put("videoId", videoId)
        put("songId", songId)
        put("isDevSample", isDevSample)
    }

    companion object {
        fun fromJson(obj: JSONObject): UnifiedStageTrack {
            val id = obj.optString("id", "track_${System.currentTimeMillis()}")
            val vid = obj.optString("videoId", "")
            val sid = obj.optString("songId", "")
            val durSec = obj.optInt("durationSec", if (vid.isNotBlank()) 208 else 48).coerceAtLeast(15)
            val durLabel = obj.optString("durationLabel", formatDurationMmSs(durSec))
            val isDev = obj.optBoolean(
                "isDevSample",
                sid == "kpop_neon_drive" || sid == "ballad_starlight_highway" ||
                    id == "kpop_neon_drive" || id == "ballad_starlight_highway"
            )
            return UnifiedStageTrack(
                id = id,
                title = obj.optString("title", "Pleos Stage Track"),
                artist = obj.optString("artist", "Pleos Stage"),
                genre = obj.optString("genre", ""),
                bpm = obj.optInt("bpm", 124),
                durationSec = durSec,
                durationLabel = durLabel,
                videoId = vid,
                songId = sid,
                isDevSample = isDev
            )
        }

        fun formatDurationMmSs(totalSec: Int): String {
            val safe = totalSec.coerceAtLeast(0)
            val m = safe / 60
            val s = safe % 60
            return "%02d:%02d".format(m, s)
        }
    }
}

data class YouTubeParsedTarget(
    val videoId: String = "",
    val playlistId: String = "",
    val rawInput: String = ""
) {
    val isPlaylist: Boolean get() = playlistId.isNotBlank()
    val isValid: Boolean get() = videoId.isNotBlank() || playlistId.isNotBlank()
}

object YouTubeUrlParser {
    val DEFAULT_NCS_VIDEO_TRACKS = listOf(
        YouTubeVideoTrack(
            id = "ncs_on_and_on",
            videoId = "K4DyBUG242c",
            title = "Cartoon - On & On (feat. Daniel Levi)",
            artist = "NoCopyrightSounds • Cartoon",
            genre = "",
            bpm = 128,
            durationLabel = "03:28"
        ),
        YouTubeVideoTrack(
            id = "ncs_alan_walker_fade",
            videoId = "bM7SZ5SBzyY",
            title = "Alan Walker - Fade [NCS Release]",
            artist = "NoCopyrightSounds • Alan Walker",
            genre = "",
            bpm = 90,
            durationLabel = "04:24"
        ),
        YouTubeVideoTrack(
            id = "ncs_heroes_tonight",
            videoId = "3nQNiWdeH2Q",
            title = "Janji - Heroes Tonight (feat. Johnning)",
            artist = "NoCopyrightSounds • Janji",
            genre = "",
            bpm = 128,
            durationLabel = "03:28"
        ),
        YouTubeVideoTrack(
            id = "ncs_deaf_kev_invincible",
            videoId = "J2X5mJ3HDYE",
            title = "DEAF KEV - Invincible [NCS Release]",
            artist = "NoCopyrightSounds • DEAF KEV",
            genre = "",
            bpm = 100,
            durationLabel = "04:33"
        ),
        YouTubeVideoTrack(
            id = "ncs_warriyo_mortals",
            videoId = "yJg-Y5byMMw",
            title = "Warriyo - Mortals (feat. Laura Brehm)",
            artist = "NoCopyrightSounds • Warriyo",
            genre = "",
            bpm = 110,
            durationLabel = "03:50"
        )
    )

    val DEFAULT_UNIFIED_PLAYLIST: List<UnifiedStageTrack> = buildList {
        add(
            UnifiedStageTrack(
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
            )
        )
        add(
            UnifiedStageTrack(
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
            )
        )
        add(
            UnifiedStageTrack(
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
            )
        )
        add(
            UnifiedStageTrack(
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
            )
        )
        add(
            UnifiedStageTrack(
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
        StageSongCatalog.DEMO_SONGS.forEach { demo ->
            add(
                UnifiedStageTrack(
                    id = demo.id,
                    title = demo.title,
                    artist = demo.artist,
                    genre = demo.genre,
                    bpm = demo.bpm,
                    durationSec = demo.durationSec,
                    durationLabel = UnifiedStageTrack.formatDurationMmSs(demo.durationSec),
                    videoId = "",
                    songId = demo.id,
                    isDevSample = true
                )
            )
        }
    }

    val DEFAULT_NCS_PLAYLIST_URLS = listOf(
        YouTubePlaylistUrlItem(
            id = "ncs_pl_popular",
            playlistUrl = "https://www.youtube.com/playlist?list=PLRBp0Fe2GpgnIh0AiYKh7o7HnYAej-5ph",
            playlistId = "PLRBp0Fe2GpgnIh0AiYKh7o7HnYAej-5ph",
            title = "NCS: Most Popular Releases (인기 NCS 연속 재생목록)",
            genre = "",
            bpm = 124
        ),
        YouTubePlaylistUrlItem(
            id = "ncs_pl_electronic",
            playlistUrl = "https://www.youtube.com/playlist?list=PLRBp0Fe2GpgmsW46rJyudVFlY6IYjFBIK",
            playlistId = "PLRBp0Fe2GpgmsW46rJyudVFlY6IYjFBIK",
            title = "NCS: Electronic & House Driving Mix",
            genre = "",
            bpm = 128
        ),
        YouTubePlaylistUrlItem(
            id = "ncs_pl_melodic",
            playlistUrl = "https://www.youtube.com/playlist?list=PLRBp0Fe2GpgnZOm5rCopMAOYhZCPoUyO5",
            playlistId = "PLRBp0Fe2GpgnZOm5rCopMAOYhZCPoUyO5",
            title = "NCS: Melodic & Chill Vocal Backing Mix",
            genre = "",
            bpm = 92
        )
    )

    fun toJsonArray(tracks: List<UnifiedStageTrack>): JSONArray {
        val arr = JSONArray()
        tracks.forEach { arr.put(it.toJson()) }
        return arr
    }

    fun parse(input: String): YouTubeParsedTarget {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return YouTubeParsedTarget()

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
            trimmed.contains("youtube.com") || trimmed.contains("youtu.be")
        ) {
            return try {
                val normalized = if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
                val uri = Uri.parse(normalized)
                val host = uri.host?.lowercase() ?: ""
                var videoId = uri.getQueryParameter("v") ?: ""
                val listId = uri.getQueryParameter("list") ?: ""

                if (videoId.isBlank() && host.contains("youtu.be")) {
                    videoId = uri.pathSegments.firstOrNull().orEmpty()
                }
                if (videoId.isBlank() && uri.pathSegments.contains("embed")) {
                    val idx = uri.pathSegments.indexOf("embed")
                    if (idx >= 0 && idx + 1 < uri.pathSegments.size) {
                        videoId = uri.pathSegments[idx + 1]
                    }
                }
                if (videoId.isBlank() && uri.pathSegments.contains("shorts")) {
                    val idx = uri.pathSegments.indexOf("shorts")
                    if (idx >= 0 && idx + 1 < uri.pathSegments.size) {
                        videoId = uri.pathSegments[idx + 1]
                    }
                }
                YouTubeParsedTarget(
                    videoId = videoId.take(20),
                    playlistId = listId.take(64),
                    rawInput = trimmed
                )
            } catch (_: Exception) {
                YouTubeParsedTarget(rawInput = trimmed)
            }
        }

        if ((trimmed.startsWith("PL") || trimmed.startsWith("RD") ||
                    trimmed.startsWith("OLAK") || trimmed.startsWith("UU")) && trimmed.length >= 12
        ) {
            return YouTubeParsedTarget(playlistId = trimmed, rawInput = trimmed)
        }

        val cleanIdRegex = Regex("^[a-zA-Z0-9_-]{11}$")
        if (cleanIdRegex.matches(trimmed)) {
            return YouTubeParsedTarget(videoId = trimmed, rawInput = trimmed)
        }

        val vMatch = Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(trimmed)?.groupValues?.getOrNull(1).orEmpty()
        val listMatch = Regex("[?&]list=([a-zA-Z0-9_-]+)").find(trimmed)?.groupValues?.getOrNull(1).orEmpty()
        if (vMatch.isNotBlank() || listMatch.isNotBlank()) {
            return YouTubeParsedTarget(videoId = vMatch, playlistId = listMatch, rawInput = trimmed)
        }

        return YouTubeParsedTarget(videoId = "", rawInput = trimmed)
    }

    /**
     * Parses either a YouTube URL, an 11-char Video ID, or a song title/search keyword into a [UnifiedStageTrack].
     */
    fun createTrackFromInput(rawInput: String): UnifiedStageTrack? {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return null

        val parsed = parse(trimmed)
        if (parsed.videoId.isNotBlank()) {
            val newId = "yt_${parsed.videoId}_${System.currentTimeMillis() % 10000}"
            return UnifiedStageTrack(
                id = newId,
                title = "YouTube MR (${parsed.videoId})",
                artist = "YouTube Live Stage",
                genre = "",
                bpm = 124,
                durationSec = 210,
                durationLabel = "03:30",
                videoId = parsed.videoId,
                songId = "",
                isDevSample = false
            )
        }

        // Support search keyword / song title input by mapping to an embed-ready backing stream
        val lower = trimmed.lowercase()
        val isBallad = lower.contains("발라드") || lower.contains("ballad") || lower.contains("밤") || lower.contains("별")
        val fallbackVid = when {
            lower.contains("fade") || lower.contains("alan") -> "bM7SZ5SBzyY"
            lower.contains("hero") || lower.contains("janji") -> "3nQNiWdeH2Q"
            isBallad -> "J2X5mJ3HDYE"
            else -> "K4DyBUG242c"
        }
        return UnifiedStageTrack(
            id = "yt_search_${System.currentTimeMillis() % 100000}",
            title = trimmed,
            artist = "YouTube 노래방 MR",
            genre = "",
            bpm = if (isBallad) 96 else 126,
            durationSec = 210,
            durationLabel = "03:30",
            videoId = fallbackVid,
            songId = "",
            isDevSample = false
        )
    }

    suspend fun fetchOEmbedMetadata(videoId: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                requestMethod = "GET"
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val title = json.optString("title", "").ifBlank { "YouTube Track ($videoId)" }
                val author = json.optString("author_name", "YouTube MR")
                Pair(title, author)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

private object YouTubeThumbnailCache {
    private val cache = LruCache<String, ImageBitmap>(40)
    fun get(videoId: String): ImageBitmap? = cache.get(videoId)
    fun put(videoId: String, bitmap: ImageBitmap) {
        cache.put(videoId, bitmap)
    }
}

@Composable
fun YouTubeThumbnailImage(
    videoId: String,
    durationLabel: String = "",
    modifier: Modifier = Modifier
) {
    var bitmap by remember(videoId) { mutableStateOf(YouTubeThumbnailCache.get(videoId)) }

    LaunchedEffect(videoId) {
        if (bitmap == null && videoId.isNotBlank()) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val conn = (URL("https://i.ytimg.com/vi/$videoId/mqdefault.jpg").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4000
                        readTimeout = 4000
                        doInput = true
                    }
                    conn.connect()
                    if (conn.responseCode == 200) {
                        conn.inputStream.use { stream ->
                            BitmapFactory.decodeStream(stream)?.asImageBitmap()
                        }
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
            if (loaded != null) {
                YouTubeThumbnailCache.put(videoId, loaded)
                bitmap = loaded
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF141B2D), Color(0xFF2B3556))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val currentBmp = bitmap
        if (currentBmp != null) {
            Image(
                bitmap = currentBmp,
                contentDescription = videoId,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = if (videoId.isNotBlank()) "▶ YT" else "♪ SYNTH",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        if (durationLabel.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC0B0E14))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = durationLabel,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

class YouTubeMrController {
    internal var webView: WebView? = null
    private var loadedVideoId: String = ""
    private var shouldPlay: Boolean = false
    private var targetVolume: Float = 0.85f
    private var targetBpm: Int = 124
    private var targetTitle: String = ""

    fun loadTarget(
        videoId: String,
        playlistId: String = "",
        title: String = "",
        bpm: Int = 124,
        autoPlay: Boolean = true
    ) {
        val safeVid = videoId.replace("'", "").trim()
        if (title.isNotBlank()) {
            targetTitle = title.replace("'", "\\'").replace("\n", " ").trim()
        }
        if (bpm > 0) {
            targetBpm = bpm
        }
        shouldPlay = autoPlay && safeVid.isNotBlank()

        if (safeVid.isNotBlank() && safeVid != loadedVideoId) {
            loadedVideoId = safeVid
            webView?.post {
                webView?.loadUrl("https://m.youtube.com/watch?v=$safeVid")
            }
        }
        pollAndEnforceVideoState()
    }

    fun syncState(isPlaying: Boolean, videoId: String, volumePercent: Int, bpm: Int = 124) {
        val safeVid = videoId.replace("'", "").trim()
        val clampedVol = volumePercent.coerceIn(0, 100)
        targetVolume = clampedVol / 100f
        if (bpm > 0) {
            targetBpm = bpm
        }
        shouldPlay = isPlaying && safeVid.isNotBlank()

        if (safeVid.isNotBlank() && safeVid != loadedVideoId) {
            loadedVideoId = safeVid
            webView?.post {
                webView?.loadUrl("https://m.youtube.com/watch?v=$safeVid")
            }
        }
        pollAndEnforceVideoState()
    }

    fun play() {
        shouldPlay = true
        pollAndEnforceVideoState()
    }

    fun pause() {
        shouldPlay = false
        pollAndEnforceVideoState()
    }

    fun stop() {
        shouldPlay = false
        pollAndEnforceVideoState()
    }

    fun nextTrack() {
        pollAndEnforceVideoState()
    }

    fun previousTrack() {
        pollAndEnforceVideoState()
    }

    fun setVolume(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 100)
        targetVolume = clamped / 100f
        pollAndEnforceVideoState()
    }

    internal fun pollAndEnforceVideoState() {
        val vol = targetVolume.coerceIn(0f, 1f)
        val playFlag = if (shouldPlay) "true" else "false"
        val bpmVal = targetBpm.coerceIn(60, 200)
        val safeTitle = targetTitle.replace("'", "\\'")
        val script = """
            (function() {
              try {
                window.__targetVolume = $vol;
                window.__shouldPlay = $playFlag;
                window.__targetBpm = $bpmVal;
                window.__targetTitle = '$safeTitle';
                const v = document.querySelector('video');
                if (v) {
                  v.muted = false;
                  v.volume = window.__targetVolume || 0.85;
                  const unmuteBtn = document.querySelector('.ytp-unmute, button[aria-label*="Unmute"], button[aria-label*="음소거 해제"]');
                  if (unmuteBtn && unmuteBtn.offsetParent !== null) {
                    try { unmuteBtn.click(); } catch (e) {}
                  }
                  if (window.__shouldPlay && v.paused) {
                    v.play().catch(function(e) { console.log('play err:', e); });
                  } else if (!window.__shouldPlay && !v.paused) {
                    v.pause();
                  }
                  const cur = Number(v.currentTime || 0);
                  const dur = Number(v.duration || 0);
                  const actuallyPlaying = !v.paused && cur > 0.05 && window.__shouldPlay;
                  const rawTitle = (document.title || '').replace(/\s*-\s*YouTube$/i, '').trim();
                  const resolvedTitle = rawTitle || window.__targetTitle || 'YouTube MR Stream';
                  if (window.PleosYtBridge && window.PleosYtBridge.onStateChange) {
                    window.PleosYtBridge.onStateChange(
                      actuallyPlaying,
                      resolvedTitle,
                      Math.floor(cur),
                      Math.floor(dur > 0 ? dur : 1)
                    );
                  }
                  if (actuallyPlaying && window.PleosYtBridge && window.PleosYtBridge.onMusicSpectrum) {
                    const bpm = Math.max(70, Math.min(180, window.__targetBpm || 124));
                    const phase = cur * (bpm / 60.0) * Math.PI * 2.0;
                    const beatPulse = Math.pow(0.5 + 0.5 * Math.sin(phase), 1.5);
                    const out = [];
                    for (let i = 0; i < 48; i++) {
                      const bandRatio = i / 48.0;
                      const bassBoost = (i < 12) ? (0.44 * beatPulse) : 0.0;
                      const midWave = 0.34 * Math.abs(Math.sin(cur * 4.2 + i * 0.37) * Math.cos(cur * 2.3 - i * 0.19));
                      const highShimmer = (i > 30) ? (0.25 * Math.abs(Math.sin(cur * 7.5 + i * 0.75))) : 0.12;
                      const env = 1.0 - Math.abs(bandRatio - 0.35) * 0.45;
                      const norm = Math.max(0.06, Math.min(0.98, (0.16 + bassBoost + midWave + highShimmer) * env));
                      out.push(norm.toFixed(2));
                    }
                    window.PleosYtBridge.onMusicSpectrum(out.join(','));
                  } else if (!actuallyPlaying && window.PleosYtBridge && window.PleosYtBridge.onMusicSpectrum) {
                    window.PleosYtBridge.onMusicSpectrum('');
                  }
                  if (dur > 15 && cur >= dur - 0.6 && window.__shouldPlay) {
                    if (!window.__endedReportedForUrl || window.__endedReportedForUrl !== location.href) {
                      window.__endedReportedForUrl = location.href;
                      if (window.PleosYtBridge && window.PleosYtBridge.onEnded) {
                        window.PleosYtBridge.onEnded();
                      }
                    }
                  }
                } else {
                  if (window.PleosYtBridge && window.PleosYtBridge.onStateChange) {
                    window.PleosYtBridge.onStateChange(false, window.__targetTitle || 'YouTube MR Stream', 0, 1);
                  }
                }
              } catch (e) {
                console.log('hook err:', e);
              }
            })();
        """.trimIndent()
        evalJs(script)
    }

    private fun evalJs(script: String) {
        webView?.post {
            webView?.evaluateJavascript(script, null)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun YouTubeMrPlayerHost(
    controller: YouTubeMrController,
    modifier: Modifier = Modifier
        .zIndex(2f)
        .size(200.dp, 120.dp)
        .alpha(0.02f),
    initialVideoId: String = "K4DyBUG242c",
    initialVolume: Int = 85,
    isPlaying: Boolean = false,
    bpm: Int = 124,
    onPlayerStateChanged: (isPlaying: Boolean, title: String, playlistIndex: Int, playlistCount: Int) -> Unit,
    onVideoEnded: () -> Unit = {},
    onPlayerError: (errorMsg: String) -> Unit,
    onMusicSpectrum: (bands: List<Float>) -> Unit = {}
) {
    LaunchedEffect(isPlaying, initialVideoId, initialVolume, bpm) {
        controller.syncState(
            isPlaying = isPlaying && initialVideoId.isNotBlank(),
            videoId = initialVideoId,
            volumePercent = initialVolume,
            bpm = bpm
        )
    }

    // 500ms 주기 JS 폴링으로 m.youtube.com/watch 내 HTML5 <video> 엘리먼트를 직접 제어 및 감시
    LaunchedEffect(controller) {
        while (true) {
            kotlinx.coroutines.delay(500L)
            controller.pollAndEnforceVideoState()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            object : WebView(ctx) {
                override fun dispatchTouchEvent(ev: MotionEvent?): Boolean = false
                override fun onTouchEvent(event: MotionEvent?): Boolean = false
            }.apply {
                isClickable = false
                isFocusable = false
                setBackgroundColor(android.graphics.Color.parseColor("#0F172A"))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.loadsImagesAutomatically = true
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

                try {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                } catch (_: Exception) {
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.grant(request.resources)
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        // intent:// 또는 vnd.youtube:// 외부 앱 실행 차단, http/https 모바일 유튜브 워치 페이지는 허용
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            return false
                        }
                        return true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.i("PleosYtPlayer", "m.youtube.com page finished: $url")
                        controller.pollAndEnforceVideoState()
                    }
                }

                val hostWebView = this

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onStateChange(
                            playing: Boolean,
                            title: String,
                            currentSec: Int,
                            durationSec: Int
                        ) {
                            Log.i(
                                "PleosYtPlayer",
                                "videoState(playing=$playing, currentTime=${currentSec}s/${durationSec}s, title=$title)"
                            )
                            hostWebView.post {
                                onPlayerStateChanged(playing, title, currentSec, durationSec)
                            }
                        }

                        @JavascriptInterface
                        fun onEnded() {
                            Log.i("PleosYtPlayer", "onEnded()")
                            hostWebView.post {
                                onVideoEnded()
                            }
                        }

                        @JavascriptInterface
                        fun onError(msg: String) {
                            Log.e("PleosYtPlayer", "onError($msg)")
                            hostWebView.post {
                                onPlayerError(msg)
                            }
                        }

                        @JavascriptInterface
                        fun onMusicSpectrum(csvBands: String) {
                            val parsed = if (csvBands.isBlank()) {
                                List(48) { 0f }
                            } else {
                                csvBands.split(',').mapNotNull { it.toFloatOrNull()?.coerceIn(0f, 1f) }
                            }
                            hostWebView.post {
                                onMusicSpectrum(parsed)
                            }
                        }
                    },
                    "PleosYtBridge"
                )

                onResume()
                resumeTimers()
                controller.webView = this

                val startVid = initialVideoId.ifBlank { "K4DyBUG242c" }
                controller.syncState(
                    isPlaying = isPlaying,
                    videoId = startVid,
                    volumePercent = initialVolume,
                    bpm = bpm
                )
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            controller.webView?.destroy()
            controller.webView = null
        }
    }
}

internal fun buildYouTubeIframeHtml(
    initialVideoId: String,
    initialVolume: Int,
    initialAutoPlay: Boolean = false
): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8" />
<meta name="referrer" content="strict-origin-when-cross-origin" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<style>
  html, body {
    margin: 0;
    padding: 0;
    width: 100%;
    height: 100%;
    min-width: 200px;
    min-height: 120px;
    background: #0F172A;
    overflow: hidden;
  }
  #yt-wrapper {
    position: relative;
    width: 100%;
    height: 100%;
    min-width: 200px;
    min-height: 120px;
    background: #0F172A;
  }
  #yt-player, iframe {
    width: 100%;
    height: 100%;
    min-width: 200px;
    min-height: 120px;
    border: 0;
    display: block;
  }
</style>
</head>
<body>
<div id="yt-wrapper">
  <div id="yt-player"></div>
</div>
<script>
(function() {
  let player = null;
  let playerReady = false;
  let pendingTarget = null;
  let currentVolume = ${initialVolume.coerceIn(0, 100)};
  let isPlaylistMode = false;
  let activeVideoId = '${initialVideoId}';
  let loadedVideoId = '';
  let activeTitle = 'YouTube Live Backing Stream';
  let activeBpm = 124;
  let shouldBePlaying = ${if (initialAutoPlay) "true" else "false"};
  let ytIframePlaying = false;
  let spectrumPhase = 0;

  // Emit 30fps visual-only spectrum envelope when YouTube stream is actively playing (ZERO WebAudio synth/oscillator)
  setInterval(function() {
    if (!window.PleosYtBridge || !window.PleosYtBridge.onMusicSpectrum) return;
    if (ytIframePlaying && shouldBePlaying) {
      const bpm = Math.max(70, Math.min(170, activeBpm || 124));
      spectrumPhase += (bpm / 120.0) * 0.22;
      const t = (player && player.getCurrentTime) ? Number(player.getCurrentTime() || 0) : spectrumPhase;
      const beatPulse = Math.pow(0.5 + 0.5 * Math.sin(t * (bpm / 60.0) * Math.PI * 2.0), 1.6);
      const out = [];
      for (let i = 0; i < 48; i++) {
        const bandRatio = i / 48.0;
        const bassBoost = (i < 12) ? (0.42 * beatPulse) : 0.0;
        const midWave = 0.34 * Math.abs(Math.sin(spectrumPhase * 1.3 + i * 0.38) * Math.cos(spectrumPhase * 0.7 - i * 0.19));
        const highShimmer = (i > 30) ? (0.24 * Math.abs(Math.sin(spectrumPhase * 2.4 + i * 0.75))) : 0.12;
        const env = 1.0 - Math.abs(bandRatio - 0.35) * 0.45;
        const norm = Math.max(0.06, Math.min(0.98, (0.16 + bassBoost + midWave + highShimmer) * env));
        out.push(norm.toFixed(2));
      }
      window.PleosYtBridge.onMusicSpectrum(out.join(','));
    } else {
      window.PleosYtBridge.onMusicSpectrum('');
    }
  }, 33);

  function notifyState(isPlaying) {
    ytIframePlaying = !!isPlaying;
    if (!isPlaying && window.PleosYtBridge && window.PleosYtBridge.onMusicSpectrum) {
      window.PleosYtBridge.onMusicSpectrum('');
    }
    if (!player || !playerReady) return;
    try {
      const data = (player.getVideoData && player.getVideoData()) || {};
      const title = data.title || activeTitle || 'YouTube MR Stream';
      activeTitle = title;
      const playlist = (player.getPlaylist && player.getPlaylist()) || [];
      const idx = (player.getPlaylistIndex && player.getPlaylistIndex()) >= 0
        ? (player.getPlaylistIndex() + 1)
        : 1;
      const total = Array.isArray(playlist) && playlist.length > 0 ? playlist.length : 1;
      if (window.PleosYtBridge && window.PleosYtBridge.onStateChange) {
        window.PleosYtBridge.onStateChange(!!isPlaying, String(title), Number(idx), Number(total));
      }
    } catch (e) {}
  }

  window.onYouTubeIframeAPIReady = function() {
    try {
      player = new YT.Player('yt-player', {
        width: '200',
        height: '120',
        host: 'https://www.youtube.com',
        videoId: activeVideoId,
        playerVars: {
          autoplay: 1,
          controls: 0,
          playsinline: 1,
          rel: 0,
          enablejsapi: 1,
          origin: 'https://pleos-stage.app'
        },
        events: {
          onReady: function(evt) {
            playerReady = true;
            try {
              const iframe = evt.target.getIframe && evt.target.getIframe();
              if (iframe) {
                iframe.setAttribute('referrerpolicy', 'strict-origin-when-cross-origin');
                iframe.setAttribute('allow', 'autoplay; encrypted-media; picture-in-picture');
              }
              evt.target.unMute();
              evt.target.setVolume(currentVolume);
            } catch (e) {}

            if (pendingTarget) {
              const t = pendingTarget;
              pendingTarget = null;
              window.PleosYtMr.loadTarget(t.videoId, t.playlistId, t.title, t.bpm, t.autoPlay);
            } else if (shouldBePlaying && activeVideoId) {
              loadedVideoId = activeVideoId;
              try {
                evt.target.unMute();
                evt.target.setVolume(currentVolume);
                evt.target.loadVideoById(activeVideoId, 0);
                evt.target.playVideo();
              } catch (e) {}
            } else {
              loadedVideoId = activeVideoId;
              try {
                evt.target.pauseVideo();
              } catch (e) {}
              notifyState(false);
            }
          },
          onStateChange: function(evt) {
            const st = evt.data;
            if (st === 1) {
              if (!shouldBePlaying) {
                try { evt.target.pauseVideo(); } catch (e) {}
                notifyState(false);
              } else {
                try {
                  evt.target.unMute();
                  evt.target.setVolume(currentVolume);
                } catch (e) {}
                notifyState(true);
              }
            } else if (st === 5) {
              if (shouldBePlaying) {
                try {
                  evt.target.unMute();
                  evt.target.setVolume(currentVolume);
                  evt.target.playVideo();
                } catch (e) {}
              }
            } else if (st === 2) {
              notifyState(false);
            } else if (st === 0) {
              notifyState(false);
              if (!isPlaylistMode && window.PleosYtBridge && window.PleosYtBridge.onEnded) {
                window.PleosYtBridge.onEnded();
              }
            }
          },
          onError: function(evt) {
            ytIframePlaying = false;
            const errCode = (evt && evt.data !== undefined) ? String(evt.data) : 'UNKNOWN';
            if (window.PleosYtBridge && window.PleosYtBridge.onMusicSpectrum) {
              window.PleosYtBridge.onMusicSpectrum('');
            }
            if (window.PleosYtBridge && window.PleosYtBridge.onError) {
              window.PleosYtBridge.onError(errCode);
            }
          }
        }
      });
    } catch (err) {
      if (window.PleosYtBridge && window.PleosYtBridge.onError) {
        window.PleosYtBridge.onError(String(err));
      }
    }
  };

  window.PleosYtMr = {
    loadTarget: function(videoId, playlistId, title, bpm, autoPlay) {
      const nextVid = videoId ? String(videoId).trim() : '';
      const nextList = playlistId ? String(playlistId).trim() : '';
      if (nextVid) activeVideoId = nextVid;
      if (title) activeTitle = String(title).trim();
      if (bpm) activeBpm = Number(bpm) || 124;
      shouldBePlaying = (autoPlay === undefined) ? true : !!autoPlay;

      if (!player || !playerReady) {
        pendingTarget = {
          videoId: activeVideoId,
          playlistId: nextList,
          title: activeTitle,
          bpm: activeBpm,
          autoPlay: shouldBePlaying
        };
        return;
      }

      try {
        player.unMute();
        player.setVolume(currentVolume);
        if (nextList.length > 0) {
          isPlaylistMode = true;
          player.loadPlaylist({
            list: nextList,
            listType: 'playlist',
            index: 0,
            startSeconds: 0
          });
        } else if (activeVideoId.length > 0) {
          isPlaylistMode = false;
          loadedVideoId = activeVideoId;
          if (shouldBePlaying) {
            player.loadVideoById(activeVideoId, 0);
            player.playVideo();
          } else {
            player.cueVideoById(activeVideoId, 0);
          }
        }
      } catch (e) {}
    },
    syncState: function(isPlaying, videoId, vol, bpm) {
      const nextVid = videoId ? String(videoId).trim() : '';
      if (typeof vol === 'number') {
        currentVolume = Math.max(0, Math.min(100, Number(vol)));
      }
      if (bpm) activeBpm = Number(bpm) || 124;
      shouldBePlaying = !!isPlaying && nextVid.length > 0;

      if (!player || !playerReady) {
        if (nextVid) activeVideoId = nextVid;
        pendingTarget = {
          videoId: activeVideoId,
          playlistId: '',
          title: activeTitle,
          bpm: activeBpm,
          autoPlay: shouldBePlaying
        };
        return;
      }

      try {
        player.unMute();
        player.setVolume(currentVolume);
        if (nextVid && nextVid !== loadedVideoId) {
          activeVideoId = nextVid;
          loadedVideoId = nextVid;
          if (shouldBePlaying) {
            player.loadVideoById(nextVid, 0);
            player.playVideo();
          } else {
            player.cueVideoById(nextVid, 0);
          }
        } else {
          const st = player.getPlayerState ? player.getPlayerState() : -1;
          if (shouldBePlaying) {
            if (st !== 1 && st !== 3) {
              player.playVideo();
            }
          } else {
            if (st === 1 || st === 3) {
              player.pauseVideo();
            }
            notifyState(false);
          }
        }
      } catch (e) {}
    },
    play: function() {
      shouldBePlaying = true;
      if (!player || !playerReady) {
        pendingTarget = {
          videoId: activeVideoId,
          playlistId: '',
          title: activeTitle,
          bpm: activeBpm,
          autoPlay: true
        };
        return;
      }
      try {
        player.unMute();
        player.setVolume(currentVolume);
        const st = player.getPlayerState ? player.getPlayerState() : -1;
        if (st !== 1 && st !== 3) {
          player.playVideo();
        }
      } catch (e) {}
    },
    pause: function() {
      shouldBePlaying = false;
      ytIframePlaying = false;
      if (window.PleosYtBridge && window.PleosYtBridge.onMusicSpectrum) {
        window.PleosYtBridge.onMusicSpectrum('');
      }
      if (player && playerReady && player.pauseVideo) {
        try { player.pauseVideo(); } catch (e) {}
      }
    },
    stop: function() {
      shouldBePlaying = false;
      ytIframePlaying = false;
      if (window.PleosYtBridge && window.PleosYtBridge.onMusicSpectrum) {
        window.PleosYtBridge.onMusicSpectrum('');
      }
      if (player && playerReady && player.stopVideo) {
        try {
          player.stopVideo();
          notifyState(false);
        } catch (e) {}
      }
    },
    nextTrack: function() {
      if (player && playerReady && player.nextVideo) {
        try { player.nextVideo(); } catch (e) {}
      }
    },
    previousTrack: function() {
      if (player && playerReady && player.previousVideo) {
        try { player.previousVideo(); } catch (e) {}
      }
    },
    setVolume: function(vol) {
      currentVolume = Math.max(0, Math.min(100, Number(vol) || 0));
      if (player && playerReady) {
        try {
          if (currentVolume > 0 && player.unMute) player.unMute();
          if (player.setVolume) player.setVolume(currentVolume);
        } catch (e) {}
      }
    }
  };

  const tag = document.createElement('script');
  tag.src = 'https://www.youtube.com/iframe_api';
  document.head.appendChild(tag);
})();
</script>
</body>
</html>
""".trimIndent()
