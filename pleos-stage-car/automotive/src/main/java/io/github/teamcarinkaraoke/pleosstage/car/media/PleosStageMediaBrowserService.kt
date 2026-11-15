package io.github.teamcarinkaraoke.pleosstage.car.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.service.media.MediaBrowserService
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.ServiceCompat
import io.github.teamcarinkaraoke.pleosstage.car.MainActivity
import io.github.teamcarinkaraoke.pleosstage.car.audio.UnifiedStageTrack
import io.github.teamcarinkaraoke.pleosstage.car.audio.YouTubeUrlParser

/**
 * Shared singleton bridge linking [PleosStageMediaBrowserService], AAOS Driving View Media Widget,
 * Steering Wheel Media Hardware Keys, and [MainActivity]'s Live Stage Controller.
 */
object PleosMediaSessionBridge {
    private const val TAG = "PleosMediaSession"
    const val MEDIA_ROOT_ID = "pleos_stage_root"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaSession: MediaSession? = null
    private var cachedAlbumArt: Bitmap? = null

    @Volatile
    private var currentTrack: UnifiedStageTrack? =
        if (io.github.teamcarinkaraoke.pleosstage.car.BuildConfig.DEBUG) {
            YouTubeUrlParser.DEFAULT_UNIFIED_PLAYLIST.firstOrNull()
        } else {
            null
        }

    @Volatile
    private var currentPlaylist: List<UnifiedStageTrack> =
        if (io.github.teamcarinkaraoke.pleosstage.car.BuildConfig.DEBUG) {
            YouTubeUrlParser.DEFAULT_UNIFIED_PLAYLIST
        } else {
            emptyList()
        }

    @Volatile
    private var isPlayingState: Boolean = false

    @Volatile
    private var positionMsState: Long = 0L

    @Volatile
    private var lastPublishedMetadataSignature: String = ""

    @Volatile
    private var cachedMetadata: MediaMetadata? = null

    // Stage controller callbacks wired by MainActivity / PleosConnectInfotainmentScreen
    var onPlayCommand: (() -> Unit)? = null
    var onPauseCommand: (() -> Unit)? = null
    var onTogglePlayPauseCommand: (() -> Unit)? = null
    var onSkipToNextCommand: (() -> Unit)? = null
    var onSkipToPreviousCommand: (() -> Unit)? = null
    var onStopCommand: (() -> Unit)? = null
    var onSeekToCommand: ((Long) -> Unit)? = null
    var onPlayFromMediaIdCommand: ((String) -> Unit)? = null
    var onPlayFromSearchCommand: ((String) -> Unit)? = null
    var onTaskRemovedCleanup: (() -> Unit)? = null

    // Registered by PleosStageMediaBrowserService to enter/leave foreground with playback
    var playbackStateListener: ((Boolean) -> Unit)? = null

    val isPlaying: Boolean get() = isPlayingState
    val currentTrackTitle: String?
        get() = currentTrack?.title?.takeIf { it.isNotBlank() && it != "예약된 곡이 없습니다" }
    val currentTrackArtist: String?
        get() = currentTrack?.artist?.takeIf { it.isNotBlank() && !it.startsWith("상단 검색창") }

    private fun setPlayingState(playing: Boolean, forceRefreshNotification: Boolean = false) {
        val changed = isPlayingState != playing
        isPlayingState = playing
        if (changed || (playing && forceRefreshNotification)) {
            mainHandler.post { playbackStateListener?.invoke(playing) }
        }
    }

