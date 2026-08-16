package com.example.disposableprivacyworkspace.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SecureSessionKeyStore {
    private val ks: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    fun getOrCreate(alias: String): SecretKey {
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setUserAuthenticationRequired(false).build())
        return generator.generateKey()
    }
    fun delete(alias: String) { if (ks.containsAlias(alias)) ks.deleteEntry(alias) }
    fun exists(alias: String): Boolean = ks.containsAlias(alias)
}
