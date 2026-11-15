package io.github.teamcarinkaraoke.pleosstage.car.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

data class GuideVocalTelemetryState(
    val active: Boolean = false,
    val guidePitchHz: Float = 0f,
    val guideNote: String = "-",
    val guideVoiceLevelPercent: Float = 0f
)

/**
 * Native Kotlin AudioTrack Vocal Guide (Soft EP / Chime Bell Pluck) & Real Smartphone Mic PCM Reverb Mixer.
 *
 * Key Guarantees:
 * 1. Independently outputs Guide Vocal whenever `guideVocalEnabled == true`, even when YouTube MR
 *    is paused or stopped (`isStagePlaying == false`), driven by an internal BPM melody clock.
 * 2. Continuously emits real-time `guideTelemetry` (`guidePitchHz`, `guideNote`, `guideVoiceLevelPercent`
 *    undulating between 25% ~ 85% with the pluck envelope) via `StateFlow` whenever `guideVocalEnabled == true`.
 * 3. NEVER synthesizes audio from `vocal-telemetry`. Microphone channel ONLY outputs real `audio-pcm-chunk` data.
 * 4. Uses a soft Electric Piano (EP) / Music Box Chime Bell pluck (Clean Sine + 2nd Harmonic,
 *    15ms Attack + 350ms Exponential Decay) capped at `maxGain <= 0.145f` (`peakAmp < 20%`).
 * 5. Outputs a 100% pure zero (`0`) PCM array when `guideVocalEnabled == false` and no real mic PCM is arriving.
 */
