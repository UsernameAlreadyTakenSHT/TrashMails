package io.github.usernamealreadytakensht.trashmails.data.providers

import io.github.usernamealreadytakensht.trashmails.data.Http
import io.github.usernamealreadytakensht.trashmails.data.HttpApi
import io.github.usernamealreadytakensht.trashmails.data.CreateOptions
import io.github.usernamealreadytakensht.trashmails.data.Inbox
import io.github.usernamealreadytakensht.trashmails.data.MailContent
import io.github.usernamealreadytakensht.trashmails.data.MailProvider
import io.github.usernamealreadytakensht.trashmails.data.MailSummary
import io.github.usernamealreadytakensht.trashmails.data.Provider
import io.github.usernamealreadytakensht.trashmails.data.ProviderException
import io.github.usernamealreadytakensht.trashmails.data.randomName
import io.github.usernamealreadytakensht.trashmails.data.sanitizeName
import org.json.JSONArray
import java.net.URLEncoder

/**
 * Inbox Kitten exposes raw Mailgun events.
 * `list?recipient=` is a prefix search: the exact address is filtered client-side.
 */
class InboxKittenProvider(private val http: HttpApi = Http) : MailProvider {
    override val provider = Provider.INBOX_KITTEN

    private companion object {
        const val BASE = "https://inboxkitten.com/api/v1/mail"
        const val DOMAIN = "inboxkitten.com"
    }

    override suspend fun createInbox(name: String?, options: CreateOptions): Inbox {
        val n = sanitizeName(name) ?: randomName()
        return Inbox(id = n, provider = provider, address = "$n@$DOMAIN")
    }

    override suspend fun listMessages(inbox: Inbox): List<MailSummary> {
        val body = http.get("$BASE/list?recipient=${URLEncoder.encode(inbox.id, "UTF-8")}")
        val arr = JSONArray(body)
        val seen = HashSet<String>()
        val out = ArrayList<MailSummary>()
        for (i in 0 until arr.length()) {
            val ev = arr.getJSONObject(i)
            if (!ev.optString("recipient").equals(inbox.address, ignoreCase = true)) continue
            val storage = ev.optJSONObject("storage") ?: continue
            val key = storage.optString("key")
            if (key.isBlank() || !seen.add(key)) continue
            val headers = ev.optJSONObject("message")?.optJSONObject("headers")
            out += MailSummary(
                id = ev.optString("id", key),
                from = headers?.optString("from").orEmpty().ifBlank {
                    ev.optJSONObject("envelope")?.optString("sender").orEmpty()
                },
                subject = headers?.optString("subject").orEmpty(),
                date = (ev.optDouble("timestamp", 0.0) * 1000).toLong(),
                ref = mapOf("key" to key, "region" to storage.optString("region")),
            )
        }
        return out.sortedByDescending { it.date }
    }

    override suspend fun getMessage(inbox: Inbox, summary: MailSummary): MailContent {
        val key = summary.ref["key"] ?: throw ProviderException("Missing message key")
        val region = summary.ref["region"].orEmpty()
        val html = http.get(
            "$BASE/getHtml?key=${URLEncoder.encode(key, "UTF-8")}&region=${URLEncoder.encode(region, "UTF-8")}"
        )
        return MailContent(html = html, text = null)
    }
}
