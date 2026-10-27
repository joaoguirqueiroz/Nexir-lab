package br.com.vertice81.nexir

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

data class LanDevice(
    val id: String,
    val name: String,
    val kind: String,
    val model: String,
    val address: String,
    val protocol: String,
    val firmware: String = "",
    val controllable: Boolean = false
)

object RokuControl {
    private val keys = mapOf(
        "POWER" to "PowerOff", "MUTE" to "VolumeMute",
        "HOME" to "Home", "MENU" to "Info", "BACK" to "Back",
        "VOL_UP" to "VolumeUp", "VOL_DOWN" to "VolumeDown",
        "CH_UP" to "ChannelUp", "CH_DOWN" to "ChannelDown",
        "UP" to "Up", "DOWN" to "Down", "LEFT" to "Left", "RIGHT" to "Right",
        "OK" to "Select", "PLAY_PAUSE" to "Play",
        "PREVIOUS" to "Rev", "NEXT" to "Fwd",
        "HDMI_1" to "InputHDMI1", "HDMI_2" to "InputHDMI2",
        "HDMI_3" to "InputHDMI3", "HDMI_4" to "InputHDMI4"
    )
    fun supports(key: String): Boolean = keys.containsKey(key)
    fun isPrivateIpv4(ip: String): Boolean {
        val p = ip.split('.').mapNotNull { it.toIntOrNull() }
        return p.size == 4 && p.all { it in 0..255 } && (
            p[0] == 10 ||
            (p[0] == 172 && p[1] in 16..31) ||
            (p[0] == 192 && p[1] == 168)
        )
    }
    private fun request(ip: String, method: String, path: String): Pair<Int, String> {
        require(isPrivateIpv4(ip)) { "Informe um IP privado válido da sua rede Wi-Fi" }
        val url = URL("http://$ip:8060/$path")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 2200
            readTimeout = 2200
            requestMethod = method
            useCaches = false
            if (method == "POST") doOutput = true
        }
        return try {
            if (method == "POST") connection.outputStream.use { }
            val code = connection.responseCode
            val body = if (method == "GET" && code == 200) {
                connection.inputStream.bufferedReader().use { it.readText().take(32000) }
            } else ""
            code to body
        } finally { connection.disconnect() }
    }
    fun inspect(ip: String): LanDevice {
        val (status, xml) = request(ip, "GET", "query/device-info")
        check(status == 200 && xml.contains("<device-info")) {
            "Este IP não respondeu como um dispositivo Roku (HTTP $status)"
        }
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        var current = ""
        val fields = mutableMapOf<String, String>()
        var rootSeen = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    current = parser.name
                    if (current == "device-info") rootSeen = true
                }
                XmlPullParser.TEXT -> if (current.isNotBlank()) {
                    val v = parser.text.trim()
                    if (v.isNotEmpty()) fields[current] = v.take(120)
                }
                XmlPullParser.END_TAG -> current = ""
            }
            parser.next()
        }
        check(rootSeen && (fields["model-number"] != null || fields["model-name"] != null)) {
            "Resposta de identificação Roku incompleta"
        }
        return LanDevice(
            id = "roku:$ip", name = fields["user-device-name"]?.takeIf { it.isNotBlank() }
                ?: fields["friendly-device-name"] ?: "Dispositivo Roku",
            kind = "TV / Player Roku",
            model = fields["model-name"] ?: fields["model-number"] ?: "Modelo não informado",
            address = ip, protocol = "ROKU_ECP",
            firmware = fields["software-version"] ?: "Não informado",
            controllable = true
        )
    }
    fun send(ip: String, key: String): String {
        val ecpKey = keys[key] ?: error("Comando $key não existe no protocolo Roku ECP")
        val (code, _) = request(ip, "POST", "keypress/$ecpKey")
        check(code in 200..299) {
            if (code == 403) "O Roku recusou o comando. Ative 'Controle por aplicativos móveis' nas configurações."
            else "Controle Roku indisponível (HTTP $code). Verifique compatibilidade e autorização."
        }
        return "Roku ECP: $ecpKey aceito via LAN"
    }
}

