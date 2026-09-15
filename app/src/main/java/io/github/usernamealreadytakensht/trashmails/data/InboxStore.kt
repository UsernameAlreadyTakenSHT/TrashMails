package io.github.usernamealreadytakensht.trashmails.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistence of created inboxes (SharedPreferences, JSON). */
class InboxStore(context: Context) {
    private val prefs = context.getSharedPreferences("inboxes", Context.MODE_PRIVATE)

    fun load(): List<Inbox> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        // One malformed entry is dropped on its own, not the whole list with it.
        return (0 until arr.length()).mapNotNull { i -> runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull() }
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
            // Guerrilla Mail session tokens were stored by earlier versions; they are useless after an hour.
            token = o.optString("token").takeIf { it.isNotBlank() && provider != Provider.GUERRILLA_MAIL },
            createdAt = o.optLong("createdAt"),
            expiresAt = if (o.has("expiresAt") && !o.isNull("expiresAt")) o.getLong("expiresAt") else null,
        )
    }

    private companion object {
        const val KEY = "list"
    }
}
