package io.github.usernamealreadytakensht.trashmails.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.usernamealreadytakensht.trashmails.data.CreationQuota
import io.github.usernamealreadytakensht.trashmails.data.Inbox
import io.github.usernamealreadytakensht.trashmails.data.InboxStore
import io.github.usernamealreadytakensht.trashmails.data.MailContent
import io.github.usernamealreadytakensht.trashmails.data.MailProvider
import io.github.usernamealreadytakensht.trashmails.data.MailSummary
import io.github.usernamealreadytakensht.trashmails.data.Provider
import io.github.usernamealreadytakensht.trashmails.data.ReadStore
import io.github.usernamealreadytakensht.trashmails.data.Settings
import io.github.usernamealreadytakensht.trashmails.data.SettingsStore
import io.github.usernamealreadytakensht.trashmails.data.providers.BurnerKiwiProvider
import io.github.usernamealreadytakensht.trashmails.data.providers.GuerrillaMailProvider
import io.github.usernamealreadytakensht.trashmails.data.providers.InboxKittenProvider
import io.github.usernamealreadytakensht.trashmails.data.providers.MailTmProvider
import io.github.usernamealreadytakensht.trashmails.data.providers.MaildropProvider
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
    private val readStore = ReadStore(app)
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
    /** Unread messages per inbox at its last listing, for the badge on the home screen. */
    var unread by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    /** Ids of the messages opened at least once, per inbox key. */
    var read by mutableStateOf(readStore.load())
        private set

    /** Set between onStart and onStop of the activity: polling only runs while true. */
    private var foreground = false
    private var createJob: Job? = null
    private var pollJob: Job? = null
    private var messageJob: Job? = null
    /** Last successful listing, for the manual-refresh throttle. */
    private var lastFetchKey: String? = null
    private var lastFetchAt = 0L
    /** The error the polling loop itself last reported, so a recovery only clears that one. */
    private var lastFetchError: String? = null

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

    /** How many addresses "forget after [hours] hours" would drop right now (the open one never is). */
    fun countOlderThan(hours: Int): Int = if (hours <= 0) 0 else inboxes.count { isOld(it, hours * 3_600_000L) }

    private fun isOld(inbox: Inbox, maxAgeMs: Long) =
        inbox.createdAt < System.currentTimeMillis() - maxAgeMs && inbox.key != currentInbox()?.key

    /** Drops the addresses older than the setting allows (local list only; the open one is kept). */
    private fun forgetOldInboxes() {
        val maxAge = settings.forgetAfterMs ?: return
        val kept = inboxes.filterNot { isOld(it, maxAge) }
        if (kept.size != inboxes.size) {
            inboxes = kept
            unread = unread.filterKeys { key -> kept.any { it.key == key } }
            read = read.filterKeys { key -> kept.any { it.key == key } }
            readStore.save(read)
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
            if (settings.lastProvider != provider) updateSettings(settings.copy(lastProvider = provider))
            if (settings.copyOnCreate) getApplication<Application>().copyToClipboard(inbox.address, "Copied: ${inbox.address}")
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
        unread = unread - inbox.key
        read = read - inbox.key
        readStore.save(read)
        store.save(inboxes)
    }

    fun openInbox(inbox: Inbox) {
        screen = Screen.InboxDetail(inbox)
        messages = emptyList()
        listLoaded = false
        error = null
        startPolling(inbox)
    }

    fun isRead(inbox: Inbox, summary: MailSummary) = read[inbox.key]?.contains(summary.id) == true

    private fun countUnread(inbox: Inbox, list: List<MailSummary>) = list.count { !isRead(inbox, it) }

    fun openMessage(inbox: Inbox, summary: MailSummary) {
        screen = Screen.Message(inbox, summary)
        if (!isRead(inbox, summary)) {
            read = read + (inbox.key to read[inbox.key].orEmpty() + summary.id)
            readStore.save(read)
            unread = unread + (inbox.key to countUnread(inbox, messages))
        }
        messageJob?.cancel()
        content = null
        error = null
        messageLoading = true
        messageJob = viewModelScope.launch {
            content = attempt("Could not load the message") {
                // The plain text is derived here, once and off the main thread, when the provider has none.
                val c = providerFor(inbox).getMessage(inbox, summary)
                if (c.text == null && c.html != null) c.copy(text = htmlToText(c.html)) else c
            }
            messageLoading = false
        }
    }

    private fun cancelMessage() {
        messageJob?.cancel()
        messageJob = null
        messageLoading = false
        content = null
    }

    /** Deletes [summary] on the server; if it is the open message, the screen goes back to the list at once. */
    fun deleteMessage(inbox: Inbox, summary: MailSummary) {
        if ((screen as? Screen.Message)?.summary?.id == summary.id) back()
        // Not tied to a screen: a deletion started should complete even if the user moves on.
        viewModelScope.launch {
            val deleted = attempt("Could not delete the message") { providerFor(inbox).deleteMessage(inbox, summary) }
            when {
                deleted == null -> Unit
                !deleted -> error = "${inbox.provider.label} did not delete the message"
                else -> {
                    if (currentInbox()?.key == inbox.key) {
                        messages = messages.filterNot { it.id == summary.id }
                        unread = unread + (inbox.key to countUnread(inbox, messages))
                    }
                    notice = "Message deleted"
                }
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
        val before = error
        val list = attempt("Could not load the inbox") { providerFor(inbox).listMessages(inbox) }
        if (list != null) {
            lastFetchKey = inbox.key
            lastFetchAt = System.currentTimeMillis()
            messages = list
            unread = unread + (inbox.key to countUnread(inbox, list))
            listLoaded = true
            // A listing that works again clears the listing error it had set — not somebody else's
            // (a message that failed to load keeps saying why while polling goes on behind it).
            if (error != null && error == lastFetchError) error = null
            lastFetchError = null
        } else if (error != before) {
            lastFetchError = error
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
