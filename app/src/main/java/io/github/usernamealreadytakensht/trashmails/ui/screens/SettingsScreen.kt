package io.github.usernamealreadytakensht.trashmails.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.usernamealreadytakensht.trashmails.data.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: (Settings) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle("Reading emails")
            SettingRow(
                title = "Render HTML emails",
                description = "Off: every email is shown as plain text, so nothing the sender wrote is " +
                    "rendered or fetched. A single message can still be viewed as HTML from its menu.",
                checked = settings.renderHtml,
                onCheckedChange = { onChange(settings.copy(renderHtml = it)) },
            )
            HorizontalDivider()
            SettingRow(
                title = "Load remote images",
                description = "Off: images, styles and fonts hosted on the sender's servers are not fetched " +
                    "when an email is rendered as HTML, so the sender cannot tell it was opened. " +
                    "They can be loaded for a single message from its menu.",
                checked = settings.loadImages,
                onCheckedChange = { onChange(settings.copy(loadImages = it)) },
            )
            HorizontalDivider()
            SettingRow(
                title = "Confirm before opening links",
                description = "Shows the link's address and its site, and asks before leaving the app.",
                checked = settings.confirmLinks,
                onCheckedChange = { onChange(settings.copy(confirmLinks = it)) },
            )

            SectionTitle("Inboxes")
            ChoiceRow(
                title = "Auto-refresh",
                description = "How often the open inbox is listed again. Manual: only when you tap refresh " +
                    "(at most once per 30 s).",
                choices = Settings.POLL_INTERVALS,
                selected = settings.pollIntervalSec,
                onSelect = { onChange(settings.copy(pollIntervalSec = it)) },
            )
            HorizontalDivider()
            ChoiceRow(
                title = "Forget old addresses",
                description = "Addresses older than this are dropped from the list when the app opens. Nothing is " +
                    "deleted on the provider's side; most inboxes have expired there long before.",
                choices = Settings.FORGET_AFTER,
                selected = settings.forgetAfterHours,
                onSelect = { onChange(settings.copy(forgetAfterHours = it)) },
            )
            HorizontalDivider()
            SettingRow(
                title = "Copy new addresses",
                description = "A freshly created address goes straight to the clipboard.",
                checked = settings.copyOnCreate,
                onCheckedChange = { onChange(settings.copy(copyOnCreate = it)) },
            )

            SectionTitle("Privacy")
            SettingRow(
                title = "Block screenshots",
                description = "The app cannot be captured or screen-recorded, and shows blank in the recent apps: " +
                    "verification codes stay on the screen only.",
                checked = settings.blockScreenshots,
                onCheckedChange = { onChange(settings.copy(blockScreenshots = it)) },
            )

            SectionTitle("Appearance")
            ChoiceRow(
                title = "Theme",
                description = "",
                choices = Settings.THEMES,
                selected = settings.theme,
                onSelect = { onChange(settings.copy(theme = it)) },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** A switch with its title and explanation; the whole row toggles it. */
@Composable
private fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A setting with a few values: the row shows the current one, tapping it opens a radio list. */
@Composable
private fun <T> ChoiceRow(
    title: String,
    description: String,
    choices: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            choices.firstOrNull { it.first == selected }?.second ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (description.isNotEmpty()) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value); open = false })
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
    )
}
