package io.github.teamcarinkaraoke.pleosstage.car.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import io.github.teamcarinkaraoke.pleosstage.car.BuildConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import io.github.teamcarinkaraoke.pleosstage.car.audio.YouTubeUrlParser
import io.github.teamcarinkaraoke.pleosstage.car.audio.buildYouTubeIframeHtml
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class EmbeddedServerState(
    val isRunning: Boolean = false,
    val port: Int = PleosEmbeddedSignalingServer.DEFAULT_PORT,
    val lanIp: String = "127.0.0.1",
    val lanWsUrl: String = "ws://127.0.0.1:${PleosEmbeddedSignalingServer.DEFAULT_PORT}/ws",
    val localLoopbackWsUrl: String = "ws://127.0.0.1:${PleosEmbeddedSignalingServer.DEFAULT_PORT}/ws",
    val emulatorBridgeWsUrl: String = "ws://10.0.2.2:${PleosEmbeddedSignalingServer.DEFAULT_PORT}/ws",
    val activePin: String = "4829",
    val connectedMicCount: Int = 0
)

/**
 * [Architecture B] Embedded Ktor Signaling & Audio Relay Server running inside the AAOS Car Head Unit.
 *
 * Eliminates the external PC Node.js (`pleos-stage-server`) dependency:
 * - Hosts a native WebSocket server (`ws://<car-ip>:8080/ws` and `ws://<car-ip>:8080`) directly on AAOS.
 * - Relays 4-digit PIN pairing, WebRTC P2P SDP Offer/Answer/ICE, 60fps vocal pitch/RMS telemetry,
 *   low-latency 16kHz PCM audio chunks, and bi-directional stage remote control commands.
 * - Broadcasts & responds on UDP port 8081 so smartphones on the same Wi-Fi auto-discover the Car IP.
 */