    private val sessionCallback = object : MediaSession.Callback() {
        override fun onPlay() {
            Log.i(TAG, "MediaSession Callback -> onPlay()")
            mainHandler.post {
                val playCb = onPlayCommand
                if (playCb != null) {
                    playCb.invoke()
                } else {
                    onTogglePlayPauseCommand?.invoke()
                }
            }
        }

        override fun onPause() {
            Log.i(TAG, "MediaSession Callback -> onPause()")
            mainHandler.post {
                val pauseCb = onPauseCommand
                if (pauseCb != null) {
                    pauseCb.invoke()
                } else {
                    onTogglePlayPauseCommand?.invoke()
                }
            }
        }

        override fun onSkipToNext() {
            Log.i(TAG, "MediaSession Callback -> onSkipToNext()")
            mainHandler.post {
                onSkipToNextCommand?.invoke()
            }
        }

        override fun onSkipToPrevious() {
            Log.i(TAG, "MediaSession Callback -> onSkipToPrevious()")
            mainHandler.post {
                onSkipToPreviousCommand?.invoke()
            }
        }

        override fun onStop() {
            Log.i(TAG, "MediaSession Callback -> onStop()")
            mainHandler.post {
                val stopCb = onStopCommand
                if (stopCb != null) {
                    stopCb.invoke()
                } else {
                    onPauseCommand?.invoke()
                }
            }
        }

        override fun onSeekTo(pos: Long) {
            Log.i(TAG, "MediaSession Callback -> onSeekTo($pos)")
            mainHandler.post {
                onSeekToCommand?.invoke(pos.coerceAtLeast(0L))
            }
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            Log.i(TAG, "MediaSession Callback -> onPlayFromMediaId($mediaId)")
            if (!mediaId.isNullOrBlank()) {
                mainHandler.post {
                    onPlayFromMediaIdCommand?.invoke(mediaId)
                }
            }
        }

        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            val resolvedQuery = query?.trim().orEmpty().ifEmpty {
                val extraTitle = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE)?.trim().orEmpty()
                val extraArtist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)?.trim().orEmpty()
                listOf(extraArtist, extraTitle).filter { it.isNotEmpty() }.joinToString(" ")
            }
            Log.i(TAG, "MediaSession Callback -> onPlayFromSearch(raw='$query', resolved='$resolvedQuery')")
            mainHandler.post {
                if (resolvedQuery.isEmpty()) {
                    // Empty query means "play something": resume the current track
                    onPlay()
                } else {
                    val searchCb = onPlayFromSearchCommand
                    if (searchCb != null) {
                        searchCb.invoke(resolvedQuery)
                    } else {
                        onPlay()
                    }
                }
            }
        }

        override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
            @Suppress("DEPRECATION")
            val keyEvent = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                Log.i(TAG, "Steering Wheel / Media Key Event -> keyCode=${keyEvent.keyCode}")
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        onPlay()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        onPause()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_HEADSETHOOK -> {
                        mainHandler.post { onTogglePlayPauseCommand?.invoke() }
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        onSkipToNext()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        onSkipToPrevious()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                        onStop()
                        return true
                    }
                }
            }
            return super.onMediaButtonEvent(mediaButtonIntent)
        }
    }

    @Synchronized
    fun getOrCreateSession(context: Context): MediaSession {
        val appCtx = context.applicationContext
        val existing = mediaSession
        if (existing != null) {
            if (!existing.isActive) {
                existing.isActive = true
            }
            return existing
        }

        val launchIntent = Intent(appCtx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appCtx,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val session = MediaSession(appCtx, "PleosStageMediaSession").apply {
            setSessionActivity(pendingIntent)
            setCallback(sessionCallback, mainHandler)
            isActive = true
        }
        mediaSession = session
        lastPublishedMetadataSignature = ""

        // Immediately publish initial metadata & playback state so AAOS Media Widget is never blank
        applyMetadataAndStateInternal(
            session = session,
            track = currentTrack,
            isPlaying = isPlayingState,
            positionMs = positionMsState,
            stopped = false
        )
        Log.i(TAG, "PleosStage MediaSession created & activated")
        return session
    }

    fun handleMediaButtonIntent(context: Context, intent: Intent?) {
        if (intent == null) return
        @Suppress("DEPRECATION")
        val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        val session = getOrCreateSession(context)
        session.controller.dispatchMediaButtonEvent(keyEvent)
    }

    fun updatePlaybackAndMetadata(
        context: Context,
        track: UnifiedStageTrack?,
        isPlaying: Boolean,
        positionMs: Long,
        playlist: List<UnifiedStageTrack> = emptyList()
    ) {
        val prevTrackSignature = "${currentTrack?.id}|${currentTrack?.title}|${currentTrack?.artist}"
        val nextTrackSignature = "${track?.id}|${track?.title}|${track?.artist}"
        val trackChanged = prevTrackSignature != nextTrackSignature
        currentTrack = track
        if (isPlaying && playbackStateListener == null) {
            runCatching {
                val appCtx = context.applicationContext
                appCtx.startService(Intent(appCtx, PleosStageMediaBrowserService::class.java))
            }.onFailure { e ->
                Log.w(TAG, "Deferred service start skipped: ${e.message}")
            }
        }
        setPlayingState(isPlaying, forceRefreshNotification = trackChanged)
        positionMsState = positionMs.coerceAtLeast(0L)
        currentPlaylist = playlist
        val session = getOrCreateSession(context)
        applyMetadataAndStateInternal(
            session = session,
            track = track,
            isPlaying = isPlaying,
            positionMs = positionMsState,
            stopped = false
        )
    }

    fun getBrowseTracks(): List<UnifiedStageTrack> {
        return currentPlaylist
    }

    fun republishCurrentState(context: Context) {
        val session = getOrCreateSession(context)
        lastPublishedMetadataSignature = ""
        applyMetadataAndStateInternal(
            session = session,
            track = currentTrack,
            isPlaying = isPlayingState,
            positionMs = positionMsState,
            stopped = false
        )
    }

    fun handleTaskRemoved(context: Context) {
        Log.i(TAG, "onTaskRemoved -> stopping audio and saving state")
        setPlayingState(false)
        mainHandler.post {
            onTaskRemovedCleanup?.invoke()
        }
        val session = mediaSession
        if (session != null) {
            applyMetadataAndStateInternal(
                session = session,
                track = currentTrack,
                isPlaying = false,
                positionMs = positionMsState,
                stopped = false
            )
        }
    }

    fun stopAndDeactivateSession() {
        setPlayingState(false)
        val session = mediaSession ?: return
        applyMetadataAndStateInternal(
            session = session,
            track = currentTrack,
            isPlaying = false,
            positionMs = positionMsState,
            stopped = false
        )
    }

    private fun applyMetadataAndStateInternal(
        session: MediaSession,
        track: UnifiedStageTrack?,
        isPlaying: Boolean,
        positionMs: Long,
        stopped: Boolean
    ) {
        val safeTitle = track?.title?.takeIf { it.isNotBlank() && it != "예약된 곡이 없습니다" }
            ?: "테스트 음원"
        val safeArtist = track?.artist?.takeIf {
            it.isNotBlank() && !it.startsWith("상단 검색창")
        } ?: "Pleos Stage · 테스트 음원"
        val safeId = track?.id?.takeIf { it.isNotBlank() } ?: "ncs_short_test_30s"
        val durationMs = ((track?.durationSec?.takeIf { it > 0 } ?: 30) * 1000L)
        val art = getOrCreateStageAlbumArt()

        val browseTracks = getBrowseTracks()
        val activeIndex = browseTracks.indexOfFirst { it.id == safeId }.let { if (it >= 0) it else 0 }
        val activeQueueId = (activeIndex + 1).toLong()

        val metadataSignature = "$safeId|$safeTitle|$safeArtist|$durationMs|${browseTracks.size}"
        if (metadataSignature != lastPublishedMetadataSignature) {
            lastPublishedMetadataSignature = metadataSignature
            val queueItems = browseTracks.mapIndexed { idx, item ->
                val desc = MediaDescription.Builder()
                    .setMediaId(item.id)
                    .setTitle(item.title)
                    .setSubtitle(item.artist)
                    .setDescription("카인노래방 (Car in Karaoke)")
                    .setIconBitmap(art)
                    .build()
                MediaSession.QueueItem(desc, (idx + 1).toLong())
            }
            session.setQueueTitle("카인노래방 (Car in Karaoke)")
            session.setQueue(queueItems)

            val metadata = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, safeId)
                .putString(MediaMetadata.METADATA_KEY_TITLE, safeTitle)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, safeTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, safeArtist)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, safeArtist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "카인노래방 (Car in Karaoke)")
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, "카인노래방 (Car in Karaoke)")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, art)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, art)
                .build()
            cachedMetadata = metadata
            session.setMetadata(metadata)
        } else {
            cachedMetadata?.let { session.setMetadata(it) }
        }

        val playbackStateCode = when {
            stopped -> PlaybackState.STATE_STOPPED
            isPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }

        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or
            PlaybackState.ACTION_PLAY_FROM_SEARCH

        val playbackState = PlaybackState.Builder()
            .setActions(actions)
            .setActiveQueueItemId(activeQueueId)
            .setState(
                playbackStateCode,
                positionMs.coerceIn(0L, durationMs),
                1.0f,
                SystemClock.elapsedRealtime()
            )
            .build()

        if (!stopped && !session.isActive) {
            session.isActive = true
        }
        session.setPlaybackState(playbackState)
    }

    private fun getOrCreateStageAlbumArt(): Bitmap {
        val existing = cachedAlbumArt
        if (existing != null && !existing.isRecycled) return existing

        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                size.toFloat(),
                size.toFloat(),
                intArrayOf(0xFF0F172A.toInt(), 0xFF1E293B.toInt(), 0xFF2B95F6.toInt()),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            color = 0xFF38BDF8.toInt()
        }
        canvas.drawCircle(size / 2f, size / 2f, 76f, ringPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 36f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("STAGE", size / 2f, size / 2f + 13f, textPaint)
        cachedAlbumArt = bmp
        return bmp
    }
}

