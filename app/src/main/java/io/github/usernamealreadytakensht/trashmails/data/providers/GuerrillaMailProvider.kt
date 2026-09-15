package io.github.usernamealreadytakensht.trashmails.data.providers

import io.github.usernamealreadytakensht.trashmails.data.Http
import io.github.usernamealreadytakensht.trashmails.data.HttpApi
import io.github.usernamealreadytakensht.trashmails.data.Inbox
import io.github.usernamealreadytakensht.trashmails.data.MailContent
import io.github.usernamealreadytakensht.trashmails.data.MailProvider
import io.github.usernamealreadytakensht.trashmails.data.MailSummary
import io.github.usernamealreadytakensht.trashmails.data.Provider
import io.github.usernamealreadytakensht.trashmails.data.ProviderException
import io.github.usernamealreadytakensht.trashmails.data.randomName
import io.github.usernamealreadytakensht.trashmails.data.sanitizeName
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Guerrilla Mail: a public inbox addressed by name, read through a session token (`sid_token`).
 * The token is obtained by attaching to the inbox by name (`set_email_user`) and cached for the
 * process lifetime; sessions expire after about an hour, which the API signals by answering
 * `get_email_list` with statistics only (no `list`), or `fetch_email` with the literal `false`.
 * Either answer triggers one re-attach and retry. Emails are kept one hour.
 */
class GuerrillaMailProvider(private val http: HttpApi = Http) : MailProvider {
    override val provider = Provider.GUERRILLA_MAIL

    private companion object {
        const val BASE = "https://api.guerrillamail.com/ajax.php"
        const val DOMAIN = "guerrillamailblock.com"
    }

    /** Session token per inbox name. */
    private val sids = ConcurrentHashMap<String, String>()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** One API call; the reply is whatever JSON value the endpoint returns (an object, or `false`). */
    private suspend fun call(f: String, params: String): Any? =
        JSONTokener(http.get("$BASE?f=$f&lang=en$params")).nextValue().also { value ->
            val auth = (value as? JSONObject)?.optJSONObject("auth")
            if (auth != null && !auth.optBoolean("success", true)) {
                throw SessionException("Guerrilla Mail: ${auth.optJSONArray("error_codes")?.join(", ") ?: "auth error"}")
            }
        }

    private suspend fun get(f: String, params: String): JSONObject =
        call(f, params) as? JSONObject ?: throw ProviderException("Unexpected reply from Guerrilla Mail")

    /** Attaches a new session to [name], caches and returns its token. */
    private suspend fun attach(name: String): JSONObject =
        get("set_email_user", "&email_user=${enc(name)}").also { json ->
            json.optString("sid_token").takeIf { it.isNotBlank() }?.let { sids[name] = it }
        }

    /** The session was refused or answered for another inbox; a fresh attach may fix it. */
    private class SessionException(message: String) : ProviderException(message)

    /**
     * Runs [block] with the session token (cached, else freshly attached); when the server rejects
     * that session, it is dropped and [block] runs once more with a new one. That second failure
     * is final.
     */
    private suspend fun <T> withSid(inbox: Inbox, block: suspend (String) -> T): T {
        val first = sids[inbox.id] ?: attach(inbox.id).getString("sid_token")
        try {
            return block(first)
        } catch (e: SessionException) {
            sids.remove(inbox.id, first)
        }
        return block(attach(inbox.id).getString("sid_token"))
    }

    override suspend fun createInbox(name: String?): Inbox {
        val n = sanitizeName(name) ?: randomName()
        val json = attach(n)
        return Inbox(
            id = n,
            provider = provider,
            address = json.optString("email_addr").ifBlank { "$n@$DOMAIN" },
            token = json.optString("sid_token").takeIf { it.isNotBlank() },
            createdAt = json.optLong("email_timestamp").takeIf { it > 0 }?.let { it * 1000 }
                ?: System.currentTimeMillis(),
        )
    }

    /** A listing reply that really is [inbox]'s: it carries `list`, and `email` (when present) matches. */
    private fun JSONObject.isListingOf(inbox: Inbox): Boolean =
        has("list") && optString("email").let { it.isBlank() || it.equals(inbox.address, ignoreCase = true) }

    override suspend fun listMessages(inbox: Inbox): List<MailSummary> {
        val json = withSid(inbox) { sid ->
            get("get_email_list", "&offset=0&sid_token=${enc(sid)}").also {
                if (!it.isListingOf(inbox)) throw SessionException("Guerrilla Mail did not return the inbox")
            }
        }
        val arr = json.optJSONArray("list") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            MailSummary(
                id = m.optString("mail_id"),
                from = m.optString("mail_from"),
                subject = m.optString("mail_subject"),
                // The welcome mail has no timestamp: date it at the inbox creation, not "now" on every poll.
                date = m.optLong("mail_timestamp").takeIf { it > 0 }?.let { it * 1000 } ?: inbox.createdAt,
            )
        }.sortedByDescending { it.date }
    }

    override suspend fun getMessage(inbox: Inbox, summary: MailSummary): MailContent {
        // `false`: the message is gone, or the session was; a fresh session tells the two apart.
        val m = withSid(inbox) { sid ->
            call("fetch_email", "&email_id=${enc(summary.id)}&sid_token=${enc(sid)}") as? JSONObject
                ?: throw SessionException("This message has expired (Guerrilla Mail keeps mail for one hour)")
        }
        val body = m.optString("mail_body").takeIf { it.isNotBlank() }
            ?: throw ProviderException("Empty message")
        // Guerrilla returns the body as HTML, plain-text mails included (wrapped in <pre>/<p>).
        return MailContent(html = body, text = null)
    }

    override val canDeleteMessages get() = true

    override suspend fun deleteMessage(inbox: Inbox, summary: MailSummary): Boolean {
        val json = withSid(inbox) { sid -> get("del_email", "&email_ids%5B%5D=${enc(summary.id)}&sid_token=${enc(sid)}") }
        val deleted = json.optJSONArray("deleted_ids") ?: return false
        return (0 until deleted.length()).any { deleted.optString(it) == summary.id }
    }
}
