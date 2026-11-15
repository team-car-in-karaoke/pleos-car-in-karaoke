package io.github.teamcarinkaraoke.pleosstage.car.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.DrivingModeType
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.AmbientLightBrightnessColorListener
import ai.pleos.playground.vehicle.listener.AmbientLightBrightnessListener
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DoorLockStateListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.DrivingModeStateListener
import ai.pleos.playground.vehicle.listener.EtcsVoiceGuidanceVolumeListener
import ai.pleos.playground.vehicle.listener.EvBatteryLevelListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.EvChargePortOpenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.GloveBoxLockStateListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacAirCleanerStateListener
import ai.pleos.playground.vehicle.listener.HvacFanSpeedListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.HvacSeatVentilationListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SmartGlassLevelListener
import ai.pleos.playground.vehicle.listener.SmartGlassStateListener
import ai.pleos.playground.vehicle.listener.TemperatureDisplayUnitListener
import ai.pleos.playground.vehicle.listener.WindowLockStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.teamcarinkaraoke.pleosstage.car.sdk.PleosVehicleManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.reflect.Proxy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class SdkApiExecutionStatus(
    val isSuccess: Boolean,
    val actionLabel: String,
    val latencyMs: Long,
    val summaryText: String,
    val timestampStr: String
)

data class SdkConsoleLogEntry(
    val id: Long,
    val timestampStr: String,
    val tag: String,
    val apiName: String,
    val isSuccess: Boolean,
    val latencyMs: Long,
    val detail: String
)

private class SdkUnitTestApiSpec(
    val id: String,
    val domainId: String,
    val apiTitle: String,
    val methodSignature: String,
    val targetAreaDesc: String,
    val runCapability: ((onSuccess: (String) -> Unit, onFailure: (Throwable) -> Unit) -> Unit),
    val runGet: ((onSuccess: (String) -> Unit, onFailure: (Throwable) -> Unit) -> Unit),
    val runSet: ((toggleStep: Int, onSuccess: (String) -> Unit, onFailure: (Throwable) -> Unit) -> Unit),
    val startListening: ((onEvent: (String) -> Unit, onError: (Throwable) -> Unit) -> (() -> Unit))
)

private data class SdkDomainGroup(
    val id: String,
    val badgeTitle: String,
    val fullTitle: String,
    val subtitle: String,
    val items: List<SdkApiTestEntryHolder>
)

private class SdkApiTestEntryHolder(val spec: SdkApiExecutionSpecWrapper)
private typealias SdkApiExecutionSpecWrapper = SdkUnitTestApiSpec

private fun formatSdkException(t: Throwable): String {
    val errorCodeText = runCatching {
        val method = t.javaClass.methods.firstOrNull {
            (it.name == "getErrorCode" || it.name == "getCode") && it.parameterCount == 0
        }
        method?.invoke(t)?.toString()?.trim().orEmpty()
    }.getOrDefault("")
    val msg = t.message?.trim().orEmpty()
    val cls = t.javaClass.simpleName.ifBlank { "VehicleException" }
    return when {
        errorCodeText.isNotEmpty() && msg.isNotEmpty() && msg != errorCodeText && msg != "Error code: $errorCodeText" ->
            "$errorCodeText ($msg)"
        errorCodeText.isNotEmpty() -> errorCodeText
        msg.isNotEmpty() && (cls == "UnsupportedOperationException" || cls == "IllegalStateException") -> msg
        msg.isNotEmpty() -> "$cls: $msg"
        else -> cls
    }
}

private fun nowTimeStr(): String {
    return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
}

