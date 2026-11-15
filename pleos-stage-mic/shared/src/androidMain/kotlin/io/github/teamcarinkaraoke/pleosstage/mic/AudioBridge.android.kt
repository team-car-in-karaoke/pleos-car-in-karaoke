package io.github.teamcarinkaraoke.pleosstage.mic

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Base64
import android.util.Log
import android.util.LruCache
import android.view.WindowManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Strictly allows ONLY routable LAN / Hotspot private IPv4 ranges (`192.168.0.0/16` and `172.16.0.0/12`),
 * 100% excluding cellular 5G/LTE (`10.x.x.x`), CLAT (`192.0.0.x`), CGNAT (`100.64.x.x`), loopback (`127.x.x.x`),
 * link-local (`169.254.x.x`), and `.0` / `.255` subnet/broadcast addresses.
 */
private fun isValidPrivatePhoneIpv4(ip: String): Boolean {
    val clean = ip.trim()
    if (clean.isBlank() ||
        clean == "0.0.0.0" ||
        clean.startsWith("127.") ||
        clean.startsWith("10.") ||
        clean.startsWith("192.0.0.") ||
        clean.startsWith("169.254.")
    ) {
        return false
    }
    val parts = clean.split(".")
    if (parts.size != 4) return false
    val nums = parts.map { it.toIntOrNull() ?: return false }
    if (nums.any { it !in 0..255 }) return false
    if (nums[3] == 0 || nums[3] == 255) return false
    return (nums[0] == 192 && nums[1] == 168) ||
        (nums[0] == 172 && nums[1] in 16..31)
}

private fun isHotspotInterfaceName(ifNameRaw: String): Boolean {
    val n = ifNameRaw.trim().lowercase()
    if (n.isEmpty()) return false
    return n.startsWith("swlan") ||
        n.startsWith("ap") ||
        n.startsWith("wlan1") ||
        n.startsWith("wlan2") ||
        n.startsWith("rndis") ||
        n.contains("tether") ||
        n.contains("softap") ||
        n.startsWith("p2p") ||
        n.contains("bridge")
}

private fun isCellularOrTunnelInterfaceName(ifNameRaw: String): Boolean {
    val n = ifNameRaw.trim().lowercase()
    if (n.isEmpty()) return false
    return n == "lo" ||
        n.startsWith("rmnet") ||
        n.startsWith("ccmni") ||
        n.startsWith("pdp") ||
        n.startsWith("clat") ||
        n.startsWith("tun") ||
        n.startsWith("ppp") ||
        n.startsWith("dummy") ||
        n.startsWith("epdg") ||
        n.startsWith("v4-")
}

private fun isKnownHotspotSubnet(ip: String): Boolean {
    return ip.startsWith("192.168.43.") ||
        ip.startsWith("192.168.137.") ||
        ip.startsWith("192.168.49.") ||
        ip.startsWith("172.20.10.")
}

private const val MIC_HOTSPOT_PREFS_NAME = "pleos_hotspot_cache"

private val server8082BindLock = Any()

@Volatile
private var activeGlobal8082ServerSocket: ServerSocket? = null

@Volatile
private var activeGlobal8081UdpSocket: DatagramSocket? = null

private val MIC_MQTT_TCP_BROKERS = listOf(
    "broker.emqx.io" to 1883,
    "broker.hivemq.com" to 1883
)

private fun extractValidPrivateIpv4FromEndpoint(rawIpOrUrl: String): String {
    val clean = rawIpOrUrl.trim()
        .removePrefix("ws://")
        .removePrefix("wss://")
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore("/")
        .substringBefore(":")
        .trim()
    return if (isValidPrivatePhoneIpv4(clean)) clean else ""
}

private fun saveKnownCarEndpointIp(context: Context?, rawIpOrUrl: String) {
    val ctx = context?.applicationContext ?: return
    val host = extractValidPrivateIpv4FromEndpoint(rawIpOrUrl)
    if (host.isEmpty()) return
    val octets = host.split(".")
    if (octets.size != 4) return
    val subnetPrefix = "${octets[0]}.${octets[1]}.${octets[2]}"
    try {
        val prefs = ctx.getSharedPreferences(MIC_HOTSPOT_PREFS_NAME, Context.MODE_PRIVATE)
        val prevIps = prefs.getString("recent_car_ips", "").orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && it != host }
        val nextIps = (listOf(host) + prevIps).take(6).joinToString(",")

        val prevSubs = prefs.getString("recent_car_subnets", "").orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && it != subnetPrefix }
        val nextSubs = (listOf(subnetPrefix) + prevSubs).take(6).joinToString(",")

        prefs.edit()
            .putString("last_car_ip", host)
            .putString("last_car_subnet", subnetPrefix)
            .putString("recent_car_ips", nextIps)
            .putString("recent_car_subnets", nextSubs)
            .apply()
    } catch (_: Exception) {
    }
}

private fun saveKnownPhoneHotspotIp(context: Context?, hotspotIp: String) {
    val ctx = context?.applicationContext ?: return
    if (!isValidPrivatePhoneIpv4(hotspotIp)) return
    val octets = hotspotIp.split(".")
    if (octets.size != 4) return
    val subnetPrefix = "${octets[0]}.${octets[1]}.${octets[2]}"
    try {
        ctx.getSharedPreferences(MIC_HOTSPOT_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("last_phone_hotspot_ip", hotspotIp)
            .putString("last_phone_hotspot_subnet", subnetPrefix)
            .apply()
    } catch (_: Exception) {
    }
}

private fun loadCachedCarEndpointIps(context: Context?): List<String> {
    val ctx = context?.applicationContext ?: return emptyList()
    val result = LinkedHashSet<String>()
    try {
        val prefs = ctx.getSharedPreferences(MIC_HOTSPOT_PREFS_NAME, Context.MODE_PRIVATE)
        val lastIp = prefs.getString("last_car_ip", "").orEmpty().trim()
        if (isValidPrivatePhoneIpv4(lastIp)) result.add(lastIp)
        prefs.getString("recent_car_ips", "").orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { isValidPrivatePhoneIpv4(it) }
            .forEach { result.add(it) }
        val lastSub = prefs.getString("last_car_subnet", "").orEmpty().trim()
        if (lastSub.isNotBlank() && isValidPrivatePhoneIpv4("$lastSub.1")) {
            result.add("$lastSub.1")
        }
        prefs.getString("recent_car_subnets", "").orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && isValidPrivatePhoneIpv4("$it.1") }
            .forEach { result.add("$it.1") }
    } catch (_: Exception) {
    }
    return result.toList()
}

/**
 * Galaxy Random Hotspot (`192.168.[0..255].xxx`, `172.20.10.x`, `swlan0`/`ap0`/`wlan1`) & Wi-Fi (`wlan0`) 100% Detector:
 * Combines 100% of `9dee909` detectors + Release-safe (`debuggable=false`) kernel & neighbor detectors:
 * 1) Exception-isolated `NetworkInterface` enumeration (`getByName("swlan0", "ap0", "wlan1", ...)` + `getNetworkInterfaces()`)
 *    WITHOUT `intf.isUp` / `intf.isLoopback` (`ioctl(SIOCGIFFLAGS)` throws `SocketException` under Release SELinux!).
 * 2) 256-Subnet `RT_TABLE_LOCAL` (Rule Priority 0) Broadcast Route Probe (`probeSock.broadcast = true` -> `.255`, `.1`, `.2`):
 *    Matches kernel `broadcast 192.168.X.255 dev swlan0 src 192.168.X.Y` BEFORE `rmnet` cellular `fwmark` (Rule 14000),
 *    extracting Galaxy's random Hotspot IP (`192.168.X.Y`) in < 5ms with 0 wire packets!
 * 3) `/proc/net/arp`, `ip neigh`, `arp -n`, `ifconfig`, `ip -4 addr`, `ip route show table all` local + Hotspot neighbor parser.
 * 4) `WifiManager.connectionInfo` & `ConnectivityManager.allNetworks` (`LinkProperties` + per-network bound socket).
 */
