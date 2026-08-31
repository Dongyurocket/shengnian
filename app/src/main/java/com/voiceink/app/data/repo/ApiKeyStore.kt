package com.voiceink.app.data.repo

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key 安全存储（§5）：AES/GCM 密钥由 Android Keystore 持有（不出 TEE/SE），
 * 密文落 SharedPreferences。两类 Key（聊天 LLM / Embedding）以 alias 区分。
 */
class ApiKeyStore(context: Context, private val alias: String) {

    private val prefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun save(apiKey: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("${alias}_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("${alias}_ct", Base64.encodeToString(ct, Base64.NO_WRAP))
            .apply()
    }

    fun load(): String? {
        val iv = prefs.getString("${alias}_iv", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        val ct = prefs.getString("${alias}_ct", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return runCatching { String(cipher.doFinal(ct), Charsets.UTF_8) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove("${alias}_iv").remove("${alias}_ct").apply()
    }
}
