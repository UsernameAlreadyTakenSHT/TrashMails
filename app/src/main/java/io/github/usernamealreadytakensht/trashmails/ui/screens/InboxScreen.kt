package io.github.usernamealreadytakensht.trashmails.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.usernamealreadytakensht.trashmails.data.Inbox
import io.github.usernamealreadytakensht.trashmails.data.MailSummary
import io.github.usernamealreadytakensht.trashmails.ui.AlternateEmailIcon
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
    var extending by rememberSaveable { mutableStateOf(false) }
    if (extending) ExtendedAddressDialog(inbox, onDismiss = { extending = false })
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
                    if (inbox.provider.extendedAddresses) IconButton(onClick = { extending = true }) {
                        Icon(AlternateEmailIcon, contentDescription = "Extended address")
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

/**
 * A variant of the address for one site: DropMail delivers `local-tag@sub.domain` to `local@domain`
 * (tag and sub: letters), so a site that refuses the plain address, or one the user would rather
 * not give the real address to, gets an address of its own. Nothing is stored: the tag is only a
 * label the sender sees.
 */
@Composable
private fun ExtendedAddressDialog(inbox: Inbox, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val local = inbox.address.substringBefore('@')
    val domain = inbox.address.substringAfter('@')
    var tag by rememberSaveable { mutableStateOf(randomLetters(6)) }
    val sub = rememberSaveable { randomLetters(4) }
    val extended = "$local-$tag@$sub.$domain"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extended address") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mail sent to this variant lands in the same inbox and shows which variant it came to. Handy for a site that refuses the plain address, or to see who passed it on.")
                OutlinedTextField(
                    value = tag,
                    // Letters only, as the service allows; the random subdomain part is not editable.
                    onValueChange = { tag = it.lowercase().filter { c -> c in 'a'..'z' }.take(20) },
                    singleLine = true,
                    label = { Text("Tag") },
                    supportingText = { Text("Letters only") },
                    isError = tag.isBlank(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (tag.isNotBlank()) Text(extended, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            TextButton(enabled = tag.isNotBlank(), onClick = { context.copyToClipboard(extended, "Copied: $extended"); onDismiss() }) { Text("Copy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun randomLetters(length: Int): String = (1..length).map { ('a'..'z').random() }.joinToString("")
