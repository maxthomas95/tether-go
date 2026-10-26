package com.tether.go.chats

import com.tether.go.cli.ShellQuote

/**
 * One Claude Code conversation found on a remote host. Built from the CLI's own
 * transcript files, never from the terminal stream.
 */
data class RemoteChat(
  val sessionId: String,
  /** Claude's encoded project directory name, e.g. `-home-max-repo-tether`. */
  val projectKey: String,
  /** Working directory recorded in the transcript, when one was found. */
  val cwd: String?,
  /** `/rename` title, else Claude's generated `ai-title`. */
  val title: String?,
  val firstPrompt: String?,
  val lastPrompt: String?,
  val modifiedEpochSeconds: Long,
) {
  val displayTitle: String
    get() = title ?: firstPrompt ?: lastPrompt ?: "Untitled chat"

  /**
   * Where `claude --resume` must run: Claude looks transcripts up by the
   * current directory. Falls back to a best-effort decode of the project key,
   * which is lossy (`-` is ambiguous) but right for typical repo paths.
   */
  val workingDir: String
    get() = cwd ?: projectKey.replace('-', '/')
}

/**
 * Lists Claude Code conversations on a remote host by reading
 * `${CLAUDE_CONFIG_DIR:-~/.claude}/projects/<encoded-cwd>/<uuid>.jsonl`, the
 * layout desktop Tether's transcript picker reads locally.
 *
 * The listing is a read-only side channel over an SSH exec channel. It needs
 * only POSIX `sh`, `ls`, `grep`, `cut`, `sed`, `tail` and `stat` on the host
 * (no `jq`/Python), and caps how much of each transcript it ships back. The
 * extracted lines can be truncated JSON, so parsing is deliberately lenient.
 */
object ClaudeChatCatalog {
  const val DEFAULT_LIMIT = 60

  private const val CLAUDE_HOME = "\"\${CLAUDE_CONFIG_DIR:-\$HOME/.claude}\""
  private const val USER_PROMPT_MARKER = "\"role\":\"user\",\"content\":\""
  private const val MAX_PROMPT_CHARS = 240
  private val UUID_RE = Regex("^[0-9a-fA-F-]{36}$")

  /**
   * Shell command that prints one record per transcript, newest first:
   *
   * ```
   * F <mtime-epoch> <projectKey>/<uuid>.jsonl
   * D "cwd":"..."                  (first cwd field)
   * U {...user prompt line...}      (up to 4, cut to 2000 bytes)
   * T {"type":"custom-title",...}   (latest)
   * A {"type":"ai-title",...}       (latest)
   * L {"type":"last-prompt",...}    (latest)
   * ```
   *
   * Wrapped in `sh -c` so it runs the same under a non-POSIX login shell.
   */
  fun listCommand(limit: Int = DEFAULT_LIMIT): String {
    val script = """
      cd $CLAUDE_HOME/projects 2>/dev/null || exit 0
      ls -t -- */*.jsonl 2>/dev/null | head -n ${limit.coerceIn(1, 500)} | while IFS= read -r f; do
        m=${'$'}(stat -c %Y -- "${'$'}f" 2>/dev/null || stat -f %m -- "${'$'}f" 2>/dev/null || echo 0)
        echo "F ${'$'}m ${'$'}f"
        grep -m 1 -o '"cwd":"[^"]*"' -- "${'$'}f" | sed 's/^/D /'
        grep -m 4 '$USER_PROMPT_MARKER' -- "${'$'}f" | cut -c1-2000 | sed 's/^/U /'
        grep '^{"type":"custom-title"' -- "${'$'}f" | tail -n 1 | cut -c1-600 | sed 's/^/T /'
        grep '^{"type":"ai-title"' -- "${'$'}f" | tail -n 1 | cut -c1-600 | sed 's/^/A /'
        grep '^{"type":"last-prompt"' -- "${'$'}f" | tail -n 1 | cut -c1-1000 | sed 's/^/L /'
      done
    """.trimIndent()
    return "sh -c ${ShellQuote.quoteArg(script)}"
  }

  /** Shell command that prints `yes` when a transcript for [sessionId] exists. */
  fun transcriptExistsCommand(sessionId: String): String {
    require(UUID_RE.matches(sessionId)) { "Claude session ids are UUIDs" }
    val script = "ls $CLAUDE_HOME/projects/*/$sessionId.jsonl >/dev/null 2>&1 && echo yes"
    return "sh -c ${ShellQuote.quoteArg(script)}"
  }

  fun transcriptExists(output: String): Boolean = output.trim() == "yes"

