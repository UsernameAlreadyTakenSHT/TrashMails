package io.github.usernamealreadytakensht.trashmails.ui

import io.github.usernamealreadytakensht.trashmails.data.ProviderException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.UnknownHostException

class MessagesTest {
    @Test
    fun providerText_isShownUnchanged_whenItIsOneCleanLine() {
        val text = "DropMail.me asks for a captcha right now: try again later"
        assertEquals(text, ProviderException(text).userMessage("fallback"))
    }

    @Test
    fun providerText_losesControlCharactersAndLineBreaks_andIsBounded() {
        assertEquals("tempmail.lol: Invalid domain selected", ProviderException("tempmail.lol:\u0000 Invalid\r\n  domain\tselected").userMessage("fallback"))
        assertEquals(200, ProviderException("x".repeat(500)).userMessage("fallback").length)
        assertEquals("fallback", ProviderException("\n\t").userMessage("fallback"))
    }

    @Test
    fun transportErrors_getTheirOwnWording() {
        assertEquals("No internet connection", UnknownHostException("dropmail.me").userMessage("fallback"))
        assertEquals("fallback", IllegalStateException("boom").userMessage("fallback"))
    }
}
