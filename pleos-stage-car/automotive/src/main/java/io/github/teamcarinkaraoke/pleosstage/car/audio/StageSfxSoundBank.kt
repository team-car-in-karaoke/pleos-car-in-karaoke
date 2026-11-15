package io.github.teamcarinkaraoke.pleosstage.car.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pre-generated 48kHz Stereo Acoustic-Modeled Karaoke SFX Sound Bank.
 *
 * Synthesizes realistic, zero-delay stereo PCM buffers for:
 *  - APPLAUSE (~2.2s): 52 individual clappers (`900Hz ~ 2.6kHz` resonant micro-clap transients,
 *    Gaussian timing jitter, wide L/R constant-power stereo panning + Haas delay, concert hall early reflections).
 *  - CHEER (~2.5s): 22-voice detuned crowd vowel chorus ("와아아~!" F1: 650~850Hz, F2: 1200~1600Hz, F3: 2500~3000Hz
 *    with upward pitch glide) + crowd applause bed + bright celebration brass/chime fanfare chord (C5-E5-G5-C6).
 *  - TAMBOURINE (~0.9s): Rhythmic 4-hit karaoke pattern ("찰칵-찰-찰-찰!"), first-hit palm membrane body thump
 *    (180Hz ~ 320Hz) + 6 pairs of brass jingles with inharmonic metallic partials (5.4kHz, 6.9kHz, 8.7kHz, 10.8kHz, 12.6kHz).
 */
object StageSfxSoundBank {
    const val SAMPLE_RATE = 48_000
    private const val TWO_PI = (2.0 * PI).toFloat()

    @Volatile
    private var applauseStereo: FloatArray? = null

    @Volatile
    private var cheerStereo: FloatArray? = null

    @Volatile
    private var tambourineStereo: FloatArray? = null

    private val initLock = Any()

    /**
     * Warms up all 3 stereo PCM buffers on a background thread so button presses play with 0ms latency.
     */
    fun warmUpAsync() {
        if (applauseStereo != null && cheerStereo != null && tambourineStereo != null) return
        Thread(
            {
                ensureReady()
            },
            "PleosStageSfxWarmUp"
        ).apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    fun ensureReady() {
        if (applauseStereo != null && cheerStereo != null && tambourineStereo != null) return
        synchronized(initLock) {
            if (applauseStereo == null) {
                applauseStereo = generateApplauseStereo()
            }
            if (tambourineStereo == null) {
                tambourineStereo = generateTambourineStereo()
            }
            if (cheerStereo == null) {
                cheerStereo = generateCheerStereo(applauseStereo!!)
            }
        }
    }

    /**
     * Returns interleaved 48kHz Stereo FloatArray ([L0, R0, L1, R1, ...]) for the requested SFX type.
     */
    fun getStereoBuffer(type: String): FloatArray? {
        ensureReady()
        return when (type.uppercase().trim()) {
            "APPLAUSE" -> applauseStereo
            "CHEER" -> cheerStereo
            "TAMBOURINE" -> tambourineStereo
            else -> null
        }
    }

    /**
     * 2nd-order Constant-Skirt Bandpass Biquad Filter for formant & palm-cavity resonances.
     */
    private class BandpassBiquad(
        centerHz: Float,
        q: Float,
        sampleRate: Int = SAMPLE_RATE
    ) {
        private val b0: Float
        private val b1: Float = 0f
        private val b2: Float
        private val a1: Float
        private val a2: Float
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        init {
            val safeFreq = centerHz.coerceIn(60f, sampleRate * 0.46f)
            val safeQ = q.coerceAtLeast(0.35f)
            val w0 = TWO_PI * safeFreq / sampleRate.toFloat()
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2f * safeQ)
            val a0 = 1f + alpha
            b0 = alpha / a0
            b2 = -alpha / a0
            a1 = (-2f * cosW0) / a0
            a2 = (1f - alpha) / a0
        }

        fun process(x0: Float): Float {
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            return y0
        }

        fun reset() {
            x1 = 0f
            x2 = 0f
            y1 = 0f
            y2 = 0f
        }
    }

    private fun nextGaussian(rng: Random): Float {
        // Fast Marsaglia-style sum of uniforms (~N(0, 1)) without Box-Muller log(0) branch
        var sum = 0f
        repeat(6) {
            sum += rng.nextFloat()
        }
        return (sum - 3f) * 1.4142f
    }

