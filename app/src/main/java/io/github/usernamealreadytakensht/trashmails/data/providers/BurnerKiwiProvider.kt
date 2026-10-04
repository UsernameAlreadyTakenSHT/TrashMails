package io.github.usernamealreadytakensht.trashmails.data.providers

import io.github.usernamealreadytakensht.trashmails.data.Http
import io.github.usernamealreadytakensht.trashmails.data.HttpApi
import io.github.usernamealreadytakensht.trashmails.data.CreateOptions
import io.github.usernamealreadytakensht.trashmails.data.checkedAddress
import io.github.usernamealreadytakensht.trashmails.data.secondsToMillis
import io.github.usernamealreadytakensht.trashmails.data.pathSegment
import io.github.usernamealreadytakensht.trashmails.data.Inbox
import io.github.usernamealreadytakensht.trashmails.data.MailContent
import io.github.usernamealreadytakensht.trashmails.data.MailProvider
import io.github.usernamealreadytakensht.trashmails.data.MailSummary
import io.github.usernamealreadytakensht.trashmails.data.Provider
import io.github.usernamealreadytakensht.trashmails.data.ProviderException
import io.github.usernamealreadytakensht.trashmails.data.text
import io.github.usernamealreadytakensht.trashmails.data.textOrEmpty
import org.json.JSONObject

/**
 * Burner Kiwi: the address is generated server-side and a JWT token protects the inbox.
 * Message bodies are returned directly in the list call.
 */
class BurnerKiwiProvider(private val http: HttpApi = Http) : MailProvider {
    override val provider = Provider.BURNER_KIWI

    private companion object {
        const val BASE = "https://burner.kiwi/api/v2/inbox"
    }

    private fun unwrap(body: String): JSONObject {
        val json = JSONObject(body)
        if (!json.optBoolean("success")) {
            // Prefixed: server text must not pass for a message of the app itself.
            val msg = "Burner Kiwi: " + (json.optJSONObject("errors")?.optString("msg") ?: "error")
            throw ProviderException(msg)
        }
        return json
    }

    override suspend fun createInbox(name: String?, options: CreateOptions): Inbox {
        // Inbox creation is a GET (POST returns 405).
        val json = unwrap(http.get(BASE))
        val result = json.getJSONObject("result")
        val email = result.getJSONObject("email")
        return Inbox(
            id = email.getString("id"),
            provider = provider,
            address = checkedAddress(email.getString("address"), provider),
            token = result.getString("token"),
            createdAt = secondsToMillis(email.optLong("created_at")) ?: System.currentTimeMillis(),
            expiresAt = secondsToMillis(email.optLong("ttl")),
        )
    }

    private fun headers(inbox: Inbox) =
        mapOf("X-Burner-Key" to (inbox.token ?: throw ProviderException("Missing token")))

    override suspend fun listMessages(inbox: Inbox): List<MailSummary> {
        val json = unwrap(http.get("$BASE/${pathSegment(inbox.id)}/messages", headers(inbox)))
        val arr = json.optJSONArray("result") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            MailSummary(
                id = m.getString("id"),
                from = m.textOrEmpty("from").ifBlank { m.textOrEmpty("sender") },
                subject = m.textOrEmpty("subject"),
                date = secondsToMillis(m.optLong("received_at")) ?: 0L,
                html = m.text("body_html"),
                text = m.text("body_plain"),
            )
        }.sortedByDescending { it.date }
    }

    override suspend fun getMessage(inbox: Inbox, summary: MailSummary): MailContent =
        MailContent(html = summary.html, text = summary.text)
}
