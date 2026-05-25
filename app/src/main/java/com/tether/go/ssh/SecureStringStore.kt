package com.tether.go.ssh

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
private const val AES_GCM_TAG_BITS = 128
private const val CIPHER_TEXT_VERSION = "v1"
private const val KEY_AUTH_VALIDITY_SECONDS = 300

class SecureStorageException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)

interface StringCipher {
  fun encrypt(plaintext: String): String
  fun decrypt(ciphertext: String): String
}

class EncryptedStringPreferenceStore(
  private val backingStore: StringPreferenceStore,
  private val cipher: StringCipher,
) : StringPreferenceStore {
  override fun getString(key: String): String? {
    val encryptedValue = backingStore.getString(key) ?: return null
    return runCatching { cipher.decrypt(encryptedValue) }
      .getOrElse { error ->
        throw SecureStorageException("Encrypted preference value could not be read", error)
      }
  }

  override fun putString(key: String, value: String) {
    val encryptedValue = runCatching { cipher.encrypt(value) }
      .getOrElse { error ->
        throw SecureStorageException("Encrypted preference value could not be written", error)
      }
    backingStore.putString(key, encryptedValue)
  }

  override fun removeString(key: String) {
    backingStore.removeString(key)
  }
}

class AndroidKeystoreAesGcmStringCipher(
  private val keyAlias: String,
) : StringCipher {
  override fun encrypt(plaintext: String): String {
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
    val iv = cipher.iv
    val encryptedBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
    return listOf(
      CIPHER_TEXT_VERSION,
      iv.encodeUrlBase64(),
      encryptedBytes.encodeUrlBase64(),
    ).joinToString(separator = ":")
  }

  override fun decrypt(ciphertext: String): String {
    val fields = ciphertext.split(':')
    require(fields.size == 3 && fields[0] == CIPHER_TEXT_VERSION) {
      "Unsupported encrypted value format"
    }
    val iv = fields[1].decodeUrlBase64()
    val encryptedBytes = fields[2].decodeUrlBase64()
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
    cipher.init(
      Cipher.DECRYPT_MODE,
      getOrCreateSecretKey(),
      GCMParameterSpec(AES_GCM_TAG_BITS, iv),
    )
    return String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8)
  }

  private fun getOrCreateSecretKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existingKey = keyStore.getKey(keyAlias, null) as? SecretKey
    if (existingKey != null) return existingKey

    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    keyGenerator.init(
      KeyGenParameterSpec.Builder(
        keyAlias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setUserAuthenticationRequired(true)
        .applyUserAuthenticationParameters()
        .build(),
    )
    return keyGenerator.generateKey()
  }
}

private fun KeyGenParameterSpec.Builder.applyUserAuthenticationParameters(): KeyGenParameterSpec.Builder {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    setUserAuthenticationParameters(
      KEY_AUTH_VALIDITY_SECONDS,
      KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
    )
  } else {
    @Suppress("DEPRECATION")
    setUserAuthenticationValidityDurationSeconds(KEY_AUTH_VALIDITY_SECONDS)
  }
  return this
}

private fun ByteArray.encodeUrlBase64(): String =
  Base64.getUrlEncoder().withoutPadding().encodeToString(this)

private fun String.decodeUrlBase64(): ByteArray =
  Base64.getUrlDecoder().decode(this)
