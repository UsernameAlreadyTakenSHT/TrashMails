package io.github.usernamealreadytakensht.trashmails.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
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
import io.github.usernamealreadytakensht.trashmails.data.providers.allProviders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
class MailViewModel(app: Application, private val savedState: SavedStateHandle) : AndroidViewModel(app) {
    private val store = InboxStore(app)
    private val quota = CreationQuota(app)
    private val settingsStore = SettingsStore(app)
    private val readStore = ReadStore(app)
    private val providers: Map<Provider, MailProvider> = allProviders()

    var inboxes by mutableStateOf(store.load())
        private set
    var settings by mutableStateOf(settingsStore.load())
        private set
    private var screenState by mutableStateOf<Screen>(Screen.Home)
    val screen: Screen get() = screenState

    /** Moves to [s] and records where we are, so the screen comes back after the process is killed. */
    private fun setScreen(s: Screen) {
        screenState = s
        savedState[SAVED_INBOX] = (s as? Screen.InboxDetail)?.inbox?.key ?: (s as? Screen.Message)?.inbox?.key
        savedState[SAVED_MESSAGE] = (s as? Screen.Message)?.summary?.let(::summaryToJson)
    }

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
    private val readState = readStore.load()
    /** Unread messages per inbox at its last listing, for the badge on the home screen (persisted). */
    var unread by mutableStateOf(readState.unread)
        private set
    /** Ids of the messages opened at least once, per inbox key. */
    var read by mutableStateOf(readState.read)
        private set

    private fun saveRead() = readStore.save(ReadStore.State(read, unread))

    private fun setUnread(inbox: Inbox, list: List<MailSummary>) {
        unread = unread + (inbox.key to countUnread(inbox, list))
        saveRead()
    }

    /** Set between onStart and onStop of the activity: polling only runs while true. */
    private var foreground = false
    private var createJob: Job? = null
    private var pollJob: Job? = null
    private var messageJob: Job? = null
    /** Last successful listing, for the manual-refresh throttle. */
    private var lastFetchKey: String? = null
    private var lastFetchAt = 0L
    /** Message ids deleted (or being deleted) per inbox key, filtered out of listings until the server agrees. */
    private val pendingDeletes = mutableMapOf<String, MutableSet<String>>()
    /** The error the polling loop itself last reported, so a recovery only clears that one. */
    private var lastFetchError: String? = null
    /** Why the last listing of the open inbox failed, or null; shown inline under the list. */
    var listProblem by mutableStateOf<String?>(null)
        private set

