package com.tether.go.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class TerminalInputSnapshot(
  val totalBytes: Int = 0,
  val preview: String = "",
)

class TerminalInputBuffer(
  private val maxTailBytes: Int = 96,
) {
  init {
    require(maxTailBytes > 0) { "maxTailBytes must be positive" }
  }

  private val lock = Any()
  private val tail = ArrayDeque<Byte>()
  private var totalBytes = 0

  var snapshot by mutableStateOf(TerminalInputSnapshot())
    private set

  fun append(data: ByteArray) {
    if (data.isEmpty()) return

    val nextSnapshot = synchronized(lock) {
      totalBytes += data.size
      data.forEach { byte ->
        tail.addLast(byte)
        while (tail.size > maxTailBytes) {
          tail.removeFirst()
        }
      }

      TerminalInputSnapshot(
        totalBytes = totalBytes,
        preview = tail.toByteArray().toTerminalInputPreview(),
      )
    }

    snapshot = nextSnapshot
  }

  fun clear() {
    synchronized(lock) {
      tail.clear()
      totalBytes = 0
    }
    snapshot = TerminalInputSnapshot()
  }
}

internal fun ByteArray.toTerminalInputPreview(): String = buildString {
  for (byte in this@toTerminalInputPreview) {
    when (val value = byte.toInt() and 0xFF) {
      0x09 -> append("<tab>")
      0x0A -> append("<lf>")
      0x0D -> append("<cr>")
      0x1B -> append("<esc>")
      0x7F -> append("<del>")
      in 0x20..0x7E -> append(value.toChar())
      else -> append("\\x").append(value.toString(16).padStart(2, '0'))
    }
  }
}

private fun ArrayDeque<Byte>.toByteArray(): ByteArray {
  val bytes = ByteArray(size)
  var index = 0
  for (byte in this) {
    bytes[index] = byte
    index += 1
  }
  return bytes
}