    /**
     * Applies concert-hall early reflections + lush stereo cross-diffusion so crowd/percussion sounds live in a hall.
     */
    private fun applyConcertHallEarlyReflections(
        dryStereo: FloatArray,
        frames: Int,
        wetMix: Float,
        damping: Float = 0.35f
    ) {
        val tapDelaysMs = floatArrayOf(11.3f, 18.7f, 27.9f, 39.4f, 53.8f, 71.2f, 92.5f)
        val tapGainsL = floatArrayOf(0.36f, -0.24f, 0.28f, 0.19f, -0.16f, 0.13f, 0.09f)
        val tapGainsR = floatArrayOf(-0.26f, 0.34f, -0.21f, 0.26f, 0.18f, -0.14f, 0.10f)
        val tapSamples = IntArray(tapDelaysMs.size) {
            ((tapDelaysMs[it] / 1000f) * SAMPLE_RATE).toInt().coerceAtLeast(1)
        }

        val wetL = FloatArray(frames)
        val wetR = FloatArray(frames)
        var lpL = 0f
        var lpR = 0f

        for (i in 0 until frames) {
            var accL = 0f
            var accR = 0f
            for (t in tapSamples.indices) {
                val srcIdx = i - tapSamples[t]
                if (srcIdx >= 0) {
                    // Cross-couple L & R for realistic hall width
                    val srcL = dryStereo[srcIdx * 2]
                    val srcR = dryStereo[srcIdx * 2 + 1]
                    accL += (srcL * 0.7f + srcR * 0.3f) * tapGainsL[t]
                    accR += (srcR * 0.7f + srcL * 0.3f) * tapGainsR[t]
                }
            }
            lpL += (accL - lpL) * (1f - damping)
            lpR += (accR - lpR) * (1f - damping)
            wetL[i] = lpL
            wetR[i] = lpR
        }

        val dryGain = 1f - wetMix * 0.25f
        for (i in 0 until frames) {
            dryStereo[i * 2] = dryStereo[i * 2] * dryGain + wetL[i] * wetMix
            dryStereo[i * 2 + 1] = dryStereo[i * 2 + 1] * dryGain + wetR[i] * wetMix
        }
    }

    private fun normalizeStereoBuffer(stereo: FloatArray, targetPeak: Float) {
        var maxAbs = 0.0001f
        for (v in stereo) {
            val a = abs(v)
            if (a > maxAbs) maxAbs = a
        }
        val scale = targetPeak / maxAbs
        for (i in stereo.indices) {
            val s = stereo[i] * scale
            // Gentle soft saturation near +/-1.0
            stereo[i] = s.coerceIn(-0.98f, 0.98f)
        }
    }

