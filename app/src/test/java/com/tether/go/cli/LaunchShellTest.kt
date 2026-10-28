package com.tether.go.cli

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Execute only local fake CLIs under /bin/sh; no SSH, real agents or accounts. */
class LaunchShellTest {
  @Test fun envCwdAndNativeResumeReachRealPosixArgvWithoutInjection() = withFakeClis { root, cwd ->
    val marker = File(root, "injected")
    val value = "spaces 'quotes' \"double\" \$HOME; touch ${marker.path} \$(touch ${marker.path})"
    val id = "id';\$(touch\${IFS}${marker.path});\$HOME"
    CliToolId.entries.filter { it != CliToolId.CUSTOM }.forEach { tool ->
      val flags = listOf("--model 'model with spaces'", "--verbose")
      val command = LaunchCommandBuilder.build(LaunchProfile(
        tool, workingDir = cwd.path, flags = flags, env = mapOf("FOO" to value), resumeToolSessionId = id,
      ))
      val expectedArgs = NativeResume.buildArgs(tool, listOf("--model", "model with spaces", "--verbose"), id)
      assertEquals(listOf(cwd.path, value) + expectedArgs, execute(root, command))
      assertFalse(marker.exists())
    }
  }

  @Test fun zeroEnvKeepsExistingCommandAndManualShellSemantics() = withFakeClis { root, cwd ->
    val command = LaunchCommandBuilder.build(LaunchProfile(CliToolId.CLAUDE,
      workingDir = cwd.path, flags = listOf("--resume 'old id'", "--model \"\$MODEL\"")))
    assertFalse(command.contains(" && env "))
    assertEquals(listOf(cwd.path, "", "--resume", "old id", "--model", "manual model"), execute(root, command))
    assertEquals("claude", LaunchCommandBuilder.build(LaunchProfile(CliToolId.CLAUDE, env = mapOf(" " to ""))))
  }

  @Test fun rejectsInvalidEnvironmentBeforeAnyShellExecution() {
    listOf("BAD NAME", "1BAD", "A=B", "BAD;NAME", "BAD\u0000NAME").forEach { name ->
      assertThrows(IllegalArgumentException::class.java) {
        LaunchCommandBuilder.build(LaunchProfile(CliToolId.CLAUDE, env = mapOf(name to "value")))
      }
    }
    assertThrows(IllegalArgumentException::class.java) {
      LaunchCommandBuilder.build(LaunchProfile(CliToolId.CLAUDE, env = mapOf("FOO" to "bad\u0000value")))
    }
  }

  @Test fun envAssignmentsAreTrimmedAndMultipleValuesRemainSeparate() = withFakeClis { root, cwd ->
    val command = LaunchCommandBuilder.build(LaunchProfile(CliToolId.CODEX,
      workingDir = cwd.path, env = linkedMapOf(" FOO " to "a=b 'c'", "EMPTY" to "")))
    assertEquals(listOf(cwd.path, "a=b 'c'"), execute(root, command))
    assertTrue(command.contains("env 'FOO=a=b '\\''c'\\''' 'EMPTY=' codex"))
  }

  private fun execute(root: File, command: String): List<String> {
    val builder = ProcessBuilder("/bin/sh", "-c", command).redirectErrorStream(true)
    builder.environment()["PATH"] = "${root.path}:/usr/bin:/bin"
    builder.environment()["FOO"] = ""
    builder.environment()["MODEL"] = "manual model"
    val process = builder.start()
    try {
      assertTrue("Local fake CLI timed out", process.waitFor(5, TimeUnit.SECONDS))
      val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
      assertEquals(output, 0, process.exitValue())
      return output.split('\u0000').dropLast(1)
    } finally {
      process.destroyForcibly()
    }
  }

  private fun withFakeClis(test: (File, File) -> Unit) {
    val root = Files.createTempDirectory("tether-launch-test").toFile()
    try {
      val cwd = File(root, "repo space 'quote' \$dollar;semi").also { assertTrue(it.mkdir()) }
      listOf("claude", "codex", "copilot", "opencode").forEach { binary ->
        File(root, binary).apply {
          writeText("#!/bin/sh\nprintf '%s\\0' \"\$PWD\" \"\$FOO\"\nfor arg do printf '%s\\0' \"\$arg\"; done\n")
          assertTrue(setExecutable(true))
        }
      }
      test(root, cwd)
    } finally {
      root.deleteRecursively()
    }
  }
}
