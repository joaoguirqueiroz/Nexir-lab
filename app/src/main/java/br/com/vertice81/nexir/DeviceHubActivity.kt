package br.com.vertice81.nexir

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.concurrent.thread

/** Painel simplificado e mobile-first; o laboratório anterior continua disponível. */
class DeviceHubActivity : Activity() {
    private val background = Color.rgb(9, 15, 22)
    private val panel = Color.rgb(20, 31, 40)
    private val panel2 = Color.rgb(29, 43, 53)
    private val mint = Color.rgb(116, 224, 206)
    private val ink = Color.rgb(238, 247, 247)
    private val muted = Color.rgb(157, 180, 185)
    private val amber = Color.rgb(248, 194, 119)
    private lateinit var ir: NativeIr
    private lateinit var profiles: IrRepository
    private lateinit var scanner: LanDiscovery
    private lateinit var host: LinearLayout
    private lateinit var body: LinearLayout
    private var screen = "CONTROLE"
    private var lanMode = false
    private var selected: LanDevice? = null
    private var discoveryStatus = "Busca ainda não iniciada"
    private val devices = linkedMapOf<String, LanDevice>()
    private var lastInfo = "Selecione um aparelho e escolha o método de controle."
    private var lastTap = 0L
    private val preferences by lazy { getSharedPreferences("nexir_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        ir = NativeIr(this)
        profiles = IrRepository(this)
        lanMode = !ir.available
        scanner = LanDiscovery(this,
            found = { device ->
                devices[device.id] = device
                discoveryStatus = "${devices.size} dispositivo(s) identificado(s) na rede"
                if (screen == "DISPOSITIVOS") render()
            },
            status = { message ->
                discoveryStatus = message
                if (screen == "DISPOSITIVOS") render()
            }
        )
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::profiles.isInitialized && ::host.isInitialized) {
            profiles.load()
            render()
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v + 0.5f).toInt()
    private fun shape(color: Int, radius: Int = 17, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun text(label: String, sp: Float = 14f, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = label
            textSize = sp
            setTextColor(color)
            typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            setLineSpacing(dp(2).toFloat(), 1f)
        }

    private fun column(pad: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (pad > 0) setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun spacer(height: Int = 10) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun button(label: String, accent: Boolean = false, danger: Boolean = false, action: () -> Unit) =
        Button(this).apply {
            isAllCaps = false
            text = label
            textSize = 12.5f
            setTextColor(if (accent) background else if (danger) amber else ink)
            background = shape(if (accent) mint else panel2, 14, if (danger) amber else null)
            minHeight = dp(50)
            minimumHeight = dp(50)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { action() }
        }

    private fun LinearLayout.action(label: String, accent: Boolean = false, action: () -> Unit) {
        addView(button(label, accent, action = action),
            LinearLayout.LayoutParams(-1, dp(51)).apply { bottomMargin = dp(9) })
    }

    private fun LinearLayout.line(label: String, important: Boolean = false) {
        addView(text(label, if (important) 15f else 12.5f, if (important) ink else muted, important),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }

    private fun card(title: String, subtitle: String = "", block: LinearLayout.() -> Unit) {
        val box = column(17)
        box.background = shape(panel, 20)
        box.addView(text(title, 16f, ink, true))
        if (subtitle.isNotBlank()) { box.addView(spacer(5)); box.addView(text(subtitle, 12.5f, muted)) }
        box.addView(spacer(15))
        box.block()
        body.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
    }

    private fun render() {
        host = column().apply { setBackgroundColor(background) }
        setContentView(host)
        val header = column().apply { setPadding(dp(18), dp(20), dp(18), dp(10)) }
        header.addView(text("NEXIR  /  LAB", 26f, ink, true))
        header.addView(spacer(4))
        header.addView(text("CONTROLE DE DISPOSITIVOS  •  v1.2", 10.5f, mint, true))
        header.addView(spacer(12))
        header.addView(text(
            (if (ir.available) "● IR NATIVO" else "○ SEM EMISSOR IR") +
                "     " + (if (selected != null) "● ROKU NA REDE" else "○ REDE NÃO SELECIONADA"),
            11f, if (ir.available || selected != null) mint else amber
        ))
        host.addView(header)
        val tabsScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val tabs = row().apply { setPadding(dp(14), dp(3), dp(14), dp(9)) }
        listOf("CONTROLE", "DISPOSITIVOS", "LABORATÓRIO").forEach { item ->
            val b = button(item, accent = screen == item) { screen = item; render() }
            tabs.addView(b, LinearLayout.LayoutParams(-2, dp(44)).apply { rightMargin = dp(8) })
        }
        tabsScroll.addView(tabs)
        host.addView(tabsScroll)
        val scroll = ScrollView(this).apply { isFillViewport = false; clipToPadding = false }
        body = column().apply { setPadding(dp(16), dp(5), dp(16), dp(32)) }
        scroll.addView(body)
        host.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        when (screen) {
            "DISPOSITIVOS" -> devicesScreen()
            "LABORATÓRIO" -> labScreen()
            else -> controlScreen()
        }
    }

    private fun controlScreen() {
        val active = selected
        card("EQUIPAMENTO", "Configuração selecionada para os próximos comandos") {
            line(
                if (lanMode) active?.name ?: "Nenhum Roku selecionado"
                else profiles.selected()?.name ?: "Perfil IR não selecionado", true
            )
            line(if (lanMode) "${active?.model ?: "Nenhum modelo"} · ${active?.address ?: "Sem IP"}"
                 else profiles.selected()?.model ?: "Escolha um perfil IR")
            val buttons = row()
            buttons.addView(button("INFRAVERMELHO", accent = !lanMode) {
                lanMode = false; render()
            }, LinearLayout.LayoutParams(0, dp(49), 1f).apply { rightMargin = dp(7) })
            buttons.addView(button("REDE LOCAL", accent = lanMode) {
                lanMode = true; render()
            }, LinearLayout.LayoutParams(0, dp(49), 1f))
            addView(buttons)
            addView(spacer())
            action(if (lanMode) "ESCOLHER TV / TELÃO DA REDE" else "ALTERAR MARCA / PERFIL") {
                if (lanMode) { screen = "DISPOSITIVOS"; render() } else pickProfile()
            }
        }
        if (lanMode && selected == null) {
            card("REDE SEM APARELHO SELECIONADO") {
                line("Conecte o celular ao mesmo Wi-Fi do equipamento. A descoberta identifica aparelhos, mas só controla modelos com protocolo compatível e autorização.")
                action("BUSCAR APARELHOS", true) { screen = "DISPOSITIVOS"; render(); scan() }
            }
        } else if (!lanMode && !ir.available) {
            card("EMISSOR IR NÃO ENCONTRADO") {
                line("Este telefone não informou um transmissor infravermelho ao Android. Para controlar sem ESP32, use a opção REDE LOCAL em um aparelho compatível.")
                action("PROCURAR APARELHOS NA REDE", true) { lanMode = true; screen = "DISPOSITIVOS"; render(); scan() }
            }
        }
        card("CONTROLE REMOTO", if (lanMode) "Roku ECP · comandos oficiais pela rede local" else "Transmissão IR nativa · aponte para o receptor") {
            line("ENERGIA E ÁUDIO", true)
            remoteRow(listOf("⏻  DESLIGAR" to "POWER", "MUDO" to "MUTE"))
            remoteRow(listOf("VOL +" to "VOL_UP", "VOL −" to "VOL_DOWN"))
            addView(spacer(8))
            line("NAVEGAÇÃO", true)
            remoteRow(listOf("⌂ HOME" to "HOME", "☰ MENU" to "MENU", "↩ VOLTAR" to "BACK"))
            remoteRow(listOf("↑" to "UP"))
            remoteRow(listOf("←" to "LEFT", "OK" to "OK", "→" to "RIGHT"))
            remoteRow(listOf("↓" to "DOWN"))
            addView(spacer(8))
            line("CANAIS E REPRODUÇÃO", true)
            remoteRow(listOf("CANAL +" to "CH_UP", "CANAL −" to "CH_DOWN"))
            remoteRow(listOf("⏮ VOLTAR" to "PREVIOUS", "▶ PAUSAR" to "PLAY_PAUSE", "AVANÇAR ⏭" to "NEXT"))
            addView(spacer(8))
            line("ENTRADAS", true)
            if (lanMode) {
                remoteRow(listOf("HDMI 1" to "HDMI_1", "HDMI 2" to "HDMI_2"))
                remoteRow(listOf("HDMI 3" to "HDMI_3", "HDMI 4" to "HDMI_4"))
                line("HDMI, volume e energia pela rede dependem do modelo (principalmente Roku TV).")
            } else remoteRow(listOf("SOURCE / HDMI" to "SOURCE"))
        }
        card("RETORNO DO COMANDO") {
            line(lastInfo)
            line("O envio confirmado não garante que o aparelho respondeu. Observe a tela para validar.")
        }
    }

    private fun LinearLayout.remoteRow(items: List<Pair<String, String>>) {
        val controls = row()
        items.forEachIndexed { i, item ->
            val ready = if (lanMode) selected != null && RokuControl.supports(item.second)
                        else ir.available && profiles.selected()?.commands?.containsKey(item.second) == true
            val control = button(item.first, accent = item.second == "OK" && ready) { transmit(item.second) }
            control.isEnabled = ready
            control.alpha = if (ready) 1f else 0.4f
            controls.addView(control, LinearLayout.LayoutParams(0, dp(57), 1f).apply {
                if (i < items.lastIndex) rightMargin = dp(6)
                bottomMargin = dp(7)
            })
        }
        addView(controls)
    }

    private fun transmit(key: String) {
        if (!preferences.getBoolean("consent", false)) {
            AlertDialog.Builder(this).setTitle("Controle de equipamento físico")
                .setMessage("Confirme que este aparelho é seu ou que você tem autorização expressa para controlá-lo. O comando pode alterar energia, entradas, canais ou áudio.")
                .setPositiveButton("TENHO AUTORIZAÇÃO") { _, _ ->
                    preferences.edit().putBoolean("consent", true).apply()
                    transmit(key)
                }.setNegativeButton("CANCELAR", null).show()
            return
        }
        if (System.currentTimeMillis() - lastTap < 300) return
        val local = selected
        val command = profiles.selected()?.commands?.get(key)
        if (lanMode && local == null) { toast("Selecione um Roku na rede."); return }
        if (!lanMode && (!ir.available || command == null)) { toast("Comando IR não disponível."); return }
        lastTap = System.currentTimeMillis()
        val useNetwork = lanMode
        thread(name = "nexir-send-single-command") {
            try {
                val result = if (useNetwork) RokuControl.send(local!!.address, key)
                             else ir.transmit(command!!).let { "Padrão IR transmitido: ${it.pulses} durações, ${it.carrierHz} Hz" }
                runOnUiThread { lastInfo = "${key.replace('_', ' ')} · $result"; toast("Comando enviado"); render() }
            } catch (e: Exception) {
                runOnUiThread {
                    lastInfo = "Falha no comando ${key.replace('_', ' ')}: ${e.message ?: "não disponível"}"
                    render()
                    toast("Não foi possível controlar o aparelho")
                }
            }
        }
    }

    private fun devicesScreen() {
        card("DESCOBERTA NA REDE", "Wi-Fi local · SSDP Roku e anúncios mDNS") {
            line(discoveryStatus, true)
            line("A identificação obtém informações disponíveis na rede. Não tenta adivinhar senhas nem alterar configurações.")
            action("BUSCAR DISPOSITIVOS", true) { scan() }
            action("ADICIONAR ROKU POR IP") { manualRoku() }
        }
        if (devices.isEmpty()) {
            card("NENHUM DISPOSITIVO LISTADO") {
                line("Conecte o celular à mesma rede Wi-Fi da TV e toque em BUSCAR. Alguns painéis profissionais só permitem controle por apps do fabricante ou portas físicas.")
            }
        } else {
            devices.values.toList().forEach { device ->
                card(device.name, "${device.kind} · ${device.protocol}") {
                    line("Modelo: ${device.model}")
                    line("Endereço: ${device.address}")
                    if (device.firmware.isNotBlank()) line("Firmware: ${device.firmware}")
                    if (device.controllable) {
                        action("USAR COMO CONTROLE DE REDE", true) {
                            selected = device
                            lanMode = true
                            screen = "CONTROLE"
                            lastInfo = "Roku selecionado. Controle disponível quando a opção 'Controle por aplicativos móveis' estiver habilitada no aparelho."
                            render()
                        }
                    } else {
                        action("VER COMPATIBILIDADE") {
                            message("Dispositivo descoberto",
                                "O anúncio mDNS não fornece acesso ao controle. Para este modelo, use o IR nativo do celular ou um aplicativo/protocolo oficial com pareamento autorizado.")
                        }
                    }
                }
            }
        }
        card("SOBRE O ACESSO", "O aplicativo não ignora senhas, pareamento ou permissões") {
            line("Roku: consulta modelo e firmware por ECP, e envia comandos se 'Controle por aplicativos móveis' estiver habilitado.")
            line("Google Cast, AirPlay e serviços HTTP: identificação inicial. Controle depende de integração e autenticação específicas.")
            line("TVs apenas com IR: não publicam seu modelo na rede. Identificação por etiqueta está no Laboratório.")
        }
    }

    private fun scan() {
        devices.clear()
        discoveryStatus = "Iniciando busca..."
        render()
        scanner.stop()
        scanner.start()
    }

    private fun manualRoku() {
        val field = EditText(this).apply {
            hint = "Ex.: 192.168.1.25"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        val layout = column(18).apply { addView(field) }
        AlertDialog.Builder(this).setTitle("Identificar Roku pelo IP")
            .setMessage("Somente endereços privados na sua rede autorizada. Nenhum comando será enviado durante a identificação.")
            .setView(layout)
            .setPositiveButton("IDENTIFICAR") { _, _ ->
                val ip = field.text.toString().trim()
                if (!RokuControl.isPrivateIpv4(ip)) {
                    message("Endereço inválido", "Digite um endereço IPv4 privado válido.")
                } else {
                    discoveryStatus = "Consultando informações do Roku..."
                    render()
                    thread {
                        try {
                            val device = RokuControl.inspect(ip)
                            runOnUiThread {
                                devices[device.id] = device
                                discoveryStatus = "Modelo identificado: ${device.model}"
                                render()
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                discoveryStatus = "Falha ao identificar"
                                message("Dispositivo não identificado", e.message ?: "Resposta indisponível")
                                render()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("CANCELAR", null).show()
    }

    private fun labScreen() {
        card("IDENTIFICAÇÃO POR CÂMERA", "OCR local em fotos de etiquetas") {
            line("Para televisores que não divulgam dados na rede, fotografe a etiqueta e revise marca e modelo antes de selecionar um perfil IR.")
            action("ABRIR LEITURA DE ETIQUETA", true) { openLegacy("IDENTIFICAR") }
        }
        card("BIBLIOTECA DE CONTROLES", "Perfis e códigos offline") {
            line("${profiles.profiles.size} perfis locais disponíveis. Os códigos são referenciais e exigem confirmação no equipamento.")
            action("ABRIR BIBLIOTECA") { openLegacy("BIBLIOTECA") }
            action("EDITAR E APRENDER COMANDOS") { openLegacy("APRENDER") }
        }
        card("AUDITORIA E HARDWARE") {
            line("Emissor IR nativo: ${if (ir.available) "DISPONÍVEL" else "NÃO DETECTADO"}", true)
            line("Faixas de portadora: ${ir.ranges()}")
            line("Captura IR pelo emissor do celular não é oferecida pela API padrão do Android; para aprender sinais, utilize receptor IR externo compatível.")
            action("DIAGNÓSTICO DE SINAIS") { openLegacy("DIAGNÓSTICO") }
            action("RELATÓRIOS DE TESTE") { openLegacy("AUDITORIA") }
        }
    }

    private fun pickProfile() {
        val items = profiles.profiles.map { "${it.brand}  ·  ${it.name}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Selecionar equipamento IR")
            .setItems(items) { _, index ->
                profiles.choose(profiles.profiles[index].id)
                lanMode = false
                render()
            }.setNegativeButton("CANCELAR", null).show()
    }

    private fun openLegacy(tab: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra("start_tab", tab))
    }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun message(title: String, body: String) =
        AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("OK", null).show()
    override fun onDestroy() { scanner.stop(); super.onDestroy() }
}
