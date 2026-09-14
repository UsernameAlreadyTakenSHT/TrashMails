package com.example.trashmails.data.providers

import com.example.trashmails.data.Http
import com.example.trashmails.data.Inbox
import com.example.trashmails.data.MailContent
import com.example.trashmails.data.MailProvider
import com.example.trashmails.data.MailSummary
import com.example.trashmails.data.Provider
import com.example.trashmails.data.ProviderException
import org.json.JSONObject

/**
 * Burner Kiwi: the address is generated server-side and a JWT token protects the inbox.
 * Message bodies are returned directly in the list call.
 */
class BurnerKiwiProvider : MailProvider {
    override val provider = Provider.BURNER_KIWI

    private companion object {
        const val BASE = "https://burner.kiwi/api/v2/inbox"
    }

    private fun unwrap(body: String): JSONObject {
        val json = JSONObject(body)
        if (!json.optBoolean("success")) {
            val msg = json.optJSONObject("errors")?.optString("msg") ?: "Burner Kiwi error"
            throw ProviderException(msg)
        }
        return json
    }

    override suspend fun createInbox(name: String?): Inbox {
        // Inbox creation is a GET (POST returns 405).
        val json = unwrap(Http.get(BASE))
        val result = json.getJSONObject("result")
        val email = result.getJSONObject("email")
        return Inbox(
            id = email.getString("id"),
            provider = provider,
            address = email.getString("address"),
            token = result.getString("token"),
            createdAt = email.optLong("created_at") * 1000,
            expiresAt = email.optLong("ttl").takeIf { it > 0 }?.let { it * 1000 },
        )
    }

    private fun headers(inbox: Inbox) =
        mapOf("X-Burner-Key" to (inbox.token ?: throw ProviderException("Missing token")))

    override suspend fun listMessages(inbox: Inbox): List<MailSummary> {
        val json = unwrap(Http.get("$BASE/${inbox.id}/messages", headers(inbox)))
        val arr = json.optJSONArray("result") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            MailSummary(
                id = m.getString("id"),
                from = m.optString("from").ifBlank { m.optString("sender") },
                subject = m.optString("subject"),
                date = m.optLong("received_at") * 1000,
                html = m.optString("body_html").takeIf { it.isNotBlank() },
                text = m.optString("body_plain").takeIf { it.isNotBlank() },
            )
        }.sortedByDescending { it.date }
    }

    override suspend fun getMessage(inbox: Inbox, summary: MailSummary): MailContent =
        MailContent(html = summary.html, text = summary.text)
}
