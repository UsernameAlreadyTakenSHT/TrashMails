package io.github.usernamealreadytakensht.trashmails.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistence of created inboxes (SharedPreferences, JSON), their tokens sealed by [box]. */
class InboxStore(private val prefs: Prefs, private val box: SecretBox = PlainBox) {
    constructor(context: Context) : this(SharedPrefs(context, "inboxes"), KeystoreBox())

    fun load(): List<Inbox> {
        val raw = prefs.getString(KEY) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        // One malformed entry is dropped on its own, not the whole list with it.
        // Keys deduplicated: the home list is keyed by them, and a duplicate would crash it at every launch.
        val inboxes = (0 until arr.length()).mapNotNull { i -> runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull() }
            .distinctBy { it.key }
        // Tokens stored in clear by 0.5.4 and earlier are sealed at once rather than at the next change.
        val clear = (0 until arr.length()).any { i ->
            arr.optJSONObject(i)?.optString("token").orEmpty().let { it.isNotBlank() && !it.startsWith(KeystoreBox.PREFIX) }
        }
        if (clear && box !== PlainBox) save(inboxes)
        return inboxes
    }

    fun save(inboxes: List<Inbox>) {
        val arr = JSONArray()
        inboxes.forEach { arr.put(toJson(it)) }
        prefs.put(KEY to arr.toString())
    }

    private fun toJson(i: Inbox) = JSONObject()
        .put("id", i.id)
        .put("provider", i.provider.name)
        .put("address", i.address)
        .put("token", i.token?.let(box::seal))
        .put("createdAt", i.createdAt)
        .put("expiresAt", i.expiresAt)

    private fun fromJson(o: JSONObject): Inbox? {
        val provider = Provider.fromName(o.optString("provider")) ?: return null
        return Inbox(
            id = o.getString("id"),
            provider = provider,
            address = o.getString("address"),
            // Guerrilla Mail session tokens were stored by earlier versions; they are useless after an hour.
            // A token that cannot be opened (keystore key lost) leaves this address without access, not the list.
            token = o.optString("token").takeIf { it.isNotBlank() && provider != Provider.GUERRILLA_MAIL }?.let(box::open),
            createdAt = o.optLong("createdAt"),
            expiresAt = if (o.has("expiresAt") && !o.isNull("expiresAt")) o.getLong("expiresAt") else null,
        )
    }

    private companion object {
        const val KEY = "list"
    }
}
