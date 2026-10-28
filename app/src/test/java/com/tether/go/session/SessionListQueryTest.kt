package com.tether.go.session

import com.tether.go.cli.CliToolId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionListQueryTest {
  @Test
  fun emptyAndWhitespaceQueriesPreserveNaturalOrderAndOriginalObjects() {
    val sessions = listOf(sample("second"), sample("first"))
    for (text in listOf("", "  \t\n ")) {
      val query = SessionListQuery(text)
      val results = query.search(sessions, { it }, { SessionStatus.Disconnected })
      assertFalse(query.isSearching)
      assertEquals(sessions, results)
      assertSame(sessions.first(), results.first())
      assertEquals(0.0, query.score(sessions.first(), SessionStatus.Disconnected)!!, 0.0)
    }
  }

  @Test
  fun normalizesCaseAndSurroundingWhitespaceWithoutDependingOnDeviceLocale() {
    val originalLocale = Locale.getDefault()
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"))
      assertNotNull(SessionListQuery("  API \t").score(sample(label = "api-server"), SessionStatus.Running))
    } finally {
      Locale.setDefault(originalLocale)
    }
  }

  @Test
  fun matchesScatteredSubsequencesButNotOutOfOrderCharacters() {
    val session = sample(label = "api-server")
    assertNotNull(SessionListQuery("apsv").score(session, SessionStatus.Running))
    assertNull(SessionListQuery("vsap").score(session, SessionStatus.Running))
    assertNull(SessionListQuery("zzzz").score(session, SessionStatus.Running))
  }

  @Test
  fun searchesEachMetadataFieldIncludingEndpointAndCliAliases() {
    val session = sample().copy(
      label = "Build Dashboard",
      username = "alex",
      host = "build-box.example",
      port = 2222,
      workingDir = "/projects/mobile",
      cliTool = CliToolId.COPILOT,
    )
    for (text in listOf("dashboard", "alex", "build-box", "2222", "alex@build-box.example:2222", "mobile", "copilot", "github")) {
      assertNotNull(text, SessionListQuery(text).score(session, SessionStatus.Running))
    }
    val custom = session.copy(cliTool = CliToolId.CUSTOM, customBinary = "/bin/my-agent")
    assertNotNull(SessionListQuery("my-agent").score(custom, SessionStatus.Running))
  }

  @Test
  fun queryMustMatchWithinOneFieldRatherThanJoiningSeparateFields() {
    val session = sample(label = "alpha").copy(workingDir = "beta")
    assertNull(SessionListQuery("alpha beta").score(session, SessionStatus.Running))
    assertNotNull(SessionListQuery("alpha beta").score(session.copy(label = "alpha beta"), SessionStatus.Running))
  }

  @Test
  fun regexMetacharactersAreLiteralAndDoNotThrow() {
    val session = sample(label = "a.b.*+?[x]")
    assertNotNull(SessionListQuery(".*+?").score(session, SessionStatus.Running))
    assertNull(SessionListQuery(".*+?").score(sample(), SessionStatus.Running))
    assertNull(SessionListQuery("(a+)+$").score(sample(label = "a".repeat(5000)), SessionStatus.Running))
  }

  @Test
  fun neverSearchesEnvironmentFlagsKeyReferencesOrSessionIds() {
    val session = sample(id = "secret-session-identifier").copy(
      hostId = "secret-host-reference",
      flags = listOf("--secret-flag"),
      env = mapOf("SECRET" to "secret-env-value"),
      privateKeyId = "secret-key-reference",
    )
    for (query in listOf("secret-session", "secret-host", "secret-flag", "secret-env", "secret-key")) {
      assertNull(query, SessionListQuery(query).score(session, SessionStatus.Running))
    }
  }

  @Test
  fun nullAndEmptyOptionalFieldsAreSafe() {
    val session = sample().copy(workingDir = null, customBinary = null)
    assertNull(SessionListQuery("not-present").score(session, SessionStatus.Running))
    assertNotNull(SessionListQuery("claude").score(session, SessionStatus.Running))
  }

  @Test
  fun contiguousStartAnchoredBoundaryAndDenseMatchesRankHigher() {
    fun score(text: String, query: String) = SessionListQuery(query).score(sample(label = text), SessionStatus.Running)!!
    assertTrue(score("abcxyz", "abc") > score("axbxcx", "abc"))
    assertTrue(score("srvfoo", "srv") > score("foosrv", "srv"))
    assertTrue(score("api-server", "s") > score("api-server", "p"))
    assertTrue(score("abc", "abc") > score("abc-very-long-suffix", "abc"))
  }

  @Test
  fun weightsPreferLabelThenDirectoryThenEndpointThenCli() {
    val endpoint = sample("endpoint").copy(host = "needle")
    val text = endpoint.endpointLabel
    val candidates = listOf(
      sample("cli").copy(cliTool = CliToolId.CUSTOM, customBinary = text),
      endpoint,
      sample("directory").copy(workingDir = text),
      sample("label", text),
    )
    val results = SessionListQuery(text).search(candidates, { it }, { SessionStatus.Running })
    assertEquals(listOf("label", "directory", "endpoint", "cli"), results.map { it.id })
  }

  @Test
  fun corroboratingFieldsBreakTiesWithoutDoubleCountingCliAliases() {
    val labelOnly = sample("label", "needle")
    val corroborated = labelOnly.copy(id = "both", workingDir = "needle")
    val query = SessionListQuery("needle")
    assertTrue(query.score(corroborated, SessionStatus.Running)!! > query.score(labelOnly, SessionStatus.Running)!!)
    val cli = sample().copy(cliTool = CliToolId.CUSTOM, customBinary = "custom")
    // "custom" is already the id/display name; repeating it as the binary adds no score.
    assertEquals(query.copy(text = "custom").score(cli, SessionStatus.Running), query.copy(text = "custom").score(cli.copy(customBinary = null), SessionStatus.Running))
  }

  @Test
  fun tiedScoresPreserveInputOrderWithoutReorderingTheSource() {
    val candidates = listOf(sample("c", "same"), sample("a", "same"), sample("b", "same"))
    val results = SessionListQuery("same").search(candidates, { it }, { SessionStatus.Running })
    assertEquals(listOf("c", "a", "b"), results.map { it.id })
    assertEquals(listOf("c", "a", "b"), candidates.map { it.id })
    assertSame(candidates.first(), results.first())
  }

  @Test
  fun waitingFilterAndQueryMustBothMatchAcrossAllSixStatuses() {
    for (status in SessionStatus.entries) {
      val session = sample(label = "api-server")
      assertNotNull(status.name, SessionListQuery("apsv").score(session, status))
      assertNull(status.name, SessionListQuery("zzzz").score(session, status))
      val waiting = SessionListQuery("apsv", SessionListFilter.Waiting)
      assertEquals(status.name, status == SessionStatus.Waiting, waiting.score(session, status) != null)
      assertEquals(status.name, status == SessionStatus.Waiting, waiting.copy(text = "").score(session, status) != null)
      assertNull(status.name, waiting.copy(text = "zzzz").score(session, status))
    }
  }

  @Test
  fun noCandidatesAndNoMatchesAreEmptyResults() {
    assertTrue(SessionListQuery().search(emptyList<Session>(), { it }, { SessionStatus.Running }).isEmpty())
    assertTrue(SessionListQuery("zzzz").search(listOf(sample()), { it }, { SessionStatus.Running }).isEmpty())
  }

  private fun sample(id: String = "s1", label: String = "unrelated") = Session(
    id = id,
    label = label,
    hostId = null,
    host = "host.test",
    port = 22,
    username = "user",
    cliTool = CliToolId.CLAUDE,
    customBinary = null,
    workingDir = null,
    flags = emptyList(),
    env = emptyMap(),
    privateKeyId = null,
    createdAtMillis = 100,
    updatedAtMillis = 200,
  )
}
