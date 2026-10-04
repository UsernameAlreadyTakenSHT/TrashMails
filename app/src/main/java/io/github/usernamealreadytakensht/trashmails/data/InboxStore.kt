package io.github.usernamealreadytakensht.trashmails.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistence of created inboxes (SharedPreferences, JSON), their tokens sealed by [box]. */
class InboxStore(private val prefs: Prefs, private val box: SecretBox = PlainBox) {
    constructor(context: Context) : this(SharedPrefs(context, "inboxes"), KeystoreBox)

    fun load(): List<Inbox> {
        val raw = prefs.getString(KEY) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        // One malformed entry is dropped on its own, not the whole list with it.
        // Keys deduplicated: the home list is keyed by them, and a duplicate would crash it at every launch.
        val inboxes = (0 until arr.length()).mapNotNull { i -> runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull() }
            .distinctBy { it.key }
        // Tokens stored in clear by 0.5.4 and earlier are sealed at once rather than at the next change,
        // if sealing works right now (otherwise the same clear value would only be written back).
        val clear = (0 until arr.length()).any { i ->
            arr.optJSONObject(i)?.optString("token").orEmpty().let { it.isNotBlank() && !it.startsWith(KeystoreBox.PREFIX) }
        }
        if (clear && box.seal("probe").startsWith(KeystoreBox.PREFIX)) save(inboxes)
        return inboxes
    }

    /**
     * Sealed tokens that could not be opened at load, by inbox key, written back as they were: a
     * keystore failing for a moment must not turn into a token erased for good at the next save
     * (for mail.tm, the password, and with it the account).
     */
    private val unopened = mutableMapOf<String, String>()

    fun save(inboxes: List<Inbox>) {
        val arr = JSONArray()
        inboxes.forEach { arr.put(toJson(it)) }
        prefs.put(KEY to arr.toString())
    }

    private fun toJson(i: Inbox) = JSONObject()
        .put("id", i.id)
        .put("provider", i.provider.name)
        .put("address", i.address)
        .put("token", i.token?.let(box::seal) ?: unopened[i.key])
        .put("createdAt", i.createdAt)
        .put("expiresAt", i.expiresAt)

    private fun fromJson(o: JSONObject): Inbox? {
        val provider = Provider.fromName(o.optString("provider")) ?: return null
        // Guerrilla Mail session tokens were stored by earlier versions; they are useless after an hour.
        val stored = o.optString("token").takeIf { it.isNotBlank() && provider != Provider.GUERRILLA_MAIL }
        // A token that cannot be opened leaves this address without access for now, not the list;
        // its sealed value is kept to be written back (see [unopened]).
        val token = stored?.let(box::open)
        val inbox = Inbox(
            id = o.getString("id"),
            provider = provider,
            address = o.getString("address"),
            token = token,
            createdAt = o.optLong("createdAt"),
            expiresAt = if (o.has("expiresAt") && !o.isNull("expiresAt")) o.getLong("expiresAt") else null,
        )
        if (stored != null && token == null) unopened[inbox.key] = stored else unopened.remove(inbox.key)
        return inbox
    }

    private companion object {
        const val KEY = "list"
    }
}
