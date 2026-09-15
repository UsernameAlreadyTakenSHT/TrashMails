package io.github.usernamealreadytakensht.trashmails.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Messages kept on the device for the providers that hand them over once (tempmail.lol consumes
 * an email when it is fetched). Per inbox key: the summaries with their bodies, newest first,
 * bounded in count and body size. SharedPreferences, JSON. Writes go through [update], one at a
 * time: a listing merging what it fetched and a deletion can run at once (leaving a message
 * screen starts a poll), and neither must undo the other.
 */
class MessageCache(private val prefs: Prefs) {
    constructor(context: Context) : this(SharedPrefs(context, "messages"))

    fun load(inboxKey: String): List<MailSummary> {
        val arr = prefs.getString(inboxKey)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull() }
    }

    /**
     * Replaces the list of [inboxKey] with what [change] makes of the current one, atomically with
     * respect to other updates, and returns what is now kept (newest first, bounded). Nothing is
     * written when [change] changes nothing.
     */
    fun update(inboxKey: String, change: (List<MailSummary>) -> List<MailSummary>): List<MailSummary> = synchronized(lock) {
        val current = load(inboxKey)
        val next = change(current).sortedByDescending { it.date }.take(MAX_MESSAGES)
        if (next != current) {
            val arr = JSONArray()
            next.forEach { arr.put(toJson(it)) }
            prefs.put(inboxKey to arr.toString())
        }
        next
    }

    private val lock = Any()

    fun clear(inboxKey: String) = prefs.put(inboxKey to "")

    private fun toJson(m: MailSummary) = JSONObject()
        .put("id", m.id)
        .put("from", m.from)
        .put("subject", m.subject)
        .put("date", m.date)
        .put("html", m.html?.take(MAX_BODY_CHARS))
        .put("text", m.text?.take(MAX_BODY_CHARS))

    private fun fromJson(o: JSONObject) = MailSummary(
        id = o.getString("id"),
        from = o.textOrEmpty("from"),
        subject = o.textOrEmpty("subject"),
        date = o.optLong("date"),
        html = o.text("html"),
        text = o.text("text"),
    )

    private companion object {
        const val MAX_MESSAGES = 100
        const val MAX_BODY_CHARS = 256 * 1024
    }
}
