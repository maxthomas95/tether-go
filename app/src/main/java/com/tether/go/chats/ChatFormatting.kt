package com.tether.go.chats

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MINUTE = 60L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private const val WEEK = 7 * DAY

/** Compact "5m ago" / "yesterday" / "Oct 3" label for a chat's last activity. */
fun relativeTimeLabel(
  epochSeconds: Long,
  nowEpochSeconds: Long,
  zone: ZoneId = ZoneId.systemDefault(),
): String {
  val delta = (nowEpochSeconds - epochSeconds).coerceAtLeast(0)
  return when {
    delta < MINUTE -> "just now"
    delta < HOUR -> "${delta / MINUTE}m ago"
    delta < DAY -> "${delta / HOUR}h ago"
    delta < 2 * DAY -> "yesterday"
    delta < WEEK -> "${delta / DAY}d ago"
    else -> DateTimeFormatter.ofPattern("MMM d", Locale.US)
      .format(Instant.ofEpochSecond(epochSeconds).atZone(zone))
  }
}

/** Show `/home/<user>/repo` (or `/root/...` for root) as `~/repo`. */
fun shortenHomePath(path: String, username: String): String {
  val home = if (username == "root") "/root" else "/home/$username"
  return when {
    path == home -> "~"
    path.startsWith("$home/") -> "~" + path.substring(home.length)
    else -> path
  }
}

/** Last path segment, used as the default label for a session in that dir. */
fun pathLeaf(path: String): String =
  path.trimEnd('/').substringAfterLast('/').ifEmpty { path }

/** Case-insensitive match against a chat's title, prompts, and directory. */
fun RemoteChat.matches(query: String): Boolean {
  val q = query.trim()
  if (q.isEmpty()) return true
  return listOfNotNull(title, firstPrompt, lastPrompt, workingDir, sessionId)
    .any { it.contains(q, ignoreCase = true) }
}
