package com.tether.go.session

import com.tether.go.cli.CliToolId
import com.tether.go.cli.LaunchProfile

/**
 * Lifecycle status of a phone-owned session, surfaced in the UI as a status
 * dot. v0.1 distinguishes Connecting / Running / Disconnected / Error directly
 * from the SSH transport; Waiting and Idle are reserved for the future passive
 * status side-channel.
 */
enum class SessionStatus {
  Connecting,
  Running,
  Waiting,
  Idle,
  Disconnected,
  Error,
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
