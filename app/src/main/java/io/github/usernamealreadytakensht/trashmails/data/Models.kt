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
    /** Domains the user may pick for a new address; empty when there is no choice. */
    val domains: List<String> = emptyList(),
    /** The service can hand out a scrambled alias of the inbox name, harder to guess than the name. */
    val scrambleAvailable: Boolean = false,
    /** The name typed is only a prefix: the service appends a random suffix (and picks the domain). */
    val nameIsPrefix: Boolean = false,
    /** Addresses normally sit on a random subdomain; the user may ask for the bare domain instead. */
    val subdomainOptional: Boolean = false,
    /** The service picks among [domains] unless one is chosen: "Random" comes first and is the default. */
    val randomDomain: Boolean = false,
    /** Any `local-tag@sub.domain` (tag and sub made of letters) is delivered to `local@domain`. */
    val extendedAddresses: Boolean = false,
) {
    // Declaration order is the display order within a group (open source first, unavailable last).
    INBOX_KITTEN("Inbox Kitten", "@inboxkitten.com", true, 20, "https://inboxkitten.com", sourceUrl = "https://github.com/uilicious/inboxkitten"),
    MAILDROP("Maildrop", "@maildrop.cc", true, 20, "https://maildrop.cc", "https://maildrop.cc/privacy/", sourceUrl = "https://github.com/m242/maildrop"),
    // Guerrilla Mail has no separate policy page: the privacy section lives in its terms.
    GUERRILLA_MAIL(
        "Guerrilla Mail", "@guerrillamail.com", true, 20, "https://www.guerrillamail.com", "https://www.guerrillamail.com/tos",
        // Every domain delivers to the same inbox name; the choice is only what the address looks like.
        domains = listOf(
            "guerrillamail.com", "guerrillamail.net", "guerrillamail.org", "guerrillamail.biz", "guerrillamail.de",
            "guerrillamail.info", "guerrillamailblock.com", "grr.la", "sharklasers.com", "spam4.me", "pokemail.net",
        ),
        scrambleAvailable = true,
    ),
    MAIL_TM("mail.tm", "domain chosen by mail.tm", true, 10, "https://mail.tm", "https://mail.tm/en/privacy/"),
    // Only the clients are open source (github.com/tempmail-lol); the service itself is not.
    TEMPMAIL_LOL(
        "tempmail.lol", "random domain", true, 20, "https://tempmail.lol", "https://tempmail.lol/privacy",
        nameIsPrefix = true,
        subdomainOptional = true,
    ),
    // Only the permanent domains are listed; the rotating ones the API also offers expire within a year.
    DROPMAIL(
        "DropMail.me", "random domain", false, 20, "https://dropmail.me", "https://dropmail.me/privacypolicy.html",
        domains = listOf(
            "dropmail.me", "10mail.org", "10mail.info", "10mail.xyz", "emlhub.com", "emlpro.com", "emltmp.com", "freeml.net",
            "mailpwr.com", "mailtowin.com", "maximail.fyi", "maximail.vip", "mimimail.me", "spymail.one", "yomail.info",
        ),
        randomDomain = true,
        extendedAddresses = true,
    ),
    BURNER_KIWI(
        "Burner Kiwi", "random address", false, 5, "https://burner.kiwi",
        sourceUrl = "https://github.com/haydenwoodhead/burner.kiwi",
        unavailableReason = "Mail never arrives: two of its three domains have expired and the last one " +
            "(deceit.pro) rejects every recipient. Checked September 2026.",
    );

    val available: Boolean get() = unavailableReason == null

    companion object {
        /** The entry named [name], or null: stored names may come from a version that no longer has it. */
        fun fromName(name: String?): Provider? = entries.firstOrNull { it.name == name }
    }
    /** What follows the name in the create dialog's field: the domain when there is one fixed. */
    val fieldSuffix: String get() = if (domainHint.startsWith("@")) domainHint else "@…"
    val openSource: Boolean get() = sourceUrl != null
}

/**
 * Choices offered when creating an address, for the providers that support them
 * ([Provider.domains], [Provider.scrambleAvailable]); ignored by the others.
 */
data class CreateOptions(val domain: String? = null, val scramble: Boolean = false, val noSubdomain: Boolean = false)

/** A temporary inbox. [id] is the mailbox name (kitten/guerrilla/maildrop), the account id (burner/mail.tm) or the address (tempmail.lol/dropmail). */
data class Inbox(
    val id: String,
    val provider: Provider,
    val address: String,
    val token: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
) {
    /** Identity across providers (an id is only unique within one). Not part of equals: derived from it. */
    val key: String = "${provider.name}:$id"
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

/** A message body. [text] is the provider's plain part, or one derived from [html] by the ViewModel. */
data class MailContent(val html: String?, val text: String?)

interface MailProvider {
    val provider: Provider
    suspend fun createInbox(name: String?, options: CreateOptions = CreateOptions()): Inbox
    suspend fun listMessages(inbox: Inbox): List<MailSummary>
    suspend fun getMessage(inbox: Inbox, summary: MailSummary): MailContent
    /** Whether [deleteMessage] does anything on the server; the delete button is only shown when true. */
    val canDeleteMessages: Boolean get() = false
    suspend fun deleteMessage(inbox: Inbox, summary: MailSummary): Boolean = false

    /** Whether [deleteInbox] removes the inbox on the server (an account), not only from this app. */
    val canDeleteInbox: Boolean get() = false
    suspend fun deleteInbox(inbox: Inbox): Boolean = false

    /** True when [deleteMessage] only drops a local copy: the server hands messages over once and keeps none. */
    val deletesLocally: Boolean get() = false

    /** The app forgets [inbox]: anything the provider kept for it locally goes too. */
    fun forgetInbox(inbox: Inbox) {}
}

open class ProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)