    /**
     * 👏 APPLAUSE (~2.2s, 48kHz Stereo):
     * 52 individual clappers with 900Hz~2.6kHz bandpass palm resonances, per-clap random timing jitter,
     * wide L/R stereo panning + Haas micro-delay, and concert hall early reflections.
     */
    private fun generateApplauseStereo(): FloatArray {
        val durationSec = 2.22f
        val frames = (SAMPLE_RATE * durationSec).toInt()
        val out = FloatArray(frames * 2)
        val rng = Random(20261114L)

        val clapperCount = 52
        for (person in 0 until clapperCount) {
            // Distribute clappers evenly across stereo stage with slight randomness (-0.90 .. +0.90)
            val basePan = -0.88f + (1.76f * person.toFloat() / (clapperCount - 1).toFloat())
            val pan = (basePan + (rng.nextFloat() - 0.5f) * 0.12f).coerceIn(-0.92f, 0.92f)
            val panAngle = (pan + 1f) * (PI.toFloat() * 0.25f)
            val gainL = cos(panAngle)
            val gainR = sin(panAngle)
            // Subtle Haas interaural delay (0..20 samples = 0..0.42ms)
            val haasSamples = (abs(pan) * 18f).toInt()
            val delayL = if (pan > 0f) haasSamples else 0
            val delayR = if (pan < 0f) haasSamples else 0

            // Each person has cupped-palm (900..1550Hz) or flat-palm (1550..2600Hz) resonance
            val primaryFreq = if (person % 2 == 0) {
                920f + rng.nextFloat() * 680f
            } else {
                1520f + rng.nextFloat() * 1060f
            }
            val secondaryFreq = (primaryFreq * (1.42f + rng.nextFloat() * 0.35f)).coerceAtMost(3400f)
            val primaryQ = 1.6f + rng.nextFloat() * 1.4f
            val bpMain = BandpassBiquad(primaryFreq, primaryQ)
            val bpSnap = BandpassBiquad(secondaryFreq, 2.1f)

            // Individual clapping rate: 3.3 .. 5.3 claps/sec
            val baseIntervalSec = 1f / (3.3f + rng.nextFloat() * 2.0f)
            var tSec = rng.nextFloat() * 0.16f // Staggered audience entry
            val personDistanceGain = 0.55f + rng.nextFloat() * 0.45f

            while (tSec < durationSec - 0.08f) {
                val startFrame = (tSec * SAMPLE_RATE).toInt()
                val clapLenSec = 0.048f + rng.nextFloat() * 0.026f
                val clapFrames = (clapLenSec * SAMPLE_RATE).toInt()

                // Overall crowd envelope at this clap's timestamp (fast rise, warm sustain, natural taper)
                val crowdProgress = tSec / durationSec
                val crowdMacroEnv = when {
                    crowdProgress < 0.08f -> (crowdProgress / 0.08f).pow(0.65f)
                    crowdProgress < 0.68f -> 1.0f - (crowdProgress - 0.08f) * 0.12f
                    else -> ((1f - crowdProgress) / 0.32f).coerceAtLeast(0f).pow(1.45f)
                }

                val strikeVel = personDistanceGain * (0.70f + rng.nextFloat() * 0.35f) * crowdMacroEnv
                if (strikeVel > 0.02f) {
                    bpMain.reset()
                    bpSnap.reset()
                    val attackSamples = (0.0011f * SAMPLE_RATE).toInt().coerceAtLeast(1)
                    val fastDecayTau = 0.0065f + rng.nextFloat() * 0.0035f
                    val bodyDecayTau = 0.024f + rng.nextFloat() * 0.016f

                    for (k in 0 until clapFrames) {
                        val dstFrame = startFrame + k
                        if (dstFrame >= frames) break
                        val localSec = k.toFloat() / SAMPLE_RATE.toFloat()
                        val attack = if (k < attackSamples) k.toFloat() / attackSamples.toFloat() else 1f
                        // Double-exponential micro-clap transient envelope (initial skin snap + palm air cavity)
                        val snapEnv = exp(-localSec / fastDecayTau)
                        val bodyEnv = exp(-localSec / bodyDecayTau)
                        val env = attack * (0.68f * snapEnv + 0.32f * bodyEnv)

                        val white = nextGaussian(rng)
                        val mainRes = bpMain.process(white)
                        val snapRes = bpSnap.process(white) * snapEnv
                        val sample = (mainRes * 0.76f + snapRes * 0.44f) * env * strikeVel

                        val idxL = dstFrame + delayL
                        val idxR = dstFrame + delayR
                        if (idxL < frames) out[idxL * 2] += sample * gainL
                        if (idxR < frames) out[idxR * 2 + 1] += sample * gainR
                    }
                }

                // Human timing jitter (+/- 26ms)
                val jitterSec = (rng.nextFloat() - 0.5f) * 0.052f
                tSec += (baseIntervalSec + jitterSec).coerceAtLeast(0.095f)
            }
        }

        applyConcertHallEarlyReflections(out, frames, wetMix = 0.42f, damping = 0.32f)
        normalizeStereoBuffer(out, targetPeak = 0.84f)
        return out
    }

