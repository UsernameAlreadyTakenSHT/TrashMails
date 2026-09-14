package io.github.usernamealreadytakensht.trashmails.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Ids of the messages opened at least once, per inbox key (SharedPreferences, JSON). */
class ReadStore(context: Context) {
    private val prefs = context.getSharedPreferences("read", Context.MODE_PRIVATE)

    fun load(): Map<String, Set<String>> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().associateWith { key ->
            val arr = json.optJSONArray(key) ?: JSONArray()
            (0 until arr.length()).map { arr.optString(it) }.toSet()
        }
    }

    fun save(read: Map<String, Set<String>>) {
        val json = JSONObject()
        // Only the most recent ids are kept per inbox: providers hold a few dozen messages at most.
        read.forEach { (key, ids) -> json.put(key, JSONArray(ids.toList().takeLast(MAX_PER_INBOX))) }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private companion object {
        const val KEY = "ids"
        const val MAX_PER_INBOX = 200
    }
}
