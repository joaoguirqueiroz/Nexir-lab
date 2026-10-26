package br.com.vertice81.nexir

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import kotlin.concurrent.thread

/** Bluetooth Classic SPP. Only paired devices; no silent scanning or unauthorized connections. */
class BluetoothBridge(private val context: Context, private val events: (String) -> Unit, private val capture: (IrCommand) -> Unit) {
    private val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var busy = false
    @Volatile var connectedName: String? = null
        private set
    fun permitted() = Build.VERSION.SDK_INT < 31 ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    fun paired(): List<BluetoothDevice> {
        if (!permitted()) throw SecurityException("Permita o acesso Bluetooth")
        val a = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        if (!a.isEnabled) return emptyList()
        return a.bondedDevices.sortedBy { it.name ?: "" }
    }
    fun connect(device: BluetoothDevice) {
        if (busy) { events("Já existe uma conexão em andamento"); return }
        if (!permitted()) { events("Autorize a permissão Bluetooth"); return }
        disconnect()
        busy = true
        thread(name="nexir-spp") {
            var opened: BluetoothSocket? = null
            try {
                opened = device.createRfcommSocketToServiceRecord(spp)
                opened.connect()
                socket = opened
                connectedName = device.name ?: device.address
                events("ESP32 conectado: $connectedName")
                val reader = BufferedReader(InputStreamReader(opened.inputStream, Charsets.UTF_8))
                while (socket === opened) {
                    val line = reader.readLine() ?: break
                    if (line.length > 8000) { events("Mensagem externa excessivamente longa ignorada"); continue }
                    if (line.startsWith("{")) {
                        try {
                            val json = JSONObject(line)
                            if (json.optString("type") == "ir") {
                                val parsed = IrCommand.fromJson(json, 38000, "RAW")
                                IrEngine.validate(parsed)
                                capture(parsed)
                            } else events("Recebido JSON não suportado")
                        } catch (e: Exception) { events("Captura rejeitada: ${e.message}") }
                    } else events("ESP32: $line")
                }
            } catch (e: Exception) { events("Conexão Bluetooth: ${e.message}") }
            finally {
                if (socket === opened) { socket = null; connectedName = null; events("ESP32 desconectado") }
                try { opened?.close() } catch (_: Exception) {}
                busy = false
            }
        }
    }
    fun captureNext() = send("CAPTURE\n")
    fun transmit(command: IrCommand): SendResult {
        val pulses = IrEngine.validate(command)
        check(connectedName != null) { "Conecte um ESP32 primeiro" }
        val message = "TX|${command.frequency}|${pulses.joinToString(",")}\n"
        require(message.length <= 4000) { "Quadro maior que o limite do ESP32" }
        send(message)
        return SendResult("ESP32 · solicitado (aguardando confirmação)", command.frequency,
            pulses.size, pulses.sumOf { it.toLong() }, command.protocol)
    }
    private fun send(message: String) {
        val active = socket ?: throw IllegalStateException("ESP32 não conectado")
        thread(name="nexir-spp-write") {
            try {
                synchronized(active) { active.outputStream.write(message.toByteArray(Charsets.US_ASCII)); active.outputStream.flush() }
            } catch (e: Exception) { events("Falha no envio: ${e.message}") }
        }
    }
    fun disconnect() {
        val prev = socket
        socket = null
        connectedName = null
        try { prev?.close() } catch (_: Exception) {}
    }
}
