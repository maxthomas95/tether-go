package com.tether.go.session

import android.content.Context
import com.tether.go.cli.CliToolId
import com.tether.go.ssh.SharedPreferencesStringStore
import com.tether.go.ssh.StringPreferenceStore
import java.nio.charset.StandardCharsets
import java.util.Base64

private const val SESSION_RECORDS_KEY = "session_records_v1"
private const val SESSIONS_PREFS_NAME = "tether_go_sessions"

/**
 * Durable storage for phone-owned session metadata. Uses the same
 * tab-separated, base64url-per-field encoding as the SSH host store so it stays
 * dependency-free and unit-testable on the JVM (no Android JSON).
 *
 * No secrets are stored here: only a `privateKeyId` reference, never key bytes,
 * passphrases, or passwords.
 */
interface SessionStore {
  fun loadSessions(): List<Session>
  fun upsertSession(session: Session): List<Session>
  fun deleteSession(sessionId: String): List<Session>
}

open class PreferenceBackedSessionStore(
  private val preferences: StringPreferenceStore,
) : SessionStore {
  override fun loadSessions(): List<Session> =
    preferences.getString(SESSION_RECORDS_KEY).decodeSessions()

  override fun upsertSession(session: Session): List<Session> {
    val next = loadSessions()
      .filterNot { it.id == session.id }
      .plus(session)
      .sortedByDescending { it.updatedAtMillis }
    preferences.putString(SESSION_RECORDS_KEY, next.encodeSessions())
    return next
  }

  override fun deleteSession(sessionId: String): List<Session> {
    val next = loadSessions().filterNot { it.id == sessionId }
    preferences.putString(SESSION_RECORDS_KEY, next.encodeSessions())
    return next
  }
}

class SharedPreferencesSessionStore(
  context: Context,
) : PreferenceBackedSessionStore(
  SharedPreferencesStringStore(
    context.applicationContext.getSharedPreferences(SESSIONS_PREFS_NAME, Context.MODE_PRIVATE),
  ),
)

// ── Encoding ──────────────────────────────────────────────────────────────

private const val FIELD_COUNT = 14
private const val LIST_SEPARATOR = "\n"
private const val KV_SEPARATOR = "="

private fun List<Session>.encodeSessions(): String =
  joinToString(separator = "\n") { s ->
    encodeFields(
      s.id,
      s.label,
      s.hostId.orEmpty(),
      s.host,
      s.port.toString(),
      s.username,
      s.cliTool.id,
      s.customBinary.orEmpty(),
      s.workingDir.orEmpty(),
      s.flags.joinToString(LIST_SEPARATOR),
      s.env.entries.joinToString(LIST_SEPARATOR) { "${it.key}$KV_SEPARATOR${it.value}" },
      s.privateKeyId.orEmpty(),
      s.createdAtMillis.toString(),
      s.updatedAtMillis.toString(),
    )
  }

private fun String?.decodeSessions(): List<Session> {
  if (isNullOrBlank()) return emptyList()
  return lineSequence().mapNotNull { line ->
    val fields = line.split('\t')
    if (fields.size != FIELD_COUNT) return@mapNotNull null
    val decoded = fields.map { encoded ->
      runCatching {
        String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
      }.getOrNull() ?: return@mapNotNull null
    }
    val port = decoded[4].toIntOrNull() ?: return@mapNotNull null
    val createdAt = decoded[12].toLongOrNull() ?: return@mapNotNull null
    val updatedAt = decoded[13].toLongOrNull() ?: return@mapNotNull null
    Session(
      id = decoded[0],
      label = decoded[1],
      hostId = decoded[2].takeIf { it.isNotBlank() },
      host = decoded[3],
      port = port,
      username = decoded[5],
      cliTool = CliToolId.fromId(decoded[6]),
      customBinary = decoded[7].takeIf { it.isNotBlank() },
      workingDir = decoded[8].takeIf { it.isNotBlank() },
      flags = decoded[9].splitToListOrEmpty(),
      env = decoded[10].decodeEnv(),
      privateKeyId = decoded[11].takeIf { it.isNotBlank() },
      createdAtMillis = createdAt,
      updatedAtMillis = updatedAt,
    )
  }.toList()
}

private fun String.splitToListOrEmpty(): List<String> =
  if (isEmpty()) emptyList() else split(LIST_SEPARATOR).filter { it.isNotEmpty() }

private fun String.decodeEnv(): Map<String, String> {
  if (isEmpty()) return emptyMap()
  return split(LIST_SEPARATOR).mapNotNull { entry ->
    val idx = entry.indexOf(KV_SEPARATOR)
    if (idx <= 0) return@mapNotNull null
    entry.substring(0, idx) to entry.substring(idx + 1)
  }.toMap()
}

private fun encodeFields(vararg fields: String): String =
  fields.joinToString(separator = "\t") { field ->
    Base64.getUrlEncoder().withoutPadding().encodeToString(field.toByteArray(StandardCharsets.UTF_8))
  }
