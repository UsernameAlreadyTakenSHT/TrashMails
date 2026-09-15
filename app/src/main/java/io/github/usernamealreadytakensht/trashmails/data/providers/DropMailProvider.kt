package io.github.usernamealreadytakensht.trashmails.data.providers

import io.github.usernamealreadytakensht.trashmails.data.CreateOptions
import io.github.usernamealreadytakensht.trashmails.data.Http
import io.github.usernamealreadytakensht.trashmails.data.HttpApi
import io.github.usernamealreadytakensht.trashmails.data.HttpException
import io.github.usernamealreadytakensht.trashmails.data.Inbox
import io.github.usernamealreadytakensht.trashmails.data.MailContent
import io.github.usernamealreadytakensht.trashmails.data.MailProvider
import io.github.usernamealreadytakensht.trashmails.data.MailSummary
import io.github.usernamealreadytakensht.trashmails.data.MessageCache
import io.github.usernamealreadytakensht.trashmails.data.Prefs
import io.github.usernamealreadytakensht.trashmails.data.Provider
import io.github.usernamealreadytakensht.trashmails.data.ProviderException
import io.github.usernamealreadytakensht.trashmails.data.text
import io.github.usernamealreadytakensht.trashmails.data.textOrEmpty
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/**
 * DropMail.me: a random address (the login part is the server's) on a chosen or random domain,
 * through a GraphQL API behind a free `af_` token the app requests for the device (a day long,
 * replaced when it runs out). The address lives in a session that dies ten minutes after its last
 * access: every refresh extends it, and once it has lapsed the address is restored into a new
 * session with its restore key — which the server changes on every restoration, so it is kept in
 * [prefs] rather than in the immutable [Inbox]. Received mail goes with the session and cannot be
 * deleted on the server, so every fetch is merged into the local [MessageCache], which is what the
 * app lists, reads and deletes from.
 */
