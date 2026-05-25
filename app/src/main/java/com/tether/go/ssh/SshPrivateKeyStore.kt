package com.tether.go.ssh

import android.content.Context
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.Base64
import java.util.Locale
import java.util.UUID

private const val PRIVATE_KEY_METADATA_KEY = "private_key_metadata_v1"
private const val PRIVATE_KEY_MATERIAL_PREFIX = "private_key_material_v1_"
private const val PRIVATE_KEY_PREFS_NAME = "tether_go_ssh_private_keys"
private const val PRIVATE_KEYSTORE_ALIAS = "tether_go_ssh_private_keys_v1"

private val PrivateKeyBeginPattern = Regex("-----BEGIN ([A-Z0-9 ]+PRIVATE KEY)-----")
private val Base64BodyPattern = Regex("^[A-Za-z0-9+/=]+$")
private val SupportedPrivateKeyLabels = setOf(
  "OPENSSH PRIVATE KEY",
  "PRIVATE KEY",
  "ENCRYPTED PRIVATE KEY",
  "RSA PRIVATE KEY",
  "DSA PRIVATE KEY",
  "EC PRIVATE KEY",
)

data class SshPrivateKeyMetadata(
  val id: String,
  val label: String,
  val keyFormat: String,
  val hasPassphrase: Boolean,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
)

data class SshPrivateKeyMaterial(
  val id: String,
  val privateKeyData: String,
  val passphrase: String?,
)

class SshPrivateKeyImportException(message: String) : IllegalArgumentException(message)

interface SshPrivateKeyStore {
  fun loadPrivateKeys(): List<SshPrivateKeyMetadata>
  fun importPrivateKey(
    label: String,
    privateKeyData: String,
    passphrase: String?,
  ): SshPrivateKeyMetadata
  fun loadPrivateKeyMaterial(id: String): SshPrivateKeyMaterial?
  fun deletePrivateKey(id: String): List<SshPrivateKeyMetadata>
}

open class PreferenceBackedSshPrivateKeyStore(
  private val preferences: StringPreferenceStore,
  private val clock: Clock = Clock.systemUTC(),
  private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : SshPrivateKeyStore {
  override fun loadPrivateKeys(): List<SshPrivateKeyMetadata> =
    preferences.getString(PRIVATE_KEY_METADATA_KEY).decodePrivateKeyMetadata()

  override fun importPrivateKey(
    label: String,
    privateKeyData: String,
    passphrase: String?,
  ): SshPrivateKeyMetadata {
    val validatedKey = privateKeyData.normalizedSshPrivateKey()
    val trimmedLabel = label.trim().takeIf { it.isNotBlank() }
      ?: throw SshPrivateKeyImportException("Private key label is required")
    val id = idGenerator()
    val now = clock.millis()
    val metadata = SshPrivateKeyMetadata(
      id = id,
      label = trimmedLabel,
      keyFormat = validatedKey.keyFormat,
      hasPassphrase = !passphrase.isNullOrEmpty(),
      createdAtMillis = now,
      updatedAtMillis = now,
    )
    val material = SshPrivateKeyMaterial(
      id = id,
      privateKeyData = validatedKey.pemBlock,
      passphrase = passphrase?.takeIf { it.isNotEmpty() },
    )
    preferences.putString(privateKeyMaterialKey(id), material.encodePrivateKeyMaterial())
    val nextMetadata = loadPrivateKeys()
      .filterNot { it.id == id }
      .plus(metadata)
      .sortedWith(compareBy<SshPrivateKeyMetadata> { it.label.lowercase(Locale.ROOT) }.thenBy { it.id })
    preferences.putString(PRIVATE_KEY_METADATA_KEY, nextMetadata.encodePrivateKeyMetadata())
    return metadata
  }

  override fun loadPrivateKeyMaterial(id: String): SshPrivateKeyMaterial? =
    preferences.getString(privateKeyMaterialKey(id)).decodePrivateKeyMaterial()

  override fun deletePrivateKey(id: String): List<SshPrivateKeyMetadata> {
    preferences.removeString(privateKeyMaterialKey(id))
    val nextMetadata = loadPrivateKeys().filterNot { it.id == id }
    preferences.putString(PRIVATE_KEY_METADATA_KEY, nextMetadata.encodePrivateKeyMetadata())
    return nextMetadata
  }
}

class AndroidSshPrivateKeyStore(
  context: Context,
) : PreferenceBackedSshPrivateKeyStore(
  EncryptedStringPreferenceStore(
    backingStore = SharedPreferencesStringStore(
      context.applicationContext.getSharedPreferences(PRIVATE_KEY_PREFS_NAME, Context.MODE_PRIVATE),
    ),
    cipher = AndroidKeystoreAesGcmStringCipher(PRIVATE_KEYSTORE_ALIAS),
  ),
)

internal fun String.normalizedSshPrivateKey(): ValidatedSshPrivateKey {
  val normalized = replace("\r\n", "\n").replace('\r', '\n')
  val beginMatch = PrivateKeyBeginPattern.find(normalized)
    ?: throw SshPrivateKeyImportException("Private key must include a supported PEM BEGIN line")
  val keyFormat = beginMatch.groupValues[1]
  if (keyFormat !in SupportedPrivateKeyLabels) {
    throw SshPrivateKeyImportException("Unsupported private key format")
  }

  val endMarker = "-----END $keyFormat-----"
  val endIndex = normalized.indexOf(endMarker, startIndex = beginMatch.range.last + 1)
  if (endIndex < 0) {
    throw SshPrivateKeyImportException("Private key must include a matching PEM END line")
  }

  val blockEnd = endIndex + endMarker.length
  val pemBlock = normalized.substring(beginMatch.range.first, blockEnd).trim()
  val body = normalized.substring(beginMatch.range.last + 1, endIndex)
  val base64Body = body
    .lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.contains(':') }
    .onEach { line ->
      if (!Base64BodyPattern.matches(line)) {
        throw SshPrivateKeyImportException("Private key body is not valid base64")
      }
    }
    .joinToString(separator = "")

  if (base64Body.isBlank()) {
    throw SshPrivateKeyImportException("Private key body is empty")
  }

  runCatching { Base64.getDecoder().decode(base64Body) }
    .getOrElse {
      throw SshPrivateKeyImportException("Private key body is not valid base64")
    }

  return ValidatedSshPrivateKey(
    keyFormat = keyFormat,
    pemBlock = pemBlock,
  )
}

