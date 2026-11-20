package com.tmc.vpn.servers

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Each profile is encrypted with a non-exportable Android Keystore AES key. */
class SecureProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tmc_profiles_secure", Context.MODE_PRIVATE)
    private val alias = "tmc_vpn_profiles_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
    fun save(id: String, raw: String) {
        require(raw.length <= 65536)
        val parsed = Config.parse(ByteArrayInputStream(raw.toByteArray(StandardCharsets.UTF_8)))
        require(parsed.peers.isNotEmpty()) { "Perfil sin peers" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(raw.toByteArray(StandardCharsets.UTF_8))
        val payload = cipher.iv + encrypted
        check(prefs.edit().putString(id, Base64.encodeToString(payload, Base64.NO_WRAP)).commit()) { "No se pudo guardar el perfil" }
    }
    fun load(id: String): String {
        val payload = Base64.decode(requireNotNull(prefs.getString(id, null)) { "Perfil no disponible" }, Base64.NO_WRAP)
        require(payload.size > 28) { "Perfil cifrado inválido" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        return String(cipher.doFinal(payload.copyOfRange(12, payload.size)), StandardCharsets.UTF_8)
    }
    fun contains(id: String): Boolean = prefs.contains(id)
    fun delete(id: String) { prefs.edit().remove(id).commit() }
}
