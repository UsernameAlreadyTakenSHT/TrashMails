package io.github.usernamealreadytakensht.trashmails.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryBoundsTest {
    @Test
    fun headersAreBounded_andBidiControlsDropped() {
        val s = MailSummary(id = "1", from = "evil\u202Emoc.lapyap@x", subject = "s".repeat(500_000), date = 1, to = "a\u2066b")
        val b = s.bounded()
        assertEquals("evilmoc.lapyap@x", b.from)
        assertEquals(1_000, b.subject.length)
        assertEquals("ab", b.to)
    }

    @Test
    fun headersAreOneLine() {
        val s = MailSummary(id = "1", from = "service@paypal.com\n\n <x@evil>", subject = "a\r\nb", date = 1).bounded()
        assertEquals("service@paypal.com <x@evil>", s.from)
        assertEquals("a b", s.subject)
    }

    @Test
    fun aBodyLosesItsBidiControls_soALinkReadsAsWhatItIs() {
        val body = "Click ‮⁦https://evil.example/⁩⁦https://bank.com/login?next=⁩‬ now"
        assertEquals("Click https://evil.example/https://bank.com/login?next= now", body.withoutBidiControls())
    }

    @Test
    fun serverAddresses_mustBePlain() {
        assertEquals("ab.c+d@sub.example.com", checkedAddress("ab.c+d@sub.example.com", Provider.MAIL_TM))
        for (bad in listOf("a@b", "a\n@b.com", "a@b.com\u202E", "a".repeat(100) + "@b.com", "a@b.com x", "@b.com")) {
            assertTrue(bad, runCatching { checkedAddress(bad, Provider.MAIL_TM) }.isFailure)
        }
    }

    @Test
    fun pathSegments_cannotWalkThePath() {
        assertEquals("a%2F..%2Fb%20c", pathSegment("a/../b c"))
        for (bad in listOf("", ".", "..")) assertTrue(runCatching { pathSegment(bad) }.isFailure)
    }

    @Test
    fun oversizedIdsOrRefs_areNotSane() {
        assertTrue(MailSummary(id = "abc", from = "", subject = "", date = 1, ref = mapOf("key" to "k")).isSane())
        assertFalse(MailSummary(id = "x".repeat(5_000), from = "", subject = "", date = 1).isSane())
        assertFalse(MailSummary(id = "1", from = "", subject = "", date = 1, ref = mapOf("key" to "k".repeat(5_000))).isSane())
    }
}
