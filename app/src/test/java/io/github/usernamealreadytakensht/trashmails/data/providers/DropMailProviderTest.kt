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
import org.json.JSONObject
import org.junit.Test

class DropMailProviderTest {
    private val token = """{"token":"af_AQJqqXvgiNBpVVWpR1uUJvxKfflpOF8U79e7uaTZ"}"""
    private val session = """{"data":{"introduceSession":{"id":"U2Vzc2lvbjrZmdj6-cBPCqyLi5_vdC97","addresses":[{"restoreKey":"031ba24cfba5f20dd66a4d3139ef20a26","address":"alxtherxj@10mail.org"}]}}}"""
    private val notFound = """{"errors":[{"extensions":{"code":"SESSION_NOT_FOUND"},"path":["session"],"message":"session_not_found"}],"data":{"session":null}}"""
    private val emptySession = """{"data":{"introduceSession":{"id":"U2Vzc2lvbjpuZXc","addresses":[]}}}"""
    private val restored = """{"data":{"restoreAddress":{"restoreKey":"0ae8b0151f77f3a3120d97d81cdf42cdd"}}}"""
    private val inbox = Inbox(id = "alxtherxj@10mail.org", provider = Provider.DROPMAIL, address = "alxtherxj@10mail.org", token = "031ba24cfba5f20dd66a4d3139ef20a26")

    /** The GraphQL query a recorded call carried (the JSON envelope escapes its quotes). */
    private val FakeHttp.Call.query: String get() = body?.let { runCatching { JSONObject(it).getString("query") }.getOrNull() }.orEmpty()

    private fun http() = FakeHttp().on("/api/token/generate", body(token))

    private fun provider(http: FakeHttp, prefs: MemoryPrefs = MemoryPrefs(), cache: MessageCache = MessageCache(MemoryPrefs())) =
        DropMailProvider(http, cache, prefs)

    @Test
    fun createInbox_requestsATokenOnce_andLeavesTheDomainToTheServerByDefault() = runBlocking {
        val http = http().onBody("introduceSession", body(session))
        val prefs = MemoryPrefs()
        val p = provider(http, prefs)

        val created = p.createInbox(null, CreateOptions())
        assertEquals("alxtherxj@10mail.org", created.address)
        assertEquals("031ba24cfba5f20dd66a4d3139ef20a26", created.token)
        assertNull(created.expiresAt)
        val create = http.calls.last()
        assertTrue(create.url.endsWith("/api/graphql/af_AQJqqXvgiNBpVVWpR1uUJvxKfflpOF8U79e7uaTZ"))
        assertTrue(create.query.contains("permanentDomainOnly: true"))

        // The token is kept: a second creation does not ask for another.
        p.createInbox(null, CreateOptions())
        assertEquals(1, http.urls().count { it.contains("/api/token/generate") })
    }

    @Test
    fun createInbox_looksUpTheChosenDomainId_andRefusesAnUnknownOne() = runBlocking {
        val http = http().onBody("domains", fixture("dropmail_domains")).onBody("introduceSession", body(session))
        val p = provider(http)

        p.createInbox(null, CreateOptions(domain = "dropmail.me"))
        assertTrue(http.calls.last().query.contains("domainId: \"RG9tYWluOjE\""))

        // The list is fetched once per process; an unknown name fails before any mutation.
        val e = assertThrows(ProviderException::class.java) {
            runBlocking { p.createInbox(null, CreateOptions(domain = "emlhub.com")) }
        }
        assertEquals("DropMail.me no longer offers @emlhub.com", e.message)
        assertEquals(1, http.calls.count { it.query.contains("domains") })
    }

    @Test
    fun createInbox_explainsTheCaptcha() = runBlocking {
        val http = FakeHttp().on("/api/token/generate", httpError(402, """{"credits_needed":1,"captcha":{},"error":"captcha_required"}"""))
        val e = assertThrows(ProviderException::class.java) {
            runBlocking { provider(http).createInbox(null, CreateOptions()) }
        }
        assertEquals("DropMail.me asks for a captcha right now: try again later", e.message)
    }

