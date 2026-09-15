package io.github.usernamealreadytakensht.trashmails.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