@SuppressLint("MissingPermission")
@Composable
fun PleosSdkUnitTestPane(
    vehicleManager: PleosVehicleManager,
    modifier: Modifier = Modifier,
    headerTrailingContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val coroutineScope = rememberCoroutineScope()

    val isSdkConnected by vehicleManager.isVehicleSdkConnected.collectAsState()
    val lastGleoIntent by vehicleManager.lastGleoIntentInfo.collectAsState()

    var totalCalls by remember { mutableIntStateOf(0) }
    var successCalls by remember { mutableIntStateOf(0) }
    var failedCalls by remember { mutableIntStateOf(0) }
    var isBatchRunning by remember { mutableStateOf(false) }
    var selectedDomainFilter by remember { mutableStateOf("ALL") }
    var activeTopViewTab by remember { mutableStateOf("CARDS") } // "CARDS" or "CONSOLE"
    var isBottomConsoleExpanded by remember { mutableStateOf(false) }

    val statusByApiId = remember { mutableStateMapOf<String, SdkApiExecutionStatus>() }
    val toggleCounterByApiId = remember { mutableStateMapOf<String, Int>() }
    val activeUnsubscribers = remember { mutableStateMapOf<String, () -> Unit>() }
    val consoleLogs = remember { mutableStateListOf<SdkConsoleLogEntry>() }
    var logSeq by remember { mutableStateOf(1L) }

    // Real Android TTS instance for Domain #9 (initialized on demand, zero dummy fallback)
    var ttsEngineRef by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReadyStatus by remember { mutableIntStateOf(TextToSpeech.ERROR) }

    fun pushLog(
        tag: String,
        apiName: String,
        isSuccess: Boolean,
        latencyMs: Long,
        detail: String
    ) {
        mainHandler.post {
            val ts = nowTimeStr()
            val entry = SdkConsoleLogEntry(
                id = logSeq++,
                timestampStr = ts,
                tag = tag,
                apiName = apiName,
                isSuccess = isSuccess,
                latencyMs = latencyMs,
                detail = detail
            )
            consoleLogs.add(0, entry)
            if (consoleLogs.size > 250) {
                consoleLogs.removeRange(250, consoleLogs.size)
            }
        }
    }

    fun executeTimedSdkAction(
        spec: SdkUnitTestApiSpec,
        actionTag: String,
        block: ((String) -> Unit, (Throwable) -> Unit) -> Unit
    ) {
        val startMs = SystemClock.elapsedRealtime()
        val completed = AtomicBoolean(false)

        // 2500ms timeout guard so unbound VHAL calls report real timeout failure rather than hanging silently
        mainHandler.postDelayed(
            {
                if (completed.compareAndSet(false, true)) {
                    val elapsed = SystemClock.elapsedRealtime() - startMs
                    totalCalls++
                    failedCalls++
                    val errText = "ERROR_TIMEOUT_NO_VHAL_RESPONSE (2500ms 내 응답 없음)"
                    statusByApiId[spec.id] = SdkApiExecutionStatus(
                        isSuccess = false,
                        actionLabel = actionTag,
                        latencyMs = elapsed,
                        summaryText = "❌ 호출 실패 (${elapsed}ms): $errText",
                        timestampStr = nowTimeStr()
                    )
                    pushLog(actionTag, spec.apiTitle, false, elapsed, errText)
                }
            },
            2500L
        )

        try {
            block(
                { valueStr ->
                    if (completed.compareAndSet(false, true)) {
                        val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(0L)
                        mainHandler.post {
                            vehicleManager.markSdkServiceConnected()
                            totalCalls++
                            successCalls++
                            statusByApiId[spec.id] = SdkApiExecutionStatus(
                                isSuccess = true,
                                actionLabel = actionTag,
                                latencyMs = elapsed,
                                summaryText = "✅ 정상 호출 (${elapsed}ms) · $valueStr",
                                timestampStr = nowTimeStr()
                            )
                            pushLog(actionTag, spec.apiTitle, true, elapsed, valueStr)
                        }
                    }
                },
                { throwable ->
                    if (completed.compareAndSet(false, true)) {
                        val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(0L)
                        val errFormatted = formatSdkException(throwable)
                        mainHandler.post {
                            totalCalls++
                            failedCalls++
                            statusByApiId[spec.id] = SdkApiExecutionStatus(
                                isSuccess = false,
                                actionLabel = actionTag,
                                latencyMs = elapsed,
                                summaryText = "❌ 호출 실패 (${elapsed}ms): $errFormatted",
                                timestampStr = nowTimeStr()
                            )
                            pushLog(actionTag, spec.apiTitle, false, elapsed, errFormatted)
                        }
                    }
                }
            )
        } catch (syncErr: Throwable) {
            if (completed.compareAndSet(false, true)) {
                val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(0L)
                val errFormatted = formatSdkException(syncErr)
                mainHandler.post {
                    totalCalls++
                    failedCalls++
                    statusByApiId[spec.id] = SdkApiExecutionStatus(
                        isSuccess = false,
                        actionLabel = actionTag,
                        latencyMs = elapsed,
                        summaryText = "❌ 호출 실패 (${elapsed}ms): $errFormatted",
                        timestampStr = nowTimeStr()
                    )
                    pushLog(actionTag, spec.apiTitle, false, elapsed, errFormatted)
                }
            }
        }
    }

    val domainGroups = remember(vehicleManager) {
        buildAllPleosDomainSpecs(
            context = context,
            vehicleManager = vehicleManager,
            getLastGleoIntent = { vehicleManager.lastGleoIntentInfo.value },
            getOrCreateTts = { onResult ->
                val existing = ttsEngineRef
                if (existing != null && ttsReadyStatus == TextToSpeech.SUCCESS) {
                    onResult(existing, TextToSpeech.SUCCESS)
                } else {
                    var created: TextToSpeech? = null
                    created = TextToSpeech(context.applicationContext) { status ->
                        ttsReadyStatus = status
                        ttsEngineRef = created
                        onResult(created, status)
                    }
                }
            },
            coroutineScopeProvider = { coroutineScope }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            activeUnsubscribers.values.forEach { unsub ->
                try {
                    unsub()
                } catch (_: Throwable) {
                }
            }
            activeUnsubscribers.clear()
            try {
                ttsEngineRef?.stop()
                ttsEngineRef?.shutdown()
                ttsEngineRef = null
            } catch (_: Throwable) {
            }
        }
    }

    val displayedDomains = remember(selectedDomainFilter, domainGroups) {
        if (selectedDomainFilter == "ALL") {
            domainGroups
        } else {
            domainGroups.filter { it.id == selectedDomainFilter }
        }
    }

    fun runAllCapabilitiesAndGets() {
        if (isBatchRunning) return
        isBatchRunning = true
        coroutineScope.launch {
            pushLog("BATCH", "Run All Tests (11 Domains)", true, 0L, "전체 도메인 Capability + Get 순차 일괄 진단 시작")
            for (group in domainGroups) {
                for (holder in group.items) {
                    val spec = holder.spec
                    executeTimedSdkAction(spec, "CAP", spec.runCapability)
                    delay(45L)
                    executeTimedSdkAction(spec, "GET", spec.runGet)
                    delay(45L)
                }
            }
            isBatchRunning = false
            pushLog("BATCH", "Run All Tests 완료", true, 0L, "일괄 진단 완료 (총 ${totalCalls}건 호출)")
        }
    }

    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density * 1.25f,
            fontScale = 1.35f
        )
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFF3F6FA))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Unified Ultra-High-Visibility Top Summary Bar
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Row 1: Title + SDK Status Badge + Reconnect + DEV/PROD Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "🧪 Pleos Vehicle SDK 단위 테스트",
                                fontSize = 25.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A),
                                maxLines = 1
                            )

                            Row(
                                modifier = Modifier
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (isSdkConnected) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                                    )
                                    .border(
                                        1.5.dp,
                                        if (isSdkConnected) Color(0xFF10B981) else Color(0xFFEF4444),
                                        RoundedCornerShape(999.dp)
                                    )
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(11.dp)
                                        .clip(CircleShape)
                                        .background(if (isSdkConnected) Color(0xFF059669) else Color(0xFFDC2626))
                                )
                                Text(
                                    text = if (isSdkConnected) "SDK CONNECTED" else "SDK DISCONNECTED",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSdkConnected) Color(0xFF065F46) else Color(0xFF991B1B),
                                    maxLines = 1
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .border(1.5.dp, Color(0xFF94A3B8), RoundedCornerShape(12.dp))
                                    .clickable {
                                        vehicleManager.reinitializeVehicleSdk()
                                        pushLog("INIT", "Vehicle.initialize()", true, 0L, "수동 SDK 재초기화 호출됨")
                                    }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "SDK 재연결",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1
                                )
                            }
                        }

                        if (headerTrailingContent != null) {
                            Spacer(modifier = Modifier.width(10.dp))
                            headerTrailingContent()
                        }
                    }

                    // Header Row 2: Top View Tabs (Cards vs Full Console) + Counters + Run All Tests
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top View Switcher: 100% Width API Cards vs Full-Screen Log Console
                        Row(
                            modifier = Modifier
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE2E8F0))
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (activeTopViewTab == "CARDS") Color(0xFF0F172A) else Color.Transparent)
                                    .clickable { activeTopViewTab = "CARDS" }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "📋 API 테스트 카드",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (activeTopViewTab == "CARDS") Color.White else Color(0xFF1E293B),
                                    maxLines = 1
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (activeTopViewTab == "CONSOLE") Color(0xFF0F172A) else Color.Transparent)
                                    .clickable { activeTopViewTab = "CONSOLE" }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🖥️ 실시간 로그 콘솔 (${consoleLogs.size})",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (activeTopViewTab == "CONSOLE") Color(0xFF4ADE80) else Color(0xFF1E293B),
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SdkCounterBadge(
                                label = "호출",
                                count = totalCalls,
                                bgColor = Color(0xFFF1F5F9),
                                borderColor = Color(0xFF94A3B8),
                                textColor = Color(0xFF0F172A)
                            )
                            SdkCounterBadge(
                                label = "성공",
                                count = successCalls,
                                bgColor = Color(0xFFD1FAE5),
                                borderColor = Color(0xFF10B981),
                                textColor = Color(0xFF065F46)
                            )
                            SdkCounterBadge(
                                label = "실패",
                                count = failedCalls,
                                bgColor = Color(0xFFFEE2E2),
                                borderColor = Color(0xFFEF4444),
                                textColor = Color(0xFF991B1B)
                            )

                            Box(
                                modifier = Modifier
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isBatchRunning) Color(0xFF475569) else Color(0xFF1D4ED8))
                                    .clickable(enabled = !isBatchRunning) {
                                        runAllCapabilitiesAndGets()
                                    }
                                    .padding(horizontal = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isBatchRunning) "⏳ 일괄 진단 중..." else "▶ 전체 API 일괄 진단",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            if (activeTopViewTab == "CONSOLE") {
                // Full-Width & Full-Height High-Contrast SDK Call Log Console Tab
                SdkLiveConsoleSurface(
                    consoleLogs = consoleLogs,
                    onClearLogs = {
                        consoleLogs.clear()
                        totalCalls = 0
                        successCalls = 0
                        failedCalls = 0
                    },
                    trailingActionLabel = "📋 테스트 카드로 돌아가기",
                    onTrailingAction = { activeTopViewTab = "CARDS" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                // 2. Domain Filter Strip (Height 48.dp, 18.sp Bold High-Contrast)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SdkFilterChip(
                        text = "전체 도메인 (${domainGroups.size})",
                        selected = selectedDomainFilter == "ALL",
                        onClick = { selectedDomainFilter = "ALL" }
                    )
                    domainGroups.forEach { grp ->
                        SdkFilterChip(
                            text = grp.badgeTitle,
                            selected = selectedDomainFilter == grp.id,
                            onClick = { selectedDomainFilter = grp.id }
                        )
                    }
                }

                // 3. 100% Full-Width Domain Unit Test Cards
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(displayedDomains, key = { it.id }) { domain ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(18.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = domain.fullTitle,
                                            fontSize = 25.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = domain.subtitle,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF334155)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "${domain.items.size}개 API",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF1D4ED8),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(Color(0xFFDBEAFE))
                                            .border(1.5.dp, Color(0xFF60A5FA), RoundedCornerShape(999.dp))
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }

                                domain.items.forEach { holder ->
                                    val spec = holder.spec
                                    val lastStatus = statusByApiId[spec.id]
                                    val isListening = activeUnsubscribers.containsKey(spec.id)

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFFF8FAFC))
                                            .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(14.dp))
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = spec.apiTitle,
                                                    fontSize = 21.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFF0F172A)
                                                )
                                                Text(
                                                    text = "${spec.methodSignature}  ·  영역: ${spec.targetAreaDesc}",
                                                    fontSize = 18.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF334155),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (isListening) {
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "● 실시간 구독 중",
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .background(Color(0xFF7C3AED))
                                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                                )
                                            }
                                        }

                                        // 4 Action Buttons Across 100% Card Width (Height 54.dp, 18.sp ExtraBold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            SdkActionButton(
                                                label = "Capability 확인",
                                                bgColor = Color(0xFFEFF6FF),
                                                borderColor = Color(0xFF3B82F6),
                                                textColor = Color(0xFF1D4ED8),
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    executeTimedSdkAction(spec, "CAP", spec.runCapability)
                                                }
                                            )
                                            SdkActionButton(
                                                label = "Get 조회",
                                                bgColor = Color(0xFFECFDF5),
                                                borderColor = Color(0xFF10B981),
                                                textColor = Color(0xFF047857),
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    executeTimedSdkAction(spec, "GET", spec.runGet)
                                                }
                                            )
                                            SdkActionButton(
                                                label = "Set 제어 호출",
                                                bgColor = Color(0xFFFFFBEB),
                                                borderColor = Color(0xFFF59E0B),
                                                textColor = Color(0xFFB45309),
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    val nextStep = (toggleCounterByApiId[spec.id] ?: 0) + 1
                                                    toggleCounterByApiId[spec.id] = nextStep
                                                    executeTimedSdkAction(spec, "SET") { ok, fail ->
                                                        spec.runSet(nextStep, ok, fail)
                                                    }
                                                }
                                            )
                                            SdkActionButton(
                                                label = if (isListening) "● Listener 구독: ON" else "Listener 구독: OFF",
                                                bgColor = if (isListening) Color(0xFF7C3AED) else Color(0xFFF5F3FF),
                                                borderColor = if (isListening) Color(0xFF6D28D9) else Color(0xFF8B5CF6),
                                                textColor = if (isListening) Color.White else Color(0xFF5B21B6),
                                                modifier = Modifier.weight(1.15f),
                                                onClick = {
                                                    val existingUnsub = activeUnsubscribers.remove(spec.id)
                                                    if (existingUnsub != null) {
                                                        try {
                                                            existingUnsub()
                                                            statusByApiId[spec.id] = SdkApiExecutionStatus(
                                                                isSuccess = true,
                                                                actionLabel = "UNSUB",
                                                                latencyMs = 0L,
                                                                summaryText = "✅ Listener 구독 해제 완료",
                                                                timestampStr = nowTimeStr()
                                                            )
                                                            pushLog("UNSUB", spec.apiTitle, true, 0L, "unregisterXxx 호출 완료")
                                                        } catch (e: Throwable) {
                                                            val err = formatSdkException(e)
                                                            pushLog("UNSUB", spec.apiTitle, false, 0L, err)
                                                        }
                                                    } else {
                                                        val startMs = SystemClock.elapsedRealtime()
                                                        try {
                                                            val unsub = spec.startListening(
                                                                { eventPayload ->
                                                                    mainHandler.post {
                                                                        vehicleManager.markSdkServiceConnected()
                                                                        statusByApiId[spec.id] = SdkApiExecutionStatus(
                                                                            isSuccess = true,
                                                                            actionLabel = "EVENT",
                                                                            latencyMs = 0L,
                                                                            summaryText = "✅ 실시간 이벤트 수신 · $eventPayload",
                                                                            timestampStr = nowTimeStr()
                                                                        )
                                                                        pushLog("EVENT", spec.apiTitle, true, 0L, eventPayload)
                                                                    }
                                                                },
                                                                { err ->
                                                                    val errStr = formatSdkException(err)
                                                                    mainHandler.post {
                                                                        totalCalls++
                                                                        failedCalls++
                                                                        statusByApiId[spec.id] = SdkApiExecutionStatus(
                                                                            isSuccess = false,
                                                                            actionLabel = "EVENT_ERR",
                                                                            latencyMs = 0L,
                                                                            summaryText = "❌ Listener onFailed: $errStr",
                                                                            timestampStr = nowTimeStr()
                                                                        )
                                                                        pushLog("EVENT_ERR", spec.apiTitle, false, 0L, errStr)
                                                                    }
                                                                }
                                                            )
                                                            val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(0L)
                                                            activeUnsubscribers[spec.id] = unsub
                                                            totalCalls++
                                                            successCalls++
                                                            statusByApiId[spec.id] = SdkApiExecutionStatus(
                                                                isSuccess = true,
                                                                actionLabel = "SUB",
                                                                latencyMs = elapsed,
                                                                summaryText = "✅ 정상 호출 (${elapsed}ms) · Listener 구독 시작됨",
                                                                timestampStr = nowTimeStr()
                                                            )
                                                            pushLog("SUB", spec.apiTitle, true, elapsed, "registerXxx 구독 활성화")
                                                        } catch (e: Throwable) {
                                                            val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(0L)
                                                            val err = formatSdkException(e)
                                                            totalCalls++
                                                            failedCalls++
                                                            statusByApiId[spec.id] = SdkApiExecutionStatus(
                                                                isSuccess = false,
                                                                actionLabel = "SUB",
                                                                latencyMs = elapsed,
                                                                summaryText = "❌ 호출 실패 (${elapsed}ms): $err",
                                                                timestampStr = nowTimeStr()
                                                            )
                                                            pushLog("SUB", spec.apiTitle, false, elapsed, err)
                                                        }
                                                    }
                                                }
                                            )
                                        }

                                        // High-Contrast Call Result Box (18.sp Bold, LineHeight 26.sp)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    when {
                                                        lastStatus == null -> Color(0xFFF1F5F9)
                                                        lastStatus.isSuccess -> Color(0xFFD1FAE5)
                                                        else -> Color(0xFFFEE2E2)
                                                    }
                                                )
                                                .border(
                                                    1.5.dp,
                                                    when {
                                                        lastStatus == null -> Color(0xFFCBD5E1)
                                                        lastStatus.isSuccess -> Color(0xFF10B981)
                                                        else -> Color(0xFFEF4444)
                                                    },
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .padding(horizontal = 16.dp, vertical = 13.dp)
                                        ) {
                                            Text(
                                                text = if (lastStatus == null) {
                                                    if (spec.domainId == "GLEO") {
                                                        "대기 중 · 버튼을 눌러 실제 SDK 응답을 확인하세요 (Gleo 인텐트: $lastGleoIntent)"
                                                    } else {
                                                        "대기 중 · 위 버튼을 눌러 실제 Pleos SDK 응답을 확인하세요"
                                                    }
                                                } else {
                                                    "[${lastStatus.timestampStr} · ${lastStatus.actionLabel}] ${lastStatus.summaryText}"
                                                },
                                                fontSize = 18.sp,
                                                lineHeight = 26.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    lastStatus == null -> Color(0xFF334155)
                                                    lastStatus.isSuccess -> Color(0xFF065F46)
                                                    else -> Color(0xFF991B1B)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Bottom Expandable Live SDK Log Bar / Drawer (100% Width)
                val latestLog = consoleLogs.firstOrNull()
                if (isBottomConsoleExpanded) {
                    SdkLiveConsoleSurface(
                        consoleLogs = consoleLogs,
                        onClearLogs = {
                            consoleLogs.clear()
                            totalCalls = 0
                            successCalls = 0
                            failedCalls = 0
                        },
                        trailingActionLabel = "▼ 하단 로그 접기",
                        onTrailingAction = { isBottomConsoleExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F172A),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
                            .clickable { isBottomConsoleExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (latestLog == null) {
                                    "🖥️ 실시간 SDK 호출 로그 대기 중 · 클릭하면 하단 로그 창이 펼쳐집니다"
                                } else if (latestLog.isSuccess) {
                                    "✅ [${latestLog.timestampStr} · ${latestLog.tag}] ${latestLog.apiName} (${latestLog.latencyMs}ms) → ${latestLog.detail}"
                                } else {
                                    "❌ [${latestLog.timestampStr} · ${latestLog.tag}] ${latestLog.apiName} → ${latestLog.detail}"
                                },
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    latestLog == null -> Color(0xFFF1F5F9)
                                    latestLog.isSuccess -> Color(0xFF4ADE80)
                                    else -> Color(0xFFFCA5A5)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(1.2.dp, Color(0xFF64748B), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "▲ 로그 펼치기 (${consoleLogs.size}건)",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFF1F5F9),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SdkLiveConsoleSurface(
    consoleLogs: List<SdkConsoleLogEntry>,
    onClearLogs: () -> Unit,
    trailingActionLabel: String,
    onTrailingAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0F172A),
        shadowElevation = 3.dp,
        modifier = modifier.border(1.5.dp, Color(0xFF334155), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "실시간 SDK 호출 로그 콘솔 (${consoleLogs.size}건)",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF8FAFC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "응답 지연시간(ms) · 실제 반환 데이터 · VehicleException 에러코드",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFCBD5E1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.5.dp, Color(0xFF64748B), RoundedCornerShape(10.dp))
                            .clickable { onClearLogs() }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "로그 초기화",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF1F5F9),
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2563EB))
                            .clickable { onTrailingAction() }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = trailingActionLabel,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
            }

            if (consoleLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF090E1A))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "아직 실행된 SDK 호출이 없습니다.\nAPI 테스트 카드의 버튼 또는 [▶ 전체 API 일괄 진단]을 클릭하면 실제 Vehicle SDK 응답이 실시간으로 기록됩니다.",
                        fontSize = 18.sp,
                        lineHeight = 27.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF1F5F9)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF090E1A))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(consoleLogs, key = { it.id }) { log ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (log.isSuccess) Color(0xFF0A281E) else Color(0xFF2B1218)
                                )
                                .border(
                                    1.2.dp,
                                    if (log.isSuccess) Color(0xFF15803D) else Color(0xFF991B1B),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${log.timestampStr} [${log.tag}] ${log.apiName}",
                                    fontSize = 18.sp,
                                    lineHeight = 25.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFF1F5F9),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${log.latencyMs}ms",
                                    fontSize = 17.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF1F5F9)
                                )
                            }
                            Text(
                                text = if (log.isSuccess) {
                                    "✅ 정상 호출 (${log.latencyMs}ms) -> ${log.detail}"
                                } else {
                                    "❌ 호출 실패: ${log.detail}"
                                },
                                fontSize = 18.sp,
                                lineHeight = 26.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (log.isSuccess) Color(0xFF4ADE80) else Color(0xFFFCA5A5)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SdkCounterBadge(
    label: String,
    count: Int,
    bgColor: Color,
    borderColor: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1)
        Text(text = "$count", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = textColor, maxLines = 1)
    }
}

