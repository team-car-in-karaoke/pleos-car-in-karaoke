package io.github.teamcarinkaraoke.pleosstage.car.sdk

import android.content.Context
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.MoveControl
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.AmbientLightBrightnessColorListener
import ai.pleos.playground.vehicle.listener.AmbientLightBrightnessListener
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Official Pleos Connect Vehicle SDK (`ai.pleos.playground:Vehicle:2.3.2`) Integration Layer.
 *
 * Directly invokes the compiled Pleos Vehicle SDK APIs (`Vehicle`, `Window`, `Light`, `HVAC`,
 * `Seat`, `Toll`, `Gear`, `Brake`).
 *
 * The vehicle's physical Ambient Light (`vehicle.getLight()`) is automatically synchronized
 * in real time to the active song's genre, BPM beat waveform envelope, and vocal pitch/RMS.
 */
enum class GearPosition(val label: String) {
    P("P"),
    R("R"),
    N("N"),
    D("D")
}

enum class VocalZoneTheme(
    val label: String,
    val primaryHex: Long,
    val secondaryHex: Long
) {
    IDLE_OBSIDIAN("스탠바이 무드 조명", 0xFF00E5FF, 0xFF151A24),
    KPOP_SYNTH_GROOVE("K-Pop 비트 싱크 조명", 0xFF00E5FF, 0xFF7C4DFF),
    KPOP_DROP_MAGENTA("클라이맥스 네온 조명", 0xFFFF2E93, 0xFF2B95F6),
    BALLAD_STARLIGHT_WAVE("스타라이트 감성 조명", 0xFF5C6BC0, 0xFF8E24AA),
    BALLAD_WARM_CLIMAX("하이라이트 웜 골드 조명", 0xFFF06292, 0xFFFFB300),
    ENCORE_GOLD_FINALE("앙코르 피날레 조명", 0xFFFFD600, 0xFFFF2E93)
}

data class VehicleDigitalTwinState(
    // 1. Soundproof Booth Mode (Window & Sunroof)
    val allWindowsClosed: Boolean = false,
    val windowOpenPercentFL: Int = 35,
    val windowOpenPercentFR: Int = 35,
    val windowOpenPercentRL: Int = 20,
    val windowOpenPercentRR: Int = 20,
    val roofBlindClosed: Boolean = false,

    // 2. Automatic Genre & Waveform Driven Vehicle Ambient Light (Pleos Light SDK)
    val ambientLightEnabled: Boolean = true,
    val ambientColorHex: Long = 0xFF2B95F6,
    val ambientSecondaryHex: Long = 0xFF7C4DFF,
    val ambientBrightness: Int = 70, // 0..100
    val vocalZoneTheme: VocalZoneTheme = VocalZoneTheme.IDLE_OBSIDIAN,
    val strobeActive: Boolean = false,
    val secondRowMoodLightAlpha: Float = 0.15f,

    // 3. Climax Do-Not-Disturb Shield (Toll & Alert)
    val tollHiPassMuted: Boolean = false,
    val nonCriticalAlertsMuted: Boolean = false,

    // 4. Passionate Singing Cooldown & Greenroom Care (HVAC & Seat)
    val hvacOuterAirMode: Boolean = false,
    val hvacTempCelsius: Float = 22.5f,
    val hvacFanLevel: Int = 2, // 1..7
    val seatVentilationLevel: Int = 0, // 0..3단
    val greenroomCareActive: Boolean = false,
    val highRmsAccumulatedSec: Int = 0,

    // 5. Audience Encore Interaction (Seat & Light Finale)
    val seatRelaxationPulseActive: Boolean = false,
    val encoreFinaleActive: Boolean = false,

    // 6. Driving Safety Interlock (Brake & Gear)
    val gearPosition: GearPosition = GearPosition.P,
    val parkingBrakeEngaged: Boolean = true,
    val isParked: Boolean = true,

    // SDK Telemetry Log
    val sdkConnectedMode: String = "ai.pleos.playground:Vehicle:2.3.2 (Official SDK)",
    val lastSdkCommandLog: String = "Vehicle.initialize() • Auto Genre/Waveform Ambient Sync Ready"
)

class PleosVehicleManager(context: Context) {

    companion object {
        private const val TAG = "PleosVehicleManager"
    }