  /** Parse [listCommand] output, newest first, dropping chats with no prompt. */
  fun parse(output: String): List<RemoteChat> {
    val chats = mutableListOf<RemoteChat>()
    var current: Builder? = null
    for (line in output.lineSequence()) {
      if (line.length < 2 || line[1] != ' ') continue
      val body = line.substring(2)
      if (line[0] == 'F') {
        current?.build()?.let(chats::add)
        current = Builder.fromHeader(body)
      } else {
        current?.accept(line[0], body)
      }
    }
    current?.build()?.let(chats::add)
    return chats.sortedByDescending { it.modifiedEpochSeconds }
  }

  /**
   * Turn a raw prompt into a one-line preview. Returns null for prompts that are
   * not something the user typed: slash-command wrappers, local-command output,
   * and caveat/system blocks all start with `<`.
   */
  internal fun cleanPrompt(raw: String?): String? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (text.startsWith("<") || text.startsWith("Caveat:")) return null
    val singleLine = text.replace(Regex("\\s+"), " ")
    return if (singleLine.length > MAX_PROMPT_CHARS) singleLine.take(MAX_PROMPT_CHARS) + "…" else singleLine
  }

  private class Builder(
    private val sessionId: String,
    private val projectKey: String,
    private val modified: Long,
  ) {
    private var cwd: String? = null
    private var firstPrompt: String? = null
    private var customTitle: String? = null
    private var aiTitle: String? = null
    private var lastPrompt: String? = null

    fun accept(tag: Char, body: String) {
      when (tag) {
        'D' -> cwd = cwd ?: LenientJson.stringAfter(body, "\"cwd\":\"")?.takeIf { it.isNotBlank() }
        'U' -> firstPrompt = firstPrompt ?: cleanPrompt(LenientJson.stringAfter(body, USER_PROMPT_MARKER))
        'T' -> customTitle = cleanTitle(LenientJson.stringAfter(body, "\"customTitle\":\""))
        'A' -> aiTitle = cleanTitle(LenientJson.stringAfter(body, "\"aiTitle\":\""))
        'L' -> lastPrompt = cleanPrompt(LenientJson.stringAfter(body, "\"lastPrompt\":\""))
      }
    }

    fun build(): RemoteChat? {
      if (firstPrompt == null && lastPrompt == null && customTitle == null && aiTitle == null) return null
      return RemoteChat(
        sessionId = sessionId,
        projectKey = projectKey,
        cwd = cwd,
        title = customTitle ?: aiTitle,
        firstPrompt = firstPrompt,
        lastPrompt = lastPrompt,
        modifiedEpochSeconds = modified,
      )
    }

    private fun cleanTitle(raw: String?): String? =
      raw?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }

    companion object {
      /** `<mtime> <projectKey>/<uuid>.jsonl`; null for anything else. */
      fun fromHeader(body: String): Builder? {
        val space = body.indexOf(' ')
        if (space <= 0) return null
        val modified = body.substring(0, space).toLongOrNull() ?: 0L
        val path = body.substring(space + 1)
        val slash = path.lastIndexOf('/')
        if (slash <= 0 || !path.endsWith(".jsonl")) return null
        val id = path.substring(slash + 1).removeSuffix(".jsonl")
        if (!UUID_RE.matches(id)) return null
        return Builder(sessionId = id, projectKey = path.substring(0, slash), modified = modified)
      }
    }
  }
}

/** Tolerant JSON string extraction for possibly-truncated JSONL lines. */
internal object LenientJson {
  /**
   * Decode the JSON string that starts right after [marker] (which must end
   * with the opening quote). Stops at the closing quote or, for a truncated
   * line, at end of input.
   */
  fun stringAfter(line: String, marker: String): String? {
    val start = line.indexOf(marker)
    if (start < 0) return null
    val out = StringBuilder()
    var i = start + marker.length
    while (i < line.length) {
      val ch = line[i]
      when {
        ch == '"' -> return out.toString()
        ch != '\\' -> out.append(ch)
        i + 1 >= line.length -> return out.toString()
        else -> {
          i += 1
          i += appendEscape(line, i, out)
        }
      }
      i += 1
    }
    return out.toString()
  }

  /** Append the escape whose code is at [index]; returns extra chars consumed. */
  private fun appendEscape(line: String, index: Int, out: StringBuilder): Int {
    when (val code = line[index]) {
      'n' -> out.append('\n')
      't' -> out.append('\t')
      'r' -> out.append('\r')
      'b' -> out.append('\b')
      'f' -> out.append('\u000C')
      'u' -> {
        val hex = line.substring(index + 1, minOf(index + 5, line.length))
        val value = hex.takeIf { it.length == 4 }?.toIntOrNull(16) ?: return hex.length
        out.append(value.toChar())
        return 4
      }
      else -> out.append(code)
    }
    return 0
  }
}
