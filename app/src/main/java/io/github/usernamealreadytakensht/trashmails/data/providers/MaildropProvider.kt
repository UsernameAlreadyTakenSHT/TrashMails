package io.github.usernamealreadytakensht.trashmails.data.providers

import io.github.usernamealreadytakensht.trashmails.data.Http
import io.github.usernamealreadytakensht.trashmails.data.HttpApi
import io.github.usernamealreadytakensht.trashmails.data.CreateOptions
import io.github.usernamealreadytakensht.trashmails.data.Inbox
import io.github.usernamealreadytakensht.trashmails.data.MailContent
import io.github.usernamealreadytakensht.trashmails.data.MailProvider
import io.github.usernamealreadytakensht.trashmails.data.MailSummary
import io.github.usernamealreadytakensht.trashmails.data.MimeText
import io.github.usernamealreadytakensht.trashmails.data.Provider
import io.github.usernamealreadytakensht.trashmails.data.ProviderException
import io.github.usernamealreadytakensht.trashmails.data.randomName
import io.github.usernamealreadytakensht.trashmails.data.sanitizeName
import io.github.usernamealreadytakensht.trashmails.data.text
import io.github.usernamealreadytakensht.trashmails.data.textOrEmpty
import org.json.JSONObject
import java.time.Instant

/** Maildrop: public GraphQL API, no authentication. */
class MaildropProvider(private val http: HttpApi = Http) : MailProvider {
    override val provider = Provider.MAILDROP

    private companion object {
        const val ENDPOINT = "https://api.maildrop.cc/graphql"
        const val DOMAIN = "maildrop.cc"
        val WHITESPACE = Regex("\\s+")
    }

    private suspend fun query(query: String, variables: Map<String, String>): JSONObject {
        val body = JSONObject().put("query", query).put("variables", JSONObject(variables)).toString()
        val json = JSONObject(http.postJson(ENDPOINT, body))
        json.optJSONArray("errors")?.takeIf { it.length() > 0 }?.let {
            throw ProviderException(it.getJSONObject(0).optString("message", "Maildrop error"))
        }
        return json.getJSONObject("data")
    }

    override suspend fun createInbox(name: String?, options: CreateOptions): Inbox {
        val n = sanitizeName(name) ?: randomName()
        return Inbox(id = n, provider = provider, address = "$n@$DOMAIN")
    }

    override suspend fun listMessages(inbox: Inbox): List<MailSummary> {
        val data = query(
            "query(\$m: String!) { inbox(mailbox: \$m) { id headerfrom mailfrom subject date } }",
            mapOf("m" to inbox.id),
        )
        val arr = data.optJSONArray("inbox") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            MailSummary(
                id = m.getString("id"),
                from = m.textOrEmpty("headerfrom").ifBlank { m.textOrEmpty("mailfrom") }.replace(WHITESPACE, " "),
                subject = m.textOrEmpty("subject").replace(WHITESPACE, " "),
                date = parseDate(m.optString("date")),
            )
        }.sortedByDescending { it.date }
    }

    override suspend fun getMessage(inbox: Inbox, summary: MailSummary): MailContent {
        val data = query(
            "query(\$m: String!, \$id: String!) { message(mailbox: \$m, id: \$id) { html data } }",
            mapOf("m" to inbox.id, "id" to summary.id),
        )
        val m = data.optJSONObject("message") ?: throw ProviderException("Message not found")
        val html = m.text("html")
        if (html != null) return MailContent(html = html, text = null)
        // No HTML: `data` is the raw RFC 822 message (headers + body), read for its text or HTML part.
        val raw = m.text("data") ?: return MailContent(null, null)
        val parts = MimeText.parse(raw)
        return MailContent(html = parts.html, text = parts.text)
    }

    override val canDeleteMessages get() = true

    override suspend fun deleteMessage(inbox: Inbox, summary: MailSummary): Boolean {
        val data = query(
            "mutation(\$m: String!, \$id: String!) { delete(mailbox: \$m, id: \$id) }",
            mapOf("m" to inbox.id, "id" to summary.id),
        )
        return data.optBoolean("delete")
    }

    private fun parseDate(s: String): Long =
        runCatching { Instant.parse(s).toEpochMilli() }.getOrDefault(0L)
}