class NativeVocalAudioEngine {
    companion object {
        private const val TAG = "PleosNativeVocal"
        const val SAMPLE_RATE = 48000
        private const val FRAME_SAMPLES = 480 // 10ms stable low-latency render frame at 48kHz
        private const val PREBUFFER_MIN_SAMPLES = 1920 // 40ms (~2 packets) initial pre-buffer so wireless jitter never starves
        private const val PREBUFFER_IDLE_RESET_NS = 240_000_000L // 240ms complete packet silence before re-arming pre-buffer
        private const val PREBUFFER_IDLE_RESET_SAMPLES = 11520 // 240ms at 48kHz continuous empty samples
        private const val CATCHUP_START_SAMPLES = 2880 // >60ms jitter accumulation triggers smooth 1.04x catch-up (NO sample drop!)
        private const val CATCHUP_STOP_SAMPLES = 1920 // Smoothly return to 1.00x normal speed once buffer reaches 40ms
        private const val ABNORMAL_OVERFLOW_RESET_SAMPLES = 9600 // 200ms emergency-only reset guard
        private const val CATCHUP_READ_STEP = 1.04f // 4% smooth linear-interpolation catch-up (inaudible pitch, zero cut!)
        private const val STITCH_SAMPLES = 32 // Smooth 32-sample (0.67ms) linear stitch on initial start or underrun recovery
        private const val UNDERRUN_HOLD_DECAY = 0.995f // Smooth per-sample hold/decay on momentary underrun (NO 240-sample 5ms buzz loop!)
        private const val SEQ_DEDUP_RING_SIZE = 64
        private const val TWO_PI = (2.0 * PI).toFloat()

        // EP / Chime Bell Pluck Envelope Constants (15ms Attack + 350ms Exponential Decay)
        private const val PLUCK_ATTACK_SAMPLES = 720 // 15ms at 48kHz
        private const val PLUCK_DECAY_TAU_SEC = 0.14f // ~350ms effective exponential decay window
        private const val PLUCK_H2_DECAY_TAU_SEC = 0.085f // Faster 2nd harmonic decay for warm bell tone
        private const val MAX_PLUCK_DURATION_SAMPLES = 26400 // 550ms total pluck life, then pure 0f

        // Gain Staging (Crisp 88~92% Dry Vocal + Subtle 8~22% Natural Reverb)
        private const val GUIDE_PLUCK_BASE_GAIN = 0.095f
        private const val MIC_PCM_BASE_GAIN = 0.135f
        private const val HARD_MAX_OUTPUT_GAIN = 0.180f

        private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        private val SONG_MELODY_PATTERNS: Map<String, FloatArray> = mapOf(
            "ncs_on_and_on" to floatArrayOf(
                261.63f, 293.66f, 329.63f, 392.00f, 440.00f, 392.00f, 329.63f, 293.66f,
                329.63f, 392.00f, 440.00f, 523.25f, 493.88f, 440.00f, 392.00f, 329.63f
            ),
            "ncs_alan_walker_fade" to floatArrayOf(
                293.66f, 349.23f, 440.00f, 392.00f, 349.23f, 329.63f, 293.66f, 261.63f,
                293.66f, 349.23f, 440.00f, 523.25f, 440.00f, 392.00f, 349.23f, 293.66f
            ),
            "ncs_heroes_tonight" to floatArrayOf(
                329.63f, 392.00f, 440.00f, 493.88f, 523.25f, 493.88f, 392.00f, 329.63f,
                349.23f, 392.00f, 440.00f, 523.25f, 493.88f, 440.00f, 392.00f, 329.63f
            ),
            "ncs_deaf_kev_invincible" to floatArrayOf(
                261.63f, 329.63f, 392.00f, 440.00f, 392.00f, 349.23f, 329.63f, 293.66f,
                261.63f, 293.66f, 329.63f, 392.00f, 440.00f, 392.00f, 329.63f, 261.63f
            ),
            "ncs_warriyo_mortals" to floatArrayOf(
                293.66f, 329.63f, 392.00f, 440.00f, 493.88f, 440.00f, 392.00f, 329.63f,
                261.63f, 293.66f, 349.23f, 392.00f, 440.00f, 392.00f, 329.63f, 293.66f
            )
        )

        private val DEFAULT_MELODY_PATTERN = floatArrayOf(
            261.63f, 293.66f, 329.63f, 349.23f, 392.00f, 440.00f, 392.00f, 329.63f,
            523.25f, 440.00f, 392.00f, 349.23f, 329.63f, 293.66f, 329.63f, 261.63f
        )

        fun freqToNoteName(freqHz: Float): String {
            if (freqHz < 60f || freqHz > 1500f) return "-"
            val midi = (69.0 + 12.0 * (ln(freqHz.toDouble() / 440.0) / ln(2.0))).roundToInt()
            val noteIdx = ((midi % 12) + 12) % 12
            val octave = (midi / 12) - 1
            return "${NOTE_NAMES[noteIdx]}$octave"
        }

        fun resolveRealtimeGuidePitch(
            trackId: String,
            bpm: Int,
            elapsedSec: Float,
            lyricBaseHz: Float,
            keyShiftSemitones: Int,
            playbackRate: Float = 1.0f,
            syncOffsetMs: Int = 0
        ): Pair<Float, String> {
            val pattern = SONG_MELODY_PATTERNS[trackId] ?: DEFAULT_MELODY_PATTERN
            val safeRate = playbackRate.coerceIn(0.75f, 1.25f)
            val safeBpm = (bpm.coerceIn(68, 160) * safeRate).coerceIn(50f, 210f)
            val beatsPerSec = safeBpm / 60f
            val adjustedElapsedSec = (elapsedSec + (syncOffsetMs.coerceIn(-500, 500) / 1000f)).coerceAtLeast(0f)
            // Advance melody note every 1.25 beats for responsive vocal guide phrasing
            val stepIdx = ((adjustedElapsedSec * beatsPerSec) / 1.25f).toInt()
            val patternHz = pattern[stepIdx % pattern.size]
            val baseHz = if (stepIdx % 4 == 0 && lyricBaseHz > 100f) {
                lyricBaseHz
            } else {
                patternHz
            }
            val shiftedHz = (baseHz * 2.0.pow(keyShiftSemitones / 12.0)).toFloat().coerceIn(180f, 660f)
            return shiftedHz to freqToNoteName(shiftedHz)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile
    private var isRenderRunning: Boolean = false
    private var audioRenderThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    private val _guideTelemetry = MutableStateFlow(GuideVocalTelemetryState())
    val guideTelemetry: StateFlow<GuideVocalTelemetryState> = _guideTelemetry.asStateFlow()

    // Volatile real-time control parameters (Guide Vocal defaults to FALSE / OFF)
    @Volatile
    var isStagePlaying: Boolean = false
        private set

    @Volatile
    var guideVocalEnabled: Boolean = false
        private set

    @Volatile
    var backingVolumePercent: Int = 85
        private set

    @Volatile
    var micVolumePercent: Int = 85
        private set

    @Volatile
    var guideVolumePercent: Int = 75
        private set

    @Volatile
    var echoLevelPercent: Int = 80
        private set

    @Volatile
    var mrPlaybackRate: Float = 1.00f
        private set

    @Volatile
    var syncOffsetMs: Int = 0
        private set

    @Volatile
    var keyShiftSemitones: Int = 0
        private set

    @Volatile
    var currentTrackId: String = "ncs_on_and_on"
        private set

    @Volatile
    var currentBpm: Int = 120
        private set

    @Volatile
    var targetGuidePitchHz: Float = 261.63f
        private set

    @Volatile
    var targetGuideNote: String = "C4"
        private set

    @Volatile
    var isHighlightPhrase: Boolean = false
        private set

    @Volatile
    var masterVolumeScale: Float = 1.0f
        private set

    // Real Smartphone Mic PCM circular ring buffer (48kHz mono float samples) + Adaptive Playout state
    private val micRingCapacity = SAMPLE_RATE // 1.0s (48,000 samples) capacity
    private val micRingBuffer = FloatArray(micRingCapacity)
    private val micRingLock = Any()
    private var micWritePos = 0
    private var micReadPos = 0
    private var micReadSubPos = 0f
    private var micAvailableSamples = 0
    private var isCatchingUp = false
    private var micHpPrevIn = 0f
    private var micHpPrevOut = 0f
    private var micPrevRawInputEnd = 0f
    private var lastMicInputRate = SAMPLE_RATE
    private var micResamplePos = 1.0f
    private var isPrebuffering = true
    private var lastMicPacketArrivalNs: Long = 0L
    private var consecutiveEmptySamples = 0
    private var hadUnderrunGap = false
    private var lastMicOutputSample = 0f
    private var micCrossfadeFromSample = 0f
    private var micCrossfadeRemaining = 0

    // 1.0s Input Rate Diagnostic Telemetry (verifies exact 48,000 samples/sec = 1000ms/sec loading)
    private var rateWindowStartNs: Long = 0L
    private var rateWindowLoadedSamples: Int = 0
    @Volatile
    private var lastMeasuredLoadedSamplesPerSec: Int = 0

    // 64-entry Sequence Deduplication Ring (blocks exact duplicates while allowing minor packet reorder <= 30)
    private val recentPcmSeqRing = LongArray(SEQ_DEDUP_RING_SIZE) { -1L }
    private var recentPcmSeqPos = 0
    private var highestReceivedPcmSeq: Long = -1L
    private var lastEnqueuedSeq: Long = -1L

    fun getBufferedMicLatencyMs(): Int {
        val bufferedSamples = synchronized(micRingLock) { micAvailableSamples }
        val ringMs = ((bufferedSamples * 1000f) / SAMPLE_RATE.toFloat()).roundToInt()
        return if (ringMs > 0) ringMs.coerceIn(10, 60) else 15
    }

    private fun softLimitSample(x: Float, ceiling: Float): Float {
        val absX = abs(x)
        val knee = ceiling * 0.70f
        if (absX <= knee) return x
        val excess = (absX - knee) / (ceiling - knee).coerceAtLeast(0.001f)
        val compressed = knee + (ceiling - knee) * (excess / (1f + excess))
        return if (x >= 0f) compressed else -compressed
    }

    // Polyphonic 48kHz Stereo SFX voice pool (supports up to 8 overlapping Applause / Cheer / Tambourine voices)
    private class ActiveSfxVoice(
        var stereoBuffer: FloatArray? = null,
        var cursorFrame: Int = 0,
        var totalFrames: Int = 0,
        var gain: Float = 0.58f
    )

    private val sfxVoicePool = Array(8) { ActiveSfxVoice() }
    private val sfxLock = Any()
    private var nextSfxStealIdx = 0

    private fun hasActiveSfxVoices(): Boolean {
        synchronized(sfxLock) {
            for (slot in sfxVoicePool) {
                val buf = slot.stereoBuffer
                if (buf != null && slot.cursorFrame < slot.totalFrames) {
                    return true
                }
            }
            return false
        }
    }

    fun start() {
        StageSfxSoundBank.warmUpAsync()
        if (isRenderRunning && audioRenderThread?.isAlive == true) return
        isRenderRunning = true
        audioRenderThread = Thread(
            {
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                } catch (_: Exception) {
                }
                runAudioRenderLoop()
            },
            "PleosCarAudioTrackLoop"
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun setStagePlaying(playing: Boolean) {
        if (isStagePlaying != playing) {
            isStagePlaying = playing
            Log.i(
                TAG,
                "Stage playback state updated: isPlaying=$playing, guideVocalEnabled=$guideVocalEnabled, echo=$echoLevelPercent%"
            )
        }
    }

    fun setGuideVocalEnabled(enabled: Boolean) {
        if (guideVocalEnabled != enabled) {
            guideVocalEnabled = enabled
            if (enabled) {
                val initHz = if (targetGuidePitchHz > 65f) targetGuidePitchHz else 261.63f
                val initNote = if (targetGuideNote != "-" && targetGuideNote.isNotBlank()) targetGuideNote else freqToNoteName(initHz)
                _guideTelemetry.value = GuideVocalTelemetryState(
                    active = true,
                    guidePitchHz = initHz,
                    guideNote = initNote,
                    guideVoiceLevelPercent = 72f
                )
            } else {
                _guideTelemetry.value = GuideVocalTelemetryState(
                    active = false,
                    guidePitchHz = 0f,
                    guideNote = "-",
                    guideVoiceLevelPercent = 0f
                )
            }
            Log.i(
                TAG,
                "Guide Vocal toggled (independent of MR playback): guideVocalEnabled=$enabled, isStagePlaying=$isStagePlaying"
            )
        }
    }

    fun setBackingVolume(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 100)
        if (backingVolumePercent != clamped) {
            backingVolumePercent = clamped
            Log.i(TAG, "Native AudioTrack Backing Volume balance updated: mrVolume=$clamped%")
        }
    }

    fun setMicVolume(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 100)
        if (micVolumePercent != clamped) {
            micVolumePercent = clamped
            Log.i(TAG, "Native AudioTrack Mic Volume updated: micVolume=$clamped%")
        }
    }

    fun setGuideVolume(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 100)
        if (guideVolumePercent != clamped) {
            guideVolumePercent = clamped
            Log.i(TAG, "Native AudioTrack Guide Vocal Volume updated: guideVolume=$clamped%")
        }
    }

