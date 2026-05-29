package com.tether.go.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tether.go.ui.theme.LocalTetherTheme

/** A Tether-themed single-line text field used across the form screens. */
@Composable
fun TetherTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isError: Boolean = false,
  singleLine: Boolean = true,
  keyboardType: KeyboardType = KeyboardType.Text,
  isPassword: Boolean = false,
  placeholder: String? = null,
) {
  val theme = LocalTetherTheme.current
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    enabled = enabled,
    singleLine = singleLine,
    isError = isError,
    label = { Text(label) },
    placeholder = placeholder?.let { { Text(it, color = theme.textMuted) } },
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
    textStyle = MaterialTheme.typography.bodyMedium,
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = theme.textPrimary,
      unfocusedTextColor = theme.textPrimary,
      disabledTextColor = theme.textMuted,
      focusedBorderColor = theme.accent,
      unfocusedBorderColor = theme.border,
      disabledBorderColor = theme.border,
      cursorColor = theme.accent,
      focusedLabelColor = theme.accent,
      unfocusedLabelColor = theme.textMuted,
      errorBorderColor = theme.statusDead,
      errorLabelColor = theme.statusDead,
    ),
  )
}

/** A toggleable pill used for selecting hosts, keys, CLI tools, and themes. */
@Composable
fun SelectableChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val theme = LocalTetherTheme.current
  val container = if (selected) theme.accent.copy(alpha = 0.18f) else theme.bgHeader
  val border = if (selected) theme.accent else theme.border
  val content = when {
    !enabled -> theme.textMuted
    selected -> theme.accent
    else -> theme.textSecondary
  }
  Text(
    text = label,
    color = content,
    style = MaterialTheme.typography.labelLarge,
    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(container, RoundedCornerShape(8.dp))
      .border(androidx.compose.foundation.BorderStroke(1.dp, border), RoundedCornerShape(8.dp))
      .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(horizontal = 12.dp, vertical = 8.dp),
  )
}

/** Small uppercase section header for the form screens. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
  val theme = LocalTetherTheme.current
  Text(
    text = text.uppercase(),
    color = theme.textSecondary,
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.SemiBold,
    modifier = modifier
      .fillMaxWidth()
      .padding(top = 8.dp, bottom = 2.dp),
  )
}
