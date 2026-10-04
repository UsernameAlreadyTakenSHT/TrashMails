package io.github.usernamealreadytakensht.trashmails.ui.screens

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.usernamealreadytakensht.trashmails.data.MailContent
import io.github.usernamealreadytakensht.trashmails.data.MailSummary
import io.github.usernamealreadytakensht.trashmails.data.Provider
import io.github.usernamealreadytakensht.trashmails.data.Settings
import io.github.usernamealreadytakensht.trashmails.ui.CopyIcon
import io.github.usernamealreadytakensht.trashmails.ui.copyToClipboard
import io.github.usernamealreadytakensht.trashmails.ui.formatDate
import io.github.usernamealreadytakensht.trashmails.ui.localDeleteNote
import java.io.ByteArrayInputStream
import java.io.File
import java.net.IDN

/**
 * One message. The body is plain text unless [Settings.renderHtml] is on; either way the
 * overflow menu switches this message alone to the other rendering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    provider: Provider,
    summary: MailSummary,
    content: MailContent?,
    loading: Boolean,
    error: String?,
    settings: Settings,
    deletesLocally: Boolean,
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    var showHtml by rememberSaveable(summary.id) { mutableStateOf(settings.renderHtml) }
    var loadImages by rememberSaveable(summary.id) { mutableStateOf(settings.loadImages) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    /** A tapped link waiting for the user's go-ahead (as a string: Uri is not saveable). */
    var pendingLink by rememberSaveable { mutableStateOf<String?>(null) }
    val hasHtml = content?.html != null
    val onLink: (Uri) -> Unit = { uri ->
        when {
            !isAllowedLink(uri) -> Toast.makeText(context, "Links of this kind cannot be opened", Toast.LENGTH_SHORT).show()
            // No real link is this long; one that is would crash the app on its way to the browser
            // or into the saved state (both go through a binder limited to about 1 MB).
            uri.toString().length > MAX_LINK_CHARS -> Toast.makeText(context, "This link is too long to open", Toast.LENGTH_SHORT).show()
            settings.confirmLinks -> pendingLink = uri.toString()
            else -> openLink(context, uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(summary.subject.ifBlank { "(no subject)" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    val body = content?.text
                    if (body != null) IconButton(onClick = { context.copyToClipboard(body, "Message text copied", sensitive = true) }) {
                        Icon(CopyIcon, contentDescription = "Copy message text")
                    }
                    if (onDelete != null) IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete message")
                    }
                    // Always present so the buttons keep their place while the body loads.
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (showHtml && hasHtml) "View as plain text" else "View as HTML") },
                            enabled = hasHtml,
                            onClick = { showHtml = !showHtml; menuOpen = false },
                        )
                        if (hasHtml && showHtml && !loadImages) DropdownMenuItem(
                            text = { Text("Load remote images") },
                            onClick = { loadImages = true; menuOpen = false },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(summary.from.ifBlank { "(unknown sender)" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                summary.to?.let {
                    Text("to $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${formatDate(summary.date)} · ${provider.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            val text = content?.text
            when {
                content == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator()
                        error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                        else -> Text("(empty message)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                showHtml && content.html != null -> {
                    if (!loadImages) BodyBanner("Remote images not loaded", "Load") { loadImages = true }
                    HtmlBody(content.html, loadImages, onLink)
                }
                !text.isNullOrBlank() -> {
                    if (hasHtml) BodyBanner("Shown as plain text", "View as HTML") { showHtml = true }
                    PlainBody(text, onLink)
                }
                // An HTML mail with no text at all (images only): say so, and offer the HTML view.
                hasHtml -> {
                    BodyBanner("No text in this message", "View as HTML") { showHtml = true }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("(empty message)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (confirmDelete && onDelete != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this message?") },
        text = {
            Text(
                if (deletesLocally) localDeleteNote(provider)
                else "It will be deleted on ${provider.label}; this cannot be undone."
            )
        },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )

    pendingLink?.let { link ->
        LinkDialog(
            url = link,
            onOpen = { pendingLink = null; openLink(context, Uri.parse(link)) },
            // Sensitive: a sign-in or reset link carries a token the clipboard preview would show.
            onCopy = { pendingLink = null; context.copyToClipboard(link, "Link copied", sensitive = true) },
            onDismiss = { pendingLink = null },
        )
    }
}

/** Web addresses in a plain body; trailing punctuation is left out of the link. */
private val URL_IN_TEXT = Regex("""https?://[^\s<>"']+""")
private const val URL_TRAIL = ".,;:!?)]}>'\""

/** Beyond this many characters the plain body is cut, with a button to lay out the rest. */
private const val PLAIN_BODY_PREVIEW = 200_000

/** The plain body, with every http(s) address tappable through the same link policy as HTML. */
@Composable
private fun PlainBody(fullText: String, onLink: (Uri) -> Unit) {
    // A multi-megabyte text (an attachment inlined as text) would take seconds to lay out in one go.
    var showAll by rememberSaveable(fullText.length) { mutableStateOf(fullText.length <= PLAIN_BODY_PREVIEW) }
    val text = if (showAll) fullText else fullText.substring(0, PLAIN_BODY_PREVIEW)
    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    val annotated = remember(text, linkStyle) {
        buildAnnotatedString {
            var last = 0
            for (m in URL_IN_TEXT.findAll(text)) {
                val url = m.value.trimEnd { it in URL_TRAIL }
                if (url.length < 10) continue
                append(text, last, m.range.first)
                withLink(LinkAnnotation.Url(url, linkStyle) { onLink(Uri.parse(url)) }) { append(url) }
                last = m.range.first + url.length
            }
            append(text, last, text.length)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SelectionContainer {
            Text(annotated, style = MaterialTheme.typography.bodyMedium)
        }
        if (!showAll) TextButton(onClick = { showAll = true }) { Text("Show all (${fullText.length / 1000} k characters)") }
    }
}

/** One line above the body telling what is not shown, with the action that shows it. */
@Composable
private fun BodyBanner(text: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onAction) { Text(action) }
    }
    HorizontalDivider()
}

/**
 * The HTML body in a WebView. Unless [loadImages], every network load is blocked (images, styles,
 * fonts, frames): the sender learns nothing from the message being opened. Inline data: images
 * still show.
 */
@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlBody(html: String, loadImages: Boolean, onLink: (Uri) -> Unit) {
    // Follow the app theme (which may override the system one), not the system setting.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val background = MaterialTheme.colorScheme.surface.toArgb()
    // Reload only when the content or the image policy changes, not on every recomposition.
    val key = html.hashCode() to loadImages
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                // Theme background until the page paints (no white flash), and darkened rendering in
                // dark mode: algorithmic darkening from Android 13, the older force-dark before.
                setBackgroundColor(background)
                if (dark) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) settings.isAlgorithmicDarkeningAllowed = true
                    else settings.forceDark = WebSettings.FORCE_DARK_ON
                }
                webViewClient = MailWebViewClient(onLink)
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                // Loaded images leave nothing behind (no HTTP cache, no cookie) that a sender could
                // use to tell two disposable addresses are read on the same device.
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                CookieManager.getInstance().setAcceptCookie(false)
                // The default user agent names the device model, Android build and WebView version:
                // a stable mark next to the IP once images load. Chrome's own reduced form instead.
                settings.userAgentString = GENERIC_USER_AGENT
            }
        },
        update = { view ->
            if (view.tag != key) {
                view.tag = key
                view.settings.blockNetworkLoads = !loadImages
                (view.webViewClient as MailWebViewClient).blockRemote = !loadImages
                view.loadDataWithBaseURL(null, withViewport(html), "text/html", "utf-8", null)
            }
        },
        // Leaving the message (or switching to plain text) frees the renderer at once.
        onRelease = { view ->
            view.stopLoading()
            view.clearCache(true)
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
            view.destroy()
        },
    )
}

