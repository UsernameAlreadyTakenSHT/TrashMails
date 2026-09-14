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

class InboxKittenProviderTest {
    private val inbox = Inbox(id = "fixture", provider = Provider.INBOX_KITTEN, address = "fixture@inboxkitten.com")

    @Test
    fun listMessages_keepsOurRecipientOnly_dedupesByStorageKey_sortsNewestFirst() = runBlocking {
        val http = FakeHttp().on("/mail/list", fixture("kitten_list"))
        val list = InboxKittenProvider(http).listMessages(inbox)

        assertEquals(listOf("No from header", "Kitten one"), list.map(MailSummary::subject))
        // Recipient matched case-insensitively; no From header, so the envelope sender is used.
        assertEquals("bob@example.com", list[0].from)
        assertEquals("Alice <alice@example.com>", list[1].from)
        assertEquals(1_789_415_200_123L, list[1].date)
        assertEquals(mapOf("key" to "key-one", "region" to "us-east-1"), list[1].ref)
        assertTrue(http.urls().single().endsWith("/mail/list?recipient=fixture"))
    }

    @Test
    fun getMessage_fetchesByStorageKey() = runBlocking {
        val http = FakeHttp().on("/mail/getHtml", body("<p>hello</p>"))
        val summary = MailSummary("evt1", "", "", 0, ref = mapOf("key" to "key one", "region" to "eu"))

        val content = InboxKittenProvider(http).getMessage(inbox, summary)

        assertEquals("<p>hello</p>", content.html)
        assertTrue(http.urls().single().endsWith("/mail/getHtml?key=key+one&region=eu"))
    }
}

class MaildropProviderTest {
    private val inbox = Inbox(id = "fixture", provider = Provider.MAILDROP, address = "fixture@maildrop.cc")

    @Test
    fun listMessages_collapsesWhitespace_fallsBackToEnvelopeSender_sortsNewestFirst() = runBlocking {
        val http = FakeHttp().on("/graphql", fixture("maildrop_inbox"))
        val list = MaildropProvider(http).listMessages(inbox)

        assertEquals(listOf("m2", "m1"), list.map(MailSummary::id))
        assertEquals("bob@example.com", list[0].from)
        assertEquals("Alice <alice@example.com>", list[1].from)
        assertEquals("Hello world", list[1].subject)
        assertTrue(http.calls.single().body!!.contains("\"m\":\"fixture\""))
    }

    @Test
    fun getMessage_prefersHtml() = runBlocking {
        val content = MaildropProvider(FakeHttp().on("/graphql", fixture("maildrop_message")))
            .getMessage(inbox, MailSummary("m1", "", "", 0))
        assertEquals("<p>Hi</p>", content.html)
        assertNull(content.text)
    }

    @Test
    fun graphqlErrorsBecomeProviderErrors() = runBlocking {
        val provider = MaildropProvider(FakeHttp().on("/graphql", fixture("maildrop_error")))
        val e = assertThrows(ProviderException::class.java) { runBlocking { provider.listMessages(inbox) } }
        assertEquals("Mailbox name is not valid", e.message)
    }

    @Test
    fun deleteMessage_returnsTheMutationResult() = runBlocking {
        val provider = MaildropProvider(FakeHttp().on("/graphql", body("""{"data":{"delete":true}}""")))
        assertTrue(provider.deleteMessage(inbox, MailSummary("m1", "", "", 0)))
    }
}

class BurnerKiwiProviderTest {
    @Test
    fun createInbox_readsAddressTokenAndTtl() = runBlocking {
        val created = BurnerKiwiProvider(FakeHttp().on("/api/v2/inbox", fixture("burner_create"))).createInbox(null)

        assertEquals("7c1c0d2e-1111-4222-8333-944455556666", created.id)
        assertEquals("cheerful-otter@deceit.pro", created.address)
        assertEquals("eyJfixture", created.token)
        assertEquals(1_789_415_000_000L, created.createdAt)
        assertEquals(1_789_501_400_000L, created.expiresAt)
    }

    @Test
    fun listMessages_carriesBodies_andSendsTheToken() = runBlocking {
        val inbox = Inbox(id = "7c1c", provider = Provider.BURNER_KIWI, address = "x@deceit.pro", token = "eyJfixture")
        val http = FakeHttp().on("/messages", fixture("burner_messages"))
        val provider = BurnerKiwiProvider(http)

        val list = provider.listMessages(inbox)

        assertEquals(listOf("Burner two", "Burner one"), list.map(MailSummary::subject))
        assertEquals("carol@example.com", list[0].from)
        assertNull(list[0].html)
        assertEquals("plain only", list[0].text)
        assertEquals("eyJfixture", http.calls.single().headers["X-Burner-Key"])
        // The body came with the listing: no further request to read it.
        assertEquals("<p>hi</p>", provider.getMessage(inbox, list[1]).html)
        assertEquals(1, http.calls.size)
    }

    @Test
    fun apiErrorsBecomeProviderErrors() = runBlocking {
        val provider = BurnerKiwiProvider(FakeHttp().on("/api/v2/inbox", fixture("burner_error")))
        val e = assertThrows(ProviderException::class.java) { runBlocking { provider.createInbox(null) } }
        assertEquals("Forbidden: invalid token", e.message)
    }
}
