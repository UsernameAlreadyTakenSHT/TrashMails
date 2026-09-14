package com.example.trashmails.data.providers

import com.example.trashmails.data.Http
import com.example.trashmails.data.Inbox
import com.example.trashmails.data.MailContent
import com.example.trashmails.data.MailProvider
import com.example.trashmails.data.MailSummary
import com.example.trashmails.data.Provider
import com.example.trashmails.data.ProviderException
import com.example.trashmails.data.randomName
import com.example.trashmails.data.sanitizeName
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Guerrilla Mail: a public inbox addressed by name, read through a session token (`sid_token`).
 * Sessions expire after about an hour, so every call re-attaches to the inbox by name first
 * (`set_email_user`) and uses the fresh token it returns; the stored one is never relied on.
 * Emails are kept one hour.
 */
class GuerrillaMailProvider : MailProvider {
    override val provider = Provider.GUERRILLA_MAIL

    private companion object {
        const val BASE = "https://api.guerrillamail.com/ajax.php"
        const val DOMAIN = "guerrillamailblock.com"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun param(f: String, params: String) = "$BASE?f=$f&lang=en$params"

    private suspend fun get(f: String, params: String): JSONObject {
        val json = JSONObject(Http.get(param(f, params)))
        val auth = json.optJSONObject("auth")
        if (auth != null && !auth.optBoolean("success", true)) {
            throw ProviderException("Guerrilla Mail: ${auth.optJSONArray("error_codes")?.join(", ") ?: "auth error"}")
        }
        return json
    }

    /** Attaches the session to [name] and returns the token to use for the following calls. */
    private suspend fun attach(name: String): JSONObject =
        get("set_email_user", "&email_user=${enc(name)}")

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

    override suspend fun listMessages(inbox: Inbox): List<MailSummary> {
        val sid = attach(inbox.id).getString("sid_token")
        val json = get("get_email_list", "&offset=0&sid_token=$sid")
        val arr = json.optJSONArray("list") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            MailSummary(
                id = m.optString("mail_id"),
                from = m.optString("mail_from"),
                subject = m.optString("mail_subject"),
                date = m.optLong("mail_timestamp").takeIf { it > 0 }?.let { it * 1000 }
                    ?: System.currentTimeMillis(),
                ref = mapOf("sid" to sid),
            )
        }.sortedByDescending { it.date }
    }

    override suspend fun getMessage(inbox: Inbox, summary: MailSummary): MailContent {
        val sid = summary.ref["sid"] ?: attach(inbox.id).getString("sid_token")
        val m = get("fetch_email", "&email_id=${enc(summary.id)}&sid_token=$sid")
        val body = m.optString("mail_body").takeIf { it.isNotBlank() }
            ?: throw ProviderException("Empty message")
        // Guerrilla returns the body as HTML, plain-text mails included (wrapped in <pre>/<p>).
        return MailContent(html = body, text = null)
    }

    override val canDeleteMessages get() = true

    override suspend fun deleteMessage(inbox: Inbox, summary: MailSummary): Boolean {
        val sid = summary.ref["sid"] ?: attach(inbox.id).getString("sid_token")
        get("del_email", "&email_ids%5B%5D=${enc(summary.id)}&sid_token=$sid")
        return true
    }
}
