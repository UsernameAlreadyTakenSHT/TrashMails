package com.example.trashmails.ui.screens

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.trashmails.data.MailContent
import com.example.trashmails.data.MailSummary
import com.example.trashmails.data.Provider
import com.example.trashmails.data.Settings
import com.example.trashmails.ui.CopyIcon
import com.example.trashmails.ui.copyToClipboard
import com.example.trashmails.ui.formatDate
import com.example.trashmails.ui.htmlToText

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
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val hasHtml = content?.html != null

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
                    if (hasHtml) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (showHtml) "View as plain text" else "View as HTML") },
                                onClick = { showHtml = !showHtml; menuOpen = false },
                            )
                        }
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
                showHtml && content.html != null -> HtmlBody(content.html)
                !text.isNullOrBlank() -> PlainBody(text)
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("(empty message)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
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

@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlBody(html: String) {
    val dark = isSystemInDarkTheme()
    val background = MaterialTheme.colorScheme.surface.toArgb()
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
                // A tapped link leaves the app: nothing ever navigates inside the mail view.
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        openLink(ctx, request.url)
                        return true
                    }
                }
                settings.javaScriptEnabled = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
            }
        },
        update = { it.loadDataWithBaseURL(null, withViewport(html), "text/html", "utf-8", null) },
    )
}

/**
 * Opens a link from an email in the browser (or the mail app for mailto:). Any other scheme is
 * dropped: the sender must not be able to fire intent://, tel: or another app's deep link.
 */
private fun openLink(context: Context, uri: Uri) {
    val web = when (uri.scheme?.lowercase()) {
        "http", "https" -> true
        "mailto" -> false
        else -> return
    }
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (web) intent.addCategory(Intent.CATEGORY_BROWSABLE)
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
