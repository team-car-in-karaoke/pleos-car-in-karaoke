package io.github.teamcarinkaraoke.pleosstage.car.model

import org.json.JSONObject
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Result payload for the 5-second Karaoke Score Reveal synchronized 1:1 between Car & Smartphone.
 */
data class KaraokeScoreResult(
    val resultId: String,
    val score: Int,
    val badge: String,
    val title: String,
    val comment: String,
    val pitchAccuracy: Int,
    val vocalPower: Int,
    val stageManner: Int,
    val activeVoiceTicks: Int,
    val trackTitle: String = "",
    val willAdvanceNext: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("resultId", resultId)
        put("score", score)
        put("badge", badge)
        put("title", title)
        put("comment", comment)
        put("pitchAccuracy", pitchAccuracy)
        put("vocalPower", vocalPower)
        put("stageManner", stageManner)
        put("activeVoiceTicks", activeVoiceTicks)
        put("trackTitle", trackTitle)
        put("willAdvanceNext", willAdvanceNext)
        put("ts", timestampMs)
    }
}

/**
 * Accumulates real-time vocal telemetry (`micLevelPercent`, `pitchHz`) during song playback.
 * Reset whenever a new song starts or the active track changes.
 */
class VocalScoreAccumulator {
    var activeVoiceTicks: Int = 0
        private set
    var activeVoiceSeconds: Double = 0.0
        private set
    var energySum: Double = 0.0
        private set
    var peakEnergy: Float = 0f
        private set
    var pitchDetectedTicks: Int = 0
        private set

    val avgActiveEnergy: Double
        get() = if (activeVoiceTicks > 0) energySum / activeVoiceTicks.toDouble() else 0.0

    fun reset() {
        activeVoiceTicks = 0
        activeVoiceSeconds = 0.0
        energySum = 0.0
        peakEnergy = 0f
        pitchDetectedTicks = 0
    }

    /**
     * Records a telemetry tick (~10-20 Hz while song is playing).
     * - `activeVoiceTicks`: count where `micLevelPercent >= 8`
     * - `peakAndAvgEnergy`: average & peak vocal energy within active vocal segments
     * - `pitchDetectedTicks`: count where `pitchHz > 60`
     */
    fun recordTick(micLevelPercent: Int, pitchHz: Float, dtSeconds: Double = 0.085) {
        val clampedLevel = micLevelPercent.coerceIn(0, 100)
        if (clampedLevel >= 8) {
            activeVoiceTicks++
            activeVoiceSeconds += dtSeconds.coerceIn(0.03, 0.25)
            energySum += clampedLevel.toDouble()
            if (clampedLevel.toFloat() > peakEnergy) {
                peakEnergy = clampedLevel.toFloat()
            }
            if (pitchHz > 60f) {
                pitchDetectedTicks++
            }
        }
    }
}

object KaraokeScoreCalculator {

