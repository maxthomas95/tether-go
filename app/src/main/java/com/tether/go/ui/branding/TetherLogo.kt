package com.tether.go.ui.branding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tether.go.R

/**
 * The Tether mark — the braided-knot anchor in the teal→green gradient shared
 * with desktop Tether. Bundled as a bitmap (`drawable-nodpi/tether_logo.png`)
 * because the gradient + knot detail does not reduce cleanly to a vector path.
 */
@Composable
fun TetherLogo(
  modifier: Modifier = Modifier,
  size: Dp = 28.dp,
) {
  Image(
    painter = painterResource(id = R.drawable.tether_logo),
    contentDescription = "Tether",
    contentScale = ContentScale.Fit,
    modifier = modifier.size(size),
  )
}
