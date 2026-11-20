package io.github.usernamealreadytakensht.trashmails.ui

import io.github.usernamealreadytakensht.trashmails.data.ProviderException
import io.github.usernamealreadytakensht.trashmails.data.HttpException
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

    @Test
    fun aRedirect_isSaidForWhatItIs() {
        assertEquals(
            "api.example.com has moved its API (HTTP 308): this version of the app cannot follow it",
            HttpException(308, "api.example.com").userMessage("fallback"),
        )
        assertEquals("api.example.com answered HTTP 500", HttpException(500, "api.example.com").userMessage("fallback"))
    }
}
