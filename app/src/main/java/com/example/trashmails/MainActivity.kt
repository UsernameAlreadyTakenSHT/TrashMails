package com.example.trashmails

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.trashmails.ui.MailViewModel
import com.example.trashmails.ui.Screen
import com.example.trashmails.ui.screens.HomeScreen
import com.example.trashmails.ui.screens.InboxScreen
import com.example.trashmails.ui.screens.MessageScreen
import com.example.trashmails.ui.screens.SettingsScreen
import com.example.trashmails.ui.theme.TrashMailsTheme

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
            TrashMailsTheme { App(vm) }
        }
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
            counts = vm.counts,
            creating = vm.creating,
            error = vm.error,
            notice = vm.notice,
            quotas = vm.quotas,
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
            onBack = vm::back,
        )
        is Screen.InboxDetail -> InboxScreen(
            inbox = s.inbox,
            messages = vm.messages,
            loading = vm.listLoading,
            loaded = vm.listLoaded,
            refreshHint = vm.refreshHint,
            error = vm.error,
            notice = vm.notice,
            onBack = vm::back,
            onRefresh = { vm.refresh(s.inbox) },
            onOpen = { vm.openMessage(s.inbox, it) },
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
                { vm.deleteMessage(s.inbox, s.summary); vm.back() }
            } else null,
        )
    }
}
