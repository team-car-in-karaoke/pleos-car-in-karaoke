package io.github.teamcarinkaraoke.pleosstage.mic

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

actual fun warmUpLocalStageSfx() {
    val ctx = AndroidPlatformContextHolder.appContext ?: return
    MobileStageSfxSoundPool.warmUpAsync(ctx)
}

actual fun playLocalStageSfx(sfxType: String, volumeScale: Float) {
    val ctx = AndroidPlatformContextHolder.appContext ?: return
    MobileStageSfxSoundPool.play(ctx, sfxType, volumeScale)
}

/**
 * Smartphone Polyphonic 48kHz Stereo WAV SoundPool Engine for APPLAUSE, CHEER, and TAMBOURINE.
 * Completely independent of AudioRecord / socket streaming.
 */
private object MobileStageSfxSoundPool {
    private const val SAMPLE_RATE = 48_000
    private const val TWO_PI = (2.0 * PI).toFloat()

    private val warmingUp = AtomicBoolean(false)

    @Volatile
    private var soundPool: SoundPool? = null

    private val soundIdMap = ConcurrentHashMap<String, Int>()
    private val loadedSampleIds = ConcurrentHashMap.newKeySet<Int>()
    private val pendingPlayTypes = ConcurrentHashMap<Int, Float>()