@Composable
private fun SdkFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color(0xFF0F172A) else Color.White)
            .border(
                1.5.dp,
                if (selected) Color(0xFF0F172A) else Color(0xFF94A3B8),
                RoundedCornerShape(999.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else Color(0xFF0F172A),
            maxLines = 1
        )
    }
}

@Composable
private fun SdkActionButton(
    label: String,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.8.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@SuppressLint("MissingPermission")
private fun buildAllPleosDomainSpecs(
    context: Context,
    vehicleManager: PleosVehicleManager,
    getLastGleoIntent: () -> String,
    getOrCreateTts: (((TextToSpeech?, Int) -> Unit) -> Unit),
    coroutineScopeProvider: () -> kotlinx.coroutines.CoroutineScope
): List<SdkDomainGroup> {
    val vehicle: Vehicle = vehicleManager.getVehicleSdkInstance()
    val allCabinSeats = SeatArea(
        SeatValue.ROW_1_LEFT,
        SeatValue.ROW_1_RIGHT,
        SeatValue.ROW_2_LEFT,
        SeatValue.ROW_2_RIGHT
    )
    val frontSeats = SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT)
    val seatFL = SeatArea(SeatValue.ROW_1_LEFT)
    val windowFL = WindowArea(WindowValue.ROW_1_LEFT)
    val allSideWindows = WindowArea(
        WindowValue.ROW_1_LEFT,
        WindowValue.ROW_1_RIGHT,
        WindowValue.ROW_2_LEFT,
        WindowValue.ROW_2_RIGHT
    )
    val doorFL = DoorArea(DoorValue.ROW_1_LEFT)

    fun readOnlySetError(propName: String): UnsupportedOperationException {
        return UnsupportedOperationException("ERROR_READ_ONLY_PROPERTY ($propName 는 하드웨어 읽기 전용 센서입니다)")
    }

    // 1. Light Domain
    val lightItems = listOf(
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "light_ambient_switch",
                domainId = "LIGHT",
                apiTitle = "AmbientLightSwitch (무드조명 전원)",
                methodSignature = "Light.get/setAmbientLightSwitch",
                targetAreaDesc = "SeatArea(ALL_CABIN)",
                runCapability = { ok, fail ->
                    vehicle.getLight().checkAmbientLightSwitchCapabilities(
                        { cfg, map -> ok("access=${cfg.accessType}, areas=${map.keys.size}개") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getLight().getAmbientLightSwitch(
                        allCabinSeats,
                        { sw -> ok("AmbientLightSwitch=$sw") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val next = if (step % 2 == 1) LightSwitch.ON else LightSwitch.OFF
                    vehicle.getLight().setAmbientLightSwitch(
                        allCabinSeats,
                        next,
                        { _, res -> ok("setAmbientLightSwitch($next) -> $res") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : LightStateListener {
                        override fun onLightStateUpdated(state: LightState?) = onEvent("AmbientLightState=$state")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getLight().registerAmbientLightStateListener(allCabinSeats, listener)
                    ({ vehicle.getLight().unregisterAmbientLightStateListener(allCabinSeats, listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "light_ambient_brightness",
                domainId = "LIGHT",
                apiTitle = "AmbientLightBrightness (무드조명 밝기)",
                methodSignature = "Light.get/setAmbientLightBrightness",
                targetAreaDesc = "SeatArea(ALL_CABIN)",
                runCapability = { ok, fail ->
                    vehicle.getLight().checkAmbientLightBrightnessCapabilities(
                        { cfg, map ->
                            val b = map.values.firstOrNull()
                            ok("access=${cfg.accessType}, range=${b?.minValue}..${b?.maxValue}")
                        },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getLight().getAmbientLightBrightness(
                        allCabinSeats,
                        { b -> ok("Brightness=${b}%") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val target = if (step % 2 == 1) 90 else 40
                    vehicle.getLight().setAmbientLightBrightness(
                        allCabinSeats,
                        target,
                        { _, v -> ok("setAmbientLightBrightness($target) -> $v%") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : AmbientLightBrightnessListener {
                        override fun onBrightnessUpdated(brightness: Int?) = onEvent("Brightness=${brightness}%")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getLight().registerAmbientLightBrightnessListener(allCabinSeats, listener)
                    ({ vehicle.getLight().unregisterAmbientLightBrightnessListener(allCabinSeats, listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "light_ambient_color",
                domainId = "LIGHT",
                apiTitle = "AmbientLightBrightnessColor (무드조명 ARGB 컬러)",
                methodSignature = "Light.get/setAmbientLightBrightnessColor",
                targetAreaDesc = "SeatArea(ALL_CABIN)",
                runCapability = { ok, fail ->
                    vehicle.getLight().checkAmbientLightBrightnessColorCapabilities(
                        { cfg, areas -> ok("access=${cfg.accessType}, areas=${areas.size}개") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getLight().getAmbientLightBrightnessColor(
                        allCabinSeats,
                        { c ->
                            val hex = c?.let { String.format(Locale.US, "0x%08X", it) } ?: "null"
                            ok("ARGB=$hex")
                        },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val colors = intArrayOf(0xFF00E5FF.toInt(), 0xFFFF2E93.toInt(), 0xFFFFD600.toInt())
                    val target = colors[step % colors.size]
                    vehicle.getLight().setAmbientLightBrightnessColor(
                        allCabinSeats,
                        target,
                        { _, v -> ok(String.format(Locale.US, "setColor -> 0x%08X", v)) },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : AmbientLightBrightnessColorListener {
                        override fun onBrightnessColorUpdated(argbColor: Int?) {
                            val hex = argbColor?.let { String.format(Locale.US, "0x%08X", it) } ?: "null"
                            onEvent("ARGB=$hex")
                        }
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getLight().registerAmbientLightBrightnessColorListener(allCabinSeats, listener)
                    ({ vehicle.getLight().unregisterAmbientLightBrightnessColorListener(allCabinSeats, listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "light_cabin_switch",
                domainId = "LIGHT",
                apiTitle = "CabinLightSwitch (실내 중앙등)",
                methodSignature = "Light.get/setCabinLightSwitch",
                targetAreaDesc = "GlobalArea",
                runCapability = { ok, fail ->
                    vehicle.getLight().checkCabinLightSwitchCapabilities(
                        { cfg, list -> ok("access=${cfg.accessType}, supported=$list") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getLight().getCabinLightSwitch(
                        { sw -> ok("CabinLightSwitch=$sw") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val target = if (step % 2 == 1) LightSwitch.ON else LightSwitch.OFF
                    vehicle.getLight().setCabinLightSwitch(
                        target,
                        { res -> ok("setCabinLightSwitch($target) -> $res") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : LightStateListener {
                        override fun onLightStateUpdated(state: LightState?) = onEvent("CabinLightState=$state")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getLight().registerCabinLightState(listener)
                    ({ vehicle.getLight().unregisterCabinLightState(listener) })
                }
            )
        )
    )

    // 2. Window & SmartGlass Domain
    val windowItems = listOf(
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "window_position_fl",
                domainId = "WINDOW",
                apiTitle = "WindowPosition (운전석 창문 개폐율)",
                methodSignature = "Window.getCurrent/setWindowPosition",
                targetAreaDesc = "WindowArea(ROW_1_LEFT)",
                runCapability = { ok, fail ->
                    vehicle.getWindow().checkWindowPositionCapabilities(
                        { cfg, map ->
                            val b = map.values.firstOrNull()
                            ok("access=${cfg.accessType}, areas=${map.keys.size}개, bounds=${b?.minValue}..${b?.maxValue}")
                        },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getWindow().getCurrentWindowPosition(
                        windowFL,
                        { pos -> ok("WindowPosition(ROW_1_LEFT)=${pos}%") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val target = if (step % 2 == 1) 0 else 35
                    vehicle.getWindow().setWindowPosition(
                        windowFL,
                        target,
                        { _, res -> ok("setWindowPosition($target%) -> $res") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : WindowPositionListener {
                        override fun onWindowPositionUpdated(position: Int?) = onEvent("WindowPosition=${position}%")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getWindow().registerWindowPosition(windowFL, listener)
                    ({ vehicle.getWindow().unregisterWindowPosition(windowFL, listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "window_lock",
                domainId = "WINDOW",
                apiTitle = "WindowLock (전 좌석 윈도우 잠금)",
                methodSignature = "Window.getCurrent/setWindowLock",
                targetAreaDesc = "WindowArea(ALL_SIDE)",
                runCapability = { ok, fail ->
                    vehicle.getWindow().checkWindowLockCapabilities(
                        { cfg, list -> ok("access=${cfg.accessType}, areas=${list.size}개") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getWindow().getCurrentWindowLockState(
                        allSideWindows,
                        { locked -> ok("WindowLockState=$locked") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val next = step % 2 == 1
                    vehicle.getWindow().setWindowLock(
                        allSideWindows,
                        next,
                        { _, res -> ok("setWindowLock($next) -> $res") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : WindowLockStateListener {
                        override fun onWindowLockStateUpdated(state: Boolean?) = onEvent("WindowLockState=$state")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getWindow().registerWindowLockState(allSideWindows, listener)
                    ({ vehicle.getWindow().unregisterWindowLockState(allSideWindows, listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "smart_glass_state",
                domainId = "WINDOW",
                apiTitle = "SmartGlassState (스마트 글래스 활성화)",
                methodSignature = "Window.getCurrent/setSmartGlassState",
                targetAreaDesc = "GlobalArea",
                runCapability = { ok, fail ->
                    vehicle.getWindow().checkSmartGlassStateCapabilities(
                        { cfg -> ok("access=${cfg.accessType}") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getWindow().getCurrentSmartGlassState(
                        { state -> ok("SmartGlassState=$state") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val next = step % 2 == 1
                    vehicle.getWindow().setSmartGlassState(
                        next,
                        { res -> ok("setSmartGlassState($next) -> $res") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : SmartGlassStateListener {
                        override fun onSmartGlassStateUpdated(state: Boolean?) = onEvent("SmartGlassState=$state")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getWindow().registerSmartGlassState(listener)
                    ({ vehicle.getWindow().unregisterSmartGlassState(listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "smart_glass_level",
                domainId = "WINDOW",
                apiTitle = "SmartGlassLevel (스마트 글래스 암막 농도)",
                methodSignature = "Window.getCurrent/setSmartGlassLevel",
                targetAreaDesc = "WindowArea(ALL_SIDE)",
                runCapability = { ok, fail ->
                    vehicle.getWindow().checkSmartGlassLevelCapabilities(
                        { cfg, map ->
                            val b = map.values.firstOrNull()
                            ok("access=${cfg.accessType}, bounds=${b?.minValue}..${b?.maxValue}")
                        },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getWindow().getCurrentSmartGlassLevel(
                        allSideWindows,
                        { lvl -> ok("SmartGlassLevel=$lvl") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val target = if (step % 2 == 1) 5 else 1
                    vehicle.getWindow().setSmartGlassLevel(
                        allSideWindows,
                        target,
                        { _, res -> ok("setSmartGlassLevel($target) -> $res") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : SmartGlassLevelListener {
                        override fun onSmartGlassLevelUpdated(level: Int?) = onEvent("SmartGlassLevel=$level")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getWindow().registerSmartGlassLevel(allSideWindows, listener)
                    ({ vehicle.getWindow().unregisterSmartGlassLevel(allSideWindows, listener) })
                }
            )
        )
    )

    // 3. Door Domain
    val doorItems = listOf(
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "door_lock_fl",
                domainId = "DOOR",
                apiTitle = "DoorLock (운전석 도어 잠금)",
                methodSignature = "Door.getDoorLockState / setDoorLock",
                targetAreaDesc = "DoorArea(ROW_1_LEFT)",
                runCapability = { ok, fail ->
                    vehicle.getDoor().checkDoorLockCapabilities(
                        { cfg, list -> ok("access=${cfg.accessType}, areas=${list.size}개") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getDoor().getDoorLockState(
                        doorFL,
                        { locked -> ok("DoorLockState=$locked") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val next = step % 2 == 1
                    vehicle.getDoor().setDoorLock(
                        doorFL,
                        next,
                        { _, res -> ok("setDoorLock($next) -> $res") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : DoorLockStateListener {
                        override fun onDoorLockStateUpdated(state: Boolean?) = onEvent("DoorLockState=$state")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getDoor().registerDoorLockState(doorFL, listener)
                    ({ vehicle.getDoor().unregisterDoorLockState(doorFL, listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "door_position_fl",
                domainId = "DOOR",
                apiTitle = "DoorPosition (운전석 도어 개폐 위치)",
                methodSignature = "Door.getCurrent/setDoorPosition",
                targetAreaDesc = "DoorArea(ROW_1_LEFT)",
                runCapability = { ok, fail ->
                    vehicle.getDoor().checkDoorPositionCapabilities(
                        { cfg, map ->
                            val b = map.values.firstOrNull()
                            ok("access=${cfg.accessType}, bounds=${b?.minValue}..${b?.maxValue}")
                        },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getDoor().getCurrentDoorPosition(
                        doorFL,
                        { pos -> ok("DoorPosition=$pos") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val target = if (step % 2 == 1) 0 else 1
                    vehicle.getDoor().setDoorPosition(
                        doorFL,
                        target,
                        { _, res -> ok("setDoorPosition($target) -> $res") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : DoorPositionListener {
                        override fun onPositionUpdated(pos: Int?) = onEvent("DoorPosition=$pos")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getDoor().registerDoorPosition(doorFL, listener)
                    ({ vehicle.getDoor().unregisterDoorPosition(doorFL, listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "door_glovebox_lock",
                domainId = "DOOR",
                apiTitle = "GloveBoxLock (글로브박스 잠금)",
                methodSignature = "Door.getCurrent/setGloveBoxLock",
                targetAreaDesc = "GlobalArea",
                runCapability = { ok, fail ->
                    vehicle.getDoor().checkGloveBoxLockCapabilities(
                        { cfg -> ok("access=${cfg.accessType}") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getDoor().getCurrentGloveBoxLockState(
                        { state -> ok("GloveBoxLock=$state") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val next = step % 2 == 1
                    vehicle.getDoor().setGloveBoxLock(
                        next,
                        { res -> ok("setGloveBoxLock($next) -> $res") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : GloveBoxLockStateListener {
                        override fun onGloveBoxLockStateUpdated(state: Boolean?) = onEvent("GloveBoxLock=$state")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getDoor().registerGloveBoxLockState(listener)
                    ({ vehicle.getDoor().unregisterGloveBoxLockState(listener) })
                }
            )
        )
    )

    // 4. HVAC Domain
    val hvacItems = listOf(
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "hvac_power",
                domainId = "HVAC",
                apiTitle = "HvacPower (공조 마스터 전원)",
                methodSignature = "HVAC.getHvacPowerState / setHvacPower",
                targetAreaDesc = "SeatArea(ROW_1_L+R)",
                runCapability = { ok, fail ->
                    vehicle.getHVAC().checkHvacPowerCapabilities(
                        { cfg, areas -> ok("access=${cfg.accessType}, areas=$areas") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getHVAC().getHvacPowerState(
                        frontSeats,
                        { state -> ok("HvacPowerState=$state") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val next = step % 2 == 1
                    vehicle.getHVAC().setHvacPower(
                        frontSeats,
                        next,
                        { _, res -> ok("setHvacPower($next) -> $res") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : HvacPowerStateListener {
                        override fun onHvacPowerStateUpdated(state: Boolean?) = onEvent("HvacPowerState=$state")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getHVAC().registerHvacPowerState(frontSeats, listener)
                    ({ vehicle.getHVAC().unregisterHvacPowerState(frontSeats, listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "hvac_temperature",
                domainId = "HVAC",
                apiTitle = "HvacTemperature (공조 설정 온도)",
                methodSignature = "HVAC.get/setHvacTemperature",
                targetAreaDesc = "SeatArea(ROW_1_LEFT)",
                runCapability = { ok, fail ->
                    vehicle.getHVAC().checkHvacTemperatureCapabilities(
                        { cfg, map, _ ->
                            val b = map.values.firstOrNull()
                            ok("access=${cfg.accessType}, areas=${map.keys.size}개, bounds=${b?.minValue}..${b?.maxValue}°C")
                        },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getHVAC().getHvacTemperature(
                        seatFL,
                        TemperatureUnit.CELSIUS,
                        { temp -> ok("HvacTemperature=${temp}°C") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val target = if (step % 2 == 1) 20.5f else 22.5f
                    vehicle.getHVAC().setHvacPower(frontSeats, true, { _, _ -> }, { _ -> })
                    vehicle.getHVAC().setHvacTemperature(
                        seatFL,
                        target,
                        TemperatureUnit.CELSIUS,
                        { _, res -> ok("setHvacTemperature(${target}°C) -> $res") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : HvacTemperatureListener {
                        override fun onHvacTemperatureUpdated(temperature: Float?) = onEvent("HvacTemp=${temperature}°C")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getHVAC().registerHvacTemperature(seatFL, listener)
                    ({ vehicle.getHVAC().unregisterHvacTemperature(seatFL, listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "hvac_fan_speed",
                domainId = "HVAC",
                apiTitle = "HvacFanSpeed (공조 풍량 단수)",
                methodSignature = "HVAC.get/setHvacFanSpeed",
                targetAreaDesc = "SeatArea(ROW_1_L+R)",
                runCapability = { ok, fail ->
                    vehicle.getHVAC().checkHvacFanSpeedCapabilities(
                        { cfg, map ->
                            val b = map.values.firstOrNull()
                            ok("access=${cfg.accessType}, bounds=${b?.minValue}..${b?.maxValue}단")
                        },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getHVAC().getHvacFanSpeed(
                        frontSeats,
                        { spd -> ok("HvacFanSpeed=${spd}단") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val target = if (step % 2 == 1) 4 else 2
                    vehicle.getHVAC().setHvacPower(frontSeats, true, { _, _ -> }, { _ -> })
                    vehicle.getHVAC().setHvacFanSpeed(
                        frontSeats,
                        target,
                        { _, res -> ok("setHvacFanSpeed(${target}단) -> $res") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : HvacFanSpeedListener {
                        override fun onHvacFanSpeedUpdated(speed: Int?) = onEvent("HvacFanSpeed=${speed}단")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getHVAC().registerHvacFanSpeed(frontSeats, listener)
                    ({ vehicle.getHVAC().unregisterHvacFanSpeed(frontSeats, listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "hvac_ac_aircleaner",
                domainId = "HVAC",
                apiTitle = "HvacAirCleaner & AC (공기청정 · 에어컨)",
                methodSignature = "HVAC.get/setHvacAirCleaner",
                targetAreaDesc = "SeatArea(ROW_1_L+R)",
                runCapability = { ok, fail ->
                    vehicle.getHVAC().checkHvacAirCleanerCapabilities(
                        { cfg, areas -> ok("access=${cfg.accessType}, areas=${areas.size}개") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getHVAC().getHvacAirCleanerState(
                        frontSeats,
                        { state -> ok("HvacAirCleaner=$state") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val next = step % 2 == 1
                    vehicle.getHVAC().setHvacPower(frontSeats, true, { _, _ -> }, { _ -> })
                    vehicle.getHVAC().setHvacAirCleaner(
                        frontSeats,
                        next,
                        { _, res -> ok("setHvacAirCleaner($next) -> $res") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : HvacAirCleanerStateListener {
                        override fun onHvacAirCleanerStateUpdated(state: Boolean?) = onEvent("HvacAirCleaner=$state")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getHVAC().registerHvacAirCleanerState(frontSeats, listener)
                    ({ vehicle.getHVAC().unregisterHvacAirCleanerState(frontSeats, listener) })
                }
            )
        )
    )

    // 5. Seat Domain
    val seatItems = listOf(
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "seat_occupancy_fl",
                domainId = "SEAT",
                apiTitle = "SeatOccupancy (운전석 탑승 감지)",
                methodSignature = "Seat.getCurrentSeatOccupancy",
                targetAreaDesc = "SeatArea(ROW_1_LEFT)",
                runCapability = { ok, fail ->
                    vehicle.getSeat().checkSeatOccupancyCapabilities(
                        { cfg, areas -> ok("access=${cfg.accessType}, areas=${areas.size}개") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getSeat().getCurrentSeatOccupancy(
                        seatFL,
                        { occ -> ok("SeatOccupancy(ROW_1_LEFT)=$occ") },
                        { e -> fail(e) }
                    )
                },
                runSet = { _, _, fail ->
                    fail(readOnlySetError("SeatOccupancy"))
                },
                startListening = { onEvent, onErr ->
                    val listener = object : SeatOccupancyListener {
                        override fun onSeatOccupancyUpdated(occupancy: Boolean?) = onEvent("SeatOccupancy=$occupancy")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getSeat().registerSeatOccupancy(seatFL, listener)
                    ({ vehicle.getSeat().unregisterSeatOccupancy(seatFL, listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "seat_ventilation_fl",
                domainId = "SEAT",
                apiTitle = "HvacSeatVentilation (시트 통풍 제어)",
                methodSignature = "Seat.getHvacSeatVentilationState / setHvacSeatVentilation",
                targetAreaDesc = "SeatArea(ROW_1_LEFT)",
                runCapability = { ok, fail ->
                    vehicle.getSeat().checkSeatVentilationCapabilities(
                        { cfg, map ->
                            val b = map.values.firstOrNull()
                            ok("access=${cfg.accessType}, bounds=${b?.minValue}..${b?.maxValue}단")
                        },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getSeat().getHvacSeatVentilationState(
                        seatFL,
                        { lvl -> ok("SeatVentilation=${lvl}단") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val target = step % 4
                    vehicle.getHVAC().setHvacPower(frontSeats, true, { _, _ -> }, { _ -> })
                    vehicle.getSeat().setHvacSeatVentilation(
                        seatFL,
                        target,
                        { _, res -> ok("setHvacSeatVentilation(${target}단) -> $res") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : HvacSeatVentilationListener {
                        override fun onHvacSeatVentilationUpdated(ventilation: Int?) = onEvent("SeatVentilation=${ventilation}단")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getSeat().registerHvacSeatVentilation(seatFL, listener)
                    ({ vehicle.getSeat().unregisterHvacSeatVentilation(seatFL, listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "seat_temperature_fl",
                domainId = "SEAT",
                apiTitle = "HvacSeatTemperature (시트 열선 제어)",
                methodSignature = "Seat.get/setHvacSeatTemperature",
                targetAreaDesc = "SeatArea(ROW_1_LEFT)",
                runCapability = { ok, fail ->
                    vehicle.getSeat().checkSeatTemperatureCapabilities(
                        { cfg, map ->
                            val b = map.values.firstOrNull()
                            ok("access=${cfg.accessType}, bounds=${b?.minValue}..${b?.maxValue}단")
                        },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getSeat().getHvacSeatTemperature(
                        seatFL,
                        { lvl -> ok("SeatTemperature=${lvl}단") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val target = step % 4
                    vehicle.getHVAC().setHvacPower(frontSeats, true, { _, _ -> }, { _ -> })
                    vehicle.getSeat().setHvacSeatTemperature(
                        seatFL,
                        target,
                        { _, res -> ok("setHvacSeatTemperature(${target}단) -> $res") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : HvacSeatTemperatureListener {
                        override fun onHvacSeatTemperatureUpdated(temperature: Int?) = onEvent("SeatTemperature=${temperature}단")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getSeat().registerHvacSeatTemperature(seatFL, listener)
                    ({ vehicle.getSeat().unregisterHvacSeatTemperature(seatFL, listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "seat_belt_buckled_fl",
                domainId = "SEAT",
                apiTitle = "SeatBeltBuckled (안전벨트 체결 감지)",
                methodSignature = "Seat.getCurrentSeatBeltBuckledState",
                targetAreaDesc = "SeatArea(ROW_1_LEFT)",
                runCapability = { ok, fail ->
                    vehicle.getSeat().checkSeatBeltBuckledCapabilities(
                        { cfg, areas -> ok("access=${cfg.accessType}, areas=${areas.size}개") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getSeat().getCurrentSeatBeltBuckledState(
                        seatFL,
                        { buckled -> ok("SeatBeltBuckled=$buckled") },
                        { e -> fail(e) }
                    )
                },
                runSet = { _, _, fail ->
                    fail(readOnlySetError("SeatBeltBuckled"))
                },
                startListening = { onEvent, onErr ->
                    val listener = object : SeatBeltBuckledStateListener {
                        override fun onSeatBeltBuckledStateUpdated(state: Boolean?) = onEvent("SeatBeltBuckled=$state")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getSeat().registerSeatBeltBuckledState(seatFL, listener)
                    ({ vehicle.getSeat().unregisterSeatBeltBuckledState(seatFL, listener) })
                }
            )
        )
    )

    // 6. Driving & EV Battery Domain
    val drivingEvItems = listOf(
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "driving_gear_selection",
                domainId = "DRIVING_EV",
                apiTitle = "GearSelection (변속 기어 P/R/N/D)",
                methodSignature = "Gear.getCurrentGearSelectionState",
                targetAreaDesc = "GlobalArea",
                runCapability = { ok, fail ->
                    vehicle.getGear().getCurrentGearSelectionState(
                        { g -> ok("READ supported (current=$g)") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getGear().getCurrentGearSelectionState(
                        { gear -> ok("GearSelection=$gear") },
                        { e -> fail(e) }
                    )
                },
                runSet = { _, _, fail ->
                    fail(readOnlySetError("GearSelection"))
                },
                startListening = { onEvent, onErr ->
                    val listener = object : GearSelectionListener {
                        override fun onSelected(gear: VehicleGear?) = onEvent("GearSelection=$gear")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getGear().registerGearSelection(listener)
                    ({ vehicle.getGear().unregisterGearSelection(listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "driving_brake_pedal",
                domainId = "DRIVING_EV",
                apiTitle = "BrakePedalStatus (브레이크 페달 답력 감지)",
                methodSignature = "Brake.getCurrentBrakePedalStatus",
                targetAreaDesc = "GlobalArea",
                runCapability = { ok, fail ->
                    vehicle.getBrake().checkBrakePedalStatusCapabilities(
                        { cfg -> ok("access=${cfg.accessType}") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getBrake().getCurrentBrakePedalStatus(
                        { pressed -> ok("BrakePedalStatus=$pressed") },
                        { e -> fail(e) }
                    )
                },
                runSet = { _, _, fail ->
                    fail(readOnlySetError("BrakePedalStatus"))
                },
                startListening = { onEvent, onErr ->
                    val listener = object : BrakePedalStatusListener {
                        override fun onBrakePedalStatusUpdated(status: Boolean?) = onEvent("BrakePedalStatus=$status")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getBrake().registerBrakePedalStatus(listener)
                    ({ vehicle.getBrake().unregisterBrakePedalStatus(listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "driving_mode_state",
                domainId = "DRIVING_EV",
                apiTitle = "DrivingModeState (주행 모드 ECO/COMFORT/SPORT)",
                methodSignature = "DrivingMode.getCurrentDrivingModeState",
                targetAreaDesc = "GlobalArea",
                runCapability = { ok, fail ->
                    vehicle.getDrivingMode().checkDrivingModeStateCapabilities(
                        { cfg -> ok("access=${cfg.accessType}") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getDrivingMode().getCurrentDrivingModeState(
                        { mode -> ok("DrivingMode=$mode") },
                        { e -> fail(e) }
                    )
                },
                runSet = { _, _, fail ->
                    fail(readOnlySetError("DrivingModeState"))
                },
                startListening = { onEvent, onErr ->
                    val listener = object : DrivingModeStateListener {
                        override fun onDrivingModeStateUpdated(state: DrivingModeType?) = onEvent("DrivingMode=$state")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getDrivingMode().registerDrivingModeState(listener)
                    ({ vehicle.getDrivingMode().unregisterDrivingModeState(listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "ev_battery_soc",
                domainId = "DRIVING_EV",
                apiTitle = "EvBatteryStateOfCharge (배터리 SOC %)",
                methodSignature = "EvBattery.getEvBatteryStateOfCharge",
                targetAreaDesc = "GlobalArea",
                runCapability = { ok, fail ->
                    vehicle.getEvBattery().checkEvBatteryStateOfChargeCapabilities(
                        { cfg -> ok("access=${cfg.accessType}") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getEvBattery().getEvBatteryStateOfCharge(
                        { soc -> ok("EvBatterySOC=${soc}%") },
                        { e -> fail(e) }
                    )
                },
                runSet = { _, _, fail ->
                    fail(readOnlySetError("EvBatteryStateOfCharge"))
                },
                startListening = { onEvent, onErr ->
                    val listener = object : EvBatteryStateOfChargeListener {
                        override fun onEvBatteryStateOfChargeUpdated(value: Int?) = onEvent("EvBatterySOC=${value}%")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getEvBattery().registerEvBatteryStateOfCharge(listener)
                    ({ vehicle.getEvBattery().unregisterEvBatteryStateOfCharge(listener) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "ev_charge_port_open",
                domainId = "DRIVING_EV",
                apiTitle = "EvChargePortOpen (충전 포트 도어 제어)",
                methodSignature = "EvBattery.isEvChargePortOpen / setEvChargePortOpen",
                targetAreaDesc = "GlobalArea",
                runCapability = { ok, fail ->
                    vehicle.getEvBattery().checkEvChargePortOpenCapabilities(
                        { cfg -> ok("access=${cfg.accessType}") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getEvBattery().isEvChargePortOpen(
                        { opened -> ok("EvChargePortOpen=$opened") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val next = step % 2 == 1
                    vehicle.getEvBattery().setEvChargePortOpen(
                        next,
                        { res -> ok("setEvChargePortOpen($next) -> $res") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : EvChargePortOpenListener {
                        override fun onEvChargePortOpenUpdated(isOpened: Boolean?) = onEvent("EvChargePortOpen=$isOpened")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getEvBattery().registerEvChargePortOpen(listener)
                    ({ vehicle.getEvBattery().unregisterEvChargePortOpen(listener) })
                }
            )
        )
    )

    // 7. Toll Domain
    val tollItems = listOf(
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "toll_etcs_volume",
                domainId = "TOLL",
                apiTitle = "EtcsVoiceGuidanceVolume (하이패스 음성 안내 볼륨)",
                methodSignature = "Toll.getCurrent/setEtcsVoiceGuidanceVolume",
                targetAreaDesc = "GlobalArea",
                runCapability = { ok, fail ->
                    vehicle.getToll().checkEtcsVoiceGuidanceVolumeCapabilities(
                        { cfg, bounds -> ok("access=${cfg.accessType}, bounds=${bounds.minValue}..${bounds.maxValue}") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getToll().getCurrentEtcsVoiceGuidanceVolume(
                        { vol -> ok("EtcsVoiceGuidanceVolume=$vol") },
                        { e -> fail(e) }
                    )
                },
                runSet = { step, ok, fail ->
                    val target = if (step % 2 == 1) 0 else 5
                    vehicle.getToll().setEtcsVoiceGuidanceVolume(
                        target,
                        { ok("setEtcsVoiceGuidanceVolume($target) -> OK") },
                        { e -> fail(e) }
                    )
                },
                startListening = { onEvent, onErr ->
                    val listener = object : EtcsVoiceGuidanceVolumeListener {
                        override fun onEtcsVoiceGuidanceVolumeUpdated(volume: Int?) = onEvent("EtcsVolume=$volume")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getToll().registerEtcsVoiceGuidanceVolume(listener)
                    ({ vehicle.getToll().unregisterEtcsVoiceGuidanceVolume(listener) })
                }
            )
        )
    )

    // 8. CarInfo & Display Domain
    val carInfoDisplayItems = listOf(
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "carinfo_vin",
                domainId = "CARINFO_DISPLAY",
                apiTitle = "VINInfo (차대번호 조회)",
                methodSignature = "CarInfo.getVINInfo",
                targetAreaDesc = "GlobalArea",
                runCapability = { ok, fail ->
                    vehicle.getCarInfo().checkVINInfoCapabilities(
                        { cfg -> ok("access=${cfg.accessType}") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getCarInfo().getVINInfo(
                        { vin -> ok("VIN=$vin") },
                        { e -> fail(e) }
                    )
                },
                runSet = { _, _, fail ->
                    fail(readOnlySetError("VINInfo"))
                },
                startListening = { _, onErr ->
                    onErr(UnsupportedOperationException("ERROR_NOT_SUPPORTED_PROPERTY (VINInfo는 정적 프로퍼티로 Listener 미지원)"))
                    ({})
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "carinfo_crn",
                domainId = "CARINFO_DISPLAY",
                apiTitle = "CRNInfo (차량 등록번호 조회)",
                methodSignature = "CarInfo.getCRNInfo",
                targetAreaDesc = "GlobalArea",
                runCapability = { ok, fail ->
                    vehicle.getCarInfo().checkCRNInfoCapabilities(
                        { cfg -> ok("access=${cfg.accessType}") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getCarInfo().getCRNInfo(
                        { crn -> ok("CRN=$crn") },
                        { e -> fail(e) }
                    )
                },
                runSet = { _, _, fail ->
                    fail(readOnlySetError("CRNInfo"))
                },
                startListening = { _, onErr ->
                    onErr(UnsupportedOperationException("ERROR_NOT_SUPPORTED_PROPERTY (CRNInfo는 정적 프로퍼티로 Listener 미지원)"))
                    ({})
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "display_temp_unit",
                domainId = "CARINFO_DISPLAY",
                apiTitle = "TemperatureDisplayUnit (클러스터/디스플레이 온도 단위)",
                methodSignature = "Display.getCurrentTemperatureDisplayUnit",
                targetAreaDesc = "GlobalArea",
                runCapability = { ok, fail ->
                    vehicle.getDisplay().checkTemperatureDisplayUnitCapabilities(
                        { cfg -> ok("access=${cfg.accessType}") },
                        { e -> fail(e) }
                    )
                },
                runGet = { ok, fail ->
                    vehicle.getDisplay().getCurrentTemperatureDisplayUnit(
                        { unit -> ok("TemperatureDisplayUnit=$unit") },
                        { e -> fail(e) }
                    )
                },
                runSet = { _, _, fail ->
                    fail(readOnlySetError("TemperatureDisplayUnit"))
                },
                startListening = { onEvent, onErr ->
                    val listener = object : TemperatureDisplayUnitListener {
                        override fun onTemperatureDisplayUnitUpdated(unit: TemperatureUnit?) = onEvent("TempUnit=$unit")
                        override fun onFailed(e: Exception) = onErr(e)
                    }
                    vehicle.getDisplay().registerTemperatureDisplayUnit(listener)
                    ({ vehicle.getDisplay().unregisterTemperatureDisplayUnit(listener) })
                }
            )
        )
    )

    // 9. TTS / STT Voice Domain
    val ttsSttItems = listOf(
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "voice_tts_engine",
                domainId = "TTS_STT",
                apiTitle = "Android TextToSpeech (차량 음성 합성 엔진)",
                methodSignature = "TextToSpeech.isLanguageAvailable / speak",
                targetAreaDesc = "Locale.KOREAN",
                runCapability = { ok, fail ->
                    getOrCreateTts { tts, status ->
                        if (tts != null && status == TextToSpeech.SUCCESS) {
                            val langCode = tts.isLanguageAvailable(Locale.KOREAN)
                            ok("engine=${tts.defaultEngine}, koLangCode=$langCode")
                        } else {
                            fail(IllegalStateException("TextToSpeech 초기화 실패 (status=$status)"))
                        }
                    }
                },
                runGet = { ok, fail ->
                    getOrCreateTts { tts, status ->
                        if (tts != null && status == TextToSpeech.SUCCESS) {
                            ok("defaultEngine=${tts.defaultEngine}, speaking=${tts.isSpeaking}, voices=${tts.voices?.size ?: 0}개")
                        } else {
                            fail(IllegalStateException("TextToSpeech 미연결 (status=$status)"))
                        }
                    }
                },
                runSet = { step, ok, fail ->
                    getOrCreateTts { tts, status ->
                        if (tts != null && status == TextToSpeech.SUCCESS) {
                            tts.language = Locale.KOREAN
                            val uttId = "pleos_sdk_tts_$step"
                            val res = tts.speak(
                                "플레오스 스테이지 차량 음성 합성 테스트입니다.",
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                uttId
                            )
                            if (res == TextToSpeech.SUCCESS) {
                                ok("TextToSpeech.speak(uttId=$uttId) -> SUCCESS")
                            } else {
                                fail(IllegalStateException("TextToSpeech.speak 반환 에러 (code=$res)"))
                            }
                        } else {
                            fail(IllegalStateException("TextToSpeech 엔진 미지원 (status=$status)"))
                        }
                    }
                },
                startListening = { onEvent, onErr ->
                    var boundTts: TextToSpeech? = null
                    getOrCreateTts { tts, status ->
                        if (tts != null && status == TextToSpeech.SUCCESS) {
                            boundTts = tts
                            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) = onEvent("TTS onStart($utteranceId)")
                                override fun onDone(utteranceId: String?) = onEvent("TTS onDone($utteranceId)")
                                @Deprecated("Deprecated in Java")
                                override fun onError(utteranceId: String?) = onErr(IllegalStateException("TTS onError($utteranceId)"))
                            })
                        } else {
                            onErr(IllegalStateException("TTS Listener 등록 실패 (status=$status)"))
                        }
                    }
                    ({ boundTts?.setOnUtteranceProgressListener(null) })
                }
            )
        ),
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "voice_stt_recognizer",
                domainId = "TTS_STT",
                apiTitle = "SpeechRecognizer (차량 STT 음성 인식 서비스)",
                methodSignature = "SpeechRecognizer.isRecognitionAvailable / startListening",
                targetAreaDesc = "ko-KR",
                runCapability = { ok, fail ->
                    val available = SpeechRecognizer.isRecognitionAvailable(context)
                    if (available) {
                        ok("SpeechRecognizer.isRecognitionAvailable=true")
                    } else {
                        fail(IllegalStateException("ERROR_STT_SERVICE_NOT_AVAILABLE (에뮬레이터에 음성인식 서비스 미설치)"))
                    }
                },
                runGet = { ok, fail ->
                    val services = context.packageManager.queryIntentServices(
                        Intent("android.speech.RecognitionService"),
                        PackageManager.MATCH_ALL
                    )
                    if (services.isNotEmpty()) {
                        val names = services.joinToString { it.serviceInfo?.packageName.orEmpty() }
                        ok("RecognitionServices=[$names]")
                    } else {
                        fail(IllegalStateException("등록된 RecognitionService 없음 (0개)"))
                    }
                },
                runSet = { _, ok, fail ->
                    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                        fail(IllegalStateException("SpeechRecognizer 미지원 환경 (isRecognitionAvailable=false)"))
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            try {
                                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                                }
                                recognizer.setRecognitionListener(object : RecognitionListener {
                                    override fun onReadyForSpeech(params: Bundle?) {
                                        ok("onReadyForSpeech 수신 완료")
                                        recognizer.cancel()
                                        recognizer.destroy()
                                    }
                                    override fun onBeginningOfSpeech() {}
                                    override fun onRmsChanged(rmsdB: Float) {}
                                    override fun onBufferReceived(buffer: ByteArray?) {}
                                    override fun onEndOfSpeech() {}
                                    override fun onError(error: Int) {
                                        fail(IllegalStateException("SpeechRecognizer.onError(code=$error)"))
                                        recognizer.destroy()
                                    }
                                    override fun onResults(results: Bundle?) {}
                                    override fun onPartialResults(partialResults: Bundle?) {}
                                    override fun onEvent(eventType: Int, params: Bundle?) {}
                                })
                                recognizer.startListening(intent)
                            } catch (t: Throwable) {
                                fail(t)
                            }
                        }
                    }
                },
                startListening = { onEvent, onErr ->
                    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                        throw IllegalStateException("SpeechRecognizer 미지원 환경")
                    }
                    var sr: SpeechRecognizer? = null
                    Handler(Looper.getMainLooper()).post {
                        try {
                            val r = SpeechRecognizer.createSpeechRecognizer(context)
                            sr = r
                            r.setRecognitionListener(object : RecognitionListener {
                                override fun onReadyForSpeech(params: Bundle?) = onEvent("STT onReadyForSpeech")
                                override fun onBeginningOfSpeech() = onEvent("STT onBeginningOfSpeech")
                                override fun onRmsChanged(rmsdB: Float) {}
                                override fun onBufferReceived(buffer: ByteArray?) {}
                                override fun onEndOfSpeech() = onEvent("STT onEndOfSpeech")
                                override fun onError(error: Int) = onErr(IllegalStateException("STT Error code=$error"))
                                override fun onResults(results: Bundle?) {
                                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                    onEvent("STT Results=${matches?.joinToString()}")
                                }
                                override fun onPartialResults(partialResults: Bundle?) {}
                                override fun onEvent(eventType: Int, params: Bundle?) {}
                            })
                        } catch (t: Throwable) {
                            onErr(t)
                        }
                    }
                    ({
                        Handler(Looper.getMainLooper()).post {
                            try {
                                sr?.cancel()
                                sr?.destroy()
                            } catch (_: Throwable) {
                            }
                        }
                    })
                }
            )
        )
    )

    // 10. CarUxRestrictions Domain
    val uxrItems = listOf(
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = "aaos_car_ux_restrictions",
                domainId = "UX_RESTRICTIONS",
                apiTitle = "CarUxRestrictionsManager (AAOS 주행 중 사용자 조작 규제)",
                methodSignature = "Car.createCar -> getCurrentCarUxRestrictions",
                targetAreaDesc = "AAOS CarService (uxrestriction)",
                runCapability = { ok, fail ->
                    try {
                        val carClass = Class.forName("android.car.Car")
                        val carObj = carClass.getMethod("createCar", Context::class.java)
                            .invoke(null, context.applicationContext)
                            ?: throw IllegalStateException("Car.createCar returned null")
                        val uxrMgr = carClass.getMethod("getCarManager", String::class.java)
                            .invoke(carObj, "uxrestriction")
                            ?: throw IllegalStateException("CarUxRestrictionsManager is null")
                        ok("CarService 연결됨 (${uxrMgr.javaClass.simpleName})")
                    } catch (t: Throwable) {
                        fail(t.cause ?: t)
                    }
                },
                runGet = { ok, fail ->
                    try {
                        val carClass = Class.forName("android.car.Car")
                        val carObj = carClass.getMethod("createCar", Context::class.java)
                            .invoke(null, context.applicationContext)
                            ?: throw IllegalStateException("Car.createCar returned null")
                        val uxrMgr = carClass.getMethod("getCarManager", String::class.java)
                            .invoke(carObj, "uxrestriction")
                            ?: throw IllegalStateException("CarUxRestrictionsManager is null")
                        val cur = uxrMgr.javaClass.getMethod("getCurrentCarUxRestrictions").invoke(uxrMgr)
                            ?: throw IllegalStateException("getCurrentCarUxRestrictions returned null")
                        val reqOpt = cur.javaClass.getMethod("isRequiresDistractionOptimization").invoke(cur)
                        val mask = cur.javaClass.getMethod("getActiveRestrictions").invoke(cur)
                        ok("requiresDistractionOpt=$reqOpt, activeRestrictionsMask=$mask")
                    } catch (t: Throwable) {
                        fail(t.cause ?: t)
                    }
                },
                runSet = { _, _, fail ->
                    fail(readOnlySetError("CarUxRestrictions (AAOS 시스템 정책 전용)"))
                },
                startListening = { onEvent, _ ->
                    val carClass = Class.forName("android.car.Car")
                    val carObj = carClass.getMethod("createCar", Context::class.java)
                        .invoke(null, context.applicationContext)
                        ?: throw IllegalStateException("Car.createCar returned null")
                    val uxrMgr = carClass.getMethod("getCarManager", String::class.java)
                        .invoke(carObj, "uxrestriction")
                        ?: throw IllegalStateException("CarUxRestrictionsManager is null")
                    val listenerIf = Class.forName(
                        "android.car.drivingstate.CarUxRestrictionsManager\$OnUxRestrictionsChangedListener"
                    )
                    val proxy = Proxy.newProxyInstance(
                        listenerIf.classLoader,
                        arrayOf(listenerIf)
                    ) { _, method, args ->
                        if (method.name == "onUxRestrictionsChanged" && !args.isNullOrEmpty() && args[0] != null) {
                            val r = args[0]
                            val reqOpt = r.javaClass.getMethod("isRequiresDistractionOptimization").invoke(r)
                            val mask = r.javaClass.getMethod("getActiveRestrictions").invoke(r)
                            onEvent("requiresOpt=$reqOpt, mask=$mask")
                        }
                        null
                    }
                    uxrMgr.javaClass.getMethod("registerListener", listenerIf).invoke(uxrMgr, proxy)
                    ({
                        try {
                            uxrMgr.javaClass.getMethod("unregisterListener").invoke(uxrMgr)
                        } catch (_: Throwable) {
                        }
                    })
                }
            )
        )
    )

    // 11. Gleo AI Intent Domain
    val gleoActions = listOf(
        Triple("gleo_play_song", "PLAY_KARAOKE_SONG", "노래 선곡 및 즉시 재생 인텐트"),
        Triple("gleo_sound_mode", "SET_STAGE_SOUND_MODE", "에코 및 키 조절 인텐트"),
        Triple("gleo_playback_ctrl", "CONTROL_STAGE_PLAYBACK", "재생 · 일시정지 제어 인텐트")
    )
    val gleoItems = gleoActions.map { (specId, actionName, desc) ->
        SdkApiTestEntryHolder(
            SdkUnitTestApiSpec(
                id = specId,
                domainId = "GLEO",
                apiTitle = "$actionName ($desc)",
                methodSignature = "PackageManager.queryIntentActivities / startActivity($actionName)",
                targetAreaDesc = "MainActivity IntentFilter",
                runCapability = { ok, fail ->
                    val probe = Intent(actionName).setPackage(context.packageName)
                    val matches = context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
                    if (matches.isNotEmpty()) {
                        val targetName = matches.first().activityInfo?.name.orEmpty()
                        ok("Manifest 등록 확인됨 (receiver=$targetName)")
                    } else {
                        fail(IllegalStateException("ERROR_INTENT_FILTER_NOT_FOUND ($actionName 미등록)"))
                    }
                },
                runGet = { ok, _ ->
                    ok("최근 수신 인텐트 -> ${getLastGleoIntent()}")
                },
                runSet = { step, ok, fail ->
                    try {
                        val intent = Intent(actionName).apply {
                            setPackage(context.packageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            when (actionName) {
                                "PLAY_KARAOKE_SONG" -> putExtra("song_query", "네온 드라이브")
                                "SET_STAGE_SOUND_MODE" -> {
                                    putExtra("echo_level", if (step % 2 == 1) 85 else 60)
                                    putExtra("key_shift", 0)
                                }
                                "CONTROL_STAGE_PLAYBACK" -> {
                                    putExtra("command", if (step % 2 == 1) "PAUSE" else "RESUME")
                                }
                            }
                        }
                        context.startActivity(intent)
                        ok("startActivity($actionName) 실제 디스패치 성공")
                    } catch (t: Throwable) {
                        fail(t)
                    }
                },
                startListening = { onEvent, _ ->
                    val job: Job = coroutineScopeProvider().launch {
                        vehicleManager.lastGleoIntentInfo.collect { info ->
                            onEvent(info)
                        }
                    }
                    ({ job.cancel() })
                }
            )
        )
    }

    return listOf(
        SdkDomainGroup(
            id = "LIGHT",
            badgeTitle = "1. Light",
            fullTitle = "1. Light (실내 무드등 · 실내 중앙등)",
            subtitle = "vehicle.getLight() — AmbientLightSwitch / Brightness / Color / CabinLightSwitch",
            items = lightItems
        ),
        SdkDomainGroup(
            id = "WINDOW",
            badgeTitle = "2. Window & SmartGlass",
            fullTitle = "2. Window & SmartGlass (창문 · 스마트 글래스)",
            subtitle = "vehicle.getWindow() — WindowPosition / WindowLock / SmartGlassState / SmartGlassLevel",
            items = windowItems
        ),
        SdkDomainGroup(
            id = "DOOR",
            badgeTitle = "3. Door",
            fullTitle = "3. Door (도어 잠금 · 도어 개폐 · 글로브박스)",
            subtitle = "vehicle.getDoor() — DoorLock / DoorPosition / GloveBoxLock",
            items = doorItems
        ),
        SdkDomainGroup(
            id = "HVAC",
            badgeTitle = "4. HVAC 공조",
            fullTitle = "4. HVAC 공조 (마스터 전원 · 온도 · 풍량 · 공기청정)",
            subtitle = "vehicle.getHVAC() — HvacPower / HvacTemperature / HvacFanSpeed / HvacAirCleaner",
            items = hvacItems
        ),
        SdkDomainGroup(
            id = "SEAT",
            badgeTitle = "5. Seat 시트",
            fullTitle = "5. Seat 시트/탑승감지 (탑승 감지 · 통풍 · 열선 · 안전벨트)",
            subtitle = "vehicle.getSeat() — SeatOccupancy / HvacSeatVentilation / HvacSeatTemperature / SeatBeltBuckled",
            items = seatItems
        ),
        SdkDomainGroup(
            id = "DRIVING_EV",
            badgeTitle = "6. Driving & EV",
            fullTitle = "6. Driving & EV 배터리 (기어 · 브레이크 · 주행모드 · 배터리 SOC)",
            subtitle = "vehicle.getGear() / getBrake() / getDrivingMode() / getEvBattery()",
            items = drivingEvItems
        ),
        SdkDomainGroup(
            id = "TOLL",
            badgeTitle = "7. Toll 하이패스",
            fullTitle = "7. Toll 하이패스 (ETCS 음성 안내 볼륨)",
            subtitle = "vehicle.getToll() — EtcsVoiceGuidanceVolume",
            items = tollItems
        ),
        SdkDomainGroup(
            id = "CARINFO_DISPLAY",
            badgeTitle = "8. CarInfo & Display",
            fullTitle = "8. CarInfo & Display (차대번호 VIN · CRN · 온도 표시 단위)",
            subtitle = "vehicle.getCarInfo() / getDisplay() — VINInfo / CRNInfo / TemperatureDisplayUnit",
            items = carInfoDisplayItems
        ),
        SdkDomainGroup(
            id = "TTS_STT",
            badgeTitle = "9. TTS / STT 음성",
            fullTitle = "9. TTS / STT 음성 (차량 음성 합성 · 음성 인식)",
            subtitle = "android.speech.tts.TextToSpeech & android.speech.SpeechRecognizer",
            items = ttsSttItems
        ),
        SdkDomainGroup(
            id = "UX_RESTRICTIONS",
            badgeTitle = "10. CarUxRestrictions",
            fullTitle = "10. CarUxRestrictions 주행규제 (AAOS CarService)",
            subtitle = "android.car.Car -> CarUxRestrictionsManager",
            items = uxrItems
        ),
        SdkDomainGroup(
            id = "GLEO",
            badgeTitle = "11. Gleo AI 인텐트",
            fullTitle = "11. Gleo AI 인텐트 (음성 에이전트 액션 디스패치 검증)",
            subtitle = "PLAY_KARAOKE_SONG · SET_STAGE_SOUND_MODE · CONTROL_STAGE_PLAYBACK",
            items = gleoItems
        )
    )
}
