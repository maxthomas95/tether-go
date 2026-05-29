package com.tether.go.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatusDetectorTest {
  private val esc = Char(27).toString()
  private val bel = Char(7).toString()

  @Test
  fun outputFlowingSignalsRunning() = runTest {
    val detector = StatusDetector(this)
    detector.register("s")
    detector.feedData("s", "regular output")
    advanceTimeBy(600)
    runCurrent()
    assertEquals(DetectedState.Running, detector.stateOf("s"))
  }

  @Test
  fun oscNotificationSignalsWaiting() = runTest {
    val detector = StatusDetector(this)
    detector.register("s")
    detector.feedData("s", "done ${esc}]9;Claude is waiting$bel")
    advanceTimeBy(600)
    runCurrent()
    assertEquals(DetectedState.Waiting, detector.stateOf("s"))
  }

  @Test
  fun silenceProgressesToWaitingThenIdle() = runTest {
    val detector = StatusDetector(this)
    detector.register("s")
    detector.feedData("s", "x")
    advanceTimeBy(600)
    runCurrent()
    assertEquals(DetectedState.Running, detector.stateOf("s"))

    advanceTimeBy(3_000) // silence -> waiting (after its debounce)
    runCurrent()
    assertEquals(DetectedState.Waiting, detector.stateOf("s"))

    advanceTimeBy(27_500) // total silence -> idle
    runCurrent()
    assertEquals(DetectedState.Idle, detector.stateOf("s"))
  }

  @Test
  fun bellFiresOnceAndCoalesces() = runTest {
    var clock = 10_000L
    val bells = mutableListOf<String>()
    val detector = StatusDetector(this, now = { clock })
    detector.setOnBell { bells.add(it) }
    detector.register("s")

    detector.feedData("s", "a${bel}b")  // fires
    clock = 10_500
    detector.feedData("s", "c$bel")     // within coalesce window -> suppressed
    clock = 13_000
    detector.feedData("s", "d$bel")     // outside window -> fires

    assertEquals(2, bells.size)
  }
}
