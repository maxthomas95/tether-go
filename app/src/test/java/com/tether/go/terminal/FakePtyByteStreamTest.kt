package com.tether.go.terminal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class FakePtyByteStreamTest {
  @Test
  fun bannerClearsScreenAndStatesNoSsh() {
    val banner = FakePtyByteStream().banner().toString(StandardCharsets.UTF_8)

    assertTrue(banner.startsWith("\u001B[2J\u001B[H"))
    assertTrue(banner.contains("transport=fake-pty"))
    assertTrue(banner.contains("ssh=not-started"))
  }

  @Test
  fun chunkEmitsRequestedLineVolumeAsPtyBytes() {
    val chunk = FakePtyByteStream().nextChunk(linesPerChunk = 12).toString(StandardCharsets.UTF_8)

    val ptyLines = chunk.lines().count { it.contains("pty ") }
    assertTrue("expected at least 12 data lines, got $ptyLines", ptyLines >= 12)
    assertTrue(chunk.contains("\u001B[38;5;"))
    assertTrue(chunk.contains("\r\n"))
  }
}
