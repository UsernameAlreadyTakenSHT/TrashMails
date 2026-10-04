package io.github.usernamealreadytakensht.trashmails.data.providers

import io.github.usernamealreadytakensht.trashmails.data.Inbox
import io.github.usernamealreadytakensht.trashmails.data.MailContent
import io.github.usernamealreadytakensht.trashmails.data.MailProvider
import io.github.usernamealreadytakensht.trashmails.data.MailSummary
import io.github.usernamealreadytakensht.trashmails.data.MessageCache

/**
 * A provider whose emails live in [cache] rather than on the server (tempmail.lol hands them over
 * once, DropMail.me loses them with the session): bodies are read from the listing or the cache,
 * and deleting a message only drops the local copy.
 */
internal interface CacheBackedProvider : MailProvider {
    val cache: MessageCache

    override suspend fun getMessage(inbox: Inbox, summary: MailSummary): MailContent {
        // The bodies travel with the summary; after a process death only the cached copy has them.
        val m = summary.takeIf { it.html != null || it.text != null } ?: cache.load(inbox.key).firstOrNull { it.id == summary.id } ?: summary
        return MailContent(html = m.html, text = m.text)
    }

    override val canDeleteMessages get() = true
    override val deletesLocally get() = true
}