/** Descoberta voluntária na rede Wi-Fi local; nunca envia comandos de controle durante a busca. */
@Suppress("DEPRECATION")
class LanDiscovery(
    private val context: Context,
    private val found: (LanDevice) -> Unit,
    private val status: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private var lock: WifiManager.MulticastLock? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = manager.getNetworkCapabilities(manager.activeNetwork)
        if (cap == null || (!cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                     !cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
            status("Conecte o celular à mesma rede Wi-Fi dos aparelhos.")
            return
        }
        running = true
        status("Procurando TVs e displays na rede local…")
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            lock = wifi.createMulticastLock("nexir-lan").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) { status("Descoberta multicast limitada por este aparelho.") }

        val services = mapOf(
            "_googlecast._tcp." to "Google Cast / Smart TV",
            "_airplay._tcp." to "AirPlay / Display",
            "_http._tcp." to "Painel / Serviço HTTP"
        )
        services.forEach { (serviceType, kind) ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(t: String) {}
                override fun onDiscoveryStopped(t: String) {}
                override fun onStartDiscoveryFailed(t: String, code: Int) { try { nsd.stopServiceDiscovery(this) } catch (_: Exception) {} }
                override fun onStopDiscoveryFailed(t: String, code: Int) {}
                override fun onServiceLost(info: NsdServiceInfo) {}
                override fun onServiceFound(info: NsdServiceInfo) {
                    if (!running) return
                    // O nome anunciado pela rede não comprova o modelo; o controle não é habilitado.
                    val name = info.serviceName.take(80)
                    main.post {
                        if (running) found(LanDevice(
                            id = "nsd:$serviceType:$name", name = name,
                            kind = kind, model = "Modelo não confirmado",
                            address = "Via mDNS", protocol = "MDNS", controllable = false
                        ))
                    }
                    // Resolução mDNS é informativa: não ativa controle sem pareamento.
                    try {
                        nsd.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val attributes = serviceInfo.attributes
                                val model = attributes["md"]?.toString(Charsets.UTF_8)?.take(80)
                                    ?: "Modelo não anunciado"
                                val friendly = attributes["fn"]?.toString(Charsets.UTF_8)?.take(80)
                                    ?: name
                                val ip = serviceInfo.host?.hostAddress ?: "IP não divulgado"
                                main.post {
                                    if (running) found(LanDevice(
                                        id = "nsd:$serviceType:$name", name = friendly,
                                        kind = kind, model = model, address = ip,
                                        protocol = "MDNS", controllable = false
                                    ))
                                }
                            }
                        })
                    } catch (_: Exception) {}
                }
            }
            try {
                nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                listeners.add(listener)
            } catch (_: Exception) {}
        }
        thread(name = "nexir-roku-discovery") { discoverRoku() }
        main.postDelayed({
            if (running) { stop(); status("Busca concluída. Selecione um dispositivo compatível.") }
        }, 7500L)
    }

    private fun discoverRoku() {
        try {
            MulticastSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(0))
                socket.soTimeout = 900
                val message = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: roku:ecp\r\n\r\n"
                    .toByteArray(Charsets.US_ASCII)
                socket.send(DatagramPacket(message, message.size, InetAddress.getByName("239.255.255.250"), 1900))
                val end = System.currentTimeMillis() + 5800L
                val seen = mutableSetOf<String>()
                while (running && System.currentTimeMillis() < end) {
                    val buffer = ByteArray(4096)
                    val packet = DatagramPacket(buffer, buffer.size)
                    try { socket.receive(packet) } catch (_: java.net.SocketTimeoutException) { continue }
                    val ip = packet.address.hostAddress ?: continue
                    if (!RokuControl.isPrivateIpv4(ip) || !seen.add(ip)) continue
                    val response = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    if (!response.contains("roku:ecp", ignoreCase = true)) continue
                    try {
                        val device = RokuControl.inspect(ip)
                        main.post { if (running) found(device) }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) { main.post { if (running) status("Busca SSDP limitada: ${e.message}") } }
    }

    fun stop() {
        running = false
        listeners.forEach { try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {} }
        listeners.clear()
        try { if (lock?.isHeld == true) lock?.release() } catch (_: Exception) {}
        lock = null
    }
}
