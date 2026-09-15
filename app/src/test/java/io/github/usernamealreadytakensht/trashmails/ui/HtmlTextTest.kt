package io.github.usernamealreadytakensht.trashmails.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTextTest {
    @Test
    fun blocksBecomeLines_andEntitiesAreDecoded() {
        val html = "<html><head><style>p{}</style></head><body><p>Hello&nbsp;<b>world</b></p><p>caf&eacute; &#233; &#xE9; &amp; 3 &lt; 4</p></body></html>"
        // Paragraphs are separated by one blank line.
        assertEquals("Hello world\n\ncafé é é & 3 < 4", htmlToText(html))
    }

    @Test
    fun linkAddressIsShownWhenHiddenBehindText() {
        assertEquals(
            "Click here <https://example.com/x>\nhttps://example.com/y",
            htmlToText("<a href=\"https://example.com/x\">Click <i>here</i></a><br><a href='https://example.com/y'>https://example.com/y</a>"),
        )
    }

    @Test
    fun listItemsGetBullets_andBlankRunsCollapse() {
        assertEquals("Items\n\n• one\n• two", htmlToText("<h1>Items</h1><br><br><br><ul><li>one</li><li>two</li></ul>"))
    }

    @Test
    fun scriptsCommentsAndUnknownEntitiesAreHandled() {
        assertEquals("a &unknown; b", htmlToText("<script>alert(1)</script>a &unknown; <!-- c -->b"))
    }

    @Test
    fun openingBlockTagsBreakLines_andLinkIsNotAListItem() {
        assertEquals("a\nb", htmlToText("<div>a<p>b"))
        assertEquals("x", htmlToText("<link rel=\"stylesheet\" href=\"s.css\">x"))
    }

    @Test
    fun unclosedTagsStayLinear() {
        val hostile = "<a href=x>".repeat(50_000) + "<script>" + "<!--"
        val start = System.nanoTime()
        val out = htmlToText(hostile)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 2_000)
        assertEquals("", out)
    }

    @Test
    fun hugeInputIsTruncatedWithANote() {
        val out = htmlToText("x".repeat(2_000_000))
        assertTrue(out.endsWith("[message truncated]"))
        assertTrue(out.length < 1_100_000)
    }
}