    /**
     * 🎉 CHEER (~2.5s, 48kHz Stereo):
     * 22-voice detuned crowd vowel chorus ("와아아~!" F1: 650~850Hz, F2: 1200~1600Hz, F3: 2500~3000Hz + upward pitch glide)
     * + energetic applause + bright celebration brass/chime fanfare chord (C5-E5-G5-C6).
     */
    private fun generateCheerStereo(applauseSource: FloatArray): FloatArray {
        val durationSec = 2.50f
        val frames = (SAMPLE_RATE * durationSec).toInt()
        val out = FloatArray(frames * 2)
        val rng = Random(20260707L)

        // 1) Multi-Voice Crowd Formant Chorus ("와아아~!")
        val voiceCount = 22
        for (v in 0 until voiceCount) {
            val pan = (-0.86f + (1.72f * v.toFloat() / (voiceCount - 1).toFloat()) + (rng.nextFloat() - 0.5f) * 0.1f)
                .coerceIn(-0.90f, 0.90f)
            val panAngle = (pan + 1f) * (PI.toFloat() * 0.25f)
            val gainL = cos(panAngle)
            val gainR = sin(panAngle)

            // Mix of male (145..215Hz) and female/youth (255..395Hz) crowd voices
            val startPitchHz = if (v % 2 == 0) {
                148f + rng.nextFloat() * 68f
            } else {
                258f + rng.nextFloat() * 132f
            }
            // Joyful upward pitch glide (+3.5 to +6.0 semitones)
            val glideRatio = 2f.pow((3.5f + rng.nextFloat() * 2.5f) / 12f)
            val peakPitchHz = startPitchHz * glideRatio

            // Individual speaker vowel formants for open Korean "아/와" ("와아아~!")
            // F1: 650~850Hz, F2: 1200~1600Hz, F3: 2500~3000Hz
            val targetF1 = 650f + rng.nextFloat() * 200f
            val targetF2 = 1200f + rng.nextFloat() * 400f
            val targetF3 = 2500f + rng.nextFloat() * 500f

            val f1Filter = BandpassBiquad(targetF1, q = 4.6f + rng.nextFloat() * 1.2f)
            val f2Filter = BandpassBiquad(targetF2, q = 5.2f + rng.nextFloat() * 1.5f)
            val f3Filter = BandpassBiquad(targetF3, q = 6.0f + rng.nextFloat() * 1.8f)
            // Initial "우/오" onset filter for the "와(wa)->아(aa)" diphthong articulation
            val onsetDiphthongFilter = BandpassBiquad(460f + rng.nextFloat() * 90f, q = 3.8f)

            val entryDelaySec = rng.nextFloat() * 0.14f
            val voiceEndSec = durationSec - (0.05f + rng.nextFloat() * 0.22f)
            val vibratoRateHz = 4.8f + rng.nextFloat() * 2.1f
            val vibratoDepth = 0.012f + rng.nextFloat() * 0.010f
            var vibPhase = rng.nextFloat() * TWO_PI
            var glottalPhase = rng.nextFloat()
            val voiceLevel = (0.68f + rng.nextFloat() * 0.32f) / sqrt(voiceCount.toFloat())

            val startFrame = (entryDelaySec * SAMPLE_RATE).toInt()
            val endFrame = (voiceEndSec * SAMPLE_RATE).toInt().coerceAtMost(frames)

            for (i in startFrame until endFrame) {
                val voiceSec = (i - startFrame).toFloat() / SAMPLE_RATE.toFloat()
                val totalVoiceDur = (endFrame - startFrame).toFloat() / SAMPLE_RATE.toFloat()
                val normT = (voiceSec / totalVoiceDur).coerceIn(0f, 1f)

                // Smooth vocal swell & release
                val env = when {
                    voiceSec < 0.09f -> (voiceSec / 0.09f).pow(0.75f)
                    normT < 0.68f -> 1.0f - (normT - 0.1f).coerceAtLeast(0f) * 0.15f
                    else -> ((1f - normT) / 0.32f).coerceAtLeast(0f).pow(1.55f)
                }

                // Pitch rises excitedly over first 0.48s then gently settles
                val glideProgress = (voiceSec / 0.48f).coerceIn(0f, 1f)
                val curvedGlide = 1f - (1f - glideProgress) * (1f - glideProgress)
                val slightTailDrop = if (normT > 0.72f) 1f - (normT - 0.72f) * 0.09f else 1f
                vibPhase += TWO_PI * vibratoRateHz / SAMPLE_RATE.toFloat()
                val vibrato = 1f + sin(vibPhase) * vibratoDepth * glideProgress
                val f0 = (startPitchHz + (peakPitchHz - startPitchHz) * curvedGlide) * slightTailDrop * vibrato

                // Natural glottal pulse source (warm harmonic fold impulse + breathy aspiration)
                glottalPhase += f0 / SAMPLE_RATE.toFloat()
                if (glottalPhase >= 1f) glottalPhase -= 1f
                // Smooth asymmetric glottal waveform rich in vocal harmonics without harsh aliasing
                val p = glottalPhase
                val glottalWave = if (p < 0.42f) {
                    val u = p / 0.42f
                    sin(PI.toFloat() * u) - 0.35f * sin(TWO_PI * u)
                } else {
                    -0.28f * exp(-(p - 0.42f) * 7.5f)
                }
                // Add crisp upper-harmonic excitation + natural vocal breathiness
                val harmonicEdge = sin(TWO_PI * p * 2f) * 0.45f +
                    sin(TWO_PI * p * 3f) * 0.32f +
                    sin(TWO_PI * p * 4f) * 0.22f +
                    sin(TWO_PI * p * 5f) * 0.15f
                val breathNoise = nextGaussian(rng) * (0.16f + 0.08f * (1f - glideProgress))
                val excitation = glottalWave * 0.75f + harmonicEdge * 0.45f + breathNoise

                // "와(wa)" -> "아아아(aaa)" transition over first 120ms
                val openVowelMix = (voiceSec / 0.12f).coerceIn(0f, 1f)
                val closedOnset = onsetDiphthongFilter.process(excitation) * (1f - openVowelMix)
                val openVowel = (
                    f1Filter.process(excitation) * 1.00f +
                        f2Filter.process(excitation) * 0.68f +
                        f3Filter.process(excitation) * 0.34f
                    ) * (0.25f + 0.75f * openVowelMix)

                val voicedSample = (closedOnset * 0.7f + openVowel) * env * voiceLevel
                out[i * 2] += voicedSample * gainL
                out[i * 2 + 1] += voicedSample * gainR
            }
        }

        // 2) Bright Brass / Chime Celebration Fanfare Chord (C5 - E5 - G5 - C6)
        val chordFreqs = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f)
        val chordOnsetsSec = floatArrayOf(0.01f, 0.065f, 0.12f, 0.175f)
        val chordPans = floatArrayOf(-0.42f, 0.38f, -0.22f, 0.28f)
        val chordWeights = floatArrayOf(0.24f, 0.22f, 0.22f, 0.26f)