/**
 * Official AAOS & Pleos Connect MediaBrowserService implementation.
 * Exposes the active [MediaSession] token and browse tree to the vehicle's
 * Driving View Media Widget and Steering Wheel Media Controls.
 */
class PleosStageMediaBrowserService : MediaBrowserService() {

    companion object {
        private const val TAG = "PleosMediaBrowserSvc"
        private const val CHANNEL_ID = "pleos_stage_playback"
        private const val NOTIFICATION_ID = 4829
    }

    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        val session = PleosMediaSessionBridge.getOrCreateSession(this)
        sessionToken = session.sessionToken
        createNotificationChannel()
        PleosMediaSessionBridge.playbackStateListener = { playing ->
            if (playing) enterForeground() else exitForeground()
        }
        // Pleos Music/Audio guide: make the service "Started" so onTaskRemoved() is delivered
        // even when the system only binds to it.
        runCatching {
            startService(Intent(this, PleosStageMediaBrowserService::class.java))
        }.onFailure { e ->
            Log.w(TAG, "startService from onCreate skipped (${e.javaClass.simpleName}): ${e.message}")
        }
        if (PleosMediaSessionBridge.isPlaying) {
            enterForeground()
        }
        Log.i(TAG, "PleosStageMediaBrowserService onCreate -> sessionToken registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PleosMediaSessionBridge.handleMediaButtonIntent(this, intent)
        if (PleosMediaSessionBridge.isPlaying) {
            enterForeground()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        PleosMediaSessionBridge.playbackStateListener = null
        exitForeground()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "카인노래방 재생",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }.onFailure { e ->
            Log.w(TAG, "createNotificationChannel failed: ${e.message}")
        }
    }