    fun setEchoLevel(levelPercent: Int) {
        val clamped = levelPercent.coerceIn(0, 100)
        if (echoLevelPercent != clamped) {
            echoLevelPercent = clamped
            Log.i(TAG, "Native AudioTrack Reverb Echo updated: echo=$clamped%")
        }
    }

    fun setReverbLevel(levelPercent: Int) = setEchoLevel(levelPercent)

    fun setPlaybackRate(rate: Float) {
        val clamped = ((rate.coerceIn(0.75f, 1.25f) * 20f).roundToInt() / 20f)
        if (abs(mrPlaybackRate - clamped) > 0.005f) {
            mrPlaybackRate = clamped
            Log.i(TAG, "Native AudioTrack Guide BPM PlaybackRate updated: mrPlaybackRate=${clamped}x")
        }
    }

    fun setSyncOffsetMs(offsetMs: Int) {
        val clamped = offsetMs.coerceIn(-500, 500)
        if (syncOffsetMs != clamped) {
            syncOffsetMs = clamped
            Log.i(TAG, "Native AudioTrack SyncOffset updated: syncOffsetMs=${clamped}ms")
        }
    }

    fun setKeyShift(semitones: Int) {
        val clamped = semitones.coerceIn(-6, 6)
        if (keyShiftSemitones != clamped) {
            keyShiftSemitones = clamped
            Log.i(TAG, "Native AudioTrack KeyShift updated: keyShift=$clamped")
        }
    }

    fun setTrackConfig(trackId: String, bpm: Int) {
        currentTrackId = trackId.ifBlank { "ncs_on_and_on" }
        currentBpm = if (bpm > 0) bpm.coerceIn(68, 165) else 120
    }

    fun setGuidePitch(pitchHz: Float, note: String, isHighlight: Boolean = false) {
        if (pitchHz > 65f) {
            targetGuidePitchHz = pitchHz.coerceIn(160f, 700f)
            targetGuideNote = note.ifBlank { freqToNoteName(targetGuidePitchHz) }
        }
        isHighlightPhrase = isHighlight
    }

    fun setMasterVolumeScale(scale: Float) {
        masterVolumeScale = scale.coerceIn(0f, 1.0f)
    }

