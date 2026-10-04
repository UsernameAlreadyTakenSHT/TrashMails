package io.github.usernamealreadytakensht.trashmails.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Messages kept on the device for the providers that hand them over once (tempmail.lol consumes
 * an email when it is fetched). Per inbox key: the summaries with their bodies, newest first,
 * bounded in count, body size, total size and age. One file per address ([FileStore]), JSON, read
 * only when that address is listed. Writes go through [update], one at a time: a listing merging
 * what it fetched and a deletion can run at once (leaving a message screen starts a poll), and
 * neither must undo the other.
 *
 * Nothing here throws: a cache that cannot be read is an empty one, so a damaged or oversized
 * file can never keep the app from starting or an address from being removed.
 */
class MessageCache(private val prefs: Prefs, private val migrate: () -> Unit = {}) {
    constructor(context: Context) : this(FileStore(File(context.noBackupFilesDir, "messages")), context)

    private constructor(store: FileStore, context: Context) : this(store, migrate = { migrateFrom(context, store) })

    private val lock = Any()
    private var migrated = false
    /** Addresses removed during this run: nothing is written for them any more. */
    private val forgotten = mutableSetOf<String>()

    private fun ready() {
        if (!migrated) { migrated = true; migrate() }
    }

    fun load(inboxKey: String): List<MailSummary> = synchronized(lock) {
        val kept = read(inboxKey).map { it.first }
        // Expired entries leave the file as soon as they are seen, not at the next change.
        if (inboxKey in expired) update(inboxKey) { it } else kept
    }

    /** Addresses whose file still holds messages [read] found expired. */
    private val expired = mutableSetOf<String>()

    /** The kept messages of [inboxKey], each with when it was first kept (entries from before 0.5.4 count from now). */
    private fun read(inboxKey: String): List<Pair<MailSummary, Long>> = try {
        ready()
        val arr = prefs.getString(inboxKey)?.let { JSONArray(it) }
        val now = System.currentTimeMillis()
        if (arr == null) emptyList()
        else {
            val all = (0 until arr.length()).mapNotNull { i ->
                runCatching { arr.getJSONObject(i).let { o -> fromJson(o) to o.optLong("kept", now) } }.getOrNull()
            }
            all.filter { (_, kept) -> kept >= now - MAX_AGE_MS }.also { if (it.size < all.size) expired += inboxKey }
        }
    } catch (e: Exception) {
        emptyList()
    } catch (e: OutOfMemoryError) {
        emptyList()
    } catch (e: StackOverflowError) {
        emptyList()
    }

    /**
     * Replaces the list of [inboxKey] with what [change] makes of the current one, atomically with
     * respect to other updates, and returns what is now kept (newest first, bounded). Nothing is
     * written when [change] changes nothing.
     */
    fun update(inboxKey: String, change: (List<MailSummary>) -> List<MailSummary>): List<MailSummary> = synchronized(lock) {
        // A reply still in flight when its address was removed must not write it back.
        if (inboxKey in forgotten) return emptyList()
        val entries = read(inboxKey)
        val current = entries.map { it.first }
        val keptAt = entries.associate { (m, kept) -> m.id to kept }
        // Bodies bounded before comparing: a fresh one longer than its stored copy would otherwise
        // always differ from it and rewrite the file at every refresh.
        var next = change(current).sortedByDescending { it.date }.take(MAX_MESSAGES)
            .map { m -> m.bounded().copy(html = m.html?.take(MAX_BODY_CHARS), text = m.text?.take(MAX_BODY_CHARS)) }
        val purge = expired.remove(inboxKey)
        if (next != current || purge) {
            val now = System.currentTimeMillis()
            var json = serialize(next, keptAt, now)
            // The oldest go first until the address fits its budget (JSON escaping can grow a body).
            while (json.length > MAX_FILE_CHARS && next.isNotEmpty()) {
                next = next.dropLast(1)
                json = serialize(next, keptAt, now)
            }
            prefs.put(inboxKey to json)
        }
        next
    }

    /**
     * Each message with the time it was first kept: [read] drops it [MAX_AGE_MS] later. That time is
     * the app's, not the email's date, which the sender sets (an old one would drop it at once, one
     * in the future would keep it for ever).
     */
    private fun serialize(list: List<MailSummary>, keptAt: Map<String, Long>, now: Long): String {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it).put("kept", keptAt[it.id] ?: now)) }
        return arr.toString()
    }

    /** Drops what is kept for [inboxKey], for good: the address was removed. */
    fun clear(inboxKey: String) {
        synchronized(lock) {
            forgotten += inboxKey
            runCatching { ready(); prefs.remove(inboxKey) }
        }
    }

    /** True once [clear] ran for [inboxKey]: whatever a reply still in flight brings is dropped. */
    fun isForgotten(inboxKey: String): Boolean = synchronized(lock) { inboxKey in forgotten }

    private fun toJson(m: MailSummary) = JSONObject()
        .put("id", m.id)
        .put("from", m.from)
        .put("subject", m.subject)
        .put("date", m.date)
        .put("html", m.html)
        .put("text", m.text)
        .put("to", m.to)

    private fun fromJson(o: JSONObject) = MailSummary(
        id = o.getString("id"),
        from = o.textOrEmpty("from"),
        subject = o.textOrEmpty("subject"),
        date = o.optLong("date"),
        html = o.text("html"),
        text = o.text("text"),
        to = o.text("to"),
    )

    private companion object {
        /**
         * Up to 0.5.3 every address shared one SharedPreferences file, loaded whole: its entries
         * move to their own files, then it goes. If it cannot even be loaded (the out-of-memory it
         * could reach), it goes all the same.
         */
        fun migrateFrom(context: Context, store: FileStore) {
            if (!File(context.dataDir, "shared_prefs/messages.xml").exists()) return
            try {
                context.getSharedPreferences("messages", Context.MODE_PRIVATE).all
                    .forEach { (key, value) -> if (value is String) store.put(key to value) }
            } catch (e: Throwable) {
                // Nothing worth keeping can be read back.
            }
            runCatching { context.deleteSharedPreferences("messages") }
        }

        const val MAX_MESSAGES = 50
        const val MAX_BODY_CHARS = 64 * 1024
        /** What one address may take on disk and in memory once parsed, all messages together. */
        const val MAX_FILE_CHARS = 2 * 1024 * 1024
        /** Kept emails go this long after the app kept them: they hold codes and sign-in links long expired. */
        const val MAX_AGE_MS = 7 * 24 * 3_600_000L
    }
}