    @Test
    fun listMessages_mergesTheSessionMailIntoTheCache_andKeepsItOnceTheSessionIsGone() = runBlocking {
        val cache = MessageCache(MemoryPrefs())
        val prefs = MemoryPrefs()
        val http = http().onBody("introduceSession(input: {permanentDomainOnly", body(session))
            .onBody("session(id:", fixture("dropmail_session"), body(notFound), body("""{"data":{"session":{"id":"U2Vzc2lvbjpuZXc","mails":[]}}}"""))
            .onBody("introduceSession(input: {withAddress: false", body(emptySession))
            .onBody("restoreAddress", body(restored))
        val p = provider(http, prefs, cache)
        val inbox = p.createInbox(null, CreateOptions())

        val first = p.listMessages(inbox)
        assertEquals(listOf("Second", "Probe one"), first.map(MailSummary::subject))
        assertEquals("TrashMails probe <probe@example.com>", first[1].from)
        assertEquals("other@example.com", first[0].from)
        assertEquals("<p>Hello <b>there</b></p>", first[1].html)
        assertNull(first[0].html)
        assertTrue(http.calls.last().query.contains("session(id: \"U2Vzc2lvbjrZmdj6-cBPCqyLi5_vdC97\")"))

        // The session lapsed: the address is restored into a new one and the cached mail is what is listed.
        val second = p.listMessages(inbox)
        assertEquals(first, second)
        val restore = http.calls.last().query
        assertTrue(restore.contains("sessionId: \"U2Vzc2lvbjpuZXc\""))
        assertTrue(restore.contains("mailAddress: \"alxtherxj@10mail.org\""))
        assertTrue(restore.contains("restoreKey: \"031ba24cfba5f20dd66a4d3139ef20a26\""))

        // The next listing queries the new session with the new restore key on record.
        p.listMessages(inbox)
        assertTrue(http.calls.last().query.contains("session(id: \"U2Vzc2lvbjpuZXc\")"))
        assertTrue(prefs.getString("session:${inbox.key}")!!.contains("0ae8b0151f77f3a3120d97d81cdf42cdd"))
    }

    @Test
    fun listMessages_restoresFromTheInboxToken_whenNothingWasStored() = runBlocking {
        val http = http().onBody("introduceSession", body(emptySession)).onBody("restoreAddress", body(restored))
        val p = provider(http)

        assertTrue(p.listMessages(inbox).isEmpty())
        assertTrue(http.calls.last().query.contains("restoreKey: \"031ba24cfba5f20dd66a4d3139ef20a26\""))
        assertEquals(0, http.calls.count { it.query.contains("session(id:") })
    }

    @Test
    fun listMessages_adoptsTheLiveSession_whenTheAddressIsStillInUse() = runBlocking {
        val inUse = """{"errors":[{"extensions":{"code":"resolver_error"},"path":["restoreAddress"],"message":"already_in_use"}],"data":{"restoreAddress":null}}"""
        val sessions = """{"data":{"sessions":[{"id":"U2Vzc2lvbjpvdGhlcg","addresses":[{"address":"someone@dropmail.me","restoreKey":"other"}]},{"id":"U2Vzc2lvbjrZmdj6-cBPCqyLi5_vdC97","addresses":[{"address":"alxtherxj@10mail.org","restoreKey":"0adoptedkey"}]}]}}"""
        val http = http().onBody("introduceSession", body(emptySession)).onBody("restoreAddress", body(inUse))
            .onBody("sessions {", body(sessions)).onBody("session(id:", fixture("dropmail_session"))
        val prefs = MemoryPrefs()
        val p = provider(http, prefs)

        assertTrue(p.listMessages(inbox).isEmpty())
        // The session found holding the address is the one listed from now on, with the key it holds:
        // the one this app knew was rotated by the restore whose reply never got saved.
        assertEquals(listOf("Second", "Probe one"), p.listMessages(inbox).map(MailSummary::subject))
        assertTrue(http.calls.last().query.contains("session(id: \"U2Vzc2lvbjrZmdj6-cBPCqyLi5_vdC97\")"))
        assertTrue(prefs.getString("session:${inbox.key}")!!.contains("0adoptedkey"))
    }

