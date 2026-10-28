package com.tether.go.session

import com.tether.go.cli.CliToolId
import com.tether.go.cli.LaunchProfile

/**
 * Lifecycle status of a phone-owned session, surfaced in the UI as a status
 * dot. Connecting / Disconnected / Error come from the SSH transport; while
 * connected, the passive status side-channel distinguishes Running / Waiting /
 * Idle without changing the terminal stream.
 */
enum class SessionStatus {
  Connecting,
  Running,
  Waiting,
  Idle,
  Disconnected,
  Error;

  /** Includes connections being established and connected sessions at rest. */
  val isLive: Boolean
    get() = when (this) {
      Connecting, Running, Waiting, Idle -> true
      Disconnected, Error -> false
    }

  /** Desktop's attention filter includes Waiting only, not errors or saved sessions. */
  val needsAttention: Boolean
    get() = when (this) {
      Waiting -> true
      Connecting, Running, Idle, Disconnected, Error -> false
    }
}

/**
 * Persisted metadata for a phone-owned session. The live SSH connection and
 * terminal buffer are runtime-only state held by [SessionManager]; this record
 * is the durable part so the session list survives navigation and app restarts
 * and can be reconnected.
 */
data class Session(
  val id: String,
  val label: String,
  val hostId: String?,
  val host: String,
  val port: Int,
  val username: String,
  val cliTool: CliToolId,
  val customBinary: String?,
  val workingDir: String?,
  val flags: List<String>,
  val env: Map<String, String>,
  val privateKeyId: String?,
  val createdAtMillis: Long,
  val updatedAtMillis: Long,
  val notificationsMuted: Boolean = false,
) {
  val endpointLabel: String get() = "$username@$host:$port"

  fun launchProfile(): LaunchProfile = LaunchProfile(
    cliTool = cliTool,
    customBinary = customBinary,
    workingDir = workingDir,
    flags = flags,
    env = env,
  )
}
