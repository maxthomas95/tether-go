package com.tether.go.terminal

import java.nio.charset.StandardCharsets

private const val ESC = "\u001B"
private const val BEL = "\u0007"

class FakePtyByteStream {
  private var sequence = 0
  private var frame = 0

  fun banner(): ByteArray = buildString {
    append(ESC).append("[2J").append(ESC).append("[H")
    append(ESC).append("]0;Tether Go terminal spike").append(BEL)
    append(ESC).append("[38;5;45m")
    append("tether-go terminal renderer spike")
    append(ESC).append("[0m\r\n")
    append("renderer=ConnectBot termlib/libvterm transport=fake-pty ssh=not-started\r\n")
    append("streaming ANSI, wraps, color, scrollback, and resize pressure\r\n\r\n")
  }.toByteArray(StandardCharsets.UTF_8)

  fun nextChunk(linesPerChunk: Int = DEFAULT_LINES_PER_CHUNK): ByteArray {
    val lineCount = linesPerChunk.coerceAtLeast(1)

    val chunk = buildString {
      appendStatusLine()
      repeat(lineCount) {
        appendDataLine(sequence)
        sequence += 1
      }
    }

    frame += 1
    return chunk.toByteArray(StandardCharsets.UTF_8)
  }

  private fun StringBuilder.appendStatusLine() {
    if (frame % 3 != 0) return

    append(ESC).append("[s")
    append(ESC).append("[1;1H")
    append(ESC).append("[48;5;236m").append(ESC).append("[38;5;51m")
    append(" fake PTY ")
    append("frame=").append(frame.toString().padStart(5, '0'))
    append(" bytes-streamed lines=").append(sequence.toString().padStart(6, '0'))
    append(" ")
    append(ESC).append("[0m").append(ESC).append("[K")
    append(ESC).append("[u")
  }

  private fun StringBuilder.appendDataLine(value: Int) {
    val color = 16 + ((value * 17) % 216)
    append(ESC).append("[38;5;").append(color).append("m")
    append("pty ")
    append(value.toString().padStart(6, '0'))
    append(" | ")
    appendMeter(value)
    append(" | ")
    append("cols:")
    append((60 + (value % 90)).toString().padStart(3, '0'))
    append(" | ")

    if (value % 11 == 0) {
      append("wrap-probe ")
      append("abcdefghijklmnopqrstuvwxyz0123456789/".repeat(4))
    } else {
      append("agent-cli redraw block ")
      append((value % 8).toString())
      append(" :: raw bytes only")
    }

    append(ESC).append("[0m\r\n")
  }

  private fun StringBuilder.appendMeter(value: Int) {
    val active = (value % METER_WIDTH) + 1
    repeat(METER_WIDTH) { index ->
      append(if (index < active) '#' else '.')
    }
  }

  private companion object {
    const val DEFAULT_LINES_PER_CHUNK = 28
    const val METER_WIDTH = 24
  }
}
