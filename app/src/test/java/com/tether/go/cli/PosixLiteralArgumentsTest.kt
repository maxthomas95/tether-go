package com.tether.go.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PosixLiteralArgumentsTest {
  @Test fun readsWhitespaceAndEmptyInput() {
    assertEquals(emptyList<String>(), PosixLiteralArguments.parse(" \t "))
    assertEquals(listOf("--verbose", "value"), PosixLiteralArguments.parse("  --verbose\tvalue "))
  }

  @Test fun readsQuotedEmptyConcatenatedAndEscapedLiterals() {
    assertEquals(
      listOf("", "", "ab cd", "one two", "a'b", "\$HOME;*", "a\\q", "\$literal"),
      PosixLiteralArguments.parse("'' \"\" a'b c'd one\\ two 'a'\\''b' '\$HOME;*' \"a\\q\" \"\\\$literal\""),
    )
  }

  @Test fun rejectsExpansionOperatorsGlobsAndComments() {
    listOf("\$HOME", "\$(id)", "`id`", "\"\$HOME\"", "\"`id`\"", "a;b", "a|b", "a&b",
      "a>b", "a<b", "(a)", "*.json", "a?b", "[ab]", "{a,b}", "~/repo", "#comment",
      "hello\u2028world").forEach { source ->
      val error = assertThrows(source, IllegalArgumentException::class.java) { PosixLiteralArguments.parse(source) }
      assertTrue(error.message.orEmpty().contains("Default / manual"))
    }
  }

  @Test fun rejectsUnclosedQuotesAndEscapes() {
    listOf("'abc", "\"abc", "abc\\", "\"abc\\").forEach { source ->
      assertThrows(source, IllegalArgumentException::class.java) { PosixLiteralArguments.parse(source) }
    }
  }

  @Test fun rejectsControlCharactersEvenInsideQuotesOrEscapes() {
    listOf("\u0000", "\n", "\r", "\u001b", "\u007f", "\u0085").forEach { control ->
      listOf(control, "'$control'", "\"$control\"", "\\$control").forEach { source ->
        assertThrows(IllegalArgumentException::class.java) { PosixLiteralArguments.parse(source) }
      }
    }
  }

  @Test fun rejectsLiteralTabsInsideQuotesOrEscapesButAcceptsSeparators() {
    listOf("'a\tb'", "\"a\tb\"", "a\\\tb", "\"a\\\tb\"").forEach { source ->
      assertThrows(IllegalArgumentException::class.java) { PosixLiteralArguments.parse(source) }
    }
    assertEquals(listOf("--model", "safe"), PosixLiteralArguments.parse("--model\tsafe"))
  }

  @Test fun allowsLiteralShellCharactersWhenQuotedOrEscaped() {
    assertEquals(listOf("\$;*?[]{}~#|&()<>", "\$", "*", "\"", "\\", "`"),
      PosixLiteralArguments.parse("'\$;*?[]{}~#|&()<>' \\$ \\* \\\" \\\\ \\`"))
  }
}
