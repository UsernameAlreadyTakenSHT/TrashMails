package com.example.trashmails.data

enum class Provider(
    val label: String,
    val domainHint: String,
    val allowsCustomName: Boolean,
    /** Max inboxes that can be created per rolling 24 h window. */
    val dailyLimit: Int,
) {
    INBOX_KITTEN("Inbox Kitten", "@inboxkitten.com", true, 20),
    BURNER_KIWI("Burner Kiwi", "random address", false, 5),
    MAILDROP("Maildrop", "@maildrop.cc", true, 20),
}

/** A temporary inbox. [id] is the mailbox name (kitten/maildrop) or the UUID (burner). */
data class Inbox(
    val id: String,
    val provider: Provider,
    val address: String,
    val token: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
) {
    val key: String get() = "${provider.name}:$id"
}

data class MailSummary(
    val id: String,
    val from: String,
    val subject: String,
    val date: Long,
    /** Body already fetched (Burner returns it in the list call). */
    val html: String? = null,
    val text: String? = null,
    /** Opaque provider data needed to load the body (e.g. Mailgun key/region). */
    val ref: Map<String, String> = emptyMap(),
)

data class MailContent(val html: String?, val text: String?)

interface MailProvider {
    val provider: Provider
    suspend fun createInbox(name: String?): Inbox
    suspend fun listMessages(inbox: Inbox): List<MailSummary>
    suspend fun getMessage(inbox: Inbox, summary: MailSummary): MailContent
    suspend fun deleteMessage(inbox: Inbox, summary: MailSummary): Boolean = false
}

class ProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)
