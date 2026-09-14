package com.example.trashmails

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.example.trashmails.ui.MailViewModel
import com.example.trashmails.ui.Screen
import com.example.trashmails.ui.screens.HomeScreen
import com.example.trashmails.ui.screens.InboxScreen
import com.example.trashmails.ui.screens.MessageScreen
import com.example.trashmails.ui.theme.TrashMailsTheme

class MainActivity : ComponentActivity() {
    private val vm: MailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
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
            quotas = vm.quotas,
            onOpenCreate = vm::refreshQuota,
            onDismissError = vm::clearError,
            onCancelCreate = vm::cancelCreate,
            onOpen = vm::openInbox,
            onDelete = vm::deleteInbox,
            onCreate = vm::createInbox,
        )
        is Screen.InboxDetail -> InboxScreen(
            inbox = s.inbox,
            messages = vm.messages,
            loading = vm.listLoading,
            loaded = vm.listLoaded,
            error = vm.error,
            onBack = vm::back,
            onRefresh = { vm.refresh(s.inbox) },
            onOpen = { vm.openMessage(s.inbox, it) },
            onDismissError = vm::clearError,
        )
        is Screen.Message -> MessageScreen(
            provider = s.inbox.provider,
            summary = s.summary,
            content = vm.content,
            loading = vm.messageLoading,
            error = vm.error,
            onBack = vm::back,
            onDelete = if (vm.canDeleteMessages(s.inbox)) {
                { vm.deleteMessage(s.inbox, s.summary); vm.back() }
            } else null,
        )
    }
}
