package com.tether.go.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tether.go.ui.theme.LocalTetherTheme

/**
 * Push-to-talk voice input for the terminal quick bar. Hold to record via
 * Android's on-device/Google [SpeechRecognizer]; the final transcript is handed
 * to [onTranscript] (typed into the PTY by the caller). Requests RECORD_AUDIO on
 * first use and degrades gracefully when no recognition service is available.
 */
@Composable
fun VoiceInputButton(
  onTranscript: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalTetherTheme.current
  val context = LocalContext.current
  val latestOnTranscript by rememberUpdatedState(onTranscript)

  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED,
    )
  }
  var listening by remember { mutableStateOf(false) }

  val recognizer = remember {
    if (SpeechRecognizer.isRecognitionAvailable(context)) {
      SpeechRecognizer.createSpeechRecognizer(context)
    } else {
      null
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted -> hasPermission = granted }

  DisposableEffect(recognizer) {
    recognizer?.setRecognitionListener(object : RecognitionListener {
      override fun onReadyForSpeech(params: Bundle?) {}
      override fun onBeginningOfSpeech() {}
      override fun onRmsChanged(rmsdB: Float) {}
      override fun onBufferReceived(buffer: ByteArray?) {}
      override fun onEndOfSpeech() {}
      override fun onError(error: Int) {
        listening = false
      }
      override fun onResults(results: Bundle?) {
        val text = results
          ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
          ?.firstOrNull()
          .orEmpty()
        if (text.isNotBlank()) latestOnTranscript(text)
        listening = false
      }
      override fun onPartialResults(partialResults: Bundle?) {}
      override fun onEvent(eventType: Int, params: Bundle?) {}
    })
    onDispose { recognizer?.destroy() }
  }

  fun startListening() {
    val active = recognizer ?: run {
      Toast.makeText(context, "Voice input not available on this device", Toast.LENGTH_SHORT).show()
      return
    }
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }
    listening = true
    active.startListening(intent)
  }

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .height(40.dp)
      .sizeIn(minWidth = 52.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(if (listening) theme.statusDead.copy(alpha = 0.25f) else theme.bgActive)
      .padding(horizontal = 12.dp)
      .pointerInput(hasPermission, recognizer) {
        detectTapGestures(
          onPress = {
            if (!hasPermission) {
              permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              return@detectTapGestures
            }
            startListening()
            tryAwaitRelease()
            recognizer?.stopListening()
          },
        )
      },
  ) {
    Text(
      text = if (listening) "● rec" else "🎤",
      color = if (listening) theme.statusDead else theme.textPrimary,
      fontSize = 14.sp,
    )
  }
}