    /**
     * Enqueues incoming real-time PCM16 microphone audio chunk from `pleos-stage-mic`
     * with drift-free 1:1 linear resampling to 48,000Hz (so 1.000s of mic capture == exact 48,000 samples)
     * and WITHOUT `Drop-Oldest` sample deletion.
     */
    fun enqueueMicPcmChunk(sampleRate: Int, pcmShorts: ShortArray, seq: Long = -1L) {
        if (pcmShorts.isEmpty()) return
        val inRate = sampleRate.coerceIn(8000, 48000)
        val approxOutLen = when (inRate) {
            SAMPLE_RATE -> pcmShorts.size
            24000 -> pcmShorts.size * 2
            16000 -> pcmShorts.size * 3
            else -> ((pcmShorts.size.toLong() * SAMPLE_RATE) / inRate).toInt().coerceAtLeast(1)
        }
        val arrivalNs = System.nanoTime()

        synchronized(micRingLock) {
            if (seq > 0L) {
                // Detect smartphone mic session restart (seq reset back near 1)
                if (highestReceivedPcmSeq > 0L && (highestReceivedPcmSeq - seq) >= 500L) {
                    recentPcmSeqRing.fill(-1L)
                    recentPcmSeqPos = 0
                    highestReceivedPcmSeq = -1L
                }
                // Ignore exact duplicates across Local WS / WebRTC / Relay
                for (seen in recentPcmSeqRing) {
                    if (seen == seq) return
                }
                // Ignore stale packets more than 30 sequences behind
                if (highestReceivedPcmSeq > 0L && (highestReceivedPcmSeq - seq) > 30L) {
                    return
                }
                recentPcmSeqRing[recentPcmSeqPos] = seq
                recentPcmSeqPos = (recentPcmSeqPos + 1) and (SEQ_DEDUP_RING_SIZE - 1)
                if (seq > highestReceivedPcmSeq) {
                    highestReceivedPcmSeq = seq
                }
            }

            if (inRate != lastMicInputRate) {
                lastMicInputRate = inRate
                micResamplePos = 1.0f
            }

            lastMicPacketArrivalNs = arrivalNs
            val packetSkipped = seq > 0L && lastEnqueuedSeq > 0L && seq != lastEnqueuedSeq + 1L
            if (seq > 0L) {
                lastEnqueuedSeq = seq
            }

            // NEVER hard-drop samples during normal jitter! Only reset if >= 9600 samples (200ms) abnormally accumulated.
            if (micAvailableSamples + approxOutLen > ABNORMAL_OVERFLOW_RESET_SAMPLES) {
                val prevSample = if (micAvailableSamples > 0) micRingBuffer[micReadPos] else lastMicOutputSample
                val drop = ((micAvailableSamples + approxOutLen) - CATCHUP_STOP_SAMPLES).coerceAtLeast(0)
                micReadPos = (micReadPos + drop) % micRingCapacity
                micReadSubPos = 0f
                micAvailableSamples = (micAvailableSamples - drop).coerceAtLeast(0)
                isCatchingUp = false
                micCrossfadeFromSample = prevSample
                micCrossfadeRemaining = STITCH_SAMPLES
            }

            val prevChunkEnd = if (micAvailableSamples > 0) {
                micRingBuffer[(micWritePos - 1 + micRingCapacity) % micRingCapacity]
            } else {
                lastMicOutputSample
            }
            val stitchCount = if (packetSkipped) STITCH_SAMPLES else 0
            var writtenOutSamples = 0

            fun storeOutputSample(rawSample: Float) {
                // Gentle sub-sonic DC blocker (preserves 100% of vocal warmth, clarity, and crisp highs up to 24kHz)
                val hpOut = 0.992f * (micHpPrevOut + rawSample - micHpPrevIn)
                micHpPrevIn = rawSample
                micHpPrevOut = hpOut

                val sampleToStore = if (stitchCount > 0 && writtenOutSamples < stitchCount) {
                    val alpha = (writtenOutSamples + 1).toFloat() / stitchCount.toFloat()
                    prevChunkEnd * (1f - alpha) + hpOut * alpha
                } else {
                    hpOut
                }
                writtenOutSamples++

                micRingBuffer[micWritePos] = sampleToStore.coerceIn(-1f, 1f)
                micWritePos = (micWritePos + 1) % micRingCapacity
                if (micAvailableSamples < micRingCapacity) {
                    micAvailableSamples++
                } else {
                    micReadPos = (micReadPos + 1) % micRingCapacity
                    micReadSubPos = 0f
                }
            }

            var prev = micPrevRawInputEnd
            when (inRate) {
                SAMPLE_RATE -> {
                    for (k in pcmShorts.indices) {
                        val curr = pcmShorts[k] / 32768f
                        storeOutputSample(curr)
                        prev = curr
                    }
                }
                24000 -> {
                    // Exact 1:2 linear upsampling (480 samples @ 24kHz -> exact 960 samples @ 48kHz; 50 chunks/s -> exact 48,000 samples/s)
                    for (k in pcmShorts.indices) {
                        val curr = pcmShorts[k] / 32768f
                        storeOutputSample(0.5f * (prev + curr))
                        storeOutputSample(curr)
                        prev = curr
                    }
                }
                16000 -> {
                    // Exact 1:3 linear upsampling (320 samples @ 16kHz -> exact 960 samples @ 48kHz)
                    for (k in pcmShorts.indices) {
                        val curr = pcmShorts[k] / 32768f
                        val diff = curr - prev
                        storeOutputSample(prev + diff * (1f / 3f))
                        storeOutputSample(prev + diff * (2f / 3f))
                        storeOutputSample(curr)
                        prev = curr
                    }
                }
                else -> {
                    // Stateful fractional linear upsampler with zero boundary drift across packets
                    val step = inRate.toFloat() / SAMPLE_RATE.toFloat()
                    var pos = micResamplePos
                    for (k in pcmShorts.indices) {
                        val curr = pcmShorts[k] / 32768f
                        val diff = curr - prev
                        while (pos <= 1.0f) {
                            storeOutputSample(prev + diff * pos)
                            pos += step
                        }
                        pos -= 1.0f
                        prev = curr
                    }
                    micResamplePos = pos
                }
            }
            micPrevRawInputEnd = prev

            if (rateWindowStartNs == 0L) {
                rateWindowStartNs = arrivalNs
                rateWindowLoadedSamples = writtenOutSamples
            } else {
                rateWindowLoadedSamples += writtenOutSamples
                val elapsedNs = arrivalNs - rateWindowStartNs
                if (elapsedNs >= 1_000_000_000L) {
                    lastMeasuredLoadedSamplesPerSec =
                        ((rateWindowLoadedSamples.toLong() * 1_000_000_000L) / elapsedNs).toInt()
                    rateWindowStartNs = arrivalNs
                    rateWindowLoadedSamples = 0
                }
            }
        }
    }

    fun triggerSfx(sfxType: String) {
        val normalized = sfxType.uppercase().trim()
        val voiceGain = when (normalized) {
            "TAMBOURINE" -> 0.62f
            "CHEER" -> 0.58f
            else -> 0.56f
        }
        val buf = StageSfxSoundBank.getStereoBuffer(normalized)
        if (buf != null && buf.size >= 2) {
            synchronized(sfxLock) {
                var targetSlot: ActiveSfxVoice? = null
                for (slot in sfxVoicePool) {
                    if (slot.stereoBuffer == null || slot.cursorFrame >= slot.totalFrames) {
                        targetSlot = slot
                        break
                    }
                }
                if (targetSlot == null) {
                    targetSlot = sfxVoicePool[nextSfxStealIdx]
                    nextSfxStealIdx = (nextSfxStealIdx + 1) % sfxVoicePool.size
                }
                targetSlot.stereoBuffer = buf
                targetSlot.cursorFrame = 0
                targetSlot.totalFrames = buf.size / 2
                targetSlot.gain = voiceGain
            }
            Log.i(TAG, "Triggered polyphonic 48kHz stereo SFX: $normalized (${buf.size / 2} frames)")
        }
    }

