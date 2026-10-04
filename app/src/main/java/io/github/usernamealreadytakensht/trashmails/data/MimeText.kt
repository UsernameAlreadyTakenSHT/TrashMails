package io.github.usernamealreadytakensht.trashmails.data

import java.nio.charset.Charset
import java.util.Base64

/**
 * Just enough MIME to read a raw RFC 822 message (what Maildrop hands out when it has no HTML):
 * headers are split off, multipart bodies are walked, and the text/plain and text/html parts are
 * decoded (quoted-printable, base64, declared charset). Attachments and anything else are skipped.
 */
object MimeText {
    /** The readable parts of a message; either may be missing. */
    data class Parts(val text: String?, val html: String?)

    fun parse(raw: String): Parts = parseEntity(raw.replace("\r\n", "\n"), 0)

    /** Deepest multipart nesting walked: real mail stays within three or four, a crafted one could nest thousands. */
    private const val MAX_DEPTH = 8

    private fun parseEntity(entity: String, depth: Int): Parts {
        val split = entity.indexOf("\n\n")
        val (headerBlock, body) = if (split < 0) entity to "" else entity.substring(0, split) to entity.substring(split + 2)
        val headers = unfold(headerBlock)
        val contentType = headers["content-type"].orEmpty()
        val mediaType = contentType.substringBefore(';').trim().lowercase()

        if (mediaType.startsWith("multipart/")) {
            if (depth >= MAX_DEPTH) return Parts(null, null)
            val boundary = parameter(contentType, "boundary") ?: return Parts(null, null)
            var text: String? = null
            var html: String? = null
            // A leading newline so the first boundary, at the very start of the body, splits like the others.
            for (part in ("\n" + body).split("\n--$boundary").drop(1)) {
                if (part.startsWith("--")) break
                val sub = parseEntity(part.removePrefix("\n"), depth + 1)
                text = text ?: sub.text
                html = html ?: sub.html
            }
            return Parts(text, html)
        }

        val decoded = decode(body, headers["content-transfer-encoding"], parameter(contentType, "charset"))
        return when {
            mediaType == "text/html" -> Parts(null, decoded)
            mediaType.isEmpty() || mediaType == "text/plain" -> Parts(decoded, null)
            else -> Parts(null, null)
        }
    }

    /** Longest header value kept; real ones (even a long Content-Type) stay far below. */
    private const val MAX_HEADER = 8 * 1024

    /**
     * Header names lowercased, continuation lines joined. Each value is built in a StringBuilder
     * and bounded: re-concatenating the whole value at every continuation line was quadratic on a
     * header folded over hundreds of thousands of lines.
     */
    private fun unfold(block: String): Map<String, String> {
        val out = LinkedHashMap<String, StringBuilder>()
        var current: StringBuilder? = null
        for (line in block.lineSequence()) {
            if (line.firstOrNull()?.isWhitespace() == true && current != null) {
                if (current.length < MAX_HEADER) current.append(' ').append(line.trim())
            } else {
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val name = line.substring(0, colon).trim().lowercase()
                current = StringBuilder(line.substring(colon + 1).trim().take(MAX_HEADER)).also { out[name] = it }
            }
        }
        return out.mapValues { (_, v) -> v.toString().take(MAX_HEADER) }
    }

    /** `name=value` or `name="value"` in a header's parameter list. */
    private fun parameter(header: String, name: String): String? =
        header.split(';').drop(1).map { it.trim() }
            .firstOrNull { it.substringBefore('=').trim().equals(name, ignoreCase = true) }
            ?.substringAfter('=')?.trim()?.trim('"')?.takeIf { it.isNotEmpty() }

    private fun decode(body: String, encoding: String?, charsetName: String?): String {
        val charset = charsetName?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return when (encoding?.trim()?.lowercase()) {
            "quoted-printable" -> String(quotedPrintable(body), charset)
            "base64" -> runCatching { String(Base64.getMimeDecoder().decode(body), charset) }.getOrDefault(body)
            else -> body
        }.trimEnd()
    }

    private fun quotedPrintable(s: String): ByteArray {
        val out = java.io.ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '=' && i + 1 < s.length) {
                if (s[i + 1] == '\n') { i += 2; continue } // soft line break
                val hex = s.substring(i + 1, minOf(i + 3, s.length))
                val value = hex.takeIf { it.length == 2 }?.toIntOrNull(16)
                if (value != null) { out.write(value); i += 3; continue }
            }
            // Non-ASCII in a QP body is already broken; keep its UTF-8 bytes rather than drop it.
            if (c.code < 0x80) out.write(c.code) else out.write(c.toString().toByteArray())
            i++
        }
        return out.toByteArray()
    }
}