    fun warmUpAsync(context: Context) {
        if (soundPool != null && loadedSampleIds.size >= 3) return
        if (!warmingUp.compareAndSet(false, true)) return

        val appCtx = context.applicationContext
        Thread(
            {
                try {
                    ensureInitialized(appCtx)
                } catch (_: Exception) {
                } finally {
                    warmingUp.set(false)
                }
            },
            "PleosMobileSfxWarmUp"
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun play(context: Context, rawType: String, volumeScale: Float) {
        val type = rawType.uppercase().trim()
        val clampedVol = volumeScale.coerceIn(0.05f, 1.0f)
        val pool = soundPool
        val sampleId = soundIdMap[type]
        if (pool != null && sampleId != null && loadedSampleIds.contains(sampleId)) {
            pool.play(sampleId, clampedVol, clampedVol, 1, 0, 1.0f)
            return
        }

        val appCtx = context.applicationContext
        Thread(
            {
                try {
                    ensureInitialized(appCtx)
                    val readyPool = soundPool ?: return@Thread
                    val id = soundIdMap[type] ?: return@Thread
                    if (loadedSampleIds.contains(id)) {
                        readyPool.play(id, clampedVol, clampedVol, 1, 0, 1.0f)
                    } else {
                        pendingPlayTypes[id] = clampedVol
                    }
                } catch (_: Exception) {
                }
            },
            "PleosMobileSfxTrigger"
        ).apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    private fun ensureInitialized(appCtx: Context) {
        if (soundPool != null && soundIdMap.size >= 3) return

        val sfxDir = File(appCtx.cacheDir, "pleos_stage_sfx_v2").apply { mkdirs() }
        val applauseWav = File(sfxDir, "applause_48k_st.wav")
        val cheerWav = File(sfxDir, "cheer_48k_st.wav")
        val tambourineWav = File(sfxDir, "tambourine_48k_st.wav")

        val needApplause = !applauseWav.exists() || applauseWav.length() < 10_000L
        val needCheer = !cheerWav.exists() || cheerWav.length() < 10_000L
        val needTambourine = !tambourineWav.exists() || tambourineWav.length() < 10_000L

        if (needApplause || needCheer) {
            val applauseBuf = synthesizeApplauseStereo()
            if (needApplause) writeStereoFloatAsWav16(applauseWav, applauseBuf)
            if (needCheer) {
                val cheerBuf = synthesizeCheerStereo(applauseBuf)
                writeStereoFloatAsWav16(cheerWav, cheerBuf)
            }
        }
        if (needTambourine) {
            val tambBuf = synthesizeTambourineStereo()
            writeStereoFloatAsWav16(tambourineWav, tambBuf)
        }

        val pool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()

        pool.setOnLoadCompleteListener { sp, sampleId, status ->
            if (status == 0) {
                loadedSampleIds.add(sampleId)
                val pendingVol = pendingPlayTypes.remove(sampleId)
                if (pendingVol != null) {
                    sp.play(sampleId, pendingVol, pendingVol, 1, 0, 1.0f)
                }
            }
        }

        soundIdMap["APPLAUSE"] = pool.load(applauseWav.absolutePath, 1)
        soundIdMap["CHEER"] = pool.load(cheerWav.absolutePath, 1)
        soundIdMap["TAMBOURINE"] = pool.load(tambourineWav.absolutePath, 1)
        soundPool = pool
    }

    private fun writeStereoFloatAsWav16(targetFile: File, stereo: FloatArray) {
        val pcmByteLen = stereo.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcmByteLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // Subchunk1Size (PCM)
        header.putShort(1) // AudioFormat = 1 (PCM)
        header.putShort(2) // NumChannels = 2 (Stereo)
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 2 * 2) // ByteRate
        header.putShort(4) // BlockAlign
        header.putShort(16) // BitsPerSample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmByteLen)

        BufferedOutputStream(FileOutputStream(targetFile), 32_768).use { bos ->
            bos.write(header.array())
            val chunk = ByteArray(4096)
            var idx = 0
            for (v in stereo) {
                val s = (v.coerceIn(-0.98f, 0.98f) * 32760f).toInt().coerceIn(-32767, 32767)
                chunk[idx++] = (s and 0xFF).toByte()
                chunk[idx++] = ((s shr 8) and 0xFF).toByte()
                if (idx >= chunk.size) {
                    bos.write(chunk, 0, idx)
                    idx = 0
                }
            }
            if (idx > 0) {
                bos.write(chunk, 0, idx)
            }
            bos.flush()
        }
    }

    private class BandpassBiquad(centerHz: Float, q: Float) {
        private val b0: Float
        private val b2: Float
        private val a1: Float
        private val a2: Float
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        init {
            val safeFreq = centerHz.coerceIn(60f, SAMPLE_RATE * 0.46f)
            val safeQ = q.coerceAtLeast(0.35f)
            val w0 = TWO_PI * safeFreq / SAMPLE_RATE.toFloat()
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
            val y0 = b0 * x0 + b2 * x2 - a1 * y1 - a2 * y2
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
        var sum = 0f
        repeat(6) { sum += rng.nextFloat() }
        return (sum - 3f) * 1.4142f
    }

    private fun applyHallReflections(dryStereo: FloatArray, frames: Int, wetMix: Float, damping: Float) {
        val tapDelaysMs = floatArrayOf(11.3f, 18.7f, 27.9f, 39.4f, 53.8f, 71.2f)
        val tapGainsL = floatArrayOf(0.35f, -0.23f, 0.27f, 0.18f, -0.15f, 0.11f)
        val tapGainsR = floatArrayOf(-0.25f, 0.33f, -0.20f, 0.25f, 0.17f, -0.12f)
        val tapSamples = IntArray(tapDelaysMs.size) { ((tapDelaysMs[it] / 1000f) * SAMPLE_RATE).toInt().coerceAtLeast(1) }

        val wetL = FloatArray(frames)
        val wetR = FloatArray(frames)
        var lpL = 0f
        var lpR = 0f
        for (i in 0 until frames) {
            var accL = 0f
            var accR = 0f
            for (t in tapSamples.indices) {
                val src = i - tapSamples[t]
                if (src >= 0) {
                    val sL = dryStereo[src * 2]
                    val sR = dryStereo[src * 2 + 1]
                    accL += (sL * 0.7f + sR * 0.3f) * tapGainsL[t]
                    accR += (sR * 0.7f + sL * 0.3f) * tapGainsR[t]
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

    private fun normalizeStereo(stereo: FloatArray, targetPeak: Float) {
        var maxAbs = 0.0001f
        for (v in stereo) {
            val a = abs(v)
            if (a > maxAbs) maxAbs = a
        }
        val scale = targetPeak / maxAbs
        for (i in stereo.indices) {
            stereo[i] = (stereo[i] * scale).coerceIn(-0.98f, 0.98f)
        }
    }

    private fun synthesizeApplauseStereo(): FloatArray {
        val durationSec = 2.20f
        val frames = (SAMPLE_RATE * durationSec).toInt()
        val out = FloatArray(frames * 2)
        val rng = Random(20261114L)
        val clappers = 50
        for (p in 0 until clappers) {
            val pan = (-0.88f + 1.76f * p / (clappers - 1f) + (rng.nextFloat() - 0.5f) * 0.1f).coerceIn(-0.92f, 0.92f)
            val ang = (pan + 1f) * (PI.toFloat() * 0.25f)
            val gL = cos(ang)
            val gR = sin(ang)
            val f0 = if (p % 2 == 0) 920f + rng.nextFloat() * 680f else 1520f + rng.nextFloat() * 1060f
            val bpMain = BandpassBiquad(f0, 1.6f + rng.nextFloat() * 1.4f)
            val bpSnap = BandpassBiquad((f0 * 1.48f).coerceAtMost(3300f), 2.1f)
            val baseInterval = 1f / (3.3f + rng.nextFloat() * 2.0f)
            var tSec = rng.nextFloat() * 0.16f
            val distGain = 0.55f + rng.nextFloat() * 0.45f
            while (tSec < durationSec - 0.08f) {
                val startFrame = (tSec * SAMPLE_RATE).toInt()
                val clapFrames = ((0.048f + rng.nextFloat() * 0.025f) * SAMPLE_RATE).toInt()
                val prog = tSec / durationSec
                val macro = when {
                    prog < 0.08f -> (prog / 0.08f).pow(0.65f)
                    prog < 0.68f -> 1f - (prog - 0.08f) * 0.12f
                    else -> ((1f - prog) / 0.32f).coerceAtLeast(0f).pow(1.45f)
                }
                val vel = distGain * (0.7f + rng.nextFloat() * 0.35f) * macro
                if (vel > 0.02f) {
                    bpMain.reset()
                    bpSnap.reset()
                    val atk = (0.0011f * SAMPLE_RATE).toInt().coerceAtLeast(1)
                    val tauFast = 0.0065f + rng.nextFloat() * 0.0035f
                    val tauBody = 0.024f + rng.nextFloat() * 0.015f
                    for (k in 0 until clapFrames) {
                        val dst = startFrame + k
                        if (dst >= frames) break
                        val lt = k.toFloat() / SAMPLE_RATE.toFloat()
                        val a = if (k < atk) k.toFloat() / atk.toFloat() else 1f
                        val sEnv = exp(-lt / tauFast)
                        val bEnv = exp(-lt / tauBody)
                        val w = nextGaussian(rng)
                        val smp = (bpMain.process(w) * 0.76f + bpSnap.process(w) * sEnv * 0.44f) *
                            a * (0.68f * sEnv + 0.32f * bEnv) * vel
                        out[dst * 2] += smp * gL
                        out[dst * 2 + 1] += smp * gR
                    }
                }
                tSec += (baseInterval + (rng.nextFloat() - 0.5f) * 0.052f).coerceAtLeast(0.095f)
            }
        }
        applyHallReflections(out, frames, wetMix = 0.40f, damping = 0.32f)
        normalizeStereo(out, 0.85f)
        return out
    }

    private fun synthesizeCheerStereo(applauseSource: FloatArray): FloatArray {
        val durationSec = 2.50f
        val frames = (SAMPLE_RATE * durationSec).toInt()
        val out = FloatArray(frames * 2)
        val rng = Random(20260707L)
        val voices = 20
        for (v in 0 until voices) {
            val pan = (-0.85f + 1.70f * v / (voices - 1f)).coerceIn(-0.9f, 0.9f)
            val ang = (pan + 1f) * (PI.toFloat() * 0.25f)
            val gL = cos(ang)
            val gR = sin(ang)
            val startF0 = if (v % 2 == 0) 150f + rng.nextFloat() * 65f else 260f + rng.nextFloat() * 130f
            val peakF0 = startF0 * 2f.pow((3.5f + rng.nextFloat() * 2.5f) / 12f)
            val f1 = BandpassBiquad(650f + rng.nextFloat() * 200f, 4.8f)
            val f2 = BandpassBiquad(1200f + rng.nextFloat() * 400f, 5.4f)
            val f3 = BandpassBiquad(2500f + rng.nextFloat() * 500f, 6.2f)
            val onset = BandpassBiquad(470f + rng.nextFloat() * 80f, 3.8f)
            val startIdx = (rng.nextFloat() * 0.14f * SAMPLE_RATE).toInt()
            val endIdx = ((durationSec - 0.06f - rng.nextFloat() * 0.20f) * SAMPLE_RATE).toInt().coerceAtMost(frames)
            val vibHz = 4.9f + rng.nextFloat() * 2.0f
            var vibPhase = rng.nextFloat() * TWO_PI
            var glottalPhase = rng.nextFloat()
            val vGain = (0.7f + rng.nextFloat() * 0.3f) / sqrt(voices.toFloat())
            for (i in startIdx until endIdx) {
                val vt = (i - startIdx).toFloat() / SAMPLE_RATE.toFloat()
                val vDur = (endIdx - startIdx).toFloat() / SAMPLE_RATE.toFloat()
                val nt = (vt / vDur).coerceIn(0f, 1f)
                val env = when {
                    vt < 0.09f -> (vt / 0.09f).pow(0.75f)
                    nt < 0.68f -> 1f - (nt - 0.1f).coerceAtLeast(0f) * 0.15f
                    else -> ((1f - nt) / 0.32f).coerceAtLeast(0f).pow(1.55f)
                }
                val gp = (vt / 0.48f).coerceIn(0f, 1f)
                val cg = 1f - (1f - gp) * (1f - gp)
                vibPhase += TWO_PI * vibHz / SAMPLE_RATE.toFloat()
                val f0 = (startF0 + (peakF0 - startF0) * cg) * (1f + sin(vibPhase) * 0.015f * gp)
                glottalPhase += f0 / SAMPLE_RATE.toFloat()
                if (glottalPhase >= 1f) glottalPhase -= 1f
                val p = glottalPhase
                val gw = if (p < 0.42f) {
                    val u = p / 0.42f
                    sin(PI.toFloat() * u) - 0.35f * sin(TWO_PI * u)
                } else {
                    -0.28f * exp(-(p - 0.42f) * 7.5f)
                }
                val harm = sin(TWO_PI * p * 2f) * 0.45f + sin(TWO_PI * p * 3f) * 0.32f + sin(TWO_PI * p * 4f) * 0.22f
                val exc = gw * 0.75f + harm * 0.45f + nextGaussian(rng) * 0.18f
                val openMix = (vt / 0.12f).coerceIn(0f, 1f)
                val voiced = (
                    onset.process(exc) * (1f - openMix) * 0.7f +
                        (f1.process(exc) + f2.process(exc) * 0.68f + f3.process(exc) * 0.34f) * (0.25f + 0.75f * openMix)
                    ) * env * vGain
                out[i * 2] += voiced * gL
                out[i * 2 + 1] += voiced * gR
            }
        }
        val chordFreqs = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f)
        val chordOnsets = floatArrayOf(0.01f, 0.065f, 0.12f, 0.175f)
        val chordPans = floatArrayOf(-0.4f, 0.38f, -0.22f, 0.28f)
        for (n in chordFreqs.indices) {
            val freq = chordFreqs[n]
            val sIdx = (chordOnsets[n] * SAMPLE_RATE).toInt()
            val nFrames = (1.6f * SAMPLE_RATE).toInt()
            val ang = (chordPans[n] + 1f) * (PI.toFloat() * 0.25f)
            val gL = cos(ang) * 0.23f
            val gR = sin(ang) * 0.23f
            var ph = 0f
            for (k in 0 until nFrames) {
                val dst = sIdx + k
                if (dst >= frames) break
                val t = k.toFloat() / SAMPLE_RATE.toFloat()
                ph += TWO_PI * freq / SAMPLE_RATE.toFloat()
                if (ph > TWO_PI) ph -= TWO_PI
                val atk = (t / 0.014f).coerceIn(0f, 1f)
                val sus = exp(-t / 0.62f)
                val spk = exp(-t / 0.19f)
                val s = (sin(ph) * sus + sin(ph * 2f) * (0.34f * sus + 0.18f * spk) + sin(ph * 3f) * 0.2f * sus + sin(ph * 4f) * 0.11f * spk) * atk * 0.42f
                out[dst * 2] += s * gL
                out[dst * 2 + 1] += s * gR
            }
        }
        applyHallReflections(out, frames, wetMix = 0.36f, damping = 0.28f)
        val appFrames = applauseSource.size / 2
        for (i in 0 until frames) {
            val tSec = i.toFloat() / SAMPLE_RATE.toFloat()
            val env = when {
                tSec < 0.08f -> tSec / 0.08f
                tSec > durationSec - 0.45f -> ((durationSec - tSec) / 0.45f).coerceIn(0f, 1f)
                else -> 1f
            }
            val ia = i % appFrames
            val ib = (i + appFrames / 3) % appFrames
            out[i * 2] = out[i * 2] * 0.72f + (applauseSource[ia * 2] * 0.72f + applauseSource[ib * 2 + 1] * 0.45f) * env * 0.46f
            out[i * 2 + 1] = out[i * 2 + 1] * 0.72f + (applauseSource[ia * 2 + 1] * 0.72f + applauseSource[ib * 2] * 0.45f) * env * 0.46f
        }
        normalizeStereo(out, 0.86f)
        return out
    }

    private fun synthesizeTambourineStereo(): FloatArray {
        val durationSec = 0.90f
        val frames = (SAMPLE_RATE * durationSec).toInt()
        val out = FloatArray(frames * 2)
        val rng = Random(20260909L)
        val strokeTimes = floatArrayOf(0.012f, 0.105f, 0.245f, 0.440f, 0.635f)
        val strokeVels = floatArrayOf(1.00f, 0.46f, 0.80f, 0.84f, 0.96f)
        val strokeBody = booleanArrayOf(true, false, false, false, true)
        val strokeDecay = floatArrayOf(0.095f, 0.065f, 0.085f, 0.088f, 0.135f)
        val basePartials = floatArrayOf(5400f, 6900f, 8700f, 10800f, 12600f, 14300f)
        val weights = floatArrayOf(0.28f, 0.25f, 0.21f, 0.17f, 0.13f, 0.08f)

        for (s in strokeTimes.indices) {
            val sStart = (strokeTimes[s] * SAMPLE_RATE).toInt()
            val vel = strokeVels[s]
            val dec = strokeDecay[s]
            if (strokeBody[s]) {
                val bScale = if (s == 0) 1.0f else 0.55f
                val bFrames = (0.045f * SAMPLE_RATE).toInt()
                val palmBp = BandpassBiquad(245f, 2.1f)
                var ph = 0f
                for (k in 0 until bFrames) {
                    val dst = sStart + k
                    if (dst >= frames) break
                    val t = k.toFloat() / SAMPLE_RATE.toFloat()
                    val f = 182f + (320f - 182f) * exp(-t / 0.0085f)
                    ph += TWO_PI * f / SAMPLE_RATE.toFloat()
                    val env = (t / 0.0012f).coerceIn(0f, 1f) * exp(-t / 0.0115f)
                    val smp = (sin(ph) * 0.72f + palmBp.process(nextGaussian(rng)) * exp(-t / 0.0055f) * 0.55f) * env * vel * bScale * 0.48f
                    out[dst * 2] += smp * 0.96f
                    out[dst * 2 + 1] += smp * 1.04f
                }
            }
            for (pair in 0 until 6) {
                val pStart = sStart + (rng.nextFloat() * 0.0065f * SAMPLE_RATE).toInt()
                val pLen = ((dec * 2.2f) * SAMPLE_RATE).toInt()
                val pan = (-0.58f + 1.16f * pair / 5f).coerceIn(-0.68f, 0.68f)
                val ang = (pan + 1f) * (PI.toFloat() * 0.25f)
                val gL = cos(ang)
                val gR = sin(ang)
                val det = 0.974f + rng.nextFloat() * 0.052f
                val phases = FloatArray(basePartials.size) { rng.nextFloat() * TWO_PI }
                val freqs = FloatArray(basePartials.size) { idx ->
                    (basePartials[idx] * det * (0.992f + rng.nextFloat() * 0.016f)).coerceAtMost(SAMPLE_RATE * 0.45f)
                }
                val sizzleBp = BandpassBiquad(7800f + rng.nextFloat() * 3200f, 1.45f)
                val chatHz = 38f + rng.nextFloat() * 28f
                var chatPh = rng.nextFloat() * TWO_PI
                for (k in 0 until pLen) {
                    val dst = pStart + k
                    if (dst >= frames) break
                    val t = k.toFloat() / SAMPLE_RATE.toFloat()
                    val atk = (t / 0.0010f).coerceIn(0f, 1f)
                    val fastSnap = exp(-t / (dec * 0.24f))
                    val tail = exp(-t / dec)
                    chatPh += TWO_PI * chatHz / SAMPLE_RATE.toFloat()
                    val chatMod = 0.76f + 0.24f * sin(chatPh)
                    var modal = 0f
                    for (m in freqs.indices) {
                        val fm = 1f + 0.008f * fastSnap * sin(chatPh * 2.7f + m)
                        phases[m] += TWO_PI * (freqs[m] * fm) / SAMPLE_RATE.toFloat()
                        if (phases[m] > TWO_PI) phases[m] -= TWO_PI
                        modal += sin(phases[m]) * weights[m] * exp(-t / (dec * (1.05f - m * 0.07f)))
                    }
                    val siz = sizzleBp.process(nextGaussian(rng)) * (0.58f * fastSnap + 0.26f * tail)
                    val jSmp = (modal * 0.68f + siz * 0.56f) * atk * chatMod * vel * 0.34f
                    out[dst * 2] += jSmp * gL
                    out[dst * 2 + 1] += jSmp * gR
                }
            }
        }
        applyHallReflections(out, frames, wetMix = 0.24f, damping = 0.18f)
        normalizeStereo(out, 0.88f)
        return out
    }
}
