package io.github.usernamealreadytakensht.trashmails.data

import java.io.File
import java.io.FileOutputStream
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
            val tmp = tmpFor(target)
            // Synced before the rename: a power loss right after must find the old file or the new
            // one, not an empty one.
            FileOutputStream(tmp).use { out ->
                out.write(value.toString().toByteArray())
                out.fd.sync()
            }
            // Atomic over the old file on Android (Linux); the delete only serves the JVM tests on Windows.
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        }
    }

    /** Deletes the file, and the temporary one a write cut short may have left with the same data. */
    override fun remove(vararg keys: String) {
        keys.forEach { key -> fileFor(key).let { it.delete(); tmpFor(it).delete() } }
    }

    private fun tmpFor(target: File) = File(dir, target.name + ".tmp")
}
