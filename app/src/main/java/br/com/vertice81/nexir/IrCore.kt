package br.com.vertice81.nexir

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Timings are microseconds: ON, OFF, ON, OFF, ... */
data class IrCommand(
    val code: String? = null,
    val pattern: List<Int>? = null,
    val protocol: String = "NEC",
    val order: String = "MSB",
    val frequency: Int = 38000
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("protocol", protocol); put("order", order); put("frequency", frequency)
        if (code != null) put("code", code)
        if (pattern != null) put("pattern", JSONArray(pattern))
    }

    companion object {
        fun fromJson(json: JSONObject, inheritedFrequency: Int = 38000, inheritedProtocol: String = "NEC") = IrCommand(
            code = json.optString("code").takeIf { it.isNotBlank() },
            pattern = json.optJSONArray("pattern")?.let { a -> (0 until a.length()).map { a.getInt(it) } },
            protocol = json.optString("protocol", inheritedProtocol).uppercase(Locale.ROOT),
            order = json.optString("order", "MSB").uppercase(Locale.ROOT),
            frequency = json.optInt("frequency", inheritedFrequency)
        )
    }
}

data class IrProfile(
    val id: String,
    var name: String,
    var brand: String,
    var model: String,
    var note: String,
    val commands: MutableMap<String, IrCommand>
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("brand", brand); put("model", model); put("note", note)
        put("commands", JSONObject().also { o -> commands.forEach { (key, value) -> o.put(key, value.toJson()) } })
    }
    companion object {
        fun fromJson(json: JSONObject): IrProfile {
            val obj = json.getJSONObject("commands")
            val map = linkedMapOf<String, IrCommand>()
            val keys = obj.keys()
            while (keys.hasNext()) { val key = keys.next(); map[key] = IrCommand.fromJson(obj.getJSONObject(key)) }
            return IrProfile(
                json.getString("id"), json.optString("name", "Sem nome"),
                json.optString("brand", "Desconhecida"), json.optString("model", "Não confirmado"),
                json.optString("note", ""), map
            )
        }
    }
}

class IrRepository(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("nexir_profiles", Context.MODE_PRIVATE)
    var profiles: MutableList<IrProfile> = mutableListOf()
        private set
    var selectedId: String = ""
        private set
    init { load() }
    fun load() {
        val stored = prefs.getString("profiles", null)
        val text = stored ?: ctx.assets.open("profiles.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        profiles = (0 until arr.length()).map { IrProfile.fromJson(arr.getJSONObject(it)) }.toMutableList()
        selectedId = prefs.getString("selectedId", profiles.firstOrNull()?.id ?: "") ?: ""
        if (profiles.none { it.id == selectedId }) selectedId = profiles.firstOrNull()?.id ?: ""
    }
    fun selected(): IrProfile? = profiles.firstOrNull { it.id == selectedId }
    fun choose(id: String) { if (profiles.any { it.id == id }) { selectedId = id; persist() } }
    fun add(profile: IrProfile) { require(profiles.none { it.id == profile.id }); profiles.add(0, profile); selectedId = profile.id; persist() }
    fun save() = persist()
    fun import(text: String): Int {
        // Entire import is parsed/validated before replacing anything.
        val root = JSONArray(text)
        require(root.length() in 1..100) { "Importe entre 1 e 100 perfis" }
        val parsed = (0 until root.length()).map { IrProfile.fromJson(root.getJSONObject(it)) }
        require(parsed.sumOf { it.commands.size } <= 1500) { "Biblioteca grande demais" }
        val unique = parsed.distinctBy { it.id }
        unique.forEach { profile ->
            require(profile.name.length <= 100 && profile.id.length <= 120)
            profile.commands.values.forEach { IrEngine.validate(it) }
        }
        unique.forEach { profile -> profiles.removeAll { it.id == profile.id }; profiles.add(profile) }
        persist()
        return unique.size
    }
    fun export(): String = JSONArray().apply { profiles.forEach { put(it.toJson()) } }.toString(2)
    private fun persist() {
        prefs.edit().putString("profiles", export()).putString("selectedId", selectedId).apply()
    }
}

data class SendResult(
    val path: String,
    val carrierHz: Int,
    val pulses: Int,
    val durationMicros: Long,
    val protocol: String,
    val timestamp: Long = System.currentTimeMillis()
)

object IrEngine {
    fun validate(command: IrCommand): IntArray {
        require(command.frequency in 20000..60000) { "Frequência fora do intervalo seguro (20–60 kHz)" }
        val pulses = encode(command)
        require(pulses.isNotEmpty() && pulses.size <= 500) { "Padrão inválido ou longo demais" }
        require(pulses.all { it in 1..120000 }) { "Pulsos inválidos" }
        require(pulses.fold(0L) { a, b -> a + b } < 2_000_000L) { "Android exige transmissão menor que 2 segundos" }
        return pulses
    }

    fun encode(c: IrCommand): IntArray {
        c.pattern?.let { return it.toIntArray() }
        val bits = when(c.protocol) {
            "SONY12" -> 12
            "NEC", "SAMSUNG" -> 32
            else -> throw IllegalArgumentException("Protocolo não suportado: ${c.protocol}")
        }
        val hex = c.code?.removePrefix("0x")?.removePrefix("0X")?.trim()?.uppercase(Locale.ROOT)
            ?: throw IllegalArgumentException("Código hexadecimal ausente")
        require(hex.matches(Regex("[0-9A-F]{1,8}"))) { "Código hexadecimal inválido" }
        val num = hex.toLong(16)
        require(num ushr bits == 0L) { "Código maior que $bits bits" }
        val values = ArrayList<Int>()
        if (c.protocol == "SONY12") {
            values += listOf(2400, 600)
            for (i in 0 until bits) {
                val index = if (c.order == "LSB") i else bits - 1 - i
                values += if (((num shr index) and 1L) == 1L) 1200 else 600
                values += 600
            }
        } else {
            values += if (c.protocol == "SAMSUNG") listOf(4500, 4500) else listOf(9000, 4500)
            for (i in 0 until bits) {
                val index = if (c.order == "LSB") i else bits - 1 - i
                values += 560
                values += if (((num shr index) and 1L) == 1L) 1690 else 560
            }
            values += 560
        }
        return values.toIntArray()
    }
}

class NativeIr(private val context: Context) {
    private val service = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    val available get() = try { service?.hasIrEmitter() == true } catch (_: Exception) { false }
    fun ranges(): String {
        if (!available) return "Sem transmissor IR nativo"
        return try {
            val list = service?.carrierFrequencies
            if (list.isNullOrEmpty()) "Faixas de frequência não informadas pelo fabricante"
            else list.joinToString(" · ") { "${it.minFrequency / 1000}–${it.maxFrequency / 1000} kHz" }
        } catch (_: Exception) { "Não foi possível consultar as frequências" }
    }
    fun transmit(command: IrCommand): SendResult {
        check(available) { "Celular sem emissor IR nativo: conecte um ESP32 ou use outro aparelho" }
        val pattern = IrEngine.validate(command)
        service!!.transmit(command.frequency, pattern)
        return SendResult("IR nativo", command.frequency, pattern.size, pattern.sumOf { it.toLong() }, command.protocol)
    }
}