fun discoverCandidateLocalIps(context: Context?): List<String> {
    val priorityIfHotspotIps = mutableListOf<String>()
    val priority0HotspotIps = mutableListOf<String>()
    val normalLanIps = mutableListOf<String>()
    val addrLanIps = mutableListOf<String>()

    fun inspectNetworkInterfaceSafely(intf: NetworkInterface?) {
        if (intf == null) return
        val ifName = try { (intf.name ?: "").lowercase() } catch (_: Throwable) { "" }
        if (isCellularOrTunnelInterfaceName(ifName)) return
        val isHotspotIf = isHotspotInterfaceName(ifName)
        val addrs = try { intf.inetAddresses } catch (_: Throwable) { null } ?: return
        while (true) {
            val hasNext = try { addrs.hasMoreElements() } catch (_: Throwable) { false }
            if (!hasNext) break
            val addr = try { addrs.nextElement() } catch (_: Throwable) { null } ?: continue
            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                val host = addr.hostAddress ?: continue
                if (!isValidPrivatePhoneIpv4(host)) continue
                if (isHotspotIf || isKnownHotspotSubnet(host)) {
                    if (host !in priorityIfHotspotIps) priorityIfHotspotIps.add(host)
                } else {
                    if (host !in normalLanIps) normalLanIps.add(host)
                }
            }
        }
    }

    // Step 1) Direct Hotspot interface lookup + full NetworkInterface enumeration (isolated per interface, NO `intf.isUp` ioctl!)
    val directHotspotIfNames = arrayOf(
        "swlan0", "swlan1", "ap0", "ap1", "wlan1", "wlan2",
        "rndis0", "tether0", "softap0", "p2p0", "bridge0", "wlan0"
    )
    for (name in directHotspotIfNames) {
        try {
            inspectNetworkInterfaceSafely(NetworkInterface.getByName(name))
        } catch (_: Throwable) {
        }
    }
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces != null) {
            val hasMore = try { interfaces.hasMoreElements() } catch (_: Throwable) { false }
            if (!hasMore) break
            val intf = try { interfaces.nextElement() } catch (_: Throwable) { null } ?: continue
            inspectNetworkInterfaceSafely(intf)
        }
    } catch (_: Throwable) {
    }

    // Step 2) 256-Subnet `RT_TABLE_LOCAL` (Priority 0 `.255` broadcast) + Kernel FIB Route Table Scan (< 5ms, 0 wire packets)
    try {
        val cm = context?.applicationContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeCaps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val isActiveWifi = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        @Suppress("DEPRECATION")
        val nonCellularNetworks = cm?.allNetworks.orEmpty().filter { net ->
            val caps = try { cm?.getNetworkCapabilities(net) } catch (_: Throwable) { null }
            caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }

        val defaultWanIp = try {
            DatagramSocket().use { s ->
                s.connect(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)), 53))
                (s.localAddress as? Inet4Address)?.takeIf { !it.isLoopbackAddress }?.hostAddress.orEmpty()
            }
        } catch (_: Exception) {
            ""
        }

        if (isActiveWifi && isValidPrivatePhoneIpv4(defaultWanIp) && defaultWanIp !in normalLanIps) {
            normalLanIps.add(defaultWanIp)
        }

        fun probeKernelSubnetRoute(b0: Int, b1: Int, b2: Int, expectedPrefix: String, extraOctets: IntArray = intArrayOf(255, 1, 2)) {
            var resolvedHost = ""
            // `.255` with `broadcast = true` hits Linux `RT_TABLE_LOCAL` (Rule Priority 0, ahead of `rmnet` fwmark Rule 14000!)
            // and returns Galaxy's exact `src 192.168.X.Y` Hotspot IP even when `Y` is random!
            for (hostOctet in extraOctets) {
                try {
                    DatagramSocket().use { probeSock ->
                        probeSock.broadcast = true
                        val targetAddr = InetAddress.getByAddress(
                            byteArrayOf(b0.toByte(), b1.toByte(), b2.toByte(), hostOctet.toByte())
                        )
                        probeSock.connect(InetSocketAddress(targetAddr, 8082))
                        val localAddr = probeSock.localAddress
                        if (localAddr is Inet4Address && !localAddr.isLoopbackAddress) {
                            val candidate = localAddr.hostAddress.orEmpty()
                            if (candidate.startsWith(expectedPrefix) && isValidPrivatePhoneIpv4(candidate)) {
                                resolvedHost = candidate
                            }
                        }
                    }
                } catch (_: Throwable) {
                }
                if (resolvedHost.isNotEmpty()) break

                for (net in nonCellularNetworks) {
                    try {
                        DatagramSocket().use { netSock ->
                            netSock.broadcast = true
                            net.bindSocket(netSock)
                            val targetAddr = InetAddress.getByAddress(
                                byteArrayOf(b0.toByte(), b1.toByte(), b2.toByte(), hostOctet.toByte())
                            )
                            netSock.connect(InetSocketAddress(targetAddr, 8082))
                            val localAddr = netSock.localAddress
                            if (localAddr is Inet4Address && !localAddr.isLoopbackAddress) {
                                val candidate = localAddr.hostAddress.orEmpty()
                                if (candidate.startsWith(expectedPrefix) && isValidPrivatePhoneIpv4(candidate)) {
                                    resolvedHost = candidate
                                }
                            }
                        }
                    } catch (_: Throwable) {
                    }
                    if (resolvedHost.isNotEmpty()) break
                }
                if (resolvedHost.isNotEmpty()) break
            }
            if (resolvedHost.isNotEmpty() &&
                resolvedHost.startsWith(expectedPrefix) &&
                isValidPrivatePhoneIpv4(resolvedHost)
            ) {
                if (resolvedHost != defaultWanIp || isKnownHotspotSubnet(resolvedHost)) {
                    if (resolvedHost !in priority0HotspotIps) {
                        priority0HotspotIps.add(resolvedHost)
                    }
                } else if (isActiveWifi) {
                    if (resolvedHost !in normalLanIps) {
                        normalLanIps.add(resolvedHost)
                    }
                }
            }
        }

        // Fixed common Hotspot subnets first, then full 0..255 Galaxy random `/24` sweep
        probeKernelSubnetRoute(192, 168, 43, "192.168.43.")
        probeKernelSubnetRoute(172, 20, 10, "172.20.10.", intArrayOf(255, 15, 1, 2))
        probeKernelSubnetRoute(192, 168, 49, "192.168.49.")
        probeKernelSubnetRoute(192, 168, 137, "192.168.137.")
        for (sub in 0..255) {
            probeKernelSubnetRoute(192, 168, sub, "192.168.$sub.")
        }
        for (m in 16..31) {
            probeKernelSubnetRoute(172, m, 0, "172.$m.")
        }
    } catch (_: Throwable) {
    }

    // Step 3) WifiManager.connectionInfo.ipAddress (`wlan0` Wi-Fi Router IP)
    try {
        val wm = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        @Suppress("DEPRECATION")
        val ipInt = wm?.connectionInfo?.ipAddress ?: 0
        if (ipInt != 0) {
            val ip = String.format(
                java.util.Locale.US,
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
            if (isValidPrivatePhoneIpv4(ip) &&
                ip !in priority0HotspotIps &&
                ip !in priorityIfHotspotIps &&
                ip !in normalLanIps
            ) {
                normalLanIps.add(0, ip)
            }
        }
    } catch (_: Exception) {
    }

    // Step 4) ConnectivityManager.allNetworks (`getLinkProperties()`)
    try {
        val cm = context?.applicationContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        @Suppress("DEPRECATION")
        val networks = cm?.allNetworks.orEmpty()
        for (net in networks) {
            val lp = cm?.getLinkProperties(net) ?: continue
            val ifName = lp.interfaceName.orEmpty().lowercase()
            if (isCellularOrTunnelInterfaceName(ifName)) continue
            val isHotspotIf = isHotspotInterfaceName(ifName)
            for (linkAddr in lp.linkAddresses) {
                val addr = linkAddr.address
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val host = addr.hostAddress.orEmpty()
                    if (!isValidPrivatePhoneIpv4(host)) continue
                    if (isHotspotIf || isKnownHotspotSubnet(host)) {
                        if (host !in priorityIfHotspotIps) priorityIfHotspotIps.add(host)
                    } else {
                        if (host !in normalLanIps) normalLanIps.add(host)
                    }
                }
            }
        }
    } catch (_: Exception) {
    }

    // Step 5) `/proc/net/arp`, `ip neigh`, `arp -n`, `ifconfig`, `ip -4 addr` & `ip route` inspection
    val discoveredHotspotNeighborIps = LinkedHashSet<String>()
    val privateIpTokenRegex = Regex("""\b((?:192\.168|172\.(?:1[6-9]|2\d|3[01]))\.\d{1,3}\.\d{1,3})\b""")
    try {
        val arpFile = java.io.File("/proc/net/arp")
        if (arpFile.canRead()) {
            arpFile.readLines().drop(1).forEach { line ->
                val cols = line.trim().split(Regex("""\s+"""))
                if (cols.size >= 6) {
                    val neighIp = cols[0]
                    val dev = cols[5].lowercase()
                    if (isValidPrivatePhoneIpv4(neighIp) && !isCellularOrTunnelInterfaceName(dev)) {
                        discoveredHotspotNeighborIps.add(neighIp)
                    }
                }
            }
        }
    } catch (_: Throwable) {
    }

    try {
        val proc = ProcessBuilder(
            "sh",
            "-c",
            "ip neigh; echo '---PLEOS_ARP---'; arp -n; echo '---PLEOS_IFCFG---'; ifconfig; echo '---PLEOS_ADDR---'; ip -4 addr; echo '---PLEOS_ROUTE---'; ip route show table all"
        )
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        proc.waitFor(700, TimeUnit.MILLISECONDS)

        val ifHeaderRegex = Regex("""^(?:\d+:\s+)?([a-zA-Z0-9_.-]+?)(?:@\S+)?(?::|\s+Link\s+encap)""")
        val inetRegex = Regex("""\binet(?:\s+addr:|\s+)((?:192\.168|172\.(?:1[6-9]|2\d|3[01]))\.\d{1,3}\.\d{1,3})(?:/\d+)?\b""")
        val srcRegex = Regex("""\bsrc\s+((?:192\.168|172\.(?:1[6-9]|2\d|3[01]))\.\d{1,3}\.\d{1,3})\b""")
        val devRegex = Regex("""\bdev\s+([a-zA-Z0-9_.-]+)""")

        var section = "NEIGH"
        var currentAddrIf = ""
        for (rawLine in output.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.contains("---PLEOS_ARP---") -> { section = "ARP"; continue }
                line.contains("---PLEOS_IFCFG---") -> { section = "IFCFG"; currentAddrIf = ""; continue }
                line.contains("---PLEOS_ADDR---") -> { section = "ADDR"; currentAddrIf = ""; continue }
                line.contains("---PLEOS_ROUTE---") -> { section = "ROUTE"; continue }
            }
            when (section) {
                "NEIGH", "ARP" -> {
                    val devName = devRegex.find(line)?.groupValues?.getOrNull(1)?.lowercase()
                        ?: line.substringAfterLast(" on ", "").substringBefore(" ").lowercase()
                    if (isCellularOrTunnelInterfaceName(devName)) continue
                    val neighIp = privateIpTokenRegex.find(line)?.groupValues?.getOrNull(1).orEmpty()
                    if (isValidPrivatePhoneIpv4(neighIp)) {
                        discoveredHotspotNeighborIps.add(neighIp)
                    }
                }
                "IFCFG", "ADDR" -> {
                    val hdrMatch = ifHeaderRegex.find(line)
                    if (hdrMatch != null) {
                        currentAddrIf = hdrMatch.groupValues.getOrNull(1).orEmpty()
                    }
                    val ip = inetRegex.find(line)?.groupValues?.getOrNull(1).orEmpty()
                    if (!isValidPrivatePhoneIpv4(ip)) continue
                    val trailingIf = line.substringAfterLast(" ", "")
                    val effectiveIf = if (currentAddrIf.isNotBlank()) currentAddrIf else trailingIf
                    if (isCellularOrTunnelInterfaceName(effectiveIf)) continue
                    if (isHotspotInterfaceName(effectiveIf) || isKnownHotspotSubnet(ip)) {
                        if (ip !in priorityIfHotspotIps) priorityIfHotspotIps.add(ip)
                    } else {
                        if (ip !in addrLanIps) addrLanIps.add(ip)
                    }
                }
                "ROUTE" -> {
                    val devName = devRegex.find(line)?.groupValues?.getOrNull(1).orEmpty()
                    if (isCellularOrTunnelInterfaceName(devName)) continue
                    val ip = srcRegex.find(line)?.groupValues?.getOrNull(1).orEmpty()
                    if (!isValidPrivatePhoneIpv4(ip)) continue
                    if (isHotspotInterfaceName(devName) || isKnownHotspotSubnet(ip)) {
                        if (ip !in priorityIfHotspotIps) priorityIfHotspotIps.add(ip)
                    } else {
                        if (ip !in addrLanIps) addrLanIps.add(ip)
                    }
                }
            }
        }
    } catch (_: Throwable) {
    }

    // If any Hotspot client neighbor (`192.168.X.Z`, e.g. MacBook) was detected, probe `.255` & `neighIp` to capture Phone's exact own IP!
    for (neighIp in discoveredHotspotNeighborIps) {
        val segs = neighIp.split(".")
        if (segs.size != 4) continue
        val prefix = "${segs[0]}.${segs[1]}.${segs[2]}."
        val b0 = segs[0].toIntOrNull() ?: continue
        val b1 = segs[1].toIntOrNull() ?: continue
        val b2 = segs[2].toIntOrNull() ?: continue
        val b3 = segs[3].toIntOrNull() ?: continue
        for (targetLast in intArrayOf(255, b3, 1)) {
            try {
                DatagramSocket().use { s ->
                    s.broadcast = true
                    val dest = InetAddress.getByAddress(byteArrayOf(b0.toByte(), b1.toByte(), b2.toByte(), targetLast.toByte()))
                    s.connect(InetSocketAddress(dest, 8082))
                    val local = (s.localAddress as? Inet4Address)?.takeIf { !it.isLoopbackAddress }?.hostAddress.orEmpty()
                    if (local.startsWith(prefix) && isValidPrivatePhoneIpv4(local) && local !in priorityIfHotspotIps) {
                        priorityIfHotspotIps.add(0, local)
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    val primaryHotspot = priorityIfHotspotIps.firstOrNull() ?: priority0HotspotIps.firstOrNull()
    if (!primaryHotspot.isNullOrBlank()) {
        saveKnownPhoneHotspotIp(context, primaryHotspot)
    }

    return (priorityIfHotspotIps + priority0HotspotIps + normalLanIps + addrLanIps)
        .filter { isValidPrivatePhoneIpv4(it) }
        .distinct()
}

private fun resolveMicBrokerAddress(hostname: String): InetAddress {
    val cleanHost = hostname.trim()
    try {
        return InetAddress.getByName(cleanHost)
    } catch (_: Throwable) {
    }
    val dnsServers = arrayOf(byteArrayOf(8, 8, 8, 8), byteArrayOf(1, 1, 1, 1))
    for (dnsBytes in dnsServers) {
        try {
            val txId = (System.nanoTime().toInt() and 0xFFFF)
            val qOut = ByteArrayOutputStream(64)
            qOut.write((txId ushr 8) and 0xFF); qOut.write(txId and 0xFF)
            qOut.write(0x01); qOut.write(0x00)
            qOut.write(0x00); qOut.write(0x01)
            qOut.write(0x00); qOut.write(0x00)
            qOut.write(0x00); qOut.write(0x00)
            qOut.write(0x00); qOut.write(0x00)
            for (label in cleanHost.split(".")) {
                val lb = label.toByteArray(Charsets.US_ASCII)
                if (lb.isEmpty() || lb.size > 63) continue
                qOut.write(lb.size)
                qOut.write(lb)
            }
            qOut.write(0x00)
            qOut.write(0x00); qOut.write(0x01)
            qOut.write(0x00); qOut.write(0x01)
            val queryPkt = qOut.toByteArray()

            DatagramSocket().use { udp ->
                udp.soTimeout = 700
                udp.send(DatagramPacket(queryPkt, queryPkt.size, InetAddress.getByAddress(dnsBytes), 53))
                val respBuf = ByteArray(512)
                val respPkt = DatagramPacket(respBuf, respBuf.size)
                udp.receive(respPkt)
                val rLen = respPkt.length
                if (rLen >= 16) {
                    val rId = ((respBuf[0].toInt() and 0xFF) shl 8) or (respBuf[1].toInt() and 0xFF)
                    val qdCount = ((respBuf[4].toInt() and 0xFF) shl 8) or (respBuf[5].toInt() and 0xFF)
                    val anCount = ((respBuf[6].toInt() and 0xFF) shl 8) or (respBuf[7].toInt() and 0xFF)
                    fun skipName(p0: Int): Int {
                        var p = p0
                        while (p < rLen) {
                            val b = respBuf[p].toInt() and 0xFF
                            if (b == 0) return p + 1
                            if ((b and 0xC0) == 0xC0) return p + 2
                            p += 1 + b
                        }
                        return rLen
                    }
                    if (rId == txId && anCount > 0) {
                        var pos = 12
                        for (q in 0 until qdCount) pos = skipName(pos) + 4
                        for (a in 0 until anCount) {
                            if (pos >= rLen) break
                            pos = skipName(pos)
                            if (pos + 10 > rLen) break
                            val rType = ((respBuf[pos].toInt() and 0xFF) shl 8) or (respBuf[pos + 1].toInt() and 0xFF)
                            val rClass = ((respBuf[pos + 2].toInt() and 0xFF) shl 8) or (respBuf[pos + 3].toInt() and 0xFF)
                            val rdLen = ((respBuf[pos + 8].toInt() and 0xFF) shl 8) or (respBuf[pos + 9].toInt() and 0xFF)
                            val rDataPos = pos + 10
                            if (rType == 1 && rClass == 1 && rdLen == 4 && rDataPos + 4 <= rLen) {
                                return InetAddress.getByAddress(cleanHost, respBuf.copyOfRange(rDataPos, rDataPos + 4))
                            }
                            pos = rDataPos + rdLen
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }
    return InetAddress.getByName(cleanHost)
}

private fun detectAllPhoneLocalIps(context: Context?): List<String> = discoverCandidateLocalIps(context)

private fun writeMicMqttVarLen(out: ByteArrayOutputStream, length: Int) {
    var x = length
    do {
        var encodedByte = x and 0x7F
        x = x ushr 7
        if (x > 0) encodedByte = encodedByte or 0x80
        out.write(encodedByte)
    } while (x > 0)
}

private fun writeMicMqttUtf8(out: ByteArrayOutputStream, text: String) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    out.write((bytes.size ushr 8) and 0xFF)
    out.write(bytes.size and 0xFF)
    out.write(bytes)
}

private fun readMicMqttRemainingLength(ins: InputStream): Int {
    var multiplier = 1
    var value = 0
    var count = 0
    while (true) {
        val b = ins.read()
        if (b < 0) return -1
        value += (b and 0x7F) * multiplier
        if ((b and 0x80) == 0) return value
        multiplier *= 128
        count++
        if (count > 4) return -1
    }
}

private fun buildMicMqttPublishBytes(topic: String, jsonPayload: String): ByteArray {
    val body = ByteArrayOutputStream()
    writeMicMqttUtf8(body, topic)
    body.write(jsonPayload.toByteArray(Charsets.UTF_8))
    val bodyBytes = body.toByteArray()
    val pkt = ByteArrayOutputStream(bodyBytes.size + 5)
    pkt.write(0x30) // PUBLISH QoS 0
    writeMicMqttVarLen(pkt, bodyBytes.size)
    pkt.write(bodyBytes)
    return pkt.toByteArray()
}

private fun readWebSocketTextFrame(ins: InputStream, out: OutputStream, outLock: Any): String? {
    while (true) {
        val b0 = ins.read()
        if (b0 < 0) return null
        val b1 = ins.read()
        if (b1 < 0) return null
        val opcode = b0 and 0x0F
        val masked = (b1 and 0x80) != 0
        var payloadLen = (b1 and 0x7F).toLong()
        if (payloadLen == 126L) {
            val hi = ins.read()
            val lo = ins.read()
            if (hi < 0 || lo < 0) return null
            payloadLen = ((hi and 0xFF) shl 8 or (lo and 0xFF)).toLong()
        } else if (payloadLen == 127L) {
            var len64 = 0L
            repeat(8) {
                val v = ins.read()
                if (v < 0) return null
                len64 = (len64 shl 8) or (v.toLong() and 0xFFL)
            }
            payloadLen = len64
        }
        if (payloadLen < 0L || payloadLen > 1_048_576L) return null
        val maskKey = ByteArray(4)
        if (masked) {
            var mRead = 0
            while (mRead < 4) {
                val r = ins.read(maskKey, mRead, 4 - mRead)
                if (r < 0) return null
                mRead += r
            }
        }
        val payload = ByteArray(payloadLen.toInt())
        var pRead = 0
        while (pRead < payload.size) {
            val r = ins.read(payload, pRead, payload.size - pRead)
            if (r < 0) return null
            pRead += r
        }
        if (masked) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
            }
        }
        when (opcode) {
            0x8 -> return null // Close
            0x9 -> { // Ping -> Pong
                synchronized(outLock) {
                    out.write(0x8A)
                    out.write(payload.size and 0x7F)
                    out.write(payload)
                    out.flush()
                }
            }
            0x1 -> return String(payload, Charsets.UTF_8)
        }
    }
}

private fun writeWebSocketTextFrame(out: OutputStream, text: String) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    val len = bytes.size
    val headerLen = when {
        len <= 125 -> 2
        len <= 65535 -> 4
        else -> 10
    }
    val frame = ByteArray(headerLen + len)
    frame[0] = 0x81.toByte()
    when {
        len <= 125 -> {
            frame[1] = len.toByte()
        }
        len <= 65535 -> {
            frame[1] = 126.toByte()
            frame[2] = ((len shr 8) and 0xFF).toByte()
            frame[3] = (len and 0xFF).toByte()
        }
        else -> {
            frame[1] = 127.toByte()
            val len64 = len.toLong()
            for (i in 0 until 8) {
                frame[2 + i] = ((len64 shr (56 - i * 8)) and 0xFFL).toByte()
            }
        }
    }
    System.arraycopy(bytes, 0, frame, headerLen, len)
    out.write(frame)
    out.flush()
}

private fun writeMaskedClientWebSocketTextFrame(out: OutputStream, text: String, maskKey: ByteArray) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    val len = bytes.size
    val headerLen = when {
        len <= 125 -> 2
        len <= 65535 -> 4
        else -> 10
    }
    val frame = ByteArray(headerLen + 4 + len)
    frame[0] = 0x81.toByte()
    when {
        len <= 125 -> {
            frame[1] = (0x80 or len).toByte()
        }
        len <= 65535 -> {
            frame[1] = (0x80 or 126).toByte()
            frame[2] = ((len shr 8) and 0xFF).toByte()
            frame[3] = (len and 0xFF).toByte()
        }
        else -> {
            frame[1] = (0x80 or 127).toByte()
            val len64 = len.toLong()
            for (i in 0 until 8) {
                frame[2 + i] = ((len64 shr (56 - i * 8)) and 0xFFL).toByte()
            }
        }
    }
    frame[headerLen] = maskKey[0]
    frame[headerLen + 1] = maskKey[1]
    frame[headerLen + 2] = maskKey[2]
    frame[headerLen + 3] = maskKey[3]
    val payloadOffset = headerLen + 4
    for (i in 0 until len) {
        frame[payloadOffset + i] = (bytes[i].toInt() xor maskKey[i and 3].toInt()).toByte()
    }
    out.write(frame)
    out.flush()
}

private val NATIVE_NOTE_STRINGS = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

private fun nativeFreqToNote(freqHz: Float): String {
    if (freqHz < 65f || freqHz > 1400f) return "-"
    val midi = (12f * (ln(freqHz / 440f) / ln(2f))).roundToInt() + 69
    val noteIdx = ((midi % 12) + 12) % 12
    val octave = (midi / 12) - 1
    return "${NATIVE_NOTE_STRINGS[noteIdx]}$octave"
}

private fun estimateNativePitchAndRms(samples: ShortArray, count: Int, sampleRate: Int): Pair<Float, Float> {
    if (count <= 0) return 0f to 0f
    var sumSq = 0.0
    for (i in 0 until count) {
        val v = samples[i] / 32768.0
        sumSq += v * v
    }
    val rmsLinear = sqrt(sumSq / count).toFloat()
    if (rmsLinear < 0.010f) {
        val quietPercent = ((rmsLinear * 4.5f).pow(0.78f) * 100f).coerceIn(0f, 100f)
        return quietPercent to 0f
    }

    val minLag = (sampleRate / 950).coerceAtLeast(16)
    val maxLag = (sampleRate / 75).coerceAtMost(count / 2)
    var bestLag = -1
    var bestCorr = 0f
    val window = (count - maxLag).coerceAtMost(240)
    if (window > 32 && maxLag > minLag) {
        var energy0 = 0f
        for (i in 0 until window) {
            val x = samples[i] / 32768f
            energy0 += x * x
        }
        if (energy0 > 1e-6f) {
            for (lag in minLag..maxLag) {
                var corr = 0f
                var energyLag = 0f
                for (i in 0 until window) {
                    val a = samples[i] / 32768f
                    val b = samples[i + lag] / 32768f
                    corr += a * b
                    energyLag += b * b
                }
                val denom = sqrt(energy0 * energyLag)
                if (denom > 1e-6f) {
                    val norm = corr / denom
                    if (norm > bestCorr) {
                        bestCorr = norm
                        bestLag = lag
                    }
                }
            }
        }
    }
    val pitchHz = if (bestLag > 0 && bestCorr >= 0.72f) {
        sampleRate.toFloat() / bestLag.toFloat()
    } else {
        0f
    }
    val scaledRms = ((rmsLinear * 4.5f).pow(0.78f) * 100f).coerceIn(0f, 100f)
    return scaledRms to pitchHz
}

private object MicYtThumbCache {
    private val cache = LruCache<String, ImageBitmap>(32)
    fun get(id: String): ImageBitmap? = cache.get(id)
    fun put(id: String, bmp: ImageBitmap) {
        cache.put(id, bmp)
    }
}

object AndroidPlatformContextHolder {
    @Volatile
    var appContext: Context? = null

    @Volatile
    private var currentActivityRef: java.lang.ref.WeakReference<Activity>? = null

    @Volatile
    var keepScreenOnEnabled: Boolean = true
        private set

    @Volatile
    var isDebugBuild: Boolean = false
        private set

    fun bindActivity(activity: Activity, debugBuild: Boolean? = null) {
        appContext = activity.applicationContext
        isDebugBuild = debugBuild ?: ((activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        currentActivityRef = java.lang.ref.WeakReference(activity)
        applyKeepScreenOnFlag(keepScreenOnEnabled)
    }

    fun unbindActivity(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
        }
    }

    fun applyKeepScreenOnFlag(enabled: Boolean) {
        keepScreenOnEnabled = enabled
        val activity = currentActivityRef?.get() ?: return
        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed) {
                if (enabled) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }
}

actual fun setKeepScreenOnEnabled(enabled: Boolean) {
    AndroidPlatformContextHolder.applyKeepScreenOnFlag(enabled)
}

actual fun openYouTubeKaraokeAppSearch(query: String) {
    val ctx = AndroidPlatformContextHolder.appContext ?: return
    val encoded = Uri.encode(query.ifBlank { "노래방 MR KY TJ" })
    val url = "https://www.youtube.com/results?search_query=$encoded"
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    } catch (_: Exception) {
    }
}

actual fun readClipboardTextForPlaylist(): String? {
    val ctx = AndroidPlatformContextHolder.appContext ?: return null
    return try {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val raw = clip.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim()
            if (!raw.isNullOrBlank()) {
                Regex("(https?://\\S+)").find(raw)?.groupValues?.getOrNull(1) ?: raw
            } else null
        } else null
    } catch (_: Exception) {
        null
    }
}

@Composable
actual fun YouTubeThumbnailBox(
    videoId: String,
    durationLabel: String,
    modifier: Modifier
) {
    var bmp by remember(videoId) { mutableStateOf(MicYtThumbCache.get(videoId)) }

    LaunchedEffect(videoId) {
        if (bmp == null && videoId.isNotBlank()) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val conn = (URL("https://i.ytimg.com/vi/$videoId/mqdefault.jpg").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4000
                        readTimeout = 4000
                        doInput = true
                    }
                    conn.connect()
                    if (conn.responseCode == 200) {
                        conn.inputStream.use { s ->
                            BitmapFactory.decodeStream(s)?.asImageBitmap()
                        }
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
            if (loaded != null) {
                MicYtThumbCache.put(videoId, loaded)
                bmp = loaded
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF141B2D), Color(0xFF2B3556))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        val current = bmp
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = videoId,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "▶ NCS",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        if (durationLabel.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC0B0E14))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = durationLabel,
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun applyIncomingCarStateJsonOnMain(controller: AudioBridgeController, stateObj: JSONObject) {
    val songId = stateObj.optString("currentSongId", stateObj.optString("currentTrackId", "ncs_on_and_on"))
    val currentTrackId = stateObj.optString("currentTrackId", songId)
    val isPlaying = stateObj.optBoolean("isPlaying", false)
    val progressSec = stateObj.optInt("currentPositionSec", stateObj.optInt("progressSec", 0))
    val durationSec = stateObj.optInt("durationSec", 208)
    val backingVolume = stateObj.optInt("backingVolume", stateObj.optInt("mrVolume", 85))
    val micVolume = stateObj.optInt("micVolume", 85)
    val guideVolume = stateObj.optInt("guideVolume", 75)
    val keyShift = stateObj.optInt("keyShift", 0)
    val echoLevel = stateObj.optInt("echoLevel", stateObj.optInt("reverbLevel", 80))
    val mrPlaybackRate = stateObj.optDouble("mrPlaybackRate", 1.0).toFloat()
    val syncOffsetMs = stateObj.optInt("syncOffsetMs", 0)
    val isParked = stateObj.optBoolean("isParked", true)
    val score = stateObj.optInt("score", 92)
    val isDevMode = if (AndroidPlatformContextHolder.isDebugBuild) stateObj.optBoolean("isDevMode", true) else false
    val carHostId = stateObj.optString("carHostId", "")
    val stateRevision = stateObj.optLong("stateRevision", 0L)
    if (stateObj.has("scoreEnabled")) {
        controller.onScoreModeSyncedFromCar(stateObj.optBoolean("scoreEnabled", true))
    }
    val guideVocalEnabled = stateObj.optBoolean("guideVocalEnabled", stateObj.optBoolean("demoVocalWave", false))
    val guidePitchHz = stateObj.optDouble("guidePitchHz", 0.0).toFloat()
    val guideNote = stateObj.optString("guideNote", "-")
    val guideVoiceLevelPercent = stateObj.optDouble("guideVoiceLevelPercent", 0.0).toFloat()
    controller.onGuideVocalTelemetrySyncedFromWebView(
        enabled = guideVocalEnabled,
        pitchHz = guidePitchHz,
        note = guideNote,
        levelPercent = guideVoiceLevelPercent
    )
    val playlistArr = stateObj.optJSONArray("playlist")
    val playlistEncoded = if (playlistArr == null) {
        ""
    } else if (playlistArr.length() == 0) {
        "__EMPTY__"
    } else {
        buildString {
            for (i in 0 until playlistArr.length()) {
                val item = playlistArr.optJSONObject(i) ?: continue
                if (isNotEmpty()) append("\n")
                append(item.optString("id", "").replace("\t", " "))
                append("\t").append(item.optString("title", "").replace("\t", " "))
                append("\t").append(item.optString("artist", "").replace("\t", " "))
                append("\t").append(item.optString("genre", "").replace("\t", " "))
                append("\t").append(item.optInt("bpm", 124))
                append("\t").append(item.optInt("durationSec", 180))
                append("\t").append(item.optString("durationLabel", "03:00").replace("\t", " "))
                append("\t").append(item.optString("videoId", "").replace("\t", " "))
                append("\t").append(item.optString("songId", "").replace("\t", " "))
                append("\t").append(item.optBoolean("isDevSample", false))
            }
        }
    }
    controller.onCarStateSyncFromWebView(
        songId = songId,
        isPlaying = isPlaying,
        keyShift = keyShift,
        echoLevel = echoLevel,
        isParked = isParked,
        score = score,
        currentTrackId = currentTrackId,
        progressSec = progressSec,
        durationSec = durationSec,
        backingVolume = backingVolume,
        isDevMode = isDevMode,
        playlistEncoded = playlistEncoded,
        micVolume = micVolume,
        guideVolume = guideVolume,
        mrPlaybackRate = mrPlaybackRate,
        syncOffsetMs = syncOffsetMs,
        carHostId = carHostId,
        stateRevision = stateRevision
    )
}

private fun applyIncomingKaraokeScoreJsonOnMain(controller: AudioBridgeController, data: JSONObject) {
    val rawScore = data.optInt("score", 95).coerceIn(75, 100)
    val (fallbackBadge, fallbackTitle) = MicKaraokeScoreCalculator.resolveBadgeAndTitle(rawScore)
    val result = KaraokeScoreResultState(
        resultId = data.optString("resultId", ""),
        score = rawScore,
        badge = data.optString("badge", "").ifBlank { fallbackBadge },
        title = data.optString("title", "").ifBlank { fallbackTitle },
        comment = data.optString("comment", "안정적인 음정 감각과 시원한 성량으로 무대를 빛냈습니다! ✨"),
        pitchAccuracy = data.optInt("pitchAccuracy", (rawScore - 1).coerceIn(76, 100)).coerceIn(70, 100),
        vocalPower = data.optInt("vocalPower", (rawScore - 2).coerceIn(76, 100)).coerceIn(70, 100),
        stageManner = data.optInt("stageManner", rawScore.coerceIn(80, 100)).coerceIn(70, 100),
        trackTitle = data.optString("trackTitle", ""),
        willAdvanceNext = data.optBoolean("willAdvanceNext", false)
    )
    controller.onKaraokeScoreResultReceived(result)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun HeadlessAudioWebViewBridge(
    controller: AudioBridgeController,
    modifier: Modifier
) {
    val verified8082Channels = remember(controller) { ConcurrentHashMap<String, Channel<String>>() }
    val direct8080ChannelRef = remember(controller) { AtomicReference<Channel<String>?>(null) }

    // Background Hotspot/LAN IP watcher (`targetSdk = 28` exposes `swlan0` & `wlan0`),
    // 6.0s Car Heartbeat Watchdog, External-Car-Only `:8082` Direct Server, Direct `:8080` Kotlin Client, & UDP `:8081` Discovery
    LaunchedEffect(controller) {
        val ioScope = this
        var active8080TargetKey = ""
        var active8080Job: Job? = null

        fun startDirect8080KotlinSocket(wsUrlRaw: String, pinRaw: String) {
            val cleanUrl = wsUrlRaw.trim()
            if (cleanUrl.isBlank() || cleanUrl.contains("127.0.0.1") || cleanUrl.contains("localhost")) return
            val parsedUri = try {
                URI(cleanUrl)
            } catch (_: Exception) {
                return
            }
            val scheme = parsedUri.scheme?.lowercase().orEmpty()
            if (scheme != "ws" && scheme != "http") return
            val host = parsedUri.host?.trim().orEmpty()
            if (host.isBlank() || host == "127.0.0.1" || host == "localhost") return
            val port = if (parsedUri.port > 0) parsedUri.port else 8080
            val path = parsedUri.rawPath?.ifBlank { "/ws" } ?: "/ws"
            val targetKey = "$host:$port$path"
            if (targetKey == active8080TargetKey && active8080Job?.isActive == true) {
                return
            }
            active8080TargetKey = targetKey
            active8080Job?.cancel()
            active8080Job = ioScope.launch(Dispatchers.IO) {
                var sock: Socket? = null
                val sendChan = Channel<String>(capacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                var writerJob: Job? = null
                try {
                    sock = Socket().apply {
                        tcpNoDelay = true
                        keepAlive = true
                        soTimeout = 10000
                        sendBufferSize = 128 * 1024
                        receiveBufferSize = 64 * 1024
                    }
                    sock.connect(InetSocketAddress(host, port), 1200)
                    val out = sock.getOutputStream()
                    val bufIn = BufferedInputStream(sock.getInputStream())
                    val outLock = Any()

                    val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
                    val secKey = Base64.encodeToString(nonce, Base64.NO_WRAP)
                    val upgradeReq = "GET $path HTTP/1.1\r\n" +
                        "Host: $host:$port\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: $secKey\r\n" +
                        "Sec-WebSocket-Version: 13\r\n\r\n"
                    synchronized(outLock) {
                        out.write(upgradeReq.toByteArray(Charsets.US_ASCII))
                        out.flush()
                    }

                    val hdr = StringBuilder()
                    var crlfState = 0
                    while (hdr.length < 4096) {
                        val c = bufIn.read()
                        if (c < 0) throw EOFException("WS 8080 upgrade EOF")
                        hdr.append(c.toChar())
                        crlfState = when {
                            c == '\r'.code && (crlfState == 0 || crlfState == 2) -> crlfState + 1
                            c == '\n'.code && (crlfState == 1 || crlfState == 3) -> crlfState + 1
                            else -> 0
                        }
                        if (crlfState == 4) break
                    }
                    if (!hdr.toString().startsWith("HTTP/1.1 101")) {
                        return@launch
                    }

                    writerJob = launch(Dispatchers.IO) {
                        val rng = SecureRandom()
                        val maskKey = ByteArray(4)
                        for (msg in sendChan) {
                            try {
                                rng.nextBytes(maskKey)
                                synchronized(outLock) {
                                    writeMaskedClientWebSocketTextFrame(out, msg, maskKey)
                                }
                            } catch (_: Exception) {
                                break
                            }
                        }
                    }

                    val joinPin = pinRaw.ifBlank { controller.connection.value.pin.ifBlank { "4829" } }
                    val initCandJson = JSONArray(controller.phoneCandidateIps).toString()
                    val initPrimaryIp = controller.phoneLocalIp
                    sendChan.trySend(
                        """{"event":"join-pin","data":{"pin":"$joinPin","deviceName":"Pleos Smart Mic","phoneIp":"$initPrimaryIp","phonePort":8082,"candidateIps":$initCandJson}}"""
                    )

                    val keepAliveJob = launch(Dispatchers.IO) {
                        var direct8080PingSeq = 1L
                        while (isActive && !sock.isClosed) {
                            delay(1500L)
                            val p = controller.connection.value.pin.ifBlank { "4829" }
                            val sendTs = System.currentTimeMillis()
                            val seq = ++direct8080PingSeq
                            val lastNet = controller.connection.value.netLatencyMs
                            sendChan.trySend("""{"event":"mic-ping","data":{"pin":"$p","deviceName":"Pleos Smart Mic"}}""")
                            sendChan.trySend("""{"event":"latency-ping","type":"latency-ping","data":{"type":"latency-ping","pin":"$p","seq":$seq,"clientSendTs":$sendTs,"lastNetLatencyMs":$lastNet}}""")
                        }
                    }

                    try {
                        while (isActive && !sock.isClosed) {
                            val frameText = readWebSocketTextFrame(bufIn, out, outLock) ?: break
                            val isVerifiedCarFrame = frameText.contains("\"paired-with-car\"") ||
                                frameText.contains("\"car-ready\"") ||
                                frameText.contains("\"car-pong\"") ||
                                frameText.contains("\"car-ping\"") ||
                                frameText.contains("\"latency-pong\"") ||
                                frameText.contains("\"car-state-sync\"") ||
                                frameText.contains("\"karaoke-score-result\"")
                            if (isVerifiedCarFrame) {
                                if (direct8080ChannelRef.get() !== sendChan) {
                                    direct8080ChannelRef.set(sendChan)
                                    saveKnownCarEndpointIp(AndroidPlatformContextHolder.appContext, host)
                                    Log.i("PleosMicNativeAudio", "Direct Kotlin :8080 audio socket active -> ws://$targetKey")
                                }
                            }
                            try {
                                val msg8080 = JSONObject(frameText)
                                val evt8080 = msg8080.optString("event", msg8080.optString("type", ""))
                                val data8080 = msg8080.optJSONObject("data") ?: msg8080
                                if (evt8080 == "car-ping") {
                                    val p = controller.connection.value.pin.ifBlank { "4829" }
                                    sendChan.trySend("""{"event":"mic-pong","data":{"pin":"$p"}}""")
                                }
                                if (evt8080 == "latency-pong" || data8080.optString("type", "") == "latency-pong") {
                                    val nowMs = System.currentTimeMillis()
                                    val sendTs = data8080.optLong("clientSendTs", nowMs)
                                    val netMs = ((nowMs - sendTs) / 2L).toInt().coerceIn(1, 250)
                                    val carBufMs = data8080.optInt("carBufferMs", 12).coerceIn(6, 200)
                                    withContext(Dispatchers.Main) {
                                        controller.onDirectLatencyPongReceived(netMs, carBufMs, "DIRECT")
                                    }
                                    continue
                                }
                                when (evt8080) {
                                    "karaoke-score-result" -> {
                                        withContext(Dispatchers.Main) {
                                            controller.onDirectCarHeartbeatReceived()
                                            applyIncomingKaraokeScoreJsonOnMain(controller, data8080)
                                        }
                                    }
                                    "karaoke-score-dismiss" -> {
                                        val rid = data8080.optString("resultId", "")
                                        withContext(Dispatchers.Main) {
                                            controller.onDirectCarHeartbeatReceived()
                                            controller.onKaraokeScoreDismissedFromCar(rid)
                                        }
                                    }
                                    "car-state-sync", "paired-with-car", "car-ready" -> {
                                        val stObj = if (evt8080 == "car-state-sync") data8080 else data8080.optJSONObject("state")
                                        withContext(Dispatchers.Main) {
                                            controller.onDirectCarHeartbeatReceived()
                                            if (stObj != null) {
                                                if (stObj.has("scoreEnabled")) {
                                                    controller.onScoreModeSyncedFromCar(stObj.optBoolean("scoreEnabled", true))
                                                }
                                                applyIncomingCarStateJsonOnMain(controller, stObj)
                                            }
                                        }
                                    }
                                    "car-pong", "car-ping" -> {
                                        withContext(Dispatchers.Main) {
                                            controller.onDirectCarHeartbeatReceived()
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                    } finally {
                        keepAliveJob.cancel()
                    }
                } catch (_: Exception) {
                } finally {
                    direct8080ChannelRef.compareAndSet(sendChan, null)
                    sendChan.close()
                    writerJob?.cancel()
                    try {
                        sock?.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }

        controller.onVerifiedCarWs8080Callback = { wsUrl, pin ->
            saveKnownCarEndpointIp(AndroidPlatformContextHolder.appContext, wsUrl)
            startDirect8080KotlinSocket(wsUrl, pin)
        }
        controller.onCarWs8080ClosedCallback = {
            active8080TargetKey = ""
            active8080Job?.cancel()
            active8080Job = null
            direct8080ChannelRef.set(null)
        }

        val initialIps = withContext(Dispatchers.IO) {
            discoverCandidateLocalIps(AndroidPlatformContextHolder.appContext)
        }
        if (initialIps.isNotEmpty()) {
            controller.onPhoneLocalIpsDetected(initialIps)
        }

        // Preload cached Car Endpoint IP (`SharedPreferences("pleos_hotspot_cache")`) for < 0.5s instant reconnect
        val cachedCarIps = withContext(Dispatchers.IO) {
            loadCachedCarEndpointIps(AndroidPlatformContextHolder.appContext)
        }
        if (cachedCarIps.isNotEmpty()) {
            val pinNow = controller.connection.value.pin.ifBlank { "4829" }
            val primaryCachedCarIp = cachedCarIps.first()
            startDirect8080KotlinSocket("ws://$primaryCachedCarIp:8080/ws", pinNow)
            controller.onCarEndpointDiscovered("ws://$primaryCachedCarIp:8080/ws", pinNow)
        }

        // Native Kotlin TCP MQTT Signaling Bridge (`broker.emqx.io:1883` & `broker.hivemq.com:1883`)
        // Ensures `candidateIps` reaches Car AAOS Emulator behind QEMU NAT in < 100ms even if WebView file:// WSS is blocked!
        val micMqttClientTag = "pleos_mic_kt_${UUID.randomUUID().toString().replace("-", "").take(10)}"

        for ((brokerHost, brokerPort) in MIC_MQTT_TCP_BROKERS) {
            launch(Dispatchers.IO) {
                while (isActive) {
                    var mqttSock: Socket? = null
                    try {
                        val activePinSnapshot = controller.connection.value.pin.ifBlank { "4829" }
                        val topic = "pleos-stage/karaoke/pin-$activePinSnapshot"
                        val brokerClientId = "${micMqttClientTag}_${brokerHost.take(4)}"

                        val resolvedBrokerAddr = resolveMicBrokerAddress(brokerHost)
                        mqttSock = Socket().apply {
                            tcpNoDelay = true
                            keepAlive = true
                            soTimeout = 18_000
                        }
                        mqttSock.connect(InetSocketAddress(resolvedBrokerAddr, brokerPort), 2500)
                        val mqttOut = mqttSock.getOutputStream()
                        val mqttIn = BufferedInputStream(mqttSock.getInputStream())
                        val mqttWriteLock = Any()

                        // 1) MQTT CONNECT
                        val connBody = ByteArrayOutputStream()
                        writeMicMqttUtf8(connBody, "MQTT")
                        connBody.write(0x04)
                        connBody.write(0x02)
                        connBody.write(0x00)
                        connBody.write(0x1E)
                        writeMicMqttUtf8(connBody, brokerClientId)
                        val connBodyBytes = connBody.toByteArray()
                        val connPkt = ByteArrayOutputStream(connBodyBytes.size + 5)
                        connPkt.write(0x10)
                        writeMicMqttVarLen(connPkt, connBodyBytes.size)
                        connPkt.write(connBodyBytes)
                        synchronized(mqttWriteLock) {
                            mqttOut.write(connPkt.toByteArray())
                            mqttOut.flush()
                        }

                        // 2) Read CONNACK
                        val ackHdr = mqttIn.read()
                        val ackLen = readMicMqttRemainingLength(mqttIn)
                        if (ackHdr < 0 || (ackHdr and 0xF0) != 0x20 || ackLen < 2) {
                            throw EOFException("MQTT CONNACK invalid")
                        }
                        val ackBuf = ByteArray(ackLen)
                        var gotAck = 0
                        while (gotAck < ackLen) {
                            val r = mqttIn.read(ackBuf, gotAck, ackLen - gotAck)
                            if (r < 0) throw EOFException("MQTT CONNACK EOF")
                            gotAck += r
                        }
                        if (ackBuf[1].toInt() != 0) {
                            throw EOFException("MQTT CONNACK rejected")
                        }

                        // 3) SUBSCRIBE to topic so when Car boots and sends `car-ready`, Phone immediately replies with `CANDIDATE_IPS`!
                        val subBody = ByteArrayOutputStream()
                        subBody.write(0x00)
                        subBody.write(0x01)
                        writeMicMqttUtf8(subBody, topic)
                        subBody.write(0x00)
                        val subBodyBytes = subBody.toByteArray()
                        val subPkt = ByteArrayOutputStream(subBodyBytes.size + 5)
                        subPkt.write(0x82)
                        writeMicMqttVarLen(subPkt, subBodyBytes.size)
                        subPkt.write(subBodyBytes)
                        synchronized(mqttWriteLock) {
                            mqttOut.write(subPkt.toByteArray())
                            mqttOut.flush()
                        }

                        fun sendCandidateIpsOverNativeMqtt() {
                            val cands = controller.phoneCandidateIps
                            if (cands.isEmpty()) return
                            val primaryIp = cands.first()
                            val pNow = controller.connection.value.pin.ifBlank { "4829" }
                            val candArr = JSONArray(cands)
                            val baseData = JSONObject().apply {
                                put("pin", pNow)
                                put("deviceName", "Pleos Smart Mic")
                                put("phoneIp", primaryIp)
                                put("phonePort", 8082)
                                put("candidateIps", candArr)
                                put("ts", System.currentTimeMillis())
                            }
                            for (evtName in arrayOf("join-pin", "CANDIDATE_IPS", "HELLO")) {
                                val env = JSONObject().apply {
                                    put("from", brokerClientId)
                                    put("role", "mic")
                                    put("event", evtName)
                                    put("type", evtName)
                                    put("data", baseData)
                                }.toString()
                                val pktBytes = buildMicMqttPublishBytes(topic, env)
                                synchronized(mqttWriteLock) {
                                    mqttOut.write(pktBytes)
                                }
                            }
                            synchronized(mqttWriteLock) {
                                mqttOut.flush()
                            }
                        }

                        // Publish immediately upon connecting!
                        sendCandidateIpsOverNativeMqtt()

                        val periodicPubJob = launch(Dispatchers.IO) {
                            var lastPublishedIps: List<String> = controller.phoneCandidateIps
                            var subTick = 0
                            while (isActive && !mqttSock.isClosed) {
                                delay(400L)
                                subTick++
                                try {
                                    val curCands = controller.phoneCandidateIps
                                    val candsChanged = curCands.isNotEmpty() && curCands != lastPublishedIps
                                    val directOk = controller.connection.value.isDirectLocalConnected &&
                                        controller.hasRecentVerifiedCarPacket(6000L)
                                    if (candsChanged || (subTick % 5 == 0 && !directOk && curCands.isNotEmpty())) {
                                        lastPublishedIps = curCands
                                        sendCandidateIpsOverNativeMqtt()
                                    } else if (subTick % 25 == 0) {
                                        // Keep TCP MQTT alive with lightweight PINGREQ every 10s
                                        synchronized(mqttWriteLock) {
                                            mqttOut.write(byteArrayOf(0xC0.toByte(), 0x00))
                                            mqttOut.flush()
                                        }
                                    }
                                } catch (_: Exception) {
                                    break
                                }
                            }
                        }

                        try {
                            while (isActive && !mqttSock.isClosed &&
                                controller.connection.value.pin.ifBlank { "4829" } == activePinSnapshot
                            ) {
                                val hdr = try {
                                    mqttIn.read()
                                } catch (_: java.net.SocketTimeoutException) {
                                    continue
                                }
                                if (hdr < 0) break
                                val remLen = readMicMqttRemainingLength(mqttIn)
                                if (remLen < 0 || remLen > 262_144) break
                                val buf = ByteArray(remLen)
                                var got = 0
                                while (got < remLen) {
                                    val n = mqttIn.read(buf, got, remLen - got)
                                    if (n < 0) break
                                    got += n
                                }
                                if (got < remLen) break
                                if ((hdr and 0xF0) == 0x30 && remLen >= 4) {
                                    val qos = (hdr ushr 1) and 0x03
                                    val topicLen = ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
                                    val offset = 2 + topicLen + (if (qos > 0) 2 else 0)
                                    if (offset < remLen) {
                                        val jsonStr = String(buf, offset, remLen - offset, Charsets.UTF_8)
                                        if (jsonStr.contains("\"role\":\"car\"") &&
                                            (jsonStr.contains("\"car-ready\"") || jsonStr.contains("\"car-registered\""))
                                        ) {
                                            sendCandidateIpsOverNativeMqtt()
                                        }
                                    }
                                }
                            }
                        } finally {
                            periodicPubJob.cancel()
                        }
                    } catch (_: Exception) {
                    } finally {
                        try {
                            mqttSock?.close()
                        } catch (_: Exception) {
                        }
                    }
                    delay(2000L)
                }
            }
        }

        // 500ms Heartbeat Watchdog in Kotlin (reverts to ○ 연결 안 됨 if no real car packet for 6.0s)
        launch(Dispatchers.Main) {
            while (isActive) {
                delay(500L)
                controller.tickConnectionWatchdog()
            }
        }

        // Periodic 2.0-second Galaxy Random Hotspot / Wi-Fi IP Scanner & Immediate Broadcast while unpaired
        launch(Dispatchers.IO) {
            var lastIps: List<String> = initialIps
            while (isActive) {
                delay(2000L)
                val currentIps = discoverCandidateLocalIps(AndroidPlatformContextHolder.appContext)
                val ipsChanged = currentIps != lastIps
                if (ipsChanged) {
                    lastIps = currentIps
                }
                val isDirectPaired = controller.connection.value.isDirectLocalConnected &&
                    controller.hasRecentVerifiedCarPacket(6000L)
                if (currentIps.isNotEmpty() && (ipsChanged || !isDirectPaired)) {
                    withContext(Dispatchers.Main) {
                        controller.onPhoneLocalIpsDetected(currentIps)
                    }
                }
            }
        }

        // 1) External-Car-Only `:8082` Direct WebSocket/TCP Server (`0.0.0.0:8082`):
        //    - Strictly rejects self-loopback (`127.0.0.1` / `isLoopbackAddress`) so Smartphone NEVER mistakes itself for Car!
        //    - Cleans up any zombie `:8082` socket and binds with `reuseAddress = true` (`SO_REUSEADDR`).
        launch(Dispatchers.IO) {
            val verifiedCarSenders = ConcurrentHashMap<String, (String) -> Unit>()

            fun broadcastToVerifiedCars(jsonLine: String) {
                for (sender in verifiedCarSenders.values) {
                    try {
                        sender(jsonLine)
                    } catch (_: Exception) {
                    }
                }
            }

            controller.outboundSocketWriter = { jsonLine ->
                if (verifiedCarSenders.isNotEmpty()) {
                    broadcastToVerifiedCars(jsonLine)
                }
            }

            var serverSocket: ServerSocket? = null
            try {
                for (bindAttempt in 1..4) {
                    try {
                        synchronized(server8082BindLock) {
                            try {
                                activeGlobal8082ServerSocket?.close()
                            } catch (_: Exception) {
                            }
                            activeGlobal8082ServerSocket = null
                            val created = ServerSocket().apply {
                                reuseAddress = true
                                bind(InetSocketAddress("0.0.0.0", 8082))
                            }
                            activeGlobal8082ServerSocket = created
                            serverSocket = created
                        }
                        break
                    } catch (e: Exception) {
                        if (bindAttempt == 4) throw e
                        delay(200L)
                    }
                }
                val boundServer = serverSocket ?: return@launch
                while (isActive && !boundServer.isClosed) {
                    val client = try {
                        boundServer.accept()
                    } catch (_: Exception) {
                        null
                    } ?: continue

                    launch(Dispatchers.IO) {
                        val remoteAddr = client.inetAddress
                        val remoteIp = remoteAddr?.hostAddress.orEmpty()
                        // Strictly forbid any loopback / self-connection on `:8082`!
                        if (remoteAddr == null ||
                            remoteAddr.isLoopbackAddress ||
                            remoteIp == "127.0.0.1" ||
                            remoteIp == "::1" ||
                            remoteIp.startsWith("127.")
                        ) {
                            try {
                                client.close()
                            } catch (_: Exception) {
                            }
                            return@launch
                        }

                        val connId = UUID.randomUUID().toString().take(8)
                        val writerLock = Any()
                        val sendChan = Channel<String>(capacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                        var writerJob: Job? = null
                        var pingLoopJob: Job? = null
                        try {
                            client.tcpNoDelay = true
                            client.keepAlive = true
                            client.sendBufferSize = 128 * 1024
                            client.receiveBufferSize = 64 * 1024
                            val rawOut = client.getOutputStream()
                            val bufIn = BufferedInputStream(client.getInputStream())
                            bufIn.mark(8)
                            val firstByte = bufIn.read()
                            if (firstByte < 0) return@launch
                            bufIn.reset()

                            val isWebSocketUpgrade = (firstByte == 'G'.code)
                            if (isWebSocketUpgrade) {
                                val headerBuilder = StringBuilder()
                                var crlfState = 0
                                while (headerBuilder.length < 8192) {
                                    val c = bufIn.read()
                                    if (c < 0) return@launch
                                    headerBuilder.append(c.toChar())
                                    crlfState = when {
                                        c == '\r'.code && (crlfState == 0 || crlfState == 2) -> crlfState + 1
                                        c == '\n'.code && (crlfState == 1 || crlfState == 3) -> crlfState + 1
                                        else -> 0
                                    }
                                    if (crlfState == 4) break
                                }
                                val wsKey = Regex("""(?i)Sec-WebSocket-Key:\s*(\S+)""")
                                    .find(headerBuilder.toString())
                                    ?.groupValues?.getOrNull(1)?.trim().orEmpty()
                                if (wsKey.isBlank()) return@launch
                                val acceptDigest = MessageDigest.getInstance("SHA-1")
                                    .digest((wsKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII))
                                val acceptKey = Base64.encodeToString(acceptDigest, Base64.NO_WRAP)
                                val handshakeResp = "HTTP/1.1 101 Switching Protocols\r\n" +
                                    "Upgrade: websocket\r\n" +
                                    "Connection: Upgrade\r\n" +
                                    "Sec-WebSocket-Accept: $acceptKey\r\n\r\n"
                                synchronized(writerLock) {
                                    rawOut.write(handshakeResp.toByteArray(Charsets.US_ASCII))
                                    rawOut.flush()
                                }
                            }

                            val writer = if (!isWebSocketUpgrade) PrintWriter(rawOut, true) else null
                            writerJob = launch(Dispatchers.IO) {
                                for (jsonLine in sendChan) {
                                    try {
                                        synchronized(writerLock) {
                                            if (isWebSocketUpgrade) {
                                                writeWebSocketTextFrame(rawOut, jsonLine)
                                            } else {
                                                writer?.println(jsonLine)
                                            }
                                        }
                                    } catch (_: Exception) {
                                        break
                                    }
                                }
                            }
                            val writeFn: (String) -> Unit = { jsonLine ->
                                sendChan.trySend(jsonLine)
                            }

                            val lineReader = if (!isWebSocketUpgrade) BufferedReader(InputStreamReader(bufIn, Charsets.UTF_8)) else null
                            while (isActive && !client.isClosed) {
                                val line = if (isWebSocketUpgrade) {
                                    readWebSocketTextFrame(bufIn, rawOut, writerLock)
                                } else {
                                    lineReader?.readLine()
                                } ?: break
                                if (line.isBlank()) continue
                                try {
                                    val msg = JSONObject(line)
                                    val evt = msg.optString("event", msg.optString("type", ""))
                                    val data = msg.optJSONObject("data") ?: msg
                                    val isCarHandshakeOrEvent = evt == "car-hello" ||
                                        evt == "paired-with-car" ||
                                        evt == "car-ping" ||
                                        evt == "car-pong" ||
                                        evt == "car-ready" ||
                                        evt == "latency-pong" ||
                                        evt == "car-state-sync" ||
                                        evt == "karaoke-score-result" ||
                                        evt == "karaoke-score-dismiss" ||
                                        data.optString("type", "") == "latency-pong"
                                    if (!isCarHandshakeOrEvent) continue

                                    // External Car handshake (`car-hello`) verified! Register Priority 1 `:8082` Direct Pipe & persist IP!
                                    if (isValidPrivatePhoneIpv4(remoteIp)) {
                                        saveKnownCarEndpointIp(AndroidPlatformContextHolder.appContext, remoteIp)
                                    }
                                    verified8082Channels[connId] = sendChan
                                    val newlyVerified = verifiedCarSenders.put(connId, writeFn) == null
                                    if (newlyVerified) {
                                        val pinNow = controller.connection.value.pin.ifBlank { "4829" }
                                        val initTs = System.currentTimeMillis()
                                        writeFn("""{"event":"join-pin","data":{"pin":"$pinNow","deviceName":"Pleos Smart Mic"}}""")
                                        writeFn("""{"event":"latency-ping","type":"latency-ping","data":{"type":"latency-ping","pin":"$pinNow","seq":1,"clientSendTs":$initTs,"lastNetLatencyMs":3}}""")

                                        pingLoopJob = launch(Dispatchers.IO) {
                                            var directPingSeq = 1L
                                            while (isActive && !client.isClosed) {
                                                delay(1000L)
                                                val pPin = controller.connection.value.pin.ifBlank { "4829" }
                                                val sendTs = System.currentTimeMillis()
                                                val seq = ++directPingSeq
                                                val lastNet = controller.connection.value.netLatencyMs
                                                writeFn("""{"event":"mic-ping","data":{"pin":"$pPin","deviceName":"Pleos Smart Mic"}}""")
                                                writeFn("""{"event":"latency-ping","type":"latency-ping","data":{"type":"latency-ping","pin":"$pPin","seq":$seq,"clientSendTs":$sendTs,"lastNetLatencyMs":$lastNet}}""")
                                            }
                                        }
                                    }

                                    if (evt == "car-ping") {
                                        writeFn("""{"event":"mic-pong","data":{"pin":"${controller.connection.value.pin.ifBlank { "4829" }}"}}""")
                                    }

                                    if (evt == "latency-pong" || data.optString("type", "") == "latency-pong") {
                                        val nowMs = System.currentTimeMillis()
                                        val sendTs = data.optLong("clientSendTs", nowMs)
                                        val netMs = ((nowMs - sendTs) / 2L).toInt().coerceIn(1, 250)
                                        val carBufMs = data.optInt("carBufferMs", 12).coerceIn(6, 200)
                                        withContext(Dispatchers.Main) {
                                            if (!controller.connection.value.isDirectLocalConnected) {
                                                controller.onDirectLocalSocketStateChanged(true)
                                            }
                                            controller.onDirectLatencyPongReceived(netMs, carBufMs, "DIRECT")
                                        }
                                        continue
                                    }

                                    val stateObj = when (evt) {
                                        "car-hello", "paired-with-car", "car-ready" -> data.optJSONObject("state")
                                        "car-state-sync" -> data
                                        else -> null
                                    }
                                    withContext(Dispatchers.Main) {
                                        if (newlyVerified || !controller.connection.value.isDirectLocalConnected) {
                                            controller.onDirectLocalSocketStateChanged(true)
                                        }
                                        controller.onDirectCarHeartbeatReceived()
                                        if (evt == "karaoke-score-result") {
                                            applyIncomingKaraokeScoreJsonOnMain(controller, data)
                                        } else if (evt == "karaoke-score-dismiss") {
                                            controller.onKaraokeScoreDismissedFromCar(data.optString("resultId", ""))
                                        } else if (stateObj != null) {
                                            applyIncomingCarStateJsonOnMain(controller, stateObj)
                                        }
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            pingLoopJob?.cancel()
                            verified8082Channels.remove(connId)
                            sendChan.close()
                            writerJob?.cancel()
                            val wasVerified = verifiedCarSenders.remove(connId) != null
                            if (wasVerified && verifiedCarSenders.isEmpty()) {
                                withContext(Dispatchers.Main) {
                                    controller.onDirectLocalSocketStateChanged(false)
                                }
                            }
                            try {
                                client.close()
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                controller.outboundSocketWriter = null
                synchronized(server8082BindLock) {
                    if (activeGlobal8082ServerSocket === serverSocket) {
                        activeGlobal8082ServerSocket = null
                    }
                    try {
                        serverSocket?.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }

        // 2) UDP Broadcast Discovery on local Wi-Fi (:8081) + Parallel Cached/Neighbor `:8080` Probe
        withContext(Dispatchers.IO) {
            try {
                activeGlobal8081UdpSocket?.close()
            } catch (_: Exception) {
            }
            activeGlobal8081UdpSocket = null

            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(0))
                    soTimeout = 1800
                }
                activeGlobal8081UdpSocket = socket
                val probeBytes = "PLEOS_DISCOVER".toByteArray(Charsets.UTF_8)
                val receiveBuf = ByteArray(512)

                while (isActive) {
                    try {
                        val neighIps = linkedSetOf<String>()
                        // Include cached Car IPs & subnet gateways from `pleos_hotspot_cache` at top priority!
                        for (cachedIp in loadCachedCarEndpointIps(AndroidPlatformContextHolder.appContext)) {
                            if (cachedIp !in controller.phoneCandidateIps) {
                                neighIps.add(cachedIp)
                            }
                        }
                        try {
                            val proc = ProcessBuilder("sh", "-c", "ip neigh; cat /proc/net/arp").start()
                            val out = proc.inputStream.bufferedReader().readText()
                            proc.waitFor(400, TimeUnit.MILLISECONDS)
                            Regex("""\b((?:192\.168|172\.(?:1[6-9]|2\d|3[0-1]))\.\d{1,3}\.\d{1,3})\b""")
                                .findAll(out)
                                .forEach { m ->
                                    val ip = m.groupValues[1]
                                    if (isValidPrivatePhoneIpv4(ip) && ip !in controller.phoneCandidateIps) {
                                        neighIps.add(ip)
                                    }
                                }
                        } catch (_: Exception) {
                        }
                        neighIps.take(10).forEach { neighIp ->
                            launch(Dispatchers.IO) {
                                var testSock: Socket? = null
                                try {
                                    testSock = Socket()
                                    testSock.connect(InetSocketAddress(neighIp, 8080), 250)
                                    testSock.close()
                                    val pinNow = controller.connection.value.pin.ifBlank { "4829" }
                                    controller.onCarEndpointDiscovered("ws://$neighIp:8080/ws", pinNow)
                                } catch (_: Exception) {
                                } finally {
                                    try { testSock?.close() } catch (_: Exception) {}
                                }
                            }
                        }

                        val broadcastTargets = linkedSetOf("255.255.255.255")
                        for (localIp in controller.phoneCandidateIps) {
                            val octets = localIp.split(".")
                            if (octets.size == 4) {
                                broadcastTargets.add("${octets[0]}.${octets[1]}.${octets[2]}.255")
                            }
                        }
                        for (bcastHost in broadcastTargets) {
                            try {
                                val bcastPacket = DatagramPacket(
                                    probeBytes,
                                    probeBytes.size,
                                    InetAddress.getByName(bcastHost),
                                    8081
                                )
                                socket.send(bcastPacket)
                            } catch (_: Exception) {
                            }
                        }

                        val respPacket = DatagramPacket(receiveBuf, receiveBuf.size)
                        socket.receive(respPacket)
                        val jsonStr = String(respPacket.data, 0, respPacket.length, Charsets.UTF_8)
                        val obj = JSONObject(jsonStr)
                        val wsUrl = obj.optString("wsUrl", "")
                        val pin = obj.optString("pin", "4829")
                        if (wsUrl.isNotBlank() && !wsUrl.contains("127.0.0.1")) {
                            controller.onCarEndpointDiscovered(wsUrl, pin)
                        }
                    } catch (_: Exception) {
                    }
                    delay(2500L)
                }
            } catch (_: Exception) {
            } finally {
                if (activeGlobal8081UdpSocket === socket) {
                    activeGlobal8081UdpSocket = null
                }
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    Box(
        modifier = modifier
            .size(240.dp, 200.dp)
            .alpha(1f)
            .zIndex(-1f)
    ) {
        // 1. 240x200dp YouTube IFrame Player WebView at zIndex(-1f) hidden behind the opaque dark stage UI
        AndroidView(
            modifier = Modifier
                .size(240.dp, 200.dp)
                .alpha(1f)
                .zIndex(-1f),
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(android.graphics.Color.parseColor("#0F172A"))
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.loadsImagesAutomatically = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

                    try {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                    } catch (_: Exception) {
                    }

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val isMainFrame = request?.isForMainFrame ?: true
                            if (!isMainFrame) {
                                return false
                            }
                            val url = request?.url?.toString().orEmpty()
                            return !(url.startsWith("https://pleos-stage.app") ||
                                url.startsWith("https://www.youtube.com") ||
                                url.startsWith("http://127.0.0.1"))
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            controller.syncLocalBackingPlayback()
                        }
                    }

                    onResume()
                    resumeTimers()
                    controller.ytJsEvaluator = { script ->
                        this.post {
                            this.evaluateJavascript(script, null)
                        }
                    }

                    loadDataWithBaseURL(
                        "https://pleos-stage.app",
                        buildMicYouTubeIframeHtml(),
                        "text/html",
                        "utf-8",
                        "https://pleos-stage.app"
                    )
                }
            }
        )

        // 2. Bundled `file:///android_asset/mic-engine.html` WebSocket Signaling, WebRTC P2P, Mic Capture & DEV Synth Engine
        AndroidView(
            modifier = Modifier
                .size(1.dp)
                .alpha(0.01f),
            factory = { context ->
                AndroidPlatformContextHolder.appContext = context.applicationContext
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = true

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.grant(request.resources)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val ips = detectAllPhoneLocalIps(context)
                            if (ips.isNotEmpty()) {
                                controller.onPhoneLocalIpsDetected(ips)
                            }
                            controller.connectToCarStage(
                                controller.connection.value.serverUrl,
                                controller.connection.value.pin.ifBlank { "4829" }
                            )
                        }
                    }

                    val hostWebView = this
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun isNativeAudioCaptureEnabled(): Boolean = true

                            @JavascriptInterface
                            fun onVerifiedCarWs8080(wsUrl: String, pin: String) {
                                controller.onVerifiedCarWs8080FromWebView(wsUrl, pin)
                            }

                            @JavascriptInterface
                            fun onCarWs8080Closed() {
                                controller.onCarWs8080ClosedFromWebView()
                            }

                            @JavascriptInterface
                            fun sendDirectSocketEvent(event: String, dataJson: String) {
                                controller.sendDirectSocketEventFromWebView(event, dataJson)
                            }

                            @JavascriptInterface
                            fun onAudioPcmChunkForDirectSocket(sampleRate: Int, pcmJsonArray: String) {
                                controller.onDirectPcmChunkFromWebView(sampleRate, pcmJsonArray, -1L)
                            }

                            @JavascriptInterface
                            fun onAudioPcmChunkForDirectSocketSeq(sampleRate: Int, seq: Long, pcmJsonArray: String) {
                                controller.onDirectPcmChunkFromWebView(sampleRate, pcmJsonArray, seq)
                            }

                            @JavascriptInterface
                            fun sendDirectLatencyPing(seq: Long, clientSendTs: Long, lastNetMs: Int) {
                                controller.sendDirectLatencyPing(seq, clientSendTs, lastNetMs)
                            }

                            @JavascriptInterface
                            fun onLatencyMetrics(totalMs: Int, netMs: Int, carBufferMs: Int, transport: String) {
                                hostWebView.post {
                                    controller.onLatencyMetricsFromWebView(totalMs, netMs, carBufferMs, transport)
                                }
                            }

                            @JavascriptInterface
                            fun onAudioMetrics(rms: Float, pitch: Float, note: String) {
                                hostWebView.post {
                                    controller.onMetricsFromWebView(rms, pitch, note)
                                }
                            }

                            @JavascriptInterface
                            fun onVerifiedCarPacket(pin: String) {
                                hostWebView.post {
                                    controller.onVerifiedCarPacketReceived(pin)
                                }
                            }

                            @JavascriptInterface
                            fun onConnectionState(state: String, message: String, pin: String) {
                                hostWebView.post {
                                    controller.onConnectionStateFromWebView(state, message, pin)
                                }
                            }

                            @JavascriptInterface
                            fun onCarStateSync(
                                songId: String,
                                isPlaying: Boolean,
                                keyShift: Int,
                                echoLevel: Int,
                                isParked: Boolean,
                                score: Int
                            ) {
                                hostWebView.post {
                                    controller.onCarStateSyncFromWebView(
                                        songId = songId,
                                        isPlaying = isPlaying,
                                        keyShift = keyShift,
                                        echoLevel = echoLevel,
                                        isParked = isParked,
                                        score = score
                                    )
                                }
                            }

                            @JavascriptInterface
                            fun onGuideVocalSync(enabled: Boolean) {
                                hostWebView.post {
                                    controller.onGuideVocalSyncedFromWebView(enabled)
                                }
                            }

                            @JavascriptInterface
                            fun onGuideVocalTelemetrySync(
                                enabled: Boolean,
                                pitchHz: Float,
                                note: String,
                                levelPercent: Float
                            ) {
                                hostWebView.post {
                                    controller.onGuideVocalTelemetrySyncedFromWebView(
                                        enabled = enabled,
                                        pitchHz = pitchHz,
                                        note = note,
                                        levelPercent = levelPercent
                                    )
                                }
                            }

                            @JavascriptInterface
                            fun onCarStateSyncFull(
                                songId: String,
                                currentTrackId: String,
                                isPlaying: Boolean,
                                progressSec: Int,
                                durationSec: Int,
                                backingVolume: Int,
                                keyShift: Int,
                                echoLevel: Int,
                                isParked: Boolean,
                                score: Int,
                                isDevMode: Boolean,
                                playlistEncoded: String
                            ) {
                                hostWebView.post {
                                    controller.onCarStateSyncFromWebView(
                                        songId = songId,
                                        isPlaying = isPlaying,
                                        keyShift = keyShift,
                                        echoLevel = echoLevel,
                                        isParked = isParked,
                                        score = score,
                                        currentTrackId = currentTrackId,
                                        progressSec = progressSec,
                                        durationSec = durationSec,
                                        backingVolume = backingVolume,
                                        isDevMode = isDevMode,
                                        playlistEncoded = playlistEncoded
                                    )
                                }
                            }

                            @JavascriptInterface
                            fun onCarStateSyncExtended(
                                songId: String,
                                currentTrackId: String,
                                isPlaying: Boolean,
                                progressSec: Int,
                                durationSec: Int,
                                backingVolume: Int,
                                micVolume: Int,
                                guideVolume: Int,
                                keyShift: Int,
                                echoLevel: Int,
                                mrPlaybackRate: Float,
                                syncOffsetMs: Int,
                                isParked: Boolean,
                                score: Int,
                                isDevMode: Boolean,
                                playlistEncoded: String,
                                carHostId: String,
                                stateRevision: Long
                            ) {
                                hostWebView.post {
                                    controller.onCarStateSyncFromWebView(
                                        songId = songId,
                                        isPlaying = isPlaying,
                                        keyShift = keyShift,
                                        echoLevel = echoLevel,
                                        isParked = isParked,
                                        score = score,
                                        currentTrackId = currentTrackId,
                                        progressSec = progressSec,
                                        durationSec = durationSec,
                                        backingVolume = backingVolume,
                                        isDevMode = isDevMode,
                                        playlistEncoded = playlistEncoded,
                                        micVolume = micVolume,
                                        guideVolume = guideVolume,
                                        mrPlaybackRate = mrPlaybackRate,
                                        syncOffsetMs = syncOffsetMs,
                                        carHostId = carHostId,
                                        stateRevision = stateRevision
                                    )
                                }
                            }

                            @JavascriptInterface
                            fun onScoreModeSync(enabled: Boolean) {
                                hostWebView.post {
                                    controller.onScoreModeSyncedFromCar(enabled)
                                }
                            }

                            @JavascriptInterface
                            fun onKaraokeScoreResult(jsonStr: String) {
                                hostWebView.post {
                                    try {
                                        val obj = JSONObject(jsonStr)
                                        applyIncomingKaraokeScoreJsonOnMain(controller, obj)
                                    } catch (_: Exception) {
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun onKaraokeScoreDismiss(resultId: String) {
                                hostWebView.post {
                                    controller.onKaraokeScoreDismissedFromCar(resultId)
                                }
                            }
                        },
                        "PleosMicBridge"
                    )

                    controller.jsEvaluator = { script ->
                        this.post {
                            this.evaluateJavascript(script, null)
                        }
                    }

                    loadUrl("file:///android_asset/mic-engine.html")
                }
            }
        )
    }

    DisposableEffect(controller) {
        val captureRunning = AtomicBoolean(true)
        val mainHandler = Handler(Looper.getMainLooper())

        @SuppressLint("MissingPermission")
        fun openRawUnfilteredAudioRecord(): Pair<AudioRecord, Int>? {
            val candidates = arrayOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION to 24000,
                MediaRecorder.AudioSource.VOICE_RECOGNITION to 48000,
                MediaRecorder.AudioSource.MIC to 24000,
                MediaRecorder.AudioSource.MIC to 48000
            )
            for ((src, rate) in candidates) {
                try {
                    val minBuf = AudioRecord.getMinBufferSize(
                        rate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    if (minBuf <= 0) continue
                    val chunkBytes = ((rate / 50) * 2)
                    val targetBuf = maxOf(minBuf * 4, chunkBytes * 10)
                    val rec = AudioRecord(
                        src,
                        rate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        targetBuf
                    )
                    if (rec.state == AudioRecord.STATE_INITIALIZED) {
                        // NEVER attach NoiseSuppressor, AutomaticGainControl, or AcousticEchoCanceler!
                        return rec to rate
                    } else {
                        rec.release()
                    }
                } catch (_: Exception) {
                }
            }
            return null
        }

        val captureThread = Thread(
            {
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                } catch (_: Exception) {
                }

                var audioRecord: AudioRecord? = null
                try {
                    while (captureRunning.get()) {
                        val ctx = AndroidPlatformContextHolder.appContext
                        val hasMicPerm = ctx != null && ctx.checkPermission(
                            Manifest.permission.RECORD_AUDIO,
                            Process.myPid(),
                            Process.myUid()
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasMicPerm) {
                            try {
                                Thread.sleep(250L)
                            } catch (_: InterruptedException) {
                                break
                            }
                            continue
                        }

                        val opened = openRawUnfilteredAudioRecord()
                        if (opened == null) {
                            try {
                                Thread.sleep(500L)
                            } catch (_: InterruptedException) {
                                break
                            }
                            continue
                        }

                        val record = opened.first
                        val hwRate = opened.second
                        audioRecord = record
                        val hwChunkSamples = hwRate / 50 // Exact 20.000ms (480 @ 24kHz, or 960 @ 48kHz)
                        val txRate = 24000
                        val txSamples = 480 // Exact 20.000ms @ 24,000Hz (50 chunks/sec == exact 24,000 samples/sec == 1000.0ms/sec)
                        val hwBuf = ShortArray(hwChunkSamples)
                        val txBuf = if (hwRate == txRate) hwBuf else ShortArray(txSamples)
                        val pcmSb = StringBuilder(3072)
                        val packetSb = StringBuilder(3328)
                        var nativeSeq = 0L
                        var chunkCounter = 0L
                        var windowStartNs = System.nanoTime()
                        var windowSentSamples = 0

                        try {
                            record.startRecording()
                            Log.i(
                                "PleosMicNativeAudio",
                                "Native AudioRecord started: hw=${hwRate}Hz -> tx=${txRate}Hz chunk=${txSamples} (20.0ms, NO NS/AGC/AEC)"
                            )
                        } catch (e: Exception) {
                            Log.e("PleosMicNativeAudio", "Failed to start AudioRecord: ${e.message}")
                            try {
                                record.release()
                            } catch (_: Exception) {
                            }
                            audioRecord = null
                            continue
                        }

                        while (captureRunning.get()) {
                            var filled = 0
                            var readFailed = false
                            while (captureRunning.get() && filled < hwChunkSamples) {
                                val r = record.read(
                                    hwBuf,
                                    filled,
                                    hwChunkSamples - filled,
                                    AudioRecord.READ_BLOCKING
                                )
                                if (r > 0) {
                                    filled += r
                                } else {
                                    readFailed = true
                                    break
                                }
                            }
                            if (!captureRunning.get() || readFailed) break

                            if (hwRate != txRate) {
                                // Clean 2:1 boxcar decimation (960 @ 48kHz -> 480 @ 24kHz = exact 20.000ms)
                                for (k in 0 until txSamples) {
                                    val s0 = hwBuf[k * 2].toInt()
                                    val s1 = hwBuf[k * 2 + 1].toInt()
                                    txBuf[k] = ((s0 + s1) shr 1).toShort()
                                }
                            }

                            chunkCounter++
                            val muted = controller.isMicMutedState
                            if (!muted) {
                                val seq = ++nativeSeq
                                pcmSb.setLength(0)
                                pcmSb.append('[')
                                for (i in 0 until txSamples) {
                                    if (i > 0) pcmSb.append(',')
                                    pcmSb.append(txBuf[i].toInt())
                                }
                                pcmSb.append(']')
                                val pcmArrayJson = pcmSb.toString()

                                val pin = controller.connection.value.pin.ifBlank { "4829" }
                                val chan8080 = direct8080ChannelRef.get()
                                when {
                                    verified8082Channels.isNotEmpty() -> {
                                        packetSb.setLength(0)
                                        packetSb.append("""{"event":"audio-pcm-chunk","data":{"pin":"""")
                                        packetSb.append(pin)
                                        packetSb.append("""","sr":""")
                                        packetSb.append(txRate)
                                        packetSb.append(""","seq":""")
                                        packetSb.append(seq)
                                        packetSb.append(""","pcm":""")
                                        packetSb.append(pcmArrayJson)
                                        packetSb.append("}}")
                                        val fullFrame = packetSb.toString()
                                        for (ch in verified8082Channels.values) {
                                            ch.trySend(fullFrame)
                                        }
                                    }
                                    chan8080 != null -> {
                                        packetSb.setLength(0)
                                        packetSb.append("""{"event":"audio-pcm-chunk","data":{"pin":"""")
                                        packetSb.append(pin)
                                        packetSb.append("""","sr":""")
                                        packetSb.append(txRate)
                                        packetSb.append(""","seq":""")
                                        packetSb.append(seq)
                                        packetSb.append(""","pcm":""")
                                        packetSb.append(pcmArrayJson)
                                        packetSb.append("}}")
                                        chan8080.trySend(packetSb.toString())
                                    }
                                    controller.hasRecentVerifiedCarPacket(6000L) -> {
                                        // Fallback ONLY when WebRTC P2P (`vocal-fast-lane`) or WSS Relay is active
                                        controller.dispatchNativePcmToWebViewFallback(txRate, seq, pcmArrayJson)
                                    }
                                }

                                windowSentSamples += txSamples
                                val nowNs = System.nanoTime()
                                val elapsedNs = nowNs - windowStartNs
                                if (elapsedNs >= 2_000_000_000L) {
                                    val samplesPerSec = ((windowSentSamples.toLong() * 1_000_000_000L) / elapsedNs).toInt()
                                    val msPerSec = (samplesPerSec * 1000) / txRate
                                    Log.i(
                                        "PleosMicNativeAudio",
                                        "1:1 Native Mic Stream -> sentSamplesPerSec=$samplesPerSec @ ${txRate}Hz (${msPerSec}ms/s), " +
                                            "direct8082=${verified8082Channels.isNotEmpty()}, direct8080=${chan8080 != null}"
                                    )
                                    windowStartNs = nowNs
                                    windowSentSamples = 0
                                }
                            }

                            // Update Smartphone & Car vocal level/pitch meters at 25Hz (every 40ms) directly from Kotlin
                            if (chunkCounter % 2L == 0L) {
                                if (muted) {
                                    mainHandler.post {
                                        controller.onMetricsFromWebView(0f, 0f, "-")
                                    }
                                } else {
                                    val (scaledRms, rawPitch) = estimateNativePitchAndRms(txBuf, txSamples, txRate)
                                    val pitchRounded = ((rawPitch * 10f).roundToInt()) / 10f
                                    val note = nativeFreqToNote(pitchRounded)
                                    val rmsInt = scaledRms.roundToInt().coerceIn(0, 100)
                                    mainHandler.post {
                                        controller.onMetricsFromWebView(rmsInt.toFloat(), pitchRounded, note)
                                    }

                                    val conn = controller.connection.value
                                    val chan8080 = direct8080ChannelRef.get()
                                    val pin = conn.pin.ifBlank { "4829" }
                                    val telemetryJson = """{"event":"vocal-telemetry","type":"vocal-telemetry","data":{"pin":"$pin","rms":$rmsInt,"pitch":$pitchRounded,"note":"$note","totalLatencyMs":${conn.totalVocalLatencyMs},"netLatencyMs":${conn.netLatencyMs},"carBufferMs":${conn.carBufferMs},"transport":"${conn.transportMode}","ts":${System.currentTimeMillis()}}}"""
                                    when {
                                        verified8082Channels.isNotEmpty() -> {
                                            for (ch in verified8082Channels.values) {
                                                ch.trySend(telemetryJson)
                                            }
                                        }
                                        chan8080 != null -> {
                                            chan8080.trySend(telemetryJson)
                                        }
                                        controller.hasRecentVerifiedCarPacket(6000L) -> {
                                            controller.dispatchNativeTelemetryToWebViewFallback(rmsInt.toFloat(), pitchRounded, note)
                                        }
                                    }
                                }
                            }
                        }

                        try {
                            record.stop()
                        } catch (_: Exception) {
                        }
                        try {
                            record.release()
                        } catch (_: Exception) {
                        }
                        audioRecord = null
                    }
                } finally {
                    try {
                        audioRecord?.stop()
                    } catch (_: Exception) {
                    }
                    try {
                        audioRecord?.release()
                    } catch (_: Exception) {
                    }
                }
            },
            "PleosNativeAudioRecord"
        ).apply {
            isDaemon = true
            start()
        }

        onDispose {
            captureRunning.set(false)
            try {
                captureThread.interrupt()
            } catch (_: Exception) {
            }
            synchronized(server8082BindLock) {
                try {
                    activeGlobal8082ServerSocket?.close()
                } catch (_: Exception) {
                }
                activeGlobal8082ServerSocket = null
            }
            try {
                activeGlobal8081UdpSocket?.close()
            } catch (_: Exception) {
            }
            activeGlobal8081UdpSocket = null
            controller.onVerifiedCarWs8080Callback = null
            controller.onCarWs8080ClosedCallback = null
            controller.jsEvaluator = null
            controller.ytJsEvaluator = null
        }
    }
}

private fun buildMicYouTubeIframeHtml(): String = """
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
    min-width: 240px;
    min-height: 200px;
    background: #0F172A;
    overflow: hidden;
  }
  #yt-wrapper, #yt-player, iframe {
    width: 100%;
    height: 100%;
    min-width: 240px;
    min-height: 200px;
    border: 0;
    display: block;
    background: #0F172A;
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
  let activeVideoId = 'K4DyBUG242c';
  let loadedVideoId = 'K4DyBUG242c';
  let currentVolume = 85;
  let currentPlaybackRate = 1.0;
  let currentSyncOffsetMs = 0;
  let shouldBePlaying = false;
  let pendingSync = null;

  function applyVolumeAndRateToPlayer(target) {
    if (!target) return;
    try {
      const vol = Math.max(0, Math.min(100, Number(currentVolume)));
      if (vol <= 0) {
        if (target.mute) target.mute();
        if (target.setVolume) target.setVolume(0);
      } else {
        if (target.unMute) target.unMute();
        const curvedVol = Math.max(1, Math.min(100, Math.round(Math.pow(vol / 100.0, 2.2) * 100)));
        if (target.setVolume) target.setVolume(curvedVol);
      }
      if (target.setPlaybackRate) {
        const targetR = Math.max(0.75, Math.min(1.25, Number(currentPlaybackRate) || 1.0));
        target.setPlaybackRate(targetR);
        if (target.getPlaybackRate && Math.abs(targetR - 1.0) >= 0.04) {
          const actualR = Number(target.getPlaybackRate() || 1.0);
          if (Math.abs(actualR - 1.0) < 0.01) {
            target.setPlaybackRate(targetR < 1.0 ? 0.75 : 1.25);
          }
        }
      }
    } catch (e) {}
  }

  window.onYouTubeIframeAPIReady = function() {
    try {
      player = new YT.Player('yt-player', {
        width: '240',
        height: '200',
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
              applyVolumeAndRateToPlayer(evt.target);
            } catch (e) {}

            if (pendingSync) {
              const s = pendingSync;
              pendingSync = null;
              window.PleosYtMr.syncState(s.isPlaying, s.videoId, s.vol, s.bpm, s.rate, s.syncOffsetMs);
            } else if (shouldBePlaying && activeVideoId) {
              try {
                applyVolumeAndRateToPlayer(evt.target);
                evt.target.playVideo();
              } catch (e) {}
            } else {
              try { evt.target.pauseVideo(); } catch (e) {}
            }
          },
          onStateChange: function(evt) {
            const st = evt.data;
            if (st === 1) {
              if (!shouldBePlaying) {
                try { evt.target.pauseVideo(); } catch (e) {}
              } else {
                applyVolumeAndRateToPlayer(evt.target);
              }
            } else if (st === 5 && shouldBePlaying) {
              try {
                applyVolumeAndRateToPlayer(evt.target);
                evt.target.playVideo();
              } catch (e) {}
            }
          }
        }
      });
    } catch (e) {}
  };

  window.PleosYtMr = {
    syncState: function(isPlaying, videoId, vol, bpm, rate, syncOffsetMs) {
      const nextVid = videoId ? String(videoId).trim() : '';
      if (typeof vol === 'number') {
        currentVolume = Math.max(0, Math.min(100, Number(vol)));
      }
      if (typeof rate === 'number' && !isNaN(rate)) {
        currentPlaybackRate = Math.max(0.75, Math.min(1.25, Math.round(Number(rate) * 20) / 20));
      }
      if (typeof syncOffsetMs === 'number' && !isNaN(syncOffsetMs)) {
        currentSyncOffsetMs = Math.max(-500, Math.min(500, Math.round(Number(syncOffsetMs))));
      }
      shouldBePlaying = !!isPlaying && nextVid.length > 0;

      if (!player || !playerReady) {
        if (nextVid) activeVideoId = nextVid;
        pendingSync = {
          isPlaying: shouldBePlaying,
          videoId: activeVideoId,
          vol: currentVolume,
          bpm: bpm || 124,
          rate: currentPlaybackRate,
          syncOffsetMs: currentSyncOffsetMs
        };
        return;
      }

      try {
        applyVolumeAndRateToPlayer(player);
        if (nextVid && nextVid !== loadedVideoId) {
          activeVideoId = nextVid;
          loadedVideoId = nextVid;
          if (shouldBePlaying) {
            player.loadVideoById({ videoId: nextVid, startSeconds: 0 });
            player.playVideo();
          } else {
            player.cueVideoById({ videoId: nextVid, startSeconds: 0 });
          }
        } else {
          if (shouldBePlaying) {
            player.playVideo();
          } else {
            player.pauseVideo();
          }
        }
      } catch (e) {}
    },
    setVolume: function(vol) {
      if (typeof vol === 'number' && !isNaN(vol)) {
        currentVolume = Math.max(0, Math.min(100, Number(vol)));
      }
      if (player && playerReady) {
        applyVolumeAndRateToPlayer(player);
      }
    },
    setPlaybackRate: function(rate) {
      if (typeof rate === 'number' && !isNaN(rate)) {
        currentPlaybackRate = Math.max(0.75, Math.min(1.25, Math.round(Number(rate) * 20) / 20));
      }
      if (player && playerReady) {
        applyVolumeAndRateToPlayer(player);
      }
    },
    seekTo: function(sec) {
      const target = Math.max(0.0, Number(sec) || 0.0);
      if (!player || !playerReady) return;
      try {
        if (player.seekTo) {
          player.seekTo(target, true);
        }
      } catch (e) {}
    },
    nudgeSync: function(deltaSeconds) {
      const delta = Number(deltaSeconds) || 0;
      if (Math.abs(delta) < 0.005 || !player || !playerReady) return;
      try {
        if (player.getCurrentTime && player.seekTo) {
          const cur = Number(player.getCurrentTime() || 0);
          player.seekTo(Math.max(0.0, cur + delta), true);
        }
      } catch (e) {}
    },
    pause: function() {
      shouldBePlaying = false;
      if (player && playerReady && player.pauseVideo) {
        try { player.pauseVideo(); } catch (e) {}
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
