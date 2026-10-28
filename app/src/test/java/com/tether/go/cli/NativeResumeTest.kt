package com.tether.go.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeResumeTest {
  @Test fun registrySupportsExactlyFourNativeTools() {
    CliToolId.entries.forEach { tool ->
      assertEquals(tool != CliToolId.CUSTOM, CliToolRegistry.byId(tool).supportsSessionResume)
    }
  }

  @Test fun matchesDesktopNativeArgvOrdering() {
    val manual = listOf("--model", "model-name", "--verbose")
    assertEquals(manual + listOf("--resume", "id"), NativeResume.buildArgs(CliToolId.CLAUDE, manual, "id"))
    assertEquals(listOf("resume", "id") + manual, NativeResume.buildArgs(CliToolId.CODEX, manual, "id"))
    assertEquals(listOf("--resume", "id") + manual, NativeResume.buildArgs(CliToolId.COPILOT, manual, "id"))
    assertEquals(listOf("--session", "id") + manual, NativeResume.buildArgs(CliToolId.OPENCODE, manual, "id"))
  }

  @Test fun rejectsCustomEvenWithAnExplicitId() {
    assertThrows(IllegalArgumentException::class.java) { NativeResume.buildArgs(CliToolId.CUSTOM, emptyList(), "id") }
  }

  @Test fun rejectsBlankControlWhitespaceOptionLikeAndUnboundedIds() {
    listOf("", " ", "a b", "a\tb", "a\nb", "a\rb", "a\u0000b", "a\u001bb", "a\u007fb", "a\u0085b",
      "a\u00a0b", "a\u2028b", "-id", "--last", "x".repeat(257)).forEach { id ->
      supportedTools.forEach { tool ->
        assertThrows("$tool: $id", IllegalArgumentException::class.java) {
          LaunchCommandBuilder.build(LaunchProfile(tool, resumeToolSessionId = id))
        }
      }
    }
    assertEquals(listOf("resume", "x".repeat(256)), NativeResume.buildArgs(CliToolId.CODEX, emptyList(), "x".repeat(256)))
  }

  @Test fun rejectsEveryKnownSelectorInSplitEqualsQuotedEscapedAndShortForms() {
    val selectors = mapOf(
      CliToolId.CLAUDE to listOf("--resume", "--continue", "--session-id", "--fork-session", "--from-pr", "--teleport", "-r", "-c", "-rid", "-vc", "-r=id"),
      CliToolId.CODEX to listOf("resume", "fork", "--last", "--worktree"),
      CliToolId.COPILOT to listOf("--resume", "--continue", "--session-id", "--connect", "--worktree", "-r", "-w", "-rid", "-yw", "-w=dir"),
      CliToolId.OPENCODE to listOf("--session", "--continue", "--fork", "-s", "-c", "-sid", "-vc", "-s=id"),
    )
    selectors.forEach { (tool, options) ->
      options.forEach { option ->
        listOf(option, "$option value", "$option=value", "'$option' value", "\"$option\" value",
          option.replaceFirst("-", "\\-")).forEach { source ->
          assertThrows("$tool: $source", IllegalArgumentException::class.java) {
            LaunchCommandBuilder.build(LaunchProfile(tool, flags = listOf(source), resumeToolSessionId = "chosen"))
          }
        }
      }
    }
  }

  @Test fun rejectsEndOfOptionsThatCouldHideManagedSelector() {
    supportedTools.forEach { tool ->
      assertThrows(IllegalArgumentException::class.java) { NativeResume.buildArgs(tool, listOf("--"), "id") }
    }
  }

  @Test fun acceptsSafeFlagsAndQuotesLiteralValues() {
    val command = LaunchCommandBuilder.build(LaunchProfile(CliToolId.CLAUDE,
      flags = listOf("--permission-mode plan", "--model 'safe model'", "--verbose"), resumeToolSessionId = "id"))
    assertEquals("claude '--permission-mode' 'plan' '--model' 'safe model' '--verbose' '--resume' 'id'", command)
  }

  @Test fun knownModelAndAgentValuesAreNotMistakenForSelectors() {
    listOf("resume", "fork").forEach { value ->
      assertEquals(listOf("resume", "id", "--model", value),
        NativeResume.buildArgs(CliToolId.CODEX, listOf("--model", value), "id"))
    }
    listOf("-mclaude", "-manthropic/claude", "-m=claude").forEach { option ->
      assertEquals(listOf("--session", "id", option),
        NativeResume.buildArgs(CliToolId.OPENCODE, listOf(option), "id"))
    }
    assertEquals(listOf("--session", "id", "--agent", "research"),
      NativeResume.buildArgs(CliToolId.OPENCODE, listOf("--agent", "research"), "id"))
  }

  @Test fun incompleteKnownValueOptionsCannotConsumeManagedSelector() {
    supportedTools.forEach { tool ->
      listOf(listOf("--model"), listOf("--model", ""), listOf("--model", "--resume")).forEach { args ->
        assertThrows(IllegalArgumentException::class.java) { NativeResume.buildArgs(tool, args, "id") }
      }
    }
  }

  @Test fun defaultManualKeepsRawSelectorsAndShellSyntax() {
    CliToolId.entries.forEach { tool ->
      val flags = "--resume old \$VAR; echo hi"
      val command = LaunchCommandBuilder.build(LaunchProfile(tool, customBinary = "my-agent", flags = listOf(flags)))
      assertEquals("${CliToolRegistry.binaryFor(tool, "my-agent")} $flags", command)
    }
  }

  @Test fun neverAddsImplicitLastOrClaudeCreateFallback() {
    supportedTools.forEach { tool ->
      val default = LaunchCommandBuilder.build(LaunchProfile(tool))
      assertEquals(CliToolRegistry.binaryFor(tool, null), default)
      val resumed = LaunchCommandBuilder.build(LaunchProfile(tool, resumeToolSessionId = "missing-id"))
      assertTrue(resumed.contains("'missing-id'"))
      assertFalse(resumed.contains("--last") || resumed.contains("--continue") || resumed.contains("--session-id"))
    }
  }

  private val supportedTools = CliToolId.entries.filter { it != CliToolId.CUSTOM }
}
