package com.tether.go.ssh

import android.content.Context
import android.content.SharedPreferences
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

private const val HOST_RECORDS_KEY = "host_records_v1"
private const val PINNED_HOST_KEYS_KEY = "pinned_host_keys_v1"

data class SshHostRecord(
  val id: String,
  val host: String,
  val port: Int,
  val username: String,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
  val privateKeyId: String? = null,
) {
  val displayName: String
    get() = "$username@$host:$port"

  val endpoint: SshHostEndpoint
    get() = SshHostEndpoint(host, port)
}

data class SshHostEndpoint(
  val host: String,
  val port: Int,
) {
  val normalizedHost: String = host.normalizedSshHost()

  val displayName: String
    get() = "$host:$port"

  fun sameEndpoint(other: SshHostEndpoint): Boolean =
    normalizedHost == other.normalizedHost && port == other.port
}

data class PinnedHostKey(
  val endpoint: SshHostEndpoint,
  val keyType: String,
  val publicKeyBase64: String,
  val sha256Fingerprint: String,
  val acceptedAtMillis: Long,
) {
  fun matches(keyType: String, publicKeyBase64: String): Boolean =
    this.keyType == keyType && this.publicKeyBase64 == publicKeyBase64
}

interface SshHostStore {
  fun loadHosts(): List<SshHostRecord>
  fun upsertHost(host: SshHostRecord): List<SshHostRecord>
  fun deleteHost(hostId: String): List<SshHostRecord>
  fun findPinnedHostKey(endpoint: SshHostEndpoint): PinnedHostKey?
  fun pinHostKey(hostKey: PinnedHostKey)
}

interface StringPreferenceStore {
  fun getString(key: String): String?
  fun putString(key: String, value: String)
  fun removeString(key: String)
}

open class PreferenceBackedSshHostStore(
  private val preferences: StringPreferenceStore,
) : SshHostStore {
  override fun loadHosts(): List<SshHostRecord> =
    preferences.getString(HOST_RECORDS_KEY).decodeHostRecords()

  override fun upsertHost(host: SshHostRecord): List<SshHostRecord> {
    val currentHosts = loadHosts()
    val replacedHost = currentHosts.firstOrNull { it.id == host.id }
    val nextHosts = currentHosts
      .filterNot { it.id == host.id }
      .plus(host)
      .sortedWith(
        compareBy<SshHostRecord> { it.host.lowercase(Locale.ROOT) }
          .thenBy { it.port }
          .thenBy { it.username },
      )
    preferences.putString(HOST_RECORDS_KEY, nextHosts.encodeHostRecords())

    if (
      replacedHost != null &&
      !replacedHost.endpoint.sameEndpoint(host.endpoint) &&
      nextHosts.none { it.endpoint.sameEndpoint(replacedHost.endpoint) }
    ) {
      val nextKeys = loadPinnedHostKeys()
        .filterNot { it.endpoint.sameEndpoint(replacedHost.endpoint) }
      preferences.putString(PINNED_HOST_KEYS_KEY, nextKeys.encodePinnedHostKeys())
    }

    return nextHosts
  }

  override fun deleteHost(hostId: String): List<SshHostRecord> {
    val currentHosts = loadHosts()
    val deletedHost = currentHosts.firstOrNull { it.id == hostId }
    val nextHosts = currentHosts.filterNot { it.id == hostId }
    preferences.putString(HOST_RECORDS_KEY, nextHosts.encodeHostRecords())

    if (deletedHost != null && nextHosts.none { it.endpoint.sameEndpoint(deletedHost.endpoint) }) {
      val nextKeys = loadPinnedHostKeys()
        .filterNot { it.endpoint.sameEndpoint(deletedHost.endpoint) }
      preferences.putString(PINNED_HOST_KEYS_KEY, nextKeys.encodePinnedHostKeys())
    }

    return nextHosts
  }

  override fun findPinnedHostKey(endpoint: SshHostEndpoint): PinnedHostKey? =
    loadPinnedHostKeys().firstOrNull { it.endpoint.sameEndpoint(endpoint) }

  override fun pinHostKey(hostKey: PinnedHostKey) {
    val nextKeys = loadPinnedHostKeys()
      .filterNot { it.endpoint.sameEndpoint(hostKey.endpoint) }
      .plus(hostKey)
      .sortedWith(
        compareBy<PinnedHostKey> { it.endpoint.normalizedHost }
          .thenBy { it.endpoint.port },
      )
    preferences.putString(PINNED_HOST_KEYS_KEY, nextKeys.encodePinnedHostKeys())
  }

  private fun loadPinnedHostKeys(): List<PinnedHostKey> =
    preferences.getString(PINNED_HOST_KEYS_KEY).decodePinnedHostKeys()
}

