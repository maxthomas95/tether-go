package com.tether.go.terminal

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.TerminalDimensions
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import org.connectbot.terminal.VTermKey

private const val VTERM_MOD_CTRL = 4
private const val STREAM_DELAY_MS = 85L

private val AppBackground = Color(0xFF0B0D10)
private val TerminalBackground = Color(0xFF05080B)
private val TerminalForeground = Color(0xFFD9E7E2)
private val PanelBackground = Color(0xFF151A1F)
private val AccentCyan = Color(0xFF50D5E8)
private val AccentGreen = Color(0xFF9BE564)
private val AccentAmber = Color(0xFFFFC857)
private val MutedText = Color(0xFFA7B4AE)

private val TerminalAnsiPalette = intArrayOf(
  0xFF05080B.toInt(),
  0xFFE65F5C.toInt(),
  0xFF7AD66D.toInt(),
  0xFFEACB65.toInt(),
  0xFF58A6FF.toInt(),
  0xFFD07AF2.toInt(),
  0xFF50D5E8.toInt(),
  0xFFD9E7E2.toInt(),
  0xFF52605D.toInt(),
  0xFFFF7A70.toInt(),
  0xFF9BE564.toInt(),
  0xFFFFE08A.toInt(),
  0xFF8AB4FF.toInt(),
  0xFFE09BFF.toInt(),
  0xFF81F7E5.toInt(),
  0xFFFFFFFF.toInt(),
)

@Composable
fun TerminalSpikeScreen() {
  val inputBuffer = remember { TerminalInputBuffer() }
  val fakePty = remember { FakePtyByteStream() }
  var terminalSize by remember { mutableStateOf(TerminalDimensions(rows = 32, columns = 96)) }
  var streamRunning by remember { mutableStateOf(true) }
  var showIme by remember { mutableStateOf(false) }
  var forcedSize by remember { mutableStateOf<Pair<Int, Int>?>(null) }

  val terminal = remember {
    TerminalEmulatorFactory.create(
      initialRows = terminalSize.rows,
      initialCols = terminalSize.columns,
      defaultForeground = TerminalForeground,
      defaultBackground = TerminalBackground,
      onKeyboardInput = inputBuffer::append,
      onResize = { terminalSize = it },
    )
  }

  LaunchedEffect(terminal) {
    terminal.applyColorScheme(
      ansiColors = TerminalAnsiPalette,
      defaultForeground = TerminalForeground.toArgb(),
      defaultBackground = TerminalBackground.toArgb(),
    )
    terminal.writeInput(fakePty.banner())
  }

  LaunchedEffect(terminal, streamRunning) {
    while (true) {
      if (streamRunning) {
        terminal.writeInput(fakePty.nextChunk())
      }
      delay(STREAM_DELAY_MS)
    }
  }

  val inputSnapshot = inputBuffer.snapshot

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(AppBackground)
      .systemBarsPadding(),
  ) {
    TerminalHeader(
      terminalSize = terminalSize,
      streamRunning = streamRunning,
      inputSnapshot = inputSnapshot,
      onToggleStream = { streamRunning = !streamRunning },
    )

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .background(TerminalBackground),
    ) {
      Terminal(
        terminalEmulator = terminal,
        modifier = Modifier.fillMaxSize(),
        typeface = Typeface.MONOSPACE,
        initialFontSize = 12.sp,
        minFontSize = 7.sp,
        maxFontSize = 22.sp,
        backgroundColor = TerminalBackground,
        foregroundColor = TerminalForeground,
        selectionBackgroundColor = AccentCyan,
        selectionForegroundColor = TerminalBackground,
        keyboardEnabled = true,
        showSoftKeyboard = showIme,
        onTerminalTap = { showIme = true },
        onImeVisibilityChanged = { showIme = it },
        forcedSize = forcedSize,
      )
    }

    InputBufferStrip(
      inputSnapshot = inputSnapshot,
      onClearInput = inputBuffer::clear,
    )

    TerminalQuickBar(
      terminal = terminal,
      showIme = showIme,
      forcedSize = forcedSize,
      onShowImeChange = { showIme = it },
      onForcedSizeChange = { forcedSize = it },
    )
  }
}

@Composable
private fun TerminalHeader(
  terminalSize: TerminalDimensions,
  streamRunning: Boolean,
  inputSnapshot: TerminalInputSnapshot,
  onToggleStream: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 58.dp)
      .background(PanelBackground)
      .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = "Tether Go",
        color = TerminalForeground,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = "ConnectBot termlib | ${terminalSize.columns}x${terminalSize.rows} | stdin ${inputSnapshot.totalBytes} B",
        color = MutedText,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    FilledTonalButton(
      onClick = onToggleStream,
      modifier = Modifier.height(38.dp),
      shape = RoundedCornerShape(6.dp),
      colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = if (streamRunning) Color(0xFF183322) else Color(0xFF332A18),
        contentColor = if (streamRunning) AccentGreen else AccentAmber,
      ),
    ) {
      Text(
        text = if (streamRunning) "Pause" else "Run",
        maxLines = 1,
        fontSize = 13.sp,
      )
    }
  }
}

