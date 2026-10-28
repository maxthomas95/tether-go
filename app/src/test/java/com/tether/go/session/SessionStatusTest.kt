package com.tether.go.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStatusTest {
  @Test
  fun liveAndAttentionSemanticsCoverEveryStatus() {
    val expected = mapOf(
      SessionStatus.Connecting to (true to false),
      SessionStatus.Running to (true to false),
      SessionStatus.Waiting to (true to true),
      SessionStatus.Idle to (true to false),
      SessionStatus.Disconnected to (false to false),
      SessionStatus.Error to (false to false),
    )
    assertEquals(SessionStatus.entries.toSet(), expected.keys)
    for ((status, flags) in expected) {
      assertEquals("${status.name} live", flags.first, status.isLive)
      assertEquals("${status.name} needs attention", flags.second, status.needsAttention)
    }
  }
}
