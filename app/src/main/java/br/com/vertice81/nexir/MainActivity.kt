package br.com.vertice81.nexir

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

@Suppress("DEPRECATION")
class MainActivity : Activity() {
    private val bg = Color.rgb(7, 12, 18)
    private val surface = Color.rgb(16, 25, 34)
    private val surface2 = Color.rgb(21, 35, 45)
    private val accent = Color.rgb(104, 220, 204)
    private val secondary = Color.rgb(151, 170, 180)
    private val white = Color.rgb(233, 243, 245)
    private val warning = Color.rgb(242, 180, 108)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var ir: NativeIr
    private lateinit var store: IrRepository
    private lateinit var bridge: BluetoothBridge
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var statusLabel: TextView
    private val preferences by lazy { getSharedPreferences("nexir_settings", MODE_PRIVATE) }
    private val logs = mutableListOf<String>()
    private var lastSend: SendResult? = null
    private var tab = "CONTROLE"
    private var useEsp32 = false
    private var lastClick = 0L
    private var cameraUri: Uri? = null
    private var lastCapture: IrCommand? = null
    private var lastAuditCommand: String? = null
    private var lastAuditProfile: String? = null
    private var lastAuditTimestamp: Long = 0L

    companion object {
        const val RC_CAMERA = 1001
        const val RC_GALLERY = 1002
        const val RC_IMPORT = 1003
        const val RC_EXPORT = 1004
        const val RC_BLUETOOTH = 1005
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        store = IrRepository(this)
        ir = NativeIr(this)
        useEsp32 = !ir.available
        logs.addAll(preferences.getString("history", "")!!.split('\n').filter { it.isNotBlank() }.takeLast(30))
        bridge = BluetoothBridge(this,
            events = { message -> runOnUiThread { log(message); if (tab == "DIAGNÓSTICO" || tab == "APRENDER") render() } },
            capture = { command -> runOnUiThread { lastCapture = command; log("Captura recebida: ${command.pattern?.size ?: 0} durações; 38 kHz presumidos"); showSaveCapture(command) } }
        )
        render()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value + .5f).toInt()
    private fun rounded(color: Int, radius: Int = 14, borderColor: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        borderColor?.let { setStroke(dp(1), it) }
    }
    private fun text(value: String, size: Float = 14f, color: Int = white, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = value
        this.textSize = size
        setTextColor(color)
        typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
        includeFontPadding = true
    }
    private fun space(height: Int = 12) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun vertical(padding: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (padding > 0) setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }
    private fun horizontal() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun card(title: String, subtitle: String? = null, block: LinearLayout.() -> Unit): LinearLayout {
        val box = vertical(16).apply { background = rounded(surface, 18) }
        box.addView(text(title, 16f, white, true))
        if (subtitle != null) { box.addView(space(5)); box.addView(text(subtitle, 12.5f, secondary)) }
        box.addView(space(12))
        box.block()
        content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        return box
    }
    private fun label(value: String) { content.addView(text(value, 12f, secondary, true), LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(12) }) }
    private fun button(value: String, primary: Boolean = false, outlined: Boolean = false, action: () -> Unit): Button = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 12.5f
        setTextColor(if (primary) bg else if (outlined) accent else white)
        background = rounded(if (primary) accent else if (outlined) surface else surface2,
            13, if (outlined) accent else null)
        minHeight = dp(44)
        minimumHeight = dp(44)
        setPadding(dp(9), dp(8), dp(9), dp(8))
        setOnClickListener { action() }
    }
    private fun LinearLayout.addAction(label: String, primary: Boolean = false, action: () -> Unit) {
        addView(button(label, primary, action = action), LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(9) })
    }
    private fun LinearLayout.addLine(value: String, emphasize: Boolean = false) {
        addView(text(value, if (emphasize) 14f else 12.5f, if (emphasize) white else secondary, emphasize),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }

    private fun render() {
        root = vertical()
        root.setBackgroundColor(bg)
        setContentView(root)
        val header = vertical().apply {
            setPadding(dp(18), dp(22), dp(18), dp(13))
            addView(text("NEXIR", 28f, white, true))
            addView(text("INFRARED CONTROL & ANALYSIS  /  LAB 1.0", 10f, accent, true))
            addView(space(11))
            statusLabel = text("●  ${if (ir.available) "EMISSOR IR DISPONÍVEL" else "IR NATIVO AUSENTE"}   ·   ${if (bridge.connectedName != null) "ESP32 CONECTADO" else "ESP32 OFFLINE"}", 11f, if (ir.available || bridge.connectedName != null) accent else warning)
            addView(statusLabel)
        }
        root.addView(header)
        val scrollTabs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val tabs = horizontal().apply { setPadding(dp(14), 0, dp(14), dp(8)) }
        listOf("CONTROLE", "BIBLIOTECA", "IDENTIFICAR", "APRENDER", "AUDITORIA", "DIAGNÓSTICO").forEach { name ->
            tabs.addView(button(name, primary = name == tab) { tab = name; render() },
                LinearLayout.LayoutParams(-2, dp(43)).apply { marginEnd = dp(7) })
        }
        scrollTabs.addView(tabs)
        root.addView(scrollTabs)
        val scroller = ScrollView(this).apply { isFillViewport = false; clipToPadding = false }
        content = vertical().apply { setPadding(dp(16), dp(6), dp(16), dp(30)) }
        scroller.addView(content)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        when(tab) {
            "CONTROLE" -> remoteScreen()
            "BIBLIOTECA" -> libraryScreen()
            "IDENTIFICAR" -> identifyScreen()
            "APRENDER" -> learnScreen()
            "AUDITORIA" -> auditScreen()
            else -> diagnosticsScreen()
        }
    }

    private fun remoteScreen() {
        val selected = store.selected()
        card("CONTROLE ATIVO", "Selecione o perfil do seu equipamento autorizado") {
            addLine(selected?.name ?: "Nenhum perfil disponível", true)
            addLine(selected?.model ?: "")
            addAction("ALTERAR PERFIL") { chooseProfile() }
            val row = horizontal()
            row.addView(button("IR DO CELULAR", primary = !useEsp32) { useEsp32 = false; render() }, LinearLayout.LayoutParams(0, dp(43), 1f).apply { marginEnd = dp(6) })
            row.addView(button("ESP32 EXTERNO", primary = useEsp32) { useEsp32 = true; render() }, LinearLayout.LayoutParams(0, dp(43), 1f))
            addView(row)
        }
        card("CONTROLE REMOTO", "Os comandos são transmitidos ao pressionar; não há envio automático ou varredura de códigos") {
            remoteRow(listOf("⏻  POWER" to "POWER", "◌  MUDO" to "MUTE"))
            remoteRow(listOf("⌂  HOME" to "HOME", "☰  MENU" to "MENU", "↩  VOLTAR" to "BACK"))
            addView(space(12))
            addLine("VOLUME  /  CANAIS", true)
            remoteRow(listOf("VOL +" to "VOL_UP", "CH +" to "CH_UP"))
            remoteRow(listOf("VOL −" to "VOL_DOWN", "CH −" to "CH_DOWN"))
            addView(space(10))
            addLine("NAVEGAÇÃO", true)
            remoteRow(listOf("↑" to "UP"))
            remoteRow(listOf("←" to "LEFT", "OK" to "OK", "→" to "RIGHT"))
            remoteRow(listOf("↓" to "DOWN"))
            addView(space(10))
            addLine("ENTRADAS E MÍDIA", true)
            remoteRow(listOf("HDMI / SOURCE" to "SOURCE", "PLAY / PAUSE" to "PLAY_PAUSE"))
            remoteRow(listOf("⏮ ANTERIOR" to "PREVIOUS", "PRÓXIMO ⏭" to "NEXT"))
            val builtIn = setOf("POWER", "MUTE", "HOME", "MENU", "BACK", "VOL_UP", "VOL_DOWN", "CH_UP", "CH_DOWN", "UP", "DOWN", "LEFT", "RIGHT", "OK", "SOURCE", "PLAY_PAUSE", "PREVIOUS", "NEXT")
            val extra = selected?.commands?.keys?.filter { it !in builtIn }?.sorted().orEmpty()
            if (extra.isNotEmpty()) {
                addView(space(8))
                addLine("FUNÇÕES PERSONALIZADAS", true)
                extra.chunked(2).forEach { row -> remoteRow(row.map { it.replace('_', ' ') to it }) }
            }
            addView(space(8))
            addAction("+ CONFIGURAR / ADICIONAR COMANDO") { addOrEditCommand() }
            addAction("VER CÓDIGOS DO PERFIL") { showCodes() }
        }
        card("COMPATIBILIDADE", "Validação real depende da marca, modelo, receptor e hardware de transmissão") {
            addLine("Códigos de fábrica são perfis iniciais. Confirme a reação do equipamento no primeiro teste.")
            addLine("O aplicativo não detecta automaticamente se a TV executou o comando; o envio sem erro confirma apenas que a API ou ESP32 aceitou a solicitação.")
        }
    }

    private fun LinearLayout.remoteRow(items: List<Pair<String, String>>) {
        val row = horizontal()
        items.forEachIndexed { index, pair ->
            val present = store.selected()?.commands?.containsKey(pair.second) == true
            val b = button(pair.first, outlined = present) { sendCommand(pair.second) }
            if (!present) b.alpha = .62f
            row.addView(b, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = if (index < items.lastIndex) dp(7) else 0; bottomMargin = dp(7) })
        }
        addView(row)
    }

    private fun sendCommand(key: String) {
        val command = store.selected()?.commands?.get(key)
        if (command == null) { showMessage("Comando ausente", "O perfil não possui $key. Aprenda o sinal usando o ESP32 ou cadastre um código compatível."); return }
        if (!preferences.getBoolean("consent", false)) {
            AlertDialog.Builder(this).setTitle("Controle de equipamento físico")
                .setMessage("Use este aplicativo somente com equipamentos seus ou que tenha autorização para controlar. Os comandos podem alterar volume, canal, fonte e energia.")
                .setPositiveButton("Tenho autorização") { _, _ -> preferences.edit().putBoolean("consent", true).apply(); sendCommand(key) }
                .setNegativeButton("Cancelar", null).show()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastClick < 250) return
        lastClick = now
        thread(name="nexir-ir-send") {
            try {
                val result = if (useEsp32) bridge.transmit(command) else ir.transmit(command)
                runOnUiThread {
                    lastSend = result
                    lastAuditCommand = key
                    lastAuditProfile = store.selectedId
                    lastAuditTimestamp = System.currentTimeMillis()
                    log("$key · ${result.path} · ${result.carrierHz} Hz · ${result.pulses} durações · ${result.durationMicros / 1000} ms")
                    if (tab == "AUDITORIA") render()
                    toast("$key: ${if (useEsp32) "solicitado ao ESP32" else "transmitido"}")
                }
            } catch (e: Exception) { runOnUiThread { showMessage("Não foi possível transmitir", e.message ?: "Falha de hardware"); log("Falha $key: ${e.message}") } }
        }
    }

    private fun libraryScreen() {
        card("BIBLIOTECA OFFLINE", "Armazenada no dispositivo, com importação e exportação em JSON") {
            addAction("+ NOVO PERFIL") { newProfile() }
            addAction("IMPORTAR PERFIS JSON") { openDocument(RC_IMPORT, "application/json") }
            addAction("EXPORTAR BIBLIOTECA") {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_TITLE, "nexir-profiles.json")
                }
                startActivityForResult(intent, RC_EXPORT)
            }
        }
        label("${store.profiles.size} PERFIS DISPONÍVEIS")
        store.profiles.forEach { profile ->
            card("${if (profile.id == store.selectedId) "●  " else ""}${profile.name}", "${profile.brand}   ·   ${profile.commands.size} comandos") {
                addLine(profile.model)
                if (profile.note.isNotBlank()) addLine(profile.note)
                addAction(if (profile.id == store.selectedId) "PERFIL SELECIONADO ✓" else "USAR ESTE PERFIL", profile.id != store.selectedId) {
                    store.choose(profile.id); render()
                }
            }
        }
    }

    private fun identifyScreen() {
        card("IDENTIFICAÇÃO ASSISTIDA", "OCR local de etiquetas e modelos · sem envio de imagens a servidores") {
            addLine("Fotografe a etiqueta de uma TV, projetor ou receptor. O reconhecimento de texto sugere marca e modelo, mas a seleção do perfil exige sua confirmação.")
            addAction("FOTOGRAFAR ETIQUETA", true) { photograph() }
            addAction("SELECIONAR FOTO DA GALERIA") { openDocument(RC_GALLERY, "image/*") }
        }
        card("COMO A IDENTIFICAÇÃO FUNCIONA") {
            addLine("01  Extrai textos usando ML Kit com modelo embarcado.")
            addLine("02  Compara nomes de marcas com a biblioteca instalada.")
            addLine("03  Mostra o texto da etiqueta para revisão humana.")
            addLine("04  Você escolhe e testa o perfil. Marca/modelo não podem ser obtidos somente por infravermelho transmissor.")
        }
    }

    private fun learnScreen() {
        card("ESP32 · CAPTURA E TRANSMISSÃO", "Conexão Bluetooth Classic SPP com módulo receptor/transmissor IR") {
            addLine("Conexão: ${bridge.connectedName ?: "Desconectado"}", true)
            addLine("Emparelhe o ESP32 NEXIR-BRIDGE nas Configurações Bluetooth do Android antes de conectar.")
            addAction("CONECTAR DISPOSITIVO PAREADO", true) { connectEsp32() }
            addAction("AGUARDAR PRÓXIMO SINAL IR") {
                try { bridge.captureNext(); toast("Aponte o controle original ao receptor do ESP32") }
                catch (e: Exception) { showMessage("Sem conexão", e.message ?: "Conecte o ESP32") }
            }
            if (lastCapture != null) {
                val sample = lastCapture!!.pattern.orEmpty()
                addLine("Última captura: ${sample.size} durações, ${sample.sumOf { it.toLong() } / 1000} ms; amostra [µs]: ${sample.take(12).joinToString(", ")}")
                addAction("SALVAR ÚLTIMA CAPTURA") { showSaveCapture(lastCapture!!) }
            }
            addAction("DESCONECTAR ESP32") { bridge.disconnect(); render() }
        }
        card("CAPTURA PELO PRÓPRIO CELULAR", "Disponibilidade real do hardware Android") {
            addLine("Emissor nativo: ${if (ir.available) "DETECTADO (pode transmitir)" else "NÃO DETECTADO"}", true)
            addLine("Receptor nativo: NÃO ACESSÍVEL PELA API PADRÃO DO ANDROID.")
            addLine("A presença de um emissor IR não habilita captura. Alguns aparelhos podem ter hardware/SDK proprietário de recepção, mas precisam de integração específica, validada para aquele modelo.")
            addLine("Para aprender sinais hoje, conecte o ESP32 com receptor IR externo, capture, salve e depois retransmita com o emissor nativo, se presente.")
        }
        card("REQUISITOS FÍSICOS", "O aprendizado não funciona apenas com o emissor IR do celular") {
            addLine("• ESP32 original com Bluetooth Classic (WROOM/DevKit V1), sensor VS1838B/TSOP38xx e biblioteca IRremoteESP8266.")
            addLine("• Para transmitir externamente: LED infravermelho com resistor/transistor dimensionados corretamente.")
            addLine("• A frequência de 38 kHz informada para um sinal capturado é presumida pelo sensor; não é uma medição da portadora.")
            addLine("• A forma de onda capturada pode precisar de ajustes e não funciona com controles RF ou Bluetooth sem IR.")
        }
    }

    private fun auditScreen() {
        card("AUDITORIA IR · APARELHOS AUTORIZADOS", "Ensaio de envio de comando único e verificação manual do resultado") {
            addLine("Perfil selecionado: ${store.selected()?.name ?: "Nenhum"}", true)
            addLine("Transmissor: ${if (useEsp32) "ESP32 conectado: ${bridge.connectedName ?: "não"}" else if (ir.available) "IR nativo disponível" else "IR nativo indisponível"}")
            addLine("O teste verifica se um equipamento sob seu controle executa um comando do perfil selecionado. Não há varredura de códigos, repetição automática, bloqueio/jamming ou atuação em equipamentos de terceiros.")
            addAction("SELECIONAR COMANDO DE TESTE", true) {
                val commands = store.selected()?.commands?.keys?.sorted().orEmpty()
                if (commands.isEmpty()) {
                    showMessage("Sem comandos", "Selecione um perfil com comandos cadastrados.")
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Um comando por teste")
                        .setItems(commands.toTypedArray()) { _, index ->
                            val name = commands[index]
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Confirmar envio")
                                .setMessage("Você está diante do seu equipamento autorizado e deseja transmitir o comando $name apenas uma vez? Observe a resposta no aparelho.")
                                .setPositiveButton("ENVIAR UMA VEZ") { _, _ -> sendCommand(name) }
                                .setNegativeButton("Cancelar", null).show()
                        }
                        .setNegativeButton("Cancelar", null).show()
                }
            }
        }
        val pending = lastAuditCommand
        if (pending != null && lastAuditProfile == store.selectedId && System.currentTimeMillis() - lastAuditTimestamp < 300000L) {
            card("OBSERVAÇÃO DO RESULTADO", "A transmissão não informa se a TV reagiu; marque o que você observou") {
                addLine("Comando: $pending · enviado pelo ${lastSend?.path ?: "transmissor"}", true)
                addAction("O APARELHO EXECUTOU") { saveAudit(pending, "EXECUTOU") }
                addAction("O APARELHO NÃO EXECUTOU") { saveAudit(pending, "NÃO EXECUTOU") }
                addAction("RESULTADO INCONCLUSIVO") { saveAudit(pending, "INCONCLUSIVO") }
            }
        }
        card("RESULTADOS DA AUDITORIA", "Anotações locais; nenhuma resposta do aparelho é detectada automaticamente") {
            val history = preferences.getString("audit_history", "").orEmpty().lines().filter { it.isNotBlank() }.takeLast(15)
            if (history.isEmpty()) addLine("Nenhum ensaio registrado.")
            history.asReversed().forEach { addLine(it) }
            addAction("COMPARTILHAR RESULTADOS") {
                val body = "NEXIR LAB — Auditoria IR\n" + preferences.getString("audit_history", "").orEmpty()
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                startActivity(Intent.createChooser(intent, "Compartilhar auditoria"))
            }
        }
        card("INTERPRETAÇÃO TÉCNICA", "Receptores IR geralmente não confirmam identidade") {
            addLine("Se o aparelho executou um comando compatível, isso demonstra aceitação daquele sinal no cenário testado. Isoladamente, não comprova uma CVE ou permite concluir sobre outros modelos.")
            addLine("Mitigações podem incluir controle de acesso físico, posicionamento dos receptores e políticas de operação apropriadas ao ambiente.")
        }
    }

    private fun saveAudit(command: String, outcome: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val row = "$stamp | ${store.selected()?.brand ?: "?"} | ${store.selected()?.model ?: "?"} | $command | $outcome"
        val past = preferences.getString("audit_history", "").orEmpty().lines().filter { it.isNotBlank() }
        preferences.edit().putString("audit_history", (past + row).takeLast(50).joinToString("\n")).apply()
        log("Auditoria: $command · $outcome")
        lastAuditCommand = null
        render()
    }

    private fun diagnosticsScreen() {
        card("DIAGNÓSTICO DE HARDWARE", "Status informado pelo Android e conexão externa") {
            addLine("Emissor IR nativo: ${if (ir.available) "PRESENTE" else "NÃO DETECTADO"}", true)
            addLine("Captura IR nativa: não fornecida pela API pública ConsumerIrManager")
            addLine("Portadoras suportadas: ${ir.ranges()}")
            addLine("Ponte externa: ${bridge.connectedName ?: "Não conectada"}")
            addLine("Transmissão selecionada: ${if (useEsp32) "ESP32 SPP" else "ConsumerIrManager"}")
            addLine("Android: ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
            addLine("Hardware: ${Build.MANUFACTURER} / ${Build.MODEL}")
        }
        lastSend?.let { v ->
            card("ÚLTIMO SINAL", "Valores solicitados à API ou ponte externa; não são medidas de um osciloscópio") {
                addLine("Caminho: ${v.path}")
                addLine("Protocolo: ${v.protocol}")
                addLine("Portadora configurada: ${v.carrierHz} Hz")
                addLine("Durações enviadas: ${v.pulses}")
                addLine("Duração total: ${"%.2f".format(Locale.US, v.durationMicros / 1000.0)} ms")
                addLine("Horário: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(v.timestamp))}")
                addLine("A transmissão não garante recepção/execução pelo aparelho.")
            }
        }
        card("REGISTRO DE EVENTOS", "Histórico local dos últimos eventos") {
            if (logs.isEmpty()) addLine("Nenhum evento registrado.")
            logs.takeLast(20).asReversed().forEach { addLine(it) }
            addAction("LIMPAR HISTÓRICO") {
                logs.clear(); preferences.edit().remove("history").apply(); render()
            }
            addAction("COMPARTILHAR DIAGNÓSTICO") {
                val diagnostic = "NEXIR LAB • Diagnóstico\nIR: ${ir.available}\n${ir.ranges()}\nESP32: ${bridge.connectedName ?: "offline"}\n\n${logs.joinToString("\n")}".take(10000)
                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, diagnostic) }
                startActivity(Intent.createChooser(intent, "Compartilhar diagnóstico"))
            }
        }
    }

    private fun chooseProfile() {
        val labels = store.profiles.map { "${it.brand} · ${it.name}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Escolher perfil").setItems(labels) { _, which ->
            store.choose(store.profiles[which].id); render()
        }.setNegativeButton("Cancelar", null).show()
    }
    private fun inputDialog(title: String, hint: String, initial: String = "", next: (String) -> Unit) {
        val field = EditText(this).apply { setSingleLine(true); setText(initial); this.hint = hint; setTextColor(white); setHintTextColor(secondary) }
        val container = vertical(18).apply { addView(field) }
        AlertDialog.Builder(this).setTitle(title).setView(container).setPositiveButton("CONTINUAR") { _, _ ->
            val value = field.text.toString().trim()
            if (value.isNotBlank()) next(value) else toast("Preencha o campo")
        }.setNegativeButton("Cancelar", null).show()
    }
    private fun newProfile() {
        inputDialog("Novo perfil", "Nome do equipamento") { name ->
            inputDialog("Marca", "Ex.: Samsung, LG, Projetor") { brand ->
                val p = IrProfile("custom-${UUID.randomUUID()}", name.take(100), brand.take(70), "Modelo informado manualmente", "Criado no NEXIR LAB", linkedMapOf())
                store.add(p); render()
            }
        }
    }
    private fun addOrEditCommand() {
        val current = store.selected() ?: return
        inputDialog("Nome técnico do comando", "Ex.: POWER, HDMI2, HOME") { keyIn ->
            val key = keyIn.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9_]+"), "_").take(32)
            val options = arrayOf("NEC / LG (32 bits)", "Samsung IR (32 bits)", "Sony SIRC (12 bits)")
            AlertDialog.Builder(this).setTitle("Protocolo para $key").setItems(options) { _, which ->
                val protocol = listOf("NEC", "SAMSUNG", "SONY12")[which]
                val old = current.commands[key]
                inputDialog("Código hexadecimal", "Ex.: E0E040BF", old?.code ?: "") { code ->
                    inputDialog("Frequência portadora (Hz)", "Ex.: 38000", (old?.frequency ?: 38000).toString()) { freq ->
                        AlertDialog.Builder(this).setTitle("Ordem dos bits")
                            .setItems(arrayOf("MSB — bit mais significativo primeiro", "LSB — bit menos significativo primeiro")) { _, orderIdx ->
                                val cmd = IrCommand(code = code, protocol = protocol,
                                    order = if (orderIdx == 0) "MSB" else "LSB", frequency = freq.toIntOrNull() ?: 0)
                                try { IrEngine.validate(cmd); current.commands[key] = cmd; store.save(); log("Comando configurado: $key"); render() }
                                catch (e: Exception) { showMessage("Código inválido", e.message ?: "Confira o valor") }
                            }.setNegativeButton("Cancelar", null).show()
                    }
                }
            }.setNegativeButton("Cancelar", null).show()
        }
    }
    private fun showCodes() {
        val selected = store.selected() ?: return
        val details = selected.commands.entries.sortedBy { it.key }.joinToString("\n\n") { (key, command) ->
            val count = try { IrEngine.validate(command).size } catch (_: Exception) { 0 }
            "$key\n  ${command.protocol} | ${command.frequency} Hz | ${command.order} | " +
                "${command.code ?: "RAW ($count durações)"}"
        }
        AlertDialog.Builder(this).setTitle("${selected.name} • Códigos")
            .setMessage(details.take(5000).ifBlank { "Nenhum comando cadastrado." })
            .setPositiveButton("OK", null).show()
    }
    private fun showSaveCapture(command: IrCommand) {
        AlertDialog.Builder(this).setTitle("Captura IR recebida")
            .setMessage("${command.pattern?.size ?: 0} durações detectadas. Salvar em '${store.selected()?.name ?: "perfil atual"}'? Portadora presumida em 38 kHz.")
            .setPositiveButton("SALVAR COMANDO") { _, _ ->
                inputDialog("Nome do botão", "Ex.: POWER, SOURCE, HDMI2") { rawName ->
                    val name = rawName.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9_]+"), "_").take(32)
                    val profile = store.selected()
                    if (profile == null) showMessage("Crie um perfil", "Abra Biblioteca e crie um perfil antes de salvar")
                    else {
                        profile.commands[name] = command.copy(protocol = "RAW", code = null)
                        store.save()
                        log("Aprendizado salvo no botão $name")
                        render()
                    }
                }
            }.setNegativeButton("Descartar", null).show()
    }
    private fun connectEsp32() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), RC_BLUETOOTH)
            return
        }
        try {
            val devices = bridge.paired()
            if (devices.isEmpty()) { showMessage("Nenhum dispositivo pareado", "Ative o Bluetooth e emparelhe o ESP32 NEXIR-BRIDGE nas configurações do Android."); return }
            AlertDialog.Builder(this).setTitle("Escolher ESP32 pareado")
                .setItems(devices.map { "${it.name ?: "Sem nome"} · ${it.address}" }.toTypedArray()) { _, index -> bridge.connect(devices[index]); toast("Conectando...") }
                .setNegativeButton("Cancelar", null).show()
        } catch (e: Exception) { showMessage("Bluetooth", e.message ?: "Não foi possível listar dispositivos") }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_BLUETOOTH) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) connectEsp32()
            else showMessage("Permissão necessária", "Para conectar ao ESP32 pareado, autorize Bluetooth.")
        }
    }
    private fun photograph() {
        try {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "nexir-label-${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NEXIR")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Não foi possível criar arquivo de foto")
            cameraUri = uri
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("photo", uri)
            }
            startActivityForResult(intent, RC_CAMERA)
        } catch (e: Exception) { showMessage("Câmera indisponível", "${e.message}. Se preferir, selecione uma foto da galeria.") }
    }
    private fun openDocument(code: Int, type: String) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); this.type = type }
        try { startActivityForResult(intent, code) }
        catch (e: ActivityNotFoundException) { showMessage("Seletor indisponível", "Não foi possível abrir arquivos") }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_CAMERA && resultCode != RESULT_OK) {
            cameraUri?.let { contentResolver.delete(it, null, null) }; cameraUri = null; return
        }
        if (resultCode != RESULT_OK) return
        val uri = if (requestCode == RC_CAMERA) cameraUri else data?.data
        if (uri == null) { toast("Arquivo não selecionado"); return }
        when (requestCode) {
            RC_GALLERY, RC_CAMERA -> recognizePhoto(uri)
            RC_IMPORT -> try {
                val payload = contentResolver.openInputStream(uri)?.bufferedReader()?.use { r -> r.readText().take(1_000_001) } ?: ""
                require(payload.length <= 1_000_000) { "Arquivo maior que 1 MB" }
                val count = store.import(payload)
                toast("$count perfis importados"); render()
            } catch (e: Exception) { showMessage("Erro ao importar", e.message ?: "JSON inválido") }
            RC_EXPORT -> try {
                contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { it.write(store.export()) }
                    ?: throw IllegalStateException("Não foi possível gravar o arquivo")
                toast("Biblioteca exportada")
            } catch (e: Exception) { showMessage("Erro ao exportar", e.message ?: "Erro de escrita") }
        }
    }
    private fun recognizePhoto(uri: Uri) {
        try {
            val input = InputImage.fromFilePath(this, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(input).addOnSuccessListener { result ->
                val raw = result.text.take(1800)
                val candidate = store.profiles.filter { profile ->
                    raw.contains(profile.brand, ignoreCase = true) && profile.brand.length >= 2
                }
                val proposed = candidate.firstOrNull()
                val match = Regex("(?i)(?:model|modelo|model no)[\\s:.-]*([A-Z0-9][A-Z0-9-]{3,25})").find(raw)?.groupValues?.getOrNull(1)
                val description = "Texto extraído (revise):\n${raw.ifBlank { "Nenhuma palavra detectada." }}\n\n" +
                    "Modelo encontrado: ${match ?: "não identificado"}\n" +
                    "Perfil sugerido: ${proposed?.name ?: "nenhum perfil local encontrado"}\n\n" +
                    "A leitura não confirma compatibilidade IR."
                AlertDialog.Builder(this).setTitle("Análise local da etiqueta").setMessage(description)
                    .setPositiveButton(if (proposed != null) "CONFIRMAR PERFIL" else "ENTENDI") { _, _ ->
                        if (proposed != null) { store.choose(proposed.id); tab = "CONTROLE"; render() }
                    }.setNeutralButton("VER BIBLIOTECA") { _, _ -> tab = "BIBLIOTECA"; render() }
                    .setNegativeButton("Cancelar", null).show()
                recognizer.close()
            }.addOnFailureListener { err -> showMessage("Falha no reconhecimento", err.message ?: "Não foi possível ler"); recognizer.close() }
        } catch (e: Exception) { showMessage("Imagem indisponível", e.message ?: "Não foi possível abrir a imagem") }
    }
    private fun log(message: String) {
        logs += "${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}  $message"
        while (logs.size > 40) logs.removeAt(0)
        preferences.edit().putString("history", logs.joinToString("\n")).apply()
    }
    private fun toast(value: String) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show() }
    private fun showMessage(title: String, message: String) { AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show() }
    override fun onDestroy() { bridge.disconnect(); super.onDestroy() }
}
