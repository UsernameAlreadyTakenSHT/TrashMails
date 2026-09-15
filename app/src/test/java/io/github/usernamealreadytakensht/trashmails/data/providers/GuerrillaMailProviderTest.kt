package io.github.usernamealreadytakensht.trashmails.data.providers

import io.github.usernamealreadytakensht.trashmails.data.Inbox
import io.github.usernamealreadytakensht.trashmails.data.MailSummary
import io.github.usernamealreadytakensht.trashmails.data.Provider
import io.github.usernamealreadytakensht.trashmails.data.ProviderException
import io.github.usernamealreadytakensht.trashmails.data.providers.FakeHttp.Companion.body
import io.github.usernamealreadytakensht.trashmails.data.providers.FakeHttp.Companion.fixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GuerrillaMailProviderTest {
    private val inbox = Inbox(
        id = "trashmailsprobe", provider = Provider.GUERRILLA_MAIL,
        address = "trashmailsprobe@guerrillamailblock.com", createdAt = 1_789_415_079_000L,
    )

    @Test
    fun createInbox_usesTheSessionReply() = runBlocking {
        val http = FakeHttp().on("f=set_email_user", fixture("guerrilla_set_email_user"))
        val created = GuerrillaMailProvider(http).createInbox("Trash Mails Probe!")

        assertEquals("trashmailsprobe", created.id)
        assertEquals("trashmailsprobe@guerrillamailblock.com", created.address)
        assertEquals("hv15ugm7f1tcfi7i1vsb399aci", created.token)
        assertEquals(1_789_415_079_000L, created.createdAt)
        assertTrue(http.urls().single().contains("email_user=trashmailsprobe"))
    }

    @Test
    fun listMessages_attachesOnceThenReusesTheSession() = runBlocking {
        val http = FakeHttp()
            .on("f=set_email_user", fixture("guerrilla_set_email_user"))
            .on("f=get_email_list", fixture("guerrilla_list"))
        val provider = GuerrillaMailProvider(http)

        val first = provider.listMessages(inbox)
        provider.listMessages(inbox)

        assertEquals(listOf("Your code", "Welcome to Guerrilla Mail"), first.map(MailSummary::subject))
        assertEquals(1_789_415_200_000L, first[0].date)
        // The welcome mail has no timestamp: dated at the inbox creation, not "now".
        assertEquals(inbox.createdAt, first[1].date)
        assertEquals(1, http.urls().count { it.contains("f=set_email_user") })
        assertEquals(2, http.urls().count { it.contains("f=get_email_list") })
    }

    @Test
    fun listMessages_reattachesWhenTheSessionIsGone() = runBlocking {
        val http = FakeHttp()
            .on("f=set_email_user", fixture("guerrilla_set_email_user"))
            .on("f=get_email_list", fixture("guerrilla_list_expired"), fixture("guerrilla_list"))

        val list = GuerrillaMailProvider(http).listMessages(inbox)

        assertEquals(2, list.size)
        assertEquals(
            listOf("f=set_email_user", "f=get_email_list", "f=set_email_user", "f=get_email_list"),
            http.urls().map { url -> url.substringAfter("?").substringBefore("&") },
        )
    }

    @Test
    fun getMessage_returnsHtml_andReportsAnExpiredOne() = runBlocking {
        val http = FakeHttp()
            .on("f=set_email_user", fixture("guerrilla_set_email_user"))
            .on("f=fetch_email", fixture("guerrilla_fetch_email"), body("false"), body("false"))
        val provider = GuerrillaMailProvider(http)
        val summary = MailSummary(id = "812345", from = "", subject = "", date = 0)

        val content = provider.getMessage(inbox, summary)
        assertEquals("<p>Your code is <b>123456</b></p>", content.html)
        assertNull(content.text)

        val e = assertThrows(ProviderException::class.java) { runBlocking { provider.getMessage(inbox, summary) } }
        assertTrue(e.message!!.contains("expired"))
    }

    @Test
    fun authFailureBecomesAProviderError() = runBlocking {
        val http = FakeHttp().on("f=set_email_user", body("""{"auth":{"success":false,"error_codes":["auth.session_expired"]}}"""))
        val e = assertThrows(ProviderException::class.java) { runBlocking { GuerrillaMailProvider(http).createInbox(null) } }
        assertTrue(e.message!!.contains("auth.session_expired"))
    }
}

class GuerrillaMailSessionTest {
    private val inbox = Inbox(
        id = "trashmailsprobe", provider = Provider.GUERRILLA_MAIL,
        address = "trashmailsprobe@guerrillamailblock.com", createdAt = 1_789_415_079_000L,
    )
    private val authFailure = body("""{"auth":{"success":false,"error_codes":["auth.session_expired"]}}""")

    @Test
    fun aRejectedCachedSessionIsDroppedAndAttachedAgain() = runBlocking {
        val http = FakeHttp()
            .on("f=set_email_user", fixture("guerrilla_set_email_user"))
            .on("f=get_email_list", fixture("guerrilla_list"), authFailure, fixture("guerrilla_list"))
        val provider = GuerrillaMailProvider(http)

        provider.listMessages(inbox)
        val second = provider.listMessages(inbox)

        assertEquals(2, second.size)
        assertEquals(
            listOf("f=set_email_user", "f=get_email_list", "f=get_email_list", "f=set_email_user", "f=get_email_list"),
            http.urls().map { url -> url.substringAfter("?").substringBefore("&") },
        )
    }

    @Test
    fun aRejectedFreshSessionIsFinal() = runBlocking {
        val http = FakeHttp()
            .on("f=set_email_user", fixture("guerrilla_set_email_user"))
            .on("f=get_email_list", authFailure)
        val e = assertThrows(ProviderException::class.java) { runBlocking { GuerrillaMailProvider(http).listMessages(inbox) } }
        assertTrue(e.message!!.contains("auth.session_expired"))
        // Attach, refused, attach again, refused again: no third try.
        assertEquals(4, http.calls.size)
    }

    @Test
    fun deleteMessage_reportsWhatTheServerDeleted() = runBlocking {
        val http = FakeHttp()
            .on("f=set_email_user", fixture("guerrilla_set_email_user"))
            .on("f=del_email", body("""{"deleted_ids":["812345"],"auth":{"success":true,"error_codes":[]}}"""))
        val provider = GuerrillaMailProvider(http)
        assertTrue(provider.deleteMessage(inbox, MailSummary("812345", "", "", 0)))
        assertTrue(!provider.deleteMessage(inbox, MailSummary("999", "", "", 0)))
    }
}