class DropMailProvider(
    private val http: HttpApi = Http,
    private val cache: MessageCache,
    private val prefs: Prefs,
) : MailProvider {
    override val provider = Provider.DROPMAIL

    private companion object {
        const val TOKEN_URL = "https://dropmail.me/api/token/generate"
        const val GRAPHQL_URL = "https://dropmail.me/api/graphql/"
        const val TOKEN_LIFETIME_MS = 24 * 3_600_000L
        /** A token is replaced this long before its end, so a listing never runs into a dead one. */
        const val TOKEN_MARGIN_MS = 10 * 60_000L
        const val MAX_TOMBSTONES = 200
        const val KEY_TOKEN = "token"
        const val KEY_TOKEN_EXPIRES = "tokenExpiresAt"
        const val MAIL_FIELDS = "id receivedAt fromAddr headerFrom headerSubject text html"
    }

    /** Set when a lapsed session was replaced, for the user to hear once. */
    @Volatile
    private var pendingNotice: String? = null

    override fun takeNotice(): String? = pendingNotice.also { pendingNotice = null }

    /** A refresh extends the session: the sooner after coming back, the fewer lapses. */
    override val refreshOnForeground get() = true

    /** Domain ids by name, fetched once per process: the API documents them as never changing. */
    @Volatile
    private var domainIds: Map<String, String>? = null

    override suspend fun createInbox(name: String?, options: CreateOptions): Inbox {
        val domainId = options.domain?.takeIf { it in provider.domains }?.let { domainId(it) }
        // Left to the server, the pick stays among the permanent domains (the ones the dialog lists).
        val input = if (domainId != null) "domainId: ${quote(domainId)}" else "permanentDomainOnly: true"
        val json = graphql("mutation { introduceSession(input: {$input}) { id addresses { address restoreKey } } }")
        val session = json.data("introduceSession") ?: fail(json, "DropMail.me could not create the address")
        val first = session.optJSONArray("addresses")?.optJSONObject(0)
            ?: throw ProviderException("DropMail.me created a session without an address")
        val address = first.getString("address")
        val restoreKey = first.getString("restoreKey")
        val inbox = Inbox(id = address, provider = provider, address = address, token = restoreKey)
        saveSession(inbox, session.getString("id"), restoreKey)
        return inbox
    }

    override suspend fun listMessages(inbox: Inbox): List<MailSummary> {
        val (sessionId, restoreKey) = loadSession(inbox)
        val json = sessionId?.let { graphql("{ session(id: ${quote(it)}) { id mails { $MAIL_FIELDS } } }") }
        val session = json?.data("session")
        if (session == null) {
            // Lapsed (or never listed since a restore failed): back into a fresh session, which
            // holds no mail yet — what was received before stays in the cache.
            if (json != null && json.errorCode() != "SESSION_NOT_FOUND") fail(json, "DropMail.me could not list the inbox")
            restore(inbox, restoreKey)
            return cache.load(inbox.key)
        }
        val arr = session.optJSONArray("mails") ?: fail(json, "DropMail.me could not list the inbox")
        val fresh = (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = m.text("id") ?: return@mapNotNull null
            MailSummary(
                id = id,
                from = m.text("headerFrom") ?: m.textOrEmpty("fromAddr"),
                subject = m.textOrEmpty("headerSubject"),
                date = parseDate(m.optString("receivedAt")),
                html = m.text("html"),
                text = m.text("text"),
            )
        }
        // Merged under the cache lock, tombstones read there too: a deletion running meanwhile
        // (leaving a message screen starts a poll) must not be undone by this listing.
        return cache.update(inbox.key) { known ->
            val deleted = tombstones(inbox)
            (fresh + known).distinctBy { it.id }.filterNot { it.id in deleted }
        }
    }

    override suspend fun getMessage(inbox: Inbox, summary: MailSummary): MailContent {
        // The bodies travel with the summary; after a process death only the cached copy has them.
        val m = summary.takeIf { it.html != null || it.text != null } ?: cache.load(inbox.key).firstOrNull { it.id == summary.id } ?: summary
        return MailContent(html = m.html, text = m.text)
    }

    override val canDeleteMessages get() = true
    override val deletesLocally get() = true

    override suspend fun deleteMessage(inbox: Inbox, summary: MailSummary): Boolean {
        // The server keeps the message as long as the session lives: remembered (before the copy
        // goes, so a listing in flight sees it) so that no fetch brings it back.
        val arr = JSONArray()
        (tombstones(inbox) + summary.id).takeLast(MAX_TOMBSTONES).forEach { arr.put(it) }
        prefs.put(deletedKey(inbox) to arr.toString())
        cache.update(inbox.key) { known -> known.filterNot { it.id == summary.id } }
        return true
    }

    override fun forgetInbox(inbox: Inbox) {
        cache.clear(inbox.key)
        prefs.remove(sessionKey(inbox), deletedKey(inbox))
    }

    /**
     * A new session holding the address again; the restore key it hands back replaces the old one.
     * The server rotates the key as soon as it answers, so the mutation and the save of its reply
     * run as one non-cancellable step: a screen left while the reply is in flight must not leave
     * the app holding a key the server no longer accepts. An address the server says is still in
     * use sits in a live session this app lost track of (a restore interrupted before its save,
     * a process death): that session is looked up and adopted, with the key it currently holds.
     */
    private suspend fun restore(inbox: Inbox, restoreKey: String) {
        val created = graphql("mutation { introduceSession(input: {withAddress: false}) { id } }")
        val sessionId = created.data("introduceSession")?.optString("id")?.takeIf { it.isNotBlank() }
            ?: fail(created, "DropMail.me could not open a session")
        val input = "sessionId: ${quote(sessionId)}, mailAddress: ${quote(inbox.address)}, restoreKey: ${quote(restoreKey)}"
        val restored = withContext(NonCancellable) {
            graphql("mutation { restoreAddress(input: {$input}) { restoreKey } }").also { reply ->
                reply.data("restoreAddress")?.optString("restoreKey")?.takeIf { it.isNotBlank() }?.let { saveSession(inbox, sessionId, it) }
            }
        }
        if (restored.data("restoreAddress") == null) when (restored.errorMessage()) {
            "bad_signature" -> throw ProviderException("DropMail.me rejected the restore key of ${inbox.address}: the address cannot be brought back")
            "already_in_use" -> { adoptSession(inbox); return }
            else -> fail(restored, "DropMail.me could not restore the address")
        }
        pendingNotice = "The DropMail.me session had lapsed: the address is live again, but mail sent meanwhile bounced"
    }

    private suspend fun adoptSession(inbox: Inbox) {
        val json = graphql("{ sessions { id addresses { address restoreKey } } }")
        val sessions = json.optJSONObject("data")?.optJSONArray("sessions") ?: fail(json, "DropMail.me could not list its sessions")
        for (i in 0 until sessions.length()) {
            val session = sessions.optJSONObject(i) ?: continue
            val addresses = session.optJSONArray("addresses") ?: continue
            for (j in 0 until addresses.length()) {
                val address = addresses.optJSONObject(j) ?: continue
                if (address.optString("address") != inbox.address) continue
                val key = address.text("restoreKey") ?: fail(json, "DropMail.me listed the address without its restore key")
                saveSession(inbox, session.getString("id"), key)
                return
            }
        }
        // The live session belongs to a token this app no longer has (a replaced token): it dies
        // on its own ten minutes after its last access, and the address can be restored then.
        throw ProviderException("DropMail.me still holds ${inbox.address} in a session this app cannot reach: try again in ten minutes")
    }

    private suspend fun domainId(name: String): String {
        val ids = domainIds ?: run {
            val json = graphql("{ domains { id name } }")
            val arr = json.optJSONObject("data")?.optJSONArray("domains") ?: fail(json, "DropMail.me could not list its domains")
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { it.optString("name") to it.optString("id") } }
                .filter { (n, id) -> n.isNotBlank() && id.isNotBlank() }
                .toMap()
                .also { domainIds = it }
        }
        return ids[name] ?: throw ProviderException("DropMail.me no longer offers @$name")
    }

    /**
     * Runs [query] with the device token and returns the reply (`data` and `errors` alike: the
     * callers tell an expected error apart). A token the server rejects is replaced, once.
     */
    private suspend fun graphql(query: String, retry: Boolean = true): JSONObject {
        val body = JSONObject().put("query", query).toString()
        val reply = try {
            http.postJson(GRAPHQL_URL + token(), body)
        } catch (e: HttpException) {
            if (e.code == 403 && retry) {
                prefs.put(KEY_TOKEN to "", KEY_TOKEN_EXPIRES to "")
                return graphql(query, retry = false)
            }
            throw ProviderException("DropMail.me refused the request (HTTP ${e.code})")
        }
        return JSONObject(reply)
    }

    /** The device's `af_` token, requested when there is none or it is about to run out. */
    private suspend fun token(): String {
        val stored = prefs.getString(KEY_TOKEN)
        val expiresAt = prefs.getString(KEY_TOKEN_EXPIRES)?.toLongOrNull() ?: 0L
        if (!stored.isNullOrBlank() && expiresAt - System.currentTimeMillis() > TOKEN_MARGIN_MS) return stored
        val json = try {
            JSONObject(http.postJson(TOKEN_URL, """{"type":"af","lifetime":"1d"}"""))
        } catch (e: HttpException) {
            val reason = runCatching { JSONObject(e.body).optString("error") }.getOrDefault("")
            throw ProviderException(
                if (reason == "captcha_required") "DropMail.me asks for a captcha right now: try again later"
                else "DropMail.me refused to hand out a token (HTTP ${e.code})"
            )
        }
        val token = json.text("token") ?: throw ProviderException("DropMail.me sent no token")
        prefs.put(KEY_TOKEN to token, KEY_TOKEN_EXPIRES to (System.currentTimeMillis() + TOKEN_LIFETIME_MS).toString())
        return token
    }

    private fun sessionKey(inbox: Inbox) = "session:${inbox.key}"
    private fun deletedKey(inbox: Inbox) = "deleted:${inbox.key}"

    /** The current session id (null when none was ever stored) and restore key of [inbox]. */
    private fun loadSession(inbox: Inbox): Pair<String?, String> {
        val o = prefs.getString(sessionKey(inbox))?.let { runCatching { JSONObject(it) }.getOrNull() }
        val restoreKey = o?.optString("restoreKey")?.takeIf { it.isNotBlank() } ?: inbox.token
            ?: throw ProviderException("Missing restore key")
        return o?.optString("session")?.takeIf { it.isNotBlank() } to restoreKey
    }

    private fun saveSession(inbox: Inbox, sessionId: String, restoreKey: String) =
        prefs.put(sessionKey(inbox) to JSONObject().put("session", sessionId).put("restoreKey", restoreKey).toString())

    /** Ids of the messages deleted locally while the server may still list them. */
    private fun tombstones(inbox: Inbox): List<String> {
        val arr = prefs.getString(deletedKey(inbox))?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }

    private fun quote(s: String): String = JSONObject.quote(s)

    private fun parseDate(iso: String): Long =
        runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrDefault(System.currentTimeMillis())

    private fun JSONObject.data(field: String): JSONObject? = optJSONObject("data")?.optJSONObject(field)
    private fun JSONObject.firstError(): JSONObject? = optJSONArray("errors")?.optJSONObject(0)
    private fun JSONObject.errorCode(): String? = firstError()?.optJSONObject("extensions")?.optString("code")?.takeIf { it.isNotBlank() }
    private fun JSONObject.errorMessage(): String? = firstError()?.optString("message")?.takeIf { it.isNotBlank() }

    /** Throws the reply's error in the user's terms, or [fallback] when it carries none. */
    private fun fail(json: JSONObject?, fallback: String): Nothing = throw ProviderException(
        when (json?.errorCode()) {
            "RATE_LIMIT_EXCEEDED" -> "DropMail.me: request quota exceeded, wait a moment"
            "DOMAIN_NOT_FOUND" -> "DropMail.me no longer offers this domain"
            null -> fallback
            else -> "DropMail.me: ${json.errorMessage() ?: fallback}"
        }
    )
}
