package io.github.usernamealreadytakensht.trashmails

import android.app.UiModeManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.usernamealreadytakensht.trashmails.data.Settings
import io.github.usernamealreadytakensht.trashmails.ui.MailViewModel
import io.github.usernamealreadytakensht.trashmails.ui.Screen
import io.github.usernamealreadytakensht.trashmails.ui.screens.HomeScreen
import io.github.usernamealreadytakensht.trashmails.ui.screens.InboxScreen
import io.github.usernamealreadytakensht.trashmails.ui.screens.MessageScreen
import io.github.usernamealreadytakensht.trashmails.ui.screens.SettingsScreen
import io.github.usernamealreadytakensht.trashmails.ui.theme.TrashMailsTheme

class MainActivity : ComponentActivity() {
    private val vm: MailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // FLAG_SECURE follows the setting live, so toggling it needs no restart.
            val blockScreenshots = vm.settings.blockScreenshots
            LaunchedEffect(blockScreenshots) {
                if (blockScreenshots) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            val theme = vm.settings.theme
            LaunchedEffect(theme) { applyNightMode(theme) }
            TrashMailsTheme(
                darkTheme = when (theme) {
                    Settings.THEME_LIGHT -> false
                    Settings.THEME_DARK -> true
                    else -> isSystemInDarkTheme()
                },
            ) { App(vm) }
        }
    }

    /**
     * From Android 12 the whole app configuration can follow the chosen theme, so the window
     * background, the values-night resources and the message WebView switch with it; before that
     * only the Compose colours do.
     */
    private fun applyNightMode(theme: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val manager = getSystemService(UiModeManager::class.java) ?: return
        manager.setApplicationNightMode(
            when (theme) {
                Settings.THEME_LIGHT -> UiModeManager.MODE_NIGHT_NO
                Settings.THEME_DARK -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
        )
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        vm.onBackground()
        super.onStop()
    }
}

@Composable
private fun App(vm: MailViewModel) {
    BackHandler(enabled = vm.screen != Screen.Home) { vm.back() }

    when (val s = vm.screen) {
        Screen.Home -> HomeScreen(
            inboxes = vm.inboxes,
            unread = vm.unread,
            creating = vm.creating,
            createError = vm.createError,
            error = vm.error,
            notice = vm.notice,
            quotas = vm.quotas,
            defaultProvider = vm.settings.lastProvider,
            onOpenCreate = vm::refreshQuota,
            onOpenSettings = vm::openSettings,
            onDismissError = vm::clearError,
            onDismissNotice = vm::clearNotice,
            onCancelCreate = vm::cancelCreate,
            onOpen = vm::openInbox,
            onDelete = vm::deleteInbox,
            canDeleteOnServer = vm::canDeleteInbox,
            onCreate = vm::createInbox,
        )
        Screen.Settings -> SettingsScreen(
            settings = vm.settings,
            onChange = vm::updateSettings,
            countOlderThan = vm::countOlderThan,
            onBack = vm::back,
        )
        is Screen.InboxDetail -> InboxScreen(
            inbox = s.inbox,
            messages = vm.messages,
            loading = vm.listLoading,
            loaded = vm.listLoaded,
            refreshHint = vm.refreshHint,
            staleHint = vm.staleHint,
            error = vm.error,
            notice = vm.notice,
            onBack = vm::back,
            onRefresh = { vm.refresh(s.inbox) },
            onOpen = { vm.openMessage(s.inbox, it) },
            isRead = { vm.isRead(s.inbox, it) },
            onDismissError = vm::clearError,
            onDismissNotice = vm::clearNotice,
        )
        is Screen.Message -> MessageScreen(
            provider = s.inbox.provider,
            summary = s.summary,
            content = vm.content,
            loading = vm.messageLoading,
            error = vm.error,
            settings = vm.settings,
            onBack = vm::back,
            onDelete = if (vm.canDeleteMessages(s.inbox)) {
                { vm.deleteMessage(s.inbox, s.summary) }
            } else null,
        )
    }
}
