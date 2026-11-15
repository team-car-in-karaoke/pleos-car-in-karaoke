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
        private const val FRAME_SAMPLES = 960 // 20ms frame at 48kHz
        private const val TWO_PI = (2.0 * PI).toFloat()

        // EP / Chime Bell Pluck Envelope Constants (15ms Attack + 350ms Exponential Decay)
        private const val PLUCK_ATTACK_SAMPLES = 720 // 15ms at 48kHz
        private const val PLUCK_DECAY_TAU_SEC = 0.14f // ~350ms effective exponential decay window
        private const val PLUCK_H2_DECAY_TAU_SEC = 0.085f // Faster 2nd harmonic decay for warm bell tone
        private const val MAX_PLUCK_DURATION_SAMPLES = 26400 // 550ms total pluck life, then pure 0f

        // Strict Gain Ceiling (~15% of full scale so YouTube backing is always dominant; peakAmp < 20%)
        private const val GUIDE_PLUCK_BASE_GAIN = 0.095f
        private const val MIC_PCM_BASE_GAIN = 0.125f
        private const val HARD_MAX_OUTPUT_GAIN = 0.145f

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
            ),
            "kpop_neon_drive" to floatArrayOf(
                261.63f, 293.66f, 329.63f, 392.00f, 440.00f, 392.00f, 329.63f, 293.66f,
                349.23f, 392.00f, 440.00f, 523.25f, 493.88f, 440.00f, 392.00f, 329.63f
            ),
            "ballad_starlight_highway" to floatArrayOf(
                220.00f, 246.94f, 261.63f, 293.66f, 329.63f, 392.00f, 349.23f, 329.63f,
                293.66f, 261.63f, 293.66f, 329.63f, 440.00f, 392.00f, 329.63f, 261.63f
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
            keyShiftSemitones: Int
        ): Pair<Float, String> {
            val pattern = SONG_MELODY_PATTERNS[trackId] ?: DEFAULT_MELODY_PATTERN
            val safeBpm = bpm.coerceIn(68, 160)
            val beatsPerSec = safeBpm / 60f
            // Advance melody note every 1.25 beats for responsive vocal guide phrasing
            val stepIdx = ((elapsedSec.coerceAtLeast(0f) * beatsPerSec) / 1.25f).toInt()
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
    private var audioLoopJob: Job? = null
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
    var echoLevelPercent: Int = 80
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

    // Real Smartphone Mic PCM circular ring buffer (48kHz mono float samples)
    private val micRingCapacity = SAMPLE_RATE // 1.0s capacity
    private val micRingBuffer = FloatArray(micRingCapacity)
    private val micRingLock = Any()
    private var micWritePos = 0
    private var micReadPos = 0
    private var micAvailableSamples = 0
    private var micHpPrevIn = 0f
    private var micHpPrevOut = 0f

    // SFX trigger state
    @Volatile
    private var activeSfxType: String = ""

    @Volatile
    private var sfxRemainingSamples: Int = 0

    private var sfxSampleCounter: Int = 0

    fun start() {
        if (audioLoopJob?.isActive == true) return
        audioLoopJob = scope.launch {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            } catch (_: Exception) {
            }
            runAudioRenderLoop()
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

    fun setEchoLevel(levelPercent: Int) {
        val clamped = levelPercent.coerceIn(0, 100)
        if (echoLevelPercent != clamped) {
            echoLevelPercent = clamped
            Log.i(TAG, "Native AudioTrack Reverb Echo updated: echo=$clamped%")
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
     * Enqueues incoming real-time PCM16 microphone audio chunk from `pleos-stage-mic` (typically 16kHz mono),
     * upsampling cleanly to 48kHz and pushing into the low-latency reverb mixer ring buffer.
     * ONLY real human voice PCM chunks sent from the smartphone are played on the mic channel.
     */
    fun enqueueMicPcmChunk(sampleRate: Int, pcmShorts: ShortArray) {
        if (pcmShorts.isEmpty()) return
        val inRate = sampleRate.coerceIn(8000, 48000)
        val ratio = SAMPLE_RATE.toFloat() / inRate.toFloat()
        val outLen = (pcmShorts.size * ratio).toInt().coerceAtLeast(1)

        synchronized(micRingLock) {
            val maxBuffered = 4320
            if (micAvailableSamples + outLen > maxBuffered) {
                val drop = (micAvailableSamples + outLen) - maxBuffered
                micReadPos = (micReadPos + drop) % micRingCapacity
                micAvailableSamples = (micAvailableSamples - drop).coerceAtLeast(0)
            }

            for (i in 0 until outLen) {
                val srcPos = i / ratio
                val idx0 = srcPos.toInt().coerceIn(0, pcmShorts.lastIndex)
                val idx1 = (idx0 + 1).coerceAtMost(pcmShorts.lastIndex)
                val frac = srcPos - idx0
                val s0 = pcmShorts[idx0] / 32768f
                val s1 = pcmShorts[idx1] / 32768f
                val raw = s0 + (s1 - s0) * frac

                // 110Hz High-Pass Filter to remove mic handling rumble
                val hpOut = 0.985f * (micHpPrevOut + raw - micHpPrevIn)
                micHpPrevIn = raw
                micHpPrevOut = hpOut

                micRingBuffer[micWritePos] = hpOut.coerceIn(-1f, 1f)
                micWritePos = (micWritePos + 1) % micRingCapacity
                if (micAvailableSamples < micRingCapacity) {
                    micAvailableSamples++
                } else {
                    micReadPos = (micReadPos + 1) % micRingCapacity
                }
            }
        }
    }

    fun triggerSfx(sfxType: String) {
        activeSfxType = sfxType.uppercase()
        sfxSampleCounter = 0
        sfxRemainingSamples = when (activeSfxType) {
            "TAMBOURINE" -> (SAMPLE_RATE * 0.28f).toInt()
            else -> (SAMPLE_RATE * 1.15f).toInt()
        }
        Log.i(TAG, "Triggered native AudioTrack SFX: $activeSfxType")
    }

    private fun runAudioRenderLoop() {
        val minBufBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAME_SAMPLES * 4 * 4)

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
                .setBufferSizeInBytes(minBufBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build Native AudioTrack: ${e.message}", e)
            return
        }

        audioTrack = track
        try {
            track.setVolume(1.0f)
            track.play()
            Log.i(
                TAG,
                "Native AudioTrack started: ${SAMPLE_RATE}Hz STEREO PCM_16BIT USAGE_MEDIA MODE_STREAM (guideVocalEnabled=$guideVocalEnabled)"
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

        // Schroeder + Stereo Multi-Tap Ping-Pong Delay Reverb Buffers (at 48kHz)
        val delayLenL = 6480 // 135ms
        val delayLenR = 9360 // 195ms
        val delayBufL = FloatArray(delayLenL)
        val delayBufR = FloatArray(delayLenR)
        var delayPosL = 0
        var delayPosR = 0
        var delayDampL = 0f
        var delayDampR = 0f

        val combLen0 = 1427
        val combLen1 = 1783
        val combLen2 = 1973
        val combLen3 = 2099
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

        val apLen0 = 241
        val apLen1 = 83
        val apBuf0L = FloatArray(apLen0)
        val apBuf0R = FloatArray(apLen0)
        val apBuf1L = FloatArray(apLen1)
        val apBuf1R = FloatArray(apLen1)
        var apPos0 = 0
        var apPos1 = 0

        var reverbBuffersDirty = false
        var frameCount = 0L
        var noiseSeed = 0x13579BDF

        try {
            while (scope.isActive) {
                frameCount++

                // 1. Pull available real smartphone mic PCM samples for this 20ms frame
                var hasMicPcmInFrame = false
                synchronized(micRingLock) {
                    val toRead = micAvailableSamples.coerceAtMost(FRAME_SAMPLES)
                    if (toRead > 0) {
                        hasMicPcmInFrame = true
                        for (i in 0 until toRead) {
                            micTempBuf[i] = micRingBuffer[micReadPos]
                            micReadPos = (micReadPos + 1) % micRingCapacity
                        }
                        micAvailableSamples -= toRead
                        for (i in toRead until FRAME_SAMPLES) {
                            micTempBuf[i] = 0f
                        }
                    } else {
                        for (i in 0 until FRAME_SAMPLES) {
                            micTempBuf[i] = 0f
                        }
                    }
                }

                // Guide Vocal runs whenever `guideVocalEnabled == true`, EVEN WHEN YouTube MR is paused/stopped!
                val shouldPlayGuide = guideVocalEnabled

                // Guarantee 100% pure digital silence (`0` array) when guide vocal is OFF and no real mic PCM / SFX is active
                if (!shouldPlayGuide && !hasMicPcmInFrame && sfxRemainingSamples <= 0) {
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
                        delayBufL.fill(0f)
                        delayBufR.fill(0f)
                        delayDampL = 0f
                        delayDampR = 0f
                        combBuf0.fill(0f)
                        combBuf1.fill(0f)
                        combBuf2.fill(0f)
                        combBuf3.fill(0f)
                        combFilter0 = 0f
                        combFilter1 = 0f
                        combFilter2 = 0f
                        combFilter3 = 0f
                        apBuf0L.fill(0f)
                        apBuf0R.fill(0f)
                        apBuf1L.fill(0f)
                        apBuf1R.fill(0f)
                        reverbBuffersDirty = false
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

                reverbBuffersDirty = true
                val activeBpm = currentBpm.coerceIn(72, 160)
                val samplesPerBeat = ((60f / activeBpm) * SAMPLE_RATE).toInt().coerceAtLeast(14000)
                val retriggerIntervalSamples = (samplesPerBeat * 1.25f).toInt().coerceAtLeast(20000)

                // Trigger a soft EP / Chime Bell pluck on internal BPM melody clock (works both when playing and when paused!)
                if (shouldPlayGuide) {
                    internalGuideElapsedSec += (FRAME_SAMPLES.toFloat() / SAMPLE_RATE.toFloat())
                    val (clockPitchHz, clockNote) = resolveRealtimeGuidePitch(
                        trackId = currentTrackId,
                        bpm = activeBpm,
                        elapsedSec = internalGuideElapsedSec,
                        lyricBaseHz = if (isStagePlaying) targetGuidePitchHz else 0f,
                        keyShiftSemitones = keyShiftSemitones
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

                    // Emit real-time Guide Vocal Telemetry (Pitch, Note & 25% ~ 85% Undulating Level) every 40ms
                    if (frameCount % 2L == 0L) {
                        val meterDecaySec = ((pluckSampleAge - PLUCK_ATTACK_SAMPLES).coerceAtLeast(0)).toFloat() / SAMPLE_RATE.toFloat()
                        val meterEnv = if (pluckSampleAge < PLUCK_ATTACK_SAMPLES) {
                            0.35f + 0.65f * (pluckSampleAge.toFloat() / PLUCK_ATTACK_SAMPLES.toFloat())
                        } else {
                            exp(-meterDecaySec / 0.34f).coerceIn(0f, 1f)
                        }
                        val voiceLevelPercent = (26f + 58f * meterEnv).coerceIn(25f, 85f)
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
                val wetGain = echoNorm * 0.48f
                val feedbackAmount = (0.22f + echoNorm * 0.38f).coerceIn(0.18f, 0.62f)
                val masterScale = masterVolumeScale.coerceIn(0.25f, 1f)

                var peakSampleAbs = 0f

                for (i in 0 until FRAME_SAMPLES) {
                    var vocalMonoSample = 0f

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
                        val activeGuideGain = if (isStagePlaying) GUIDE_PLUCK_BASE_GAIN else 0.138f
                        vocalMonoSample += (cleanSine + secondHarmonic) * activeGuideGain
                    } else if (shouldPlayGuide) {
                        samplesSinceLastPluck++
                    }

                    // B. Mix real-time Smartphone Mic PCM sample (ONLY real `audio-pcm-chunk` bytes)
                    val micSample = micTempBuf[i]
                    if (abs(micSample) > 0.0001f) {
                        vocalMonoSample += micSample * MIC_PCM_BASE_GAIN
                    }

                    // C. Mix Stage SFX (Applause / Cheer / Tambourine) if triggered
                    if (sfxRemainingSamples > 0) {
                        sfxRemainingSamples--
                        sfxSampleCounter++
                        noiseSeed = noiseSeed * 1664525 + 1013904223
                        val whiteNoise = ((noiseSeed shr 16) and 0x7FFF) / 16384f - 1f
                        if (activeSfxType == "TAMBOURINE") {
                            val env = (sfxRemainingSamples.toFloat() / (SAMPLE_RATE * 0.28f)).coerceIn(0f, 1f)
                            val jingle = sin(TWO_PI * 5800f * (sfxSampleCounter.toFloat() / SAMPLE_RATE)) +
                                sin(TWO_PI * 7600f * (sfxSampleCounter.toFloat() / SAMPLE_RATE))
                            vocalMonoSample += (jingle * 0.035f + whiteNoise * 0.045f) * env * env
                        } else {
                            val totalDur = SAMPLE_RATE * 1.15f
                            val env = sin((sfxSampleCounter.toFloat() / totalDur).coerceIn(0f, 1f) * PI.toFloat())
                            vocalMonoSample += whiteNoise * 0.055f * env
                        }
                    }

                    // D. Schroeder + Stereo Ping-Pong Delay Concert Hall Reverb DSP
                    val reverbInput = vocalMonoSample

                    val delayedL = delayBufL[delayPosL]
                    val delayedR = delayBufR[delayPosR]
                    delayDampL += (delayedL - delayDampL) * 0.42f
                    delayDampR += (delayedR - delayDampR) * 0.42f

                    delayBufL[delayPosL] = (reverbInput + delayDampR * feedbackAmount * 0.55f).coerceIn(-0.5f, 0.5f)
                    delayBufR[delayPosR] = (reverbInput * 0.85f + delayDampL * feedbackAmount * 0.55f).coerceIn(-0.5f, 0.5f)
                    delayPosL = (delayPosL + 1) % delayLenL
                    delayPosR = (delayPosR + 1) % delayLenR

                    val c0 = combBuf0[combPos0]
                    combFilter0 = c0 * 0.72f + combFilter0 * 0.28f
                    combBuf0[combPos0] = (reverbInput + combFilter0 * feedbackAmount).coerceIn(-0.5f, 0.5f)
                    combPos0 = (combPos0 + 1) % combLen0

                    val c1 = combBuf1[combPos1]
                    combFilter1 = c1 * 0.72f + combFilter1 * 0.28f
                    combBuf1[combPos1] = (reverbInput + combFilter1 * feedbackAmount).coerceIn(-0.5f, 0.5f)
                    combPos1 = (combPos1 + 1) % combLen1

                    val c2 = combBuf2[combPos2]
                    combFilter2 = c2 * 0.72f + combFilter2 * 0.28f
                    combBuf2[combPos2] = (reverbInput + combFilter2 * feedbackAmount).coerceIn(-0.5f, 0.5f)
                    combPos2 = (combPos2 + 1) % combLen2

                    val c3 = combBuf3[combPos3]
                    combFilter3 = c3 * 0.72f + combFilter3 * 0.28f
                    combBuf3[combPos3] = (reverbInput + combFilter3 * feedbackAmount).coerceIn(-0.5f, 0.5f)
                    combPos3 = (combPos3 + 1) % combLen3

                    val combSumL = (c0 + c2) * 0.32f + delayedL * 0.38f
                    val combSumR = (c1 + c3) * 0.32f + delayedR * 0.38f

                    val ap0BufOutL = apBuf0L[apPos0]
                    val ap0InL = combSumL + ap0BufOutL * 0.5f
                    val ap0OutL = -combSumL + ap0BufOutL
                    apBuf0L[apPos0] = ap0InL.coerceIn(-0.5f, 0.5f)

                    val ap0BufOutR = apBuf0R[apPos0]
                    val ap0InR = combSumR + ap0BufOutR * 0.5f
                    val ap0OutR = -combSumR + ap0BufOutR
                    apBuf0R[apPos0] = ap0InR.coerceIn(-0.5f, 0.5f)
                    apPos0 = (apPos0 + 1) % apLen0

                    val ap1BufOutL = apBuf1L[apPos1]
                    val ap1InL = ap0OutL + ap1BufOutL * 0.5f
                    val wetOutL = -ap0OutL + ap1BufOutL
                    apBuf1L[apPos1] = ap1InL.coerceIn(-0.5f, 0.5f)

                    val ap1BufOutR = apBuf1R[apPos1]
                    val ap1InR = ap0OutR + ap1BufOutR * 0.5f
                    val wetOutR = -ap0OutR + ap1BufOutR
                    apBuf1R[apPos1] = ap1InR.coerceIn(-0.5f, 0.5f)
                    apPos1 = (apPos1 + 1) % apLen1

                    // Subtle background mix (~12% - 14% max peak) so YouTube backing music is clearly main
                    val dryGain = 0.85f
                    val mixL = (vocalMonoSample * dryGain + wetOutL * wetGain) * masterScale
                    val mixR = (vocalMonoSample * dryGain + wetOutR * wetGain) * masterScale

                    val limL = mixL.coerceIn(-HARD_MAX_OUTPUT_GAIN, HARD_MAX_OUTPUT_GAIN)
                    val limR = mixR.coerceIn(-HARD_MAX_OUTPUT_GAIN, HARD_MAX_OUTPUT_GAIN)

                    val absMax = maxOf(abs(limL), abs(limR))
                    if (absMax > peakSampleAbs) peakSampleAbs = absMax

                    stereoPcmOut[i * 2] = (limL * 32760f).toInt().coerceIn(-32767, 32767).toShort()
                    stereoPcmOut[i * 2 + 1] = (limR * 32760f).toInt().coerceIn(-32767, 32767).toShort()
                }

                track.write(stereoPcmOut, 0, stereoPcmOut.size, AudioTrack.WRITE_BLOCKING)

                // Log every ~1.5 seconds (75 frames of 20ms) when vocal guide or real mic is actively outputting
                if (frameCount % 75L == 0L && (shouldPlayGuide || hasMicPcmInFrame)) {
                    val gt = _guideTelemetry.value
                    Log.i(
                        TAG,
                        "Native AudioTrack EP Bell / Mic mix -> guideVocal=$guideVocalEnabled, isStagePlaying=$isStagePlaying, " +
                            "note=${currentPluckFreqHz.roundToInt()}Hz ($currentPluckNote), guideLevel=${gt.guideVoiceLevelPercent.roundToInt()}%, " +
                            "echo=$echoLevelPercent%, micPcm=$hasMicPcmInFrame, peakAmp=${(peakSampleAbs * 100).roundToInt()}%"
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
        audioLoopJob?.cancel()
        audioLoopJob = null
        scope.cancel()
    }
}
