package br.rootscope.ipshadow

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val background = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val navy = Color.rgb(8, 17, 29)
    private val surface = Color.rgb(18, 31, 48)
    private val mint = Color.rgb(43, 218, 178)
    private val pale = Color.rgb(234, 246, 248)
    private val secondary = Color.rgb(162, 181, 198)
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private var page = 0
    private var active = false
    private var busy = false
    private var ip = "Ainda não verificado"
    private var rx = 0L
    private var tx = 0L
    private var handshake: Long? = null
    private var error: String? = null
    private val requestVpn = 1001
    private val requestProfile = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(navy) }
        setContentView(root)
        page = savedInstanceState?.getInt("page") ?: 0
        render()
        refresh()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("page", page); super.onSaveInstanceState(outState) }
    override fun onResume() { super.onResume(); refresh() }
    override fun onDestroy() { background.shutdownNow(); super.onDestroy() }

    private fun dp(n: Int): Int = (resources.displayMetrics.density * n).toInt()
    private fun shape(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(16).toFloat()
        setStroke(dp(1), Color.rgb(38, 62, 79))
    }
    private fun label(t: String, size: Float = 15f, color: Int = pale, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    private fun add(container: LinearLayout, t: String, size: Float = 15f, color: Int = pale, bold: Boolean = false): TextView {
        val v = label(t, size, color, bold)
        container.addView(v, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        return v
    }
    private fun card(title: String, subtitle: String? = null): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = shape(surface)
            setPadding(dp(18), dp(18), dp(18), dp(15))
        }
        add(card, title, 18f, pale, true)
        if (subtitle != null) add(card, subtitle, 13f, secondary)
        body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        return card
    }
    private fun action(parent: LinearLayout, text: String, mainAction: Boolean = false, enabled: Boolean = true, click: () -> Unit) {
        val button = Button(this).apply {
            this.text = text; textSize = 14f; isAllCaps = false
            setTextColor(if (mainAction) navy else pale)
            background = shape(if (mainAction) mint else Color.rgb(31, 50, 69))
            minHeight = dp(52); isEnabled = enabled
            setOnClickListener { click() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(9) })
    }
    private fun render() {
        if (isFinishing || isDestroyed) return
        root.removeAllViews()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(18), dp(22), dp(15))
        }
        add(header, "IP SHADOW", 24f, mint, true)
        add(header, "PRIVACIDADE E REDE • ANDROID", 11f, secondary)
        root.addView(header)
        val scroll = ScrollView(this).apply { isFillViewport = false; clipToPadding = false }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = if (resources.configuration.screenWidthDp < 360) 12 else 18
            setPadding(dp(p), dp(2), dp(p), dp(15))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        when (page) {
            0 -> home()
            1 -> profiles()
            2 -> diagnostics()
            else -> privacy()
        }
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(7), dp(4), dp(7))
            setBackgroundColor(Color.rgb(12, 27, 42))
        }
        listOf("Início", "Servidores", "Rede", "Proteção").forEachIndexed { index, name ->
            val tab = label(name, if (resources.configuration.screenWidthDp < 340) 11f else 12f,
                if (page == index) mint else secondary, page == index).apply {
                gravity = Gravity.CENTER; minimumHeight = dp(52); isClickable = true; isFocusable = true
                contentDescription = "Abrir " + name
                setOnClickListener { page = index; render() }
            }
            nav.addView(tab, LinearLayout.LayoutParams(0, dp(55), 1f))
        }
        root.addView(nav)
    }
    private fun home() {
        val c = card(if (active) "VPN ATIVA" else "VPN DESLIGADA",
            if (active) "Túnel ativado no Android. Verifique o IP externo." else "Navegação fora do túnel VPN.")
        add(c, "IP público observado", 13f, secondary)
        add(c, ip, 25f, pale, true)
        action(c, if (busy) "Aguarde..." else if (active) "Desconectar VPN" else "Conectar VPN",
            true, !busy) { if (active) disconnect() else connect() }
        action(c, "Atualizar IP público", false, !busy) { probe() }
        val n = card("Conexão segura", "O IP muda quando seu tráfego sai por um servidor WireGuard válido.")
        add(n, if (SecureProfile.exists(this)) "Perfil configurado e salvo no aparelho." else "Sem servidor configurado. Importe um perfil WireGuard .conf para começar.", 14f, if (SecureProfile.exists(this)) mint else secondary)
        action(n, "Configurar servidor") { page = 1; render() }
        if (error != null) add(card("Aviso"), error ?: "", 14f, Color.rgb(255, 185, 155))
    }
    private fun profiles() {
        val c = card("Servidor VPN", "Perfil WireGuard importado e guardado com Android Keystore.")
        add(c, if (SecureProfile.exists(this)) "Perfil disponível para conectar." else "Nenhum perfil configurado.", 15f)
        action(c, "Importar arquivo .conf", true, !active && !busy) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            }, requestProfile)
        }
        if (SecureProfile.exists(this)) action(c, "Excluir perfil", false, !active && !busy) {
            AlertDialog.Builder(this).setTitle("Excluir perfil?").setMessage("A configuração VPN será removida deste aparelho.")
                .setPositiveButton("Excluir") { _, _ -> SecureProfile.remove(this); render() }
                .setNegativeButton("Cancelar", null).show()
        }
        val f = card("VPN gratuita", "Sem VPS própria: Proton VPN Free com um perfil WireGuard válido.")
        add(f, "1. Acesse sua conta gratuita no Proton VPN.", 14f)
        add(f, "2. Gere e baixe o perfil WireGuard disponível para sua conta.", 14f)
        add(f, "3. Importe o arquivo acima. Depois conecte na tela Início.", 14f)
        action(f, "Abrir instruções oficiais", true) {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://protonvpn.com/support/wireguard-configurations")))
        }
        add(f, "Não há servidores abertos ou credenciais compartilhadas embutidas no APK.", 12f, secondary)
    }
    private fun diagnostics() {
        val c = card("Diagnóstico de rede", "Consulta HTTPS externa, sem resultados simulados.")
        add(c, "IPv4 público: " + ip, 16f, pale, true)
        add(c, "Estado do túnel: " + if (active) "ATIVO" else "DESLIGADO", 14f)
        add(c, "Download: " + rx + " bytes", 14f)
        add(c, "Upload: " + tx + " bytes", 14f)
        add(c, "Handshake: " + (handshake?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "não informado"), 13f, secondary)
        action(c, "Testar IP e conexão", true) { probe() }
        action(c, "Copiar IP") {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("IP público", ip))
            android.widget.Toast.makeText(this, "IP copiado", android.widget.Toast.LENGTH_SHORT).show()
        }
        val note = card("Interpretação")
        add(note, "Túnel ativo não garante que o servidor esteja encaminhando internet. Verifique o IP antes e depois da conexão.", 13f, secondary)
    }
    private fun privacy() {
        val c = card("Proteção", "Kill switch controlado pelo próprio Android.")
        add(c, "Para impedir tráfego sem túnel, abra as configurações VPN e ative VPN sempre ativa e Bloquear conexões sem VPN.", 14f)
        action(c, "Abrir configurações de VPN", true) {
            startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
        }
        val warning = card("Limitações")
        add(warning, "A VPN altera o IP público de saída, mas não esconde operadora, IMEI, GPS ou contas conectadas. O IP SHADOW não fornece servidor próprio.", 14f, secondary)
    }
    private fun refresh() {
        background.execute {
            val result = runCatching {
                Triple(ShadowVpn.backend.getState(ShadowVpn.tunnel) == Tunnel.State.UP,
                    ShadowVpn.backend.getStatistics(ShadowVpn.tunnel), null)
            }
            main.post {
                if (isFinishing || isDestroyed) return@post
                if (result.isSuccess) {
                    val value = result.getOrThrow()
                    active = value.first
                    rx = value.second.totalRx()
                    tx = value.second.totalTx()
                    handshake = value.second.peers().mapNotNull { peer ->
                        value.second.peer(peer)?.latestHandshakeEpochMillis()?.takeIf { it > 0 }
                    }.maxOrNull()
                } else { error = result.exceptionOrNull()?.localizedMessage }
                render()
            }
        }
    }
    private fun connect() {
        if (!SecureProfile.exists(this)) {
            error = "Importe um perfil na aba Servidores."
            page = 1; render(); return
        }
        try {
            val consent = VpnService.prepare(this)
            if (consent != null) startActivityForResult(consent, requestVpn) else change(true)
        } catch (e: Exception) { error = e.localizedMessage; render() }
    }
    private fun disconnect() = change(false)
    private fun change(up: Boolean) {
        if (busy) return
        busy = true; error = null; render()
        background.execute {
            val failure = runCatching {
                if (up) ShadowVpn.backend.setState(ShadowVpn.tunnel, Tunnel.State.UP, SecureProfile.load(this))
                else ShadowVpn.backend.setState(ShadowVpn.tunnel, Tunnel.State.DOWN, null)
            }.exceptionOrNull()
            main.post {
                busy = false; error = failure?.localizedMessage
                refresh()
                if (failure == null && up) probe()
                if (!up) ip = "Ainda não verificado"
            }
        }
    }
    private fun probe() {
        if (busy) return
        busy = true; render()
        background.execute {
            val value = runCatching {
                val connection = URL("https://api4.ipify.org").openConnection() as javax.net.ssl.HttpsURLConnection
                connection.connectTimeout = 6000; connection.readTimeout = 6000
                try { connection.inputStream.bufferedReader().use { it.readText().trim() } }
                finally { connection.disconnect() }
            }.getOrNull()
            main.post {
                ip = if (value != null && value.length in 7..45) value else "Sem resposta IPv4"
                busy = false; render()
            }
        }
    }
    @Deprecated("Compatibilidade com Activity APIs")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestVpn) {
            if (resultCode == RESULT_OK) change(true)
            else { error = "Permissão de VPN não concedida."; render() }
        }
        if (requestCode == requestProfile && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            busy = true; render()
            background.execute {
                val failure = runCatching {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Arquivo não encontrado")
                    require(bytes.size in 1..16384) { "Arquivo inválido ou grande demais" }
                    SecureProfile.save(this, bytes)
                }.exceptionOrNull()
                main.post {
                    busy = false
                    error = failure?.localizedMessage
                    render()
                    if (failure == null) AlertDialog.Builder(this)
                        .setTitle("Perfil importado")
                        .setMessage("Conectar agora?")
                        .setPositiveButton("Conectar") { _, _ -> connect() }
                        .setNegativeButton("Depois", null).show()
                }
            }
        }
    }
}