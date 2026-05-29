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
}
