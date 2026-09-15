package io.github.usernamealreadytakensht.trashmails.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.usernamealreadytakensht.trashmails.data.Inbox
import io.github.usernamealreadytakensht.trashmails.data.MailSummary
import io.github.usernamealreadytakensht.trashmails.ui.CopyIcon
import io.github.usernamealreadytakensht.trashmails.ui.copyToClipboard
import io.github.usernamealreadytakensht.trashmails.ui.formatDate
import io.github.usernamealreadytakensht.trashmails.ui.rememberMessageHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    inbox: Inbox,
    messages: List<MailSummary>,
    loading: Boolean,
    loaded: Boolean,
    refreshHint: String,
    staleHint: String?,
    error: String?,
    notice: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (MailSummary) -> Unit,
    isRead: (MailSummary) -> Boolean,
    onDismissError: (String) -> Unit,
    onDismissNotice: (String) -> Unit,
) {
    val context = LocalContext.current
    val host = rememberMessageHost(error, notice, onDismissError, onDismissNotice)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(inbox.address, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(inbox.provider.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { context.copyToClipboard(inbox.address, "Copied: ${inbox.address}") }) {
                        Icon(CopyIcon, contentDescription = "Copy address")
                    }
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                },
            )
        },
        snackbarHost = { SnackbarHost(host) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "Refreshing" })
            else Spacer(Modifier.height(4.dp))
            if (staleHint != null) Text(
                staleHint,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            loaded -> "Inbox is empty.\n$refreshHint"
                            loading -> "Loading…"
                            else -> "Could not load the inbox.\n$refreshHint"
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn {
                    items(messages, key = { it.id }) { m ->
                        MessageRow(m, read = isRead(m), onClick = { onOpen(m) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** Unread messages stand out in bold. */
@Composable
private fun MessageRow(m: MailSummary, read: Boolean, onClick: () -> Unit) {
    val weight = if (read) FontWeight.Normal else FontWeight.Bold
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { stateDescription = if (read) "Read" else "Unread" }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                m.from.ifBlank { "(unknown sender)" },
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, fontWeight = weight,
            )
            Spacer(Modifier.width(8.dp))
            Text(formatDate(m.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            m.subject.ifBlank { "(no subject)" },
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium, fontWeight = weight,
        )
    }
}
