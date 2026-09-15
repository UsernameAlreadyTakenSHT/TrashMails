package io.github.usernamealreadytakensht.trashmails.ui

import io.github.usernamealreadytakensht.trashmails.data.Provider

/*
 * Everything the UI knows per provider, next to the logo in ProviderLogo.kt. Both are exhaustive
 * `when`s: adding a Provider entry fails to compile until it is described here.
 */


/** What happens to emails and the inbox on the provider's server. */
data class RetentionInfo(val mails: List<String>, val account: List<String>, val warning: String?)

fun retentionInfo(provider: Provider): RetentionInfo = when (provider) {
    Provider.INBOX_KITTEN -> RetentionInfo(
        mails = listOf(
            "Kept on the server for about 3 days, then deleted automatically.",
            "No manual deletion: the API does not allow it.",
        ),
        account = listOf(
            "There is no account: the inbox only exists by its name.",
            "Removing the address only forgets it in this app.",
        ),
        warning = "Public inbox: anyone who knows the name can read the emails while they exist.",
    )
    Provider.GUERRILLA_MAIL -> RetentionInfo(
        mails = listOf(
            "Kept on the server for 1 hour, then deleted automatically.",
            "Can be deleted manually from this app.",
        ),
        account = listOf(
            "There is no account: the inbox only exists by its name.",
            "Removing the address only forgets it in this app.",
            "When creating: the domain (all are aliases of the same inbox) and a scrambled address (a random alias instead of the name).",
        ),
        warning = "Public inbox: anyone who knows the name can read the emails while they exist. A scrambled address keeps the name out of sight.",
    )
    Provider.MAIL_TM -> RetentionInfo(
        mails = listOf(
            "Kept on the server for 7 days, then deleted automatically.",
            "Can be deleted manually from this app.",
        ),
        account = listOf(
            "A real account (address + password) that mail.tm keeps until it is deleted.",
            "Removing the address can delete the account on mail.tm, messages included (ticked by default); otherwise the password is just lost and nobody can open the inbox anymore.",
        ),
        warning = null,
    )
    Provider.BURNER_KIWI -> RetentionInfo(
        mails = listOf(
            "Expire 24 h after the inbox was created, then purged within 48 h.",
            "No manual deletion before expiry.",
        ),
        account = listOf(
            "The inbox expires by itself after 24 h.",
            "The access token is erased from this app: nobody will be able to read it anymore.",
        ),
        warning = null,
    )
    Provider.MAILDROP -> RetentionInfo(
        mails = listOf(
            "Kept on the server: 10 messages max (oldest are overwritten).",
            "The inbox is emptied after 24 h without a new email.",
            "Can be deleted manually from this app.",
        ),
        account = listOf(
            "There is no account: the inbox only exists by its name.",
            "Removing the address only forgets it in this app.",
        ),
        warning = "Public inbox: anyone who knows the name can read the emails while they exist.",
    )
}
