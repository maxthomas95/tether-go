package com.tether.go.cli

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchCommandBuilderTest {
  @Test
  fun buildsClaudeWithDirEnvAndFlags() {
    val command = LaunchCommandBuilder.build(
      LaunchProfile(
        cliTool = CliToolId.CLAUDE,
        workingDir = "/repo/tether",
        flags = listOf("--verbose"),
        env = mapOf("FOO" to "bar"),
      ),
    )
    assertEquals("cd '/repo/tether' && 'FOO=bar' claude --verbose", command)
  }

  @Test
  fun preservesHomeWorkingDir() {
    val command = LaunchCommandBuilder.build(
      LaunchProfile(cliTool = CliToolId.CODEX, workingDir = "~/proj"),
    )
    assertEquals("cd ~/'proj' && codex", command)
  }

  @Test
  fun usesCustomBinary() {
    val command = LaunchCommandBuilder.build(
      LaunchProfile(cliTool = CliToolId.CUSTOM, customBinary = "my-agent"),
    )
    assertEquals("my-agent", command)
  }

  @Test
  fun customBinaryFallsBackToClaude() {
    assertEquals("claude", CliToolRegistry.binaryFor(CliToolId.CUSTOM, null))
    assertEquals("agent", CliToolRegistry.binaryFor(CliToolId.CUSTOM, "agent"))
    assertEquals("opencode", CliToolRegistry.binaryFor(CliToolId.OPENCODE, null))
  }

  @Test
  fun claudeResumeAndPinnedSessionIdsPrecedeFlags() {
    val id = "acea7208-d1f9-4c06-8350-657668b70f38"
    val resume = LaunchCommandBuilder.build(
      LaunchProfile(
        cliTool = CliToolId.CLAUDE,
        workingDir = "~/repo",
        flags = listOf("--dangerously-skip-permissions"),
        toolSession = ToolSessionLaunch(id, resume = true),
      ),
    )
    assertEquals("cd ~/'repo' && claude --resume $id --dangerously-skip-permissions", resume)

    val fresh = LaunchCommandBuilder.build(
      LaunchProfile(cliTool = CliToolId.CLAUDE, toolSession = ToolSessionLaunch(id, resume = false)),
    )
    assertEquals("claude --session-id $id", fresh)
  }

  @Test
  fun otherToolsUseTheirNativeResumeAndIgnorePins() {
    fun build(tool: CliToolId, resume: Boolean) = LaunchCommandBuilder.build(
      LaunchProfile(cliTool = tool, toolSession = ToolSessionLaunch("abc", resume)),
    )
    assertEquals("codex resume abc", build(CliToolId.CODEX, resume = true))
    assertEquals("copilot --resume abc", build(CliToolId.COPILOT, resume = true))
    assertEquals("opencode --session abc", build(CliToolId.OPENCODE, resume = true))
    assertEquals("codex", build(CliToolId.CODEX, resume = false))
  }

  @Test
  fun unsafeSessionIdsAreQuoted() {
    val command = LaunchCommandBuilder.build(
      LaunchProfile(cliTool = CliToolId.CLAUDE, toolSession = ToolSessionLaunch("a b;c", resume = true)),
    )
    assertEquals("claude --resume 'a b;c'", command)
  }
}
