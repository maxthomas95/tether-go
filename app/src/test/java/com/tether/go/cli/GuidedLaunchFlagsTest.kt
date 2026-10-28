package com.tether.go.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedLaunchFlagsTest {
  @Test fun emptySelectionReturnsExactOriginalFragmentsWithoutParsingForEveryTool() {
    val raw = listOf("  --model \"\$MODEL\"; echo hi  ", "", "'unclosed", "*.json")
    CliToolId.entries.forEach { tool ->
      assertSame(raw, GuidedLaunchFlags.resolve(tool, raw, GuidedLaunchSelection()))
    }
  }

  @Test fun supportsOnlyNativeFieldsForEachTool() {
    val selections = listOf(
      GuidedLaunchSelection(model = "model"), GuidedLaunchSelection(agent = "build"),
      GuidedLaunchSelection(nativeProfile = "work"), GuidedLaunchSelection(reasoningEffort = "xhigh"),
    )
    val support = mapOf(
      CliToolId.CLAUDE to setOf(0), CliToolId.OPENCODE to setOf(0, 1),
      CliToolId.CODEX to setOf(0, 2, 3), CliToolId.COPILOT to emptySet(), CliToolId.CUSTOM to emptySet(),
    )
    support.forEach { (tool, indices) -> selections.forEachIndexed { index, selection ->
      if (index in indices) assertTrue(GuidedLaunchFlags.resolve(tool, emptyList(), selection).isNotEmpty())
      else assertThrows(IllegalArgumentException::class.java) { GuidedLaunchFlags.resolve(tool, emptyList(), selection) }
    } }
  }

  @Test fun appendsNativeTokensInStableOrderAndQuotesEveryToken() {
    assertEquals(listOf("'--verbose'", "'--model=sonnet[1m]'"),
      GuidedLaunchFlags.resolve(CliToolId.CLAUDE, listOf("--verbose"), GuidedLaunchSelection(model = "sonnet[1m]")))
    assertEquals(listOf("'--model=provider/model'", "'--agent=build'"),
      GuidedLaunchFlags.resolve(CliToolId.OPENCODE, emptyList(), GuidedLaunchSelection(model = "provider/model", agent = "build")))
    assertEquals(listOf("'--model=model'", "'--profile=work'", "'-c'", "'model_reasoning_effort=\"xhigh\"'"),
      GuidedLaunchFlags.resolve(CliToolId.CODEX, emptyList(),
        GuidedLaunchSelection(model = "model", nativeProfile = "work", reasoningEffort = "xhigh")))
  }

  @Test fun validatesEachFieldAt128CharactersAndRejectsEmbeddedWhitespace() {
    fieldCases.forEach { (tool, make) ->
      assertTrue(GuidedLaunchFlags.resolve(tool, emptyList(), make("x".repeat(128))).isNotEmpty())
      listOf("x".repeat(129), "x y", "x\ty", "x\ny", "x\ry", "x\u0000y", "x\u001by", "x\u007fy", "x\u0085y",
        "x\u00a0y", "x\u2028y", "-x", "--model", "'x'", "\"x\"", "x\\y", "\$HOME", "\$(id)",
        "x;y", "x|y", "x&y", "x>y", "x<y", "x*y", "x?y", "x=y", "{x}", "#x", "é").forEach { value ->
        assertThrows("$tool: $value", IllegalArgumentException::class.java) {
          GuidedLaunchFlags.resolve(tool, emptyList(), make(value))
        }
      }
    }
  }

  @Test fun trimsOnlyGuidedInputsAndTreatsWhitespaceOnlyAsCleared() {
    val raw = listOf("  \$SHELL; echo hi  ", "'unclosed")
    CliToolId.entries.forEach { tool ->
      assertSame(raw, GuidedLaunchFlags.resolve(tool, raw, GuidedLaunchSelection(" \t ", " ", " ", " ")))
    }
    fieldCases.forEach { (tool, make) ->
      assertEquals(GuidedLaunchFlags.resolve(tool, emptyList(), make("safe")),
        GuidedLaunchFlags.resolve(tool, emptyList(), make(" safe ")))
      assertEquals(GuidedLaunchFlags.resolve(tool, emptyList(), make("x".repeat(128))),
        GuidedLaunchFlags.resolve(tool, emptyList(), make(" " + "x".repeat(128) + " ")))
    }
  }

  @Test fun guidedSettingsStillUseSecondStageResumeConflictValidation() {
    val cases = mapOf(
      CliToolId.CLAUDE to listOf("--resume old", "--continue", "-c"),
      CliToolId.OPENCODE to listOf("-s old", "--session old", "--continue", "--fork"),
      CliToolId.CODEX to listOf("resume old", "fork old", "--last", "--worktree"),
    )
    cases.forEach { (tool, selectors) -> selectors.forEach { raw ->
      val flags = GuidedLaunchFlags.resolve(tool, listOf(raw), GuidedLaunchSelection(model = "safe"))
      assertThrows(IllegalArgumentException::class.java) {
        LaunchCommandBuilder.build(LaunchProfile(tool, flags = flags, resumeToolSessionId = "chosen"))
      }
    } }
  }

  @Test fun allowsDesktopIdentifierAlphabetButCodexRejectsBrackets() {
    fieldCases.forEach { (tool, make) ->
      assertTrue(GuidedLaunchFlags.resolve(tool, emptyList(), make("A0._:@/+~-z")).isNotEmpty())
      if (tool == CliToolId.CODEX) assertThrows(IllegalArgumentException::class.java) {
        GuidedLaunchFlags.resolve(tool, emptyList(), make("sonnet[1m]"))
      } else assertTrue(GuidedLaunchFlags.resolve(tool, emptyList(), make("sonnet[1m]")).isNotEmpty())
    }
  }

  @Test fun rejectsOverlappingLongSelectorsIncludingDuplicateEmptyAndQuotedForms() {
    listOf(CliToolId.CLAUDE, CliToolId.OPENCODE, CliToolId.CODEX).forEach { tool ->
      listOf("--model old", "--model=old", "'--model' 'old'", "\\--model old", "--model=", "--model ''", "--model",
        "--model=a --model=b").forEach { raw -> assertConflict(tool, raw, GuidedLaunchSelection(model = "new")) }
    }
    listOf("--agent build", "--agent=build", "--agent", "--agent=", "--agent ''", "--agent=a --agent=b")
      .forEach { assertConflict(CliToolId.OPENCODE, it, GuidedLaunchSelection(agent = "new")) }
    listOf("--profile work", "--profile=work", "--profile", "--profile=", "--profile ''", "--profile=a --profile=b")
      .forEach { assertConflict(CliToolId.CODEX, it, GuidedLaunchSelection(nativeProfile = "new")) }
  }

  @Test fun rejectsOverlappingShortSplitEqualsAndAttachedForms() {
    listOf(CliToolId.CODEX, CliToolId.OPENCODE).forEach { tool ->
      listOf("-m old", "-m=old", "-mold", "'-mold'", "-m", "-m=", "-m ''")
        .forEach { assertConflict(tool, it, GuidedLaunchSelection(model = "new")) }
    }
    listOf("-p work", "-p=work", "-pwork", "'-pwork'", "-p", "-p=", "-p ''")
      .forEach { assertConflict(CliToolId.CODEX, it, GuidedLaunchSelection(nativeProfile = "new")) }
  }

  @Test fun rejectsAmbiguousShortGroupsRatherThanRewritingThem() {
    listOf(CliToolId.CODEX, CliToolId.OPENCODE).forEach { tool ->
      listOf("-vm old", "-vmold", "-vm=old", "-vmm").forEach { raw ->
        assertThrows(IllegalArgumentException::class.java) {
          GuidedLaunchFlags.resolve(tool, listOf(raw), GuidedLaunchSelection(model = "new"))
        }
      }
    }
    listOf("-vpwork", "-vcmodel=old", "-vc", "-vmp").forEach { raw ->
      assertThrows(IllegalArgumentException::class.java) {
        GuidedLaunchFlags.resolve(CliToolId.CODEX, listOf(raw), GuidedLaunchSelection(nativeProfile = "new"))
      }
    }
  }

  @Test fun rejectsConfigOverlapInEveryNativeSpellingIncludingQuotedKeys() {
    listOf("-c ", "--config ", "-c=", "--config=", "-c").forEach { prefix ->
      listOf("model=\"old\"", "model = \"old\"", "\"model\"=\"old\"", "'model'='old'").forEach { payload ->
        assertConflict(CliToolId.CODEX, prefix + ShellQuote.quoteArg(payload), GuidedLaunchSelection(model = "new"))
      }
      listOf("model_reasoning_effort=\"low\"", "model_reasoning_effort=", "\"model_reasoning_effort\" = 'low'").forEach { payload ->
        assertConflict(CliToolId.CODEX, prefix + ShellQuote.quoteArg(payload), GuidedLaunchSelection(reasoningEffort = "high"))
      }
    }
  }

  @Test fun ambiguousEscapedConfigKeysFailClosedWithoutEvaluatingToml() {
    assertThrows(IllegalArgumentException::class.java) {
      GuidedLaunchFlags.resolve(CliToolId.CODEX, listOf("-c " + ShellQuote.quoteArg("\"\\u006dodel\"=\"old\"")),
        GuidedLaunchSelection(model = "new"))
    }
  }

  @Test fun rejectsMissingEmptyAndOptionLikeKnownValuesEvenForInactiveFields() {
    val cases = mapOf(
      CliToolId.CLAUDE to listOf("--permission-mode"),
      CliToolId.OPENCODE to listOf("--agent", "--session", "-s"),
      CliToolId.CODEX to listOf("--profile", "-p", "--config", "-c", "--cd", "-C"),
    )
    cases.forEach { (tool, options) -> options.forEach { option ->
      listOf(option, "$option ''", "$option=", "$option --model=old", "$option=--model=old").forEach { raw ->
        assertThrows("$tool: $raw", IllegalArgumentException::class.java) {
          GuidedLaunchFlags.resolve(tool, listOf(raw), GuidedLaunchSelection(model = "new"))
        }
      }
    } }
  }

  @Test fun preservesAllUnrelatedWordsOrderEmptyLiteralsAndManualSelectorsForEmptyFields() {
    val args = listOf("", "prompt with spaces", "--model=manual", "--model", "second", "-mthird", "-c", "model=\"fourth\"",
      "--config", "model_reasoning_effort=\"medium\"", "--config=note=--profile=work", "-cprovider.name=\"a b\"",
      "resume", "fork", "--unknown=value", "--model-like", "--profile work")
    val raw = args.map(ShellQuote::quoteArg)
    val resolved = GuidedLaunchFlags.resolve(CliToolId.CODEX, raw, GuidedLaunchSelection(nativeProfile = "new"))
    assertEquals(args + "--profile=new", PosixLiteralArguments.parse(resolved.joinToString(" ")))
  }

  @Test fun consumesKnownValuesBeforeConsideringSelectorTextOrShortGroups() {
    val args = listOf("--profile", "resume", "-pfork", "--config", "note=--model=old", "-cnotes=\"-vm\"", "-C/tmp/model",
      "--image", "file with spaces", "--sandbox=workspace-write", "--ask-for-approval", "never")
    val resolved = GuidedLaunchFlags.resolve(CliToolId.CODEX, args.map(ShellQuote::quoteArg), GuidedLaunchSelection(model = "fork"))
    assertEquals(args + "--model=fork", PosixLiteralArguments.parse(resolved.joinToString(" ")))
    assertEquals(listOf("-smodel", "--model=new"), PosixLiteralArguments.parse(
      GuidedLaunchFlags.resolve(CliToolId.OPENCODE, listOf("-smodel"), GuidedLaunchSelection(model = "new")).joinToString(" ")))
  }

  @Test fun overlappingUnknownOptionPayloadIsRejectedRatherThanGuessedOrDropped() {
    assertConflict(CliToolId.CODEX, "--unknown '--model=old'", GuidedLaunchSelection(model = "new"))
  }

  @Test fun activeControlsRejectEndOfOptionsAndShellSyntax() {
    fieldCases.forEach { (tool, make) ->
      listOf("--", "'--'", "--verbose -- prompt", "\$MODEL", "echo a; echo b", "*.json", "'unclosed").forEach { raw ->
        assertThrows(IllegalArgumentException::class.java) { GuidedLaunchFlags.resolve(tool, listOf(raw), make("safe")) }
      }
    }
  }

  @Test fun clearingSelectionRestoresManualInputInsteadOfRemovingItsSelectors() {
    val raw = listOf("  --model=manual --verbose  ")
    assertThrows(IllegalArgumentException::class.java) {
      GuidedLaunchFlags.resolve(CliToolId.CLAUDE, raw, GuidedLaunchSelection(model = "guided"))
    }
    assertSame(raw, GuidedLaunchFlags.resolve(CliToolId.CLAUDE, raw, GuidedLaunchSelection()))
    assertSame(raw, GuidedLaunchFlags.resolve(CliToolId.CUSTOM, raw, GuidedLaunchSelection()))
  }

  @Test fun retainedManualInputIsRevalidatedForTheNewToolAndSelection() {
    val raw = listOf("--agent manual")
    assertTrue(GuidedLaunchFlags.resolve(CliToolId.CLAUDE, raw, GuidedLaunchSelection(model = "sonnet")).isNotEmpty())
    assertSame(raw, GuidedLaunchFlags.resolve(CliToolId.OPENCODE, raw, GuidedLaunchSelection()))
    assertConflict(CliToolId.OPENCODE, raw.single(), GuidedLaunchSelection(agent = "build"))
  }

  private fun assertConflict(tool: CliToolId, raw: String, selection: GuidedLaunchSelection) {
    val error = assertThrows("$tool: $raw", IllegalArgumentException::class.java) {
      GuidedLaunchFlags.resolve(tool, listOf(raw), selection)
    }
    assertTrue(error.message.orEmpty().contains("Remove the manual selector or clear the guided"))
  }

  private val fieldCases: List<Pair<CliToolId, (String) -> GuidedLaunchSelection>> = listOf(
    CliToolId.CLAUDE to { GuidedLaunchSelection(model = it) },
    CliToolId.OPENCODE to { GuidedLaunchSelection(model = it) },
    CliToolId.OPENCODE to { GuidedLaunchSelection(agent = it) },
    CliToolId.CODEX to { GuidedLaunchSelection(model = it) },
    CliToolId.CODEX to { GuidedLaunchSelection(nativeProfile = it) },
    CliToolId.CODEX to { GuidedLaunchSelection(reasoningEffort = it) },
  )
}
