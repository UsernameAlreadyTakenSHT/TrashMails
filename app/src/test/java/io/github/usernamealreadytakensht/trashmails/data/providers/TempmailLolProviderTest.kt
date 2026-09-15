package io.github.usernamealreadytakensht.trashmails.data.providers

import io.github.usernamealreadytakensht.trashmails.data.CreateOptions
import io.github.usernamealreadytakensht.trashmails.data.Inbox
import io.github.usernamealreadytakensht.trashmails.data.MailSummary
import io.github.usernamealreadytakensht.trashmails.data.MemoryPrefs
import io.github.usernamealreadytakensht.trashmails.data.MessageCache
import io.github.usernamealreadytakensht.trashmails.data.Provider
import io.github.usernamealreadytakensht.trashmails.data.ProviderException
import io.github.usernamealreadytakensht.trashmails.data.providers.FakeHttp.Companion.body
import io.github.usernamealreadytakensht.trashmails.data.providers.FakeHttp.Companion.fixture
import io.github.usernamealreadytakensht.trashmails.data.providers.FakeHttp.Companion.httpError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TempmailLolProviderTest {
    private val created = """{"address":"trashmailsprobe90a610@imagesthere.com","token":"2l9prthcmfgqlebbcgg1hj4pp2cxl52ntlerdc"}"""
    private val inbox = Inbox(
        id = "trashmailsprobe90a610@imagesthere.com", provider = Provider.TEMPMAIL_LOL,
        address = "trashmailsprobe90a610@imagesthere.com", token = "2l9prthcmfgqlebbcgg1hj4pp2cxl52ntlerdc",
    )

    @Test
    fun createInbox_sendsThePrefixAndTheSubdomainChoice_andSetsAnExpiry() = runBlocking {
        val http = FakeHttp().on("/inbox/create", body(created))
        val before = System.currentTimeMillis()
        val inbox = TempmailLolProvider(http, MessageCache(MemoryPrefs())).createInbox("Trash Mails Probe", CreateOptions(noSubdomain = true))

        assertEquals("trashmailsprobe90a610@imagesthere.com", inbox.address)
        assertEquals("2l9prthcmfgqlebbcgg1hj4pp2cxl52ntlerdc", inbox.token)
        assertTrue(inbox.expiresAt!! >= before + 3_600_000L)
        val sent = http.calls.single().body!!
        assertTrue(sent.contains("\"prefix\":\"trashmailsprobe\""))
        assertTrue(sent.contains("\"subdomain\":false"))
    }

    @Test
    fun createInbox_withoutOptions_sendsNeither() = runBlocking {
        val http = FakeHttp().on("/inbox/create", body(created))
        TempmailLolProvider(http, MessageCache(MemoryPrefs())).createInbox(null, CreateOptions())
        assertEquals("{}", http.calls.single().body)
    }

    @Test
    fun createInbox_reportsTheServersReason() = runBlocking {
        val http = FakeHttp().on("/inbox/create", httpError(400, """{"error":"Invalid domain selected"}"""))
        val e = assertThrows(ProviderException::class.java) {
            runBlocking { TempmailLolProvider(http, MessageCache(MemoryPrefs())).createInbox(null, CreateOptions()) }
        }
        assertEquals("tempmail.lol: Invalid domain selected", e.message)
    }

    @Test
    fun listMessages_keepsWhatTheServerHandedOver_acrossFetches() = runBlocking {
        val cache = MessageCache(MemoryPrefs())
        val http = FakeHttp().on("/inbox?token=", fixture("tempmaillol_inbox"), body("""{"emails":[],"expired":true}"""))
        val provider = TempmailLolProvider(http, cache)

        val first = provider.listMessages(inbox)
        assertEquals(listOf("Second", "First"), first.map(MailSummary::subject))
        assertEquals("<p>Hello <b>there</b></p>", first[1].html)
        assertNull(first[0].html)
        assertEquals("Plain only", first[0].text)

        // The server has consumed them (and the inbox expired): the cache still lists both.
        val second = provider.listMessages(inbox)
        assertEquals(first, second)
        assertEquals(2, cache.load(inbox.key).size)
        assertEquals(2, http.calls.size)
        assertTrue(http.urls().all { it.endsWith("/inbox?token=2l9prthcmfgqlebbcgg1hj4pp2cxl52ntlerdc") })
    }

    @Test
    fun getMessage_readsTheCachedBody_andDeleteDropsTheLocalCopy() = runBlocking {
        val cache = MessageCache(MemoryPrefs())
        val provider = TempmailLolProvider(FakeHttp().on("/inbox?token=", fixture("tempmaillol_inbox")), cache)
        val list = provider.listMessages(inbox)

        assertEquals("Hello there", provider.getMessage(inbox, list[1]).text)
        assertTrue(provider.deleteMessage(inbox, list[1]))
        assertEquals(listOf("Second"), cache.load(inbox.key).map(MailSummary::subject))

        provider.forgetInbox(inbox)
        assertTrue(cache.load(inbox.key).isEmpty())
    }
}
