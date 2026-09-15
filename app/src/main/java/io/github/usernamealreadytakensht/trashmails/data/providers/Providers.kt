package io.github.usernamealreadytakensht.trashmails.data.providers

import io.github.usernamealreadytakensht.trashmails.data.Http
import io.github.usernamealreadytakensht.trashmails.data.HttpApi
import io.github.usernamealreadytakensht.trashmails.data.MailProvider
import io.github.usernamealreadytakensht.trashmails.data.Provider

/**
 * One implementation per [Provider], built here and nowhere else. The `when` is exhaustive: a new
 * enum entry does not compile until its implementation is listed, instead of failing at runtime.
 */
fun allProviders(http: HttpApi = Http): Map<Provider, MailProvider> =
    Provider.entries.associateWith { provider ->
        when (provider) {
            Provider.INBOX_KITTEN -> InboxKittenProvider(http)
            Provider.MAILDROP -> MaildropProvider(http)
            Provider.GUERRILLA_MAIL -> GuerrillaMailProvider(http)
            Provider.MAIL_TM -> MailTmProvider(http)
            Provider.BURNER_KIWI -> BurnerKiwiProvider(http)
        }
    }
