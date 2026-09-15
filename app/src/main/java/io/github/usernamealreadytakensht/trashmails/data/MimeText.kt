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

    fun parse(raw: String): Parts = parseEntity(raw.replace("\r\n", "\n"))

    private fun parseEntity(entity: String): Parts {
        val split = entity.indexOf("\n\n")
        val (headerBlock, body) = if (split < 0) entity to "" else entity.substring(0, split) to entity.substring(split + 2)
        val headers = unfold(headerBlock)
        val contentType = headers["content-type"].orEmpty()
        val mediaType = contentType.substringBefore(';').trim().lowercase()

        if (mediaType.startsWith("multipart/")) {
            val boundary = parameter(contentType, "boundary") ?: return Parts(null, null)
            var text: String? = null
            var html: String? = null
            // A leading newline so the first boundary, at the very start of the body, splits like the others.
            for (part in ("\n" + body).split("\n--$boundary").drop(1)) {
                if (part.startsWith("--")) break
                val sub = parseEntity(part.removePrefix("\n"))
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

    /** Header names lowercased, continuation lines joined. */
    private fun unfold(block: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var name: String? = null
        for (line in block.lineSequence()) {
            if (line.firstOrNull()?.isWhitespace() == true && name != null) {
                out[name] = out[name] + " " + line.trim()
            } else {
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                name = line.substring(0, colon).trim().lowercase()
                out[name] = line.substring(colon + 1).trim()
            }
        }
        return out
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
