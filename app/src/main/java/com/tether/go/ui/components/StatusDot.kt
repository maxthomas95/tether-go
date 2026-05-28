package com.tether.go.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tether.go.session.SessionStatus
import com.tether.go.ui.theme.LocalTetherTheme

/**
 * The Tether status dot: green = running, yellow = waiting, gray = idle/closed,
 * red = error. Connecting pulses to read as "working". Status colors come from
 * the active theme so they match the desktop status vocabulary.
 */
@Composable
fun StatusDot(
  status: SessionStatus,
  modifier: Modifier = Modifier,
  size: Dp = 9.dp,
) {
  val theme = LocalTetherTheme.current
  val color: Color = when (status) {
    SessionStatus.Running -> theme.statusRunning
    SessionStatus.Waiting -> theme.statusWaiting
    SessionStatus.Connecting -> theme.statusWaiting
    SessionStatus.Idle -> theme.statusIdle
    SessionStatus.Disconnected -> theme.statusIdle
    SessionStatus.Error -> theme.statusDead
  }

  val alpha = if (status == SessionStatus.Connecting) {
    val transition = rememberInfiniteTransition(label = "status-pulse")
    val pulse by transition.animateFloat(
      initialValue = 0.35f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 750, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "status-pulse-alpha",
    )
    pulse
  } else {
    1f
  }

  Box(modifier.size(size).alpha(alpha).background(color, CircleShape))
}
