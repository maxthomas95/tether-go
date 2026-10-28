package com.tether.go.settings

import com.tether.go.cli.CliToolId
import com.tether.go.cli.GuidedLaunchFlags
import com.tether.go.cli.GuidedLaunchSelection
import com.tether.go.ssh.StringPreferenceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchDefaultsStoreTest {
  @Test fun missingRecordsLeaveEveryToolEmptyWithoutWriting() {
    val backing = MapStore()
    val store = PreferenceBackedLaunchDefaultsStore(backing)
    CliToolId.entries.forEach { assertEquals(GuidedLaunchSelection(), store.load(it)) }
    assertEquals(0, backing.writes)
    assertEquals(0, backing.removes)
    assertTrue(backing.values.isEmpty())
  }

  @Test fun roundTripsEverySupportedFieldInOneVersionedRecordPerTool() {
    val backing = MapStore()
    val store = PreferenceBackedLaunchDefaultsStore(backing)
    selections.forEach { (tool, selection) ->
      assertEquals(selection, store.save(tool, selection))
      assertEquals(selection, PreferenceBackedLaunchDefaultsStore(backing).load(tool))
      val record = backing.values.getValue(key(tool)).split('\t')
      assertEquals(listOf("1", tool.id, selection.model, selection.agent, selection.nativeProfile, selection.reasoningEffort), record)
    }
    assertEquals(3, backing.values.size)
    assertEquals(3, backing.writes)
    assertEquals(0, backing.removes)
    selections.forEach { (tool, selection) -> assertEquals(selection, store.load(tool)) }
  }

  @Test fun savesNormalizedIdentifiersWithTheSameSharedValidationAsLaunch() {
    val store = PreferenceBackedLaunchDefaultsStore(MapStore())
    selections.forEach { (tool, selection) ->
      val padded = selection.copy(model = " ${selection.model} ", agent = " ${selection.agent} ",
        nativeProfile = " ${selection.nativeProfile} ", reasoningEffort = " ${selection.reasoningEffort} ")
      assertEquals(GuidedLaunchFlags.normalize(tool, padded), store.save(tool, padded))
      assertEquals(selection, store.load(tool))
    }
  }

  @Test fun updatesAndClearsOnlyTheSelectedTool() {
    val backing = MapStore()
    val store = PreferenceBackedLaunchDefaultsStore(backing)
    selections.forEach { (tool, selection) -> store.save(tool, selection) }
    val others = backing.values.filterKeys { it != key(CliToolId.CLAUDE) }
    store.save(CliToolId.CLAUDE, GuidedLaunchSelection(model = "new"))
    assertEquals(GuidedLaunchSelection(model = "new"), store.load(CliToolId.CLAUDE))
    store.clear(CliToolId.CLAUDE)
    assertEquals(others, backing.values)
    assertEquals(GuidedLaunchSelection(), store.load(CliToolId.CLAUDE))
    store.clear(CliToolId.CLAUDE)
    assertEquals(others, backing.values)
  }

  @Test fun allEmptyOrWhitespaceSaveClearsOneRecordWithoutWritingABlankRecord() {
    listOf(GuidedLaunchSelection(), GuidedLaunchSelection(" ", "\t", "\n", " ")).forEach { empty ->
      val backing = MapStore()
      val store = PreferenceBackedLaunchDefaultsStore(backing)
      selections.forEach { (tool, selection) -> store.save(tool, selection) }
      val others = backing.values.filterKeys { it != key(CliToolId.CODEX) }
      assertEquals(GuidedLaunchSelection(), store.save(CliToolId.CODEX, empty))
      assertEquals(others, backing.values)
      assertEquals(3, backing.writes)
      assertEquals(1, backing.removes)
    }
  }

  @Test fun invalidInputNeverReplacesThePreviousRecord() {
    val invalid = listOf("two words", "\$HOME", "x;cmd", "-option", "'quoted'", "a\tb", "a\nb", "x".repeat(129), "日本語")
    selections.forEach { (tool, valid) -> invalid.forEach { value ->
      val backing = MapStore()
      val store = PreferenceBackedLaunchDefaultsStore(backing)
      store.save(tool, valid)
      val before = backing.values.toMap()
      assertThrows(IllegalArgumentException::class.java) { store.save(tool, valid.copy(model = value)) }
      assertEquals(before, backing.values)
      assertEquals(1, backing.writes)
      assertEquals(0, backing.removes)
    } }
  }

  @Test fun unsupportedFieldsAreRejectedBeforeAnyPreferenceAccess() {
    val cases = listOf(
      CliToolId.CLAUDE to GuidedLaunchSelection(agent = "build"),
      CliToolId.CLAUDE to GuidedLaunchSelection(nativeProfile = "work"),
      CliToolId.CLAUDE to GuidedLaunchSelection(reasoningEffort = "high"),
      CliToolId.OPENCODE to GuidedLaunchSelection(nativeProfile = "work"),
      CliToolId.OPENCODE to GuidedLaunchSelection(reasoningEffort = "high"),
      CliToolId.CODEX to GuidedLaunchSelection(agent = "build"),
      CliToolId.CODEX to GuidedLaunchSelection(model = "sonnet[1m]"),
    )
    cases.forEach { (tool, selection) ->
      val backing = MapStore()
      assertThrows(IllegalArgumentException::class.java) {
        PreferenceBackedLaunchDefaultsStore(backing).save(tool, selection)
      }
      assertEquals(0, backing.reads)
      assertEquals(0, backing.writes)
      assertEquals(0, backing.removes)
    }
  }

  @Test fun copilotAndCustomHaveNoManagedDefaultsAndNeverAccessPreferences() {
    val backing = MapStore()
    val store = PreferenceBackedLaunchDefaultsStore(backing)
    listOf(CliToolId.COPILOT, CliToolId.CUSTOM).forEach { tool ->
      assertEquals(GuidedLaunchSelection(), store.load(tool))
      assertThrows(IllegalArgumentException::class.java) { store.save(tool, GuidedLaunchSelection()) }
      assertThrows(IllegalArgumentException::class.java) { store.clear(tool) }
    }
    assertEquals(0, backing.reads)
    assertEquals(0, backing.writes)
    assertEquals(0, backing.removes)
  }

  @Test fun malformedAndFutureRecordsAreRecoverableAndNeverSilentlyOverwritten() {
    val malformed = listOf(
      "", "garbage", "1\tclaude\tmodel", "2\tclaude\tmodel\t\t\t", "1\tcodex\tmodel\t\t\t",
      "1\tclaude\tmodel\t\t\t\textra", "1\tclaude\tmodel\tagent\t\t", "1\tclaude\tmodel\t\tprofile\t",
      "1\tclaude\tmodel\t\t\teffort", "1\tclaude\tmodel;cmd\t\t\t", "1\tclaude\tbad\u0000value\t\t\t",
      "1\tclaude\t${"x".repeat(129)}\t\t\t", "x".repeat(601),
    )
    malformed.forEach { record ->
      val backing = MapStore(mutableMapOf(key(CliToolId.CLAUDE) to record, "unrelated" to "keep"))
      val store = PreferenceBackedLaunchDefaultsStore(backing)
      val before = backing.values.toMap()
      assertThrows(LaunchDefaultsException::class.java) { store.load(CliToolId.CLAUDE) }
      assertThrows(LaunchDefaultsException::class.java) { store.save(CliToolId.CLAUDE, GuidedLaunchSelection(model = "new")) }
      assertThrows(LaunchDefaultsException::class.java) { store.save(CliToolId.CLAUDE, GuidedLaunchSelection()) }
      assertEquals(before, backing.values)
      assertEquals(0, backing.writes)
      assertEquals(0, backing.removes)
      store.clear(CliToolId.CLAUDE)
      assertEquals(mapOf("unrelated" to "keep"), backing.values)
      assertEquals(GuidedLaunchSelection(), store.load(CliToolId.CLAUDE))
      store.save(CliToolId.CLAUDE, GuidedLaunchSelection(model = "recovered"))
      assertEquals("recovered", store.load(CliToolId.CLAUDE).model)
    }
  }

  @Test fun corruptOneToolDoesNotBlockOthersOrDeleteUnknownKeys() {
    val backing = MapStore(mutableMapOf(key(CliToolId.CLAUDE) to "unsupported", "guided_launch_codex_v2" to "future"))
    val store = PreferenceBackedLaunchDefaultsStore(backing)
    store.save(CliToolId.CODEX, selections.getValue(CliToolId.CODEX))
    store.clear(CliToolId.OPENCODE)
    assertEquals(selections.getValue(CliToolId.CODEX), store.load(CliToolId.CODEX))
    assertEquals("unsupported", backing.values[key(CliToolId.CLAUDE)])
    assertEquals("future", backing.values["guided_launch_codex_v2"])
  }

  @Test fun readFailureIsReportedAndPreventsSaveWithoutLeakingUnderlyingData() {
    val backing = MapStore()
    backing.failRead = true
    val store = PreferenceBackedLaunchDefaultsStore(backing)
    val error = assertThrows(LaunchDefaultsException::class.java) { store.load(CliToolId.CODEX) }
    assertFalse(error.toString().contains("private backing data"))
    assertThrows(LaunchDefaultsException::class.java) { store.save(CliToolId.CODEX, GuidedLaunchSelection(model = "new")) }
    assertEquals(0, backing.writes)
    assertEquals(0, backing.removes)
    // Explicit reset is allowed even when reading is impossible.
    store.clear(CliToolId.CODEX)
    assertEquals(1, backing.removes)
  }

  @Test fun writeFailureDoesNotReportSuccessOrDamageOtherRecords() {
    val backing = MapStore()
    val store = PreferenceBackedLaunchDefaultsStore(backing)
    store.save(CliToolId.CODEX, selections.getValue(CliToolId.CODEX))
    val before = backing.values.toMap()
    backing.failWrite = true
    val error = assertThrows(LaunchDefaultsException::class.java) { store.save(CliToolId.CODEX, GuidedLaunchSelection(model = "new")) }
    assertFalse(error.toString().contains("private backing data"))
    assertEquals(before, backing.values)
    assertEquals(selections.getValue(CliToolId.CODEX), store.load(CliToolId.CODEX))
  }

  @Test fun clearFailureAndEmptySaveFailurePreserveTheRecord() {
    val backing = MapStore()
    val store = PreferenceBackedLaunchDefaultsStore(backing)
    store.save(CliToolId.CODEX, selections.getValue(CliToolId.CODEX))
    val before = backing.values.toMap()
    backing.failRemove = true
    val error = assertThrows(LaunchDefaultsException::class.java) { store.clear(CliToolId.CODEX) }
    assertFalse(error.toString().contains("private backing data"))
    assertThrows(LaunchDefaultsException::class.java) { store.save(CliToolId.CODEX, GuidedLaunchSelection()) }
    assertEquals(before, backing.values)
  }

  private val selections = linkedMapOf(
    CliToolId.CLAUDE to GuidedLaunchSelection(model = "sonnet[1m]"),
    CliToolId.OPENCODE to GuidedLaunchSelection(model = "provider/model", agent = "build"),
    CliToolId.CODEX to GuidedLaunchSelection(model = "model", nativeProfile = "work", reasoningEffort = "xhigh"),
  )

  private fun key(tool: CliToolId) = "guided_launch_${tool.id}_v1"

  private class MapStore(val values: MutableMap<String, String> = mutableMapOf()) : StringPreferenceStore {
    var reads = 0
    var writes = 0
    var removes = 0
    var failRead = false
    var failWrite = false
    var failRemove = false
    override fun getString(key: String): String? {
      reads++
      check(!failRead) { "private backing data" }
      return values[key]
    }
    override fun putString(key: String, value: String) {
      check(!failWrite) { "private backing data" }
      writes++
      values[key] = value
    }
    override fun removeString(key: String) {
      check(!failRemove) { "private backing data" }
      removes++
      values.remove(key)
    }
  }
}