    /**
     * User-friendly 100-point skewed (`75 ~ 100`, strongly skewed toward `92 ~ 100`) vocal score formula.
     */
    fun calculateKaraokeScore(
        activeVoiceTicks: Int,
        activeVoiceSeconds: Double,
        avgActiveEnergy: Double,
        peakEnergy: Float,
        pitchDetectedTicks: Int,
        trackTitle: String = "",
        willAdvanceNext: Boolean = false
    ): KaraokeScoreResult {
        val finalScore: Int
        val pitchGauge: Int
        val powerGauge: Int
        val mannerGauge: Int
        val detailComment: String

        if (activeVoiceTicks <= 0 || activeVoiceSeconds < 1.5) {
            // Unvoiced or < 1.5s of actual real-mic vocal input: baseline encouraging score in 75..79 (never 82..100)
            finalScore = Random.nextInt(75, 80)
            pitchGauge = (finalScore + Random.nextInt(-2, 4)).coerceIn(74, 84)
            powerGauge = (finalScore + Random.nextInt(-3, 3)).coerceIn(73, 83)
            mannerGauge = (finalScore + Random.nextInt(2, 8)).coerceIn(78, 88)
            detailComment = "반주와 함께 기분 좋은 드라이빙 스테이지를 즐기셨어요! 다음 곡에선 마이크에 목소리를 들려주세요!"
        } else {
            // 1. Participation P = min(1.0, activeVoiceSeconds / 18.0), at least 0.38 guaranteed if sung >= 1s (or active phrase)
            val rawP = min(1.0, activeVoiceSeconds / 18.0).coerceAtLeast(0.0)
            val participationP = if (activeVoiceSeconds >= 1.0 || activeVoiceTicks >= 10) {
                max(0.38, rawP)
            } else {
                max(0.24, rawP)
            }

            // 2. Energy E = min(1.0, avgActiveEnergy / 28.0)
            val energyE = min(1.0, avgActiveEnergy / 28.0).coerceAtLeast(0.0)

            // 3. Pitch Vitality V = min(1.0, pitchActiveRatio * 1.4)
            val pitchActiveRatio = (pitchDetectedTicks.toDouble() / activeVoiceTicks.coerceAtLeast(1).toDouble())
                .coerceIn(0.0, 1.0)
            val pitchVitalityV = min(1.0, pitchActiveRatio * 1.4).coerceAtLeast(0.0)

            // 4. Normalized composite x = clamp(0.50 * P + 0.30 * E + 0.20 * V, 0.0, 1.0)
            val x = (0.50 * participationP + 0.30 * energyE + 0.20 * pitchVitalityV).coerceIn(0.0, 1.0)

            // 5. High-score skew exponent transformation: y = x^0.38
            val y = x.pow(0.38)

            // 6. Passion bonus (0..2)
            val passionBonus = when {
                peakEnergy >= 65f && activeVoiceSeconds >= 10.0 -> 2
                peakEnergy >= 40f || activeVoiceSeconds >= 5.0 || pitchActiveRatio >= 0.55 -> Random.nextInt(1, 3)
                else -> Random.nextInt(0, 2)
            }

            // Final Score = clamp(round(80 + 20 * y + passionBonus(0..2)), 82, 100)
            finalScore = (80.0 + 20.0 * y + passionBonus.toDouble()).roundToInt().coerceIn(82, 100)

            pitchGauge = (82.0 + 18.0 * pitchVitalityV.pow(0.42)).roundToInt()
                .coerceIn(max(82, finalScore - 5), 100)
            powerGauge = (82.0 + 18.0 * energyE.pow(0.42)).roundToInt()
                .coerceIn(max(82, finalScore - 5), 100)
            mannerGauge = (84.0 + 16.0 * participationP.pow(0.38)).roundToInt()
                .coerceIn(max(84, finalScore - 3), 100)

            val peakPct = peakEnergy.roundToInt().coerceIn(12, 100)
            detailComment = when {
                finalScore >= 98 -> "최고 성량 ${peakPct}% · 흔들림 없는 음정과 폭발적인 성량이 완벽한 조화를 이뤘어요!"
                finalScore >= 90 -> "최고 성량 ${peakPct}% · 탄탄한 보컬 파워와 생동감 넘치는 음정 표현이 돋보였어요!"
                else -> "최고 성량 ${peakPct}% · 감미로운 음색과 여유로운 무대 매너로 공간을 가득 채웠어요!"
            }
        }

        val (badge, title) = mapGradeBadgeAndTitle(finalScore)

        return KaraokeScoreResult(
            resultId = "score_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            score = finalScore,
            badge = badge,
            title = title,
            comment = detailComment,
            pitchAccuracy = pitchGauge,
            vocalPower = powerGauge,
            stageManner = mannerGauge,
            activeVoiceTicks = activeVoiceTicks,
            trackTitle = trackTitle,
            willAdvanceNext = willAdvanceNext
        )
    }

    fun calculateFromAccumulator(
        accumulator: VocalScoreAccumulator,
        trackTitle: String = "",
        willAdvanceNext: Boolean = false
    ): KaraokeScoreResult {
        return calculateKaraokeScore(
            activeVoiceTicks = accumulator.activeVoiceTicks,
            activeVoiceSeconds = accumulator.activeVoiceSeconds,
            avgActiveEnergy = accumulator.avgActiveEnergy,
            peakEnergy = accumulator.peakEnergy,
            pitchDetectedTicks = accumulator.pitchDetectedTicks,
            trackTitle = trackTitle,
            willAdvanceNext = willAdvanceNext
        )
    }

    fun mapGradeBadgeAndTitle(score: Int): Pair<String, String> {
        return when {
            score >= 98 -> "GOLDEN CROWN" to "👑 완벽한 레전드 무대! 가왕 탄생!"
            score >= 90 -> "SUPER STAR" to "🎉 환상적인 가창력! 앙코르 요청 쇄도!"
            score >= 82 -> "GREAT STAGE" to "✨ 분위기 최고! 감미로운 라이브 무대!"
            else -> "GOOD VIBE" to "🎵 즐거운 드라이빙 노래방 스테이지!"
        }
    }
}
