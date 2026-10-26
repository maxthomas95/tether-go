package com.tether.go.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ChatFormattingTest {
  private val now = 1_700_000_000L

  @Test
  fun relativeTimes() {
    assertEquals("just now", relativeTimeLabel(now - 30, now))
    assertEquals("5m ago", relativeTimeLabel(now - 5 * 60, now))
    assertEquals("3h ago", relativeTimeLabel(now - 3 * 3600, now))
    assertEquals("yesterday", relativeTimeLabel(now - 30 * 3600, now))
    assertEquals("4d ago", relativeTimeLabel(now - 4 * 86400, now))
    assertEquals("Oct 30", relativeTimeLabel(now - 15 * 86400, now, ZoneOffset.UTC))
    assertEquals("just now", relativeTimeLabel(now + 100, now))
  }

  @Test
  fun shortensHomeForUserAndRoot() {
    assertEquals("~/repo/x", shortenHomePath("/home/max/repo/x", "max"))
    assertEquals("~", shortenHomePath("/home/max", "max"))
    assertEquals("/home/maxwell/x", shortenHomePath("/home/maxwell/x", "max"))
    assertEquals("~/x", shortenHomePath("/root/x", "root"))
    assertEquals("/srv/app", shortenHomePath("/srv/app", "max"))
  }

  @Test
  fun pathLeafs() {
    assertEquals("tether", pathLeaf("/home/max/repo/tether/"))
    assertEquals("~", pathLeaf("~"))
    assertEquals("/", pathLeaf("/"))
  }

  @Test
  fun searchMatchesTitlePromptAndDir() {
    val chat = RemoteChat(
      sessionId = "acea7208-d1f9-4c06-8350-657668b70f38",
      projectKey = "-home-max-repo",
      cwd = "/home/max/repo",
      title = "Resume support",
      firstPrompt = "add it",
      lastPrompt = null,
      modifiedEpochSeconds = 0,
    )
    assertTrue(chat.matches(""))
    assertTrue(chat.matches("resume"))
    assertTrue(chat.matches("max/repo"))
    assertTrue(chat.matches("acea7208"))
    assertFalse(chat.matches("codex"))
  }
}
