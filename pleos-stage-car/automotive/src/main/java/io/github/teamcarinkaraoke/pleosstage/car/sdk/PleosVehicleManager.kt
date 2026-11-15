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
import ai.pleos.playground.vehicle.listener.EtcsVoiceGuidanceVolumeListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacAirCleanerStateListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import ai.pleos.playground.vehicle.listener.HvacRecirculationStateListener
import ai.pleos.playground.vehicle.listener.HvacSeatVentilationListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SmartGlassLevelListener
import ai.pleos.playground.vehicle.listener.SmartGlassStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.lang.reflect.Proxy
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Official Pleos Connect Vehicle SDK (`ai.pleos.playground:Vehicle:2.3.2`) & AAOS `CarUxRestrictionsManager` Integration Layer.
 *
 * Strictly follows the official Pleos Connect Vehicle SDK lifecycle and Typed API pattern:
 * `Vehicle(context)` -> `vehicle.initialize()` -> Domain (`Light`, `Window`, `HVAC`, `Seat`, `Toll`, `EvBattery`, `Gear`, `Brake`)
 * -> `checkXxxCapabilities` -> `getCurrentXxx` / `getXxx` -> `registerXxx` -> `setXxx` -> `unregisterXxx` -> `vehicle.release()`.
 *
 * Ensures real-time bidirectional synchronization with Pleos Connect Virtual Console (`127.0.0.1:8088`),
 * with safe local fallback when running on environments where specific VHAL properties return `ERROR_NOT_SUPPORTED_PROPERTY`.
 */
enum class GearPosition(val label: String) {
    P("P"),
    R("R"),
    N("N"),
    D("D")
}

enum class LightingControlMode(val label: String) {
    AUTO_MUSIC("자동조정"),
    FIXED("고정"),
    OFF("꺼짐")
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
    ENCORE_GOLD_FINALE("피날레 무대 조명", 0xFFFFD600, 0xFFFF2E93),
    ECO_BATTERY_SAVER("배터리 절전 조명", 0xFF1BB55C, 0xFF0F172A)
}

/**
 * SeatOccupancy-driven stage sound & comfort mode:
 * - 1 passenger: `운전석 사운드`
 * - 2+ passengers: `전 좌석 사운드`
 */
enum class SeatOccupancyStageMode(
    val badgeLabel: String,
    val subtitle: String
) {
    SOLO_STAGE("운전석 사운드", "1인 탑승 감지 · 운전석 맞춤 통풍"),
    DUET_STAGE("전 좌석 사운드", "2인 탑승 감지 · 탑승 좌석 맞춤 통풍"),
    FAMILY_CHORUS_STAGE("전 좌석 사운드", "다인원 탑승 감지 · 탑승 좌석 맞춤 통풍");

    companion object {
        fun fromOccupiedCount(count: Int): SeatOccupancyStageMode = when {
            count <= 1 -> SOLO_STAGE
            count == 2 -> DUET_STAGE
            else -> FAMILY_CHORUS_STAGE
        }
    }
}

data class VehicleDigitalTwinState(
    // 1. Soundproof Booth Mode & SmartGlass / WindowBlind Concert Hall Blackout (Window Domain)
    val allWindowsClosed: Boolean = false,
    val windowOpenPercentFL: Int = 35,
    val windowOpenPercentFR: Int = 35,
    val windowOpenPercentRL: Int = 20,
    val windowOpenPercentRR: Int = 20,
    val roofBlindClosed: Boolean = false,
    val roofBlindPositionPercent: Int = 0,
    val smartGlassBlackoutEnabled: Boolean = false,
    val smartGlassLevel: Int = 0,
    val smartGlassMinLevel: Int = 0,
    val smartGlassMaxLevel: Int = 5,

    // 2. Automatic Genre & Waveform Driven Vehicle Ambient Light + Cabin/Reading Light Auto-Off (Light Domain)
    val lightingControlMode: LightingControlMode = LightingControlMode.AUTO_MUSIC,
    val fixedAmbientBrightness: Int = 70, // 10..100 user-saved brightness for FIXED mode
    val ambientLightEnabled: Boolean = true,
    val ambientColorHex: Long = 0xFF2B95F6,
    val ambientSecondaryHex: Long = 0xFF7C4DFF,
    val ambientBrightness: Int = 70, // 0..100
    val cabinLightOffForStage: Boolean = true,
    val readingLightOffForStage: Boolean = true,
    val vocalZoneTheme: VocalZoneTheme = VocalZoneTheme.IDLE_OBSIDIAN,
    val strobeActive: Boolean = false,
    val secondRowMoodLightAlpha: Float = 0.15f,

    // 3. Climax Do-Not-Disturb Shield (Toll ETCS Voice Guidance Volume Domain)
    val tollHiPassMuted: Boolean = false,
    val etcsVoiceGuidanceVolume: Int = 5,
    val etcsSavedVolumeBeforeMute: Int = 5,
    val etcsMaxVolume: Int = 10,
    val nonCriticalAlertsMuted: Boolean = false,

    // 4. Passionate Singing Cooldown, Enclosed Cabin AirCleaner & SeatOccupancy Smart Care (HVAC & Seat Domains)
    val hvacPowerOn: Boolean = false,
    val hvacAcOn: Boolean = true,
    val hvacOuterAirMode: Boolean = false,
    val hvacAirCleanerEnabled: Boolean = false,
    val hvacTempCelsius: Float = 22.5f,
    val hvacCurrentIndoorTempCelsius: Float = 23.0f,
    val hvacFanLevel: Int = 2, // 1..7
    val seatVentilationLevel: Int = 0, // 0..3단 (Representative active level)
    val seatVentilationFL: Int = 0,
    val seatVentilationFR: Int = 0,
    val seatVentilationRL: Int = 0,
    val seatVentilationRR: Int = 0,
    val seatOccupiedFL: Boolean = true,
    val seatOccupiedFR: Boolean = false,
    val seatOccupiedRL: Boolean = false,
    val seatOccupiedRR: Boolean = false,
    val occupiedSeatCount: Int = 1,
    val occupancyStageMode: SeatOccupancyStageMode = SeatOccupancyStageMode.SOLO_STAGE,
    val greenroomCareActive: Boolean = false,
    val highRmsAccumulatedSec: Int = 0,

    // 5. EV Battery SOC Monitoring & Eco Stage Mode (EvBattery Domain)
    val evBatterySocPercent: Int = 78,
    val ecoStageModeActive: Boolean = false,
    val ecoBatteryLowTriggered: Boolean = false,

    // 6. Audience Encore Interaction (Seat & Light Finale)
    val seatRelaxationPulseActive: Boolean = false,
    val encoreFinaleActive: Boolean = false,

    // 7. Driving Safety Interlock (Brake & Gear & AAOS CarUxRestrictions)
    val gearPosition: GearPosition = GearPosition.P,
    val brakePedalPressed: Boolean = false,
    val parkingBrakeEngaged: Boolean = true,
    val isParked: Boolean = true,
    val uxRestrictionsActive: Boolean = false,
    val uxRestrictionNoKeyboard: Boolean = false,
    val uxRestrictionNoSetup: Boolean = false,

    // SDK Telemetry Log
    val sdkConnectedMode: String = "ai.pleos.playground:Vehicle:2.3.2 (Official SDK)",
    val lastSdkCommandLog: String = "Vehicle.initialize() • Pleos Connect Virtual Console Bidirectional Sync Ready"
) {
    val isKeyboardRestricted: Boolean
        get() = !isParked || gearPosition != GearPosition.P || uxRestrictionNoKeyboard || uxRestrictionsActive

    val isSetupRestricted: Boolean
        get() = !isParked || gearPosition != GearPosition.P || uxRestrictionNoSetup || uxRestrictionsActive

    val occupiedSeatsSummary: String
        get() {
            val labels = mutableListOf<String>()
            if (seatOccupiedFL) labels.add("운전석")
            if (seatOccupiedFR) labels.add("동승석")
            if (seatOccupiedRL) labels.add("2열좌")
            if (seatOccupiedRR) labels.add("2열우")
            return if (labels.isEmpty()) "운전석(기본)" else labels.joinToString("·")
        }
}

class PleosVehicleManager(private val appContext: Context) {

    companion object {
        private const val TAG = "PleosVehicleManager"
        private const val UX_RESTRICTIONS_NO_KEYBOARD = 8   // CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD (1 shl 3)
        private const val UX_RESTRICTIONS_NO_SETUP = 64     // CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP (1 shl 6)
        private const val ECO_BATTERY_THRESHOLD_PERCENT = 20
        private const val ECO_MAX_AMBIENT_BRIGHTNESS = 30
    }

    private val _twinState = MutableStateFlow(VehicleDigitalTwinState())
    val twinState: StateFlow<VehicleDigitalTwinState> = _twinState.asStateFlow()

    private val _isVehicleSdkConnected = MutableStateFlow(false)
    val isVehicleSdkConnected: StateFlow<Boolean> = _isVehicleSdkConnected.asStateFlow()

    private val _lastGleoIntentInfo = MutableStateFlow("대기 중 (수신된 Gleo 인텐트 없음)")
    val lastGleoIntentInfo: StateFlow<String> = _lastGleoIntentInfo.asStateFlow()

    // Official Pleos Playground Vehicle SDK Entry Point (v2.3.2)
    private val vehicle: Vehicle = Vehicle(appContext.applicationContext)

    fun getVehicleSdkInstance(): Vehicle = vehicle

    fun getApplicationContext(): Context = appContext.applicationContext

    fun markSdkServiceConnected() {
        if (!_isVehicleSdkConnected.value) {
            _isVehicleSdkConnected.value = true
        }
    }