/**
 * A link the user tapped leaves the app ([onLink]); nothing ever navigates inside the mail view:
 * - navigations the user did not tap (`<meta http-equiv="refresh">`, frames) are dropped, so a
 *   sender cannot open a page — and reveal the reader's IP — merely by being read;
 * - a navigation of the mail frame itself, or anything but a GET, gets an empty reply whatever
 *   the image policy: Android does not route POST navigations (forms) through
 *   [shouldOverrideUrlLoading], so a form could otherwise load the sender's page in-app;
 * - while [blockRemote], every other resource request gets an empty reply too — a second guard
 *   behind WebSettings.blockNetworkLoads.
 * Requests are intercepted off the main thread, hence the flag kept here rather than read from
 * the WebView.
 */
private class MailWebViewClient(private val onLink: (Uri) -> Unit) : WebViewClient() {
    @Volatile var blockRemote = true

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (request.isForMainFrame && request.hasGesture()) onLink(request.url)
        return true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
        if (blockRemote || request.isForMainFrame || request.method != "GET") EMPTY() else null

    private companion object {
        val EMPTY = { WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) }
    }
}

/**
 * Shows where a link leads before leaving the app: the site (or address) in large type, since that
 * is what to check, and the full URL under it.
 */
@Composable
private fun LinkDialog(url: String, onOpen: () -> Unit, onCopy: () -> Unit, onDismiss: () -> Unit) {
    val uri = Uri.parse(url)
    val site = if (uri.scheme.equals("mailto", ignoreCase = true)) uri.schemeSpecificPart else uri.host?.let(::displayHost) ?: url
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (uri.scheme.equals("mailto", ignoreCase = true)) "Write to this address?" else "Open this site?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(site, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SelectionContainer {
                    Text(
                        url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text("Open") } },
        // Two buttons straight in the slot: the dialog wraps them when the font is scaled up.
        dismissButton = {
            TextButton(onClick = onCopy) { Text("Copy link") }
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * A host as the link dialog shows it: a non-ASCII one with its punycode form too, so a look-alike
 * letter cannot pass for the real site. When punycode cannot be computed (java.net.IDN knows only
 * Unicode 3.2: a later character, even an invisible one, made it fail and the bare Unicode host was
 * shown), every non-ASCII character is spelled out as a code point instead.
 */
internal fun displayHost(host: String): String {
    if (host.all { it.code < 0x80 }) return host
    val ascii = runCatching { IDN.toASCII(host, IDN.ALLOW_UNASSIGNED) }.getOrNull()
        ?: host.codePoints().toArray().joinToString("") { cp -> if (cp < 0x80) cp.toChar().toString() else "\\u{%X}".format(cp) }
    return if (ascii.equals(host, ignoreCase = true)) host else "$host ($ascii)"
}

/** http(s) and mailto only: the sender must not be able to fire intent://, tel: or another app's deep link. */
private fun isAllowedLink(uri: Uri): Boolean = uri.scheme?.lowercase() in setOf("http", "https", "mailto")

/** What Chromium may leave in the app's WebView directory once remote images were loaded. */
private val WEBVIEW_TRACES = listOf(
    "HTTP Cache", "Cache", "Code Cache", "GPUCache", "Network Persistent State", "TransportSecurity",
    "Cookies", "Cookies-journal", "Local Storage", "Session Storage", "IndexedDB", "Service Worker",
)

/**
 * Deletes what a WebView of an earlier run left on disk: the hosts a sender's images came from
 * (network state, HSTS), storage, cookies. Called at start-up, before any WebView exists in this
 * process, and off the main thread; [HtmlBody] clears what it can itself when it goes, but a process
 * killed while a message was open never got there.
 */
fun clearWebViewTraces(context: Context) {
    val root = File(context.dataDir, "app_webview")
    for (dir in listOf(root, File(root, "Default"))) {
        WEBVIEW_TRACES.forEach { runCatching { File(dir, it).deleteRecursively() } }
    }
}

/** What Chrome itself sends with user-agent reduction: no model, no build, a frozen Android version. */
private const val GENERIC_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

/** Longest link opened or offered: browsers cap URLs around 2 MB, real ones stay far below this. */
private const val MAX_LINK_CHARS = 8 * 1024

/** Opens an allowed link in the browser (or the mail app for mailto:). */
private fun openLink(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (!uri.scheme.equals("mailto", ignoreCase = true)) intent.addCategory(Intent.CATEGORY_BROWSABLE)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
    } catch (_: RuntimeException) {
        // TransactionTooLargeException and the like, rethrown by the system as a RuntimeException.
        Toast.makeText(context, "This link could not be opened", Toast.LENGTH_SHORT).show()
    }
}

private const val VIEWPORT = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"

/** HTML emails rarely declare a viewport; without one the WebView renders them tiny. */
internal fun withViewport(html: String): String {
    if (html.contains("name=\"viewport\"", ignoreCase = true)) return html
    // Plain scans, no regex: this runs on the main thread, and a `<head[^>]*>` regex is quadratic
    // on a body of `<head` with no `>` after it (an ANR a sender could trigger).
    var i = html.indexOf("<head", ignoreCase = true)
    while (i >= 0) {
        val next = html.getOrNull(i + 5)
        if (next == null || next == '>' || next == '/' || next.isWhitespace()) break // not <header
        i = html.indexOf("<head", i + 5, ignoreCase = true)
    }
    val end = if (i < 0) -1 else html.indexOf('>', i)
    return if (end >= 0) html.replaceRange(end + 1, end + 1, VIEWPORT) else VIEWPORT + html
}
