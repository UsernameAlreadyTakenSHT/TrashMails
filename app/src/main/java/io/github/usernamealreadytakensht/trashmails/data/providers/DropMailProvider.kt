package io.github.usernamealreadytakensht.trashmails.data.providers

import io.github.usernamealreadytakensht.trashmails.data.CreateOptions
import io.github.usernamealreadytakensht.trashmails.data.checkedAddress
import io.github.usernamealreadytakensht.trashmails.data.isSane
import io.github.usernamealreadytakensht.trashmails.data.Http
import io.github.usernamealreadytakensht.trashmails.data.HttpApi
import io.github.usernamealreadytakensht.trashmails.data.HttpException
import io.github.usernamealreadytakensht.trashmails.data.Inbox
import io.github.usernamealreadytakensht.trashmails.data.MailSummary
import io.github.usernamealreadytakensht.trashmails.data.MessageCache
import io.github.usernamealreadytakensht.trashmails.data.KeystoreBox
import io.github.usernamealreadytakensht.trashmails.data.PlainBox
import io.github.usernamealreadytakensht.trashmails.data.Prefs
import io.github.usernamealreadytakensht.trashmails.data.parseIsoDate
import io.github.usernamealreadytakensht.trashmails.data.SecretBox
import io.github.usernamealreadytakensht.trashmails.data.Provider
import io.github.usernamealreadytakensht.trashmails.data.ProviderException
import io.github.usernamealreadytakensht.trashmails.data.text
import io.github.usernamealreadytakensht.trashmails.data.textOrEmpty
import io.github.usernamealreadytakensht.trashmails.data.works
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
    override val cache: MessageCache,
    private val prefs: Prefs,
    /** Seals the device token and the restore keys before they reach [prefs]. */
    private val box: SecretBox = PlainBox,
) : CacheBackedProvider {
    override val provider = Provider.DROPMAIL

    private companion object {
        const val TOKEN_URL = "https://dropmail.me/api/token/generate"
        const val GRAPHQL_URL = "https://dropmail.me/api/graphql/"
        const val TOKEN_LIFETIME_MS = 24 * 3_600_000L
        /** A token is kept to its very end: the sessions opened with it cannot be reached with the next one. */
        const val TOKEN_MARGIN_MS = 0L
        /** A token younger than this is not replaced on a 403: the refusal is more likely a firewall than the token. */
        const val TOKEN_MIN_AGE_MS = 3_600_000L
        const val MAX_TOMBSTONES = 200
        /** A session lists 100 mails at most: this many ids cover it with room to spare. */
        const val MAX_RECEIVED = 500
        /** And this many characters of them: a server cannot make the file grow with huge ids. */
        const val MAX_RECEIVED_CHARS = 64 * 1024
        /** Longest message id kept; real ones are a few dozen characters. */
        const val MAX_ID_CHARS = 1_000
        val TOKEN_FORMAT = Regex("af_[A-Za-z0-9_-]{8,256}")
        const val KEY_TOKEN = "token"
        const val TOKEN_CONTEXT = "dropmail-device-token"
        const val KEY_TOKEN_EXPIRES = "tokenExpiresAt"
        const val MAIL_FIELDS = "id receivedAt fromAddr headerFrom headerSubject text html toAddr toAddrOrig"
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
        noAddressLeft = false
        val domainId = options.domain?.takeIf { it in provider.domains }?.let { domainId(it) }
        // Left to the server, the pick stays among the permanent domains (the ones the dialog lists).
        val input = if (domainId != null) "domainId: ${quote(domainId)}" else "permanentDomainOnly: true"
        val json = graphql("mutation { introduceSession(input: {$input}) { id addresses { address restoreKey } } }")
        val session = json.data("introduceSession") ?: fail(json, "DropMail.me could not create the address")
        val first = session.optJSONArray("addresses")?.optJSONObject(0)
            ?: throw ProviderException("DropMail.me created a session without an address")
        val address = checkedAddress(first.getString("address"), provider)
        val restoreKey = first.getString("restoreKey")
        val inbox = Inbox(id = address, provider = provider, address = address, token = restoreKey)
        // The same address may have been removed earlier in this run: its session is kept again.
        cache.revive(inbox.key)
        saveSession(inbox, session.getString("id"), restoreKey)
        return inbox
    }

    override suspend fun listMessages(inbox: Inbox): List<MailSummary> {
        val (sessionId, restoreKey) = loadSession(inbox)
        // The ids only first: the bodies (up to 100 mails of them) are fetched again only when a
        // mail the app does not hold yet has arrived, not at every refresh.
        val json = sessionId?.let { graphql("{ session(id: ${quote(it)}) { id mails { id } } }") }
        val session = json?.data("session")
        if (session == null) {
            // Lapsed (or never listed since a restore failed): back into a fresh session, which
            // holds no mail yet — what was received before stays in the cache.
            if (json != null && json.errorCode() != "SESSION_NOT_FOUND") fail(json, "DropMail.me could not list the inbox")
            restore(inbox, restoreKey)
            return cache.load(inbox.key)
        }
        val ids = session.optJSONArray("mails") ?: fail(json, "DropMail.me could not list the inbox")
        // Received before (even if dropped from the cache since, kept a week or past its bounds) or
        // deleted: the server lists them as long as the session lives, and they must not come back.
        val received = received(inbox)
        val held = cache.load(inbox.key).map { it.id }.toSet() + tombstones(inbox) + received
        val unseen = (0 until ids.length()).mapNotNull { ids.optJSONObject(it)?.text("id") }
            .filter { it.length <= MAX_ID_CHARS }.filterNot { it in held }
        if (unseen.isEmpty()) return cache.load(inbox.key)
        val full = graphql("{ session(id: ${quote(sessionId)}) { id mails { $MAIL_FIELDS } } }")
        val arr = full.data("session")?.optJSONArray("mails") ?: fail(full, "DropMail.me could not list the inbox")
        val fresh = (0 until arr.length()).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = m.text("id") ?: return@mapNotNull null
            MailSummary(
                id = id,
                from = m.text("headerFrom") ?: m.textOrEmpty("fromAddr"),
                subject = m.textOrEmpty("headerSubject"),
                date = parseIsoDate(m.optString("receivedAt")),
                html = m.text("html"),
                text = m.text("text"),
                // Only an extended address is worth showing: the plain one is the inbox itself.
                to = m.text("toAddrOrig")?.takeIf { it != m.text("toAddr") },
            )
        }.filter { it.isSane() } // an absurd id would go into the received list, and on disk
        // Merged under the cache lock, tombstones and received ids read there too: a deletion or
        // another listing running meanwhile must not be undone by this one. The ids are recorded
        // once the cache is written, and only those it kept: one cut by its bounds stays to come.
        var kept: List<MailSummary> = emptyList()
        cache.unlessForgotten(inbox.key) {
            val receivedNow = received(inbox)
            kept = cache.update(inbox.key) { known ->
                val deleted = tombstones(inbox)
                (fresh.filterNot { it.id in receivedNow } + known).distinctBy { it.id }.filterNot { it.id in deleted }
            }
            val keptIds = kept.map { it.id }.toSet()
            recordReceived(inbox, receivedNow + fresh.map { it.id }.filter { it in keptIds })
        }
        return kept
    }

    /** Stores [ids] as received for [inbox]: the latest, bounded in count and in size. */
    private fun recordReceived(inbox: Inbox, ids: List<String>) {
        var bounded = ids.distinct().takeLast(MAX_RECEIVED)
        while (bounded.sumOf { it.length } > MAX_RECEIVED_CHARS) bounded = bounded.drop(1)
        prefs.put(receivedKey(inbox) to JSONArray(bounded).toString())
    }

    override suspend fun deleteMessage(inbox: Inbox, summary: MailSummary): Boolean {
        // The server keeps the message as long as the session lives: remembered (before the copy
        // goes, so a listing in flight sees it) so that no fetch brings it back.
        // Read and written under the cache lock: two deletions at once must not each drop the other's id.
        cache.update(inbox.key) { known ->
            val arr = JSONArray()
            (tombstones(inbox) + summary.id).takeLast(MAX_TOMBSTONES).forEach { arr.put(it) }
            prefs.put(deletedKey(inbox) to arr.toString())
            known.filterNot { it.id == summary.id }
        }
        return true
    }

    /**
     * The device token goes with the last address: whoever read it from the app's files could
     * otherwise list its sessions, and so the restore keys of addresses already removed.
     */
    override fun forgetAll() {
        noAddressLeft = true
        prefs.remove(KEY_TOKEN, KEY_TOKEN_EXPIRES)
    }

    /**
     * Set once the last address is removed, until one is created: a request still in flight then
     * must not write the device token back (it went with the last address).
     */
    @Volatile private var noAddressLeft = false

    private fun putToken(vararg entries: Pair<String, Any>) {
        if (!noAddressLeft) prefs.put(*entries)
    }

    override fun forgetInbox(inbox: Inbox) {
        // One step under the cache lock: a restoration in flight cannot save its session in between.
        cache.clear(inbox.key) { prefs.remove(sessionKey(inbox), deletedKey(inbox), receivedKey(inbox)) }
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
            // A 403 may be the token rejected, or a firewall or rate limit: replacing the token
            // strands every session opened with it, so it is replaced when the server says the
            // token is the problem, and otherwise at most once an hour.
            val tokenRejected = "authentication_error" in e.body || "token" in e.body.lowercase()
            if (e.code == 403 && retry && (tokenRejected || tokenAgeMs() > TOKEN_MIN_AGE_MS)) {
                putToken(KEY_TOKEN to "", KEY_TOKEN_EXPIRES to "")
                return graphql(query, retry = false)
            }
            throw ProviderException("DropMail.me refused the request (HTTP ${e.code})")
        }
        return JSONObject(reply)
    }

    /** How long ago the stored token was handed out (its expiry is set a lifetime after that); long ago when there is none. */
    private fun tokenAgeMs(): Long {
        val expiresAt = prefs.getString(KEY_TOKEN_EXPIRES)?.toLongOrNull() ?: return Long.MAX_VALUE
        return System.currentTimeMillis() - (expiresAt - TOKEN_LIFETIME_MS)
    }

    /** The device's `af_` token, requested when there is none or it is about to run out. */
    private suspend fun token(): String {
        val raw = prefs.getString(KEY_TOKEN)?.takeIf { it.isNotBlank() }
        val stored = raw?.let { box.open(it, TOKEN_CONTEXT) }
        // A token stored in clear or in the older format is sealed again the first time it is read,
        // if sealing works right now.
        if (stored != null && !KeystoreBox.isCurrent(raw)) {
            box.seal(stored, TOKEN_CONTEXT).takeIf(KeystoreBox::isCurrent)?.let { putToken(KEY_TOKEN to it) }
        }
        val expiresAt = prefs.getString(KEY_TOKEN_EXPIRES)?.toLongOrNull() ?: 0L
        // Valid only within one token lifetime from now: an expiry further out (a clock set ahead
        // when it was written, or an edited file) is not believed.
        val left = expiresAt - System.currentTimeMillis()
        val valid = left > TOKEN_MARGIN_MS && left <= TOKEN_LIFETIME_MS
        // The stored token is used only if it is one (a file edited to hold anything else is not).
        if (!stored.isNullOrBlank() && valid && TOKEN_FORMAT.matches(stored)) return stored
        // Stored but unopenable while the keystore fails, and still valid: a new token would strand
        // every session opened with this one, so the refresh fails and is tried again. Unopenable
        // while the keystore works, it is damaged: a new one is requested.
        if (raw != null && stored == null && valid && !box.works()) {
            throw ProviderException("DropMail.me: the device token cannot be read right now, try again in a moment")
        }
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
        // It goes into the URL path as is: nothing but the token's own characters may get there.
        if (!TOKEN_FORMAT.matches(token)) throw ProviderException("DropMail.me sent a token the app cannot use")
        putToken(KEY_TOKEN to box.seal(token, TOKEN_CONTEXT), KEY_TOKEN_EXPIRES to (System.currentTimeMillis() + TOKEN_LIFETIME_MS).toString())
        return token
    }

    private fun sessionKey(inbox: Inbox) = "session:${inbox.key}"
    /** What a restore key is sealed for: its address. */
    private fun restoreContext(inbox: Inbox) = "dropmail-restore:${inbox.key}"
    private fun deletedKey(inbox: Inbox) = "deleted:${inbox.key}"
    private fun receivedKey(inbox: Inbox) = "received:${inbox.key}"

    /** Ids of the mails already taken into the cache for [inbox], whether still there or not. */
    private fun received(inbox: Inbox): List<String> {
        val arr = prefs.getString(receivedKey(inbox))?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }

    /** The current session id (null when none was ever stored) and restore key of [inbox]. */
    private fun loadSession(inbox: Inbox): Pair<String?, String> {
        val o = prefs.getString(sessionKey(inbox))?.let { runCatching { JSONObject(it) }.getOrNull() }
        val raw = o?.optString("restoreKey")?.takeIf { it.isNotBlank() }
        val opened = raw?.let { box.open(it, restoreContext(inbox)) }
        // Stored but unopenable while the keystore fails: it may be the only valid key, so the
        // refresh fails and is retried. The address's first key is only a fallback for a damaged one.
        if (raw != null && opened == null && !box.works()) {
            throw ProviderException("DropMail.me: the restore key cannot be read right now, try again in a moment")
        }
        val restoreKey = opened ?: inbox.token ?: throw ProviderException("Missing restore key")
        val sessionId = o?.optString("session")?.takeIf { it.isNotBlank() }
        // A restore key stored in clear or in the older format is sealed again the first time it is
        // read, if sealing works right now; only one that did open (never the fallback).
        if (sessionId != null && opened != null && !KeystoreBox.isCurrent(raw) && KeystoreBox.isCurrent(box.seal("probe", "probe"))) {
            saveSession(inbox, sessionId, opened)
        }
        return sessionId to restoreKey
    }

    /** Not for an address removed while its restoration was in flight: removing it drops all of it. */
    private fun saveSession(inbox: Inbox, sessionId: String, restoreKey: String) {
        // Checked and written as one step under the cache lock, so it cannot land just after the removal.
        cache.unlessForgotten(inbox.key) {
            // On disk before going on: the server has already rotated the previous key.
            prefs.putNow(sessionKey(inbox) to JSONObject().put("session", sessionId).put("restoreKey", box.seal(restoreKey, restoreContext(inbox))).toString())
        }
    }

    /** Ids of the messages deleted locally while the server may still list them. */
    private fun tombstones(inbox: Inbox): List<String> {
        val arr = prefs.getString(deletedKey(inbox))?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }
    }

    private fun quote(s: String): String = JSONObject.quote(s)

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
