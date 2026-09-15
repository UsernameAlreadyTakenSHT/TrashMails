package io.github.usernamealreadytakensht.trashmails.ui

/**
 * Plain-text rendering of an HTML email, for reading without a WebView and for copying.
 * Block elements become line breaks, list items get a bullet, and a link whose text is not its
 * address is followed by that address in angle brackets so nothing is hidden behind anchor text.
 *
 * The input is sender-controlled, so every pass is linear: blocks, comments and anchors are
 * found with indexOf scans rather than lazy `.*?` regexes (which turn quadratic on unclosed
 * tags), and the input is capped at [MAX_INPUT] characters.
 */
fun htmlToText(html: String): String {
    val truncated = html.length > MAX_INPUT
    var s = if (truncated) html.substring(0, MAX_INPUT) else html
    for (tag in listOf("script", "style", "head")) s = stripBlocks(s, tag)
    s = stripComments(s)
    s = spellOutLinks(s)
    s = s.replace(LI, "\n• ")
        .replace(BLOCK_BREAK, "\n")
        .replace(CELL_END, " ")
        .replace(TAG, "")
        .replace(OPEN, '<').replace(CLOSE, '>')
    s = decodeEntities(s)
    val out = StringBuilder(s.length)
    for (raw in s.lineSequence()) {
        val line = raw.trim()
        // Collapse runs of blank lines to one.
        if (line.isNotEmpty() || (out.isNotEmpty() && !out.endsWith("\n\n"))) out.append(line).append('\n')
    }
    if (truncated) out.append("\n[message truncated]")
    return out.toString().trim()
}

/** Largest HTML converted; beyond that the text ends with a truncation note. */
private const val MAX_INPUT = 1 shl 20

/** Stand-ins for the angle brackets around a link address, so tag stripping leaves them alone. */
private const val OPEN = ''
private const val CLOSE = ''

private val LI = Regex("""(?i)<li\b[^>]*>""")
private val BLOCK_BREAK = Regex("""(?i)<br\s*/?>|</?(?:p|div|tr|h[1-6]|blockquote|table)\b[^>]*>""")
private val CELL_END = Regex("""(?i)</t[dh]>""")
private val TAG = Regex("<[^>]+>")
private val HREF = Regex("""(?i)\bhref\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
private val ENTITY = Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);""")

/** True when [s] has `<tag` (or `</tag` when [i] points at the `/`) at [i], followed by whitespace, `>` or `/`. */
private fun tagAt(s: String, i: Int, tag: String): Boolean {
    if (!s.regionMatches(i + 1, tag, 0, tag.length, ignoreCase = true)) return false
    val next = s.getOrNull(i + 1 + tag.length) ?: return true
    return next.isWhitespace() || next == '>' || next == '/'
}

private fun indexOfTag(s: String, tag: String, from: Int, closing: Boolean = false): Int {
    val needle = if (closing) "</" else "<"
    var i = s.indexOf(needle, from)
    while (i >= 0) {
        if (tagAt(s, i + needle.length - 1, tag)) return i
        i = s.indexOf(needle, i + 1)
    }
    return -1
}

/** Removes every `<tag>…</tag>` block; an unclosed one is removed to the end, as a browser would hide it. */
private fun stripBlocks(s: String, tag: String): String {
    var from = 0
    val out = StringBuilder(s.length)
    while (true) {
        val start = indexOfTag(s, tag, from)
        if (start < 0) { out.append(s, from, s.length); break }
        out.append(s, from, start)
        val close = indexOfTag(s, tag, start, closing = true)
        if (close < 0) break
        val end = s.indexOf('>', close)
        if (end < 0) break
        from = end + 1
    }
    return out.toString()
}

private fun stripComments(s: String): String {
    var from = 0
    val out = StringBuilder(s.length)
    while (true) {
        val start = s.indexOf("<!--", from)
        if (start < 0) { out.append(s, from, s.length); break }
        out.append(s, from, start)
        val end = s.indexOf("-->", start + 4)
        if (end < 0) break
        from = end + 3
    }
    return out.toString()
}

/** `<a href=x>text</a>` → `text <x>` (placeholder brackets) unless the text already is the address. */
private fun spellOutLinks(s: String): String {
    var from = 0
    val out = StringBuilder(s.length)
    while (true) {
        val start = indexOfTag(s, "a", from)
        if (start < 0) { out.append(s, from, s.length); break }
        val tagEnd = s.indexOf('>', start)
        val close = if (tagEnd < 0) -1 else indexOfTag(s, "a", tagEnd, closing = true)
        if (close < 0) { out.append(s, from, s.length); break }
        val closeEnd = s.indexOf('>', close).let { if (it < 0) s.length - 1 else it }
        out.append(s, from, start)
        val href = HREF.find(s, start)?.takeIf { it.range.first < tagEnd }
            ?.let { m -> m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } }
        val text = s.substring(tagEnd + 1, close).replace(TAG, "").trim()
        out.append(
            when {
                href.isNullOrEmpty() -> text
                text.isEmpty() -> href
                text.equals(href, ignoreCase = true) -> text
                else -> "$text $OPEN$href$CLOSE"
            }
        )
        from = closeEnd + 1
    }
    return out.toString()
}

private val NAMED_ENTITIES = mapOf(
    "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "copy" to "©", "reg" to "®", "trade" to "™", "hellip" to "…", "mdash" to "—", "ndash" to "–",
    "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "euro" to "€", "pound" to "£",
    "middot" to "·", "bull" to "•", "laquo" to "«", "raquo" to "»", "eacute" to "é", "egrave" to "è",
    "agrave" to "à", "ccedil" to "ç", "ugrave" to "ù", "ecirc" to "ê", "ocirc" to "ô", "icirc" to "î",
)

/** Numeric (`&#233;`, `&#xE9;`) and the common named entities; unknown ones are left as they are. */
private fun decodeEntities(s: String): String =
    s.replace(ENTITY) { m ->
        val ref = m.groupValues[1]
        when {
            ref.startsWith("#x") -> ref.substring(2).toIntOrNull(16)?.let { codePointToString(it) }
            ref.startsWith("#") -> ref.substring(1).toIntOrNull()?.let { codePointToString(it) }
            else -> NAMED_ENTITIES[ref]
        } ?: m.value
    }

private fun codePointToString(cp: Int): String? =
    if (cp in 1..0x10FFFF) String(Character.toChars(cp)) else null
