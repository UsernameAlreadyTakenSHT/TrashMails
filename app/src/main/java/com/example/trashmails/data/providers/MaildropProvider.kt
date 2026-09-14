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
import java.time.Instant

/** Maildrop: public GraphQL API, no authentication. */
class MaildropProvider : MailProvider {
    override val provider = Provider.MAILDROP

    private companion object {
        const val ENDPOINT = "https://api.maildrop.cc/graphql"
        const val DOMAIN = "maildrop.cc"
        val WHITESPACE = Regex("\\s+")
    }

    private suspend fun query(query: String, variables: Map<String, String>): JSONObject {
        val body = JSONObject().put("query", query).put("variables", JSONObject(variables)).toString()
        val json = JSONObject(Http.postJson(ENDPOINT, body))
        json.optJSONArray("errors")?.takeIf { it.length() > 0 }?.let {
            throw ProviderException(it.getJSONObject(0).optString("message", "Maildrop error"))
        }
        return json.getJSONObject("data")
    }

    override suspend fun createInbox(name: String?): Inbox {
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
                from = m.optString("headerfrom").ifBlank { m.optString("mailfrom") }.replace(WHITESPACE, " "),
                subject = m.optString("subject").replace(WHITESPACE, " "),
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
        val html = m.optString("html").takeIf { it.isNotBlank() }
        // `data` is the raw message (headers + body): only useful when there is no HTML.
        val raw = m.optString("data").takeIf { it.isNotBlank() }
        return MailContent(html = html, text = if (html == null) raw else null)
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
