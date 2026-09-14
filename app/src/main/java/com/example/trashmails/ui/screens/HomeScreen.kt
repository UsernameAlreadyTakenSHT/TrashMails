package com.example.trashmails.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.trashmails.data.CreationQuota
import com.example.trashmails.data.Inbox
import com.example.trashmails.data.Provider
import com.example.trashmails.ui.CopyIcon
import com.example.trashmails.ui.ProviderLogo
import com.example.trashmails.ui.copyToClipboard
import com.example.trashmails.ui.formatDuration
import com.example.trashmails.ui.formatRemaining

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    inboxes: List<Inbox>,
    counts: Map<String, Int>,
    creating: Boolean,
    error: String?,
    quotas: Map<Provider, CreationQuota.Status>,
    onOpenCreate: () -> Unit,
    onDismissError: () -> Unit,
    onOpen: (Inbox) -> Unit,
    onDelete: (Inbox) -> Unit,
    onCreate: (Provider, String?) -> Unit,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var toDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("TrashMails") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenCreate(); showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New address")
            }
        },
        snackbarHost = {
            error?.let {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = onDismissError) { Text("OK") } },
                ) { Text(it) }
            }
        },
    ) { padding ->
        if (inboxes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No address yet.\nTap + to create one.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp,
                    start = 16.dp, end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(inboxes, key = { it.key }) { inbox ->
                    InboxCard(
                        inbox = inbox,
                        count = counts[inbox.key],
                        onClick = { onOpen(inbox) },
                        onCopy = { context.copyToClipboard(inbox.address) },
                        onDelete = { toDelete = inbox.key },
                    )
                }
            }
        }
    }

    if (showDialog) {
        CreateInboxDialog(
            creating = creating,
            quotas = quotas,
            onDismiss = { showDialog = false },
            onCreate = { p, n -> showDialog = false; onCreate(p, n) },
        )
    }

    toDelete?.let { key ->
        val inbox = inboxes.firstOrNull { it.key == key }
        if (inbox == null) toDelete = null else RemoveInboxDialog(
            inbox = inbox,
            onConfirm = { onDelete(inbox); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun InboxCard(
    inbox: Inbox,
    count: Int?,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(inbox.address, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProviderLogo(inbox.provider, 28.dp)
                    count?.let { Badge { Text("$it") } }
                    formatRemaining(inbox.expiresAt)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            FilledTonalIconButton(onClick = onCopy) {
                Icon(CopyIcon, contentDescription = "Copy address")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        }
    }
}

@Composable
private fun CreateInboxDialog(
    creating: Boolean,
    quotas: Map<Provider, CreationQuota.Status>,
    onDismiss: () -> Unit,
    onCreate: (Provider, String?) -> Unit,
) {
    var provider by rememberSaveable { mutableStateOf(Provider.INBOX_KITTEN) }
    var name by rememberSaveable { mutableStateOf("") }
    val status = quotas[provider]
    val exhausted = status?.exhausted == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New address") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Provider.entries.forEach { p ->
                        ProviderRow(
                            p,
                            selected = provider == p,
                            exhausted = quotas[p]?.exhausted == true,
                            onClick = { provider = p },
                        )
                    }
                }
                QuotaLine(status)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = provider.allowsCustomName,
                    singleLine = true,
                    label = { Text("Name (optional)") },
                    placeholder = { Text("empty = random") },
                    suffix = { Text(provider.domainHint) },
                    supportingText = {
                        Text(
                            if (provider.allowsCustomName) "Letters, digits, . _ -"
                            else "Address is generated by the service."
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider()
                // All three blocks are stacked (only the selected one is visible), so the
                // dialog height is always that of the tallest block and never jumps.
                Box {
                    Provider.entries.forEach { p ->
                        Column(
                            Modifier.alpha(if (p == provider) 1f else 0f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) { RetentionDetails(p) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !creating && !exhausted,
                onClick = { onCreate(provider, name.takeIf { provider.allowsCustomName && it.isNotBlank() }) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** What happens to emails and the inbox on the provider's server. */
private data class RetentionInfo(val mails: List<String>, val account: List<String>, val warning: String?)

private fun retentionInfo(provider: Provider): RetentionInfo = when (provider) {
    Provider.INBOX_KITTEN -> RetentionInfo(
        mails = listOf(
            "Kept on the server for about 3 days, then deleted automatically.",
            "No manual deletion: the API does not allow it.",
        ),
        account = listOf(
            "There is no account: the inbox only exists by its name.",
            "Removing the address only forgets it in this app.",
        ),
        warning = "Public inbox: anyone who knows the name can read the emails while they exist.",
    )
    Provider.BURNER_KIWI -> RetentionInfo(
        mails = listOf(
            "Expire 24 h after the inbox was created, then purged within 48 h.",
            "No manual deletion before expiry.",
        ),
        account = listOf(
            "The inbox expires by itself after 24 h.",
            "The access token is erased from this app: nobody will be able to read it anymore.",
        ),
        warning = null,
    )
    Provider.MAILDROP -> RetentionInfo(
        mails = listOf(
            "Kept on the server: 10 messages max (oldest are overwritten).",
            "The inbox is emptied after 24 h without a new email.",
            "This app never deletes anything on the server.",
        ),
        account = listOf(
            "There is no account: the inbox only exists by its name.",
            "Removing the address only forgets it in this app.",
        ),
        warning = "Public inbox: anyone who knows the name can read the emails while they exist.",
    )
}

@Composable
private fun RemoveInboxDialog(inbox: Inbox, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove this address?") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(inbox.address, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "The address will be removed from this app. Here is what happens on ${inbox.provider.label}'s side:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                RetentionDetails(inbox.provider)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InfoSection(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        lines.forEach { line ->
            Row {
                Text("•  ", style = MaterialTheme.typography.bodySmall)
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** "Emails / Inbox" block plus warning, shared by the create and remove dialogs. */
@Composable
private fun RetentionDetails(provider: Provider) {
    val info = retentionInfo(provider)
    InfoSection("Emails", info.mails)
    InfoSection("Inbox / account", info.account)
    info.warning?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ProviderRow(provider: Provider, selected: Boolean, exhausted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (exhausted) 0.5f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProviderLogo(provider, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(provider.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                provider.domainHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = null)
    }
}

/** One always-present line so the dialog height never changes: remaining quota or time to next slot. */
@Composable
private fun QuotaLine(status: CreationQuota.Status?) {
    status ?: return
    val (text, color) = when {
        status.exhausted -> {
            val wait = (status.nextSlotAt ?: 0L) - System.currentTimeMillis()
            "Limit of ${status.limit} per 24 h reached · next slot in ${formatDuration(wait)}" to
                MaterialTheme.colorScheme.error
        }
        else -> "${status.remaining} of ${status.limit} left in the next 24 h" to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}