class PleosEmbeddedSignalingServer(
    private val appContext: Context,
    initialPin: String = "4829"
) {
    companion object {
        private const val TAG = "PleosKtorServer"
        const val DEFAULT_PORT = 8080
        const val DISCOVERY_UDP_PORT = 8081
        private const val HOTSPOT_PREFS_NAME = "pleos_hotspot_cache"

        @Volatile
        private var activeGlobalEngine: ApplicationEngine? = null

        @Volatile
        private var activeGlobalUdpSocket: DatagramSocket? = null

        private val MQTT_TCP_BROKERS = listOf(
            "broker.emqx.io" to 1883,
            "broker.hivemq.com" to 1883
        )

        fun detectLocalLanIp(context: Context): String {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val wifiIpInt = wifiManager?.connectionInfo?.ipAddress ?: 0
                if (wifiIpInt != 0) {
                    val ip = String.format(
                        java.util.Locale.US,
                        "%d.%d.%d.%d",
                        wifiIpInt and 0xff,
                        wifiIpInt shr 8 and 0xff,
                        wifiIpInt shr 16 and 0xff,
                        wifiIpInt shr 24 and 0xff
                    )
                    if (ip != "0.0.0.0" && !ip.startsWith("127.")) {
                        return ip
                    }
                }
            } catch (_: Exception) {
            }

            try {
                val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                @Suppress("DEPRECATION")
                val networks = listOfNotNull(cm?.activeNetwork) + cm?.allNetworks.orEmpty()
                var cmFallback: String? = null
                for (net in networks.distinct()) {
                    val caps = cm?.getNetworkCapabilities(net)
                    val isCellularOnly = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true &&
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) != true
                    if (isCellularOnly) continue
                    val lp = cm?.getLinkProperties(net) ?: continue
                    for (la in lp.linkAddresses) {
                        val addr = la.address
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val host = addr.hostAddress ?: continue
                            if (host.startsWith("192.168.") || host.startsWith("172.")) {
                                return host
                            }
                            if (!host.startsWith("169.254.") && !host.startsWith("127.") && cmFallback == null) {
                                cmFallback = host
                            }
                        }
                    }
                }
                if (!cmFallback.isNullOrBlank()) {
                    return cmFallback
                }
            } catch (_: Exception) {
            }

            var fallbackIp = "127.0.0.1"
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                if (interfaces != null) {
                    while (interfaces.hasMoreElements()) {
                        try {
                            val intf = interfaces.nextElement() ?: continue
                            val ifName = (intf.name ?: "").lowercase()
                            if (ifName == "lo" || ifName.startsWith("rmnet") || ifName.startsWith("dummy") || ifName.startsWith("v4-")) {
                                continue
                            }
                            val addrs = intf.inetAddresses ?: continue
                            while (addrs.hasMoreElements()) {
                                val addr = try { addrs.nextElement() } catch (_: Throwable) { null } ?: continue
                                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                                    val host = addr.hostAddress ?: continue
                                    if (host.startsWith("192.168.") || host.startsWith("172.")) {
                                        return host
                                    }
                                    if (host.startsWith("10.") && fallbackIp == "127.0.0.1") {
                                        fallbackIp = host
                                    } else if (!host.startsWith("169.254.") && !host.startsWith("127.") && fallbackIp == "127.0.0.1") {
                                        fallbackIp = host
                                    }
                                }
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            } catch (_: Throwable) {
            }

            if (fallbackIp == "127.0.0.1") {
                try {
                    DatagramSocket().use { s ->
                        s.connect(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)), 53))
                        val host = (s.localAddress as? Inet4Address)?.takeIf { !it.isLoopbackAddress }?.hostAddress.orEmpty()
                        if (host.isNotBlank() && host != "0.0.0.0" && !host.startsWith("127.")) {
                            fallbackIp = host
                        }
                    }
                } catch (_: Exception) {
                }
            }
            return fallbackIp
        }

        fun loadLastSavedHotspotIp(context: Context): String {
            return try {
                val prefs = context.applicationContext.getSharedPreferences(HOTSPOT_PREFS_NAME, Context.MODE_PRIVATE)
                val last = prefs.getString("last_hotspot_ip", "").orEmpty().trim()
                if (last.isNotBlank() && isAllowedCarOutboundIp(last) && !last.startsWith("10.0.2.")) last else ""
            } catch (_: Exception) {
                ""
            }
        }

        /**
         * Extracts the Host PC/MacBook's active Smartphone Hotspot Gateway IP inside AAOS Emulator:
         * When MacBook connects to a Smartphone Hotspot (Galaxy random `192.168.X.Y` or iPhone `172.20.10.1`),
         * Android Emulator populates `LinkProperties.dnsServers` and system properties (`net.dns1..4`, `dhcp.*.dns*`)
         * with the upstream DNS server — which on a Smartphone Hotspot IS the Smartphone's exact `:8082` IP!
         */
        fun extractDnsAndSystemPropHotspotIps(context: Context): List<String> {
            val dnsIps = LinkedHashSet<String>()
            val privateIpv4Regex = Regex("""\b((?:192\.168|172\.(?:1[6-9]|2\d|3[0-1]))\.\d{1,3}\.\d{1,3})\b""")

            fun collectCandidateIp(raw: String?) {
                if (raw.isNullOrBlank()) return
                val clean = raw.trim().removePrefix("[").removeSuffix("]").trim()
                if (isAllowedCarOutboundIp(clean) && !clean.startsWith("10.0.2.")) {
                    dnsIps.add(clean)
                }
                privateIpv4Regex.findAll(raw).forEach { m ->
                    val matched = m.groupValues[1]
                    if (isAllowedCarOutboundIp(matched) && !matched.startsWith("10.0.2.")) {
                        dnsIps.add(matched)
                    }
                }
            }

            // 1) ConnectivityManager LinkProperties.dnsServers across activeNetwork & allNetworks
            try {
                val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                @Suppress("DEPRECATION")
                val networks = listOfNotNull(cm?.activeNetwork) + cm?.allNetworks.orEmpty()
                for (net in networks.distinct()) {
                    val lp = cm?.getLinkProperties(net) ?: continue
                    for (dnsAddr in lp.dnsServers) {
                        if (dnsAddr is Inet4Address && !dnsAddr.isLoopbackAddress) {
                            collectCandidateIp(dnsAddr.hostAddress)
                        }
                    }
                }
            } catch (_: Throwable) {
            }

            // 2) Zero-overhead reflection on `android.os.SystemProperties` (`net.dns1..4`, `dhcp.*`)
            val dnsPropKeys = arrayOf(
                "net.dns1", "net.dns2", "net.dns3", "net.dns4",
                "dhcp.eth0.dns1", "dhcp.eth0.dns2", "dhcp.eth0.gateway", "dhcp.eth0.server",
                "dhcp.wlan0.dns1", "dhcp.wlan0.dns2", "dhcp.wlan0.gateway", "dhcp.wlan0.server",
                "dhcp.eth1.dns1", "dhcp.eth1.gateway", "dhcp.wifi_eth.dns1"
            )
            try {
                val sysPropClass = Class.forName("android.os.SystemProperties")
                val getMethod = sysPropClass.getMethod("get", String::class.java)
                for (key in dnsPropKeys) {
                    val value = getMethod.invoke(null, key) as? String
                    collectCandidateIp(value)
                }
            } catch (_: Throwable) {
            }

            // 3) `getprop net.dns1..4` & `getprop` DNS/gateway scan + `/etc/resolv.conf`
            for (key in arrayOf("net.dns1", "net.dns2", "net.dns3", "net.dns4")) {
                try {
                    val p = ProcessBuilder("getprop", key).redirectErrorStream(true).start()
                    val v = p.inputStream.bufferedReader().use { it.readText() }.trim()
                    p.waitFor(200, TimeUnit.MILLISECONDS)
                    collectCandidateIp(v)
                } catch (_: Throwable) {
                }
            }
            try {
                val pAll = ProcessBuilder("getprop").redirectErrorStream(true).start()
                val allProps = pAll.inputStream.bufferedReader().use { it.readText() }
                pAll.waitFor(350, TimeUnit.MILLISECONDS)
                for (line in allProps.lineSequence()) {
                    val lower = line.lowercase()
                    if (lower.contains("dns") || lower.contains("gateway") || lower.contains("dhcp") || lower.contains("hotspot")) {
                        collectCandidateIp(line)
                    }
                }
            } catch (_: Throwable) {
            }
            try {
                val resolvFile = java.io.File("/etc/resolv.conf")
                if (resolvFile.canRead()) {
                    collectCandidateIp(resolvFile.readText())
                }
            } catch (_: Throwable) {
            }

            return dnsIps.toList()
        }

        /**
         * Resolves IPv4 via direct UDP DNS (`8.8.8.8:53` & `1.1.1.1:53`) BEFORE falling back to system `InetAddress.getByName`.
         * Crucial on macOS AAOS Emulator: when MacBook switches from Wi-Fi Router to Smartphone Hotspot,
         * QEMU SLIRP's `10.0.2.3:53` DNS proxy points to the dead Wi-Fi router and stalls `getaddrinfo` for 15s+,
         * whereas raw UDP to literal `8.8.8.8:53` routes straight out the Hotspot NAT in ~25ms!
         */
        fun resolveHostIpv4BypassingBrokenEmulatorDns(hostname: String): InetAddress {
            val cleanHost = hostname.trim()
            if (cleanHost.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))) {
                val octs = cleanHost.split(".").map { it.toInt().toByte() }.toByteArray()
                return InetAddress.getByAddress(octs)
            }

            fun skipDnsName(buf: ByteArray, startPos: Int, len: Int): Int {
                var p = startPos
                while (p < len) {
                    val b = buf[p].toInt() and 0xFF
                    if (b == 0) return p + 1
                    if ((b and 0xC0) == 0xC0) return p + 2
                    p += 1 + b
                }
                return len
            }

            val dnsServers = arrayOf(
                byteArrayOf(8, 8, 8, 8),
                byteArrayOf(1, 1, 1, 1)
            )
            for (dnsBytes in dnsServers) {
                try {
                    val txId = (System.nanoTime().toInt() and 0xFFFF)
                    val qOut = ByteArrayOutputStream(64)
                    qOut.write((txId ushr 8) and 0xFF)
                    qOut.write(txId and 0xFF)
                    qOut.write(0x01) // RD = 1
                    qOut.write(0x00)
                    qOut.write(0x00); qOut.write(0x01) // QDCOUNT = 1
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
                    qOut.write(0x00); qOut.write(0x01) // QTYPE = A
                    qOut.write(0x00); qOut.write(0x01) // QCLASS = IN
                    val queryPkt = qOut.toByteArray()

                    DatagramSocket().use { udp ->
                        udp.soTimeout = 700
                        val dnsAddr = InetAddress.getByAddress(dnsBytes)
                        udp.send(DatagramPacket(queryPkt, queryPkt.size, dnsAddr, 53))
                        val respBuf = ByteArray(512)
                        val respPkt = DatagramPacket(respBuf, respBuf.size)
                        udp.receive(respPkt)
                        val rLen = respPkt.length
                        if (rLen >= 16) {
                            val rId = ((respBuf[0].toInt() and 0xFF) shl 8) or (respBuf[1].toInt() and 0xFF)
                            val anCount = ((respBuf[6].toInt() and 0xFF) shl 8) or (respBuf[7].toInt() and 0xFF)
                            val qdCount = ((respBuf[4].toInt() and 0xFF) shl 8) or (respBuf[5].toInt() and 0xFF)
                            if (rId == txId && anCount > 0) {
                                var pos = 12
                                for (q in 0 until qdCount) {
                                    pos = skipDnsName(respBuf, pos, rLen) + 4
                                }
                                for (a in 0 until anCount) {
                                    if (pos >= rLen) break
                                    pos = skipDnsName(respBuf, pos, rLen)
                                    if (pos + 10 > rLen) break
                                    val rType = ((respBuf[pos].toInt() and 0xFF) shl 8) or (respBuf[pos + 1].toInt() and 0xFF)
                                    val rClass = ((respBuf[pos + 2].toInt() and 0xFF) shl 8) or (respBuf[pos + 3].toInt() and 0xFF)
                                    val rdLen = ((respBuf[pos + 8].toInt() and 0xFF) shl 8) or (respBuf[pos + 9].toInt() and 0xFF)
                                    val rDataPos = pos + 10
                                    if (rType == 1 && rClass == 1 && rdLen == 4 && rDataPos + 4 <= rLen) {
                                        val ipBytes = respBuf.copyOfRange(rDataPos, rDataPos + 4)
                                        return InetAddress.getByAddress(cleanHost, ipBytes)
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

        fun detectHotspotGatewayIps(context: Context): List<String> {
            val result = LinkedHashSet<String>()
            // Priority 0-A) Emulator / Host Upstream DNS & System Properties (`LinkProperties.dnsServers`, `getprop net.dns1..4`)
            // On Smartphone Hotspot, the upstream DNS server IS the Smartphone's exact `:8082` IP!
            try {
                result.addAll(extractDnsAndSystemPropHotspotIps(context))
            } catch (_: Throwable) {
            }

            // Priority 0-B) Cached last-known Smartphone Hotspot IPs & `/24` Subnet `.1` Gateways (`SharedPreferences("pleos_hotspot_cache")`)
            try {
                val prefs = context.applicationContext.getSharedPreferences(HOTSPOT_PREFS_NAME, Context.MODE_PRIVATE)
                val cachedIp = prefs.getString("last_hotspot_ip", "").orEmpty().trim()
                if (cachedIp.isNotBlank() && isAllowedCarOutboundIp(cachedIp) && !cachedIp.startsWith("10.0.2.")) {
                    result.add(cachedIp)
                }
                prefs.getString("recent_hotspot_ips", "").orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && isAllowedCarOutboundIp(it) && !it.startsWith("10.0.2.") }
                    .forEach { result.add(it) }

                val cachedSubnets = LinkedHashSet<String>()
                val lastSub = prefs.getString("last_hotspot_subnet", "").orEmpty().trim()
                if (lastSub.isNotBlank()) cachedSubnets.add(lastSub)
                prefs.getString("recent_hotspot_subnets", "").orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { cachedSubnets.add(it) }
                for (sub in cachedSubnets) {
                    val gwCandidate = "$sub.1"
                    if (isAllowedCarOutboundIp(gwCandidate) && !gwCandidate.startsWith("10.0.2.")) {
                        result.add(gwCandidate)
                    }
                }
            } catch (_: Exception) {
            }
            // 1) Official ConnectivityManager LinkProperties gateway, DNS & DHCP server inspection (100% unlocked on targetSdk = 37 Release!)
            try {
                val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                @Suppress("DEPRECATION")
                val networks = listOfNotNull(cm?.activeNetwork) + cm?.allNetworks.orEmpty()
                for (net in networks.distinct()) {
                    val lp = cm?.getLinkProperties(net) ?: continue
                    val ownIps = lp.linkAddresses.mapNotNull { (it.address as? Inet4Address)?.hostAddress }.toSet()
                    for (dnsAddr in lp.dnsServers) {
                        val dnsIp = (dnsAddr as? Inet4Address)?.hostAddress.orEmpty()
                        if (dnsIp.isNotBlank() && dnsIp !in ownIps && isAllowedCarOutboundIp(dnsIp) && !dnsIp.startsWith("10.0.2.")) {
                            result.add(dnsIp)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val dhcpSrv = lp.dhcpServerAddress?.hostAddress.orEmpty()
                        if (dhcpSrv.isNotBlank() && dhcpSrv !in ownIps && isAllowedCarOutboundIp(dhcpSrv) && !dhcpSrv.startsWith("10.0.2.")) {
                            result.add(dhcpSrv)
                        }
                    }
                    for (route in lp.routes) {
                        val gw = (route.gateway as? Inet4Address)?.hostAddress.orEmpty()
                        if (gw.isNotBlank() && gw != "0.0.0.0" && gw !in ownIps && isAllowedCarOutboundIp(gw) && !gw.startsWith("10.0.2.")) {
                            result.add(gw)
                        }
                    }
                }
            } catch (_: Exception) {
            }
            // 2) `ip route` & `ip -4 addr` gateway inspection (detects dynamic Galaxy swlan0 gateway when Car Wi-Fi joins Hotspot directly)
            try {
                val proc = ProcessBuilder("sh", "-c", "ip route; ip -4 addr; ip neigh").start()
                val routeOut = proc.inputStream.bufferedReader().readText()
                proc.waitFor(500, TimeUnit.MILLISECONDS)
                Regex("""\bvia\s+((?:192\.168|172\.(?:1[6-9]|2\d|3[0-1]))\.\d{1,3}\.\d{1,3})\b""")
                    .findAll(routeOut)
                    .forEach { m ->
                        val gw = m.groupValues[1]
                        if (!gw.endsWith(".0") && !gw.endsWith(".255")) result.add(gw)
                    }
                Regex("""\b((?:192\.168|172\.(?:1[6-9]|2\d|3[0-1]))\.\d{1,3}\.\d{1,3})\s+dev\s+\S+\s+lladdr\b""")
                    .findAll(routeOut)
                    .forEach { m ->
                        val neigh = m.groupValues[1]
                        if (!neigh.endsWith(".0") && !neigh.endsWith(".255")) result.add(neigh)
                    }
            } catch (_: Exception) {
            }
            // 3) WifiManager DHCP gateway
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val dhcp = wifiManager?.dhcpInfo
                if (dhcp != null) {
                    listOf(dhcp.gateway, dhcp.serverAddress).forEach { ipInt ->
                        if (ipInt != 0) {
                            val ip = String.format(
                                java.util.Locale.US,
                                "%d.%d.%d.%d",
                                ipInt and 0xff,
                                ipInt shr 8 and 0xff,
                                ipInt shr 16 and 0xff,
                                ipInt shr 24 and 0xff
                            )
                            if (ip != "0.0.0.0" && !ip.startsWith("127.") && !ip.startsWith("10.0.2.")) {
                                result.add(ip)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            // 4) Fixed Standard Android / iPhone / Wi-Fi Direct / Windows Hotspot Gateways (`:8082` fast probe behind Emulator NAT)
            result.add("192.168.43.1")
            result.add("172.20.10.1")
            result.add("192.168.49.1")
            result.add("192.168.137.1")
            return result.toList()
        }

        /**
         * Strictly allows ONLY:
         * - QEMU SLIRP Hotspot DNS/Gateway (`10.0.2.3`) and Host Loopback (`10.0.2.2`)
         * - Private Wi-Fi/Hotspot LAN `192.168.x.1..254`
         * - Private Hotspot/Tethering LAN `172.16.0.1`..`172.31.255.254`
         * Completely rejects cellular `10.x.x.x` (other than `10.0.2.2`/`10.0.2.3`), CLAT `192.0.0.x`, `127.x.x.x`, `169.254.x.x`.
         */
        fun isAllowedCarOutboundIp(ip: String): Boolean {
            val clean = ip.trim()
            if (clean == "10.0.2.3" || clean == "10.0.2.2") return true
            val parts = clean.split('.')
            if (parts.size != 4) return false
            val o1 = parts[0].toIntOrNull() ?: return false
            val o2 = parts[1].toIntOrNull() ?: return false
            val o3 = parts[2].toIntOrNull() ?: return false
            val o4 = parts[3].toIntOrNull() ?: return false
            if (o3 !in 0..255 || o4 !in 1..254) return false
            if (o1 == 192 && o2 == 168) return true
            if (o1 == 172 && o2 in 16..31) return true
            return false
        }
    }

    private data class ConnectedClient(
        val id: String,
        val session: DefaultWebSocketServerSession,
        var role: String = "unknown", // "car" or "mic"
        var pin: String = "4829",
        var deviceName: String = "Pleos Smart Mic"
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: ApplicationEngine? = null
    private var udpDiscoveryJob: Job? = null
    private var hotspotProbeJob: Job? = null
    private var nativeMqttListenerJob: Job? = null
    private val outboundMicJobs = ConcurrentHashMap<String, Job>()
    private val outboundMicSenders = ConcurrentHashMap<String, (String) -> Unit>()
    private val verifiedOutboundMicKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val knownPhoneCandidateIps: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile
    private var lastLocalMicPacketMs: Long = 0L

    private fun saveKnownHotspotIp(ip: String, isManualHint: Boolean = false) {
        val clean = ip.trim()
        if (!isAllowedCarOutboundIp(clean) || clean.startsWith("10.0.2.")) return
        val parts = clean.split(".")
        if (parts.size != 4) return
        val subnetPrefix = "${parts[0]}.${parts[1]}.${parts[2]}"
        try {
            val prefs = appContext.applicationContext.getSharedPreferences(HOTSPOT_PREFS_NAME, Context.MODE_PRIVATE)
            val prevIps = prefs.getString("recent_hotspot_ips", "").orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() && it != clean }
            val nextIps = (listOf(clean) + prevIps).take(8).joinToString(",")

            val prevSubs = prefs.getString("recent_hotspot_subnets", "").orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() && it != subnetPrefix }
            val nextSubs = (listOf(subnetPrefix) + prevSubs).take(8).joinToString(",")

            val editor = prefs.edit()
                .putString("recent_hotspot_ips", nextIps)
                .putString("recent_hotspot_subnets", nextSubs)
            if (!isManualHint || prefs.getString("last_hotspot_ip", "").isNullOrBlank()) {
                editor.putString("last_hotspot_ip", clean)
                editor.putString("last_hotspot_subnet", subnetPrefix)
            }
            editor.apply()
        } catch (_: Exception) {
        }
    }

    private fun saveKnownHotspotSubnetPrefix(subnetPrefix: String) {
        val cleanSub = subnetPrefix.trim()
        if (!isAllowedCarOutboundIp("$cleanSub.1") || cleanSub.startsWith("10.0.2")) return
        try {
            val prefs = appContext.applicationContext.getSharedPreferences(HOTSPOT_PREFS_NAME, Context.MODE_PRIVATE)
            val prevSubs = prefs.getString("recent_hotspot_subnets", "").orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() && it != cleanSub }
            val nextSubs = (listOf(cleanSub) + prevSubs).take(8).joinToString(",")
            val editor = prefs.edit().putString("recent_hotspot_subnets", nextSubs)
            if (prefs.getString("last_hotspot_subnet", "").isNullOrBlank()) {
                editor.putString("last_hotspot_subnet", cleanSub)
            }
            editor.apply()
        } catch (_: Exception) {
        }
    }

    fun registerCandidatePhoneIps(ips: Collection<String>, port: Int = 8082, deviceName: String = "Pleos Smart Mic") {
        // Launch all candidate IPs in PARALLEL (non-blocking coroutine per IP, connectTimeout = 800ms)
        for (raw in ips) {
            val clean = raw.trim()
                .removePrefix("ws://")
                .removePrefix("http://")
                .substringBefore("/")
                .substringBefore(":")
                .trim()
            if (isAllowedCarOutboundIp(clean)) {
                if (!clean.startsWith("10.0.2.")) {
                    knownPhoneCandidateIps.add(clean)
                }
                connectOutboundToSmartphoneMic(clean, port, currentPin, deviceName)
            }
        }
    }

    /**
     * Offline / Full DNS Block Backup:
     * Accepts full IPv4 (`192.168.159.193`), short 2-octet Hotspot code (`159.193` -> `192.168.159.193` / `10.1` -> `172.20.10.1`),
     * single subnet octet (`159` -> scans `192.168.159.1..254:8082`), or empty string (scans known & gateway `/24` subnets).
     */
    fun connectOrScanHotspotTarget(rawInput: String, port: Int = 8082, deviceName: String = "Pleos Smart Mic") {
        val trimmed = rawInput.trim()
            .removePrefix("ws://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore(":")
            .trim()

        if (trimmed.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))) {
            saveKnownHotspotIp(trimmed, isManualHint = true)
            registerCandidatePhoneIps(listOf(trimmed), port, deviceName)
            return
        }
        // Short 2-octet code: e.g. "159.193" -> "192.168.159.193" (and if "10.x", also "172.20.10.x")
        if (trimmed.matches(Regex("""^\d{1,3}\.\d{1,3}$"""))) {
            val parts = trimmed.split(".")
            val o3 = parts[0].toIntOrNull() ?: return
            val o4 = parts[1].toIntOrNull() ?: return
            if (o3 in 0..255 && o4 in 1..254) {
                val expanded = mutableListOf("192.168.$o3.$o4")
                if (o3 == 10) expanded.add("172.20.10.$o4")
                expanded.forEach { saveKnownHotspotIp(it, isManualHint = true) }
                registerCandidatePhoneIps(expanded, port, deviceName)
            }
            return
        }
        // Single subnet third-octet or empty -> parallel fast `/24` scan on `:8082`
        val hintNum = trimmed.toIntOrNull()
        if (hintNum != null && hintNum in 0..255) {
            saveKnownHotspotSubnetPrefix("192.168.$hintNum")
        }
        probeOfflineHotspotSubnet(trimmed, port, deviceName)
    }

    fun probeOfflineHotspotSubnet(subnetHint: String = "", port: Int = 8082, deviceName: String = "Pleos Smart Mic") {
        scope.launch(Dispatchers.IO) {
            val prefixes = LinkedHashSet<String>()
            val hintNum = subnetHint.trim().toIntOrNull()
            if (hintNum != null && hintNum in 0..255) {
                prefixes.add("192.168.$hintNum")
            }
            for (gw in detectHotspotGatewayIps(appContext) + knownPhoneCandidateIps) {
                connectOutboundToSmartphoneMic(gw, port, currentPin, deviceName)
                val segs = gw.split(".")
                if (segs.size == 4 && (gw.startsWith("192.168.") || gw.startsWith("172.20."))) {
                    prefixes.add("${segs[0]}.${segs[1]}.${segs[2]}")
                }
            }
            val lanIp = detectLocalLanIp(appContext)
            if (lanIp.startsWith("192.168.") || lanIp.startsWith("172.")) {
                val segs = lanIp.split(".")
                if (segs.size == 4) prefixes.add("${segs[0]}.${segs[1]}.${segs[2]}")
            }

            // Fast parallel probe of gateway/host octets across candidate `/24` subnets
            val priorityHosts = listOf(1, 2, 129, 254) + (3..253).filter { it != 129 }
            for (prefix in prefixes.take(3)) {
                for (batch in priorityHosts.chunked(32)) {
                    if (verifiedOutboundMicKeys.isNotEmpty()) return@launch
                    val jobs = batch.map { hostOctet ->
                        val targetIp = "$prefix.$hostOctet"
                        launch(Dispatchers.IO) {
                            var s: Socket? = null
                            try {
                                s = Socket()
                                s.connect(InetSocketAddress(targetIp, port), 180)
                                s.close()
                                registerCandidatePhoneIps(listOf(targetIp), port, deviceName)
                            } catch (_: Exception) {
                            } finally {
                                try { s?.close() } catch (_: Exception) {}
                            }
                        }
                    }
                    jobs.forEach { it.join() }
                }
            }
        }
    }

    private val clients = ConcurrentHashMap<String, ConnectedClient>()
    @Volatile
    private var currentPin: String = initialPin
    @Volatile
    var onMicPcmChunk: ((sampleRate: Int, pcmShorts: ShortArray, seq: Long) -> Unit)? = null
    @Volatile
    var onDirectRemoteCommand: ((type: String, payload: JSONObject) -> Unit)? = null
    @Volatile
    var audioEngineController: io.github.teamcarinkaraoke.pleosstage.car.audio.CarAudioEngineController? = null
    @Volatile
    var onDirectMicConnectionChanged: ((connected: Boolean, deviceName: String, count: Int) -> Unit)? = null
    @Volatile
    var onMicLatencyUpdated: ((totalLatencyMs: Int, netLatencyMs: Int, carBufferMs: Int, transport: String) -> Unit)? = null
    @Volatile
    var onMicVocalTelemetry: ((rms: Float, pitchHz: Float, note: String) -> Unit)? = null
    @Volatile
    var onMicActivityVerified: ((deviceName: String, transport: String) -> Unit)? = null
    private val serverPcmSeqLock = Any()
    private val recentServerPcmSeqRing = LongArray(64) { -1L }
    private var recentServerPcmSeqPos = 0
    private var highestServerPcmSeq: Long = -1L

    private fun shouldAcceptServerPcmSeq(seq: Long): Boolean {
        if (seq <= 0L) return true
        synchronized(serverPcmSeqLock) {
            if (highestServerPcmSeq > 0L && (highestServerPcmSeq - seq) >= 500L) {
                recentServerPcmSeqRing.fill(-1L)
                recentServerPcmSeqPos = 0
                highestServerPcmSeq = -1L
            }
            for (seen in recentServerPcmSeqRing) {
                if (seen == seq) return false
            }
            if (highestServerPcmSeq > 0L && (highestServerPcmSeq - seq) > 30L) {
                return false
            }
            recentServerPcmSeqRing[recentServerPcmSeqPos] = seq
            recentServerPcmSeqPos = (recentServerPcmSeqPos + 1) and 63
            if (seq > highestServerPcmSeq) {
                highestServerPcmSeq = seq
            }
            return true
        }
    }
    val carHostId: String = "car_host_" + UUID.randomUUID().toString().replace("-", "").take(8)
    private val stateRevisionCounter = java.util.concurrent.atomic.AtomicLong(1L)

    fun nextStateRevision(): Long = stateRevisionCounter.incrementAndGet()

    private fun markVerifiedMicActivity(deviceName: String = "Pleos Smart Mic", transport: String = "DIRECT") {
        lastLocalMicPacketMs = System.currentTimeMillis()
        val safeName = deviceName.ifBlank { "Pleos Smart Mic" }
        val activeCount = (clients.values.count { it.role == "mic" } + verifiedOutboundMicKeys.size).coerceAtLeast(1)
        _serverState.update { it.copy(connectedMicCount = activeCount) }
        onMicActivityVerified?.invoke(safeName, transport)
        onDirectMicConnectionChanged?.invoke(true, safeName, activeCount)
    }

    @Volatile
    private var roomStateJson: JSONObject = JSONObject().apply {
        put("carHostId", carHostId)
        put("stateRevision", 1L)
        put("currentSongId", if (BuildConfig.DEBUG) "ncs_short_test_30s" else "")
        put("currentTrackId", if (BuildConfig.DEBUG) "ncs_short_test_30s" else "")
        put("isPlaying", false)
        put("progressSec", 0)
        put("durationSec", if (BuildConfig.DEBUG) 30 else 0)
        put("backingVolume", 85)
        put("mrVolume", 85)
        put("micVolume", 85)
        put("guideVolume", 75)
        put("keyShift", 0)
        put("echoLevel", 80)
        put("reverbLevel", 80)
        put("mrPlaybackRate", 1.0)
        put("syncOffsetMs", 0)
        put("isParked", true)
        put("score", 78)
        put("scoreEnabled", true)
        put("isDevMode", BuildConfig.DEBUG)
        put("guideVocalEnabled", false)
        put("demoVocalWave", false)
        put("guidePitchHz", 0.0)
        put("guideNote", "-")
        put("guideVoiceLevelPercent", 0)
        put(
            "playlist",
            if (BuildConfig.DEBUG) {
                YouTubeUrlParser.toJsonArray(YouTubeUrlParser.DEFAULT_UNIFIED_PLAYLIST)
            } else {
                org.json.JSONArray()
            }
        )
    }

    fun broadcastKaraokeScoreResult(scoreJson: JSONObject) {
        scope.launch {
            sendToRole("mic", "karaoke-score-result", scoreJson)
            sendToRole("monitor", "karaoke-score-result", scoreJson)
        }
    }

    fun broadcastKaraokeScoreDismiss(resultId: String) {
        scope.launch {
            val payload = JSONObject().apply {
                put("resultId", resultId)
                put("ts", System.currentTimeMillis())
            }
            sendToRole("mic", "karaoke-score-dismiss", payload)
            sendToRole("monitor", "karaoke-score-dismiss", payload)
        }
    }

    fun broadcastCarStateSync(stateUpdate: JSONObject) {
        scope.launch {
            val keys = stateUpdate.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                roomStateJson.put(k, stateUpdate.opt(k))
            }
            roomStateJson.put("carHostId", carHostId)
            if (!stateUpdate.has("stateRevision")) {
                roomStateJson.put("stateRevision", stateRevisionCounter.get())
            } else {
                val incomingRev = stateUpdate.optLong("stateRevision", 1L)
                if (incomingRev > stateRevisionCounter.get()) {
                    stateRevisionCounter.set(incomingRev)
                }
            }
            sendToRole("mic", "car-state-sync", roomStateJson)
            sendToRole("monitor", "car-state-sync", roomStateJson)
        }
    }

    private val _serverState = MutableStateFlow(
         run {
            val ip = detectLocalLanIp(appContext)
            EmbeddedServerState(
                isRunning = false,
                port = DEFAULT_PORT,
                lanIp = ip,
                lanWsUrl = "ws://$ip:$DEFAULT_PORT/ws",
                localLoopbackWsUrl = "ws://127.0.0.1:$DEFAULT_PORT/ws",
                emulatorBridgeWsUrl = "ws://10.0.2.2:$DEFAULT_PORT/ws",
                activePin = initialPin
            )
        }
    )
    val serverState: StateFlow<EmbeddedServerState> = _serverState.asStateFlow()

    fun start() {
        if (engine != null) return
        scope.launch {
            val lanIp = detectLocalLanIp(appContext)
            // Clean up any zombie Ktor CIO engine holding port :8080 before rebinding with SO_REUSEADDR
            val staleEngine = activeGlobalEngine
            if (staleEngine != null) {
                activeGlobalEngine = null
                try {
                    staleEngine.stop(150, 300)
                } catch (_: Exception) {
                }
            }

            var startedEngine: CIOApplicationEngine? = null
            var lastStartError: Exception? = null
            for (attempt in 1..4) {
                try {
                    val ktorEngine: CIOApplicationEngine = embeddedServer(
                        factory = CIO,
                        port = DEFAULT_PORT,
                        host = "0.0.0.0",
                        configure = {
                            reuseAddress = true
                        }
                    ) {
                        install(WebSockets) {
                            pingPeriod = Duration.ofSeconds(10)
                            timeout = Duration.ofSeconds(25)
                            maxFrameSize = Long.MAX_VALUE
                            masking = false
                        }

                        routing {
                            options("{...}") {
                                call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                                call.response.header(HttpHeaders.AccessControlAllowMethods, "GET, POST, OPTIONS")
                                call.response.header(HttpHeaders.AccessControlAllowHeaders, "*")
                                call.respondText("", ContentType.Text.Plain, HttpStatusCode.NoContent)
                            }

                            get("/") {
                                call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                                call.respondText(buildServerInfoJson(), ContentType.Application.Json)
                            }

                            get("/api/info") {
                                call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                                call.respondText(buildServerInfoJson(), ContentType.Application.Json)
                            }

                            get("/monitor") {
                                call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                                call.respondText(buildPcMonitorHtml(), ContentType.Text.Html)
                            }

                            get("/yt-player") {
                                val videoId = call.request.queryParameters["videoId"]?.ifBlank { "K4DyBUG242c" } ?: "K4DyBUG242c"
                                val volume = call.request.queryParameters["volume"]?.toIntOrNull()?.coerceIn(0, 100) ?: 85
                                val autoPlay = call.request.queryParameters["autoplay"] != "0"
                                call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                                call.response.header("Referrer-Policy", "strict-origin-when-cross-origin")
                                call.respondText(
                                    buildYouTubeIframeHtml(
                                        initialVideoId = videoId,
                                        initialVolume = volume,
                                        initialAutoPlay = autoPlay
                                    ),
                                    ContentType.Text.Html
                                )
                            }

                            webSocket("/ws") {
                                handleWebSocketSession(this)
                            }

                            webSocket("/") {
                                handleWebSocketSession(this)
                            }
                        }
                    }

                    ktorEngine.start(wait = false)
                    startedEngine = ktorEngine
                    activeGlobalEngine = ktorEngine
                    break
                } catch (e: Exception) {
                    lastStartError = e
                    try {
                        activeGlobalEngine?.stop(100, 200)
                    } catch (_: Exception) {
                    }
                    activeGlobalEngine = null
                    if (attempt < 4) {
                        delay(250L)
                    }
                }
            }

            if (startedEngine != null) {
                engine = startedEngine
                val updatedIp = detectLocalLanIp(appContext)
                _serverState.update {
                    it.copy(
                        isRunning = true,
                        lanIp = updatedIp,
                        lanWsUrl = "ws://$updatedIp:$DEFAULT_PORT/ws",
                        localLoopbackWsUrl = "ws://127.0.0.1:$DEFAULT_PORT/ws",
                        emulatorBridgeWsUrl = "ws://10.0.2.2:$DEFAULT_PORT/ws",
                        activePin = currentPin
                    )
                }
                Log.i(TAG, "Pleos Embedded Ktor Server started on ws://$updatedIp:$DEFAULT_PORT/ws (PIN $currentPin)")

                startUdpDiscoveryResponder()
                startPeriodicHotspotDirectProbeAndWatchdog()
                startNativeMqttCandidateListener()
            } else {
                Log.e(TAG, "Failed to start Ktor Embedded Server on port $DEFAULT_PORT: ${lastStartError?.message}", lastStartError)
                _serverState.update {
                    it.copy(
                        lanIp = lanIp,
                        lanWsUrl = "ws://$lanIp:$DEFAULT_PORT/ws"
                    )
                }
                startPeriodicHotspotDirectProbeAndWatchdog()
                startNativeMqttCandidateListener()
            }
        }
    }

    private fun buildServerInfoJson(): String {
        val ip = detectLocalLanIp(appContext)
        val micCount = clients.values.count { it.role == "mic" }
        val hasCar = clients.values.any { it.role == "car" }
        return JSONObject().apply {
            put("status", "ok")
            put("engine", "Pleos Embedded Ktor CIO Server")
            put("pin", currentPin)
            put("lanIp", ip)
            put("port", DEFAULT_PORT)
            put("wsUrl", "ws://$ip:$DEFAULT_PORT/ws")
            put("lanUrl", "ws://$ip:$DEFAULT_PORT/ws")
            put("emulatorUrl", "ws://10.0.2.2:$DEFAULT_PORT/ws")
            put("hasCar", hasCar)
            put("micCount", micCount)
        }.toString()
    }

    private fun buildPcMonitorHtml(): String = """
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Pleos Stage · PC 실시간 오디오 모니터 (:8080/monitor)</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Pretendard", "Segoe UI", Roboto, sans-serif; }
    body { background: #0B1120; color: #F8FAFC; min-height: 100vh; display: flex; flex-direction: column; align-items: center; padding: 28px 20px; }
    .container { width: 100%; max-width: 920px; display: flex; flex-direction: column; gap: 18px; }
    .card { background: #131C31; border: 1px solid #1E293B; border-radius: 20px; padding: 22px 24px; box-shadow: 0 12px 32px rgba(0,0,0,0.35); }
    .header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
    .badge { display: inline-flex; align-items: center; gap: 8px; padding: 6px 14px; border-radius: 999px; font-size: 13px; font-weight: 800; background: #1E293B; color: #38BDF8; }
    .badge.live { background: rgba(16, 185, 129, 0.18); color: #34D399; border: 1px solid rgba(16, 185, 129, 0.4); }
    .btn-primary { width: 100%; padding: 18px 24px; border-radius: 16px; border: none; font-size: 18px; font-weight: 900; cursor: pointer; background: linear-gradient(90deg, #2563EB, #7C3AED); color: #FFF; transition: transform 0.12s ease, opacity 0.15s ease; }
    .btn-primary:hover { opacity: 0.95; transform: translateY(-1px); }
    .btn-primary.active { background: linear-gradient(90deg, #059669, #0284C7); }
    .track-title { font-size: 26px; font-weight: 900; color: #FFFFFF; margin-top: 6px; }
    .track-sub { font-size: 14px; color: #94A3B8; margin-top: 4px; }
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    @media (max-width: 720px) { .grid { grid-template-columns: 1fr; } }
    .meter-box { background: #0F172A; border-radius: 14px; padding: 16px; border: 1px solid #1E293B; }
    .meter-label { font-size: 13px; font-weight: 700; color: #94A3B8; display: flex; justify-content: space-between; margin-bottom: 8px; }
    .meter-bar-bg { width: 100%; height: 12px; background: #1E293B; border-radius: 999px; overflow: hidden; }
    .meter-bar-fill { height: 100%; width: 0%; border-radius: 999px; transition: width 0.08s linear; }
    canvas { width: 100%; height: 120px; background: #090E1A; border-radius: 14px; border: 1px solid #1E293B; display: block; margin-top: 12px; }
    .controls { display: flex; gap: 10px; margin-top: 14px; flex-wrap: wrap; }
    .btn-ctrl { padding: 10px 18px; border-radius: 12px; border: 1px solid #334155; background: #1E293B; color: #F8FAFC; font-weight: 800; font-size: 14px; cursor: pointer; }
    .btn-ctrl:hover { background: #334155; }
    #ytHiddenWrap { position: fixed; width: 1px; height: 1px; opacity: 0.01; pointer-events: none; bottom: 0; right: 0; overflow: hidden; }
  </style>
</head>
<body>
  <div class="container">
    <div class="card">
      <div class="header">
        <div>
          <div style="font-size: 13px; font-weight: 800; color: #38BDF8; letter-spacing: 0.5px;">PLEOS STAGE · REAL-TIME PC AUDIO BRIDGE</div>
          <h1 style="font-size: 22px; font-weight: 900; margin-top: 4px;">🔊 PC 브라우저 실시간 오디오 모니터 (반주 + 마이크 리버브)</h1>
        </div>
        <div id="wsBadge" class="badge">● WS 연결 대기 중...</div>
      </div>
      <p style="font-size: 14px; color: #94A3B8; margin-top: 10px; line-height: 1.5;">
        안드로이드 에뮬레이터 오디오 출력이 들리지 않을 때, 아래 버튼을 한 번 클릭하면 차량의 <b>실시간 반주(MR/유튜브/신디사이저)</b>와 스마트폰 마이크의 <b>콘서트홀 Wet 리버브 보컬</b>이 현재 PC 스피커로 즉시 출력됩니다.
      </p>
      <button id="startAudioBtn" class="btn-primary" style="margin-top: 16px;" onclick="enableMonitorAudio()">
        🔊 여기를 클릭하여 PC 스피커 실시간 오디오 모니터링 켜기
      </button>
    </div>

    <div class="card">
      <div style="display:flex; justify-content:space-between; align-items:center;">
        <span id="playStatusBadge" class="badge">⏸ 대기 중 (PAUSED)</span>
        <span id="timeLabel" style="font-size:15px; font-weight:800; color:#38BDF8;">0:00 / 0:30</span>
      </div>
      <div id="trackTitle" class="track-title">테스트 음원</div>
      <div id="trackSub" class="track-sub">Pleos Stage Live Audio Engine · Key 0 · Echo 80%</div>

      <canvas id="fftCanvas" width="840" height="120"></canvas>

      <div class="controls">
        <button class="btn-ctrl" onclick="sendRemoteCmd('START_SONG')">▶ 차량 반주 재생</button>
        <button class="btn-ctrl" onclick="sendRemoteCmd('PAUSE_SONG')">⏸ 일시정지</button>
        <button class="btn-ctrl" onclick="sendRemoteCmd('TRIGGER_SFX', {sfx:'cheer'})">👏 환호성 효과음</button>
        <button class="btn-ctrl" onclick="sendRemoteCmd('TRIGGER_SFX', {sfx:'fanfare'})">🎉 팡파레 효과음</button>
      </div>
    </div>

    <div class="grid">
      <div class="meter-box">
        <div class="meter-label">
          <span>🎵 실시간 반주(MR) 출력 레벨</span>
          <span id="mrVolText">85%</span>
        </div>
        <div class="meter-bar-bg">
          <div id="mrBar" class="meter-bar-fill" style="background: linear-gradient(90deg, #3B82F6, #A855F7);"></div>
        </div>
        <div style="margin-top:12px; display:flex; align-items:center; gap:10px; font-size:13px; color:#CBD5E1;">
          <span>PC 반주 볼륨</span>
          <input id="pcMrSlider" type="range" min="0" max="100" value="85" style="flex:1;" oninput="updatePcVolumes()" />
        </div>
      </div>

      <div class="meter-box">
        <div class="meter-label">
          <span>🎤 마이크 보컬 + 콘서트홀 리버브 수신</span>
          <span id="micTelemetryText">0% · -</span>
        </div>
        <div class="meter-bar-bg">
          <div id="micBar" class="meter-bar-fill" style="background: linear-gradient(90deg, #10B981, #38BDF8);"></div>
        </div>
        <div style="margin-top:12px; display:flex; align-items:center; gap:10px; font-size:13px; color:#CBD5E1;">
          <span>PC 마이크 리버브 볼륨</span>
          <input id="pcMicSlider" type="range" min="0" max="150" value="100" style="flex:1;" oninput="updatePcVolumes()" />
        </div>
      </div>
    </div>
  </div>

  <div id="ytHiddenWrap"><div id="ytPlayer"></div></div>

  <script>
    let audioCtx = null;
    let masterGain = null;
    let backingGain = null;
    let micDryGain = null;
    let micWetGain = null;
    let convolver = null;
    let analyser = null;
    let audioEnabled = false;
    let ws = null;
    let nextPcmTime = 0;

    let carState = {
      isPlaying: false,
      currentTrackId: 'ncs_short_test_30s',
      title: '테스트 음원',
      artist: 'Pleos Stage · 테스트 음원',
      videoId: 'K4DyBUG242c',
      progressSec: 0,
      durationSec: 30,
      backingVolume: 85,
      echoLevel: 80,
      keyShift: 0,
      bpm: 128
    };

    let synthTimer = null;
    let stepIndex = 0;
    let ytPlayer = null;
    let ytReady = false;
    let currentYtVideoId = '';

    const tag = document.createElement('script');
    tag.src = "https://www.youtube.com/iframe_api";
    document.head.appendChild(tag);

    window.onYouTubeIframeAPIReady = function() {
      ytPlayer = new YT.Player('ytPlayer', {
        height: '1',
        width: '1',
        videoId: 'K4DyBUG242c',
        playerVars: { autoplay: 0, controls: 0, playsinline: 1, origin: window.location.origin },
        events: {
          onReady: () => { ytReady = true; syncPlaybackEngine(); }
        }
      });
    };

    function createConcertHallImpulse(ctx, seconds, decay) {
      const rate = ctx.sampleRate;
      const len = rate * seconds;
      const impulse = ctx.createBuffer(2, len, rate);
      for (let ch = 0; ch < 2; ch++) {
        const buf = impulse.getChannelData(ch);
        for (let i = 0; i < len; i++) {
          buf[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, decay);
        }
      }
      return impulse;
    }

    function enableMonitorAudio() {
      if (!audioCtx) {
        const AC = window.AudioContext || window.webkitAudioContext;
        audioCtx = new AC();
        masterGain = audioCtx.createGain();
        masterGain.gain.value = 1.0;

        backingGain = audioCtx.createGain();
        backingGain.gain.value = 0.85;

        micDryGain = audioCtx.createGain();
        micDryGain.gain.value = 0.85;

        micWetGain = audioCtx.createGain();
        micWetGain.gain.value = 0.75;

        convolver = audioCtx.createConvolver();
        convolver.buffer = createConcertHallImpulse(audioCtx, 2.4, 2.6);

        analyser = audioCtx.createAnalyser();
        analyser.fftSize = 256;

        backingGain.connect(analyser);
        micDryGain.connect(analyser);
        convolver.connect(micWetGain);
        micWetGain.connect(analyser);

        analyser.connect(masterGain);
        masterGain.connect(audioCtx.destination);
      }
      if (audioCtx.state === 'suspended') {
        audioCtx.resume();
      }
      audioEnabled = true;
      const btn = document.getElementById('startAudioBtn');
      btn.textContent = '✅ PC 실시간 오디오 출력 활성화됨 (반주 + 마이크 리버브 스트리밍 중)';
      btn.classList.add('active');
      syncPlaybackEngine();
    }

    function updatePcVolumes() {
      const mrVal = Number(document.getElementById('pcMrSlider').value || 85);
      const micVal = Number(document.getElementById('pcMicSlider').value || 100);
      document.getElementById('mrVolText').textContent = mrVal + '%';
      if (backingGain && audioCtx) {
        backingGain.gain.setTargetAtTime(mrVal / 100, audioCtx.currentTime, 0.03);
      }
      if (ytReady && ytPlayer && ytPlayer.setVolume) {
        ytPlayer.setVolume(mrVal);
      }
      if (micDryGain && micWetGain && audioCtx) {
        micDryGain.gain.setTargetAtTime((micVal / 100) * 0.9, audioCtx.currentTime, 0.03);
        micWetGain.gain.setTargetAtTime((micVal / 100) * (carState.echoLevel / 100) * 0.95, audioCtx.currentTime, 0.03);
      }
    }

    function scheduleSynthStep() {
      if (!audioEnabled || !audioCtx || !carState.isPlaying) return;
      const now = audioCtx.currentTime;
      const isBallad = (carState.currentTrackId || '').includes('ballad') || (carState.currentTrackId || '').includes('invincible');
      const shiftRatio = Math.pow(2, (carState.keyShift || 0) / 12);
      const chordProg = isBallad
        ? [[220, 261.63, 329.63], [174.61, 220, 261.63], [130.81, 164.81, 196.00], [196.00, 246.94, 293.66]]
        : [[261.63, 329.63, 392.00], [196.00, 246.94, 293.66], [220.00, 261.63, 329.63], [174.61, 220.00, 261.63]];
      const melodyProg = isBallad
        ? [440, 523.25, 659.25, 587.33, 523.25, 440, 392, 440]
        : [523.25, 659.25, 783.99, 659.25, 587.33, 523.25, 440, 392];

      const bar = Math.floor(stepIndex / 4) % chordProg.length;
      const beat = stepIndex % 4;
      const chord = chordProg[bar];

      // Bass on every beat
      const bassOsc = audioCtx.createOscillator();
      const bassG = audioCtx.createGain();
      bassOsc.type = 'triangle';
      bassOsc.frequency.setValueAtTime((chord[0] / 2) * shiftRatio, now);
      bassG.gain.setValueAtTime(0.24, now);
      bassG.gain.exponentialRampToValueAtTime(0.001, now + 0.28);
      bassOsc.connect(bassG);
      bassG.connect(backingGain);
      bassOsc.start(now);
      bassOsc.stop(now + 0.3);

      // Chord pad on beat 0 and 2
      if (beat === 0 || beat === 2) {
        chord.forEach(freq => {
          const osc = audioCtx.createOscillator();
          const g = audioCtx.createGain();
          osc.type = 'sine';
          osc.frequency.setValueAtTime(freq * shiftRatio, now);
          g.gain.setValueAtTime(0.09, now);
          g.gain.exponentialRampToValueAtTime(0.001, now + 0.45);
          osc.connect(g);
          g.connect(backingGain);
          osc.start(now);
          osc.stop(now + 0.48);
        });
      }

      // Lead melody
      const melFreq = melodyProg[stepIndex % melodyProg.length] * shiftRatio;
      const melOsc = audioCtx.createOscillator();
      const melG = audioCtx.createGain();
      melOsc.type = 'sawtooth';
      melOsc.frequency.setValueAtTime(melFreq, now);
      melG.gain.setValueAtTime(0.06, now);
      melG.gain.exponentialRampToValueAtTime(0.001, now + 0.22);
      melOsc.connect(melG);
      melG.connect(backingGain);
      melOsc.start(now);
      melOsc.stop(now + 0.24);

      stepIndex++;
    }

    function syncPlaybackEngine() {
      if (synthTimer) {
        clearInterval(synthTimer);
        synthTimer = null;
      }
      const isBuiltInSynth = !carState.videoId &&
        (carState.currentTrackId === 'kpop_neon_drive' || carState.currentTrackId === 'ballad_starlight_highway');
      if (carState.isPlaying && audioEnabled && isBuiltInSynth) {
        const bpm = carState.bpm || 118;
        const intervalMs = Math.max(180, Math.round((60 / bpm / 2) * 1000));
        scheduleSynthStep();
        synthTimer = setInterval(scheduleSynthStep, intervalMs);
      }
      if (ytReady && ytPlayer) {
        try {
          if (carState.videoId && carState.videoId !== currentYtVideoId) {
            currentYtVideoId = carState.videoId;
            if (carState.isPlaying && audioEnabled) {
              ytPlayer.loadVideoById(currentYtVideoId, carState.progressSec || 0);
            } else {
              ytPlayer.cueVideoById(currentYtVideoId, carState.progressSec || 0);
            }
          } else {
            if (carState.isPlaying && audioEnabled) {
              ytPlayer.playVideo();
            } else {
              ytPlayer.pauseVideo();
            }
          }
        } catch (_e) {}
      }
    }

    function playIncomingPcmChunk(sr, pcmArray) {
      if (!audioEnabled || !audioCtx || !pcmArray || !pcmArray.length) return;
      const len = pcmArray.length;
      const buf = audioCtx.createBuffer(1, len, sr || 16000);
      const ch = buf.getChannelData(0);
      for (let i = 0; i < len; i++) {
        ch[i] = Math.max(-1, Math.min(1, pcmArray[i] / 32768));
      }
      const src = audioCtx.createBufferSource();
      src.buffer = buf;
      src.connect(micDryGain);
      src.connect(convolver);

      const now = audioCtx.currentTime;
      if (nextPcmTime < now || nextPcmTime > now + 0.25) {
        nextPcmTime = now + 0.015;
      }
      src.start(nextPcmTime);
      nextPcmTime += buf.duration;
    }

    function playSfx(sfxType) {
      if (!audioEnabled || !audioCtx) return;
      const now = audioCtx.currentTime;
      const notes = sfxType === 'fanfare' ? [523.25, 659.25, 783.99, 1046.5] : [440, 554.37, 659.25, 880];
      notes.forEach((f, idx) => {
        const osc = audioCtx.createOscillator();
        const g = audioCtx.createGain();
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(f, now + idx * 0.08);
        g.gain.setValueAtTime(0.18, now + idx * 0.08);
        g.gain.exponentialRampToValueAtTime(0.001, now + idx * 0.08 + 0.35);
        osc.connect(g);
        g.connect(masterGain);
        osc.start(now + idx * 0.08);
        osc.stop(now + idx * 0.08 + 0.38);
      });
    }

    function formatSec(sec) {
      const s = Math.max(0, Math.floor(sec || 0));
      return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');
    }

    function applyCarState(st) {
      if (!st) return;
      const prevPlaying = carState.isPlaying;
      const prevTrack = carState.currentTrackId;
      if (typeof st.isPlaying === 'boolean') carState.isPlaying = st.isPlaying;
      if (st.currentTrackId || st.currentSongId) carState.currentTrackId = st.currentTrackId || st.currentSongId;
      if (typeof st.progressSec === 'number') carState.progressSec = st.progressSec;
      if (typeof st.durationSec === 'number') carState.durationSec = st.durationSec;
      if (typeof st.backingVolume === 'number') carState.backingVolume = st.backingVolume;
      if (typeof st.echoLevel === 'number') carState.echoLevel = st.echoLevel;
      if (typeof st.keyShift === 'number') carState.keyShift = st.keyShift;

      if (Array.isArray(st.playlist)) {
        const found = st.playlist.find(t => t.id === carState.currentTrackId) || st.playlist[0];
        if (found) {
          carState.title = found.title || carState.title;
          carState.artist = found.artist || carState.artist;
          carState.videoId = found.videoId || '';
        }
      }

      document.getElementById('trackTitle').textContent = carState.title;
      document.getElementById('trackSub').textContent = `${'$'}{carState.artist} · Key ${'$'}{carState.keyShift >= 0 ? '+' + carState.keyShift : carState.keyShift} · Echo ${'$'}{carState.echoLevel}%`;
      document.getElementById('timeLabel').textContent = `${'$'}{formatSec(carState.progressSec)} / ${'$'}{formatSec(carState.durationSec)}`;

      const pBadge = document.getElementById('playStatusBadge');
      if (carState.isPlaying) {
        pBadge.textContent = '▶ 실시간 반주 재생 중 (LIVE PLAYING)';
        pBadge.className = 'badge live';
      } else {
        pBadge.textContent = '⏸ 일시정지 / 대기 중 (PAUSED)';
        pBadge.className = 'badge';
      }

      if (prevPlaying !== carState.isPlaying || prevTrack !== carState.currentTrackId) {
        syncPlaybackEngine();
      } else if (ytReady && ytPlayer && ytPlayer.getCurrentTime && ytPlayer.seekTo && typeof carState.progressSec === 'number') {
        try {
          const curYt = Number(ytPlayer.getCurrentTime() || 0);
          if (Math.abs(curYt - carState.progressSec) >= 2.5) {
            ytPlayer.seekTo(carState.progressSec, true);
          }
        } catch (_se) {}
      }
    }

    function connectWs() {
      const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
      ws = new WebSocket(`${'$'}{proto}//${'$'}{location.host}/ws`);
      ws.onopen = () => {
        const b = document.getElementById('wsBadge');
        b.textContent = '● 차량 서버(:8080) 실시간 연결됨';
        b.className = 'badge live';
        ws.send(JSON.stringify({ event: 'register-monitor', data: {} }));
      };
      ws.onmessage = (ev) => {
        try {
          const msg = JSON.parse(ev.data);
          const event = msg.event || msg.type;
          const data = msg.data || msg;
          if (event === 'car-state-sync') {
            applyCarState(data);
          } else if (event === 'vocal-telemetry') {
            const rms = Math.min(100, Math.round(data.rms || 0));
            const pitch = Math.round(data.pitch || 0);
            const note = data.note || '-';
            document.getElementById('micTelemetryText').textContent = `${'$'}{rms}% · ${'$'}{pitch > 0 ? pitch + 'Hz (' + note + ')' : note}`;
            document.getElementById('micBar').style.width = rms + '%';
          } else if (event === 'audio-pcm-chunk') {
            playIncomingPcmChunk(data.sr || 16000, data.pcm);
          } else if (event === 'remote-command' && data.type === 'TRIGGER_SFX') {
            playSfx(data.sfx || 'cheer');
          }
        } catch (_e) {}
      };
      ws.onclose = () => {
        const b = document.getElementById('wsBadge');
        b.textContent = '● 재연결 중...';
        b.className = 'badge';
        setTimeout(connectWs, 1500);
      };
    }

    function sendRemoteCmd(type, extra) {
      if (!ws || ws.readyState !== 1) return;
      const payload = Object.assign({ type }, extra || {});
      ws.send(JSON.stringify({ event: 'remote-command', data: payload }));
    }

    const canvas = document.getElementById('fftCanvas');
    const ctx2d = canvas.getContext('2d');
    const freqData = new Uint8Array(64);

    function renderLoop() {
      ctx2d.clearRect(0, 0, canvas.width, canvas.height);
      const bars = 48;
      const gap = 5;
      const barW = (canvas.width - gap * (bars + 1)) / bars;

      let avg = 0;
      if (analyser && audioEnabled && carState.isPlaying) {
        analyser.getByteFrequencyData(freqData);
      } else {
        freqData.fill(0);
      }

      for (let i = 0; i < bars; i++) {
        const v = carState.isPlaying ? (freqData[i] / 255) : 0;
        avg += v;
        const h = carState.isPlaying ? Math.max(6, v * (canvas.height - 20)) : 5;
        const x = gap + i * (barW + gap);
        const y = (canvas.height - h) / 2;
        ctx2d.fillStyle = carState.isPlaying ? (i % 2 === 0 ? '#3B82F6' : '#8B5CF6') : '#1E293B';
        ctx2d.beginPath();
        ctx2d.roundRect(x, y, barW, h, 4);
        ctx2d.fill();
      }
      document.getElementById('mrBar').style.width = carState.isPlaying ? Math.min(100, Math.round((avg / bars) * 140)) + '%' : '0%';
      requestAnimationFrame(renderLoop);
    }

    connectWs();
    renderLoop();
  </script>
</body>
</html>
    """.trimIndent()

    private suspend fun handleWebSocketSession(session: DefaultWebSocketServerSession) {
        val clientId = "client_${UUID.randomUUID().toString().take(8)}"
        val client = ConnectedClient(id = clientId, session = session, pin = currentPin)
        clients[clientId] = client

        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    handleIncomingJsonMessage(client, text)
                }
            }
        } catch (_: Exception) {
        } finally {
            clients.remove(clientId)
            if (client.role == "mic") {
                val remainingMics = clients.values.count { it.role == "mic" } + verifiedOutboundMicKeys.size
                _serverState.update { it.copy(connectedMicCount = remainingMics) }
                if (remainingMics > 0) {
                    onDirectMicConnectionChanged?.invoke(true, "Pleos Smart Mic", remainingMics)
                } else if (System.currentTimeMillis() - lastLocalMicPacketMs > 6000L) {
                    onDirectMicConnectionChanged?.invoke(false, "○ 연결 대기 중", 0)
                }
                val payload = JSONObject().apply {
                    put("micId", client.id)
                    put("micCount", remainingMics)
                }
                sendToRole("car", "mic-disconnected", payload)
            } else if (client.role == "car") {
                val payload = JSONObject().apply {
                    put("pin", client.pin)
                }
                sendToRole("mic", "car-disconnected", payload)
            }
        }
    }

    /**
     * Zero-JSONObject / Zero-JSONArray fast-path for `audio-pcm-chunk` frames when no external
     * monitor browser is attached. Eliminates ~24,000 boxed Integer allocations per second so
     * GC pauses never delay real-time audio packet ingestion.
     */
    private fun tryFastDispatchAudioPcmChunk(rawText: String, onVerified: () -> Unit): Boolean {
        if (!rawText.contains("\"audio-pcm-chunk\"")) return false
        if (clients.values.any { it.role == "monitor" }) return false
        val pcmKeyIdx = rawText.indexOf("\"pcm\"")
        if (pcmKeyIdx < 0) return false
        val openBracket = rawText.indexOf('[', pcmKeyIdx + 5)
        if (openBracket < 0) return false
        val closeBracket = rawText.indexOf(']', openBracket + 1)
        if (closeBracket <= openBracket) return false

        fun parseJsonLongField(fieldKey: String, defaultVal: Long): Long {
            val kIdx = rawText.indexOf(fieldKey)
            if (kIdx < 0) return defaultVal
            val colon = rawText.indexOf(':', kIdx + fieldKey.length)
            if (colon < 0) return defaultVal
            var p = colon + 1
            val n = rawText.length
            while (p < n && (rawText[p] == ' ' || rawText[p] == '\t' || rawText[p] == '"')) p++
            var sign = 1L
            if (p < n && rawText[p] == '-') {
                sign = -1L
                p++
            }
            var acc = 0L
            var hasDigit = false
            while (p < n) {
                val ch = rawText[p]
                if (ch in '0'..'9') {
                    acc = acc * 10L + (ch - '0')
                    hasDigit = true
                    p++
                } else {
                    break
                }
            }
            return if (hasDigit) acc * sign else defaultVal
        }

        onVerified()
        val seq = parseJsonLongField("\"seq\"", -1L)
        if (!shouldAcceptServerPcmSeq(seq)) return true
        val sr = parseJsonLongField("\"sr\"", 48000L).toInt().coerceIn(8000, 48000)

        var count = 0
        var hasToken = false
        for (i in (openBracket + 1) until closeBracket) {
            val c = rawText[i]
            if (c == ',') {
                if (hasToken) {
                    count++
                    hasToken = false
                }
            } else if ((c in '0'..'9') || c == '-') {
                hasToken = true
            }
        }
        if (hasToken) count++
        if (count <= 0) return true

        val shorts = ShortArray(count)
        var outIdx = 0
        var sign = 1
        var acc = 0
        var inNum = false
        for (i in (openBracket + 1) until closeBracket) {
            val c = rawText[i]
            when {
                c == '-' -> {
                    sign = -1
                    acc = 0
                    inNum = true
                }
                c in '0'..'9' -> {
                    acc = acc * 10 + (c - '0')
                    inNum = true
                }
                c == ',' -> {
                    if (inNum && outIdx < count) {
                        shorts[outIdx++] = (acc * sign).coerceIn(-32768, 32767).toShort()
                    }
                    sign = 1
                    acc = 0
                    inNum = false
                }
            }
        }
        if (inNum && outIdx < count) {
            shorts[outIdx++] = (acc * sign).coerceIn(-32768, 32767).toShort()
        }
        val finalShorts = if (outIdx == shorts.size) shorts else shorts.copyOf(outIdx)
        if (finalShorts.isNotEmpty()) {
            onMicPcmChunk?.invoke(sr, finalShorts, seq)
        }
        return true
    }

    private suspend fun handleIncomingJsonMessage(client: ConnectedClient, rawText: String) {
        if (tryFastDispatchAudioPcmChunk(rawText) { markVerifiedMicActivity(client.deviceName, "DIRECT") }) {
            return
        }
        val root = try {
            JSONObject(rawText)
        } catch (_: Exception) {
            return
        }

        val event = root.optString("event", root.optString("type", ""))
        val data = root.optJSONObject("data") ?: root

        when (event) {
            "register-car" -> {
                val requestedPin = data.optString("pin", "").trim()
                if (requestedPin.matches(Regex("^\\d{4}$"))) {
                    currentPin = requestedPin
                }
                client.role = "car"
                client.pin = currentPin

                val ip = detectLocalLanIp(appContext)
                val wsLanUrl = "ws://$ip:$DEFAULT_PORT/ws"
                val micCount = clients.values.count { it.role == "mic" }

                _serverState.update {
                    it.copy(
                        isRunning = true,
                        lanIp = ip,
                        lanWsUrl = wsLanUrl,
                        activePin = currentPin,
                        connectedMicCount = micCount
                    )
                }

                val resp = JSONObject().apply {
                    put("ok", true)
                    put("pin", currentPin)
                    put("roomId", "pleos-room-$currentPin")
                    put("lanIp", ip)
                    put("port", DEFAULT_PORT)
                    put("lanUrl", wsLanUrl)
                    put("wsUrl", wsLanUrl)
                    put("emulatorUrl", "ws://10.0.2.2:$DEFAULT_PORT/ws")
                    put("micCount", micCount)
                }
                sendEvent(client, "car-registered", resp)

                val readyPayload = JSONObject().apply {
                    put("pin", currentPin)
                    put("roomId", "pleos-room-$currentPin")
                    put("state", roomStateJson)
                }
                sendToRole("mic", "car-ready", readyPayload)
            }

            "join-pin", "mic-ping" -> {
                val inputPin = data.optString("pin", "").trim()
                val deviceName = data.optString("deviceName", "Pleos Smart Mic").ifBlank { "Pleos Smart Mic" }
                val phoneIp = data.optString("phoneIp", "").trim()
                val phonePort = data.optInt("phonePort", 8082)
                val candArr = data.optJSONArray("candidateIps")
                val candList = mutableListOf<String>()
                if (phoneIp.isNotBlank()) candList.add(phoneIp)
                if (candArr != null) {
                    for (i in 0 until candArr.length()) {
                        val ipItem = candArr.optString(i, "").trim()
                        if (ipItem.isNotBlank()) candList.add(ipItem)
                    }
                }
                if (candList.isNotEmpty()) {
                    candList.firstOrNull { isAllowedCarOutboundIp(it) && !it.startsWith("10.0.2.") }?.let {
                        saveKnownHotspotIp(it)
                    }
                    registerCandidatePhoneIps(candList, phonePort, deviceName)
                }

                // Match exact 4-digit PIN, or allow 'AUTO' / single active Car room on same Wi-Fi
                val isPinValid = inputPin == currentPin || inputPin == "AUTO" || inputPin.matches(Regex("^\\d{4}$"))
                if (!isPinValid) {
                    val err = JSONObject().apply {
                        put("ok", false)
                        put("error", "유효하지 않은 4자리 PIN 번호입니다 (차량 PIN: $currentPin)")
                    }
                    sendEvent(client, "join-error", err)
                    return
                }

                client.role = "mic"
                client.pin = currentPin
                client.deviceName = deviceName
                markVerifiedMicActivity(deviceName, "DIRECT")

                val micCount = (clients.values.count { it.role == "mic" } + verifiedOutboundMicKeys.size).coerceAtLeast(1)
                val hasCar = clients.values.any { it.role == "car" }
                _serverState.update { it.copy(connectedMicCount = micCount) }

                val okResp = JSONObject().apply {
                    put("ok", true)
                    put("pin", currentPin)
                    put("roomId", "pleos-room-$currentPin")
                    put("carConnected", hasCar)
                    put("state", roomStateJson)
                }
                sendEvent(client, "paired-with-car", okResp)
                sendEvent(
                    client,
                    "car-pong",
                    JSONObject().apply {
                        put("pin", currentPin)
                        put("ts", System.currentTimeMillis())
                    }
                )

                val micConnectedPayload = JSONObject().apply {
                    put("micId", client.id)
                    put("deviceName", deviceName)
                    put("pin", currentPin)
                    put("micCount", micCount)
                }
                sendToRole("car", "mic-connected", micConnectedPayload)
            }

            "phone-outbound-offer", "CANDIDATE_IPS", "HELLO" -> {
                val phoneIp = data.optString("phoneIp", "").trim()
                val phonePort = data.optInt("phonePort", 8082)
                val deviceName = data.optString("deviceName", "Pleos Smart Mic").ifBlank { "Pleos Smart Mic" }
                val candArr = data.optJSONArray("candidateIps")
                val candList = mutableListOf<String>()
                if (phoneIp.isNotBlank()) candList.add(phoneIp)
                if (candArr != null) {
                    for (i in 0 until candArr.length()) {
                        val ipItem = candArr.optString(i, "").trim()
                        if (ipItem.isNotBlank()) candList.add(ipItem)
                    }
                }
                if (candList.isNotEmpty()) {
                    registerCandidatePhoneIps(candList, phonePort, deviceName)
                }
            }

            "webrtc-offer" -> {
                val payload = JSONObject().apply {
                    put("sdp", data.opt("sdp"))
                    put("from", client.id)
                    put("pin", currentPin)
                }
                sendToRole("car", "webrtc-offer", payload)
            }

            "webrtc-answer" -> {
                val payload = JSONObject().apply {
                    put("sdp", data.opt("sdp"))
                    put("from", client.id)
                    put("pin", currentPin)
                }
                sendToRole("mic", "webrtc-answer", payload)
            }

            "webrtc-ice-candidate" -> {
                val payload = JSONObject().apply {
                    put("candidate", data.opt("candidate"))
                    put("from", client.id)
                    put("pin", currentPin)
                }
                val targetRole = if (client.role == "mic") "car" else "mic"
                sendToRole(targetRole, "webrtc-ice-candidate", payload)
            }

            "latency-ping" -> {
                markVerifiedMicActivity(client.deviceName, "DIRECT")
                val seq = data.optLong("seq", 0L)
                val clientSendTs = data.optLong("clientSendTs", System.currentTimeMillis())
                val carBufferMs = audioEngineController?.getBufferedMicLatencyMs() ?: 12
                val reportedNetMs = data.optInt("lastNetLatencyMs", 3).coerceIn(1, 200)
                val totalMs = reportedNetMs + 11 + carBufferMs
                val pongData = JSONObject().apply {
                    put("type", "latency-pong")
                    put("seq", seq)
                    put("clientSendTs", clientSendTs)
                    put("carBufferMs", carBufferMs)
                    put("transport", "DIRECT")
                }
                sendEvent(client, "latency-pong", pongData)
                onMicLatencyUpdated?.invoke(totalMs, reportedNetMs, carBufferMs, "DIRECT")
            }

            "vocal-telemetry" -> {
                val tr = data.optString("transport", "DIRECT").ifBlank { "DIRECT" }
                markVerifiedMicActivity(client.deviceName, tr)
                if (data.has("totalLatencyMs")) {
                    val totalMs = data.optInt("totalLatencyMs", 26).coerceIn(10, 350)
                    val netMs = data.optInt("netLatencyMs", 3).coerceIn(1, 250)
                    val bufMs = data.optInt("carBufferMs", 12).coerceIn(6, 120)
                    onMicLatencyUpdated?.invoke(totalMs, netMs, bufMs, tr)
                }
                val rmsVal = data.optDouble("rms", 0.0).toFloat()
                val pitchVal = data.optDouble("pitch", 0.0).toFloat()
                val note = data.optString("note", "-")
                onMicVocalTelemetry?.invoke(rmsVal, pitchVal, note)
                val payload = JSONObject().apply {
                    put("rms", rmsVal.toDouble())
                    put("pitch", pitchVal.toDouble())
                    put("note", note)
                    put("from", client.id)
                    put("ts", data.optLong("ts", System.currentTimeMillis()))
                }
                sendToRole("car", "vocal-telemetry", payload)
                sendToRole("monitor", "vocal-telemetry", payload)
            }

            "audio-pcm-chunk" -> {
                markVerifiedMicActivity(client.deviceName, "DIRECT")
                val seq = data.optLong("seq", -1L)
                if (!shouldAcceptServerPcmSeq(seq)) return
                val sr = data.optInt("sr", 48000)
                val pcmArr = data.optJSONArray("pcm")
                if (pcmArr != null && pcmArr.length() > 0) {
                    val len = pcmArr.length()
                    val shorts = ShortArray(len)
                    for (i in 0 until len) {
                        shorts[i] = pcmArr.optInt(i, 0).coerceIn(-32768, 32767).toShort()
                    }
                    // Inject directly into Kotlin NativeVocalAudioEngine (bypass Car WebView completely!)
                    onMicPcmChunk?.invoke(sr, shorts, seq)
                }
                if (clients.values.any { it.role == "monitor" }) {
                    val payload = JSONObject().apply {
                        put("sr", sr)
                        put("seq", seq)
                        put("pcm", pcmArr)
                        put("pin", currentPin)
                    }
                    sendToRole("monitor", "audio-pcm-chunk", payload)
                }
            }

            "register-monitor" -> {
                client.role = "monitor"
                client.pin = currentPin
                val readyPayload = JSONObject().apply {
                    put("ok", true)
                    put("pin", currentPin)
                    put("state", roomStateJson)
                }
                sendEvent(client, "car-state-sync", roomStateJson)
                sendEvent(client, "monitor-registered", readyPayload)
            }

            "remote-command" -> {
                val cmdType = data.optString("type", "").ifBlank { data.optString("action", "") }
                val cmdAction = data.optString("action", "")
                var stateMutated = false
                if (cmdType == "SELECT_SONG" && data.has("songId")) {
                    roomStateJson.put("currentSongId", data.optString("songId"))
                    stateMutated = true
                } else if (cmdType == "START_SONG") {
                    roomStateJson.put("isPlaying", true)
                    stateMutated = true
                } else if (cmdType == "PAUSE_SONG" || cmdType == "STOP_SONG") {
                    roomStateJson.put("isPlaying", false)
                    stateMutated = true
                } else if (cmdType == "CHANGE_KEY" && data.has("keyShift")) {
                    roomStateJson.put("keyShift", data.optInt("keyShift"))
                    stateMutated = true
                } else if (cmdType == "CHANGE_ECHO" && (data.has("echoLevel") || data.has("reverbLevel"))) {
                    val v = data.optInt("echoLevel", data.optInt("reverbLevel", 80)).coerceIn(0, 100)
                    roomStateJson.put("echoLevel", v)
                    roomStateJson.put("reverbLevel", v)
                    stateMutated = true
                } else if ((cmdType == "CHANGE_BACKING_VOLUME" || cmdType == "YOUTUBE_VOLUME") && (data.has("backingVolume") || data.has("mrVolume") || data.has("volume"))) {
                    val v = data.optInt("backingVolume", data.optInt("mrVolume", data.optInt("volume", 85))).coerceIn(0, 100)
                    roomStateJson.put("backingVolume", v)
                    roomStateJson.put("mrVolume", v)
                    stateMutated = true
                } else if (cmdType == "CHANGE_MIC_VOLUME" && (data.has("micVolume") || data.has("volume"))) {
                    val v = data.optInt("micVolume", data.optInt("volume", 85)).coerceIn(0, 100)
                    roomStateJson.put("micVolume", v)
                    stateMutated = true
                } else if (cmdType == "CHANGE_GUIDE_VOLUME" && (data.has("guideVolume") || data.has("volume"))) {
                    val v = data.optInt("guideVolume", data.optInt("volume", 75)).coerceIn(0, 100)
                    roomStateJson.put("guideVolume", v)
                    stateMutated = true
                } else if (cmdType == "CHANGE_PLAYBACK_RATE" && (data.has("mrPlaybackRate") || data.has("playbackRate") || data.has("rate"))) {
                    val r = data.optDouble("mrPlaybackRate", data.optDouble("playbackRate", data.optDouble("rate", 1.0))).coerceIn(0.75, 1.25)
                    roomStateJson.put("mrPlaybackRate", r)
                    stateMutated = true
                } else if (cmdType == "CHANGE_SYNC_OFFSET" && (data.has("syncOffsetMs") || data.has("offsetMs"))) {
                    val off = data.optInt("syncOffsetMs", data.optInt("offsetMs", 0)).coerceIn(-500, 500)
                    roomStateJson.put("syncOffsetMs", off)
                    stateMutated = true
                } else if (cmdType == "RESET_AUDIO_MIXER") {
                    roomStateJson.put("backingVolume", 85)
                    roomStateJson.put("mrVolume", 85)
                    roomStateJson.put("micVolume", 85)
                    roomStateJson.put("guideVolume", 75)
                    roomStateJson.put("echoLevel", 80)
                    roomStateJson.put("reverbLevel", 80)
                    roomStateJson.put("mrPlaybackRate", 1.0)
                    roomStateJson.put("syncOffsetMs", 0)
                    roomStateJson.put("keyShift", 0)
                    stateMutated = true
                } else if (cmdType == "SET_GUIDE_VOCAL" || cmdType == "TOGGLE_GUIDE_VOCAL" || cmdType == "SET_DEMO_VOCAL") {
                    val nextGuide = when {
                        data.has("guideVocalEnabled") -> data.optBoolean("guideVocalEnabled", false)
                        data.has("demoVocalWave") -> data.optBoolean("demoVocalWave", false)
                        data.has("enabled") -> data.optBoolean("enabled", false)
                        else -> !roomStateJson.optBoolean("guideVocalEnabled", false)
                    }
                    roomStateJson.put("guideVocalEnabled", nextGuide)
                    roomStateJson.put("demoVocalWave", nextGuide)
                    if (!nextGuide) {
                        roomStateJson.put("guidePitchHz", 0.0)
                        roomStateJson.put("guideNote", "-")
                        roomStateJson.put("guideVoiceLevelPercent", 0)
                    }
                    stateMutated = true
                } else if (
                    cmdType.equals("toggle-score-mode", ignoreCase = true) ||
                    cmdAction.equals("toggle-score-mode", ignoreCase = true) ||
                    cmdType == "TOGGLE_SCORE_MODE" ||
                    cmdType == "SET_SCORE_MODE"
                ) {
                    val nextScoreEnabled = when {
                        data.has("enabled") -> data.optBoolean("enabled", true)
                        data.has("scoreEnabled") -> data.optBoolean("scoreEnabled", true)
                        else -> !roomStateJson.optBoolean("scoreEnabled", true)
                    }
                    roomStateJson.put("scoreEnabled", nextScoreEnabled)
                    stateMutated = true
                }
                if (stateMutated) {
                    roomStateJson.put("carHostId", carHostId)
                    roomStateJson.put("stateRevision", stateRevisionCounter.incrementAndGet())
                    sendToRole("mic", "car-state-sync", roomStateJson)
                    sendToRole("monitor", "car-state-sync", roomStateJson)
                }

                val forwarded = JSONObject(data.toString()).apply {
                    if (!has("type") && cmdType.isNotBlank()) {
                        put("type", cmdType)
                    }
                    put("from", client.id)
                    put("ts", System.currentTimeMillis())
                }
                if (cmdType.isNotBlank()) {
                    audioEngineController?.applyRemoteAudioCommandDirectly(cmdType, forwarded)
                    onDirectRemoteCommand?.invoke(cmdType, forwarded)
                }
                sendToRole("car", "remote-command", forwarded)
                sendToRole("monitor", "remote-command", forwarded)
            }

            "car-state-sync" -> {
                val keys = data.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    roomStateJson.put(k, data.opt(k))
                }
                roomStateJson.put("carHostId", carHostId)
                sendToRole("mic", "car-state-sync", roomStateJson)
                sendToRole("monitor", "car-state-sync", roomStateJson)
            }
        }
    }

    private suspend fun sendEvent(client: ConnectedClient, event: String, data: JSONObject) {
        try {
            val envelope = JSONObject().apply {
                put("event", event)
                put("data", data)
            }.toString()
            client.session.send(envelope)
        } catch (_: Exception) {
        }
    }

    private suspend fun sendToRole(role: String, event: String, data: JSONObject) {
        val envelope = JSONObject().apply {
            put("event", event)
            put("data", data)
        }.toString()
        for (target in clients.values) {
            if (target.role == role) {
                try {
                    target.session.send(envelope)
                } catch (_: Exception) {
                }
            }
        }
        if (role == "mic") {
            for (sender in outboundMicSenders.values) {
                try {
                    sender(envelope)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun writeMaskedWebSocketTextFrame(out: OutputStream, text: String) {
        val payload = text.toByteArray(Charsets.UTF_8)
        val maskKey = ByteArray(4)
        SecureRandom().nextBytes(maskKey)
        val header = ByteArrayOutputStream(14)
        header.write(0x81) // FIN=1, Opcode=1 (Text)
        when {
            payload.size <= 125 -> header.write(0x80 or payload.size)
            payload.size <= 65535 -> {
                header.write(0x80 or 126)
                header.write((payload.size shr 8) and 0xFF)
                header.write(payload.size and 0xFF)
            }
            else -> {
                header.write(0x80 or 127)
                val lenLong = payload.size.toLong()
                for (shift in 56 downTo 0 step 8) {
                    header.write(((lenLong ushr shift) and 0xFFL).toInt())
                }
            }
        }
        header.write(maskKey)
        val maskedPayload = ByteArray(payload.size)
        for (i in payload.indices) {
            maskedPayload[i] = (payload[i].toInt() xor maskKey[i and 3].toInt()).toByte()
        }
        out.write(header.toByteArray())
        out.write(maskedPayload)
        out.flush()
    }

    private fun readClientWebSocketTextFrame(ins: InputStream, out: OutputStream, writeLock: Any): String? {
        while (true) {
            val b0 = ins.read()
            if (b0 < 0) return null
            val b1 = ins.read()
            if (b1 < 0) return null
            val opcode = b0 and 0x0F
            val masked = (b1 and 0x80) != 0
            var payloadLen = (b1 and 0x7F).toLong()
            if (payloadLen == 126L) {
                val hi = ins.read(); val lo = ins.read()
                if (hi < 0 || lo < 0) return null
                payloadLen = ((hi and 0xFF) shl 8 or (lo and 0xFF)).toLong()
            } else if (payloadLen == 127L) {
                payloadLen = 0L
                for (i in 0 until 8) {
                    val b = ins.read()
                    if (b < 0) return null
                    payloadLen = (payloadLen shl 8) or (b.toLong() and 0xFFL)
                }
            }
            if (payloadLen < 0L || payloadLen > 2_000_000L) return null
            val maskKey = ByteArray(4)
            if (masked) {
                var r = 0
                while (r < 4) {
                    val n = ins.read(maskKey, r, 4 - r)
                    if (n < 0) return null
                    r += n
                }
            }
            val payload = ByteArray(payloadLen.toInt())
            var readBytes = 0
            while (readBytes < payload.size) {
                val n = ins.read(payload, readBytes, payload.size - readBytes)
                if (n < 0) return null
                readBytes += n
            }
            if (masked) {
                for (i in payload.indices) {
                    payload[i] = (payload[i].toInt() xor maskKey[i and 3].toInt()).toByte()
                }
            }
            when (opcode) {
                0x8 -> return null // Close
                0x9 -> { // Ping -> Masked Pong
                    synchronized(writeLock) {
                        val pongMask = ByteArray(4).also { SecureRandom().nextBytes(it) }
                        out.write(0x8A)
                        out.write(0x80 or (payload.size.coerceAtMost(125)))
                        out.write(pongMask)
                        for (i in 0 until payload.size.coerceAtMost(125)) {
                            out.write(payload[i].toInt() xor pongMask[i and 3].toInt())
                        }
                        out.flush()
                    }
                }
                0x1, 0x2 -> return String(payload, Charsets.UTF_8)
            }
        }
    }

    /**
     * [Architecture Hybrid Step 2] Car Emulator Outbound WebSocket (`ws://<hotspot-ip>:8082/ws`) to Smartphone Mic.
     * Supports BOTH:
     * - QEMU SLIRP Hotspot DNS/Gateway `ws://10.0.2.3:8082/ws` & `ws://10.0.2.2:8082/ws` (zero-signaling <2ms connect!)
     * - Parallel candidate IPs (`ws://192.168.x.x:8082/ws` / `ws://172.20.10.x:8082/ws`, `connectTimeout = 800ms`)
     * Sends `car-hello` handshake immediately on WebSocket 101 upgrade and waits for Smartphone's verified frame before marking `isConnected = true`.
     */
    fun connectOutboundToSmartphoneMic(
        phoneIp: String,
        phonePort: Int = 8082,
        pin: String = currentPin,
        deviceName: String = "Pleos Smart Mic"
    ) {
        val cleanIp = phoneIp.trim()
        if (!isAllowedCarOutboundIp(cleanIp)) return
        val key = "$cleanIp:$phonePort"
        if (outboundMicJobs[key]?.isActive == true) return

        val job = scope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.soTimeout = 8000
                socket.receiveBufferSize = 128 * 1024
                socket.sendBufferSize = 128 * 1024
                socket.connect(InetSocketAddress(cleanIp, phonePort), 800)
                val out: OutputStream = socket.getOutputStream()
                val bufIn = BufferedInputStream(socket.getInputStream())
                val writeLock = Any()

                // Perform RFC 6455 WebSocket client upgrade (`ws://<cleanIp>:<phonePort>/ws`)
                val nonceBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val secKey = Base64.getEncoder().encodeToString(nonceBytes)
                val upgradeReq = "GET /ws HTTP/1.1\r\n" +
                    "Host: $cleanIp:$phonePort\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $secKey\r\n" +
                    "Sec-WebSocket-Version: 13\r\n\r\n"
                synchronized(writeLock) {
                    out.write(upgradeReq.toByteArray(Charsets.US_ASCII))
                    out.flush()
                }

                // Read HTTP 101 response headers
                val hdrBuf = StringBuilder()
                var crlfState = 0
                while (hdrBuf.length < 4096) {
                    val c = bufIn.read()
                    if (c < 0) throw EOFException("WS upgrade EOF")
                    hdrBuf.append(c.toChar())
                    crlfState = when {
                        c == '\r'.code && (crlfState == 0 || crlfState == 2) -> crlfState + 1
                        c == '\n'.code && (crlfState == 1 || crlfState == 3) -> crlfState + 1
                        else -> 0
                    }
                    if (crlfState == 4) break
                }
                if (!hdrBuf.toString().startsWith("HTTP/1.1 101")) {
                    return@launch
                }

                val sendWsFrameSync: (String) -> Unit = { jsonText ->
                    try {
                        synchronized(writeLock) {
                            writeMaskedWebSocketTextFrame(out, jsonText)
                        }
                    } catch (_: Exception) {
                    }
                }

                val sendWsFrame: (String) -> Unit = { jsonText ->
                    scope.launch(Dispatchers.IO) {
                        sendWsFrameSync(jsonText)
                    }
                }
                outboundMicSenders[key] = sendWsFrame

                // Send `car-hello` handshake + `paired-with-car` + `car-pong` (do NOT set micConnected=true until Smartphone sends a verified frame!)
                val helloPayloadData = JSONObject().apply {
                    put("ok", true)
                    put("pin", pin)
                    put("roomId", "pleos-room-$pin")
                    put("carConnected", true)
                    put("state", roomStateJson)
                }
                sendWsFrameSync(
                    JSONObject().apply {
                        put("event", "car-hello")
                        put("data", helloPayloadData)
                    }.toString()
                )
                sendWsFrameSync(
                    JSONObject().apply {
                        put("event", "paired-with-car")
                        put("data", helloPayloadData)
                    }.toString()
                )
                sendWsFrameSync(
                    JSONObject().apply {
                        put("event", "car-pong")
                        put("data", JSONObject().apply {
                            put("pin", pin)
                            put("ts", System.currentTimeMillis())
                        })
                    }.toString()
                )

                Log.i(TAG, "Outbound WebSocket ws://$key/ws established from Car to Smartphone Mic (PIN $pin)")

                while (isActive && !socket.isClosed) {
                    val line = readClientWebSocketTextFrame(bufIn, out, writeLock) ?: break
                    if (line.isBlank()) continue
                    handleOutboundMicJsonLine(key, cleanIp, deviceName, line, sendWsFrameSync)
                }
            } catch (_: Exception) {
            } finally {
                outboundMicSenders.remove(key)
                verifiedOutboundMicKeys.remove(key)
                outboundMicJobs.remove(key)
                val remainingMics = clients.values.count { it.role == "mic" } + verifiedOutboundMicKeys.size
                _serverState.update { it.copy(connectedMicCount = remainingMics) }
                if (remainingMics == 0 && (System.currentTimeMillis() - lastLocalMicPacketMs > 6000L)) {
                    onDirectMicConnectionChanged?.invoke(false, "○ 연결 대기 중", 0)
                }
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
        outboundMicJobs[key] = job
    }

    private fun launchParallelHotspotOutboundProbes() {
        val targets = LinkedHashSet<String>()
        // Priority 0: Upstream DNS / Hotspot Gateway IPs (`dnsServers`, `getprop net.dns1..4`), Cached IPs & Candidate IPs
        val gatewayAndDnsIps = detectHotspotGatewayIps(appContext)
        targets.addAll(gatewayAndDnsIps)
        targets.addAll(knownPhoneCandidateIps)
        targets.add("10.0.2.3")
        targets.add("10.0.2.2")
        for (ip in targets) {
            connectOutboundToSmartphoneMic(ip, 8082, currentPin, "Pleos Smart Mic")
        }
    }

    private fun startPeriodicHotspotDirectProbeAndWatchdog() {
        hotspotProbeJob?.cancel()
        hotspotProbeJob = scope.launch(Dispatchers.IO) {
            // Immediately on app start (0ms delay), launch parallel Outbound probes to DNS Hotspot Gateway & cached IPs!
            try {
                launchParallelHotspotOutboundProbes()
            } catch (_: Exception) {
            }

            var tick = 0
            while (isActive) {
                delay(500L)
                tick++
                try {
                    val now = System.currentTimeMillis()
                    // 1) Every 1.0s: send real-time `car-pong` / `car-ping` to all Inbound & Outbound Mics
                    if (tick % 2 == 0) {
                        val pongData = JSONObject().apply {
                            put("pin", currentPin)
                            put("ts", now)
                        }
                        sendToRole("mic", "car-pong", pongData)
                        sendToRole("mic", "car-ping", pongData)
                    }

                    // 2) Every 1.5s (`tick % 3 == 0`): refresh LAN IP & attempt parallel Outbound connections when not yet directly linked
                    if (tick % 3 == 0) {
                        val latestLanIp = detectLocalLanIp(appContext)
                        if (latestLanIp.isNotBlank() && latestLanIp != _serverState.value.lanIp) {
                            _serverState.update {
                                it.copy(
                                    lanIp = latestLanIp,
                                    lanWsUrl = "ws://$latestLanIp:$DEFAULT_PORT/ws"
                                )
                            }
                        }
                        if (verifiedOutboundMicKeys.isEmpty()) {
                            launchParallelHotspotOutboundProbes()
                        }
                    }

                    // 2b) Every 5.0s (`tick % 10 == 0`): if still unpaired, fast-scan `/24` hosts on any detected DNS/Hotspot subnet
                    if (tick % 10 == 4 && verifiedOutboundMicKeys.isEmpty() && _serverState.value.connectedMicCount == 0) {
                        probeOfflineHotspotSubnet("", 8082, "Pleos Smart Mic")
                    }

                    // 3) Stabilized 6.0-second Heartbeat Watchdog across Inbound / Outbound / Native Relay
                    if (lastLocalMicPacketMs > 0L && (now - lastLocalMicPacketMs > 6000L)) {
                        if (_serverState.value.connectedMicCount > 0) {
                            _serverState.update { it.copy(connectedMicCount = 0) }
                        }
                        onDirectMicConnectionChanged?.invoke(false, "○ 연결 대기 중", 0)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun handleOutboundMicJsonLine(
        key: String,
        cleanIp: String,
        deviceName: String,
        rawText: String,
        sendWsFrameSync: (String) -> Unit
    ) {
        fun recordVerifiedDirectIp() {
            verifiedOutboundMicKeys.add(key)
            if (!cleanIp.startsWith("10.0.2.")) {
                knownPhoneCandidateIps.add(cleanIp)
                saveKnownHotspotIp(cleanIp)
            }
        }

        if (tryFastDispatchAudioPcmChunk(rawText) {
                recordVerifiedDirectIp()
                markVerifiedMicActivity(deviceName, "DIRECT")
            }
        ) {
            return
        }

        val root = try {
            JSONObject(rawText)
        } catch (_: Exception) {
            return
        }
        val event = root.optString("event", root.optString("type", ""))
        val data = root.optJSONObject("data") ?: root

        when (event) {
            "join-pin", "mic-ping", "mic-pong" -> {
                recordVerifiedDirectIp()
                markVerifiedMicActivity(deviceName, "DIRECT")
                if (event == "join-pin") {
                    val okResp = JSONObject().apply {
                        put("event", "paired-with-car")
                        put("data", JSONObject().apply {
                            put("ok", true)
                            put("pin", currentPin)
                            put("roomId", "pleos-room-$currentPin")
                            put("carConnected", true)
                            put("state", roomStateJson)
                        })
                    }.toString()
                    sendWsFrameSync(okResp)
                } else if (event == "mic-ping") {
                    val pongResp = JSONObject().apply {
                        put("event", "car-pong")
                        put("data", JSONObject().apply {
                            put("pin", currentPin)
                            put("ts", System.currentTimeMillis())
                        })
                    }.toString()
                    sendWsFrameSync(pongResp)
                }
            }
            "latency-ping" -> {
                recordVerifiedDirectIp()
                markVerifiedMicActivity(deviceName, "DIRECT")
                val seq = data.optLong("seq", 0L)
                val clientSendTs = data.optLong("clientSendTs", System.currentTimeMillis())
                val carBufferMs = audioEngineController?.getBufferedMicLatencyMs() ?: 12
                val reportedNetMs = data.optInt("lastNetLatencyMs", 3).coerceIn(1, 200)
                val totalMs = reportedNetMs + 11 + carBufferMs
                // Bounce `latency-pong` (`transport = "DIRECT"`) IMMEDIATELY & synchronously on `:8082`!
                val pongJson = JSONObject().apply {
                    put("event", "latency-pong")
                    put("type", "latency-pong")
                    put("data", JSONObject().apply {
                        put("type", "latency-pong")
                        put("seq", seq)
                        put("clientSendTs", clientSendTs)
                        put("carBufferMs", carBufferMs)
                        put("transport", "DIRECT")
                    })
                }.toString()
                sendWsFrameSync(pongJson)
                onMicLatencyUpdated?.invoke(totalMs, reportedNetMs, carBufferMs, "DIRECT")
            }
            "vocal-telemetry" -> {
                recordVerifiedDirectIp()
                val tr = data.optString("transport", "DIRECT").ifBlank { "DIRECT" }
                markVerifiedMicActivity(deviceName, tr)
                if (data.has("totalLatencyMs")) {
                    val totalMs = data.optInt("totalLatencyMs", 26).coerceIn(10, 350)
                    val netMs = data.optInt("netLatencyMs", 3).coerceIn(1, 250)
                    val bufMs = data.optInt("carBufferMs", 12).coerceIn(6, 120)
                    onMicLatencyUpdated?.invoke(totalMs, netMs, bufMs, tr)
                }
                val rmsVal = data.optDouble("rms", 0.0).toFloat()
                val pitchVal = data.optDouble("pitch", 0.0).toFloat()
                val note = data.optString("note", "-")
                onMicVocalTelemetry?.invoke(rmsVal, pitchVal, note)
                val payload = JSONObject().apply {
                    put("rms", rmsVal.toDouble())
                    put("pitch", pitchVal.toDouble())
                    put("note", note)
                    put("from", key)
                    put("ts", data.optLong("ts", System.currentTimeMillis()))
                }
                sendToRole("car", "vocal-telemetry", payload)
                sendToRole("monitor", "vocal-telemetry", payload)
            }
            "audio-pcm-chunk" -> {
                recordVerifiedDirectIp()
                markVerifiedMicActivity(deviceName, "DIRECT")
                val seq = data.optLong("seq", -1L)
                if (!shouldAcceptServerPcmSeq(seq)) return
                val sr = data.optInt("sr", 48000)
                val pcmArr = data.optJSONArray("pcm")
                if (pcmArr != null && pcmArr.length() > 0) {
                    val len = pcmArr.length()
                    val shorts = ShortArray(len)
                    for (i in 0 until len) {
                        shorts[i] = pcmArr.optInt(i, 0).coerceIn(-32768, 32767).toShort()
                    }
                    // Inject directly into Kotlin NativeVocalAudioEngine (bypass Car WebView completely!)
                    onMicPcmChunk?.invoke(sr, shorts, seq)
                }
                if (clients.values.any { it.role == "monitor" }) {
                    val payload = JSONObject().apply {
                        put("sr", sr)
                        put("seq", seq)
                        put("pcm", pcmArr)
                        put("pin", currentPin)
                    }
                    sendToRole("monitor", "audio-pcm-chunk", payload)
                }
            }
            "remote-command" -> {
                recordVerifiedDirectIp()
                markVerifiedMicActivity(deviceName, "DIRECT")
                val cmdType = data.optString("type", "").ifBlank { data.optString("action", "") }
                val cmdAction = data.optString("action", "")
                if (
                    cmdType.equals("toggle-score-mode", ignoreCase = true) ||
                    cmdAction.equals("toggle-score-mode", ignoreCase = true) ||
                    cmdType == "TOGGLE_SCORE_MODE" ||
                    cmdType == "SET_SCORE_MODE"
                ) {
                    val nextScoreEnabled = when {
                        data.has("enabled") -> data.optBoolean("enabled", true)
                        data.has("scoreEnabled") -> data.optBoolean("scoreEnabled", true)
                        else -> !roomStateJson.optBoolean("scoreEnabled", true)
                    }
                    roomStateJson.put("scoreEnabled", nextScoreEnabled)
                    roomStateJson.put("carHostId", carHostId)
                    roomStateJson.put("stateRevision", stateRevisionCounter.incrementAndGet())
                    scope.launch {
                        sendToRole("mic", "car-state-sync", roomStateJson)
                        sendToRole("monitor", "car-state-sync", roomStateJson)
                    }
                }
                val forwarded = JSONObject(data.toString()).apply {
                    if (!has("type") && cmdType.isNotBlank()) {
                        put("type", cmdType)
                    }
                    put("from", key)
                    put("ts", System.currentTimeMillis())
                }
                if (cmdType.isNotBlank()) {
                    audioEngineController?.applyRemoteAudioCommandDirectly(cmdType, forwarded)
                    onDirectRemoteCommand?.invoke(cmdType, forwarded)
                }
                sendToRole("car", "remote-command", forwarded)
                sendToRole("monitor", "remote-command", forwarded)
            }
        }
    }

    /**
     * Lightweight UDP Responder & Beacon on port 8081 so a Smartphone on the same Wi-Fi
     * can auto-discover `ws://<car-ip>:8080/ws` without manual IP entry.
     */
    private fun startUdpDiscoveryResponder() {
        udpDiscoveryJob?.cancel()
        udpDiscoveryJob = scope.launch(Dispatchers.IO) {
            try {
                activeGlobalUdpSocket?.close()
            } catch (_: Exception) {
            }
            activeGlobalUdpSocket = null

            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(DISCOVERY_UDP_PORT))
                    soTimeout = 2500
                }
                activeGlobalUdpSocket = socket
                val buf = ByteArray(512)
                while (isActive) {
                    val ip = detectLocalLanIp(appContext)
                    val wsUrl = "ws://$ip:$DEFAULT_PORT/ws"
                    val beaconJson = JSONObject().apply {
                        put("type", "PLEOS_CAR_BEACON")
                        put("wsUrl", wsUrl)
                        put("lanIp", ip)
                        put("port", DEFAULT_PORT)
                        put("pin", currentPin)
                    }.toString()

                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        if (msg.startsWith("PLEOS_DISCOVER")) {
                            val replyBytes = beaconJson.toByteArray(Charsets.UTF_8)
                            val replyPacket = DatagramPacket(
                                replyBytes,
                                replyBytes.size,
                                packet.address,
                                packet.port
                            )
                            socket.send(replyPacket)
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Broadcast periodic UDP beacon on local Wi-Fi subnet
                        try {
                            val beaconBytes = beaconJson.toByteArray(Charsets.UTF_8)
                            val bcastPacket = DatagramPacket(
                                beaconBytes,
                                beaconBytes.size,
                                InetAddress.getByName("255.255.255.255"),
                                DISCOVERY_UDP_PORT
                            )
                            socket.send(bcastPacket)
                        } catch (_: Exception) {
                        }
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                if (activeGlobalUdpSocket === socket) {
                    activeGlobalUdpSocket = null
                }
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun writeMqttVarLen(out: ByteArrayOutputStream, length: Int) {
        var x = length
        do {
            var encodedByte = x and 0x7F
            x = x ushr 7
            if (x > 0) encodedByte = encodedByte or 0x80
            out.write(encodedByte)
        } while (x > 0)
    }

    private fun writeMqttUtf8(out: ByteArrayOutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        out.write((bytes.size ushr 8) and 0xFF)
        out.write(bytes.size and 0xFF)
        out.write(bytes)
    }

    private fun readMqttRemainingLength(ins: InputStream): Int {
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

    /**
     * Pure Kotlin TCP MQTT v3.1.1 Subscriber (`broker.emqx.io:1883` & `broker.hivemq.com:1883`):
     * Works 100% independently of Android WebView `Origin: file://` WSS policies!
     * Immediately receives Smartphone `candidateIps` (`join-pin`, `CANDIDATE_IPS`, `HELLO`, `mic-ping`, `phone-outbound-offer`)
     * on `"pleos-stage/karaoke/pin-<PIN>"` and dials `ws://<hotspot-ip>:8082/ws` outbound across QEMU Emulator NAT!
     */
    private fun startNativeMqttCandidateListener() {
        nativeMqttListenerJob?.cancel()
        nativeMqttListenerJob = scope.launch(Dispatchers.IO) {
            for ((brokerHost, brokerPort) in MQTT_TCP_BROKERS) {
                launch(Dispatchers.IO) {
                    while (isActive) {
                        var sock: Socket? = null
                        try {
                            val activePinSnapshot = currentPin.ifBlank { "4829" }
                            val topic = "pleos-stage/karaoke/pin-$activePinSnapshot"
                            val clientId = "pleos_car_kt_${UUID.randomUUID().toString().replace("-", "").take(10)}"

                            val resolvedBrokerAddr = resolveHostIpv4BypassingBrokenEmulatorDns(brokerHost)
                            sock = Socket().apply {
                                tcpNoDelay = true
                                keepAlive = true
                                soTimeout = 18_000
                            }
                            sock.connect(InetSocketAddress(resolvedBrokerAddr, brokerPort), 2500)
                            val out = sock.getOutputStream()
                            val ins = BufferedInputStream(sock.getInputStream())
                            val writeLock = Any()

                            // 1) MQTT CONNECT (v3.1.1, CleanSession=1, KeepAlive=30s)
                            val connectBody = ByteArrayOutputStream()
                            writeMqttUtf8(connectBody, "MQTT")
                            connectBody.write(0x04) // Protocol Level 4 (3.1.1)
                            connectBody.write(0x02) // Clean Session
                            connectBody.write(0x00)
                            connectBody.write(0x1E) // KeepAlive = 30s
                            writeMqttUtf8(connectBody, clientId)
                            val connectBodyBytes = connectBody.toByteArray()
                            val connectPkt = ByteArrayOutputStream(connectBodyBytes.size + 5)
                            connectPkt.write(0x10)
                            writeMqttVarLen(connectPkt, connectBodyBytes.size)
                            connectPkt.write(connectBodyBytes)
                            synchronized(writeLock) {
                                out.write(connectPkt.toByteArray())
                                out.flush()
                            }

                            // 2) Read CONNACK
                            val connAckHdr = ins.read()
                            val connAckLen = readMqttRemainingLength(ins)
                            if (connAckHdr < 0 || (connAckHdr and 0xF0) != 0x20 || connAckLen < 2) {
                                throw EOFException("Invalid MQTT CONNACK")
                            }
                            val connAckBuf = ByteArray(connAckLen)
                            var rAck = 0
                            while (rAck < connAckLen) {
                                val n = ins.read(connAckBuf, rAck, connAckLen - rAck)
                                if (n < 0) throw EOFException("CONNACK EOF")
                                rAck += n
                            }
                            if (connAckBuf[1].toInt() != 0) {
                                throw EOFException("MQTT CONNACK rejected: ${connAckBuf[1].toInt()}")
                            }

                            // 3) MQTT SUBSCRIBE (`topic`, QoS 0)
                            val subBody = ByteArrayOutputStream()
                            subBody.write(0x00)
                            subBody.write(0x01) // Packet ID = 1
                            writeMqttUtf8(subBody, topic)
                            subBody.write(0x00) // Requested QoS = 0
                            val subBodyBytes = subBody.toByteArray()
                            val subPkt = ByteArrayOutputStream(subBodyBytes.size + 5)
                            subPkt.write(0x82)
                            writeMqttVarLen(subPkt, subBodyBytes.size)
                            subPkt.write(subBodyBytes)
                            synchronized(writeLock) {
                                out.write(subPkt.toByteArray())
                                out.flush()
                            }

                            // 4) Publish `car-ready` once so any waiting Smartphone immediately broadcasts `candidateIps`
                            val readyJson = JSONObject().apply {
                                put("from", carHostId)
                                put("role", "car")
                                put("event", "car-ready")
                                put("type", "car-ready")
                                put("data", JSONObject().apply {
                                    put("pin", activePinSnapshot)
                                    put("ts", System.currentTimeMillis())
                                })
                            }.toString()
                            val pubBody = ByteArrayOutputStream()
                            writeMqttUtf8(pubBody, topic)
                            pubBody.write(readyJson.toByteArray(Charsets.UTF_8))
                            val pubBodyBytes = pubBody.toByteArray()
                            val pubPkt = ByteArrayOutputStream(pubBodyBytes.size + 5)
                            pubPkt.write(0x30) // PUBLISH QoS 0
                            writeMqttVarLen(pubPkt, pubBodyBytes.size)
                            pubPkt.write(pubBodyBytes)
                            synchronized(writeLock) {
                                out.write(pubPkt.toByteArray())
                                out.flush()
                            }

                            val pingJob = launch(Dispatchers.IO) {
                                while (isActive && !sock.isClosed) {
                                    delay(12_000L)
                                    try {
                                        synchronized(writeLock) {
                                            out.write(byteArrayOf(0xC0.toByte(), 0x00))
                                            out.flush()
                                        }
                                    } catch (_: Exception) {
                                        break
                                    }
                                }
                            }

                            try {
                                while (isActive && !sock.isClosed && currentPin.ifBlank { "4829" } == activePinSnapshot) {
                                    val hdr = try {
                                        ins.read()
                                    } catch (_: java.net.SocketTimeoutException) {
                                        continue
                                    }
                                    if (hdr < 0) break
                                    val remLen = readMqttRemainingLength(ins)
                                    if (remLen < 0 || remLen > 262_144) break
                                    val payloadBuf = ByteArray(remLen)
                                    var got = 0
                                    while (got < remLen) {
                                        val n = ins.read(payloadBuf, got, remLen - got)
                                        if (n < 0) break
                                        got += n
                                    }
                                    if (got < remLen) break

                                    if ((hdr and 0xF0) == 0x30 && remLen >= 4) {
                                        val qos = (hdr ushr 1) and 0x03
                                        val topicLen = ((payloadBuf[0].toInt() and 0xFF) shl 8) or (payloadBuf[1].toInt() and 0xFF)
                                        var offset = 2 + topicLen + (if (qos > 0) 2 else 0)
                                        if (offset < remLen) {
                                            val jsonStr = String(payloadBuf, offset, remLen - offset, Charsets.UTF_8)
                                            try {
                                                val msg = JSONObject(jsonStr)
                                                val role = msg.optString("role", "")
                                                if (role == "car") continue
                                                val evt = msg.optString("event", msg.optString("type", ""))
                                                if (evt == "join-pin" ||
                                                    evt == "CANDIDATE_IPS" ||
                                                    evt == "HELLO" ||
                                                    evt == "mic-ping" ||
                                                    evt == "phone-outbound-offer"
                                                ) {
                                                    val d = msg.optJSONObject("data") ?: msg
                                                    val pIp = d.optString("phoneIp", "").trim()
                                                    val pPort = d.optInt("phonePort", 8082)
                                                    val devName = d.optString("deviceName", "Pleos Smart Mic").ifBlank { "Pleos Smart Mic" }
                                                    val arr = d.optJSONArray("candidateIps")
                                                    val cands = mutableListOf<String>()
                                                    if (pIp.isNotBlank()) cands.add(pIp)
                                                    if (arr != null) {
                                                        for (i in 0 until arr.length()) {
                                                            val item = arr.optString(i, "").trim()
                                                            if (item.isNotBlank()) cands.add(item)
                                                        }
                                                    }
                                                    if (cands.isNotEmpty()) {
                                                        registerCandidatePhoneIps(cands, pPort, devName)
                                                    }
                                                }
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                                }
                            } finally {
                                pingJob.cancel()
                            }
                        } catch (_: Exception) {
                        } finally {
                            try {
                                sock?.close()
                            } catch (_: Exception) {
                            }
                        }
                        delay(2000L)
                    }
                }
            }
        }
    }

    fun stop() {
        udpDiscoveryJob?.cancel()
        udpDiscoveryJob = null
        hotspotProbeJob?.cancel()
        hotspotProbeJob = null
        nativeMqttListenerJob?.cancel()
        nativeMqttListenerJob = null
        try {
            activeGlobalUdpSocket?.close()
        } catch (_: Exception) {
        }
        activeGlobalUdpSocket = null
        val curEngine = engine
        engine = null
        if (activeGlobalEngine === curEngine) {
            activeGlobalEngine = null
        }
        try {
            curEngine?.stop(300, 500)
        } catch (_: Exception) {
        }
        scope.cancel()
    }
}
