package com.example.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed Cyber Vault for encrypting the user's Gemini API key at rest.
 *
 * Keystore initialization is strictly lazy so `Application.onCreate()` never stalls
 * on the main thread when launching on emulators or older Android 6+ devices.
 */
class ApiKeyVault(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "livedub_cyber_vault_aes256_v2"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
        private const val PREFIX_KS = "v2:ks:"
        private const val PREFIX_FB = "v2:fb:"
        private const val FALLBACK_SALT_PREFS = "livedub_vault_meta_v2"
        private const val KEY_INSTALL_SALT = "install_salt_b64"
    }

    private val secureRandom = SecureRandom()

    @Volatile
    private var keystoreChecked: Boolean = false

    @Volatile
    private var hardwareKeystoreAvailable: Boolean = false

    val isHardwareKeystoreActive: Boolean
        get() {
            if (!keystoreChecked) {
                hardwareKeystoreAvailable = tryCheckKeystorePresent()
                keystoreChecked = true
            }
            return hardwareKeystoreAvailable
        }

    fun encrypt(plainApiKey: String): String {
        val clean = plainApiKey.trim()
        if (clean.isEmpty()) return ""
        val plainBytes = clean.toByteArray(StandardCharsets.UTF_8)
        return try {
            val ksKey = tryEnsureKeystoreKey()
            if (ksKey != null) {
                hardwareKeystoreAvailable = true
                keystoreChecked = true
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, ksKey)
                val iv = cipher.iv
                val encrypted = cipher.doFinal(plainBytes)
                PREFIX_KS +
                    Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                    Base64.encodeToString(encrypted, Base64.NO_WRAP)
            } else {
                encryptWithFallbackKey(plainBytes)
            }
        } catch (_: Throwable) {
            encryptWithFallbackKey(plainBytes)
        } finally {
            plainBytes.fill(0)
        }
    }

    fun decrypt(vaultBlob: String?): String {
        if (vaultBlob.isNullOrBlank()) return ""
        return try {
            when {
                vaultBlob.startsWith(PREFIX_KS) -> {
                    val parts = vaultBlob.removePrefix(PREFIX_KS).split(":")
                    if (parts.size != 2) return ""
                    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                    val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
                    val ksKey = tryEnsureKeystoreKey() ?: return ""
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, ksKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                    val plainBytes = cipher.doFinal(cipherBytes)
                    try {
                        String(plainBytes, StandardCharsets.UTF_8).trim()
                    } finally {
                        plainBytes.fill(0)
                    }
                }
                vaultBlob.startsWith(PREFIX_FB) -> {
                    val parts = vaultBlob.removePrefix(PREFIX_FB).split(":")
                    if (parts.size != 2) return ""
                    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                    val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
                    val key = deriveFallbackKey()
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                    val plainBytes = cipher.doFinal(cipherBytes)
                    try {
                        String(plainBytes, StandardCharsets.UTF_8).trim()
                    } finally {
                        plainBytes.fill(0)
                    }
                }
                else -> ""
            }
        } catch (_: Throwable) {
            ""
        }
    }

    fun isEncryptedBlob(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return value.startsWith(PREFIX_KS) || value.startsWith(PREFIX_FB)
    }

    private fun encryptWithFallbackKey(plainBytes: ByteArray): String {
        val key = deriveFallbackKey()
        val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plainBytes)
        return PREFIX_FB +
            Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun tryCheckKeystorePresent(): Boolean {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun tryEnsureKeystoreKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    builder.setIsStrongBoxBacked(false)
                } catch (_: Throwable) {
                }
            }
            keyGen.init(builder.build())
            keyGen.generateKey()
        } catch (_: Throwable) {
            null
        }
    }

    private fun deriveFallbackKey(): SecretKey {
        val metaPrefs = context.applicationContext.getSharedPreferences(FALLBACK_SALT_PREFS, Context.MODE_PRIVATE)
        var saltB64 = metaPrefs.getString(KEY_INSTALL_SALT, null)
        if (saltB64.isNullOrBlank()) {
            val salt = ByteArray(32).also { secureRandom.nextBytes(it) }
            saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            metaPrefs.edit().putString(KEY_INSTALL_SALT, saltB64).apply()
        }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(context.applicationContext.packageName.toByteArray(StandardCharsets.UTF_8))
        digest.update(":livedub-aes256-gcm:".toByteArray(StandardCharsets.UTF_8))
        digest.update(Base64.decode(saltB64, Base64.NO_WRAP))
        val keyMaterial = digest.digest()
        return SecretKeySpec(keyMaterial, "AES")
    }
}
