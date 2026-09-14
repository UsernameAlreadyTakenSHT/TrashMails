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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trashmails.data.CreationQuota
import com.example.trashmails.data.Inbox
import com.example.trashmails.data.Provider
import com.example.trashmails.ui.CopyIcon
import com.example.trashmails.ui.ProviderLogo
import com.example.trashmails.ui.copyToClipboard
import com.example.trashmails.ui.formatDuration
import com.example.trashmails.ui.formatRemaining
import com.example.trashmails.ui.rememberMessageHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    inboxes: List<Inbox>,
    counts: Map<String, Int>,
    creating: Boolean,
    error: String?,
    notice: String?,
    quotas: Map<Provider, CreationQuota.Status>,
    onOpenCreate: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: (String) -> Unit,
    onDismissNotice: (String) -> Unit,
    onCancelCreate: () -> Unit,
    onOpen: (Inbox) -> Unit,
    onDelete: (Inbox) -> Unit,
    onCreate: (Provider, String?) -> Unit,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var toDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // While the create dialog is open its errors are shown inside it.
    val host = rememberMessageHost(error?.takeIf { !showDialog }, notice, onDismissError, onDismissNotice)

    Scaffold(
        topBar = { TopAppBar(title = { Text("TrashMails") }) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SmallFloatingActionButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
                FloatingActionButton(onClick = { onOpenCreate(); showDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New address")
                }
            }
        },
        snackbarHost = { SnackbarHost(host) },
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
                        onCopy = { context.copyToClipboard(inbox.address, "Copied: ${inbox.address}") },
                        onDelete = { toDelete = inbox.key },
                    )
                }
            }
        }
    }

    if (showDialog) {
        CreateInboxDialog(
            creating = creating,
            error = error,
            quotas = quotas,
            onDismiss = { showDialog = false; onCancelCreate(); error?.let(onDismissError) },
            onCreate = onCreate,
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

/** The address on one line, shrinking the font when it would not fit. */
@Composable
private fun AddressLine(address: String) {
    Text(
        address,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 16.sp, stepSize = 0.5.sp),
    )
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
                AddressLine(inbox.address)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProviderLogo(inbox.provider, 28.dp)
                    count?.takeIf { it > 0 }?.let { Badge { Text("$it") } }
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
    error: String?,
    quotas: Map<Provider, CreationQuota.Status>,
    onDismiss: () -> Unit,
    onCreate: (Provider, String?) -> Unit,
) {
    var provider by rememberSaveable { mutableStateOf(Provider.INBOX_KITTEN) }
    var name by rememberSaveable { mutableStateOf("") }
    var infoFor by rememberSaveable { mutableStateOf<Provider?>(null) }
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
                    providerGroups().forEach { (title, providers) ->
                        Text(
                            title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
                        )
                        providers.forEach { p ->
                            ProviderRow(
                                p,
                                selected = provider == p,
                                exhausted = quotas[p]?.exhausted == true,
                                onClick = { if (p.available) provider = p },
                                onInfo = { infoFor = p },
                            )
                        }
                    }
                }
                QuotaLine(status)
                CreateStatusLine(creating, error)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = provider.allowsCustomName && !creating,
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

    infoFor?.let { p -> ProviderInfoDialog(p, onDismiss = { infoFor = null }) }
}

/** Providers grouped under a section title; inside a group the unavailable ones sink to the bottom. */
private fun providerGroups(): List<Pair<String, List<Provider>>> {
    val all = Provider.entries.sortedBy { !it.available }
    return listOf(
        "Open source" to all.filter { it.openSource },
        "Closed source" to all.filter { !it.openSource },
    ).filter { it.second.isNotEmpty() }
}

/** The (i) popup: retention rules, source code link, and the outage note when there is one. */
@Composable
private fun ProviderInfoDialog(provider: Provider, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(provider.label) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                provider.unavailableReason?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                RetentionDetails(provider)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Links", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    LinkLine("Website", provider.siteUrl)
                    LinkLine("Privacy policy", provider.privacyUrl, missing = "none published")
                    LinkLine("Source code", provider.sourceUrl, missing = "not published (closed source)")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** One line "Label: url", the url tappable (opens the browser); [missing] is shown when there is no url. */
@Composable
private fun LinkLine(label: String, url: String?, missing: String = "") {
    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    Text(
        buildAnnotatedString {
            append("$label: ")
            if (url == null) append(missing)
            else withLink(LinkAnnotation.Url(url, linkStyle)) { append(url.removePrefix("https://").removePrefix("www.").trimEnd('/')) }
        },
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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
    Provider.GUERRILLA_MAIL -> RetentionInfo(
        mails = listOf(
            "Kept on the server for 1 hour, then deleted automatically.",
            "Can be deleted manually from this app.",
        ),
        account = listOf(
            "There is no account: the inbox only exists by its name.",
            "Removing the address only forgets it in this app.",
        ),
        warning = "Public inbox: anyone who knows the name can read the emails while they exist.",
    )
    Provider.MAIL_TM -> RetentionInfo(
        mails = listOf(
            "Kept on the server for 7 days, then deleted automatically.",
            "Can be deleted manually from this app.",
        ),
        account = listOf(
            "A real account (address + password) that mail.tm keeps until it is deleted.",
            "Removing the address only forgets it in this app; the password is lost with it, so nobody can open the inbox anymore.",
        ),
        warning = null,
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
            "Can be deleted manually from this app.",
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
                AddressLine(inbox.address)
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
private fun ProviderRow(
    provider: Provider,
    selected: Boolean,
    exhausted: Boolean,
    onClick: () -> Unit,
    onInfo: () -> Unit,
) {
    val unavailable = !provider.available
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .selectable(selected = selected, enabled = !unavailable, onClick = onClick, role = Role.RadioButton)
            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Only the identity dims when the row cannot be picked: the (i) stays fully usable.
        val dim = Modifier.alpha(if (exhausted || unavailable) 0.5f else 1f)
        ProviderLogo(provider, 44.dp, dim, described = false)
        Column(Modifier.weight(1f).then(dim)) {
            Text(
                provider.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (unavailable) "Unavailable" else provider.domainHint,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (unavailable) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onInfo) {
            Icon(Icons.Outlined.Info, contentDescription = "About ${provider.label}")
        }
        RadioButton(selected = selected, onClick = null, enabled = !unavailable, modifier = dim.size(36.dp))
    }
}

/** Always present (min height) so the dialog does not jump: creation in progress, or why it failed. */
@Composable
private fun CreateStatusLine(creating: Boolean, error: String?) {
    Row(
        Modifier.heightIn(min = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            creating -> {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("Creating the address…", style = MaterialTheme.typography.bodySmall)
            }
            error != null -> Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
