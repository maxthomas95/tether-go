package com.tether.go.settings

import android.content.Context
import com.tether.go.cli.CliToolId
import com.tether.go.cli.GuidedLaunchFlags
import com.tether.go.cli.GuidedLaunchSelection
import com.tether.go.ssh.SharedPreferencesStringStore
import com.tether.go.ssh.StringPreferenceStore

interface LaunchDefaultsStore {
  fun load(tool: CliToolId): GuidedLaunchSelection
  fun save(tool: CliToolId, selection: GuidedLaunchSelection): GuidedLaunchSelection
  fun clear(tool: CliToolId)
}

/** Safe, recoverable messages never include the stored record or a backing-store exception. */
class LaunchDefaultsException(message: String) : IllegalStateException(message)

/**
 * One versioned record per supported CLI, with only bounded native identifiers.
 * No authentication, environment, raw arguments, or resume IDs belong here.
 * Invalid/unknown records are preserved until the user explicitly clears them.
 */
open class PreferenceBackedLaunchDefaultsStore(
  private val preferences: StringPreferenceStore,
) : LaunchDefaultsStore {
  override fun load(tool: CliToolId): GuidedLaunchSelection {
    if (tool !in GuidedLaunchFlags.supportedTools) return GuidedLaunchSelection()
    val record = try {
      preferences.getString(key(tool))
    } catch (_: Exception) {
      throw LaunchDefaultsException("Saved defaults could not be read. Retry, clear defaults in Settings, or use no defaults for this session.")
    } ?: return GuidedLaunchSelection()
    return try {
      // The shared validator permits only ASCII identifiers, so tabs need no escaping.
      require(record.length <= MAX_RECORD_LENGTH)
      val fields = record.split('\t')
      require(fields.size == 6 && fields[0] == "1" && fields[1] == tool.id)
      GuidedLaunchFlags.normalize(tool, GuidedLaunchSelection(fields[2], fields[3], fields[4], fields[5]))
    } catch (_: IllegalArgumentException) {
      throw LaunchDefaultsException("Saved defaults are invalid or use an unsupported format. Clear defaults in Settings, or use no defaults for this session. The saved record has not been changed.")
    }
  }

  override fun save(tool: CliToolId, selection: GuidedLaunchSelection): GuidedLaunchSelection {
    requireSupported(tool)
    val normalized = GuidedLaunchFlags.normalize(tool, selection)
    // Never replace an unreadable or future-version record, even with an empty save.
    load(tool)
    if (normalized == GuidedLaunchSelection()) {
      clear(tool)
    } else {
      val record = listOf("1", tool.id, normalized.model, normalized.agent,
        normalized.nativeProfile, normalized.reasoningEffort).joinToString("\t")
      try {
        preferences.putString(key(tool), record)
      } catch (_: Exception) {
        throw LaunchDefaultsException("Defaults could not be saved. Your edits are still here; retry or cancel.")
      }
    }
    return normalized
  }

  override fun clear(tool: CliToolId) {
    requireSupported(tool)
    try {
      preferences.removeString(key(tool))
    } catch (_: Exception) {
      throw LaunchDefaultsException("Defaults could not be cleared. Retry before assuming they were removed.")
    }
  }

  private fun requireSupported(tool: CliToolId) {
    require(tool in GuidedLaunchFlags.supportedTools) { "This CLI has no managed launch defaults." }
  }

  private fun key(tool: CliToolId): String = "guided_launch_${tool.id}_v1"

  private companion object {
    const val MAX_RECORD_LENGTH = 600
  }
}

/** SharedPreferences applies each whole record in memory and flushes it asynchronously. */
class SharedPreferencesLaunchDefaultsStore(context: Context) : PreferenceBackedLaunchDefaultsStore(
  SharedPreferencesStringStore(
    context.applicationContext.getSharedPreferences("tether_go_launch_defaults", Context.MODE_PRIVATE),
  ),
)
