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
    fun prependedWithoutAHead_andLeftAloneWhenDeclared() {
        assertEquals("$meta<p>x</p>", withViewport("<p>x</p>"))
        val declared = "<meta name=\"viewport\" content=\"width=500\"><p>x</p>"
        assertEquals(declared, withViewport(declared))
    }

    @Test
    fun displayHost_neverShowsALookAlikeAlone() {
        assertEquals("paypal.com", displayHost("paypal.com"))
        assertEquals("p\u0430ypal.com (xn--pypal-4ve.com)", displayHost("p\u0430ypal.com"))
        // A variation selector made IDN.toASCII throw, and the bare host was shown.
        val tricky = "p\u0430ypal.com\uDB40\uDD00"
        val shown = displayHost(tricky)
        assertTrue(shown, shown.startsWith("$tricky (") && (shown.contains("xn--") || shown.contains("\\u{430}")))
    }

    @Test
    fun privacyMeta_comesFirst_withThePolicyOnlyWhileBlocked() {
        val blocked = withPrivacyMeta("<head><title>t</title></head>", blockRemote = true)
        assertTrue(blocked.startsWith("<meta http-equiv=\"x-dns-prefetch-control\" content=\"off\"><meta http-equiv=\"Content-Security-Policy\""))
        val open = withPrivacyMeta("<p>x</p>", blockRemote = false)
        assertEquals("<meta http-equiv=\"x-dns-prefetch-control\" content=\"off\"><p>x</p>", open)
    }

    @Test
    fun mailto_keepsRecipientsSubjectAndBodyOnly() {
        assertEquals(
            "mailto:a@b.c?subject=Hi&body=x%20y",
            plainMailto("mailto:a@b.c?cc=spy@evil.com&subject=Hi&bcc=x@y.z&attach=/sdcard/a&body=x%20y"),
        )
        assertEquals("mailto:a@b.c", plainMailto("mailto:a@b.c?bcc=x@y.z"))
    }

    @Test
    fun unclosedHeadRuns_stayLinear() {
        val start = System.nanoTime()
        withViewport("<head".repeat(400_000))
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 1_000)
    }
}