    fun reinitializeVehicleSdk() {
        Thread(
            {
                initializePleosVehicleSdk()
                initializeAaosCarUxRestrictions()
            },
            "PleosVehicleReinitThread"
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun recordReceivedGleoIntent(action: String, extrasSummary: String) {
        _lastGleoIntentInfo.value = "Action=$action | Extras=$extrasSummary"
    }

    // AAOS Car & CarUxRestrictionsManager instances (accessed safely via reflection/Proxy)
    private var aaosCarInstance: Any? = null
    private var aaosUxRestrictionsManager: Any? = null
    private var systemUxRequiresOptimization: Boolean = false
    private var systemUxActiveRestrictionsMask: Int = 0

    // Pre-defined Vehicle Areas from Official Pleos SDK
    private val seatFL = SeatArea(SeatValue.ROW_1_LEFT)
    private val seatFR = SeatArea(SeatValue.ROW_1_RIGHT)
    private val seatRL = SeatArea(SeatValue.ROW_2_LEFT)
    private val seatRR = SeatArea(SeatValue.ROW_2_RIGHT)
    private val allCabinSeats = SeatArea(
        SeatValue.ROW_1_LEFT,
        SeatValue.ROW_1_RIGHT,
        SeatValue.ROW_2_LEFT,
        SeatValue.ROW_2_RIGHT
    )
    private val frontSeats = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT)
    private val secondRowSeats = SeatArea(SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT)

    private val windowFL = WindowArea(WindowValue.ROW_1_LEFT)
    private val windowFR = WindowArea(WindowValue.ROW_1_RIGHT)
    private val windowRL = WindowArea(WindowValue.ROW_2_LEFT)
    private val windowRR = WindowArea(WindowValue.ROW_2_RIGHT)
    private val allSideWindows = WindowArea(
        WindowValue.ROW_1_LEFT,
        WindowValue.ROW_1_RIGHT,
        WindowValue.ROW_2_LEFT,
        WindowValue.ROW_2_RIGHT
    )
    private val roofBlindArea = WindowArea(WindowValue.ROOF_TOP_1, WindowValue.ROOF_TOP_2)

    private var lastAppliedSdkColor: Int = 0
    private var lastAppliedSdkBrightness: Int = -1
    private var lastReadingLightOn: Boolean? = null

    // Capability bounds & exact VHAL Area sets resolved dynamically via checkXxxCapabilities
    @Volatile
    private var windowMinPos: Int = 0
    @Volatile
    private var windowMaxPos: Int = 100
    @Volatile
    private var smartGlassMinBound: Int = 0
    @Volatile
    private var smartGlassMaxBound: Int = 5
    @Volatile
    private var hvacMinTempC: Float = 17.0f
    @Volatile
    private var hvacMaxTempC: Float = 27.0f
    @Volatile
    private var hvacMinFan: Int = 1
    @Volatile
    private var hvacMaxFan: Int = 7
    @Volatile
    private var seatMaxVentLevel: Int = 3
    @Volatile
    private var etcsMinVol: Int = 0
    @Volatile
    private var etcsMaxVol: Int = 10

    @Volatile
    private var hvacPowerAreas: List<SeatArea> = emptyList()
    @Volatile
    private var hvacAcAreas: List<SeatArea> = emptyList()
    @Volatile
    private var hvacTempAreas: List<SeatArea> = emptyList()
    @Volatile
    private var hvacFanAreas: List<SeatArea> = emptyList()
    @Volatile
    private var hvacRecircAreas: List<SeatArea> = emptyList()
    @Volatile
    private var hvacAirCleanerAreas: List<SeatArea> = emptyList()
    @Volatile
    private var ambientSwitchAreas: List<SeatArea> = emptyList()
    @Volatile
    private var ambientBrightnessAreas: List<SeatArea> = emptyList()
    @Volatile
    private var ambientColorAreas: List<SeatArea> = emptyList()
    @Volatile
    private var readingLightAreas: List<SeatArea> = emptyList()
    @Volatile
    private var windowPosAreas: List<WindowArea> = emptyList()
    @Volatile
    private var smartGlassAreas: List<WindowArea> = emptyList()

    private fun resolveHvacAreas(discovered: List<SeatArea>): List<SeatArea> {
        return if (discovered.isNotEmpty()) {
            discovered
        } else {
            listOf(frontSeats, allCabinSeats, seatFL, seatFR)
        }
    }

    private fun resolveLightAreas(discovered: List<SeatArea>): List<SeatArea> {
        return if (discovered.isNotEmpty()) {
            discovered
        } else {
            listOf(allCabinSeats, frontSeats, seatFL, seatFR)
        }
    }

    private fun resolveWindowAreas(discovered: List<WindowArea>): List<WindowArea> {
        return if (discovered.isNotEmpty()) {
            discovered
        } else {
            listOf(allSideWindows, windowFL, windowFR, windowRL, windowRR)
        }
    }

    /**
     * Guarantees HVAC Master Power is turned ON on the real Vehicle SDK / Emulator HVAC bar
     * before any temperature, fan speed, air cleaner, cooldown, eco, or seat ventilation operation.
     */
    private fun ensureHvacMasterPowerOn() {
        val hvac = vehicle.getHVAC()
        for (area in resolveHvacAreas(hvacPowerAreas)) {
            try {
                hvac.setHvacPower(
                    area,
                    true,
                    { _, _ -> markSdkServiceConnected() },
                    { _ -> }
                )
            } catch (_: Throwable) {
            }
        }
    }

    // ========================================================================
    // 1. Gear & Brake Listeners (vehicle.getGear(), vehicle.getBrake())
    // ========================================================================
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            if (gear == null) return
            markSdkServiceConnected()
            val mapped = when (gear) {
                VehicleGear.GEAR_PARK -> GearPosition.P
                VehicleGear.GEAR_REVERSE -> GearPosition.R
                VehicleGear.GEAR_NEUTRAL -> GearPosition.N
                else -> GearPosition.D
            }
            applySdkGearSelectionState(mapped)
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "GearSelectionListener onFailed: ${e.message}")
        }
    }

    fun refreshGearSelectionState() {
        try {
            vehicle.getGear().getCurrentGearSelectionState(
                { gear ->
                    markSdkServiceConnected()
                    gearListener.onSelected(gear)
                },
                { e -> Log.d(TAG, "refreshGearSelectionState fallback: ${e.message}") }
            )
        } catch (t: Throwable) {
            Log.d(TAG, "refreshGearSelectionState safe ignore: ${t.message}")
        }
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) {
            if (status != null) {
                _twinState.update {
                    it.copy(
                        brakePedalPressed = status,
                        lastSdkCommandLog = "[Brake] onBrakePedalStatusUpdated($status)"
                    )
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "BrakePedalStatusListener onFailed: ${e.message}")
        }
    }

    // ========================================================================
    // 2. Light Listeners (vehicle.getLight())
    // ========================================================================
    private val ambientBrightnessListener = object : AmbientLightBrightnessListener {
        override fun onBrightnessUpdated(brightness: Int?) {
            if (brightness != null) {
                val safeVal = brightness.coerceIn(0, 100)
                lastAppliedSdkBrightness = safeVal
                _twinState.update { state ->
                    when (state.lightingControlMode) {
                        LightingControlMode.OFF -> state.copy(
                            ambientLightEnabled = false,
                            ambientBrightness = 0
                        )
                        LightingControlMode.FIXED -> {
                            val cap = if (state.ecoStageModeActive) ECO_MAX_AMBIENT_BRIGHTNESS else 100
                            state.copy(
                                ambientLightEnabled = true,
                                ambientBrightness = state.fixedAmbientBrightness.coerceIn(10, cap)
                            )
                        }
                        LightingControlMode.AUTO_MUSIC -> state.copy(
                            ambientBrightness = safeVal,
                            lastSdkCommandLog = "[Light] onBrightnessUpdated($safeVal%)"
                        )
                    }
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "AmbientLightBrightnessListener onFailed: ${e.message}")
        }
    }

    private val ambientColorListener = object : AmbientLightBrightnessColorListener {
        override fun onBrightnessColorUpdated(argbColor: Int?) {
            if (argbColor != null) {
                lastAppliedSdkColor = argbColor
                val hex = (argbColor.toLong() and 0xFFFFFFFFL) or 0xFF000000L
                _twinState.update {
                    it.copy(
                        ambientColorHex = hex,
                        lastSdkCommandLog = "[Light] onBrightnessColorUpdated(0x${hex.toString(16).uppercase()})"
                    )
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "AmbientLightBrightnessColorListener onFailed: ${e.message}")
        }
    }

    private val ambientStateListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) {
            if (state != null) {
                _twinState.update { cur ->
                    when (cur.lightingControlMode) {
                        LightingControlMode.OFF -> cur.copy(
                            ambientLightEnabled = false,
                            ambientBrightness = 0
                        )
                        LightingControlMode.FIXED -> cur.copy(
                            ambientLightEnabled = true
                        )
                        LightingControlMode.AUTO_MUSIC -> cur.copy(
                            ambientLightEnabled = state == LightState.ON,
                            lastSdkCommandLog = "[Light] AmbientLightStateUpdated($state)"
                        )
                    }
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "AmbientLightStateListener onFailed: ${e.message}")
        }
    }

    private val cabinLightStateListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) {
            if (state != null) {
                _twinState.update {
                    it.copy(
                        cabinLightOffForStage = state != LightState.ON,
                        lastSdkCommandLog = "[Light] CabinLightStateUpdated($state)"
                    )
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "CabinLightStateListener onFailed: ${e.message}")
        }
    }

    private val readingLightStateListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) {
            if (state != null) {
                _twinState.update {
                    it.copy(
                        readingLightOffForStage = state != LightState.ON,
                        lastSdkCommandLog = "[Light] ReadingLightStateUpdated($state)"
                    )
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "ReadingLightStateListener onFailed: ${e.message}")
        }
    }

    // ========================================================================
    // 3. Window, SmartGlass & WindowBlind Listeners (vehicle.getWindow())
    // ========================================================================
    private fun updateWindowPositionInState(seatWindowIndex: Int, positionPercent: Int) {
        val safePos = positionPercent.coerceIn(windowMinPos, windowMaxPos)
        _twinState.update { state ->
            val fl = if (seatWindowIndex == 0) safePos else state.windowOpenPercentFL
            val fr = if (seatWindowIndex == 1) safePos else state.windowOpenPercentFR
            val rl = if (seatWindowIndex == 2) safePos else state.windowOpenPercentRL
            val rr = if (seatWindowIndex == 3) safePos else state.windowOpenPercentRR
            val closed = (fl == 0 && fr == 0 && rl == 0 && rr == 0)
            state.copy(
                windowOpenPercentFL = fl,
                windowOpenPercentFR = fr,
                windowOpenPercentRL = rl,
                windowOpenPercentRR = rr,
                allWindowsClosed = closed,
                lastSdkCommandLog = "[Window] PositionUpdated(FL=$fl%, FR=$fr%, RL=$rl%, RR=$rr%)"
            )
        }
    }

    private val windowFLListener = object : WindowPositionListener {
        override fun onWindowPositionUpdated(position: Int?) {
            if (position != null) updateWindowPositionInState(0, position)
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "windowFLListener onFailed: ${e.message}")
        }
    }

    private val windowFRListener = object : WindowPositionListener {
        override fun onWindowPositionUpdated(position: Int?) {
            if (position != null) updateWindowPositionInState(1, position)
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "windowFRListener onFailed: ${e.message}")
        }
    }

    private val windowRLListener = object : WindowPositionListener {
        override fun onWindowPositionUpdated(position: Int?) {
            if (position != null) updateWindowPositionInState(2, position)
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "windowRLListener onFailed: ${e.message}")
        }
    }

    private val windowRRListener = object : WindowPositionListener {
        override fun onWindowPositionUpdated(position: Int?) {
            if (position != null) updateWindowPositionInState(3, position)
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "windowRRListener onFailed: ${e.message}")
        }
    }

    private val windowBlindPositionListener = object : WindowPositionListener {
        override fun onWindowPositionUpdated(position: Int?) {
            if (position != null) {
                val safePos = position.coerceIn(0, 100)
                _twinState.update {
                    it.copy(
                        roofBlindPositionPercent = safePos,
                        roofBlindClosed = safePos <= 10,
                        lastSdkCommandLog = "[WindowBlind] onWindowPositionUpdated($safePos%)"
                    )
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "windowBlindPositionListener onFailed: ${e.message}")
        }
    }

    private val smartGlassStateListener = object : SmartGlassStateListener {
        override fun onSmartGlassStateUpdated(state: Boolean?) {
            if (state != null) {
                _twinState.update {
                    it.copy(
                        smartGlassBlackoutEnabled = state,
                        lastSdkCommandLog = "[SmartGlass] onSmartGlassStateUpdated($state)"
                    )
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "SmartGlassStateListener onFailed: ${e.message}")
        }
    }

    private val smartGlassLevelListener = object : SmartGlassLevelListener {
        override fun onSmartGlassLevelUpdated(level: Int?) {
            if (level != null) {
                _twinState.update {
                    it.copy(
                        smartGlassLevel = level,
                        smartGlassBlackoutEnabled = level > smartGlassMinBound,
                        lastSdkCommandLog = "[SmartGlass] onSmartGlassLevelUpdated($level)"
                    )
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "SmartGlassLevelListener onFailed: ${e.message}")
        }
    }

    // ========================================================================
    // 4. HVAC & AirCleaner Listeners (vehicle.getHVAC())
    // ========================================================================
    private val hvacPowerListener = object : HvacPowerStateListener {
        override fun onHvacPowerStateUpdated(state: Boolean?) {
            if (state != null) {
                _twinState.update {
                    it.copy(
                        hvacPowerOn = state,
                        lastSdkCommandLog = "[HVAC] onHvacPowerStateUpdated($state)"
                    )
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "HvacPowerStateListener onFailed: ${e.message}")
        }
    }

    private val hvacAcListener = object : HvacAcStateListener {
        override fun onHvacAcStateUpdated(state: Boolean?) {
            if (state != null) {
                _twinState.update {
                    it.copy(
                        hvacAcOn = state,
                        lastSdkCommandLog = "[HVAC] onHvacAcStateUpdated($state)"
                    )
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "HvacAcStateListener onFailed: ${e.message}")
        }
    }

    private val hvacTargetTempListener = object : HvacTemperatureListener {
        override fun onHvacTemperatureUpdated(temperature: Float?) {
            if (temperature != null && temperature > 0f) {
                val rounded = ((temperature * 2f).roundToInt() / 2f).coerceIn(hvacMinTempC, hvacMaxTempC)
                _twinState.update {
                    it.copy(
                        hvacTempCelsius = rounded,
                        lastSdkCommandLog = "[HVAC] onHvacTemperatureUpdated(${rounded}°C)"
                    )
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "HvacTargetTempListener onFailed: ${e.message}")
        }
    }

    private val hvacCurrentTempListener = object : HvacTemperatureListener {
        override fun onHvacTemperatureUpdated(temperature: Float?) {
            if (temperature != null && temperature > 0f) {
                val rounded = ((temperature * 2f).roundToInt() / 2f)
                _twinState.update {
                    it.copy(
                        hvacCurrentIndoorTempCelsius = rounded,
                        hvacTempCelsius = if (it.hvacTempCelsius <= 0f) rounded.coerceIn(hvacMinTempC, hvacMaxTempC) else it.hvacTempCelsius,
                        lastSdkCommandLog = "[HVAC] onHvacTemperatureCurrentUpdated(실내 ${rounded}°C)"
                    )
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "HvacCurrentTempListener onFailed: ${e.message}")
        }
    }

    private val hvacFanSpeedListener = object : HvacFanSpeedListener {
        override fun onHvacFanSpeedUpdated(speed: Int?) {
            if (speed != null) {
                val safeSpeed = speed.coerceIn(0, hvacMaxFan)
                _twinState.update {
                    it.copy(
                        hvacFanLevel = safeSpeed,
                        lastSdkCommandLog = "[HVAC] onHvacFanSpeedUpdated(${safeSpeed}단)"
                    )
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "HvacFanSpeedListener onFailed: ${e.message}")
        }
    }

    private val hvacRecirculationListener = object : HvacRecirculationStateListener {
        override fun onHvacAutoRecirculationStateUpdated(state: Boolean?) {
            if (state != null) {
                _twinState.update {
                    it.copy(
                        hvacOuterAirMode = !state,
                        lastSdkCommandLog = "[HVAC] RecirculationUpdated(외기순환=${!state})"
                    )
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "HvacRecirculationStateListener onFailed: ${e.message}")
        }
    }

    private val hvacAirCleanerListener = object : HvacAirCleanerStateListener {
        override fun onHvacAirCleanerStateUpdated(state: Boolean?) {
            if (state != null) {
                _twinState.update {
                    it.copy(
                        hvacAirCleanerEnabled = state,
                        lastSdkCommandLog = "[HVAC] onHvacAirCleanerStateUpdated($state)"
                    )
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "HvacAirCleanerStateListener onFailed: ${e.message}")
        }
    }

    // ========================================================================
    // 5. SeatOccupancy & SeatVentilation Listeners (vehicle.getSeat())
    // ========================================================================
    private fun handleSeatOccupancyUpdated(seatIndex: Int, occupied: Boolean) {
        _twinState.update { state ->
            val fl = if (seatIndex == 0) occupied else state.seatOccupiedFL
            val fr = if (seatIndex == 1) occupied else state.seatOccupiedFR
            val rl = if (seatIndex == 2) occupied else state.seatOccupiedRL
            val rr = if (seatIndex == 3) occupied else state.seatOccupiedRR
            val rawCount = listOf(fl, fr, rl, rr).count { it }
            val effectiveCount = rawCount.coerceAtLeast(1)
            val mode = SeatOccupancyStageMode.fromOccupiedCount(effectiveCount)
            state.copy(
                seatOccupiedFL = fl,
                seatOccupiedFR = fr,
                seatOccupiedRL = rl,
                seatOccupiedRR = rr,
                occupiedSeatCount = effectiveCount,
                occupancyStageMode = mode,
                lastSdkCommandLog = "[SeatOccupancy] ${mode.badgeLabel} (FL=$fl, FR=$fr, RL=$rl, RR=$rr)"
            )
        }
        // If seat ventilation is currently active, dynamically re-route ventilation only to occupied seats
        val currentVentLevel = _twinState.value.seatVentilationLevel
        if (currentVentLevel > 0 && !_twinState.value.ecoStageModeActive) {
            applySmartOccupiedSeatVentilation(currentVentLevel)
        }
    }

    private val seatOccupancyFLListener = object : SeatOccupancyListener {
        override fun onSeatOccupancyUpdated(occupancy: Boolean?) {
            if (occupancy != null) handleSeatOccupancyUpdated(0, occupancy)
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "seatOccupancyFLListener onFailed: ${e.message}")
        }
    }

    private val seatOccupancyFRListener = object : SeatOccupancyListener {
        override fun onSeatOccupancyUpdated(occupancy: Boolean?) {
            if (occupancy != null) handleSeatOccupancyUpdated(1, occupancy)
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "seatOccupancyFRListener onFailed: ${e.message}")
        }
    }

    private val seatOccupancyRLListener = object : SeatOccupancyListener {
        override fun onSeatOccupancyUpdated(occupancy: Boolean?) {
            if (occupancy != null) handleSeatOccupancyUpdated(2, occupancy)
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "seatOccupancyRLListener onFailed: ${e.message}")
        }
    }

    private val seatOccupancyRRListener = object : SeatOccupancyListener {
        override fun onSeatOccupancyUpdated(occupancy: Boolean?) {
            if (occupancy != null) handleSeatOccupancyUpdated(3, occupancy)
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "seatOccupancyRRListener onFailed: ${e.message}")
        }
    }

    private fun handleSeatVentilationUpdated(seatIndex: Int, level: Int) {
        val safeLevel = level.coerceIn(0, seatMaxVentLevel)
        _twinState.update { state ->
            val fl = if (seatIndex == 0) safeLevel else state.seatVentilationFL
            val fr = if (seatIndex == 1) safeLevel else state.seatVentilationFR
            val rl = if (seatIndex == 2) safeLevel else state.seatVentilationRL
            val rr = if (seatIndex == 3) safeLevel else state.seatVentilationRR
            val maxActive = maxOf(fl, fr, rl, rr)
            state.copy(
                seatVentilationFL = fl,
                seatVentilationFR = fr,
                seatVentilationRL = rl,
                seatVentilationRR = rr,
                seatVentilationLevel = maxActive,
                lastSdkCommandLog = "[Seat] onHvacSeatVentilationUpdated(FL=$fl, FR=$fr, RL=$rl, RR=$rr)"
            )
        }
    }

    private val seatVentFLListener = object : HvacSeatVentilationListener {
        override fun onHvacSeatVentilationUpdated(ventilation: Int?) {
            if (ventilation != null) handleSeatVentilationUpdated(0, ventilation)
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "seatVentFLListener onFailed: ${e.message}")
        }
    }

    private val seatVentFRListener = object : HvacSeatVentilationListener {
        override fun onHvacSeatVentilationUpdated(ventilation: Int?) {
            if (ventilation != null) handleSeatVentilationUpdated(1, ventilation)
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "seatVentFRListener onFailed: ${e.message}")
        }
    }

    private val seatVentRLListener = object : HvacSeatVentilationListener {
        override fun onHvacSeatVentilationUpdated(ventilation: Int?) {
            if (ventilation != null) handleSeatVentilationUpdated(2, ventilation)
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "seatVentRLListener onFailed: ${e.message}")
        }
    }

    private val seatVentRRListener = object : HvacSeatVentilationListener {
        override fun onHvacSeatVentilationUpdated(ventilation: Int?) {
            if (ventilation != null) handleSeatVentilationUpdated(3, ventilation)
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "seatVentRRListener onFailed: ${e.message}")
        }
    }

    // ========================================================================
    // 6. Toll ETCS Voice Guidance Volume Listener (vehicle.getToll())
    // ========================================================================
    private val etcsVolumeListener = object : EtcsVoiceGuidanceVolumeListener {
        override fun onEtcsVoiceGuidanceVolumeUpdated(volume: Int?) {
            if (volume != null) {
                val safeVol = volume.coerceIn(etcsMinVol, etcsMaxVol)
                _twinState.update { state ->
                    val savedBeforeMute = if (safeVol > 0) safeVol else state.etcsSavedVolumeBeforeMute
                    state.copy(
                        etcsVoiceGuidanceVolume = safeVol,
                        etcsSavedVolumeBeforeMute = savedBeforeMute,
                        tollHiPassMuted = (safeVol == 0),
                        nonCriticalAlertsMuted = (safeVol == 0),
                        lastSdkCommandLog = "[Toll] onEtcsVoiceGuidanceVolumeUpdated(volume=$safeVol)"
                    )
                }
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "EtcsVoiceGuidanceVolumeListener onFailed: ${e.message}")
        }
    }

    // ========================================================================
    // 7. EvBattery State of Charge Listener (vehicle.getEvBattery())
    // ========================================================================
    private val evBatterySocListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
            if (value != null) {
                applyEvBatterySocUpdate(value)
            }
        }

        override fun onFailed(e: Exception) {
            Log.d(TAG, "EvBatteryStateOfChargeListener onFailed: ${e.message}")
        }
    }

    init {
        Thread {
            initializePleosVehicleSdk()
            initializeAaosCarUxRestrictions()
        }.apply {
            name = "PleosVehicleInitThread"
            isDaemon = true
            start()
        }
    }

    /**
     * Initializes `ai.pleos.playground:Vehicle:2.3.2` and executes the full official pipeline:
     * `checkXxxCapabilities` -> `getCurrentXxx` / `getXxx` -> `registerXxx` for all domains.
     */
    private fun initializePleosVehicleSdk() {
        try {
            vehicle.initialize()

            // 1) Gear & Brake
            val gearDomain = vehicle.getGear()
            gearDomain.registerGearSelection(gearListener)
            gearDomain.getCurrentGearSelectionState(
                { gear ->
                    markSdkServiceConnected()
                    gearListener.onSelected(gear)
                },
                { e -> Log.d(TAG, "getCurrentGearSelectionState fallback: ${e.message}") }
            )

            val brakeDomain = vehicle.getBrake()
            brakeDomain.checkBrakePedalStatusCapabilities(
                { cfg ->
                    markSdkServiceConnected()
                    Log.d(TAG, "BrakePedalStatus capability: ${cfg.accessType}")
                },
                { e -> Log.d(TAG, "checkBrakePedalStatusCapabilities fallback: ${e.message}") }
            )
            brakeDomain.registerBrakePedalStatus(brakeListener)
            brakeDomain.getCurrentBrakePedalStatus(
                { status ->
                    markSdkServiceConnected()
                    brakeListener.onBrakePedalStatusUpdated(status)
                },
                { e -> Log.d(TAG, "getCurrentBrakePedalStatus fallback: ${e.message}") }
            )

            // 2) Light (Ambient + Cabin + Reading)
            val lightDomain = vehicle.getLight()
            lightDomain.checkAmbientLightSwitchCapabilities(
                { cfg, map ->
                    markSdkServiceConnected()
                    if (map.isNotEmpty()) ambientSwitchAreas = map.keys.toList()
                    Log.d(TAG, "AmbientLightSwitch capability: ${cfg.accessType}, areas=${map.keys}")
                },
                { e -> Log.d(TAG, "checkAmbientLightSwitchCapabilities fallback: ${e.message}") }
            )
            lightDomain.checkAmbientLightBrightnessCapabilities(
                { cfg, map ->
                    markSdkServiceConnected()
                    if (map.isNotEmpty()) {
                        ambientBrightnessAreas = map.keys.toList()
                        val primaryArea = map.keys.first()
                        lightDomain.registerAmbientLightBrightnessListener(primaryArea, ambientBrightnessListener)
                        lightDomain.getAmbientLightBrightness(
                            primaryArea,
                            { b -> ambientBrightnessListener.onBrightnessUpdated(b) },
                            { _ -> }
                        )
                    }
                    Log.d(TAG, "AmbientLightBrightness capability: ${cfg.accessType}")
                },
                { e -> Log.d(TAG, "checkAmbientLightBrightnessCapabilities fallback: ${e.message}") }
            )
            lightDomain.checkAmbientLightBrightnessColorCapabilities(
                { cfg, areas ->
                    markSdkServiceConnected()
                    if (areas.isNotEmpty()) {
                        ambientColorAreas = areas
                        val primaryArea = areas.first()
                        lightDomain.registerAmbientLightBrightnessColorListener(primaryArea, ambientColorListener)
                        lightDomain.getAmbientLightBrightnessColor(
                            primaryArea,
                            { c -> ambientColorListener.onBrightnessColorUpdated(c) },
                            { _ -> }
                        )
                    }
                    Log.d(TAG, "AmbientLightBrightnessColor capability: ${cfg.accessType}")
                },
                { e -> Log.d(TAG, "checkAmbientLightBrightnessColorCapabilities fallback: ${e.message}") }
            )
            lightDomain.checkCabinLightSwitchCapabilities(
                { cfg, _ ->
                    markSdkServiceConnected()
                    Log.d(TAG, "CabinLightSwitch capability: ${cfg.accessType}")
                },
                { e -> Log.d(TAG, "checkCabinLightSwitchCapabilities fallback: ${e.message}") }
            )
            lightDomain.checkReadingLightSwitchCapabilities(
                { cfg, map ->
                    markSdkServiceConnected()
                    if (map.isNotEmpty()) readingLightAreas = map.keys.toList()
                    Log.d(TAG, "ReadingLightSwitch capability: ${cfg.accessType}")
                },
                { e -> Log.d(TAG, "checkReadingLightSwitchCapabilities fallback: ${e.message}") }
            )

            lightDomain.registerAmbientLightBrightnessListener(allCabinSeats, ambientBrightnessListener)
            lightDomain.registerAmbientLightBrightnessColorListener(allCabinSeats, ambientColorListener)
            lightDomain.registerAmbientLightStateListener(allCabinSeats, ambientStateListener)
            lightDomain.registerCabinLightState(cabinLightStateListener)
            lightDomain.registerReadingLightState(allCabinSeats, readingLightStateListener)

            lightDomain.getAmbientLightBrightness(
                allCabinSeats,
                { b -> ambientBrightnessListener.onBrightnessUpdated(b) },
                { e -> Log.d(TAG, "getAmbientLightBrightness fallback: ${e.message}") }
            )
            lightDomain.getAmbientLightBrightnessColor(
                allCabinSeats,
                { c -> ambientColorListener.onBrightnessColorUpdated(c) },
                { e -> Log.d(TAG, "getAmbientLightBrightnessColor fallback: ${e.message}") }
            )
            lightDomain.getAmbientLightState(
                allCabinSeats,
                { s -> ambientStateListener.onLightStateUpdated(s) },
                { e -> Log.d(TAG, "getAmbientLightState fallback: ${e.message}") }
            )
            lightDomain.getCurrentCabinLightState(
                { s -> cabinLightStateListener.onLightStateUpdated(s) },
                { e -> Log.d(TAG, "getCurrentCabinLightState fallback: ${e.message}") }
            )
            lightDomain.getCurrentReadingLightState(
                allCabinSeats,
                { s -> readingLightStateListener.onLightStateUpdated(s) },
                { e -> Log.d(TAG, "getCurrentReadingLightState fallback: ${e.message}") }
            )

            // 3) Window (4 Seat Windows + SmartGlass + WindowBlind)
            val windowDomain = vehicle.getWindow()
            windowDomain.checkWindowPositionCapabilities(
                { _, boundsMap ->
                    markSdkServiceConnected()
                    if (boundsMap.isNotEmpty()) windowPosAreas = boundsMap.keys.toList()
                    boundsMap.values.firstOrNull()?.let { bounds ->
                        windowMinPos = bounds.minValue
                        windowMaxPos = bounds.maxValue
                    }
                },
                { e -> Log.d(TAG, "checkWindowPositionCapabilities fallback: ${e.message}") }
            )
            windowDomain.registerWindowPosition(windowFL, windowFLListener)
            windowDomain.registerWindowPosition(windowFR, windowFRListener)
            windowDomain.registerWindowPosition(windowRL, windowRLListener)
            windowDomain.registerWindowPosition(windowRR, windowRRListener)

            windowDomain.getCurrentWindowPosition(
                windowFL,
                { pos -> windowFLListener.onWindowPositionUpdated(pos) },
                { e -> Log.d(TAG, "getCurrentWindowPosition(FL) fallback: ${e.message}") }
            )
            windowDomain.getCurrentWindowPosition(
                windowFR,
                { pos -> windowFRListener.onWindowPositionUpdated(pos) },
                { e -> Log.d(TAG, "getCurrentWindowPosition(FR) fallback: ${e.message}") }
            )
            windowDomain.getCurrentWindowPosition(
                windowRL,
                { pos -> windowRLListener.onWindowPositionUpdated(pos) },
                { e -> Log.d(TAG, "getCurrentWindowPosition(RL) fallback: ${e.message}") }
            )
            windowDomain.getCurrentWindowPosition(
                windowRR,
                { pos -> windowRRListener.onWindowPositionUpdated(pos) },
                { e -> Log.d(TAG, "getCurrentWindowPosition(RR) fallback: ${e.message}") }
            )

            windowDomain.checkMoveWindowBlindCapabilities(
                { cfg, _ ->
                    markSdkServiceConnected()
                    Log.d(TAG, "MoveWindowBlind capability: ${cfg.accessType}")
                },
                { e -> Log.d(TAG, "checkMoveWindowBlindCapabilities fallback: ${e.message}") }
            )
            windowDomain.checkWindowBlindPositionCapabilities(
                { cfg, _ ->
                    markSdkServiceConnected()
                    Log.d(TAG, "WindowBlindPosition capability: ${cfg.accessType}")
                },
                { e -> Log.d(TAG, "checkWindowBlindPositionCapabilities fallback: ${e.message}") }
            )
            windowDomain.registerWindowBlindPosition(roofBlindArea, windowBlindPositionListener)
            windowDomain.getCurrentWindowBlindPosition(
                roofBlindArea,
                { pos -> windowBlindPositionListener.onWindowPositionUpdated(pos) },
                { e -> Log.d(TAG, "getCurrentWindowBlindPosition fallback: ${e.message}") }
            )

            windowDomain.checkSmartGlassStateCapabilities(
                { cfg ->
                    markSdkServiceConnected()
                    Log.d(TAG, "SmartGlassState capability: ${cfg.accessType}")
                },
                { e -> Log.d(TAG, "checkSmartGlassStateCapabilities fallback: ${e.message}") }
            )
            windowDomain.checkSmartGlassLevelCapabilities(
                { _, boundsMap ->
                    markSdkServiceConnected()
                    if (boundsMap.isNotEmpty()) {
                        smartGlassAreas = boundsMap.keys.toList()
                        val primaryGlassArea = boundsMap.keys.first()
                        windowDomain.registerSmartGlassLevel(primaryGlassArea, smartGlassLevelListener)
                        windowDomain.getCurrentSmartGlassLevel(
                            primaryGlassArea,
                            { lvl -> smartGlassLevelListener.onSmartGlassLevelUpdated(lvl) },
                            { _ -> }
                        )
                    }
                    boundsMap.values.firstOrNull()?.let { bounds ->
                        smartGlassMinBound = bounds.minValue
                        smartGlassMaxBound = bounds.maxValue.coerceAtLeast(1)
                        _twinState.update {
                            it.copy(
                                smartGlassMinLevel = smartGlassMinBound,
                                smartGlassMaxLevel = smartGlassMaxBound
                            )
                        }
                    }
                },
                { e -> Log.d(TAG, "checkSmartGlassLevelCapabilities fallback: ${e.message}") }
            )
            windowDomain.registerSmartGlassState(smartGlassStateListener)
            windowDomain.registerSmartGlassLevel(allSideWindows, smartGlassLevelListener)
            windowDomain.getCurrentSmartGlassState(
                { state -> smartGlassStateListener.onSmartGlassStateUpdated(state) },
                { e -> Log.d(TAG, "getCurrentSmartGlassState fallback: ${e.message}") }
            )
            windowDomain.getCurrentSmartGlassLevel(
                allSideWindows,
                { level -> smartGlassLevelListener.onSmartGlassLevelUpdated(level) },
                { e -> Log.d(TAG, "getCurrentSmartGlassLevel fallback: ${e.message}") }
            )

            // 4) HVAC & AirCleaner (dynamically resolving exact VHAL SeatAreas!)
            val hvacDomain = vehicle.getHVAC()
            hvacDomain.checkHvacPowerCapabilities(
                { cfg, areas ->
                    markSdkServiceConnected()
                    if (areas.isNotEmpty()) {
                        hvacPowerAreas = areas
                        val primaryArea = areas.first()
                        hvacDomain.registerHvacPowerState(primaryArea, hvacPowerListener)
                        hvacDomain.getHvacPowerState(
                            primaryArea,
                            { s -> hvacPowerListener.onHvacPowerStateUpdated(s) },
                            { _ -> }
                        )
                    }
                    Log.d(TAG, "HvacPower capability: ${cfg.accessType}, areas=$areas")
                },
                { e -> Log.d(TAG, "checkHvacPowerCapabilities fallback: ${e.message}") }
            )
            hvacDomain.checkHvacAcCapabilities(
                { cfg, areas ->
                    markSdkServiceConnected()
                    if (areas.isNotEmpty()) {
                        hvacAcAreas = areas
                        val primaryArea = areas.first()
                        hvacDomain.registerHvacAcState(primaryArea, hvacAcListener)
                        hvacDomain.getHvacAcState(
                            primaryArea,
                            { s -> hvacAcListener.onHvacAcStateUpdated(s) },
                            { _ -> }
                        )
                    }
                    Log.d(TAG, "HvacAc capability: ${cfg.accessType}, areas=$areas")
                },
                { e -> Log.d(TAG, "checkHvacAcCapabilities fallback: ${e.message}") }
            )
            hvacDomain.checkHvacTemperatureCapabilities(
                { _, boundsMap, _ ->
                    markSdkServiceConnected()
                    if (boundsMap.isNotEmpty()) {
                        hvacTempAreas = boundsMap.keys.toList()
                        val primaryArea = boundsMap.keys.first()
                        hvacDomain.registerHvacTemperature(primaryArea, hvacTargetTempListener)
                        hvacDomain.getHvacTemperature(
                            primaryArea,
                            TemperatureUnit.CELSIUS,
                            { t -> hvacTargetTempListener.onHvacTemperatureUpdated(t) },
                            { _ -> }
                        )
                    }
                    boundsMap.values.firstOrNull()?.let { bounds ->
                        hvacMinTempC = bounds.minValue
                        hvacMaxTempC = bounds.maxValue
                    }
                },
                { e -> Log.d(TAG, "checkHvacTemperatureCapabilities fallback: ${e.message}") }
            )
            hvacDomain.checkHvacTemperatureCurrentCapabilities(
                { cfg, areas ->
                    markSdkServiceConnected()
                    if (areas.isNotEmpty()) {
                        val primaryArea = areas.first()
                        hvacDomain.registerHvacTemperatureCurrent(primaryArea, hvacCurrentTempListener)
                        hvacDomain.getHvacTemperatureCurrent(
                            primaryArea,
                            TemperatureUnit.CELSIUS,
                            { t -> hvacCurrentTempListener.onHvacTemperatureUpdated(t) },
                            { _ -> }
                        )
                    }
                    Log.d(TAG, "HvacTemperatureCurrent capability: ${cfg.accessType}")
                },
                { e -> Log.d(TAG, "checkHvacTemperatureCurrentCapabilities fallback: ${e.message}") }
            )
            hvacDomain.checkHvacFanSpeedCapabilities(
                { _, boundsMap ->
                    markSdkServiceConnected()
                    if (boundsMap.isNotEmpty()) {
                        hvacFanAreas = boundsMap.keys.toList()
                        val primaryArea = boundsMap.keys.first()
                        hvacDomain.registerHvacFanSpeed(primaryArea, hvacFanSpeedListener)
                        hvacDomain.getHvacFanSpeed(
                            primaryArea,
                            { spd -> hvacFanSpeedListener.onHvacFanSpeedUpdated(spd) },
                            { _ -> }
                        )
                    }
                    boundsMap.values.firstOrNull()?.let { bounds ->
                        hvacMinFan = bounds.minValue
                        hvacMaxFan = bounds.maxValue.coerceAtLeast(1)
                    }
                },
                { e -> Log.d(TAG, "checkHvacFanSpeedCapabilities fallback: ${e.message}") }
            )
            hvacDomain.checkHvacRecirculationCapabilities(
                { cfg, areas ->
                    markSdkServiceConnected()
                    if (areas.isNotEmpty()) hvacRecircAreas = areas
                    Log.d(TAG, "HvacRecirculation capability: ${cfg.accessType}")
                },
                { e -> Log.d(TAG, "checkHvacRecirculationCapabilities fallback: ${e.message}") }
            )
            hvacDomain.checkHvacAirCleanerCapabilities(
                { cfg, areas ->
                    markSdkServiceConnected()
                    if (areas.isNotEmpty()) {
                        hvacAirCleanerAreas = areas
                        val primaryArea = areas.first()
                        hvacDomain.registerHvacAirCleanerState(primaryArea, hvacAirCleanerListener)
                        hvacDomain.getHvacAirCleanerState(
                            primaryArea,
                            { ac -> hvacAirCleanerListener.onHvacAirCleanerStateUpdated(ac) },
                            { _ -> }
                        )
                    }
                    Log.d(TAG, "HvacAirCleaner capability: ${cfg.accessType}")
                },
                { e -> Log.d(TAG, "checkHvacAirCleanerCapabilities fallback: ${e.message}") }
            )

            hvacDomain.registerHvacPowerState(frontSeats, hvacPowerListener)
            hvacDomain.registerHvacAcState(frontSeats, hvacAcListener)
            hvacDomain.registerHvacTemperature(frontSeats, hvacTargetTempListener)
            hvacDomain.registerHvacTemperatureCurrent(frontSeats, hvacCurrentTempListener)
            hvacDomain.registerHvacFanSpeed(frontSeats, hvacFanSpeedListener)
            hvacDomain.registerHvacRecirculationState(frontSeats, hvacRecirculationListener)
            hvacDomain.registerHvacAirCleanerState(frontSeats, hvacAirCleanerListener)

            hvacDomain.getHvacPowerState(
                frontSeats,
                { s -> hvacPowerListener.onHvacPowerStateUpdated(s) },
                { e -> Log.d(TAG, "getHvacPowerState fallback: ${e.message}") }
            )
            hvacDomain.getHvacAcState(
                frontSeats,
                { s -> hvacAcListener.onHvacAcStateUpdated(s) },
                { e -> Log.d(TAG, "getHvacAcState fallback: ${e.message}") }
            )
            hvacDomain.getHvacTemperature(
                frontSeats,
                TemperatureUnit.CELSIUS,
                { t -> hvacTargetTempListener.onHvacTemperatureUpdated(t) },
                { e -> Log.d(TAG, "getHvacTemperature fallback: ${e.message}") }
            )
            hvacDomain.getHvacTemperatureCurrent(
                frontSeats,
                TemperatureUnit.CELSIUS,
                { t -> hvacCurrentTempListener.onHvacTemperatureUpdated(t) },
                { e -> Log.d(TAG, "getHvacTemperatureCurrent fallback: ${e.message}") }
            )
            hvacDomain.getHvacFanSpeed(
                frontSeats,
                { spd -> hvacFanSpeedListener.onHvacFanSpeedUpdated(spd) },
                { e -> Log.d(TAG, "getHvacFanSpeed fallback: ${e.message}") }
            )
            hvacDomain.getHvacRecirculationState(
                frontSeats,
                { rec -> hvacRecirculationListener.onHvacAutoRecirculationStateUpdated(rec) },
                { e -> Log.d(TAG, "getHvacRecirculationState fallback: ${e.message}") }
            )
            hvacDomain.getHvacAirCleanerState(
                frontSeats,
                { ac -> hvacAirCleanerListener.onHvacAirCleanerStateUpdated(ac) },
                { e -> Log.d(TAG, "getHvacAirCleanerState fallback: ${e.message}") }
            )

            // 5) Seat (SeatOccupancy + SeatVentilation)
            val seatDomain = vehicle.getSeat()
            seatDomain.checkSeatOccupancyCapabilities(
                { cfg, _ -> Log.d(TAG, "SeatOccupancy capability: ${cfg.accessType}") },
                { e -> Log.d(TAG, "checkSeatOccupancyCapabilities fallback: ${e.message}") }
            )
            seatDomain.checkSeatVentilationCapabilities(
                { _, boundsMap ->
                    boundsMap.values.firstOrNull()?.let { bounds ->
                        seatMaxVentLevel = bounds.maxValue.coerceAtLeast(1)
                    }
                },
                { e -> Log.d(TAG, "checkSeatVentilationCapabilities fallback: ${e.message}") }
            )
            seatDomain.checkMoveSeatBackrestAngleCapabilities(
                { cfg, _ -> Log.d(TAG, "MoveSeatBackrestAngle capability: ${cfg.accessType}") },
                { e -> Log.d(TAG, "checkMoveSeatBackrestAngleCapabilities fallback: ${e.message}") }
            )

            seatDomain.registerSeatOccupancy(seatFL, seatOccupancyFLListener)
            seatDomain.registerSeatOccupancy(seatFR, seatOccupancyFRListener)
            seatDomain.registerSeatOccupancy(seatRL, seatOccupancyRLListener)
            seatDomain.registerSeatOccupancy(seatRR, seatOccupancyRRListener)

            seatDomain.getCurrentSeatOccupancy(
                seatFL,
                { occ -> seatOccupancyFLListener.onSeatOccupancyUpdated(occ) },
                { e -> Log.d(TAG, "getCurrentSeatOccupancy(FL) fallback: ${e.message}") }
            )
            seatDomain.getCurrentSeatOccupancy(
                seatFR,
                { occ -> seatOccupancyFRListener.onSeatOccupancyUpdated(occ) },
                { e -> Log.d(TAG, "getCurrentSeatOccupancy(FR) fallback: ${e.message}") }
            )
            seatDomain.getCurrentSeatOccupancy(
                seatRL,
                { occ -> seatOccupancyRLListener.onSeatOccupancyUpdated(occ) },
                { e -> Log.d(TAG, "getCurrentSeatOccupancy(RL) fallback: ${e.message}") }
            )
            seatDomain.getCurrentSeatOccupancy(
                seatRR,
                { occ -> seatOccupancyRRListener.onSeatOccupancyUpdated(occ) },
                { e -> Log.d(TAG, "getCurrentSeatOccupancy(RR) fallback: ${e.message}") }
            )

            seatDomain.registerHvacSeatVentilation(seatFL, seatVentFLListener)
            seatDomain.registerHvacSeatVentilation(seatFR, seatVentFRListener)
            seatDomain.registerHvacSeatVentilation(seatRL, seatVentRLListener)
            seatDomain.registerHvacSeatVentilation(seatRR, seatVentRRListener)

            seatDomain.getHvacSeatVentilationState(
                seatFL,
                { lvl -> seatVentFLListener.onHvacSeatVentilationUpdated(lvl) },
                { e -> Log.d(TAG, "getHvacSeatVentilationState(FL) fallback: ${e.message}") }
            )
            seatDomain.getHvacSeatVentilationState(
                seatFR,
                { lvl -> seatVentFRListener.onHvacSeatVentilationUpdated(lvl) },
                { e -> Log.d(TAG, "getHvacSeatVentilationState(FR) fallback: ${e.message}") }
            )
            seatDomain.getHvacSeatVentilationState(
                seatRL,
                { lvl -> seatVentRLListener.onHvacSeatVentilationUpdated(lvl) },
                { e -> Log.d(TAG, "getHvacSeatVentilationState(RL) fallback: ${e.message}") }
            )
            seatDomain.getHvacSeatVentilationState(
                seatRR,
                { lvl -> seatVentRRListener.onHvacSeatVentilationUpdated(lvl) },
                { e -> Log.d(TAG, "getHvacSeatVentilationState(RR) fallback: ${e.message}") }
            )

            // 6) Toll (ETCS Voice Guidance Volume)
            val tollDomain = vehicle.getToll()
            tollDomain.checkEtcsVoiceGuidanceVolumeCapabilities(
                { _, bounds ->
                    etcsMinVol = bounds.minValue
                    etcsMaxVol = bounds.maxValue.coerceAtLeast(1)
                    _twinState.update { it.copy(etcsMaxVolume = etcsMaxVol) }
                },
                { e -> Log.d(TAG, "checkEtcsVoiceGuidanceVolumeCapabilities fallback: ${e.message}") }
            )
            tollDomain.registerEtcsVoiceGuidanceVolume(etcsVolumeListener)
            tollDomain.getCurrentEtcsVoiceGuidanceVolume(
                { vol -> etcsVolumeListener.onEtcsVoiceGuidanceVolumeUpdated(vol) },
                { e -> Log.d(TAG, "getCurrentEtcsVoiceGuidanceVolume fallback: ${e.message}") }
            )

            // 7) EvBattery (State of Charge %)
            val evBatteryDomain = vehicle.getEvBattery()
            evBatteryDomain.checkEvBatteryStateOfChargeCapabilities(
                { cfg -> Log.d(TAG, "EvBatteryStateOfCharge capability: ${cfg.accessType}") },
                { e -> Log.d(TAG, "checkEvBatteryStateOfChargeCapabilities fallback: ${e.message}") }
            )
            evBatteryDomain.registerEvBatteryStateOfCharge(evBatterySocListener)
            evBatteryDomain.getEvBatteryStateOfCharge(
                { soc -> evBatterySocListener.onEvBatteryStateOfChargeUpdated(soc) },
                { e -> Log.d(TAG, "getEvBatteryStateOfCharge fallback: ${e.message}") }
            )

            Log.i(TAG, "Official Pleos Vehicle SDK (v2.3.2) initialized with all 7 domains & Virtual Console listeners")
        } catch (t: Throwable) {
            Log.i(TAG, "Running on standard emulator without Pleos VHAL daemon (${t.message})")
        }
    }

    /**
     * Connects to AAOS `android.car.Car` -> `CarUxRestrictionsManager` (`uxrestriction`)
     * to monitor `UX_RESTRICTIONS_NO_KEYBOARD` and `UX_RESTRICTIONS_NO_SETUP` in real time.
     */
    private fun initializeAaosCarUxRestrictions() {
        try {
            val carClass = Class.forName("android.car.Car")
            val createCarMethod = carClass.getMethod("createCar", Context::class.java)
            val carObj = createCarMethod.invoke(null, appContext.applicationContext) ?: return
            aaosCarInstance = carObj

            val getCarManagerMethod = carClass.getMethod("getCarManager", String::class.java)
            val uxrManager = getCarManagerMethod.invoke(carObj, "uxrestriction") ?: return
            aaosUxRestrictionsManager = uxrManager

            val uxrManagerClass = uxrManager.javaClass
            val getCurrentMethod = uxrManagerClass.getMethod("getCurrentCarUxRestrictions")
            val initialRestrictions = getCurrentMethod.invoke(uxrManager)
            if (initialRestrictions != null) {
                applyCarUxRestrictionsObject(initialRestrictions)
            }

            val listenerInterface = Class.forName(
                "android.car.drivingstate.CarUxRestrictionsManager\$OnUxRestrictionsChangedListener"
            )
            val listenerProxy = Proxy.newProxyInstance(
                listenerInterface.classLoader,
                arrayOf(listenerInterface)
            ) { _, method, args ->
                if (method.name == "onUxRestrictionsChanged" && !args.isNullOrEmpty() && args[0] != null) {
                    applyCarUxRestrictionsObject(args[0])
                }
                null
            }
            val registerMethod = uxrManagerClass.getMethod("registerListener", listenerInterface)
            registerMethod.invoke(uxrManager, listenerProxy)
            Log.i(TAG, "AAOS CarUxRestrictionsManager listener registered")
        } catch (t: Throwable) {
            Log.d(TAG, "AAOS CarUxRestrictionsManager fallback (${t.message})")
        }
    }

    private fun applyCarUxRestrictionsObject(restrictionsObj: Any) {
        try {
            val clazz = restrictionsObj.javaClass
            val requiresOpt = clazz.getMethod("isRequiresDistractionOptimization")
                .invoke(restrictionsObj) as? Boolean ?: false
            val activeMask = clazz.getMethod("getActiveRestrictions")
                .invoke(restrictionsObj) as? Int ?: 0

            systemUxRequiresOptimization = requiresOpt
            systemUxActiveRestrictionsMask = activeMask

            val currentGear = _twinState.value.gearPosition
            val parked = (currentGear == GearPosition.P) && !requiresOpt
            val noKeyboard = !parked || (activeMask and UX_RESTRICTIONS_NO_KEYBOARD) != 0
            val noSetup = !parked || (activeMask and UX_RESTRICTIONS_NO_SETUP) != 0

            _twinState.update {
                it.copy(
                    isParked = parked,
                    uxRestrictionsActive = requiresOpt || !parked,
                    uxRestrictionNoKeyboard = noKeyboard,
                    uxRestrictionNoSetup = noSetup,
                    lastSdkCommandLog = "[CarUxRestrictions] requiresOpt=$requiresOpt | noKeyboard=$noKeyboard | noSetup=$noSetup"
                )
            }
        } catch (t: Throwable) {
            Log.d(TAG, "applyCarUxRestrictionsObject error: ${t.message}")
        }
    }

    /**
     * Step 3 Compliance: Unregisters all registered `Vehicle` domain listeners before calling `vehicle.release()`.
     */
    fun release() {
        try {
            val uxrManager = aaosUxRestrictionsManager
            if (uxrManager != null) {
                uxrManager.javaClass.getMethod("unregisterListener").invoke(uxrManager)
            }
        } catch (_: Throwable) {
        }
        try {
            val carObj = aaosCarInstance
            if (carObj != null) {
                carObj.javaClass.getMethod("disconnect").invoke(carObj)
            }
        } catch (_: Throwable) {
        }
        aaosUxRestrictionsManager = null
        aaosCarInstance = null

        try {
            // 1. Gear & Brake
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.getBrake().unregisterBrakePedalStatus(brakeListener)

            // 2. Light
            vehicle.getLight().unregisterAmbientLightBrightnessListener(allCabinSeats, ambientBrightnessListener)
            vehicle.getLight().unregisterAmbientLightBrightnessColorListener(allCabinSeats, ambientColorListener)
            vehicle.getLight().unregisterAmbientLightStateListener(allCabinSeats, ambientStateListener)
            vehicle.getLight().unregisterCabinLightState(cabinLightStateListener)
            vehicle.getLight().unregisterReadingLightState(allCabinSeats, readingLightStateListener)

            // 3. Window, SmartGlass & WindowBlind
            vehicle.getWindow().unregisterWindowPosition(windowFL, windowFLListener)
            vehicle.getWindow().unregisterWindowPosition(windowFR, windowFRListener)
            vehicle.getWindow().unregisterWindowPosition(windowRL, windowRLListener)
            vehicle.getWindow().unregisterWindowPosition(windowRR, windowRRListener)
            vehicle.getWindow().unregisterWindowBlindPosition(roofBlindArea, windowBlindPositionListener)
            vehicle.getWindow().unregisterSmartGlassState(smartGlassStateListener)
            vehicle.getWindow().unregisterSmartGlassLevel(allSideWindows, smartGlassLevelListener)

            // 4. HVAC & AirCleaner
            vehicle.getHVAC().unregisterHvacPowerState(frontSeats, hvacPowerListener)
            vehicle.getHVAC().unregisterHvacAcState(frontSeats, hvacAcListener)
            vehicle.getHVAC().unregisterHvacTemperature(frontSeats, hvacTargetTempListener)
            vehicle.getHVAC().unregisterHvacTemperatureCurrent(frontSeats, hvacCurrentTempListener)
            vehicle.getHVAC().unregisterHvacFanSpeed(frontSeats, hvacFanSpeedListener)
            vehicle.getHVAC().unregisterHvacRecirculationState(frontSeats, hvacRecirculationListener)
            vehicle.getHVAC().unregisterHvacAirCleanerState(frontSeats, hvacAirCleanerListener)

            // 5. Seat Occupancy & Ventilation
            vehicle.getSeat().unregisterSeatOccupancy(seatFL, seatOccupancyFLListener)
            vehicle.getSeat().unregisterSeatOccupancy(seatFR, seatOccupancyFRListener)
            vehicle.getSeat().unregisterSeatOccupancy(seatRL, seatOccupancyRLListener)
            vehicle.getSeat().unregisterSeatOccupancy(seatRR, seatOccupancyRRListener)
            vehicle.getSeat().unregisterHvacSeatVentilation(seatFL, seatVentFLListener)
            vehicle.getSeat().unregisterHvacSeatVentilation(seatFR, seatVentFRListener)
            vehicle.getSeat().unregisterHvacSeatVentilation(seatRL, seatVentRLListener)
            vehicle.getSeat().unregisterHvacSeatVentilation(seatRR, seatVentRRListener)

            // 6. Toll ETCS
            vehicle.getToll().unregisterEtcsVoiceGuidanceVolume(etcsVolumeListener)

            // 7. EvBattery SOC
            vehicle.getEvBattery().unregisterEvBatteryStateOfCharge(evBatterySocListener)

            // Release Vehicle binding
            vehicle.release()
            Log.i(TAG, "All 30 PleosVehicleManager listeners unregistered and Vehicle.release() completed")
        } catch (t: Throwable) {
            Log.d(TAG, "Vehicle.release() safe ignore: ${t.message}")
        }
    }

    /**
     * Automatically sets the initial vehicle ambient light mood via Pleos SDK when a song/genre is selected.
     */
    fun onSongGenreSelected(songId: String, genre: String, bpm: Int) {
        val curState = _twinState.value
        val mode = curState.lightingControlMode
        val isEco = curState.ecoStageModeActive
        val isBallad = genre.contains("Ballad", ignoreCase = true) || bpm < 95
        val zone = when {
            isEco -> VocalZoneTheme.ECO_BATTERY_SAVER
            isBallad -> VocalZoneTheme.BALLAD_STARLIGHT_WAVE
            else -> VocalZoneTheme.KPOP_SYNTH_GROOVE
        }
        if (mode == LightingControlMode.OFF) {
            _twinState.update {
                it.copy(
                    ambientLightEnabled = false,
                    ambientBrightness = 0,
                    strobeActive = false,
                    ambientColorHex = zone.primaryHex,
                    ambientSecondaryHex = zone.secondaryHex,
                    vocalZoneTheme = zone
                )
            }
            return
        }

        val cap = if (isEco) ECO_MAX_AMBIENT_BRIGHTNESS else 100
        val initialBrightness = when (mode) {
            LightingControlMode.FIXED -> curState.fixedAmbientBrightness.coerceIn(10, cap)
            LightingControlMode.AUTO_MUSIC -> {
                val rawBrightness = if (isBallad) 55 else 75
                if (isEco) rawBrightness.coerceAtMost(ECO_MAX_AMBIENT_BRIGHTNESS) else rawBrightness
            }
            LightingControlMode.OFF -> 0
        }

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
                lastSdkCommandLog = "[Genre Sync: $genre] Light(${mode.label}) 0x${zone.primaryHex.toString(16).uppercase()} | Brightness=$initialBrightness%"
            )
        }
    }

    /**
     * Choreography #1 & #3:
     * Triggered when a song starts:
     * - [방음 부스 모드 (Window & Sunroof)]: Closes all 4 windows (`setWindowPosition(0)`) & roof blind (`moveWindowBlind(FORWARD)`).
     * - [스마트 글래스 암막 콘서트홀 (SmartGlass)]: Lowers window transparency (`setSmartGlassState(true)`, `setSmartGlassLevel(max)`).
     * - [실내등/독서등 자동 소등 & 앰비언트 라이트 활성화 (Light)]: `setCabinLightSwitch(OFF)`, `setReadingLightSwitch(OFF)`, `setAmbientLightSwitch(ON)`.
     * - [하이패스 안내음 자동 음소거 (Toll)]: `setEtcsVoiceGuidanceVolume(0)`.
     * - [밀폐 차 안 쾌적 공기청정 가동 (HVAC AirCleaner)]: `setHvacAirCleaner(true)`.
     */
    fun onSongStarted(songTitle: String, genre: String = "", bpm: Int = 120) {
        val curState = _twinState.value
        val mode = curState.lightingControlMode
        val isEco = curState.ecoStageModeActive
        val isBallad = genre.contains("Ballad", ignoreCase = true) || bpm < 95
        val startZone = when {
            isEco -> VocalZoneTheme.ECO_BATTERY_SAVER
            isBallad -> VocalZoneTheme.BALLAD_STARLIGHT_WAVE
            else -> VocalZoneTheme.KPOP_SYNTH_GROOVE
        }
        val cap = if (isEco) ECO_MAX_AMBIENT_BRIGHTNESS else 100
        val targetBrightness = when (mode) {
            LightingControlMode.OFF -> 0
            LightingControlMode.FIXED -> curState.fixedAmbientBrightness.coerceIn(10, cap)
            LightingControlMode.AUTO_MUSIC -> if (isEco) {
                ECO_MAX_AMBIENT_BRIGHTNESS
            } else if (isBallad) {
                60
            } else {
                80
            }
        }
        val shouldEnableAmbient = mode != LightingControlMode.OFF
        val darkGlassLevel = smartGlassMaxBound.coerceAtLeast(1)

        try {
            // 1. Close all 4 windows & roof blind
            vehicle.getWindow().setWindowPosition(
                allSideWindows,
                windowMinPos,
                { _, pos -> Log.d(TAG, "Windows closed to $pos%") },
                { e -> Log.d(TAG, "Window.setWindowPosition fallback: ${e.message}") }
            )
            vehicle.getWindow().moveWindowBlind(
                roofBlindArea,
                MoveControl.FORWARD,
                { _, ctrl -> Log.d(TAG, "Roof blind closed: $ctrl") },
                { e -> Log.d(TAG, "Window.moveWindowBlind fallback: ${e.message}") }
            )
            // 2. Activate SmartGlass concert hall blackout
            vehicle.getWindow().setSmartGlassState(
                true,
                { state -> Log.d(TAG, "SmartGlass state enabled: $state") },
                { e -> Log.d(TAG, "Window.setSmartGlassState fallback: ${e.message}") }
            )
            vehicle.getWindow().setSmartGlassLevel(
                allSideWindows,
                darkGlassLevel,
                { _, lvl -> Log.d(TAG, "SmartGlass level set to $lvl") },
                { e -> Log.d(TAG, "Window.setSmartGlassLevel fallback: ${e.message}") }
            )
            // 3. Turn OFF cabin light & reading light for concert hall darkness, respect ambient lightingControlMode
            vehicle.getLight().setCabinLightSwitch(
                LightSwitch.OFF,
                { sw -> Log.d(TAG, "Cabin light auto-off: $sw") },
                { e -> Log.d(TAG, "Light.setCabinLightSwitch fallback: ${e.message}") }
            )
            vehicle.getLight().setReadingLightSwitch(
                allCabinSeats,
                LightSwitch.OFF,
                { sw -> Log.d(TAG, "Reading light auto-off: $sw") },
                { e -> Log.d(TAG, "Light.setReadingLightSwitch fallback: ${e.message}") }
            )
            vehicle.getLight().setAmbientLightSwitch(
                allCabinSeats,
                if (shouldEnableAmbient) LightSwitch.ON else LightSwitch.OFF,
                { _, sw -> Log.d(TAG, "Ambient light switch: $sw") },
                { e -> Log.d(TAG, "Light.setAmbientLightSwitch fallback: ${e.message}") }
            )
            vehicle.getLight().setAmbientColorAutoByDriveModeEnabled(
                false,
                { _ -> },
                { _ -> }
            )
            // 4. Mute Hi-Pass (ETCS) voice guidance during singing
            val curEtcsVol = _twinState.value.etcsVoiceGuidanceVolume
            if (curEtcsVol > 0) {
                _twinState.update { it.copy(etcsSavedVolumeBeforeMute = curEtcsVol) }
            }
            vehicle.getToll().setEtcsVoiceGuidanceVolume(
                0,
                { Log.d(TAG, "ETCS voice guidance muted (0)") },
                { e -> Log.d(TAG, "Toll.setEtcsVoiceGuidanceVolume(0) fallback: ${e.message}") }
            )
            // 5. Turn ON HVAC AirCleaner for enclosed soundproof cabin
            vehicle.getHVAC().setHvacAirCleaner(
                frontSeats,
                true,
                { _, state -> Log.d(TAG, "HVAC AirCleaner enabled: $state") },
                { e -> Log.d(TAG, "HVAC.setHvacAirCleaner fallback: ${e.message}") }
            )
        } catch (t: Throwable) {
            Log.d(TAG, "onSongStarted SDK call fallback: ${t.message}")
        }

        if (shouldEnableAmbient) {
            applyVehicleAmbientLightSdk(
                argbColor = startZone.primaryHex.toInt(),
                brightness = targetBrightness,
                readingLightOn = false
            )
        } else {
            lastAppliedSdkBrightness = 0
            try {
                for (area in resolveLightAreas(ambientBrightnessAreas)) {
                    vehicle.getLight().setAmbientLightBrightness(area, 0, { _, _ -> }, { _ -> })
                }
            } catch (_: Throwable) {
            }
        }

        _twinState.update {
            it.copy(
                allWindowsClosed = true,
                windowOpenPercentFL = 0,
                windowOpenPercentFR = 0,
                windowOpenPercentRL = 0,
                windowOpenPercentRR = 0,
                roofBlindClosed = true,
                roofBlindPositionPercent = 0,
                smartGlassBlackoutEnabled = true,
                smartGlassLevel = darkGlassLevel,
                ambientLightEnabled = shouldEnableAmbient,
                cabinLightOffForStage = true,
                readingLightOffForStage = true,
                ambientColorHex = startZone.primaryHex,
                ambientSecondaryHex = startZone.secondaryHex,
                ambientBrightness = targetBrightness,
                vocalZoneTheme = startZone,
                strobeActive = if (shouldEnableAmbient) it.strobeActive else false,
                tollHiPassMuted = true,
                etcsVoiceGuidanceVolume = 0,
                nonCriticalAlertsMuted = true,
                hvacAirCleanerEnabled = true,
                encoreFinaleActive = false,
                seatRelaxationPulseActive = false,
                lastSdkCommandLog = "[Stage Start] Window(0%) · SmartGlass($darkGlassLevel) · Ambient(${mode.label}:$targetBrightness%) · ETCS(0) · AirCleaner(ON)"
            )
        }
    }

    /**
     * Choreography #2:
     * [장르·파형·보컬 실시간 반응형 차량 앰비언트 라이트 (Pleos SDK Light Domain)]
     */
    fun onMusicAndVocalWaveformTick(
        genre: String,
        bpm: Int,
        elapsedSec: Float,
        vocalRms: Float,
        vocalPitchHz: Float,
        isHighlightOrInterlude: Boolean
    ) {
        val snapshot = _twinState.value
        val mode = snapshot.lightingControlMode

        // 1. LightingControlMode.OFF -> strictly 0% & OFF, never overwritten by beat loop
        if (mode == LightingControlMode.OFF) {
            if (snapshot.ambientBrightness != 0 || snapshot.ambientLightEnabled || snapshot.strobeActive) {
                _twinState.update {
                    it.copy(
                        ambientLightEnabled = false,
                        ambientBrightness = 0,
                        strobeActive = false
                    )
                }
            }
            return
        }

        val isEco = snapshot.ecoStageModeActive

        // 2. LightingControlMode.FIXED -> keep user's fixed brightness completely steady (zero beat fluctuation!)
        if (mode == LightingControlMode.FIXED) {
            val cap = if (isEco) ECO_MAX_AMBIENT_BRIGHTNESS else 100
            val lockedBrightness = snapshot.fixedAmbientBrightness.coerceIn(10, cap)
            if (snapshot.ambientBrightness != lockedBrightness || !snapshot.ambientLightEnabled || snapshot.strobeActive) {
                applyVehicleAmbientLightSdk(
                    argbColor = snapshot.ambientColorHex.toInt(),
                    brightness = lockedBrightness,
                    readingLightOn = false
                )
                _twinState.update {
                    it.copy(
                        ambientLightEnabled = true,
                        ambientBrightness = lockedBrightness,
                        strobeActive = false
                    )
                }
            }
            return
        }

        // 3. LightingControlMode.AUTO_MUSIC -> dynamic music beat & vocal energy reaction
        val isBallad = genre.contains("Ballad", ignoreCase = true) || bpm < 95

        val beatsElapsed = elapsedSec * (bpm.coerceAtLeast(60) / 60f)
        val beatWave = ((sin(beatsElapsed * 2.0 * PI) + 1.0) * 0.5).toFloat()
        val barIndex = (beatsElapsed / 4f).toInt()

        val instrumentalEnergy = if (isBallad) {
            28f + beatWave * 24f + (if (isHighlightOrInterlude) 18f else 0f)
        } else {
            42f + beatWave * 36f + (if (isHighlightOrInterlude) 20f else 0f)
        }
        val combinedWaveEnergy = maxOf(instrumentalEnergy, vocalRms)

        val isClimax = vocalPitchHz > 370f || vocalRms > 75f || (isHighlightOrInterlude && beatWave > 0.7f)

        val zone: VocalZoneTheme
        val targetColorHex: Long

        if (isEco) {
            zone = VocalZoneTheme.ECO_BATTERY_SAVER
            targetColorHex = blendColorHex(0xFF1BB55C, 0xFF00E5FF, beatWave * 0.5f)
        } else if (_twinState.value.encoreFinaleActive) {
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

        val rawBrightness = (30f + combinedWaveEnergy * 0.7f).roundToInt().coerceIn(25, 100)
        val targetBrightness = if (isEco) rawBrightness.coerceAtMost(ECO_MAX_AMBIENT_BRIGHTNESS) else rawBrightness
        val strobe = !isEco && isClimax && combinedWaveEnergy > 82f

        applyVehicleAmbientLightSdk(
            argbColor = targetColorHex.toInt(),
            brightness = targetBrightness,
            readingLightOn = false // Keep reading lights OFF during concert performance
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
            val light = vehicle.getLight()
            if (argbColor != lastAppliedSdkColor) {
                lastAppliedSdkColor = argbColor
                for (area in resolveLightAreas(ambientColorAreas)) {
                    light.setAmbientLightBrightnessColor(
                        area,
                        argbColor,
                        { _, _ -> markSdkServiceConnected() },
                        { _ -> }
                    )
                }
            }
            if (abs(brightness - lastAppliedSdkBrightness) >= 2) {
                lastAppliedSdkBrightness = brightness
                for (area in resolveLightAreas(ambientBrightnessAreas)) {
                    light.setAmbientLightBrightness(
                        area,
                        brightness,
                        { _, _ -> markSdkServiceConnected() },
                        { _ -> }
                    )
                }
            }
            if (lastReadingLightOn != readingLightOn) {
                lastReadingLightOn = readingLightOn
                light.setReadingLightSwitch(
                    secondRowSeats,
                    if (readingLightOn) LightSwitch.ON else LightSwitch.OFF,
                    { _ -> markSdkServiceConnected() },
                    { _ -> }
                )
            }
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
     * Applies `setHvacSeatVentilation` ONLY to currently occupied (`Occupied`) seats based on `SeatOccupancy` detection.
     */
    fun applySmartOccupiedSeatVentilation(targetLevel: Int) {
        val safeLevel = if (_twinState.value.ecoStageModeActive) {
            0
        } else {
            targetLevel.coerceIn(0, seatMaxVentLevel)
        }
        val state = _twinState.value
        val occFL = state.seatOccupiedFL
        val occFR = state.seatOccupiedFR
        val occRL = state.seatOccupiedRL
        val occRR = state.seatOccupiedRR
        val anyOccupied = occFL || occFR || occRL || occRR

        val levelFL = if (occFL || !anyOccupied) safeLevel else 0
        val levelFR = if (occFR) safeLevel else 0
        val levelRL = if (occRL) safeLevel else 0
        val levelRR = if (occRR) safeLevel else 0

        try {
            // Auto-enable HVAC Master Power so emulator HVAC bar activates immediately with seat ventilation
            ensureHvacMasterPowerOn()
            vehicle.getSeat().setHvacSeatVentilation(seatFL, levelFL, { _, _ -> markSdkServiceConnected() }, { _ -> })
            vehicle.getSeat().setHvacSeatVentilation(seatFR, levelFR, { _, _ -> markSdkServiceConnected() }, { _ -> })
            vehicle.getSeat().setHvacSeatVentilation(seatRL, levelRL, { _, _ -> markSdkServiceConnected() }, { _ -> })
            vehicle.getSeat().setHvacSeatVentilation(seatRR, levelRR, { _, _ -> markSdkServiceConnected() }, { _ -> })
        } catch (t: Throwable) {
            Log.d(TAG, "applySmartOccupiedSeatVentilation fallback: ${t.message}")
        }

        _twinState.update {
            it.copy(
                hvacPowerOn = true,
                seatVentilationLevel = safeLevel,
                seatVentilationFL = levelFL,
                seatVentilationFR = levelFR,
                seatVentilationRL = levelRL,
                seatVentilationRR = levelRR,
                lastSdkCommandLog = "탑승객 통풍 시트 ${safeLevel}단 연동 완료 (${it.occupiedSeatsSummary})"
            )
        }
    }

    /**
     * Choreography #4:
     * [열창 쿨다운 & 그린룸 케어 (HVAC & SeatOccupancy Smart Ventilation)]
     */
    fun triggerGreenroomCooldownCare(reason: String = "열창 쿨다운 공조 가동") {
        val isEco = _twinState.value.ecoStageModeActive
        val targetTemp = if (isEco) 23.0f else 20.5f.coerceIn(hvacMinTempC, hvacMaxTempC)
        val targetFan = if (isEco) 1 else 4.coerceIn(hvacMinFan, hvacMaxFan)
        val targetVent = if (isEco) 0 else 2.coerceIn(0, seatMaxVentLevel)

        try {
            ensureHvacMasterPowerOn()
            val hvac = vehicle.getHVAC()
            for (area in resolveHvacAreas(hvacAcAreas)) {
                hvac.setHvacAc(area, !isEco, { _, _ -> markSdkServiceConnected() }, { _ -> })
            }
            for (area in resolveHvacAreas(hvacRecircAreas)) {
                hvac.setHvacRecirculation(area, false, { _, _ -> markSdkServiceConnected() }, { _ -> })
            }
            for (area in resolveHvacAreas(hvacAirCleanerAreas)) {
                hvac.setHvacAirCleaner(area, true, { _, _ -> markSdkServiceConnected() }, { _ -> })
            }
            for (area in resolveHvacAreas(hvacFanAreas)) {
                hvac.setHvacFanSpeed(area, targetFan, { _, _ -> markSdkServiceConnected() }, { _ -> })
            }
            for (area in resolveHvacAreas(hvacTempAreas)) {
                hvac.setHvacTemperature(
                    area,
                    targetTemp,
                    TemperatureUnit.CELSIUS,
                    { _, _ -> markSdkServiceConnected() },
                    { _ -> }
                )
            }
        } catch (t: Throwable) {
            Log.d(TAG, "triggerGreenroomCooldownCare SDK fallback: ${t.message}")
        }

        applySmartOccupiedSeatVentilation(targetVent)

        _twinState.update {
            it.copy(
                hvacPowerOn = true,
                hvacAcOn = !isEco,
                hvacOuterAirMode = true,
                hvacAirCleanerEnabled = true,
                hvacTempCelsius = targetTemp,
                hvacFanLevel = targetFan,
                greenroomCareActive = true,
                lastSdkCommandLog = "$reason · 희망 온도 ${targetTemp}°C · 바람 ${targetFan}단"
            )
        }
    }

    fun accumulateHighOutputSingingSecond(isHighOutput: Boolean) {
        val nextSec = if (isHighOutput) _twinState.value.highRmsAccumulatedSec + 1 else _twinState.value.highRmsAccumulatedSec
        _twinState.update { it.copy(highRmsAccumulatedSec = nextSec) }
        if (nextSec >= 30 && !_twinState.value.greenroomCareActive) {
            triggerGreenroomCooldownCare("열창 감지 자동 쿨다운")
        }
    }

    /**
     * Choreography #5:
     * [관객 앙코르 인터랙션 (Seat & Light) + 하이패스 안내음 복원 (Toll)]
     */
    fun onSongFinished(finalScore: Int) {
        triggerGreenroomCooldownCare("곡 종료 쾌적 케어")

        val restoredVol = _twinState.value.etcsSavedVolumeBeforeMute.coerceIn(1, etcsMaxVol)
        try {
            vehicle.getToll().setEtcsVoiceGuidanceVolume(
                restoredVol,
                { markSdkServiceConnected() },
                { e -> Log.d(TAG, "Toll.setEtcsVoiceGuidanceVolume restore fallback: ${e.message}") }
            )
        } catch (t: Throwable) {
            Log.d(TAG, "ETCS restore fallback: ${t.message}")
        }

        if (finalScore >= 90) {
            triggerEncoreFinalePerformance()
            _twinState.update {
                it.copy(
                    tollHiPassMuted = false,
                    etcsVoiceGuidanceVolume = restoredVol,
                    nonCriticalAlertsMuted = false
                )
            }
        } else {
            _twinState.update {
                it.copy(
                    tollHiPassMuted = false,
                    etcsVoiceGuidanceVolume = restoredVol,
                    nonCriticalAlertsMuted = false,
                    lastSdkCommandLog = "곡 종료 · 쾌적 공조 및 하이패스 안내음(${restoredVol}단) 복원"
                )
            }
        }
    }

    /**
     * Real Encore Finale Stage Performance (`[앙코르 피날레 효과]`):
     * Turns ON cabin & reading lights + sweeps a vibrant 6-step ambient light color wave across the vehicle cabin!
     */
    fun triggerEncoreFinalePerformance() {
        val curMode = _twinState.value.lightingControlMode
        if (curMode == LightingControlMode.OFF) {
            return
        }
        val isEco = _twinState.value.ecoStageModeActive
        val cap = if (isEco) ECO_MAX_AMBIENT_BRIGHTNESS else 100
        val finaleBrightness = if (curMode == LightingControlMode.FIXED) {
            _twinState.value.fixedAmbientBrightness.coerceIn(10, cap)
        } else {
            cap
        }
        lastAppliedSdkBrightness = finaleBrightness

        Thread(
            {
                try {
                    val light = vehicle.getLight()
                    // 1) Turn ON Cabin Light + Reading Lights + Ambient Light at full brightness
                    light.setCabinLightSwitch(LightSwitch.ON, { _ -> markSdkServiceConnected() }, { _ -> })
                    for (area in resolveLightAreas(readingLightAreas)) {
                        light.setReadingLightSwitch(area, LightSwitch.ON, { _ -> markSdkServiceConnected() }, { _ -> })
                    }
                    for (area in resolveLightAreas(ambientSwitchAreas)) {
                        light.setAmbientLightSwitch(area, LightSwitch.ON, { _, _ -> markSdkServiceConnected() }, { _ -> })
                    }
                    for (area in resolveLightAreas(ambientBrightnessAreas)) {
                        light.setAmbientLightBrightness(area, finaleBrightness, { _, _ -> markSdkServiceConnected() }, { _ -> })
                    }
                    try {
                        vehicle.getSeat().moveSeatBackrestAngle(frontSeats, MoveControl.BACKWARD, { _, _ -> }, { _ -> })
                    } catch (_: Throwable) {
                    }

                    // 2) Celebratory 6-step Ambient Color Wave across the cabin
                    val waveColors = longArrayOf(
                        0xFFFFD600L, // Encore Gold
                        0xFFFF2E93L, // Concert Magenta
                        0xFF00E5FFL, // Neon Cyan
                        0xFF7C4DFFL, // Stage Violet
                        0xFF00E676L, // Emerald Glow
                        0xFFFFD600L  // Grand Finale Gold
                    )

                    for (idx in waveColors.indices) {
                        val hex = waveColors[idx]
                        lastAppliedSdkColor = hex.toInt()
                        for (area in resolveLightAreas(ambientColorAreas)) {
                            try {
                                light.setAmbientLightBrightnessColor(
                                    area,
                                    hex.toInt(),
                                    { _, _ -> markSdkServiceConnected() },
                                    { _ -> }
                                )
                            } catch (_: Throwable) {
                            }
                        }
                        _twinState.update {
                            it.copy(
                                ambientLightEnabled = true,
                                cabinLightOffForStage = false,
                                readingLightOffForStage = false,
                                encoreFinaleActive = true,
                                seatRelaxationPulseActive = true,
                                vocalZoneTheme = VocalZoneTheme.ENCORE_GOLD_FINALE,
                                ambientColorHex = hex,
                                ambientSecondaryHex = VocalZoneTheme.ENCORE_GOLD_FINALE.secondaryHex,
                                ambientBrightness = finaleBrightness,
                                strobeActive = !isEco && (idx < waveColors.lastIndex),
                                lastSdkCommandLog = "앙코르 피날레 조명 퍼포먼스 진행 중 (${idx + 1}/${waveColors.size})"
                            )
                        }
                        if (idx < waveColors.lastIndex) {
                            Thread.sleep(360L)
                        }
                    }
                } catch (t: Throwable) {
                    Log.d(TAG, "triggerEncoreFinalePerformance fallback: ${t.message}")
                }
            },
            "PleosEncoreFinaleWave"
        ).apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Choreography #6:
     * [주행 안전 인터락 (Read-Only Driving Gear Selection & CarUxRestrictions - AAOS Driver Distraction Compliance)]
     */
    private fun applySdkGearSelectionState(gear: GearPosition) {
        val parkingBrake = (gear == GearPosition.P)
        val parked = (gear == GearPosition.P) && !systemUxRequiresOptimization
        val noKeyboard = !parked || (systemUxActiveRestrictionsMask and UX_RESTRICTIONS_NO_KEYBOARD) != 0
        val noSetup = !parked || (systemUxActiveRestrictionsMask and UX_RESTRICTIONS_NO_SETUP) != 0

        _twinState.update {
            it.copy(
                gearPosition = gear,
                parkingBrakeEngaged = parkingBrake,
                isParked = parked,
                uxRestrictionsActive = !parked || systemUxRequiresOptimization,
                uxRestrictionNoKeyboard = noKeyboard,
                uxRestrictionNoSetup = noSetup,
                lastSdkCommandLog = if (parked) {
                    "주차 중 (${gear.label}) · 모든 노래방 공간 연출 사용 가능"
                } else {
                    "주행 중 (${gear.label}) · 안전 음악 감상 모드 작동"
                }
            )
        }
    }

    // ========================================================================
    // Interactive Bidirectional SDK Controls (UI <-> Pleos SDK <-> Virtual Console)
    // ========================================================================

    fun toggleSoundproofWindowsManually() {
        val nextClosed = !_twinState.value.allWindowsClosed
        val frontPos = if (nextClosed) windowMinPos else 35.coerceIn(windowMinPos, windowMaxPos)
        val rearPos = if (nextClosed) windowMinPos else 20.coerceIn(windowMinPos, windowMaxPos)
        try {
            val win = vehicle.getWindow()
            // Call per-seat WindowArea + combined WindowArea so all 4 windows actuate reliably
            win.setWindowPosition(windowFL, frontPos, { _, _ -> markSdkServiceConnected() }, { _ -> })
            win.setWindowPosition(windowFR, frontPos, { _, _ -> markSdkServiceConnected() }, { _ -> })
            win.setWindowPosition(windowRL, rearPos, { _, _ -> markSdkServiceConnected() }, { _ -> })
            win.setWindowPosition(windowRR, rearPos, { _, _ -> markSdkServiceConnected() }, { _ -> })
            if (nextClosed) {
                win.setWindowPosition(allSideWindows, windowMinPos, { _, _ -> markSdkServiceConnected() }, { _ -> })
            }
            win.setWindowLock(allSideWindows, nextClosed, { _, _ -> markSdkServiceConnected() }, { _ -> })
            win.moveWindowBlind(
                roofBlindArea,
                if (nextClosed) MoveControl.FORWARD else MoveControl.BACKWARD,
                { _, _ -> markSdkServiceConnected() },
                { _ -> }
            )
            if (nextClosed) {
                ensureHvacMasterPowerOn()
                for (area in resolveHvacAreas(hvacAirCleanerAreas)) {
                    vehicle.getHVAC().setHvacAirCleaner(area, true, { _, _ -> markSdkServiceConnected() }, { _ -> })
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "toggleSoundproofWindowsManually SDK fallback: ${t.message}")
        }

        _twinState.update {
            it.copy(
                allWindowsClosed = nextClosed,
                windowOpenPercentFL = if (nextClosed) 0 else frontPos,
                windowOpenPercentFR = if (nextClosed) 0 else frontPos,
                windowOpenPercentRL = if (nextClosed) 0 else rearPos,
                windowOpenPercentRR = if (nextClosed) 0 else rearPos,
                roofBlindClosed = nextClosed,
                hvacPowerOn = if (nextClosed) true else it.hvacPowerOn,
                hvacAirCleanerEnabled = if (nextClosed) true else it.hvacAirCleanerEnabled,
                lastSdkCommandLog = if (nextClosed) "모든 좌석 창문 닫힘 및 공기청정 연동" else "창문 환기 열림"
            )
        }
    }

    /**
     * Feature #2: SmartGlass & WindowBlind Concert Hall Blackout Toggle (`[암막 스마트 글래스 ON/OFF]`)
     */
    fun toggleBlackoutSmartGlassManually() {
        val nextBlackout = !_twinState.value.smartGlassBlackoutEnabled
        val targetLevel = if (nextBlackout) smartGlassMaxBound.coerceAtLeast(1) else smartGlassMinBound
        val blindMove = if (nextBlackout) MoveControl.FORWARD else MoveControl.BACKWARD
        try {
            val win = vehicle.getWindow()
            win.setSmartGlassState(
                nextBlackout,
                { _ -> markSdkServiceConnected() },
                { _ -> }
            )
            for (area in resolveWindowAreas(smartGlassAreas)) {
                win.setSmartGlassLevel(
                    area,
                    targetLevel,
                    { _, _ -> markSdkServiceConnected() },
                    { _ -> }
                )
            }
            win.moveWindowBlind(
                roofBlindArea,
                blindMove,
                { _, _ -> markSdkServiceConnected() },
                { _ -> }
            )
        } catch (t: Throwable) {
            Log.d(TAG, "toggleBlackoutSmartGlassManually SDK fallback: ${t.message}")
        }

        _twinState.update {
            it.copy(
                smartGlassBlackoutEnabled = nextBlackout,
                smartGlassLevel = targetLevel,
                roofBlindClosed = nextBlackout,
                roofBlindPositionPercent = if (nextBlackout) 0 else 100,
                lastSdkCommandLog = if (nextBlackout) "스마트 글래스 암막 켜짐 (${targetLevel}단계)" else "스마트 글래스 투명 모드"
            )
        }
    }

    fun setSmartGlassLevelManually(level: Int) {
        val safeLevel = level.coerceIn(smartGlassMinBound, smartGlassMaxBound.coerceAtLeast(1))
        val enabled = safeLevel > smartGlassMinBound
        try {
            val win = vehicle.getWindow()
            win.setSmartGlassState(enabled, { _ -> markSdkServiceConnected() }, { _ -> })
            for (area in resolveWindowAreas(smartGlassAreas)) {
                win.setSmartGlassLevel(area, safeLevel, { _, _ -> markSdkServiceConnected() }, { _ -> })
            }
        } catch (t: Throwable) {
            Log.d(TAG, "setSmartGlassLevelManually fallback: ${t.message}")
        }

        _twinState.update {
            it.copy(
                smartGlassBlackoutEnabled = enabled,
                smartGlassLevel = safeLevel,
                lastSdkCommandLog = "스마트 글래스 암막 ${safeLevel}단계 설정"
            )
        }
    }

    /**
     * Toll ETCS Voice Guidance Volume Mute / Restore Toggle (`vehicle.getToll()`)
     */
    fun toggleTollHiPassMuteManually() {
        val nextMuted = !_twinState.value.tollHiPassMuted
        val currentVol = _twinState.value.etcsVoiceGuidanceVolume
        val savedVol = if (currentVol > 0) currentVol else _twinState.value.etcsSavedVolumeBeforeMute.coerceAtLeast(3)
        val targetVol = if (nextMuted) 0 else savedVol.coerceIn(1, etcsMaxVol)

        try {
            vehicle.getToll().setEtcsVoiceGuidanceVolume(
                targetVol,
                { markSdkServiceConnected() },
                { e -> Log.d(TAG, "Toll.setEtcsVoiceGuidanceVolume($targetVol) fallback: ${e.message}") }
            )
        } catch (t: Throwable) {
            Log.d(TAG, "toggleTollHiPassMuteManually SDK fallback: ${t.message}")
        }

        _twinState.update {
            it.copy(
                tollHiPassMuted = nextMuted,
                etcsVoiceGuidanceVolume = targetVol,
                etcsSavedVolumeBeforeMute = savedVol,
                nonCriticalAlertsMuted = nextMuted,
                lastSdkCommandLog = if (nextMuted) "하이패스 안내음 잠시 꺼짐 (음소거)" else "하이패스 안내음 켜짐 (${targetVol}단)"
            )
        }
    }

    /**
     * Feature #3: Enclosed Cabin AirCleaner Toggle (`vehicle.getHVAC().setHvacAirCleaner`)
     */
    fun toggleAirCleanerManually() {
        val nextEnabled = !_twinState.value.hvacAirCleanerEnabled
        try {
            // Unconditionally turn ON HVAC Master Power so emulator HVAC bar activates immediately!
            ensureHvacMasterPowerOn()
            val hvac = vehicle.getHVAC()
            for (area in resolveHvacAreas(hvacAirCleanerAreas)) {
                hvac.setHvacAirCleaner(
                    area,
                    nextEnabled,
                    { _, _ -> markSdkServiceConnected() },
                    { e -> Log.d(TAG, "HVAC.setHvacAirCleaner fallback: ${e.message}") }
                )
            }
            for (area in resolveHvacAreas(hvacRecircAreas)) {
                hvac.setHvacRecirculation(
                    area,
                    nextEnabled,
                    { _, _ -> markSdkServiceConnected() },
                    { _ -> }
                )
            }
        } catch (t: Throwable) {
            Log.d(TAG, "toggleAirCleanerManually fallback: ${t.message}")
        }

        _twinState.update {
            it.copy(
                hvacPowerOn = true,
                hvacAirCleanerEnabled = nextEnabled,
                lastSdkCommandLog = if (nextEnabled) "실내 공기청정 켜짐 (공조 전원 연동)" else "실내 공기청정 꺼짐"
            )
        }
    }

    /**
     * Explicit 3-Way Lighting Control Mode (`AUTO_MUSIC` / `FIXED` / `OFF`) (`vehicle.getLight()`).
     */
    fun setLightingControlMode(mode: LightingControlMode) {
        val cur = _twinState.value
        val cap = if (cur.ecoStageModeActive) ECO_MAX_AMBIENT_BRIGHTNESS else 100
        val savedFixed = cur.fixedAmbientBrightness.coerceIn(10, 100)
        val shouldTurnOn = mode != LightingControlMode.OFF
        val targetBrightness = when (mode) {
            LightingControlMode.OFF -> 0
            LightingControlMode.FIXED -> savedFixed.coerceIn(10, cap)
            LightingControlMode.AUTO_MUSIC -> savedFixed.coerceIn(25, cap)
        }
        lastAppliedSdkBrightness = targetBrightness

        try {
            val light = vehicle.getLight()
            for (area in resolveLightAreas(ambientSwitchAreas)) {
                light.setAmbientLightSwitch(
                    area,
                    if (shouldTurnOn) LightSwitch.ON else LightSwitch.OFF,
                    { _, _ -> markSdkServiceConnected() },
                    { _ -> }
                )
            }
            for (area in resolveLightAreas(ambientBrightnessAreas)) {
                light.setAmbientLightBrightness(
                    area,
                    targetBrightness,
                    { _, _ -> markSdkServiceConnected() },
                    { _ -> }
                )
            }
            if (shouldTurnOn) {
                light.setCabinLightSwitch(LightSwitch.OFF, { _ -> markSdkServiceConnected() }, { _ -> })
                for (area in resolveLightAreas(readingLightAreas)) {
                    light.setReadingLightSwitch(area, LightSwitch.OFF, { _ -> markSdkServiceConnected() }, { _ -> })
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "setLightingControlMode fallback: ${t.message}")
        }

        _twinState.update {
            it.copy(
                lightingControlMode = mode,
                ambientLightEnabled = shouldTurnOn,
                ambientBrightness = targetBrightness,
                strobeActive = if (mode == LightingControlMode.AUTO_MUSIC) it.strobeActive else false,
                cabinLightOffForStage = if (shouldTurnOn) true else it.cabinLightOffForStage,
                readingLightOffForStage = if (shouldTurnOn) true else it.readingLightOffForStage,
                lastSdkCommandLog = when (mode) {
                    LightingControlMode.AUTO_MUSIC -> "실내 무드 조명 자동조정(음악 연동) 켜짐 (${targetBrightness}%)"
                    LightingControlMode.FIXED -> "실내 무드 조명 밝기 고정 (${targetBrightness}%)"
                    LightingControlMode.OFF -> "실내 무드 조명 꺼짐 (0%)"
                }
            )
        }
    }

    fun cycleLightingControlMode() {
        val nextMode = when (_twinState.value.lightingControlMode) {
            LightingControlMode.AUTO_MUSIC -> LightingControlMode.FIXED
            LightingControlMode.FIXED -> LightingControlMode.OFF
            LightingControlMode.OFF -> LightingControlMode.AUTO_MUSIC
        }
        setLightingControlMode(nextMode)
    }

    /**
     * Ambient Light & Stage Cabin/Reading Light Auto-Off Toggle (`vehicle.getLight()`)
     */
    fun toggleAmbientLightManually() {
        cycleLightingControlMode()
    }

    /**
     * Direct slider or [-]/[+] brightness manipulation:
     * - If `brightness <= 0`: immediately enters `LightingControlMode.OFF` (`0%`).
     * - Otherwise: immediately enters `LightingControlMode.FIXED` locked at `10% ~ 100%`!
     */
    fun setAmbientBrightnessManually(brightness: Int) {
        if (brightness <= 0) {
            setLightingControlMode(LightingControlMode.OFF)
            return
        }
        val rawFixed = brightness.coerceIn(10, 100)
        val cap = if (_twinState.value.ecoStageModeActive) ECO_MAX_AMBIENT_BRIGHTNESS else 100
        val safeBrightness = rawFixed.coerceIn(10, cap)
        lastAppliedSdkBrightness = safeBrightness
        try {
            val light = vehicle.getLight()
            for (area in resolveLightAreas(ambientSwitchAreas)) {
                light.setAmbientLightSwitch(
                    area,
                    LightSwitch.ON,
                    { _, _ -> markSdkServiceConnected() },
                    { _ -> }
                )
            }
            for (area in resolveLightAreas(ambientBrightnessAreas)) {
                light.setAmbientLightBrightness(
                    area,
                    safeBrightness,
                    { _, _ -> markSdkServiceConnected() },
                    { _ -> }
                )
            }
        } catch (t: Throwable) {
            Log.d(TAG, "setAmbientBrightnessManually fallback: ${t.message}")
        }

        _twinState.update {
            it.copy(
                lightingControlMode = LightingControlMode.FIXED,
                fixedAmbientBrightness = rawFixed,
                ambientLightEnabled = true,
                ambientBrightness = safeBrightness,
                strobeActive = false,
                lastSdkCommandLog = "실내 무드 조명 밝기 고정 ${safeBrightness}% 설정"
            )
        }
    }

    /**
     * HVAC Temperature & Fan Speed & Seat Ventilation Direct Controls (`vehicle.getHVAC()`, `vehicle.getSeat()`)
     */
    fun adjustHvacTemperature(deltaCelsius: Float) {
        val nextTemp = ((_twinState.value.hvacTempCelsius + deltaCelsius) * 2f).roundToInt() / 2f
        val safeTemp = nextTemp.coerceIn(hvacMinTempC, hvacMaxTempC)
        try {
            ensureHvacMasterPowerOn()
            val hvac = vehicle.getHVAC()
            for (area in resolveHvacAreas(hvacAcAreas)) {
                hvac.setHvacAc(area, true, { _, _ -> markSdkServiceConnected() }, { _ -> })
            }
            for (area in resolveHvacAreas(hvacTempAreas)) {
                hvac.setHvacTemperature(
                    area,
                    safeTemp,
                    TemperatureUnit.CELSIUS,
                    { _, _ -> markSdkServiceConnected() },
                    { _ -> }
                )
            }
        } catch (t: Throwable) {
            Log.d(TAG, "adjustHvacTemperature fallback: ${t.message}")
        }

        _twinState.update {
            it.copy(
                hvacPowerOn = true,
                hvacAcOn = true,
                hvacTempCelsius = safeTemp,
                lastSdkCommandLog = "에어컨 희망 온도 ${safeTemp}°C 변경 (공조 전원 켜짐)"
            )
        }
    }

    fun adjustHvacFanSpeed(deltaLevel: Int) {
        val safeFan = (_twinState.value.hvacFanLevel + deltaLevel).coerceIn(hvacMinFan, hvacMaxFan)
        try {
            ensureHvacMasterPowerOn()
            val hvac = vehicle.getHVAC()
            for (area in resolveHvacAreas(hvacFanAreas)) {
                hvac.setHvacFanSpeed(
                    area,
                    safeFan,
                    { _, _ -> markSdkServiceConnected() },
                    { _ -> }
                )
            }
        } catch (t: Throwable) {
            Log.d(TAG, "adjustHvacFanSpeed fallback: ${t.message}")
        }

        _twinState.update {
            it.copy(
                hvacPowerOn = true,
                hvacFanLevel = safeFan,
                lastSdkCommandLog = "에어컨 바람 세기 ${safeFan}단 변경 (공조 전원 켜짐)"
            )
        }
    }

    fun cycleOccupiedSeatVentilationLevel() {
        val nextLevel = (_twinState.value.seatVentilationLevel + 1) % (seatMaxVentLevel + 1)
        applySmartOccupiedSeatVentilation(nextLevel)
    }

    /**
     * Allows toggling a seat's occupancy state in UI for quick verification alongside Virtual Console's `SeatOccupancy` controls.
     */
    fun toggleSeatOccupancyForStage(seatIndex: Int) {
        val s = _twinState.value
        val current = when (seatIndex) {
            0 -> s.seatOccupiedFL
            1 -> s.seatOccupiedFR
            2 -> s.seatOccupiedRL
            else -> s.seatOccupiedRR
        }
        handleSeatOccupancyUpdated(seatIndex, !current)
    }

    /**
     * Feature #4: EV Battery SOC Monitoring (`EvBattery`) & `[배터리 보호 에코 스테이지]` Auto Power-Saving Mode
     */
    fun applyEvBatterySocUpdate(socPercent: Int) {
        val safeSoc = socPercent.coerceIn(0, 100)
        val isLowBattery = safeSoc <= ECO_BATTERY_THRESHOLD_PERCENT
        val prevEco = _twinState.value.ecoStageModeActive

        _twinState.update {
            it.copy(
                evBatterySocPercent = safeSoc,
                ecoBatteryLowTriggered = isLowBattery,
                ecoStageModeActive = isLowBattery,
                lastSdkCommandLog = if (isLowBattery) {
                    "배터리 잔량 ${safeSoc}% 감지 · 절약 무대 모드 자동 작동"
                } else {
                    "차량 배터리 잔량 ${safeSoc}% 연동 중"
                }
            )
        }

        if (isLowBattery && !prevEco) {
            activateEcoStagePowerSaving(reason = "배터리 잔량 ${safeSoc}% 보호 절전")
        }
    }

    fun toggleEcoStageModeManually() {
        val nextEco = !_twinState.value.ecoStageModeActive
        _twinState.update {
            it.copy(
                ecoStageModeActive = nextEco,
                lastSdkCommandLog = if (nextEco) "배터리 절약 무대 모드 켜짐" else "배터리 절약 무대 모드 해제"
            )
        }
        if (nextEco) {
            activateEcoStagePowerSaving(reason = "배터리 절약 무대 모드 켜짐")
        } else {
            // Restore brightness according to current lightingControlMode and normal HVAC fan speed (3단)
            setLightingControlMode(_twinState.value.lightingControlMode)
            try {
                ensureHvacMasterPowerOn()
                val hvac = vehicle.getHVAC()
                for (area in resolveHvacAreas(hvacAcAreas)) {
                    hvac.setHvacAc(area, true, { _, _ -> markSdkServiceConnected() }, { _ -> })
                }
                val restoredFan = 3.coerceIn(hvacMinFan, hvacMaxFan)
                for (area in resolveHvacAreas(hvacFanAreas)) {
                    hvac.setHvacFanSpeed(area, restoredFan, { _, _ -> markSdkServiceConnected() }, { _ -> })
                }
                _twinState.update {
                    it.copy(
                        hvacPowerOn = true,
                        hvacAcOn = true,
                        hvacFanLevel = restoredFan,
                        lastSdkCommandLog = "일반 무대 연출 복원 (조명 ${it.lightingControlMode.label} ${it.ambientBrightness}% · 바람 ${restoredFan}단)"
                    )
                }
            } catch (t: Throwable) {
                Log.d(TAG, "toggleEcoStageModeManually restore fallback: ${t.message}")
            }
        }
    }

    private fun activateEcoStagePowerSaving(reason: String) {
        val curMode = _twinState.value.lightingControlMode
        val isOff = curMode == LightingControlMode.OFF
        val ecoBrightness = if (isOff) 0 else ECO_MAX_AMBIENT_BRIGHTNESS // Strictly 30% power-saving stage brightness (or 0% if OFF)
        val ecoFan = 1.coerceIn(hvacMinFan, hvacMaxFan) // Strictly 1단 power-saving fan speed
        lastAppliedSdkBrightness = ecoBrightness
        try {
            val light = vehicle.getLight()
            for (area in resolveLightAreas(ambientSwitchAreas)) {
                light.setAmbientLightSwitch(
                    area,
                    if (isOff) LightSwitch.OFF else LightSwitch.ON,
                    { _, _ -> markSdkServiceConnected() },
                    { _ -> }
                )
            }
            for (area in resolveLightAreas(ambientBrightnessAreas)) {
                light.setAmbientLightBrightness(
                    area,
                    ecoBrightness,
                    { _, _ -> markSdkServiceConnected() },
                    { _ -> }
                )
            }
            if (!isOff) {
                for (area in resolveLightAreas(ambientColorAreas)) {
                    light.setAmbientLightBrightnessColor(
                        area,
                        VocalZoneTheme.ECO_BATTERY_SAVER.primaryHex.toInt(),
                        { _, _ -> markSdkServiceConnected() },
                        { _ -> }
                    )
                }
            }

            // Turn ON HVAC Master Power with AC OFF + Fan 1단 so emulator bar shows 1단 Eco state immediately!
            ensureHvacMasterPowerOn()
            val hvac = vehicle.getHVAC()
            for (area in resolveHvacAreas(hvacAcAreas)) {
                hvac.setHvacAc(area, false, { _, _ -> markSdkServiceConnected() }, { _ -> })
            }
            for (area in resolveHvacAreas(hvacFanAreas)) {
                hvac.setHvacFanSpeed(area, ecoFan, { _, _ -> markSdkServiceConnected() }, { _ -> })
            }
            vehicle.getSeat().setHvacSeatVentilation(seatFL, 0, { _, _ -> }, { _ -> })
            vehicle.getSeat().setHvacSeatVentilation(seatFR, 0, { _, _ -> }, { _ -> })
            vehicle.getSeat().setHvacSeatVentilation(seatRL, 0, { _, _ -> }, { _ -> })
            vehicle.getSeat().setHvacSeatVentilation(seatRR, 0, { _, _ -> }, { _ -> })
        } catch (t: Throwable) {
            Log.d(TAG, "activateEcoStagePowerSaving fallback: ${t.message}")
        }

        _twinState.update {
            it.copy(
                ambientLightEnabled = !isOff,
                ambientBrightness = ecoBrightness,
                ambientColorHex = VocalZoneTheme.ECO_BATTERY_SAVER.primaryHex,
                vocalZoneTheme = VocalZoneTheme.ECO_BATTERY_SAVER,
                hvacPowerOn = true,
                hvacAcOn = false,
                hvacFanLevel = ecoFan,
                seatVentilationLevel = 0,
                seatVentilationFL = 0,
                seatVentilationFR = 0,
                seatVentilationRL = 0,
                seatVentilationRR = 0,
                strobeActive = false,
                lastSdkCommandLog = "$reason · 실내 조명 ${ecoBrightness}% · 공조 바람 ${ecoFan}단 절전 적용"
            )
        }
    }
}
