package dev.metis.agent.data.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface FieldCipher {
    fun encrypt(value: String, binding: String): ByteArray
    fun decrypt(value: ByteArray, binding: String): String
}

/** Version 1 envelope: version byte, fresh 12-byte nonce, AES-GCM ciphertext/tag.
 * AAD binds content to table/row/column, preventing ciphertext swaps between records.
 * Only writes may create a key. Missing keys on reads fail closed.
 */
class KeystoreFieldCipher(private val alias: String = "metis.personal.fields.v1") : FieldCipher {
    override fun encrypt(value: String, binding: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        cipher.updateAAD(binding.toByteArray(Charsets.UTF_8))
        return byteArrayOf(VERSION) + cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    }

    override fun decrypt(value: ByteArray, binding: String): String {
        require(value.size >= MIN_ENVELOPE_SIZE && value[0] == VERSION) { "Unsupported encrypted field" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val key = requireNotNull(loadKey()) { "Personal storage key unavailable" }
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, value.copyOfRange(1, NONCE_END)))
        cipher.updateAAD(binding.toByteArray(Charsets.UTF_8))
        return Charsets.UTF_8.decode(ByteBuffer.wrap(cipher.doFinal(value.copyOfRange(NONCE_END, value.size))))
            .toString()
    }

    private fun loadKey(): SecretKey? = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        .getKey(alias, null) as SecretKey?

    @Synchronized
    private fun encryptionKey(): SecretKey = loadKey() ?: KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore",
    ).apply {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS).build(),
        )
    }.generateKey()

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val NONCE_END = 13
        const val MIN_ENVELOPE_SIZE = 29
    }
}
