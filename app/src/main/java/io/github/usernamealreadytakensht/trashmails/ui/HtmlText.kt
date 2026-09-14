package io.github.usernamealreadytakensht.trashmails.ui

/**
 * Plain-text rendering of an HTML email, for reading without a WebView and for copying.
 * Block elements become line breaks, list items get a bullet, and a link whose text is not its
 * address is followed by that address in angle brackets so nothing is hidden behind anchor text.
 */
fun htmlToText(html: String): String =
    html.replace(Regex("""(?is)<(script|style|head)[^>]*>.*?</\1>"""), "")
        .replace(Regex("""(?is)<!--.*?-->"""), "")
        .replace(Regex("""(?is)<a\s[^>]*href\s*=\s*["']?([^"'\s>]+)["']?[^>]*>(.*?)</a>""")) { m ->
            val href = m.groupValues[1]
            val text = m.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
            when {
                text.isEmpty() -> href
                text.equals(href, ignoreCase = true) -> text
                // Placeholders, turned into < > once the tags are gone (angle brackets would be stripped).
                else -> "$text $OPEN$href$CLOSE"
            }
        }
        .replace(Regex("""(?i)<li[^>]*>"""), "\n• ")
        .replace(Regex("""(?i)<br\s*/?>|</p>|</div>|</tr>|</h[1-6]>|</blockquote>|</table>"""), "\n")
        .replace(Regex("""(?i)</t[dh]>"""), " ")
        .replace(Regex("<[^>]+>"), "")
        .replace(OPEN, '<').replace(CLOSE, '>')
        .let(::decodeEntities)
        .lines().map { it.trim() }
        .fold(StringBuilder()) { out, line ->
            // Collapse runs of blank lines to one.
            if (line.isNotEmpty() || (out.isNotEmpty() && !out.endsWith("\n\n"))) out.append(line).append('\n')
            out
        }
        .toString().trim()

/** Stand-ins for the angle brackets around a link address, so tag stripping leaves them alone. */
private const val OPEN = ''
private const val CLOSE = ''

private val NAMED_ENTITIES = mapOf(
    "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "copy" to "©", "reg" to "®", "trade" to "™", "hellip" to "…", "mdash" to "—", "ndash" to "–",
    "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "euro" to "€", "pound" to "£",
    "middot" to "·", "bull" to "•", "laquo" to "«", "raquo" to "»", "eacute" to "é", "egrave" to "è",
    "agrave" to "à", "ccedil" to "ç", "ugrave" to "ù", "ecirc" to "ê", "ocirc" to "ô", "icirc" to "î",
)

/** Numeric (`&#233;`, `&#xE9;`) and the common named entities; unknown ones are left as they are. */
private fun decodeEntities(s: String): String =
    s.replace(Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);""")) { m ->
        val ref = m.groupValues[1]
        when {
            ref.startsWith("#x") -> ref.substring(2).toIntOrNull(16)?.let { codePointToString(it) }
            ref.startsWith("#") -> ref.substring(1).toIntOrNull()?.let { codePointToString(it) }
            else -> NAMED_ENTITIES[ref]
        } ?: m.value
    }

private fun codePointToString(cp: Int): String? =
    if (cp in 1..0x10FFFF) String(Character.toChars(cp)) else null