    /** One line for the inbox screen when the listing is stale: the problem and when it last worked. */
    val staleHint: String?
        get() = listProblem?.let { problem ->
            val key = currentInbox()?.key
            if (key != null && key == lastFetchKey && lastFetchAt > 0) "$problem · last updated ${formatDate(lastFetchAt)}" else problem
        }

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
    private suspend inline fun <T> attempt(
        fallback: String,
        noinline onError: (String) -> Unit = { error = it },
        crossinline block: suspend () -> T,
    ): T? = try {
        withContext(Dispatchers.Default) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e.userMessage(fallback))
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
        setScreen(Screen.Settings)
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
            saveRead()
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
                // The user may have opened the inbox meanwhile: leave it before it disappears.
                while (currentInbox()?.key == inbox.key) back()
                forget(inbox)
                notice = "Account deleted on ${inbox.provider.label}"
            }
        }
    }

    private fun forget(inbox: Inbox) {
        inboxes = inboxes.filterNot { it.key == inbox.key }
        unread = unread - inbox.key
        read = read - inbox.key
        saveRead()
        store.save(inboxes)
    }

    fun openInbox(inbox: Inbox) {
        setScreen(Screen.InboxDetail(inbox))
        messages = emptyList()
        listLoaded = false
        listProblem = null
        error = null
        startPolling(inbox)
    }

    fun isRead(inbox: Inbox, summary: MailSummary) = read[inbox.key]?.contains(summary.id) == true

    private fun countUnread(inbox: Inbox, list: List<MailSummary>) = list.count { !isRead(inbox, it) }

    fun openMessage(inbox: Inbox, summary: MailSummary) {
        setScreen(Screen.Message(inbox, summary))
        if (!isRead(inbox, summary)) {
            read = read + (inbox.key to read[inbox.key].orEmpty() + summary.id)
            setUnread(inbox, messages)
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
        // Hidden from listings from now on, so a poll already in flight cannot bring it back.
        pendingDeletes.getOrPut(inbox.key) { mutableSetOf() }.add(summary.id)
        // Not tied to a screen: a deletion started should complete even if the user moves on.
        viewModelScope.launch {
            val deleted = attempt("Could not delete the message") { providerFor(inbox).deleteMessage(inbox, summary) }
            if (deleted != true) pendingDeletes[inbox.key]?.remove(summary.id)
            when {
                deleted == null -> Unit
                !deleted -> error = "${inbox.provider.label} did not delete the message"
                else -> {
                    if (currentInbox()?.key == inbox.key) {
                        messages = messages.filterNot { it.id == summary.id }
                        setUnread(inbox, messages)
                    } else if (!isRead(inbox, summary)) {
                        // Not listed any more: the badge counts one unread fewer.
                        unread = unread + (inbox.key to ((unread[inbox.key] ?: 1) - 1).coerceAtLeast(0))
                        saveRead()
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
            is Screen.Message -> { cancelMessage(); setScreen(Screen.InboxDetail(s.inbox)) }
            is Screen.InboxDetail -> { stopPolling(); setScreen(Screen.Home); messages = emptyList(); listProblem = null }
            Screen.Settings -> setScreen(Screen.Home)
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
        startPolling(inbox, manual = true)
    }

    /**
     * Lists [inbox]. A failure of a [manual] refresh is an error (snackbar); a failure of the
     * automatic loop only sets [listProblem], shown inline, so a flaky connection does not raise a
     * snackbar at every tick.
     */
    private suspend fun fetch(inbox: Inbox, manual: Boolean) {
        listLoading = true
        val list = attempt("Could not refresh", onError = { msg ->
            listProblem = msg
            if (manual) { error = msg; lastFetchError = msg }
        }) { providerFor(inbox).listMessages(inbox) }
        if (list != null) {
            val hidden = pendingDeletes[inbox.key]
            val shown = if (hidden.isNullOrEmpty()) list else list.filterNot { it.id in hidden }
            // Once the server no longer lists a deleted id, it needs no hiding.
            hidden?.retainAll { id -> list.any { it.id == id } }
            lastFetchKey = inbox.key
            lastFetchAt = System.currentTimeMillis()
            messages = shown
            setUnread(inbox, shown)
            listLoaded = true
            listProblem = null
            // A listing that works again clears the listing error it had set — not somebody else's
            // (a message that failed to load keeps saying why while polling goes on behind it).
            if (error != null && error == lastFetchError) error = null
            lastFetchError = null
        }
        listLoading = false
    }

    /**
     * Lists [inbox] then again at the interval chosen in [settings] until [stopPolling] (once only
     * in manual mode). With [immediate] false the first listing waits for the interval to elapse
     * since the last one of the same inbox.
     */
    private fun startPolling(inbox: Inbox, immediate: Boolean = true, manual: Boolean = false) {
        stopPolling()
        if (!foreground) return
        val interval = settings.pollIntervalMs
        pollJob = viewModelScope.launch {
            if (!immediate && lastFetchKey == inbox.key) {
                if (!settings.autoRefresh) return@launch
                val wait = lastFetchAt + interval - System.currentTimeMillis()
                if (wait > 0) delay(wait)
            }
            var first = true
            while (isActive) {
                fetch(inbox, manual = manual && first)
                first = false
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

    init {
        // After a process death, land where the user was (the inbox is re-listed, the message re-fetched).
        val inbox = savedState.get<String>(SAVED_INBOX)?.let { key -> inboxes.firstOrNull { it.key == key } }
        if (inbox != null) {
            openInbox(inbox)
            savedState.get<String>(SAVED_MESSAGE)?.let(::summaryFromJson)?.let { openMessage(inbox, it) }
        }
    }

    private fun summaryToJson(s: MailSummary): String = JSONObject()
        .put("id", s.id).put("from", s.from).put("subject", s.subject).put("date", s.date)
        .put("ref", JSONObject(s.ref))
        .toString()

    private fun summaryFromJson(json: String): MailSummary? = runCatching {
        val o = JSONObject(json)
        val ref = o.optJSONObject("ref")
        MailSummary(
            id = o.getString("id"), from = o.optString("from"), subject = o.optString("subject"), date = o.optLong("date"),
            ref = ref?.keys()?.asSequence()?.associateWith { ref.optString(it) }.orEmpty(),
        )
    }.getOrNull()

    private companion object {
        const val MANUAL_REFRESH_MIN_MS = 30_000L
        const val SAVED_INBOX = "screen.inbox"
        const val SAVED_MESSAGE = "screen.message"
    }
}
