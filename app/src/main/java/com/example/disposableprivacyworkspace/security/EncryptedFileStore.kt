package com.example.disposableprivacyworkspace.security

import java.io.File
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.SecretKey

class EncryptedFileStore(private val key: SecretKey) {
    private val random = SecureRandom()
    fun write(file: File, plaintext: ByteArray) {
        file.parentFile?.mkdirs()
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext)
        file.outputStream().use { it.write(ByteBuffer.allocate(4).putInt(iv.size).array()); it.write(iv); it.write(ciphertext) }
    }
    fun read(file: File): ByteArray {
        val bytes=file.readBytes(); val ivLen=ByteBuffer.wrap(bytes,0,4).int; val iv=bytes.copyOfRange(4,4+ivLen); val ct=bytes.copyOfRange(4+ivLen,bytes.size)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,iv)); return cipher.doFinal(ct)
    }
}
