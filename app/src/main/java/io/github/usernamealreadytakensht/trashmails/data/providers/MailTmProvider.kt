package io.github.usernamealreadytakensht.trashmails.data.providers

import io.github.usernamealreadytakensht.trashmails.data.Http
import io.github.usernamealreadytakensht.trashmails.data.HttpApi
import io.github.usernamealreadytakensht.trashmails.data.HttpException
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
import org.json.JSONObject
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * mail.tm: a private inbox behind an account (address + password) and a bearer token.
 * The password is what gets persisted ([Inbox.token]); the JWT is fetched on demand and kept in
 * memory only, refreshed once on 401. The domain is whichever one mail.tm currently offers.
 */
class MailTmProvider(private val http: HttpApi = Http) : MailProvider {
    override val provider = Provider.MAIL_TM

    private companion object {
        const val BASE = "https://api.mail.tm"
    }

    /** Ids come from the server: encoded so one can never change the path (`../accounts/x`). */
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** JWT per inbox id, for this process lifetime. */
    private val jwts = ConcurrentHashMap<String, String>()

    private suspend fun activeDomain(): String {
        val arr = JSONObject(http.get("$BASE/domains")).optJSONArray("hydra:member") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val d = arr.getJSONObject(i)
            if (d.optBoolean("isActive") && !d.optBoolean("isPrivate")) return d.getString("domain")
        }
        throw ProviderException("mail.tm has no domain available right now")
    }

    private fun credentials(inbox: Inbox) = JSONObject()
        .put("address", inbox.address)
        .put("password", inbox.token ?: throw ProviderException("Missing password"))
        .toString()

    private suspend fun jwt(inbox: Inbox, refresh: Boolean = false): String {
        if (!refresh) jwts[inbox.id]?.let { return it }
        val reply = try {
            http.postJson("$BASE/token", credentials(inbox))
        } catch (e: HttpException) {
            // Rejected credentials: the account was deleted (by mail.tm after inactivity, or elsewhere).
            if (e.code == 401) throw AccountGoneException()
            throw e
        }
        val token = JSONObject(reply).getString("token")
        jwts[inbox.id] = token
        return token
    }

    /** Runs [call] with a bearer header, retrying once with a fresh token on 401. */
    private suspend fun <T> authed(inbox: Inbox, call: suspend (Map<String, String>) -> T): T {
        val headers = { t: String -> mapOf("Authorization" to "Bearer $t") }
        return try {
            call(headers(jwt(inbox)))
        } catch (e: HttpException) {
            if (e.code != 401) throw e
            call(headers(jwt(inbox, refresh = true)))
        }
    }

    override suspend fun createInbox(name: String?, options: CreateOptions): Inbox {
        val local = sanitizeName(name) ?: randomName()
        val address = "$local@${activeDomain()}"
        val password = randomName(24)
        val body = JSONObject().put("address", address).put("password", password).toString()
        val account = try {
            JSONObject(http.postJson("$BASE/accounts", body))
        } catch (e: HttpException) {
            throw when (e.code) {
                422 -> ProviderException(violationMessage(e.body) ?: "This address is already taken")
                429 -> ProviderException("mail.tm is rate limiting: try again in a moment")
                else -> e
            }
        }
        return Inbox(
            id = account.getString("id"),
            provider = provider,
            address = account.optString("address", address),
            token = password,
        )
    }

    override suspend fun listMessages(inbox: Inbox): List<MailSummary> {
        val json = authed(inbox) { h -> JSONObject(http.get("$BASE/messages", h)) }
        val arr = json.optJSONArray("hydra:member") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            val from = m.optJSONObject("from")
            MailSummary(
                id = m.getString("id"),
                from = from?.optString("address").orEmpty().ifBlank { from?.optString("name").orEmpty() },
                subject = m.optString("subject"),
                date = parseDate(m.optString("createdAt")),
            )
        }.sortedByDescending { it.date }
    }

    override suspend fun getMessage(inbox: Inbox, summary: MailSummary): MailContent {
        val m = authed(inbox) { h -> JSONObject(http.get("$BASE/messages/${enc(summary.id)}", h)) }
        // `html` is documented as a list of parts; be tolerant if it ever comes as one string.
        val html = when (val h = m.opt("html")) {
            is JSONArray -> (0 until h.length()).joinToString("\n") { h.optString(it) }
            is String -> h
            else -> ""
        }.takeIf { it.isNotBlank() }
        return MailContent(html = html, text = m.optString("text").takeIf { it.isNotBlank() })
    }

    override val canDeleteMessages get() = true

    override suspend fun deleteMessage(inbox: Inbox, summary: MailSummary): Boolean {
        authed(inbox) { h -> http.delete("$BASE/messages/${enc(summary.id)}", h) }
        return true
    }

    override val canDeleteInbox get() = true

    /**
     * Deletes the account itself: every message goes with it and the address is freed. An account
     * already gone (purged by mail.tm, or deleted elsewhere) counts as deleted, so the address can
     * still be removed from the app.
     */
    override suspend fun deleteInbox(inbox: Inbox): Boolean {
        try {
            authed(inbox) { h -> http.delete("$BASE/accounts/${enc(inbox.id)}", h) }
        } catch (e: AccountGoneException) {
            // Nothing left to delete.
        } catch (e: HttpException) {
            if (e.code != 404) throw e
        }
        jwts.remove(inbox.id)
        return true
    }

    /** The credentials are refused: mail.tm removed the account (after inactivity) or it was deleted elsewhere. */
    private class AccountGoneException :
        ProviderException("This mail.tm account no longer exists: remove the address")

    /**
     * The reason of a 422, from the API Platform error body: the violation messages when there
     * are some ("address: This value is already used."), else the description / detail line.
     */
    private fun violationMessage(body: String): String? = runCatching {
        val json = JSONObject(body)
        val violations = json.optJSONArray("violations")
        if (violations != null && violations.length() > 0) {
            (0 until violations.length()).joinToString("; ") { i ->
                val v = violations.getJSONObject(i)
                listOf(v.optString("propertyPath"), v.optString("message")).filter { it.isNotBlank() }.joinToString(": ")
            }
        } else {
            json.optString("hydra:description").ifBlank { json.optString("detail") }
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }?.let { "mail.tm: $it" }

    private fun parseDate(iso: String): Long =
        runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
}
