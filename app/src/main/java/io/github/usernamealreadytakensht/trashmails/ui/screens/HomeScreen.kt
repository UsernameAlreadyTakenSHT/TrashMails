package io.github.usernamealreadytakensht.trashmails.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.sp
import io.github.usernamealreadytakensht.trashmails.data.CreateOptions
import io.github.usernamealreadytakensht.trashmails.data.CreationQuota
import io.github.usernamealreadytakensht.trashmails.data.Inbox
import io.github.usernamealreadytakensht.trashmails.data.Provider
import io.github.usernamealreadytakensht.trashmails.ui.CopyIcon
import io.github.usernamealreadytakensht.trashmails.ui.ProviderLogo
import io.github.usernamealreadytakensht.trashmails.ui.copyToClipboard
import io.github.usernamealreadytakensht.trashmails.ui.formatDuration
import io.github.usernamealreadytakensht.trashmails.ui.formatRemaining
import io.github.usernamealreadytakensht.trashmails.ui.rememberMessageHost
import io.github.usernamealreadytakensht.trashmails.ui.retentionInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    inboxes: List<Inbox>,
    unread: Map<String, Int>,
    deleting: Set<String>,
    creating: Boolean,
    createError: String?,
    error: String?,
    notice: String?,
    quotas: Map<Provider, CreationQuota.Status>,
    defaultProvider: Provider,
    onOpenCreate: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: (String) -> Unit,
    onDismissNotice: (String) -> Unit,
    onCancelCreate: () -> Unit,
    onOpen: (Inbox) -> Unit,
    onDelete: (Inbox, Boolean) -> Unit,
    canDeleteOnServer: (Inbox) -> Boolean,
    onCreate: (Provider, String?, CreateOptions) -> Unit,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var toDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val host = rememberMessageHost(error, notice, onDismissError, onDismissNotice)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TrashMails") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenCreate(); showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New address")
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
                        unread = unread[inbox.key],
                        busy = inbox.key in deleting,
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
            error = createError,
            quotas = quotas,
            defaultProvider = defaultProvider,
            onDismiss = { showDialog = false; onCancelCreate() },
            onCreate = onCreate,
        )
    }

    toDelete?.let { key ->
        val inbox = inboxes.firstOrNull { it.key == key }
        if (inbox == null) toDelete = null else RemoveInboxDialog(
            inbox = inbox,
            canDeleteOnServer = canDeleteOnServer(inbox),
            onConfirm = { onServer -> onDelete(inbox, onServer); toDelete = null },
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
    unread: Int?,
    busy: Boolean,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    // While its account is being deleted on the server the card is dimmed and inert (no second DELETE).
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (busy) 0.5f else 1f)
            .clickable(enabled = !busy, onClickLabel = "Open inbox", onClick = onClick),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                AddressLine(inbox.address)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProviderLogo(inbox.provider, 28.dp)
                    unread?.takeIf { it > 0 }?.let { n ->
                        Badge(Modifier.clearAndSetSemantics { contentDescription = "$n unread" }) { Text("$n") }
                    }
                    formatRemaining(inbox.expiresAt)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            FilledTonalIconButton(onClick = onCopy, enabled = !busy) {
                Icon(CopyIcon, contentDescription = "Copy address")
            }
            IconButton(onClick = onDelete, enabled = !busy) {
                Icon(Icons.Default.Delete, contentDescription = "Remove address")
            }
        }
    }
}

