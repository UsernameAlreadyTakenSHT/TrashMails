package io.github.usernamealreadytakensht.trashmails.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeTextTest {
    @Test
    fun singlePartPlainText_dropsHeaders() {
        val raw = "Received: from a\r\n by b\r\nSubject: Hi\r\nContent-Type: text/plain; charset=utf-8\r\n\r\nHello\r\nthere\r\n"
        assertEquals(MimeText.Parts("Hello\nthere", null), MimeText.parse(raw))
    }

    @Test
    fun multipartAlternative_decodesQuotedPrintableAndBase64() {
        val raw = """
            |From: a@example.com
            |Content-Type: multipart/alternative;
            | boundary="XYZ"
            |
            |--XYZ
            |Content-Type: text/plain; charset="utf-8"
            |Content-Transfer-Encoding: quoted-printable
            |
            |Caf=C3=A9 tr=C3=A8s long=
            | mot
            |--XYZ
            |Content-Type: text/html; charset=utf-8
            |Content-Transfer-Encoding: base64
            |
            |PHA+Q2Fmw6k8L3A+
            |--XYZ--
            |""".trimMargin()
        assertEquals(MimeText.Parts("Café très long mot", "<p>Café</p>"), MimeText.parse(raw))
    }

    @Test
    fun attachmentsAreSkipped_andNestedMultipartWalked() {
        val raw = """
            |Content-Type: multipart/mixed; boundary=outer
            |
            |--outer
            |Content-Type: multipart/alternative; boundary=inner
            |
            |--inner
            |Content-Type: text/plain
            |
            |inner text
            |--inner--
            |--outer
            |Content-Type: application/pdf; name=x.pdf
            |Content-Transfer-Encoding: base64
            |
            |JVBERi0=
            |--outer--
            |""".trimMargin()
        assertEquals(MimeText.Parts("inner text", null), MimeText.parse(raw))
    }

    @Test
    fun headersOnlyOrUnknownType_giveNothing() {
        assertEquals(MimeText.Parts("", null), MimeText.parse("Subject: x\n\n"))
        assertNull(MimeText.parse("Content-Type: image/png\n\nabc").text)
    }

    @Test
    fun deepNesting_stopsInsteadOfOverflowing() {
        val levels = 20_000
        val raw = StringBuilder()
        repeat(levels) { raw.append("Content-Type: multipart/mixed; boundary=b${it}x\n\n--b${it}x\n") }
        raw.append("Content-Type: text/plain\n\nhidden\n")
        assertEquals(MimeText.Parts(null, null), MimeText.parse(raw.toString()))
    }

    @Test
    fun aHeaderFoldedOverManyLines_staysLinear() {
        val raw = "Content-Type: multipart/mixed; boundary=b\n\n--b\nContent-Type: text/plain\n" +
            " x\n".repeat(400_000) + "\nbody\n--b--\n"
        val start = System.nanoTime()
        MimeText.parse(raw)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 2_000)
    }

    @Test
    fun aHugeNestedMessage_isParsedWithinBounds() {
        val raw = StringBuilder()
        repeat(5) { raw.append("Content-Type: multipart/mixed; boundary=b${it}x\n\n--b${it}x\n") }
        raw.append("Content-Type: text/plain\n\nfound\n").append("y".repeat(8 shl 20))
        val text = MimeText.parse(raw.toString()).text
        assertTrue(text!!.startsWith("found"))
        assertTrue(text.length < 1 shl 20)
    }

    @Test
    fun nestingWithinTheLimit_isWalked() {
        val raw = StringBuilder()
        repeat(5) { raw.append("Content-Type: multipart/mixed; boundary=b${it}x\n\n--b${it}x\n") }
        raw.append("Content-Type: text/plain\n\nfound\n")
        assertEquals("found", MimeText.parse(raw.toString()).text)
    }
}