internal data class ValidatedSshPrivateKey(
  val keyFormat: String,
  val pemBlock: String,
)

private fun privateKeyMaterialKey(id: String): String =
  "$PRIVATE_KEY_MATERIAL_PREFIX$id"

private fun List<SshPrivateKeyMetadata>.encodePrivateKeyMetadata(): String =
  joinToString(separator = "\n") { metadata ->
    encodePrivateKeyFields(
      metadata.id,
      metadata.label,
      metadata.keyFormat,
      metadata.hasPassphrase.toString(),
      metadata.createdAtMillis.toString(),
      metadata.updatedAtMillis.toString(),
    )
  }

private fun String?.decodePrivateKeyMetadata(): List<SshPrivateKeyMetadata> =
  decodePrivateKeyLines(fieldCount = 6).mapNotNull { fields ->
    val createdAtMillis = fields[4].toLongOrNull() ?: return@mapNotNull null
    val updatedAtMillis = fields[5].toLongOrNull() ?: return@mapNotNull null
    SshPrivateKeyMetadata(
      id = fields[0],
      label = fields[1],
      keyFormat = fields[2],
      hasPassphrase = fields[3].toBooleanStrictOrNull() ?: return@mapNotNull null,
      createdAtMillis = createdAtMillis,
      updatedAtMillis = updatedAtMillis,
    )
  }

private fun SshPrivateKeyMaterial.encodePrivateKeyMaterial(): String =
  encodePrivateKeyFields(
    id,
    privateKeyData,
    passphrase.orEmpty(),
  )

private fun String?.decodePrivateKeyMaterial(): SshPrivateKeyMaterial? {
  val fields = decodePrivateKeyLines(fieldCount = 3).singleOrNull() ?: return null
  return SshPrivateKeyMaterial(
    id = fields[0],
    privateKeyData = fields[1],
    passphrase = fields[2].takeIf { it.isNotEmpty() },
  )
}

private fun String?.decodePrivateKeyLines(fieldCount: Int): List<List<String>> {
  if (isNullOrBlank()) return emptyList()
  return lineSequence()
    .mapNotNull { line ->
      val fields = line.split('\t')
      if (fields.size != fieldCount) return@mapNotNull null
      fields.mapNotNull { encodedField ->
        runCatching {
          String(Base64.getUrlDecoder().decode(encodedField), StandardCharsets.UTF_8)
        }.getOrNull()
      }.takeIf { it.size == fieldCount }
    }
    .toList()
}

private fun encodePrivateKeyFields(vararg fields: String): String =
  fields.joinToString(separator = "\t") { field ->
    Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(field.toByteArray(StandardCharsets.UTF_8))
  }
