package io.github.usernamealreadytakensht.trashmails.data.providers

import io.github.usernamealreadytakensht.trashmails.data.CreateOptions
import io.github.usernamealreadytakensht.trashmails.data.checkedAddress
import io.github.usernamealreadytakensht.trashmails.data.Http
import io.github.usernamealreadytakensht.trashmails.data.HttpApi
import io.github.usernamealreadytakensht.trashmails.data.HttpException
import io.github.usernamealreadytakensht.trashmails.data.Inbox
import io.github.usernamealreadytakensht.trashmails.data.MailContent
import io.github.usernamealreadytakensht.trashmails.data.MailProvider
import io.github.usernamealreadytakensht.trashmails.data.MailSummary
import io.github.usernamealreadytakensht.trashmails.data.MessageCache
import io.github.usernamealreadytakensht.trashmails.data.Provider
import io.github.usernamealreadytakensht.trashmails.data.ProviderException
import io.github.usernamealreadytakensht.trashmails.data.sanitizeName
import io.github.usernamealreadytakensht.trashmails.data.text
import io.github.usernamealreadytakensht.trashmails.data.textOrEmpty
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * tempmail.lol: a private inbox behind a token, on a random domain (and, unless asked otherwise, a
 * random subdomain), alive for an hour. The typed name is a prefix the service completes. Emails are
 * handed over once — the server drops them as it returns them — so every fetch is merged into the
 * local [MessageCache], which is what the app lists, reads and deletes from.
 */
class TempmailLolProvider(private val http: HttpApi = Http, private val cache: MessageCache) : MailProvider {
    override val provider = Provider.TEMPMAIL_LOL

    private companion object {
        const val BASE = "https://api.tempmail.lol/v2"
        const val LIFETIME_MS = 60 * 60 * 1000L
    }

    override suspend fun createInbox(name: String?, options: CreateOptions): Inbox {
        val body = JSONObject()
        sanitizeName(name)?.let { body.put("prefix", it) }
        if (options.noSubdomain) body.put("subdomain", false)
        val json = try {
            JSONObject(http.postJson("$BASE/inbox/create", body.toString()))
        } catch (e: HttpException) {
            throw ProviderException(explain(e.body) ?: "tempmail.lol refused to create the address (HTTP ${e.code})")
        }
        if (json.optBoolean("captcha_required")) throw ProviderException("tempmail.lol asks for a captcha right now: try again later")
        json.text("error")?.let { throw ProviderException("tempmail.lol: $it") }
        val now = System.currentTimeMillis()
        return Inbox(
            id = checkedAddress(json.getString("address"), provider),
            provider = provider,
            address = checkedAddress(json.getString("address"), provider),
            token = json.getString("token"),
            createdAt = now,
            expiresAt = now + LIFETIME_MS,
        )
    }

    /**
     * Not cancellable once sent: the server drops the emails as it hands them over, so a listing
     * cancelled by navigation between the reply and the cache write would lose them for good. The
     * HTTP timeouts bound how long it can outlive its screen.
     */
    override suspend fun listMessages(inbox: Inbox): List<MailSummary> = withContext(NonCancellable) {
        val token = inbox.token ?: throw ProviderException("Missing token")
        val json = JSONObject(http.get("$BASE/inbox?token=${URLEncoder.encode(token, "UTF-8")}"))
        val arr = json.optJSONArray("emails")
        // An expired inbox (or a reply without emails) only means nothing new: what was received stays.
        if (arr == null || arr.length() == 0) return@withContext cache.load(inbox.key)
        val fresh = (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            val from = m.textOrEmpty("from")
            val subject = m.textOrEmpty("subject")
            val date = m.optLong("date").takeIf { it > 0 } ?: System.currentTimeMillis()
            val text = m.text("body")
            val html = m.text("html")
            MailSummary(
                // The API gives no id: one is derived from what the message is, stable across fetches.
                // 64 bits of SHA-256 over separated fields: a 32-bit hashCode let a sender forge a
                // message with the same date and hash, which then took the place of the real one.
                id = "$date-${contentHash(from, subject, text.orEmpty(), html.orEmpty())}",
                from = from, subject = subject, date = date, html = html, text = text,
            )
        }
        // Merged under the cache lock: a deletion running meanwhile must not be undone.
        cache.update(inbox.key) { known -> (fresh + known).distinctBy { it.id } }
    }

    override suspend fun getMessage(inbox: Inbox, summary: MailSummary): MailContent {
        // The bodies travel with the summary; after a process death only the cached copy has them.
        val m = summary.takeIf { it.html != null || it.text != null } ?: cache.load(inbox.key).firstOrNull { it.id == summary.id } ?: summary
        return MailContent(html = m.html, text = m.text)
    }

    override val canDeleteMessages get() = true
    override val deletesLocally get() = true

    override suspend fun deleteMessage(inbox: Inbox, summary: MailSummary): Boolean {
        cache.update(inbox.key) { known -> known.filterNot { it.id == summary.id } }
        return true
    }

    override fun forgetInbox(inbox: Inbox) = cache.clear(inbox.key)

    /** The first 64 bits of the SHA-256 of [parts], NUL-separated, in hex. */
    private fun contentHash(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("\u0000").toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    /** The `error` line of a failed reply, when there is one. */
    private fun explain(body: String): String? =
        runCatching { JSONObject(body).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }?.let { "tempmail.lol: $it" }
}
