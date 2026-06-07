# JetBrains Mono

`app/src/main/res/font/jetbrains_mono.ttf` is **JetBrains Mono Regular**, used as the
terminal typeface.

- Source: https://github.com/JetBrains/JetBrainsMono
- License: SIL Open Font License 1.1 (see [OFL.txt](OFL.txt))

Why bundled: some Android builds (observed on the Motorola Razr Fold 2026) map
`Typeface.MONOSPACE` to a font whose Latin glyphs sit in full-width (CJK-style)
cells, so the terminal renderer — which sizes cells to `measureText("M")` — draws
every character with a large gap. Shipping a true Latin monospace font makes the
measured cell width match the glyph advance, so the terminal renders tightly.
