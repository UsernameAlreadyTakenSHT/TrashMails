package com.example.trashmails.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

/**
 * Copies [text]; [confirmation] is what the toast says on Android 10–12 (Android 13+ shows its own).
 * A [sensitive] clip (a message body, which may hold a verification code) is flagged so the
 * system preview hides it, and its confirmation never repeats the content.
 */
fun Context.copyToClipboard(text: String, confirmation: String, sensitive: Boolean = false) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(if (sensitive) "message" else "email", text)
    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    cm.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, confirmation, Toast.LENGTH_SHORT).show()
    }
}

fun formatDate(millis: Long): String {
    if (millis <= 0) return ""
    val d = Date(millis)
    val sameDay = DateFormat.getDateInstance(DateFormat.SHORT).format(d) ==
        DateFormat.getDateInstance(DateFormat.SHORT).format(Date())
    return if (sameDay) DateFormat.getTimeInstance(DateFormat.SHORT).format(d)
    else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(d)
}

fun formatRemaining(expiresAt: Long?): String? {
    expiresAt ?: return null
    val left = expiresAt - System.currentTimeMillis()
    if (left <= 0) return "expired"
    val h = left / 3_600_000
    val m = (left % 3_600_000) / 60_000
    return if (h > 0) "expires in ${h}h${m.toString().padStart(2, '0')}" else "expires in ${m} min"
}

/** "3h12" / "12 min" for a duration in milliseconds. */
fun formatDuration(millis: Long): String {
    val left = millis.coerceAtLeast(0)
    val h = left / 3_600_000
    val m = (left % 3_600_000) / 60_000
    return if (h > 0) "${h}h${m.toString().padStart(2, '0')}" else "${m.coerceAtLeast(1)} min"
}