@Composable
private fun CreateInboxDialog(
    creating: Boolean,
    error: String?,
    quotas: Map<Provider, CreationQuota.Status>,
    defaultProvider: Provider,
    onDismiss: () -> Unit,
    onCreate: (Provider, String?, CreateOptions) -> Unit,
) {
    var provider by rememberSaveable { mutableStateOf(defaultProvider.takeIf { it.available } ?: Provider.INBOX_KITTEN) }
    var name by rememberSaveable { mutableStateOf("") }
    var infoFor by rememberSaveable { mutableStateOf<Provider?>(null) }
    // The provider's choices start from its defaults every time: first domain (or random), no scrambling.
    var domain by rememberSaveable(provider) { mutableStateOf(provider.domains.firstOrNull()?.takeUnless { provider.randomDomain }) }
    var scramble by rememberSaveable(provider) { mutableStateOf(false) }
    var noSubdomain by rememberSaveable(provider) { mutableStateOf(false) }
    val options = CreateOptions(
        domain = domain?.takeIf { it in provider.domains },
        scramble = scramble && provider.scrambleAvailable,
        noSubdomain = noSubdomain && provider.subdomainOptional,
    )
    val status = quotas[provider]
    val exhausted = status?.exhausted == true
    // One height whatever the provider shows under the list (helper text, options), so switching
    // providers does not make the dialog jump; the dialog clamps it on small screens.
    val contentHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.75f).toDp() }

    AlertDialog(
        onDismissRequest = onDismiss,
        // While the address is being created only the Cancel button dismisses: a stray tap outside
        // must not silently abandon (and possibly orphan) a creation in progress.
        properties = DialogProperties(dismissOnClickOutside = !creating, dismissOnBackPress = !creating),
        title = { Text("New address") },
        text = {
            Column(
                Modifier.height(contentHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PROVIDER_GROUPS.forEach { (title, providers) ->
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
                // Only while there is something to say: the dialog height is fixed, so no space is reserved.
                if (creating || error != null) CreateStatusLine(creating, error)
                // The name field carries the domain choice as its suffix; a provider that names the
                // address itself shows the domain field alone (or a disabled name field when there
                // is nothing to choose at all).
                if (provider.allowsCustomName || provider.domains.isEmpty()) OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = provider.allowsCustomName && !creating,
                    singleLine = true,
                    label = { Text("Name") },
                    suffix = { if (provider.domains.isEmpty()) Text(provider.fieldSuffix) },
                    // A suffix only shows once the field is focused or filled: the domain choice must stay in sight.
                    trailingIcon = if (provider.domains.isEmpty()) null else {
                        { DomainMenu(provider, options.domain, onDomain = { domain = it }, enabled = !creating) }
                    },
                    supportingText = {
                        Text(
                            when {
                                !provider.allowsCustomName -> "Address is generated by the service."
                                provider.nameIsPrefix -> "Optional prefix: the service adds a random suffix and picks the domain."
                                provider.fieldSuffix == "@…" -> "Optional · letters, digits, . _ - · ${provider.domainHint}"
                                else -> "Optional · letters, digits, . _ -"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                else DomainField(provider, options.domain, onDomain = { domain = it }, enabled = !creating)
                if (provider.scrambleAvailable) OptionCheckbox(
                    title = "Scrambled address",
                    description = "A random alias instead of the name, which anyone could guess.",
                    checked = options.scramble, onChange = { scramble = it }, enabled = !creating,
                )
                if (provider.subdomainOptional) OptionCheckbox(
                    title = "No subdomain",
                    description = "name@example.com rather than name@x7.example.com.",
                    checked = options.noSubdomain, onChange = { noSubdomain = it }, enabled = !creating,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !creating && !exhausted,
                onClick = { onCreate(provider, name.takeIf { provider.allowsCustomName && it.isNotBlank() }, options) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    infoFor?.let { p -> ProviderInfoDialog(p, onDismiss = { infoFor = null }) }
}

/** The domain chosen, at the end of the name field ("@domain ▾"), opening the list of choices on tap. */
@Composable
private fun DomainMenu(provider: Provider, domain: String?, onDomain: (String?) -> Unit, enabled: Boolean) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clickable(enabled = enabled, onClickLabel = "Choose the domain", role = Role.DropdownList) { menuOpen = true }
                .semantics { contentDescription = "Domain ${domain ?: "random"}" }
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Capped so a long domain cannot squeeze the name input out of the field (the menu shows it whole).
            Text(
                domain?.let { "@$it" } ?: "Random",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DomainChoices(provider, menuOpen, onDismiss = { menuOpen = false }, onDomain = onDomain)
    }
}

/** The domain as a field of its own, for a provider that picks the name itself. */
@Composable
private fun DomainField(provider: Provider, domain: String?, onDomain: (String?) -> Unit, enabled: Boolean) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = domain?.let { "@$it" } ?: "Random",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text("Domain") },
            supportingText = { Text("The name is the service's own. Random picks one of ${provider.label}'s permanent domains.") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            // The read-only field is only the look; the layer on top is the one control screen readers see.
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        )
        Box(
            Modifier
                .matchParentSize()
                .semantics { contentDescription = "Domain ${domain ?: "random"}" }
                .clickable(enabled = enabled, onClickLabel = "Choose the domain", role = Role.DropdownList) { menuOpen = true },
        )
        DomainChoices(provider, menuOpen, onDismiss = { menuOpen = false }, onDomain = onDomain)
    }
}

@Composable
private fun DomainChoices(provider: Provider, open: Boolean, onDismiss: () -> Unit, onDomain: (String?) -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        if (provider.randomDomain) DropdownMenuItem(text = { Text("Random") }, onClick = { onDomain(null); onDismiss() })
        provider.domains.forEach { d ->
            DropdownMenuItem(text = { Text("@$d") }, onClick = { onDomain(d); onDismiss() })
        }
    }
}

/** A checkbox with its title and one line of explanation; the whole row toggles it. */
@Composable
private fun OptionCheckbox(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Providers grouped under a section title; inside a group the unavailable ones sink to the bottom. */
private val PROVIDER_GROUPS: List<Pair<String, List<Provider>>> = Provider.entries.sortedBy { !it.available }.let { all ->
    listOf(
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

/** [onConfirm] receives whether the inbox should also be deleted on the server (when the provider can). */
@Composable
private fun RemoveInboxDialog(
    inbox: Inbox,
    canDeleteOnServer: Boolean,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var onServer by rememberSaveable { mutableStateOf(canDeleteOnServer) }
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
                if (canDeleteOnServer) Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = onServer, role = Role.Checkbox, onValueChange = { onServer = it })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = onServer, onCheckedChange = null)
                    Text(
                        "Also delete the account on ${inbox.provider.label}, messages included",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(onServer) }) { Text(if (onServer) "Delete" else "Remove") } },
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
                when {
                    unavailable -> "Unavailable"
                    exhausted -> "Limit reached for today"
                    else -> provider.domainHint
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (unavailable || exhausted) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onInfo) {
            Icon(Icons.Outlined.Info, contentDescription = "About ${provider.label}")
        }
        RadioButton(selected = selected, onClick = null, enabled = !unavailable, modifier = dim.size(36.dp))
    }
}

/** Creation in progress, or why it failed (shown only then; the dialog height is fixed anyway). */
@Composable
private fun CreateStatusLine(creating: Boolean, error: String?) {
    Row(
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
                color = MaterialTheme.colorScheme.error, // no line cap: a mail.tm refusal can be long and the column scrolls
            )
        }
    }
}

/** Remaining quota, or the time to the next slot once it is used up. */
@Composable
private fun QuotaLine(status: CreationQuota.Status?) {
    status ?: return
    val (text, color) = when {
        status.exhausted -> {
            val wait = (status.nextSlotAt ?: 0L) - System.currentTimeMillis()
            "Limit of ${status.limit} per 24 h reached · next slot in ${formatDuration(wait)}" to
                MaterialTheme.colorScheme.error
        }
        else -> "${status.remaining} of ${status.limit} left (rolling 24 h)" to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}
