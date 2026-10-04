package io.github.usernamealreadytakensht.trashmails.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** ClipDescription.EXTRA_IS_SENSITIVE, by its value: the constant only exists from Android 13. */
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

/**
 * Copies [text]; [confirmation] is what the toast says on Android 10–12 (Android 13+ shows its own).
 * A [sensitive] clip (a message body or link, which may hold a code or token; the address of a
 * public inbox, which opens it) is flagged so the system preview and keyboard histories hide it.
 */
fun Context.copyToClipboard(text: String, confirmation: String, sensitive: Boolean = false) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(if (sensitive) "message" else "email", text)
    if (sensitive) {
        // The constant is Android 13's, but keyboards with a clipboard history (Gboard, SwiftKey…)
        // honour the same key on older versions too, so it is set on all of them.
        clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
    }
    try {
        cm.setPrimaryClip(clip)
    } catch (_: RuntimeException) {
        // A body of several megabytes exceeds what the clipboard service accepts (TransactionTooLargeException).
        Toast.makeText(this, "Too large to copy", Toast.LENGTH_SHORT).show()
        return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, confirmation, Toast.LENGTH_SHORT).show()
    }
}

// DateFormat instances are costly to build and not thread-safe: one set per thread, built once.
private val dateFormats = ThreadLocal.withInitial {
    Triple(
        DateFormat.getDateInstance(DateFormat.SHORT),
        DateFormat.getTimeInstance(DateFormat.SHORT),
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT),
    )
}

/** Time only for today, date and time otherwise. */
fun formatDate(millis: Long): String {
    if (millis <= 0) return ""
    val (date, time, dateTime) = dateFormats.get()!!
    val d = Date(millis)
    return if (date.format(d) == date.format(Date())) time.format(d) else dateTime.format(d)
}

fun formatRemaining(expiresAt: Long?, now: Long = System.currentTimeMillis()): String? {
    expiresAt ?: return null
    val left = expiresAt - now
    if (left <= 0) return "expired"
    val h = left / 3_600_000
    val m = (left % 3_600_000) / 60_000
    return if (h > 0) "expires in $h h ${m.toString().padStart(2, '0')} min" else "expires in $m min"
}

/** "3 h 12 min" / "12 min" for a duration in milliseconds. */
fun formatDuration(millis: Long): String {
    val left = millis.coerceAtLeast(0)
    val h = left / 3_600_000
    val m = (left % 3_600_000) / 60_000
    return if (h > 0) "$h h ${m.toString().padStart(2, '0')} min" else "${m.coerceAtLeast(1)} min"
}

/**
 * The current time, updated at each turn of the minute while on screen: what a label counting
 * down in minutes ("expires in 12 min") reads so it does not stay frozen until something else
 * redraws it.
 */
@Composable
fun rememberMinuteClock(): Long = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(60_000 - value % 60_000)
        value = System.currentTimeMillis()
    }
}.value
