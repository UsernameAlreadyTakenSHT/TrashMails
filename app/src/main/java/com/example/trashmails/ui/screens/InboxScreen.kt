package com.example.trashmails.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.trashmails.data.Inbox
import com.example.trashmails.data.MailSummary
import com.example.trashmails.ui.CopyIcon
import com.example.trashmails.ui.copyToClipboard
import com.example.trashmails.ui.formatDate
import com.example.trashmails.ui.rememberMessageHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    inbox: Inbox,
    messages: List<MailSummary>,
    loading: Boolean,
    loaded: Boolean,
    error: String?,
    notice: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (MailSummary) -> Unit,
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
                    IconButton(onClick = { context.copyToClipboard(inbox.address) }) {
                        Icon(CopyIcon, contentDescription = "Copy address")
                    }
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                },
            )
        },
        snackbarHost = { SnackbarHost(host) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            loaded -> "Inbox is empty.\nAuto-refresh every minute."
                            loading -> "Loading…"
                            else -> "Could not load the inbox.\nAuto-refresh every minute."
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn {
                    items(messages, key = { it.id }) { m ->
                        MessageRow(m, onClick = { onOpen(m) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageRow(m: MailSummary, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                m.from.ifBlank { "(unknown sender)" },
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(formatDate(m.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            m.subject.ifBlank { "(no subject)" },
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
