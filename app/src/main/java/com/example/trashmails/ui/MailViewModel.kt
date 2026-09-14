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
import com.example.trashmails.data.Settings
import com.example.trashmails.data.SettingsStore
import com.example.trashmails.data.providers.BurnerKiwiProvider
import com.example.trashmails.data.providers.GuerrillaMailProvider
import com.example.trashmails.data.providers.InboxKittenProvider
import com.example.trashmails.data.providers.MailTmProvider
import com.example.trashmails.data.providers.MaildropProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data class InboxDetail(val inbox: Inbox) : Screen
    data class Message(val inbox: Inbox, val summary: MailSummary) : Screen
}

/**
 * Every network call runs in a job tied to the screen that needs it (creation, the inbox
 * polling loop, one message body); leaving that screen cancels the job, so a slow reply can
 * never land on a different screen. Cancellation is never reported as an error.
 */
class MailViewModel(app: Application) : AndroidViewModel(app) {
    private val store = InboxStore(app)
    private val quota = CreationQuota(app)
    private val settingsStore = SettingsStore(app)
    private val providers: Map<Provider, MailProvider> = listOf(
        InboxKittenProvider(), MaildropProvider(), GuerrillaMailProvider(), MailTmProvider(), BurnerKiwiProvider(),
    ).associateBy { it.provider }

    var inboxes by mutableStateOf(store.load())
        private set
    var settings by mutableStateOf(settingsStore.load())
        private set
    var screen: Screen by mutableStateOf(Screen.Home)
        private set

    var messages by mutableStateOf<List<MailSummary>>(emptyList())
        private set
    var content by mutableStateOf<MailContent?>(null)
        private set
    /** An inbox is being created (the create dialog stays open meanwhile). */
    var creating by mutableStateOf(false)
        private set
    /** The open inbox is being listed. */
    var listLoading by mutableStateOf(false)
        private set
    /** The open inbox has been listed at least once: tells "empty" apart from "not loaded yet". */
    var listLoaded by mutableStateOf(false)
        private set
    /** The open message body is being fetched. */
    var messageLoading by mutableStateOf(false)
        private set
    /** Something failed; shown with an OK action. */
    var error by mutableStateOf<String?>(null)
        private set
    /** Plain information (e.g. the refresh throttle); shown briefly, not as a failure. */
    var notice by mutableStateOf<String?>(null)
        private set
    /** Creation quota per provider (rolling 24 h), refreshed by [refreshQuota]. */
    var quotas by mutableStateOf(Provider.entries.associateWith { quota.status(it) })
        private set
    /** Known message count per inbox, for the badge on the home screen. */
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /** Set between onStart and onStop of the activity: polling only runs while true. */
    private var foreground = false
    private var createJob: Job? = null
    private var pollJob: Job? = null
    private var messageJob: Job? = null
    /** Last successful listing, for the manual-refresh throttle. */
    private var lastFetchKey: String? = null
    private var lastFetchAt = 0L

    private fun providerFor(inbox: Inbox) = providers.getValue(inbox.provider)

    fun canDeleteMessages(inbox: Inbox) = providerFor(inbox).canDeleteMessages

    /** One line for the inbox empty state: how the list gets refreshed. */
    val refreshHint: String
        get() = when (val s = settings.pollIntervalSec) {
            0 -> "Tap refresh to check for mail."
            60 -> "Auto-refresh every minute."
            else -> if (s < 60) "Auto-refresh every $s s." else "Auto-refresh every ${s / 60} min."
        }

    /** The inbox the current screen belongs to, if any. */
    private fun currentInbox(): Inbox? = when (val s = screen) {
        is Screen.InboxDetail -> s.inbox
        is Screen.Message -> s.inbox
        Screen.Home, Screen.Settings -> null
    }

    /**
     * Runs [block] off the main thread (the providers parse their JSON right after the network
     * call) and returns its result, or null after storing a user-facing [error]. Cancellation
     * propagates untouched so a job cancelled by navigation leaves no trace.
     */
    private suspend inline fun <T> attempt(fallback: String, crossinline block: suspend () -> T): T? = try {
        withContext(Dispatchers.Default) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        error = e.userMessage(fallback)
        null
    }

    /** The app is visible again: resume polling the open inbox, waiting out the rest of the interval. */
    fun onForeground() {
        foreground = true
        forgetOldInboxes()
        currentInbox()?.let { startPolling(it, immediate = false) }
    }

    /** Screen off or another app in front: no request until [onForeground]. */
    fun onBackground() {
        foreground = false
        stopPolling()
    }

    fun openSettings() {
        screen = Screen.Settings
    }

    fun updateSettings(s: Settings) {
        val intervalChanged = s.pollIntervalSec != settings.pollIntervalSec
        settings = s
        settingsStore.save(s)
        if (intervalChanged) currentInbox()?.let { startPolling(it, immediate = false) }
        forgetOldInboxes()
    }

