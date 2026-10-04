package io.github.usernamealreadytakensht.trashmails.data

import android.content.Context

/** The few SharedPreferences calls the stores make, so they can run on the JVM against a map. */
interface Prefs {
    fun getString(key: String): String?
    fun getInt(key: String, default: Int): Int
    fun getBoolean(key: String, default: Boolean): Boolean
    /** Writes every entry (String, Int or Boolean values) in one edit. */
    fun put(vararg entries: Pair<String, Any>)
    /**
     * As [put], but on disk before it returns: for a value the server has already replaced on its
     * side, which a process killed before an asynchronous write would leave stale for good.
     */
    fun putNow(vararg entries: Pair<String, Any>) = put(*entries)
    /** Drops the entries, so a forgotten inbox leaves no key behind. */
    fun remove(vararg keys: String)
}

/** [Prefs] over a private SharedPreferences file. */
class SharedPrefs(context: Context, name: String) : Prefs {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    override fun put(vararg entries: Pair<String, Any>) = edit(entries).apply()

    override fun putNow(vararg entries: Pair<String, Any>) {
        edit(entries).commit()
    }

    private fun edit(entries: Array<out Pair<String, Any>>): android.content.SharedPreferences.Editor {
        val edit = prefs.edit()
        for ((key, value) in entries) {
            when (value) {
                is String -> edit.putString(key, value)
                is Int -> edit.putInt(key, value)
                is Boolean -> edit.putBoolean(key, value)
                else -> throw IllegalArgumentException("Unsupported preference type for $key")
            }
        }
        return edit
    }

    override fun remove(vararg keys: String) {
        val edit = prefs.edit()
        keys.forEach { edit.remove(it) }
        edit.apply()
    }
}
