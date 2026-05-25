package com.tether.go.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalInputBufferTest {
  @Test
  fun appendRecordsTotalBytesAndControlPreview() {
    val buffer = TerminalInputBuffer()

    buffer.append(byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte(), 0x0D))

    assertEquals(4, buffer.snapshot.totalBytes)
    assertEquals("<esc>[A<cr>", buffer.snapshot.preview)
  }

  @Test
  fun tailPreviewTruncatesWithoutResettingTotal() {
    val buffer = TerminalInputBuffer(maxTailBytes = 4)

    buffer.append("abcdef".encodeToByteArray())

    assertEquals(6, buffer.snapshot.totalBytes)
    assertEquals("cdef", buffer.snapshot.preview)
  }

  @Test
  fun clearResetsRecordedInput() {
    val buffer = TerminalInputBuffer()
    buffer.append("codex\n".encodeToByteArray())

    buffer.clear()

    assertEquals(TerminalInputSnapshot(), buffer.snapshot)
  }
}