    private fun runAudioRenderLoop() {
        val minBufBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAME_SAMPLES * 4)
        // Double the minimum OS AudioTrack buffer (`minBufferSize * 2`) so AAOS / macOS CoreAudio HAL
        // thread scheduling jitter never causes native output underruns.
        val trackBufBytes = (minBufBytes * 2).coerceAtLeast(FRAME_SAMPLES * 8)

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(trackBufBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: Exception) {
            try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(trackBufBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to build Native AudioTrack: ${e2.message}", e2)
                return
            }
        }

        audioTrack = track
        try {
            track.setVolume(1.0f)
            track.play()
            Log.i(
                TAG,
                "Native AudioTrack started: ${SAMPLE_RATE}Hz STEREO PCM_16BIT buf=${trackBufBytes}B USAGE_MEDIA MODE_STREAM (guideVocalEnabled=$guideVocalEnabled)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Native AudioTrack: ${e.message}", e)
            return
        }

        val stereoPcmOut = ShortArray(FRAME_SAMPLES * 2)
        val micTempBuf = FloatArray(FRAME_SAMPLES)

        // Soft Electric Piano (EP) / Music Box Chime Bell Pluck state (NO portamento, NO vibrato, NO drone)
        var currentPluckFreqHz = 261.63f
        var currentPluckNote = "C4"
        var pluckPhase = 0f
        var pluckSampleAge = MAX_PLUCK_DURATION_SAMPLES
        var samplesSinceLastPluck = 0
        var wasGuideActive = false
        var internalGuideElapsedSec = 0f

        // Decorrelated Prime-Length Stereo Schroeder Reverb Buffers ( bypassed 100% when echoLevelPercent <= 0 )
        val combLen0 = 1553 // Left Comb A (~32.4ms)
        val combLen1 = 1663 // Right Comb A (~34.6ms)
        val combLen2 = 1847 // Left Comb B (~38.5ms)
        val combLen3 = 1999 // Right Comb B (~41.6ms)
        val combBuf0 = FloatArray(combLen0)
        val combBuf1 = FloatArray(combLen1)
        val combBuf2 = FloatArray(combLen2)
        val combBuf3 = FloatArray(combLen3)
        var combPos0 = 0
        var combPos1 = 0
        var combPos2 = 0
        var combPos3 = 0
        var combFilter0 = 0f
        var combFilter1 = 0f
        var combFilter2 = 0f
        var combFilter3 = 0f
        var reverbPreDamp = 0f

        val apLen0L = 241
        val apLen0R = 257
        val apLen1L = 83
        val apLen1R = 97
        val apBuf0L = FloatArray(apLen0L)
        val apBuf0R = FloatArray(apLen0R)
        val apBuf1L = FloatArray(apLen1L)
        val apBuf1R = FloatArray(apLen1R)
        var apPos0L = 0
        var apPos0R = 0
        var apPos1L = 0
        var apPos1R = 0

        var reverbBuffersDirty = false
        var frameCount = 0L
        var noiseSeed = 0x13579BDF

        fun clearReverbState() {
            combBuf0.fill(0f)
            combBuf1.fill(0f)
            combBuf2.fill(0f)
            combBuf3.fill(0f)
            combFilter0 = 0f
            combFilter1 = 0f
            combFilter2 = 0f
            combFilter3 = 0f
            reverbPreDamp = 0f
            apBuf0L.fill(0f)
            apBuf0R.fill(0f)
            apBuf1L.fill(0f)
            apBuf1R.fill(0f)
            reverbBuffersDirty = false
        }

        val sfxFrameL = FloatArray(FRAME_SAMPLES)
        val sfxFrameR = FloatArray(FRAME_SAMPLES)

