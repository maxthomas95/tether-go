package com.tether.go.session

import com.tether.go.cli.CliToolId
import com.tether.go.cli.CliToolRegistry
import java.util.Locale

enum class SessionListFilter {
  All,
  Waiting,
}

/**
 * Metadata-only discovery, ported from desktop Tether's session-search.ts.
 * A query is a case-insensitive subsequence of at least one field, not a regex.
 * Never searches terminal output, environment values, flags, or credentials.
 * Hiding a row does not affect the selected session or connection lifecycle.
 */
data class SessionListQuery(
  val text: String = "",
  val filter: SessionListFilter = SessionListFilter.All,
) {
  private val normalized = text.trim().lowercase(Locale.ROOT)
  val isSearching: Boolean get() = normalized.isNotEmpty()

  /** Null means no match; blank text has a neutral score and natural order. */
  fun score(session: Session, status: SessionStatus): Double? {
    if (filter == SessionListFilter.Waiting && !status.needsAttention) return null
    if (!isSearching) return 0.0
    val tool = CliToolRegistry.byId(session.cliTool)
    val scores = listOfNotNull(
      scoreField(normalized, session.label)?.times(1.0),
      session.workingDir?.let { scoreField(normalized, it)?.times(0.6) },
      scoreField(normalized, session.endpointLabel)?.times(0.45),
      // Aliases are one CLI field: they must not inflate corroboration scores.
      listOfNotNull(
        session.cliTool.id,
        tool.displayName,
        tool.binaryName,
        session.customBinary.takeIf { session.cliTool == CliToolId.CUSTOM },
      )
        .mapNotNull { scoreField(normalized, it) }.maxOrNull()?.times(0.4),
    )
    val best = scores.maxOrNull() ?: return null
    return best + (scores.sum() - best) * 0.15
  }

  /** Stable ties preserve the input order; candidates themselves are unchanged. */
  fun <T> search(
    candidates: List<T>,
    session: (T) -> Session,
    status: (T) -> SessionStatus,
  ): List<T> = candidates.mapIndexedNotNull { index, candidate ->
    score(session(candidate), status(candidate))?.let { RankedCandidate(candidate, it, index) }
  }.sortedWith(compareByDescending<RankedCandidate<T>> { it.score }.thenBy { it.index })
    .map { it.candidate }
}

private data class RankedCandidate<T>(val candidate: T, val score: Double, val index: Int)

/** Linear greedy subsequence walk: no user-controlled regex or backtracking. */
private fun scoreField(query: String, text: String): Double? {
  val lowerText = text.lowercase(Locale.ROOT)
  if (lowerText.isEmpty()) return null
  var queryIndex = 0
  var previousMatch = -2
  var bonus = 0
  for (textIndex in lowerText.indices) {
    if (lowerText[textIndex] != query[queryIndex]) continue
    if (textIndex == previousMatch + 1) bonus += 3
    if (textIndex == 0) {
      bonus += 5
    } else if (lowerText[textIndex - 1] in " /\\-_.") {
      bonus += 2
    }
    previousMatch = textIndex
    queryIndex++
    if (queryIndex == query.length) {
      return 10 + bonus + query.length.toDouble() / lowerText.length * 10
    }
  }
  return null
}
