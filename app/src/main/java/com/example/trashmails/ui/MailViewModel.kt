package com.example.trashmails.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.trashmails.data.CreationQuota
import com.example.trashmails.data.Inbox
import com.example.trashmails.data.InboxStore
import com.example.trashmails.data.MailContent
import com.example.trashmails.data.MailProvider
import com.example.trashmails.data.MailSummary
import com.example.trashmails.data.Provider
import com.example.trashmails.data.providers.BurnerKiwiProvider
import com.example.trashmails.data.providers.GuerrillaMailProvider
import com.example.trashmails.data.providers.InboxKittenProvider
import com.example.trashmails.data.providers.MailTmProvider
import com.example.trashmails.data.providers.MaildropProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data class InboxDetail(val inbox: Inbox) : Screen
    data class Message(val inbox: Inbox, val summary: MailSummary) : Screen
}

class MailViewModel(app: Application) : AndroidViewModel(app) {
    private val store = InboxStore(app)
    private val quota = CreationQuota(app)
    private val providers: Map<Provider, MailProvider> = listOf(
        InboxKittenProvider(), MaildropProvider(), GuerrillaMailProvider(), MailTmProvider(), BurnerKiwiProvider(),
    ).associateBy { it.provider }

    var inboxes by mutableStateOf(store.load())
        private set
    var screen: Screen by mutableStateOf(Screen.Home)
        private set

    var messages by mutableStateOf<List<MailSummary>>(emptyList())
        private set
    var content by mutableStateOf<MailContent?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Creation quota per provider (rolling 24 h), refreshed by [refreshQuota]. */
    var quotas by mutableStateOf(Provider.entries.associateWith { quota.status(it) })
        private set
    /** Known message count per inbox, for the badge on the home screen. */
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    private var pollJob: Job? = null
    private var lastFetchAt = 0L

    private fun providerFor(inbox: Inbox) = providers.getValue(inbox.provider)

    fun refreshQuota() {
        quotas = Provider.entries.associateWith { quota.status(it) }
    }

    fun createInbox(provider: Provider, name: String?) {
        provider.unavailableReason?.let { error = "${provider.label} is unavailable: $it"; return }
        val status = quota.status(provider)
        if (status.exhausted) {
            error = "${provider.label}: limit of ${status.limit} per 24 h reached"
            return
        }
        viewModelScope.launch {
            loading = true
            error = null
            runCatching { providers.getValue(provider).createInbox(name) }
                .onSuccess { inbox ->
                    if (inboxes.none { it.key == inbox.key }) {
                        inboxes = listOf(inbox) + inboxes
                        store.save(inboxes)
                        quota.record(provider)
                        refreshQuota()
                    }
                    openInbox(inbox)
                }
                .onFailure { error = it.message ?: "Could not create the inbox" }
            loading = false
        }
    }

    fun deleteInbox(inbox: Inbox) {
        inboxes = inboxes.filterNot { it.key == inbox.key }
        counts = counts - inbox.key
        store.save(inboxes)
    }

    fun openInbox(inbox: Inbox) {
        screen = Screen.InboxDetail(inbox)
        messages = emptyList()
        error = null
        startPolling(inbox)
    }

    fun openMessage(inbox: Inbox, summary: MailSummary) {
        screen = Screen.Message(inbox, summary)
        content = null
        error = null
        viewModelScope.launch {
            loading = true
            runCatching { providerFor(inbox).getMessage(inbox, summary) }
                .onSuccess { content = it }
                .onFailure { error = it.message ?: "Could not load the message" }
            loading = false
        }
    }

    fun deleteMessage(inbox: Inbox, summary: MailSummary) {
        viewModelScope.launch {
            runCatching { providerFor(inbox).deleteMessage(inbox, summary) }
                .onSuccess { if (it) fetch(inbox) }
                .onFailure { error = it.message }
        }
    }

    fun back() {
        when (val s = screen) {
            is Screen.Message -> { screen = Screen.InboxDetail(s.inbox); content = null; error = null }
            is Screen.InboxDetail -> { stopPolling(); screen = Screen.Home; error = null }
            Screen.Home -> Unit
        }
    }

    /** Manual refresh, throttled to once per [MANUAL_REFRESH_MIN_MS]. */
    fun refresh(inbox: Inbox) {
        val wait = MANUAL_REFRESH_MIN_MS - (System.currentTimeMillis() - lastFetchAt)
        if (wait > 0) {
            error = "Refresh available in ${(wait / 1000) + 1} s"
            return
        }
        viewModelScope.launch { fetch(inbox) }
    }

    private suspend fun fetch(inbox: Inbox) {
        lastFetchAt = System.currentTimeMillis()
        loading = true
        runCatching { providerFor(inbox).listMessages(inbox) }
            .onSuccess {
                messages = it
                counts = counts + (inbox.key to it.size)
                error = null
            }
            .onFailure { error = it.message ?: "Network error" }
        loading = false
    }

    private fun startPolling(inbox: Inbox) {
        stopPolling()
        pollJob = viewModelScope.launch {
            while (isActive) {
                fetch(inbox)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun clearError() { error = null }

    private companion object {
        const val POLL_INTERVAL_MS = 60_000L
        const val MANUAL_REFRESH_MIN_MS = 30_000L
    }
}