        for (n in chordFreqs.indices) {
            val freq = chordFreqs[n]
            val startIdx = (chordOnsetsSec[n] * SAMPLE_RATE).toInt()
            val noteDurSec = 1.65f
            val noteFrames = (noteDurSec * SAMPLE_RATE).toInt()
            val panAngle = (chordPans[n] + 1f) * (PI.toFloat() * 0.25f)
            val gainL = cos(panAngle) * chordWeights[n]
            val gainR = sin(panAngle) * chordWeights[n]

            var phase1 = 0f
            var phaseDetune = 0.17f
            for (k in 0 until noteFrames) {
                val dst = startIdx + k
                if (dst >= frames) break
                val t = k.toFloat() / SAMPLE_RATE.toFloat()
                val attack = (t / 0.014f).coerceIn(0f, 1f)
                val brassSustain = exp(-t / 0.62f)
                val chimeSparkle = exp(-t / 0.19f)

                phase1 += TWO_PI * freq / SAMPLE_RATE.toFloat()
                if (phase1 > TWO_PI) phase1 -= TWO_PI
                phaseDetune += TWO_PI * (freq * 1.0035f) / SAMPLE_RATE.toFloat()
                if (phaseDetune > TWO_PI) phaseDetune -= TWO_PI

                // Warm celebratory brass harmonics + sparkling bell overtone
                val fundamental = (sin(phase1) + sin(phaseDetune) * 0.7f) * 0.58f
                val harm2 = sin(phase1 * 2f) * (0.34f * brassSustain + 0.18f * chimeSparkle)
                val harm3 = sin(phase1 * 3f) * (0.20f * brassSustain + 0.15f * chimeSparkle)
                val bellChime = sin(phase1 * 4f) * 0.11f * chimeSparkle +
                    sin(phase1 * 5.02f) * 0.06f * chimeSparkle

                val noteSample = (fundamental * brassSustain + harm2 + harm3 + bellChime) * attack * 0.42f
                out[dst * 2] += noteSample * gainL
                out[dst * 2 + 1] += noteSample * gainR
            }
        }

        // Apply hall reflections to crowd chorus + fanfare chord
        applyConcertHallEarlyReflections(out, frames, wetMix = 0.38f, damping = 0.28f)

