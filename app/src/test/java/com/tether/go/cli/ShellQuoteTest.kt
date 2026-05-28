package com.tether.go.cli

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellQuoteTest {
  @Test
  fun quotesSimpleArgument() {
    assertEquals("'foo'", ShellQuote.quoteArg("foo"))
  }

  @Test
  fun quotesArgumentWithSpaces() {
    assertEquals("'/My Documents'", ShellQuote.quoteArg("/My Documents"))
  }

  @Test
  fun escapesEmbeddedSingleQuote() {
    assertEquals("'it'\\''s'", ShellQuote.quoteArg("it's"))
  }

  @Test
  fun preservesHomePrefix() {
    assertEquals("~", ShellQuote.quotePathPreservingHome("~"))
    assertEquals("~/'repo/tether'", ShellQuote.quotePathPreservingHome("~/repo/tether"))
    assertEquals("'/repo/tether'", ShellQuote.quotePathPreservingHome("/repo/tether"))
  }

  @Test
  fun quotesEnvAssignment() {
    assertEquals("'FOO=bar baz'", ShellQuote.quoteEnvAssignment("FOO", "bar baz"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsInvalidEnvName() {
    ShellQuote.quoteEnvAssignment("BAD NAME", "x")
  }
}
