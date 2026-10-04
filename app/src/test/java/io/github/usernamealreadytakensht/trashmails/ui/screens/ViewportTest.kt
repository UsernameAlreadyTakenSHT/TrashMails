package io.github.usernamealreadytakensht.trashmails.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportTest {
    private val meta = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"

    @Test
    fun insertedAfterTheHeadTag_notAfterHeader() {
        assertEquals("<html><header>x</header><head lang=en>$meta</head>", withViewport("<html><header>x</header><head lang=en></head>"))
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
        assertEquals("pаypal.com (xn--pypal-4ve.com)", displayHost("pаypal.com"))
        // A variation selector made IDN.toASCII throw, and the bare host was shown.
        val tricky = "pаypal.com󠄀"
        val shown = displayHost(tricky)
        assertTrue(shown, shown.startsWith("$tricky (") && (shown.contains("xn--") || shown.contains("\\u{430}")))
    }

    @Test
    fun unclosedHeadRuns_stayLinear() {
        val start = System.nanoTime()
        withViewport("<head".repeat(400_000))
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 1_000)
    }
}
