package io.github.usernamealreadytakensht.trashmails.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals the secrets the app stores (a mail.tm password, inbox tokens, DropMail.me restore keys and
 * device token) before they reach a preferences file, and opens them back. Only those values are
 * sealed, never a whole list: should the key be lost, one address loses its access, not the app
 * its addresses.
 */
interface SecretBox {
    /**
     * [plain] sealed for [context] (what the value is for, e.g. the inbox it belongs to): it opens
     * for that context only, so sealed values swapped between entries in the files do not open.
     */
    fun seal(plain: String, context: String): String
    /** The secret, or null when it cannot be opened. A value stored before sealing existed comes back as is. */
    fun open(stored: String, context: String): String?
}

/** No sealing: the JVM tests, and the fallback where the keystore is unusable. */
object PlainBox : SecretBox {
    override fun seal(plain: String, context: String) = plain
    override fun open(stored: String, context: String): String? = if (KeystoreBox.isSealed(stored)) null else stored
}

/**
 * AES-256-GCM with a key that lives in the Android Keystore (hardware-backed where the device has
 * it) and never leaves it: copying the app's files, from a backup tool or a rooted shell, no longer
 * yields the secrets. Sealed values read `k2:` + Base64(IV + ciphertext), the context as associated
 * data; `k1:` values (0.5.5 and 0.5.6, no context) still open, and are sealed again when read.
 *
 * One object for the whole process: two instances could each find no key and generate one under
 * the same alias at once, the second replacing the first. The key is looked up or generated once,
 * under the lazy's lock.
 */
object KeystoreBox : SecretBox {
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    /** Sealed, or as is if the keystore fails (some devices): never worse than before sealing existed. */
    override fun seal(plain: String, context: String): String = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        cipher.updateAAD(context.toByteArray())
        PREFIX + Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()))
    }.getOrDefault(plain)

    override fun open(stored: String, context: String): String? {
        val legacy = stored.startsWith(LEGACY_PREFIX)
        if (!legacy && !stored.startsWith(PREFIX)) return stored
        return runCatching {
            val bytes = Base64.getDecoder().decode(stored.substring(PREFIX.length))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, IV_BYTES))
            if (!legacy) cipher.updateAAD(context.toByteArray())
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
        }.getOrNull()
    }

    /** Sealed in either format. */
    fun isSealed(stored: String) = stored.startsWith(PREFIX) || stored.startsWith(LEGACY_PREFIX)

    /** Sealed in the current format: anything else (clear, or `k1:`) is worth sealing again. */
    fun isCurrent(stored: String) = stored.startsWith(PREFIX)

    const val PREFIX = "k2:"
    private const val LEGACY_PREFIX = "k1:"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "trashmails-secrets"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
}

/**
 * [Prefs] whose values are sealed by [box] before they reach [inner], each for "[purpose]:key". A
 * value that cannot be opened reads as missing; one stored before sealing reads as it is, and is
 * sealed when first read.
 */
class SealedPrefs(private val inner: Prefs, private val box: SecretBox, private val purpose: String) : Prefs {
    private fun context(key: String) = "$purpose:$key"
    private fun sealed(entries: Array<out Pair<String, Any>>) =
        entries.map { (key, value) -> key to box.seal(value.toString(), context(key)) }.toTypedArray()

    override fun getString(key: String): String? {
        val raw = inner.getString(key) ?: return null
        val value = box.open(raw, context(key)) ?: return null
        // Stored before sealing: sealed now (if sealing works) rather than at a next write that
        // may never come for an address that gets no more mail.
        if (!KeystoreBox.isCurrent(raw)) {
            box.seal(value, context(key)).takeIf(KeystoreBox::isCurrent)?.let { inner.put(key to it) }
        }
        return value
    }
    override fun getInt(key: String, default: Int): Int = getString(key)?.toIntOrNull() ?: default
    override fun getBoolean(key: String, default: Boolean): Boolean = getString(key)?.toBooleanStrictOrNull() ?: default
    override fun put(vararg entries: Pair<String, Any>) = inner.put(*sealed(entries))
    override fun putNow(vararg entries: Pair<String, Any>) = inner.putNow(*sealed(entries))
    override fun remove(vararg keys: String) = inner.remove(*keys)
}