    @Test
    fun listMessages_reportsARejectedRestoreKey_andTheRateLimit() = runBlocking {
        val badKey = """{"errors":[{"extensions":{"code":"resolver_error"},"path":["restoreAddress"],"message":"bad_signature"}],"data":{"restoreAddress":null}}"""
        val http = http().onBody("introduceSession", body(emptySession)).onBody("restoreAddress", body(badKey))
        val e = assertThrows(ProviderException::class.java) { runBlocking { provider(http).listMessages(inbox) } }
        assertEquals("DropMail.me rejected the restore key of alxtherxj@10mail.org: the address cannot be brought back", e.message)

        val limited = """{"data":{"session":{"id":"U2Vzc2lvbjpuZXc","mails":null}},"errors":[{"message":"Request quota exceeded","path":["session","mails"],"extensions":{"code":"RATE_LIMIT_EXCEEDED","captcha":{}}}]}"""
        val prefs = MemoryPrefs()
        prefs.put("session:${inbox.key}" to """{"session":"U2Vzc2lvbjpuZXc","restoreKey":"k"}""")
        val e2 = assertThrows(ProviderException::class.java) {
            runBlocking { provider(http().onBody("session(id:", body(limited)), prefs).listMessages(inbox) }
        }
        assertEquals("DropMail.me: request quota exceeded, wait a moment", e2.message)
    }

    @Test
    fun aRejectedToken_isReplacedOnce() = runBlocking {
        val prefs = MemoryPrefs()
        prefs.put("token" to "af_old", "tokenExpiresAt" to (System.currentTimeMillis() + 3_600_000L).toString())
        val http = http()
            .on("/api/graphql/af_old", httpError(403, """{"errors":[{"extensions":{"code":"authentication_error"},"message":"token_expired"}]}"""))
            .onBody("introduceSession", body(session))
        provider(http, prefs).createInbox(null, CreateOptions())
        assertEquals(
            listOf("/api/graphql/af_old", "/api/token/generate", "/api/graphql/af_AQJqqXvgiNBpVVWpR1uUJvxKfflpOF8U79e7uaTZ"),
            http.urls().map { it.substringAfter("dropmail.me") },
        )
        assertEquals("af_AQJqqXvgiNBpVVWpR1uUJvxKfflpOF8U79e7uaTZ", prefs.getString("token"))
    }

    @Test
    fun deleteMessage_dropsTheLocalCopy_andKeepsItOutOfLaterListings() = runBlocking {
        val cache = MessageCache(MemoryPrefs())
        val prefs = MemoryPrefs()
        prefs.put("session:${inbox.key}" to """{"session":"U2Vzc2lvbjrZmdj6-cBPCqyLi5_vdC97","restoreKey":"k"}""")
        val p = provider(http().onBody("session(id:", fixture("dropmail_session")), prefs, cache)
        val list = p.listMessages(inbox)

        assertEquals("Hello there", p.getMessage(inbox, list[1]).text)
        // Only the id is known after a process death: the body comes from the cache.
        assertEquals("<p>Hello <b>there</b></p>", p.getMessage(inbox, MailSummary(list[1].id, "", "", 0)).html)

        assertTrue(p.deleteMessage(inbox, list[1]))
        assertEquals(listOf("Second"), cache.load(inbox.key).map(MailSummary::subject))
        // The server still lists it while the session lives; it stays hidden.
        assertEquals(listOf("Second"), p.listMessages(inbox).map(MailSummary::subject))

        p.forgetInbox(inbox)
        assertTrue(cache.load(inbox.key).isEmpty())
        assertNull(prefs.getString("session:${inbox.key}"))
    }
}

class DropMailCacheRaceTest {
    private val inbox = Inbox(id = "alxtherxj@10mail.org", provider = Provider.DROPMAIL, address = "alxtherxj@10mail.org", token = "k")

    @Test
    fun aDeletionDuringAListing_isNotUndoneByTheMerge_andStaysHidden() = runBlocking {
        val cache = MessageCache(MemoryPrefs())
        val prefs = MemoryPrefs()
        prefs.put("session:${inbox.key}" to """{"session":"U2Vzc2lvbjrZmdj6-cBPCqyLi5_vdC97","restoreKey":"k"}""")
        lateinit var provider: DropMailProvider
        lateinit var victim: MailSummary
        // The server still lists the message the user deletes while the second listing is in flight.
        val http = FakeHttp().on("/api/token/generate", body("""{"token":"af_x"}""")).onBody(
            "session(id:",
            fixture("dropmail_session"),
            { runBlocking { provider.deleteMessage(inbox, victim) }; fixture("dropmail_session")() },
        )
        provider = DropMailProvider(http, cache, prefs)
        victim = provider.listMessages(inbox)[1]

        assertEquals(listOf("Second"), provider.listMessages(inbox).map(MailSummary::subject))
        assertEquals(listOf("Second"), provider.listMessages(inbox).map(MailSummary::subject))
        assertEquals(listOf("Second"), cache.load(inbox.key).map(MailSummary::subject))
    }
}
