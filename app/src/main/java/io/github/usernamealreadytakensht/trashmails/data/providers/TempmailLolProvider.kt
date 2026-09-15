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
import io.github.usernamealreadytakensht.trashmails.data.Provider
import io.github.usernamealreadytakensht.trashmails.data.ProviderException
import io.github.usernamealreadytakensht.trashmails.data.sanitizeName
import org.json.JSONObject
import java.net.URLEncoder

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
        json.optString("error").takeIf { it.isNotBlank() }?.let { throw ProviderException("tempmail.lol: $it") }
        val now = System.currentTimeMillis()
        return Inbox(
            id = json.getString("address"),
            provider = provider,
            address = json.getString("address"),
            token = json.getString("token"),
            createdAt = now,
            expiresAt = now + LIFETIME_MS,
        )
    }

    override suspend fun listMessages(inbox: Inbox): List<MailSummary> {
        val token = inbox.token ?: throw ProviderException("Missing token")
        val known = cache.load(inbox.key)
        val json = JSONObject(http.get("$BASE/inbox?token=${URLEncoder.encode(token, "UTF-8")}"))
        val arr = json.optJSONArray("emails")
        // An expired inbox (or a reply without emails) only means nothing new: what was received stays.
        if (arr == null || arr.length() == 0) return known
        val fresh = (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            val from = m.optString("from")
            val subject = m.optString("subject")
            val date = m.optLong("date").takeIf { it > 0 } ?: System.currentTimeMillis()
            val text = m.optString("body").takeIf { it.isNotBlank() }
            val html = m.optString("html").takeIf { it.isNotBlank() }
            MailSummary(
                // The API gives no id: one is derived from what the message is, stable across fetches.
                id = "$date-${(from + subject + (text ?: html.orEmpty())).hashCode().toUInt().toString(16)}",
                from = from, subject = subject, date = date, html = html, text = text,
            )
        }
        val merged = (fresh + known).distinctBy { it.id }.sortedByDescending { it.date }
        cache.save(inbox.key, merged)
        return merged
    }

    override suspend fun getMessage(inbox: Inbox, summary: MailSummary): MailContent =
        MailContent(html = summary.html, text = summary.text)

    override val canDeleteMessages get() = true
    override val deletesLocally get() = true

    override suspend fun deleteMessage(inbox: Inbox, summary: MailSummary): Boolean {
        cache.save(inbox.key, cache.load(inbox.key).filterNot { it.id == summary.id })
        return true
    }

    override fun forgetInbox(inbox: Inbox) = cache.clear(inbox.key)

    /** The `error` line of a failed reply, when there is one. */
    private fun explain(body: String): String? =
        runCatching { JSONObject(body).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }?.let { "tempmail.lol: $it" }
}