class SharedPreferencesSshHostStore(
  context: Context,
) : PreferenceBackedSshHostStore(
  SharedPreferencesStringStore(
    context.applicationContext.getSharedPreferences("tether_go_ssh_hosts", Context.MODE_PRIVATE),
  ),
)

internal class SharedPreferencesStringStore(
  private val sharedPreferences: SharedPreferences,
) : StringPreferenceStore {
  override fun getString(key: String): String? =
    sharedPreferences.getString(key, null)

  override fun putString(key: String, value: String) {
    sharedPreferences.edit().putString(key, value).apply()
  }

  override fun removeString(key: String) {
    sharedPreferences.edit().remove(key).apply()
  }
}

internal fun String.normalizedSshHost(): String =
  trim().lowercase(Locale.ROOT)

internal fun publicKeyBlobBase64(publicKeyBlob: ByteArray): String =
  Base64.getEncoder().encodeToString(publicKeyBlob)

private fun List<SshHostRecord>.encodeHostRecords(): String =
  joinToString(separator = "\n") { record ->
    encodeFields(
      record.id,
      record.host,
      record.port.toString(),
      record.username,
      record.createdAtMillis.toString(),
      record.updatedAtMillis.toString(),
      record.privateKeyId.orEmpty(),
    )
  }

private fun String?.decodeHostRecords(): List<SshHostRecord> =
  decodeLines(fieldCounts = intArrayOf(6, 7)).mapNotNull { fields ->
    val port = fields[2].toIntOrNull() ?: return@mapNotNull null
    val createdAtMillis = fields[4].toLongOrNull() ?: return@mapNotNull null
    val updatedAtMillis = fields[5].toLongOrNull() ?: return@mapNotNull null
    SshHostRecord(
      id = fields[0],
      host = fields[1],
      port = port,
      username = fields[3],
      createdAtMillis = createdAtMillis,
      updatedAtMillis = updatedAtMillis,
      privateKeyId = fields.getOrNull(6)?.takeIf { it.isNotBlank() },
    )
  }

private fun List<PinnedHostKey>.encodePinnedHostKeys(): String =
  joinToString(separator = "\n") { hostKey ->
    encodeFields(
      hostKey.endpoint.host,
      hostKey.endpoint.port.toString(),
      hostKey.keyType,
      hostKey.publicKeyBase64,
      hostKey.sha256Fingerprint,
      hostKey.acceptedAtMillis.toString(),
    )
  }

private fun String?.decodePinnedHostKeys(): List<PinnedHostKey> =
  decodeLines(fieldCounts = intArrayOf(6)).mapNotNull { fields ->
    val port = fields[1].toIntOrNull() ?: return@mapNotNull null
    val acceptedAtMillis = fields[5].toLongOrNull() ?: return@mapNotNull null
    PinnedHostKey(
      endpoint = SshHostEndpoint(
        host = fields[0],
        port = port,
      ),
      keyType = fields[2],
      publicKeyBase64 = fields[3],
      sha256Fingerprint = fields[4],
      acceptedAtMillis = acceptedAtMillis,
    )
  }

private fun String?.decodeLines(fieldCounts: IntArray): List<List<String>> {
  if (isNullOrBlank()) return emptyList()
  return lineSequence()
    .mapNotNull { line ->
      val fields = line.split('\t')
      if (fields.size !in fieldCounts) return@mapNotNull null
      fields.mapNotNull { encodedField ->
        runCatching {
          String(Base64.getUrlDecoder().decode(encodedField), StandardCharsets.UTF_8)
        }.getOrNull()
      }.takeIf { it.size == fields.size }
    }
    .toList()
}

private fun encodeFields(vararg fields: String): String =
  fields.joinToString(separator = "\t") { field ->
    Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(field.toByteArray(StandardCharsets.UTF_8))
  }