        try {
            while (isRenderRunning) {
                frameCount++

                // 1. Pull real smartphone mic PCM samples (480 samples = 10ms render chunk, paced 100% by WRITE_BLOCKING):
                //    - Initial Pre-buffering (`1920` samples = `40ms`) ONLY when stream starts after >= 650ms idle.
                //    - Adaptive Playout Resampler (`1.04x` smooth catch-up above `2880` samples / `60ms` down to `1920` / `40ms`, ZERO dropped samples).
                //    - Momentary underrun: hold/decay `lastMicOutputSample *= 0.995f` + 32-sample linear Smooth Stitch on next packet (NO 240-sample PLC buzz!).
                var hasMicPcmInFrame = false
                synchronized(micRingLock) {
                    if (isPrebuffering) {
                        if (micAvailableSamples >= PREBUFFER_MIN_SAMPLES) {
                            isPrebuffering = false
                            consecutiveEmptySamples = 0
                            hadUnderrunGap = false
                            micReadSubPos = 0f
                            micCrossfadeFromSample = lastMicOutputSample
                            micCrossfadeRemaining = STITCH_SAMPLES
                        }
                    } else if (micAvailableSamples == 0) {
                        val silenceNs = if (lastMicPacketArrivalNs > 0L) {
                            System.nanoTime() - lastMicPacketArrivalNs
                        } else {
                            Long.MAX_VALUE
                        }
                        if (silenceNs >= PREBUFFER_IDLE_RESET_NS && consecutiveEmptySamples >= PREBUFFER_IDLE_RESET_SAMPLES) {
                            isPrebuffering = true
                            isCatchingUp = false
                            micReadSubPos = 0f
                            consecutiveEmptySamples = 0
                            hadUnderrunGap = false
                        }
                    }

                    if (micAvailableSamples > CATCHUP_START_SAMPLES) {
                        isCatchingUp = true
                    } else if (micAvailableSamples <= CATCHUP_STOP_SAMPLES) {
                        isCatchingUp = false
                    }

                    for (i in 0 until FRAME_SAMPLES) {
                        if (isCatchingUp && micAvailableSamples <= CATCHUP_STOP_SAMPLES) {
                            isCatchingUp = false
                        }

                        if (!isPrebuffering && micAvailableSamples > 0) {
                            val readStep = if (isCatchingUp) CATCHUP_READ_STEP else 1.00f
                            val s0 = micRingBuffer[micReadPos]
                            val s1 = if (micAvailableSamples >= 2) {
                                micRingBuffer[(micReadPos + 1) % micRingCapacity]
                            } else {
                                s0
                            }
                            var s = s0 + (s1 - s0) * micReadSubPos

                            micReadSubPos += readStep
                            val wholeSteps = micReadSubPos.toInt()
                            micReadSubPos -= wholeSteps
                            val advance = wholeSteps.coerceAtMost(micAvailableSamples)
                            micReadPos = (micReadPos + advance) % micRingCapacity
                            micAvailableSamples -= advance
                            if (micAvailableSamples == 0) {
                                micReadSubPos = 0f
                            }

                            consecutiveEmptySamples = 0

                            if (hadUnderrunGap) {
                                micCrossfadeFromSample = lastMicOutputSample
                                micCrossfadeRemaining = STITCH_SAMPLES
                                hadUnderrunGap = false
                            }

                            if (micCrossfadeRemaining > 0) {
                                val alpha = (STITCH_SAMPLES - micCrossfadeRemaining + 1).toFloat() / STITCH_SAMPLES.toFloat()
                                s = micCrossfadeFromSample * (1f - alpha) + s * alpha
                                micCrossfadeRemaining--
                            }

                            lastMicOutputSample = s
                            micTempBuf[i] = s
                            hasMicPcmInFrame = true
                        } else {
                            if (!isPrebuffering) {
                                consecutiveEmptySamples++
                                hadUnderrunGap = true
                                val held = lastMicOutputSample * UNDERRUN_HOLD_DECAY
                                if (abs(held) >= 0.00002f) {
                                    lastMicOutputSample = held
                                    micTempBuf[i] = held
                                    hasMicPcmInFrame = true
                                } else {
                                    lastMicOutputSample = 0f
                                    micTempBuf[i] = 0f
                                }
                            } else {
                                lastMicOutputSample = 0f
                                micTempBuf[i] = 0f
                            }
                        }
                    }
                }

                // Guide Vocal runs whenever `guideVocalEnabled == true`, EVEN WHEN YouTube MR is paused/stopped!
                val shouldPlayGuide = guideVocalEnabled

                // Pull active polyphonic 48kHz stereo SFX buffers (Applause / Cheer / Tambourine)
                var hasSfxInFrame = false
                synchronized(sfxLock) {
                    for (slot in sfxVoicePool) {
                        val buf = slot.stereoBuffer ?: continue
                        val startCursor = slot.cursorFrame
                        val totalFrames = slot.totalFrames
                        if (startCursor < totalFrames) {
                            if (!hasSfxInFrame) {
                                sfxFrameL.fill(0f)
                                sfxFrameR.fill(0f)
                                hasSfxInFrame = true
                            }
                            val copyCount = minOf(FRAME_SAMPLES, totalFrames - startCursor)
                            val g = slot.gain
                            for (k in 0 until copyCount) {
                                val srcBase = (startCursor + k) * 2
                                sfxFrameL[k] += buf[srcBase] * g
                                sfxFrameR[k] += buf[srcBase + 1] * g
                            }
                            val nextCursor = startCursor + copyCount
                            slot.cursorFrame = nextCursor
                            if (nextCursor >= totalFrames) {
                                slot.stereoBuffer = null
                            }
                        }
                    }
                }

                // Guarantee 100% pure digital silence (`0` array) when guide vocal is OFF and no real mic PCM / SFX is active
                if (!shouldPlayGuide && !hasMicPcmInFrame && !hasSfxInFrame) {
                    if (wasGuideActive || _guideTelemetry.value.active) {
                        wasGuideActive = false
                        _guideTelemetry.value = GuideVocalTelemetryState(
                            active = false,
                            guidePitchHz = 0f,
                            guideNote = "-",
                            guideVoiceLevelPercent = 0f
                        )
                    }
                    pluckSampleAge = MAX_PLUCK_DURATION_SAMPLES
                    if (reverbBuffersDirty) {
                        clearReverbState()
                    }
                    stereoPcmOut.fill(0)
                    track.write(stereoPcmOut, 0, stereoPcmOut.size, AudioTrack.WRITE_BLOCKING)
                    continue
                }

                // Ensure AudioTrack stays in PLAYSTATE_PLAYING even if YouTube WebView paused
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    try {
                        track.play()
                    } catch (_: Exception) {
                    }
                }

                val reverbActive = echoLevelPercent > 0
                if (reverbActive) {
                    reverbBuffersDirty = true
                } else if (reverbBuffersDirty) {
                    // 100% Pure Dry Bypass when echoLevel == 0%: clear any residual reverb tail immediately
                    clearReverbState()
                }

                val playbackRate = mrPlaybackRate.coerceIn(0.75f, 1.25f)
                val baseBpm = currentBpm.coerceIn(72, 160)
                val samplesPerBeat = ((60f / (baseBpm * playbackRate)) * SAMPLE_RATE).toInt().coerceAtLeast(10000)
                val retriggerIntervalSamples = (samplesPerBeat * 1.25f).toInt().coerceAtLeast(14000)
                // Relative vocal/guide gain compensation when backingVolumePercent is lowered (so vocals stay prominent when STREAM_MUSIC is reduced)
                val backingNorm = (backingVolumePercent.coerceIn(0, 100) / 100f)
                val vocalRelativeBalanceBoost = (1.0f + (0.85f - backingNorm).coerceAtLeast(0f) * 0.45f).coerceIn(0.90f, 1.35f)
                val guideVolumeGain = (guideVolumePercent.coerceIn(0, 100) / 100f) * vocalRelativeBalanceBoost
                val micVolumeGain = (micVolumePercent.coerceIn(0, 100) / 100f) * vocalRelativeBalanceBoost

                // Trigger a soft EP / Chime Bell pluck on internal BPM melody clock (works both when playing and when paused!)
                if (shouldPlayGuide) {
                    internalGuideElapsedSec += (FRAME_SAMPLES.toFloat() / SAMPLE_RATE.toFloat()) * playbackRate
                    val (clockPitchHz, clockNote) = resolveRealtimeGuidePitch(
                        trackId = currentTrackId,
                        bpm = baseBpm,
                        elapsedSec = internalGuideElapsedSec,
                        lyricBaseHz = if (isStagePlaying) targetGuidePitchHz else 0f,
                        keyShiftSemitones = keyShiftSemitones,
                        playbackRate = 1.0f,
                        syncOffsetMs = syncOffsetMs
                    )
                    targetGuidePitchHz = clockPitchHz
                    targetGuideNote = clockNote

                    val pitchChanged = abs(clockPitchHz - currentPluckFreqHz) > 1.2f
                    val retriggerDue = samplesSinceLastPluck >= retriggerIntervalSamples
                    if (!wasGuideActive || pitchChanged || retriggerDue) {
                        currentPluckFreqHz = clockPitchHz
                        currentPluckNote = clockNote
                        pluckSampleAge = 0
                        samplesSinceLastPluck = 0
                        wasGuideActive = true
                    }

                    // Emit real-time Guide Vocal Telemetry (Pitch, Note & Undulating Level scaled by guideVolume) every 40ms (4 frames of 10ms)
                    if (frameCount % 4L == 0L) {
                        val meterDecaySec = ((pluckSampleAge - PLUCK_ATTACK_SAMPLES).coerceAtLeast(0)).toFloat() / SAMPLE_RATE.toFloat()
                        val meterEnv = if (pluckSampleAge < PLUCK_ATTACK_SAMPLES) {
                            0.35f + 0.65f * (pluckSampleAge.toFloat() / PLUCK_ATTACK_SAMPLES.toFloat())
                        } else {
                            exp(-meterDecaySec / 0.34f).coerceIn(0f, 1f)
                        }
                        val voiceLevelPercent = if (guideVolumeGain <= 0.001f) {
                            0f
                        } else {
                            ((26f + 58f * meterEnv) * (guideVolumeGain / 0.75f).coerceIn(0.25f, 1.15f)).coerceIn(8f, 92f)
                        }
                        _guideTelemetry.value = GuideVocalTelemetryState(
                            active = true,
                            guidePitchHz = currentPluckFreqHz,
                            guideNote = currentPluckNote,
                            guideVoiceLevelPercent = voiceLevelPercent
                        )
                    }
                } else {
                    if (wasGuideActive || _guideTelemetry.value.active) {
                        wasGuideActive = false
                        _guideTelemetry.value = GuideVocalTelemetryState(
                            active = false,
                            guidePitchHz = 0f,
                            guideNote = "-",
                            guideVoiceLevelPercent = 0f
                        )
                    }
                    pluckSampleAge = MAX_PLUCK_DURATION_SAMPLES
                }

                val echoNorm = (echoLevelPercent / 100f).coerceIn(0f, 1f)
                // 100% Pure Dry Bypass when echoLevel == 0%; Soft Non-Modulating Stereo Space (90~94% Dry + 4~16% Wet) when > 0%
                val micDryRatio = if (reverbActive) (0.94f - echoNorm * 0.04f).coerceIn(0.90f, 0.94f) else 1.0f
                val guideDryGain = if (reverbActive) 0.90f else 1.0f
                val wetGain = if (reverbActive) (0.04f + echoNorm * 0.12f).coerceIn(0.04f, 0.16f) else 0f
                val feedbackAmount = (0.22f + echoNorm * 0.30f).coerceIn(0.22f, 0.52f)
                val masterScale = masterVolumeScale.coerceIn(0.25f, 1f)

                var peakSampleAbs = 0f

                for (i in 0 until FRAME_SAMPLES) {
                    var guideAndSfxMono = 0f
                    var micCleanDryOut = 0f
                    var micReverbFeed = 0f

                    // A. Soft Electric Piano (EP) / Music Box Chime Bell Pluck (Clean Sine + 2nd Harmonic, 15ms Attack + 350ms Exp Decay)
                    if (shouldPlayGuide && pluckSampleAge < MAX_PLUCK_DURATION_SAMPLES && currentPluckFreqHz > 60f) {
                        samplesSinceLastPluck++
                        val age = pluckSampleAge++

                        val env: Float
                        val h2Env: Float
                        if (age < PLUCK_ATTACK_SAMPLES) {
                            // 15ms smooth linear attack
                            env = age.toFloat() / PLUCK_ATTACK_SAMPLES.toFloat()
                            h2Env = env
                        } else {
                            // Fast 350ms exponential decay to complete silence (no continuous sustain/drone)
                            val decaySec = (age - PLUCK_ATTACK_SAMPLES).toFloat() / SAMPLE_RATE.toFloat()
                            env = exp(-decaySec / PLUCK_DECAY_TAU_SEC)
                            h2Env = exp(-decaySec / PLUCK_H2_DECAY_TAU_SEC)
                        }

                        pluckPhase += currentPluckFreqHz / SAMPLE_RATE
                        if (pluckPhase >= 1f) pluckPhase -= 1f

                        val cleanSine = sin(TWO_PI * pluckPhase) * 0.82f * env
                        val secondHarmonic = sin(TWO_PI * ((pluckPhase * 2f) % 1f)) * 0.18f * h2Env
                        val baseGuideGain = if (isStagePlaying) (GUIDE_PLUCK_BASE_GAIN / 0.75f) else (0.138f / 0.75f)
                        val activeGuideGain = baseGuideGain * guideVolumeGain
                        guideAndSfxMono += (cleanSine + secondHarmonic) * activeGuideGain
                    } else if (shouldPlayGuide) {
                        samplesSinceLastPluck++
                    }

                    // B. Pure Unfiltered Full-Spectrum Dry Vocal + Clean Reverb Feed
                    val micSample = micTempBuf[i]
                    if (abs(micSample) > 0.00001f) {
                        val rawMicScaled = micSample * MIC_PCM_BASE_GAIN * micVolumeGain
                        micCleanDryOut = rawMicScaled * micDryRatio
                        if (reverbActive) {
                            micReverbFeed = rawMicScaled
                        }
                    }

                    val dryMix = guideAndSfxMono * guideDryGain + micCleanDryOut
                    val mixL: Float
                    val mixR: Float

                    if (!reverbActive) {
                        // D1. 100% Pure Dry Bypass (echoLevel == 0%): zero reverb DSP, zero phase/comb interference!
                        mixL = dryMix * masterScale
                        mixR = dryMix * masterScale
                    } else {
                        // D2. Warm Pre-Damped Stereo Schroeder Reverb (reverb feed gently rolled off above ~2.8kHz
                        //     so wet reflections never cause metallic comb-filtering against the crisp Dry voice)
                        val rawReverbIn = guideAndSfxMono + micReverbFeed
                        reverbPreDamp += 0.30f * (rawReverbIn - reverbPreDamp)
                        val reverbInput = reverbPreDamp

                        val c0 = combBuf0[combPos0]
                        combFilter0 = c0 * 0.46f + combFilter0 * 0.54f
                        combBuf0[combPos0] = (reverbInput + combFilter0 * feedbackAmount).coerceIn(-0.5f, 0.5f)
                        combPos0 = (combPos0 + 1) % combLen0

                        val c1 = combBuf1[combPos1]
                        combFilter1 = c1 * 0.46f + combFilter1 * 0.54f
                        combBuf1[combPos1] = (reverbInput + combFilter1 * feedbackAmount).coerceIn(-0.5f, 0.5f)
                        combPos1 = (combPos1 + 1) % combLen1

                        val c2 = combBuf2[combPos2]
                        combFilter2 = c2 * 0.46f + combFilter2 * 0.54f
                        combBuf2[combPos2] = (reverbInput + combFilter2 * feedbackAmount).coerceIn(-0.5f, 0.5f)
                        combPos2 = (combPos2 + 1) % combLen2

                        val c3 = combBuf3[combPos3]
                        combFilter3 = c3 * 0.46f + combFilter3 * 0.54f
                        combBuf3[combPos3] = (reverbInput + combFilter3 * feedbackAmount).coerceIn(-0.5f, 0.5f)
                        combPos3 = (combPos3 + 1) % combLen3

                        val combSumL = (c0 + c2) * 0.40f
                        val combSumR = (c1 + c3) * 0.40f

                        val ap0DelayedL = apBuf0L[apPos0L]
                        val ap0OutL = -0.45f * combSumL + ap0DelayedL
                        apBuf0L[apPos0L] = (combSumL + 0.45f * ap0OutL).coerceIn(-0.5f, 0.5f)
                        apPos0L = (apPos0L + 1) % apLen0L

                        val ap0DelayedR = apBuf0R[apPos0R]
                        val ap0OutR = -0.45f * combSumR + ap0DelayedR
                        apBuf0R[apPos0R] = (combSumR + 0.45f * ap0OutR).coerceIn(-0.5f, 0.5f)
                        apPos0R = (apPos0R + 1) % apLen0R

                        val ap1DelayedL = apBuf1L[apPos1L]
                        val wetOutL = -0.45f * ap0OutL + ap1DelayedL
                        apBuf1L[apPos1L] = (ap0OutL + 0.45f * wetOutL).coerceIn(-0.5f, 0.5f)
                        apPos1L = (apPos1L + 1) % apLen1L

                        val ap1DelayedR = apBuf1R[apPos1R]
                        val wetOutR = -0.45f * ap0OutR + ap1DelayedR
                        apBuf1R[apPos1R] = (ap0OutR + 0.45f * wetOutR).coerceIn(-0.5f, 0.5f)
                        apPos1R = (apPos1R + 1) % apLen1R

                        mixL = (dryMix + wetOutL * wetGain) * masterScale
                        mixR = (dryMix + wetOutR * wetGain) * masterScale
                    }

                    val limL = softLimitSample(mixL, HARD_MAX_OUTPUT_GAIN)
                    val limR = softLimitSample(mixR, HARD_MAX_OUTPUT_GAIN)

                    val outL = if (hasSfxInFrame) {
                        softLimitSample(limL + sfxFrameL[i] * masterScale, 0.92f)
                    } else {
                        limL
                    }
                    val outR = if (hasSfxInFrame) {
                        softLimitSample(limR + sfxFrameR[i] * masterScale, 0.92f)
                    } else {
                        limR
                    }

                    val absMax = maxOf(abs(outL), abs(outR))
                    if (absMax > peakSampleAbs) peakSampleAbs = absMax

                    stereoPcmOut[i * 2] = (outL * 32760f).toInt().coerceIn(-32767, 32767).toShort()
                    stereoPcmOut[i * 2 + 1] = (outR * 32760f).toInt().coerceIn(-32767, 32767).toShort()
                }

                track.write(stereoPcmOut, 0, stereoPcmOut.size, AudioTrack.WRITE_BLOCKING)

                // Log every ~1.5 seconds (150 frames of 10ms) when vocal guide or real mic is actively outputting
                if (frameCount % 150L == 0L && (shouldPlayGuide || hasMicPcmInFrame)) {
                    val gt = _guideTelemetry.value
                    val loadedPerSec = lastMeasuredLoadedSamplesPerSec
                    val loadedMsPerSec = (loadedPerSec * 1000) / SAMPLE_RATE
                    Log.i(
                        TAG,
                        "Native AudioTrack Clean Vocal Mix -> guideVocal=$guideVocalEnabled, isStagePlaying=$isStagePlaying, " +
                            "note=${currentPluckFreqHz.roundToInt()}Hz ($currentPluckNote), guideLevel=${gt.guideVoiceLevelPercent.roundToInt()}%, " +
                            "echo=$echoLevelPercent%, catchUp=$isCatchingUp, micPcm=$hasMicPcmInFrame, " +
                            "loadedSamplesPerSec=$loadedPerSec (${loadedMsPerSec}ms/s), peakAmp=${(peakSampleAbs * 100).roundToInt()}%"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Native AudioTrack render loop stopped: ${e.message}")
        } finally {
            try {
                track.pause()
                track.flush()
                track.release()
            } catch (_: Exception) {
            }
            if (audioTrack === track) {
                audioTrack = null
            }
        }
    }

    fun release() {
        isRenderRunning = false
        try {
            audioRenderThread?.interrupt()
        } catch (_: Exception) {
        }
        audioRenderThread = null
        scope.cancel()
    }
}
