package io.github.usernamealreadytakensht.trashmails.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per inbox key: the ids of the messages opened at least once, and the unread count at the last
 * listing (so the badges survive a restart). SharedPreferences, JSON.
 */
class ReadStore(private val prefs: Prefs) {
    constructor(context: Context) : this(SharedPrefs(context, "read"))

    data class State(val read: Map<String, Set<String>>, val unread: Map<String, Int>)

    fun load(): State {
        val ids = prefs.getString(KEY_IDS)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val read = ids?.keys()?.asSequence()?.associateWith { key ->
            val arr = ids.optJSONArray(key) ?: JSONArray()
            (0 until arr.length()).map { arr.optString(it) }.toSet()
        }.orEmpty()
        val counts = prefs.getString(KEY_UNREAD)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val unread = counts?.keys()?.asSequence()?.associateWith { counts.optInt(it) }.orEmpty()
        return State(read, unread)
    }

    fun save(state: State) {
        val ids = JSONObject()
        // Only the most recent ids are kept per inbox: providers hold a few dozen messages at most.
        state.read.forEach { (key, set) -> ids.put(key, JSONArray(set.toList().takeLast(MAX_PER_INBOX))) }
        val counts = JSONObject()
        state.unread.forEach { (key, n) -> counts.put(key, n) }
        prefs.put(KEY_IDS to ids.toString(), KEY_UNREAD to counts.toString())
    }

    private companion object {
        const val KEY_IDS = "ids"
        const val KEY_UNREAD = "unread"
        const val MAX_PER_INBOX = 200
    }
}
