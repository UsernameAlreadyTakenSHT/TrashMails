package io.github.usernamealreadytakensht.trashmails.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportTest {
    private val meta = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"

    @Test
    fun insertedFirst_orAfterALeadingDoctype() {
        assertEquals("$meta<html><head lang=en></head>", withViewport("<html><head lang=en></head>"))
        assertEquals("\uFEFF <!DOCTYPE html>$meta<p>x</p>", withViewport("\uFEFF <!DOCTYPE html><p>x</p>"))
    }

    @Test
    fun explicitPrefetchLinks_areDisarmedWhileBlocked() {
        val html = "<link rel=dns-prefetch href=//a.evil><LINK\nrel=preconnect href=https://b.evil><a href=\"https://x/?a=1&rel=abc\">r</a>"
        val blocked = withPrivacyMeta(html, blockRemote = true)
        assertTrue(blocked, blocked.contains("<x-link rel=dns-prefetch") && blocked.contains("<x-link\nrel=preconnect"))
        // Addresses holding "rel=" are left as they are.
        assertTrue(blocked.contains("https://x/?a=1&rel=abc"))
        assertTrue(withPrivacyMeta(html, blockRemote = false).contains("<link rel=dns-prefetch"))
    }

    @Test
    fun onlyHtmlWhitespaceIsSkipped_beforeTheDoctype() {
        // Text to the HTML parser (it opens the body): the meta must come before it, not after the doctype.
        for (lead in listOf("\u00A0", "\u2028", " \uFEFF", "\uFEFF\uFEFF", "\u000B")) {
            val out = withPrivacyMeta(lead + "<!DOCTYPE html><img src=http://x>", blockRemote = true)
            assertTrue(lead, out.startsWith("<meta http-equiv=\"x-dns-prefetch-control\""))
        }
        val ok = withPrivacyMeta("\uFEFF \t\r\n\u000C<!DOCTYPE html><p>x</p>", blockRemote = true)
        assertTrue(ok.startsWith("\uFEFF \t\r\n\u000C<!DOCTYPE html><meta"))
    }

    @Test
    fun aSendersHeadCannotChooseWhereThePolicyLands() {
        // A <head> in a comment, an attribute or a title, or after body content, left the meta inert.
        for (html in listOf(
            "<!--<head>--><img src=http://x>",
            "<p>x</p><head></head><img src=http://x>",
            "<div title='<head>'></div><img src=http://x>",
            "<title><head></title><img src=http://x>",
        )) {
            assertTrue(html, withPrivacyMeta(html, blockRemote = true).startsWith("<meta http-equiv=\"x-dns-prefetch-control\""))
        }
    }

    @Test
    fun prependedWithoutAHead_andBeforeOneDeclared() {
        assertEquals("$meta<p>x</p>", withViewport("<p>x</p>"))
        // The email's own one comes after ours, and wins.
        val declared = "<meta name=\"viewport\" content=\"width=500\"><p>x</p>"
        assertEquals("$meta$declared", withViewport(declared))
    }

    @Test
    fun displayHost_neverShowsALookAlikeAlone() {
        assertEquals("paypal.com", displayHost("paypal.com"))
        assertEquals("xn--pypal-4ve.com (p\u0430ypal.com)", displayHost("p\u0430ypal.com"))
        // A variation selector made IDN.toASCII throw, and the bare host was shown.
        val tricky = "p\u0430ypal.com\uDB40\uDD00"
        val shown = displayHost(tricky)
        assertTrue(shown, shown.endsWith(" ($tricky)") && (shown.startsWith("xn--") || shown.contains("\\u{430}")))
    }

    @Test
    fun privacyMeta_comesFirst_withThePolicyOnlyWhileBlocked() {
        val blocked = withPrivacyMeta("<head><title>t</title></head>", blockRemote = true)
        assertTrue(blocked.startsWith("<meta http-equiv=\"x-dns-prefetch-control\" content=\"off\"><meta http-equiv=\"Content-Security-Policy\""))
        val open = withPrivacyMeta("<p>x</p>", blockRemote = false)
        assertEquals("<meta http-equiv=\"x-dns-prefetch-control\" content=\"off\"><p>x</p>", open)
    }

    @Test
    fun mailtoDialog_showsEveryRecipient_oneLineEach() {
        val padded = "mailto:support@bank.com?subject=Hi" + "%0A".repeat(300) + "&to=attacker@evil.com"
        assertEquals("support@bank.com\nattacker@evil.com", mailtoRecipients(padded))
        assertEquals("a@b.c\nd@e.f", mailtoRecipients("mailto:a@b.c,%E2%80%AEd@e.f%0A"))
    }

    @Test
    fun mailto_keepsRecipientsSubjectAndBodyOnly_decoded() {
        assertEquals(
            MailDraft(listOf("a@b.c"), "Hi", "x y"),
            parseMailto("mailto:a@b.c?cc=spy@evil.com&subject=Hi&bcc=x@y.z&attach=/sdcard/a&body=x%20y"),
        )
        assertEquals(MailDraft(listOf("a@b.c"), null, null), parseMailto("mailto:a@b.c?bcc=x@y.z"))
    }

    @Test
    fun mailto_anEncodedAmpersandCannotSmuggleAField() {
        val draft = parseMailto("mailto:me@bank.com?subject=Hi%26cc%3Devil%40attacker.com%26bcc%3Dspy%40attacker.com")
        assertEquals(listOf("me@bank.com"), draft.to)
        assertEquals("Hi&cc=evil@attacker.com&bcc=spy@attacker.com", draft.subject)
        // An encoded "?" in the address part: decoded, it is no address and goes.
        assertTrue(parseMailto("mailto:me@bank.com%3Fbcc%3Dspy@x.y").to.isEmpty())
    }

    @Test
    fun unclosedHeadRuns_stayLinear() {
        val start = System.nanoTime()
        withViewport("<head".repeat(400_000))
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 1_000)
    }
}