    /** Drops the addresses older than the setting allows (local list only; the open one is kept). */
    private fun forgetOldInboxes() {
        val maxAge = settings.forgetAfterMs ?: return
        val cutoff = System.currentTimeMillis() - maxAge
        val open = currentInbox()?.key
        val kept = inboxes.filter { it.createdAt >= cutoff || it.key == open }
        if (kept.size != inboxes.size) {
            inboxes = kept
            counts = counts.filterKeys { key -> kept.any { it.key == key } }
            store.save(kept)
        }
    }

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
        createJob?.cancel()
        creating = true
        error = null
        createJob = viewModelScope.launch {
            val inbox = attempt("Could not create the inbox") { providers.getValue(provider).createInbox(name) }
            creating = false
            inbox ?: return@launch
            if (inboxes.none { it.key == inbox.key }) {
                inboxes = listOf(inbox) + inboxes
                store.save(inboxes)
                quota.record(provider)
                refreshQuota()
            }
            openInbox(inbox)
        }
    }

    /** Abandons a creation in progress (the dialog was dismissed). */
    fun cancelCreate() {
        createJob?.cancel()
        createJob = null
        creating = false
    }

    fun canDeleteInbox(inbox: Inbox) = providerFor(inbox).canDeleteInbox

    /**
     * Forgets [inbox]. With [onServer], the provider deletes it there first (a mail.tm account,
     * messages included) and the address is only forgotten once that succeeded, so a failure
     * leaves it in the list to try again.
     */
    fun deleteInbox(inbox: Inbox, onServer: Boolean = false) {
        if (!onServer) { forget(inbox); return }
        viewModelScope.launch {
            val deleted = attempt("Could not delete the account on ${inbox.provider.label}") { providerFor(inbox).deleteInbox(inbox) }
            if (deleted == true) {
                forget(inbox)
                notice = "Account deleted on ${inbox.provider.label}"
            }
        }
    }

    private fun forget(inbox: Inbox) {
        inboxes = inboxes.filterNot { it.key == inbox.key }
        counts = counts - inbox.key
        store.save(inboxes)
    }

    fun openInbox(inbox: Inbox) {
        screen = Screen.InboxDetail(inbox)
        messages = emptyList()
        listLoaded = false
        error = null
        startPolling(inbox)
    }

    fun openMessage(inbox: Inbox, summary: MailSummary) {
        screen = Screen.Message(inbox, summary)
        messageJob?.cancel()
        content = null
        error = null
        messageLoading = true
        messageJob = viewModelScope.launch {
            content = attempt("Could not load the message") { providerFor(inbox).getMessage(inbox, summary) }
            messageLoading = false
        }
    }

    private fun cancelMessage() {
        messageJob?.cancel()
        messageJob = null
        messageLoading = false
        content = null
    }

    fun deleteMessage(inbox: Inbox, summary: MailSummary) {
        // Not tied to a screen: a deletion started should complete even if the user moves on.
        viewModelScope.launch {
            val deleted = attempt("Could not delete the message") { providerFor(inbox).deleteMessage(inbox, summary) }
            if (deleted == true && currentInbox()?.key == inbox.key) {
                messages = messages.filterNot { it.id == summary.id }
                counts = counts + (inbox.key to messages.size)
            }
        }
    }

    fun back() {
        error = null
        notice = null
        when (val s = screen) {
            is Screen.Message -> { cancelMessage(); screen = Screen.InboxDetail(s.inbox) }
            is Screen.InboxDetail -> { stopPolling(); screen = Screen.Home; messages = emptyList() }
            Screen.Settings -> screen = Screen.Home
            Screen.Home -> Unit
        }
    }

    /** Manual refresh, throttled to once per [MANUAL_REFRESH_MIN_MS]; restarts the polling loop. */
    fun refresh(inbox: Inbox) {
        if (lastFetchKey == inbox.key) {
            val wait = MANUAL_REFRESH_MIN_MS - (System.currentTimeMillis() - lastFetchAt)
            if (wait > 0) {
                notice = "Refresh available in ${(wait / 1000) + 1} s"
                return
            }
        }
        startPolling(inbox)
    }

    private suspend fun fetch(inbox: Inbox) {
        listLoading = true
        val list = attempt("Could not load the inbox") { providerFor(inbox).listMessages(inbox) }
        if (list != null) {
            lastFetchKey = inbox.key
            lastFetchAt = System.currentTimeMillis()
            messages = list
            counts = counts + (inbox.key to list.size)
            listLoaded = true
            error = null
        }
        listLoading = false
    }

    /**
     * Lists [inbox] then again at the interval chosen in [settings] until [stopPolling] (once only
     * in manual mode). With [immediate] false the first listing waits for the interval to elapse
     * since the last one of the same inbox.
     */
    private fun startPolling(inbox: Inbox, immediate: Boolean = true) {
        stopPolling()
        if (!foreground) return
        val interval = settings.pollIntervalMs
        pollJob = viewModelScope.launch {
            if (!immediate && lastFetchKey == inbox.key) {
                if (!settings.autoRefresh) return@launch
                val wait = lastFetchAt + interval - System.currentTimeMillis()
                if (wait > 0) delay(wait)
            }
            while (isActive) {
                fetch(inbox)
                if (!settings.autoRefresh) return@launch
                delay(interval)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        listLoading = false
    }

    /** Clears [error] if it still is [shown] (a newer error that replaced it stays). */
    fun clearError(shown: String) { if (error == shown) error = null }

    fun clearNotice(shown: String) { if (notice == shown) notice = null }

    private companion object {
        const val MANUAL_REFRESH_MIN_MS = 30_000L
    }
}
