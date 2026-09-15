package io.github.usernamealreadytakensht.trashmails.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import io.github.usernamealreadytakensht.trashmails.data.ProviderException
import org.json.JSONException
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** A short, user-facing line for a failed provider call, instead of the exception's own text. */
fun Throwable.userMessage(fallback: String): String = when (this) {
    is ProviderException -> message?.forDisplay() ?: fallback
    is UnknownHostException -> "No internet connection"
    is SSLException -> "Secure connection failed"
    is InterruptedIOException -> "The server took too long to answer"
    is SocketException -> "Could not reach the server"
    is JSONException -> "Unexpected reply from the server"
    else -> fallback
}

/** Provider text may come from a server: one line, no control characters, bounded. */
private fun String.forDisplay(): String? =
    replace(CONTROL_CHARS, " ").trim().take(MAX_MESSAGE_LENGTH).takeIf { it.isNotEmpty() }

private val CONTROL_CHARS = Regex("""[p{Cntrl}s]+""")
private const val MAX_MESSAGE_LENGTH = 200

/**
 * A [SnackbarHostState] fed by the ViewModel's [error] (with an OK action) and [notice] (short,
 * no action). Each is cleared once its snackbar goes away, or when the screen is left, so the
 * same text can show again later and never resurfaces on return. The dismiss callbacks receive
 * the text that was shown: a newer message that replaced it meanwhile must not be wiped.
 */
@Composable
fun rememberMessageHost(
    error: String?,
    notice: String?,
    onDismissError: (String) -> Unit,
    onDismissNotice: (String) -> Unit,
): SnackbarHostState {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error ?: return@LaunchedEffect
        try {
            host.showSnackbar(error, actionLabel = "OK", duration = SnackbarDuration.Long)
        } finally {
            onDismissError(error)
        }
    }
    LaunchedEffect(notice) {
        notice ?: return@LaunchedEffect
        try {
            host.showSnackbar(notice, duration = SnackbarDuration.Short)
        } finally {
            onDismissNotice(notice)
        }
    }
    return host
}
