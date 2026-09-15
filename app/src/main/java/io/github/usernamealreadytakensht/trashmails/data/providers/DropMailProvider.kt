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
        val known = cache.load(inbox.key)
        val (sessionId, restoreKey) = loadSession(inbox)
        val json = sessionId?.let { graphql("{ session(id: ${quote(it)}) { id mails { $MAIL_FIELDS } } }") }
        val session = json?.data("session")
        if (session == null) {
            // Lapsed (or never listed since a restore failed): back into a fresh session, which
            // holds no mail yet — what was received before stays in the cache.
            if (json != null && json.errorCode() != "SESSION_NOT_FOUND") fail(json, "DropMail.me could not list the inbox")
            restore(inbox, restoreKey)
            return known
        }
        val arr = session.optJSONArray("mails") ?: fail(json, "DropMail.me could not list the inbox")
        val deleted = tombstones(inbox)
        val fresh = (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = m.optString("id").takeIf { it.isNotBlank() && it !in deleted } ?: return@mapNotNull null
            MailSummary(
                id = id,
                from = m.optString("headerFrom").takeIf { it.isNotBlank() } ?: m.optString("fromAddr"),
                subject = m.optString("headerSubject"),
                date = parseDate(m.optString("receivedAt")),
                html = m.optString("html").takeIf { it.isNotBlank() },
                text = m.optString("text").takeIf { it.isNotBlank() },
            )
        }
        val merged = (fresh + known).distinctBy { it.id }.sortedByDescending { it.date }
        cache.save(inbox.key, merged)
        return merged
    }

    override suspend fun getMessage(inbox: Inbox, summary: MailSummary): MailContent {
        // The bodies travel with the summary; after a process death only the cached copy has them.
        val m = summary.takeIf { it.html != null || it.text != null } ?: cache.load(inbox.key).firstOrNull { it.id == summary.id } ?: summary
        return MailContent(html = m.html, text = m.text)
    }

    override val canDeleteMessages get() = true
    override val deletesLocally get() = true

    override suspend fun deleteMessage(inbox: Inbox, summary: MailSummary): Boolean {
        cache.save(inbox.key, cache.load(inbox.key).filterNot { it.id == summary.id })
        // The server keeps the message as long as the session lives: remembered so a fetch does not bring it back.
        val arr = JSONArray()
        (tombstones(inbox) + summary.id).takeLast(MAX_TOMBSTONES).forEach { arr.put(it) }
        prefs.put(deletedKey(inbox) to arr.toString())
        return true
    }

    override fun forgetInbox(inbox: Inbox) {
        cache.clear(inbox.key)
        prefs.put(sessionKey(inbox) to "", deletedKey(inbox) to "")
    }

    /**
     * A new session holding the address again; the restore key it hands back replaces the old one.
     * An address the server says is still in use sits in a live session this app lost track of
     * (a restore that failed half-way): that session is looked up and adopted instead.
     */
    private suspend fun restore(inbox: Inbox, restoreKey: String) {
        val created = graphql("mutation { introduceSession(input: {withAddress: false}) { id } }")
        val sessionId = created.data("introduceSession")?.optString("id")?.takeIf { it.isNotBlank() }
            ?: fail(created, "DropMail.me could not open a session")
        val input = "sessionId: ${quote(sessionId)}, mailAddress: ${quote(inbox.address)}, restoreKey: ${quote(restoreKey)}"
        val restored = graphql("mutation { restoreAddress(input: {$input}) { restoreKey } }")
        val newKey = restored.data("restoreAddress")?.optString("restoreKey")?.takeIf { it.isNotBlank() }
            ?: when (restored.errorMessage()) {
                "bad_signature" -> throw ProviderException("DropMail.me rejected the restore key of ${inbox.address}: the address cannot be brought back")
                "already_in_use" -> { adoptSession(inbox, restoreKey); return }
                else -> fail(restored, "DropMail.me could not restore the address")
            }
        saveSession(inbox, sessionId, newKey)
    }

    private suspend fun adoptSession(inbox: Inbox, restoreKey: String) {
        val json = graphql("{ sessions { id addresses { address } } }")
        val sessions = json.optJSONObject("data")?.optJSONArray("sessions") ?: fail(json, "DropMail.me could not list its sessions")
        val holder = (0 until sessions.length()).mapNotNull { sessions.optJSONObject(it) }.firstOrNull { s ->
            val addresses = s.optJSONArray("addresses") ?: return@firstOrNull false
            (0 until addresses.length()).any { addresses.optJSONObject(it)?.optString("address") == inbox.address }
        } ?: throw ProviderException("DropMail.me says ${inbox.address} is in use, but not by this app")
        saveSession(inbox, holder.getString("id"), restoreKey)
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
        val token = json.optString("token").takeIf { it.isNotBlank() } ?: throw ProviderException("DropMail.me sent no token")
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