@Composable
private fun InputBufferStrip(
  inputSnapshot: TerminalInputSnapshot,
  onClearInput: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(Color(0xFF101316))
      .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "stdin",
      color = AccentCyan,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = inputSnapshot.preview.ifEmpty { "-" },
      modifier = Modifier.weight(1f),
      color = TerminalForeground,
      fontFamily = FontFamily.Monospace,
      fontSize = 12.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    TextButton(
      onClick = onClearInput,
      modifier = Modifier.height(34.dp),
      colors = ButtonDefaults.textButtonColors(contentColor = MutedText),
    ) {
      Text(text = "Clear", fontSize = 12.sp, maxLines = 1)
    }
  }
}

@Composable
private fun TerminalQuickBar(
  terminal: TerminalEmulator,
  showIme: Boolean,
  forcedSize: Pair<Int, Int>?,
  onShowImeChange: (Boolean) -> Unit,
  onForcedSizeChange: (Pair<Int, Int>?) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(PanelBackground)
      .padding(horizontal = 10.dp, vertical = 8.dp)
      .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    QuickBarButton(
      label = if (showIme) "Hide IME" else "IME",
      selected = showIme,
      onClick = { onShowImeChange(!showIme) },
    )
    QuickBarButton(
      label = "Auto",
      selected = forcedSize == null,
      onClick = { onForcedSizeChange(null) },
    )
    QuickBarButton(
      label = "80x24",
      selected = forcedSize == Pair(24, 80),
      onClick = { onForcedSizeChange(Pair(24, 80)) },
    )
    QuickBarButton(
      label = "132x40",
      selected = forcedSize == Pair(40, 132),
      onClick = { onForcedSizeChange(Pair(40, 132)) },
    )
    QuickBarButton(label = "Esc") { terminal.dispatchKey(0, VTermKey.ESCAPE) }
    QuickBarButton(label = "Tab") { terminal.dispatchKey(0, VTermKey.TAB) }
    QuickBarButton(label = "Enter") { terminal.dispatchKey(0, VTermKey.ENTER) }
    QuickBarButton(label = "Up") { terminal.dispatchKey(0, VTermKey.UP) }
    QuickBarButton(label = "Down") { terminal.dispatchKey(0, VTermKey.DOWN) }
    QuickBarButton(label = "Left") { terminal.dispatchKey(0, VTermKey.LEFT) }
    QuickBarButton(label = "Right") { terminal.dispatchKey(0, VTermKey.RIGHT) }
    QuickBarButton(label = "^C") { terminal.dispatchCharacter(VTERM_MOD_CTRL, 'c'.code) }
    QuickBarButton(label = "^D") { terminal.dispatchCharacter(VTERM_MOD_CTRL, 'd'.code) }
    QuickBarButton(label = "claude") { terminal.sendText("claude\n") }
    QuickBarButton(label = "codex") { terminal.sendText("codex\n") }
    QuickBarButton(label = "resume") { terminal.sendText("resume\n") }
  }
}

@Composable
private fun QuickBarButton(
  label: String,
  selected: Boolean = false,
  onClick: () -> Unit,
) {
  FilledTonalButton(
    onClick = onClick,
    modifier = Modifier
      .height(40.dp)
      .sizeIn(minWidth = 52.dp),
    shape = RoundedCornerShape(6.dp),
    colors = ButtonDefaults.filledTonalButtonColors(
      containerColor = if (selected) Color(0xFF1D3A3F) else Color(0xFF20262C),
      contentColor = if (selected) AccentCyan else TerminalForeground,
    ),
    contentPadding = ButtonDefaults.TextButtonContentPadding,
  ) {
    Text(
      text = label,
      maxLines = 1,
      overflow = TextOverflow.Clip,
      fontSize = 13.sp,
    )
  }
}

private fun TerminalEmulator.sendText(text: String) {
  text.forEachCodePoint { codePoint ->
    if (codePoint == '\n'.code || codePoint == '\r'.code) {
      dispatchKey(0, VTermKey.ENTER)
    } else {
      dispatchCharacter(0, codePoint)
    }
  }
}

private inline fun String.forEachCodePoint(action: (Int) -> Unit) {
  var index = 0
  while (index < length) {
    val codePoint = codePointAt(index)
    action(codePoint)
    index += Character.charCount(codePoint)
  }
}
