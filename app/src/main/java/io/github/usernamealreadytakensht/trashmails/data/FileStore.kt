package io.github.usernamealreadytakensht.trashmails.data

import java.io.File
import java.security.MessageDigest

/**
 * [Prefs] over a directory, one file per key (named by a hash of the key): a value is read only
 * when asked for, written alone, and removing it deletes its file. For values too big to share a
 * SharedPreferences file, which is parsed whole at first access and rewritten whole at each change.
 * Writes go to a temporary file renamed over the old one, so a crash mid-write leaves the old value.
 */
class FileStore(private val dir: File) : Prefs {
    private fun fileFor(key: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, hash)
    }

    override fun getString(key: String): String? = fileFor(key).takeIf { it.isFile }?.readText()
    override fun getInt(key: String, default: Int): Int = getString(key)?.toIntOrNull() ?: default
    override fun getBoolean(key: String, default: Boolean): Boolean = getString(key)?.toBooleanStrictOrNull() ?: default

    override fun put(vararg entries: Pair<String, Any>) {
        dir.mkdirs()
        for ((key, value) in entries) {
            val target = fileFor(key)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(value.toString())
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        }
    }

    override fun remove(vararg keys: String) {
        keys.forEach { fileFor(it).delete() }
    }
}
