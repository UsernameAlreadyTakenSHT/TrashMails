package com.example.trashmails.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistence of created inboxes (SharedPreferences, JSON). */
class InboxStore(context: Context) {
    private val prefs = context.getSharedPreferences("inboxes", Context.MODE_PRIVATE)

    fun load(): List<Inbox> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> fromJson(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    fun save(inboxes: List<Inbox>) {
        val arr = JSONArray()
        inboxes.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun toJson(i: Inbox) = JSONObject()
        .put("id", i.id)
        .put("provider", i.provider.name)
        .put("address", i.address)
        .put("token", i.token)
        .put("createdAt", i.createdAt)
        .put("expiresAt", i.expiresAt)

    private fun fromJson(o: JSONObject): Inbox? {
        val provider = runCatching { Provider.valueOf(o.getString("provider")) }.getOrNull() ?: return null
        return Inbox(
            id = o.getString("id"),
            provider = provider,
            address = o.getString("address"),
            token = o.optString("token").takeIf { it.isNotBlank() },
            createdAt = o.optLong("createdAt"),
            expiresAt = if (o.has("expiresAt") && !o.isNull("expiresAt")) o.getLong("expiresAt") else null,
        )
    }

    private companion object {
        const val KEY = "list"
    }
}
