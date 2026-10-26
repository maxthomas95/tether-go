package com.tether.go.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeChatCatalogTest {
  private val idA = "acea7208-d1f9-4c06-8350-657668b70f38"
  private val idB = "01cad56c-ea70-475a-b2e0-61b37580f4a4"

  private fun userLine(content: String, cwd: String = "/home/max/repo/tether") =
    """{"parentUuid":null,"isSidechain":false,"type":"user","message":{"role":"user","content":"$content"},"cwd":"$cwd","sessionId":"x"}"""

  @Test
  fun parsesTitlesPromptsAndCwdNewestFirst() {
    val output = listOf(
      "F 1700000000 -home-max-repo-tether/$idB.jsonl",
      "D \"cwd\":\"/home/max/repo/tether\"",
      "U ${userLine("fix the login bug")}",
      "F 1700000500 -home-max-repo-go/$idA.jsonl",
      "D \"cwd\":\"/home/max/repo/go\"",
      "U ${userLine("<command-name>/model</command-name>")}",
      "U ${userLine("add resume support\\nplease")}",
      "A {\"type\":\"ai-title\",\"aiTitle\":\"Resume support\",\"sessionId\":\"$idA\"}",
      "L {\"type\":\"last-prompt\",\"lastPrompt\":\"ship it\",\"sessionId\":\"$idA\"}",
    ).joinToString("\n")

    val chats = ClaudeChatCatalog.parse(output)

    assertEquals(listOf(idA, idB), chats.map { it.sessionId })
    val newest = chats[0]
    assertEquals("/home/max/repo/go", newest.cwd)
    assertEquals("Resume support", newest.displayTitle)
    assertEquals("add resume support please", newest.firstPrompt)
    assertEquals("ship it", newest.lastPrompt)
    assertEquals(1700000500L, newest.modifiedEpochSeconds)
    assertEquals("fix the login bug", chats[1].displayTitle)
  }

  @Test
  fun customTitleBeatsAiTitle() {
    val output = listOf(
      "F 1 p/$idA.jsonl",
      "T {\"type\":\"custom-title\",\"customTitle\":\"My name\",\"sessionId\":\"$idA\"}",
      "A {\"type\":\"ai-title\",\"aiTitle\":\"Generated\",\"sessionId\":\"$idA\"}",
    ).joinToString("\n")
    assertEquals("My name", ClaudeChatCatalog.parse(output).single().displayTitle)
  }

  @Test
  fun dropsChatsWithNothingTypedAndBadHeaders() {
    val output = listOf(
      "F 1 p/$idA.jsonl",
      "D \"cwd\":\"/srv\"",
      "F 2 p/not-a-uuid.jsonl",
      "U ${userLine("orphaned")}",
      "F 3 p/$idB.jsonl",
      "U ${userLine("<local-command-caveat>Caveat</local-command-caveat>")}",
      "garbage line",
    ).joinToString("\n")
    assertTrue(ClaudeChatCatalog.parse(output).isEmpty())
  }

  @Test
  fun truncatedUserLineStillYieldsPrompt() {
    val output = "F 1 p/$idA.jsonl\nU " +
      """{"type":"user","message":{"role":"user","content":"a very long prompt that was cut"""
    assertEquals("a very long prompt that was cut", ClaudeChatCatalog.parse(output).single().firstPrompt)
  }

  @Test
  fun workingDirFallsBackToDecodedProjectKey() {
    val output = "F 1 -home-max-repo/$idA.jsonl\nL {\"type\":\"last-prompt\",\"lastPrompt\":\"hi\"}"
    val chat = ClaudeChatCatalog.parse(output).single()
    assertNull(chat.cwd)
    assertEquals("/home/max/repo", chat.workingDir)
  }

  @Test
  fun longPromptsAreClipped() {
    val prompt = "x".repeat(400)
    val cleaned = ClaudeChatCatalog.cleanPrompt(prompt)!!
    assertEquals(241, cleaned.length)
    assertTrue(cleaned.endsWith("…"))
  }

  @Test
  fun listCommandIsWrappedForPosixShell() {
    val command = ClaudeChatCatalog.listCommand(limit = 10)
    assertTrue(command.startsWith("sh -c '"))
    assertTrue(command.contains("head -n 10"))
    assertTrue(command.contains("CLAUDE_CONFIG_DIR"))
  }

  @Test
  fun transcriptProbeRequiresUuidAndReadsYes() {
    assertTrue(ClaudeChatCatalog.transcriptExistsCommand(idA).contains("/$idA.jsonl"))
    assertTrue(ClaudeChatCatalog.transcriptExists("yes\n"))
    assertFalse(ClaudeChatCatalog.transcriptExists(""))
    assertTrue(runCatching { ClaudeChatCatalog.transcriptExistsCommand("x; rm -rf ~") }.isFailure)
  }
}

class LenientJsonTest {
  @Test
  fun decodesEscapes() {
    val line = """{"k":"a\"b\\c\né done","next":"x"}"""
    assertEquals("a\"b\\c\né done", LenientJson.stringAfter(line, "\"k\":\""))
  }

  @Test
  fun toleratesTruncationMidEscape() {
    assertEquals("abc", LenientJson.stringAfter("{\"k\":\"abc\\", "\"k\":\""))
    assertEquals("abc", LenientJson.stringAfter("{\"k\":\"abc\\u00", "\"k\":\""))
  }

  @Test
  fun missingMarkerIsNull() {
    assertNull(LenientJson.stringAfter("{}", "\"k\":\""))
  }
}
