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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.trashmails.data.MailContent
import com.example.trashmails.data.MailSummary
import com.example.trashmails.data.Provider
import com.example.trashmails.ui.CopyIcon
import com.example.trashmails.ui.copyToClipboard
import com.example.trashmails.ui.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    provider: Provider,
    summary: MailSummary,
    content: MailContent?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
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
            when {
                content?.html != null -> HtmlBody(content.html)
                content?.text != null -> SelectionContainer {
                    Text(
                        content.text,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator()
                        error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                        else -> Text("(empty message)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
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

/** Rough text extraction from an HTML email (for copying). */
private fun htmlToText(html: String): String =
    html.replace(Regex("""(?is)<(script|style)[^>]*>.*?</\1>"""), "")
        .replace(Regex("""(?i)<br\s*/?>|</p>|</div>|</tr>|</li>|</h[1-6]>"""), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'")
        .lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