    private fun buildPlaybackNotification(): Notification {
        createNotificationChannel()
        val session = PleosMediaSessionBridge.getOrCreateSession(this)
        val safeIconRes = applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_media_play
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(safeIconRes)
            .setContentTitle(PleosMediaSessionBridge.currentTrackTitle ?: "카인노래방")
            .setContentText(PleosMediaSessionBridge.currentTrackArtist ?: "Car in Karaoke")
            .setContentIntent(session.controller.sessionActivity)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun enterForeground() {
        runCatching {
            startService(Intent(this, PleosStageMediaBrowserService::class.java))
        }
        runCatching {
            val notification = buildPlaybackNotification()
            val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                fgType
            )
            isForeground = true
        }.onFailure { e ->
            // Safely absorbs ForegroundServiceStartNotAllowedException / SecurityException on API 34
            Log.w(TAG, "startForeground safely suppressed (${e.javaClass.simpleName}): ${e.message}")
        }
    }

    private fun exitForeground() {
        if (!isForeground) return
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }.onFailure { e ->
            Log.w(TAG, "stopForeground failed (${e.javaClass.simpleName}): ${e.message}")
        }
        isForeground = false
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        Log.i(TAG, "onGetRoot called by clientPackageName=$clientPackageName")
        PleosMediaSessionBridge.republishCurrentState(this)
        val extras = Bundle().apply {
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2)
        }
        return BrowserRoot(PleosMediaSessionBridge.MEDIA_ROOT_ID, extras)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowser.MediaItem>>
    ) {
        val items = mutableListOf<MediaBrowser.MediaItem>()
        if (parentId == PleosMediaSessionBridge.MEDIA_ROOT_ID) {
            val tracks = PleosMediaSessionBridge.getBrowseTracks()
            for (track in tracks) {
                val desc = MediaDescription.Builder()
                    .setMediaId(track.id)
                    .setTitle(track.title)
                    .setSubtitle(track.artist)
                    .setDescription("카인노래방 (Car in Karaoke) • ${track.durationLabel}")
                    .build()
                items.add(
                    MediaBrowser.MediaItem(
                        desc,
                        MediaBrowser.MediaItem.FLAG_PLAYABLE
                    )
                )
            }
        }
        result.sendResult(items)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val removedPkg = rootIntent?.component?.packageName ?: rootIntent?.`package`
        if (removedPkg != null && removedPkg != packageName) {
            super.onTaskRemoved(rootIntent)
            return
        }
        Log.i(TAG, "PleosStageMediaBrowserService onTaskRemoved (pkg=$removedPkg)")
        // Pleos Music/Audio guide order: stopMedia() -> stopSelf() -> super.onTaskRemoved()
        PleosMediaSessionBridge.handleTaskRemoved(applicationContext)
        exitForeground()
        runCatching { stopSelf() }
        super.onTaskRemoved(rootIntent)
    }
}
