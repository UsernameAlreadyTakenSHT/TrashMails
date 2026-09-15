package io.github.usernamealreadytakensht.trashmails.ui.screens

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import io.github.usernamealreadytakensht.trashmails.ui.htmlToText
import java.io.ByteArrayInputStream

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
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    var showHtml by rememberSaveable(summary.id) { mutableStateOf(settings.renderHtml) }
    var loadImages by rememberSaveable(summary.id) { mutableStateOf(settings.loadImages) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    /** A tapped link waiting for the user's go-ahead (as a string: Uri is not saveable). */
    var pendingLink by rememberSaveable { mutableStateOf<String?>(null) }
    val hasHtml = content?.html != null
    val onLink: (Uri) -> Unit = { uri ->
        when {
            !isAllowedLink(uri) -> Toast.makeText(context, "Links of this kind cannot be opened", Toast.LENGTH_SHORT).show()
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
                    val body = content?.text ?: content?.html?.let(::htmlToText)
                    if (body != null) IconButton(onClick = { context.copyToClipboard(body, "Message text copied", sensitive = true) }) {
                        Icon(CopyIcon, contentDescription = "Copy content")
                    }
                    if (onDelete != null) IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                    // Always present so the buttons keep their place while the body loads.
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (showHtml) "View as plain text" else "View as HTML") },
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
                Text(summary.from, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatDate(summary.date)} · ${provider.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            val text = content?.text ?: content?.html?.let(::htmlToText)
            when {
                content == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator()
                        error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                        else -> Text("(empty message)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                showHtml && content.html != null -> {
                    if (!loadImages) BlockedBanner(onLoad = { loadImages = true })
                    HtmlBody(content.html, loadImages, onLink)
                }
                !text.isNullOrBlank() -> PlainBody(text)
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("(empty message)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    pendingLink?.let { link ->
        LinkDialog(
            url = link,
            onOpen = { pendingLink = null; openLink(context, Uri.parse(link)) },
            onCopy = { pendingLink = null; context.copyToClipboard(link, "Link copied") },
            onDismiss = { pendingLink = null },
        )
    }
}

@Composable
private fun PlainBody(text: String) {
    SelectionContainer {
        Text(
            text,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Tells that the sender's images, styles and fonts were not fetched, with a way to fetch them. */
@Composable
private fun BlockedBanner(onLoad: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Remote images not loaded",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onLoad) { Text("Load") }
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
    val site = if (uri.scheme.equals("mailto", ignoreCase = true)) uri.schemeSpecificPart else uri.host ?: url
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (uri.scheme.equals("mailto", ignoreCase = true)) "Write to this address?" else "Open this site?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(site, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SelectionContainer {
                    Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text("Open") } },
        dismissButton = {
            Row {
                TextButton(onClick = onCopy) { Text("Copy link") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** http(s) and mailto only: the sender must not be able to fire intent://, tel: or another app's deep link. */
private fun isAllowedLink(uri: Uri): Boolean = uri.scheme?.lowercase() in setOf("http", "https", "mailto")

/** Opens an allowed link in the browser (or the mail app for mailto:). */
private fun openLink(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (!uri.scheme.equals("mailto", ignoreCase = true)) intent.addCategory(Intent.CATEGORY_BROWSABLE)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
    }
}

private const val VIEWPORT = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"

/** HTML emails rarely declare a viewport; without one the WebView renders them tiny. */
private fun withViewport(html: String): String {
    if (html.contains("name=\"viewport\"", ignoreCase = true)) return html
    val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
    return if (head != null) html.replaceRange(head.range.last + 1, head.range.last + 1, VIEWPORT)
    else VIEWPORT + html
}
