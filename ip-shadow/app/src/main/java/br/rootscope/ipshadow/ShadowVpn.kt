package br.rootscope.ipshadow

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ShadowApp : Application() {
    override fun onCreate() { super.onCreate(); ShadowVpn.initialize(this) }
}
object ShadowVpn {
    lateinit var backend: GoBackend
    val tunnel = object : Tunnel {
        override fun getName() = "ipshadow"
        override fun onStateChange(newState: Tunnel.State) {}
    }
    fun initialize(context: Context) {
        if (::backend.isInitialized) return
        backend = GoBackend(context.applicationContext)
        GoBackend.setAlwaysOnCallback {
            Thread {
                runCatching {
                    if (SecureProfile.exists(context))
                        backend.setState(tunnel, Tunnel.State.UP, SecureProfile.load(context))
                }
            }.start()
        }
    }
}
object SecureProfile {
    private const val name = "profile.aes"
    private const val alias = "ipshadow.profile.key"
    fun exists(context: Context) = context.getFileStreamPath(name).exists()
    fun remove(context: Context) = context.deleteFile(name)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val old = store.getKey(alias, null) as? SecretKey
        if (old != null) return old
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return generator.generateKey()
    }
    fun save(context: Context, content: ByteArray) {
        try {
            val original = content.toString(Charsets.UTF_8)
            val amended = if (Regex("(?im)^\\s*\\[Peer]\\s*$").findAll(original).count() == 1 &&
                original.contains("0.0.0.0/0") && !original.contains("::/0"))
                original.replace(Regex("(?im)^(\\s*AllowedIPs\\s*=\\s*[^\\r\\n#]*0\\.0\\.0\\.0/0[^\\r\\n#]*)"), "$1, ::/0")
            else original
            val config = Config.parse(ByteArrayInputStream(amended.toByteArray(Charsets.UTF_8)))
            val canonical = config.toWgQuickString()
            require(canonical.contains("0.0.0.0/0") && canonical.contains("::/0")) {
                "Perfil deve rotear todo IPv4 e IPv6 (0.0.0.0/0, ::/0)"
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val encrypted = cipher.iv + cipher.doFinal(canonical.toByteArray(Charsets.UTF_8))
            val atomic = AtomicFile(context.getFileStreamPath(name))
            val output = atomic.startWrite()
            try { output.write(encrypted); atomic.finishWrite(output) }
            catch (e: Exception) { atomic.failWrite(output); throw e }
        } finally { content.fill(0) }
    }
    fun load(context: Context): Config {
        val bytes = context.openFileInput(name).use { it.readBytes() }
        require(bytes.size > 28) { "Perfil criptografado inválido" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val plain = cipher.doFinal(bytes.copyOfRange(12, bytes.size))
        return try { Config.parse(ByteArrayInputStream(plain)) } finally { plain.fill(0) }
    }
}