    private val _twinState = MutableStateFlow(VehicleDigitalTwinState())
    val twinState: StateFlow<VehicleDigitalTwinState> = _twinState.asStateFlow()

    // Official Pleos Playground Vehicle SDK Entry Point
    private val vehicle: Vehicle = Vehicle(context.applicationContext)

    // Pre-defined Vehicle Areas from Official Pleos SDK
    private val allCabinSeats = SeatArea(
        SeatValue.ROW_1_LEFT,
        SeatValue.ROW_1_RIGHT,
        SeatValue.ROW_2_LEFT,
        SeatValue.ROW_2_RIGHT
    )
    private val frontSeats = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT)
    private val secondRowSeats = SeatArea(SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT)
    private val allSideWindows = WindowArea(
        WindowValue.ROW_1_LEFT,
        WindowValue.ROW_1_RIGHT,
        WindowValue.ROW_2_LEFT,
        WindowValue.ROW_2_RIGHT
    )
    private val roofBlindArea = WindowArea(WindowValue.ROOF_TOP_1, WindowValue.ROOF_TOP_2)

    private var lastAppliedSdkColor: Int = 0
    private var lastAppliedSdkBrightness: Int = -1

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            val mapped = when (gear) {
                VehicleGear.GEAR_PARK, null -> GearPosition.P
                VehicleGear.GEAR_REVERSE -> GearPosition.R
                VehicleGear.GEAR_NEUTRAL -> GearPosition.N
                else -> GearPosition.D
            }
            setGearAndBrakeState(mapped, _twinState.value.parkingBrakeEngaged)
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "GearSelectionListener onFailed: ${e.message}")
        }
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) {
            if (status != null) {
                Log.d(TAG, "BrakePedalStatusUpdated: $status")
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "BrakePedalStatusListener onFailed: ${e.message}")
        }
    }

    private val ambientBrightnessListener = object : AmbientLightBrightnessListener {
        override fun onBrightnessUpdated(brightness: Int?) {
            if (brightness != null) {
                _twinState.update { it.copy(ambientBrightness = brightness.coerceIn(0, 100)) }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "AmbientLightBrightnessListener onFailed: ${e.message}")
        }
    }

    private val ambientColorListener = object : AmbientLightBrightnessColorListener {
        override fun onBrightnessColorUpdated(argbColor: Int?) {
            if (argbColor != null) {
                _twinState.update { it.copy(ambientColorHex = argbColor.toLong() and 0xFFFFFFFFL) }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "AmbientLightBrightnessColorListener onFailed: ${e.message}")
        }
    }

    private val ambientStateListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) {
            if (state != null) {
                _twinState.update { it.copy(ambientLightEnabled = state == LightState.ON) }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "AmbientLightStateListener onFailed: ${e.message}")
        }
    }

    init {
        initializePleosVehicleSdk()
    }

    private fun initializePleosVehicleSdk() {
        try {
            vehicle.initialize()
            vehicle.getGear().registerGearSelection(gearListener)
            vehicle.getBrake().registerBrakePedalStatus(brakeListener)
            vehicle.getLight().registerAmbientLightBrightnessListener(allCabinSeats, ambientBrightnessListener)
            vehicle.getLight().registerAmbientLightBrightnessColorListener(allCabinSeats, ambientColorListener)
            vehicle.getLight().registerAmbientLightStateListener(allCabinSeats, ambientStateListener)
            vehicle.getGear().getCurrentGearSelectionState(
                { gear -> gearListener.onSelected(gear) },
                { e -> Log.d(TAG, "getCurrentGearSelectionState fallback: ${e.message}") }
            )
            Log.i(TAG, "Official Pleos Vehicle SDK (v2.3.2) initialized")
        } catch (t: Throwable) {
            Log.i(TAG, "Running on standard emulator without Pleos VHAL daemon (${t.message})")
        }
    }

    fun release() {
        try {
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.getBrake().unregisterBrakePedalStatus(brakeListener)
            vehicle.getLight().unregisterAmbientLightBrightnessListener(allCabinSeats, ambientBrightnessListener)
            vehicle.getLight().unregisterAmbientLightBrightnessColorListener(allCabinSeats, ambientColorListener)
            vehicle.getLight().unregisterAmbientLightStateListener(allCabinSeats, ambientStateListener)
            vehicle.release()
        } catch (t: Throwable) {
            Log.d(TAG, "Vehicle.release() safe ignore: ${t.message}")
        }
    }

    /**
     * Automatically sets the initial vehicle ambient light mood via Pleos SDK when a song/genre is selected.
     */
    fun onSongGenreSelected(songId: String, genre: String, bpm: Int) {
        val isBallad = genre.contains("Ballad", ignoreCase = true) || bpm < 95
        val zone = if (isBallad) VocalZoneTheme.BALLAD_STARLIGHT_WAVE else VocalZoneTheme.KPOP_SYNTH_GROOVE
        val initialBrightness = if (isBallad) 55 else 75

        applyVehicleAmbientLightSdk(
            argbColor = zone.primaryHex.toInt(),
            brightness = initialBrightness,
            readingLightOn = false
        )

        _twinState.update {
            it.copy(
                ambientLightEnabled = true,
                ambientColorHex = zone.primaryHex,
                ambientSecondaryHex = zone.secondaryHex,
                ambientBrightness = initialBrightness,
                vocalZoneTheme = zone,
                lastSdkCommandLog = "[Genre Sync: $genre] Light.setAmbientLightBrightnessColor(0x${zone.primaryHex.toString(16).uppercase()}) | Brightness=$initialBrightness%"
            )
        }
    }

    /**
     * Choreography #1 & #3:
     * Triggered when a song starts:
     * - [방음 부스 모드 (Window & Sunroof)]: Closes all 4 windows (`setWindowPosition(0)`) & roof blind (`moveWindowBlind(FORWARD)`).
     * - [클라이맥스 방해 금지 실드 (Toll & Alert)]: Mutes Hi-Pass ETCS terminal voice (`setEtcsVoiceGuidanceVolume(0)`).
     * - [앰비언트 라이트 자동 활성화 (Light)]: Enables cabin ambient lighting (`setAmbientLightSwitch(ON)`).
     */
    fun onSongStarted(songTitle: String, genre: String = "", bpm: Int = 120) {
        val isBallad = genre.contains("Ballad", ignoreCase = true) || bpm < 95
        val startZone = if (isBallad) VocalZoneTheme.BALLAD_STARLIGHT_WAVE else VocalZoneTheme.KPOP_SYNTH_GROOVE

        try {
            vehicle.getWindow().setWindowPosition(
                allSideWindows,
                0,
                { _, pos -> Log.d(TAG, "Windows closed to $pos%") },
                { e -> Log.d(TAG, "Window.setWindowPosition fallback: ${e.message}") }
            )
            vehicle.getWindow().moveWindowBlind(
                roofBlindArea,
                MoveControl.FORWARD,
                { _, ctrl -> Log.d(TAG, "Roof blind closed: $ctrl") },
                { e -> Log.d(TAG, "Window.moveWindowBlind fallback: ${e.message}") }
            )
            vehicle.getToll().setEtcsVoiceGuidanceVolume(
                0,
                { Log.d(TAG, "Toll ETCS voice muted (0)") },
                { e -> Log.d(TAG, "Toll.setEtcsVoiceGuidanceVolume fallback: ${e.message}") }
            )
            vehicle.getLight().setAmbientLightSwitch(
                allCabinSeats,
                LightSwitch.ON,
                { _, sw -> Log.d(TAG, "Ambient light switch: $sw") },
                { e -> Log.d(TAG, "Light.setAmbientLightSwitch fallback: ${e.message}") }
            )
            vehicle.getLight().setAmbientColorAutoByDriveModeEnabled(
                false,
                { _ -> },
                { _ -> }
            )
        } catch (t: Throwable) {
            Log.d(TAG, "onSongStarted SDK call fallback: ${t.message}")
        }

        applyVehicleAmbientLightSdk(
            argbColor = startZone.primaryHex.toInt(),
            brightness = if (isBallad) 60 else 80,
            readingLightOn = false
        )

        _twinState.update {
            it.copy(
                allWindowsClosed = true,
                windowOpenPercentFL = 0,
                windowOpenPercentFR = 0,
                windowOpenPercentRL = 0,
                windowOpenPercentRR = 0,
                roofBlindClosed = true,
                ambientLightEnabled = true,
                ambientColorHex = startZone.primaryHex,
                ambientSecondaryHex = startZone.secondaryHex,
                vocalZoneTheme = startZone,
                tollHiPassMuted = true,
                nonCriticalAlertsMuted = true,
                encoreFinaleActive = false,
                seatRelaxationPulseActive = false,
                lastSdkCommandLog = "[Stage Start] Window.setWindowPosition(0) | Light.setAmbientLightSwitch(ON) | Toll.setEtcsVoiceGuidanceVolume(0)"
            )
        }
    }

    /**
     * Choreography #2:
     * [장르·파형·보컬 실시간 반응형 차량 앰비언트 라이트 (Pleos SDK Light Domain)]
     * Automatically modulates the physical vehicle's ambient light color and brightness
     * based on the song's tempo, BPM beat waveform phase,
     * and live vocal RMS/Pitch waveform.
     */
    fun onMusicAndVocalWaveformTick(
        genre: String,
        bpm: Int,
        elapsedSec: Float,
        vocalRms: Float,
        vocalPitchHz: Float,
        isHighlightOrInterlude: Boolean
    ) {
        val isBallad = genre.contains("Ballad", ignoreCase = true) || bpm < 95

        // Compute real-time musical beat waveform envelope (0.0 .. 1.0) from BPM & elapsed time
        val beatsElapsed = elapsedSec * (bpm.coerceAtLeast(60) / 60f)
        val beatWave = ((sin(beatsElapsed * 2.0 * PI) + 1.0) * 0.5).toFloat()
        val barIndex = (beatsElapsed / 4f).toInt()

        // Combine instrumental waveform energy with live vocal RMS (0..100)
        val instrumentalEnergy = if (isBallad) {
            28f + beatWave * 24f + (if (isHighlightOrInterlude) 18f else 0f)
        } else {
            42f + beatWave * 36f + (if (isHighlightOrInterlude) 20f else 0f)
        }
        val combinedWaveEnergy = maxOf(instrumentalEnergy, vocalRms)

        val isClimax = vocalPitchHz > 370f || vocalRms > 75f || (isHighlightOrInterlude && beatWave > 0.7f)

        val zone: VocalZoneTheme
        val targetColorHex: Long

        if (_twinState.value.encoreFinaleActive) {
            zone = VocalZoneTheme.ENCORE_GOLD_FINALE
            targetColorHex = if (beatWave > 0.5f) 0xFFFFD600 else 0xFFFF2E93
        } else if (isBallad) {
            if (isClimax) {
                zone = VocalZoneTheme.BALLAD_WARM_CLIMAX
                targetColorHex = blendColorHex(0xFFF06292, 0xFFFFB300, beatWave)
            } else {
                zone = VocalZoneTheme.BALLAD_STARLIGHT_WAVE
                val balladPalette = listOf(0xFF3949AB, 0xFF5E35B1, 0xFF039BE5, 0xFF8E24AA)
                val baseHex = balladPalette[barIndex % balladPalette.size]
                val nextHex = balladPalette[(barIndex + 1) % balladPalette.size]
                targetColorHex = blendColorHex(baseHex, nextHex, beatWave)
            }
        } else {
            // Dynamic Neon Stage
            if (isClimax) {
                zone = VocalZoneTheme.KPOP_DROP_MAGENTA
                targetColorHex = blendColorHex(0xFFFF2E93, 0xFF00E5FF, beatWave)
            } else {
                zone = VocalZoneTheme.KPOP_SYNTH_GROOVE
                val kpopPalette = listOf(0xFF00E5FF, 0xFF2B95F6, 0xFF7C4DFF, 0xFF00E676)
                val baseHex = kpopPalette[barIndex % kpopPalette.size]
                val nextHex = kpopPalette[(barIndex + 1) % kpopPalette.size]
                targetColorHex = blendColorHex(baseHex, nextHex, beatWave)
            }
        }

        val targetBrightness = (30f + combinedWaveEnergy * 0.7f).roundToInt().coerceIn(25, 100)
        val strobe = isClimax && combinedWaveEnergy > 82f

        applyVehicleAmbientLightSdk(
            argbColor = targetColorHex.toInt(),
            brightness = targetBrightness,
            readingLightOn = isHighlightOrInterlude
        )

        _twinState.update { state ->
            state.copy(
                ambientColorHex = targetColorHex,
                ambientSecondaryHex = zone.secondaryHex,
                ambientBrightness = targetBrightness,
                vocalZoneTheme = zone,
                strobeActive = strobe,
                secondRowMoodLightAlpha = if (isHighlightOrInterlude) 0.9f else 0.25f,
                lastSdkCommandLog = "Light.setAmbientLightBrightnessColor(0x${targetColorHex.toString(16).uppercase()}) | Brightness=${targetBrightness}%"
            )
        }
    }

    private fun applyVehicleAmbientLightSdk(
        argbColor: Int,
        brightness: Int,
        readingLightOn: Boolean
    ) {
        try {
            if (argbColor != lastAppliedSdkColor) {
                lastAppliedSdkColor = argbColor
                vehicle.getLight().setAmbientLightBrightnessColor(
                    allCabinSeats,
                    argbColor,
                    { _, _ -> },
                    { _ -> }
                )
            }
            if (kotlin.math.abs(brightness - lastAppliedSdkBrightness) >= 2) {
                lastAppliedSdkBrightness = brightness
                vehicle.getLight().setAmbientLightBrightness(
                    allCabinSeats,
                    brightness,
                    { _, _ -> },
                    { _ -> }
                )
            }
            vehicle.getLight().setReadingLightSwitch(
                secondRowSeats,
                if (readingLightOn) LightSwitch.ON else LightSwitch.OFF,
                { _ -> },
                { _ -> }
            )
        } catch (t: Throwable) {
            Log.d(TAG, "applyVehicleAmbientLightSdk fallback: ${t.message}")
        }
    }

    private fun blendColorHex(c1: Long, c2: Long, ratio: Float): Long {
        val t = ratio.coerceIn(0f, 1f)
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF

        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF

        val r = (r1 + (r2 - r1) * t).roundToInt().coerceIn(0, 255)
        val g = (g1 + (g2 - g1) * t).roundToInt().coerceIn(0, 255)
        val b = (b1 + (b2 - b1) * t).roundToInt().coerceIn(0, 255)

        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    /**
     * Choreography #4:
     * [열창 쿨다운 & 그린룸 케어 (HVAC & Seat)]
     */
    fun triggerGreenroomCooldownCare(reason: String = "열창 30초 지속 / 곡 종료 케어") {
        try {
            vehicle.getHVAC().setHvacPower(
                frontSeats,
                true,
                { _, _ -> },
                { _ -> }
            )
            vehicle.getHVAC().setHvacRecirculation(
                frontSeats,
                false, // 외기순환 (Fresh Air)
                { _, _ -> },
                { _ -> }
            )
            vehicle.getHVAC().setHvacFanSpeed(
                frontSeats,
                4,
                { _, _ -> },
                { _ -> }
            )
            vehicle.getHVAC().setHvacTemperature(
                frontSeats,
                20.5f,
                TemperatureUnit.CELSIUS,
                { _, _ -> },
                { _ -> }
            )
            vehicle.getSeat().setHvacSeatVentilation(
                frontSeats,
                2,
                { _, _ -> },
                { _ -> }
            )
        } catch (t: Throwable) {
            Log.d(TAG, "triggerGreenroomCooldownCare SDK fallback: ${t.message}")
        }

        _twinState.update {
            it.copy(
                hvacOuterAirMode = true,
                hvacTempCelsius = 20.5f,
                hvacFanLevel = 4,
                seatVentilationLevel = 2,
                greenroomCareActive = true,
                lastSdkCommandLog = "[$reason] HVAC.setHvacRecirculation(false) | Fan=4단 | Seat.setHvacSeatVentilation(2)"
            )
        }
    }

    fun accumulateHighOutputSingingSecond(isHighOutput: Boolean) {
        val nextSec = if (isHighOutput) _twinState.value.highRmsAccumulatedSec + 1 else _twinState.value.highRmsAccumulatedSec
        _twinState.update { it.copy(highRmsAccumulatedSec = nextSec) }
        if (nextSec >= 30 && !_twinState.value.greenroomCareActive) {
            triggerGreenroomCooldownCare("고성량 열창 30초 감지 자동 쿨다운")
        }
    }

    /**
     * Choreography #5:
     * [관객 앙코르 인터랙션 (Seat & Light)]
     */
    fun onSongFinished(finalScore: Int) {
        try {
            vehicle.getToll().setEtcsVoiceGuidanceVolume(
                5,
                { Log.d(TAG, "Toll ETCS voice volume restored to 5") },
                { _ -> }
            )
        } catch (t: Throwable) {
            Log.d(TAG, "Toll restore fallback: ${t.message}")
        }

        triggerGreenroomCooldownCare("곡 종료 그린룸 케어")

        if (finalScore >= 90) {
            try {
                vehicle.getLight().setAmbientLightBrightnessColor(
                    allCabinSeats,
                    VocalZoneTheme.ENCORE_GOLD_FINALE.primaryHex.toInt(),
                    { _, _ -> },
                    { _ -> }
                )
                vehicle.getLight().setAmbientLightBrightness(
                    allCabinSeats,
                    100,
                    { _, _ -> },
                    { _ -> }
                )
                vehicle.getSeat().moveSeatBackrestAngle(
                    frontSeats,
                    MoveControl.BACKWARD,
                    { _, _ -> },
                    { _ -> }
                )
            } catch (t: Throwable) {
                Log.d(TAG, "Encore finale SDK fallback: ${t.message}")
            }

            _twinState.update {
                it.copy(
                    tollHiPassMuted = false,
                    nonCriticalAlertsMuted = false,
                    encoreFinaleActive = true,
                    seatRelaxationPulseActive = true,
                    vocalZoneTheme = VocalZoneTheme.ENCORE_GOLD_FINALE,
                    ambientColorHex = VocalZoneTheme.ENCORE_GOLD_FINALE.primaryHex,
                    ambientSecondaryHex = VocalZoneTheme.ENCORE_GOLD_FINALE.secondaryHex,
                    strobeActive = true,
                    lastSdkCommandLog = "[90점+ 앙코르 피날레!] Light.setAmbientLightBrightnessColor(GOLD) | Seat.moveSeatBackrestAngle()"
                )
            }
        } else {
            _twinState.update {
                it.copy(
                    tollHiPassMuted = false,
                    nonCriticalAlertsMuted = false,
                    lastSdkCommandLog = "[곡 종료] Toll.setEtcsVoiceGuidanceVolume(5) 복구 | 그린룸 공조/통풍 케어 가동"
                )
            }
        }
    }

    /**
     * Choreography #6:
     * [주행 안전 인터락 (Brake & Gear - AAOS Driver Distraction Compliance)]
     */
    fun setGearAndBrakeState(gear: GearPosition, parkingBrake: Boolean = (gear == GearPosition.P)) {
        val parked = (gear == GearPosition.P) && parkingBrake

        _twinState.update {
            it.copy(
                gearPosition = gear,
                parkingBrakeEngaged = parkingBrake,
                isParked = parked,
                lastSdkCommandLog = if (parked) {
                    "[주행 안전 인터락 해제] Gear=P & ParkingBrake=ON • 전체 스테이지 UI 활성화"
                } else {
                    "[주행 안전 인터락 작동!] Gear=${gear.label} 주행 감지 • 가사/채점 숨김 & 동승석 세이프 오디오 모드 전환"
                }
            )
        }
    }

    fun toggleSoundproofWindowsManually() {
        val nextClosed = !_twinState.value.allWindowsClosed
        val targetPos = if (nextClosed) 0 else 35
        try {
            vehicle.getWindow().setWindowPosition(
                allSideWindows,
                targetPos,
                { _, _ -> },
                { _ -> }
            )
            vehicle.getWindow().moveWindowBlind(
                roofBlindArea,
                if (nextClosed) MoveControl.FORWARD else MoveControl.BACKWARD,
                { _, _ -> },
                { _ -> }
            )
        } catch (t: Throwable) {
            Log.d(TAG, "toggleSoundproofWindowsManually SDK fallback: ${t.message}")
        }

        _twinState.update {
            it.copy(
                allWindowsClosed = nextClosed,
                windowOpenPercentFL = if (nextClosed) 0 else 35,
                windowOpenPercentFR = if (nextClosed) 0 else 35,
                windowOpenPercentRL = if (nextClosed) 0 else 20,
                windowOpenPercentRR = if (nextClosed) 0 else 20,
                roofBlindClosed = nextClosed,
                lastSdkCommandLog = "Window.setWindowPosition($targetPos) | moveWindowBlind(${if (nextClosed) "FORWARD" else "BACKWARD"})"
            )
        }
    }
}
