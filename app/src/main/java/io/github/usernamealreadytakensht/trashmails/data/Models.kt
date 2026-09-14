package io.github.usernamealreadytakensht.trashmails.data

enum class Provider(
    val label: String,
    val domainHint: String,
    val allowsCustomName: Boolean,
    /** Max inboxes that can be created per rolling 24 h window. */
    val dailyLimit: Int,
    val siteUrl: String,
    val privacyUrl: String? = null,
    /** Public repository of the service itself, when its code is published. */
    val sourceUrl: String? = null,
    /** Set when the service cannot deliver mail anymore: the provider is listed but cannot be picked. */
    val unavailableReason: String? = null,
) {
    // Declaration order is the display order within a group (open source first, unavailable last).
    INBOX_KITTEN("Inbox Kitten", "@inboxkitten.com", true, 20, "https://inboxkitten.com", sourceUrl = "https://github.com/uilicious/inboxkitten"),
    MAILDROP("Maildrop", "@maildrop.cc", true, 20, "https://maildrop.cc", "https://maildrop.cc/privacy/", sourceUrl = "https://github.com/m242/maildrop"),
    // Guerrilla Mail has no separate policy page: the privacy section lives in its terms.
    GUERRILLA_MAIL("Guerrilla Mail", "@guerrillamailblock.com", true, 20, "https://www.guerrillamail.com", "https://www.guerrillamail.com/tos"),
    MAIL_TM("mail.tm", "@ domain chosen by mail.tm", true, 10, "https://mail.tm", "https://mail.tm/en/privacy/"),
    BURNER_KIWI(
        "Burner Kiwi", "random address", false, 5, "https://burner.kiwi",
        sourceUrl = "https://github.com/haydenwoodhead/burner.kiwi",
        unavailableReason = "Mail never arrives: two of its three domains have expired and the last one " +
            "(deceit.pro) rejects every recipient. Checked September 2026.",
    );

    val available: Boolean get() = unavailableReason == null
    val openSource: Boolean get() = sourceUrl != null
}

/** A temporary inbox. [id] is the mailbox name (kitten/guerrilla/maildrop) or the account id (burner/mail.tm). */
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
    /** Whether [deleteMessage] does anything on the server; the delete button is only shown when true. */
    val canDeleteMessages: Boolean get() = false
    suspend fun deleteMessage(inbox: Inbox, summary: MailSummary): Boolean = false

    /** Whether [deleteInbox] removes the inbox on the server (an account), not only from this app. */
    val canDeleteInbox: Boolean get() = false
    suspend fun deleteInbox(inbox: Inbox): Boolean = false
}

open class ProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)