        // 3) Layer lively Stereo Applause underneath the cheering crowd
        val applauseFrames = applauseSource.size / 2
        for (i in 0 until frames) {
            val tSec = i.toFloat() / SAMPLE_RATE.toFloat()
            val applauseEnv = when {
                tSec < 0.08f -> tSec / 0.08f
                tSec > durationSec - 0.45f -> ((durationSec - tSec) / 0.45f).coerceIn(0f, 1f)
                else -> 1.0f
            }
            // Cross-index into pre-generated applause so texture stays dense for all 2.5s
            val idxA = i % applauseFrames
            val idxB = (i + applauseFrames / 3) % applauseFrames
            val clapL = (applauseSource[idxA * 2] * 0.72f + applauseSource[idxB * 2 + 1] * 0.45f) * applauseEnv
            val clapR = (applauseSource[idxA * 2 + 1] * 0.72f + applauseSource[idxB * 2] * 0.45f) * applauseEnv

            out[i * 2] = out[i * 2] * 0.72f + clapL * 0.46f
            out[i * 2 + 1] = out[i * 2 + 1] * 0.72f + clapR * 0.46f
        }

        normalizeStereoBuffer(out, targetPeak = 0.86f)
        return out
    }

    /**
     * 🥁 TAMBOURINE (~0.9s, 48kHz Stereo):
     * Rhythmic 4-hit karaoke pattern ("찰칵-찰-찰-찰!"):
     *  - First hit includes crisp palm/frame body impact (180Hz ~ 320Hz pitch-swept thump) + rebound ghost shake ("찰-칵")
     *  - 6 pairs of brass jingles with inharmonic metallic partials (5.4kHz, 6.9kHz, 8.7kHz, 10.8kHz, 12.6kHz)
     *    + high-passed metallic rattle shimmer.
     */
    private fun generateTambourineStereo(): FloatArray {
        val durationSec = 0.90f
        val frames = (SAMPLE_RATE * durationSec).toInt()
        val out = FloatArray(frames * 2)
        val rng = Random(20260909L)

        // 4 main rhythmic hits ("찰[칵] - 찰 - 찰 - 찰!") + subtle rebound "칵" after hit 1
        data class TambourineStroke(
            val timeSec: Float,
            val velocity: Float,
            val hasPalmBody: Boolean,
            val decaySec: Float
        )

        val strokes = listOf(
            TambourineStroke(timeSec = 0.012f, velocity = 1.00f, hasPalmBody = true, decaySec = 0.095f),  // "찰" (accent palm strike)
            TambourineStroke(timeSec = 0.105f, velocity = 0.46f, hasPalmBody = false, decaySec = 0.065f), // "칵" (up-shake rebound!)
            TambourineStroke(timeSec = 0.245f, velocity = 0.80f, hasPalmBody = false, decaySec = 0.085f), // "찰"
            TambourineStroke(timeSec = 0.440f, velocity = 0.84f, hasPalmBody = false, decaySec = 0.088f), // "찰"
            TambourineStroke(timeSec = 0.635f, velocity = 0.96f, hasPalmBody = true, decaySec = 0.135f)   // "찰!" (crisp final accent)
        )

        // Requested brass jingle modal partials + upper air shimmer
        val basePartialsHz = floatArrayOf(5400f, 6900f, 8700f, 10800f, 12600f, 14300f)
        val partialWeights = floatArrayOf(0.28f, 0.25f, 0.21f, 0.17f, 0.13f, 0.08f)
        val jinglePairs = 6

        for (stroke in strokes) {
            val strokeStart = (stroke.timeSec * SAMPLE_RATE).toInt()

            // 1) Palm / Frame Body Thump (180Hz ~ 320Hz pitch-swept shell resonance)
            if (stroke.hasPalmBody) {
                val bodyScale = if (stroke.timeSec < 0.05f) 1.0f else 0.55f
                val bodyFrames = (0.045f * SAMPLE_RATE).toInt()
                val palmBp = BandpassBiquad(245f, q = 2.1f)
                var membranePhase = 0f
                for (k in 0 until bodyFrames) {
                    val dst = strokeStart + k
                    if (dst >= frames) break
                    val t = k.toFloat() / SAMPLE_RATE.toFloat()
                    // Pitch drops rapidly from 320Hz -> 182Hz on palm impact
                    val instFreq = 182f + (320f - 182f) * exp(-t / 0.0085f)
                    membranePhase += TWO_PI * instFreq / SAMPLE_RATE.toFloat()
                    val attack = (t / 0.0012f).coerceIn(0f, 1f)
                    val thumpEnv = attack * exp(-t / 0.0115f)
                    val slapNoise = palmBp.process(nextGaussian(rng)) * exp(-t / 0.0055f)
                    val bodySample = (sin(membranePhase) * 0.72f + slapNoise * 0.55f) *
                        thumpEnv * stroke.velocity * bodyScale * 0.48f
                    out[dst * 2] += bodySample * 0.96f
                    out[dst * 2 + 1] += bodySample * 1.04f
                }
            }

            // 2) 6 Pairs of Brass Jingles (each pair slightly staggered by 0..6ms as the frame shakes!)
            for (pair in 0 until jinglePairs) {
                val pairJitterSec = rng.nextFloat() * 0.0065f
                val pairStart = strokeStart + (pairJitterSec * SAMPLE_RATE).toInt()
                val pairLenFrames = ((stroke.decaySec * 2.2f) * SAMPLE_RATE).toInt()

                val pan = (-0.58f + (1.16f * pair.toFloat() / (jinglePairs - 1).toFloat()) + (rng.nextFloat() - 0.5f) * 0.08f)
                    .coerceIn(-0.68f, 0.68f)
                val panAngle = (pan + 1f) * (PI.toFloat() * 0.25f)
                val gainL = cos(panAngle)
                val gainR = sin(panAngle)

                // Slight manufacturing detune per brass jingle pair (+/- 2.6%)
                val detune = 0.974f + rng.nextFloat() * 0.052f
                val phases = FloatArray(basePartialsHz.size) { rng.nextFloat() * TWO_PI }
                val pairFreqs = FloatArray(basePartialsHz.size) { idx ->
                    (basePartialsHz[idx] * detune * (0.992f + rng.nextFloat() * 0.016f))
                        .coerceAtMost(SAMPLE_RATE * 0.45f)
                }
                // Metallic crisp high-band noise filter (6.5kHz ~ 12.5kHz) for realistic disc-on-pin contact sizzle
                val metallicSizzleBp = BandpassBiquad(7800f + rng.nextFloat() * 3200f, q = 1.45f)
                val chatterRateHz = 38f + rng.nextFloat() * 28f
                var chatterPhase = rng.nextFloat() * TWO_PI

                for (k in 0 until pairLenFrames) {
                    val dst = pairStart + k
                    if (dst >= frames) break
                    val t = k.toFloat() / SAMPLE_RATE.toFloat()
                    val attack = (t / 0.0010f).coerceIn(0f, 1f)
                    // Fast initial impact + shimmering metallic ring tail
                    val fastSnap = exp(-t / (stroke.decaySec * 0.24f))
                    val shimmerTail = exp(-t / stroke.decaySec)

                    // Jingle pair micro-chatter amplitude modulation (two discs bouncing against each other)
                    chatterPhase += TWO_PI * chatterRateHz / SAMPLE_RATE.toFloat()
                    val chatterMod = 0.76f + 0.24f * sin(chatterPhase)

                    var modalSum = 0f
                    for (m in pairFreqs.indices) {
                        // Subtle FM pitch-bend on impact when discs collide
                        val fmMod = 1f + 0.008f * fastSnap * sin(chatterPhase * 2.7f + m)
                        phases[m] += TWO_PI * (pairFreqs[m] * fmMod) / SAMPLE_RATE.toFloat()
                        if (phases[m] > TWO_PI) phases[m] -= TWO_PI
                        // Higher partials decay slightly faster for natural brass damping
                        val modeDecay = exp(-t / (stroke.decaySec * (1.05f - m * 0.07f)))
                        modalSum += sin(phases[m]) * partialWeights[m] * modeDecay
                    }

                    val sizzle = metallicSizzleBp.process(nextGaussian(rng)) * (0.58f * fastSnap + 0.26f * shimmerTail)
                    val jingleSample = (modalSum * 0.68f + sizzle * 0.56f) *
                        attack * chatterMod * stroke.velocity * 0.34f

                    out[dst * 2] += jingleSample * gainL
                    out[dst * 2 + 1] += jingleSample * gainR
                }
            }
        }

        applyConcertHallEarlyReflections(out, frames, wetMix = 0.24f, damping = 0.18f)
        normalizeStereoBuffer(out, targetPeak = 0.88f)
        return out
    }
}
