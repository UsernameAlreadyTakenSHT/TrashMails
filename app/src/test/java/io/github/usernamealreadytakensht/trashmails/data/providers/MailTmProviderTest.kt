package io.github.usernamealreadytakensht.trashmails.data.providers

import io.github.usernamealreadytakensht.trashmails.data.Inbox
import io.github.usernamealreadytakensht.trashmails.data.MailSummary
import io.github.usernamealreadytakensht.trashmails.data.Provider
import io.github.usernamealreadytakensht.trashmails.data.ProviderException
import io.github.usernamealreadytakensht.trashmails.data.providers.FakeHttp.Companion.error
import io.github.usernamealreadytakensht.trashmails.data.providers.FakeHttp.Companion.fixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MailTmProviderTest {
    private val inbox = Inbox(
        id = "6aa87175a29c4e703e015c72", provider = Provider.MAIL_TM,
        address = "fixture84399063@uberip.com", token = "Fixture-Pass-123",
    )

    private fun http() = FakeHttp()
        .on("/domains", fixture("mailtm_domains"))
        .on("/accounts/", fixture("mailtm_account"))
        .on("/accounts", fixture("mailtm_account"))
        .on("/token", fixture("mailtm_token"))
        .on("/messages/", fixture("mailtm_message"))
        .on("/messages", fixture("mailtm_messages"))

    @Test
    fun createInbox_picksTheActiveDomain_andKeepsThePassword() = runBlocking {
        val http = http()
        val created = MailTmProvider(http).createInbox("Fixture")

        assertEquals("6aa87175a29c4e703e015c72", created.id)
        assertEquals("fixture84399063@uberip.com", created.address)
        assertEquals(24, created.token!!.length)
        val post = http.calls.single { it.method == "POST" }
        assertTrue(post.url.endsWith("/accounts"))
        assertTrue(post.body!!.contains("\"address\":\"fixture@uberip.com\""))
        assertTrue(post.body.contains("\"password\":\"${created.token}\""))
    }

    @Test
    fun createInbox_explainsA422() = runBlocking {
        val http = FakeHttp()
            .on("/domains", fixture("mailtm_domains"))
            .on("/accounts", error(422, FakeHttp.fixture("mailtm_422")()))
        val e = assertThrows(ProviderException::class.java) { runBlocking { MailTmProvider(http).createInbox("admin") } }
        assertEquals("mail.tm: address: The username \"admin\" is not valid.", e.message)
    }

    @Test
    fun listMessages_fetchesTheTokenOnce_andSortsNewestFirst() = runBlocking {
        val http = http()
        val provider = MailTmProvider(http)

        val list = provider.listMessages(inbox)
        provider.listMessages(inbox)

        assertEquals(listOf("Second", "First"), list.map(MailSummary::subject))
        assertEquals("alice@example.com", list[0].from)
        // No address on the sender: the display name is used instead.
        assertEquals("Bob", list[1].from)
        assertEquals(1, http.urls("POST").count { it.endsWith("/token") })
        val listing = http.calls.filter { it.url.endsWith("/messages") }
        assertEquals(2, listing.size)
        assertTrue(listing.all { it.headers["Authorization"]!!.startsWith("Bearer eyJ") })
    }

    @Test
    fun listMessages_refreshesTheTokenOnceOn401() = runBlocking {
        val http = FakeHttp()
            .on("/token", fixture("mailtm_token"))
            .on("/messages", error(401), fixture("mailtm_messages_empty"))

        val list = MailTmProvider(http).listMessages(inbox)

        assertTrue(list.isEmpty())
        assertEquals(listOf("/token", "/messages", "/token", "/messages"), http.urls().map { it.substringAfter("api.mail.tm") })
    }

    @Test
    fun aDeletedAccountIsReportedAsSuch() = runBlocking {
        val http = FakeHttp().on("/token", error(401, """{"code":401,"message":"Invalid credentials."}"""))
        val e = assertThrows(ProviderException::class.java) { runBlocking { MailTmProvider(http).listMessages(inbox) } }
        assertTrue(e.message!!.contains("no longer exists"))
    }

    @Test
    fun getMessage_joinsTheHtmlParts() = runBlocking {
        val content = MailTmProvider(http()).getMessage(inbox, MailSummary("6aa8720fa29c4e703e015c73", "", "", 0))
        assertEquals("<div dir=\"ltr\">Hello <b>again</b></div>", content.html)
        assertEquals("Hello again", content.text)
    }

    @Test
    fun deleteInbox_deletesTheAccount() = runBlocking {
        val http = http().on("/accounts/6aa87175a29c4e703e015c72", FakeHttp.body(""))
        assertTrue(MailTmProvider(http).deleteInbox(inbox))
        assertEquals(listOf("https://api.mail.tm/accounts/6aa87175a29c4e703e015c72"), http.urls("DELETE"))
    }
}
