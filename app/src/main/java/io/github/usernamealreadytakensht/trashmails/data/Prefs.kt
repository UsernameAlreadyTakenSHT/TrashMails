package io.github.usernamealreadytakensht.trashmails.data

import android.content.Context

/** The few SharedPreferences calls the stores make, so they can run on the JVM against a map. */
interface Prefs {
    fun getString(key: String): String?
    fun getInt(key: String, default: Int): Int
    fun getBoolean(key: String, default: Boolean): Boolean
    /** Writes every entry (String, Int or Boolean values) in one edit. */
    fun put(vararg entries: Pair<String, Any>)
    /** Drops the entries, so a forgotten inbox leaves no key behind. */
    fun remove(vararg keys: String)
}

/** [Prefs] over a private SharedPreferences file. */
class SharedPrefs(context: Context, name: String) : Prefs {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    override fun put(vararg entries: Pair<String, Any>) {
        val edit = prefs.edit()
        for ((key, value) in entries) {
            when (value) {
                is String -> edit.putString(key, value)
                is Int -> edit.putInt(key, value)
                is Boolean -> edit.putBoolean(key, value)
                else -> throw IllegalArgumentException("Unsupported preference type for $key")
            }
        }
        edit.apply()
    }

    override fun remove(vararg keys: String) {
        val edit = prefs.edit()
        keys.forEach { edit.remove(it) }
        edit.apply()
    }
}
