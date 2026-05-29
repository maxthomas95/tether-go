package com.tether.go.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Inferred activity state for a connected session (a passive, render-free signal). */
enum class DetectedState { Starting, Running, Waiting, Idle }

/**
 * Passive status detection, ported from desktop Tether's `StatusDetector`.
 *
 * Observes a *copy* of each session's PTY output and infers whether the CLI is
 * running, waiting for the user, or idle — without parsing or altering what the
 * terminal renders (preserving the dumb-pipe invariant). Detection layers:
 *  1. OSC 9 notification sequence (Claude Code emits this at end-of-turn) -> Waiting.
 *  2. BEL (0x07) -> a coalesced bell signal.
 *  3. Silence: no output for [waitingTimeoutMs] -> Waiting, [idleTimeoutMs] -> Idle.
 * Output flowing -> Running. No remote hooks are used; we are only an SSH client,
 * so unlike desktop there is no end-of-turn hook signal to lean on.
 *
 * All methods must be called on the same (Main) thread that backs [scope].
 */
class StatusDetector(
  private val scope: CoroutineScope,
  private val waitingTimeoutMs: Long = 3_000,
  private val idleTimeoutMs: Long = 30_000,
  private val debounceMs: Long = 500,
  private val bellCoalesceMs: Long = 2_000,
  private val now: () -> Long = { System.currentTimeMillis() },
) {
  private val esc = Char(27)
  private val bel = Char(7)
  // ESC ] 9 ; <text> (BEL | ESC \) — built from interpolated control chars so the
  // source carries no literal control bytes.
  private val oscNotification = Regex("$esc\\]9;[^$bel$esc]*(?:$bel|$esc\\\\)")
  private val bufferMax = 4096

  private val states = HashMap<String, DetectedState>()
  private val buffers = HashMap<String, String>()
  private val lastBellAt = HashMap<String, Long>()
  private val silenceJobs = HashMap<String, Job>()
  private val debounceJobs = HashMap<String, Job>()

  private var onStateChange: ((String, DetectedState) -> Unit)? = null
  private var onBell: ((String) -> Unit)? = null

  fun setOnStateChange(callback: (String, DetectedState) -> Unit) {
    onStateChange = callback
  }

  fun setOnBell(callback: (String) -> Unit) {
    onBell = callback
  }

  fun register(sessionId: String) {
    states[sessionId] = DetectedState.Starting
  }

  /** Reset to Starting and clear buffers/timers — call on each (re)connect. */
  fun reset(sessionId: String) {
    silenceJobs.remove(sessionId)?.cancel()
    debounceJobs.remove(sessionId)?.cancel()
    buffers.remove(sessionId)
    states[sessionId] = DetectedState.Starting
    onStateChange?.invoke(sessionId, DetectedState.Starting)
  }

  fun unregister(sessionId: String) {
    states.remove(sessionId)
    buffers.remove(sessionId)
    lastBellAt.remove(sessionId)
    silenceJobs.remove(sessionId)?.cancel()
    debounceJobs.remove(sessionId)?.cancel()
  }

  /** The passive tap: called for every chunk of PTY output. */
  fun feedData(sessionId: String, data: String) {
    if (!states.containsKey(sessionId)) return

    if (data.indexOf(bel) != -1) {
      val t = now()
      val last = lastBellAt[sessionId] ?: 0L
      if (t - last >= bellCoalesceMs) {
        lastBellAt[sessionId] = t
        onBell?.invoke(sessionId)
      }
    }

    val combined = (buffers[sessionId] ?: "") + data
    val buffer = if (combined.length > bufferMax) combined.takeLast(bufferMax) else combined
    buffers[sessionId] = buffer

    silenceJobs.remove(sessionId)?.cancel()

    if (oscNotification.containsMatchIn(buffer)) {
      transition(sessionId, DetectedState.Waiting)
      buffers[sessionId] = ""
    } else {
      transition(sessionId, DetectedState.Running)
    }

    silenceJobs[sessionId] = scope.launch {
      delay(waitingTimeoutMs)
      val current = states[sessionId]
      if (current == DetectedState.Running || current == DetectedState.Starting) {
        transition(sessionId, DetectedState.Waiting)
      }
      delay((idleTimeoutMs - waitingTimeoutMs).coerceAtLeast(0))
      val afterWaiting = states[sessionId]
      if (afterWaiting == DetectedState.Waiting || afterWaiting == DetectedState.Running) {
        transition(sessionId, DetectedState.Idle)
      }
    }
  }

  fun stateOf(sessionId: String): DetectedState = states[sessionId] ?: DetectedState.Starting

  private fun transition(sessionId: String, newState: DetectedState) {
    if (states[sessionId] == newState) return
    debounceJobs.remove(sessionId)?.cancel()
    debounceJobs[sessionId] = scope.launch {
      delay(debounceMs)
      setState(sessionId, newState)
      debounceJobs.remove(sessionId)
    }
  }

  private fun setState(sessionId: String, state: DetectedState) {
    val current = states[sessionId] ?: return
    if (current == state) return
    states[sessionId] = state
    onStateChange?.invoke(sessionId, state)
  }
}
